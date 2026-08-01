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

## Capacidad

`M16ShelterCapacity`: total ≥ 0, occupancy + reserved ≤ total, disponibilidad derivada.

## Privacidad

`M16PrivacySanitizer` excluye PII, IDs de usuario, notas internas.

## Dependencias

M03 (org), M02 (auth), M04 (verificación admin futura), M05 (media ref), M06 (notif hooks), M08/M09/M15 (B3).

## Estrategia local

`M16MemoryStore` + `MockM16ShelterRepository`; seed determinista.

## Estrategia Supabase (B2)

`SupabaseM16ShelterRepository` → `M16_REMOTE_VALIDATION_PENDING` hasta migración 053.

## Errores

`M16ShelterErrorMapper` — códigos tipificados sin excepciones crudas en UI.

## Idempotencia

Repetición mismo estado → replay; `PERMANENTLY_CLOSED` terminal.

## Migración 053 (propuesta, no creada)

Tabla `m16_shelter_profiles(organization_id UNIQUE, ...)` referenciando `organizations.id`; RLS por membership M03.
