# LeoVer SEC-04D — verify a private Storage backup against its manifest.
# No credentials. No signed URLs.
param(
  [Parameter(Mandatory = $true)]
  [string]$BackupDir
)

$ErrorActionPreference = "Stop"
$BackupDir = [System.IO.Path]::GetFullPath($BackupDir)
$manifestPath = Join-Path $BackupDir "manifest.json"
if (-not (Test-Path $manifestPath)) { throw "Missing manifest.json in $BackupDir" }
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
$ok = 0
$fail = 0
$empty = 0
foreach ($obj in $manifest.objects) {
  if ($obj.result -eq "EMPTY") {
    $empty++
    Write-Output "EMPTY bucket=$($obj.bucket)"
    continue
  }
  $local = Join-Path (Join-Path $BackupDir "objects\$($obj.bucket)") ($obj.object_path -replace "/", "\")
  if (-not (Test-Path $local)) {
    Write-Output "FAIL missing path=$($obj.bucket)/$($obj.object_path)"
    $fail++
    continue
  }
  $fi = Get-Item $local
  $hash = (Get-FileHash -Algorithm SHA256 -Path $local).Hash
  if ($fi.Length -ne [int64]$obj.size -or $hash -ne $obj.sha256) {
    Write-Output "FAIL mismatch path=$($obj.bucket)/$($obj.object_path) size=$($fi.Length) expected=$($obj.size)"
    $fail++
    continue
  }
  Write-Output "OK bucket=$($obj.bucket) size=$($obj.size)"
  $ok++
}
Write-Output "VERIFY_OK=$ok"
Write-Output "VERIFY_FAIL=$fail"
Write-Output "VERIFY_EMPTY_BUCKETS=$empty"
if ($fail -gt 0) { exit 1 }
