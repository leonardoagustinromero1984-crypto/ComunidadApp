# Read-only coverage gate. Uses git diff. Never reset/restore/checkout.
param(
    [string]$RepoRoot = "",
    [string]$OutJson = ""
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}
$MapPath = Join-Path $RepoRoot "docs\qa\change-impact-map.yaml"
$CatalogPath = Join-Path $RepoRoot "docs\qa\regression-catalog.yaml"

function Fail([string]$Message) { throw $Message }

function Get-ChangedFiles {
    $names = @()
    $gitArgs = @(
        @("diff", "--name-only"),
        @("diff", "--name-only", "--cached"),
        @("ls-files", "--others", "--exclude-standard")
    )
    foreach ($a in $gitArgs) {
        $out = cmd /c "git -C `"$RepoRoot`" $($a -join ' ') 2>nul"
        foreach ($line in @($out)) {
            $t = ([string]$line).Trim().Replace("\", "/")
            if ($t -and $t -notmatch '^warning:') { $names += $t }
        }
    }
    return @($names | Select-Object -Unique)
}

function Test-Ignored([string]$Rel, [string[]]$Prefixes) {
    foreach ($p in $Prefixes) {
        $n = $p.TrimEnd("/")
        if ($Rel -eq $n -or $Rel.StartsWith("$n/")) { return $true }
    }
    return $false
}

function Test-PathMatch([string]$Rel, [string]$Pattern) {
    $pat = $Pattern.Trim().Replace("\", "/").TrimEnd("/")
    if ($Rel -eq $pat) { return $true }
    if ($Rel.StartsWith("$pat/")) { return $true }
    if ($pat.EndsWith("**")) {
        $stem = $pat.TrimEnd("*").TrimEnd("/")
        return $Rel.StartsWith($stem)
    }
    return $false
}

if (-not (Test-Path $MapPath)) { Fail "IMPACT_MAP_MISSING $MapPath" }
if (-not (Test-Path $CatalogPath)) { Fail "CATALOG_MISSING $CatalogPath" }

$mapRaw = Get-Content $MapPath -Raw
$ignore = @()
foreach ($m in [regex]::Matches($mapRaw, '(?m)^\s+-\s+(docs/|artifacts/|apk/|\.gradle/|build/|\.claude/|web/)')) {
    $ignore += $m.Groups[1].Value
}
if ($ignore.Count -eq 0) {
    $ignore = @("docs/", "artifacts/", "apk/", ".gradle/", "build/", ".claude/", "web/")
}

$blocks = @()
$current = $null
foreach ($line in Get-Content $MapPath) {
    if ($line -match '^\s+- id:\s+(\S+)') {
        if ($current) { $blocks += $current }
        $current = @{
            Id            = $Matches[1]
            Paths         = @()
            Modules       = @()
            Gradle        = @()
            MaestroRequired = $false
        }
        continue
    }
    if (-not $current) { continue }
    if ($line -match '^\s+-\s+(\S+\S*)\s*$' -and $line -notmatch 'id:') {
        $val = $Matches[1]
        if ($val -match 'com\.comunidapp') { $current.Gradle += $val }
        elseif ($val -match '/' -or $val -match '\.kt$') { $current.Paths += $val }
        elseif ($val -match '^[A-Z0-9_]+$' -and $line -match 'modules|^\s+- [A-Z]') { $current.Modules += $val }
    }
    if ($line -match 'modules:\s*\[([^\]]+)\]') {
        $current.Modules += @($Matches[1].Split(",") | ForEach-Object { $_.Trim() })
    }
    if ($line -match 'maestro_required:\s*true') { $current.MaestroRequired = $true }
}
if ($current) { $blocks += $current }

$changed = Get-ChangedFiles
$modules = [System.Collections.Generic.List[string]]::new()
$gradle = [System.Collections.Generic.List[string]]::new()
$maestro = $false
$unmapped = [System.Collections.Generic.List[string]]::new()
$considered = [System.Collections.Generic.List[string]]::new()

$functionalHint = [regex]'^(app/src/main/|shared/src/(commonMain|androidMain|jvmMain)/|infra/supabase-canonical/supabase/migrations/)'

foreach ($rel in $changed) {
    if (Test-Ignored $rel $ignore) { continue }
    $hit = $null
    foreach ($b in $blocks) {
        foreach ($p in $b.Paths) {
            if (Test-PathMatch $rel $p) { $hit = $b; break }
        }
        if ($hit) { break }
    }
    if ($hit) {
        [void]$considered.Add($rel)
        foreach ($m in $hit.Modules) {
            if ($m -and -not $modules.Contains($m)) { $modules.Add($m) }
        }
        foreach ($g in $hit.Gradle) {
            if ($g -and -not $gradle.Contains($g)) { $gradle.Add($g) }
        }
        if ($hit.MaestroRequired) { $maestro = $true }
    } elseif ($rel -match $functionalHint) {
        [void]$unmapped.Add($rel)
    }
}

Write-Host "CHANGED AREAS:"
if ($modules.Count -eq 0) { Write-Host "(none mapped)" } else { $modules | ForEach-Object { Write-Host $_ } }
Write-Host ""
Write-Host "REQUIRED TEST SUITES:"
Write-Host "FAST"
$modules | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "TEST FILES / GRADLE FILTERS:"
if ($gradle.Count -eq 0) { Write-Host "(full :app:testDebugUnitTest if running Affected)" } else { $gradle | ForEach-Object { Write-Host $_ } }
Write-Host ""
Write-Host "MAESTRO_REQUIRED: $(if ($maestro) { 'YES' } else { 'NO' })"
Write-Host "MAESTRO: not started by this gate"
Write-Host ""

$coverage = "OK"
if ($unmapped.Count -gt 0) {
    $coverage = "MISSING"
    Write-Host "UNMAPPED FUNCTIONAL FILES:"
    $unmapped | ForEach-Object { Write-Host $_ }
}

Write-Host "COVERAGE: $coverage"

$result = [pscustomobject]@{
    Modules         = @($modules)
    GradleTests     = @($gradle)
    MaestroRequired = $maestro
    Coverage        = $coverage
    ChangedMapped   = @($considered)
    Unmapped        = @($unmapped)
}
if ($OutJson) {
    New-Item -ItemType Directory -Path (Split-Path $OutJson) -Force | Out-Null
    $result | ConvertTo-Json -Depth 5 | Set-Content $OutJson -Encoding utf8
}
if ($coverage -eq "MISSING") {
    Fail "QA_REGRESSION_COVERAGE_MISSING $($unmapped -join ',')"
}
$result
