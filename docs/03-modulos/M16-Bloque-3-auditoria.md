# M16 Bloque 3 — Auditoría de integración

**Fecha:** 2026-08-01

## Autoridades reutilizadas

| Módulo | Autoridad | Uso M16 B3 |
|--------|-----------|------------|
| M08 | `Pet`, `PetRepository` | Nombre, especie, foto, estado lifecycle |
| M09 | `AdoptionPost`, `AdoptionRepository` | Proceso adopción por `publisherOrganizationId` |
| M11 | `ShelterProfile`, `ShelterPetPlacement` | Alojamiento físico por `organizationId` |
| M15 | `M15FosterPlacement` | Tránsito activo por `requesterOrganizationId` |
| M16 | `M16ShelterProfile`, capacidad configurada | Capacidad total; snapshot manual |

## Relaciones existentes

- M11 `ShelterPetPlacement.petId` + perfil M11 por `organizationId`
- M09 `AdoptionPost.petId` + `publisherOrganizationId`
- M15 `M15FosterPlacement.petId` + `requesterOrganizationId`
- Legacy `Shelter` (`shelter_*`) mapeado documentado → org M16 (`shelter_1` → `org_refugio_norte`)

## Relaciones faltantes (sin migración 054)

- Consulta M08 por `organizationId` como principal — **no existe**; se usa M11 placements + pet lookup por ID
- Consulta M15 remota por org — **stub vacío** en Supabase; proyección Kotlin parcial

## Fórmula de ocupación

```
physicalOccupancy = mascotas con placement M11 abierto (RESERVED/ACTIVE/QUARANTINE/MEDICAL_CARE)
reservedCapacity  = subset RESERVED dentro de alojadas
availableCapacity = totalCapacity - physicalOccupancy - reservedCapacity  (mínimo 0)
inActiveFoster    = M15 ACTIVE/RESERVED (dimensión separada, no suma a física salvo inconsistencia)
activeAdoption    = M09 PUBLISHED/PAUSED (dimensión separada)
recentlyAdopted   = M09 ADOPTED (métrica histórica, no ocupación)
```

## Estados operativos por mascota

- `PHYSICALLY_HOUSED` — placement M11 abierto, sin tránsito activo conflictivo
- `IN_ACTIVE_FOSTER` — tránsito M15 activo/reservado
- `ACTIVE_ADOPTION_PROCESS` — publicación M09 activa (puede coexistir con alojada)
- `RECENTLY_ADOPTED` — adopción completada
- `INACTIVE` — DECEASED/ARCHIVED M08
- `INCONSISTENT` — alojada + tránsito activo u otras referencias huérfanas

## Estrategia M11

- `M11M16ShelterCompatibilityAdapter`: legacy `shelter_1/2` → org; navegación a detalle M16 si perfil publicado
- Rutas `shelter_*` preservadas; sin migración automática de datos

## Migración 054

```text
MIGRACIÓN 054 NO REQUERIDA
```

La proyección se construye en Kotlin con contratos existentes + extensiones mínimas:
- `AdoptionRepository.getAdoptionsByOrganization`
- `M15FosterPlacementRepository.observePlacementsForOrganization`

## Riesgos

- Legacy `shelterId` ≠ `organizationId` — bridge explícito en adapter
- Remoto M15 sin query por org — datos parciales hasta extensión M10/M15
- Snapshot `currentOccupancy` M16 conservado como informativo

## Diferido

- M16 Bloque 4 (métricas, smoke remoto, cierre global)
- M06 allowlist M16
- Cola M04 UI para verificación refugio
