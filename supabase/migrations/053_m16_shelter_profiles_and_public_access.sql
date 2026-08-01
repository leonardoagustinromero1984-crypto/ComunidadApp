-- =============================================================================
-- LeoVer M16 — migración 053: perfiles públicos de refugio, RLS y acceso sanitizado
-- Forward-only sobre 001–052. No modifica M11 shelter_profiles (042) ni legacy shelters.
-- Perfil M16 = directorio público vinculado 1:1 a organización M03 elegible.
-- LOCAL ONLY hasta apply remoto autorizado en entorno no productivo.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos — reutiliza shelter.view / shelter.manage de M11 (042)
-- ---------------------------------------------------------------------------
-- Sin permisos nuevos: M16 administra vía shelter.manage; lectura interna vía shelter.view.

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.m16_shelter_profiles (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete restrict,
  display_name text not null,
  description text,
  operational_status text not null default 'ACTIVE',
  publication_status text not null default 'DRAFT',
  verification_status text not null default 'UNVERIFIED',
  public_zone_text text not null,
  coverage_areas text[] not null default '{}',
  zone_id_name text not null default 'America/Argentina/Buenos_Aires',
  accepted_species text[] not null default '{}',
  services text[] not null default '{}',
  total_capacity integer not null,
  current_occupancy integer not null default 0,
  reserved_count integer not null default 0,
  public_image_ref text,
  internal_notes text,
  created_by uuid references public.users (id),
  updated_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m16_shelter_profiles_org_uniq unique (organization_id),
  constraint m16_shelter_profiles_operational_chk
    check (operational_status = any (array[
      'ACTIVE','PAUSED','TEMPORARILY_CLOSED','PERMANENTLY_CLOSED'
    ]::text[])),
  constraint m16_shelter_profiles_publication_chk
    check (publication_status = any (array['DRAFT','PUBLISHED','UNPUBLISHED']::text[])),
  constraint m16_shelter_profiles_verification_chk
    check (verification_status = any (array[
      'UNVERIFIED','PENDING','VERIFIED','REJECTED','SUSPENDED'
    ]::text[])),
  constraint m16_shelter_profiles_capacity_chk check (total_capacity >= 0),
  constraint m16_shelter_profiles_occupancy_chk check (current_occupancy >= 0),
  constraint m16_shelter_profiles_reserved_chk check (reserved_count >= 0),
  constraint m16_shelter_profiles_used_chk
    check (current_occupancy + reserved_count <= total_capacity),
  constraint m16_shelter_profiles_display_name_len
    check (char_length(trim(display_name)) between 1 and 120),
  constraint m16_shelter_profiles_zone_len
    check (char_length(trim(public_zone_text)) between 1 and 120)
);

create index if not exists m16_shelter_profiles_publication_idx
  on public.m16_shelter_profiles (publication_status, operational_status);
create index if not exists m16_shelter_profiles_verification_idx
  on public.m16_shelter_profiles (verification_status);

create table if not exists public.m16_shelter_opening_periods (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.m16_shelter_profiles (id) on delete cascade,
  day_of_week integer not null,
  closed boolean not null default false,
  open_time time,
  close_time time,
  constraint m16_opening_day_chk check (day_of_week between 1 and 7),
  constraint m16_opening_times_chk check (
    closed
    or (open_time is not null and close_time is not null and open_time < close_time)
  )
);

create index if not exists m16_opening_shelter_idx
  on public.m16_shelter_opening_periods (shelter_profile_id, day_of_week);

create table if not exists public.m16_shelter_public_contacts (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.m16_shelter_profiles (id) on delete cascade,
  channel_type text not null,
  value text not null,
  label text,
  is_public boolean not null default true,
  constraint m16_contact_type_chk
    check (channel_type = any (array[
      'INSTITUTIONAL_EMAIL','INSTITUTIONAL_PHONE','WEBSITE','SOCIAL','MESSAGING'
    ]::text[])),
  constraint m16_contact_value_len check (char_length(trim(value)) between 1 and 500)
);

