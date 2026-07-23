-- =============================================================================
-- LeoVer M11 — migración 042: operación de refugios (perfiles, mascotas, voluntarios)
-- Forward-only sobre 001–041. NO reescribe 040/041 ni legacy public.shelters (006).
-- Compatibilidad: tabla legacy `shelters` permanece intacta (listing Sumate).
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 shelter.* (deny-by-default vía has_org_permission)
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('shelter.view', 'Ver operación de refugio'),
  ('shelter.manage', 'Gestionar perfil y capacidad del refugio'),
  ('shelter.pet.read', 'Ver mascotas alojadas'),
  ('shelter.pet.intake', 'Ingresar / reservar mascotas'),
  ('shelter.pet.release', 'Egresar mascotas'),
  ('shelter.volunteer.read', 'Ver voluntarios'),
  ('shelter.volunteer.manage', 'Gestionar voluntarios')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code like 'shelter.%'
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code in ('shelter.view', 'shelter.pet.read', 'shelter.volunteer.read')
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.shelter_profiles (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete restrict,
  branch_id uuid null references public.organization_branches (id) on delete set null,
  display_name text not null,
  description text,
  status text not null default 'DRAFT',
  total_capacity integer not null,
  current_occupancy integer not null default 0,
  reserved_capacity integer not null default 0,
  accepted_species text[] not null default '{}',
  accepts_emergencies boolean not null default false,
  public_zone_text text,
  internal_address_ref text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint shelter_profiles_status_chk
    check (status = any (array['DRAFT','ACTIVE','PAUSED','SUSPENDED','CLOSED']::text[])),
  constraint shelter_profiles_capacity_chk check (total_capacity > 0),
  constraint shelter_profiles_occupancy_chk check (current_occupancy >= 0),
  constraint shelter_profiles_reserved_chk check (reserved_capacity >= 0),
  constraint shelter_profiles_used_chk
    check (current_occupancy + reserved_capacity <= total_capacity)
);

create unique index if not exists shelter_profiles_one_active_per_sede
  on public.shelter_profiles (
    organization_id,
    coalesce(branch_id, '00000000-0000-0000-0000-000000000000'::uuid)
  )
  where status = 'ACTIVE';

create index if not exists shelter_profiles_org_idx on public.shelter_profiles (organization_id);
create index if not exists shelter_profiles_status_idx on public.shelter_profiles (status);

create table if not exists public.shelter_pet_placements (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.shelter_profiles (id) on delete restrict,
  pet_id uuid not null references public.pets (id) on delete restrict,
  intake_type text not null,
  status text not null default 'RESERVED',
  admitted_at timestamptz not null default timezone('utc', now()),
  admitted_by uuid not null references public.users (id),
  source_organization_id uuid references public.organizations (id),
  source_user_id uuid references public.users (id),
  notes text,
  ended_at timestamptz,
  end_reason text,
  organizational_responsibility_id uuid references public.pet_responsibilities (id),
  related_foster_placement_id uuid,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint shelter_pet_intake_chk
    check (intake_type = any (array['RESCUE','OWNER_SURRENDER','TRANSFER','FOSTER_RETURN','FOUND','OTHER']::text[])),
  constraint shelter_pet_status_chk
    check (status = any (array[
      'RESERVED','ACTIVE','QUARANTINE','MEDICAL_CARE','RELEASED','TRANSFERRED','ADOPTED','DECEASED','CANCELLED'
    ]::text[]))
);

create unique index if not exists shelter_pet_one_open_per_pet
  on public.shelter_pet_placements (pet_id)
  where status = any (array['RESERVED','ACTIVE','QUARANTINE','MEDICAL_CARE']::text[]);

create index if not exists shelter_pet_shelter_idx on public.shelter_pet_placements (shelter_profile_id);
create index if not exists shelter_pet_status_idx on public.shelter_pet_placements (status);
create index if not exists shelter_pet_pet_idx on public.shelter_pet_placements (pet_id);

