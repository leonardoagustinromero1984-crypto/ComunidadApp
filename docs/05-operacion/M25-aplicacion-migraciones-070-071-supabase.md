# Operación — migraciones 070–071 (M25 marketplace)

**LeoVer** · staging `wystsapjfpdtoprlmizz` · **no producción**.

## Estado

```text
070_m25_marketplace_catalog_cart_and_orders.sql — APLICADA staging 2026-08-02
071_m25_marketplace_operations_inventory_and_returns.sql — APLICADA staging 2026-08-02
```

## Procedimiento ejecutado

1. Confirmado staging no productivo (`wystsapjfpdtoprlmizz`).
2. Aplicado **070** completo vía `supabase db query --linked -f`.
3. Registrado `070` en `schema_migrations`.
4. Aplicado **071** completo.
5. Registrado `071` en `schema_migrations`.
6. Validación 120/120 PASS + smoke 25/25 PASS.

## Verificación

```powershell
supabase db query --linked "select version from supabase_migrations.schema_migrations where version in ('070','071') order by version;"
supabase db query --linked -f scripts/ops/m25_remote_validation_070_071.sql
supabase db query --linked -f scripts/ops/m25_remote_smoke_25.sql
```

## Límites

- No `supabase db push` global.
- No producción.
- **M24 pospuesto** — sin cobros.
