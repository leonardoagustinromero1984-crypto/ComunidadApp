# KMP-IOS — Bloque 9 validación

**HEAD implementación KMP-9:** `0ef16f532659502ce0ccfda82bb37aebdde96507`

**HEAD workaround KMP-9.1:** `9287e83e2afdaac83867a1d9ac8f87d7dbcdcd4e`

## Gate #11

| Check | Resultado |
| ----- | --------- |
| `:shared:compileKotlinIosSimulatorArm64` | PASS |
| `:shared:linkDebugFrameworkIosSimulatorArm64` | FAIL |

**Causa:** KT-86501 — Kotlin/Native compiler cache al linkear
`storage-kt-iosSimulatorArm64:3.0.3` (`kotlinx.datetime/Instant` already bound).

## Gate #12

| Check | Resultado |
| ----- | --------- |
| IOS_SIMULATOR_COMPILE | PASS |
| IOS_FRAMEWORK_LINK (`linkDebugFrameworkIosSimulatorArm64`) | PASS |
| IOS_DEVICE_COMPILE (`compileKotlinIosArm64`) | PASS |
| IOS_SHARED_TESTS (`iosSimulatorArm64Test` / `linkDebugTestIosSimulatorArm64`) | FAIL |

**SHA Gate #12:** `9287e83e2afdaac83867a1d9ac8f87d7dbcdcd4e`

**Causa:** KT-86501 también afecta al TEST binary. KMP-9.1 solo deshabilitó cache del framework.

## Workaround KMP-9.1 / 9.2

En `shared/build.gradle.kts`, solo `iosSimulatorArm64`:

| Binary | Cache |
| ------ | ----- |
| DEBUG FRAMEWORK | DISABLED (9.1) |
| DEBUG TEST (`linkDebugTestIosSimulatorArm64`) | DISABLED (9.2) |
| `iosArm64` | UNCHANGED |

`disableNativeCache(DisableCacheInKotlinVersion.2_3_20, …)` → [KT-86501](https://youtrack.jetbrains.com/issue/KT-86501)

Storage / M05 / MEDIA WRITE REAL_REMOTE: preservados.

**KMP-9 = NO CLOSED GREEN** hasta Gate cloud verde tras 9.2.

## Windows (regresión)

| Check | Resultado esperado post-9.2 |
| ----- | --------------------------- |
| `:shared:testAndroidHostTest` | 192 PASS |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