create table if not exists public.shelter_volunteer_assignments (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.shelter_profiles (id) on delete cascade,
  user_id uuid not null references public.users (id) on delete restrict,
  role text not null,
  status text not null default 'INVITED',
  starts_at timestamptz not null default timezone('utc', now()),
  ends_at timestamptz,
  notes text,
  invited_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint shelter_vol_role_chk
    check (role = any (array[
      'ANIMAL_CARE','CLEANING','TRANSPORT','ADMINISTRATION','VETERINARY_SUPPORT','EVENT_SUPPORT','OTHER'
    ]::text[])),
  constraint shelter_vol_status_chk
    check (status = any (array['INVITED','ACTIVE','PAUSED','ENDED','REJECTED']::text[]))
);

create unique index if not exists shelter_vol_open_uniq
  on public.shelter_volunteer_assignments (shelter_profile_id, user_id, role)
  where status = any (array['INVITED','ACTIVE','PAUSED']::text[]);

create index if not exists shelter_vol_shelter_idx on public.shelter_volunteer_assignments (shelter_profile_id);
create index if not exists shelter_vol_user_idx on public.shelter_volunteer_assignments (user_id);
create index if not exists shelter_vol_status_idx on public.shelter_volunteer_assignments (status);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m11_require_authenticated()
returns uuid
language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v;
end;
$$;

create or replace function public._m11_recompute_availability(
  p_status text, p_capacity integer, p_occupancy integer, p_reserved integer
) returns text language sql immutable as $$
  select case
    when p_status is distinct from 'ACTIVE' then 'UNAVAILABLE'
    when greatest(coalesce(p_occupancy,0),0) + greatest(coalesce(p_reserved,0),0)
         >= greatest(coalesce(p_capacity,0),0) then 'FULL'
    when greatest(coalesce(p_occupancy,0),0) + greatest(coalesce(p_reserved,0),0) > 0
         then 'LIMITED'
    else 'AVAILABLE'
  end;
$$;

create or replace function public._m11_sync_shelter_capacity(p_shelter_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_occ integer;
  v_res integer;
begin
  select
    count(*) filter (where status in ('ACTIVE','QUARANTINE','MEDICAL_CARE')),
    count(*) filter (where status = 'RESERVED')
  into v_occ, v_res
  from public.shelter_pet_placements
  where shelter_profile_id = p_shelter_id;

  update public.shelter_profiles set
    current_occupancy = coalesce(v_occ, 0),
    reserved_capacity = coalesce(v_res, 0),
    updated_at = timezone('utc', now())
  where id = p_shelter_id;
end;
$$;

create or replace function public._m11_require_org_manage(p_org_id uuid, p_actor uuid, p_perm text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  if p_org_id is null then raise exception 'ORGANIZATION_NOT_ELIGIBLE'; end if;
  if not exists (
    select 1 from public.organizations o where o.id = p_org_id and o.status = 'ACTIVE'
  ) then
    raise exception 'ORGANIZATION_NOT_ELIGIBLE';
  end if;
  if not public.has_org_permission(p_org_id, p_perm) then
    raise exception 'SHELTER_FORBIDDEN';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. RLS
-- ---------------------------------------------------------------------------
alter table public.shelter_profiles enable row level security;
alter table public.shelter_pet_placements enable row level security;
alter table public.shelter_volunteer_assignments enable row level security;

drop policy if exists shelter_profiles_select_m11 on public.shelter_profiles;
drop policy if exists shelter_profiles_ins_m11 on public.shelter_profiles;
drop policy if exists shelter_profiles_upd_m11 on public.shelter_profiles;
drop policy if exists shelter_profiles_del_m11 on public.shelter_profiles;
create policy shelter_profiles_select_m11 on public.shelter_profiles for select to authenticated
  using (
    status = 'ACTIVE'
    or public.has_org_permission(organization_id, 'shelter.view')
  );
create policy shelter_profiles_ins_m11 on public.shelter_profiles for insert to authenticated with check (false);
create policy shelter_profiles_upd_m11 on public.shelter_profiles for update to authenticated using (false);
create policy shelter_profiles_del_m11 on public.shelter_profiles for delete to authenticated using (false);

drop policy if exists shelter_pets_select_m11 on public.shelter_pet_placements;
drop policy if exists shelter_pets_ins_m11 on public.shelter_pet_placements;
drop policy if exists shelter_pets_upd_m11 on public.shelter_pet_placements;
drop policy if exists shelter_pets_del_m11 on public.shelter_pet_placements;
create policy shelter_pets_select_m11 on public.shelter_pet_placements for select to authenticated
  using (
    exists (
      select 1 from public.shelter_profiles s
      where s.id = shelter_profile_id
        and public.has_org_permission(s.organization_id, 'shelter.pet.read')
    )
  );
create policy shelter_pets_ins_m11 on public.shelter_pet_placements for insert to authenticated with check (false);
create policy shelter_pets_upd_m11 on public.shelter_pet_placements for update to authenticated using (false);
create policy shelter_pets_del_m11 on public.shelter_pet_placements for delete to authenticated using (false);

drop policy if exists shelter_vol_select_m11 on public.shelter_volunteer_assignments;
drop policy if exists shelter_vol_ins_m11 on public.shelter_volunteer_assignments;
drop policy if exists shelter_vol_upd_m11 on public.shelter_volunteer_assignments;
drop policy if exists shelter_vol_del_m11 on public.shelter_volunteer_assignments;
create policy shelter_vol_select_m11 on public.shelter_volunteer_assignments for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from public.shelter_profiles s
      where s.id = shelter_profile_id
        and public.has_org_permission(s.organization_id, 'shelter.volunteer.read')
    )
  );
