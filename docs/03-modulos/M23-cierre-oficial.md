# M23 — Cierre oficial

**Fecha:** 2026-08-02  
**Entorno validado:** staging `wystsapjfpdtoprlmizz` (no producción)

## Resumen

M23 Agenda y reservas queda **cerrado oficialmente** tras Bloques 1–4:

| Bloque | Commit | Alcance |
|---|---|---|
| 1 | `4051a81` | Fundación mock, dominio, UI base |
| 2 | `ce84f21` | Migración 068, repositorios Supabase |
| 3 | `0377e98` | Operaciones completas, agenda, M20/M21/M06 |
| 4 | `e1379e5` | 069, staging, validación 110/110 |
| cierre | *(HEAD post-smoke)* | smoke 25/25 script dedicado, cierre oficial documental |

## HEAD cierre oficial

Ver commit de cierre post-smoke en `main` (incluye `scripts/ops/m23_remote_smoke_25.sql` y evidencia documental).

Commit técnico Bloque 4: `e1379e5717e596a37f866e999d3c3cb7eef8f6c2`

## Migraciones

- `068_m23_scheduling_availability_and_bookings.sql` — **APLICADA staging**
- `069_m23_booking_operations_and_concurrency.sql` — **APLICADA staging**
- Fix pre-aplicación 068: `m23_list_provider_bookings` → `language sql`

## Concurrencia

Advisory lock transaccional + EXCLUDE gist + idempotencia `client_request_id`.

## Integraciones

- **M22:** FK prestador/oferta/sede
- **M08:** `pet_id` opcional
- **M20:** conversación contextual BOOKING (cliente)
- **M21:** elegibilidad post-COMPLETED (adaptador, sin auto-reseña)
- **M06:** hooks best-effort, allowlist sin ampliar
- **M24:** explícitamente fuera de alcance; sin pagos

## Validación

- SQL/RLS/privacidad: **110/110 PASS** (2026-08-02) — script `m23_remote_validation_068_069.sql`
- Smoke remoto repositorio: **25/25 PASS** (2026-08-02) — script `m23_remote_smoke_25.sql`
- Tests unitarios M23: **84/84 PASS**

## Incidencias transitorias

Documentadas en `M23-Bloque-4-validacion.md`. Resueltas; no bloquean cierre.

## Deuda no bloqueante

- Migraciones locales 039–052 sin registro remoto (documentada globalmente)
- Política cancelación desde `policy_snapshot` — enforcement parcial en SQL
- M06 notificaciones de reserva diferidas a allowlist futura

## M24

Preauditoría documentada; implementación **no iniciada**.

## Producción

Sin cambios SQL ni despliegue Android de cierre M23 en producción.
