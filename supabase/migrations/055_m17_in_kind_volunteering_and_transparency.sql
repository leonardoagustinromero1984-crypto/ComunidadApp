-- =============================================================================
-- LeoVer M17 — migración 055: bienes, voluntariado y transparencia
-- Forward-only sobre 001–054. Reutiliza helpers _m17_* de 054.
-- quantity_committed = suma pledges ACCEPTED + DELIVERED (no PLEDGED).
-- Cobertura pública: quantity_delivered = suma pledges DELIVERED.
-- LOCAL ONLY — no aplicar hasta entorno no productivo autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Tablas — donaciones de bienes
-- ---------------------------------------------------------------------------
create table if not exists public.m17_in_kind_needs (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete restrict,
  campaign_id uuid references public.m17_donation_campaigns (id) on delete set null,
  shelter_profile_id uuid references public.m16_shelter_profiles (id) on delete set null,
  category text not null,
  title text not null,
  description text not null,
  quantity_needed integer not null,
  quantity_unit text not null default 'unidades',
  quantity_committed integer not null default 0,
  status text not null default 'DRAFT',
  public_location_text text,
  public_instructions text,
  image_ref text,
  moderation_status text,
  internal_notes text,
  created_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  published_at timestamptz,
  fulfilled_at timestamptz,
  cancelled_at timestamptz,
  constraint m17_inkind_category_chk check (category = any (array[
    'FOOD','MEDICATION','HYGIENE','BEDDING','TRANSPORT_SUPPLIES',
    'CONSTRUCTION_MATERIALS','OTHER'
  ]::text[])),
  constraint m17_inkind_status_chk check (status = any (array[
    'DRAFT','PUBLISHED','FULFILLED','CANCELLED'
  ]::text[])),
  constraint m17_inkind_qty_needed_chk check (quantity_needed > 0),
  constraint m17_inkind_qty_committed_chk check (quantity_committed >= 0),
  constraint m17_inkind_title_len check (char_length(trim(title)) between 1 and 120),
  constraint m17_inkind_moderation_chk check (
    moderation_status is null
    or moderation_status = any (array['APPROVED','PENDING','BLOCKED','HIDDEN']::text[])
  )
);

create index if not exists m17_inkind_needs_org_idx on public.m17_in_kind_needs (organization_id);
create index if not exists m17_inkind_needs_status_idx on public.m17_in_kind_needs (status);

create table if not exists public.m17_in_kind_pledges (
  id uuid primary key default gen_random_uuid(),
  need_id uuid not null references public.m17_in_kind_needs (id) on delete restrict,
  contributor_user_id uuid not null references public.users (id) on delete restrict,
  quantity integer not null,
  status text not null default 'PLEDGED',
  private_message text,
  public_message text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  accepted_at timestamptz,
  delivered_at timestamptz,
  cancelled_at timestamptz,
  rejected_at timestamptz,
  constraint m17_inkind_pledge_qty_chk check (quantity > 0),
  constraint m17_inkind_pledge_status_chk check (status = any (array[
    'PLEDGED','ACCEPTED','DELIVERED','CANCELLED','REJECTED'
  ]::text[]))
);

create index if not exists m17_inkind_pledges_need_idx on public.m17_in_kind_pledges (need_id, status);

-- ---------------------------------------------------------------------------
-- 2. Tablas — voluntariado
-- ---------------------------------------------------------------------------
create table if not exists public.m17_volunteer_opportunities (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete restrict,
  shelter_profile_id uuid references public.m16_shelter_profiles (id) on delete set null,
  campaign_id uuid references public.m17_donation_campaigns (id) on delete set null,
  opportunity_type text not null,
  title text not null,
  description text not null,
  required_people integer not null,
  accepted_people integer not null default 0,
  required_skills text[] not null default '{}',
  public_location_text text,
  starts_at timestamptz,
  ends_at timestamptz,
  status text not null default 'DRAFT',
  moderation_status text,
  image_ref text,
  internal_notes text,
  created_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  published_at timestamptz,
  filled_at timestamptz,
  completed_at timestamptz,
  cancelled_at timestamptz,
  constraint m17_vol_type_chk check (opportunity_type = any (array[
    'SHELTER_SUPPORT','ANIMAL_CARE','TRANSPORT','EVENTS','FUNDRAISING',
    'PHOTOGRAPHY','ADMINISTRATIVE','CONSTRUCTION','PROFESSIONAL_SUPPORT','OTHER'
  ]::text[])),
  constraint m17_vol_status_chk check (status = any (array[
    'DRAFT','PUBLISHED','PAUSED','FILLED','COMPLETED','CANCELLED'
  ]::text[])),
  constraint m17_vol_required_chk check (required_people > 0),
  constraint m17_vol_accepted_chk check (accepted_people >= 0),
  constraint m17_vol_moderation_chk check (
    moderation_status is null
    or moderation_status = any (array['APPROVED','PENDING','BLOCKED','HIDDEN']::text[])
  )
);

