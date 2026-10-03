# LeoVer Automated QA (Maestro)

E2E Android on the dedicated **LeoVer-QA emulator** against STAGING `tobqbddfcyitwgbkthhy`.
Maestro is the on-demand E2E tool. Daily regression is Fast JVM tests (`run-regression.ps1`); it does **not** start the emulator.

When Maestro *is* requested, the target is LeoVer-QA. The runner never picks a physical phone unless a future explicit flag is added. Do not use a personal phone for automated QA.

No Web. No UI Foundation. No PROD. Password is never stored in tracked files.

## Environment

| Tool | Expected on this machine |
| --- | --- |
| Java | Android Studio JBR 21 at `C:\Program Files\Android\Android Studio\jbr` (PATH `java` may still be 8) |
| ANDROID_HOME | `%LOCALAPPDATA%\Android\Sdk` |
| ADB | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` |
| Emulator | `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe` |
| AVD | `LeoVer-QA` (Pixel 7 equivalent, x86_64, installed system image) |
| Device | Emulator serial `emulator-XXXX` resolved by AVD name, never first device |
| Maestro | Official CLI under `%LOCALAPPDATA%\maestro` |
| App | `com.comunidapp.app.staging` |
| APK | `apk/LeoVer-M08-Staging-debug.apk` |

The runner sets `JAVA_HOME` to the JBR so Maestro does not pick Java 8.

Installed system image on this machine (do not assume API 34/35 without auditing):

`system-images\android-37.1\google_apis_playstore_ps16k\x86_64`

`sdkmanager` / `avdmanager` / `cmdline-tools` are not installed. LeoVer-QA was created by writing AVD ini files that point at that image. `PlayStore.enabled=false` even though the only available image is a Play Store package. Personal AVD `Pixel_7_Pro` is not modified.

## Create LeoVer-QA

If `emulator -list-avds` does not include `LeoVer-QA`:

1. Confirm the image exists under `%LOCALAPPDATA%\Android\Sdk\system-images`.
2. Prefer an already-installed Google APIs x86_64 image (API 34/35 if present; otherwise the installed 37.1 image).
3. Do not install extra multi-GB images unless none are compatible.
4. Do not use `avdmanager` against `Pixel_7_Pro`.
5. Create a new AVD named exactly `LeoVer-QA` (Pixel-class, keyboard on, portrait, hardware GPU, ~3 GB RAM).

Files:

- `%USERPROFILE%\.android\avd\LeoVer-QA.ini`
- `%USERPROFILE%\.android\avd\LeoVer-QA.avd\config.ini`

## Start / stop

```powershell
.\scripts\qa\start-leover-emulator.ps1
.\scripts\qa\stop-leover-emulator.ps1
```

Start:

1. Reuses LeoVer-QA if it is already running.
2. Otherwise launches `emulator.exe -avd LeoVer-QA`.
3. Resolves serial by `adb emu avd name` == `LeoVer-QA`.
4. `adb wait-for-device` and `sys.boot_completed=1`.
5. Wakes / unlocks the screen.
6. Prints TARGET / AVD / SERIAL / ANDROID VERSION and echoes the serial.

Stop kills only that serial (`adb -s emulator-XXXX emu kill`). It does not `adb kill-server` and does not close other emulators.

Timeout: 300 seconds. Fail-closed if the serial is missing or ambiguous.

## Runner

From repo root, only when you explicitly want E2E. Daily default is `.\scripts\qa\run-regression.ps1` (no emulator).

`-UseEmulator` defaults to `$true` **inside this Maestro runner**. If LeoVer-QA is not running, *this* script starts it. `run-regression.ps1` will not call it unless `-Maestro`, `-SmokeE2E`, or `-FullE2E` is passed. After those flags it stops the AVD unless `-KeepEmulator`.

```powershell
# Smoke QA01 / QA02 / QA03 (default suite)
.\scripts\qa\run-maestro-e2e.ps1 -Smoke

# Same, plus reinstall APK on the emulator serial
.\scripts\qa\run-maestro-e2e.ps1 -Smoke -UseEmulator -InstallApk

# Navigation smoke only. Does not publish Mora, claim the IN_CARE FOUND,
# apply to Luna, or recreate Bruno. See docs/qa/LEOVER-MAESTRO-STATUS.md.
.\scripts\qa\run-maestro-e2e.ps1 -CommunityCare

