-- 1095: PostGIS helpers must resolve under search_path=public.
-- STAGING evidence (probe, rolled back):
--   IDENTITY_OK
--   CREATE_FAIL SQLSTATE 42883
--   message: function st_makepoint(double precision, double precision) does not exist
--   hint: No function matches the given name and argument types.
--   context: canon_create_lost_found line 22 assignment (v_loc := ST_MakePoint)
-- Does not edit 1094 or earlier.

create or replace function public._canon_geo_point(
  p_lng double precision,
  p_lat double precision
)
returns extensions.geography
language sql
immutable
parallel safe
set search_path = extensions, public
as $$
  select extensions.ST_SetSRID(extensions.ST_MakePoint(p_lng, p_lat), 4326)::extensions.geography;
$$;

revoke all on function public._canon_geo_point(double precision, double precision) from public, anon, authenticated;

alter table public.persons
  add column if not exists base_address text null;

create or replace function public.canon_create_lost_found(
  p_kind text,
  p_pet_id uuid default null,
  p_locality_id text default null,
  p_species text default null,
  p_note text default null,
  p_lat double precision default null,
  p_lng double precision default null,
  p_incident_at timestamptz default null,
  p_photo_asset_id uuid default null,
  p_name text default null,
  p_sex text default 'UNKNOWN',
  p_size text default 'UNKNOWN',
  p_estimated_age_months integer default null,
  p_breed_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_id uuid;
  v_kind text := upper(btrim(coalesce(p_kind, '')));
  v_pet uuid := p_pet_id;
  v_loc extensions.geography(Point, 4326);
  v_species text := nullif(btrim(coalesce(p_species, '')), '');
begin
  if auth.uid() is null then
    raise exception 'LF-CREATE-AUTH';
  end if;
  if v_kind not in ('LOST', 'FOUND') then
    raise exception 'LF-CREATE-KIND';
  end if;
  if v_kind = 'LOST' and v_pet is not null
     and not public._acl_pet_holder(auth.uid(), v_pet) then
    raise exception 'LF-CREATE-FORBIDDEN';
  end if;
  if p_lat is not null and p_lng is not null then
    if p_lat < -90 or p_lat > 90 or p_lng < -180 or p_lng > 180 then
      raise exception 'LF-CREATE-LOCATION'
        using detail = 'lat/lng out of range', hint = '22023';
    end if;
    begin
      v_loc := public._canon_geo_point(p_lng, p_lat);
    exception when others then
      raise exception 'LF-CREATE-LOCATION'
        using detail = sqlerrm, hint = sqlstate;
    end;
  end if;
  if v_species is not null and not exists (select 1 from public.species s where s.code = v_species) then
    v_species := 'OTHER';
  end if;

  if v_kind = 'FOUND' then
    if exists (
      select 1 from public.lost_found_alerts a
       where a.created_by = auth.uid()
         and a.kind = 'FOUND'
         and a.status = 'OPEN'
         and a.created_at > timezone('utc', now()) - interval '15 seconds'
         and coalesce(a.note, '') = coalesce(p_note, '')
    ) then
      select id into v_id
        from public.lost_found_alerts
       where created_by = auth.uid() and kind = 'FOUND' and status = 'OPEN'
       order by created_at desc
       limit 1;
      return v_id;
    end if;
    begin
      v_pet := public._canon_create_found_identity(
        p_name, p_species, p_sex, p_size, p_photo_asset_id, p_estimated_age_months, p_breed_id
      );
    exception when others then
      raise exception 'LF-CREATE-IDENTITY'
        using detail = sqlerrm, hint = sqlstate;
    end;
    if v_pet is null then
      raise exception 'LF-CREATE-IDENTITY';
    end if;
  end if;

  begin
    insert into public.lost_found_alerts (
      kind, pet_id, created_by, locality_id, species_code, note,
      precise_location, photo_asset_id, incident_at, status, next_wave_at
    ) values (
      v_kind, v_pet, auth.uid(), p_locality_id, v_species, p_note,
      v_loc, p_photo_asset_id, coalesce(p_incident_at, timezone('utc', now())), 'OPEN',
      timezone('utc', now()) + interval '15 minutes'
    ) returning id into v_id;
  exception when others then
    raise exception 'LF-CREATE-ALERT'
      using detail = sqlerrm, hint = sqlstate;
  end;

  if v_kind = 'FOUND' then
    begin
      perform public._canon_match_found_to_lost(v_id);
    exception when others then
      null;
    end;
  end if;
  begin
    perform public._canon_fanout_lost_found_recipients(v_id);
  exception when others then
    null;
  end;
  begin
    perform public.canon_audit(
      'lost_found.create',
      'lost_found_alerts',
      v_id,
      jsonb_build_object('kind', v_kind, 'pet_id', v_pet)
    );
  exception when others then
    null;
  end;
  return v_id;
end;
$$;

grant execute on function public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid, text, text, text, integer, uuid) to authenticated;

