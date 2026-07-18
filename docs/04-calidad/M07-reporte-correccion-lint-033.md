# M07 — Reporte corrección lint migración 033

**Fecha:** 2026-07-18
**Producto:** LeoVer
**Rama:** `m07/correccion-lint-staging-033`
**Commit:** `9f5cd74150b2f058b0ac40e9ec0f3609e8c3fdcb`
**Migración:** `supabase/migrations/033_m07_staging_lint_runtime_fixes.sql`
**Project ref staging (últimos 4):** `mizz`

```text
VALIDACIÓN LOCAL 001–033 PASS
APPLY REMOTO 033 PASS
HISTORIAL REMOTO 033 REGISTRADO
DB LINT REMOTO PASS — 0 ERRORES
WARNINGS NO BLOQUEANTES: BACKLOG (~22)
MATRIZ SQL STAGING PENDIENTE DE EJECUCIÓN
RELEASE BLOQUEADO
```

STAGING PASS completo: **NO** (falta matriz SQL). Producción: **NO** tocada.

---

## 1. Contexto remoto

| Hecho | Valor |
|---|---|
| Reset remoto 001–032 | PASS |
| Apply remoto 033 | **PASS** |
| Historial remoto | Local/Remote **001–033** alineado; max **033**; 0 faltantes; 0 duplicadas |
| Backup | fuera del repo (`LeoVerBackups`, pre-M07 / pre-033) |
| `db lint` remoto | **PASS** — exit 0 · 0 errores · ~22 warnings |
| Migraciones 001–032 | **no reeditadas** |
| Migración 034 | **no creada** |

Comando lint confirmado por propietario:

```text
supabase db lint --linked --level warning --fail-on error
```

---

## 2. Tabla L05–L11

| ID | Función | SQLSTATE | Causa | Corrección | Prueba local | Resultado |
|---|---|---|---|---|---|---|
| L05 | `is_username_available` | 42704 | `u::citext` con `search_path=public` | `u::extensions.citext` | `is_username_available(...)` | PASS |
| L06 | `add_reputation_points` | 42702 | `badge_type` ambiguo | `v_badge_type` + constraint estable | `PERFORM add_reputation_points(...,0,NULL)` | PASS |
| L07 | `complete_profile_onboarding` | 42704 | `u::citext` | `u::extensions.citext` | def contiene `extensions.citext` | PASS |
| L08 | `org_hash_invitation_token` | 42883 | `digest` sin schema | `extensions.digest` | hash sha256 | PASS |
| L08b | `_resolve_invitation_by_token` | 0A000 | `STABLE` + `UPDATE` | **VOLATILE** | `provolatile=v` | PASS |
| L09 | `invite_organization_member` | 42883 | `gen_random_bytes` sin schema | `extensions.gen_random_bytes` | def calificada | PASS |
| L10 | `m06_claim_outbox` | 0A000 | CTE+UPDATE en `COALESCE` | CTE top-level + `INTO` | jsonb / SKIP LOCKED | PASS |
| L11 | `m06_claim_push_deliveries` | 0A000 | igual L10 | mismo patrón | jsonb / SKIP LOCKED | PASS |

---

## 3. Advertencias (no bloqueantes) — BACKLOG

Lint remoto/local post-033: **0 errors**, **~22 warnings**.

| Clase | Ejemplos | Acción |
|---|---|---|
| Variables actor / `v_actor` no leídas | listados M07, legal hold, … | Backlog |
| Parámetro no usado | `m06_claim_push_deliveries.p_worker_id` | Backlog (firma pública conservada) |
| IMMUTABLE con expresión STABLE | `m07_retention_until`, `m07_sanitize_health_details`, … | Backlog |
| STABLE con expresión VOLATILE | `m07_list_*` | Backlog |

Ninguna warning se silenció como PASS. **No** migración 034 en esta tarea.

Clasificación:

```text
DB LINT REMOTO: PASS — 0 ERRORES
WARNINGS NO BLOQUEANTES: BACKLOG
```

---

## 4. Validación local (previa al apply remoto)

| Paso | Resultado |
|---|---|
| 2× `db reset --local` | APPLY OK 001–033 |
| 2× lint local `--fail-on error` | exit **0**, 0 errors |
| Historial | 33 · max **033** · 0 dupes |
| Catálogos | 118 / 28 / 14 · permisos 8 |
| Android / quality | PASS |

---

## 5. Matriz SQL staging (siguiente paso)

| Artefacto | Estado |
|---|---|
| Script validación | `scripts/sql/m07_validate_staging_001_033.sql` |
| Guía | `docs/04-calidad/M07-ejecucion-matriz-sql-staging-001-033.md` |
| Ejecución remota | **PENDIENTE** (manual SQL Editor; no desde Cursor) |

---

## 6. Deudas / bloqueos

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
USERNAME NO REVALIDADO — STAGING PENDIENTE
RELEASE BLOQUEADO
```

AuthRepository / domain/auth / UsernameValidators: **intactos**.
M08 no iniciado · main no tocado.
