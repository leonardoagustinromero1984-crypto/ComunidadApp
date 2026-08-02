-- =============================================================================
-- LeoVer M18 — migración 058: eventos comunitarios, inscripciones, recordatorios,
-- RLS y superficie pública sanitizada.
-- Forward-only sobre 001–057. Sin pagos ni venta de entradas.
-- LOCAL ONLY — no aplicar hasta entorno no productivo autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 event.* (deny-by-default vía has_org_permission)
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('event.view', 'Ver eventos comunitarios de la organización'),
  ('event.manage', 'Gestionar eventos comunitarios, inscripciones y check-in')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in ('event.view', 'event.manage')
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code = 'event.view'
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.m18_community_events (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete restrict,
  title text not null,
  description text not null,
  event_type text not null,
  event_status text not null default 'DRAFT',
  venue_name text,
  pet_id uuid references public.pets (id) on delete set null,
  pet_public_name text,
  shelter_profile_id uuid references public.m16_shelter_profiles (id) on delete set null,
  shelter_public_name text,
  public_location_text text,
  cover_image_ref text,
  max_capacity integer not null,
  waitlist_enabled boolean not null default true,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  check_in_opens_at timestamptz,
  check_in_closes_at timestamptz,
  internal_notes text,
  moderation_status text,
  created_by uuid references public.users (id),
  published_at timestamptz,
  completed_at timestamptz,
  cancelled_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m18_event_type_chk check (event_type = any (array[
    'ADOPTION_FAIR','VOLUNTEER_DAY','TRAINING_WORKSHOP',
    'COMMUNITY_GATHERING','FREE_FUNDRAISER','AWARENESS_WALK'
  ]::text[])),
  constraint m18_event_status_chk check (event_status = any (array[
    'DRAFT','PUBLISHED','PAUSED','COMPLETED','CANCELLED'
  ]::text[])),
  constraint m18_event_capacity_chk check (max_capacity > 0),
  constraint m18_event_title_len check (char_length(trim(title)) between 1 and 120),
  constraint m18_event_desc_len check (char_length(trim(description)) between 10 and 5000),
  constraint m18_event_dates_chk check (ends_at > starts_at),
  constraint m18_event_moderation_chk check (
    moderation_status is null
    or moderation_status = any (array['APPROVED','PENDING','BLOCKED','HIDDEN']::text[])
  )
);

create index if not exists m18_events_org_idx on public.m18_community_events (organization_id);
create index if not exists m18_events_status_idx on public.m18_community_events (event_status);
create index if not exists m18_events_public_idx
  on public.m18_community_events (event_status, moderation_status, starts_at desc)
  where event_status in ('PUBLISHED','PAUSED','COMPLETED');

create table if not exists public.m18_event_registrations (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.m18_community_events (id) on delete cascade,
  user_id uuid not null references public.users (id) on delete restrict,
  status text not null default 'REGISTERED',
  attendee_display_name text,
  registered_at timestamptz not null default timezone('utc', now()),
  checked_in_at timestamptz,
  reminder_scheduled boolean not null default false,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m18_reg_status_chk check (status = any (array[
    'REGISTERED','WAITLISTED','CANCELLED','CHECKED_IN','NO_SHOW'
  ]::text[])),
  constraint m18_reg_attendee_len check (
    attendee_display_name is null or char_length(trim(attendee_display_name)) <= 80
  )
);

create unique index if not exists m18_reg_event_user_uniq
  on public.m18_event_registrations (event_id, user_id);

create index if not exists m18_reg_event_idx
  on public.m18_event_registrations (event_id, status, registered_at);

create table if not exists public.m18_event_reminders (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.m18_community_events (id) on delete cascade,
  user_id uuid not null references public.users (id) on delete cascade,
  scheduled_for timestamptz not null,
  status text not null default 'SCHEDULED',
  sent_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m18_reminder_status_chk check (status = any (array[
    'SCHEDULED','SENT','SKIPPED'
  ]::text[]))
);

create unique index if not exists m18_reminder_event_user_uniq
  on public.m18_event_reminders (event_id, user_id);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m18_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v;
end;
$$;

