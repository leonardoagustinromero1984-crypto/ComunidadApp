-- =============================================================================
-- LeoVer M17 — migración 054: campañas solidarias, contribuciones contractuales,
-- actualizaciones, RLS y superficie pública sanitizada.
-- Forward-only sobre 001–053. Sin pagos reales; CONFIRMED solo server-side (M24).
-- LOCAL ONLY — no aplicar hasta entorno no productivo autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 donation.* (deny-by-default vía has_org_permission)
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('donation.view', 'Ver campañas solidarias de la organización'),
  ('donation.manage', 'Gestionar campañas solidarias y actualizaciones')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in ('donation.view', 'donation.manage')
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code = 'donation.view'
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.m17_donation_campaigns (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete restrict,
  title text not null,
  description text not null,
  campaign_type text not null,
  campaign_status text not null default 'DRAFT',
  goal_amount_minor bigint not null,
  currency text not null default 'ARS',
  pet_id uuid references public.pets (id) on delete set null,
  pet_public_name text,
  shelter_profile_id uuid references public.m16_shelter_profiles (id) on delete set null,
  shelter_public_name text,
  need_description text,
  public_location_text text,
  cover_image_ref text,
  gallery_image_refs text[] not null default '{}',
  moderation_status text,
  internal_notes text,
  created_by uuid references public.users (id),
  starts_at timestamptz not null default timezone('utc', now()),
  ends_at timestamptz,
  published_at timestamptz,
  completed_at timestamptz,
  cancelled_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m17_campaign_type_chk check (campaign_type = any (array[
    'MEDICAL','FOOD_AND_SUPPLIES','RESCUE','SHELTER_INFRASTRUCTURE',
    'TRANSPORT','EMERGENCY','GENERAL_SUPPORT'
  ]::text[])),
  constraint m17_campaign_status_chk check (campaign_status = any (array[
    'DRAFT','PUBLISHED','PAUSED','COMPLETED','CANCELLED'
  ]::text[])),
  constraint m17_campaign_goal_chk check (goal_amount_minor > 0),
  constraint m17_campaign_currency_chk check (char_length(trim(currency)) between 3 and 3),
  constraint m17_campaign_title_len check (char_length(trim(title)) between 1 and 120),
  constraint m17_campaign_desc_len check (char_length(trim(description)) between 10 and 5000),
  constraint m17_campaign_dates_chk check (ends_at is null or ends_at > starts_at),
  constraint m17_campaign_moderation_chk check (
    moderation_status is null
    or moderation_status = any (array['APPROVED','PENDING','BLOCKED','HIDDEN']::text[])
  )
);

create index if not exists m17_campaigns_org_idx on public.m17_donation_campaigns (organization_id);
create index if not exists m17_campaigns_status_idx on public.m17_donation_campaigns (campaign_status);
create index if not exists m17_campaigns_public_idx
  on public.m17_donation_campaigns (campaign_status, moderation_status)
  where campaign_status in ('PUBLISHED','PAUSED','COMPLETED');

create table if not exists public.m17_campaign_updates (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.m17_donation_campaigns (id) on delete cascade,
  message text not null,
  created_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  constraint m17_update_msg_len check (char_length(trim(message)) between 3 and 2000)
);

create index if not exists m17_updates_campaign_idx
  on public.m17_campaign_updates (campaign_id, created_at desc);

create table if not exists public.m17_contributions (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.m17_donation_campaigns (id) on delete restrict,
  amount_minor bigint not null,
  currency text not null,
  status text not null default 'PENDING',
  visibility text not null default 'PUBLIC',
  donor_display_name text,
  public_message text,
  provider_reference text,
  idempotency_key text,
  created_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  confirmed_at timestamptz,
  refunded_at timestamptz,
  constraint m17_contrib_amount_chk check (amount_minor > 0),
  constraint m17_contrib_currency_chk check (char_length(trim(currency)) = 3),
  constraint m17_contrib_status_chk check (status = any (array[
    'PENDING','CONFIRMED','FAILED','CANCELLED','REFUNDED'
  ]::text[])),
  constraint m17_contrib_visibility_chk check (visibility = any (array[
    'PUBLIC','ANONYMOUS','PRIVATE'
  ]::text[])),
  constraint m17_contrib_donor_len check (
    donor_display_name is null or char_length(trim(donor_display_name)) <= 80
  )
);

