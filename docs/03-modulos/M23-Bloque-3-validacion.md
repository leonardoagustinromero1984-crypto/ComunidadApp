# M23 Bloque 3 — Validación

## HEAD

Pendiente commit `feat(m23): add booking operations and provider agenda`

## Operaciones implementadas

- Agenda cliente: filtros (próximas, historial, estado), detalle, cancelación, reprogramación, política capturada, historial.
- Agenda prestador: vista operativa, filtros día/estado, confirmación, rechazo, cancelación, completar, no-show, expiración.
- Reprogramación atómica mock con `rescheduled_from_booking_id`.
- Historial append-only en mock store.
- Privacidad: notas privadas separadas, sanitizer público.
- M20: adaptador conversación contextual (`BOOKING` en enum M20).
- M21: adaptador elegibilidad post-`COMPLETED` sin crear reseñas.
- M06: hooks best-effort (`M23BookingNotificationAdapter`); fallo no revierte operación.

## Tests M23

```text
84/84 PASS
  M23SchedulingFoundationTest     13
  M23BookingOperationsTest        21
  M23BookingRemoteMapperTest      15
  M23BookingBlock3Test            35
```

## Compilación

```text
.\gradlew.bat compileLocalDebugKotlin — PASS
```

## Incidencia transitoria de test (resuelta)

- `completeTerminal` falló una vez: fixture con reserva CONFIRMED en horario futuro.
- Regla `M23_COMPLETE_TOO_EARLY` correcta; fixture corregido.
- Regresión M23 final: **84/84 PASS**. No es defecto abierto.

## Brechas 068 documentadas para Bloque 4

- Reprogramación RPC
- Expiración RPC
- Historial RPC
- Confirmación con re-validación de solapamiento
- Motivos privados en rechazo
- `rescheduled_from_booking_id`, `pet_id`

## Migración

068 creada, **no aplicada**. 069 pendiente de auditoría Bloque 4.
