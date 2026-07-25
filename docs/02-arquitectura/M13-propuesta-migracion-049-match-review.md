# M13 — Propuesta exacta de migración 049 (revisión humana remota)

**LeoVer** · Estado: **IMPLEMENTADA EN REPO** · archivo `049_m13_match_review_workflow.sql` · **no aplicada remotamente**.

## Por qué era imprescindible

La migración **048** (aplicada en pruebas, validación estructural 13/13 PASS) ya incluye:

- `lost_found_match_candidates`
- `lost_found_match_decisions`
- `lost_found_match_status_history`
- helpers `_m13_can_write_case`, `_m13_can_manage_case`, `_m13_append_candidate_history`
- permiso `lostfound.match.confirm`

pero **no** exponía RPC cliente de revisión. RLS deniega INSERT/UPDATE del cliente. 049 cierra ese gap.

## Archivo

```text
supabase/migrations/049_m13_match_review_workflow.sql
```

(Nombre canónico del prompt de ejecución; la propuesta temprana usaba `…_decisions.sql`.)

## RPC cliente exactas

| RPC | Transición | Autoridad |
|-----|------------|-----------|
| `m13_open_match_review(p_candidate_id uuid)` | `PROPOSED → UNDER_REVIEW` | dueño caso / `match.review` / moderate |
| `m13_confirm_match_candidate(p_candidate_id uuid, p_reason_code text, p_note_private text)` | `UNDER_REVIEW → CONFIRMED` | dueño / `match.confirm` / moderate |
| `m13_reject_match_candidate(...)` | `UNDER_REVIEW → REJECTED` | idem confirm |
| `m13_mark_match_inconclusive(...)` | `UNDER_REVIEW → INCONCLUSIVE` | review o confirm |
| `m13_withdraw_match_candidate(...)` | `PROPOSED\|UNDER_REVIEW → WITHDRAWN` | review |
| `m13_expire_match_candidate(...)` | `PROPOSED\|UNDER_REVIEW → EXPIRED` | review |
| `m13_list_match_decisions(p_candidate_id uuid)` | lectura | manage/read |
| `m13_list_match_status_history(p_candidate_id uuid)` | lectura | manage/read |

## Reglas SQL

- `SECURITY DEFINER` + `search_path = public`
- actor solo `auth.uid()`
- `SELECT … FOR UPDATE`
- índice único `lost_found_match_decisions_candidate_uniq`
- historial via `_m13_append_candidate_history`
- al `CONFIRMED`: actualiza `lost_found_sighting_details`; **no** cierra `lost_found_posts`
- grants solo `authenticated` en RPC cliente

## CI

Guard M07 highest = **049** (falla ante 050 sin update deliberado).
