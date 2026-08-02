# M19 Bloque 4 — Auditoría de paridad

**Fecha:** 2026-08-02  
**Migraciones:** 060 (base), **061 requerida y creada**

## Matriz de paridad (Kotlin / mock / 060 / Supabase / solución)

| # | Función | Kotlin | Mock | 060 | Supabase | Brecha | Solución |
|---|---------|--------|------|-----|----------|--------|----------|
| 1 | Posts | Sí | Sí | Sí | Sí | — | 060 |
| 2 | Estados | ARCHIVED, REMOVED_BY_MODERATION | Sí | Parcial | Sí | 060 sin ARCHIVED | **061** |
| 3 | Visibilidad PUBLIC/ORG | Sí | Sí | No | Sí | — | **061** |
| 4 | Referencias M08/M16/M17/M18 | Sí | Sí | No | Sí | JSON | **061** |
| 5 | Media M05 refs | Sí | Sí | cover only | Sí | JSON attachments | **061** |
| 6 | Comentarios | Sí | Sí | Sí | Sí | — | 060 |
| 7 | Respuestas anidadas | No | No | No | No | Fuera alcance | — |
| 8 | Edición post | Sí | Sí | Sí | Sí | — | 060 |
| 9 | Archivado post | Sí | Sí | No | Sí | RPC | **061** |
| 10 | Moderación M04 | Adapter | Sí | moderation_status | Sí | vía M04 | 060+061 |
| 11 | Reacciones LOVE | Sí | Sí | No | Sí | enum | **061** |
| 12 | Conteos agregados | Sí | Sí | Parcial | Sí | love_count | **061** |
| 13 | Feed paginado | Sí | Sí | lista plana | Sí | cursor RPC | **061** |
| 14 | Cursor estable | publishedAt\|id | Sí | No | Sí | — | **061** |
| 15 | Filtros por kind | Sí | Sí | query/org | Sí | p_kind | **061** |
| 16 | Reportes M04 | Adapter | Sí | N/A | N/A | cola M04 | mock+adapter |
| 17 | Seguimiento | No | No | No | No | Fuera alcance D01 | documentado |
| 18 | Guardados | No | No | No | No | Futuro | documentado |
| 19 | Privacidad PII | Sanitizer | Sí | RPC JSON | Sí | — | 060+061 |
| 20 | Idempotencia | Sí | Sí | Parcial | Sí | archive/reaction | **061** |

## Conclusión 061

**061 REQUERIDA** — 060 no cubría feed cursor, ARCHIVED, LOVE, visibility, references/media JSON, edit/archive comment.

## Staging

- Entorno: `wystsapjfpdtoprlmizz` (no producción)
- 060 aplicada vía `supabase db query --linked`
- 061 aplicada vía `supabase db query --linked`
- `schema_migrations`: 060, 061 registradas (`migration repair`)

## Navegación

Comunidad → Red social (M19). Sumate → Eventos permanece M18.
