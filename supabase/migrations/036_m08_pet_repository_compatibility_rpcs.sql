-- =============================================================================
-- LeoVer M08 Etapa 3C — migración 036: RPCs de compatibilidad de repositorios
-- Forward-fix sobre 035. NO reescribe 035. NO columnas nuevas.
-- LOCAL ONLY — STAGING NO AUTORIZADO — sin apply remoto.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Precondiciones
-- ---------------------------------------------------------------------------
do $$
begin
  if to_regclass('public.pets') is null then
    raise exception 'M08_036_PRE: public.pets missing';
  end if;
  if to_regclass('public.pet_responsibilities') is null then
    raise exception 'M08_036_PRE: pet_responsibilities missing (035 required)';
  end if;
  if not exists (
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'm08_require_capability'
  ) then
    raise exception 'M08_036_PRE: m08_require_capability missing';
  end if;
  if not exists (
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'm08_normalize_microchip'
  ) then
    raise exception 'M08_036_PRE: m08_normalize_microchip missing';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'pets' and column_name = 'birth_date'
  ) then
    raise exception 'M08_036_PRE: unexpected birth_date column (must not invent)';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2. Helpers internos (relación + capabilities efectivas)
-- ---------------------------------------------------------------------------

-- Precedencia: PRINCIPAL > CO_RESPONSIBLE > TEMPORARY_CUSTODIAN > AUTHORIZED > STAFF > NONE
create or replace function public._m08_actor_relation_code(
  p_pet_id uuid,
  p_actor uuid default auth.uid()
)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_role text;
begin
  if p_actor is null or p_pet_id is null then
    return 'NONE';
  end if;

  if exists (
    select 1
    from public.pet_responsibilities r
    where r.pet_id = p_pet_id
      and r.role_code = 'PRINCIPAL'
      and r.status = 'ACTIVE'
      and r.person_id = p_actor
      and (r.ends_at is null or r.ends_at > timezone('utc', now()))
  ) then
    return 'PRINCIPAL';
  end if;

  if exists (
    select 1
    from public.pet_responsibilities r
    join public.organization_memberships m
      on m.organization_id = r.organization_id
     and m.user_id = p_actor
     and m.status = 'ACTIVE'
     and m.role_code in ('OWNER', 'ADMIN')
    where r.pet_id = p_pet_id
      and r.role_code = 'PRINCIPAL'
      and r.status = 'ACTIVE'
      and r.organization_id is not null
      and (r.ends_at is null or r.ends_at > timezone('utc', now()))
  ) then
    return 'PRINCIPAL';
  end if;

  select r.role_code into v_role
  from public.pet_responsibilities r
  where r.pet_id = p_pet_id
    and r.status = 'ACTIVE'
    and r.role_code = 'CO_RESPONSIBLE'
    and r.person_id = p_actor
    and (r.ends_at is null or r.ends_at > timezone('utc', now()))
  limit 1;
  if v_role is not null then
    return 'CO_RESPONSIBLE';
  end if;

  if exists (
    select 1
    from public.pet_responsibilities r
    join public.organization_memberships m
      on m.organization_id = r.organization_id
     and m.user_id = p_actor
     and m.status = 'ACTIVE'
     and m.role_code in ('OWNER', 'ADMIN')
    where r.pet_id = p_pet_id
      and r.role_code = 'CO_RESPONSIBLE'
      and r.status = 'ACTIVE'
      and r.organization_id is not null
      and (r.ends_at is null or r.ends_at > timezone('utc', now()))
  ) then
    return 'CO_RESPONSIBLE';
  end if;

  if exists (
    select 1
    from public.pet_responsibilities r
    where r.pet_id = p_pet_id
      and r.status = 'ACTIVE'
      and r.role_code = 'TEMPORARY_CUSTODIAN'
      and r.person_id = p_actor
      and (r.ends_at is null or r.ends_at > timezone('utc', now()))
  ) then
    return 'TEMPORARY_CUSTODIAN';
  end if;

  if exists (
    select 1
    from public.pet_authorizations a
    where a.pet_id = p_pet_id
      and a.person_id = p_actor
      and a.status = 'ACTIVE'
      and (a.valid_until is null or a.valid_until > timezone('utc', now()))
      and a.valid_from <= timezone('utc', now())
  ) then
    return 'AUTHORIZED';
  end if;

  -- STAFF: permiso M02 pet.* sin grafo de responsabilidad/autorización
  if public.has_permission('pet.read')
     or public.has_permission('pet.view_history')
     or public.has_permission('pet.update')
     or public.has_permission('pet.manage_health')
     or public.has_permission('pet.archive')
     or public.has_permission('pet.restore')
     or public.has_permission('pet.mark_deceased')
     or public.has_permission('pet.manage_media')
     or public.has_permission('pet.manage_responsibilities')
     or public.has_permission('pet.manage_authorizations')
     or public.has_permission('pet.initiate_transfer')
     or public.has_permission('pet.accept_transfer')
     or public.has_permission('pet.cancel_transfer')
     or public.has_permission('pet.create')
  then
    return 'STAFF';
  end if;

  return 'NONE';
