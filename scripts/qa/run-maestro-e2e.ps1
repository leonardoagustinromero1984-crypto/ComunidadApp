# LeoVer Maestro E2E runner. STAGING only. Default target: LeoVer-QA emulator.
param(
    [switch]$Smoke,
    [switch]$CommunityCare,
    [switch]$Full,
    [switch]$ResetAndSeed,
    [switch]$InstallApk,
    [switch]$UninstallFirst,
    [bool]$UseEmulator = $true,
    [bool]$StartEmulator = $true,
    [switch]$StopEmulatorAfter,
    [switch]$ResetEmulator,
    [switch]$UsePhysicalDevice
)

$ErrorActionPreference = "Stop"
$ExpectedRef = "tobqbddfcyitwgbkthhy"
$LegacyRef = "wystsapjfpdtoprlmizz"
$AppId = "com.comunidapp.app.staging"
$ApkRel = "apk\LeoVer-M08-Staging-debug.apk"
$AvdName = "LeoVer-QA"
$QaLon = "-58.3808"
$QaLat = "-34.6031"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Workdir = Join-Path $RepoRoot "infra\supabase-canonical"
$FlowsDir = Join-Path $RepoRoot ".maestro\flows"
$LocalSecret = Join-Path $PSScriptRoot ".qa-community-care-actors.local.json"
$PhotoSrc = Join-Path $RepoRoot ".maestro\fixtures\qa-pet.jpg"
$PhotoDst = Join-Path $PSScriptRoot "fixtures\pet-dog.jpg"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ArtifactDir = Join-Path $RepoRoot "artifacts\qa\maestro\$Stamp"
$ReportPath = Join-Path $ArtifactDir "report.md"

$JavaHome = "C:\Program Files\Android\Android Studio\jbr"
$Adb = "C:\Users\Supervielle\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$AndroidHome = "C:\Users\Supervielle\AppData\Local\Android\Sdk"

function Fail([string]$Message) { throw $Message }

function Resolve-MaestroBat {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "maestro\maestro\bin\maestro.bat"),
        (Join-Path $env:LOCALAPPDATA "maestro\bin\maestro.bat")
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    $fromPath = Get-Command maestro -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    Fail "MAESTRO_NOT_FOUND"
}

function Assert-StagingOnly {
    $refFile = Join-Path $Workdir "supabase\.temp\project-ref"
    if (-not (Test-Path $refFile)) { Fail "QA_E2E_ABORT_UNLINKED" }
    $linked = (Get-Content $refFile -Raw).Trim()
    if ($linked -eq $LegacyRef) { Fail "QA_E2E_ABORT_LEGACY_PROJECT" }
    if ($linked -ne $ExpectedRef) { Fail "QA_E2E_ABORT_UNKNOWN_PROJECT:$linked" }
    Write-Host "STAGING_ONLY_GUARD=PASS ref=$ExpectedRef"
}

function Get-QaPassword {
    if (-not [string]::IsNullOrWhiteSpace($env:LEOVER_QA_PASSWORD)) {
        return [string]$env:LEOVER_QA_PASSWORD
    }
    if (-not (Test-Path $LocalSecret)) {
        Fail "QA_PASSWORD_MISSING: $LocalSecret"
    }
    $doc = Get-Content $LocalSecret -Raw | ConvertFrom-Json
    $pwd = [string]$doc.password
    if ([string]::IsNullOrWhiteSpace($pwd)) { Fail "QA_PASSWORD_EMPTY" }
    return $pwd
}

function Ensure-Tools {
    if (-not (Test-Path (Join-Path $JavaHome "bin\java.exe"))) {
        Fail "JAVA_17_PLUS_MISSING $JavaHome"
    }
    if (-not (Test-Path $Adb)) { Fail "ADB_MISSING $Adb" }
    $script:MaestroBat = Resolve-MaestroBat
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $AndroidHome
    $env:ANDROID_SDK_ROOT = $AndroidHome
    $env:MAESTRO_CLI_NO_ANALYTICS = "true"
    $env:MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED = "true"
    $env:Path = "$(Join-Path $JavaHome 'bin');$(Split-Path $Adb);$(Split-Path $script:MaestroBat);$env:Path"
}

