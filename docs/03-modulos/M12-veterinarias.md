# M12 — Veterinarias

**Estado del módulo:** Bloque 1–2 cerrados · **Bloque 3 (agenda/turnos) entregado en repo**.
**Migración 046:** aplicada y validada en Supabase de pruebas (Bloque 2).
**Migración 047:** creada en repo — **no aplicada remotamente** desde Cursor.

> Nota D01: M12 técnico (Veterinarias) ≠ M12 producto (mascotas perdidas). Ver nota en D01.

## Bloque 1 — dominio y directorio local

Modelos, fakes, directorio público, errores tipados, permisos `veterinary.*` (IDs).

## Bloque 2 — persistencia de perfiles y servicios

- SQL: `046_m12_veterinary_profiles_and_services.sql` (6 tablas, RLS, 26 RPC).
- Android: mocks B2 + `SupabaseVeterinary*` (RPC).
- UI: hub de gestión, profesionales, servicios, horarios.
- Docs: `M12-persistencia-perfiles-servicios.md`, `M12-aplicacion-migracion-046-supabase.md`.

## Bloque 3 — agenda, disponibilidad y solicitudes de turno

- SQL: `047_m12_veterinary_appointments_and_availability.sql` (5 tablas, RLS, 22 RPC cliente, permisos schedule/appointment).
- Slots calculados (sin pre-generación masiva); cupo transaccional; autoridad M08.
- Android: `VeterinaryScheduleRepository` / `VeterinaryAppointmentRepository` (Mock + Supabase).
- UI: reserva, mis turnos, agenda gestionada, settings, reglas.
- Docs: `M12-agenda-disponibilidad-turnos.md`, `M12-aplicacion-migracion-047-supabase.md`.
- Sin pagos ni historia clínica. Hooks M06 preparados (sin push).

## Integraciones

| Módulo | Uso |
|--------|-----|
| M03 | org ACTIVE, sede, `has_org_permission` |
| M04 | `organizations.review_verification` en `m12_review_*` (B2) |
| M05 | logo/cover/avatar |
| M06 | hooks de turno/recordatorio (contrato, sin push) |
| M07 | `m07_best_effort_audit` |
| M08 | autoridad de mascota para solicitar turno |

## Límites

Sin pagos, señas, checkout, Mercado Pago, historia clínica, diagnóstico, recetas, laboratorio, chat, video ni push real. Legacy `service_profiles` / `bookings` intactos.

## Plan Bloque 4 (exacto, no iniciado)

1. Recordatorios M06 operativos (sin inventar canal no aprobado).
2. Endurecimiento operativo de agenda (excepciones masivas / reportes livianos si el prompt lo define).
3. Integraciones de experiencia (seguimiento de solicitud) sin pagos ni HC.
4. Tests + migración siguiente solo si el prompt de Bloque 4 lo exige.