create index if not exists m16_contacts_shelter_idx
  on public.m16_shelter_public_contacts (shelter_profile_id);

create table if not exists public.m16_shelter_needs (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.m16_shelter_profiles (id) on delete cascade,
  category text not null,
  description text not null,
  constraint m16_need_category_len check (char_length(trim(category)) between 1 and 60),
  constraint m16_need_desc_len check (char_length(trim(description)) between 1 and 500)
);

create index if not exists m16_needs_shelter_idx on public.m16_shelter_needs (shelter_profile_id);

create table if not exists public.m16_shelter_verification_requests (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.m16_shelter_profiles (id) on delete restrict,
  requested_by uuid not null references public.users (id),
  status text not null default 'PENDING',
  decision text,
  decision_notes text,
  decided_by uuid references public.users (id),
  moderation_case_id uuid references public.moderation_cases (id),
  requested_at timestamptz not null default timezone('utc', now()),
  decided_at timestamptz,
  constraint m16_verification_req_status_chk
    check (status in ('PENDING','UNDER_REVIEW','APPROVED','REJECTED','CANCELLED')),
  constraint m16_verification_req_decision_chk
    check (decision is null or decision in ('VERIFIED','REJECTED'))
);

create unique index if not exists m16_verification_one_open
  on public.m16_shelter_verification_requests (shelter_profile_id)
  where status in ('PENDING','UNDER_REVIEW');

create index if not exists m16_verification_queue_idx
  on public.m16_shelter_verification_requests (status, requested_at);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m16_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v;
end;
$$;

create or replace function public._m16_org_is_eligible(p_org_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.organizations o
    where o.id = p_org_id
      and o.type in ('SHELTER', 'RESCUE_GROUP', 'NGO')
      and o.status in ('ACTIVE', 'RESTRICTED')
  );
$$;

create or replace function public._m16_require_org_manage(p_org_id uuid, p_perm text default 'shelter.manage')
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m16_require_authenticated();
begin
  if not public._m16_org_is_eligible(p_org_id) then
    raise exception 'M16_ORGANIZATION_NOT_ELIGIBLE';
  end if;
  if not public.has_org_permission(p_org_id, p_perm) then
    raise exception 'M16_PERMISSION_DENIED';
  end if;
  return v_actor;
end;
$$;

create or replace function public._m16_recompute_availability(
  p_operational text, p_publication text,
  p_capacity integer, p_occupancy integer, p_reserved integer
) returns text language sql immutable as $$
  select case
    when p_operational is distinct from 'ACTIVE'
      or p_publication is distinct from 'PUBLISHED' then 'UNAVAILABLE'
    when greatest(coalesce(p_occupancy,0),0) + greatest(coalesce(p_reserved,0),0)
         >= greatest(coalesce(p_capacity,0),0) then 'FULL'
    when greatest(coalesce(p_occupancy,0),0) + greatest(coalesce(p_reserved,0),0) > 0
         then 'LIMITED'
    else 'AVAILABLE'
  end;
$$;

create or replace function public._m16_profile_json(p_id uuid, p_include_internal boolean default false)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m16_shelter_profiles;
  v_periods jsonb;
  v_contacts jsonb;
  v_needs jsonb;
  v_free integer;
  v_avail text;
