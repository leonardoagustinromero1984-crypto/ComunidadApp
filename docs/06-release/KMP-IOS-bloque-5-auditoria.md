# KMP-IOS — Bloque 5 auditoría (Auth real)

**HEAD base:** `0a5108e`
**WIP M09/decoding/M29:** preservado

| Área | Clasificación | Notas |
| ---- | ------------- | ----- |
| `supabase-auth` + ktor engines en `:shared` | SHARED / READY_TO_REUSE | Ya en classpath |
| `SessionState` / `SessionRepository` / fakes | SHARED | No romper KMP-3 |
| `PocSupabaseConfig` patrón | SHARED | Generalizar a `SharedSupabaseConfig` |
| M22 Postgrest client | SHARED (POC) | No Auth |
| `AuthRepository` / GoTrue productivo `:app` | ANDROID_ONLY | Consent/OTP/FCM/delete |
| `SupabaseClientProvider` `:app` | ANDROID_ONLY | No mover fat client |
| Deep links Android | ANDROID_ONLY | DEFERRED iOS |
| `PlatformPreferences` | ADAPTER (no secretos) | Intactas |
| Secure storage / Keychain | ADAPTER_REQUIRED → este bloque | iosMain Keychain |
| SettingsSessionManager default | DEFERRED para tokens iOS | NSUserDefaults inseguro para JWT |
| iOS URL/anon injection | ADAPTER_REQUIRED → este bloque | Info.plist + xcconfig ejemplo |
| Apple/Google Sign In / APNs | DEFERRED | |
| Profile/pets/LF/adoption REAL_REMOTE | DEFERRED | Siguen SHARED_FAKE |

## Decisión

- Auth email/password + restore/refresh/signOut en commonMain vía supabase-kt.
- `SessionManager` custom → `SecureSessionStorage` (Keychain iOS).
- iOS principal: `REAL_REMOTE` (nunca fingir con FakeSession).
- Sin config usable: sigue `REAL_REMOTE` + login Unavailable (honesto para CI sin secrets).
