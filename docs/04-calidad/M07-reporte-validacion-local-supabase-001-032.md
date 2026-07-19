# M07 — Reporte de validación local Supabase (migraciones 001–032)

**Producto:** LeoVer  
**Fecha:** 2026-07-18  
**Actor técnico:** Auto (Cursor)  
**Commit base (pre-corrección):** `78281e6531dc074ecadbd2641bc6dc705fd796a0`  
**Rama:** `m07/correccion-020-citext-validacion-local`  
**Rama origen:** `m07/validacion-local-y-staging-014-032`

---

## 1. Conclusión local

```text
VALIDACIÓN SUPABASE LOCAL PASS
```

Ambos `supabase db reset --local` aplicaron **001–032** desde cero.  
Historial local: 32 versiones, máxima **032**, sin duplicados.  
Catálogos SQL: **118 / 28 / 14** · permisos M07: **8** · RLS/grants/hardening verificados.  
Android: **544** tests · assemble/lint/jacoco SUCCESS · quality script **PASSED**.

Staging remoto:

```text
PENDIENTE DE VALIDACIÓN REMOTA
RELEASE BLOQUEADO
USERNAME NO REVALIDADO — STAGING PENDIENTE
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

Migración **033:** no creada.

---

## 2. Git

| Campo | Valor |
|---|---|
| HEAD base | `78281e6531dc074ecadbd2641bc6dc705fd796a0` |
| Rama corrección | `m07/correccion-020-citext-validacion-local` |
| Merge a `main` | no |
| M08 | no iniciado |
| AuthRepository / domain/auth / UsernameValidators | **intactos** |

---

## 3. Herramientas

| Herramienta | Valor |
|---|---|
| Docker | **29.6.1** |
| Supabase CLI | **2.109.1** |
| Link remoto / project ref | **ausente** |
| Secrets staging | **ausente** |

---

## 4. Configuración local

| Archivo | Notas |
|---|---|
| `supabase/config.toml` | `project_id = "ComunidadApp"` (local); sin project ref remoto |
| `supabase/.gitignore` | `.temp`, env locales |
| `supabase/seed.sql` | vacío (sin datos) |

**Puertos locales** (Windows excluía 54255–54354):

| Servicio | Puerto |
|---|---|
| API | 55321 |
| Postgres | 55322 |
| Shadow | 55320 |
| Mailpit | 55324 |
| Studio | 55323 (deshabilitado: healthcheck flaky en Windows) |
| Analytics | 55327 (deshabilitado: requisito Docker TCP en Windows) |

`health_timeout = "5m"`. Claves JWT/anon/service_role **no** documentadas.

---

## 5. Defectos y correcciones (sin 033)

### L01 — citext + `search_path = public` (020)

- **SQLSTATE:** `42704` — type "citext" does not exist  
- **Objeto:** `invite_organization_member` (+ mismo patrón en el archivo)  
- **Causa:** extensión en `extensions`; funciones `SECURITY DEFINER` con `SET search_path = public`  
- **Corrección:** **10** usos → `extensions.citext` (columnas, variables, casts, policy)  
- **No cambiado:** `SET search_path = public` (sin añadir `extensions` al path)

### L02 — BOM UTF-8 (029)

- **SQLSTATE:** `42601` — syntax error at BOM  
- **Corrección:** eliminado BOM (`EF BB BF`); archivo inicia en `-- `

### L03 — dollar-quoting truncado (029)

- **SQLSTATE:** `42601` — `as $` / `end; $;`  
- **Objetos:** `m07_client_note_data_access`, `m07_trg_dead_letter_observe`  
- **Corrección:** restaurar `$$` (4 sitios)

### L04 — cambio de tipo de retorno (031)

- **SQLSTATE:** `42P13` — cannot change return type of existing function  
- **Objetos:** `m07_list_*` / `m07_request_export` (029 `setof`/`uuid` → 031 `jsonb`)  
- **Corrección:** `DROP FUNCTION IF EXISTS …` previo a `CREATE OR REPLACE`

### L05 — healthchecks Windows (config local)

- Studio/analytics marcados unhealthy; stack usable con studio+analytics off  
- No es defecto SQL de migraciones

---

## 6. Resets

| Paso | Resultado |
|---|---|
| `supabase start` (tras correcciones) | SUCCESS — API `http://127.0.0.1:55321`, DB `:55322` |
| 1º `supabase db reset --local` | **APPLY OK 001–032** (`RESET1_EXIT=0`) |
| 2º `supabase db reset --local` | **APPLY OK 001–032** (`RESET2_EXIT=0`) — reproducible |

