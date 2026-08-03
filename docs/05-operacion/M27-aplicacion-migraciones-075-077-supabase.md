# M27 — Aplicación migraciones 075–077 en Supabase staging

**Proyecto:** `wystsapjfpdtoprlmizz` (staging, no productivo)  
**Última migración previa:** 074 (M26)

## Orden aplicado

1. `075_m27_integrations_webhooks_oauth_limits_sandbox.sql`
2. `076_m27_integration_operations_security_and_delivery.sql`
3. `077_m27_idempotency_stale_resource_fix.sql` (correctiva: pgcrypto/SSRF/legacy/idempotencia)

```powershell
supabase link --project-ref wystsapjfpdtoprlmizz
supabase db query --linked -f supabase/migrations/075_m27_integrations_webhooks_oauth_limits_sandbox.sql
supabase db query --linked -f supabase/migrations/076_m27_integration_operations_security_and_delivery.sql
supabase db query --linked -f supabase/migrations/077_m27_idempotency_stale_resource_fix.sql
```

Registrar en `supabase_migrations.schema_migrations` si el CLI no lo hace automáticamente.

## Validación post-aplicación

```powershell
supabase db query --linked -f scripts/ops/m27_remote_validation_075_076.sql
supabase db query --linked -f scripts/ops/m27_remote_smoke_25.sql
```

Esperado: **130/130** y **25/25 PASS**.

## Notas

- No usar `db push` global ni tocar producción.
- 077 corrige: `digest`/`hmac` en schema `extensions`, regex SSRF/URL, claves legacy con `key_hash`, idempotencia huérfana.
- Limpieza de datos de prueba en scripts: deshabilitar trigger `m27_audit_no_update` antes de borrar auditoría.
