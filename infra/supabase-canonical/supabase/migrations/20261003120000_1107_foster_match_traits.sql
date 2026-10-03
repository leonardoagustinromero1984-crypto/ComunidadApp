-- LeoVer Canonical Baseline
-- Logical migration: 1107
-- Structured foster traits shared by the home and the found-animal request.
-- Does not edit 1000–1106.
--
-- Historical rows keep null traits. Null means unknown (animal) or no
-- preference (home). It is not false. Existing needs/notes text stays.
-- species_pref and age_pref stay as the legacy free-text hints. New boolean
-- columns are the structured contract. A null boolean does not overwrite
-- a historical hint; the client treats text as a positive hint only.
--
-- Notification: there is still no foster push fan-out. Eligible homes see
-- REQUESTED rows through canon_list_open_foster_requests (verified active
-- FOSTER capability and a base location). Trait matching narrows that pool.
-- Unknown animal facts do not drop a request. An explicit incompatibility
-- is rejected by canon_apply_to_foster_request. LeoVer does not select a home.
--
-- Selection now also refuses a missing, withdrawn, or inactive applicant.
-- Cancel exists for REQUESTED and MATCHED. ACTIVE still ends through
-- canon_complete_foster_transit.

alter table public.foster_care_requests
  add column if not exists size_band text null,
  add column if not exists life_stage text null,
  add column if not exists needs_medication boolean null,
  add column if not exists cohabits_dogs boolean null,
  add column if not exists cohabits_cats boolean null,
  add column if not exists cohabits_children boolean null,
  add column if not exists reduced_mobility boolean null,
  add column if not exists needs_isolation boolean null,
  add column if not exists additional_info text null;

alter table public.foster_care_requests
  drop constraint if exists foster_care_requests_size_band_chk;
alter table public.foster_care_requests
  add constraint foster_care_requests_size_band_chk
  check (size_band is null or size_band in ('SMALL', 'MEDIUM', 'LARGE'));

alter table public.foster_care_requests
  drop constraint if exists foster_care_requests_life_stage_chk;
alter table public.foster_care_requests
  add constraint foster_care_requests_life_stage_chk
  check (life_stage is null or life_stage in ('YOUNG', 'ADULT', 'SENIOR'));

alter table public.foster_profiles
  add column if not exists accepts_dogs boolean null,
  add column if not exists accepts_cats boolean null,
  add column if not exists accepts_small boolean null,
  add column if not exists accepts_medium boolean null,
  add column if not exists accepts_large boolean null,
  add column if not exists accepts_young boolean null,
  add column if not exists accepts_adult boolean null,
  add column if not exists accepts_senior boolean null,
  add column if not exists accepts_reduced_mobility boolean null,
  add column if not exists can_isolate boolean null,
  add column if not exists lives_with_dogs boolean null,
  add column if not exists lives_with_cats boolean null,
  add column if not exists lives_with_children boolean null;

