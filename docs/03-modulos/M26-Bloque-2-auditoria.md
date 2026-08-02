# M26 Bloque 2 — Auditoría

Migración 072: tablas `m26_visual_match_suggestions`, `m26_duplicate_candidates`, `m26_assistance_sessions`, `m26_evaluated_recommendations`.

- RLS deny-all para `authenticated`; acceso exclusivo vía RPC `SECURITY DEFINER`.
- Proyecciones públicas sin `requester_user_id`, `owner_user_id` ni IDs internos.
- Recomendaciones elegibles: `human_reviewed = true` y `status = 'APPROVED'`.
- Sin pagos ni tablas M24.
- Asistencia stub documentada; no sustituye flujos M04.

Estado: migración creada localmente; **no aplicada** a staging.
