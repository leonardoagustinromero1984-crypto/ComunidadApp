# M12 — Persistencia perfiles y servicios (Bloque 2)

**Migración:** `046_m12_veterinary_profiles_and_services.sql`  
**Estado apply remoto:** **NO aplicada** desde este bloque (solo archivo en repo).

## Auditoría / patrones

| Pieza | Clasificación |
|-------|----------------|
| `has_org_permission` / `organization_permissions` | REUTILIZADO |
| Unicidad ACTIVE org+sede (sentinel UUID) | REUTILIZADO (como 042) |
| RLS deny mutations + RPC SECURITY DEFINER | REUTILIZADO |
| Revoke table ALL (estilo 044) | ADAPTADO |
| `m04_require_permission('organizations.review_verification')` | REUTILIZADO |
| M05 `m05://` / `file_asset:` | REUTILIZADO |
| `m07_best_effort_audit` | REUTILIZADO |
| `service_profiles` / bookings | PRESERVADO_LEGACY |
| Agenda/turnos/HC/pagos | NO_APLICA |

## Tablas

1. `veterinary_clinic_profiles`
2. `veterinary_professionals`
3. `veterinary_clinic_professionals`
4. `veterinary_professional_specialties`
5. `veterinary_services`
6. `veterinary_opening_hours`

## RPC cliente (26)

Clínicas, profesionales, servicios y horarios `m12_*` — EXECUTE solo `authenticated`; helpers `_m12_*` sin EXECUTE a roles de cliente.

## Android

- Fakes: `VeterinaryBlock2Repositories` + store B1.
- Remoto: `SupabaseVeterinaryM12RemoteDataSource` + `SupabaseVeterinaryRepositories`.
- `DataProvider` selecciona mock vs Supabase.
- UI gestión: profesionales / servicios / horarios / hub (activar, pausar, archivar, solicitar verificación).

## Límites

Sin turnos reales, historia clínica ni pagos. `requires_appointment` es flag de servicio, no agenda.
