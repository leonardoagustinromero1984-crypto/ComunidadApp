-- =============================================================================
-- LeoVer M11 — migración 043: campañas, pedidos de insumos y red de ayuda
-- Forward-only sobre 001–042. NO reescribe 040/041/042.
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 shelter.campaign / supply / contribution
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('shelter.campaign.read', 'Ver campañas de refugio'),
  ('shelter.campaign.manage', 'Gestionar campañas de refugio'),
  ('shelter.supply.read', 'Ver pedidos de insumos'),
  ('shelter.supply.manage', 'Gestionar pedidos de insumos'),
  ('shelter.contribution.read', 'Ver aportes de insumos'),
  ('shelter.contribution.manage', 'Confirmar y registrar recepción de aportes')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in (
    'shelter.campaign.read', 'shelter.campaign.manage',
    'shelter.supply.read', 'shelter.supply.manage',
    'shelter.contribution.read', 'shelter.contribution.manage'
  )
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code in ('shelter.campaign.read', 'shelter.supply.read', 'shelter.contribution.read')
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.shelter_campaigns (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.shelter_profiles (id) on delete restrict,
  title text not null,
  description text not null,
  category text not null,
  visibility text not null,
  status text not null default 'DRAFT',
  starts_at timestamptz,
  ends_at timestamptz,
  cover_asset_ref text,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint shelter_campaigns_category_chk
    check (category = any (array[
      'FOOD','MEDICATION','HYGIENE','VETERINARY','TRANSPORT','INFRASTRUCTURE','EMERGENCY','OTHER'
    ]::text[])),
  constraint shelter_campaigns_visibility_chk
    check (visibility = any (array['PUBLIC','INTERNAL']::text[])),
  constraint shelter_campaigns_status_chk
    check (status = any (array['DRAFT','ACTIVE','PAUSED','COMPLETED','CANCELLED']::text[])),
  constraint shelter_campaigns_cover_ref_chk
    check (cover_asset_ref is null or cover_asset_ref ~* '^(m05://|file_asset:)'),
  constraint shelter_campaigns_title_chk check (char_length(trim(title)) > 0),
  constraint shelter_campaigns_description_chk check (char_length(trim(description)) > 0)
);

create index if not exists shelter_campaigns_shelter_idx
  on public.shelter_campaigns (shelter_profile_id);
create index if not exists shelter_campaigns_status_idx
  on public.shelter_campaigns (status);
create index if not exists shelter_campaigns_visibility_idx
  on public.shelter_campaigns (visibility);

create table if not exists public.shelter_campaign_updates (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.shelter_campaigns (id) on delete restrict,
  author_user_id uuid not null references public.users (id),
  visibility text not null,
  message text not null,
  evidence_ref text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint shelter_campaign_updates_visibility_chk
    check (visibility = any (array['PUBLIC','INTERNAL']::text[])),
  constraint shelter_campaign_updates_message_chk check (char_length(trim(message)) > 0),
  constraint shelter_campaign_updates_evidence_ref_chk
    check (evidence_ref is null or evidence_ref ~* '^(m05://|file_asset:)')
);

create index if not exists shelter_campaign_updates_campaign_idx
  on public.shelter_campaign_updates (campaign_id);
create index if not exists shelter_campaign_updates_created_idx
  on public.shelter_campaign_updates (created_at desc);

create table if not exists public.shelter_supply_requests (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.shelter_profiles (id) on delete restrict,
  campaign_id uuid references public.shelter_campaigns (id) on delete set null,
  category text not null,
  item_name text not null,
  description text,
  quantity_requested integer not null,
  quantity_committed integer not null default 0,
  quantity_received integer not null default 0,
  unit_text text not null,
  priority text not null default 'NORMAL',
  status text not null default 'DRAFT',
  expires_at timestamptz,
  public_notes text,
  internal_notes text,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint shelter_supply_category_chk
    check (category = any (array[
      'FOOD','MEDICATION','HYGIENE','VETERINARY','TRANSPORT','INFRASTRUCTURE','EMERGENCY','OTHER'
    ]::text[])),
  constraint shelter_supply_priority_chk
    check (priority = any (array['NORMAL','HIGH','URGENT']::text[])),
  constraint shelter_supply_status_chk
    check (status = any (array[
      'DRAFT','OPEN','PARTIALLY_COMMITTED','FULLY_COMMITTED',
      'PARTIALLY_RECEIVED','FULFILLED','EXPIRED','CANCELLED'
    ]::text[])),
  constraint shelter_supply_qty_requested_chk check (quantity_requested > 0),
  constraint shelter_supply_qty_committed_chk check (quantity_committed >= 0),
  constraint shelter_supply_qty_received_chk check (quantity_received >= 0),
  constraint shelter_supply_received_le_committed_chk check (quantity_received <= quantity_committed),
  constraint shelter_supply_item_name_chk check (char_length(trim(item_name)) > 0),
  constraint shelter_supply_unit_text_chk check (char_length(trim(unit_text)) > 0)
);

create index if not exists shelter_supply_requests_shelter_idx
  on public.shelter_supply_requests (shelter_profile_id);
create index if not exists shelter_supply_requests_campaign_idx
  on public.shelter_supply_requests (campaign_id);
create index if not exists shelter_supply_requests_status_idx
  on public.shelter_supply_requests (status);
create index if not exists shelter_supply_requests_expires_idx
  on public.shelter_supply_requests (expires_at);

