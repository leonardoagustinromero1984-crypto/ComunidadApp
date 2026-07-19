# M07 — Reporte de validación staging

**Fecha:** 2026-07-19
**Producto:** LeoVer
**Rama docs smoke:** `m07/cierre-smoke-apk-otp`
**Commit fix OTP:** `b2189b974528f41d0a3b7519be200033395f69d4` (`b2189b9`)
**Rama lint 033:** `m07/correccion-lint-staging-033`
**Project ref staging (últimos 4):** `mizz`
**Entorno objetivo:** staging no productivo
**Release:** **RELEASE BLOQUEADO**

---

## 0. Estado declarado (obligatorio)

```text
APPLY STAGING 001–033 PASS
DB LINT STAGING PASS
SMOKE TEST APK STAGING PASS
EMAIL OTP 8 DÍGITOS — PASS
DEFECTO OTP CERRADO
USERNAME REVALIDADO — PASS
MATRIZ SQL STAGING FAIL — 3 RESULTADOS EN DIAGNÓSTICO
VALIDACIÓN STAGING PENDIENTE
RELEASE BLOQUEADO
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

**No es STAGING PASS.** Tres FAIL de matriz en diagnóstico (script read-only); ver `docs/04-calidad/M07-diagnostico-fails-matriz-staging.md`.

Guía matriz: `docs/04-calidad/M07-ejecucion-matriz-sql-staging-001-033.md`
Script matriz: `scripts/sql/m07_validate_staging_001_033.sql`
Diagnóstico FAIL: `docs/04-calidad/M07-ejecucion-diagnostico-fails-matriz.md`
Script diagnóstico: `scripts/sql/m07_diagnose_staging_matrix_fails.sql`
Smoke APK: `docs/04-calidad/M07-smoke-test-apk-staging.md`
Defecto OTP: `docs/04-calidad/M07-defecto-email-otp-longitud.md`

---

## 0.1 Evidencia remota (apply / lint)

| Hecho | Resultado |
|---|---|
| Apply remoto 001–033 | **PASS** |
| `migration list` Local/Remote | alineado 001–033 |
| `db lint` remoto | **PASS** — 0 errores · ~22 warnings backlog |
| Producción | **NO** tocada |
| M08 / `main` | **NO** |

---

## 0.2 Smoke APK + OTP (2026-07-19)

| Hecho | Resultado |
|---|---|
| APK | `apk/Leover-debug.apk` (18/07/2026) |
| Commit fix OTP | `b2189b9` |
| OTP 8 dígitos revalidado | **PASS** |
| Registro | **PASS** |
| Confirmación correo | **PASS** |
| Login posterior | **PASS** |
| Logout | **PASS** |
| Username único | **PASS** |
| Username duplicado rechazado | **PASS** |
| Perfil completo | **PASS** |
| Persistencia perfil | **PASS** |
| Smoke APK | **PASS** |
| Base remota por fix OTP | **NO** modificada |
| Tests Android (cierre) | **559**, 0 failures |
| `assembleDebug` / `testDebugUnitTest` / `lintDebug` | **PASS** (verificación en cierre) |

AuthRepository / domain/auth / UsernameValidators: solo cambios intencionales del fix OTP en `b2189b9`.

---

## 0.3 Matriz SQL

| Hecho | Resultado |
|---|---|
| Script matriz | `scripts/sql/m07_validate_staging_001_033.sql` |
| Resultado reportado | 261 filas · 249 PASS · **3 FAIL** · 3 NOT_EXECUTED · 5 BACKLOG |
| FAIL | `internal_writers_anon_execute`; `org_hash_invitation_token` DEFINER; `org_hash_invitation_token` search_path |
| Diagnóstico | script read-only `m07_diagnose_staging_matrix_fails.sql` — **pendiente de ejecución manual** |
| Clasificación preliminar | anon EXECUTE → DEFECTO_REAL (probable `_resolve_invitation_by_token`); org_hash ×2 → FALSO_POSITIVO de matriz |
| Migración 034 | **NO** creada; pendiente de decisión post-CSV |
| Declarar STAGING PASS | **NO** |

---

## 1. Condición de release

```text
RELEASE BLOQUEADO
```

Smoke Auth/OTP/perfil: **PASS**. Matriz SQL: **pendiente**. Validación staging general: **PENDIENTE**.

---

## 2. Limitaciones

- No se inventaron PASS de matriz SQL.
- No se aplicaron migraciones nuevas.
- No se tocó producción.
- No se inició M08.
- No se hizo merge a `main`.
