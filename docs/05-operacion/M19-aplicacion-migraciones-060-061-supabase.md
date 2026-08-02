# Operación — migraciones 060 y 061 (M19 red social extendida)

**LeoVer** · Supabase staging/pruebas · **no producción**.

## Archivos canónicos

```text
supabase/migrations/060_m19_social_posts_and_engagement.sql
supabase/migrations/061_m19_social_feed_media_and_moderation.sql
```

## Estado actual (post cierre M19)

```text
060 — APLICADA EN STAGING NO PRODUCTIVO
061 — APLICADA EN STAGING NO PRODUCTIVO
Validación remota M19 — 105/105 PASS
```

Registro en `supabase_migrations.schema_migrations`: 060, 061.

Validación: `scripts/ops/m19_remote_validation_060_061.sql`

## Procedimiento (referencia)

1. Confirmar entorno **no productivo** (`wystsapjfpdtoprlmizz`).
2. Aplicar 060 completo (si no aplicada).
3. Aplicar 061 completo.
4. Registrar historial si aplica: `supabase migration repair 060 --status applied --linked`
5. Ejecutar validación remota.
6. Smoke RPC feed paginado:

```sql
select public.m19_list_public_feed_page(null, null, null, 10, 'ALL');
```

## Rollback

Forward-only; no editar 060/061 retroactivamente.

## App

Con `useSupabase = true`, `DataProvider.m19SocialRepository` usa `SupabaseM19SocialRepository` con RPC `m19_list_public_feed_page`.
