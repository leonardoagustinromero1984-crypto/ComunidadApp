# Operación — migraciones 064–065 (M21 reputación)

**LeoVer** · Supabase staging `wystsapjfpdtoprlmizz` · **no producción**.

## Archivos

```text
supabase/migrations/064_m21_reputation_reviews_and_verifications.sql
supabase/migrations/065_m21_review_operations_and_verification_workflows.sql
scripts/ops/m21_hotfix_post_065.sql
```

## Estado (2026-08-02)

```text
064 — APLICADA EN STAGING
065 — APLICADA EN STAGING
Hotfix post-065 — APLICADO EN STAGING
```

## Prerrequisitos

Migraciones 001–063 aplicadas en orden.

## Procedimiento aplicado

1. Confirmar entorno no productivo (`wystsapjfpdtoprlmizz`).
2. Aplicar 064: `supabase db query --linked -f supabase/migrations/064_m21_reputation_reviews_and_verifications.sql`
3. `supabase migration repair 064 --status applied --linked`
4. Aplicar 065: `supabase db query --linked -f supabase/migrations/065_m21_review_operations_and_verification_workflows.sql`
5. `supabase migration repair 065 --status applied --linked`
6. Hotfix: `supabase db query --linked -f scripts/ops/m21_hotfix_post_065.sql`

## Validación post-aplicación

```bat
supabase db query --linked -f scripts/ops/m21_remote_validation_064_065.sql
supabase db query --linked -f scripts/ops/m21_smoke_remote_01_25.sql
```

Resultados: **130/130 PASS**, **25/25 PASS**.

## Referencias

- `docs/03-modulos/M21-Bloque-4-auditoria.md`
- `docs/03-modulos/M21-cierre-oficial.md`