create unique index if not exists m17_contrib_idempotency_uniq
  on public.m17_contributions (campaign_id, idempotency_key)
  where idempotency_key is not null;

create index if not exists m17_contrib_campaign_idx
  on public.m17_contributions (campaign_id, status);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m17_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v;
end;
$$;

create or replace function public._m17_org_is_eligible(p_org_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.organizations o
    where o.id = p_org_id
      and o.type in ('SHELTER', 'RESCUE_GROUP', 'NGO')
      and o.status in ('ACTIVE', 'RESTRICTED')
  );
$$;

create or replace function public._m17_require_org_perm(p_org_id uuid, p_perm text)
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m17_require_authenticated();
begin
  if not public._m17_org_is_eligible(p_org_id) then
    raise exception 'M17_ORGANIZATION_NOT_ELIGIBLE';
  end if;
  if not public.has_org_permission(p_org_id, p_perm) then
    raise exception 'M17_PERMISSION_DENIED';
  end if;
  return v_actor;
end;
$$;

create or replace function public._m17_is_moderator(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.platform_role_assignments pra
    join public.platform_roles pr on pr.id = pra.role_id
    where pra.user_id = p_user
      and pr.code in ('PLATFORM_ADMIN', 'MODERATOR', 'SUPPORT')
      and pra.status = 'ACTIVE'
  );
$$;

create or replace function public._m17_campaign_is_public(p_row public.m17_donation_campaigns)
returns boolean language sql stable as $$
  select p_row.campaign_status in ('PUBLISHED','PAUSED','COMPLETED')
    and coalesce(p_row.moderation_status, 'APPROVED') in ('APPROVED')
$$;

create or replace function public._m17_confirmed_minor(p_campaign_id uuid)
returns bigint language sql stable security definer set search_path = public as $$
  select coalesce(sum(c.amount_minor), 0)::bigint
  from public.m17_contributions c
  where c.campaign_id = p_campaign_id and c.status = 'CONFIRMED';
$$;

