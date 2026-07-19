# M07 â€” Reporte de validaciÃ³n staging

**Fecha:** 2026-07-19
**Producto:** LeoVer
**Rama docs smoke:** `m07/cierre-smoke-apk-otp`
**Commit fix OTP:** `b2189b974528f41d0a3b7519be200033395f69d4` (`b2189b9`)
**Rama lint 033:** `m07/correccion-lint-staging-033`
**Project ref staging (Ãºltimos 4):** `mizz`
**Entorno objetivo:** staging no productivo
**Release:** **RELEASE BLOQUEADO**

---

## 0. Estado declarado (obligatorio)

```text
APPLY STAGING 001â€“033 PASS
DB LINT STAGING PASS
SMOKE TEST APK STAGING PASS
EMAIL OTP 8 DÃGITOS â€” PASS
DEFECTO OTP CERRADO
USERNAME REVALIDADO â€” PASS
MATRIZ SQL STAGING FAIL â€” 3 RESULTADOS PENDIENTES DE DIAGNÃ“STICO
VALIDACIÃ“N STAGING PENDIENTE
RELEASE BLOQUEADO
EXPORTACIÃ“N DE ARCHIVO PENDIENTE
INTEGRACIÃ“N M06 PENDIENTE
```

**No es STAGING PASS.** Falta cerrar el diagnÃ³stico de la matriz SQL (3 FAIL).

GuÃ­a matriz: `docs/04-calidad/M07-ejecucion-matriz-sql-staging-001-033.md`
Script: `scripts/sql/m07_validate_staging_001_033.sql`
Smoke APK: `docs/04-calidad/M07-smoke-test-apk-staging.md`
Defecto OTP: `docs/04-calidad/M07-defecto-email-otp-longitud.md`

---

## 0.1 Evidencia remota (apply / lint)

| Hecho | Resultado |
|---|---|
| Apply remoto 001â€“033 | **PASS** |
| `migration list` Local/Remote | alineado 001â€“033 |
| `db lint` remoto | **PASS** â€” 0 errores Â· ~22 warnings backlog |
| ProducciÃ³n | **NO** tocada |
| M08 / `main` | **NO** |

---

## 0.2 Smoke APK + OTP (2026-07-19)

| Hecho | Resultado |
|---|---|
| APK | `apk/Leover-debug.apk` (18/07/2026) |
| Commit fix OTP | `b2189b9` |
| OTP 8 dÃ­gitos revalidado | **PASS** |
| Registro | **PASS** |
| ConfirmaciÃ³n correo | **PASS** |
| Login posterior | **PASS** |
| Logout | **PASS** |
| Username Ãºnico | **PASS** |
| Username duplicado rechazado | **PASS** |
| Perfil completo | **PASS** |
| Persistencia perfil | **PASS** |
| Smoke APK | **PASS** |
| Base remota por fix OTP | **NO** modificada |
| Tests Android (cierre) | **â‰¥559**, 0 failures |
| `assembleDebug` / `testDebugUnitTest` / `lintDebug` | **PASS** (verificaciÃ³n en cierre) |

AuthRepository / domain/auth / UsernameValidators: solo cambios intencionales del fix OTP en `b2189b9`.

---

## 0.3 Matriz SQL

| Hecho | Resultado |
|---|---|
| Script preparado | `scripts/sql/m07_validate_staging_001_033.sql` |
| EjecuciÃ³n / cierre | **FAIL** â€” **3** resultados pendientes de diagnÃ³stico |
| Declarar STAGING PASS | **NO** |

---

## 1. CondiciÃ³n de release

```text
RELEASE BLOQUEADO
```

Smoke Auth/OTP/perfil: **PASS**. Matriz SQL: **pendiente**. ValidaciÃ³n staging general: **PENDIENTE**.

---

## 2. Limitaciones

- No se inventaron PASS de matriz SQL.
- No se aplicaron migraciones nuevas.
- No se tocÃ³ producciÃ³n.
- No se iniciÃ³ M08.
- No se hizo merge a `main`.
