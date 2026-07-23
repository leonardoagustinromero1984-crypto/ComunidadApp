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

| Migración | Alcance | Apply remoto |
|-----------|---------|--------------|
| `042_m11_shelter_operations_core.sql` | Perfiles, placements, voluntarios | Aplicada (pruebas) |
| `043_m11_shelter_campaigns_and_aid.sql` | Campañas, insumos, aportes | Aplicada (pruebas) |
| `044_m11_harden_campaign_aid_permissions.sql` | Hardening permisos (sin cambio funcional) | **Pendiente apply** |
| Bloque 3 | Urgencias/eventos/reportes | Migración **045** (no iniciar sin PASS 044) |

Legacy `shelters` intacta. Detalle bloque 1: `docs/02-arquitectura/M11-operacion-refugios.md`.
Detalle bloque 2: `docs/02-arquitectura/M11-campanas-insumos-red-ayuda.md`.
Hardening: `docs/05-operacion/M11-aplicacion-migracion-044-seguridad.md`.

## Bloque 2 — Campañas e insumos (no monetario)

- Campañas por refugio operativo (`DRAFT` → `ACTIVE` / `PAUSED` / `COMPLETED` / `CANCELLED`).
- Visibilidad `PUBLIC` (listado público solo ACTIVE+PUBLIC) o `INTERNAL`.
- Pedidos de insumos con estados derivados de aportes (`OPEN` → … → `FULFILLED` / `EXPIRED` / `CANCELLED`).
- Aportes: pledge, confirmación, recepción parcial/total; sin CBU, alias ni pagos.
- Evidencia M05: `m05://` y `file_asset:` únicamente.
- Permisos M03: `shelter.campaign.*`, `shelter.supply.*`, `shelter.contribution.*`.
- M06 hooks preparados sin push; M07 auditoría en dominio mock.
- Cliente: mutaciones solo vía RPC `SECURITY DEFINER`; tablas con `SELECT` + RLS (correctiva 044).
- Apply: `docs/05-operacion/M11-aplicacion-migracion-043-supabase.md` + hardening 044 (**no apply desde Cursor**).

## Pantallas

Bloque 1: `shelters`, `my_shelters`, `shelter_ops_detail`, `shelter_dashboard`, formulario, mascotas/ingreso/detalle, voluntarios/invitación.

Bloque 2: listado público/gestión de campañas y pedidos, detalle, formularios, novedades, aporte y recepción de contribuciones.

## Tests / build

| Test | Alcance |
|------|---------|
| `M11ShelterOperationsCoreTest` | Bloque 1 |
| `M11ShelterCampaignsAndAidTest` | Bloque 2 dominio mock (~40 casos) |
| `M11CampaignMigrationStaticGuardsTest` | Contratos SQL 043 |
| `M11CampaignSecurityGuardsTest` | Hardening permisos 044 |

`compileLocalDebugKotlin` · `./gradlew :app:testLocalDebugUnitTest --tests "com.comunidapp.app.viewmodel.M11*"`

## Pendientes

Apply remoto **044** (hardening) + PASS de seguridad; luego Bloque 3 (migración **045**). Push M06, reputación, chat, APK cuando se solicite.