create or replace function public._m17_financial_summary(p_campaign_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_goal bigint;
  v_currency text;
  v_confirmed bigint;
  v_confirmed_count integer;
  v_pending_count integer;
  v_percent integer;
begin
  select goal_amount_minor, currency into v_goal, v_currency
  from public.m17_donation_campaigns where id = p_campaign_id;
  if not found then return null; end if;

  select coalesce(sum(amount_minor), 0)::bigint, count(*)::integer
  into v_confirmed, v_confirmed_count
  from public.m17_contributions
  where campaign_id = p_campaign_id and status = 'CONFIRMED';

  select count(*)::integer into v_pending_count
  from public.m17_contributions
  where campaign_id = p_campaign_id and status = 'PENDING';

  v_percent := least(999, ((v_confirmed * 100) / greatest(v_goal, 1))::integer);

  return jsonb_build_object(
    'confirmed_amount_minor', v_confirmed,
    'currency', v_currency,
    'goal_amount_minor', v_goal,
    'confirmed_contribution_count', v_confirmed_count,
    'pending_contribution_count', v_pending_count,
    'progress_percent', v_percent
  );
end;
$$;

create or replace function public._m17_public_contribution_json(p_row public.m17_contributions)
returns jsonb language plpgsql stable as $$
begin
  if p_row.status <> 'CONFIRMED' or p_row.visibility = 'PRIVATE' then
    return null;
  end if;
  return jsonb_build_object(
    'id', p_row.id,
    'amount_minor', p_row.amount_minor,
    'currency', p_row.currency,
    'donor_label', case
      when p_row.visibility = 'ANONYMOUS' then 'Donante anónimo'
      else coalesce(nullif(trim(p_row.donor_display_name), ''), 'Donante')
    end,
    'message', p_row.public_message,
    'created_at', p_row.created_at
  );
end;
$$;

create or replace function public._m17_public_campaign_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m17_donation_campaigns;
  v_org_name text;
  v_updates jsonb;
  v_summary jsonb;
begin
  select * into v from public.m17_donation_campaigns where id = p_id;
  if not found or not public._m17_campaign_is_public(v) then return null; end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = v.organization_id;

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', u.id, 'message', u.message, 'created_at', u.created_at
  ) order by u.created_at desc), '[]'::jsonb)
  into v_updates
  from public.m17_campaign_updates u where u.campaign_id = p_id;

  v_summary := public._m17_financial_summary(p_id);

  return jsonb_build_object(
    'id', v.id,
    'title', v.title,
    'description', v.description,
    'organization_display_name', v_org_name,
    'campaign_type', v.campaign_type,
    'status', v.campaign_status,
    'goal_amount_minor', v.goal_amount_minor,
    'currency', v.currency,
    'confirmed_amount_minor', (v_summary->>'confirmed_amount_minor')::bigint,
    'progress_percent', (v_summary->>'progress_percent')::integer,
    'confirmed_contribution_count', (v_summary->>'confirmed_contribution_count')::integer,
    'reference', jsonb_build_object(
      'pet_public_name', v.pet_public_name,
      'shelter_public_name', v.shelter_public_name,
      'need_description', v.need_description,
      'public_location_text', v.public_location_text
    ),
    'cover_image_ref', v.cover_image_ref,
    'gallery_image_refs', to_jsonb(v.gallery_image_refs),
    'public_updates', v_updates,
    'starts_at', v.starts_at,
    'ends_at', v.ends_at
  );
end;
$$;

create or replace function public._m17_internal_campaign_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m17_donation_campaigns;
  v_org_name text;
  v_updates jsonb;
begin
  select * into v from public.m17_donation_campaigns where id = p_id;
  if not found then return null; end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = v.organization_id;

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', u.id, 'message', u.message, 'created_at', u.created_at
  ) order by u.created_at desc), '[]'::jsonb)
  into v_updates
  from public.m17_campaign_updates u where u.campaign_id = p_id;

  return jsonb_build_object(
    'id', v.id,
    'organization_id', v.organization_id,
    'organization_display_name', v_org_name,
    'title', v.title,
    'description', v.description,
    'campaign_type', v.campaign_type,
    'status', v.campaign_status,
    'goal_amount_minor', v.goal_amount_minor,
    'currency', v.currency,
    'reference', jsonb_build_object(
      'pet_id', v.pet_id,
      'pet_public_name', v.pet_public_name,
      'shelter_profile_id', v.shelter_profile_id,
      'shelter_public_name', v.shelter_public_name,
      'need_description', v.need_description,
      'public_location_text', v.public_location_text
    ),
    'cover_image_ref', v.cover_image_ref,
    'gallery_image_refs', to_jsonb(v.gallery_image_refs),
    'public_updates', v_updates,
    'internal_notes', v.internal_notes,
    'moderation_status', v.moderation_status,
    'starts_at', v.starts_at,
    'ends_at', v.ends_at,
    'created_by', v.created_by,
    'created_at', v.created_at,
    'updated_at', v.updated_at,
    'published_at', v.published_at,
    'completed_at', v.completed_at,
    'cancelled_at', v.cancelled_at
  );
end;
$$;

-- Bloquear confirmación financiera desde cliente
create or replace function public._m17_contributions_guard()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'UPDATE' then
    if new.status is distinct from old.status
       and new.status = 'CONFIRMED'
       and coalesce(current_setting('request.jwt.claim.role', true), '') <> 'service_role' then
      raise exception 'M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE';
    end if;
  end if;
  if tg_op = 'INSERT' then
    if new.status = 'CONFIRMED'
       and coalesce(current_setting('request.jwt.claim.role', true), '') <> 'service_role' then
      raise exception 'M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE';
    end if;
  end if;
  return new;
