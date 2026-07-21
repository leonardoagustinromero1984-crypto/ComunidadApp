# M08 — Plan de despliegue staging 035/036

**Producto:** LeoVer

```text
M08 ETAPA 4D — BACKEND STAGING Y APK DISTRIBUIBLE LISTOS
SMOKE APK STAGING MANUAL — PENDIENTE
PRODUCCIÓN NO MODIFICADA
```

Histórico (pre-apply):
```text
M08 ETAPA 4D — PREPARACIÓN STAGING LISTA
APPLY 035/036 — PENDIENTE DE CONFIRMACIÓN MANUAL
APK DISTRIBUIBLE — PENDIENTE DEL APPLY
```

## Estado post-apply (2026-07-21)

- Migraciones locales/remotas **001–036** alineadas; pendientes **0**; **037** ausente.
- Apply 035 y 036: **realizado** (confirmación humana). Warning pg-delta posterior: no bloqueante.
- Lint remoto: exit 0, 0 errores (warnings documentados en reporte).
- Matrices staging 035/036: `runner_summary` **PASS**, FAIL=0.
- APK: `apk/LeoVer-M08-Staging-debug.apk` (gitignored).
- NO re-ejecutar: backup / db push / dry-run / reset / repair.
- Producción: **no modificada**.

## Precondiciones (cumplidas)

- Migraciones 001–036 en repo; 037 ausente; 001–036 sin modificar.
- Proyecto remoto LeoVer = **STAGING**.
- `local.properties` con staging HTTPS (gitignored). Sin `service_role`.

## 1) Backup (histórico — no repetir)

Backup pre-apply fuera del repo bajo `$HOME\LeoVerBackups\<fecha>_pre_m08_035_036`.
**No** guardar backups dentro del repositorio. **No** hacer backups adicionales en esta continuación.

```powershell
# NO ejecutar — backup ya realizado pre-apply; no repetir
# $backup = Join-Path $HOME "LeoVerBackups\${fecha}_pre_m08_035_036"
# npx supabase db dump --linked ...
```

## 2) Historial remoto (verificación post-apply)

```bash
# Verificación (lectura): ya confirmado 001–036 alineados
npx supabase migration list --linked
```

## 3) Dry-run / Apply (histórico — no repetir)

```bash
# NO re-ejecutar — apply ya realizado; no db push
# NO re-ejecutar: npx supabase db push --linked --dry-run
# NO re-ejecutar: npx supabase db push --linked
```

## 4) Post-apply (ejecutado)

```bash
npx supabase db lint --linked --level warning --fail-on error
npx supabase db query --linked -f scripts/sql/m08_validate_staging_035.sql
npx supabase db query --linked -f scripts/sql/m08_validate_staging_036.sql
```

`runner_summary` FAIL=0 obligatorio → **cumplido**.

## 5) APK staging

```bash
.\gradlew.bat :app:assembleStagingDebug --no-configuration-cache
# copia: apk/LeoVer-M08-Staging-debug.apk (gitignored)
```

Checklist smoke (manual pendiente): `docs/04-calidad/M08-checklist-smoke-apk-staging.md`
Reporte: `docs/04-calidad/M08-reporte-validacion-staging-post-apply-4d.md`

## Contención

Ante error: detener, conservar evidencias, no reparar el historial de migraciones de forma improvisada, no Dashboard SQL ad-hoc, no debilitar RLS.
