-- LeoVer Canonical Baseline
-- Logical migration: 1108
-- Structured adopter profile and adoption requirements.
-- Does not edit 1000–1107.
--
-- Null means unknown. It is not false. Historical housing, experience,
-- hours and notes text stay readable and are not rewritten.
-- allergies stays on the profile and is not part of the public listing.
-- The form does not ask for it. DNI, salary and medical data are not added.
--
-- Matching is advisory. canon_apply_adoption is not changed and does not
-- reject an application because a trait is missing or incompatible.
-- LeoVer does not assign the animal.
--
-- FOUND_CASE stays unpublished. No legal found-to-adoption process is
-- defined, so temporary custody is not a publish right. Edit permission
-- alone is still not enough: the caller must be an active rescuer or an
-- organization member, the same rule as migration 1038.

alter table public.adoption_general_profiles
  add column if not exists has_outdoor_space boolean null,
  add column if not exists has_secure_enclosure boolean null,
  add column if not exists household_agrees boolean null,
  add column if not exists has_dogs boolean null,
  add column if not exists has_cats boolean null,
  add column if not exists has_other_animals boolean null,
  add column if not exists experience_band text null,
  add column if not exists hours_alone_band text null,
  add column if not exists can_vet_followup boolean null,
  add column if not exists can_medicate boolean null,
  add column if not exists accepts_special_needs boolean null,
  add column if not exists species_pref text null,
  add column if not exists size_pref text null,
  add column if not exists life_stage_pref text null;

alter table public.adoption_general_profiles
  drop constraint if exists adoption_general_profiles_experience_band_chk;
alter table public.adoption_general_profiles
  add constraint adoption_general_profiles_experience_band_chk
  check (experience_band is null or experience_band in ('NONE', 'SOME', 'SPECIAL_CARE'));

alter table public.adoption_general_profiles
  drop constraint if exists adoption_general_profiles_hours_alone_band_chk;
alter table public.adoption_general_profiles
  add constraint adoption_general_profiles_hours_alone_band_chk
  check (hours_alone_band is null or hours_alone_band in ('UNDER_4', 'H4_TO_8', 'OVER_8'));

alter table public.adoption_general_profiles
  drop constraint if exists adoption_general_profiles_species_pref_chk;
alter table public.adoption_general_profiles
  add constraint adoption_general_profiles_species_pref_chk
  check (species_pref is null or species_pref in ('DOG', 'CAT', 'ANY'));

alter table public.adoption_general_profiles
  drop constraint if exists adoption_general_profiles_size_pref_chk;
alter table public.adoption_general_profiles
  add constraint adoption_general_profiles_size_pref_chk
  check (size_pref is null or size_pref in ('SMALL', 'MEDIUM', 'LARGE'));

alter table public.adoption_general_profiles
  drop constraint if exists adoption_general_profiles_life_stage_pref_chk;
alter table public.adoption_general_profiles
  add constraint adoption_general_profiles_life_stage_pref_chk
  check (life_stage_pref is null or life_stage_pref in ('YOUNG', 'ADULT', 'SENIOR'));

alter table public.adoption_publications
  add column if not exists title text null,
  add column if not exists description text null,
  add column if not exists location_text text null,
  add column if not exists accepts_children boolean null,
  add column if not exists accepts_other_dogs boolean null,
  add column if not exists accepts_cats boolean null,
  add column if not exists needs_outdoor_space boolean null,
  add column if not exists needs_secure_enclosure boolean null,
  add column if not exists max_hours_alone text null,
  add column if not exists experience_required text null,
  add column if not exists accepts_no_experience boolean null,
  add column if not exists requires_special_care_experience boolean null;

alter table public.adoption_publications
  drop constraint if exists adoption_publications_max_hours_alone_chk;
alter table public.adoption_publications
  add constraint adoption_publications_max_hours_alone_chk
  check (max_hours_alone is null or max_hours_alone in ('UNDER_4', 'H4_TO_8', 'OVER_8'));

alter table public.adoption_publications
  drop constraint if exists adoption_publications_experience_required_chk;
alter table public.adoption_publications
  add constraint adoption_publications_experience_required_chk
  check (experience_required is null or experience_required in ('NONE', 'SOME', 'SPECIAL_CARE'));

-- The previous signature wrote every argument. New arguments default to null
-- and are appended so a 13-argument caller still matches.
drop function if exists public.canon_upsert_adoption_general_profile(
  text, text, boolean, integer, integer, text, text, text, text, text, text, text, text
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
  p_life_stage_pref text default null
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
    species_pref, size_pref, life_stage_pref
  ) values (
    auth.uid(), p_housing_type, p_housing_tenure, p_animals_allowed, p_adults_count, p_children_count,
    p_allergies, p_other_pets, p_experience, p_hours_alone, p_primary_caretaker, p_vet_reference, p_motivation, p_notes,
    p_has_outdoor_space, p_has_secure_enclosure, p_household_agrees, p_has_dogs, p_has_cats, p_has_other_animals,
    p_experience_band, p_hours_alone_band, p_can_vet_followup, p_can_medicate, p_accepts_special_needs,
    p_species_pref, p_size_pref, p_life_stage_pref
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
    updated_at = timezone('utc', now());
  return true;
end;
$$;

drop function if exists public.canon_create_adoption(uuid, uuid, text);

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
  p_requires_special_care_experience boolean default null
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
    max_hours_alone, experience_required, accepts_no_experience, requires_special_care_experience
  ) values (
    p_pet_id, auth.uid(), p_organization_id, p_note, p_title, p_description, p_location_text,
    p_accepts_children, p_accepts_other_dogs, p_accepts_cats, p_needs_outdoor_space, p_needs_secure_enclosure,
    p_max_hours_alone, p_experience_required, p_accepts_no_experience, p_requires_special_care_experience
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
  boolean, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean, boolean, text, text, text
) from public, anon;
grant execute on function public.canon_upsert_adoption_general_profile(
  text, text, boolean, integer, integer, text, text, text, text, text, text, text, text,
  boolean, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean, boolean, text, text, text
) to authenticated;

revoke all on function public.canon_create_adoption(
  uuid, uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean
) from public, anon;
grant execute on function public.canon_create_adoption(
  uuid, uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, text, text, boolean, boolean
) to authenticated;

revoke all on function public.canon_list_adoptions() from public, anon;
grant execute on function public.canon_list_adoptions() to authenticated;
