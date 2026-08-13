# KMP-IOS — Bloque 9 validación

**HEAD implementación KMP-9:** `0ef16f532659502ce0ccfda82bb37aebdde96507`

**GitHub Actions Gate #11:** FAIL

## Gate #11

| Check | Resultado |
| ----- | --------- |
| `:shared:compileKotlinIosSimulatorArm64` | PASS |
| `:shared:linkDebugFrameworkIosSimulatorArm64` | FAIL |

**Causa:** KT-86501 — Kotlin/Native compiler cache al linkear
`storage-kt-iosSimulatorArm64:3.0.3`

```
IrTypeAliasSymbolImpl is already bound.
Signature: kotlinx.datetime/Instant|null[0]
Failed to build cache for storage-kt...
As a workaround, please try to disable compiler caches.
```

No es fallo de PHPicker / M05 / FileRef / Auth / credentials / ObjC export.

## Workaround KMP-9.1

En `shared/build.gradle.kts`, solo `iosSimulatorArm64` framework:

`disableNativeCache(DisableCacheInKotlinVersion.2_3_20, …)` → [KT-86501](https://youtrack.jetbrains.com/issue/KT-86501)

`iosArm64` cache: sin cambio.

Storage / M05 / MEDIA WRITE REAL_REMOTE: preservados.

**KMP-9 = NO CLOSED GREEN** hasta Gate cloud verde tras 9.1.

## Windows (regresión)

| Check | Resultado esperado post-9.1 |
| ----- | --------------------------- |
| `:shared:testAndroidHostTest` | 192 PASS |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
