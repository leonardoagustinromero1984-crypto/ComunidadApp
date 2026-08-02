# M27 — Auditoría inicial

Bloque 1 implementa la base local para integraciones y API pública de LeoVer: webhooks, OAuth stub, claves API, límites/sandbox y contratos versionados.

- Persistencia: memoria determinista; no hay SQL ni wiring Supabase en Bloque 1.
- Integración M24: explícitamente excluida (sin pagos).
- OAuth: stub local; no hay proveedor OAuth real ni flujo de autorización externo.
- Privacidad: las proyecciones públicas no incluyen IDs internos ni secretos completos; solo prefijos (`whsec_`, `lv_cli_`, `lvk_`).
