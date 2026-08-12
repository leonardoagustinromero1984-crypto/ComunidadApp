# KMP-IOS — Cloud gate

## Workflow

- Archivo: `.github/workflows/kmp-ios-validation.yml`
- Trigger: `workflow_dispatch` only
- Runner: `macos-latest`
- Timeout: 30 min
- Java 17 + Gradle wrapper

## Gate mínimo

1. Environment dump (macOS / Xcode / Java / Gradle / Kotlin / simulators)
2. Discover `:shared` iOS tasks
3. `compileKotlinIosSimulatorArm64` (+ device si existe)
4. `linkDebugFrameworkIosSimulatorArm64`
5. common/iOS tests (`iosSimulatorArm64Test` o fallback `testAndroidHostTest`)
6. `xcodebuild` iosApp scheme `LeoVerKmpPoc` (`CODE_SIGNING_ALLOWED=NO`)
7. Boot simulator dinámico, install, launch, crash check, screenshot artifact

## Evidencia obligatoria (step summary)

```text
=== LEOVER KMP IOS CLOUD GATE ===

BASE_COMMIT = <sha>
MACOS_VERSION = ...
XCODE_VERSION = ...
JAVA_VERSION = ...
GRADLE_VERSION = ...
KOTLIN_VERSION = ...
COMMON_TESTS = PASS
IOS_SIMULATOR_COMPILE = PASS
IOS_FRAMEWORK_LINK = PASS
IOS_APP_BUILD = PASS
```

## Cómo disparar (sin `gh`)

GitHub → Actions → **KMP iOS Validation** → Run workflow → `main` → Run workflow
