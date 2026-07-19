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
CORRECCIÓN 034 VALIDADA LOCALMENTE
APPLY 034 STAGING PENDIENTE
MATRIZ SQL STAGING PENDIENTE DE REEJECUCIÓN
VALIDACIÓN STAGING PENDIENTE
RELEASE BLOQUEADO
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

**No es STAGING PASS.** Corrección 034 validada solo en local; apply remoto y reejecución de matriz en staging pendientes.
Ver `docs/04-calidad/M07-reporte-correccion-matriz-034.md` y `docs/04-calidad/M07-diagnostico-fails-matriz-staging.md`.

Guía matriz (histórica 001–033): `docs/04-calidad/M07-ejecucion-matriz-sql-staging-001-033.md`
Script matriz histórica: `scripts/sql/m07_validate_staging_001_033.sql`
Script matriz 001–034: `scripts/sql/m07_validate_staging_001_034.sql`
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
| Matriz histórica 001–033 (staging) | 261 filas · 249 PASS · **3 FAIL** · 3 NOT_EXECUTED · 5 BACKLOG (no ocultar) |
| FAIL originales | `internal_writers_anon_execute`; org_hash DEFINER; org_hash search_path |
| Evidencia remota definitiva | `_resolve…`: PUBLIC=false · anon_direct=**true** · service_role=true |
| Clasificación definitiva | 1 DEFECTO REAL + 2 FALSOS POSITIVOS de matriz |
| Migración 034 | creada y **validada localmente**; **apply staging pendiente** |
| Matriz local 001–034 | 266 · 258 PASS · **0 FAIL** · 3 NOT_EXECUTED · 5 BACKLOG |
| Matriz SQL staging reejecución | **PENDIENTE** |
| Declarar STAGING PASS | **NO** |

---

## 1. Condición de release

```text
RELEASE BLOQUEADO
```

OTP 8 dígitos / username / smoke APK: **PASS**. Apply 034 remoto: **PENDIENTE**. Matriz staging: **PENDIENTE DE REEJECUCIÓN**. Validación staging general: **PENDIENTE**.

---

## 2. Limitaciones

- No se inventaron PASS de matriz SQL staging.
- No se aplicó 034 en remoto (`db push` / reset / repair: no).
- No se tocó producción.
- No se inició M08.
- No se hizo merge a `main`.
