# M18 Bloque 3 — Validación local

**Fecha:** 2026-08-02  
**Veredicto local:** PASS (mock) — **validación remota 058 PENDIENTE**

## Tests ejecutados

Clase: `M18EventOperationsTest` (20 casos)

| # | Caso | Resultado esperado |
|---|------|-------------------|
| 1 | Registro con cupo | REGISTERED |
| 2 | Sin cupo + waitlist | WAITLISTED |
| 3 | Registro duplicado | idempotente |
| 4 | Una inscripción activa | mismo id |
| 5 | Cancelación libera cupo | availableSpots ↑ |
| 6 | Promoción waitlist | primer WAITLISTED → REGISTERED |
| 7 | Promoción idempotente | retry contado |
| 8 | Capacidad | occupied ≤ maxCapacity |
| 9 | Check-in válido | CHECKED_IN |
| 10 | Check-in duplicado | idempotente |
| 11 | Usuario no autorizado | fallo |
| 12 | ATTENDED | desde CHECKED_IN |
| 13 | NO_SHOW | solo post-evento (validador) |
| 14–15 | Evento cerrado/cancelado | rechazo registro |
| 16–17 | Privacidad pública | sin PII / userId |
| 18 | Seeds deterministas | PASS |
| 19 | Error remoto mapeado | PASS |
| 20 | M06 no bloquea | PASS |

## Compilación

`compileLocalDebugKotlin` — ejecutada al cierre del bloque.

## Migraciones

- **058:** no aplicada
- **059:** no creada

## Bloque 4

No iniciado antes del cierre de este bloque.
