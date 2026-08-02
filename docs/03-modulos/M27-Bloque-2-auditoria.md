# M27 Bloque 2 — Auditoría

Migración **075** (`075_m27_integrations_webhooks_oauth_limits_sandbox.sql`):

- Tablas: `m27_webhook_endpoints`, `m27_oauth_applications`, `m27_api_credentials`, `m27_rate_limit_quotas`, `m27_api_contracts`.
- RLS deny-all en tablas; acceso vía RPC `security definer`.
- RPCs de lectura/escritura alineados con `M27IntegrationRepository`.
- Cuotas seed PRODUCTION/SANDBOX; contrato v1 publicado por defecto.
- Sin pagos M24; OAuth stub (sin proveedor externo).

**Estado:** migración creada, **no aplicada** a staging.