create table if not exists public.shelter_supply_contributions (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.shelter_supply_requests (id) on delete restrict,
  contributor_user_id uuid not null references public.users (id),
  quantity_committed integer not null,
  quantity_received integer not null default 0,
  status text not null default 'PLEDGED',
  contributor_notes text,
  internal_receipt_notes text,
  evidence_ref text,
  committed_at timestamptz not null default timezone('utc', now()),
  received_at timestamptz,
  cancelled_at timestamptz,
  constraint shelter_supply_contrib_status_chk
    check (status = any (array[
      'PLEDGED','CONFIRMED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED','REJECTED'
    ]::text[])),
  constraint shelter_supply_contrib_qty_committed_chk check (quantity_committed > 0),
  constraint shelter_supply_contrib_qty_received_chk check (quantity_received >= 0),
  constraint shelter_supply_contrib_received_le_committed_chk
    check (quantity_received <= quantity_committed),
  constraint shelter_supply_contrib_evidence_ref_chk
    check (evidence_ref is null or evidence_ref ~* '^(m05://|file_asset:)')
);

create index if not exists shelter_supply_contributions_request_idx
  on public.shelter_supply_contributions (request_id);
create index if not exists shelter_supply_contributions_contributor_idx
  on public.shelter_supply_contributions (contributor_user_id);
create index if not exists shelter_supply_contributions_status_idx
  on public.shelter_supply_contributions (status);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m11_is_safe_evidence_ref(p_ref text)
returns boolean
language sql immutable parallel safe as $$
  select p_ref is null
    or (
      p_ref ~* '^(m05://|file_asset:)'
      and p_ref !~* '^https?://'
      and p_ref !~* 'object/public/leover'
    );
$$;

create or replace function public._m11_require_safe_evidence(p_ref text)
returns void
language plpgsql immutable as $$
begin
  if not public._m11_is_safe_evidence_ref(p_ref) then
    raise exception 'SHELTER_EVIDENCE_REF_INVALID';
  end if;
end;
$$;

create or replace function public._m11_recompute_supply_status(
  p_requested integer,
  p_committed integer,
  p_received integer,
  p_expires_at timestamptz,
  p_now timestamptz,
  p_current text
) returns text
language plpgsql immutable as $$
begin
  if p_current in ('CANCELLED', 'DRAFT') then
    return p_current;
  end if;
  if coalesce(p_received, 0) >= greatest(coalesce(p_requested, 0), 1) then
    return 'FULFILLED';
  end if;
  if coalesce(p_received, 0) > 0 then
    return 'PARTIALLY_RECEIVED';
  end if;
  if coalesce(p_committed, 0) >= greatest(coalesce(p_requested, 0), 1) then
    return 'FULLY_COMMITTED';
  end if;
  if coalesce(p_committed, 0) > 0 then
    return 'PARTIALLY_COMMITTED';
  end if;
  if p_expires_at is not null and p_expires_at < p_now then
    return 'EXPIRED';
  end if;
  return 'OPEN';
end;
$$;

