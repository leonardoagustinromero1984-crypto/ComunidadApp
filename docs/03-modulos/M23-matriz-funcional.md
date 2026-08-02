# M23 — Matriz funcional

| Capacidad | Bloque 1 | Bloque 2 |
|---|---|---|
| Reglas de disponibilidad | Mock | Tabla `m23_availability_rules` + RPC |
| Excepciones agenda | Mock | Tabla `m23_availability_exceptions` + RPC |
| Slots calculados | `M23SlotGenerator` | RPC `m23_get_public_available_slots`, sin materialización |
| Reservas lifecycle | Mock + dominio | Tabla `m23_bookings` + RPC autorizada |
| Historial | Mock map | Tabla `m23_booking_history` interna |
| Políticas | Snapshot en reserva | JSONB policy_snapshot |
| Idempotencia | client_request_id mock | UNIQUE constraint |
| Anti solapamiento | Dominio | RPC transaccional |
| Privacidad | Sanitizer | RPC SECURITY DEFINER |
| Pagos | No | No |
| M06 | Stub | Stub |
| M21 | Adapter stub | Adapter stub |

## Estado

- Bloque 1 y Bloque 2 implementados localmente.
- `068_m23_scheduling_availability_and_bookings.sql` fue creada y **no aplicada**.
- Bloque 3 no iniciado.
- M24 no iniciado; M23 no contiene pagos.
