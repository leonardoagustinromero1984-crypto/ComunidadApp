# M07 — Diagnóstico de FAIL matriz SQL staging

**Fecha:** 2026-07-19
**Producto:** LeoVer
**Rama diagnóstico:** `m07/diagnostico-matriz-staging-fails` @ `9aefed3`
**Rama corrección:** `m07/fix-matrix-security-034`
**Matriz origen (histórica):** `scripts/sql/m07_validate_staging_001_033.sql`
**Matriz corregida:** `scripts/sql/m07_validate_staging_001_034.sql`
**Script diagnóstico:** `scripts/sql/m07_diagnose_staging_matrix_fails.sql` (read-only)
**Entorno:** staging / pruebas (ref …`mizz`)
**Apply remoto 034:** **NO** (pendiente)

---

## 0. Evidencia remota definitiva

Función: `public._resolve_invitation_by_token(p_token text)`

| Campo | Valor |
|---|---|
| owner | postgres |
| SECURITY DEFINER | true |
| PUBLIC EXECUTE | **false** |
| anon_execute_direct | **true** |
| anon_execute_effective | **true** |
| authenticated_execute | **true** |
| service_role_execute | **true** |

Conclusión remota:

- `anon` tenía **grant DIRECTO** (no herencia vía PUBLIC).
- Helper interno SECURITY DEFINER: no debe ejecutarse desde `anon` ni exponerse a `authenticated`.
- `service_role` conserva EXECUTE; owner `postgres` conserva privilegios implícitos.

`org_hash_invitation_token`:

| Campo | Valor |
|---|---|
| SECURITY | **INVOKER** |
| search_path | **unset** |
| digest | `extensions.digest` calificado |
| tablas | no accede / no modifica |

---

## 1. Resultados originales (matriz 001–033) — no ocultar

| check_name | expected | actual | status |
|---|---|---|---|
| `internal_writers_anon_execute` | 0 | 1 | FAIL |
| `org_hash_invitation_token_search_path` | `search_path=public` | `<unset>` | FAIL |
| `org_hash_invitation_token(p_token text)_security_definer` | SECURITY DEFINER | INVOKER | FAIL |

Resumen matriz reportado: 261 filas · 249 PASS · 3 FAIL · 3 NOT_EXECUTED · 5 BACKLOG.

---

## 2. Clasificación definitiva

| FAIL | Clasificación | Acción |
|---|---|---|
| `internal_writers_anon_execute` | **DEFECTO REAL** | Migración **034** (REVOKE anon/authenticated; GRANT service_role) |
| `org_hash_…_security_definer` | **FALSO POSITIVO** de matriz | Matriz 001–034 espera INVOKER |
| `org_hash_…_search_path` | **FALSO POSITIVO** de matriz | No exigir `search_path=public` |

No convertir `org_hash_invitation_token` a SECURITY DEFINER.
No agregar `search_path` solo para satisfacer el test.

---

## 3. Decisión 034

Crear `supabase/migrations/034_m07_internal_invitation_helper_grants.sql`:

- `REVOKE ALL` desde PUBLIC, anon, authenticated
- `GRANT EXECUTE` a service_role
- Sin cambio de firma, cuerpo, search_path ni security mode

Estado post-decisión (local validado; remoto pendiente):

```text
CORRECCIÓN 034 VALIDADA LOCALMENTE
APPLY 034 STAGING PENDIENTE
MATRIZ SQL STAGING PENDIENTE DE REEJECUCIÓN
VALIDACIÓN STAGING PENDIENTE
RELEASE BLOQUEADO
```

Detalle: `docs/04-calidad/M07-reporte-correccion-matriz-034.md`.
