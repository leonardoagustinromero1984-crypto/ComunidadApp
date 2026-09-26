# Stop only LeoVer-QA. Does not kill adb or other emulators.
$ErrorActionPreference = "Stop"
$AvdName = "LeoVer-QA"
$Adb = "C:\Users\Supervielle\AppData\Local\Android\Sdk\platform-tools\adb.exe"

function Fail([string]$Message) { throw $Message }
if (-not (Test-Path $Adb)) { Fail "ADB_MISSING $Adb" }

$killed = 0
foreach ($line in @(& $Adb devices)) {
    if ($line -notmatch "^(emulator-\d+)\s+device$") { continue }
    $serial = $Matches[1]
    $raw = & $Adb -s $serial emu avd name 2>$null
    $name = (@($raw) | Where-Object { $_ -and $_ -notmatch "OK" -and $_ -notmatch "KO" } | Select-Object -First 1)
    if (([string]$name).Trim() -eq $AvdName) {
        Write-Host "STOPPING $AvdName serial=$serial"
        & $Adb -s $serial emu kill
        $killed++
    }
}
if ($killed -eq 0) {
    Write-Host "LEOVER_QA_NOT_RUNNING"
} else {
    Write-Host "STOPPED=$killed"
}
