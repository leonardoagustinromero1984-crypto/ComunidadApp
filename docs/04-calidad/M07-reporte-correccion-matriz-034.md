# M07 — Reporte corrección matriz y migración 034

**Fecha:** 2026-07-19
**Producto:** LeoVer
**Rama:** `m07/fix-matrix-security-034`
**Migración:** `supabase/migrations/034_m07_internal_invitation_helper_grants.sql`
**Matriz histórica:** `scripts/sql/m07_validate_staging_001_033.sql` (conservada)
**Matriz nueva:** `scripts/sql/m07_validate_staging_001_034.sql`
**Apply remoto 034:** **NO** (pendiente)

---

## Defecto

`public._resolve_invitation_by_token(text)` (SECURITY DEFINER) tenía **EXECUTE directo** residual a `anon` en staging (`anon_execute_direct=true` con `PUBLIC EXECUTE=false`).

## Función

| Atributo | Valor |
|---|---|
| Firma | `public._resolve_invitation_by_token(p_token text)` |
| Modo | SECURITY DEFINER (sin cambio) |
| search_path | `public` (sin cambio) |
| Cuerpo | sin cambio |

## Grants previos (staging remoto)

| Rol | EXECUTE |
|---|---|
| PUBLIC | false |
| anon (directo) | **true** |
| anon (efectivo) | **true** |
| authenticated (efectivo) | **true** |
| service_role | true |

## Grants corregidos (034 + validación local)

| Rol | EXECUTE |
|---|---|
| PUBLIC | **false** |
| anon (directo) | **false** |
| anon (efectivo) | **false** |
| authenticated (efectivo) | **false** |
| service_role | **true** |

## Migración 034

Mínima: REVOKE PUBLIC/anon/authenticated + GRANT service_role. No edita 001–033.

## Matriz 001–034

Cambios vs 001–033:

- Historial: 34 versiones / máxima 034 / 0 duplicadas / 0 faltantes
- Writers internos: `_resolve…` sin EXECUTE anon/authenticated
- `org_hash_invitation_token`: INVOKER + `extensions.digest` + sin table access + grants (service_role)
- Sin convertir NOT_EXECUTED/BACKLOG legítimos a PASS

## Validación local

| Paso | Resultado |
|---|---|
| Reset 1 001–034 | **PASS** (`RESET1_EXIT=0`) |
| Lint 1 (`--fail-on error`) | **PASS** (`LINT1_EXIT=0`, 0 errores) |
| Reset 2 001–034 | **PASS** (`RESET2_EXIT=0`) |
| Lint 2 | **PASS** (`LINT2_EXIT=0`, 0 errores) |
| Historial local | **34** / max **034** / dup **0** |
| Matriz local 001–034 | total=**266** · PASS=**258** · FAIL=**0** · NOT_EXECUTED=**3** · BACKLOG=**5** · `runner_summary` **PASS** |
| Android `testDebugUnitTest` | **559** tests · **0** failures |
| `assembleDebug` / `lintDebug` | **PASS** (`GRADLE_EXIT=0`) |

## Clasificación de los 3 FAIL originales

| Check | Clasificación |
|---|---|
| `internal_writers_anon_execute` | DEFECTO REAL → 034 |
| `org_hash_…_security_definer` | FALSO POSITIVO → matriz corregida |
| `org_hash_…_search_path` | FALSO POSITIVO → matriz corregida |

## Apply remoto

**PENDIENTE** — no `db push`, no reset remoto, no migration repair en esta tarea.
