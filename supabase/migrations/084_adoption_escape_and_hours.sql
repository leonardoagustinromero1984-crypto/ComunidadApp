-- LeoVer M09 — migración 084: protección contra escapes y horas numéricas.
-- Forward-only sobre 001–083. No reescribe 083.
-- El texto requirements sigue siendo información adicional.
-- Null en un requisito estructurado significa que no se declaró.
-- No se agregan DNI, salario, estado civil ni datos médicos.

begin;

alter table public.adoptions
  add column if not exists requires_escape_protection boolean null,
  add column if not exists requires_landlord_pet_permission boolean null,
  add column if not exists accepts_other_animals boolean null,
  add column if not exists max_hours_from integer null,
  add column if not exists max_hours_to integer null;

alter table public.adoptions
  drop constraint if exists adoptions_max_hours_range_chk;
alter table public.adoptions
  add constraint adoptions_max_hours_range_chk
  check (
    (max_hours_from is null or max_hours_from >= 0)
    and (max_hours_to is null or max_hours_from is not null)
    and (max_hours_to is null or max_hours_to >= max_hours_from)
  );

drop function if exists public.m09_create_adoption_publication(
  uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean
);

create function public.m09_create_adoption_publication(
  p_pet_id uuid,
  p_title text,
  p_description text,
  p_requirements text default '',
  p_location_text text default '',
  p_publish boolean default false,
  p_accepts_children boolean default null,
  p_accepts_other_dogs boolean default null,
  p_accepts_cats boolean default null,
  p_needs_outdoor_space boolean default null,
  p_needs_secure_enclosure boolean default null,
  p_max_hours_alone text default null,
  p_experience_required text default null,
  p_accepts_no_experience boolean default null,
  p_requires_special_care_experience boolean default null,
  p_requires_escape_protection boolean default null,
  p_requires_landlord_pet_permission boolean default null,
  p_accepts_other_animals boolean default null,
  p_max_hours_from integer default null,
  p_max_hours_to integer default null
)
returns public.adoptions
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_pet public.pets;
  v_row public.adoptions;
  v_status text;
  v_name text;
begin
  if p_pet_id is null then
    raise exception 'PET_NOT_FOUND';
  end if;
  select * into v_pet from public.pets p where p.id = p_pet_id;
  if not found then
    raise exception 'PET_NOT_FOUND';
  end if;
  if v_pet.status = 'DECEASED' or v_pet.status = 'ARCHIVED' or v_pet.status <> 'ACTIVE' then
    raise exception 'PET_NOT_ADOPTABLE';
  end if;
  if not public._m09_actor_can_manage_pet(p_pet_id, v_actor) then
    raise exception 'FORBIDDEN';
  end if;
  if exists (
    select 1 from public.adoptions a
    where a.pet_id = p_pet_id
      and a.status = any (public.m09_adoption_open_statuses())
  ) then
    raise exception 'ADOPTION_ALREADY_EXISTS';
  end if;
  if coalesce(trim(p_title), '') = '' then
    raise exception 'ADOPTION_TITLE_REQUIRED';
  end if;
  if coalesce(trim(p_description), '') = '' then
    raise exception 'ADOPTION_DESCRIPTION_REQUIRED';
  end if;

  v_status := case when p_publish then 'PUBLISHED' else 'DRAFT' end;
  select coalesce(nullif(trim(u.display_name), ''), u.name, 'Usuario') into v_name
  from public.users u where u.id = v_actor;

  insert into public.adoptions (
    publisher_id, publisher_name, shelter_id, pet_id,
    name, title, description, requirements, location, location_text,
    photo_url, species, sex, age_years, age_months, size,
    status, published_at, created_at, updated_at,
    accepts_children, accepts_other_dogs, accepts_cats,
    needs_outdoor_space, needs_secure_enclosure, max_hours_alone,
    experience_required, accepts_no_experience, requires_special_care_experience,
    requires_escape_protection, requires_landlord_pet_permission, accepts_other_animals,
    max_hours_from, max_hours_to
  ) values (
    v_actor,
    coalesce(v_name, 'Usuario'),
    null,
    p_pet_id,
    v_pet.name,
    trim(p_title),
    trim(p_description),
    coalesce(p_requirements, ''),
    coalesce(nullif(trim(p_location_text), ''), ''),
    coalesce(nullif(trim(p_location_text), ''), ''),
    v_pet.photo_url,
    v_pet.species,
    v_pet.sex,
    v_pet.age_years,
    v_pet.age_months,
    v_pet.size,
    v_status,
    case when p_publish then timezone('utc', now()) else null end,
    timezone('utc', now()),
    timezone('utc', now()),
    p_accepts_children, p_accepts_other_dogs, p_accepts_cats,
    p_needs_outdoor_space, p_needs_secure_enclosure, p_max_hours_alone,
    p_experience_required, p_accepts_no_experience, p_requires_special_care_experience,
    p_requires_escape_protection, p_requires_landlord_pet_permission, p_accepts_other_animals,
    p_max_hours_from, p_max_hours_to
  )
  returning * into v_row;

  return v_row;
