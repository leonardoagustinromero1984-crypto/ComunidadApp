# M11 — Operación de refugios (arquitectura bloque 1)

## Integración

| Módulo | Uso |
|--------|-----|
| M03 | Org ACTIVE, membresía, permisos `shelter.*`, sucursal opcional |
| M08 | Pets + `CO_RESPONSIBLE` organizacional; PRINCIPAL intacto |
| M09 | Finalización → cierra placement abierto (`ADOPTED`) |
| M10 | `FOSTER_RETURN` completa foster activo y revoca TEMPORARY_CUSTODIAN |

## Capacidad

```text
used = current_occupancy + reserved_capacity
ACTIVE + used = 0 → AVAILABLE
ACTIVE + 0 < used < capacity → LIMITED
ACTIVE + used >= capacity → FULL
otro status → UNAVAILABLE
```

## Migración 042

Tablas: `shelter_profiles`, `shelter_pet_placements`, `shelter_volunteer_assignments`.

RLS: writes denegados; `drop policy if exists` + enable RLS.

RPC: create/update/status/get/list profiles; reserve/admit/change/release/list pets; invite/accept/pause/end/list volunteers.

Compatibilidad: **no** modifica `public.shelters` (006).

## Permisos M03

`shelter.view`, `shelter.manage`, `shelter.pet.read|intake|release`, `shelter.volunteer.read|manage`.

## Limitaciones

Sin campañas, donaciones, turnos, chat, push, pagos. Apply remoto pendiente.