-- Explicit incompatibility only. Null on either side stays compatible.
create or replace function public._canon_foster_traits_compatible(
  p_request_id uuid,
  p_foster_user_id uuid
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_species text;
  v_size text;
  v_stage text;
  v_med boolean;
  v_dogs boolean;
  v_cats boolean;
  v_children boolean;
  v_mobility boolean;
  v_isolation boolean;
  v_home public.foster_profiles%rowtype;
begin
  select
    p.species_code,
    coalesce(r.size_band, case when p.size in ('SMALL', 'MEDIUM', 'LARGE') then p.size else null end),
    r.life_stage,
    r.needs_medication,
    r.cohabits_dogs,
    r.cohabits_cats,
    r.cohabits_children,
    r.reduced_mobility,
    r.needs_isolation
  into v_species, v_size, v_stage, v_med, v_dogs, v_cats, v_children, v_mobility, v_isolation
  from public.foster_care_requests r
  join public.pets p on p.id = r.pet_id
  where r.id = p_request_id;
  if not found then
    return false;
  end if;

  select * into v_home from public.foster_profiles fp where fp.user_id = p_foster_user_id;
  if not found or v_home.active is not true or coalesce(v_home.capacity, 0) <= 0 then
    return false;
  end if;
  if not public._canon_person_has_base_location(p_foster_user_id) then
    return false;
  end if;

  if v_species = 'DOG' and v_home.accepts_dogs is false then return false; end if;
  if v_species = 'CAT' and v_home.accepts_cats is false then return false; end if;
  if v_size = 'SMALL' and v_home.accepts_small is false then return false; end if;
  if v_size = 'MEDIUM' and v_home.accepts_medium is false then return false; end if;
  if v_size = 'LARGE' and v_home.accepts_large is false then return false; end if;
  if v_stage = 'YOUNG' and v_home.accepts_young is false then return false; end if;
  if v_stage = 'ADULT' and v_home.accepts_adult is false then return false; end if;
  if v_stage = 'SENIOR' and v_home.accepts_senior is false then return false; end if;
  if v_med is true and v_home.accepts_treatment is false then return false; end if;
  if v_dogs is false and v_home.lives_with_dogs is true then return false; end if;
  if v_cats is false and v_home.lives_with_cats is true then return false; end if;
  if v_children is false and v_home.lives_with_children is true then return false; end if;
  if v_mobility is true and v_home.accepts_reduced_mobility is false then return false; end if;
  if v_isolation is true and v_home.can_isolate is false then return false; end if;
  return true;
end;
$$;

drop function if exists public.canon_request_foster_for_pet(uuid, text, text);

create or replace function public.canon_request_foster_for_pet(
  p_pet_id uuid,
  p_needs text default null,
  p_notes text default null,
  p_size_band text default null,
  p_life_stage text default null,
  p_needs_medication boolean default null,
  p_cohabits_dogs boolean default null,
  p_cohabits_cats boolean default null,
  p_cohabits_children boolean default null,
  p_reduced_mobility boolean default null,
  p_needs_isolation boolean default null,
  p_additional_info text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_size text := nullif(upper(btrim(coalesce(p_size_band, ''))), '');
  v_stage text := nullif(upper(btrim(coalesce(p_life_stage, ''))), '');
  v_extra text := nullif(btrim(coalesce(p_additional_info, p_notes, '')), '');
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_pet_id is null then raise exception 'VALIDATION'; end if;
  if not public._acl_pet_holder(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v_size is not null and v_size not in ('SMALL', 'MEDIUM', 'LARGE') then
    raise exception 'VALIDATION';
  end if;
  if v_stage is not null and v_stage not in ('YOUNG', 'ADULT', 'SENIOR') then
    raise exception 'VALIDATION';
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended('leover-foster-request:' || p_pet_id::text, 1102)
  );

  select r.id
    into v_id
    from public.foster_care_requests r
   where r.pet_id = p_pet_id
     and r.status in ('REQUESTED', 'MATCHED', 'ACTIVE')
   order by r.created_at
   limit 1
   for update;
  if v_id is not null then
    return v_id;
  end if;

  begin
    insert into public.foster_care_requests (
      pet_id, requested_by, needs, notes,
      size_band, life_stage, needs_medication,
      cohabits_dogs, cohabits_cats, cohabits_children,
      reduced_mobility, needs_isolation, additional_info
    ) values (
      p_pet_id, auth.uid(), nullif(btrim(coalesce(p_needs, '')), ''), v_extra,
      v_size, v_stage, p_needs_medication,
      p_cohabits_dogs, p_cohabits_cats, p_cohabits_children,
      p_reduced_mobility, p_needs_isolation, v_extra
    )
    returning id into v_id;
    return v_id;
  exception
    when unique_violation then
      select r.id
        into v_id
        from public.foster_care_requests r
       where r.pet_id = p_pet_id
         and r.status in ('REQUESTED', 'MATCHED', 'ACTIVE')
       order by r.created_at
       limit 1;
      if v_id is null then
        raise;
      end if;
      return v_id;
  end;
end;
$$;

drop function if exists public.canon_update_foster_preferences(integer, text, boolean, text, text, boolean, boolean, text);

create or replace function public.canon_update_foster_preferences(
  p_capacity integer default 1,
  p_locality_id text default null,
  p_active boolean default true,
  p_species_pref text default null,
  p_age_pref text default null,
  p_accepts_treatment boolean default null,
  p_other_animals_ok boolean default null,
  p_notes text default null,
  p_accepts_dogs boolean default null,
  p_accepts_cats boolean default null,
  p_accepts_small boolean default null,
  p_accepts_medium boolean default null,
  p_accepts_large boolean default null,
  p_accepts_young boolean default null,
  p_accepts_adult boolean default null,
  p_accepts_senior boolean default null,
  p_accepts_reduced_mobility boolean default null,
  p_can_isolate boolean default null,
  p_lives_with_dogs boolean default null,
  p_lives_with_cats boolean default null,
  p_lives_with_children boolean default null
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public.canon_upsert_foster_profile(p_capacity, p_locality_id);
  update public.foster_profiles
     set active = coalesce(p_active, active),
         species_pref = p_species_pref,
         age_pref = p_age_pref,
         accepts_treatment = p_accepts_treatment,
         other_animals_ok = p_other_animals_ok,
         notes = p_notes,
         accepts_dogs = p_accepts_dogs,
         accepts_cats = p_accepts_cats,
         accepts_small = p_accepts_small,
         accepts_medium = p_accepts_medium,
         accepts_large = p_accepts_large,
         accepts_young = p_accepts_young,
         accepts_adult = p_accepts_adult,
         accepts_senior = p_accepts_senior,
         accepts_reduced_mobility = p_accepts_reduced_mobility,
         can_isolate = p_can_isolate,
         lives_with_dogs = p_lives_with_dogs,
         lives_with_cats = p_lives_with_cats,
         lives_with_children = p_lives_with_children
   where user_id = auth.uid();
  return true;
end;
$$;

create or replace function public.canon_get_my_foster_profile()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_build_object(
      'user_id', f.user_id,
      'capacity', f.capacity,
      'active', f.active,
      'locality_id', f.locality_id,
      'species_pref', f.species_pref,
      'age_pref', f.age_pref,
      'accepts_treatment', f.accepts_treatment,
      'other_animals_ok', f.other_animals_ok,
      'notes', f.notes,
      'accepts_dogs', f.accepts_dogs,
      'accepts_cats', f.accepts_cats,
      'accepts_small', f.accepts_small,
      'accepts_medium', f.accepts_medium,
      'accepts_large', f.accepts_large,
      'accepts_young', f.accepts_young,
      'accepts_adult', f.accepts_adult,
      'accepts_senior', f.accepts_senior,
      'accepts_reduced_mobility', f.accepts_reduced_mobility,
      'can_isolate', f.can_isolate,
      'lives_with_dogs', f.lives_with_dogs,
      'lives_with_cats', f.lives_with_cats,
      'lives_with_children', f.lives_with_children
    )
    from public.foster_profiles f
    where f.user_id = auth.uid()
  ), 'null'::jsonb);
end;
$$;

create or replace function public.canon_apply_to_foster_request(p_request_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_req public.foster_care_requests%rowtype;
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_req from public.foster_care_requests where id = p_request_id for update;
  if not found or v_req.status <> 'REQUESTED' then raise exception 'NOT_FOUND'; end if;
  perform public._canon_assert_foster_eligible();
  if not public._canon_foster_traits_compatible(p_request_id, auth.uid()) then
    raise exception 'FOSTER_NOT_ELIGIBLE';
  end if;
  insert into public.foster_care_applications (request_id, foster_user_id)
  values (p_request_id, auth.uid())
  on conflict (request_id, foster_user_id) do update set status = 'PENDING'
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_select_foster_applicant(p_application_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_app public.foster_care_applications%rowtype;
  v_req public.foster_care_requests%rowtype;
  v_place uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_app from public.foster_care_applications where id = p_application_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  select * into v_req from public.foster_care_requests where id = v_app.request_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_req.requested_by is distinct from auth.uid()
     and not public._acl_pet_holder(auth.uid(), v_req.pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v_req.status not in ('REQUESTED', 'MATCHED') then raise exception 'STATUS_INVALID'; end if;
  if v_app.status is distinct from 'PENDING' then raise exception 'STATUS_INVALID'; end if;
  if not exists (
    select 1
      from public.foster_profiles fp
      join public.person_capabilities c
        on c.user_id = fp.user_id
       and c.capability = 'FOSTER'
     where fp.user_id = v_app.foster_user_id
       and fp.active
       and c.verification_status = 'VERIFIED'
       and c.active
  ) then
    raise exception 'FOSTER_NOT_ELIGIBLE';
  end if;
  if not public._canon_person_has_base_location(v_app.foster_user_id) then
    raise exception 'BASE_LOCATION_REQUIRED';
  end if;
  if not public._canon_foster_traits_compatible(v_req.id, v_app.foster_user_id) then
    raise exception 'FOSTER_NOT_ELIGIBLE';
  end if;

  update public.foster_care_applications
     set status = 'SELECTED'
   where id = p_application_id;
  update public.foster_care_applications
     set status = 'NOT_SELECTED'
   where request_id = v_req.id and id is distinct from p_application_id and status = 'PENDING';
  update public.foster_care_requests
     set status = 'ACTIVE',
         selected_application_id = p_application_id,
         updated_at = timezone('utc', now())
   where id = v_req.id;

  insert into public.foster_placements (pet_id, foster_user_id, status, created_by)
  values (v_req.pet_id, v_app.foster_user_id, 'OPEN', auth.uid())
  returning id into v_place;

  if not exists (
    select 1 from public.pet_responsibility_links l
     where l.pet_id = v_req.pet_id and l.holder_person_id = v_app.foster_user_id and l.status = 'ACTIVE'
  ) then
    insert into public.pet_responsibility_links (
      pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
    ) values (v_req.pet_id, 'PERSON', v_app.foster_user_id, 'AUTHORIZED', auth.uid());
  end if;
  return v_place;
end;
$$;

create or replace function public.canon_cancel_foster_request(p_request_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_req public.foster_care_requests%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_request_id is null then raise exception 'VALIDATION'; end if;
  select * into v_req from public.foster_care_requests where id = p_request_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if not public._canon_can_manage_foster_request(auth.uid(), v_req.id) then
    raise exception 'FORBIDDEN';
  end if;
  if v_req.status not in ('REQUESTED', 'MATCHED') then
    raise exception 'STATUS_INVALID';
  end if;
  update public.foster_care_requests
     set status = 'CANCELLED',
         updated_at = timezone('utc', now())
   where id = v_req.id;
  update public.foster_care_applications
     set status = 'NOT_SELECTED'
   where request_id = v_req.id
     and status = 'PENDING';
  return v_req.id;
end;
$$;

create or replace function public.canon_list_open_foster_requests()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public._canon_assert_foster_eligible();
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', r.id,
      'pet_id', r.pet_id,
      'pet_name', p.name,
      'species', p.species_code,
      'sex', p.sex,
      'needs', r.needs,
      'notes', r.notes,
      'status', r.status,
      'created_at', r.created_at,
      'size_band', coalesce(r.size_band, case when p.size in ('SMALL', 'MEDIUM', 'LARGE') then p.size else null end),
      'life_stage', r.life_stage,
      'needs_medication', r.needs_medication,
      'cohabits_dogs', r.cohabits_dogs,
      'cohabits_cats', r.cohabits_cats,
      'cohabits_children', r.cohabits_children,
      'reduced_mobility', r.reduced_mobility,
      'needs_isolation', r.needs_isolation,
      'additional_info', r.additional_info
    ) order by r.created_at desc)
    from public.foster_care_requests r
    join public.pets p on p.id = r.pet_id
   where r.status = 'REQUESTED'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_foster_requests()
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
      'id', r.id,
      'pet_id', r.pet_id,
      'pet_name', p.name,
      'species', p.species_code,
      'sex', p.sex,
      'status', r.status,
      'needs', r.needs,
      'notes', r.notes,
      'selected_application_id', r.selected_application_id,
      'placement_id', pl.id,
      'placement_status', pl.status,
      'created_at', r.created_at,
      'updated_at', r.updated_at,
      'size_band', r.size_band,
      'life_stage', r.life_stage,
      'needs_medication', r.needs_medication,
      'cohabits_dogs', r.cohabits_dogs,
      'cohabits_cats', r.cohabits_cats,
      'cohabits_children', r.cohabits_children,
      'reduced_mobility', r.reduced_mobility,
      'needs_isolation', r.needs_isolation,
      'additional_info', r.additional_info
    ) order by r.updated_at desc)
    from public.foster_care_requests r
    join public.pets p on p.id = r.pet_id
    left join lateral (
      select fp.id, fp.status
        from public.foster_placements fp
        join public.foster_care_applications a on a.id = r.selected_application_id
       where fp.pet_id = r.pet_id
         and fp.foster_user_id = a.foster_user_id
       order by case when fp.status = 'OPEN' then 0 else 1 end, fp.starts_at desc
       limit 1
    ) pl on true
   where public._canon_can_manage_foster_request(auth.uid(), r.id)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_foster_request_applications(p_request_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_req public.foster_care_requests%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_req from public.foster_care_requests where id = p_request_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_req.requested_by is distinct from auth.uid()
     and not public._acl_pet_holder(auth.uid(), v_req.pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', a.id,
      'foster_user_id', a.foster_user_id,
      'foster_name', coalesce(p.display_name, p.username),
      'status', a.status,
      'locality_id', fp.locality_id,
      'created_at', a.created_at,
      'capacity', fp.capacity,
      'profile_active', fp.active,
      'has_base_location', public._canon_person_has_base_location(a.foster_user_id),
      'accepts_dogs', fp.accepts_dogs,
      'accepts_cats', fp.accepts_cats,
      'accepts_small', fp.accepts_small,
      'accepts_medium', fp.accepts_medium,
      'accepts_large', fp.accepts_large,
      'accepts_young', fp.accepts_young,
      'accepts_adult', fp.accepts_adult,
      'accepts_senior', fp.accepts_senior,
      'accepts_treatment', fp.accepts_treatment,
      'accepts_reduced_mobility', fp.accepts_reduced_mobility,
      'can_isolate', fp.can_isolate,
      'lives_with_dogs', fp.lives_with_dogs,
      'lives_with_cats', fp.lives_with_cats,
      'lives_with_children', fp.lives_with_children,
      'species_pref', fp.species_pref,
      'age_pref', fp.age_pref,
      'species', pet.species_code,
      'size_band', coalesce(v_req.size_band, case when pet.size in ('SMALL', 'MEDIUM', 'LARGE') then pet.size else null end),
      'life_stage', v_req.life_stage,
      'needs_medication', v_req.needs_medication,
      'cohabits_dogs', v_req.cohabits_dogs,
      'cohabits_cats', v_req.cohabits_cats,
      'cohabits_children', v_req.cohabits_children,
      'reduced_mobility', v_req.reduced_mobility,
      'needs_isolation', v_req.needs_isolation
    ) order by a.created_at)
    from public.foster_care_applications a
    join public.persons p on p.user_id = a.foster_user_id
    join public.pets pet on pet.id = v_req.pet_id
    left join public.foster_profiles fp on fp.user_id = a.foster_user_id
   where a.request_id = p_request_id
  ), '[]'::jsonb);
