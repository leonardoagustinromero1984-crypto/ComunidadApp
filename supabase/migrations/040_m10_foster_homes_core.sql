-- =============================================================================
-- LeoVer M10 — migración 040: hogares de tránsito (perfiles, solicitudes, ingresos)
-- Forward-only sobre 001–039. NO reescribe migraciones anteriores.
-- Tablas legacy foster_homes / foster_requests (006/011) quedan intactas (incompatibles).
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.foster_home_profiles (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references public.users (id) on delete cascade,
  display_name text not null,
  description text,
  status text not null default 'DRAFT',
  availability_status text not null default 'UNAVAILABLE',
  total_capacity integer not null,
  current_occupancy integer not null default 0,
  reserved_count integer not null default 0,
  accepted_species text[] not null default '{}',
  accepted_sizes text[] not null default '{}',
  accepts_special_needs boolean not null default false,
  accepts_emergencies boolean not null default false,
  has_other_animals boolean,
  observations text,
  zone_text text not null,
  public_location_text text,
  private_address_text text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint foster_home_profiles_status_chk
    check (status = any (array['DRAFT','ACTIVE','PAUSED','SUSPENDED','CLOSED']::text[])),
  constraint foster_home_profiles_availability_chk
    check (availability_status = any (array['AVAILABLE','LIMITED','FULL','UNAVAILABLE']::text[])),
  constraint foster_home_profiles_capacity_chk
    check (total_capacity > 0),
  constraint foster_home_profiles_occupancy_chk
    check (current_occupancy >= 0 and reserved_count >= 0
      and current_occupancy + reserved_count <= total_capacity)
);

create unique index if not exists foster_home_profiles_one_open_per_owner_uidx
  on public.foster_home_profiles (owner_user_id)
  where status <> 'CLOSED';

create index if not exists foster_home_profiles_owner_idx
  on public.foster_home_profiles (owner_user_id);
create index if not exists foster_home_profiles_status_idx
  on public.foster_home_profiles (status);
create index if not exists foster_home_profiles_availability_idx
  on public.foster_home_profiles (availability_status);

-- Solicitudes M10 (nombre foster_care_requests: evita choque con legacy foster_requests)
create table if not exists public.foster_care_requests (
  id uuid primary key default gen_random_uuid(),
  foster_home_id uuid not null references public.foster_home_profiles (id) on delete cascade,
  pet_id uuid not null references public.pets (id) on delete restrict,
  requester_user_id uuid references public.users (id) on delete set null,
  requester_organization_id uuid,
  message text not null,
  urgency text not null default 'NORMAL',
  requested_start_at timestamptz,
  estimated_end_at timestamptz,
  special_needs text,
  status text not null default 'SUBMITTED',
  created_at timestamptz not null default timezone('utc', now()),
  reviewed_at timestamptz,
  reviewed_by uuid references public.users (id),
  rejection_reason text,
  constraint foster_care_requests_urgency_chk
    check (urgency = any (array['NORMAL','HIGH','EMERGENCY']::text[])),
  constraint foster_care_requests_status_chk
    check (status = any (array['SUBMITTED','UNDER_REVIEW','ACCEPTED','REJECTED','CANCELLED','EXPIRED']::text[])),
  constraint foster_care_requests_actor_chk
    check (requester_user_id is not null or requester_organization_id is not null)
);

create unique index if not exists foster_care_requests_one_active_uidx
  on public.foster_care_requests (foster_home_id, pet_id)
  where status = any (array['SUBMITTED','UNDER_REVIEW','ACCEPTED']::text[]);

create index if not exists foster_care_requests_home_idx on public.foster_care_requests (foster_home_id);
create index if not exists foster_care_requests_pet_idx on public.foster_care_requests (pet_id);
create index if not exists foster_care_requests_requester_idx on public.foster_care_requests (requester_user_id);
create index if not exists foster_care_requests_status_idx on public.foster_care_requests (status);

create table if not exists public.foster_placements (
  id uuid primary key default gen_random_uuid(),
  foster_request_id uuid not null references public.foster_care_requests (id) on delete restrict,
  foster_home_id uuid not null references public.foster_home_profiles (id) on delete cascade,
  pet_id uuid not null references public.pets (id) on delete restrict,
  requester_user_id uuid references public.users (id),
  requester_organization_id uuid,
  foster_user_id uuid not null references public.users (id),
  status text not null default 'RESERVED',
  started_at timestamptz not null default timezone('utc', now()),
  estimated_end_at timestamptz,
  ended_at timestamptz,
  initial_notes text,
  end_reason text,
  temporary_responsibility_id uuid,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint foster_placements_status_chk
    check (status = any (array['RESERVED','ACTIVE','COMPLETED','CANCELLED']::text[])),
  constraint foster_placements_one_request unique (foster_request_id)
);

