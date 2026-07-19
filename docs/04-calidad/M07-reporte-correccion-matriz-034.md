# M07 — Reporte corrección matriz y migración 034

**Fecha:** 2026-07-19  
**Producto:** LeoVer  
**Rama origen fix:** `m07/fix-matrix-security-034` @ `e4574fa`  
**Migración:** `supabase/migrations/034_m07_internal_invitation_helper_grants.sql`  
**Matriz histórica:** `scripts/sql/m07_validate_staging_001_033.sql` (conservada)  
**Matriz nueva:** `scripts/sql/m07_validate_staging_001_034.sql`

---

## Estado

```text
CORRECCIÓN 034 STAGING PASS
DEFECTO DE GRANTS CERRADO
```

Sin migración **035**.

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

## Grants posteriores (post-034 remoto + matriz)

| Rol | EXECUTE |
|---|---|
| PUBLIC | **false** |
| anon (directo) | **false** |
| anon (efectivo) | **false** |
| authenticated (efectivo) | **false** |
| service_role | **true** |

---

## Apply remoto 034

| Paso | Resultado |
|---|---|
| Backup previo | schema.sql · data.sql · roles.sql |
| Dry-run | **solo** `034_m07_internal_invitation_helper_grants.sql` |
| Apply | **PASS** (exit 0) |
| Historial | 034 registrado · Local/Remote 001–034 · 34 / max 034 · 0 dup · 0 faltantes |
| `db lint` remoto | **PASS** — exit 0 · 0 errores · warnings backlog |
| Matriz remota 001–034 | **PASS** — 0 FAIL (ver resultado CSV) |

---

## Clasificación de los 3 FAIL originales (001–033)

| Check | Clasificación | Cierre |
|---|---|---|
| `internal_writers_anon_execute` | DEFECTO REAL | 034 + matriz remota PASS |
| `org_hash_…_security_definer` | FALSO POSITIVO | matriz 001–034 espera INVOKER |
| `org_hash_…_search_path` | FALSO POSITIVO | no exigir search_path |

---

## Validación local (previa al apply)

| Paso | Resultado |
|---|---|
| Reset ×2 001–034 | **PASS** |
| Lint ×2 (`--fail-on error`) | **PASS** |
| Matriz local 001–034 | 0 FAIL · `runner_summary` PASS |
| Android 559 / assemble / lint | **PASS** |

## Matriz remota final

Ver `docs/04-calidad/M07-resultado-matriz-sql-staging-001-034.md`:  
CSV 267 / 259 PASS / **0 FAIL**; `runner_summary` PASS.
