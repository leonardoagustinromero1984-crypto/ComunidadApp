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

## Propuesta Bloque 3 (exacta)

1. Evolución operativa (RPC M10 041).
2. Egreso completo + custodia temporal M08.
3. Gastos y ayuda sobre placements M10.
4. Smoke funcional remoto integrado M15/M10.

## Propuesta Bloque 4

Métricas agregadas, privacidad final, hooks M06 reales, cierre técnico local.

## Pendientes

```text
VALIDACIÓN FUNCIONAL MANUAL M15 B1/B2 PENDIENTE
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
MIGRACIÓN 053 NO REQUERIDA
M14 052 APPLY REMOTO PENDIENTE
GITHUB ANDROID CI PENDIENTE
```
