# M12 — Veterinarias

**Estado del módulo:** Bloque 1 cerrado · **Bloque 2 (persistencia) en curso / entregado en repo**.
**Migración 046:** creada en repo — **no aplicada remotamente** desde Cursor.

> Nota D01: M12 técnico (Veterinarias) ≠ M12 producto (mascotas perdidas). Ver nota en D01.

## Bloque 1 — dominio y directorio local

Modelos, fakes, directorio público, errores tipados, permisos `veterinary.*` (IDs).

## Bloque 2 — persistencia

- SQL: `046_m12_veterinary_profiles_and_services.sql` (6 tablas, RLS, 26 RPC, permisos sembrados).
- Android: mocks B2 + `SupabaseVeterinary*` (RPC).
- UI: hub de gestión, profesionales, servicios, horarios.
- Docs: `M12-persistencia-perfiles-servicios.md`, `M12-aplicacion-migracion-046-supabase.md`.

## Integraciones

| Módulo | Uso |
|--------|-----|
| M03 | org ACTIVE, sede, `has_org_permission` |
| M04 | `organizations.review_verification` en `m12_review_*` |
| M05 | logo/cover/avatar |
| M06/M07 | hooks / `m07_best_effort_audit` |

## Límites

Sin agenda/turnos reales, historia clínica ni pagos. Legacy `service_profiles` intacto.

## Plan Bloque 3 (exacto)

1. Agenda/disponibilidad (sin pagos).
2. Solicitud de turno y estados (sin cobro).
3. Notificaciones M06 de recordatorio.
4. Proyecciones de cupos sin inventar disponibilidad ficticia.
5. Tests + migración siguiente (047+) si hace falta.
