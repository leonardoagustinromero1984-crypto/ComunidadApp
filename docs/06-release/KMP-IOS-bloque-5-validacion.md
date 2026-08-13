# KMP-IOS — Bloque 5 validación

**HEAD base KMP-5:** `b93faa9`
**Gate #6 (cloud):** FAIL
- `IOS_SIMULATOR_COMPILE` / `:shared:compileKotlinIosSimulatorArm64` = PASS
- `IOS_FRAMEWORK_LINK` / `:shared:linkDebugFrameworkIosSimulatorArm64` = FAIL
- Causa: `ClassCastException: IrExternalPackageFragmentImpl cannot be cast to IrClass` en ObjC export (`IrExportCheckerVisitor` / `createConstructorAdapter`) al exportar tipos que implementan/reciben APIs supabase-kt (`SessionManager`, `SupabaseClient`, `UserSession`) + interop Keychain público.

## Fix KMP-5.1 (local)

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | PASS — **92** tests |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL |
| Warnings `This cast can never succeed` en Keychain | **0** |
| SESSION IOS | **REAL_REMOTE** (sin FakeSession en host) |
| KMP-5 PASS cloud | **NO** hasta re-run gate |

### Superficie ObjC reducida (`internal`)

| Declaración | Motivo |
| ----------- | ------ |
| `SecureStorageSessionManager` | implementa `SessionManager` / usa `UserSession` |
| `SupabaseAuthSessionGateway` | constructor/`createClient` → `SupabaseClient` |
| `createAuthRepository` | factory Kotlin-only (PocIosEntry) |
| `createSecureSessionStorage` (expect/actual) | factory plataforma |
| `IosKeychainSecureSessionStorage` | interop Security/CF |
| `IosSupabaseConfigReader` | reader Bundle Kotlin-only |
| `AndroidSecureSessionStorage` | adapter Android shared |

**Sigue exportable / entry Swift:** `PocIosViewController()`

### Keychain

Antes: casts NSString/NSData/NSMutableDictionary→CFDictionary / NSCopyingProtocol (warnings “cast can never succeed”).
Después: CFString/CFData/CFDictionary nativos (`CFStringCreateWithCString`, `CFDataCreate`, `CFDictionaryCreate`). Sin NSUserDefaults.

## Modos iOS

| Capa | Modo |
| ---- | ---- |
| Sesión / Auth | **REAL_REMOTE** |
| Perfil / pets / LF / adopciones | SHARED_FAKE |

## Gate

Re-ejecutar manualmente: GitHub → Actions → KMP iOS Validation → `main`.
No marcar KMP-5 PASS hasta framework link cloud PASS.

## WIP

M09 / decoding / M29 preservados.
