# KMP/iOS — Matriz de compartición

## A. Compartible inmediatamente (KMP-1 ejecutado)

| Ruta actual (antes) | Destino | Riesgo | Tests | Mover ahora |
| --------------------- | ------- | ------ | ----- | ----------- |
| `app/.../domain/pets/*` (excepto authorization bridge) | `shared/.../domain/pets/` mismo paquete | Bajo | PetDomainStage2 + commonTest | SÍ |
| `app/.../domain/organization/OrganizationId` | `shared/.../organization/OrganizationId.kt` | Bajo | vía pets | SÍ |
| `app/.../domain/onboarding/OnboardingModels.kt` | `shared/.../onboarding/` | Bajo | FirstRun VM + commonTest | SÍ |
| `app/.../domain/m23/M23BookingResilience.kt` | `shared/.../m23/` | Bajo | M23 Block3 + commonTest | SÍ |
| Reglas lost/found canónicas | `shared/.../shared/domain/lostfound/` | Bajo (nuevo) | commonTest | SÍ (sin tocar WIP models) |
| Reglas adoption listing | `shared/.../shared/domain/adoption/` | Bajo (nuevo) | commonTest | SÍ |
| Reloj de dominio | `shared/.../platform/PlatformClock` | Bajo | commonTest | SÍ |

## B. Compartible con adapter (pendiente)

| Área | Contrato | Estado |
| ---- | -------- | ------ |
| Image picker | `ImagePicker` / FileRef | PARCIAL (POC) |
| Secure storage | PlatformSecureStorage | AUSENTE |
| Logger | PlatformLogger | AUSENTE |
| Location | PlatformLocationProvider | AUSENTE |
| Notifications | PlatformNotificationGateway | AUSENTE |
| HTTP/Supabase productivo | cliente KMP | PARCIAL (solo POC M22) |

## C. Mantener específico

| Área | Motivo |
| ---- | ------ |
| Manifest / FCM / Activities | Android APIs |
| `PetAuthorizationBridge` | Depende AuthorizationContext M02 Android |
| DataStore onboarding persist | Android |
| `AdoptionPost` / `LostFoundPost` (WIP) | Dirty tree M09 — no mover |
| Cámara / channels / intents | Android-only |
| Web (`web/`) | Fuera de scope |

## Prioridad diferida

- m23 con `java.time` (SlotGenerator, Filters, Operations)
- AuthValidators (acopla AppError)
- UI Compose productiva (Prioridad 3)
