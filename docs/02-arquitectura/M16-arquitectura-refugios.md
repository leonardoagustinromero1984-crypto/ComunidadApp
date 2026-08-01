# M16 — Arquitectura refugios

## Límites

M16 modela **perfil operativo y público de refugio** vinculado a organización M03. No reemplaza M11 legacy ni M15 tránsito.

## Relación con organizaciones

```text
Organization (M03) 1 — 0..1 M16ShelterProfile
Tipos elegibles: SHELTER, RESCUE_GROUP, NGO
```

## Flujos Bloque 1

1. Miembro manager crea perfil borrador para org elegible.
2. Edita datos públicos, horarios, servicios, necesidades, capacidad.
3. Publica perfil (`PUBLISHED`).
4. Solicita verificación (`PENDING`) — decisión admin M04 en bloques posteriores.

## Perfil público vs interno

| Campo | Interno | Público |
|-------|---------|---------|
| organizationId | Sí | No |
| internalNotes | Sí | No |
| displayName, zona, servicios | Sí | Sí (sanitizado) |
| capacidad agregada | Sí | Agregada |

## Permisos

Mock usa `organizationManagers` map. Producción: `OrganizationPermissionCode` vía M03 membership.

## Capacidad y ocupación (Bloque 3)

- **Capacidad configurada:** `M16ShelterCapacity.totalCapacity` + `reservedCapacity` (M16).
- **Ocupación física autoritativa:** placements M11 abiertos (`ShelterPetRepository`) vinculados a la misma `organizationId`.
- **Tránsito activo:** colocaciones M15 abiertas — dimensión separada; no suma a ocupación física.
- **Adopción activa:** procesos M09/M14 abiertos — dimensión separada; la mascota puede seguir alojada.
- **Adoptadas recientes:** métrica histórica (adopción completada), no ocupan cupo.
- **Fórmula:** `availableCapacity = max(0, totalCapacity - physicalOccupancy - reservedCapacity)`; advertencia si `physicalOccupancy > totalCapacity`.
- **`currentOccupancy` legacy:** snapshot informativo; la UI operativa usa el valor calculado por `M16ShelterOperationsService`.

## Proyección operativa (Bloque 3)

```text
M16ShelterOperationsRepository
  → M16ShelterOperationsService (dominio)
  → M08 PetRepository
  → M09 AdoptionRepository.getAdoptionsByOrganization
  → M11 ShelterPetRepository (placements abiertos)
  → M15 M15FosterPlacementRepository.observePlacementsForOrganization
```

Modelos de lectura: `M16ShelterOperationsSummary`, `M16ShelterPetOperationalItem`, `M16ShelterOccupancyBreakdown`, `M16ShelterOperationsFilter`.

Resumen operativo **no público** — requiere `shelter.view` / `shelter.manage` (M03). No se expone en `M16PublicShelter` ni RPCs públicas.

## Compatibilidad M11 (Bloque 3)

`M11M16ShelterCompatibilityAdapter`: si el perfil legacy tiene `organizationId` inequívoco con M16, navega al detalle público M16; si no, mantiene pantalla legacy con aviso.

## Privacidad

`M16PrivacySanitizer` excluye PII, IDs de usuario, notas internas.

## Dependencias

M03 (org), M02 (auth), M04 (verificación admin futura), M05 (media ref), M06 (notif hooks), M08/M09/M15 (B3).

## Estrategia local

`M16MemoryStore` + `MockM16ShelterRepository`; seed determinista.

## Estrategia Supabase (B2)

`SupabaseM16ShelterRepository` → `SupabaseM16RemoteDataSource` → RPCs `m16_*` (migración 053).

## Errores

`M16ShelterErrorMapper` — códigos tipificados sin excepciones crudas en UI.

## Idempotencia

Repetición mismo estado → replay; `PERMANENTLY_CLOSED` terminal.

## Migración 053

Tablas: `m16_shelter_profiles` (organization_id UNIQUE), períodos, contactos, necesidades, solicitudes verificación.
RLS deny-by-default; lectura pública vía `_m16_public_shelter_json` / RPCs anon.
Permisos: reutiliza `shelter.view` / `shelter.manage` (M11).
