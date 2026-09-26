# LeoVer SEC-04D — restore selected objects from a private backup to an isolated project.
# Never restores onto STAGING. Never flips private buckets to public.
# No credentials in this file. Uses an existing Supabase CLI login.
# Env: LEOVER_RESTORE_PROJECT_REF, LEOVER_SUPABASE_WORKDIR
param(
  [Parameter(Mandatory = $true)]
  [string]$BackupDir,
  [string]$DestProjectRef = $(if ($env:LEOVER_RESTORE_PROJECT_REF) { $env:LEOVER_RESTORE_PROJECT_REF } else { "wystsapjfpdtoprlmizz" }),
  [string]$Workdir = $(if ($env:LEOVER_SUPABASE_WORKDIR) { $env:LEOVER_SUPABASE_WORKDIR } else { (Join-Path $PSScriptRoot "..\..\infra\supabase-canonical") }),
  [string]$DestPrefix = "sec04d-probe",
  [int]$MaxObjects = 2,
  [string]$StagingRef = "tobqbddfcyitwgbkthhy"
)

$ErrorActionPreference = "Stop"
if ($DestProjectRef -eq $StagingRef) {
  throw "Refusing to restore Storage onto STAGING ($StagingRef)."
}
$Workdir = (Resolve-Path $Workdir).Path
$BackupDir = [System.IO.Path]::GetFullPath($BackupDir)
$manifestPath = Join-Path $BackupDir "manifest.json"
if (-not (Test-Path $manifestPath)) { throw "Missing manifest.json" }
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
$candidates = @($manifest.objects | Where-Object { $_.result -eq "COPIED" } | Sort-Object size)
if ($candidates.Count -eq 0) { throw "Backup has no copied objects" }
$sample = @($candidates | Select-Object -First $MaxObjects)
$results = New-Object System.Collections.Generic.List[object]

foreach ($obj in $sample) {
  $local = Join-Path (Join-Path $BackupDir "objects\$($obj.bucket)") ($obj.object_path -replace "/", "\")
  if (-not (Test-Path $local)) { throw "Missing local object $($obj.object_path)" }
  $leaf = Split-Path $obj.object_path -Leaf
  $remote = "ss:///$($obj.bucket)/$DestPrefix/$leaf"
  $parent = Join-Path $env:TEMP ("leover-sec04d-put-" + [guid]::NewGuid().ToString("N"))
  $stage = Join-Path $parent $DestPrefix
  New-Item -ItemType Directory -Force -Path $stage | Out-Null
  Copy-Item $local (Join-Path $stage $leaf)
  Write-Output "RESTORE_PUT $remote"
  Push-Location $parent
  try {
    # CLI 2.x rejects absolute Windows sources. Copy the prefix folder from cwd.
    npx --yes supabase --experimental --workdir $Workdir storage cp -r $DestPrefix "ss:///$($obj.bucket)/" --project-ref $DestProjectRef
    if ($LASTEXITCODE -ne 0) { throw "storage cp upload failed for $($obj.object_path)" }
  } finally {
    Pop-Location
    Remove-Item -Recurse -Force $parent -ErrorAction SilentlyContinue
  }

  $verifyDir = Join-Path $env:TEMP ("leover-sec04d-restore-" + [guid]::NewGuid().ToString("N"))
  New-Item -ItemType Directory -Force -Path $verifyDir | Out-Null
  try {
    Push-Location $verifyDir
    npx --yes supabase --experimental --workdir $Workdir storage cp -r $remote . --project-ref $DestProjectRef
    if ($LASTEXITCODE -ne 0) { throw "storage cp download-verify failed" }
    $downloaded = Get-ChildItem -File | Select-Object -First 1
    if (-not $downloaded) { throw "No file downloaded for verify" }
    $hash = (Get-FileHash -Algorithm SHA256 -Path $downloaded.FullName).Hash
    $match = ($downloaded.Length -eq [int64]$obj.size) -and ($hash -eq $obj.sha256)
    $results.Add([pscustomobject]@{
      bucket = $obj.bucket
      source_path = $obj.object_path
      dest_path = "$DestPrefix/$leaf"
      size = $obj.size
      sha256_match = $match
      result = $(if ($match) { "RESTORED_VERIFIED" } else { "HASH_MISMATCH" })
    }) | Out-Null
    Write-Output "RESTORE_VERIFY match=$match size=$($downloaded.Length)"
  } finally {
    Pop-Location
    Remove-Item -Recurse -Force $verifyDir -ErrorAction SilentlyContinue
  }
}

$out = Join-Path $BackupDir "restore-sample.json"
[pscustomobject]@{
  dest_project_ref = $DestProjectRef
  dest_prefix = $DestPrefix
  staging_untouched = $true
  sample = $results
} | ConvertTo-Json -Depth 5 | Set-Content -Path $out -Encoding utf8
Write-Output "RESTORE_REPORT=$out"
$bad = @($results | Where-Object { $_.result -ne "RESTORED_VERIFIED" })
if ($bad.Count -gt 0) { exit 1 }
