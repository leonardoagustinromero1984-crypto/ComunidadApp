# KMP-IOS — Bloque 4 validación

**HEAD base:** `b84a8db`
**Commit KMP-4:** `0a5108eb07bff18c8060014f024e3301d21c8bf8`

## Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | PASS — **70** tests (0 failures) |
| Suite LF/Adoption nueva | 22 tests (`LostFoundAdoptionVerticalTest`) |
| Suites previas | 48 tests |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| commonMain isolation | Sin imports `android.*` / `platform.UIKit` / `platform.Foundation` nuevos |
| APK / emulador / lint / SQL | NO |

## Datos iOS

| Capa | Modo |
| ---- | ---- |
| Sesión | SESSION_STUB |
| Perfil / mascotas | SHARED_FAKE |
| Lost/Found | SHARED_FAKE |
| Adopciones | SHARED_FAKE |

## WIP

M09 / decoding / M29 preservados fuera del commit.

## Gate

GitHub → Actions → **KMP iOS Validation** → Run workflow → `main` (manual).
