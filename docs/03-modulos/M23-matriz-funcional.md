# M23 — Matriz funcional

| Capacidad | Bloque 1 | Bloque 2 | Bloque 3 |
|---|---|---|---|
| Reglas de disponibilidad | Mock | Tabla + RPC | Admin mock + conflicto |
| Excepciones agenda | Mock | Tabla + RPC | Idempotencia mock |
| Slots calculados | `M23SlotGenerator` | RPC público | — |
| Reservas lifecycle | Mock + dominio | Tabla + RPC | Operaciones completas |
| Historial | Mock map | Tabla interna | UI + mock append |
| Reprogramación | — | — | Atómica mock |
| Expiración | — | — | Dominio + mock |
| Filtros agenda | — | — | Cliente/prestador |
| M20 contextual | — | — | Adaptador |
| M21 elegibilidad | Stub | Stub | Adaptador COMPLETED |
| Pagos | No | No | No |

## Estado

- Bloques 1–3 implementados localmente.
- `068_m23_scheduling_availability_and_bookings.sql` creada, **no aplicada**.
- Bloque 4 pendiente (paridad remota, staging).
- M24 no iniciado.
