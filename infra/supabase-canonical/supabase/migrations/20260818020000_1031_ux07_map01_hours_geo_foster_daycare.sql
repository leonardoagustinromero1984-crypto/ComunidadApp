-- LeoVer Canonical
-- Logical migration: 1031
-- UX-07 + MAP-01: structured service hours, public premises geo,
-- foster direct placement, daycare guests vs reservations.
-- Forward-only. Do not edit 1000-1030.
-- Staging only.

-- ---------------------------------------------------------------------------
-- Provider holder ACL (ActiveContext is never authority)
-- ---------------------------------------------------------------------------

create or replace function public._acl_provider_holder(p_user uuid, p_provider uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.service_providers sp
    where sp.id = p_provider
      and (
        (sp.holder_kind = 'PERSON' and sp.holder_person_id = p_user)
        or (
          sp.holder_kind = 'ORGANIZATION'
          and sp.holder_organization_id is not null
          and public._acl_org_member(p_user, sp.holder_organization_id)
        )
      )
  );
$$;

revoke all on function public._acl_provider_holder(uuid, uuid) from public, anon;
grant execute on function public._acl_provider_holder(uuid, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Public premises coordinates (never private home as public pin)
-- ---------------------------------------------------------------------------

alter table public.service_providers
  add column if not exists public_geo extensions.geography(Point, 4326) null;

alter table public.service_providers
  add column if not exists geo_is_public_premises boolean not null default false;

alter table public.service_providers
  add column if not exists public_address_text text null;

create index if not exists service_providers_public_geo_gix
  on public.service_providers using gist (public_geo)
  where geo_is_public_premises and public_geo is not null;

alter table public.provider_coverage_areas
  add column if not exists service_radius_m integer null
    check (service_radius_m is null or (service_radius_m > 0 and service_radius_m <= 50000));

-- ---------------------------------------------------------------------------
-- Structured weekly hours (free-text is never authority)
-- weekday: ISO 1=Monday .. 7=Sunday
-- ---------------------------------------------------------------------------

create table if not exists public.provider_weekly_hours (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.service_providers(id) on delete cascade,
  weekday smallint not null check (weekday between 1 and 7),
  closed boolean not null default true,
  opens_at time null,
  closes_at time null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  unique (provider_id, weekday),
  constraint provider_weekly_hours_open_window check (
    (closed and opens_at is null and closes_at is null)
    or (not closed and opens_at is not null and closes_at is not null and opens_at < closes_at)
  )
);

alter table public.provider_weekly_hours enable row level security;

-- ---------------------------------------------------------------------------
-- Geo write — confirmed map pin only. No geocoding.
-- ---------------------------------------------------------------------------

create or replace function public.canon_set_provider_public_geo(
  p_provider uuid,
  p_lat double precision,
  p_lng double precision,
  p_public_premises boolean,
  p_address text default null
)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_mobile boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_provider is null then raise exception 'PROVIDER_REQUIRED'; end if;
  if not public._acl_provider_holder(auth.uid(), p_provider) then
    raise exception 'PROVIDER_HOLDER_FORBIDDEN';
  end if;
  if p_lat is null or p_lng is null
     or p_lat < -90 or p_lat > 90
     or p_lng < -180 or p_lng > 180 then
    raise exception 'PROVIDER_GEO_INVALID';
  end if;

  select exists (
    select 1
    from public.service_offerings o
    where o.provider_id = p_provider
      and o.active
      and o.category_code in ('WALKING', 'TRAINING', 'CARE')
  ) and not exists (
    select 1
    from public.service_offerings o
    where o.provider_id = p_provider
      and o.active
      and o.category_code in ('VETERINARY', 'BOARDING', 'GROOMING', 'SHOP')
  ) into v_mobile;

  if coalesce(p_public_premises, false) and coalesce(v_mobile, false) then
    raise exception 'MOBILE_PROVIDER_HOME_NOT_PUBLIC';
  end if;

  if not coalesce(p_public_premises, false) then
    update public.service_providers
       set public_geo = null,
           geo_is_public_premises = false,
           public_address_text = nullif(btrim(coalesce(p_address, '')), '')
     where id = p_provider;
    return;
  end if;

  update public.service_providers
     set public_geo = ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::extensions.geography,
         geo_is_public_premises = true,
         public_address_text = nullif(btrim(coalesce(p_address, '')), '')
   where id = p_provider;
end;
$$;

-- ---------------------------------------------------------------------------
-- Structured hours write / read
-- p_days jsonb: [{weekday, closed, opens_at, closes_at}, ...]
-- ---------------------------------------------------------------------------

create or replace function public.canon_set_provider_weekly_hours(
  p_provider uuid,
  p_days jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_day jsonb;
  v_weekday smallint;
  v_closed boolean;
  v_open time;
  v_close time;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_provider is null then raise exception 'PROVIDER_REQUIRED'; end if;
  if not public._acl_provider_holder(auth.uid(), p_provider) then
    raise exception 'PROVIDER_HOLDER_FORBIDDEN';
  end if;
  if p_days is null or jsonb_typeof(p_days) <> 'array' then
    raise exception 'PROVIDER_HOURS_INVALID';
  end if;

  delete from public.provider_weekly_hours where provider_id = p_provider;

  for v_day in select value from jsonb_array_elements(p_days)
  loop
    v_weekday := (v_day->>'weekday')::smallint;
    v_closed := coalesce((v_day->>'closed')::boolean, true);
    v_open := nullif(v_day->>'opens_at', '')::time;
    v_close := nullif(v_day->>'closes_at', '')::time;
    if v_weekday is null or v_weekday < 1 or v_weekday > 7 then
      raise exception 'PROVIDER_HOURS_INVALID';
    end if;
    insert into public.provider_weekly_hours (
      provider_id, weekday, closed, opens_at, closes_at
    ) values (
      p_provider,
      v_weekday,
      v_closed,
      case when v_closed then null else v_open end,
      case when v_closed then null else v_close end
    );
  end loop;
end;
$$;

create or replace function public.canon_list_provider_weekly_hours(p_provider uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'weekday', h.weekday,
      'closed', h.closed,
      'opens_at', h.opens_at,
      'closes_at', h.closes_at
    ) order by h.weekday)
    from public.provider_weekly_hours h
    where h.provider_id = p_provider
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Directory list: additive geo/hours/distance. No Places. No geocoding.
-- Nearby uses PostGIS only when caller supplies device coordinates.
-- ---------------------------------------------------------------------------

drop function if exists public.canon_list_providers();

create or replace function public.canon_list_providers(
  p_lat double precision default null,
  p_lng double precision default null,
  p_radius_m integer default 15000
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
declare
  v_origin extensions.geography;
  v_radius integer;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  v_radius := greatest(100, least(coalesce(p_radius_m, 15000), 50000));
  if p_lat is not null and p_lng is not null then
    if p_lat < -90 or p_lat > 90 or p_lng < -180 or p_lng > 180 then
      raise exception 'PROVIDER_GEO_INVALID';
    end if;
    v_origin := ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::extensions.geography;
  end if;

  return coalesce((
    select jsonb_agg(row_data order by (row_data->>'display_name'))
    from (
      select jsonb_build_object(
        'id', p.id,
        'holder_kind', p.holder_kind,
        'holder_person_id', p.holder_person_id,
        'holder_organization_id', p.holder_organization_id,
        'display_name', p.display_name,
        'categories', coalesce((
          select jsonb_agg(o.category_code)
          from public.service_offerings o
          where o.provider_id = p.id and o.active
        ), '[]'::jsonb),
        'locality_ids', coalesce((
          select jsonb_agg(c.locality_id)
          from public.provider_coverage_areas c
          where c.provider_id = p.id
        ), '[]'::jsonb),
        'province_ids', coalesce((
          select jsonb_agg(distinct loc.parent_id)
          from public.provider_coverage_areas c
          join public.location_nodes loc on loc.id = c.locality_id
          where c.provider_id = p.id
        ), '[]'::jsonb),
        'lat', case
          when p.geo_is_public_premises and p.public_geo is not null
            then ST_Y(p.public_geo::geometry)
        end,
        'lng', case
          when p.geo_is_public_premises and p.public_geo is not null
            then ST_X(p.public_geo::geometry)
        end,
        'geo_is_public_premises', p.geo_is_public_premises,
        'public_address_text', p.public_address_text,
        'distance_m', case
          when v_origin is not null
           and p.geo_is_public_premises
           and p.public_geo is not null
            then ST_Distance(p.public_geo, v_origin)
        end,
        'hours', coalesce((
          select jsonb_agg(jsonb_build_object(
            'weekday', h.weekday,
            'closed', h.closed,
            'opens_at', h.opens_at,
            'closes_at', h.closes_at
          ) order by h.weekday)
          from public.provider_weekly_hours h
          where h.provider_id = p.id
        ), '[]'::jsonb)
      ) as row_data
      from public.service_providers p
      where p.lifecycle_status = 'ACTIVE'
        and (
          v_origin is null
          or (
            p.geo_is_public_premises
            and p.public_geo is not null
            and ST_DWithin(p.public_geo, v_origin, v_radius)
          )
        )
    ) listed
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Foster direct placement — existing canonical pet only.
-- Does not duplicate pets or VitaCora. Custody/grant remain pending
-- until the pet holder authorizes them.
-- ---------------------------------------------------------------------------

create or replace function public.canon_create_foster_placement(
  p_pet uuid,
  p_starts timestamptz default timezone('utc', now()),
  p_ends timestamptz default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_pet is null then raise exception 'PET_REQUIRED'; end if;
  if not exists (select 1 from public.pets pet where pet.id = p_pet) then
    raise exception 'PET_NOT_FOUND';
  end if;
  if not exists (
    select 1 from public.foster_profiles fp
    where fp.user_id = auth.uid() and fp.active
  ) then
    raise exception 'FOSTER_PROFILE_REQUIRED';
  end if;
  if exists (
    select 1 from public.foster_placements fp
    where fp.pet_id = p_pet
      and fp.foster_user_id = auth.uid()
      and fp.status = 'OPEN'
  ) then
    raise exception 'FOSTER_PLACEMENT_ALREADY_OPEN';
  end if;

  insert into public.foster_placements (
    pet_id, foster_user_id, custody_id, status, starts_at, ends_at, created_by
  ) values (
    p_pet, auth.uid(), null, 'OPEN', coalesce(p_starts, timezone('utc', now())), p_ends, auth.uid()
  ) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_list_foster_placements()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', f.id,
      'pet_id', f.pet_id,
      'pet_name', pet.name,
      'foster_user_id', f.foster_user_id,
      'custody_id', f.custody_id,
      'status', f.status,
      'starts_at', f.starts_at,
      'ends_at', f.ends_at,
      'vitacora_access_granted', exists (
        select 1
        from public.vitacora_access_grants g
        where g.pet_id = f.pet_id
          and g.grantee_person_id = auth.uid()
          and g.revoked_at is null
          and (g.expires_at is null or g.expires_at > timezone('utc', now()))
      )
    ) order by f.starts_at desc)
    from public.foster_placements f
    join public.pets pet on pet.id = f.pet_id
    where f.foster_user_id = auth.uid()
       or public._acl_pet_holder(auth.uid(), f.pet_id)
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Daycare: reservations != guests (IN_STAY only)
-- ---------------------------------------------------------------------------

create or replace function public.canon_list_daycare_reservations()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', b.id,
      'pet_id', b.pet_id,
      'pet_name', pet.name,
      'provider_id', b.provider_id,
      'starts_at', b.starts_at,
      'ends_at', b.ends_at,
      'status', b.status
    ) order by b.starts_at)
    from public.bookings b
    join public.pets pet on pet.id = b.pet_id
    where public._acl_provider_holder(auth.uid(), b.provider_id)
      and b.status in ('REQUESTED', 'CONFIRMED')
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_daycare_guests()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', s.id,
      'booking_id', s.booking_id,
      'pet_id', s.pet_id,
      'pet_name', pet.name,
      'checked_in_at', s.checked_in_at,
      'planned_checkout_at', b.ends_at,
      'status', s.status
    ) order by s.checked_in_at)
    from public.daycare_stays s
    join public.bookings b on b.id = s.booking_id
    join public.pets pet on pet.id = s.pet_id
    where s.status = 'IN_STAY'
      and public._acl_provider_holder(auth.uid(), b.provider_id)
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

do $$
declare r record;
begin
  for r in
    select p.proname, pg_get_function_identity_arguments(p.oid) as args
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'canon_set_provider_public_geo',
        'canon_set_provider_weekly_hours',
        'canon_list_provider_weekly_hours',
        'canon_list_providers',
        'canon_create_foster_placement',
        'canon_list_foster_placements',
        'canon_list_daycare_reservations',
        'canon_list_daycare_guests'
      )
  loop
    execute format('revoke all on function public.%I(%s) from public, anon', r.proname, r.args);
    execute format('grant execute on function public.%I(%s) to authenticated', r.proname, r.args);
  end loop;
end$$;
