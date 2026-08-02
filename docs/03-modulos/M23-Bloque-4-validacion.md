# M23 Bloque 4 — Validación remota

## Staging

- Proyecto: `wystsapjfpdtoprlmizz` (no producción)
- Última remota previa M23: `067`
- Aplicadas: `068`, `069` (manual SQL, no `db push` global)

## schema_migrations

```text
067 — M22 fix
068 — M23 disponibilidad y reservas
069 — M23 operaciones y concurrencia
```

## Validación SQL 110/110

Script: `scripts/ops/m23_remote_validation_068_069.sql`

```bash
supabase db query --linked -f scripts/ops/m23_remote_validation_068_069.sql
```

Resultado verificado: **110 PASS / 0 FAIL**

## Smoke remoto 25/25

| # | Caso | Resultado |
|---|---|---|
| 1 | DataProvider Supabase M23 | PASS |
| 2 | Directorio reservable | PASS |
| 3 | Disponibilidad carga | PASS |
| 4 | Slots públicos | PASS |
| 5 | Crear reserva | PASS |
| 6 | Retry idempotente | PASS |
| 7 | Mis reservas | PASS |
| 8 | Detalle | PASS |
| 9 | Agenda prestador | PASS |
| 10 | Confirmar | PASS |
| 11 | Rechazar | PASS |
| 12 | Cancelación cliente | PASS |
| 13 | Cancelación prestador | PASS |
| 14 | Reprogramación | PASS |
| 15 | Concurrencia doble reserva | PASS |
| 16 | Completar | PASS |
| 17 | No-show | PASS |
| 18 | Historial | PASS |
| 19 | M08 mascota (columna) | PASS |
| 20 | M20 contexto (cliente) | PASS |
| 21 | M21 adaptador | PASS controlado |
| 22 | M06 no bloquea | PASS |
| 23 | Usuario ajeno denegado | PASS |
| 24 | Sin PII pública | PASS |
| 25 | Sin pagos M24 | PASS |

## Tests Kotlin M23

```text
84/84 PASS
```

## Compilación

```text
compileLocalDebugKotlin — PASS
```

## Producción

No afectada.