# Everything
.\scripts\qa\run-maestro-e2e.ps1 -Full
```

Stop after the run (optional):

```powershell
.\scripts\qa\run-maestro-e2e.ps1 -Smoke -StopEmulatorAfter
```

`-ResetEmulator` is reserved (fail-closed). Do not wipe the AVD on every run. For business-data reproducibility use `reset-staging.ps1` + `seed-community-care-actors.ps1`.

`-ResetAndSeed` is **opt-in** and fail-closed:

1. Confirms linked project is `tobqbddfcyitwgbkthhy`.
2. `reset-staging.ps1 -Execute`
3. `seed-community-care-actors.ps1`
4. `adb -s <LeoVer-QA serial> install -r` of the staging APK
5. Runs Maestro against that serial (`maestro test --device <serial>`)
6. Writes `artifacts/qa/maestro/<timestamp>/report.md`

Without `-ResetAndSeed` the runner does not wipe STAGING.

Device selection is fail-closed: list `adb devices`, keep only the emulator whose AVD name is `LeoVer-QA`. Never take the first device. Physical phones are ignored.

## First run order

1. Environment / AVD
2. Start LeoVer-QA
3. Smoke QA01 → QA02 → QA03
4. If smoke PASS: lost/found navigation, notification list, and the other runnable Community Care reads
5. Do not run flows tagged `needs-fresh-fixture`, `manual`, or `physical-two-device`

The runner skips those tags for `-CommunityCare` and `-Full`. Mora, the responder FOUND, Luna, and Bruno are completed fixtures. Publishing or claiming them is not part of this suite. Status: `docs/qa/LEOVER-MAESTRO-STATUS.md`.

Stop the suite if a P0 smoke or P0 Community Care flow fails. Do not run `-Full` until those pass.

## Location QA

Synthetic pin near the STAGING Community Care dataset (Obelisco / Microcentro, CABA):

- Longitude: `-58.3808`
- Latitude: `-34.6031`

Applied with `adb -s <serial> emu geo fix -58.3808 -34.6031` (emulator order is lon then lat). Tests can use the mock location, open the map, drop a pin, and run nearby search. Exact real-world GPS remains `MANUAL_REQUIRED`. Do not use the phone GPS.

## Photo fixture

Product still requires a photo. Do not remove that rule.

1. Source: `scripts/qa/fixtures/pet-dog.jpg` (copy of `.maestro/fixtures/qa-pet.jpg`).
2. `adb -s <serial> push` → `/sdcard/Pictures/LeoVerQA/pet-dog.jpg`
3. Media scan so Gallery / Photo Picker can see it.
4. Maestro `addMedia` also injects `.maestro/fixtures/qa-pet.jpg`.

Do not use the physical camera. Real camera capture stays `MANUAL_REQUIRED`.

## QR / Maps / network

- QR generation, display, and share screen are automatable (`05c_qr_mora.yaml`). Physical scan is `MANUAL_REQUIRED`.
- Maps: the runner checks that `com.google.android.gms` is present. Do not change Maps API keys. Existing key restrictions stay as they are. Do not print keys.
- E2E uses real STAGING (`tobqbddfcyitwgbkthhy`). No mock backend for the main suite.

## QA ADB permissions

After `-InstallApk` the runner may grant (emulator only, package `com.comunidapp.app.staging`):

- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.READ_MEDIA_IMAGES`
- `android.permission.READ_EXTERNAL_STORAGE`

These are QA grants on the emulator. They do not change product permission UX. For permission-UX flows, revoke first, then exercise the dialog. Do not change production permission logic.

## Emulator prep (before Maestro)

- Screen on and unlocked
- Animations scaled to 0
- Portrait lock
- Mock geo as above
- Timezone attempt: `America/Argentina/Buenos_Aires`
- Photo fixture pushed
- Network ping to 8.8.8.8 and STAGING host

No product-code changes to achieve this.

## Credentials

Flows read `QA_USERNAME` / `QA_PASSWORD` (and `QA01_USERNAME` …) from the environment.

The runner loads the shared password from gitignored:

`scripts/qa/.qa-community-care-actors.local.json`

Create it by running `scripts/qa/seed-community-care-actors.ps1`.
Never put the password in YAML.

Usernames (login is username + password):

| QA | Username |
| --- | --- |
| QA01 | qa01owner |
| QA02 | qa02finder |
| QA03 | qa03rescuer |
| QA04 | qa04rescuer2 |
| QA05 | qa05unavailable |
| QA06 | qa06foster |
| QA07 | qa07shelter |
| QA08 | qa08pending |
| QA09 | qa09noreq |

