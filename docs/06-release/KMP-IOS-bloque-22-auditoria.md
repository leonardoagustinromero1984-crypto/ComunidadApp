# KMP-IOS — Bloque 22 auditoría (Lost/Found owner management)

Fuente: PostgREST `lost_found_posts` + RLS `author_id` + Android `markResolved`.

## CONTRACTS

| Operación | Mecanismo | Estado |
| --------- | --------- | ------ |
| Resolve ACTIVE→RESOLVED | PostgREST update `{status, updated_at}` | REAL_REMOTE |
| Update description/location | PostgREST update (API shared) | REAL_REMOTE API; UI mínima no prioritaria |
| Hard delete | RLS permite; app no usa | **NO UI** (no inventar delete product) |
| CLOSED / cancel / reunited | No contrato productivo | BLOCKED |

## OWNER_RULE

UI: `viewerCanManage` derivado de `author_id == session` sin exponer authorId.
Autoridad: RLS.

## MEDIA_CHANGE

Reuso M05 publish path; no UI de reemplazo en este bloque.

## STATUS_CONTRACT

Única transición productiva Android: `ACTIVE → RESOLVED`.