create policy shelter_vol_ins_m11 on public.shelter_volunteer_assignments for insert to authenticated with check (false);
create policy shelter_vol_upd_m11 on public.shelter_volunteer_assignments for update to authenticated using (false);
create policy shelter_vol_del_m11 on public.shelter_volunteer_assignments for delete to authenticated using (false);

revoke all on table public.shelter_profiles from public, anon;
revoke all on table public.shelter_pet_placements from public, anon;
revoke all on table public.shelter_volunteer_assignments from public, anon;
grant select on table public.shelter_profiles to authenticated;
grant select on table public.shelter_pet_placements to authenticated;
grant select on table public.shelter_volunteer_assignments to authenticated;
grant all on table public.shelter_profiles to service_role;
grant all on table public.shelter_pet_placements to service_role;
grant all on table public.shelter_volunteer_assignments to service_role;

-- ---------------------------------------------------------------------------
-- 4. RPCs perfil
-- ---------------------------------------------------------------------------
create or replace function public.m11_create_shelter_profile(
  p_organization_id uuid,
  p_display_name text,
  p_total_capacity integer,
  p_accepted_species text[] default '{}',
  p_description text default null,
  p_branch_id uuid default null,
  p_accepts_emergencies boolean default false,
  p_public_zone_text text default null,
  p_internal_address_ref text default null,
  p_activate boolean default false
) returns public.shelter_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_profiles;
  v_status text := case when p_activate then 'ACTIVE' else 'DRAFT' end;