function Get-AvdNameForSerial([string]$Serial) {
    $raw = & $Adb -s $Serial emu avd name 2>$null
    $name = (@($raw) | Where-Object { $_ -and $_ -notmatch "OK" -and $_ -notmatch "KO" } | Select-Object -First 1)
    return ([string]$name).Trim()
}

function Resolve-LeoVerSerial {
    if ($UsePhysicalDevice) {
        Fail "QA_E2E_PHYSICAL_DEVICE_DISABLED default is LeoVer-QA emulator"
    }
    if (-not $UseEmulator) {
        Fail "QA_E2E_EMULATOR_REQUIRED pass -UseEmulator:`$true"
    }
    if ($ResetEmulator) {
        Fail "QA_E2E_RESET_EMULATOR_RESERVED use reset-staging + seed for business data"
    }
    if ($StartEmulator) {
        & (Join-Path $PSScriptRoot "start-leover-emulator.ps1")
        if ($LASTEXITCODE -ne 0) { Fail "QA_EMU_START_FAILED" }
    }
    $hits = @()
    foreach ($line in @(& $Adb devices)) {
        if ($line -notmatch "^(emulator-\d+)\s+device$") { continue }
        $serial = $Matches[1]
        if ((Get-AvdNameForSerial $serial) -eq $AvdName) { $hits += $serial }
    }
    if ($hits.Count -eq 0) { Fail "QA_EMU_SERIAL_NOT_FOUND AVD=$AvdName" }
    if ($hits.Count -gt 1) { Fail "QA_EMU_SERIAL_AMBIGUOUS $($hits -join ',')" }
    return $hits[0]
}

function Prepare-Emulator([string]$Serial) {
    & $Adb -s $Serial shell settings put global window_animation_scale 0 | Out-Null
    & $Adb -s $Serial shell settings put global transition_animation_scale 0 | Out-Null
    & $Adb -s $Serial shell settings put global animator_duration_scale 0 | Out-Null
    & $Adb -s $Serial shell settings put system accelerometer_rotation 0 | Out-Null
    & $Adb -s $Serial shell settings put system user_rotation 0 | Out-Null
    & $Adb -s $Serial shell settings put global device_provisioned 1 | Out-Null
    & $Adb -s $Serial shell settings put secure user_setup_complete 1 | Out-Null
    & $Adb -s $Serial shell settings put global setup_wizard_has_run 1 | Out-Null
    & $Adb -s $Serial emu geo fix $QaLon $QaLat | Out-Null
    $script:MockLocationOk = $LASTEXITCODE -eq 0
    & $Adb -s $Serial shell service call alarm 3 s16 America/Argentina/Buenos_Aires 2>$null | Out-Null
    & $Adb -s $Serial shell cmd locale set-device-locale es-AR 2>$null | Out-Null
    $net = & $Adb -s $Serial shell dumpsys connectivity 2>$null | Out-String
    $script:NetworkOk = $net -match "VALIDATED" -and $net -match "INTERNET"
    $stgResolve = & $Adb -s $Serial shell ping -c 1 -W 2 tobqbddfcyitwgbkthhy.supabase.co 2>$null | Out-String
    $script:StagingOk = $stgResolve -match "PING tobqbddfcyitwgbkthhy.supabase.co"
    $maps = & $Adb -s $Serial shell pm path com.google.android.gms 2>$null | Out-String
    $script:MapsOk = $maps -match "package:"
    Write-Host "WARMING_MAESTRO_DRIVER serial=$Serial"
    $warmLog = Join-Path $env:TEMP "leover-maestro-hierarchy.out"
    $warmErr = Join-Path $env:TEMP "leover-maestro-hierarchy.err"
    $warm = Start-Process -FilePath $script:MaestroBat -ArgumentList @("--device", $Serial, "hierarchy") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $warmLog -RedirectStandardError $warmErr
    if ($warm.ExitCode -ne 0) {
        Write-Host "MAESTRO_DRIVER_WARM_WARN exit=$($warm.ExitCode)"
    } else {
        Write-Host "MAESTRO_DRIVER_WARM=OK"
    }
}

