# LeoVer SEC-04D — backup Storage bytes to a private directory outside Git.
# No credentials in this file. Uses an existing Supabase CLI login.
# Env: LEOVER_PROJECT_REF, LEOVER_SUPABASE_WORKDIR, LEOVER_STORAGE_BACKUP_DIR
param(
  [string]$ProjectRef = $(if ($env:LEOVER_PROJECT_REF) { $env:LEOVER_PROJECT_REF } else { "tobqbddfcyitwgbkthhy" }),
  [string]$Workdir = $(if ($env:LEOVER_SUPABASE_WORKDIR) { $env:LEOVER_SUPABASE_WORKDIR } else { (Join-Path $PSScriptRoot "..\..\infra\supabase-canonical") }),
  [string]$OutDir = $env:LEOVER_STORAGE_BACKUP_DIR,
  [string[]]$Buckets = @("public-media", "private-media", "documents", "moderation-evidence", "vitacora-import")
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Workdir = (Resolve-Path $Workdir).Path
if (-not $OutDir) {
  $stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
  $OutDir = Join-Path $env:LOCALAPPDATA "LeoVer-SEC04D\$stamp"
}
$OutDir = [System.IO.Path]::GetFullPath($OutDir)
if ($OutDir.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "Refusing to write Storage backup inside the Git repository: $OutDir"
}
if ($ProjectRef -eq "tobqbddfcyitwgbkthhy" -and $Workdir -notlike "*supabase-canonical*") {
  Write-Warning "STAGING source expected canonical workdir. Continuing with -Workdir $Workdir"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$objectsRoot = Join-Path $OutDir "objects"
New-Item -ItemType Directory -Force -Path $objectsRoot | Out-Null

$backupId = "sec04d-" + (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$started = (Get-Date).ToUniversalTime().ToString("o")
$entries = New-Object System.Collections.Generic.List[object]

foreach ($bucket in $Buckets) {
  Write-Output "BACKUP_BUCKET=$bucket"
  Push-Location $objectsRoot
  try {
    npx --yes supabase --experimental --workdir $Workdir storage cp -r "ss:///$bucket" . --project-ref $ProjectRef
    if ($LASTEXITCODE -ne 0) {
      Write-Output "BACKUP_BUCKET_EMPTY_OR_UNREADABLE=$bucket exit=$LASTEXITCODE"
    }
  } finally {
    Pop-Location
  }

  $bucketDir = Join-Path $objectsRoot $bucket
  if (-not (Test-Path $bucketDir)) {
    New-Item -ItemType Directory -Force -Path $bucketDir | Out-Null
  }
  $files = @(Get-ChildItem -Path $bucketDir -Recurse -File)
  if ($files.Count -eq 0) {
    $entries.Add([pscustomobject]@{
      bucket = $bucket
      object_path = $null
      size = 0
      sha256 = $null
      content_type = $null
      result = "EMPTY"
    }) | Out-Null
    continue
  }
  foreach ($f in $files) {
    $rel = $f.FullName.Substring($bucketDir.Length).TrimStart("\", "/")
    $objectPath = ($rel -replace "\\", "/")
    $hash = (Get-FileHash -Algorithm SHA256 -Path $f.FullName).Hash
    $entries.Add([pscustomobject]@{
      backup_id = $backupId
      timestamp = $started
      project_ref = $ProjectRef
      bucket = $bucket
      object_path = $objectPath
      size = $f.Length
      sha256 = $hash
      result = "COPIED"
    }) | Out-Null
  }
}

$copied = @($entries | Where-Object { $_.result -eq "COPIED" })
$manifest = [ordered]@{
  backup_id = $backupId
  timestamp = $started
  project_ref = $ProjectRef
  source_role = "read-only-storage-copy"
  buckets = $Buckets
  object_count = $copied.Count
  total_bytes = [int64](($copied | Measure-Object -Property size -Sum).Sum)
  objects = $entries
  notes = @(
    "Private directory. Do not commit.",
    "No tokens, JWTs, or signed URLs stored.",
    "Empty buckets recorded as EMPTY."
  )
}
$manifestPath = Join-Path $OutDir "manifest.json"
$manifest | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestPath -Encoding utf8
Set-Content -Path (Join-Path $OutDir "BACKUP_OUTSIDE_GIT.txt") -Value "OUT_DIR=$OutDir`nREPO=$repoRoot" -Encoding utf8
Write-Output "BACKUP_ID=$backupId"
Write-Output "BACKUP_DIR=$OutDir"
Write-Output "OBJECT_COUNT=$($copied.Count)"
Write-Output "TOTAL_BYTES=$($manifest.total_bytes)"
Write-Output "MANIFEST=$manifestPath"
