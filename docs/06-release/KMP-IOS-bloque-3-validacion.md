# KMP-IOS — Bloque 3 validación

**HEAD base:** `fcab51a`
**Commit KMP-3:** (ver SHA post-push)
**Fecha local:** 2026-08-12

## Validación Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | PASS — **48** tests (0 failures) |
| Suite vertical nueva | 16 tests (`SessionProfilePetsVerticalTest`) |
| Suites previas | 32 tests (domain/home/M08/M22) |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| Dependencias Android accidentales en commonMain | Ninguna nueva (`android.*` / `java.time` / `UUID` / `BuildConfig`) |
| Emulador / APK / lint / JaCoCo / suite completa | NO ejecutados (regla) |
| iOS local macOS | NO (sin Mac) |
| Gate iOS GitHub Actions | **PENDIENTE DE EJECUCIÓN MANUAL** |

## Datos iOS (honestidad)

| Capa | Modo |
| ---- | ---- |
| Sesión | `SESSION_STUB` (`FakeSessionRepository`) |
| Perfil | `SHARED_FAKE` (`FakeUserProfileRepository`) |
| Mascotas | `SHARED_FAKE` (`FakeSharedPetsRepository` sobre `PetAggregate`) |
| Auth remoto GoTrue | No en iOS (Android productivo intacto) |
| Keychain | Pendiente — no se guardan tokens |

## WIP ajeno

Preservado fuera del commit: M09 / decoding / `M29-brand-studio-y-publicidad.md`.

## Gate cloud (manual)

GitHub → Actions → **KMP iOS Validation** → Run workflow → `main`
Esperado: `COMMON_TESTS`, `IOS_SIMULATOR_COMPILE`, `IOS_FRAMEWORK_LINK`, `IOS_APP_BUILD`.
