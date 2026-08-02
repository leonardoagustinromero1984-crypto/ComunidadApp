# M23 — Matriz funcional

| Capacidad | Bloque 1 | Bloque 2 |
|---|---|---|
| Reglas de disponibilidad | Mock | Tabla `m23_availability_rules` |
| Excepciones agenda | Mock | Tabla `m23_availability_exceptions` |
| Slots calculados | `M23SlotGenerator` | RPC `m23_get_public_available_slots` |
| Reservas lifecycle | Mock + dominio | Tabla `m23_bookings` + RPC |
| Historial | Mock map | Tabla `m23_booking_history` |
| Políticas | Snapshot en reserva | JSONB policy_snapshot |
| Idempotencia | client_request_id mock | UNIQUE constraint |
| Anti solapamiento | Dominio | RPC transaccional |
| Privacidad | Sanitizer | RPC SECURITY DEFINER |
| Pagos | No | No |
| M06 | Stub | Stub |
| M21 | Adapter stub | Adapter stub |