exception
  when unique_violation then
    raise exception 'ADOPTION_ALREADY_EXISTS';
end;
$$;

drop function if exists public.m09_update_adoption_publication(
  uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean
);

create function public.m09_update_adoption_publication(
  p_adoption_id uuid,
  p_title text,
  p_description text,
  p_requirements text default '',
  p_location_text text default '',
  p_accepts_children boolean default null,
  p_accepts_other_dogs boolean default null,
  p_accepts_cats boolean default null,
  p_needs_outdoor_space boolean default null,
  p_needs_secure_enclosure boolean default null,
  p_max_hours_alone text default null,
  p_experience_required text default null,
  p_accepts_no_experience boolean default null,
  p_requires_special_care_experience boolean default null,
  p_requires_escape_protection boolean default null,
  p_requires_landlord_pet_permission boolean default null,
  p_accepts_other_animals boolean default null,
  p_max_hours_from integer default null,
  p_max_hours_to integer default null
)
returns public.adoptions
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoptions;
begin
  select * into v_row from public.adoptions a where a.id = p_adoption_id;
  if not found then
    raise exception 'ADOPTION_NOT_FOUND';
  end if;
  if v_row.status in ('CLOSED', 'ADOPTED') then
    raise exception 'ADOPTION_NOT_EDITABLE';
  end if;
  if v_row.publisher_id <> v_actor
     and (v_row.pet_id is null or not public._m09_actor_can_manage_pet(v_row.pet_id, v_actor)) then
    raise exception 'FORBIDDEN';
  end if;
  if coalesce(trim(p_title), '') = '' or coalesce(trim(p_description), '') = '' then
    raise exception 'ADOPTION_NOT_EDITABLE';
  end if;

  update public.adoptions a set
    title = trim(p_title),
    name = coalesce(a.name, trim(p_title)),
    description = trim(p_description),
    requirements = coalesce(p_requirements, ''),
    location_text = coalesce(p_location_text, ''),
    location = coalesce(nullif(trim(p_location_text), ''), a.location),
    accepts_children = p_accepts_children,
    accepts_other_dogs = p_accepts_other_dogs,
    accepts_cats = p_accepts_cats,
    needs_outdoor_space = p_needs_outdoor_space,
    needs_secure_enclosure = p_needs_secure_enclosure,
    max_hours_alone = p_max_hours_alone,
    experience_required = p_experience_required,
    accepts_no_experience = p_accepts_no_experience,
    requires_special_care_experience = p_requires_special_care_experience,
    requires_escape_protection = p_requires_escape_protection,
    requires_landlord_pet_permission = p_requires_landlord_pet_permission,
    accepts_other_animals = p_accepts_other_animals,
    max_hours_from = p_max_hours_from,
    max_hours_to = p_max_hours_to,
    updated_at = timezone('utc', now())
  where a.id = p_adoption_id
  returning * into v_row;

  return v_row;
end;
$$;

revoke all on function public.m09_create_adoption_publication(
  uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean,
  boolean, boolean, boolean, integer, integer
) from public;
grant execute on function public.m09_create_adoption_publication(
  uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean,
  boolean, boolean, boolean, integer, integer
) to authenticated;

revoke all on function public.m09_update_adoption_publication(
  uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean,
  boolean, boolean, boolean, integer, integer
) from public;
grant execute on function public.m09_update_adoption_publication(
  uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean,
  boolean, boolean, boolean, integer, integer
) to authenticated;

commit;
