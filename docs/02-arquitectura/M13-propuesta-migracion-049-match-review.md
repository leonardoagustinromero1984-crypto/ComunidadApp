# M13 — Propuesta exacta de migración 049 (revisión humana remota)

**LeoVer** · Estado: **PROPUESTA** · **no creada** en el repo · requiere aprobación explícita.

## Por qué es imprescindible

La migración **048** (aplicada en pruebas, validación estructural 13/13 PASS) ya incluye:

- `lost_found_match_candidates`
- `lost_found_match_decisions`
- `lost_found_match_status_history`
- helpers `_m13_can_write_case`, `_m13_can_manage_case`, `_m13_append_candidate_history`
- permiso `lostfound.match.confirm`

pero **no** expone RPC cliente de revisión. RLS deniega INSERT/UPDATE del cliente en decisiones/historial (`with check (false)` / `using (false)`). Sin 049 no hay escritura autoritativa remota de confirm/reject.

## Archivo propuesto (no crear hasta aprobación)

```text
supabase/migrations/049_m13_match_review_decisions.sql
```

## RPC cliente exactas

| RPC | Transición | Autoridad |
|-----|------------|-----------|
| `m13_open_match_review(p_candidate_id uuid)` | `PROPOSED → UNDER_REVIEW` | dueño caso / `match.review` / moderate |
| `m13_confirm_match_candidate(p_candidate_id uuid, p_reason_code text, p_note_private text)` | `UNDER_REVIEW → CONFIRMED` | dueño / `match.confirm` / moderate |
| `m13_reject_match_candidate(...)` | `UNDER_REVIEW → REJECTED` | idem |
| `m13_mark_match_inconclusive(...)` | `UNDER_REVIEW → INCONCLUSIVE` | idem |
| `m13_withdraw_match_candidate(...)` | `PROPOSED\|UNDER_REVIEW → WITHDRAWN` | idem |
| `m13_expire_match_candidate(...)` | `PROPOSED\|UNDER_REVIEW → EXPIRED` | sistema/gestor |
| `m13_list_match_decisions(p_candidate_id uuid)` | lectura | manage/read |
| `m13_list_match_status_history(p_candidate_id uuid)` | lectura | manage/read |

## Reglas SQL obligatorias

- `SECURITY DEFINER` + `search_path = public`
- actor solo `auth.uid()`
- `SELECT … FOR UPDATE` del candidato en transiciones
- una decisión final por candidato (unique parcial o check previo)
- reintento idempotente del mismo terminal → success sin duplicar fila
- historial append-only vía `_m13_append_candidate_history`
- al `CONFIRMED`: actualizar detalle/sighting a `CONFIRMED`; **no** cerrar `lost_found_posts` automáticamente
- revoke helpers; grant `EXECUTE` solo `authenticated` en RPC cliente
- sin `service_role` en Android; sin IA; sin autoconfirmación

## Android post-049

Reemplazar stubs `MATCH_REVIEW_RPC_UNAVAILABLE` en `SupabaseM13MatchRepository` por wrappers RPC; cablear timeline remoto.

## CI

Actualizar deliberadamente el guard M07 de highest `048` → `049` en el mismo bloque que cree el archivo.

## Fuera de alcance de 049

Pagos, chat, historia clínica, apply automático, APK, cierre oficial M12.
