# KMP/iOS — Matriz de compartición (actualizada KMP-3)

## A. Shared (listo)

| Área | Destino | Estado |
| ---- | ------- | ------ |
| Pets domain M08 | `shared/.../domain/pets` | SHARED |
| OrganizationId / onboarding models / M23 resilience | shared | SHARED |
| Lost/found + adoption **rules** | shared | SHARED |
| PlatformClock | shared | SHARED |
| SessionState / SessionRepository / fakes | `shared/.../session` | SHARED |
| UserProfileSummary / repo / fake | `shared/.../profile` | SHARED |
| PetSummary / PetDetailView / SharedPetsRepository | `shared/.../pets` | SHARED |
| Vertical UI states + ErrorSanitizer | `shared/.../ui` | SHARED |
| LeoVerSharedApp (CMP) | `shared/.../vertical` | SHARED |
| OnboardingIntentStore + PlatformPreferences | shared + actuals | SHARED + ADAPTER |
| POC M08/M22 | shared | SHARED (dev) |

## B. Adapter / parcial

| Área | Estado |
| ---- | ------ |
| AndroidSessionMapper | ADAPTER (proyección; Auth real en :app) |
| ImagePicker / FileRef | PARCIAL (POC) |
| PlatformPreferences Android/iOS | ADAPTER (no sensibles) |
| Secure storage / Keychain | AUSENTE — DEFERRED |
| Supabase pets/profile productivo | ANDROID_ONLY — ADAPTER_REQUIRED futuro |
| HTTP/Supabase KMP | PARCIAL (solo POC M22) |

## C. Mantener específico / diferido

| Área | Clasificación |
| ---- | ------------- |
| Auth GoTrue / deep links / SessionViewModel :app | ANDROID_ONLY |
| DataProvider / Room / DataStore productivo | ANDROID_ONLY |
| `PetAuthorizationBridge` | ANDROID_ONLY |
| FCM / Manifest / cámara productiva | ANDROID_ONLY |
| WIP M09 models/decoding / M29 docs | DEFERRED (fuera de KMP) |
| Apple Sign In / APNs / ubicación / background | DEFERRED |
| Web | Fuera de scope |

## D. READY_TO_MOVE (no este bloque)

- Proyección perfil M02 → shared (parcialmente cubierto con Summary)
- Cliente Supabase KMP auth limpio cuando riesgo bajo
- Lost/found + adoption **UI vertical** → KMP-4
