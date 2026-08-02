# M23 Bloque 4 — Auditoría paridad remota

## HEAD inicial Bloque 4

`0377e984...` — `feat(m23): add booking operations and provider agenda`

## Decisión 069

**Requerida.** Motivos:

| Brecha 068 | Corrección 069 |
|---|---|
| Confirm sin re-check solapamiento | `m23_confirm_booking` reescrita |
| Reject sin motivos | `m23_reject_booking(uuid,text,text)` |
| Sin reprogramación RPC | `m23_reschedule_booking` atómica |
| Sin expiración | `m23_expire_booking` |
| Sin historial RPC | `m23_list_booking_history` |
| Sin `rescheduled_from_booking_id` | Columna + FK |
| Sin `pet_id` | Columna FK M08 |
| Concurrencia solo advisory lock | + EXCLUDE gist REQUESTED/CONFIRMED |
| `m23_list_provider_bookings` plpgsql inválido | Corregido en 068 pre-aplicación (`language sql`) |

## Concurrencia validada

1. Creación: advisory lock + overlap SELECT + UNIQUE client_request_id
2. Confirmación: FOR UPDATE + advisory lock + overlap recheck
3. Reprogramación: transacción única cancel+insert + lock
4. EXCLUDE `(provider_id, tstzrange)` WHERE activo — defensa en profundidad

## Paridad Kotlin vs RPC post-069

| Operación | Kotlin | RPC 068/069 | Brecha |
|---|---|---|---|
| Slots | Sí | Sí | — |
| Crear REQUESTED | Sí | Sí | — |
| Confirmar | Sí | 069 | — |
| Rechazar | Sí | 069 | — |
| Cancelar | Sí | Sí | Política snapshot parcial |
| Reprogramar | Sí | 069 | — |
| Completar | Sí | 069 | — |
| No-show | Sí | 069 | — |
| Expirar | Sí | 069 | — |
| Historial | Sí | 069 | — |

## Staging

- Proyecto: `wystsapjfpdtoprlmizz` (no producción)
- 068 aplicada manualmente vía SQL Editor/CLI
- 069 aplicada tras 068
- 039–052 no aplicadas (deuda documentada)

## M06 / M20 / M21 / M24

- M06: hooks stub; allowlist sin ampliar
- M20: contexto BOOKING en cliente; RPC M20 sin cambio
- M21: adaptador elegibilidad COMPLETED; sin reseñas auto
- M24: no iniciado; sin pagos