create or replace function public._m11_sync_supply_request_totals(p_request_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_req public.shelter_supply_requests;
  v_committed integer;
  v_received integer;
  v_status text;
begin
  select * into v_req from public.shelter_supply_requests where id = p_request_id for update;
  if not found then return; end if;

  select
    coalesce(sum(c.quantity_committed) filter (
      where c.status not in ('CANCELLED', 'REJECTED')
    ), 0),
    coalesce(sum(c.quantity_received) filter (
      where c.status not in ('CANCELLED', 'REJECTED')
    ), 0)
  into v_committed, v_received
  from public.shelter_supply_contributions c
  where c.request_id = p_request_id;

  v_status := public._m11_recompute_supply_status(
    v_req.quantity_requested, v_committed, v_received,
    v_req.expires_at, timezone('utc', now()), v_req.status
  );

  update public.shelter_supply_requests set
    quantity_committed = v_committed,
    quantity_received = v_received,
    status = v_status,
    updated_at = timezone('utc', now())
  where id = p_request_id;
end;
$$;

create or replace function public._m11_open_supply_request_statuses()
returns text[]
language sql immutable parallel safe as $$
  select array[
    'DRAFT','OPEN','PARTIALLY_COMMITTED','FULLY_COMMITTED','PARTIALLY_RECEIVED'
  ]::text[];
$$;

-- ---------------------------------------------------------------------------
-- 3. Catálogo M07
-- ---------------------------------------------------------------------------
insert into public.observability_event_catalog (
  event_key, module, category, default_severity, sensitivity, organization_scoped,
  retention_policy_key, remote_persistence_allowed, analytics_allowed,
  allowed_metadata_keys, required_metadata_keys
) values
('SHELTER_CAMPAIGN_CREATED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_CAMPAIGN_STATUS_CHANGED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_SUPPLY_REQUEST_CREATED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_SUPPLY_CONTRIBUTION_PLEDGED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_SUPPLY_CONTRIBUTION_RECEIVED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_SUPPLY_REQUEST_FULFILLED','M11','AUDIT','NOTICE','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[])
on conflict (event_key) do nothing;

-- ---------------------------------------------------------------------------
-- 4. RLS
-- ---------------------------------------------------------------------------
alter table public.shelter_campaigns enable row level security;
alter table public.shelter_campaign_updates enable row level security;
alter table public.shelter_supply_requests enable row level security;
alter table public.shelter_supply_contributions enable row level security;

drop policy if exists shelter_campaigns_select_m11 on public.shelter_campaigns;
drop policy if exists shelter_campaigns_ins_m11 on public.shelter_campaigns;
drop policy if exists shelter_campaigns_upd_m11 on public.shelter_campaigns;
drop policy if exists shelter_campaigns_del_m11 on public.shelter_campaigns;
create policy shelter_campaigns_select_m11 on public.shelter_campaigns for select to authenticated
  using (
    (status = 'ACTIVE' and visibility = 'PUBLIC')
    or exists (
      select 1 from public.shelter_profiles s
      where s.id = shelter_profile_id
        and public.has_org_permission(s.organization_id, 'shelter.campaign.read')
    )
  );
create policy shelter_campaigns_ins_m11 on public.shelter_campaigns for insert to authenticated with check (false);
create policy shelter_campaigns_upd_m11 on public.shelter_campaigns for update to authenticated using (false);
create policy shelter_campaigns_del_m11 on public.shelter_campaigns for delete to authenticated using (false);

drop policy if exists shelter_campaign_updates_select_m11 on public.shelter_campaign_updates;
drop policy if exists shelter_campaign_updates_ins_m11 on public.shelter_campaign_updates;
drop policy if exists shelter_campaign_updates_upd_m11 on public.shelter_campaign_updates;
drop policy if exists shelter_campaign_updates_del_m11 on public.shelter_campaign_updates;
create policy shelter_campaign_updates_select_m11 on public.shelter_campaign_updates for select to authenticated
  using (
    exists (
      select 1 from public.shelter_campaigns c
      join public.shelter_profiles s on s.id = c.shelter_profile_id
      where c.id = campaign_id
        and (
          (c.status = 'ACTIVE' and c.visibility = 'PUBLIC' and visibility = 'PUBLIC')
          or public.has_org_permission(s.organization_id, 'shelter.campaign.read')
        )
    )
  );
create policy shelter_campaign_updates_ins_m11 on public.shelter_campaign_updates for insert to authenticated with check (false);
create policy shelter_campaign_updates_upd_m11 on public.shelter_campaign_updates for update to authenticated using (false);
create policy shelter_campaign_updates_del_m11 on public.shelter_campaign_updates for delete to authenticated using (false);

drop policy if exists shelter_supply_requests_select_m11 on public.shelter_supply_requests;
drop policy if exists shelter_supply_requests_ins_m11 on public.shelter_supply_requests;
drop policy if exists shelter_supply_requests_upd_m11 on public.shelter_supply_requests;
drop policy if exists shelter_supply_requests_del_m11 on public.shelter_supply_requests;
create policy shelter_supply_requests_select_m11 on public.shelter_supply_requests for select to authenticated
  using (
    exists (
      select 1 from public.shelter_profiles s
      where s.id = shelter_profile_id
        and public.has_org_permission(s.organization_id, 'shelter.supply.read')
    )
    or (
      status = any (array['OPEN','PARTIALLY_COMMITTED','FULLY_COMMITTED','PARTIALLY_RECEIVED']::text[])
      and exists (
        select 1 from public.shelter_profiles s
        where s.id = shelter_profile_id and s.status = 'ACTIVE'
      )
      and (
        campaign_id is null
        or exists (
          select 1 from public.shelter_campaigns c
          where c.id = campaign_id and c.status = 'ACTIVE' and c.visibility = 'PUBLIC'
        )
      )
    )
  );
create policy shelter_supply_requests_ins_m11 on public.shelter_supply_requests for insert to authenticated with check (false);
create policy shelter_supply_requests_upd_m11 on public.shelter_supply_requests for update to authenticated using (false);
create policy shelter_supply_requests_del_m11 on public.shelter_supply_requests for delete to authenticated using (false);

drop policy if exists shelter_supply_contributions_select_m11 on public.shelter_supply_contributions;
drop policy if exists shelter_supply_contributions_ins_m11 on public.shelter_supply_contributions;
drop policy if exists shelter_supply_contributions_upd_m11 on public.shelter_supply_contributions;
drop policy if exists shelter_supply_contributions_del_m11 on public.shelter_supply_contributions;
create policy shelter_supply_contributions_select_m11 on public.shelter_supply_contributions for select to authenticated
  using (
    contributor_user_id = auth.uid()
    or exists (
      select 1 from public.shelter_supply_requests r
      join public.shelter_profiles s on s.id = r.shelter_profile_id
      where r.id = request_id
        and public.has_org_permission(s.organization_id, 'shelter.contribution.read')
    )
  );
create policy shelter_supply_contributions_ins_m11 on public.shelter_supply_contributions for insert to authenticated with check (false);
create policy shelter_supply_contributions_upd_m11 on public.shelter_supply_contributions for update to authenticated using (false);
create policy shelter_supply_contributions_del_m11 on public.shelter_supply_contributions for delete to authenticated using (false);

revoke all on table public.shelter_campaigns from public, anon;
revoke all on table public.shelter_campaign_updates from public, anon;
revoke all on table public.shelter_supply_requests from public, anon;
revoke all on table public.shelter_supply_contributions from public, anon;
grant select on table public.shelter_campaigns to authenticated;
grant select on table public.shelter_campaign_updates to authenticated;
grant select on table public.shelter_supply_requests to authenticated;
grant select on table public.shelter_supply_contributions to authenticated;
grant all on table public.shelter_campaigns to service_role;
grant all on table public.shelter_campaign_updates to service_role;
grant all on table public.shelter_supply_requests to service_role;
grant all on table public.shelter_supply_contributions to service_role;

-- ---------------------------------------------------------------------------
-- 5. RPCs — campañas
-- M06: hooks Android/dominio preparados; NO invocar m06_emit_domain_notification aquí.
-- ---------------------------------------------------------------------------
create or replace function public.m11_create_shelter_campaign(
  p_shelter_id uuid,
  p_title text,
  p_description text,
  p_category text,
  p_visibility text,
  p_starts_at timestamptz default null,
  p_ends_at timestamptz default null,
  p_cover_asset_ref text default null,
  p_activate boolean default false
) returns public.shelter_campaigns
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
  v_row public.shelter_campaigns;
  v_status text := case when coalesce(p_activate, false) then 'ACTIVE' else 'DRAFT' end;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.campaign.manage');
  if char_length(trim(coalesce(p_title, ''))) = 0
     or char_length(trim(coalesce(p_description, ''))) = 0 then
    raise exception 'SHELTER_CAMPAIGN_INVALID_TRANSITION';
  end if;
  perform public._m11_require_safe_evidence(p_cover_asset_ref);
  if v_status = 'ACTIVE' and v_s.status <> 'ACTIVE' then
    raise exception 'SHELTER_CAMPAIGN_NOT_ACTIVE';
  end if;

  insert into public.shelter_campaigns (
    shelter_profile_id, title, description, category, visibility, status,
    starts_at, ends_at, cover_asset_ref, created_by
  ) values (
    p_shelter_id, trim(p_title), trim(p_description), upper(trim(p_category)),
    upper(trim(p_visibility)), v_status,
    p_starts_at, p_ends_at,
    nullif(trim(coalesce(p_cover_asset_ref, '')), ''), v_actor
  ) returning * into v_row;

  perform public.m07_best_effort_audit(
    'SHELTER_CAMPAIGN_CREATED', 'CREATE', 'SUCCESS', v_corr,
    'shelter_campaign', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m11_update_shelter_campaign(
  p_id uuid,
  p_title text,
  p_description text,
  p_category text,
  p_visibility text,
  p_starts_at timestamptz default null,
  p_ends_at timestamptz default null,
  p_cover_asset_ref text default null
) returns public.shelter_campaigns
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_campaigns;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_campaigns where id = p_id for update;
  if not found then raise exception 'SHELTER_CAMPAIGN_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.campaign.manage');
  if v_row.status in ('COMPLETED', 'CANCELLED') then
    raise exception 'SHELTER_CAMPAIGN_INVALID_TRANSITION';
  end if;
  if char_length(trim(coalesce(p_title, ''))) = 0
     or char_length(trim(coalesce(p_description, ''))) = 0 then
    raise exception 'SHELTER_CAMPAIGN_INVALID_TRANSITION';
  end if;
  perform public._m11_require_safe_evidence(p_cover_asset_ref);

  update public.shelter_campaigns set
    title = trim(p_title),
    description = trim(p_description),
    category = upper(trim(p_category)),
    visibility = upper(trim(p_visibility)),
    starts_at = p_starts_at,
    ends_at = p_ends_at,
    cover_asset_ref = nullif(trim(coalesce(p_cover_asset_ref, '')), ''),
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_change_shelter_campaign_status(
  p_id uuid,
  p_status text
) returns public.shelter_campaigns
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_campaigns;
  v_s public.shelter_profiles;
  v_status text := upper(trim(p_status));
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.shelter_campaigns where id = p_id for update;
  if not found then raise exception 'SHELTER_CAMPAIGN_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.campaign.manage');
  if v_status not in ('DRAFT','ACTIVE','PAUSED','COMPLETED','CANCELLED') then
    raise exception 'SHELTER_CAMPAIGN_INVALID_TRANSITION';
  end if;
  if v_row.status = v_status then return v_row; end if;

  if v_status = 'ACTIVE' then
    if v_s.status <> 'ACTIVE' then raise exception 'SHELTER_CAMPAIGN_NOT_ACTIVE'; end if;
  end if;

  if v_status = 'COMPLETED' then
    if exists (
      select 1 from public.shelter_supply_requests r
      where r.campaign_id = p_id
        and r.status = any (public._m11_open_supply_request_statuses())
    ) then
      raise exception 'SHELTER_CAMPAIGN_HAS_OPEN_REQUESTS';
    end if;
  end if;

  if v_status = 'CANCELLED' then
    update public.shelter_supply_requests r set
      status = 'CANCELLED',
      updated_at = timezone('utc', now())
    where r.campaign_id = p_id
      and r.status = any (public._m11_open_supply_request_statuses());

    update public.shelter_supply_contributions c set
      status = 'CANCELLED',
      cancelled_at = timezone('utc', now())
    from public.shelter_supply_requests r
    where c.request_id = r.id
      and r.campaign_id = p_id
      and c.status not in ('CANCELLED', 'REJECTED', 'RECEIVED', 'PARTIALLY_RECEIVED')
      and c.quantity_received = 0;
  end if;

  update public.shelter_campaigns set
    status = v_status,
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'SHELTER_CAMPAIGN_STATUS_CHANGED', 'STATUS_CHANGE', 'SUCCESS', v_corr,
    'shelter_campaign', v_row.id::text,
    jsonb_build_object(
      'result','SUCCESS','module','M11','organization_id', v_s.organization_id,
      'new_status', v_status
    )
  );
  return v_row;