end;
$$;

create or replace function public._m08_actor_effective_capabilities(
  p_pet_id uuid,
  p_actor uuid default auth.uid()
)
returns text[]
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_caps text[] := array[]::text[];
  v_code text;
begin
  if p_actor is null or p_pet_id is null then
    return array[]::text[];
  end if;

  foreach v_code in array public.m08_pet_capability_codes()
  loop
    if public.m08_actor_has_capability(p_pet_id, v_code, p_actor) then
      v_caps := array_append(v_caps, v_code);
    end if;
  end loop;

  return v_caps;
end;
$$;

comment on function public._m08_actor_relation_code(uuid, uuid) is
  'M08 internal: relation precedence PRINCIPAL>CO_RESPONSIBLE>TEMPORARY_CUSTODIAN>AUTHORIZED>STAFF>NONE';
comment on function public._m08_actor_effective_capabilities(uuid, uuid) is
  'M08 internal: sorted allowlist capabilities without duplicates via m08_actor_has_capability';

-- ---------------------------------------------------------------------------
-- 3. m08_update_pet_profile
-- ---------------------------------------------------------------------------
create or replace function public.m08_update_pet_profile(
  p_pet_id uuid,
  p_name text,
  p_species text,
  p_breed text,
  p_sex text,
  p_size text,
  p_description text,
  p_age_years integer,
  p_age_months integer,
  p_color text,
  p_microchip_id text
)
returns public.pets
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_pet public.pets;
  v_name text;
  v_chip text;
begin
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status <> 'ACTIVE' then
    raise exception 'PET_NOT_ACTIVE';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.update');

  v_name := trim(coalesce(p_name, ''));
  if v_name = '' then
    raise exception 'PET_NAME_REQUIRED';
  end if;

  if p_age_years is null or p_age_years < 0 then
    raise exception 'PET_AGE_YEARS_INVALID';
  end if;
  if p_age_months is null or p_age_months < 0 then
    raise exception 'PET_AGE_MONTHS_INVALID';
  end if;

  -- Microchip: NULL o vacío → NULL; normalización vía helper (+ trigger)
  v_chip := nullif(trim(coalesce(p_microchip_id, '')), '');

  begin
    update public.pets
    set
      name = v_name,
      species = coalesce(nullif(trim(coalesce(p_species, '')), ''), 'UNKNOWN'),
      breed = case when p_breed is null then null else nullif(trim(p_breed), '') end,
      sex = coalesce(nullif(trim(coalesce(p_sex, '')), ''), 'UNKNOWN'),
      size = coalesce(nullif(trim(coalesce(p_size, '')), ''), 'UNKNOWN'),
      description = coalesce(p_description, ''),
      age_years = p_age_years,
      age_months = p_age_months,
      color = case when p_color is null then null else nullif(trim(p_color), '') end,
      microchip_id = v_chip,
      microchip_normalized = public.m08_normalize_microchip(v_chip),
      updated_at = timezone('utc', now())
    where id = p_pet_id
    returning * into v_pet;
  exception
    when unique_violation then
      raise exception 'PET_MICROCHIP_ACTIVE_CONFLICT';
  end;

  -- Emisión runtime m08.pet.updated diferida (catálogo 035); deuda M07 — no inventar PASS.
  return v_pet;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. m08_update_pet_health
