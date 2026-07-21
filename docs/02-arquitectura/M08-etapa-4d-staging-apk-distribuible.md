# M08 Etapa 4D — Staging y APK distribuible

**Producto:** LeoVer  
**Rama:** `m08/etapa-4d-staging-apk-distribuible`

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

Con flavors, las tareas agregadas `assembleDebug` / `testDebugUnitTest` / `lintDebug`
construyen solo el flavor **local** (staging/production se habilitan al pedir
`*Staging*` / `*Production*` o `-PenableStagingBuild` / `-PenableProductionBuild`).

```text
.\gradlew.bat :app:assembleDebug                 # → localDebug
.\gradlew.bat :app:testDebugUnitTest             # → localDebug
.\gradlew.bat :app:lintLocalDebug                # usar lintLocalDebug (lintDebug es ambiguo)
.\gradlew.bat :app:assembleLocalDebug
.\gradlew.bat :app:assembleStagingDebug          # SOLO tras apply 035/036 + credenciales
```
Salida staging esperada (post-apply):  
`app/build/outputs/apk/staging/debug/app-staging-debug.apk` → `apk/LeoVer-M08-Staging-debug.apk` (no Git).

## Secuencia remota — DO NOT RUN YET / NO ejecutar todavía

Detalle: `docs/04-calidad/M08-plan-despliegue-staging-035-036.md`

1. Backup fuera del repo (`$HOME/LeoVerBackups/<fecha>_pre_m08_035_036`)
2. `supabase migration list --linked` — NO ejecutar todavía (remoto 001–034; pendientes 035–036)
3. `supabase db push --linked --dry-run` — NO ejecutar todavía (solo 035 y 036)
4. Confirmación humana
5. `supabase db push --linked` — NO ejecutar todavía
6. Lint + matrices staging
7. `assembleStagingDebug` + smoke checklist (solo tras apply)

**Apply realizado en esta etapa: NO.**  
**APK distribuible generado: NO.**  
**Producción: no tocada.**

## Validadores staging

- `scripts/sql/m08_validate_staging_035.sql`
- `scripts/sql/m08_validate_staging_036.sql`

## Quality

`scripts/ci/m08_stage4d_quality_checks.sh`