end;
$$;

create trigger m17_contributions_guard_trg
  before insert or update on public.m17_contributions
  for each row execute function public._m17_contributions_guard();

-- ---------------------------------------------------------------------------
-- 3. RLS
-- ---------------------------------------------------------------------------
alter table public.m17_donation_campaigns enable row level security;
alter table public.m17_campaign_updates enable row level security;
alter table public.m17_contributions enable row level security;

create policy m17_campaigns_select on public.m17_donation_campaigns for select to authenticated
  using (
    public.has_org_permission(organization_id, 'donation.view')
    or public._m17_is_moderator(auth.uid())
  );

create policy m17_campaigns_mut on public.m17_donation_campaigns for all to authenticated
  using (false);

create policy m17_updates_select on public.m17_campaign_updates for select to authenticated
  using (
    exists (
      select 1 from public.m17_donation_campaigns c
      where c.id = campaign_id
        and public.has_org_permission(c.organization_id, 'donation.view')
    )
    or public._m17_is_moderator(auth.uid())
  );

create policy m17_updates_mut on public.m17_campaign_updates for all to authenticated using (false);

create policy m17_contrib_select on public.m17_contributions for select to authenticated
  using (
    exists (
      select 1 from public.m17_donation_campaigns c
      where c.id = campaign_id
        and public.has_org_permission(c.organization_id, 'donation.view')
    )
    or public._m17_is_moderator(auth.uid())
  );

create policy m17_contrib_mut on public.m17_contributions for all to authenticated using (false);

revoke all on table public.m17_donation_campaigns from public, anon;
revoke all on table public.m17_campaign_updates from public, anon;
revoke all on table public.m17_contributions from public, anon;
grant select on table public.m17_donation_campaigns to authenticated;
grant select on table public.m17_campaign_updates to authenticated;
grant select on table public.m17_contributions to authenticated;
grant all on table public.m17_donation_campaigns to service_role;
grant all on table public.m17_campaign_updates to service_role;
grant all on table public.m17_contributions to service_role;

-- ---------------------------------------------------------------------------
-- 4. RPCs — superficie pública (anon + authenticated)
-- ---------------------------------------------------------------------------
create or replace function public.m17_list_public_campaigns(
  p_query text default null,
  p_type text default null,
  p_organization_id uuid default null,
  p_shelter_profile_id uuid default null,
  p_with_pet_only boolean default false,
  p_active_only boolean default true,
  p_completed_only boolean default false,
  p_near_goal_only boolean default false
) returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_row public.m17_donation_campaigns;
  v_q text := nullif(lower(trim(coalesce(p_query, ''))), '');
  v_summary jsonb;
begin
  for v_row in
    select c.* from public.m17_donation_campaigns c
    where public._m17_campaign_is_public(c)
      and (
        coalesce(p_completed_only, false) and c.campaign_status = 'COMPLETED'
        or not coalesce(p_completed_only, false) and (
          coalesce(p_active_only, true) and c.campaign_status = 'PUBLISHED'
          or not coalesce(p_active_only, false) and c.campaign_status in ('PUBLISHED','PAUSED','COMPLETED')
        )
      )
      and (p_type is null or c.campaign_type = upper(trim(p_type)))
      and (p_organization_id is null or c.organization_id = p_organization_id)
      and (p_shelter_profile_id is null or c.shelter_profile_id = p_shelter_profile_id)
      and (not coalesce(p_with_pet_only, false) or c.pet_id is not null)
      and (v_q is null or c.title ilike '%' || v_q || '%' or c.description ilike '%' || v_q || '%')
    order by c.updated_at desc
  loop
    if coalesce(p_near_goal_only, false) then
      v_summary := public._m17_financial_summary(v_row.id);
      if (v_summary->>'progress_percent')::integer < 85 then continue; end if;
    end if;
    return next public._m17_public_campaign_json(v_row.id);
  end loop;
