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

## Propuesta Bloque 2 (exacta)

1. Especificar migración **053** (perfiles M15, solicitudes, placements) tras aprobación.
2. Repos Supabase M15 + reconciliación con 040/041 M10.
3. Bridge opcional M10→M15 o deprecación gradual de prefijos Foster en repos.
4. Validación estructural remota post-apply.
5. Pruebas automáticas focalizadas (si el usuario las solicita).

## Propuesta Bloque 3

Evolución, egreso, custodia temporal M08, gastos/ayuda.

## Propuesta Bloque 4

Métricas agregadas, privacidad final, hooks M06 reales, cierre técnico local.

## Pendientes

```text
VALIDACIÓN FUNCIONAL MANUAL M15 B1 PENDIENTE
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
MIGRACIÓN 053 NO CREADA
M10 040/041 APPLY REMOTO PENDIENTE
M14 052 APPLY REMOTO PENDIENTE
GITHUB ANDROID CI PENDIENTE
```
