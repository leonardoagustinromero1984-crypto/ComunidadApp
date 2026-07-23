# M10 — Hogares de tránsito

## Auditoría (bloques 1–2)

| Área | Clasificación |
|------|----------------|
| Perfiles / solicitudes / ingreso (bloque 1) | **Reutilizable** |
| Migración `040` | **Reutilizable** (no modificar) |
| Gastos / evolución / ayuda / finalización pre-041 | **Ausente** → implementado en bloque 2 |
| Sumate / `PublishFosterScreen` | **Legacy / mock** |
| `foster_homes` / `foster_requests` legacy | **Incompatible** |
| M08 `TEMPORARY_CUSTODIAN` / `PRINCIPAL` | **Reutilizable** |
| Pagos / bucket público comprobantes | **Fuera de alcance** / **rechazado** |

## Modelos

Bloque 1: `FosterHomeProfile`, `FosterHomeRequest`, `FosterPlacement`.

Bloque 2: `FosterExpense`, `FosterEvolutionEntry`, `FosterHelpRequest`, `FosterHelpContribution`, `FosterPlacementEndReason`.

## Ciclo del tránsito

1. Perfil → solicitud → reserva → ingreso (`TEMPORARY_CUSTODIAN`, PRINCIPAL intacto).
2. Gastos / evolución / pedidos de ayuda (solo `ACTIVE`).
3. Finalización: libera capacidad, revoca custodia temporal, cierra ayuda abierta, historial inmutable.
4. Cancelación pre-ingreso: solo `RESERVED`.

## Persistencia

| Migración | Contenido | Remoto |
|-----------|-----------|--------|
| `040_m10_foster_homes_core.sql` | perfiles, solicitudes, placements | **Pendiente** |
| `041_m10_foster_care_management.sql` | gastos, evolución, ayuda, complete/cancel | **Pendiente** |

Detalle bloque 2: `docs/02-arquitectura/M10-gestion-y-finalizacion-transito.md`

## RLS / RPC (bloque 2)

`m10_add/list_foster_expense`, `m10_add/list_foster_evolution`, `m10_create_help_request`, `m10_update_help_request_status`, `m10_list_help_requests`, `m10_get_help_request`, `m10_record_help_contribution`, `m10_cancel_foster_placement`, `m10_complete_foster_placement`, `m10_list_foster_history`.

## Pantallas / rutas

Además del bloque 1: `foster_placement_management`, `foster_expenses`, `foster_expense_form`, `foster_evolution`, `foster_evolution_form`, `foster_help`, `foster_help_form`, `foster_help_detail`, `foster_complete`, `foster_history`.

## Tests / compilación

- `M10FosterHomeCoreTest`, `M10FosterCareManagementTest` (+ M08/M09 focalizados)
- `compileLocalDebugKotlin` (sin APK / lint / JaCoCo)

## Pendientes posteriores

Pagos, reintegros, reputación, chat, push, IA, estadísticas, galería, apply remoto 040/041, staging, APK.