create or replace function public._m18_org_is_eligible(p_org_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.organizations o
    where o.id = p_org_id
      and o.type in ('SHELTER', 'RESCUE_GROUP', 'NGO', 'TRAINING_CENTER')
      and o.status in ('ACTIVE', 'RESTRICTED')
  );
$$;

create or replace function public._m18_require_org_perm(p_org_id uuid, p_perm text)
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m18_require_authenticated();
begin
  if not public._m18_org_is_eligible(p_org_id) then
    raise exception 'M18_ORGANIZATION_NOT_ELIGIBLE';
  end if;
  if not public.has_org_permission(p_org_id, p_perm) then
    raise exception 'M18_PERMISSION_DENIED';
  end if;
  return v_actor;
end;
$$;

create or replace function public._m18_is_moderator(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_user is not null and (
    public.user_has_active_role(p_user, 'MODERATOR')
    or public.user_has_active_role(p_user, 'ADMIN')
    or public.user_has_active_role(p_user, 'SUPERADMIN')
  );
$$;

create or replace function public._m18_event_is_public(p_row public.m18_community_events)
returns boolean language sql stable as $$
  select p_row.event_status in ('PUBLISHED','PAUSED','COMPLETED')
    and coalesce(p_row.moderation_status, 'APPROVED') in ('APPROVED')
$$;

create or replace function public._m18_capacity_summary(p_event_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_max integer;
  v_waitlist boolean;
  v_registered integer;
  v_waitlisted integer;
  v_available integer;
begin
  select max_capacity, waitlist_enabled into v_max, v_waitlist
  from public.m18_community_events where id = p_event_id;
  if not found then return null; end if;

  select count(*)::integer into v_registered
  from public.m18_event_registrations
  where event_id = p_event_id and status in ('REGISTERED','CHECKED_IN');

  select count(*)::integer into v_waitlisted
  from public.m18_event_registrations
  where event_id = p_event_id and status = 'WAITLISTED';

  v_available := greatest(v_max - v_registered, 0);

  return jsonb_build_object(
    'max_capacity', v_max,
    'registered_count', v_registered,
    'waitlist_count', v_waitlisted,
    'available_spots', v_available,
    'is_full', v_available = 0,
    'is_waitlist_open', v_waitlist and v_available = 0
  );
end;
$$;

create or replace function public._m18_registration_stats(p_event_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'registered_count', count(*) filter (where status in ('REGISTERED','CHECKED_IN')),
    'waitlist_count', count(*) filter (where status = 'WAITLISTED'),
    'checked_in_count', count(*) filter (where status = 'CHECKED_IN')
  )
  from public.m18_event_registrations
  where event_id = p_event_id;
$$;

create or replace function public._m18_public_event_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m18_community_events;
  v_org_name text;
  v_summary jsonb;
  v_registration_open boolean;
begin
  select * into v from public.m18_community_events where id = p_id;
  if not found or not public._m18_event_is_public(v) then return null; end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = v.organization_id;

  v_summary := public._m18_capacity_summary(p_id);
  v_registration_open := v.event_status = 'PUBLISHED'
    and (
      (v_summary->>'available_spots')::integer > 0
      or (v_summary->>'is_waitlist_open')::boolean
    );

  return jsonb_build_object(
    'id', v.id,
    'title', v.title,
    'description', v.description,
    'organization_display_name', v_org_name,
    'event_type', v.event_type,
    'status', v.event_status,
    'venue_name', v.venue_name,
    'reference', jsonb_build_object(
      'pet_public_name', v.pet_public_name,
      'shelter_public_name', v.shelter_public_name,
      'public_location_text', v.public_location_text
    ),
    'cover_image_ref', v.cover_image_ref,
    'max_capacity', (v_summary->>'max_capacity')::integer,
    'registered_count', (v_summary->>'registered_count')::integer,
    'waitlist_count', (v_summary->>'waitlist_count')::integer,
    'available_spots', (v_summary->>'available_spots')::integer,
    'is_full', (v_summary->>'is_full')::boolean,
    'is_waitlist_open', (v_summary->>'is_waitlist_open')::boolean,
    'is_registration_open', v_registration_open,
    'starts_at', v.starts_at,
    'ends_at', v.ends_at
  );
end;
$$;

create or replace function public._m18_internal_event_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m18_community_events;
declare v_org_name text;
begin
  select * into v from public.m18_community_events where id = p_id;
  if not found then return null; end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = v.organization_id;

  return jsonb_build_object(
    'id', v.id,
    'organization_id', v.organization_id,
    'organization_display_name', v_org_name,
    'title', v.title,
    'description', v.description,
    'event_type', v.event_type,
    'status', v.event_status,
    'venue_name', v.venue_name,
    'reference', jsonb_build_object(
      'pet_id', v.pet_id,
      'pet_public_name', v.pet_public_name,
      'shelter_profile_id', v.shelter_profile_id,
      'shelter_public_name', v.shelter_public_name,
      'public_location_text', v.public_location_text
    ),
    'cover_image_ref', v.cover_image_ref,
    'max_capacity', v.max_capacity,
    'waitlist_enabled', v.waitlist_enabled,
    'starts_at', v.starts_at,
    'ends_at', v.ends_at,
    'check_in_opens_at', v.check_in_opens_at,
    'check_in_closes_at', v.check_in_closes_at,
    'internal_notes', v.internal_notes,
    'moderation_status', v.moderation_status,
    'created_by', v.created_by,
    'created_at', v.created_at,
    'updated_at', v.updated_at,
    'published_at', v.published_at,
    'completed_at', v.completed_at,
    'cancelled_at', v.cancelled_at
  );
end;
$$;

create or replace function public._m18_internal_registration_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare r public.m18_event_registrations;
begin
  select * into r from public.m18_event_registrations where id = p_id;
  if not found then return null; end if;
  return jsonb_build_object(
    'id', r.id,
    'event_id', r.event_id,
    'user_id', r.user_id,
    'status', r.status,
    'attendee_display_name', r.attendee_display_name,
    'registered_at', r.registered_at,
    'checked_in_at', r.checked_in_at,
    'reminder_scheduled', r.reminder_scheduled
  );
end;
$$;

create or replace function public._m18_promote_waitlist(p_event_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_summary jsonb;
  v_next uuid;
begin
  v_summary := public._m18_capacity_summary(p_event_id);
  if (v_summary->>'available_spots')::integer <= 0 then return; end if;
  select id into v_next
  from public.m18_event_registrations
  where event_id = p_event_id and status = 'WAITLISTED'
  order by registered_at asc
  limit 1;
  if v_next is null then return; end if;
  update public.m18_event_registrations
  set status = 'REGISTERED', updated_at = timezone('utc', now())
  where id = v_next;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. RLS
-- ---------------------------------------------------------------------------
alter table public.m18_community_events enable row level security;
alter table public.m18_event_registrations enable row level security;
alter table public.m18_event_reminders enable row level security;

create policy m18_events_select on public.m18_community_events for select to authenticated
  using (
    public.has_org_permission(organization_id, 'event.view')
    or public._m18_is_moderator(auth.uid())
  );

create policy m18_events_mut on public.m18_community_events for all to authenticated
  using (false);

create policy m18_regs_select on public.m18_event_registrations for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from public.m18_community_events e
      where e.id = event_id
        and public.has_org_permission(e.organization_id, 'event.view')
    )
    or public._m18_is_moderator(auth.uid())
  );

create policy m18_regs_mut on public.m18_event_registrations for all to authenticated
  using (false);

create policy m18_reminders_select on public.m18_event_reminders for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from public.m18_community_events e
      where e.id = event_id
        and public.has_org_permission(e.organization_id, 'event.view')
    )
    or public._m18_is_moderator(auth.uid())
  );

