# M08 — Reporte validación staging post-apply (Etapa 4D)

**Producto:** LeoVer
**Fecha reporte:** 2026-07-21
**Rama:** `m08/etapa-4d-staging-apk-distribuible`
**Commit base preparación:** `84ce796`

```text
M08 ETAPA 4D — BACKEND STAGING Y APK DISTRIBUIBLE LISTOS
SMOKE APK STAGING MANUAL — PENDIENTE
PRODUCCIÓN NO MODIFICADA
```

## 1. Apply remoto

| Ítem | Resultado |
|---|---|
| Fecha apply | Confirmada por operador (pre-continuación 4D) |
| Migraciones aplicadas | 035, 036 |
| Historial `migration list --linked` | local/remoto **001–036** alineados |
| Max migration | **036** |
| Pendientes / gaps / duplicadas | **0** |
| Migración 037 | **Ausente** |
| Warning pg-delta post-apply | No bloqueante (exit apply OK + historial OK) |
| `db push` en esta continuación | **NO** ejecutado |
| Backups adicionales | **NO** |
| Producción | **No modificada** |

## 2. Lint remoto

**Comando real:**

```bash
npx supabase db lint --linked --level warning --fail-on error
```

| Ítem | Resultado |
|---|---|
| Exit code | **0** |
| Errores | **0** |
| Warnings | **24** (6 volatility/immutability + 18 unused vars/params) |

Warnings no se silenciaron modificando la base. Detalle en sesión de verificación (m05/m07/m08 listados; incluye `m08_update_pet_profile` / `m08_update_pet_health` unused `v_actor`).

## 3. Matrices staging

**Comando:**

```bash
npx supabase db query --linked -f scripts/sql/m08_validate_staging_035.sql
npx supabase db query --linked -f scripts/sql/m08_validate_staging_036.sql
```

| Matriz | runner_summary | Detail |
|---|---|---|
| 035 | **PASS** | PASS=8 FAIL=0 NOT_EXECUTED=1 total=9 |
| 036 | **PASS** | PASS=8 FAIL=0 NOT_EXECUTED=1 total=9 |

`NOT_EXECUTED`: pruebas mutantes/JWT diferidas al smoke manual (sin fixtures destructivos / sin usuarios reales en matriz).

## 4. Android staging

| Ítem | Resultado |
|---|---|
| Flavors | local / staging / production |
| applicationId | `com.comunidapp.app.staging` |
| Nombre visible | LeoVer Staging |
| Credenciales | `SUPABASE_STAGING_*` en `local.properties` (gitignored) |
| HTTPS | Sí (`*.supabase.co`) |
| localhost / 10.0.2.2 / cleartext staging | No |
| service_role | No |
| production reutiliza staging | No |

## 5. Tests / build / lint / JaCoCo

| Tarea | Resultado |
|---|---|
| `testStagingDebugUnitTest` | **627** tests, **0** failures |
| `testDebugUnitTest` (local) | **627** tests, **0** failures |
| `assembleStagingDebug` | PASS (`--no-configuration-cache`) |
| `lintStagingDebug` | PASS |
| `jacocoTestReport` | PASS (tarea vigente del proyecto; depende de `testLocalDebugUnitTest`) |

Nota: baseline previo de preparación 4D reportó 632 en local; la corrida post-apply midió **627** en local y staging con 0 failures. No se inventó un PASS inflado.

## 6. Quality gates M07→4D

Todos **PASS**: m07, m08 stage2, 3-freeze, 3b, 3c, 4b, 4c, 4d.

## 7. APK distribuible

| Ítem | Valor |
|---|---|
| Ruta Gradle | `app/build/outputs/apk/staging/debug/app-staging-debug.apk` |
| Ruta distribución | `apk/LeoVer-M08-Staging-debug.apk` |
| Tamaño | 26 747 105 bytes |
| SHA-256 | `308699A3085C6EF2509D22E80016B642A755FF405AE21CEFB4D36F2B4CEF079D` |
| Trackeado en Git | **NO** (`apk/` en `.gitignore`) |
| Conexión | HTTPS remoto staging |
| Sin USB / PC / adb reverse | **Técnicamente preparado** (smoke manual pendiente) |

## 8. Estado de cierre parcial

- Backend staging + APK: **LISTOS**
- Smoke APK staging manual: **PENDIENTE**
- Etapa 4D cerrada: **NO**
- Smoke PASS: **NO declarado**
- Etapa 5: **NO iniciada**
- Merge a main: **NO**
