# KMP/iOS — Matriz de compartición (post KMP-5)

## Shared

| Área | Estado |
| ---- | ------ |
| Session models / FakeSession (tests) | SHARED |
| AuthRepository + gateway + login UI | SHARED |
| Supabase Auth client factory (mínimo) | SHARED |
| SecureSessionStorage contract | SHARED |
| LF/Adoption/Pets presentation | SHARED |
| Status rules KMP-1 | SHARED |

## Adapter

| Área | Estado |
| ---- | ------ |
| Keychain iOS | ADAPTER (iosMain) |
| Secure prefs Android shared | ADAPTER |
| IosSupabaseConfigReader | ADAPTER |
| AndroidSessionMapper | ADAPTER (proyección) |

## Android-only / diferido

| Área | Clasificación |
| ---- | ------------- |
| Auth fat :app (consent/OTP/FCM/delete) | ANDROID_ONLY |
| Deep links iOS / Apple Sign In | DEFERRED |
| Profile/pets/LF REAL_REMOTE data | DEFERRED |
| M09 WIP | DEFERRED (no tocar) |
| M24 / M28 | Fuera de scope |