end;
$$;

revoke all on function public._canon_foster_traits_compatible(uuid, uuid) from public, anon, authenticated;

revoke all on function public.canon_request_foster_for_pet(uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, boolean, text) from public, anon;
grant execute on function public.canon_request_foster_for_pet(uuid, text, text, text, text, boolean, boolean, boolean, boolean, boolean, boolean, text) to authenticated;

revoke all on function public.canon_update_foster_preferences(integer, text, boolean, text, text, boolean, boolean, text, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean) from public, anon;
grant execute on function public.canon_update_foster_preferences(integer, text, boolean, text, text, boolean, boolean, text, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean) to authenticated;

revoke all on function public.canon_get_my_foster_profile() from public, anon;
grant execute on function public.canon_get_my_foster_profile() to authenticated;

revoke all on function public.canon_apply_to_foster_request(uuid) from public, anon;
grant execute on function public.canon_apply_to_foster_request(uuid) to authenticated;

revoke all on function public.canon_select_foster_applicant(uuid) from public, anon;
grant execute on function public.canon_select_foster_applicant(uuid) to authenticated;

revoke all on function public.canon_cancel_foster_request(uuid) from public, anon;
grant execute on function public.canon_cancel_foster_request(uuid) to authenticated;

revoke all on function public.canon_list_open_foster_requests() from public, anon;
grant execute on function public.canon_list_open_foster_requests() to authenticated;

revoke all on function public.canon_list_my_foster_requests() from public, anon;
grant execute on function public.canon_list_my_foster_requests() to authenticated;

revoke all on function public.canon_list_foster_request_applications(uuid) from public, anon;
grant execute on function public.canon_list_foster_request_applications(uuid) to authenticated;

comment on function public._canon_foster_traits_compatible(uuid, uuid) is
  'Deterministic trait check. Unknown animal facts and unset home preferences do not exclude. Explicit conflicts do. Inactive homes and homes without a base location are not compatible.';

comment on function public.canon_cancel_foster_request(uuid) is
  'Manager cancels a REQUESTED or MATCHED request. Does not complete an active placement.';

notify pgrst, 'reload schema';