drop function if exists public.canon_upsert_responder_base_location(double precision, double precision, uuid, boolean);

create or replace function public.canon_upsert_responder_base_location(
  p_lat double precision,
  p_lng double precision,
  p_organization_id uuid default null,
  p_receive boolean default true,
  p_address text default null
)
returns boolean
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_loc extensions.geography(Point, 4326);
  v_address text := nullif(btrim(coalesce(p_address, '')), '');
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_lat is null or p_lng is null or p_lat < -90 or p_lat > 90 or p_lng < -180 or p_lng > 180 then
    raise exception 'LOCATION_INVALID';
  end if;
  begin
    v_loc := public._canon_geo_point(p_lng, p_lat);
  exception when others then
    raise exception 'LOCATION_INVALID'
      using detail = sqlerrm, hint = sqlstate;
  end;
  if p_organization_id is null then
    update public.persons
       set base_location = v_loc,
           base_address = coalesce(v_address, base_address),
           receive_nearby_cases = coalesce(p_receive, true),
           updated_at = timezone('utc', now())
     where user_id = auth.uid();
  else
    if not exists (
      select 1 from public.organization_memberships m
       where m.organization_id = p_organization_id
         and m.person_id = auth.uid()
         and m.status = 'ACTIVE'
    ) then
      raise exception 'FORBIDDEN';
    end if;
    update public.organizations
       set base_location = v_loc,
           address_line = coalesce(v_address, address_line),
           receive_nearby_cases = coalesce(p_receive, true),
           updated_at = timezone('utc', now())
     where id = p_organization_id;
  end if;
  return true;
end;
$$;

grant execute on function public.canon_upsert_responder_base_location(double precision, double precision, uuid, boolean, text) to authenticated;

drop function if exists public.canon_get_my_responder_base();

