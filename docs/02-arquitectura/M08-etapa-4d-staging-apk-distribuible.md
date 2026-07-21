# M08 Etapa 4D — Staging y APK distribuible

**Producto:** LeoVer
**Rama:** `m08/etapa-4d-staging-apk-distribuible`

```text
M08 ETAPA 4D — BACKEND STAGING Y APK DISTRIBUIBLE LISTOS
SMOKE APK STAGING MANUAL — PENDIENTE
PRODUCCIÓN NO MODIFICADA
```

Histórico (preparación previa al apply):
```text
M08 ETAPA 4D — PREPARACIÓN STAGING LISTA
APPLY 035/036 — PENDIENTE DE CONFIRMACIÓN MANUAL
APK DISTRIBUIBLE — PENDIENTE DEL APPLY
```

## Ambientes Android

| Flavor | applicationId | App name | Backend |
|---|---|---|---|
| `local` | `.local` | LeoVer Local | Supabase local (`SUPABASE_URL`) |
| `staging` | `.staging` | LeoVer Staging | Remoto HTTPS (`SUPABASE_STAGING_*`) |
| `production` | (sin suffix) | LeoVer | Futuro (`SUPABASE_PRODUCTION_*`) |

Credenciales solo en `local.properties` (gitignored). **Prohibido:** `service_role` en Android.

Cleartext HTTP solo en flavor `local`. Staging/production: cleartext OFF + HTTPS.

`google-services.json` incluye package names `.local` / `.staging` para permitir
`applicationIdSuffix` (FCM real en esos packages requiere registro futuro en Firebase).

## Build

Con flavors, las tareas agregadas `assembleDebug` / `testDebugUnitTest` construyen solo el flavor **local** (staging/production se habilitan al pedir `*Staging*` / `*Production*` o `-PenableStagingBuild` / `-PenableProductionBuild`).

```text
.\gradlew.bat :app:assembleDebug                 # → localDebug
.\gradlew.bat :app:testDebugUnitTest             # → localDebug
.\gradlew.bat :app:lintLocalDebug
.\gradlew.bat :app:assembleStagingDebug
.\gradlew.bat :app:testStagingDebugUnitTest
.\gradlew.bat :app:lintStagingDebug
```

Salida staging:
`app/build/outputs/apk/staging/debug/app-staging-debug.apk` → `apk/LeoVer-M08-Staging-debug.apk` (no Git).

## Remoto (post-apply confirmado)

1. Historial linked: local/remoto **001–036** alineados; pendientes **0**; **037** ausente.
2. Lint remoto: `npx supabase db lint --linked --level warning --fail-on error` → exit 0, **0 errores**, warnings documentados en el reporte.
3. Matrices:
   - `npx supabase db query --linked -f scripts/sql/m08_validate_staging_035.sql`
   - `npx supabase db query --linked -f scripts/sql/m08_validate_staging_036.sql`
4. **NO re-ejecutar** `db push` / dry-run / reset / repair.

**Apply 035/036:** realizado (confirmación humana previa).
**APK distribuible:** generado (smoke manual pendiente).
**Producción:** no tocada.

## Validadores staging

- `scripts/sql/m08_validate_staging_035.sql`
- `scripts/sql/m08_validate_staging_036.sql`

Reporte: `docs/04-calidad/M08-reporte-validacion-staging-post-apply-4d.md`
Checklist smoke: `docs/04-calidad/M08-checklist-smoke-apk-staging.md`

## Quality

`scripts/ci/m08_stage4d_quality_checks.sh`