-- ---------------------------------------------------------------------------
create or replace function public.m08_update_pet_health(
  p_pet_id uuid,
  p_vaccinations jsonb,
  p_reminders jsonb,
  p_last_deworming text,
  p_deworming_product text,
  p_last_flea_treatment text,
  p_flea_treatment_product text,
  p_sterilized text,
  p_last_vet_visit text,
  p_health_notes text,
  p_weight_kg real
)
returns public.pets
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_pet public.pets;
begin
  select * into v_pet from public.pets where id = p_pet_id for update;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status <> 'ACTIVE' then
    raise exception 'PET_NOT_ACTIVE';
  end if;

  perform public.m08_require_capability(p_pet_id, 'pet.manage_health');

  if p_weight_kg is not null and p_weight_kg < 0 then
    raise exception 'PET_WEIGHT_INVALID';
  end if;

  update public.pets
  set
    vaccinations = coalesce(p_vaccinations, '[]'::jsonb),
    reminders = coalesce(p_reminders, '[]'::jsonb),
    last_deworming = case when p_last_deworming is null then null else nullif(trim(p_last_deworming), '') end,
    deworming_product = case when p_deworming_product is null then null else nullif(trim(p_deworming_product), '') end,
    last_flea_treatment = case when p_last_flea_treatment is null then null else nullif(trim(p_last_flea_treatment), '') end,
    flea_treatment_product = case when p_flea_treatment_product is null then null else nullif(trim(p_flea_treatment_product), '') end,
    sterilized = case when p_sterilized is null then null else nullif(trim(p_sterilized), '') end,
    last_vet_visit = case when p_last_vet_visit is null then null else nullif(trim(p_last_vet_visit), '') end,
    health_notes = case when p_health_notes is null then null else nullif(trim(p_health_notes), '') end,
    weight_kg = p_weight_kg,
    updated_at = timezone('utc', now())
  where id = p_pet_id
  returning * into v_pet;

  -- Emisión runtime m08.pet.updated (HEALTH) diferida; deuda M07.
  return v_pet;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. m08_get_pet_access_context
