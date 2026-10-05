# LeoVer 17B.12 STAGING fixture runner.
# Refuses every project other than tobqbddfcyitwgbkthhy.
# Does not create auth users. Reuses QA01-QA16 and the two QA shelters.
# Without -Apply it only checks the guard and prints the plan.
param(
    [switch]$Apply
)

$ErrorActionPreference = "Stop"
$ExpectedRef = "tobqbddfcyitwgbkthhy"
$LegacyRef = "wystsapjfpdtoprlmizz"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Workdir = Join-Path $RepoRoot "infra\supabase-canonical"
$Sql = Join-Path $Workdir "qa\seed_17b12_staging.sql"

if (-not (Test-Path $Sql)) { throw "QA17B12_ABORT_SQL_MISSING" }
$refFile = Join-Path $Workdir "supabase\.temp\project-ref"
$linked = $null
if (Test-Path $refFile) { $linked = (Get-Content $refFile -Raw).Trim() }
if ([string]::IsNullOrWhiteSpace($linked)) { throw "QA17B12_ABORT_UNLINKED" }
if ($linked -eq $LegacyRef) { throw "QA17B12_ABORT_LEGACY_PROJECT" }
if ($linked -ne $ExpectedRef) { throw "QA17B12_ABORT_UNKNOWN_PROJECT:$linked" }

Write-Host "STAGING_ONLY_GUARD=PASS ref=$ExpectedRef"
Write-Host "REUSE=QA01-QA16 and slugs qa-cc02-shelter-n (A) / qa-cc02-shelter-u (B)"
Write-Host "SQL=$Sql"
if (-not $Apply) {
    Write-Host "APPLY=NOT_RUN Pass -Apply to execute on the linked STAGING project."
    exit 0
}

supabase --workdir $Workdir db query --linked -f $Sql
if ($LASTEXITCODE -ne 0) { throw "QA17B12_APPLY_FAILED" }
Write-Host "APPLY=PASS"
