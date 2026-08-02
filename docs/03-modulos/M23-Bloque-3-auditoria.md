# M23 Bloque 3 — Auditoría operativa

## HEAD inicial

`ce84f21655c08943ace60da8218e5c53ed387756` — `feat(m23): add scheduling and booking persistence`

## Matriz de cobertura (20 puntos)

| # | Capacidad | Dominio Kotlin | Mock | 068 | Remoto | UI | Brecha |
|---|-----------|----------------|------|-----|--------|-----|--------|
| 1 | Disponibilidad | `M23SlotGenerator` | Sí | RPC slots | Parcial | `M23AvailabilityScreen` | — |
| 2 | Reglas | `M23BookingValidators` | Sí | Tabla + RPC | Parcial | `M23ManageCalendarScreen` | — |
| 3 | Excepciones | Mock store | Sí | Tabla + RPC | Parcial | Calendario | — |
| 4 | Slots | `M23SlotGenerator` | Sí | `m23_get_public_available_slots` | Sí | Disponibilidad | — |
| 5 | Reserva REQUESTED | `M23BookingOperationsService` | Sí | `m23_create_booking_request` | Sí | Detalle | — |
| 6 | Confirmación | `confirm()` + overlap | Sí | `_m23_transition` | Parcial | Detalle prestador | 068 sin re-check en confirm → **069** |
| 7 | Rechazo | `reject()` terminal | Sí | `_m23_transition` | Parcial | Detalle | Motivos privados → **069** |
| 8 | Cancelación cliente | `cancel(byProvider=false)` | Sí | `m23_cancel_own_booking` | Sí | Detalle | Política snapshot → **069** |
| 9 | Cancelación prestador | `cancel(byProvider=true)` | Sí | RPC provider | Sí | Detalle | — |
| 10 | Reprogramación | `reschedule()` atómica | Sí | — | No | Detalle | **069 requerida** |
| 11 | Finalización | `complete()` | Sí | RPC | Parcial | Detalle | Validación horario → **069** |
| 12 | No-show | `noShow()` + grace | Sí | RPC | Parcial | Detalle | Grace en SQL → **069** |
| 13 | Expiración | `expire()` | Sí | — | No | — | **069 requerida** |
| 14 | Historial | `observeBookingHistory` | Sí | Tabla | Parcial | Detalle | RPC list → **069** |
| 15 | Idempotencia | `client_request_id` | Sí | UNIQUE | Sí | — | — |
| 16 | Permisos | Mock actor checks | Sí | `_m23_can_manage` | Sí | — | — |
| 17 | Privacidad | `M23PrivacySanitizer` | Sí | SECURITY DEFINER | Sí | Detalle | — |
| 18 | M06 | `M23BookingNotificationAdapter` | Stub best-effort | — | — | — | Allowlist sin ampliar |
| 19 | M20 | `M23BookingMessagingAdapter` | Sí | — | Pendiente | Botón conversación | Contexto BOOKING en M20 enum |
| 20 | M21 | `M23BookingReviewEligibilityAdapter` | Sí | — | — | Hint elegibilidad | Sin crear reseñas |

## Concurrencia (B8)

068 `m23_create_booking_request`:

1. Validación slot/reglas — sí
2. Validación política — parcial (snapshot vacío)
3. Validación prestador/oferta — sí
4. `pg_advisory_xact_lock(hashtextextended(provider_id, 23))` — sí
5. Overlap SELECT — sí
6. INSERT + historial — sí, misma transacción

**Estrategia:** advisory lock transaccional determinista por `provider_id` + overlap check. Confirmación sin re-check identificada → corrección en **069** con recheck + constraint EXCLUDE opcional.

## Estado post Bloque 3

- Operaciones mock/remoto Kotlin completas salvo RPC pendientes en 069.
- Migración 068 **sin aplicar**.
- Bloque 4 pendiente: paridad remota, aplicación staging, validación 110/110.
