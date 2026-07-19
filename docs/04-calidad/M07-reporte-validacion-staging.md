# M07 — Reporte de validación staging

**Fecha:** 2026-07-19  
**Producto:** LeoVer  
**Rama cierre:** `m07/cierre-final-staging-001-034`  
**Commit migración 034:** `e4574fa`  
**Project ref staging (últimos 4):** `mizz`  
**Entorno objetivo:** staging no productivo  
**Release:** **RELEASE BLOQUEADO** (decisión de producto / deudas restantes; **no** fallo de validación staging M07)

---

## 0. Estado declarado (obligatorio)

```text
VALIDACIÓN STAGING PASS
M07 CERRADO EN STAGING
APPLY STAGING 001–034 PASS
DB LINT STAGING PASS
MATRIZ SQL STAGING 001–034 PASS
CORRECCIÓN 034 STAGING PASS
DEFECTO DE GRANTS CERRADO
USERNAME REVALIDADO — PASS
EMAIL OTP 8 DÍGITOS — PASS
SMOKE TEST APK STAGING — PASS
RELEASE BLOQUEADO
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
WARNINGS DB LINT NO BLOQUEANTES — BACKLOG
```

Evidencia matriz: `docs/04-calidad/M07-resultado-matriz-sql-staging-001-034.md`  
Corrección 034: `docs/04-calidad/M07-reporte-correccion-matriz-034.md`  
Cierre arquitectura: `docs/02-arquitectura/M07-cierre-final-staging-001-034.md`  
Script matriz: `scripts/sql/m07_validate_staging_001_034.sql`  
Smoke APK: `docs/04-calidad/M07-smoke-test-apk-staging.md`

---

## 0.1 Evidencia remota (apply / lint)

| Hecho | Resultado |
|---|---|
| Backup previo a 034 | schema.sql · data.sql · roles.sql (fuera de repo) |
| Dry-run | únicamente `034_m07_internal_invitation_helper_grants.sql` |
| Apply remoto 034 | **PASS** (exit 0) |
| Historial Local/Remote | **alineado 001–034** · 34 versiones · max 034 · 0 faltantes · 0 duplicadas |
| `db lint` remoto | **PASS** — exit 0 · **0 errores** · warnings no bloqueantes → BACKLOG |
| Producción | **NO** tocada |
| Migración 035 | **NO** creada |
| M08 / `main` | **NO** |

---

## 0.2 Smoke APK + OTP (2026-07-19)

| Hecho | Resultado |
|---|---|
| OTP 8 dígitos | **PASS** |
| Registro / confirmación / login / logout | **PASS** |
| Username único / duplicado rechazado | **PASS** |
| Perfil / persistencia | **PASS** |
| Smoke APK staging | **PASS** |
| Tests Android | **559**, 0 failures |
| `assembleDebug` / `testDebugUnitTest` / `lintDebug` | **PASS** |

---

## 0.3 Matriz SQL final 001–034

| Hecho | Resultado |
|---|---|
| CSV físico | 267 filas · **259 PASS** · **0 FAIL** · 3 NOT_EXECUTED · 5 BACKLOG |
| `runner_summary` | total=266 · pass=258 · fail=0 · not_executed=3 · backlog=5 · **PASS** |
| Diferencia 1 fila | `runner_summary` se cuenta antes de insertarse |
| Catálogos | 118 / 28 / 14 / 8 **PASS** |
| RLS / grants / DEFINER inventario | **PASS** |
| `_resolve…` grants post-034 | PUBLIC/anon/authenticated **false** · service_role **true** |
| `org_hash` | INVOKER · digest calificado · sin falsos positivos de matriz |
| NOT_EXECUTED | justificados (Kotlin equality + 2 probes JWT) |
| BACKLOG | documentado; **no** convertidos a PASS |
| Conclusión | **MATRIZ SQL STAGING 001–034 PASS** |

Matriz histórica 001–033 (pre-034) conservada como evidencia: 3 FAIL originales → 1 defecto real (cerrado) + 2 falsos positivos (matriz corregida). Ver diagnóstico: `docs/04-calidad/M07-diagnostico-fails-matriz-staging.md`.

---

## 1. Condición de release

```text
RELEASE BLOQUEADO
```

**Aclaración:** la validación staging de M07 está **PASS**. El release permanece bloqueado por deudas de producto (exportación de archivo, integración M06 de envío, warnings lint no bloqueantes y política de no producción), **no** por fallo de matriz/apply/lint/smoke de M07.

---

## 2. Limitaciones

- Producción real **no** existe / **no** tocada / **no** validada.
- No se autoriza release productivo.
- No se inició M08.
- No se hizo merge a `main`.
- No se creó migración 035.
- NOT_EXECUTED y BACKLOG no se convirtieron en PASS inventados.
