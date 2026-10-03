-- LeoVer Canonical Baseline
-- Logical migration: 1109
-- Escape protection and numeric hours alone.
-- Does not edit 1000–1108.
--
-- hours_alone_from / hours_alone_to are the domain value.
-- hours_alone_band remains the three screen shortcuts when they match.
-- A custom range stores null in the band and the numbers in the range.
-- Null is unknown. It is not false.
--
-- escape_protection is one answer: openings and outdoor access are protected
-- against escape. Species-specific examples stay in the client copy.
-- No street address is collected.
--
-- allergies stays historical. It is not asked, not matched and not listed.
-- DNI, salary, profession, marital status, medical data and social networks
-- are not added.
--
-- canon_apply_adoption is not changed. Matching stays advisory.

alter table public.adoption_general_profiles
  add column if not exists escape_protection boolean null,
  add column if not exists hours_alone_from integer null,
  add column if not exists hours_alone_to integer null;

alter table public.adoption_general_profiles
  drop constraint if exists adoption_general_profiles_hours_alone_range_chk;
alter table public.adoption_general_profiles
  add constraint adoption_general_profiles_hours_alone_range_chk
  check (
    (hours_alone_from is null or hours_alone_from >= 0)
    and (hours_alone_to is null or hours_alone_from is not null)
    and (hours_alone_to is null or hours_alone_to >= hours_alone_from)
  );

alter table public.adoption_publications
  add column if not exists requires_escape_protection boolean null,
  add column if not exists requires_landlord_pet_permission boolean null,
  add column if not exists accepts_other_animals boolean null,
  add column if not exists max_hours_from integer null,
  add column if not exists max_hours_to integer null;

alter table public.adoption_publications
  drop constraint if exists adoption_publications_max_hours_range_chk;
alter table public.adoption_publications
  add constraint adoption_publications_max_hours_range_chk
  check (
    (max_hours_from is null or max_hours_from >= 0)
    and (max_hours_to is null or max_hours_from is not null)
    and (max_hours_to is null or max_hours_to >= max_hours_from)
  );

drop function if exists public.canon_upsert_adoption_general_profile(
  text, text, boolean, integer, integer, text, text, text, text, text, text, text, text,
  boolean, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean, boolean, text, text, text
);

