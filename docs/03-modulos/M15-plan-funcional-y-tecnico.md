# M15 — Plan funcional y técnico

## Objetivo

Hogares de tránsito alineados con producto D01, consolidando track M15 sobre legacy M10.

## Alcance Bloque 1 (cerrado localmente)

- Dominio `M15Foster*`, permisos, auditoría, hooks M06 preparados.
- Validadores y errores tipificados `M15_*`.
- Fakes in-memory + contratos repositorio (hogar, solicitud, alojamiento base).
- UI hub, listado, detalle, mi hogar, solicitud, solicitudes recibidas.
- Rutas `m15/*`; legacy `foster_*` preservado.

## Exclusiones Bloque 1

Gastos, evolución, ayuda, egreso completo, SQL, Supabase, pagos, chat, M16.

## Alcance Bloque 2 (cerrado localmente)

- Caso A: M10 tablas/RPC 040/041 autoritativas; **sin 053**.
- `SupabaseM15Foster*Repository` delegando en `Foster*Repository`.
- `M15FosterMappers.kt`; errores M10 → M15.
- DataProvider: Supabase → M10; local → mock.

## Alcance Bloque 3 (cerrado localmente)

- Evolución append-only sobre placements M10 041.
- Egreso completo + motivos tipificados; custodia temporal M08 sin transferir responsabilidad principal.
- Gastos registro (sin pagos); ayuda coordinada (sin chat).
- `SupabaseM15Placement*` / `MockM15Placement*`; UI `m15/placements/*`.
- Caso A: **sin 053**.

## Propuesta Bloque 4

Métricas agregadas, privacidad final, hooks M06 reales, smoke remoto M15/M10, cierre técnico local.

## Pendientes

```text
VALIDACIÓN FUNCIONAL MANUAL M15 B1/B2/B3 PENDIENTE
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
MIGRACIÓN 053 NO REQUERIDA
M14 052 APPLY REMOTO PENDIENTE
GITHUB ANDROID CI PENDIENTE
SMOKE REMOTO M15/M10 PENDIENTE
```