begin
  select * into v from public.m16_shelter_profiles where id = p_id;
  if not found then return null; end if;

  select coalesce(jsonb_agg(jsonb_build_object(
    'day_of_week', p.day_of_week,
    'closed', p.closed,
    'open_time', to_char(p.open_time, 'HH24:MI'),
    'close_time', to_char(p.close_time, 'HH24:MI')
  ) order by p.day_of_week, p.open_time nulls last), '[]'::jsonb)
  into v_periods
  from public.m16_shelter_opening_periods p
  where p.shelter_profile_id = p_id;

  select coalesce(jsonb_agg(jsonb_build_object(
    'type', c.channel_type,
    'value', c.value,
    'label', c.label
  ) order by c.channel_type), '[]'::jsonb)
  into v_contacts
  from public.m16_shelter_public_contacts c
  where c.shelter_profile_id = p_id
    and (p_include_internal or c.is_public);

  select coalesce(jsonb_agg(jsonb_build_object(
    'category', n.category,
    'description', n.description
  ) order by n.category), '[]'::jsonb)
  into v_needs
  from public.m16_shelter_needs n
  where n.shelter_profile_id = p_id;

  v_free := greatest(v.total_capacity - v.current_occupancy - v.reserved_count, 0);
  v_avail := public._m16_recompute_availability(
    v.operational_status, v.publication_status,
    v.total_capacity, v.current_occupancy, v.reserved_count
  );

  return jsonb_build_object(
    'id', v.id,
    'organization_id', case when p_include_internal then v.organization_id else null end,
    'display_name', v.display_name,
    'description', v.description,
    'operational_status', v.operational_status,
    'publication_status', v.publication_status,
    'verification_status', v.verification_status,
    'public_zone_text', v.public_zone_text,
    'coverage_areas', to_jsonb(v.coverage_areas),
    'opening_hours', jsonb_build_object(
      'zone_id_name', v.zone_id_name,
      'periods', v_periods
    ),
    'accepted_species', to_jsonb(v.accepted_species),
    'services', to_jsonb(v.services),
    'public_contacts', v_contacts,
    'capacity', jsonb_build_object(
      'total_capacity', v.total_capacity,
      'current_occupancy', case when p_include_internal then v.current_occupancy else null end,
      'reserved_count', case when p_include_internal then v.reserved_count else null end
    ),
    'total_capacity', v.total_capacity,
    'free_slots_approximate', v_free,
    'availability', v_avail,
    'needs', v_needs,
    'public_image_ref', v.public_image_ref,
    'internal_notes', case when p_include_internal then v.internal_notes else null end,
    'created_at', v.created_at,
    'updated_at', v.updated_at
  );
end;
$$;

