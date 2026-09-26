# LeoVer local regression. Default = Fast (no emulator, no Maestro).
param(
    [switch]$Fast,
    [switch]$Affected,
    [switch]$Core,
    [switch]$Full,
    [switch]$Backend,
    [switch]$Android,
    [switch]$Maestro,
    [switch]$SmokeE2E,
    [switch]$FullE2E,
    [switch]$ResetAndSeed,
    [switch]$KeepEmulator
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutDir = Join-Path $RepoRoot "artifacts\qa\regression\$Stamp"
$Report = Join-Path $OutDir "report.md"
$Json = Join-Path $OutDir "results.json"
$Gradlew = Join-Path $RepoRoot "gradlew.bat"
$JavaHome = "C:\Program Files\Android\Android Studio\jbr"

function Fail([string]$Message) { throw $Message }

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

$e2eRequested = [bool]($Maestro -or $SmokeE2E -or $FullE2E)
if (-not ($Fast -or $Affected -or $Core -or $Full -or $Backend -or $Android -or $e2eRequested)) {
    $Fast = $true
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$(Join-Path $JavaHome 'bin');$env:Path"

$coverage = $null
$maestroRequired = $false
$gradleFilters = @()
$modules = @()

if ($Affected -or $Core) {
    $covJson = Join-Path $OutDir "coverage.json"
    & (Join-Path $PSScriptRoot "check-regression-coverage.ps1") -RepoRoot $RepoRoot -OutJson $covJson
    if ($LASTEXITCODE -ne 0) { Fail "COVERAGE_GATE_FAILED" }
    if (Test-Path $covJson) {
        $coverage = Get-Content $covJson -Raw | ConvertFrom-Json
        $modules = @($coverage.Modules)
        $gradleFilters = @($coverage.GradleTests)
        $maestroRequired = [bool]$coverage.MaestroRequired
        if ($coverage.Coverage -eq "MISSING") { Fail "COVERAGE_GATE_MISSING" }
    }
}

function Invoke-FastGradle([string[]]$Tests) {
    if (-not (Test-Path $Gradlew)) { Fail "GRADLEW_MISSING $Gradlew" }
    $args = @(":app:testDebugUnitTest", "--no-daemon")
    foreach ($t in $Tests) {
        if ($t) { $args += @("--tests", $t) }
    }
    Write-Host "GRADLE $($args -join ' ')"
    $p = Start-Process -FilePath $Gradlew -ArgumentList $args -WorkingDirectory $RepoRoot -Wait -PassThru -NoNewWindow
    return $p.ExitCode
}

$fastStatus = "NOT RUN"
$maestroStatus = "NOT RUN"
$fullStatus = "NOT RUN"

if ($Fast -or $Affected -or $Core -or $Full -or $Android) {
    $filters = @()
    if ($Affected -and $gradleFilters.Count -gt 0 -and -not $Full -and -not $Core) {
        $filters = $gradleFilters
    }
    $code = Invoke-FastGradle $filters
    $fastStatus = if ($code -eq 0) { "PASS" } else { "FAIL" }
    if ($fastStatus -eq "FAIL") {
        $maestroStatus = "SKIPPED"
    }
}

if ($Backend) {
    Write-Host "BACKEND probes are STAGING CLI scripts; not executed in Fast mode."
}

if ($e2eRequested) {
    if ($fastStatus -eq "FAIL") {
        Write-Host "SKIP Maestro because Fast layer failed"
        $maestroStatus = "SKIPPED"
    } else {
        $maestroArgs = @(
            "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", (Join-Path $PSScriptRoot "run-maestro-e2e.ps1")
        )
        if ($FullE2E) { $maestroArgs += "-Full" }
        elseif ($SmokeE2E -or $Maestro) { $maestroArgs += "-Smoke" }
        if ($ResetAndSeed) { $maestroArgs += "-ResetAndSeed" }
        if (-not $KeepEmulator) { $maestroArgs += "-StopEmulatorAfter" }
        Write-Host "MAESTRO_ON_DEMAND $($maestroArgs -join ' ')"
        $m = Start-Process -FilePath "powershell.exe" -ArgumentList $maestroArgs -WorkingDirectory $RepoRoot -Wait -PassThru -NoNewWindow
        $maestroStatus = if ($m.ExitCode -eq 0) { "PASS" } else { "FAIL" }
        $fullStatus = if ($FullE2E) { $maestroStatus } else { "NOT RUN" }
    }
} else {
    Write-Host "MAESTRO: not started (no -Maestro/-SmokeE2E/-FullE2E)"
}

$gitHead = (git -C $RepoRoot rev-parse --short HEAD 2>$null)
$dirty = (git -C $RepoRoot status --porcelain 2>$null | Measure-Object).Count

$lines = @(
    "# LeoVer regression $Stamp",
    "",
    "HEAD: $gitHead",
    "DIRTY_FILES: $dirty",
    "MODE: $(if ($Affected) { 'AFFECTED' } elseif ($FullE2E) { 'FULL_E2E' } elseif ($SmokeE2E -or $Maestro) { 'SMOKE_E2E' } elseif ($Full) { 'FULL_FAST' } else { 'FAST' })",
    "MODULES: $($modules -join ', ')",
    "FAST/AFFECTED TESTS: $fastStatus",
    "MAESTRO REQUIRED: $(if ($maestroRequired -or $e2eRequested) { 'YES' } else { 'NO' })",
    "MAESTRO RUN: $maestroStatus",
    "FULL REGRESSION: $fullStatus",
    "EMULATOR AUTO-START: $(if ($e2eRequested) { 'only for Maestro flags' } else { 'NO' })",
    "PHONE PHYSICAL USED: NO"
)
$lines | Set-Content $Report -Encoding utf8

@{
    stamp             = $Stamp
    head              = "$gitHead"
    dirtyFiles        = $dirty
    modules           = $modules
    fast              = $fastStatus
    maestroRequired   = [bool]($maestroRequired -or $e2eRequested)
    maestroRun        = $maestroStatus
    fullRegression    = $fullStatus
} | ConvertTo-Json | Set-Content $Json -Encoding utf8

Write-Host "REPORT=$Report"
Write-Host "FAST/AFFECTED TESTS: $fastStatus"
Write-Host "MAESTRO REQUIRED: $(if ($maestroRequired -or $e2eRequested) { 'YES' } else { 'NO' })"
Write-Host "MAESTRO RUN: $maestroStatus"
Write-Host "FULL REGRESSION: $fullStatus"

if ($fastStatus -eq "FAIL") { Fail "QA_REGRESSION_FAST_FAILED" }
if ($maestroStatus -eq "FAIL") { Fail "QA_REGRESSION_MAESTRO_FAILED" }
Write-Host "LOCAL REGRESSION: PASS"
