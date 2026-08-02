# Operación — migraciones 066–067 (M22 prestadores)

**LeoVer** · Supabase staging `wystsapjfpdtoprlmizz` · **no producción**.

## Archivos

```text
supabase/migrations/066_m22_service_providers_and_catalog.sql
supabase/migrations/067_m22_branch_coverage_radius_check_fix.sql
scripts/ops/m22_remote_validation_066.sql
scripts/ops/m22_smoke_remote_01_25.sql
```

## Estado (2026-08-02)

```text
066 — APLICADA EN STAGING
067 — APLICADA EN STAGING
```

## Prerrequisitos

- Migraciones **064–065** registradas en `schema_migrations`.
- Entorno confirmado no productivo.

## Procedimiento aplicado

1. `supabase db query --linked -f supabase/migrations/066_m22_service_providers_and_catalog.sql`
2. `supabase migration repair 066 --status applied --linked`
3. Validación inicial → caso 21 CHECK RADIUS → migración **067** forward-only
4. `supabase db query --linked -f supabase/migrations/067_m22_branch_coverage_radius_check_fix.sql`
5. `supabase migration repair 067 --status applied --linked`

## Validación post-aplicación

```bat
supabase db query --linked -f scripts/ops/m22_remote_validation_066.sql
supabase db query --linked -f scripts/ops/m22_smoke_remote_01_25.sql
```

Resultados: **75/75 PASS**, **25/25 PASS**.

## Nota sobre migraciones 039–052

Existe divergencia histórica (archivos locales sin registro remoto). **No aplicar** ese rango en bloque. No bloquea M22 si 053–067 están consistentes.

## Referencias

- `docs/03-modulos/M22-cierre-oficial.md`
- `docs/03-modulos/M22-Bloque-4-auditoria.md`
