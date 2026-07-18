# M07 — Reporte de validación staging

**Fecha:** 2026-07-18
**Producto:** LeoVer
**Rama activa:** `m07/correccion-lint-staging-033`
**Commit:** `9f5cd74150b2f058b0ac40e9ec0f3609e8c3fdcb` (base lint 033)
**Rama oficial local consolidada:** `m07/validacion-local-y-staging-014-032`
**Commit local validado (001–032):** `80379b1201b3a31e94a572130a44cf07304a87ac`
**Project ref staging (últimos 4):** `mizz`
**Entorno objetivo:** staging no productivo
**Actor técnico:** Auto (Cursor)
**Release:** **RELEASE BLOQUEADO**

---

## 0. Estado declarado (obligatorio)

```text
APPLY STAGING 001–033 PASS
DB LINT STAGING PASS
MATRIZ SQL STAGING PENDIENTE DE EJECUCIÓN
VALIDACIÓN LOCAL 001–033 PASS
RELEASE BLOQUEADO
USERNAME NO REVALIDADO — STAGING PENDIENTE
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
EMAIL OTP 8 DÍGITOS — PENDIENTE DE REVALIDACIÓN CON APK NUEVO
```

**No es STAGING PASS completo** hasta ejecutar y cerrar la matriz SQL remota.

Smoke Auth email: defecto OTP 6 vs 8 reproducido; corrección en rama `m07/fix-email-otp-length` — ver `docs/04-calidad/M07-defecto-email-otp-longitud.md`.

Guía de ejecución: `docs/04-calidad/M07-ejecucion-matriz-sql-staging-001-033.md`
Script: `scripts/sql/m07_validate_staging_001_033.sql`
Detalle lint 033: `docs/04-calidad/M07-reporte-correccion-lint-033.md`

---

## 0.1 Evidencia remota confirmada (propietario)

| Hecho | Resultado |
|---|---|
| Reset/apply remoto 001–032 | **PASS** |
| Migración 033 aplicada | **PASS** |
| `migration list` Local/Remote | **alineado 001–033** (exit 0) |
| Máxima versión remota | **033** |
| Migraciones faltantes / duplicadas | **0 / 0** |
| Backup pre-033 | disponible fuera del repo (`LeoVerBackups`, pre-M07 / pre-033) |
| `supabase db lint --linked --level warning --fail-on error` | **exit 0** |
| Errores lint remotos | **0** |
| Warnings lint remotos | **~22** no bloqueantes → **BACKLOG** |
| Producción | **NO** tocada |
| AuthRepository / domain/auth / UsernameValidators | **intactos** |
| M08 | **NO** iniciado |
| `main` | **NO** modificado |
| Matriz SQL remota final | **PENDIENTE DE EJECUCIÓN MANUAL** (no ejecutada desde Cursor) |

### Errores lint corregidos en 033 (ya aplicados)

L05 `is_username_available` · L06 `add_reputation_points` · L07 `complete_profile_onboarding` · L08 `org_hash_invitation_token` · L08b `_resolve_invitation_by_token` (STABLE→VOLATILE) · L09 `invite_organization_member` · L10 `m06_claim_outbox` · L11 `m06_claim_push_deliveries`.

---

## 1. Consolidación local previa

| Paso | Resultado |
|---|---|
| Fast-forward `m07/validacion-local-y-staging-014-032` ← corrección | **OK** |
| Rama lint | `m07/correccion-lint-staging-033` @ `9f5cd74` |
| Working tree al preparar matriz | limpio (precheck) |

---

## 2. Evidencia local PASS (resumen)

| Evidencia | Valor |
|---|---|
| 2× `db reset --local` 001–033 | APPLY OK |
| 2× `db lint --local --fail-on error` | exit 0 · 0 errors |
| Historial local | 33 · max 033 · 0 dupes |
| Catálogos | 118 / 28 / 14 · permisos 8 |
| Android | 544 tests · quality PASS |

AuthRepository / domain/auth / UsernameValidators: **intactos**.

---

## 3. Auditoría de acceso staging (sanitizada)

| Elemento | Estado |
|---|---|
| Project ref (últimos 4) | `mizz` |
| Identidad staging ≠ prod | confirmada por propietario (producción real aún no existe) |
| URL / keys / tokens en este reporte | **NO** expuestos |

---

## 4. Matriz SQL remota final (preparación)

| Artefacto | Estado |
|---|---|
| `scripts/sql/m07_validate_staging_001_033.sql` | **CREADO** |
| Guía `M07-ejecucion-matriz-sql-staging-001-033.md` | **CREADA** |
| Ejecución remota desde Cursor | **NO** |
| Ejecución manual SQL Editor | **PENDIENTE** |

Cobertura del script (grupos):

`HISTORY` · `CATALOG` · `FIX_033` · `DEFINER` · `RLS` · `GRANTS` · `PERMS` · `AUDIT` · `METRICS` · `HEALTH` · `INCIDENTS` · `RETENTION` · `EXPORT` · `LINT` · `META`

Comandos destructivos en script: **NO**
Secrets en script: **NO**

---

## 5. Historial remoto (confirmado)

| Campo | Valor |
|---|---|
| Versiones | **33** |
| Máxima | **033** |
| Duplicadas | **0** |
| Faltantes 001–033 | **0** |
| Local/Remote | **alineado** |

---

## 6. Condición de release

```text
RELEASE BLOQUEADO
```

Apply + lint staging: **PASS**. Cierre STAGING general: **pendiente de matriz SQL**.

---

## 7. Limitaciones de este reporte

- Matriz SQL **no** ejecutada remotamente en esta preparación.
- No se inventaron filas PASS/FAIL de la matriz.
- No se usó producción.
- No se inició M08.
- No se hizo merge a `main`.
- No se creó migración 034.
- No se ejecutó `db push` / `db reset --linked` / repair.
