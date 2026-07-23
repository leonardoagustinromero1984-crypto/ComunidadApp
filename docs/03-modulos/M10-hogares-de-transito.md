# M10 — Hogares de tránsito

## Estado técnico

M10 está **completo técnicamente** (perfiles, solicitudes, ingreso, gastos, evolución, ayuda, finalización).

Migraciones **040** y **041** listas para apply manual; **pendientes de aplicación remota**. Smoke manual pendiente. APK solo cuando se solicite.

## Auditoría

| Área | Clasificación |
|------|----------------|
| Perfiles / solicitudes / ingreso | Reutilizable |
| Gastos / evolución / ayuda / finalización | Implementado (041) |
| Sumate / `PublishFosterScreen` | Legacy / mock |
| `foster_homes` / `foster_requests` legacy | Incompatible (no usados por M10) |
| M08 `TEMPORARY_CUSTODIAN` / `PRINCIPAL` | Reutilizable |
| Pagos | Fuera de alcance |
| Comprobantes | Solo refs privadas `m05://` / `file_asset:` (limitado por M05) |

## Disponibilidad (regla final)

```text
perfil no ACTIVE → UNAVAILABLE
ocupación + reservas = 0 → AVAILABLE
ocupación + reservas > 0 y < capacidad → LIMITED
ocupación + reservas >= capacidad → FULL
```

## Persistencia

| Migración | Contenido | Remoto |
|-----------|-----------|--------|
| `040_m10_foster_homes_core.sql` | perfiles, `foster_care_requests`, placements, ingreso | **Pendiente** |
| `041_m10_foster_care_management.sql` | gastos, evolución, ayuda, cancel/complete | **Pendiente** |

Guía operativa: `docs/05-operacion/M10-aplicacion-migraciones-supabase.md`  
Detalle bloque 2: `docs/02-arquitectura/M10-gestion-y-finalizacion-transito.md`

## Tests

`M10FosterHomeCoreTest`, `M10FosterCareManagementTest`, `M10MigrationStaticGuardsTest` (+ M08 focalizado).

## Pendientes manuales

Apply remoto 040→041, smoke SQL, pagos/reputación/chat/push/IA fuera de alcance, APK bajo pedido.
