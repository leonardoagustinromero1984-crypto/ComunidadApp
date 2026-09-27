# Start dedicated LeoVer-QA emulator. Does not touch other AVDs or kill adb.
param(
    [int]$BootTimeoutSec = 420
)

$ErrorActionPreference = "Stop"
$AvdName = "LeoVer-QA"
$Sdk = "C:\Users\Supervielle\AppData\Local\Android\Sdk"
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
$Emulator = Join-Path $Sdk "emulator\emulator.exe"
$AvdHome = Join-Path $env:USERPROFILE ".android\avd"
$JavaHome = "C:\Program Files\Android\Android Studio\jbr"
# QA Community Care synthetic pin: Obelisco / Microcentro, CABA
$QaLon = "-58.3808"
$QaLat = "-34.6031"

function Fail([string]$Message) { throw $Message }

function Get-EmulatorSerials {
    $out = @()
    foreach ($line in @(& $Adb devices)) {
        if ($line -match "^(emulator-\d+)\s+device$") {
            $out += $Matches[1]
        }
    }
    return $out
}

function Get-AvdNameForSerial([string]$Serial) {
    $raw = & $Adb -s $Serial emu avd name 2>$null
    $name = (@($raw) | Where-Object { $_ -and $_ -notmatch "OK" -and $_ -notmatch "KO" } | Select-Object -First 1)
    return ([string]$name).Trim()
}

function Find-LeoVerSerial {
    $hits = @()
    foreach ($serial in (Get-EmulatorSerials)) {
        $name = Get-AvdNameForSerial $serial
        if ($name -eq $AvdName) { $hits += $serial }
    }
    if ($hits.Count -gt 1) { Fail "QA_EMU_AMBIGUOUS serials=$($hits -join ',')" }
    if ($hits.Count -eq 1) { return $hits[0] }
    return $null
}

if (-not (Test-Path $Adb)) { Fail "ADB_MISSING $Adb" }
if (-not (Test-Path $Emulator)) { Fail "EMULATOR_MISSING $Emulator" }
if (-not (Test-Path (Join-Path $AvdHome "$AvdName.ini"))) { Fail "AVD_MISSING $AvdName" }
if (-not (Test-Path (Join-Path $JavaHome "bin\java.exe"))) { Fail "JAVA_HOME_MISSING $JavaHome" }

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:Path = "$(Join-Path $JavaHome 'bin');$(Join-Path $Sdk 'platform-tools');$(Join-Path $Sdk 'emulator');$env:Path"

$serial = Find-LeoVerSerial
if (-not $serial) {
    Write-Host "STARTING_AVD=$AvdName"
    $emuArgs = @(
        "-avd", $AvdName,
        "-netdelay", "none",
        "-netspeed", "full",
        "-gpu", "auto",
        "-no-snapshot-save"
    )
    Start-Process -FilePath $Emulator -ArgumentList $emuArgs -WindowStyle Normal | Out-Null
    $deadline = (Get-Date).AddSeconds($BootTimeoutSec)
    do {
        Start-Sleep -Seconds 3
        $serial = Find-LeoVerSerial
        if (-not $serial) {
            foreach ($line in @(& $Adb devices)) {
                if ($line -match "^(emulator-\d+)\s+(offline|unauthorized|device)$") {
                    Write-Host "WAITING_$($Matches[2].ToUpper())=$($Matches[1])"
                }
            }
        }
    } while (-not $serial -and (Get-Date) -lt $deadline)
    if (-not $serial) { Fail "QA_EMU_START_TIMEOUT after ${BootTimeoutSec}s" }
}

Write-Host "SERIAL=$serial"
& $Adb -s $serial wait-for-device
if ($LASTEXITCODE -ne 0) { Fail "QA_EMU_WAIT_FOR_DEVICE_FAILED $serial" }

$deadline = (Get-Date).AddSeconds($BootTimeoutSec)
$booted = $false
do {
    $prop = (& $Adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($prop -eq "1") { $booted = $true; break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if (-not $booted) { Fail "QA_EMU_BOOT_TIMEOUT sys.boot_completed!=1 serial=$serial" }

& $Adb -s $serial shell input keyevent 224 | Out-Null
& $Adb -s $serial shell wm dismiss-keyguard | Out-Null
& $Adb -s $serial shell input keyevent 82 | Out-Null
& $Adb -s $serial shell settings put global device_provisioned 1 | Out-Null
& $Adb -s $serial shell settings put secure user_setup_complete 1 | Out-Null
& $Adb -s $serial shell settings put global setup_wizard_has_run 1 | Out-Null
& $Adb -s $serial emu geo fix $QaLon $QaLat | Out-Null

$api = (& $Adb -s $serial shell getprop ro.build.version.sdk | Out-String).Trim()
$rel = (& $Adb -s $serial shell getprop ro.build.version.release | Out-String).Trim()
Write-Host "TARGET DEVICE: EMULATOR"
Write-Host "AVD: $AvdName"
Write-Host "SERIAL: $serial"
Write-Host "ANDROID VERSION: $rel (API $api)"
Write-Host "QA_GEO: lon=$QaLon lat=$QaLat"
Write-Host $serial
exit 0