create index if not exists m17_vol_opp_org_idx on public.m17_volunteer_opportunities (organization_id);
create index if not exists m17_vol_opp_status_idx on public.m17_volunteer_opportunities (status);

create table if not exists public.m17_volunteer_applications (
  id uuid primary key default gen_random_uuid(),
  opportunity_id uuid not null references public.m17_volunteer_opportunities (id) on delete restrict,
  applicant_user_id uuid not null references public.users (id) on delete restrict,
  status text not null default 'SUBMITTED',
  availability_summary text,
  private_message text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  reviewed_at timestamptz,
  accepted_at timestamptz,
  rejected_at timestamptz,
  withdrawn_at timestamptz,
  completed_at timestamptz,
  constraint m17_vol_app_status_chk check (status = any (array[
    'SUBMITTED','REVIEWING','ACCEPTED','REJECTED','WITHDRAWN','COMPLETED'
  ]::text[]))
);

create unique index if not exists m17_vol_app_one_active
  on public.m17_volunteer_applications (opportunity_id, applicant_user_id)
  where status in ('SUBMITTED','REVIEWING','ACCEPTED');

create index if not exists m17_vol_app_opp_idx on public.m17_volunteer_applications (opportunity_id);

-- ---------------------------------------------------------------------------
-- 3. Tablas — transparencia
-- ---------------------------------------------------------------------------
create table if not exists public.m17_campaign_transparency_reports (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.m17_donation_campaigns (id) on delete restrict,
  organization_id uuid not null references public.organizations (id) on delete restrict,
  title text not null,
  summary text not null,
  status text not null default 'DRAFT',
  total_allocated_minor bigint not null default 0,
  currency text not null default 'ARS',
  public_notes text,
  internal_notes text,
  created_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  published_at timestamptz,
  finalized_at timestamptz,
  constraint m17_trans_report_status_chk check (status = any (array[
    'DRAFT','PUBLISHED','FINALIZED'
  ]::text[])),
  constraint m17_trans_allocated_chk check (total_allocated_minor >= 0),
  constraint m17_trans_currency_chk check (char_length(trim(currency)) = 3)
);

create unique index if not exists m17_trans_report_campaign_uniq
  on public.m17_campaign_transparency_reports (campaign_id)
  where status in ('DRAFT','PUBLISHED');

create table if not exists public.m17_fund_usage_items (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.m17_campaign_transparency_reports (id) on delete cascade,
  category text not null,
  description text not null,
  amount_minor bigint not null,
  currency text not null,
  public_receipt_file_ref text,
  occurred_at timestamptz not null default timezone('utc', now()),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m17_fund_amount_chk check (amount_minor >= 0),
  constraint m17_fund_currency_chk check (char_length(trim(currency)) = 3)
);

create index if not exists m17_fund_usage_report_idx on public.m17_fund_usage_items (report_id);

create table if not exists public.m17_transparency_milestones (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.m17_campaign_transparency_reports (id) on delete cascade,
  title text not null,
  description text not null,
  status text not null default 'PENDING',
  target_date timestamptz,
  completed_at timestamptz,
  image_ref text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m17_milestone_status_chk check (status = any (array[
    'PENDING','IN_PROGRESS','COMPLETED'
  ]::text[]))
);

