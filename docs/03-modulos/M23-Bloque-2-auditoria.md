# M23 Bloque 2 — Auditoría

## Alcance implementado

- Migración forward-only `068_m23_scheduling_availability_and_bookings.sql`, creada y **no aplicada**.
- Tablas internas para reglas, excepciones, reservas e historial, con FK a M22, M01 y M03.
- RLS activo, sin `SELECT` directo para `anon` ni `authenticated`; Android usa exclusivamente RPC.
- RPC `SECURITY DEFINER` con `auth.uid()` y `search_path = public`.
- Creación atómica con lock por prestador y detección de solapamiento; `client_request_id` es idempotente por cliente.
- Slots calculados en RPC: no existe tabla de materialización ni campos de pago.
- Respuesta pública sanitizada: no devuelve `customer_user_id`, notas privadas ni `organization_id`.
- Repositorios Kotlin Supabase seleccionados por `DataProvider.useSupabase`; el mock del Bloque 1 permanece sin cambios funcionales.

## Límites conocidos

- Reprogramación remota no forma parte de las RPC de este bloque: devuelve `M23_RESCHEDULE_NOT_AVAILABLE`.
- La política se guarda como snapshot JSONB vacío preparado para su parametrización posterior.
- M06 y M21 conservan sus adapters/stubs del Bloque 1.
- M24 no está iniciado; no hay cobros, pagos, reembolsos ni comisiones.