create policy m18_reminders_mut on public.m18_event_reminders for all to authenticated
  using (false);

revoke all on table public.m18_community_events from public, anon;
revoke all on table public.m18_event_registrations from public, anon;
revoke all on table public.m18_event_reminders from public, anon;
grant select on table public.m18_community_events to authenticated;
grant select on table public.m18_event_registrations to authenticated;
grant select on table public.m18_event_reminders to authenticated;
grant all on table public.m18_community_events to service_role;
grant all on table public.m18_event_registrations to service_role;
grant all on table public.m18_event_reminders to service_role;

-- ---------------------------------------------------------------------------
-- 4. RPCs — superficie pública (anon + authenticated)
-- ---------------------------------------------------------------------------
create or replace function public.m18_list_public_events(
  p_query text default null,
  p_type text default null,
  p_organization_id uuid default null,
  p_active_only boolean default true,
  p_completed_only boolean default false,
  p_with_open_spots_only boolean default false,
  p_upcoming_only boolean default true
) returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_row public.m18_community_events;
  v_q text := nullif(lower(trim(coalesce(p_query, ''))), '');
  v_summary jsonb;
  v_now timestamptz := timezone('utc', now());
begin
  for v_row in
    select e.* from public.m18_community_events e
    where public._m18_event_is_public(e)
      and (
        coalesce(p_completed_only, false) and e.event_status = 'COMPLETED'
        or not coalesce(p_completed_only, false) and (
          coalesce(p_active_only, true) and e.event_status = 'PUBLISHED'
          or not coalesce(p_active_only, false)
            and e.event_status in ('PUBLISHED','PAUSED','COMPLETED')
        )
      )
      and (p_type is null or e.event_type = upper(trim(p_type)))
      and (p_organization_id is null or e.organization_id = p_organization_id)
      and (
        not coalesce(p_upcoming_only, true)
        or e.ends_at >= v_now
        or e.event_status = 'COMPLETED'
      )
      and (
        v_q is null
        or e.title ilike '%' || v_q || '%'
        or e.description ilike '%' || v_q || '%'
      )
    order by e.starts_at desc
  loop
    if coalesce(p_with_open_spots_only, false) then
      v_summary := public._m18_capacity_summary(v_row.id);
      if (v_summary->>'available_spots')::integer <= 0 then continue; end if;
    end if;
    return next public._m18_public_event_json(v_row.id);
  end loop;
