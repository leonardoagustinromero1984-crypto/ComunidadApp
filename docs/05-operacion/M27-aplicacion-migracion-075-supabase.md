# M27 — Aplicación migración 075 (Supabase)

**Proyecto staging:** `wystsapjfpdtoprlmizz`  
**Migración:** `075_m27_integrations_webhooks_oauth_limits_sandbox.sql`  
**Estado:** PENDIENTE — no aplicar sin autorización explícita.

## Precondiciones

- 074 aplicada en staging (`schema_migrations` incluye 071–074).
- Working tree limpio con commit `feat(m27): add integrations-and-public-api persistence`.

## Aplicación (cuando corresponda)

```powershell
# Desde el directorio del repo, con Supabase CLI vinculado a staging
supabase db push --linked
# o aplicar el SQL vía SQL Editor / psql contra staging
```

## Verificación post-aplicación

1. `select version from supabase_migrations.schema_migrations where version like '075%';`
2. Tablas `m27_*` existentes con RLS habilitado.
3. RPC `m27_list_rate_limits()` retorna 2 filas (PRODUCTION, SANDBOX).
4. RPC `m27_list_published_contracts()` incluye contrato v1.

## Rollback

Forward-only; rollback manual solo en emergencia documentada.
