# M13 — Revisión humana remota (migración 049)

**LeoVer** · Estado: **CERRADA LOCALMENTE** · Migración **049** creada · **no aplicada remotamente**.

## Por qué 049

048 dejó tablas/helpers de decisión e historial, pero RLS bloquea DML cliente y no había RPC de revisión. Sin 049 el path Supabase de open/confirm/reject era imposible.

## RPC (8)

`m13_open_match_review`, `m13_confirm_match_candidate`, `m13_reject_match_candidate`, `m13_mark_match_inconclusive`, `m13_withdraw_match_candidate`, `m13_expire_match_candidate`, `m13_list_match_decisions`, `m13_list_match_status_history`.

## Transiciones

```text
PROPOSED → UNDER_REVIEW
UNDER_REVIEW → CONFIRMED | REJECTED | INCONCLUSIVE
PROPOSED|UNDER_REVIEW → WITHDRAWN | EXPIRED
```

## Garantías

- `auth.uid()` + `FOR UPDATE`
- una decisión final (índice único)
- historial append-only
- confirm marca sighting details `CONFIRMED`; **no** cierra `lost_found_posts`
- sin autoconfirmación

## Android

`SupabaseM13RemoteDataSource` + `SupabaseM13MatchRepository` cableados a las 8 RPC; timeline remoto vía list decisions/history.

## Pendientes

- Apply remoto de 049 + validación estructural
- Smoke remoto de revisión
- Smoke funcional B2 externo
