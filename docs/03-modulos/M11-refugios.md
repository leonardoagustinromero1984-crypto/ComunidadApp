# M11 — Refugios

**Estado del módulo:** `M11 CERRADO` (cierre técnico). Detalle: `docs/03-modulos/M11-cierre-final.md`.

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
- Campañas / insumos / aportes (Bloque 2)
- Urgencias / eventos / inscripciones / métricas (Bloque 3)

## Reglas clave

- Máximo un perfil `ACTIVE` por org+sede (`branch_id`).
- Capacidad: `used = occupancy + reserved`; no editable a mano.
- Ingreso usa mascotas M08; crea `CO_RESPONSIBLE` org; **no** toca PRINCIPAL.
- Adopción M09 dispara hook de cierre de alojamiento (mock); remota dedicada = reapertura.
- `FOSTER_RETURN` cierra tránsito M10.
- Voluntarios no otorgan `shelter.manage`.

## Persistencia

| Migración | Alcance | Apply remoto (pruebas) |
|-----------|---------|------------------------|
| `042_m11_shelter_operations_core.sql` | Perfiles, placements, voluntarios | Aplicada |
| `043_m11_shelter_campaigns_and_aid.sql` | Campañas, insumos, aportes | Aplicada |
| `044_m11_harden_campaign_aid_permissions.sql` | Hardening permisos B2 | Aplicada + PASS |
| `045_m11_shelter_emergencies_events_reports.sql` | Urgencias, eventos, reportes | Aplicada + PASS |
| `046` | — | **No creada** |

Legacy `shelters` intacta. Matriz: `docs/02-arquitectura/M11-matriz-funcional-final.md`. Validación: `docs/05-operacion/M11-validacion-final.md`.

## Bloque 2 — Campañas e insumos (no monetario)

- Campañas PUBLIC/INTERNAL; estados draft→active/pause/complete/cancel.
- Pedidos de insumos y aportes no monetarios; FULFILLED; evidencia M05.
- Hardening 044: SELECT + RLS; mutaciones solo RPC.
- Docs: `docs/02-arquitectura/M11-campanas-insumos-red-ayuda.md`.

## Bloque 3 — Urgencias, eventos y reportes

- Urgencias con severidad/resolución; CRITICAL → hook M06.
- Eventos gratuitos; cupo/waitlist/asistencia.
- Métricas + CSV sin PII.
- Docs: `docs/02-arquitectura/M11-urgencias-eventos-reportes.md`.

## Pantallas

Bloque 1–3 conectados desde dashboard y listados públicos (campañas, insumos, urgencias, eventos).

## Tests / build

| Test | Alcance |
|------|---------|
| `M11ShelterOperationsCoreTest` | Bloque 1 |
| `M11ShelterCampaignsAndAidTest` | Bloque 2 |
| `M11CampaignMigrationStaticGuardsTest` / `M11CampaignSecurityGuardsTest` | 043 / 044 |
| `M11ShelterEmergenciesEventsReportsTest` / `M11Block3MigrationStaticGuardsTest` | Bloque 3 / 045 |
| `M11FinalClosureGuardsTest` | Cierre final |

## Pendientes externos (no faltantes M11)

```text
Smoke UI manual completo
M06 push real
chat / reputación
APK
promoción a otros ambientes
```
