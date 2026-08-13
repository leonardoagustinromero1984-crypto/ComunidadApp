# KMP-IOS — Bloque 5 validación

**HEAD base:** `0a5108e`
**Commit KMP-5:** (ver SHA post-push)

## Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | PASS — **92** tests |
| Suite auth nueva | 22 (`AuthSessionVerticalTest`) |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL |
| commonMain isolation | PASS |
| APK / SQL / schema | NO |

## Modos iOS

| Capa | Modo |
| ---- | ---- |
| Sesión / Auth | **REAL_REMOTE** |
| Perfil / pets / LF / adopciones | SHARED_FAKE |

Sin `SUPABASE_URL`/`SUPABASE_ANON_KEY` en Info.plist: sigue REAL_REMOTE vía `UnconfiguredAuthSessionRepository` (login → Unavailable). No se usa FakeSession en el host iOS.

## Config local

1. Copiar `iosApp/Config/Secrets.xcconfig.example` → `Secrets.xcconfig` (gitignored).
2. Definir `SUPABASE_URL` / `SUPABASE_ANON_KEY` en build settings Xcode (o incluir xcconfig).
3. Info.plist ya referencia `$(SUPABASE_URL)` / `$(SUPABASE_ANON_KEY)`.

## Gate

Manual: GitHub → Actions → KMP iOS Validation → `main`.

## WIP

M09 / decoding / M29 preservados.
