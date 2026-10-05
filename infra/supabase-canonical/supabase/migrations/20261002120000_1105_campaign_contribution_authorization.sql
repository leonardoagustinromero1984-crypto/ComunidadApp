-- 1105: Campaign contribution authorization.
-- Incremental. Does not edit 1085.
--
-- Replaces donation_campaigns.created_by = auth.uid() as administrative authority.
-- Manager means: the campaign has an organization_id AND the caller has an ACTIVE
-- membership on that organization with permission org.edit
-- (public._acl_org_permission, migration 1034: role permission or granted_permissions).
-- There is no separate donations permission in the canonical catalog.
-- org.edit is the existing organization-administration capability.
-- A null organization_id is not manageable. created_by is not backfilled into
-- memberships. Historical CONFIRMED rows stay CONFIRMED; the public total is a
-- sum of those rows, not a stored counter, so no total rewrite is required.
--
-- Self-validation ban: contributor_user_id = auth.uid() cannot confirm or reject
-- that row, even when the same user has org.edit. Another authorized member must
-- do it. FORBIDDEN is raised before any UPDATE, so status, confirmed_at,
-- confirmed_by and the CONFIRMED sum do not change.
-- A second confirm of an already CONFIRMED row by a different authorized member
-- returns the current row and does not write again, so the sum cannot double.
--
-- Layers, from the client inward:
-- 1. Table privileges. public, anon and authenticated have no SELECT/INSERT/
--    UPDATE/DELETE on donation_contributions. A direct UPDATE of status from
--    an authenticated client is rejected before a row is touched.
-- 2. RLS. Enabled since 1020 and re-enabled here. No UPDATE (or any) policy is
--    added, so a later accidental GRANT still cannot write rows for authenticated.
-- 3. Writers. canon_declare / canon_confirm / canon_reject are SECURITY DEFINER
--    and are the only client path that mutates donation_contributions.
-- 4. Manager predicate. _canon_campaign_is_manager is not executable by
--    authenticated. It requires organization_id + active org.edit.
-- 5. Self-validation ban inside confirm and reject, applied before the manager
--    write and before the idempotent return.
-- 6. Public total. canon_get_donation_campaign sums amount_minor where
--    status = 'CONFIRMED' only.

create or replace function public._canon_campaign_is_manager(p_campaign_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
      from public.donation_campaigns d
     where d.id = p_campaign_id
       and d.organization_id is not null
       and public._acl_org_permission(auth.uid(), d.organization_id, 'org.edit')
  );
$$;

create or replace function public.canon_get_donation_campaign(p_campaign_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v jsonb;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select jsonb_build_object(
    'id', d.id,
    'title', d.title,
    'description', coalesce(d.title, ''),
    'alias_cbu', d.alias_cbu,
    'payment_alias', d.alias_cbu,
    'status', d.status,
    'organization_id', d.organization_id,
    'created_by', d.created_by,
    'can_manage', public._canon_campaign_is_manager(d.id),
    'confirmed_amount_minor', coalesce((
      select sum(c.amount_minor) from public.donation_contributions c
       where c.campaign_id = d.id and c.status = 'CONFIRMED'
    ), 0),
    'confirmed_contribution_count', coalesce((
      select count(*) from public.donation_contributions c
       where c.campaign_id = d.id and c.status = 'CONFIRMED'
    ), 0),
    'pending_contribution_count', coalesce((
      select count(*) from public.donation_contributions c
       where c.campaign_id = d.id and c.status = 'PENDING'
    ), 0),
    'currency', 'ARS'
  )
    into v
    from public.donation_campaigns d
   where d.id = p_campaign_id;
  if v is null then raise exception 'NOT_FOUND'; end if;
  return v;
end;
$$;

create or replace function public.canon_confirm_campaign_contribution(p_contribution_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v public.donation_contributions%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v from public.donation_contributions where id = p_contribution_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v.contributor_user_id = auth.uid() then
    raise exception 'FORBIDDEN';
  end if;
  if not public._canon_campaign_is_manager(v.campaign_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v.status = 'CONFIRMED' then
    return jsonb_build_object('id', v.id, 'status', v.status, 'amount_minor', v.amount_minor);
  end if;
  if v.status <> 'PENDING' then raise exception 'VALIDATION'; end if;
  update public.donation_contributions
     set status = 'CONFIRMED',
         confirmed_at = timezone('utc', now()),
         confirmed_by = auth.uid()
   where id = p_contribution_id
     and status = 'PENDING'
     and contributor_user_id is distinct from auth.uid();
  return jsonb_build_object('id', p_contribution_id, 'status', 'CONFIRMED', 'amount_minor', v.amount_minor);
end;
$$;

create or replace function public.canon_reject_campaign_contribution(p_contribution_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v public.donation_contributions%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v from public.donation_contributions where id = p_contribution_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v.contributor_user_id = auth.uid() then
    raise exception 'FORBIDDEN';
  end if;
  if not public._canon_campaign_is_manager(v.campaign_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v.status = 'REJECTED' then
    return jsonb_build_object('id', v.id, 'status', v.status);
  end if;
  if v.status <> 'PENDING' then raise exception 'VALIDATION'; end if;
  update public.donation_contributions
     set status = 'REJECTED',
         confirmed_at = timezone('utc', now()),
         confirmed_by = auth.uid()
   where id = p_contribution_id
     and status = 'PENDING'
     and contributor_user_id is distinct from auth.uid();
  return jsonb_build_object('id', p_contribution_id, 'status', 'REJECTED');
end;
$$;

create or replace function public.canon_list_campaign_contributions(p_campaign_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._canon_campaign_is_manager(p_campaign_id)
     and not exists (
       select 1 from public.donation_contributions c
        where c.campaign_id = p_campaign_id and c.contributor_user_id = auth.uid()
     )
  then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id,
      'campaign_id', c.campaign_id,
      'amount_minor', coalesce(c.amount_minor, 0),
      'currency', coalesce(c.currency, 'ARS'),
      'status', c.status,
      'note', c.note,
      'created_at', c.created_at,
      'confirmed_at', c.confirmed_at,
      'confirmed_by', c.confirmed_by,
      'mine', c.contributor_user_id = auth.uid()
    ) order by c.created_at desc)
      from public.donation_contributions c
     where c.campaign_id = p_campaign_id
       and (
         public._canon_campaign_is_manager(p_campaign_id)
         or c.contributor_user_id = auth.uid()
       )
  ), '[]'::jsonb);
end;
$$;

revoke all on function public._canon_campaign_is_manager(uuid) from public, anon, authenticated;
revoke all on function public.canon_get_donation_campaign(uuid) from public, anon;
revoke all on function public.canon_confirm_campaign_contribution(uuid) from public, anon;
revoke all on function public.canon_reject_campaign_contribution(uuid) from public, anon;
revoke all on function public.canon_list_campaign_contributions(uuid) from public, anon;

grant execute on function public.canon_get_donation_campaign(uuid) to authenticated;
grant execute on function public.canon_confirm_campaign_contribution(uuid) to authenticated;
grant execute on function public.canon_reject_campaign_contribution(uuid) to authenticated;
grant execute on function public.canon_list_campaign_contributions(uuid) to authenticated;

revoke all on table public.donation_contributions from public, anon, authenticated;
alter table public.donation_contributions enable row level security;

notify pgrst, 'reload schema';