end;
$$;

create or replace function public.m17_get_public_campaign(p_campaign_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v jsonb;
begin
  v := public._m17_public_campaign_json(p_campaign_id);
  if v is null then raise exception 'M17_CAMPAIGN_NOT_PUBLIC'; end if;
  return v;
end;
$$;

create or replace function public.m17_list_public_contributions(p_campaign_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_row public.m17_contributions;
  v_c public.m17_donation_campaigns;
  v_json jsonb;
begin
  select * into v_c from public.m17_donation_campaigns where id = p_campaign_id;
  if not found or not public._m17_campaign_is_public(v_c) then
    raise exception 'M17_CAMPAIGN_NOT_PUBLIC';
  end if;
  for v_row in
    select * from public.m17_contributions
    where campaign_id = p_campaign_id and status = 'CONFIRMED' and visibility <> 'PRIVATE'
    order by created_at desc
  loop
    v_json := public._m17_public_contribution_json(v_row);
    if v_json is not null then return next v_json; end if;
  end loop;
end;
$$;

create or replace function public.m17_get_financial_summary(p_campaign_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v_c public.m17_donation_campaigns;
declare v_summary jsonb;
begin
  select * into v_c from public.m17_donation_campaigns where id = p_campaign_id;
  if not found then raise exception 'M17_CAMPAIGN_NOT_FOUND'; end if;
  if not public._m17_campaign_is_public(v_c) then
    perform public._m17_require_org_perm(v_c.organization_id, 'donation.view');
  end if;
  v_summary := public._m17_financial_summary(p_campaign_id);
  if v_summary is null then raise exception 'M17_CAMPAIGN_NOT_FOUND'; end if;
  return v_summary;
end;
$$;

create or replace function public.m17_is_organization_eligible(p_organization_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public._m17_org_is_eligible(p_organization_id);
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs — administración (authenticated)
-- ---------------------------------------------------------------------------
create or replace function public.m17_get_campaign(p_campaign_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m17_donation_campaigns;
begin
  select * into v from public.m17_donation_campaigns where id = p_campaign_id;
  if not found then raise exception 'M17_CAMPAIGN_NOT_FOUND'; end if;
  perform public._m17_require_org_perm(v.organization_id, 'donation.view');
  return public._m17_internal_campaign_json(p_campaign_id);
end;
$$;

create or replace function public.m17_list_org_campaigns(p_organization_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_row public.m17_donation_campaigns;
begin
  perform public._m17_require_org_perm(p_organization_id, 'donation.view');
  for v_row in
    select * from public.m17_donation_campaigns
    where organization_id = p_organization_id
    order by updated_at desc
  loop
    return next public._m17_internal_campaign_json(v_row.id);
  end loop;
end;
$$;

create or replace function public.m17_create_campaign(
  p_organization_id uuid,
  p_title text,
  p_description text,
  p_campaign_type text,
  p_goal_amount_minor bigint,
  p_currency text default 'ARS',
  p_starts_at timestamptz default null,
  p_ends_at timestamptz default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid; v_id uuid;
begin
  v_actor := public._m17_require_org_perm(p_organization_id, 'donation.manage');
  if coalesce(p_goal_amount_minor, 0) <= 0 then raise exception 'M17_INVALID_GOAL'; end if;
  insert into public.m17_donation_campaigns (
    organization_id, title, description, campaign_type, goal_amount_minor, currency,
    starts_at, ends_at, created_by
  ) values (
    p_organization_id, trim(p_title), trim(p_description), upper(trim(p_campaign_type)),
    p_goal_amount_minor, upper(trim(p_currency)),
    coalesce(p_starts_at, timezone('utc', now())), p_ends_at, v_actor
  ) returning id into v_id;
  return public._m17_internal_campaign_json(v_id);
end;
$$;

create or replace function public.m17_update_campaign_details(
  p_campaign_id uuid,
  p_title text,
  p_description text,
  p_campaign_type text,
  p_pet_id uuid default null,
  p_pet_public_name text default null,
  p_shelter_profile_id uuid default null,
  p_shelter_public_name text default null,
  p_need_description text default null,
  p_public_location_text text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m17_donation_campaigns; v_actor uuid;
begin
  select * into v from public.m17_donation_campaigns where id = p_campaign_id;
  if not found then raise exception 'M17_CAMPAIGN_NOT_FOUND'; end if;
  v_actor := public._m17_require_org_perm(v.organization_id, 'donation.manage');
  if v.campaign_status in ('COMPLETED','CANCELLED') then raise exception 'M17_STATE_ALREADY_FINAL'; end if;
  update public.m17_donation_campaigns set
    title = trim(p_title), description = trim(p_description),
    campaign_type = upper(trim(p_campaign_type)),
    pet_id = p_pet_id, pet_public_name = nullif(trim(coalesce(p_pet_public_name,'')), ''),
    shelter_profile_id = p_shelter_profile_id,
    shelter_public_name = nullif(trim(coalesce(p_shelter_public_name,'')), ''),
    need_description = nullif(trim(coalesce(p_need_description,'')), ''),
    public_location_text = nullif(trim(coalesce(p_public_location_text,'')), ''),
    updated_at = timezone('utc', now())
  where id = p_campaign_id;
  return public._m17_internal_campaign_json(p_campaign_id);
end;
$$;

create or replace function public.m17_update_campaign_goal(
  p_campaign_id uuid,
  p_goal_amount_minor bigint,
  p_currency text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m17_donation_campaigns;
declare v_has_confirmed boolean;
begin
  select * into v from public.m17_donation_campaigns where id = p_campaign_id;
  if not found then raise exception 'M17_CAMPAIGN_NOT_FOUND'; end if;
  perform public._m17_require_org_perm(v.organization_id, 'donation.manage');
  if v.campaign_status in ('COMPLETED','CANCELLED') then raise exception 'M17_STATE_ALREADY_FINAL'; end if;
  if coalesce(p_goal_amount_minor, 0) <= 0 then raise exception 'M17_INVALID_GOAL'; end if;
  select exists(
    select 1 from public.m17_contributions
    where campaign_id = p_campaign_id and status = 'CONFIRMED'
  ) into v_has_confirmed;
  if v_has_confirmed and upper(trim(p_currency)) <> v.currency then
    raise exception 'M17_INVALID_CURRENCY';
  end if;
  update public.m17_donation_campaigns set
    goal_amount_minor = p_goal_amount_minor,
    currency = upper(trim(p_currency)),
    updated_at = timezone('utc', now())
  where id = p_campaign_id;
  return public._m17_internal_campaign_json(p_campaign_id);
end;
$$;

create or replace function public.m17_update_campaign_images(
  p_campaign_id uuid,
  p_cover_image_ref text default null,
  p_gallery_image_refs text[] default '{}'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m17_donation_campaigns;
begin
  select * into v from public.m17_donation_campaigns where id = p_campaign_id;
  if not found then raise exception 'M17_CAMPAIGN_NOT_FOUND'; end if;
  perform public._m17_require_org_perm(v.organization_id, 'donation.manage');
  update public.m17_donation_campaigns set
    cover_image_ref = p_cover_image_ref,
    gallery_image_refs = coalesce(p_gallery_image_refs, '{}'),
    updated_at = timezone('utc', now())
  where id = p_campaign_id;
  return public._m17_internal_campaign_json(p_campaign_id);
end;
$$;

create or replace function public.m17_transition_campaign(
  p_campaign_id uuid,
  p_target_status text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m17_donation_campaigns;
declare v_target text := upper(trim(p_target_status));
begin
  select * into v from public.m17_donation_campaigns where id = p_campaign_id for update;
  if not found then raise exception 'M17_CAMPAIGN_NOT_FOUND'; end if;
  perform public._m17_require_org_perm(v.organization_id, 'donation.manage');
  if v.campaign_status = v_target then
    return public._m17_internal_campaign_json(p_campaign_id);
  end if;
  if v.campaign_status in ('COMPLETED','CANCELLED') then raise exception 'M17_STATE_ALREADY_FINAL'; end if;
  if v_target = 'PUBLISHED' and v.campaign_status not in ('DRAFT','PAUSED') then
    raise exception 'M17_INVALID_STATE_TRANSITION';
  elsif v_target = 'PAUSED' and v.campaign_status <> 'PUBLISHED' then
    raise exception 'M17_INVALID_STATE_TRANSITION';
  elsif v_target in ('COMPLETED','CANCELLED') and v.campaign_status not in ('PUBLISHED','PAUSED') then
    raise exception 'M17_INVALID_STATE_TRANSITION';
  end if;
  update public.m17_donation_campaigns set
    campaign_status = v_target,
    published_at = case when v_target = 'PUBLISHED' and published_at is null
      then timezone('utc', now()) else published_at end,
    completed_at = case when v_target = 'COMPLETED' then timezone('utc', now()) else completed_at end,
    cancelled_at = case when v_target = 'CANCELLED' then timezone('utc', now()) else cancelled_at end,
    updated_at = timezone('utc', now())
  where id = p_campaign_id;
  return public._m17_internal_campaign_json(p_campaign_id);
end;
$$;

create or replace function public.m17_add_campaign_update(
  p_campaign_id uuid,
  p_message text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m17_donation_campaigns; v_actor uuid;
begin
  select * into v from public.m17_donation_campaigns where id = p_campaign_id;
  if not found then raise exception 'M17_CAMPAIGN_NOT_FOUND'; end if;
  v_actor := public._m17_require_org_perm(v.organization_id, 'donation.manage');
  if char_length(trim(coalesce(p_message,''))) < 3 then raise exception 'M17_INVALID_DESCRIPTION'; end if;
  insert into public.m17_campaign_updates (campaign_id, message, created_by)
  values (p_campaign_id, trim(p_message), v_actor);
  update public.m17_donation_campaigns set updated_at = timezone('utc', now()) where id = p_campaign_id;
  return public._m17_internal_campaign_json(p_campaign_id);
end;
$$;

-- Grants RPC
revoke all on function public.m17_list_public_campaigns from public;
grant execute on function public.m17_list_public_campaigns to anon, authenticated;
revoke all on function public.m17_get_public_campaign from public;
grant execute on function public.m17_get_public_campaign to anon, authenticated;
revoke all on function public.m17_list_public_contributions from public;
grant execute on function public.m17_list_public_contributions to anon, authenticated;
revoke all on function public.m17_get_financial_summary from public;
grant execute on function public.m17_get_financial_summary to anon, authenticated;
revoke all on function public.m17_is_organization_eligible from public;
grant execute on function public.m17_is_organization_eligible to authenticated;

revoke all on function public.m17_get_campaign from public;
grant execute on function public.m17_get_campaign to authenticated;
revoke all on function public.m17_list_org_campaigns from public;
grant execute on function public.m17_list_org_campaigns to authenticated;
revoke all on function public.m17_create_campaign from public;
grant execute on function public.m17_create_campaign to authenticated;
revoke all on function public.m17_update_campaign_details from public;
grant execute on function public.m17_update_campaign_details to authenticated;
revoke all on function public.m17_update_campaign_goal from public;
grant execute on function public.m17_update_campaign_goal to authenticated;
revoke all on function public.m17_update_campaign_images from public;
grant execute on function public.m17_update_campaign_images to authenticated;
revoke all on function public.m17_transition_campaign from public;
grant execute on function public.m17_transition_campaign to authenticated;
revoke all on function public.m17_add_campaign_update from public;
grant execute on function public.m17_add_campaign_update to authenticated;

commit;