begin
  perform public._m11_require_org_manage(p_organization_id, v_actor, 'shelter.manage');
  if coalesce(p_total_capacity, 0) <= 0 then raise exception 'SHELTER_CAPACITY_EXCEEDED'; end if;
  if p_activate and exists (
    select 1 from public.shelter_profiles s
    where s.organization_id = p_organization_id
      and coalesce(s.branch_id, '00000000-0000-0000-0000-000000000000'::uuid)
          = coalesce(p_branch_id, '00000000-0000-0000-0000-000000000000'::uuid)
      and s.status = 'ACTIVE'
  ) then
    raise exception 'SHELTER_ALREADY_EXISTS';
  end if;

  insert into public.shelter_profiles (
    organization_id, branch_id, display_name, description, status, total_capacity,
    accepted_species, accepts_emergencies, public_zone_text, internal_address_ref
  ) values (
    p_organization_id, p_branch_id, trim(p_display_name), nullif(trim(coalesce(p_description,'')),''),
    v_status, p_total_capacity, coalesce(p_accepted_species, '{}'), coalesce(p_accepts_emergencies, false),
    nullif(trim(coalesce(p_public_zone_text,'')),''), nullif(trim(coalesce(p_internal_address_ref,'')),'')
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_update_shelter_profile(
  p_shelter_id uuid,
  p_display_name text,
  p_total_capacity integer,
  p_accepted_species text[] default '{}',
  p_description text default null,
  p_accepts_emergencies boolean default false,
  p_public_zone_text text default null,
  p_internal_address_ref text default null
) returns public.shelter_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_profiles;
begin
  select * into v_row from public.shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  perform public._m11_require_org_manage(v_row.organization_id, v_actor, 'shelter.manage');
  if coalesce(p_total_capacity, 0) <= 0 then raise exception 'SHELTER_CAPACITY_EXCEEDED'; end if;
  if p_total_capacity < (v_row.current_occupancy + v_row.reserved_capacity) then
    raise exception 'SHELTER_CAPACITY_EXCEEDED';
  end if;
  update public.shelter_profiles set
    display_name = trim(p_display_name),
    description = nullif(trim(coalesce(p_description,'')),''),
    total_capacity = p_total_capacity,
    accepted_species = coalesce(p_accepted_species, '{}'),
    accepts_emergencies = coalesce(p_accepts_emergencies, false),
    public_zone_text = nullif(trim(coalesce(p_public_zone_text,'')),''),
    internal_address_ref = nullif(trim(coalesce(p_internal_address_ref,'')),''),
    updated_at = timezone('utc', now())
  where id = p_shelter_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_change_shelter_status(p_shelter_id uuid, p_status text)
returns public.shelter_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_profiles;
  v_status text := upper(trim(p_status));
begin
  select * into v_row from public.shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  perform public._m11_require_org_manage(v_row.organization_id, v_actor, 'shelter.manage');
  if v_status = 'ACTIVE' then
    perform public._m11_require_org_manage(v_row.organization_id, v_actor, 'shelter.manage');
    if exists (
      select 1 from public.shelter_profiles s
      where s.id <> p_shelter_id
        and s.organization_id = v_row.organization_id
        and coalesce(s.branch_id, '00000000-0000-0000-0000-000000000000'::uuid)
            = coalesce(v_row.branch_id, '00000000-0000-0000-0000-000000000000'::uuid)
        and s.status = 'ACTIVE'
    ) then raise exception 'SHELTER_ALREADY_EXISTS'; end if;
  end if;
  update public.shelter_profiles set status = v_status, updated_at = timezone('utc', now())
  where id = p_shelter_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_get_shelter_profile(p_shelter_id uuid)
returns public.shelter_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_row public.shelter_profiles;
begin
  perform public._m11_require_authenticated();
  select * into v_row from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  return v_row;
end;
$$;

create or replace function public.m11_get_my_shelter_profiles()
returns setof public.shelter_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m11_require_authenticated();
begin
  return query
  select s.* from public.shelter_profiles s
  where public.has_org_permission(s.organization_id, 'shelter.view')
  order by s.updated_at desc;
end;
$$;

create or replace function public.m11_list_public_shelters()
returns setof public.shelter_profiles
language sql stable security definer set search_path = public as $$
  select * from public.shelter_profiles where status = 'ACTIVE' order by display_name;
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs mascotas
-- ---------------------------------------------------------------------------
create or replace function public.m11_reserve_shelter_capacity(
  p_shelter_id uuid, p_pet_id uuid, p_intake_type text, p_notes text default null
) returns public.shelter_pet_placements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
  v_pet public.pets;
  v_row public.shelter_pet_placements;
  v_resp uuid;
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if v_s.status <> 'ACTIVE' then raise exception 'SHELTER_NOT_ACTIVE'; end if;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.pet.intake');
  select * into v_pet from public.pets where id = p_pet_id;
  if not found then raise exception 'PET_NOT_FOUND'; end if;
  if upper(coalesce(v_pet.status,'')) = 'DECEASED' then raise exception 'SHELTER_PET_NOT_ELIGIBLE'; end if;
  if exists (
    select 1 from public.shelter_pet_placements p
    where p.pet_id = p_pet_id and p.status in ('RESERVED','ACTIVE','QUARANTINE','MEDICAL_CARE')
  ) then raise exception 'SHELTER_PET_ALREADY_ACTIVE'; end if;
  if v_s.current_occupancy + v_s.reserved_capacity >= v_s.total_capacity then
    raise exception 'SHELTER_FULL';
  end if;

  insert into public.pet_responsibilities (
    pet_id, role_code, person_id, organization_id, status, starts_at, created_by, accepted_at, reason
  ) values (
    p_pet_id, 'CO_RESPONSIBLE', null, v_s.organization_id, 'ACTIVE',
    timezone('utc', now()), v_actor, timezone('utc', now()), 'M11 shelter intake'
  ) returning id into v_resp;

  insert into public.shelter_pet_placements (
    shelter_profile_id, pet_id, intake_type, status, admitted_by,
    source_organization_id, source_user_id, notes, organizational_responsibility_id
  ) values (
    p_shelter_id, p_pet_id, upper(trim(p_intake_type)), 'RESERVED', v_actor,
    v_s.organization_id, v_actor, nullif(trim(coalesce(p_notes,'')),''), v_resp
  ) returning * into v_row;

  perform public._m11_sync_shelter_capacity(p_shelter_id);
  select * into v_row from public.shelter_pet_placements where id = v_row.id;
  return v_row;
end;
$$;

create or replace function public.m11_admit_pet(
  p_shelter_id uuid, p_pet_id uuid, p_intake_type text,
  p_notes text default null, p_related_foster_placement_id uuid default null
) returns public.shelter_pet_placements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
  v_pet public.pets;
  v_row public.shelter_pet_placements;
  v_resp uuid;
  v_foster uuid := p_related_foster_placement_id;
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id for update;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if v_s.status <> 'ACTIVE' then raise exception 'SHELTER_NOT_ACTIVE'; end if;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.pet.intake');
  select * into v_pet from public.pets where id = p_pet_id;
  if not found then raise exception 'PET_NOT_FOUND'; end if;
  if upper(coalesce(v_pet.status,'')) = 'DECEASED' then raise exception 'SHELTER_PET_NOT_ELIGIBLE'; end if;

  -- Promote existing RESERVED for same pet+shelter
  select * into v_row from public.shelter_pet_placements
  where shelter_profile_id = p_shelter_id and pet_id = p_pet_id and status = 'RESERVED'
  for update;
  if found then
    update public.shelter_pet_placements set
      status = 'ACTIVE', updated_at = timezone('utc', now()),
      notes = coalesce(nullif(trim(coalesce(p_notes,'')),''), notes)
    where id = v_row.id returning * into v_row;
    perform public._m11_sync_shelter_capacity(p_shelter_id);
    return v_row;
  end if;

  if exists (
    select 1 from public.shelter_pet_placements p
    where p.pet_id = p_pet_id and p.status in ('RESERVED','ACTIVE','QUARANTINE','MEDICAL_CARE')
  ) then raise exception 'SHELTER_PET_ALREADY_ACTIVE'; end if;
  if v_s.current_occupancy + v_s.reserved_capacity >= v_s.total_capacity then
    raise exception 'SHELTER_FULL';
  end if;

  -- M10 FOSTER_RETURN: complete active foster placement if present
  if upper(trim(p_intake_type)) = 'FOSTER_RETURN' and to_regclass('public.foster_placements') is not null then
    update public.foster_placements set
      status = 'COMPLETED',
      ended_at = timezone('utc', now()),
      end_reason = coalesce(end_reason, 'FOSTER_RETURN'),
      ended_by = v_actor,
      updated_at = timezone('utc', now())
    where pet_id = p_pet_id and status = 'ACTIVE'
    returning id into v_foster;
  end if;

  insert into public.pet_responsibilities (
    pet_id, role_code, person_id, organization_id, status, starts_at, created_by, accepted_at, reason
  ) values (
    p_pet_id, 'CO_RESPONSIBLE', null, v_s.organization_id, 'ACTIVE',
    timezone('utc', now()), v_actor, timezone('utc', now()), 'M11 shelter intake'
  ) returning id into v_resp;

  insert into public.shelter_pet_placements (
    shelter_profile_id, pet_id, intake_type, status, admitted_by,
    source_organization_id, source_user_id, notes, organizational_responsibility_id,
    related_foster_placement_id
  ) values (
    p_shelter_id, p_pet_id, upper(trim(p_intake_type)), 'ACTIVE', v_actor,
    v_s.organization_id, v_actor, nullif(trim(coalesce(p_notes,'')),''), v_resp, v_foster
  ) returning * into v_row;

  perform public._m11_sync_shelter_capacity(p_shelter_id);
  select * into v_row from public.shelter_pet_placements where id = v_row.id;
  return v_row;
end;
$$;

create or replace function public.m11_change_pet_placement_status(p_placement_id uuid, p_status text)
returns public.shelter_pet_placements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_pet_placements;
  v_s public.shelter_profiles;
  v_status text := upper(trim(p_status));
begin
  select * into v_row from public.shelter_pet_placements where id = p_placement_id for update;
  if not found then raise exception 'SHELTER_PET_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.pet.intake');
  if v_row.status not in ('RESERVED','ACTIVE','QUARANTINE','MEDICAL_CARE') then
    raise exception 'SHELTER_PET_INVALID_TRANSITION';
  end if;
  if v_status not in ('RESERVED','ACTIVE','QUARANTINE','MEDICAL_CARE') then
    raise exception 'SHELTER_PET_INVALID_TRANSITION';
  end if;
  if v_row.status = v_status then return v_row; end if;
  update public.shelter_pet_placements set status = v_status, updated_at = timezone('utc', now())
  where id = p_placement_id returning * into v_row;
  perform public._m11_sync_shelter_capacity(v_row.shelter_profile_id);
  return v_row;