create function public.canon_upsert_adoption_general_profile(
  p_housing_type text default null,
  p_housing_tenure text default null,
  p_animals_allowed boolean default null,
  p_adults_count integer default null,
  p_children_count integer default null,
  p_allergies text default null,
  p_other_pets text default null,
  p_experience text default null,
  p_hours_alone text default null,
  p_primary_caretaker text default null,
  p_vet_reference text default null,
  p_motivation text default null,
  p_notes text default null,
  p_has_outdoor_space boolean default null,
  p_has_secure_enclosure boolean default null,
  p_household_agrees boolean default null,
  p_has_dogs boolean default null,
  p_has_cats boolean default null,
  p_has_other_animals boolean default null,
  p_experience_band text default null,
  p_hours_alone_band text default null,
  p_can_vet_followup boolean default null,
  p_can_medicate boolean default null,
  p_accepts_special_needs boolean default null,
  p_species_pref text default null,
  p_size_pref text default null,
  p_life_stage_pref text default null,
  p_escape_protection boolean default null,
  p_hours_alone_from integer default null,
  p_hours_alone_to integer default null
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.adoption_general_profiles (
    person_id, housing_type, housing_tenure, animals_allowed, adults_count, children_count,
    allergies, other_pets, experience, hours_alone, primary_caretaker, vet_reference, motivation, notes,
    has_outdoor_space, has_secure_enclosure, household_agrees, has_dogs, has_cats, has_other_animals,
    experience_band, hours_alone_band, can_vet_followup, can_medicate, accepts_special_needs,
    species_pref, size_pref, life_stage_pref,
    escape_protection, hours_alone_from, hours_alone_to
  ) values (
    auth.uid(), p_housing_type, p_housing_tenure, p_animals_allowed, p_adults_count, p_children_count,
    p_allergies, p_other_pets, p_experience, p_hours_alone, p_primary_caretaker, p_vet_reference, p_motivation, p_notes,
    p_has_outdoor_space, p_has_secure_enclosure, p_household_agrees, p_has_dogs, p_has_cats, p_has_other_animals,
    p_experience_band, p_hours_alone_band, p_can_vet_followup, p_can_medicate, p_accepts_special_needs,
    p_species_pref, p_size_pref, p_life_stage_pref,
    p_escape_protection, p_hours_alone_from, p_hours_alone_to
  )
  on conflict (person_id) do update set
    housing_type = excluded.housing_type,
    housing_tenure = excluded.housing_tenure,
    animals_allowed = excluded.animals_allowed,
    adults_count = excluded.adults_count,
    children_count = excluded.children_count,
    allergies = excluded.allergies,
    other_pets = excluded.other_pets,
    experience = excluded.experience,
    hours_alone = excluded.hours_alone,
    primary_caretaker = excluded.primary_caretaker,
    vet_reference = excluded.vet_reference,
    motivation = excluded.motivation,
    notes = excluded.notes,
    has_outdoor_space = excluded.has_outdoor_space,
    has_secure_enclosure = excluded.has_secure_enclosure,
    household_agrees = excluded.household_agrees,
    has_dogs = excluded.has_dogs,
    has_cats = excluded.has_cats,
    has_other_animals = excluded.has_other_animals,
    experience_band = excluded.experience_band,
    hours_alone_band = excluded.hours_alone_band,
    can_vet_followup = excluded.can_vet_followup,
    can_medicate = excluded.can_medicate,
    accepts_special_needs = excluded.accepts_special_needs,
    species_pref = excluded.species_pref,
    size_pref = excluded.size_pref,
    life_stage_pref = excluded.life_stage_pref,
    escape_protection = excluded.escape_protection,
    hours_alone_from = excluded.hours_alone_from,
    hours_alone_to = excluded.hours_alone_to,
    updated_at = timezone('utc', now());
  return true;
end;
$$;

drop function if exists public.canon_create_adoption(
  uuid, uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean
);

create function public.canon_create_adoption(
  p_pet_id uuid,
  p_organization_id uuid default null,
  p_note text default null,
  p_title text default null,
  p_description text default null,
  p_location_text text default null,
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
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_rescuer boolean;
  v_org_ok boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if exists (
    select 1 from public.pets
    where id = p_pet_id and origin_kind = 'FOUND_CASE'
  ) then
    raise exception 'FOUND_CASE_NOT_ADOPTABLE';
  end if;
  if not public._acl_can_edit_pet(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;

  select exists (
    select 1 from public.person_capabilities
    where user_id = auth.uid() and capability = 'RESCUER' and active
  ) into v_rescuer;

  v_org_ok := false;
  if p_organization_id is not null then
    v_org_ok := public._acl_org_member(auth.uid(), p_organization_id);
  end if;

  if not v_rescuer and not v_org_ok then
    raise exception 'ADOPTION_PUBLISH_FORBIDDEN';
  end if;

  if exists (
    select 1 from public.adoption_publications
    where pet_id = p_pet_id and status = 'OPEN'
  ) then
    raise exception 'ADOPTION_ALREADY_EXISTS';
  end if;

  insert into public.adoption_publications (
    pet_id, published_by, organization_id, note, title, description, location_text,
    accepts_children, accepts_other_dogs, accepts_cats, needs_outdoor_space, needs_secure_enclosure,
    max_hours_alone, experience_required, accepts_no_experience, requires_special_care_experience,
    requires_escape_protection, requires_landlord_pet_permission, accepts_other_animals,
    max_hours_from, max_hours_to
  ) values (
    p_pet_id, auth.uid(), p_organization_id, p_note, p_title, p_description, p_location_text,
    p_accepts_children, p_accepts_other_dogs, p_accepts_cats, p_needs_outdoor_space, p_needs_secure_enclosure,
    p_max_hours_alone, p_experience_required, p_accepts_no_experience, p_requires_special_care_experience,
    p_requires_escape_protection, p_requires_landlord_pet_permission, p_accepts_other_animals,
    p_max_hours_from, p_max_hours_to
  )
  returning id into v_id;
  perform public.canon_audit('adoption.create', 'adoption_publications', v_id, '{}'::jsonb);
  return v_id;
end;
$$;

create or replace function public.canon_list_adoptions()
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
      'id', a.id,
      'pet_id', a.pet_id,
      'public_code', a.public_code,
      'status', a.status,
      'note', a.note,
      'title', a.title,
      'description', a.description,
      'location_text', a.location_text,
      'published_by', a.published_by,
      'organization_id', a.organization_id,
      'name', p.name,
      'species', p.species_code,
      'sex', p.sex,
      'size', p.size,
      'locality_id', p.home_locality_id,
      'accepts_children', a.accepts_children,
      'accepts_other_dogs', a.accepts_other_dogs,
      'accepts_cats', a.accepts_cats,
      'needs_outdoor_space', a.needs_outdoor_space,
      'needs_secure_enclosure', a.needs_secure_enclosure,
      'max_hours_alone', a.max_hours_alone,
      'experience_required', a.experience_required,
      'accepts_no_experience', a.accepts_no_experience,
      'requires_special_care_experience', a.requires_special_care_experience,
      'requires_escape_protection', a.requires_escape_protection,
      'requires_landlord_pet_permission', a.requires_landlord_pet_permission,
      'accepts_other_animals', a.accepts_other_animals,
      'max_hours_from', a.max_hours_from,
      'max_hours_to', a.max_hours_to,
      'created_at', a.created_at
    ) order by a.created_at desc)
    from public.adoption_publications a
    join public.pets p on p.id = a.pet_id
    where a.status = 'OPEN' and p.lifecycle_status = 'ACTIVE'
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_upsert_adoption_general_profile(
  text, text, boolean, integer, integer, text, text, text, text, text, text, text, text,
  boolean, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean, boolean, text, text, text,
  boolean, integer, integer
) from public, anon;
grant execute on function public.canon_upsert_adoption_general_profile(
  text, text, boolean, integer, integer, text, text, text, text, text, text, text, text,
  boolean, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean, boolean, text, text, text,
  boolean, integer, integer
) to authenticated;

revoke all on function public.canon_create_adoption(
  uuid, uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean,
  boolean, boolean, boolean, integer, integer
) from public, anon;
grant execute on function public.canon_create_adoption(
  uuid, uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean,
  boolean, boolean, boolean, integer, integer
) to authenticated;

revoke all on function public.canon_list_adoptions() from public, anon;
grant execute on function public.canon_list_adoptions() to authenticated;