create unique index if not exists foster_placements_one_active_pet_uidx
  on public.foster_placements (pet_id)
  where status = any (array['RESERVED','ACTIVE']::text[]);

create index if not exists foster_placements_home_idx on public.foster_placements (foster_home_id);
create index if not exists foster_placements_pet_idx on public.foster_placements (pet_id);
create index if not exists foster_placements_foster_user_idx on public.foster_placements (foster_user_id);
create index if not exists foster_placements_status_idx on public.foster_placements (status);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m10_require_authenticated()
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return v_uid;
end;
$$;

create or replace function public._m10_recompute_availability(
  p_status text,
  p_capacity integer,
  p_occupancy integer,
  p_reserved integer
) returns text
language sql
immutable
as $$
  select case
    when p_status is distinct from 'ACTIVE' then 'UNAVAILABLE'
    when greatest(coalesce(p_occupancy, 0), 0) + greatest(coalesce(p_reserved, 0), 0)
         >= greatest(coalesce(p_capacity, 0), 0) then 'FULL'
    when greatest(coalesce(p_occupancy, 0), 0) + greatest(coalesce(p_reserved, 0), 0) > 0
         then 'LIMITED'
    else 'AVAILABLE'
  end;
$$;

create or replace function public._m10_actor_can_manage_pet(p_pet_id uuid, p_actor uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_pet_id is null or p_actor is null then
    return false;
  end if;
  if exists (
    select 1 from public.pet_responsibilities r
    where r.pet_id = p_pet_id
      and r.person_id = p_actor
      and r.role_code = 'PRINCIPAL'
      and r.status = 'ACTIVE'
  ) then
    return true;
  end if;
  -- Organización gestora (M03): miembro activo de org con responsabilidad PRINCIPAL org
  if exists (
    select 1
    from public.pet_responsibilities r
    join public.organization_memberships m
      on m.organization_id = r.organization_id
     and m.user_id = p_actor
     and m.status = 'ACTIVE'
    where r.pet_id = p_pet_id
      and r.role_code = 'PRINCIPAL'
      and r.status = 'ACTIVE'
      and r.organization_id is not null
  ) then
    return true;
  end if;
  return false;
exception
  when undefined_table then
    return exists (
      select 1 from public.pets p where p.id = p_pet_id and p.owner_id = p_actor
    );
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. RLS
-- ---------------------------------------------------------------------------
alter table public.foster_home_profiles enable row level security;
alter table public.foster_care_requests enable row level security;
alter table public.foster_placements enable row level security;

drop policy if exists foster_home_profiles_select_m10 on public.foster_home_profiles;
drop policy if exists foster_home_profiles_ins_m10 on public.foster_home_profiles;
drop policy if exists foster_home_profiles_upd_m10 on public.foster_home_profiles;
drop policy if exists foster_home_profiles_del_m10 on public.foster_home_profiles;

create policy foster_home_profiles_select_m10
  on public.foster_home_profiles for select to authenticated
  using (
    status = 'ACTIVE'
    or owner_user_id = auth.uid()
  );

create policy foster_home_profiles_ins_m10
  on public.foster_home_profiles for insert to authenticated with check (false);
create policy foster_home_profiles_upd_m10
  on public.foster_home_profiles for update to authenticated using (false);
create policy foster_home_profiles_del_m10
  on public.foster_home_profiles for delete to authenticated using (false);

drop policy if exists foster_care_requests_select_m10 on public.foster_care_requests;
drop policy if exists foster_care_requests_ins_m10 on public.foster_care_requests;
drop policy if exists foster_care_requests_upd_m10 on public.foster_care_requests;
drop policy if exists foster_care_requests_del_m10 on public.foster_care_requests;

create policy foster_care_requests_select_m10
  on public.foster_care_requests for select to authenticated
  using (
    requester_user_id = auth.uid()
    or exists (
      select 1 from public.foster_home_profiles h
      where h.id = foster_home_id and h.owner_user_id = auth.uid()
    )
  );

create policy foster_care_requests_ins_m10
  on public.foster_care_requests for insert to authenticated with check (false);
create policy foster_care_requests_upd_m10
  on public.foster_care_requests for update to authenticated using (false);
create policy foster_care_requests_del_m10
  on public.foster_care_requests for delete to authenticated using (false);

drop policy if exists foster_placements_select_m10 on public.foster_placements;
drop policy if exists foster_placements_ins_m10 on public.foster_placements;
drop policy if exists foster_placements_upd_m10 on public.foster_placements;
drop policy if exists foster_placements_del_m10 on public.foster_placements;

create policy foster_placements_select_m10
  on public.foster_placements for select to authenticated
  using (
    foster_user_id = auth.uid()
    or requester_user_id = auth.uid()
    or exists (
      select 1 from public.foster_home_profiles h
      where h.id = foster_home_id and h.owner_user_id = auth.uid()
    )
  );

create policy foster_placements_ins_m10
  on public.foster_placements for insert to authenticated with check (false);
create policy foster_placements_upd_m10
  on public.foster_placements for update to authenticated using (false);
create policy foster_placements_del_m10
  on public.foster_placements for delete to authenticated using (false);

revoke all on table public.foster_home_profiles from public, anon;
revoke all on table public.foster_care_requests from public, anon;
revoke all on table public.foster_placements from public, anon;
grant select on table public.foster_home_profiles to authenticated;
grant select on table public.foster_care_requests to authenticated;
grant select on table public.foster_placements to authenticated;
grant all on table public.foster_home_profiles to service_role;
grant all on table public.foster_care_requests to service_role;
grant all on table public.foster_placements to service_role;

-- ---------------------------------------------------------------------------
-- 4. RPCs perfiles
-- ---------------------------------------------------------------------------
create or replace function public.m10_create_foster_home(
  p_display_name text,
  p_description text default null,
  p_total_capacity integer,
  p_accepted_species text[] default '{}',
  p_accepted_sizes text[] default '{}',
  p_accepts_special_needs boolean default false,
  p_accepts_emergencies boolean default false,
  p_has_other_animals boolean default null,
  p_observations text default null,
  p_zone_text text,
  p_public_location_text text default null,
  p_private_address_text text default null,
  p_activate boolean default false
) returns public.foster_home_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_home_profiles;
  v_status text := case when p_activate then 'ACTIVE' else 'DRAFT' end;
begin
  if coalesce(p_total_capacity, 0) <= 0 then
    raise exception 'FOSTER_HOME_CAPACITY_INVALID';
  end if;
  if coalesce(trim(p_display_name), '') = '' or coalesce(trim(p_zone_text), '') = '' then
    raise exception 'FOSTER_HOME_NOT_FOUND';
  end if;
  if exists (
    select 1 from public.foster_home_profiles h
    where h.owner_user_id = v_actor and h.status <> 'CLOSED'
  ) then
    raise exception 'FOSTER_HOME_ALREADY_EXISTS';
  end if;

  insert into public.foster_home_profiles (
    owner_user_id, display_name, description, status, availability_status,
    total_capacity, accepted_species, accepted_sizes, accepts_special_needs,
    accepts_emergencies, has_other_animals, observations, zone_text,
    public_location_text, private_address_text
  ) values (
    v_actor, trim(p_display_name), nullif(trim(coalesce(p_description, '')), ''),
    v_status,
    public._m10_recompute_availability(v_status, p_total_capacity, 0, 0),
    p_total_capacity,
    coalesce(p_accepted_species, '{}'),
    coalesce(p_accepted_sizes, '{}'),
    coalesce(p_accepts_special_needs, false),
    coalesce(p_accepts_emergencies, false),
    p_has_other_animals,
    nullif(trim(coalesce(p_observations, '')), ''),
    trim(p_zone_text),
    nullif(trim(coalesce(p_public_location_text, '')), ''),
    nullif(trim(coalesce(p_private_address_text, '')), '')
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_update_foster_home(
  p_home_id uuid,
  p_display_name text,
  p_description text default null,
  p_total_capacity integer,
  p_accepted_species text[] default '{}',
  p_accepted_sizes text[] default '{}',
  p_accepts_special_needs boolean default false,
  p_accepts_emergencies boolean default false,
  p_has_other_animals boolean default null,
  p_observations text default null,
  p_zone_text text,
  p_public_location_text text default null,
  p_private_address_text text default null
) returns public.foster_home_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_home_profiles;
begin
  if p_home_id is null then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if coalesce(p_total_capacity, 0) <= 0 then raise exception 'FOSTER_HOME_CAPACITY_INVALID'; end if;
  select * into v_row from public.foster_home_profiles where id = p_home_id for update;
  if not found then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if v_row.owner_user_id <> v_actor then raise exception 'FORBIDDEN'; end if;
  if v_row.status = 'CLOSED' then raise exception 'FOSTER_HOME_NOT_ACTIVE'; end if;
  if p_total_capacity < (v_row.current_occupancy + v_row.reserved_count) then
    raise exception 'FOSTER_PLACEMENT_CAPACITY_EXCEEDED';
  end if;

  update public.foster_home_profiles set
    display_name = trim(p_display_name),
    description = nullif(trim(coalesce(p_description, '')), ''),
    total_capacity = p_total_capacity,
    accepted_species = coalesce(p_accepted_species, '{}'),
    accepted_sizes = coalesce(p_accepted_sizes, '{}'),
    accepts_special_needs = coalesce(p_accepts_special_needs, false),
    accepts_emergencies = coalesce(p_accepts_emergencies, false),
    has_other_animals = p_has_other_animals,
    observations = nullif(trim(coalesce(p_observations, '')), ''),
    zone_text = trim(p_zone_text),
    public_location_text = nullif(trim(coalesce(p_public_location_text, '')), ''),
    private_address_text = nullif(trim(coalesce(p_private_address_text, '')), ''),
    availability_status = public._m10_recompute_availability(
      status, p_total_capacity, current_occupancy, reserved_count
    ),
    updated_at = timezone('utc', now())
  where id = p_home_id
  returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_change_foster_availability(
  p_home_id uuid,
  p_availability text
) returns public.foster_home_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_home_profiles;
begin
  if p_home_id is null then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if upper(trim(p_availability)) not in ('AVAILABLE','LIMITED','FULL','UNAVAILABLE') then
    raise exception 'FOSTER_HOME_UNAVAILABLE';
  end if;
  select * into v_row from public.foster_home_profiles where id = p_home_id for update;
  if not found then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if v_row.owner_user_id <> v_actor then raise exception 'FORBIDDEN'; end if;
  update public.foster_home_profiles set
    availability_status = upper(trim(p_availability)),
    updated_at = timezone('utc', now())
  where id = p_home_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_set_foster_home_status(
  p_home_id uuid,
  p_status text
) returns public.foster_home_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_home_profiles;
  v_status text := upper(trim(p_status));
begin
  if p_home_id is null then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if v_status not in ('DRAFT','ACTIVE','PAUSED','SUSPENDED','CLOSED') then
    raise exception 'FOSTER_HOME_NOT_ACTIVE';
  end if;
  select * into v_row from public.foster_home_profiles where id = p_home_id for update;
  if not found then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if v_row.owner_user_id <> v_actor then raise exception 'FORBIDDEN'; end if;
  update public.foster_home_profiles set
    status = v_status,
    availability_status = public._m10_recompute_availability(
      v_status, total_capacity, current_occupancy, reserved_count
    ),
    updated_at = timezone('utc', now())
  where id = p_home_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_list_available_foster_homes()
returns setof public.foster_home_profiles
language plpgsql stable security definer set search_path = public as $$
begin
  perform public._m10_require_authenticated();
  return query
  select h.* from public.foster_home_profiles h
  where h.status = 'ACTIVE'
  order by h.updated_at desc;
end;
$$;

create or replace function public.m10_get_foster_home(p_home_id uuid)
returns public.foster_home_profiles
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_home_profiles;
begin
  if p_home_id is null then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  select * into v_row from public.foster_home_profiles where id = p_home_id;
  if not found then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if v_row.status <> 'ACTIVE' and v_row.owner_user_id <> v_actor then
    raise exception 'FOSTER_HOME_NOT_FOUND';
  end if;
  return v_row;
end;
$$;

create or replace function public.m10_get_my_foster_home()
returns public.foster_home_profiles
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_home_profiles;
begin
  select * into v_row from public.foster_home_profiles
  where owner_user_id = v_actor and status <> 'CLOSED'
  order by created_at desc
  limit 1;
  if not found then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs solicitudes
-- ---------------------------------------------------------------------------
create or replace function public.m10_submit_foster_request(
  p_foster_home_id uuid,
  p_pet_id uuid,
  p_message text,
  p_urgency text default 'NORMAL',
  p_requested_start_at timestamptz default null,
  p_estimated_end_at timestamptz default null,
  p_special_needs text default null,
  p_requester_organization_id uuid default null
) returns public.foster_care_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_home public.foster_home_profiles;
  v_pet public.pets;
  v_urgency text := upper(trim(coalesce(p_urgency, 'NORMAL')));
  v_row public.foster_care_requests;
begin
  if p_foster_home_id is null or p_pet_id is null then
    raise exception 'FOSTER_REQUEST_NOT_FOUND';
  end if;
  if coalesce(trim(p_message), '') = '' then
    raise exception 'FOSTER_REQUEST_INVALID_TRANSITION';
  end if;
  select * into v_home from public.foster_home_profiles where id = p_foster_home_id for update;
  if not found then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if v_home.status <> 'ACTIVE' then raise exception 'FOSTER_HOME_NOT_ACTIVE'; end if;
  if v_home.availability_status in ('UNAVAILABLE', 'FULL') then
    raise exception 'FOSTER_HOME_UNAVAILABLE';
  end if;
  if (v_home.total_capacity - v_home.current_occupancy - v_home.reserved_count) <= 0 then
    raise exception 'FOSTER_HOME_FULL';
  end if;

  select * into v_pet from public.pets where id = p_pet_id;
  if not found then raise exception 'PET_NOT_FOUND'; end if;
  if v_pet.status in ('DECEASED', 'ARCHIVED') then
    raise exception 'PET_NOT_ELIGIBLE_FOR_FOSTER';
  end if;
  if not public._m10_actor_can_manage_pet(p_pet_id, v_actor) then
    raise exception 'FORBIDDEN';
  end if;
  if cardinality(v_home.accepted_species) > 0
     and not (upper(v_pet.species) = any (select upper(x) from unnest(v_home.accepted_species) x)) then
    raise exception 'FOSTER_HOME_INCOMPATIBLE';
  end if;
  if cardinality(v_home.accepted_sizes) > 0
     and not (upper(v_pet.size) = any (select upper(x) from unnest(v_home.accepted_sizes) x)) then
    raise exception 'FOSTER_HOME_INCOMPATIBLE';
  end if;
  if v_urgency = 'EMERGENCY' and not v_home.accepts_emergencies then
    raise exception 'FOSTER_HOME_INCOMPATIBLE';
  end if;
  if coalesce(trim(p_special_needs), '') <> '' and not v_home.accepts_special_needs then
    raise exception 'FOSTER_HOME_INCOMPATIBLE';
  end if;
  if exists (
    select 1 from public.foster_placements p
    where p.pet_id = p_pet_id and p.status in ('RESERVED', 'ACTIVE')
  ) then
    raise exception 'PET_ALREADY_IN_FOSTER';
  end if;
  if exists (
    select 1 from public.foster_care_requests r
    where r.foster_home_id = p_foster_home_id and r.pet_id = p_pet_id
      and r.status in ('SUBMITTED', 'UNDER_REVIEW', 'ACCEPTED')
  ) then
    raise exception 'FOSTER_REQUEST_ALREADY_EXISTS';
  end if;

  insert into public.foster_care_requests (
    foster_home_id, pet_id, requester_user_id, requester_organization_id,
    message, urgency, requested_start_at, estimated_end_at, special_needs, status
  ) values (
    p_foster_home_id, p_pet_id, v_actor, p_requester_organization_id,
    trim(p_message), v_urgency, p_requested_start_at, p_estimated_end_at,
    nullif(trim(coalesce(p_special_needs, '')), ''), 'SUBMITTED'
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_cancel_foster_request(p_request_id uuid)
returns public.foster_care_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_care_requests;
begin
  if p_request_id is null then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  select * into v_row from public.foster_care_requests where id = p_request_id for update;
  if not found then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  if v_row.requester_user_id is distinct from v_actor then
    raise exception 'FOSTER_REQUEST_FORBIDDEN';
  end if;
  if v_row.status = 'CANCELLED' then return v_row; end if;
  if v_row.status not in ('SUBMITTED', 'UNDER_REVIEW') then
    raise exception 'FOSTER_REQUEST_NOT_ACTIVE';
  end if;
  update public.foster_care_requests set
    status = 'CANCELLED', reviewed_at = timezone('utc', now()), reviewed_by = v_actor
  where id = p_request_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_mark_foster_request_under_review(p_request_id uuid)
returns public.foster_care_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_care_requests;
  v_home public.foster_home_profiles;
begin
  select * into v_row from public.foster_care_requests where id = p_request_id for update;
  if not found then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  select * into v_home from public.foster_home_profiles where id = v_row.foster_home_id;
  if v_home.owner_user_id <> v_actor then raise exception 'FOSTER_REQUEST_FORBIDDEN'; end if;
  if v_row.status = 'UNDER_REVIEW' then return v_row; end if;
  if v_row.status <> 'SUBMITTED' then raise exception 'FOSTER_REQUEST_INVALID_TRANSITION'; end if;
  update public.foster_care_requests set
    status = 'UNDER_REVIEW', reviewed_at = timezone('utc', now()), reviewed_by = v_actor
  where id = p_request_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_accept_foster_request(p_request_id uuid)
returns public.foster_care_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_care_requests;
  v_home public.foster_home_profiles;
begin
  select * into v_row from public.foster_care_requests where id = p_request_id for update;
  if not found then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  select * into v_home from public.foster_home_profiles where id = v_row.foster_home_id for update;
  if v_home.owner_user_id <> v_actor then raise exception 'FOSTER_REQUEST_FORBIDDEN'; end if;
  if v_row.status = 'ACCEPTED' then return v_row; end if;
  if v_row.status not in ('SUBMITTED', 'UNDER_REVIEW') then
    raise exception 'FOSTER_REQUEST_INVALID_TRANSITION';
  end if;
  if (v_home.total_capacity - v_home.current_occupancy - v_home.reserved_count) <= 0 then
    raise exception 'FOSTER_HOME_FULL';
  end if;

  update public.foster_care_requests set
    status = 'ACCEPTED', reviewed_at = timezone('utc', now()), reviewed_by = v_actor
  where id = p_request_id returning * into v_row;

  update public.foster_home_profiles set
    reserved_count = reserved_count + 1,
    availability_status = public._m10_recompute_availability(
      status, total_capacity, current_occupancy, reserved_count + 1
    ),
    updated_at = timezone('utc', now())
  where id = v_home.id;

  insert into public.foster_placements (
    foster_request_id, foster_home_id, pet_id, requester_user_id,
    requester_organization_id, foster_user_id, status, estimated_end_at
  ) values (
    v_row.id, v_home.id, v_row.pet_id, v_row.requester_user_id,
    v_row.requester_organization_id, v_home.owner_user_id, 'RESERVED', v_row.estimated_end_at
  );

  return v_row;
end;
$$;

create or replace function public.m10_reject_foster_request(
  p_request_id uuid,
  p_rejection_reason text default null
) returns public.foster_care_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_care_requests;
  v_home public.foster_home_profiles;
begin
  select * into v_row from public.foster_care_requests where id = p_request_id for update;
  if not found then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  select * into v_home from public.foster_home_profiles where id = v_row.foster_home_id;
  if v_home.owner_user_id <> v_actor then raise exception 'FOSTER_REQUEST_FORBIDDEN'; end if;
  if v_row.status = 'REJECTED' then return v_row; end if;
  if v_row.status not in ('SUBMITTED', 'UNDER_REVIEW') then
    raise exception 'FOSTER_REQUEST_INVALID_TRANSITION';
  end if;
  update public.foster_care_requests set
    status = 'REJECTED',
    rejection_reason = nullif(trim(coalesce(p_rejection_reason, '')), ''),
    reviewed_at = timezone('utc', now()),
    reviewed_by = v_actor
  where id = p_request_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_list_sent_foster_requests()
returns setof public.foster_care_requests
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
begin
  return query
  select r.* from public.foster_care_requests r
  where r.requester_user_id = v_actor
  order by r.created_at desc;
end;
$$;

create or replace function public.m10_list_received_foster_requests()
returns setof public.foster_care_requests
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
begin
  return query
  select r.* from public.foster_care_requests r
  join public.foster_home_profiles h on h.id = r.foster_home_id
  where h.owner_user_id = v_actor
  order by r.created_at desc;
end;
$$;

create or replace function public.m10_get_foster_request(p_request_id uuid)
returns public.foster_care_requests
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_care_requests;
  v_home public.foster_home_profiles;
begin
  if p_request_id is null then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  select * into v_row from public.foster_care_requests where id = p_request_id;
  if not found then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  select * into v_home from public.foster_home_profiles where id = v_row.foster_home_id;
  if v_row.requester_user_id is distinct from v_actor
     and v_home.owner_user_id is distinct from v_actor then
    raise exception 'FOSTER_REQUEST_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Ingreso transaccional
-- ---------------------------------------------------------------------------
create or replace function public.m10_start_foster_placement(
  p_request_id uuid,
  p_initial_notes text default null
) returns public.foster_placements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_req public.foster_care_requests;
  v_home public.foster_home_profiles;
  v_place public.foster_placements;
  v_resp_id uuid;
  v_now timestamptz := timezone('utc', now());
begin
  if p_request_id is null then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  select * into v_req from public.foster_care_requests where id = p_request_id for update;
  if not found then raise exception 'FOSTER_REQUEST_NOT_FOUND'; end if;
  if v_req.status <> 'ACCEPTED' then raise exception 'FOSTER_REQUEST_INVALID_TRANSITION'; end if;

  select * into v_home from public.foster_home_profiles where id = v_req.foster_home_id for update;
  if not found then raise exception 'FOSTER_HOME_NOT_FOUND'; end if;
  if v_home.owner_user_id <> v_actor then raise exception 'FORBIDDEN'; end if;

  select * into v_place from public.foster_placements
  where foster_request_id = p_request_id for update;
  if found and v_place.status = 'ACTIVE' then
    return v_place; -- idempotente
  end if;

  if exists (
    select 1 from public.foster_placements p
    where p.pet_id = v_req.pet_id and p.status = 'ACTIVE'
      and (v_place.id is null or p.id <> v_place.id)
  ) then
    raise exception 'FOSTER_PLACEMENT_ALREADY_ACTIVE';
  end if;

  if v_home.current_occupancy + 1 + greatest(v_home.reserved_count - 1, 0) > v_home.total_capacity then
    raise exception 'FOSTER_PLACEMENT_CAPACITY_EXCEEDED';
  end if;

  -- Custodia temporal M08 (sin transferir PRINCIPAL)
  insert into public.pet_responsibilities (
    pet_id, role_code, person_id, organization_id, status,
    starts_at, ends_at, created_by, accepted_at, reason
  ) values (
    v_req.pet_id, 'TEMPORARY_CUSTODIAN', v_home.owner_user_id, null, 'ACTIVE',
    v_now, coalesce(v_req.estimated_end_at, v_now + interval '90 days'),
    v_actor, v_now, 'FOSTER_PLACEMENT_STARTED'
  ) returning id into v_resp_id;

  if v_place.id is null then
    insert into public.foster_placements (
      foster_request_id, foster_home_id, pet_id, requester_user_id,
      requester_organization_id, foster_user_id, status, started_at,
      estimated_end_at, initial_notes, temporary_responsibility_id
    ) values (
      v_req.id, v_home.id, v_req.pet_id, v_req.requester_user_id,
      v_req.requester_organization_id, v_home.owner_user_id, 'ACTIVE', v_now,
      v_req.estimated_end_at, nullif(trim(coalesce(p_initial_notes, '')), ''), v_resp_id
    ) returning * into v_place;
  else
    update public.foster_placements set
      status = 'ACTIVE',
      started_at = v_now,
      initial_notes = nullif(trim(coalesce(p_initial_notes, '')), ''),
      temporary_responsibility_id = v_resp_id,
      updated_at = v_now
    where id = v_place.id
    returning * into v_place;
  end if;

  update public.foster_home_profiles set
    current_occupancy = current_occupancy + 1,
    reserved_count = greatest(reserved_count - 1, 0),
    availability_status = public._m10_recompute_availability(
      status, total_capacity, current_occupancy + 1, greatest(reserved_count - 1, 0)
    ),
    updated_at = v_now
  where id = v_home.id;

  return v_place;
end;
$$;

create or replace function public.m10_list_active_foster_placements(p_home_id uuid default null)
returns setof public.foster_placements
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
begin
  return query
  select p.* from public.foster_placements p
  where p.status in ('RESERVED', 'ACTIVE')
    and (p_home_id is null or p.foster_home_id = p_home_id)
    and (
      p.foster_user_id = v_actor
      or p.requester_user_id = v_actor
      or exists (
        select 1 from public.foster_home_profiles h
        where h.id = p.foster_home_id and h.owner_user_id = v_actor
      )
    )
  order by p.started_at desc;
end;
$$;

create or replace function public.m10_get_foster_placement(p_placement_id uuid)
returns public.foster_placements
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_placements;
begin
  if p_placement_id is null then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  select * into v_row from public.foster_placements where id = p_placement_id;
  if not found then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  if v_row.foster_user_id is distinct from v_actor
     and v_row.requester_user_id is distinct from v_actor then
    raise exception 'FORBIDDEN';
  end if;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. Grants
-- ---------------------------------------------------------------------------
revoke all on function public._m10_require_authenticated() from public;
revoke all on function public._m10_recompute_availability(text, integer, integer, integer) from public;
revoke all on function public._m10_actor_can_manage_pet(uuid, uuid) from public;
revoke all on function public.m10_create_foster_home(text, text, integer, text[], text[], boolean, boolean, boolean, text, text, text, text, boolean) from public;
revoke all on function public.m10_update_foster_home(uuid, text, text, integer, text[], text[], boolean, boolean, boolean, text, text, text, text) from public;
revoke all on function public.m10_change_foster_availability(uuid, text) from public;
revoke all on function public.m10_set_foster_home_status(uuid, text) from public;
revoke all on function public.m10_list_available_foster_homes() from public;
revoke all on function public.m10_get_foster_home(uuid) from public;
revoke all on function public.m10_get_my_foster_home() from public;
revoke all on function public.m10_submit_foster_request(uuid, uuid, text, text, timestamptz, timestamptz, text, uuid) from public;
revoke all on function public.m10_cancel_foster_request(uuid) from public;
revoke all on function public.m10_mark_foster_request_under_review(uuid) from public;
revoke all on function public.m10_accept_foster_request(uuid) from public;
revoke all on function public.m10_reject_foster_request(uuid, text) from public;
revoke all on function public.m10_list_sent_foster_requests() from public;
revoke all on function public.m10_list_received_foster_requests() from public;
revoke all on function public.m10_get_foster_request(uuid) from public;
revoke all on function public.m10_start_foster_placement(uuid, text) from public;
revoke all on function public.m10_list_active_foster_placements(uuid) from public;
revoke all on function public.m10_get_foster_placement(uuid) from public;

grant execute on function public.m10_create_foster_home(text, text, integer, text[], text[], boolean, boolean, boolean, text, text, text, text, boolean) to authenticated;
grant execute on function public.m10_update_foster_home(uuid, text, text, integer, text[], text[], boolean, boolean, boolean, text, text, text, text) to authenticated;
grant execute on function public.m10_change_foster_availability(uuid, text) to authenticated;
grant execute on function public.m10_set_foster_home_status(uuid, text) to authenticated;
grant execute on function public.m10_list_available_foster_homes() to authenticated;
grant execute on function public.m10_get_foster_home(uuid) to authenticated;
grant execute on function public.m10_get_my_foster_home() to authenticated;
grant execute on function public.m10_submit_foster_request(uuid, uuid, text, text, timestamptz, timestamptz, text, uuid) to authenticated;
grant execute on function public.m10_cancel_foster_request(uuid) to authenticated;
grant execute on function public.m10_mark_foster_request_under_review(uuid) to authenticated;
grant execute on function public.m10_accept_foster_request(uuid) to authenticated;
grant execute on function public.m10_reject_foster_request(uuid, text) to authenticated;
grant execute on function public.m10_list_sent_foster_requests() to authenticated;
grant execute on function public.m10_list_received_foster_requests() to authenticated;
grant execute on function public.m10_get_foster_request(uuid) to authenticated;
grant execute on function public.m10_start_foster_placement(uuid, text) to authenticated;
grant execute on function public.m10_list_active_foster_placements(uuid) to authenticated;
grant execute on function public.m10_get_foster_placement(uuid) to authenticated;

comment on table public.foster_home_profiles is
  'M10: perfiles de hogar de tránsito. Listado público omite private_address_text.';
comment on table public.foster_care_requests is
  'M10: solicitudes de tránsito (legacy foster_requests de 011 permanece separado).';
comment on function public.m10_start_foster_placement(uuid, text) is
  'M10: ingreso — reserva→ACTIVE, ocupación, TEMPORARY_CUSTODIAN M08 sin tocar PRINCIPAL.';

commit;
