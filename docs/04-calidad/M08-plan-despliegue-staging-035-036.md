# M08 — Plan de despliegue staging 035/036

**Producto:** LeoVer

```text
M08 ETAPA 4D — PREPARACIÓN STAGING LISTA
APPLY 035/036 — PENDIENTE DE CONFIRMACIÓN MANUAL
APK DISTRIBUIBLE — PENDIENTE DEL APPLY
```

## Precondiciones

- Migraciones 001–036 en repo; 037 ausente; 001–036 sin modificar.
- Proyecto remoto LeoVer = **STAGING** (producción no existe / no tocar).
- Remoto hoy esperado: **001–034**; pendientes: **035 y 036**.
- `local.properties` con staging HTTPS (gitignored). Sin `service_role`.

## 1) Backup (fuera del repo) — DO NOT RUN YET

```powershell
# NO ejecutar todavía — backup fuera del repo
$fecha = Get-Date -Format "yyyyMMdd_HHmmss"
$backup = Join-Path $HOME "LeoVerBackups\${fecha}_pre_m08_035_036"
New-Item -ItemType Directory -Force -Path $backup | Out-Null
npx supabase db dump --linked --file "$backup\schema.sql"
npx supabase db dump --linked --data-only --use-copy --file "$backup\data.sql"
npx supabase db dump --linked --role-only --file "$backup\roles.sql"
Get-ChildItem $backup | Format-Table Name, Length
```

Verificar tres archivos con tamaño > 0. **No** guardar backups dentro del repositorio.

## 2) Historial remoto — DO NOT RUN YET

```bash
# NO ejecutar todavía — historial remoto
npx supabase migration list --linked
```

Esperado antes del apply:

- local: 001–036  
- remoto: 001–034  
- pendientes: 035, 036  
- sin gaps / sin duplicadas  

## 3) Dry-run — DO NOT RUN YET

```bash
# NO ejecutar todavía — dry-run
npx supabase db push --linked --dry-run
```

Debe listar **exclusivamente**:

- `035_m08_pets_responsibilities_and_rls.sql`
- `036_m08_pet_repository_compatibility_rpcs.sql`

Si aparece otra migración → **DETENERSE**.

## 4) Apply — DO NOT RUN YET (requiere confirmación humana)

```bash
# NO ejecutar todavía — apply (solo tras confirmación humana)
npx supabase db push --linked
echo $LASTEXITCODE   # PowerShell: $LASTEXITCODE
npx supabase migration list --linked
```

Esperado: exit 0; local/remoto 001–036 alineadas.  
Advertencia pg-delta/certificado puede aparecer; no confundir con fallo — verificar exit code e historial.

## 5) Post-apply

```bash
# NO ejecutar todavía — lint remoto post-apply
npx supabase db lint --linked --level warning --fail-on error
```

Matrices (SQL editor / psql linked session — sin secretos en repo):

```text
scripts/sql/m08_validate_staging_035.sql
scripts/sql/m08_validate_staging_036.sql
```

`runner_summary` FAIL=0 obligatorio.

## 6) APK staging (solo tras matrices PASS)

```bash
.\gradlew.bat :app:assembleStagingDebug
# copia: apk/LeoVer-M08-Staging-debug.apk (gitignored)
```

Checklist: `docs/04-calidad/M08-checklist-smoke-apk-staging.md`

## Contención

Ante error: detener, conservar evidencias, no reparar el historial de migraciones de forma improvisada, no Dashboard SQL ad-hoc, no debilitar RLS.
