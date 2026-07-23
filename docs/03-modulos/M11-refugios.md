# M11 — Refugios

## Auditoría inicial

| Pieza | Clasificación |
|-------|----------------|
| `Shelter` / Sumate / `PublishShelterScreen` / tabla `shelters` (006) | **Legacy / mock** — ficha pública con teléfono/email |
| `ShelterRepository` (legacy) | **Legacy** — no es operación M11 |
| Organizaciones / membresías / `has_org_permission` (M03) | **Reutilizable** |
| Sucursales `organization_branches` | **Reutilizable** (sede opcional) |
| M08 `pet_responsibilities` (PRINCIPAL / CO_RESPONSIBLE) | **Reutilizable** |
| M09 finalización adopción | **Integración** vía hook `onAdoptionFinalized` |
| M10 tránsito | **Integración** vía `FOSTER_RETURN` |
| Perfil operativo M11 | **Nuevo** |

Un refugio M11 es capacidad operativa de una **organización M03**, no un ownership paralelo.

## Modelos

- `ShelterProfile` + proyección `ShelterPublicListing`
- `ShelterPetPlacement`
- `ShelterVolunteerAssignment`

## Reglas clave

- Máximo un perfil `ACTIVE` por org+sede (`branch_id`).
- Capacidad: `used = occupancy + reserved`; no editable a mano.
- Ingreso usa mascotas M08; crea `CO_RESPONSIBLE` org; **no** toca PRINCIPAL.
- Adopción M09 cierra alojamiento; `FOSTER_RETURN` cierra tránsito M10.
- Voluntarios no otorgan `shelter.manage`.

## Persistencia

`042_m11_shelter_operations_core.sql` — **pendiente de apply remoto**. Legacy `shelters` intacta.

Detalle: `docs/02-arquitectura/M11-operacion-refugios.md`

## Pantallas

`shelters`, `my_shelters`, `shelter_ops_detail`, `shelter_dashboard`, formulario, mascotas/ingreso/detalle, voluntarios/invitación.

## Tests / build

`M11ShelterOperationsCoreTest` (+ M08/M09/M10 focalizados). `compileLocalDebugKotlin`.

## Pendientes (bloque siguiente)

Campañas, insumos, donaciones, eventos, reportes, reputación, chat, push, apply remoto 042, APK.
