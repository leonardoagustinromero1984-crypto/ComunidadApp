# Cursor Cloud setup (LeoVer)

Snapshot branch for Cloud work. This is not a release and not a production deploy.

Local PCs keep LeoVer-QA **paused**. Cloud must not start an Android emulator unless a later block explicitly asks.

## Cursor Environment Install Script

```bash
bash scripts/cloud/bootstrap-cloud.sh
```

The script is idempotent. It checks JDK 21, sets `ANDROID_HOME`, installs command-line SDK packages only when they are missing (`platforms;android-36`, `platforms;android-36.1`, `build-tools;36.1.0`, `platform-tools`), and marks `gradlew` executable. It does not install an emulator, system images, Docker, or production secrets.

## What Cloud should run

Fast / Affected only:

1. Linux runner: `bash scripts/qa/run-regression.sh` (default `--fast`) or `bash scripts/qa/run-regression.sh --affected`.
2. JVM unit + contract + ViewModel: `./gradlew :app:testLocalDebugUnitTest --no-configuration-cache --no-daemon`
3. Shared JVM host tests: `./gradlew :shared:testAndroidHostTest --no-configuration-cache --no-daemon`
4. Static SQL/RPC/RLS **contract** tests that live under `app/src/test` (they read migration files; they do not apply them).

PowerShell is not required for this Cloud regression. `scripts/qa/run-regression.ps1` remains the Windows runner.

Do **not** by default:

- start `emulator`
- run Maestro
- run `connectedAndroidTest`
- apply migrations to PROD
- declare physical QA PASS

## Linux / Ubuntu requirements

| Need | Notes |
| --- | --- |
| JDK 17+ | Prefer Temurin 21. Repo Android Studio JBR path is Windows-only. Set `JAVA_HOME` to a Linux JDK 21. |
| Android SDK command-line | `ANDROID_HOME`, `platform-tools`, `platforms;android-36` (or the `compileSdk` in `app/build.gradle.kts`). Emulator images are **not** required for Fast. |
| Gradle wrapper | `./gradlew` — do not install a global Gradle as the source of truth. |
| Git | Work on `cloud-handoff/leover-20260925` or a child branch. Never `main`. |
| PowerShell (optional) | The QA runners are `.ps1`. On Ubuntu use `pwsh` if present, or invoke Gradle directly. |
| Supabase CLI | Only if running live STAGING SQL probes. Not required for Fast JVM. |

Suggested first Cloud command after the install script:

```bash
bash scripts/qa/run-regression.sh
```

That runs `:app:testLocalDebugUnitTest` and `:shared:testAndroidHostTest` with `--no-configuration-cache --no-daemon`. Flavor is `localDebug`. Configuration cache stays enabled in `gradle.properties` for the product.

Use `docs/qa/change-impact-map.yaml` to pick `--tests` from `git diff --name-only`.

## Windows-only issues (do not port blindly)

- `scripts/qa/*.ps1` assume Windows paths (`C:\Program Files\Android\Android Studio\jbr`, `%LOCALAPPDATA%\Android\Sdk`, `gradlew.bat`).
- Maestro install and `maestro.bat` are Windows-native in this snapshot.
- `start-leover-emulator.ps1` / AVD `LeoVer-QA` live on the Windows host and stay paused.
- First-boot API 37.1 16KB Play image + Maestro driver timeout + software keyboard covering `Contraseña` are **local emulator bugs**. Do not debug them in Cloud.
- `cmd /c` wrappers in `check-regression-coverage.ps1` are for PowerShell treating `git` CRLF warnings as errors.

## Secrets on Cloud

Cloud must receive secrets via the Cloud secret store, never via Git:

- `LEOVER_QA_PASSWORD` if seeding STAGING actors
- Supabase access only through CLI login / env, never committed `service_role`
- No `local.properties`, no `scripts/qa/.qa-community-care-actors.local.json`

## STAGING

Linked project for QA: `tobqbddfcyitwgbkthhy`.  
Max migration in this snapshot: **1097** `canon_begin_username_login`.  
Do not rewrite applied migrations.

## Later (not this handoff)

CI can run Fast on every PR. Full E2E + emulator + Maestro belong in Cloud/CI later, not on the developer PC.
