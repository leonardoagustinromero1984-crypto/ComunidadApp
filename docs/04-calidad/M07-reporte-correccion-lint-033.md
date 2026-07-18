# M07 — Reporte corrección lint migración 033

**Fecha:** 2026-07-18  
**Producto:** LeoVer  
**Rama:** `m07/correccion-lint-staging-033`  
**Commit base:** `949625288e149e2d709f8d704bd4188d662f294b`  
**Migración:** `supabase/migrations/033_m07_staging_lint_runtime_fixes.sql`  
**Project ref staging (últimos 4):** `mizz`  

```text
VALIDACIÓN LOCAL 001–033 PASS
CORRECCIÓN 033 LISTA PARA STAGING
VALIDACIÓN STAGING PENDIENTE
RELEASE BLOQUEADO
```

Apply remoto 033: **NO** · STAGING PASS: **NO** · Producción: **NO** tocada.

---

## 1. Contexto remoto previo

| Hecho | Valor |
|---|---|
| Reset remoto 001–032 | PASS (exit 0, fuera de esta sesión) |
| Historial remoto | Local/Remote 001–032 sincronizados |
| Backup | `C:\Users\Supervielle\LeoVerBackups\2026-07-18_pre_M07` (fuera del repo) |
| `db lint` remoto | **FAIL** — 7 errores bloqueantes |
| Migraciones 001–032 | **no reeditadas** |

---

## 2. Tabla L05–L11

| ID | Función | SQLSTATE | Causa | Corrección | Prueba | Resultado |
|---|---|---|---|---|---|---|
| L05 | `is_username_available` | 42704 | `u::citext` con `search_path=public` | `u::extensions.citext` | `is_username_available('lint_test_user_033')` | PASS |
| L06 | `add_reputation_points` | 42702 | `badge_type` ambiguo param/columna | `v_badge_type` + `ON CONFLICT ON CONSTRAINT user_badges_user_id_badge_type_key` | `PERFORM add_reputation_points(...,0,NULL)` | PASS |
| L07 | `complete_profile_onboarding` | 42704 | `u::citext` | `u::extensions.citext` | def contiene `extensions.citext` | PASS |
| L08 | `org_hash_invitation_token` | 42883 | `digest(...)` sin schema | `extensions.digest(...)` | hash sha256 len=64 idempotente | PASS |
| L08b | `_resolve_invitation_by_token` | 0A000 | marcada `STABLE` con `UPDATE` | recreada **VOLATILE** (sin `stable`) | `provolatile=v` | PASS |
| L09 | `invite_organization_member` | 42883 | `gen_random_bytes` sin schema | `extensions.gen_random_bytes(32)` | def contiene calificación | PASS |
| L10 | `m06_claim_outbox` | 0A000 | CTE+UPDATE dentro de `COALESCE` | `WITH ... SELECT ... INTO v_result` | retorna `jsonb`; grants service_role | PASS |
| L11 | `m06_claim_push_deliveries` | 0A000 | igual que L10 | mismo patrón top-level | retorna `jsonb`; grants service_role | PASS |

Notas:

- `_resolve_invitation_by_token` no se tocó por digest (L08); se corrigió el defecto propio STABLE+UPDATE (L08b) que el lint bloqueó tras L08.
- Claims con actor autenticado: ejecución end-to-end **no ejecutada** sin JWT; forma/SKIP LOCKED/límites conservados en SQL.

---

## 3. Advertencias (no bloqueantes)

Lint local post-033: **0 errors**, **22 warnings** (backlog).

| Clase | Ejemplos | Acción |
|---|---|---|
| Variables actor no leídas | `list_moderation_*`, `m07_set_legal_hold`, … | Backlog |
| IMMUTABLE con expresión STABLE | `m05_build_storage_path`, `m07_retention_until`, `m07_sanitize_health_details` | Backlog |
| STABLE con expresión VOLATILE | `m07_list_security_events`, `m07_list_application_errors`, `m07_list_audit_events` | Backlog |
| Parámetro no usado | `m06_claim_push_deliveries.p_worker_id` | Backlog (firma pública conservada) |

Ninguna warning se “silenció” como PASS; `--fail-on error` con exit 0.

---

## 4. Validación local

| Paso | Resultado |
|---|---|
| 1º `db reset --local` | APPLY OK 001–033 |
| 1º `db lint --local --level warning --fail-on error` | exit **0**, 0 errors |
| 2º `db reset --local` | APPLY OK 001–033 |
| 2º lint | exit **0**, 0 errors |
| Historial | 33 versiones, max **033**, 0 dupes |
| Catálogos | 118 / 28 / 14 · permisos 8 |
| Android | 544 tests · assemble/lint/jacoco SUCCESS |
| `m07_quality_checks.sh` | **PASSED** (highest 032\|033) |

---

## 5. Deudas / bloqueos

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
USERNAME NO REVALIDADO — STAGING PENDIENTE
RELEASE BLOQUEADO
```

AuthRepository / domain/auth / UsernameValidators: **intactos**.  
M08 no iniciado · main no tocado · apply remoto 033 pendiente del propietario.