end;
$$;

create or replace function public.m11_get_shelter_campaign(p_id uuid)
returns public.shelter_campaigns
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_campaigns;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_campaigns where id = p_id;
  if not found then raise exception 'SHELTER_CAMPAIGN_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  if v_row.visibility = 'PUBLIC' and v_row.status = 'ACTIVE' then
    return v_row;
  end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.campaign.read') then
    raise exception 'SHELTER_CAMPAIGN_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m11_list_public_shelter_campaigns()
returns setof public.shelter_campaigns
language sql stable security definer set search_path = public as $$
  select * from public.shelter_campaigns
  where status = 'ACTIVE' and visibility = 'PUBLIC'
  order by updated_at desc;
$$;

create or replace function public.m11_list_shelter_campaigns(p_shelter_id uuid)
returns setof public.shelter_campaigns
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.campaign.read') then
    raise exception 'SHELTER_CAMPAIGN_FORBIDDEN';
  end if;
  return query
  select * from public.shelter_campaigns
  where shelter_profile_id = p_shelter_id
  order by updated_at desc;
end;
$$;

create or replace function public.m11_add_shelter_campaign_update(
  p_campaign_id uuid,
  p_message text,
  p_visibility text,
  p_evidence_ref text default null
) returns public.shelter_campaign_updates
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_c public.shelter_campaigns;
  v_s public.shelter_profiles;
  v_row public.shelter_campaign_updates;
