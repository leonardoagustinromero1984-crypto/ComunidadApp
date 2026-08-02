# M27 Bloque 3 — Auditoría (operaciones)

Matriz de cobertura (Kotlin / mock / 075 / Supabase / UI / brecha):

| # | Capacidad | Kotlin | Mock | 075 | Supabase | UI | Brecha |
|---|-----------|--------|------|-----|----------|-----|--------|
| 1 | Aplicaciones integradoras | Sí | Sí | Parcial | RPC 076 | Sí | orgId interno |
| 2 | Contratos | Sí | Sí | Sí | Sí | Sí | — |
| 3 | Ambientes | Sí | Sí | Parcial | 076 STAGING | Sí | PROD deshabilitado |
| 4 | Claves | Sí | Sí | Parcial | 076 hash | Sí | plaintext una vez |
| 5 | Scopes allowlist | Sí | Sí | No | 076 | Sí | — |
| 6 | Webhooks | Sí | Sí | Parcial | 076 | Sí | — |
| 7 | Suscripciones | Sí | Sí | No | 076 | Parcial | — |
| 8 | Eventos | Sí | Sí | No | 076 | Parcial | — |
| 9 | Entregas | Sí | Sí | No | 076 | Sí | simuladas |
| 10 | Firma HMAC stub | Sí | Sí | No | 076 | — | no worker real |
| 11 | Reintentos | Sí | Sí | No | 076 | Sí | max 3 |
| 12 | Idempotencia | Sí | Sí | No | 076 | — | client_request_id |
| 13 | Límites | Sí | Sí | Cuotas | 076 contador | Sí | — |
| 14 | Sandbox | Sí | Sí | Sí | 076 | Sí | sin Internet |
| 15 | OAuth neutral | Sí | Sí | Stub | 076 | Sí | sin proveedor |
| 16 | Revocación | Sí | Sí | Parcial | 076 | Sí | — |
| 17 | Auditoría | Sí | Sí | No | 076 | Sí | append-only |
| 18 | Privacidad | Sí | Sí | Parcial | 076 | Sí | sin secretos |
| 19 | Moderación | Parcial | Sí | No | 076 SUSPENDED | — | admin stub |
| 20 | Resiliencia | Sí | Sí | — | — | Sí | — |

**075 no aplicada.** Bloque 4 cerrará brechas vía **076** y validación remota 130/130.
