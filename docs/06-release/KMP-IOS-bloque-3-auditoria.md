# KMP-IOS — Bloque 3 auditoría

**HEAD base:** `fcab51a`
**WIP M09/decoding/M29:** preservado (fuera de scope)

| Área | Estado | Notas |
| ---- | ------ | ----- |
| PetAggregate / rules / repo contracts | SHARED | `com.comunidapp.app.domain.pets` |
| Onboarding models | SHARED | `OnboardingModels.kt` |
| SharedPetHome + Fake | SHARED | Base home KMP-2 |
| SharedSessionStub | SHARED → evoluciona | Stub → SessionState formal |
| Auth productivo (GoTrue) | ANDROID_ONLY | AuthRepository / SessionViewModel |
| UserProfile (M02 completo) | ANDROID_ONLY | READY_TO_MOVE proyección mínima |
| DataProvider | ANDROID_ONLY | No se comparte |
| PetDomainRepository Supabase impl | ANDROID_ONLY | ADAPTER_REQUIRED |
| iOS Auth real | DEFERRED | SESSION_STUB / SHARED_FAKE |
| Keychain | DEFERRED | — |
| POC M08/M22 | SHARED | Escape hatch, no UX principal |
| CMP host SwiftUI | IOS_ONLY host | UI en Compose shared |

## Decisión Bloque 3

- Sesión/perfil/mascotas: contratos + fake determinista en commonMain.
- iOS: SHARED_FAKE + SESSION_STUB (documentado).
- Android: mapper/bridge; Auth real permanece en app.
- No SQL / no RPC / no DataProvider move.
