# Operación — migraciones M26 072–074 (staging)

**Proyecto:** `wystsapjfpdtoprlmizz` (staging, no producción)

## Estado

```
072_m26_ai_matching_duplicates_assistance_recommendations.sql — APLICADA
073_m26_ai_operations_review_and_safety.sql — APLICADA
074_m26_review_queue_permission_fix.sql — APLICADA
```

## Procedimiento

```powershell
supabase db query --linked -f supabase/migrations/072_m26_ai_matching_duplicates_assistance_recommendations.sql
supabase db query --linked "insert into supabase_migrations.schema_migrations(version) values ('072') on conflict do nothing;"

supabase db query --linked -f supabase/migrations/073_m26_ai_operations_review_and_safety.sql
supabase db query --linked "insert into supabase_migrations.schema_migrations(version) values ('073') on conflict do nothing;"

supabase db query --linked -f supabase/migrations/074_m26_review_queue_permission_fix.sql
supabase db query --linked "insert into supabase_migrations.schema_migrations(version) values ('074') on conflict do nothing;"

supabase db query --linked -f scripts/ops/m26_remote_validation_072_073.sql
supabase db query --linked -f scripts/ops/m26_remote_smoke_25.sql
```

## Verificación

```powershell
supabase db query --linked "select version from supabase_migrations.schema_migrations where version in ('072','073','074') order by version;"
```

No usar `supabase db push` global. No producción.
