# M16 Bloque 4 — Auditoría

**Fecha:** 2026-08-01

## Defecto Bloque 3 detectado

La fórmula Bloque 3 contaba `RESERVED` dentro de `physicalOccupancy` (vía `isOpen`) **y** otra vez en `reservedCapacity`, duplicando cupos.

## Semántica M11 RESERVED (Modelo A adoptado)

Fuente autoritativa: `_m11_sync_shelter_capacity` (migración 042):

| Estado M11 | Significado | Bucket M16 |
|------------|-------------|------------|
| ACTIVE, QUARANTINE, MEDICAL_CARE | Mascota ingresada físicamente | `physicalOccupancy` |
| RESERVED | Cupo asignado, mascota aún no ingresó | `reservedCapacity` |
| RELEASED, ADOPTED, etc. | Terminal | no cuenta |

Flujo M11: `reserve` crea placement `RESERVED`; `admit` promueve a `ACTIVE`.

## Fórmula corregida

```
physicalOccupancy  = petIds únicos en ACTIVE | QUARANTINE | MEDICAL_CARE
reservedCapacity   = petIds únicos en RESERVED excluyendo los ya físicos
committedCapacity  = physicalOccupancy + reservedCapacity
availableCapacity  = max(0, totalCapacity - committedCapacity)
overCapacityBy     = max(0, committedCapacity - totalCapacity)
isOverCapacity     = overCapacityBy > 0
```

## Adopciones recientes

- Campo proxy: `AdoptionPost.updatedAt` al pasar a `ADOPTED` (M09 no expone `completedAt` en dominio Kotlin).
- Ventana: `M16_RECENT_ADOPTION_WINDOW_DAYS = 30`.
- Sin fecha fiable → `adoptionCompletionDatesUnavailable`.

## Consulta M15 por organización

- Implementado: `FosterPlacementRepository.observePlacementsForOrganization` + delegación M15.
- Remoto: filtro PostgREST `requester_organization_id` + estados activos.
- **Limitación RLS M10:** política `foster_placements_select_m10` no incluye miembros de organización solicitante; resultados pueden estar incompletos hasta extensión RLS/RPC futura (sin migración 054/055).
- Flag: `fosterOrgQueryLimited` documentado; fallo de red → `fosterSourceUnavailable`.

## Integración M04

- Cola existente `OrganizationVerificationQueueScreen` ampliada con solicitudes `m16_shelter_verification_requests`.
- Decisión vía `m16_decide_shelter_verification` / mock equivalente.
- Pantalla `M16ShelterVerificationReviewScreen`.
- Permisos: `organizations.review_verification` / `moderation.manage` (alineado `_m16_is_moderator`).

## currentOccupancy

- Snapshot informativo; UI operativa usa `physicalOccupancy` calculada.
- Acción explícita «Actualizar snapshot de ocupación» (`syncOccupancySnapshot`).

## M11 autoridad física transitoria

`ShelterPetPlacement` (M11) determina alojamiento físico hasta módulo de placements definitivo. Rutas `shelter_*` y adaptador M11→M16 preservados.

## Migración 054

```text
MIGRACIÓN 054 NO REQUERIDA
```

## Diferido

- M06 allowlist M16
- Smoke remoto (053 no aplicada)
- RPC M15 org-wide con RLS para miembros de refugio
- Migración asistida placements M11 → autoridad futura