end;
$$;

create or replace function public.m18_get_public_event(p_event_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m18_community_events;
  v_json jsonb;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  if v.event_status = 'CANCELLED' then raise exception 'M18_EVENT_TERMINAL'; end if;
  if v.event_status not in ('PUBLISHED','PAUSED','COMPLETED') then
    raise exception 'M18_EVENT_NOT_PUBLIC';
  end if;
  v_json := public._m18_public_event_json(p_event_id);
  if v_json is null then raise exception 'M18_EVENT_NOT_PUBLIC'; end if;
  return v_json;
end;
$$;

create or replace function public.m18_get_public_registration_stats(p_event_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m18_community_events;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  if not public._m18_event_is_public(v) then
    perform public._m18_require_org_perm(v.organization_id, 'event.view');
  end if;
  return public._m18_registration_stats(p_event_id);
end;
$$;

create or replace function public.m18_get_capacity_summary(p_event_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m18_community_events;
  v_summary jsonb;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  if not public._m18_event_is_public(v) then
    perform public._m18_require_org_perm(v.organization_id, 'event.view');
  end if;
  v_summary := public._m18_capacity_summary(p_event_id);
  if v_summary is null then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  return v_summary;
end;
$$;

create or replace function public.m18_is_organization_eligible(p_organization_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public._m18_org_is_eligible(p_organization_id);
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs — administración e inscripciones (authenticated)
-- ---------------------------------------------------------------------------
create or replace function public.m18_get_event(p_event_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m18_community_events;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.view');
  return public._m18_internal_event_json(p_event_id);
end;
$$;

create or replace function public.m18_list_org_events(p_organization_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_row public.m18_community_events;
begin
  perform public._m18_require_org_perm(p_organization_id, 'event.view');
  for v_row in
    select * from public.m18_community_events
    where organization_id = p_organization_id
    order by starts_at desc
  loop
    return next public._m18_internal_event_json(v_row.id);
  end loop;
end;
$$;

create or replace function public.m18_create_event(
  p_organization_id uuid,
  p_title text,
  p_description text,
  p_event_type text,
  p_max_capacity integer,
  p_starts_at timestamptz,
  p_ends_at timestamptz,
  p_waitlist_enabled boolean default true,
  p_venue_name text default null,
  p_pet_id uuid default null,
  p_pet_public_name text default null,
  p_shelter_profile_id uuid default null,
  p_shelter_public_name text default null,
  p_public_location_text text default null,
  p_cover_image_ref text default null,
  p_check_in_opens_at timestamptz default null,
  p_check_in_closes_at timestamptz default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid; v_id uuid;
begin
  v_actor := public._m18_require_org_perm(p_organization_id, 'event.manage');
  if coalesce(p_max_capacity, 0) <= 0 then raise exception 'M18_INVALID_CAPACITY'; end if;
  if p_ends_at <= p_starts_at then raise exception 'M18_INVALID_DATE_RANGE'; end if;
  if char_length(trim(coalesce(p_title,''))) < 1 then raise exception 'M18_INVALID_TITLE'; end if;
  if char_length(trim(coalesce(p_description,''))) < 10 then raise exception 'M18_INVALID_DESCRIPTION'; end if;
  insert into public.m18_community_events (
    organization_id, title, description, event_type, max_capacity, waitlist_enabled,
    venue_name, pet_id, pet_public_name, shelter_profile_id, shelter_public_name,
    public_location_text, cover_image_ref, starts_at, ends_at,
    check_in_opens_at, check_in_closes_at, created_by
  ) values (
    p_organization_id, trim(p_title), trim(p_description), upper(trim(p_event_type)),
    p_max_capacity, coalesce(p_waitlist_enabled, true),
    nullif(trim(coalesce(p_venue_name,'')), ''),
    p_pet_id, nullif(trim(coalesce(p_pet_public_name,'')), ''),
    p_shelter_profile_id, nullif(trim(coalesce(p_shelter_public_name,'')), ''),
    nullif(trim(coalesce(p_public_location_text,'')), ''),
    p_cover_image_ref, p_starts_at, p_ends_at,
    p_check_in_opens_at, p_check_in_closes_at, v_actor
  ) returning id into v_id;
  return public._m18_internal_event_json(v_id);
end;
$$;

create or replace function public.m18_update_event_details(
  p_event_id uuid,
  p_title text,
  p_description text,
  p_event_type text,
  p_starts_at timestamptz,
  p_ends_at timestamptz,
  p_venue_name text default null,
  p_pet_id uuid default null,
  p_pet_public_name text default null,
  p_shelter_profile_id uuid default null,
  p_shelter_public_name text default null,
  p_public_location_text text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m18_community_events;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');
  if v.event_status in ('COMPLETED','CANCELLED') then raise exception 'M18_STATE_ALREADY_FINAL'; end if;
  if v.event_status not in ('DRAFT','PUBLISHED','PAUSED') then
    raise exception 'M18_INVALID_STATE_TRANSITION';
  end if;
  if p_ends_at <= p_starts_at then raise exception 'M18_INVALID_DATE_RANGE'; end if;
  update public.m18_community_events set
    title = trim(p_title), description = trim(p_description),
    event_type = upper(trim(p_event_type)),
    venue_name = nullif(trim(coalesce(p_venue_name,'')), ''),
    pet_id = p_pet_id,
    pet_public_name = nullif(trim(coalesce(p_pet_public_name,'')), ''),
    shelter_profile_id = p_shelter_profile_id,
    shelter_public_name = nullif(trim(coalesce(p_shelter_public_name,'')), ''),
    public_location_text = nullif(trim(coalesce(p_public_location_text,'')), ''),
    starts_at = p_starts_at, ends_at = p_ends_at,
    updated_at = timezone('utc', now())
  where id = p_event_id;
  return public._m18_internal_event_json(p_event_id);
end;
$$;

create or replace function public.m18_update_event_capacity(
  p_event_id uuid,
  p_max_capacity integer,
  p_waitlist_enabled boolean
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m18_community_events;
  v_occupied integer;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');
  if v.event_status in ('COMPLETED','CANCELLED') then raise exception 'M18_STATE_ALREADY_FINAL'; end if;
  if coalesce(p_max_capacity, 0) <= 0 then raise exception 'M18_INVALID_CAPACITY'; end if;
  select count(*)::integer into v_occupied
  from public.m18_event_registrations
  where event_id = p_event_id and status in ('REGISTERED','CHECKED_IN');
  if p_max_capacity < v_occupied then raise exception 'M18_CAPACITY_BELOW_REGISTERED'; end if;
  update public.m18_community_events set
    max_capacity = p_max_capacity,
    waitlist_enabled = coalesce(p_waitlist_enabled, true),
    updated_at = timezone('utc', now())
  where id = p_event_id;
  return public._m18_internal_event_json(p_event_id);
end;
$$;

create or replace function public.m18_transition_event(
  p_event_id uuid,
  p_target_status text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m18_community_events;
  v_target text := upper(trim(p_target_status));
begin
  select * into v from public.m18_community_events where id = p_event_id for update;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');
  if v.event_status = v_target then
    return public._m18_internal_event_json(p_event_id);
  end if;
  if v.event_status in ('COMPLETED','CANCELLED') then raise exception 'M18_STATE_ALREADY_FINAL'; end if;
  if v_target = 'PUBLISHED' and v.event_status not in ('DRAFT','PAUSED') then
    raise exception 'M18_INVALID_STATE_TRANSITION';
  elsif v_target = 'PAUSED' and v.event_status <> 'PUBLISHED' then
    raise exception 'M18_INVALID_STATE_TRANSITION';
  elsif v_target in ('COMPLETED','CANCELLED') and v.event_status not in ('PUBLISHED','PAUSED') then
    raise exception 'M18_INVALID_STATE_TRANSITION';
  end if;
  update public.m18_community_events set
    event_status = v_target,
    published_at = case when v_target = 'PUBLISHED' and published_at is null
      then timezone('utc', now()) else published_at end,
    completed_at = case when v_target = 'COMPLETED' then timezone('utc', now()) else completed_at end,
    cancelled_at = case when v_target = 'CANCELLED' then timezone('utc', now()) else cancelled_at end,
    updated_at = timezone('utc', now())
  where id = p_event_id;
  return public._m18_internal_event_json(p_event_id);
end;
$$;

create or replace function public.m18_register_for_event(p_event_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m18_require_authenticated();
  v public.m18_community_events;
  v_existing public.m18_event_registrations;
  v_summary jsonb;
  v_status text;
  v_id uuid;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  if v.event_status <> 'PUBLISHED' then
    if v.event_status in ('COMPLETED','CANCELLED') then raise exception 'M18_EVENT_TERMINAL';
    elsif v.event_status = 'PAUSED' then raise exception 'M18_EVENT_NOT_OPEN';
    else raise exception 'M18_EVENT_NOT_PUBLIC';
    end if;
  end if;

  select * into v_existing
  from public.m18_event_registrations
  where event_id = p_event_id and user_id = v_actor;

  if found and v_existing.status in ('REGISTERED','WAITLISTED','CHECKED_IN') then
    return public._m18_internal_registration_json(v_existing.id);
  end if;

  v_summary := public._m18_capacity_summary(p_event_id);
  if (v_summary->>'available_spots')::integer > 0 then
    v_status := 'REGISTERED';
  elsif (v_summary->>'is_waitlist_open')::boolean then
    v_status := 'WAITLISTED';
  else
    raise exception 'M18_EVENT_FULL';
  end if;

  if found then
    update public.m18_event_registrations set
      status = v_status,
      registered_at = timezone('utc', now()),
      checked_in_at = null,
      updated_at = timezone('utc', now())
    where id = v_existing.id
    returning id into v_id;
  else
    insert into public.m18_event_registrations (event_id, user_id, status)
    values (p_event_id, v_actor, v_status)
    returning id into v_id;
  end if;
  return public._m18_internal_registration_json(v_id);
end;
$$;

create or replace function public.m18_cancel_registration(p_event_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m18_require_authenticated();
  v_reg public.m18_event_registrations;
begin
  select * into v_reg
  from public.m18_event_registrations
  where event_id = p_event_id and user_id = v_actor;
  if not found then raise exception 'M18_REGISTRATION_NOT_FOUND'; end if;
  if v_reg.status in ('CANCELLED','CHECKED_IN','NO_SHOW') then
    return public._m18_internal_registration_json(v_reg.id);
  end if;
  update public.m18_event_registrations set
    status = 'CANCELLED',
    updated_at = timezone('utc', now())
  where id = v_reg.id;
  perform public._m18_promote_waitlist(p_event_id);
  return public._m18_internal_registration_json(v_reg.id);
end;
$$;

create or replace function public.m18_check_in_registration(p_registration_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_reg public.m18_event_registrations;
  v public.m18_community_events;
  v_now timestamptz := timezone('utc', now());
  v_opens timestamptz;
  v_closes timestamptz;
begin
  select * into v_reg from public.m18_event_registrations where id = p_registration_id;
  if not found then raise exception 'M18_REGISTRATION_NOT_FOUND'; end if;
  select * into v from public.m18_community_events where id = v_reg.event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');
  if v_reg.status = 'CHECKED_IN' then
    return public._m18_internal_registration_json(v_reg.id);
  end if;
  if v_reg.status <> 'REGISTERED' then raise exception 'M18_INVALID_CHECKIN_STATE'; end if;
  if v.event_status not in ('PUBLISHED','COMPLETED') then raise exception 'M18_EVENT_NOT_OPEN'; end if;
  v_opens := coalesce(v.check_in_opens_at, v.starts_at - interval '1 hour');
  v_closes := coalesce(v.check_in_closes_at, v.ends_at);
  if v_now < v_opens or v_now > v_closes then raise exception 'M18_CHECKIN_WINDOW_CLOSED'; end if;
  update public.m18_event_registrations set
    status = 'CHECKED_IN',
    checked_in_at = v_now,
    updated_at = v_now
  where id = p_registration_id;
  return public._m18_internal_registration_json(p_registration_id);
end;
$$;

create or replace function public.m18_schedule_reminder(p_event_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform public._m18_require_authenticated();
  raise exception 'M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE';
end;
$$;

create or replace function public.m18_get_my_registration(p_event_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m18_require_authenticated();
  v_reg public.m18_event_registrations;
begin
  select * into v_reg
  from public.m18_event_registrations
  where event_id = p_event_id and user_id = v_actor;
  if not found then return null; end if;
  return public._m18_internal_registration_json(v_reg.id);
end;
$$;

create or replace function public.m18_list_registrations_for_manage(p_event_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m18_community_events;
  v_row public.m18_event_registrations;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');
  for v_row in
    select * from public.m18_event_registrations
    where event_id = p_event_id
    order by registered_at desc
  loop
    return next public._m18_internal_registration_json(v_row.id);
  end loop;
end;
$$;

-- Grants RPC públicas
revoke all on function public.m18_list_public_events from public;
grant execute on function public.m18_list_public_events to anon, authenticated;
revoke all on function public.m18_get_public_event from public;
grant execute on function public.m18_get_public_event to anon, authenticated;
revoke all on function public.m18_get_public_registration_stats from public;
grant execute on function public.m18_get_public_registration_stats to anon, authenticated;
revoke all on function public.m18_get_capacity_summary from public;
grant execute on function public.m18_get_capacity_summary to anon, authenticated;
revoke all on function public.m18_is_organization_eligible from public;
grant execute on function public.m18_is_organization_eligible to authenticated;

-- Grants RPC administración
revoke all on function public.m18_get_event from public;
grant execute on function public.m18_get_event to authenticated;
revoke all on function public.m18_list_org_events from public;
grant execute on function public.m18_list_org_events to authenticated;
revoke all on function public.m18_create_event from public;
grant execute on function public.m18_create_event to authenticated;
revoke all on function public.m18_update_event_details from public;
grant execute on function public.m18_update_event_details to authenticated;
revoke all on function public.m18_update_event_capacity from public;
grant execute on function public.m18_update_event_capacity to authenticated;
revoke all on function public.m18_transition_event from public;
grant execute on function public.m18_transition_event to authenticated;
revoke all on function public.m18_register_for_event from public;
grant execute on function public.m18_register_for_event to authenticated;
revoke all on function public.m18_cancel_registration from public;
grant execute on function public.m18_cancel_registration to authenticated;
revoke all on function public.m18_check_in_registration from public;
grant execute on function public.m18_check_in_registration to authenticated;
revoke all on function public.m18_schedule_reminder from public;
grant execute on function public.m18_schedule_reminder to authenticated;
revoke all on function public.m18_get_my_registration from public;
grant execute on function public.m18_get_my_registration to authenticated;
revoke all on function public.m18_list_registrations_for_manage from public;
grant execute on function public.m18_list_registrations_for_manage to authenticated;

commit;