create index if not exists m17_milestones_report_idx on public.m17_transparency_milestones (report_id);

-- ---------------------------------------------------------------------------
-- 4. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m17_inkind_need_is_public(p_row public.m17_in_kind_needs)
returns boolean language sql stable as $$
  select p_row.status = 'PUBLISHED'
    and coalesce(p_row.moderation_status, 'APPROVED') in ('APPROVED')
$$;

create or replace function public._m17_vol_opp_is_public(p_row public.m17_volunteer_opportunities)
returns boolean language sql stable as $$
  select p_row.status in ('PUBLISHED','PAUSED','FILLED','COMPLETED')
    and coalesce(p_row.moderation_status, 'APPROVED') in ('APPROVED')
$$;

create or replace function public._m17_pledged_qty(p_need_id uuid)
returns integer language sql stable security definer set search_path = public as $$
  select coalesce(sum(quantity), 0)::integer
  from public.m17_in_kind_pledges
  where need_id = p_need_id
    and status in ('PLEDGED','ACCEPTED','DELIVERED')
$$;

create or replace function public._m17_delivered_qty(p_need_id uuid)
returns integer language sql stable security definer set search_path = public as $$
  select coalesce(sum(quantity), 0)::integer
  from public.m17_in_kind_pledges
  where need_id = p_need_id and status = 'DELIVERED'
$$;

