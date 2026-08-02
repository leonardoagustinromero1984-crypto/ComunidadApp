# M21 Bloque 4 — Auditoría de paridad

**Fecha:** 2026-08-02  
**Migraciones:** 064 (base), **065 requerida y creada**

## Matriz de paridad (Kotlin / mock / 064 / Supabase / solución)

| # | Función | Kotlin | Mock | 064 | Supabase | Brecha | Solución |
|---|---------|--------|------|-----|----------|--------|----------|
| 1 | Reseñas CRUD base | Sí | Sí | Sí | Sí | — | 064 |
| 2 | Sujetos y contextos | Sí | Sí | Parcial | Sí | context_id, labels | **065** |
| 3 | Elegibilidad persistente | Sí | Sí | No | Sí | `m21_eligibility_records` | **065** |
| 4 | Duplicado por contexto | Sí | Sí | No | Sí | índice + RPC | **065** |
| 5 | Estados ampliados | Sí | Sí | Parcial | Sí | EDITED, DISPUTED, APPEALED | **065** |
| 6 | Edición / archivado | Sí | Sí | No | Sí | RPC edit/archive | **065** |
| 7 | Respuestas del sujeto | Sí | Sí | No | Sí | `m21_review_responses` | **065** |
| 8 | Agregados / distribución | Sí | Sí | Parcial | Sí | countable statuses | **065** + hotfix |
| 9 | Moderación M04 | Adapter | Sí | N/A | Sí | `m21_report_review` | **065** |
| 10 | Disputas | Sí | Sí | No | Sí | `m21_review_disputes` | **065** |
| 11 | Antiabuso (señales) | Sí | Sí | No | Parcial | interno mock | mock + RPC guards |
| 12 | Verificaciones ampliadas | Sí | Sí | Sí | Sí | evidence_ref M05 | 064+065 |
| 13 | Evidencias privadas | Sí | Sí | Sí | Sí | no SELECT público | 064 RLS |
| 14 | Expiración / revocación | Sí | Sí | Parcial | Sí | estados terminales | 064 |
| 15 | Privacidad JSON | Sí | Sí | Sí | Sí | scrub PII | 065 helpers |
| 16 | Idempotencia submit | Sí | Sí | Parcial | Sí | context uniq | **065** + hotfix |
| 17 | Resumen propio post-edición | Sí | Sí | No | Sí | solo PUBLISHED | **065** + hotfix |
| 18 | Apelaciones | Sí | Sí | Sí | Sí | `m21_submit_appeal` | 064 |
| 19 | M06 hooks | Stub | Stub | N/A | N/A | allowlist | documentado |
| 20 | Remote datasource B3 | Sí | N/A | N/A | Sí | RPC 065 | Kotlin B4 |

## Conclusión 065

**065 REQUERIDA** — 064 no cubría elegibilidad persistente, respuestas, disputas, operaciones de edición/archivado, agregados contables extendidos ni unicidad por contexto.

Hotfix operativo `scripts/ops/m21_hotfix_post_065.sql` aplicado en staging para:
- breakdown sin columnas ambiguas;
- unicidad por contexto (no por target);
- elegibilidad sin contexto → `ELIGIBILITY_UNAVAILABLE`;
- `m21_get_my_reputation_summary` alineado a estados contables.

## Staging

- Entorno: `wystsapjfpdtoprlmizz` (no producción)
- 064 aplicada vía `supabase db query --linked`
- 065 aplicada vía `supabase db query --linked`
- Hotfix post-065 aplicado
- `schema_migrations`: 064, 065 registradas (`migration repair`)

## Navegación

Comunidad → Reputación (M21). Rutas `m21/hub`, `m21/subject/{type}/{id}`, `m21/reviews/{id}`.