-- ---------------------------------------------------------------------------
create or replace function public.m08_get_pet_access_context(p_pet_id uuid)
returns table (
  pet_id uuid,
  relation_code text,
  principal_person_id uuid,
  principal_organization_id uuid,
  capabilities text[],
  can_read boolean,
  can_update boolean,
  can_manage_health boolean,
  can_manage_media boolean,
  can_manage_responsibilities boolean,
  can_manage_authorizations boolean,
  can_initiate_transfer boolean,
  can_accept_transfer boolean,
  can_cancel_transfer boolean,
  can_archive boolean,
  can_restore boolean,
  can_mark_deceased boolean,
  can_view_history boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_exists boolean;
  v_can_read boolean;
  v_caps text[];
  v_rel text;
  v_pperson uuid;
  v_porg uuid;
begin
  select exists(select 1 from public.pets p where p.id = p_pet_id) into v_exists;
  if not v_exists then
    raise exception 'PET_NOT_FOUND';
  end if;

  v_can_read := public.m08_actor_can_read_pet(p_pet_id, v_actor);
  if not v_can_read then
    -- Sin filtrar datos privados: NONE + caps vacías + principals null
    pet_id := p_pet_id;
    relation_code := 'NONE';
    principal_person_id := null;
    principal_organization_id := null;
    capabilities := array[]::text[];
    can_read := false;
    can_update := false;
    can_manage_health := false;
    can_manage_media := false;
    can_manage_responsibilities := false;
    can_manage_authorizations := false;
    can_initiate_transfer := false;
    can_accept_transfer := false;
    can_cancel_transfer := false;
    can_archive := false;
    can_restore := false;
    can_mark_deceased := false;
    can_view_history := false;
    return next;
    return;
  end if;

  v_rel := public._m08_actor_relation_code(p_pet_id, v_actor);
  v_caps := public._m08_actor_effective_capabilities(p_pet_id, v_actor);

  select pr.person_id, pr.organization_id
    into v_pperson, v_porg
  from public.m08_current_principal(p_pet_id) pr;

  pet_id := p_pet_id;
  relation_code := v_rel;
  principal_person_id := v_pperson;
  principal_organization_id := v_porg;
  capabilities := v_caps;
  can_read := true;
  can_update := ('pet.update' = any (v_caps));
  can_manage_health := ('pet.manage_health' = any (v_caps));
  can_manage_media := ('pet.manage_media' = any (v_caps));
  can_manage_responsibilities := ('pet.manage_responsibilities' = any (v_caps));
  can_manage_authorizations := ('pet.manage_authorizations' = any (v_caps));
  can_initiate_transfer := ('pet.initiate_transfer' = any (v_caps));
  can_accept_transfer := ('pet.accept_transfer' = any (v_caps));
  can_cancel_transfer := ('pet.cancel_transfer' = any (v_caps));
  can_archive := ('pet.archive' = any (v_caps));
  can_restore := ('pet.restore' = any (v_caps));
  can_mark_deceased := ('pet.mark_deceased' = any (v_caps));
  can_view_history := ('pet.view_history' = any (v_caps));
  return next;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. m08_list_accessible_pets
-- ---------------------------------------------------------------------------
create or replace function public.m08_list_accessible_pets(
  p_status text default null
)
returns table (
  id uuid,
  owner_id uuid,
  name text,
  photo_url text,
  species text,
  sex text,
  age_years integer,
  age_months integer,
  size text,
  description text,
  vaccinations jsonb,
  last_deworming text,
  deworming_product text,
  last_flea_treatment text,
  flea_treatment_product text,
  sterilized text,
  microchip_id text,
  last_vet_visit text,
  health_notes text,
  weight_kg real,
  color text,
  breed text,
  personality text,
  location_text text,
  reminders jsonb,
  created_at timestamptz,
  updated_at timestamptz,
  status text,
  deceased_at timestamptz,
  archived_at timestamptz,
  microchip_normalized text,
  avatar_file_asset_id uuid,
  relation_code text,
  principal_person_id uuid,
  principal_organization_id uuid,
  capabilities text[],
  can_update boolean,
  can_manage_health boolean,
  can_manage_media boolean,
  can_archive boolean,
  can_mark_deceased boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m08_require_authenticated();
  v_status text;
begin
  if p_status is not null then
    v_status := upper(trim(p_status));
    if v_status <> all (public.m08_pet_lifecycle_statuses()) then
      raise exception 'PET_STATUS_FILTER_INVALID';
    end if;
  else
    v_status := null;
  end if;

  return query
  select
    p.id,
    p.owner_id,
    p.name,
    p.photo_url,
    p.species,
    p.sex,
    p.age_years,
    p.age_months,
    p.size,
    p.description,
    p.vaccinations,
    p.last_deworming,
    p.deworming_product,
    p.last_flea_treatment,
    p.flea_treatment_product,
    p.sterilized,
    p.microchip_id,
    p.last_vet_visit,
    p.health_notes,
    p.weight_kg,
    p.color,
    p.breed,
    p.personality,
    p.location_text,
    p.reminders,
    p.created_at,
    p.updated_at,
    p.status,
    p.deceased_at,
    p.archived_at,
    p.microchip_normalized,
    p.avatar_file_asset_id,
    public._m08_actor_relation_code(p.id, v_actor) as relation_code,
    pr.person_id as principal_person_id,
    pr.organization_id as principal_organization_id,
    public._m08_actor_effective_capabilities(p.id, v_actor) as capabilities,
    public.m08_actor_has_capability(p.id, 'pet.update', v_actor) as can_update,
    public.m08_actor_has_capability(p.id, 'pet.manage_health', v_actor) as can_manage_health,
    public.m08_actor_has_capability(p.id, 'pet.manage_media', v_actor) as can_manage_media,
    public.m08_actor_has_capability(p.id, 'pet.archive', v_actor) as can_archive,
    public.m08_actor_has_capability(p.id, 'pet.mark_deceased', v_actor) as can_mark_deceased
  from public.pets p
  left join lateral public.m08_current_principal(p.id) pr on true
  where public.m08_actor_can_read_pet(p.id, v_actor)
    and (v_status is null or p.status = v_status)
  order by p.updated_at desc nulls last, p.created_at desc nulls last, p.id;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. Grants / revokes
-- ---------------------------------------------------------------------------
revoke all on function public._m08_actor_relation_code(uuid, uuid) from public;
revoke all on function public._m08_actor_effective_capabilities(uuid, uuid) from public;
revoke all on function public._m08_actor_relation_code(uuid, uuid) from anon, authenticated;
revoke all on function public._m08_actor_effective_capabilities(uuid, uuid) from anon, authenticated;
grant execute on function public._m08_actor_relation_code(uuid, uuid) to service_role;
grant execute on function public._m08_actor_effective_capabilities(uuid, uuid) to service_role;

revoke all on function public.m08_update_pet_profile(uuid, text, text, text, text, text, text, integer, integer, text, text) from public;
revoke all on function public.m08_update_pet_health(uuid, jsonb, jsonb, text, text, text, text, text, text, text, real) from public;
revoke all on function public.m08_get_pet_access_context(uuid) from public;
revoke all on function public.m08_list_accessible_pets(text) from public;

revoke execute on function public.m08_update_pet_profile(uuid, text, text, text, text, text, text, integer, integer, text, text) from anon;
revoke execute on function public.m08_update_pet_health(uuid, jsonb, jsonb, text, text, text, text, text, text, text, real) from anon;
revoke execute on function public.m08_get_pet_access_context(uuid) from anon;
revoke execute on function public.m08_list_accessible_pets(text) from anon;

grant execute on function public.m08_update_pet_profile(uuid, text, text, text, text, text, text, integer, integer, text, text) to authenticated;
grant execute on function public.m08_update_pet_health(uuid, jsonb, jsonb, text, text, text, text, text, text, text, real) to authenticated;
grant execute on function public.m08_get_pet_access_context(uuid) to authenticated;
grant execute on function public.m08_list_accessible_pets(text) to authenticated;

grant execute on function public.m08_update_pet_profile(uuid, text, text, text, text, text, text, integer, integer, text, text) to service_role;
grant execute on function public.m08_update_pet_health(uuid, jsonb, jsonb, text, text, text, text, text, text, text, real) to service_role;
grant execute on function public.m08_get_pet_access_context(uuid) to service_role;
grant execute on function public.m08_list_accessible_pets(text) to service_role;

-- ---------------------------------------------------------------------------
-- 8. Comments
-- ---------------------------------------------------------------------------
comment on function public.m08_update_pet_profile(uuid, text, text, text, text, text, text, integer, integer, text, text) is
  'M08 036: full profile/identity block replace. Requires pet.update. Does not touch owner_id/status/avatar/health. Microchip soft-unique ACTIVE.';
comment on function public.m08_update_pet_health(uuid, jsonb, jsonb, text, text, text, text, text, text, text, real) is
  'M08 036: health-only block replace. Requires pet.manage_health. Does not touch profile/microchip/lifecycle/avatar.';
comment on function public.m08_get_pet_access_context(uuid) is
  'M08 036: flat access context; NONE leaks no principal/caps when unread.';
comment on function public.m08_list_accessible_pets(text) is
  'M08 036: accessible pets + access fields for Android DTO; no owner_id-only filter; no public profile showcase.';

-- STAGING NO AUTORIZADO until Etapa 4B. Public profile pets remain hidden
-- (no m08_list_public_profile_pets in 036).

-- ---------------------------------------------------------------------------
-- 9. Verificaciones finales
-- ---------------------------------------------------------------------------
do $$
declare
  v_rpc text;
begin
  foreach v_rpc in array array[
    'm08_update_pet_profile',
    'm08_update_pet_health',
    'm08_get_pet_access_context',
    'm08_list_accessible_pets'
  ]
  loop
    if not exists (
      select 1 from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = v_rpc
    ) then
      raise exception 'M08_036_FINAL: missing %', v_rpc;
    end if;
  end loop;

  if exists (
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'm08_list_public_profile_pets'
  ) then
    raise exception 'M08_036_FINAL: m08_list_public_profile_pets must not exist';
  end if;

  -- No birth_date inventada
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'pets' and column_name = 'birth_date'
  ) then
    raise exception 'M08_036_FINAL: birth_date must not exist';
  end if;
end;
$$;

commit;
