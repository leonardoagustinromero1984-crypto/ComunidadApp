# M27 Bloque 4 — Auditoría de paridad y seguridad

Staging: `wystsapjfpdtoprlmizz` (no productivo). Migraciones aplicadas: **075**, **076**, **077**.

| Punto | Kotlin | Mock | 075 | 076/077 | Supabase RPC | Brecha | Solución |
|-------|--------|------|-----|---------|--------------|--------|----------|
| Apps integradoras | Sí | Sí | Sí | Sí | Sí | — | — |
| Contratos/scopes | Sí | Sí | Sí | Sí | Sí | — | — |
| Credenciales hash | Sí | Sí | Parcial | Sí | Sí | 075 sin ops | 076 |
| Webhooks/SSRF | Sí | Sí | Sí | Sí | Sí | Regex `\w` roto | **077** |
| Eventos/entregas | Sí | Sí | — | Sí | Sí | — | — |
| Firma HMAC stub | Sí | Sí | — | Sí | Sí | pgcrypto schema | **077** |
| Idempotencia | Sí | Sí | — | Sí | Sí | Stale keys | **077** |
| Rate limit | Sí | Sí | Sí | Sí | Sí | — | — |
| OAuth stub | Sí | Sí | Sí | Sí | Sí | — | — |
| Auditoría append-only | Sí | Sí | — | Sí | Sí | — | — |
| RLS deny + RPC | — | — | Sí | Sí | Sí | — | — |
| Legacy 075 RPCs | Parcial | Sí | Sí | — | Sí | key_hash/secret | **077** |

**076 requerida:** operaciones Bloque 3 (tablas, RPCs, RLS, entrega simulada).  
**077 requerida:** pgcrypto (`extensions`), SSRF/URL check, idempotencia stale, legacy `issue_api_key`/`register_webhook`, HMAC entrega.

Entrega HTTP externa: **simulada** (sin Internet en tests). OAuth: **stub neutral**. M24: **pospuesto**.