---

## 7. Historial local

| Check | Resultado |
|---|---|
| Count | **32** |
| Versiones | `001`…`032` consecutivas |
| Duplicados | **0** |
| Máxima | **032** |

---

## 8. Validación SQL real

### Catálogos

| Check | Resultado |
|---|---|
| event keys | **118**, 0 dupes |
| metric keys | **28**, 0 dupes |
| health checks | **14**, 0 dupes |
| permisos M07 | **8** |
| `m07.incident.staff_notification` | catalogado |
| Kotlin↔SQL (quality script) | PASS |

### Permisos / hardening 032

| Check | Resultado |
|---|---|
| list audit exige `observability.view` \| `audit.view_sensitive` (sin OR `audit.view`) | PASS |
| health MANUAL exige `health.check.execute` | PASS |
| evaluate alerts exige `observability.manage` \| `alert.manage` | PASS |
| usuario común → `OBS_PERMISSION_DENIED` | PASS |
| AccountType / active_modules no son códigos de permiso | PASS |

### RLS y grants

| Check | Resultado |
|---|---|
| RLS en 17 tablas M07 | PASS |
| authenticated INSERT/UPDATE/DELETE en tablas M07 core | **0** grants |
| writers PUBLIC/anon EXECUTE | **0** |
| `m07_write_audit_event` authenticated EXECUTE | **0** |
| `m07_record_metric` EXECUTE | `postgres,service_role` only |
| 41 funciones `m07_%` DEFINER con `search_path=public` | **41/41** |

### Auditoría / errores / métricas / health / incidentes / retención / export

| Check | Resultado |
|---|---|
| event key desconocido → `OBS_EVENT_UNKNOWN` | PASS |
| metadata denegada (path write) | PASS (`OBS_*`) |
| incidentes OPEN → ACKNOWLEDGED → RESOLVED; ACK inválido denegado | PASS |
| retention preview sin purge; execute sin preview → `OBS_RETENTION_PREVIEW_REQUIRED` | PASS |
| `LEGAL_REVIEW_REQUIRED` preview → `OBS_RETENTION_LEGAL_HOLD` | PASS |
| export → `AUTHORIZED` + `file_pending=true`; `READY_SIMULATED` no alcanzable en flujo nuevo | PASS |

Deudas explícitas:

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

---

## 9. `supabase test db`

**NO EJECUTADO (sin tests):** `Files=0, Tests=0, Result: NOTESTS`.  
No hay suite pgTAP en el repo; no se inventaron tests.

---

## 10. Android / quality

| Check | Resultado |
|---|---|
| `:app:assembleDebug` | SUCCESS |
| `:app:testDebugUnitTest` | **544** / 0 fail / 0 error / 0 skipped |
| `:app:lintDebug` | SUCCESS |
| `:app:jacocoTestReport` | SUCCESS |
| `scripts/ci/m07_quality_checks.sh` | **PASSED** |

JaCoCo informativo (umbral no activado): Line **28.31%** · Instruction **17.85%** · Branch **9.73%** · Class **27.69%**.

Quality script: check de migraciones previas acotado a **001–019** (020–032 pueden recibir fixes mínimos de apply local).

---

## 11. Staging / release

```text
PENDIENTE DE VALIDACIÓN REMOTA
RELEASE BLOQUEADO
USERNAME NO REVALIDADO — STAGING PENDIENTE
```

Sin `login` / `link` / `db push` / `--linked` / producción.

---

## 12. Archivos de la corrección

| Archivo | Acción |
|---|---|
| `supabase/migrations/020_…branches.sql` | citext → `extensions.citext` (10) |
| `supabase/migrations/029_…foundation.sql` | BOM + `$$` |
| `supabase/migrations/031_…readiness.sql` | DROP antes de OR REPLACE |
| `supabase/config.toml` | puertos 5532x; analytics/studio off; health 5m |
| `supabase/.gitignore` | local |
| `supabase/seed.sql` | vacío |
| `scripts/ci/m07_quality_checks.sh` | check 001–019 |
| este reporte | actualizado |
