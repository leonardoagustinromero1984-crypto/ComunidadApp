# KMP-IOS — Sesión / Perfil / Mascotas (Bloque 3)

## Vertical compartida

```text
LeoVerSharedApp (CMP)
  Home → Perfil | Mascotas → Detalle
       ↑
SessionRepository / UserProfileRepository / SharedPetsRepository
       ↑
Fake* (iOS hoy)  |  AndroidSessionMapper + Auth :app (real)
```

## Sesión

| Artefacto | Ubicación | Notas |
| --------- | --------- | ----- |
| `SessionUser` / `SessionState` | commonMain | Sin tokens/secretos |
| `SessionRepository` + use cases | commonMain | Observe / Get / SignOut |
| `FakeSessionRepository` | commonMain | `SESSION_STUB` |
| `AndroidSessionMapper` | androidMain | Proyección mínima desde Auth :app |
| Auth GoTrue productivo | `:app` | ANDROID_ONLY |

Estados: `Unknown` · `Unauthenticated` · `Authenticated` · `Expired` · `Error`.

## Perfil

| Artefacto | Ubicación |
| --------- | --------- |
| `UserProfile` / `UserProfileSummary` | commonMain |
| `UserProfileRepository` / `GetMyProfileUseCase` | commonMain |
| `FakeUserProfileRepository` | commonMain (`SHARED_FAKE`) |

Campos públicos: `userId`, `displayName`, `email?`, `approximateLocation?`, `avatarRef?`, timestamps.
No incluye: teléfono, dirección exacta, coords, roles internos.

## Mascotas (M08)

Reutiliza `PetAggregate` / `PetId` ya en shared (no se recrea).
Capa presentación: `PetSummary`, `PetDetailView`, `SharedPetsRepository`, fake determinista.
UI states: `VerticalLoadState` Loading / Empty / Content / Error (+ sanitizer).

## UI / presenters

- `LeoVerSharedApp` + VMs ligeros en `vertical/` (patrón Flow ya usado en POC).
- iOS: `PocIosViewController` → `LeoVerSharedApp` con fakes; POCs legacy bajo “Herramientas de desarrollo”.
- Badge de modo de datos visible en Home (stub/fake — no se finge remoto).

## Onboarding / prefs

- Intent store compartido (`OnboardingIntentStore`) — no crea roles.
- `PlatformPreferences` + `InMemory` / Android SharedPreferences simple / iOS `NSUserDefaults`.
- DataStore Android productivo: sin mover. Keychain: diferido.

## Brechas documentadas

1. Auth real iOS / Supabase GoTrue KMP — DEFERRED
2. Keychain para tokens — DEFERRED
3. PetRepository Supabase productivo — ANDROID_ONLY (contrato presentación compartido)
4. Edición de perfil — no en este bloque
5. Siguiente bloque propuesto: **KMP-4 Perdidos/Encontrados + Adopciones** (no implementado)