function Install-QaApk([string]$Serial) {
    $apk = Join-Path $RepoRoot $ApkRel
    if (-not (Test-Path $apk)) { Fail "APK_MISSING $apk" }
    if ($UninstallFirst) {
        & $Adb -s $Serial uninstall $AppId | Out-Null
    }
    & $Adb -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { Fail "APK_INSTALL_FAILED" }
    $perms = @(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_EXTERNAL_STORAGE"
    )
    foreach ($perm in $perms) {
        & $Adb -s $Serial shell pm grant $AppId $perm 2>$null | Out-Null
    }
    Write-Host "APK_INSTALL=OK serial=$Serial"
    Write-Host "QA_PERMISSIONS=$($perms -join ',')"
}

function Push-PhotoFixture([string]$Serial) {
    New-Item -ItemType Directory -Path (Split-Path $PhotoDst) -Force | Out-Null
    if (Test-Path $PhotoSrc) { Copy-Item $PhotoSrc $PhotoDst -Force }
    if (-not (Test-Path $PhotoDst)) { Fail "PHOTO_FIXTURE_MISSING $PhotoDst" }
    & $Adb -s $Serial shell mkdir -p /sdcard/Pictures/LeoVerQA | Out-Null
    & $Adb -s $Serial push $PhotoDst /sdcard/Pictures/LeoVerQA/pet-dog.jpg
    $script:PhotoOk = $LASTEXITCODE -eq 0
    & $Adb -s $Serial shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/LeoVerQA/pet-dog.jpg | Out-Null
}

function Get-FlowList {
    if ($Full) {
        return @(Get-ChildItem (Join-Path $FlowsDir "*.yaml") | Sort-Object Name | ForEach-Object { $_.FullName })
    }
    if ($CommunityCare) {
        return @(
            (Join-Path $FlowsDir "01_lost_owner.yaml"),
            (Join-Path $FlowsDir "02_found_finder.yaml"),
            (Join-Path $FlowsDir "03_match_owner_notification.yaml")
        ) + @(Get-ChildItem (Join-Path $FlowsDir "*.yaml") |
            Where-Object { $_.Name -notlike "00_smoke*" -and $_.Name -notlike "01_lost*" -and $_.Name -notlike "02_found*" -and $_.Name -notlike "03_match*" } |
            Sort-Object Name | ForEach-Object { $_.FullName })
    }
    return @(
        (Join-Path $FlowsDir "00_smoke_login.yaml"),
        (Join-Path $FlowsDir "00_smoke_qa02.yaml"),
        (Join-Path $FlowsDir "00_smoke_qa03.yaml")
    )
}

function Invoke-MaestroFlow([string]$Flow, [string]$Password, [string]$OutDir, [string]$Serial) {
    $name = [IO.Path]::GetFileNameWithoutExtension($Flow)
    $log = Join-Path $OutDir "$name.log"
    $args = @(
        "test", $Flow,
        "--device", $Serial,
        "--debug-output", $OutDir,
        "-e", "APP_ID=$AppId",
        "-e", "QA_PASSWORD=$Password",
        "-e", "QA01_USERNAME=qa01owner",
        "-e", "QA02_USERNAME=qa02finder",
        "-e", "QA03_USERNAME=qa03rescuer",
        "-e", "QA04_USERNAME=qa04rescuer2",
        "-e", "QA05_USERNAME=qa05unavailable",
        "-e", "QA06_USERNAME=qa06foster",
        "-e", "QA07_USERNAME=qa07shelter",
        "-e", "QA08_USERNAME=qa08pending",
        "-e", "QA09_USERNAME=qa09noreq",
        "-e", "QA10_USERNAME=qa10vet",
        "-e", "QA11_USERNAME=qa11proa",
        "-e", "QA12_USERNAME=qa12proind",
        "-e", "QA13_USERNAME=qa13shop",
        "-e", "QA14_USERNAME=qa14adopter",
        "-e", "QA15_USERNAME=qa15adopter2",
        "-e", "QA16_USERNAME=qa16vetnorte"
    )
    $p = Start-Process -FilePath $script:MaestroBat -ArgumentList $args -Wait -PassThru -NoNewWindow -RedirectStandardOutput $log -RedirectStandardError (Join-Path $OutDir "$name.err.log")
    return @{ Name = $name; Code = $p.ExitCode; Log = $log }
}