QA09 (`qa-cc-shelter-noreq`) was intentionally moved in STAGING during P0 validation from `NOT_REQUESTED` to `PENDING`. That live row is the current canonical QA fixture state. Do not restore `NOT_REQUESTED` by hand and do not create another verification request. This note does not re-seed STAGING.
| QA10 | qa10vet |
| QA11 | qa11proa |
| QA12 | qa12proind |
| QA13 | qa13shop |
| QA14 | qa14adopter |
| QA15 | qa15adopter2 |
| QA16 | qa16vetnorte |

## Structure

```
.maestro/
  config.yaml
  config/env.example
  fixtures/          # synthetic photo for addMedia
  flows/
  subflows/
  screenshots/       # gitignored runtime dumps
scripts/qa/run-maestro-e2e.ps1
scripts/qa/start-leover-emulator.ps1
scripts/qa/stop-leover-emulator.ps1
scripts/qa/fixtures/pet-dog.jpg
artifacts/qa/maestro/<timestamp>/   # gitignored
```

Reusable subflows: `login-person` (clears app state), `login-person-keep` (keeps data after logout), `logout-person`, `open-sumate`, `open-community`, `open-notifications`, `open-lost-found`, `return-home`, `close-dialogs`, `switch-profile`.

Maestro 2.10.0 requires `launchApp.clearState` to be a boolean, not an env string. That is why there are two login subflows.

## Cursor MCP

User config: `%USERPROFILE%\.cursor\mcp.json` (not the repo).

```json
{
  "mcpServers": {
    "maestro": {
      "command": "C:\\Users\\Supervielle\\AppData\\Local\\maestro\\maestro\\bin\\maestro.bat",
      "args": ["mcp"],
      "env": {
        "JAVA_HOME": "C:\\Program Files\\Android\\Android Studio\\jbr",
        "ANDROID_HOME": "C:\\Users\\Supervielle\\AppData\\Local\\Android\\Sdk"
      }
    }
  }
}
```

Reload Cursor → Tools & MCPs. `list_devices` should see `emulator-XXXX` for LeoVer-QA.

## Install Maestro (Windows native)

Official zip only: [Maestro releases](https://github.com/mobile-dev-inc/Maestro/releases).

1. Download `maestro.zip`.
2. Verify SHA-256 against `checksums_sha256.txt` from the same release.
3. Extract to `%LOCALAPPDATA%\maestro`.
4. Add `%LOCALAPPDATA%\maestro\maestro\bin` to the **user** PATH.
5. Restart the terminal.
6. `maestro --version` and `maestro --help`.

Do not use unofficial binaries. Prefer native Windows over WSL.

## Manual required

- Camera capture
- Exact GPS / map pin
- OS push notification shade
- Physical QR scan
- External email inbox (`09c_vet_patient_email.yaml`)

A table or RPC row is not an E2E PASS. PASS means a visible Android result.

## Troubleshooting (emulator)

| Symptom | Check |
| --- | --- |
| `QA_EMU_SERIAL_NOT_FOUND` | `.\scripts\qa\start-leover-emulator.ps1` then `adb devices` |
| `QA_EMU_SERIAL_AMBIGUOUS` | Two LeoVer-QA processes; stop extras with `stop-leover-emulator.ps1` |
| Runner picked the phone | It must not. Confirm no `-UsePhysicalDevice`. Serial must be `emulator-XXXX` |
| First boot timeout | Cold start of API 37.1 can take several minutes. Re-run start script |
| Login timeout / blank screen | First process on this image spends a long time in ART verification. Login wait is 90s. Re-run smoke once the app has launched once |
| ICMP ping FAIL | Expected on this image. Runner uses `dumpsys connectivity` VALIDATED + STAGING DNS |
| Setup wizard blocks UI | Start script marks device provisioned; dismiss wizard once if needed |
| Login strings not found | Flows are Spanish (`Correo o usuario`). Finish emulator first-run language if still English |
| `maestro` not found | User PATH + new terminal; runner uses the full `.bat` path |
| Maestro fails on Java 8 | `JAVA_HOME` = Android Studio JBR |
| `adb` not found | Use the SDK `platform-tools` path above |
| Login fails | Seed actors; username not email |
| Wrong project | `infra/supabase-canonical/supabase/.temp/project-ref` must be STAGING |
| Maps blank | Do not rotate API keys. Confirm Play services on the image and network |
| Photo picker empty | Re-push `scripts/qa/fixtures/pet-dog.jpg` to `Pictures/LeoVerQA/` |
| Want a clean Android | Do not wipe by default. `-ResetEmulator` is reserved; reset STAGING + seed instead |

## Product code

This block does not change Android business rules. No migrations. No Web. UI Foundation is not started.