create or replace function public._m17_recompute_need_committed(p_need_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare v_committed integer;
begin
  select coalesce(sum(quantity), 0)::integer into v_committed
  from public.m17_in_kind_pledges
  where need_id = p_need_id and status in ('ACCEPTED','DELIVERED');
  update public.m17_in_kind_needs
  set quantity_committed = v_committed, updated_at = timezone('utc', now())
  where id = p_need_id;
end;
$$;

create or replace function public._m17_inkind_pledge_changed()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  perform public._m17_recompute_need_committed(
    coalesce(new.need_id, old.need_id)
  );
  return coalesce(new, old);
end;
$$;

create trigger m17_inkind_pledge_recompute_trg
  after insert or update or delete on public.m17_in_kind_pledges
  for each row execute function public._m17_inkind_pledge_changed();

create or replace function public._m17_public_in_kind_need_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m17_in_kind_needs;
declare v_org text;
declare v_pledged integer;
declare v_delivered integer;
declare v_pct integer;
begin
  select * into v from public.m17_in_kind_needs where id = p_id;
  if not found or not public._m17_inkind_need_is_public(v) then return null; end if;
  select coalesce(o.display_name, o.legal_name, 'Organización') into v_org
  from public.organizations o where o.id = v.organization_id;
  v_pledged := public._m17_pledged_qty(p_id);
  v_delivered := public._m17_delivered_qty(p_id);
  v_pct := least(999, ((v_delivered * 100) / greatest(v.quantity_needed, 1))::integer);
  return jsonb_build_object(
    'id', v.id,
    'title', v.title,
    'description', v.description,
    'organization_display_name', v_org,
    'category', v.category,
    'status', v.status,
    'quantity_requested', v.quantity_needed,
    'quantity_pledged', v_pledged,
    'quantity_delivered', v_delivered,
    'quantity_unit', v.quantity_unit,
    'coverage_percent', v_pct,
    'public_location_text', v.public_location_text,
    'public_instructions', v.public_instructions
  );
end;
$$;

create or replace function public._m17_public_volunteer_opp_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m17_volunteer_opportunities;
declare v_org text;
begin
  select * into v from public.m17_volunteer_opportunities where id = p_id;
  if not found or not public._m17_vol_opp_is_public(v) then return null; end if;
  select coalesce(o.display_name, o.legal_name, 'Organización') into v_org
  from public.organizations o where o.id = v.organization_id;
  return jsonb_build_object(
    'id', v.id,
    'title', v.title,
    'description', v.description,
    'organization_display_name', v_org,
    'opportunity_type', v.opportunity_type,
    'status', v.status,
    'slots_needed', v.required_people,
    'slots_filled', v.accepted_people,
    'public_location_text', v.public_location_text,
    'schedule_hint', v.starts_at
  );
end;
$$;

create or replace function public._m17_public_transparency_json(p_campaign_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m17_campaign_transparency_reports;
declare v_items jsonb;
declare v_milestones jsonb;
begin
  select * into v from public.m17_campaign_transparency_reports
  where campaign_id = p_campaign_id and status = 'PUBLISHED'
  order by published_at desc nulls last limit 1;
  if not found then return null; end if;

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', f.id, 'label', f.category, 'description', f.description,
    'amount_minor', f.amount_minor, 'currency', f.currency,
    'receipt_ref', f.public_receipt_file_ref, 'occurred_at', f.occurred_at
  ) order by f.occurred_at), '[]'::jsonb)
  into v_items from public.m17_fund_usage_items f where f.report_id = v.id;

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', m.id, 'title', m.title, 'description', m.description,
    'status', m.status, 'completed_at', m.completed_at
  ) order by m.created_at), '[]'::jsonb)
  into v_milestones from public.m17_transparency_milestones m where m.report_id = v.id;

  return jsonb_build_object(
    'campaign_id', v.campaign_id,
    'title', v.title,
    'summary', v.summary,
    'total_allocated_minor', v.total_allocated_minor,
    'currency', v.currency,
    'public_notes', v.public_notes,
    'usage_items', v_items,
    'milestones', v_milestones,
    'updated_at', v.updated_at
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. RLS
-- ---------------------------------------------------------------------------
alter table public.m17_in_kind_needs enable row level security;
alter table public.m17_in_kind_pledges enable row level security;
alter table public.m17_volunteer_opportunities enable row level security;
alter table public.m17_volunteer_applications enable row level security;
alter table public.m17_campaign_transparency_reports enable row level security;
alter table public.m17_fund_usage_items enable row level security;
alter table public.m17_transparency_milestones enable row level security;

create policy m17_inkind_needs_select on public.m17_in_kind_needs for select to authenticated
  using (public.has_org_permission(organization_id, 'donation.view') or public._m17_is_moderator(auth.uid()));
create policy m17_inkind_needs_mut on public.m17_in_kind_needs for all to authenticated using (false);

create policy m17_inkind_pledges_select on public.m17_in_kind_pledges for select to authenticated
  using (
    contributor_user_id = auth.uid()
    or exists (
      select 1 from public.m17_in_kind_needs n
      where n.id = need_id and public.has_org_permission(n.organization_id, 'donation.view')
    )
    or public._m17_is_moderator(auth.uid())
  );
create policy m17_inkind_pledges_mut on public.m17_in_kind_pledges for all to authenticated using (false);

create policy m17_vol_opp_select on public.m17_volunteer_opportunities for select to authenticated
  using (public.has_org_permission(organization_id, 'donation.view') or public._m17_is_moderator(auth.uid()));
create policy m17_vol_opp_mut on public.m17_volunteer_opportunities for all to authenticated using (false);

create policy m17_vol_app_select on public.m17_volunteer_applications for select to authenticated
  using (
    applicant_user_id = auth.uid()
    or exists (
      select 1 from public.m17_volunteer_opportunities o
      where o.id = opportunity_id and public.has_org_permission(o.organization_id, 'donation.view')
    )
    or public._m17_is_moderator(auth.uid())
  );
create policy m17_vol_app_mut on public.m17_volunteer_applications for all to authenticated using (false);

create policy m17_trans_report_select on public.m17_campaign_transparency_reports for select to authenticated
  using (public.has_org_permission(organization_id, 'donation.view') or public._m17_is_moderator(auth.uid()));
create policy m17_trans_report_mut on public.m17_campaign_transparency_reports for all to authenticated using (false);

create policy m17_fund_usage_select on public.m17_fund_usage_items for select to authenticated
  using (
    exists (
      select 1 from public.m17_campaign_transparency_reports r
      where r.id = report_id and public.has_org_permission(r.organization_id, 'donation.view')
    )
    or public._m17_is_moderator(auth.uid())
  );
create policy m17_fund_usage_mut on public.m17_fund_usage_items for all to authenticated using (false);

create policy m17_milestones_select on public.m17_transparency_milestones for select to authenticated
  using (
    exists (
      select 1 from public.m17_campaign_transparency_reports r
      where r.id = report_id and public.has_org_permission(r.organization_id, 'donation.view')
    )
    or public._m17_is_moderator(auth.uid())
  );
create policy m17_milestones_mut on public.m17_transparency_milestones for all to authenticated using (false);

revoke all on table public.m17_in_kind_needs from public, anon;
revoke all on table public.m17_in_kind_pledges from public, anon;
revoke all on table public.m17_volunteer_opportunities from public, anon;
revoke all on table public.m17_volunteer_applications from public, anon;
revoke all on table public.m17_campaign_transparency_reports from public, anon;
revoke all on table public.m17_fund_usage_items from public, anon;
revoke all on table public.m17_transparency_milestones from public, anon;

grant select on table public.m17_in_kind_needs to authenticated;
grant select on table public.m17_in_kind_pledges to authenticated;
grant select on table public.m17_volunteer_opportunities to authenticated;
grant select on table public.m17_volunteer_applications to authenticated;
grant select on table public.m17_campaign_transparency_reports to authenticated;
grant select on table public.m17_fund_usage_items to authenticated;
grant select on table public.m17_transparency_milestones to authenticated;

grant all on table public.m17_in_kind_needs to service_role;
grant all on table public.m17_in_kind_pledges to service_role;
grant all on table public.m17_volunteer_opportunities to service_role;
grant all on table public.m17_volunteer_applications to service_role;
grant all on table public.m17_campaign_transparency_reports to service_role;
grant all on table public.m17_fund_usage_items to service_role;
grant all on table public.m17_transparency_milestones to service_role;

-- ---------------------------------------------------------------------------
-- 6. RPCs públicas
-- ---------------------------------------------------------------------------
create or replace function public.m17_list_public_in_kind_needs(
  p_query text default null,
  p_category text default null,
  p_organization_id uuid default null
) returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_row public.m17_in_kind_needs;
declare v_q text := nullif(lower(trim(coalesce(p_query, ''))), '');
begin
  for v_row in
    select n.* from public.m17_in_kind_needs n
    where public._m17_inkind_need_is_public(n)
      and (p_category is null or n.category = upper(trim(p_category)))
      and (p_organization_id is null or n.organization_id = p_organization_id)
      and (v_q is null or n.title ilike '%' || v_q || '%' or n.description ilike '%' || v_q || '%')
    order by n.updated_at desc
  loop
    return next public._m17_public_in_kind_need_json(v_row.id);
  end loop;
end;
$$;

create or replace function public.m17_get_public_in_kind_need(p_need_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v jsonb;
begin
  v := public._m17_public_in_kind_need_json(p_need_id);
  if v is null then raise exception 'M17_NEED_NOT_PUBLIC'; end if;
  return v;
end;
$$;

create or replace function public.m17_list_public_volunteer_opportunities(
  p_query text default null,
  p_type text default null,
  p_organization_id uuid default null
) returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_row public.m17_volunteer_opportunities;
declare v_q text := nullif(lower(trim(coalesce(p_query, ''))), '');
begin
  for v_row in
    select o.* from public.m17_volunteer_opportunities o
    where public._m17_vol_opp_is_public(o)
      and o.status = 'PUBLISHED'
      and (p_type is null or o.opportunity_type = upper(trim(p_type)))
      and (p_organization_id is null or o.organization_id = p_organization_id)
      and (v_q is null or o.title ilike '%' || v_q || '%' or o.description ilike '%' || v_q || '%')
    order by o.updated_at desc
  loop
    return next public._m17_public_volunteer_opp_json(o.id);
  end loop;
end;
$$;

create or replace function public.m17_get_public_volunteer_opportunity(p_opportunity_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v jsonb;
begin
  v := public._m17_public_volunteer_opp_json(p_opportunity_id);
  if v is null then raise exception 'M17_OPPORTUNITY_NOT_PUBLIC'; end if;
  return v;
end;
$$;

create or replace function public.m17_get_public_campaign_transparency(p_campaign_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v jsonb;
begin
  v := public._m17_public_transparency_json(p_campaign_id);
  if v is null then raise exception 'M17_CAMPAIGN_NOT_PUBLIC'; end if;
  return v;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. RPCs autenticadas — bienes
-- ---------------------------------------------------------------------------
create or replace function public.m17_create_in_kind_pledge(
  p_need_id uuid,
  p_quantity integer,
  p_public_message text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m17_require_authenticated();
declare v_need public.m17_in_kind_needs;
declare v_id uuid;
begin
  if coalesce(p_quantity, 0) <= 0 then raise exception 'M17_INVALID_QUANTITY'; end if;
  select * into v_need from public.m17_in_kind_needs where id = p_need_id;
  if not found or v_need.status <> 'PUBLISHED' then raise exception 'M17_NEED_NOT_PUBLIC'; end if;
  insert into public.m17_in_kind_pledges (
    need_id, contributor_user_id, quantity, status, public_message
  ) values (
    p_need_id, v_actor, p_quantity, 'PLEDGED', nullif(trim(coalesce(p_public_message,'')), '')
  ) returning id into v_id;
  return jsonb_build_object('id', v_id, 'status', 'PLEDGED', 'quantity', p_quantity);
end;
$$;

create or replace function public.m17_cancel_own_in_kind_pledge(p_pledge_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m17_require_authenticated();
declare v_row public.m17_in_kind_pledges;
begin
  select * into v_row from public.m17_in_kind_pledges where id = p_pledge_id for update;
  if not found then raise exception 'M17_PLEDGE_NOT_FOUND'; end if;
  if v_row.contributor_user_id <> v_actor then raise exception 'M17_PERMISSION_DENIED'; end if;
  if v_row.status in ('DELIVERED','CANCELLED','REJECTED') then
    return jsonb_build_object('id', v_row.id, 'status', v_row.status);
  end if;
  update public.m17_in_kind_pledges set status = 'CANCELLED', cancelled_at = timezone('utc', now()),
    updated_at = timezone('utc', now()) where id = p_pledge_id;
  return jsonb_build_object('id', p_pledge_id, 'status', 'CANCELLED');
end;
$$;

create or replace function public.m17_mark_in_kind_pledge_delivered(p_pledge_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_row public.m17_in_kind_pledges;
declare v_need public.m17_in_kind_needs;
begin
  select * into v_row from public.m17_in_kind_pledges where id = p_pledge_id for update;
  if not found then raise exception 'M17_PLEDGE_NOT_FOUND'; end if;
  select * into v_need from public.m17_in_kind_needs where id = v_row.need_id;
  perform public._m17_require_org_perm(v_need.organization_id, 'donation.manage');
  if v_row.status = 'DELIVERED' then
    return jsonb_build_object('id', v_row.id, 'status', 'DELIVERED');
  end if;
  if v_row.status not in ('PLEDGED','ACCEPTED') then raise exception 'M17_INVALID_STATE'; end if;
  update public.m17_in_kind_pledges set status = 'DELIVERED', delivered_at = timezone('utc', now()),
    updated_at = timezone('utc', now()) where id = p_pledge_id;
  return jsonb_build_object('id', p_pledge_id, 'status', 'DELIVERED');
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. RPCs autenticadas — voluntariado
-- ---------------------------------------------------------------------------
create or replace function public.m17_submit_volunteer_application(
  p_opportunity_id uuid,
  p_message text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m17_require_authenticated();
declare v_opp public.m17_volunteer_opportunities;
declare v_id uuid;
begin
  select * into v_opp from public.m17_volunteer_opportunities where id = p_opportunity_id;
  if not found or v_opp.status <> 'PUBLISHED' then raise exception 'M17_OPPORTUNITY_NOT_PUBLIC'; end if;
  if exists (
    select 1 from public.m17_volunteer_applications a
    where a.opportunity_id = p_opportunity_id and a.applicant_user_id = v_actor
      and a.status in ('SUBMITTED','REVIEWING','ACCEPTED')
  ) then raise exception 'M17_DUPLICATE_APPLICATION'; end if;
  insert into public.m17_volunteer_applications (
    opportunity_id, applicant_user_id, status, private_message
  ) values (
    p_opportunity_id, v_actor, 'SUBMITTED', nullif(trim(coalesce(p_message,'')), '')
  ) returning id into v_id;
  return jsonb_build_object('id', v_id, 'status', 'SUBMITTED');
end;
$$;

create or replace function public.m17_withdraw_volunteer_application(p_application_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m17_require_authenticated();
declare v_row public.m17_volunteer_applications;
begin
  select * into v_row from public.m17_volunteer_applications where id = p_application_id for update;
  if not found then raise exception 'M17_APPLICATION_NOT_FOUND'; end if;
  if v_row.applicant_user_id <> v_actor then raise exception 'M17_PERMISSION_DENIED'; end if;
  if v_row.status in ('WITHDRAWN','REJECTED','COMPLETED') then
    return jsonb_build_object('id', v_row.id, 'status', v_row.status);
  end if;
  update public.m17_volunteer_applications set status = 'WITHDRAWN', withdrawn_at = timezone('utc', now()),
    updated_at = timezone('utc', now()) where id = p_application_id;
  return jsonb_build_object('id', p_application_id, 'status', 'WITHDRAWN');
end;
$$;

create or replace function public.m17_accept_volunteer_application(p_application_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_row public.m17_volunteer_applications;
declare v_opp public.m17_volunteer_opportunities;
begin
  select * into v_row from public.m17_volunteer_applications where id = p_application_id for update;
  if not found then raise exception 'M17_APPLICATION_NOT_FOUND'; end if;
  select * into v_opp from public.m17_volunteer_opportunities where id = v_row.opportunity_id for update;
  perform public._m17_require_org_perm(v_opp.organization_id, 'donation.manage');
  if v_opp.status in ('FILLED','COMPLETED','CANCELLED') then raise exception 'M17_OPPORTUNITY_TERMINAL'; end if;
  if v_row.status = 'ACCEPTED' then return jsonb_build_object('id', v_row.id, 'status', 'ACCEPTED'); end if;
  if v_row.status not in ('SUBMITTED','REVIEWING') then raise exception 'M17_INVALID_STATE'; end if;
  update public.m17_volunteer_applications set status = 'ACCEPTED', accepted_at = timezone('utc', now()),
    updated_at = timezone('utc', now()) where id = p_application_id;
  update public.m17_volunteer_opportunities set
    accepted_people = least(required_people, accepted_people + 1),
    updated_at = timezone('utc', now())
  where id = v_opp.id;
  return jsonb_build_object('id', p_application_id, 'status', 'ACCEPTED');
end;
$$;

-- Grants RPC
revoke all on function public.m17_list_public_in_kind_needs from public;
grant execute on function public.m17_list_public_in_kind_needs to anon, authenticated;
revoke all on function public.m17_get_public_in_kind_need from public;
grant execute on function public.m17_get_public_in_kind_need to anon, authenticated;
revoke all on function public.m17_list_public_volunteer_opportunities from public;
grant execute on function public.m17_list_public_volunteer_opportunities to anon, authenticated;
revoke all on function public.m17_get_public_volunteer_opportunity from public;
grant execute on function public.m17_get_public_volunteer_opportunity to anon, authenticated;
revoke all on function public.m17_get_public_campaign_transparency from public;
grant execute on function public.m17_get_public_campaign_transparency to anon, authenticated;

revoke all on function public.m17_create_in_kind_pledge from public;
grant execute on function public.m17_create_in_kind_pledge to authenticated;
revoke all on function public.m17_cancel_own_in_kind_pledge from public;
grant execute on function public.m17_cancel_own_in_kind_pledge to authenticated;
revoke all on function public.m17_mark_in_kind_pledge_delivered from public;
grant execute on function public.m17_mark_in_kind_pledge_delivered to authenticated;
revoke all on function public.m17_submit_volunteer_application from public;
grant execute on function public.m17_submit_volunteer_application to authenticated;
revoke all on function public.m17_withdraw_volunteer_application from public;
grant execute on function public.m17_withdraw_volunteer_application to authenticated;
revoke all on function public.m17_accept_volunteer_application from public;
grant execute on function public.m17_accept_volunteer_application to authenticated;

commit;
