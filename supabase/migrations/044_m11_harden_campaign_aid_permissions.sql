-- =============================================================================
-- LeoVer M11 — migración 044: hardening de permisos (campañas e insumos)
-- Correctiva sobre 043 ya aplicada. No modifica datos ni lógica funcional.
-- No recrea tablas/funciones. LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Tablas Bloque 2 — solo SELECT para authenticated
-- ---------------------------------------------------------------------------
revoke all privileges on table public.shelter_campaigns from public;
revoke all privileges on table public.shelter_campaigns from anon;
revoke all privileges on table public.shelter_campaigns from authenticated;

revoke all privileges on table public.shelter_campaign_updates from public;
revoke all privileges on table public.shelter_campaign_updates from anon;
revoke all privileges on table public.shelter_campaign_updates from authenticated;

revoke all privileges on table public.shelter_supply_requests from public;
revoke all privileges on table public.shelter_supply_requests from anon;
revoke all privileges on table public.shelter_supply_requests from authenticated;

revoke all privileges on table public.shelter_supply_contributions from public;
revoke all privileges on table public.shelter_supply_contributions from anon;
revoke all privileges on table public.shelter_supply_contributions from authenticated;

grant select on table public.shelter_campaigns to authenticated;
grant select on table public.shelter_campaign_updates to authenticated;
grant select on table public.shelter_supply_requests to authenticated;
grant select on table public.shelter_supply_contributions to authenticated;

alter table public.shelter_campaigns enable row level security;
alter table public.shelter_campaign_updates enable row level security;
alter table public.shelter_supply_requests enable row level security;
alter table public.shelter_supply_contributions enable row level security;

-- ---------------------------------------------------------------------------
-- 2. Helpers internos de 043 — sin EXECUTE para PUBLIC / anon / authenticated
-- ---------------------------------------------------------------------------
revoke all on function public._m11_is_safe_evidence_ref(text) from public;
revoke all on function public._m11_is_safe_evidence_ref(text) from anon;
revoke all on function public._m11_is_safe_evidence_ref(text) from authenticated;

revoke all on function public._m11_require_safe_evidence(text) from public;
revoke all on function public._m11_require_safe_evidence(text) from anon;
revoke all on function public._m11_require_safe_evidence(text) from authenticated;

revoke all on function public._m11_recompute_supply_status(integer, integer, integer, timestamptz, timestamptz, text) from public;
revoke all on function public._m11_recompute_supply_status(integer, integer, integer, timestamptz, timestamptz, text) from anon;
revoke all on function public._m11_recompute_supply_status(integer, integer, integer, timestamptz, timestamptz, text) from authenticated;

revoke all on function public._m11_sync_supply_request_totals(uuid) from public;
revoke all on function public._m11_sync_supply_request_totals(uuid) from anon;
revoke all on function public._m11_sync_supply_request_totals(uuid) from authenticated;

revoke all on function public._m11_open_supply_request_statuses() from public;
revoke all on function public._m11_open_supply_request_statuses() from anon;
revoke all on function public._m11_open_supply_request_statuses() from authenticated;

-- ---------------------------------------------------------------------------
-- 3. RPC públicas del cliente (firmas exactas de 043)
--    REVOKE PUBLIC/anon + GRANT EXECUTE solo authenticated
-- ---------------------------------------------------------------------------
revoke all on function public.m11_create_shelter_campaign(uuid, text, text, text, text, timestamptz, timestamptz, text, boolean) from public;
revoke all on function public.m11_create_shelter_campaign(uuid, text, text, text, text, timestamptz, timestamptz, text, boolean) from anon;
grant execute on function public.m11_create_shelter_campaign(uuid, text, text, text, text, timestamptz, timestamptz, text, boolean) to authenticated;

revoke all on function public.m11_update_shelter_campaign(uuid, text, text, text, text, timestamptz, timestamptz, text) from public;
revoke all on function public.m11_update_shelter_campaign(uuid, text, text, text, text, timestamptz, timestamptz, text) from anon;
grant execute on function public.m11_update_shelter_campaign(uuid, text, text, text, text, timestamptz, timestamptz, text) to authenticated;

revoke all on function public.m11_change_shelter_campaign_status(uuid, text) from public;
revoke all on function public.m11_change_shelter_campaign_status(uuid, text) from anon;
grant execute on function public.m11_change_shelter_campaign_status(uuid, text) to authenticated;

