# KMP-IOS — Bloque 1 validación

## Qué se movió a `:shared`

- Dominio M08 pets (models, enums, ids, rules, repository contracts) — mismo paquete `com.comunidapp.app.domain.pets`
- `OrganizationId` → commonMain
- `OnboardingModels`
- `M23BookingResilience`
- Nuevas reglas canónicas: lost/found + adoption listing status
- `PlatformClock` (injectable + actual Android/iOS)
- `SharedDomainFoundationTest` (11 tests)

## Qué no se movió

- `PetAuthorizationBridge` (M02 auth)
- Data models Android `AdoptionPost` / `LostFoundPost` (WIP M09)
- m23 con java.time
- UI productiva / DataProvider / Auth productivo

## Porcentaje aproximado (informativo)

~5–8% del dominio app hacia commonMain (verticales pets/onboarding/booking-resilience + reglas nuevas).

## Validación Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:allTests` | PASS — **28/28** (17 POC + 11 foundation) |
| `compileLocalDebugKotlin` | PASS |
| APK / emulator / lint / JaCoCo | NO ejecutados |
| SQL / staging / web | NO tocados |

## Riesgos

- `petFailure` pasó a público (uso cross-module Android).
- Smart-cast cross-module corregido en 2 call sites Android.
- WIP M09 permanece uncommitted (preservado).