begin
  select * into v_c from public.shelter_campaigns where id = p_campaign_id;
  if not found then raise exception 'SHELTER_CAMPAIGN_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_c.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.campaign.manage');
  if char_length(trim(coalesce(p_message, ''))) = 0 then
    raise exception 'SHELTER_CAMPAIGN_INVALID_TRANSITION';
  end if;
  perform public._m11_require_safe_evidence(p_evidence_ref);

  insert into public.shelter_campaign_updates (
    campaign_id, author_user_id, visibility, message, evidence_ref
  ) values (
    p_campaign_id, v_actor, upper(trim(p_visibility)), trim(p_message),
    nullif(trim(coalesce(p_evidence_ref, '')), '')
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_list_shelter_campaign_updates(p_campaign_id uuid)
returns setof public.shelter_campaign_updates
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_c public.shelter_campaigns;
  v_s public.shelter_profiles;
  v_can_read boolean;
begin
  select * into v_c from public.shelter_campaigns where id = p_campaign_id;
  if not found then raise exception 'SHELTER_CAMPAIGN_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_c.shelter_profile_id;
  v_can_read := public.has_org_permission(v_s.organization_id, 'shelter.campaign.read');

  return query
  select u.*
  from public.shelter_campaign_updates u
  where u.campaign_id = p_campaign_id
    and (
      v_can_read
      or (u.visibility = 'PUBLIC' and v_c.visibility = 'PUBLIC' and v_c.status = 'ACTIVE')
    )
  order by u.created_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPCs — pedidos de insumos
-- M06: hooks Android/dominio preparados; NO invocar m06_emit_domain_notification aquí.
-- ---------------------------------------------------------------------------
create or replace function public.m11_create_supply_request(
  p_shelter_id uuid,
  p_item_name text,
  p_quantity_requested integer,
  p_unit_text text,
  p_category text,
  p_description text default null,
  p_campaign_id uuid default null,
  p_priority text default 'NORMAL',
  p_expires_at timestamptz default null,
  p_public_notes text default null,
  p_internal_notes text default null,
  p_open boolean default false
) returns public.shelter_supply_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
  v_c public.shelter_campaigns;
  v_row public.shelter_supply_requests;
  v_status text := case when coalesce(p_open, false) then 'OPEN' else 'DRAFT' end;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.supply.manage');
  if v_s.status <> 'ACTIVE' then raise exception 'SHELTER_NOT_ACTIVE'; end if;
  if coalesce(p_quantity_requested, 0) <= 0 then raise exception 'SHELTER_SUPPLY_REQUEST_INVALID'; end if;
  if char_length(trim(coalesce(p_item_name, ''))) = 0
     or char_length(trim(coalesce(p_unit_text, ''))) = 0 then
    raise exception 'SHELTER_SUPPLY_REQUEST_INVALID';
  end if;

  if p_campaign_id is not null then
    select * into v_c from public.shelter_campaigns where id = p_campaign_id;
    if not found then raise exception 'SHELTER_CAMPAIGN_NOT_FOUND'; end if;
    if v_c.shelter_profile_id <> p_shelter_id then
      raise exception 'SHELTER_SUPPLY_REQUEST_INVALID';
    end if;
  end if;

  insert into public.shelter_supply_requests (
    shelter_profile_id, campaign_id, category, item_name, description,
    quantity_requested, unit_text, priority, status, expires_at,
    public_notes, internal_notes, created_by
  ) values (
    p_shelter_id, p_campaign_id, upper(trim(p_category)), trim(p_item_name),
    nullif(trim(coalesce(p_description, '')), ''),
    p_quantity_requested, trim(p_unit_text), upper(trim(coalesce(p_priority, 'NORMAL'))),
    v_status, p_expires_at,
    nullif(trim(coalesce(p_public_notes, '')), ''),
    nullif(trim(coalesce(p_internal_notes, '')), ''),
    v_actor
  ) returning * into v_row;

  perform public.m07_best_effort_audit(
    'SHELTER_SUPPLY_REQUEST_CREATED', 'CREATE', 'SUCCESS', v_corr,
    'shelter_supply_request', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m11_update_supply_request(
  p_id uuid,
  p_item_name text,
  p_quantity_requested integer,
  p_unit_text text,
  p_category text,
  p_description text default null,
  p_priority text default 'NORMAL',
  p_expires_at timestamptz default null,
  p_public_notes text default null,
  p_internal_notes text default null
) returns public.shelter_supply_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_supply_requests;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_supply_requests where id = p_id for update;
  if not found then raise exception 'SHELTER_SUPPLY_REQUEST_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.supply.manage');
  if v_row.status in ('FULFILLED', 'CANCELLED', 'EXPIRED') then
    raise exception 'SHELTER_SUPPLY_REQUEST_CLOSED';
  end if;
  if coalesce(p_quantity_requested, 0) <= 0 then raise exception 'SHELTER_SUPPLY_REQUEST_INVALID'; end if;
  if char_length(trim(coalesce(p_item_name, ''))) = 0
     or char_length(trim(coalesce(p_unit_text, ''))) = 0 then
    raise exception 'SHELTER_SUPPLY_REQUEST_INVALID';
  end if;
  if p_quantity_requested < v_row.quantity_committed then
    raise exception 'SHELTER_SUPPLY_REQUEST_INVALID';
  end if;

  update public.shelter_supply_requests set
    item_name = trim(p_item_name),
    quantity_requested = p_quantity_requested,
    unit_text = trim(p_unit_text),
    category = upper(trim(p_category)),
    description = nullif(trim(coalesce(p_description, '')), ''),
    priority = upper(trim(coalesce(p_priority, 'NORMAL'))),
    expires_at = p_expires_at,
    public_notes = nullif(trim(coalesce(p_public_notes, '')), ''),
    internal_notes = nullif(trim(coalesce(p_internal_notes, '')), ''),
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;

  perform public._m11_sync_supply_request_totals(p_id);
  select * into v_row from public.shelter_supply_requests where id = p_id;
  return v_row;
end;
$$;

create or replace function public.m11_cancel_supply_request(p_id uuid)
returns public.shelter_supply_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_supply_requests;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_supply_requests where id = p_id for update;
  if not found then raise exception 'SHELTER_SUPPLY_REQUEST_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.supply.manage');
  if v_row.status in ('FULFILLED', 'CANCELLED') then
    return v_row;
  end if;

  update public.shelter_supply_contributions c set
    status = 'CANCELLED',
    cancelled_at = timezone('utc', now())
  where c.request_id = p_id
    and c.status not in ('CANCELLED', 'REJECTED', 'RECEIVED', 'PARTIALLY_RECEIVED')
    and c.quantity_received = 0;

  update public.shelter_supply_requests set
    status = 'CANCELLED',
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;

  perform public._m11_sync_supply_request_totals(p_id);
  select * into v_row from public.shelter_supply_requests where id = p_id;
  return v_row;
