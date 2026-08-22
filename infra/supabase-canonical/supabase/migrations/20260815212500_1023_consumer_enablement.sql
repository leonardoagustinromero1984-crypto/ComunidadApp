-- LeoVer Canonical Baseline
-- Logical migration: 1023
-- Consumer enablement. Forward-only. Does not edit 1000–1022.

-- ---------------------------------------------------------------------------
-- Authority helpers (least privilege, fixed search_path)
-- ---------------------------------------------------------------------------

create or replace function public._acl_pet_org_manage(p_user_id uuid, p_pet_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.pet_responsibility_links l
    where l.pet_id = p_pet_id
      and l.status = 'ACTIVE'
      and l.holder_kind = 'ORGANIZATION'
      and public._acl_org_permission(p_user_id, l.holder_organization_id, 'org.pets.manage')
  );
$$;

create or replace function public._acl_can_edit_pet(p_user_id uuid, p_pet_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public._acl_is_admin(p_user_id)
      or public._acl_pet_permission(p_user_id, p_pet_id, 'pet.edit')
      or public._acl_pet_org_manage(p_user_id, p_pet_id);
$$;

create or replace function public._acl_can_manage_declared_health(p_user_id uuid, p_pet_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public._acl_is_admin(p_user_id)
      or public._acl_pet_permission(p_user_id, p_pet_id, 'health.manage_declared')
      or public._acl_pet_org_manage(p_user_id, p_pet_id);
$$;

create or replace function public._acl_media_readable(p_user_id uuid, p_asset public.media_assets)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    p_asset.visibility = 'PUBLIC'
    or p_asset.owner_person_id = p_user_id
    or (
      p_asset.owner_kind = 'ORGANIZATION'
      and p_asset.owner_organization_id is not null
      and public._acl_org_member(p_user_id, p_asset.owner_organization_id)
    )
    or exists (
      select 1 from public.pets p
      where p.avatar_asset_id = p_asset.id
        and public._acl_pet_holder(p_user_id, p.id)
    )
    or public._acl_is_admin(p_user_id);
$$;

-- ---------------------------------------------------------------------------
-- Media SELECT (not authenticated → all)
-- ---------------------------------------------------------------------------

grant select on table public.media_assets to anon, authenticated;
grant select on table public.pets to authenticated;

create policy media_assets_select on public.media_assets
for select
using (
  visibility = 'PUBLIC'
  or owner_person_id = auth.uid()
  or (
    owner_kind = 'ORGANIZATION'
    and owner_organization_id is not null
    and public._acl_org_member(auth.uid(), owner_organization_id)
  )
  or exists (
    select 1 from public.pets p
    where p.avatar_asset_id = media_assets.id
      and public._acl_pet_holder(auth.uid(), p.id)
  )
  or public._acl_is_admin(auth.uid())
);

-- ---------------------------------------------------------------------------
-- Storage object policies (4 canonical buckets). PRIVATE BY DEFAULT.
-- No UPDATE/DELETE grants.
-- ---------------------------------------------------------------------------

create policy canon_storage_select on storage.objects
for select
to anon, authenticated
using (
  exists (
    select 1 from public.media_assets m
    where m.bucket = storage.objects.bucket_id
      and m.object_path = storage.objects.name
      and public._acl_media_readable(auth.uid(), m)
  )
);

create policy canon_storage_insert on storage.objects
for insert
to authenticated
with check (
  auth.uid() is not null
  and bucket_id in ('public-media', 'private-media', 'documents')
  and exists (
    select 1 from public.media_assets m
    where m.bucket = bucket_id
      and m.object_path = name
      and m.owner_kind = 'PERSON'
      and m.owner_person_id = auth.uid()
      and m.lifecycle_status in ('DRAFT', 'UPLOADING', 'READY')
  )
);

-- ---------------------------------------------------------------------------
-- canon_update_pet — editable identity/profile only. No avatar. No age_years.
-- ---------------------------------------------------------------------------

create or replace function public.canon_update_pet(
  p_pet_id uuid,
  p_name text,
  p_species text,
  p_breed_id uuid default null,
  p_sex text default null,
  p_size text default null,
  p_home_locality_id text default null,
  p_birth_precision text default 'UNKNOWN',
  p_birth_date date default null,
  p_birth_year integer default null,
  p_birth_month integer default null,
  p_estimated_age_months integer default null,
  p_estimated_as_of date default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_edit_pet(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  if p_name is null or btrim(p_name) = '' then
    raise exception 'PET_NAME_REQUIRED';
  end if;
  p_sex := nullif(btrim(coalesce(p_sex, '')), '');
  p_size := nullif(btrim(coalesce(p_size, '')), '');
  if p_sex is not null and p_sex not in ('FEMALE', 'MALE', 'UNKNOWN') then
    raise exception 'PET_SEX_INVALID';
  end if;
  if p_size is not null and p_size not in ('SMALL', 'MEDIUM', 'LARGE', 'UNKNOWN') then
    raise exception 'PET_SIZE_INVALID';
  end if;
  if p_birth_precision not in (
    'EXACT_DATE', 'MONTH_PRECISION', 'YEAR_PRECISION', 'ESTIMATED', 'UNKNOWN'
  ) then
    raise exception 'PET_BIRTH_PRECISION_INVALID';
  end if;
  if p_birth_precision = 'UNKNOWN' then
    p_birth_date := null;
    p_birth_year := null;
    p_birth_month := null;
    p_estimated_age_months := null;
    p_estimated_as_of := null;
  elsif p_birth_precision = 'EXACT_DATE' then
    if p_birth_date is null then raise exception 'PET_BIRTH_DATE_REQUIRED'; end if;
  elsif p_birth_precision = 'MONTH_PRECISION' then
    if p_birth_year is null or p_birth_month is null then
      raise exception 'PET_BIRTH_MONTH_REQUIRED';
    end if;
  elsif p_birth_precision = 'YEAR_PRECISION' then
    if p_birth_year is null then raise exception 'PET_BIRTH_YEAR_REQUIRED'; end if;
  elsif p_birth_precision = 'ESTIMATED' then
    if p_estimated_age_months is null or p_estimated_as_of is null then
      raise exception 'PET_BIRTH_ESTIMATE_REQUIRED';
    end if;
  end if;

  update public.pets
    set name = btrim(p_name),
        species_code = p_species,
        breed_id = coalesce(p_breed_id, breed_id),
        sex = coalesce(p_sex, sex),
        size = coalesce(p_size, size),
        home_locality_id = coalesce(p_home_locality_id, home_locality_id),
        birth_precision = p_birth_precision,
        birth_date = p_birth_date,
        birth_year = p_birth_year,
        birth_month = p_birth_month,
        estimated_age_months = p_estimated_age_months,
        estimated_as_of = p_estimated_as_of
    where id = p_pet_id;

  if not found then raise exception 'PET_NOT_FOUND'; end if;
  perform public.canon_audit('pet.update', 'pets', p_pet_id, jsonb_build_object('actor', auth.uid()));
  return p_pet_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- canon_set_pet_avatar — persist media_assets.id. NULL clears. No signed URL.
-- ---------------------------------------------------------------------------

create or replace function public.canon_set_pet_avatar(
  p_pet_id uuid,
  p_media_asset_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_asset public.media_assets%rowtype;
  v_prev uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_edit_pet(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;

  select avatar_asset_id into v_prev from public.pets where id = p_pet_id;
  if not found then raise exception 'PET_NOT_FOUND'; end if;

  if p_media_asset_id is not null then
    select * into v_asset from public.media_assets where id = p_media_asset_id;
    if not found then raise exception 'MEDIA_ASSET_NOT_FOUND'; end if;
    if v_asset.lifecycle_status not in ('READY', 'UPLOADING') then
      raise exception 'MEDIA_ASSET_NOT_READY';
    end if;
    if v_asset.owner_kind <> 'PERSON' or v_asset.owner_person_id <> auth.uid() then
      raise exception 'MEDIA_ASSET_NOT_OWNED';
    end if;
    if v_asset.mime_type not like 'image/%' then
      raise exception 'MEDIA_ASSET_NOT_IMAGE';
    end if;
    if v_asset.metadata ? 'signed_url' or v_asset.metadata ? 'signedUrl' then
      raise exception 'SIGNED_URL_FORBIDDEN';
    end if;
  end if;

  update public.pets
    set avatar_asset_id = p_media_asset_id
    where id = p_pet_id;

  if p_media_asset_id is not null then
    insert into public.media_asset_links (asset_id, owner_table, owner_id, purpose)
    values (p_media_asset_id, 'pets', p_pet_id, 'PET_AVATAR');
  end if;

  if v_prev is not null and v_prev is distinct from p_media_asset_id then
    update public.media_assets
      set lifecycle_status = 'ARCHIVED',
          archived_at = timezone('utc', now()),
          metadata = metadata || jsonb_build_object('orphan_candidate', true)
      where id = v_prev
        and not exists (select 1 from public.pets p where p.avatar_asset_id = v_prev)
        and not exists (select 1 from public.persons per where per.avatar_asset_id = v_prev);
  end if;

  perform public.canon_audit(
    'pet.set_avatar', 'pets', p_pet_id,
    jsonb_build_object('asset', p_media_asset_id, 'previous', v_prev)
  );
  return p_pet_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Declared health writes. Provenance forced to DECLARED. Actor = auth.uid().
-- ---------------------------------------------------------------------------

create or replace function public.canon_record_pet_allergy(p_pet_id uuid, p_name text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_manage_declared_health(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.pet_allergies (pet_id, name, source, actor_user_id)
  values (p_pet_id, btrim(p_name), 'DECLARED', auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_record_pet_medication(
  p_pet_id uuid, p_name text, p_instructions text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_manage_declared_health(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.pet_medications (pet_id, name, instructions, source, actor_user_id)
  values (p_pet_id, btrim(p_name), p_instructions, 'DECLARED', auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_record_pet_vaccination(
  p_pet_id uuid, p_vaccine_name text, p_administered_on date default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_manage_declared_health(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.pet_declared_vaccinations (
    pet_id, vaccine_name, administered_on, source, actor_user_id
  ) values (p_pet_id, btrim(p_vaccine_name), p_administered_on, 'DECLARED', auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_record_pet_parasite_treatment(
  p_pet_id uuid, p_kind text, p_product_name text, p_treated_on date
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_manage_declared_health(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  if p_kind not in ('DEWORMING', 'ANTIPARASITIC') then
    raise exception 'PARASITE_KIND_INVALID';
  end if;
  insert into public.pet_parasite_treatments (
    pet_id, kind, product_name, treated_on, source, actor_user_id
  ) values (p_pet_id, p_kind, p_product_name, p_treated_on, 'DECLARED', auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_record_pet_weight(
  p_pet_id uuid, p_kilograms numeric, p_measured_on date
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_manage_declared_health(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.pet_weights (pet_id, kilograms, measured_on, source, actor_user_id)
  values (p_pet_id, p_kilograms, p_measured_on, 'DECLARED', auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_record_pet_condition(p_pet_id uuid, p_name text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_manage_declared_health(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.pet_conditions (pet_id, name, source, actor_user_id)
  values (p_pet_id, btrim(p_name), 'DECLARED', auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_set_pet_care_instructions(
  p_pet_id uuid, p_feeding text default null, p_medication text default null, p_specials text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_manage_declared_health(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.pet_care_instructions (pet_id, feeding, medication, specials, updated_by)
  values (p_pet_id, p_feeding, p_medication, p_specials, auth.uid())
  on conflict (pet_id) do update
    set feeding = excluded.feeding,
        medication = excluded.medication,
        specials = excluded.specials,
        updated_by = auth.uid(),
        updated_at = timezone('utc', now());
  return p_pet_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Public adoption — sanitized, anon-safe, same privacy shape as pet/lost-found
-- ---------------------------------------------------------------------------

create or replace function public.canon_public_adoption(p_code text)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'public_code', a.public_code,
    'status', a.status,
    'is_active', true,
    'name', p.name,
    'species', p.species_code,
    'sex', p.sex,
    'size', p.size,
    'locality_id', p.home_locality_id
  )
  from public.adoption_publications a
  join public.pets p on p.id = a.pet_id
  where a.public_code = p_code
    and a.status = 'OPEN'
    and p.lifecycle_status = 'ACTIVE';
$$;

-- ---------------------------------------------------------------------------
-- Username availability — boolean only. Same normalization as unique index.
-- Does not reserve. Final authority remains persons_username_uidx.
-- ---------------------------------------------------------------------------

create or replace function public.canon_is_username_available(p_username text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select not exists (
    select 1 from public.persons
    where lower(username) = lower(btrim(coalesce(p_username, '')))
  );
$$;

-- ---------------------------------------------------------------------------
-- List holders — authorized actors only. Multi-owner + org responsible.
-- ---------------------------------------------------------------------------

create or replace function public.canon_list_pet_holders(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public._acl_pet_holder(auth.uid(), p_pet_id)
    or public._acl_is_admin(auth.uid())
  ) then
    raise exception 'FORBIDDEN';
  end if;

  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'link_id', l.id,
      'holder_kind', l.holder_kind,
      'role', l.role,
      'status', l.status,
      'person_id', l.holder_person_id,
      'organization_id', l.holder_organization_id,
      'display_name', coalesce(per.display_name, org.name),
      'avatar_asset_id', coalesce(per.avatar_asset_id, org.logo_asset_id)
    ) order by l.created_at)
    from public.pet_responsibility_links l
    left join public.persons per on per.user_id = l.holder_person_id
    left join public.organizations org on org.id = l.holder_organization_id
    where l.pet_id = p_pet_id
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

grant execute on function public.canon_public_adoption(text) to anon, authenticated;
grant execute on function public.canon_is_username_available(text) to anon, authenticated;

do $$
declare r record;
begin
  for r in
    select p.proname, pg_get_function_identity_arguments(p.oid) as args
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'canon_update_pet',
        'canon_set_pet_avatar',
        'canon_record_pet_allergy',
        'canon_record_pet_medication',
        'canon_record_pet_vaccination',
        'canon_record_pet_parasite_treatment',
        'canon_record_pet_weight',
        'canon_record_pet_condition',
        'canon_set_pet_care_instructions',
        'canon_list_pet_holders'
      )
  loop
    execute format('revoke all on function public.%I(%s) from public, anon', r.proname, r.args);
    execute format('grant execute on function public.%I(%s) to authenticated', r.proname, r.args);
  end loop;
end$$;