create or replace function public.canon_get_my_responder_base(
  p_organization_id uuid default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
declare
  v_row jsonb;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_organization_id is not null then
    if not exists (
      select 1 from public.organization_memberships m
       where m.organization_id = p_organization_id
         and m.person_id = auth.uid()
         and m.status = 'ACTIVE'
    ) then
      raise exception 'FORBIDDEN';
    end if;
    select jsonb_build_object(
      'eligible', true,
      'has_base_location', o.base_location is not null,
      'receive_nearby_cases', coalesce(o.receive_nearby_cases, false),
      'home_locality_id', o.home_locality_id,
      'address', o.address_line,
      'lat', case when o.base_location is null then null else extensions.ST_Y(o.base_location::extensions.geometry) end,
      'lng', case when o.base_location is null then null else extensions.ST_X(o.base_location::extensions.geometry) end,
      'subject', 'ORGANIZATION',
      'organization_id', o.id
    )
      into v_row
      from public.organizations o
     where o.id = p_organization_id;
    return coalesce(v_row, jsonb_build_object('eligible', false, 'has_base_location', false));
  end if;
  select jsonb_build_object(
    'eligible', public._canon_alert_responder_eligible(auth.uid()),
    'has_base_location', p.base_location is not null,
    'receive_nearby_cases', coalesce(p.receive_nearby_cases, false),
    'home_locality_id', p.home_locality_id,
    'address', p.base_address,
    'lat', case when p.base_location is null then null else extensions.ST_Y(p.base_location::extensions.geometry) end,
    'lng', case when p.base_location is null then null else extensions.ST_X(p.base_location::extensions.geometry) end,
    'subject', 'PERSON'
  )
    into v_row
    from public.persons p
   where p.user_id = auth.uid();
  return coalesce(v_row, jsonb_build_object('eligible', false, 'has_base_location', false));
end;
$$;

grant execute on function public.canon_get_my_responder_base(uuid) to authenticated;

create or replace function public.canon_list_community_nearby(
  p_lat double precision,
  p_lng double precision,
  p_filter text default 'ALL',
  p_radius_m integer default 25000
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
declare
  v_loc extensions.geography(Point, 4326);
  v_filter text := upper(btrim(coalesce(p_filter, 'ALL')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_lat is null or p_lng is null then raise exception 'LOCATION_INVALID'; end if;
  v_loc := public._canon_geo_point(p_lng, p_lat);
  return coalesce((
    select jsonb_agg(row_to_json(x))
    from (
      select * from (
        select 'RESCUER'::text as kind,
               p.user_id::text as id,
               coalesce(p.display_name, p.username, 'Rescatista') as name,
               p.home_locality_id as locality_id,
               c.verification_status,
               ST_Distance(p.base_location, v_loc) as meters,
               null::text as hours_json,
               false as public_address
          from public.persons p
          join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'RESCUER' and c.active
         where p.base_location is not null
           and ST_DWithin(p.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'RESCUER')
        union all
        select 'FOSTER', p.user_id::text, coalesce(p.display_name, 'Tránsito'),
               p.home_locality_id, c.verification_status,
               ST_Distance(p.base_location, v_loc), null, false
          from public.persons p
          join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'FOSTER' and c.active
         where p.base_location is not null
           and ST_DWithin(p.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'FOSTER')
        union all
        select case when oc.capability = 'SHELTER' then 'SHELTER' else oc.capability end,
               o.id::text, o.name, o.home_locality_id, o.verification_status,
               ST_Distance(o.base_location, v_loc), null, false
          from public.organizations o
          join public.organization_capabilities oc on oc.organization_id = o.id
         where o.base_location is not null
           and o.lifecycle_status = 'ACTIVE'
           and ST_DWithin(o.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'SHELTER')
           and oc.capability in ('SHELTER', 'NGO')
        union all
        select 'VETERINARY', o.id::text, o.name, o.home_locality_id, o.verification_status,
               ST_Distance(o.base_location, v_loc), null, true
          from public.organizations o
          join public.organization_capabilities oc on oc.organization_id = o.id
         where o.base_location is not null
           and o.lifecycle_status = 'ACTIVE'
           and ST_DWithin(o.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'VETERINARY', 'SERVICES')
           and oc.capability in ('VETERINARY', 'CLINIC', 'BUSINESS')
        union all
        select 'PROFESSIONAL', pr.person_id::text, coalesce(p.display_name, 'Profesional'),
               p.home_locality_id, c.verification_status,
               ST_Distance(p.base_location, v_loc), null, false
          from public.professional_profiles pr
          join public.persons p on p.user_id = pr.person_id
          left join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'PROFESSIONAL'
         where pr.active
           and p.base_location is not null
           and ST_DWithin(p.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'PROFESSIONAL', 'SERVICES')
      ) q
      order by meters
      limit 80
    ) x
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_list_community_nearby(double precision, double precision, text, integer) to authenticated;

comment on function public._canon_geo_point(double precision, double precision) is
  'Canonical Point(4326) from lng/lat. PostGIS lives in extensions.';
comment on function public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid, text, text, text, integer, uuid) is
  'Atomic FOUND/LOST create. Location uses _canon_geo_point. Stages: LF-CREATE-AUTH/KIND/FORBIDDEN/LOCATION/IDENTITY/ALERT.';
