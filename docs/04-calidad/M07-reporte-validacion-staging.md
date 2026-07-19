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
MATRIZ SQL STAGING FAIL — 3 RESULTADOS PENDIENTES DE DIAGNÓSTICO
VALIDACIÓN STAGING PENDIENTE
RELEASE BLOQUEADO
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

**No es STAGING PASS.** Falta cerrar el diagnóstico de la matriz SQL (3 FAIL).

Guía matriz: `docs/04-calidad/M07-ejecucion-matriz-sql-staging-001-033.md`
Script: `scripts/sql/m07_validate_staging_001_033.sql`
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
| Script preparado | `scripts/sql/m07_validate_staging_001_033.sql` |
| Ejecución / cierre | **FAIL** — **3** resultados pendientes de diagnóstico |
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