end;
$$;

create or replace function public.m11_release_pet(
  p_placement_id uuid, p_end_reason text, p_notes text default null
) returns public.shelter_pet_placements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_pet_placements;
  v_s public.shelter_profiles;
  v_reason text := upper(trim(p_end_reason));
  v_terminal text;
begin
  select * into v_row from public.shelter_pet_placements where id = p_placement_id for update;
  if not found then raise exception 'SHELTER_PET_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.pet.release');
  if v_row.status in ('RELEASED','ADOPTED','TRANSFERRED','DECEASED','CANCELLED') then
    return v_row;
  end if;
  if v_row.status not in ('RESERVED','ACTIVE','QUARANTINE','MEDICAL_CARE') then
    raise exception 'SHELTER_PET_INVALID_TRANSITION';
  end if;
  v_terminal := case v_reason
    when 'ADOPTED' then 'ADOPTED'
    when 'DECEASED' then 'DECEASED'
    when 'TRANSFERRED' then 'TRANSFERRED'
    when 'FOSTERED' then 'TRANSFERRED'
    else 'RELEASED'
  end;

  update public.shelter_pet_placements set
    status = v_terminal,
    ended_at = timezone('utc', now()),
    end_reason = v_reason,
    notes = coalesce(nullif(trim(coalesce(p_notes,'')),''), notes),
    updated_at = timezone('utc', now())
  where id = p_placement_id returning * into v_row;

  -- Revoke org CO_RESPONSIBLE only; PRINCIPAL untouched
  if v_row.organizational_responsibility_id is not null then
    update public.pet_responsibilities set
      status = 'REVOKED',
      revoked_at = timezone('utc', now()),
      revoked_by = v_actor,
      ends_at = coalesce(ends_at, timezone('utc', now()))
    where id = v_row.organizational_responsibility_id
      and role_code = 'CO_RESPONSIBLE'
      and status = 'ACTIVE';
  end if;

  perform public._m11_sync_shelter_capacity(v_row.shelter_profile_id);
  return v_row;