end;
$$;

create or replace function public.m11_get_supply_request(p_id uuid)
returns public.shelter_supply_requests
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_supply_requests;
  v_s public.shelter_profiles;
  v_public_ok boolean;
begin
  select * into v_row from public.shelter_supply_requests where id = p_id;
  if not found then raise exception 'SHELTER_SUPPLY_REQUEST_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;

  v_public_ok := v_s.status = 'ACTIVE'
    and v_row.status = any (array['OPEN','PARTIALLY_COMMITTED','FULLY_COMMITTED','PARTIALLY_RECEIVED']::text[])
    and (
      v_row.campaign_id is null
      or exists (
        select 1 from public.shelter_campaigns c
        where c.id = v_row.campaign_id and c.status = 'ACTIVE' and c.visibility = 'PUBLIC'
      )
    );

  if v_public_ok then
    return v_row;
  end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.supply.read') then
    raise exception 'SHELTER_SUPPLY_REQUEST_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m11_list_public_supply_requests()
returns table (
  id uuid,
  shelter_profile_id uuid,
  campaign_id uuid,
  category text,
  item_name text,
  description text,
  quantity_requested integer,
  quantity_committed integer,
  quantity_received integer,
  unit_text text,
  priority text,
  status text,
  expires_at timestamptz,
  public_notes text,
  created_by uuid,
  created_at timestamptz,
  updated_at timestamptz
)
language sql stable security definer set search_path = public as $$
  select
    r.id, r.shelter_profile_id, r.campaign_id, r.category, r.item_name, r.description,
    r.quantity_requested, r.quantity_committed, r.quantity_received, r.unit_text,
    r.priority, r.status, r.expires_at, r.public_notes, r.created_by, r.created_at, r.updated_at
  from public.shelter_supply_requests r
  join public.shelter_profiles s on s.id = r.shelter_profile_id
  where s.status = 'ACTIVE'
    and r.status = any (array['OPEN','PARTIALLY_COMMITTED','FULLY_COMMITTED','PARTIALLY_RECEIVED']::text[])
    and (
      r.campaign_id is null
      or exists (
        select 1 from public.shelter_campaigns c
        where c.id = r.campaign_id and c.status = 'ACTIVE' and c.visibility = 'PUBLIC'
      )
    )
  order by r.updated_at desc;
$$;

create or replace function public.m11_list_shelter_supply_requests(p_shelter_id uuid)
returns setof public.shelter_supply_requests
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.supply.read') then
    raise exception 'SHELTER_SUPPLY_REQUEST_FORBIDDEN';
  end if;
  return query
  select * from public.shelter_supply_requests
  where shelter_profile_id = p_shelter_id
  order by updated_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. RPCs — aportes
-- M06: hooks Android/dominio preparados; NO invocar m06_emit_domain_notification aquí.
-- ---------------------------------------------------------------------------
create or replace function public.m11_pledge_supply_contribution(
  p_request_id uuid,
  p_quantity_committed integer,
  p_contributor_notes text default null
) returns public.shelter_supply_contributions
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_req public.shelter_supply_requests;
  v_row public.shelter_supply_contributions;
  v_remaining integer;
  v_corr text := public.m07_new_correlation_id();
  v_s public.shelter_profiles;
