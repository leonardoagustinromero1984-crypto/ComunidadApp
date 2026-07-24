# M12 — Agenda, disponibilidad y turnos (Bloque 3)

**Estado:** implementado en repo · migración **047** creada · **no aplicada remotamente**.

## Auditoría (clasificación)

| Área | Clasificación | Nota |
|------|---------------|------|
| M08 `m08_actor_has_active_responsibility` | REUTILIZABLE | Gate de solicitud de turno por mascota |
| M03 `has_org_permission` / org ACTIVE | REUTILIZABLE | Gestión de agenda y turnos |
| M07 `m07_best_effort_audit` | REUTILIZABLE | Transiciones y settings |
| M06 hooks de evento | ADAPTABLE | Contratos preparados, sin push |
| Perfiles/servicios M12 B2 (046) | REUTILIZABLE | Clínica, profesional, servicio |
| Legacy `service_profiles` / `bookings` | LEGACY_COMPATIBLE | Intactos; no se reutilizan para M12 |
| Pagos / historia clínica | FUERA_DE_ALCANCE | Bloque 3 no los implementa |
| Tabla de slots pre-generados | AUSENTE a propósito | Slots = proyección calculada en RPC |

## Modelos

- `VeterinaryScheduleSettings` — zona IANA, horizonte, avisos, duración default.
- `VeterinaryAvailabilityRule` — recurrente por día / profesional / servicio.
- `VeterinaryAvailabilityException` — `CLOSED` \| `CUSTOM_HOURS` \| `CAPACITY_OVERRIDE`.
- `VeterinaryAppointmentSlot` — proyección (no persistida).
- `VeterinaryAppointment` + estados y `VeterinaryAppointmentStatusHistory`.

## Privacidad

`requestNote` y notas operativas son privadas, con límite de longitud. No son historia clínica ni diagnóstico. No aparecen en listados públicos del directorio.

## Autoridad M08

La RPC de solicitud toma el actor solo de `auth.uid()`. Valida autoridad vía `m08_actor_has_active_responsibility` (con fallback de responsabilidades ACTIVE / custodia org). No confía en `requester_user_id` del cliente.

## Cupos y concurrencia

Consumen cupo: `REQUESTED`, `CONFIRMED`. Liberan: rechazo, cancelaciones, `EXPIRED`.  
Antes de insertar: `FOR UPDATE` sobre `veterinary_schedule_settings` + recuento en la misma transacción. Android no envía capacidad confiable.

## RPC (22 cliente)

Settings/availability: upsert/get settings, CRUD reglas/excepciones + status, list managed availability, list available slots.  
Turnos: request, get, list mine/managed, confirm, reject, cancel my/managed, complete, no-show, expire, history.

## RLS / grants

Cinco tablas con RLS; sin DML directo para `anon`/`authenticated`. Helpers `_m12_*` revocados. Cliente: `GRANT EXECUTE` solo a `authenticated`. `SECURITY DEFINER` + `search_path = public`.

## Permisos

`veterinary.schedule.read|manage`, `veterinary.appointment.read|request|manage`.

## UI

Reserva, mis turnos, detalle, agenda gestionada, settings, reglas, gestión de turno. Sin disponibilidad ficticia.

## Tests

`M12VeterinaryAppointmentsTest`, `M12VeterinaryAppointmentMappingTest`, `M12VeterinaryAppointmentMigrationGuardsTest` (+ regresión B2 / M08 focalizada).

## Límites

Sin pagos, señas, Mercado Pago, historia clínica, diagnóstico, recetas, laboratorio, chat, video ni push real. No afirmar aplicación remota de 047.