end;
$$;

create or replace function public.m11_list_shelter_pets(p_shelter_id uuid)
returns setof public.shelter_pet_placements
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.pet.read') then
    raise exception 'SHELTER_FORBIDDEN';
  end if;
  return query
  select * from public.shelter_pet_placements
  where shelter_profile_id = p_shelter_id
  order by admitted_at desc;
end;
$$;

create or replace function public.m11_get_shelter_pet_placement(p_placement_id uuid)
returns public.shelter_pet_placements
language plpgsql stable security definer set search_path = public as $$
declare v_row public.shelter_pet_placements;
begin
  perform public._m11_require_authenticated();
  select * into v_row from public.shelter_pet_placements where id = p_placement_id;
  if not found then raise exception 'SHELTER_PET_NOT_FOUND'; end if;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPCs voluntarios
-- ---------------------------------------------------------------------------
create or replace function public.m11_invite_volunteer(
  p_shelter_id uuid, p_user_id uuid, p_role text, p_notes text default null
) returns public.shelter_volunteer_assignments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
  v_row public.shelter_volunteer_assignments;
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.volunteer.manage');
  if not exists (select 1 from public.users u where u.id = p_user_id) then
    raise exception 'SHELTER_VOLUNTEER_NOT_FOUND';
  end if;
  if exists (
    select 1 from public.shelter_volunteer_assignments a
    where a.shelter_profile_id = p_shelter_id and a.user_id = p_user_id
      and a.role = upper(trim(p_role))
      and a.status in ('INVITED','ACTIVE','PAUSED')
  ) then raise exception 'SHELTER_VOLUNTEER_ALREADY_ASSIGNED'; end if;

  insert into public.shelter_volunteer_assignments (
    shelter_profile_id, user_id, role, status, notes, invited_by
  ) values (
    p_shelter_id, p_user_id, upper(trim(p_role)), 'INVITED',
    nullif(trim(coalesce(p_notes,'')),''), v_actor
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_accept_volunteer_assignment(p_assignment_id uuid)
returns public.shelter_volunteer_assignments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_volunteer_assignments;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_volunteer_assignments where id = p_assignment_id for update;
  if not found then raise exception 'SHELTER_VOLUNTEER_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  if v_actor <> v_row.user_id
     and not public.has_org_permission(v_s.organization_id, 'shelter.volunteer.manage') then
    raise exception 'SHELTER_VOLUNTEER_FORBIDDEN';
  end if;
  if v_row.status = 'ACTIVE' then return v_row; end if;
  if v_row.status not in ('INVITED','PAUSED') then raise exception 'SHELTER_VOLUNTEER_INVALID_TRANSITION'; end if;
  update public.shelter_volunteer_assignments set status = 'ACTIVE', updated_at = timezone('utc', now())
  where id = p_assignment_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_pause_volunteer_assignment(p_assignment_id uuid)
returns public.shelter_volunteer_assignments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_volunteer_assignments;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_volunteer_assignments where id = p_assignment_id for update;
  if not found then raise exception 'SHELTER_VOLUNTEER_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.volunteer.manage');
  if v_row.status = 'PAUSED' then return v_row; end if;
  if v_row.status <> 'ACTIVE' then raise exception 'SHELTER_VOLUNTEER_INVALID_TRANSITION'; end if;
  update public.shelter_volunteer_assignments set status = 'PAUSED', updated_at = timezone('utc', now())
  where id = p_assignment_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_end_volunteer_assignment(p_assignment_id uuid)