New-Item -ItemType Directory -Path $ArtifactDir -Force | Out-Null
Assert-StagingOnly
Ensure-Tools
$password = Get-QaPassword
$serial = Resolve-LeoVerSerial
$api = (& $Adb -s $serial shell getprop ro.build.version.sdk | Out-String).Trim()
$rel = (& $Adb -s $serial shell getprop ro.build.version.release | Out-String).Trim()
Write-Host "TARGET DEVICE: EMULATOR"
Write-Host "AVD: $AvdName"
Write-Host "SERIAL: $serial"
Write-Host "ANDROID VERSION: $rel (API $api)"

Prepare-Emulator $serial

if ($ResetAndSeed) {
    Write-Host "RESET_AND_SEED requested for $ExpectedRef"
    & (Join-Path $PSScriptRoot "reset-staging.ps1") -Execute
    if ($LASTEXITCODE -ne 0) { Fail "RESET_STAGING_FAILED" }
    & (Join-Path $PSScriptRoot "seed-community-care-actors.ps1")
    if ($LASTEXITCODE -ne 0) { Fail "SEED_FAILED" }
    $password = Get-QaPassword
}

if ($InstallApk -or $ResetAndSeed) {
    Install-QaApk $serial
}
Push-PhotoFixture $serial

$flows = Get-FlowList
$results = @()
$stop = $false
foreach ($flow in $flows) {
    if ($stop) {
        $results += [pscustomobject]@{ Name = [IO.Path]::GetFileNameWithoutExtension($flow); Status = "SKIPPED"; Detail = "stopped after P0 fail" }
        continue
    }
    Write-Host "RUN $flow"
    $r = Invoke-MaestroFlow $flow $password $ArtifactDir $serial
    $status = if ($r.Code -eq 0) { "PASS" } else { "FAIL" }
    $results += [pscustomobject]@{ Name = $r.Name; Status = $status; Detail = $r.Log }
    Write-Host "$($r.Name)=$status"
    if ($status -eq "FAIL" -and ($r.Name -like "00_smoke*" -or $r.Name -in @("01_lost_owner", "02_found_finder", "03_match_owner_notification"))) {
        $stop = $true
        Write-Host "P0_FAILED stop suite"
    }
}

if ($StopEmulatorAfter) {
    & (Join-Path $PSScriptRoot "stop-leover-emulator.ps1")
}

$lines = @(
    "# LeoVer Maestro report $Stamp",
    "",
    "TARGET: EMULATOR",
    "AVD: $AvdName",
    "SERIAL: $serial",
    "API: $api",
    "ANDROID: $rel",
    "APK: $ApkRel",
    "STAGING: $ExpectedRef",
    "QA_GEO: lon=$QaLon lat=$QaLat",
    "NETWORK: $(if ($script:NetworkOk) { 'PASS' } else { 'FAIL' })",
    "STAGING_PING: $(if ($script:StagingOk) { 'PASS' } else { 'FAIL' })",
    "MOCK_LOCATION: $(if ($script:MockLocationOk) { 'PASS' } else { 'FAIL' })",
    "PHOTO_FIXTURE: $(if ($script:PhotoOk) { 'PASS' } else { 'FAIL' })",
    "PHONE PHYSICAL USED: NO",
    "RESET_AND_SEED: $(if ($ResetAndSeed) { 'YES' } else { 'NO' })",
    "",
    "| Flow | Status |",
    "| --- | --- |"
)
foreach ($row in $results) {
    $lines += "| $($row.Name) | $($row.Status) |"
}
$lines += ""
$lines += "Artifacts: $ArtifactDir"
$lines | Set-Content $ReportPath -Encoding utf8
Write-Host "REPORT=$ReportPath"

$failed = @($results | Where-Object { $_.Status -eq "FAIL" })
if ($failed.Count -gt 0) {
    Fail "QA_E2E_FAILED $($failed.Name -join ',')"
}
Write-Host "QA_E2E=PASS"
