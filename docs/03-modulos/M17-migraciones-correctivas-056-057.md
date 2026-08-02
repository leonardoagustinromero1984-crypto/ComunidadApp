# M17 — Migraciones correctivas 056 y 057

**Contexto:** Las migraciones **054** y **055** fueron aplicadas manualmente en staging antes del registro en `schema_migrations`. No se modificaron retroactivamente para preservar el historial forward-only y evitar divergencia con entornos que ya ejecutaron SQL parcial.

## Migración 056 — `056_m17_fix_moderator_helper.sql`

| Campo | Detalle |
|-------|---------|
| **Defecto** | `_m17_is_moderator` en 054 referenciaba `platform_role_assignments`, tabla inexistente en M02 |
| **Autoridad correcta** | `user_role_assignments` + `user_has_active_role` (migración 018) |
| **Roles válidos** | `MODERATOR`, `ADMIN`, `SUPERADMIN` |
| **Síntoma** | Error `42P01: relation "platform_role_assignments" does not exist` al aplicar 054 |
| **Corrección** | `CREATE OR REPLACE` de `_m17_is_moderator` en 056 |
| **Aplicada staging** | Sí |
| **Registrada** | Sí (`schema_migrations` versión 056) |

## Migración 057 — `057_m17_fix_volunteer_public_list.sql`

| Campo | Detalle |
|-------|---------|
| **Defecto** | `m17_list_public_volunteer_opportunities` usaba alias `o.id` fuera del scope del loop (`v_row` es el record) |
| **Síntoma** | `missing FROM-clause entry for table "o"` en RPC pública de voluntariado |
| **Corrección** | `return next public._m17_public_volunteer_opp_json(v_row.id)` |
| **Aplicada staging** | Sí |
| **Registrada** | Sí (`schema_migrations` versión 057) |

## Por qué no editar 054/055

1. Ya aplicadas en staging (manual).
2. Forward-only: correcciones vía migraciones incrementales.
3. Reconciliación de historial vía script de validación (`m17_remote_validation_054_055.sql`).

## Validación posterior

- Script: `scripts/ops/m17_remote_validation_054_055.sql`
- Resultado staging: **120/120 PASS**
- Documentación: `M17-cierre-global-validacion.md`, `M17-cierre-oficial.md`

## Estado M17

**CIERRE OFICIAL COMPLETADO** — producción no afectada; pagos reales diferidos a M24.
