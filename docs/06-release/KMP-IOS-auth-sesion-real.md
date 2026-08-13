# KMP-IOS — Auth / sesión real (Bloque 5)

## Resumen

iOS deja de usar `FakeSessionRepository` en el host principal.

```text
PocIosViewController
  → createAuthRepository(IosSupabaseConfigReader, Keychain storage)
  → LeoVerSharedApp(authRepository)  // REAL_REMOTE
  → Login CMP → Home …
```

## Componentes

| Pieza | Ubicación |
| ----- | --------- |
| `AuthRepository` + modelos | `shared/.../auth` |
| `AuthSessionGateway` / Fake gateway | commonMain (tests) |
| `SupabaseAuthSessionGateway` | commonMain + supabase-kt Auth |
| `SecureStorageSessionManager` | commonMain |
| `IosKeychainSecureSessionStorage` | iosMain |
| `AndroidSecureSessionStorage` | androidMain (shared; Auth :app intacto) |
| Login CMP | `AuthLoginScreen` |

## Seguridad

- Tokens solo en Keychain (iOS) vía SessionManager.
- `SessionUser` sin tokens.
- Password solo en estado local Compose (no StateFlow).
- Rechazo de `service_role` en config.
- Sin logs de JWT / Authorization.

## Persistencia

| Tipo | API |
| ---- | --- |
| No sensible | `PlatformPreferences` (NSUserDefaults / SharedPreferences) |
| Secretos sesión | `SecureSessionStorage` → Keychain iOS |

## Logout

`signOut()` remoto + limpieza SessionManager + UI → Login.

## Fuera de alcance

Registro, recovery, Apple/Google Sign In, APNs, deep links iOS, REAL_REMOTE de perfil/pets/LF/adopciones.