returns public.shelter_volunteer_assignments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_volunteer_assignments;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_volunteer_assignments where id = p_assignment_id for update;
  if not found then raise exception 'SHELTER_VOLUNTEER_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.volunteer.manage');
  if v_row.status = 'ENDED' then return v_row; end if;
  update public.shelter_volunteer_assignments set
    status = 'ENDED', ends_at = timezone('utc', now()), updated_at = timezone('utc', now())
  where id = p_assignment_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_list_shelter_volunteers(p_shelter_id uuid)
returns setof public.shelter_volunteer_assignments
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.volunteer.read') then
    raise exception 'SHELTER_VOLUNTEER_FORBIDDEN';
  end if;
  return query
  select * from public.shelter_volunteer_assignments
  where shelter_profile_id = p_shelter_id
  order by starts_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. Grants
-- ---------------------------------------------------------------------------
revoke all on function public._m11_require_authenticated() from public;
revoke all on function public._m11_recompute_availability(text,integer,integer,integer) from public;
revoke all on function public._m11_sync_shelter_capacity(uuid) from public;
revoke all on function public._m11_require_org_manage(uuid,uuid,text) from public;

revoke all on function public.m11_create_shelter_profile(uuid,text,integer,text[],text,uuid,boolean,text,text,boolean) from public;
revoke all on function public.m11_update_shelter_profile(uuid,text,integer,text[],text,boolean,text,text) from public;
revoke all on function public.m11_change_shelter_status(uuid,text) from public;
revoke all on function public.m11_get_shelter_profile(uuid) from public;
revoke all on function public.m11_get_my_shelter_profiles() from public;
revoke all on function public.m11_list_public_shelters() from public;
revoke all on function public.m11_reserve_shelter_capacity(uuid,uuid,text,text) from public;
revoke all on function public.m11_admit_pet(uuid,uuid,text,text,uuid) from public;
revoke all on function public.m11_change_pet_placement_status(uuid,text) from public;
revoke all on function public.m11_release_pet(uuid,text,text) from public;
revoke all on function public.m11_list_shelter_pets(uuid) from public;
revoke all on function public.m11_get_shelter_pet_placement(uuid) from public;
revoke all on function public.m11_invite_volunteer(uuid,uuid,text,text) from public;
revoke all on function public.m11_accept_volunteer_assignment(uuid) from public;
revoke all on function public.m11_pause_volunteer_assignment(uuid) from public;
revoke all on function public.m11_end_volunteer_assignment(uuid) from public;
revoke all on function public.m11_list_shelter_volunteers(uuid) from public;