revoke all on function public.m11_get_shelter_campaign(uuid) from public;
revoke all on function public.m11_get_shelter_campaign(uuid) from anon;
grant execute on function public.m11_get_shelter_campaign(uuid) to authenticated;

revoke all on function public.m11_list_public_shelter_campaigns() from public;
revoke all on function public.m11_list_public_shelter_campaigns() from anon;
grant execute on function public.m11_list_public_shelter_campaigns() to authenticated;

revoke all on function public.m11_list_shelter_campaigns(uuid) from public;
revoke all on function public.m11_list_shelter_campaigns(uuid) from anon;
grant execute on function public.m11_list_shelter_campaigns(uuid) to authenticated;

revoke all on function public.m11_add_shelter_campaign_update(uuid, text, text, text) from public;
revoke all on function public.m11_add_shelter_campaign_update(uuid, text, text, text) from anon;
grant execute on function public.m11_add_shelter_campaign_update(uuid, text, text, text) to authenticated;

revoke all on function public.m11_list_shelter_campaign_updates(uuid) from public;
revoke all on function public.m11_list_shelter_campaign_updates(uuid) from anon;
grant execute on function public.m11_list_shelter_campaign_updates(uuid) to authenticated;

revoke all on function public.m11_create_supply_request(uuid, text, integer, text, text, text, uuid, text, timestamptz, text, text, boolean) from public;
revoke all on function public.m11_create_supply_request(uuid, text, integer, text, text, text, uuid, text, timestamptz, text, text, boolean) from anon;
grant execute on function public.m11_create_supply_request(uuid, text, integer, text, text, text, uuid, text, timestamptz, text, text, boolean) to authenticated;

revoke all on function public.m11_update_supply_request(uuid, text, integer, text, text, text, text, timestamptz, text, text) from public;
revoke all on function public.m11_update_supply_request(uuid, text, integer, text, text, text, text, timestamptz, text, text) from anon;
grant execute on function public.m11_update_supply_request(uuid, text, integer, text, text, text, text, timestamptz, text, text) to authenticated;

revoke all on function public.m11_cancel_supply_request(uuid) from public;
revoke all on function public.m11_cancel_supply_request(uuid) from anon;
grant execute on function public.m11_cancel_supply_request(uuid) to authenticated;

revoke all on function public.m11_get_supply_request(uuid) from public;
revoke all on function public.m11_get_supply_request(uuid) from anon;
grant execute on function public.m11_get_supply_request(uuid) to authenticated;

revoke all on function public.m11_list_public_supply_requests() from public;
revoke all on function public.m11_list_public_supply_requests() from anon;
grant execute on function public.m11_list_public_supply_requests() to authenticated;

revoke all on function public.m11_list_shelter_supply_requests(uuid) from public;
revoke all on function public.m11_list_shelter_supply_requests(uuid) from anon;
grant execute on function public.m11_list_shelter_supply_requests(uuid) to authenticated;

revoke all on function public.m11_pledge_supply_contribution(uuid, integer, text) from public;
revoke all on function public.m11_pledge_supply_contribution(uuid, integer, text) from anon;
grant execute on function public.m11_pledge_supply_contribution(uuid, integer, text) to authenticated;

revoke all on function public.m11_cancel_supply_contribution(uuid) from public;
revoke all on function public.m11_cancel_supply_contribution(uuid) from anon;
grant execute on function public.m11_cancel_supply_contribution(uuid) to authenticated;

revoke all on function public.m11_confirm_supply_contribution(uuid) from public;
revoke all on function public.m11_confirm_supply_contribution(uuid) from anon;
grant execute on function public.m11_confirm_supply_contribution(uuid) to authenticated;

revoke all on function public.m11_record_supply_receipt(uuid, integer, text, text) from public;
revoke all on function public.m11_record_supply_receipt(uuid, integer, text, text) from anon;
grant execute on function public.m11_record_supply_receipt(uuid, integer, text, text) to authenticated;

revoke all on function public.m11_get_supply_contribution(uuid) from public;
revoke all on function public.m11_get_supply_contribution(uuid) from anon;
grant execute on function public.m11_get_supply_contribution(uuid) to authenticated;

revoke all on function public.m11_list_supply_contributions(uuid) from public;
revoke all on function public.m11_list_supply_contributions(uuid) from anon;
grant execute on function public.m11_list_supply_contributions(uuid) to authenticated;

comment on table public.shelter_campaigns is
  'M11 campaigns. Client DML via SECURITY DEFINER RPCs only; authenticated has SELECT + RLS.';

commit;