begin
  select * into v_req from public.shelter_supply_requests where id = p_request_id for update;
  if not found then raise exception 'SHELTER_SUPPLY_REQUEST_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_req.shelter_profile_id;

  if v_req.status not in ('OPEN','PARTIALLY_COMMITTED') then
    if v_req.status = 'EXPIRED' or (v_req.expires_at is not null and v_req.expires_at < timezone('utc', now())) then
      raise exception 'SHELTER_SUPPLY_REQUEST_EXPIRED';
    end if;
    raise exception 'SHELTER_SUPPLY_REQUEST_CLOSED';
  end if;
  if v_req.expires_at is not null and v_req.expires_at < timezone('utc', now()) then
    raise exception 'SHELTER_SUPPLY_REQUEST_EXPIRED';
  end if;
  if coalesce(p_quantity_committed, 0) <= 0 then raise exception 'SHELTER_CONTRIBUTION_INVALID'; end if;

  v_remaining := v_req.quantity_requested - v_req.quantity_committed;
  if p_quantity_committed > v_remaining then
    raise exception 'SHELTER_CONTRIBUTION_EXCEEDS_REMAINING';
  end if;

  insert into public.shelter_supply_contributions (
    request_id, contributor_user_id, quantity_committed, contributor_notes
  ) values (
    p_request_id, v_actor, p_quantity_committed,
    nullif(trim(coalesce(p_contributor_notes, '')), '')
  ) returning * into v_row;

  perform public._m11_sync_supply_request_totals(p_request_id);

  perform public.m07_best_effort_audit(
    'SHELTER_SUPPLY_CONTRIBUTION_PLEDGED', 'PLEDGE', 'SUCCESS', v_corr,
    'shelter_supply_contribution', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m11_cancel_supply_contribution(p_contribution_id uuid)
returns public.shelter_supply_contributions
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_supply_contributions;
begin
  select * into v_row from public.shelter_supply_contributions where id = p_contribution_id for update;
  if not found then raise exception 'SHELTER_CONTRIBUTION_NOT_FOUND'; end if;
  if v_row.contributor_user_id <> v_actor then
    raise exception 'SHELTER_CONTRIBUTION_FORBIDDEN';
  end if;
  if v_row.quantity_received > 0 then
    raise exception 'SHELTER_CONTRIBUTION_ALREADY_RECEIVED';
  end if;
  if v_row.status in ('CANCELLED', 'REJECTED', 'RECEIVED', 'PARTIALLY_RECEIVED') then
    raise exception 'SHELTER_CONTRIBUTION_INVALID';
  end if;

  update public.shelter_supply_contributions set
    status = 'CANCELLED',
    cancelled_at = timezone('utc', now())
  where id = p_contribution_id returning * into v_row;

  perform public._m11_sync_supply_request_totals(v_row.request_id);
  return v_row;
end;
$$;

create or replace function public.m11_confirm_supply_contribution(p_contribution_id uuid)
returns public.shelter_supply_contributions
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_supply_contributions;
  v_req public.shelter_supply_requests;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_supply_contributions where id = p_contribution_id for update;
  if not found then raise exception 'SHELTER_CONTRIBUTION_NOT_FOUND'; end if;
  select * into v_req from public.shelter_supply_requests where id = v_row.request_id;
  select * into v_s from public.shelter_profiles where id = v_req.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.contribution.manage');

  if v_row.status <> 'PLEDGED' then
    raise exception 'SHELTER_CONTRIBUTION_INVALID';
  end if;

  update public.shelter_supply_contributions set status = 'CONFIRMED'
  where id = p_contribution_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_record_supply_receipt(
  p_contribution_id uuid,
  p_quantity_received integer,
  p_notes text default null,
  p_evidence_ref text default null
) returns public.shelter_supply_contributions
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_supply_contributions;
  v_req public.shelter_supply_requests;
  v_s public.shelter_profiles;
  v_prev_status text;
  v_new_status text;
  v_corr text := public.m07_new_correlation_id();
  v_was_fulfilled boolean;
  v_now_fulfilled boolean;
begin
  select * into v_row from public.shelter_supply_contributions where id = p_contribution_id for update;
  if not found then raise exception 'SHELTER_CONTRIBUTION_NOT_FOUND'; end if;
  select * into v_req from public.shelter_supply_requests where id = v_row.request_id for update;
  select * into v_s from public.shelter_profiles where id = v_req.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.contribution.manage');

  if v_row.status in ('CANCELLED', 'REJECTED') then
    raise exception 'SHELTER_CONTRIBUTION_INVALID';
  end if;
  if coalesce(p_quantity_received, 0) <= 0 then raise exception 'SHELTER_CONTRIBUTION_INVALID'; end if;
  if p_quantity_received > v_row.quantity_committed then raise exception 'SHELTER_CONTRIBUTION_INVALID'; end if;
  perform public._m11_require_safe_evidence(p_evidence_ref);

  v_prev_status := v_req.status;

  update public.shelter_supply_contributions set
    quantity_received = p_quantity_received,
    status = case
      when p_quantity_received >= quantity_committed then 'RECEIVED'
      when p_quantity_received > 0 then 'PARTIALLY_RECEIVED'
      else status
    end,
    internal_receipt_notes = nullif(trim(coalesce(p_notes, '')), ''),
    evidence_ref = nullif(trim(coalesce(p_evidence_ref, '')), ''),
    received_at = timezone('utc', now())
  where id = p_contribution_id returning * into v_row;

  perform public._m11_sync_supply_request_totals(v_row.request_id);
  select status into v_new_status from public.shelter_supply_requests where id = v_row.request_id;
  v_was_fulfilled := v_prev_status = 'FULFILLED';
  v_now_fulfilled := v_new_status = 'FULFILLED';

  perform public.m07_best_effort_audit(
    'SHELTER_SUPPLY_CONTRIBUTION_RECEIVED', 'RECEIPT', 'SUCCESS', v_corr,
    'shelter_supply_contribution', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
  );

  if v_now_fulfilled and not v_was_fulfilled then
    perform public.m07_best_effort_audit(
      'SHELTER_SUPPLY_REQUEST_FULFILLED', 'FULFILL', 'SUCCESS', v_corr,
      'shelter_supply_request', v_row.request_id::text,
      jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
    );
  end if;

  return v_row;
end;
$$;

create or replace function public.m11_get_supply_contribution(p_contribution_id uuid)
returns public.shelter_supply_contributions
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_supply_contributions;
  v_req public.shelter_supply_requests;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_supply_contributions where id = p_contribution_id;
  if not found then raise exception 'SHELTER_CONTRIBUTION_NOT_FOUND'; end if;
  if v_row.contributor_user_id = v_actor then return v_row; end if;
  select * into v_req from public.shelter_supply_requests where id = v_row.request_id;
  select * into v_s from public.shelter_profiles where id = v_req.shelter_profile_id;
  if not public.has_org_permission(v_s.organization_id, 'shelter.contribution.read') then
    raise exception 'SHELTER_CONTRIBUTION_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m11_list_supply_contributions(p_request_id uuid)
returns setof public.shelter_supply_contributions
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_req public.shelter_supply_requests;
  v_s public.shelter_profiles;
  v_can_read boolean;
begin
  select * into v_req from public.shelter_supply_requests where id = p_request_id;
  if not found then raise exception 'SHELTER_SUPPLY_REQUEST_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_req.shelter_profile_id;
  v_can_read := public.has_org_permission(v_s.organization_id, 'shelter.contribution.read');

  return query
  select c.*
  from public.shelter_supply_contributions c
  where c.request_id = p_request_id
    and (v_can_read or c.contributor_user_id = v_actor)
  order by c.committed_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Grants
-- ---------------------------------------------------------------------------
revoke all on function public._m11_is_safe_evidence_ref(text) from public, anon, authenticated;
revoke all on function public._m11_require_safe_evidence(text) from public, anon, authenticated;
revoke all on function public._m11_recompute_supply_status(integer,integer,integer,timestamptz,timestamptz,text) from public, anon, authenticated;
revoke all on function public._m11_sync_supply_request_totals(uuid) from public, anon, authenticated;
revoke all on function public._m11_open_supply_request_statuses() from public, anon, authenticated;

revoke all on function public.m11_create_shelter_campaign(uuid,text,text,text,text,timestamptz,timestamptz,text,boolean) from public;
revoke all on function public.m11_update_shelter_campaign(uuid,text,text,text,text,timestamptz,timestamptz,text) from public;
revoke all on function public.m11_change_shelter_campaign_status(uuid,text) from public;
revoke all on function public.m11_get_shelter_campaign(uuid) from public;
revoke all on function public.m11_list_public_shelter_campaigns() from public;
revoke all on function public.m11_list_shelter_campaigns(uuid) from public;
revoke all on function public.m11_add_shelter_campaign_update(uuid,text,text,text) from public;
revoke all on function public.m11_list_shelter_campaign_updates(uuid) from public;
revoke all on function public.m11_create_supply_request(uuid,text,integer,text,text,text,uuid,text,timestamptz,text,text,boolean) from public;
revoke all on function public.m11_update_supply_request(uuid,text,integer,text,text,text,text,timestamptz,text,text) from public;
revoke all on function public.m11_cancel_supply_request(uuid) from public;
revoke all on function public.m11_get_supply_request(uuid) from public;
revoke all on function public.m11_list_public_supply_requests() from public;
revoke all on function public.m11_list_shelter_supply_requests(uuid) from public;
revoke all on function public.m11_pledge_supply_contribution(uuid,integer,text) from public;
revoke all on function public.m11_cancel_supply_contribution(uuid) from public;
revoke all on function public.m11_confirm_supply_contribution(uuid) from public;
revoke all on function public.m11_record_supply_receipt(uuid,integer,text,text) from public;
revoke all on function public.m11_get_supply_contribution(uuid) from public;
revoke all on function public.m11_list_supply_contributions(uuid) from public;

grant execute on function public.m11_create_shelter_campaign(uuid,text,text,text,text,timestamptz,timestamptz,text,boolean) to authenticated;
grant execute on function public.m11_update_shelter_campaign(uuid,text,text,text,text,timestamptz,timestamptz,text) to authenticated;
grant execute on function public.m11_change_shelter_campaign_status(uuid,text) to authenticated;
grant execute on function public.m11_get_shelter_campaign(uuid) to authenticated;
grant execute on function public.m11_list_public_shelter_campaigns() to authenticated;
grant execute on function public.m11_list_shelter_campaigns(uuid) to authenticated;
grant execute on function public.m11_add_shelter_campaign_update(uuid,text,text,text) to authenticated;
grant execute on function public.m11_list_shelter_campaign_updates(uuid) to authenticated;
grant execute on function public.m11_create_supply_request(uuid,text,integer,text,text,text,uuid,text,timestamptz,text,text,boolean) to authenticated;
grant execute on function public.m11_update_supply_request(uuid,text,integer,text,text,text,text,timestamptz,text,text) to authenticated;
grant execute on function public.m11_cancel_supply_request(uuid) to authenticated;
grant execute on function public.m11_get_supply_request(uuid) to authenticated;
grant execute on function public.m11_list_public_supply_requests() to authenticated;
grant execute on function public.m11_list_shelter_supply_requests(uuid) to authenticated;
grant execute on function public.m11_pledge_supply_contribution(uuid,integer,text) to authenticated;
grant execute on function public.m11_cancel_supply_contribution(uuid) to authenticated;
grant execute on function public.m11_confirm_supply_contribution(uuid) to authenticated;
grant execute on function public.m11_record_supply_receipt(uuid,integer,text,text) to authenticated;
grant execute on function public.m11_get_supply_contribution(uuid) to authenticated;
grant execute on function public.m11_list_supply_contributions(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 9. Comentarios técnicos
-- ---------------------------------------------------------------------------
comment on table public.shelter_campaigns is
  'M11 Bloque 2: campañas institucionales no monetarias por refugio operativo.';
comment on table public.shelter_campaign_updates is
  'M11 Bloque 2: novedades de campaña con evidencia M05 opcional.';
comment on table public.shelter_supply_requests is
  'M11 Bloque 2: pedidos de insumos; totales y estado derivados de aportes.';
comment on table public.shelter_supply_contributions is
  'M11 Bloque 2: compromisos y recepciones no monetarias; sin datos bancarios.';
comment on function public._m11_sync_supply_request_totals(uuid) is
  'Recalcula quantity_committed/received y status del pedido desde aportes activos.';
comment on function public.m11_record_supply_receipt(uuid,integer,text,text) is
  'Registra recepción parcial/total. M06 hooks preparados; sin m06_emit_domain_notification.';

commit;