grant execute on function public.m11_create_shelter_profile(uuid,text,integer,text[],text,uuid,boolean,text,text,boolean) to authenticated;
grant execute on function public.m11_update_shelter_profile(uuid,text,integer,text[],text,boolean,text,text) to authenticated;
grant execute on function public.m11_change_shelter_status(uuid,text) to authenticated;
grant execute on function public.m11_get_shelter_profile(uuid) to authenticated;
grant execute on function public.m11_get_my_shelter_profiles() to authenticated;
grant execute on function public.m11_list_public_shelters() to authenticated;
grant execute on function public.m11_reserve_shelter_capacity(uuid,uuid,text,text) to authenticated;
grant execute on function public.m11_admit_pet(uuid,uuid,text,text,uuid) to authenticated;
grant execute on function public.m11_change_pet_placement_status(uuid,text) to authenticated;
grant execute on function public.m11_release_pet(uuid,text,text) to authenticated;
grant execute on function public.m11_list_shelter_pets(uuid) to authenticated;
grant execute on function public.m11_get_shelter_pet_placement(uuid) to authenticated;
grant execute on function public.m11_invite_volunteer(uuid,uuid,text,text) to authenticated;
grant execute on function public.m11_accept_volunteer_assignment(uuid) to authenticated;
grant execute on function public.m11_pause_volunteer_assignment(uuid) to authenticated;
grant execute on function public.m11_end_volunteer_assignment(uuid) to authenticated;
grant execute on function public.m11_list_shelter_volunteers(uuid) to authenticated;

comment on table public.shelter_profiles is
  'M11 operational shelter profile bound to M03 organization. Legacy public.shelters (006) remains for Sumate listing.';
comment on function public.m11_admit_pet(uuid,uuid,text,text,uuid) is
  'Admit pet: capacity check, org CO_RESPONSIBLE M08, optional M10 foster close. PRINCIPAL untouched.';

commit;