create or replace function public._m16_public_shelter_json(p_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select public._m16_profile_json(p_id, false);
$$;

create or replace function public._m16_replace_opening_periods(
  p_shelter_id uuid, p_periods jsonb
) returns void language plpgsql security definer set search_path = public as $$
declare
  v_elem jsonb;
  v_day integer;
  v_closed boolean;
  v_open time;
  v_close time;
begin
  delete from public.m16_shelter_opening_periods where shelter_profile_id = p_shelter_id;
  if p_periods is null or jsonb_array_length(p_periods) = 0 then return; end if;
  for v_elem in select * from jsonb_array_elements(p_periods)
  loop
    v_day := (v_elem->>'day_of_week')::integer;
    v_closed := coalesce((v_elem->>'closed')::boolean, false);
    if v_day is null or v_day < 1 or v_day > 7 then
      raise exception 'M16_INVALID_OPENING_HOURS';
    end if;
    if v_closed then
      insert into public.m16_shelter_opening_periods (shelter_profile_id, day_of_week, closed)
      values (p_shelter_id, v_day, true);
    else
      v_open := (v_elem->>'open_time')::time;
      v_close := (v_elem->>'close_time')::time;
      if v_open is null or v_close is null or v_open >= v_close then
        raise exception 'M16_INVALID_OPENING_HOURS';
      end if;
      insert into public.m16_shelter_opening_periods (
        shelter_profile_id, day_of_week, closed, open_time, close_time
      ) values (p_shelter_id, v_day, false, v_open, v_close);
    end if;
  end loop;
end;
$$;

create or replace function public._m16_replace_public_contacts(
  p_shelter_id uuid, p_contacts jsonb
) returns void language plpgsql security definer set search_path = public as $$
declare v_elem jsonb;
begin
  delete from public.m16_shelter_public_contacts where shelter_profile_id = p_shelter_id;
  if p_contacts is null then return; end if;
  for v_elem in select * from jsonb_array_elements(p_contacts)
  loop
    if coalesce(trim(v_elem->>'value'), '') = '' then
      raise exception 'M16_INVALID_PUBLIC_CONTACT';
    end if;
    insert into public.m16_shelter_public_contacts (
      shelter_profile_id, channel_type, value, label, is_public
    ) values (
      p_shelter_id,
      upper(trim(v_elem->>'type')),
      trim(v_elem->>'value'),
      nullif(trim(coalesce(v_elem->>'label', '')), ''),
      coalesce((v_elem->>'is_public')::boolean, true)
    );
  end loop;
end;
$$;

create or replace function public._m16_replace_needs(p_shelter_id uuid, p_needs jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare v_elem jsonb;
begin
  delete from public.m16_shelter_needs where shelter_profile_id = p_shelter_id;
  if p_needs is null then return; end if;
  for v_elem in select * from jsonb_array_elements(p_needs)
  loop
    insert into public.m16_shelter_needs (shelter_profile_id, category, description)
    values (
      p_shelter_id,
      trim(v_elem->>'category'),
      trim(v_elem->>'description')
    );
  end loop;
end;
$$;

create or replace function public._m16_is_moderator(p_actor uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_actor is not null and (
    public.has_permission('organizations.review_verification')
    or public.has_permission('moderation.manage')
  );
$$;

-- ---------------------------------------------------------------------------
-- 3. RLS — deny direct access; mutaciones vía RPC
-- ---------------------------------------------------------------------------
alter table public.m16_shelter_profiles enable row level security;
alter table public.m16_shelter_opening_periods enable row level security;
alter table public.m16_shelter_public_contacts enable row level security;
alter table public.m16_shelter_needs enable row level security;
alter table public.m16_shelter_verification_requests enable row level security;

create policy m16_profiles_select on public.m16_shelter_profiles for select to authenticated
  using (public.has_org_permission(organization_id, 'shelter.view'));
create policy m16_profiles_ins on public.m16_shelter_profiles for insert to authenticated with check (false);
create policy m16_profiles_upd on public.m16_shelter_profiles for update to authenticated using (false);
create policy m16_profiles_del on public.m16_shelter_profiles for delete to authenticated using (false);

create policy m16_opening_select on public.m16_shelter_opening_periods for select to authenticated
  using (exists (
    select 1 from public.m16_shelter_profiles s
    where s.id = shelter_profile_id and public.has_org_permission(s.organization_id, 'shelter.view')
  ));
create policy m16_opening_mut on public.m16_shelter_opening_periods for all to authenticated using (false);

create policy m16_contacts_select on public.m16_shelter_public_contacts for select to authenticated
  using (exists (
    select 1 from public.m16_shelter_profiles s
    where s.id = shelter_profile_id and public.has_org_permission(s.organization_id, 'shelter.view')
  ));
create policy m16_contacts_mut on public.m16_shelter_public_contacts for all to authenticated using (false);

create policy m16_needs_select on public.m16_shelter_needs for select to authenticated
  using (exists (
    select 1 from public.m16_shelter_profiles s
    where s.id = shelter_profile_id and public.has_org_permission(s.organization_id, 'shelter.view')
  ));
create policy m16_needs_mut on public.m16_shelter_needs for all to authenticated using (false);

create policy m16_verification_select on public.m16_shelter_verification_requests for select to authenticated
  using (
    exists (
      select 1 from public.m16_shelter_profiles s
      where s.id = shelter_profile_id and public.has_org_permission(s.organization_id, 'shelter.view')
    )
    or public._m16_is_moderator(auth.uid())
  );
create policy m16_verification_mut on public.m16_shelter_verification_requests for all to authenticated using (false);

revoke all on table public.m16_shelter_profiles from public, anon;
revoke all on table public.m16_shelter_opening_periods from public, anon;
revoke all on table public.m16_shelter_public_contacts from public, anon;
revoke all on table public.m16_shelter_needs from public, anon;
revoke all on table public.m16_shelter_verification_requests from public, anon;
grant select on table public.m16_shelter_profiles to authenticated;
grant select on table public.m16_shelter_opening_periods to authenticated;
grant select on table public.m16_shelter_public_contacts to authenticated;
grant select on table public.m16_shelter_needs to authenticated;
grant select on table public.m16_shelter_verification_requests to authenticated;
grant all on table public.m16_shelter_profiles to service_role;
grant all on table public.m16_shelter_opening_periods to service_role;
grant all on table public.m16_shelter_public_contacts to service_role;
grant all on table public.m16_shelter_needs to service_role;
grant all on table public.m16_shelter_verification_requests to service_role;

-- ---------------------------------------------------------------------------
-- 4. RPCs — público sanitizado (anon + authenticated)
-- ---------------------------------------------------------------------------
create or replace function public.m16_list_public_shelters(
  p_query text default null,
  p_species text default null,
  p_service text default null,
  p_operational_status text default null,
  p_verified_only boolean default false,
  p_unverified_or_pending boolean default false
) returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_row public.m16_shelter_profiles;
  v_q text := nullif(lower(trim(coalesce(p_query, ''))), '');
  v_species text := nullif(upper(trim(coalesce(p_species, ''))), '');
  v_service text := nullif(upper(trim(coalesce(p_service, ''))), '');
  v_op text := nullif(upper(trim(coalesce(p_operational_status, ''))), '');
begin
  for v_row in
    select s.* from public.m16_shelter_profiles s
    where s.publication_status = 'PUBLISHED'
      and (
        v_op is not null and s.operational_status = v_op
        or v_op is null and s.operational_status <> 'PERMANENTLY_CLOSED'
      )
      and (v_q is null or s.display_name ilike '%' || v_q || '%'
           or s.public_zone_text ilike '%' || v_q || '%'
           or coalesce(s.description, '') ilike '%' || v_q || '%')
      and (v_species is null or v_species = any (s.accepted_species) or cardinality(s.accepted_species) = 0)
      and (v_service is null or v_service = any (s.services))
      and (not coalesce(p_verified_only, false) or s.verification_status = 'VERIFIED')
      and (not coalesce(p_unverified_or_pending, false)
           or s.verification_status in ('UNVERIFIED', 'PENDING'))
    order by s.display_name
  loop
    return next public._m16_public_shelter_json(v_row.id);
  end loop;
end;
$$;

create or replace function public.m16_get_public_shelter(p_shelter_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id;
  if not found or v.publication_status <> 'PUBLISHED' then
    raise exception 'M16_SHELTER_NOT_FOUND';
  end if;
  return public._m16_public_shelter_json(p_shelter_id);
end;
$$;

create or replace function public.m16_is_organization_eligible(p_organization_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public._m16_org_is_eligible(p_organization_id);
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs — administración (authenticated)
-- ---------------------------------------------------------------------------
create or replace function public.m16_get_shelter_profile(p_shelter_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id, 'shelter.view');
  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_get_shelter_by_organization(p_organization_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
begin
  perform public._m16_require_org_manage(p_organization_id, 'shelter.view');
  select * into v from public.m16_shelter_profiles where organization_id = p_organization_id;
  if not found then return null; end if;
  return public._m16_profile_json(v.id, true);
end;
$$;

create or replace function public.m16_create_shelter_profile(
  p_organization_id uuid,
  p_display_name text,
  p_public_zone_text text,
  p_total_capacity integer,
  p_description text default null,
  p_accepted_species text[] default '{}',
  p_services text[] default '{}',
  p_publish boolean default false
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid;
  v_existing public.m16_shelter_profiles;
  v_id uuid;
  v_pub text := case when coalesce(p_publish, false) then 'PUBLISHED' else 'DRAFT' end;
begin
  v_actor := public._m16_require_org_manage(p_organization_id);
  if coalesce(p_total_capacity, -1) < 0 then raise exception 'M16_INVALID_CAPACITY'; end if;

  select * into v_existing from public.m16_shelter_profiles where organization_id = p_organization_id;
  if found then
    return public._m16_profile_json(v_existing.id, true);
  end if;

  insert into public.m16_shelter_profiles (
    organization_id, display_name, description, public_zone_text, total_capacity,
    accepted_species, services, publication_status, created_by, updated_by
  ) values (
    p_organization_id, trim(p_display_name), nullif(trim(coalesce(p_description,'')), ''),
    trim(p_public_zone_text), p_total_capacity,
    coalesce(p_accepted_species, '{}'), coalesce(p_services, '{}'),
    v_pub, v_actor, v_actor
  ) returning id into v_id;

  return public._m16_profile_json(v_id, true);
end;
$$;

create or replace function public.m16_update_shelter_public_data(
  p_shelter_id uuid,
  p_display_name text,
  p_public_zone_text text,
  p_description text default null,
  p_coverage_areas text[] default '{}',
  p_accepted_species text[] default '{}',
  p_public_image_ref text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.operational_status = 'PERMANENTLY_CLOSED' then raise exception 'M16_STATE_ALREADY_FINAL'; end if;

  update public.m16_shelter_profiles set
    display_name = trim(p_display_name),
    description = nullif(trim(coalesce(p_description,'')), ''),
    public_zone_text = trim(p_public_zone_text),
    coverage_areas = coalesce(p_coverage_areas, '{}'),
    accepted_species = coalesce(p_accepted_species, '{}'),
    public_image_ref = nullif(trim(coalesce(p_public_image_ref,'')), ''),
    updated_by = auth.uid(),
    updated_at = timezone('utc', now())
  where id = p_shelter_id;

  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_update_operational_status(
  p_shelter_id uuid, p_status text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m16_shelter_profiles;
  v_status text := upper(trim(p_status));
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.operational_status = v_status then
    return public._m16_profile_json(p_shelter_id, true);
  end if;
  if v.operational_status = 'PERMANENTLY_CLOSED' then
    raise exception 'M16_STATE_ALREADY_FINAL';
  end if;
  if v_status not in ('ACTIVE','PAUSED','TEMPORARILY_CLOSED','PERMANENTLY_CLOSED') then
    raise exception 'M16_INVALID_STATE_TRANSITION';
  end if;
  update public.m16_shelter_profiles set
    operational_status = v_status, updated_by = auth.uid(), updated_at = timezone('utc', now())
  where id = p_shelter_id;
  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_update_publication_status(
  p_shelter_id uuid, p_status text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m16_shelter_profiles;
  v_status text := upper(trim(p_status));
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.operational_status = 'PERMANENTLY_CLOSED' then raise exception 'M16_STATE_ALREADY_FINAL'; end if;
  if v.publication_status = v_status then
    return public._m16_profile_json(p_shelter_id, true);
  end if;
  if v_status not in ('DRAFT','PUBLISHED','UNPUBLISHED') then
    raise exception 'M16_INVALID_STATE_TRANSITION';
  end if;
  update public.m16_shelter_profiles set
    publication_status = v_status, updated_by = auth.uid(), updated_at = timezone('utc', now())
  where id = p_shelter_id;
  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_request_verification(p_shelter_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m16_shelter_profiles;
  v_req_id uuid;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.verification_status in ('PENDING','VERIFIED') then
    return public._m16_profile_json(p_shelter_id, true);
  end if;
  if v.verification_status = 'SUSPENDED' then
    raise exception 'M16_INVALID_STATE_TRANSITION';
  end if;

  update public.m16_shelter_profiles set
    verification_status = 'PENDING', updated_by = auth.uid(), updated_at = timezone('utc', now())
  where id = p_shelter_id;

  insert into public.m16_shelter_verification_requests (shelter_profile_id, requested_by, status)
  values (p_shelter_id, auth.uid(), 'PENDING')
  on conflict do nothing
  returning id into v_req_id;

  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_update_opening_hours(
  p_shelter_id uuid, p_zone_id_name text, p_periods jsonb
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.operational_status = 'PERMANENTLY_CLOSED' then raise exception 'M16_STATE_ALREADY_FINAL'; end if;
  update public.m16_shelter_profiles set
    zone_id_name = coalesce(nullif(trim(p_zone_id_name), ''), zone_id_name),
    updated_by = auth.uid(), updated_at = timezone('utc', now())
  where id = p_shelter_id;
  perform public._m16_replace_opening_periods(p_shelter_id, p_periods);
  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_update_services(
  p_shelter_id uuid, p_services text[]
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.operational_status = 'PERMANENTLY_CLOSED' then raise exception 'M16_STATE_ALREADY_FINAL'; end if;
  update public.m16_shelter_profiles set
    services = coalesce(p_services, '{}'), updated_by = auth.uid(), updated_at = timezone('utc', now())
  where id = p_shelter_id;
  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_update_needs(p_shelter_id uuid, p_needs jsonb)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.operational_status = 'PERMANENTLY_CLOSED' then raise exception 'M16_STATE_ALREADY_FINAL'; end if;
  perform public._m16_replace_needs(p_shelter_id, p_needs);
  update public.m16_shelter_profiles set updated_by = auth.uid(), updated_at = timezone('utc', now())
  where id = p_shelter_id;
  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_update_capacity(
  p_shelter_id uuid,
  p_total_capacity integer,
  p_current_occupancy integer default null,
  p_reserved_count integer default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
declare v_occ integer;
declare v_res integer;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.operational_status = 'PERMANENTLY_CLOSED' then raise exception 'M16_STATE_ALREADY_FINAL'; end if;
  v_occ := coalesce(p_current_occupancy, v.current_occupancy);
  v_res := coalesce(p_reserved_count, v.reserved_count);
  if coalesce(p_total_capacity, -1) < 0 or v_occ < 0 or v_res < 0 then
    raise exception 'M16_INVALID_CAPACITY';
  end if;
  if v_occ + v_res > p_total_capacity then
    raise exception 'M16_OCCUPANCY_EXCEEDS_CAPACITY';
  end if;
  update public.m16_shelter_profiles set
    total_capacity = p_total_capacity,
    current_occupancy = v_occ,
    reserved_count = v_res,
    updated_by = auth.uid(),
    updated_at = timezone('utc', now())
  where id = p_shelter_id;
  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

create or replace function public.m16_update_public_contacts(
  p_shelter_id uuid, p_contacts jsonb
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m16_shelter_profiles;
begin
  select * into v from public.m16_shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  perform public._m16_require_org_manage(v.organization_id);
  if v.operational_status = 'PERMANENTLY_CLOSED' then raise exception 'M16_STATE_ALREADY_FINAL'; end if;
  perform public._m16_replace_public_contacts(p_shelter_id, p_contacts);
  update public.m16_shelter_profiles set updated_by = auth.uid(), updated_at = timezone('utc', now())
  where id = p_shelter_id;
  return public._m16_profile_json(p_shelter_id, true);
end;
$$;

-- M04 — decisión administrativa (moderador / org.review_verification)
create or replace function public.m16_decide_shelter_verification(
  p_request_id uuid,
  p_decision text,
  p_notes text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m16_require_authenticated();
  v_req public.m16_shelter_verification_requests;
  v_decision text := upper(trim(p_decision));
  v_profile_status text;
begin
  if not public._m16_is_moderator(v_actor) then
    raise exception 'M16_PERMISSION_DENIED';
  end if;
  select * into v_req from public.m16_shelter_verification_requests where id = p_request_id for update;
  if not found then raise exception 'M16_SHELTER_NOT_FOUND'; end if;
  if v_req.status in ('APPROVED','REJECTED','CANCELLED') then
    return public._m16_profile_json(v_req.shelter_profile_id, true);
  end if;
  if v_decision not in ('VERIFIED','REJECTED') then
    raise exception 'M16_INVALID_STATE_TRANSITION';
  end if;
  v_profile_status := v_decision;

  update public.m16_shelter_verification_requests set
    status = case when v_decision = 'VERIFIED' then 'APPROVED' else 'REJECTED' end,
    decision = v_decision,
    decision_notes = nullif(trim(coalesce(p_notes,'')), ''),
    decided_by = v_actor,
    decided_at = timezone('utc', now())
  where id = p_request_id;

  update public.m16_shelter_profiles set
    verification_status = v_profile_status,
    updated_by = v_actor,
    updated_at = timezone('utc', now())
  where id = v_req.shelter_profile_id;

  return public._m16_profile_json(v_req.shelter_profile_id, true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Grants — superficie pública anon; mutaciones authenticated
-- ---------------------------------------------------------------------------
revoke all on function public._m16_require_authenticated() from public;
revoke all on function public._m16_org_is_eligible(uuid) from public;
revoke all on function public._m16_require_org_manage(uuid, text) from public;
revoke all on function public._m16_recompute_availability(text,text,integer,integer,integer) from public;
revoke all on function public._m16_profile_json(uuid, boolean) from public;
revoke all on function public._m16_public_shelter_json(uuid) from public;
revoke all on function public._m16_replace_opening_periods(uuid, jsonb) from public;
revoke all on function public._m16_replace_public_contacts(uuid, jsonb) from public;
revoke all on function public._m16_replace_needs(uuid, jsonb) from public;
revoke all on function public._m16_is_moderator(uuid) from public;

revoke all on function public.m16_list_public_shelters(text,text,text,text,boolean,boolean) from public;
grant execute on function public.m16_list_public_shelters(text,text,text,text,boolean,boolean) to anon, authenticated;

revoke all on function public.m16_get_public_shelter(uuid) from public;
grant execute on function public.m16_get_public_shelter(uuid) to anon, authenticated;

revoke all on function public.m16_is_organization_eligible(uuid) from public;
grant execute on function public.m16_is_organization_eligible(uuid) to authenticated;

revoke all on function public.m16_get_shelter_profile(uuid) from public;
revoke all on function public.m16_get_shelter_by_organization(uuid) from public;
revoke all on function public.m16_create_shelter_profile(uuid,text,text,integer,text,text[],text[],boolean) from public;
revoke all on function public.m16_update_shelter_public_data(uuid,text,text,text,text[],text[],text) from public;
revoke all on function public.m16_update_operational_status(uuid,text) from public;
revoke all on function public.m16_update_publication_status(uuid,text) from public;
revoke all on function public.m16_request_verification(uuid) from public;
revoke all on function public.m16_update_opening_hours(uuid,text,jsonb) from public;
revoke all on function public.m16_update_services(uuid,text[]) from public;
revoke all on function public.m16_update_needs(uuid,jsonb) from public;
revoke all on function public.m16_update_capacity(uuid,integer,integer,integer) from public;
revoke all on function public.m16_update_public_contacts(uuid,jsonb) from public;
revoke all on function public.m16_decide_shelter_verification(uuid,text,text) from public;

grant execute on function public.m16_get_shelter_profile(uuid) to authenticated;
grant execute on function public.m16_get_shelter_by_organization(uuid) to authenticated;
grant execute on function public.m16_create_shelter_profile(uuid,text,text,integer,text,text[],text[],boolean) to authenticated;
grant execute on function public.m16_update_shelter_public_data(uuid,text,text,text,text[],text[],text) to authenticated;
grant execute on function public.m16_update_operational_status(uuid,text) to authenticated;
grant execute on function public.m16_update_publication_status(uuid,text) to authenticated;
grant execute on function public.m16_request_verification(uuid) to authenticated;
grant execute on function public.m16_update_opening_hours(uuid,text,jsonb) to authenticated;
grant execute on function public.m16_update_services(uuid,text[]) to authenticated;
grant execute on function public.m16_update_needs(uuid,jsonb) to authenticated;
grant execute on function public.m16_update_capacity(uuid,integer,integer,integer) to authenticated;
grant execute on function public.m16_update_public_contacts(uuid,jsonb) to authenticated;
grant execute on function public.m16_decide_shelter_verification(uuid,text,text) to authenticated;

commit;
