-- 1092: verification request gates + list/retry + community nearby expansion.
-- Does not edit 1091 or earlier.

create or replace function public._canon_person_profile_complete(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
      from public.persons p
     where p.user_id = p_user_id
       and p.lifecycle_status = 'ACTIVE'
       and nullif(btrim(p.display_name), '') is not null
       and nullif(btrim(p.username), '') is not null
       and p.birth_date is not null
  );
$$;

create or replace function public._canon_person_has_contact(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
      from public.persons p
      join auth.users u on u.id = p.user_id
     where p.user_id = p_user_id
       and (
         nullif(btrim(coalesce(p.e164_phone, '')), '') is not null
         or (u.email is not null and u.email_confirmed_at is not null)
         or p.email_verified_at is not null
       )
  );
$$;

create or replace function public._canon_person_has_base_location(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.persons p
     where p.user_id = p_user_id and p.base_location is not null
  );
$$;

create or replace function public.canon_request_leover_verification(
  p_function_code text,
  p_evidence jsonb default '{}'::jsonb,
  p_organization_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_fn text := upper(btrim(coalesce(p_function_code, '')));
  v_terms boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_fn not in ('RESCUER', 'SHELTER', 'NGO', 'FOSTER', 'PROFESSIONAL', 'VETERINARY', 'BUSINESS') then
    raise exception 'FUNCTION_INVALID';
  end if;
  if not public._canon_person_profile_complete(auth.uid()) then
    raise exception 'PROFILE_INCOMPLETE';
  end if;
  if not public._canon_person_has_contact(auth.uid()) then
    raise exception 'CONTACT_REQUIRED';
  end if;
  v_terms := coalesce((p_evidence ->> 'terms_accepted') in ('true', 'TRUE', '1'), false);
  if not v_terms then
    raise exception 'TERMS_REQUIRED';
  end if;
  if v_fn in ('RESCUER', 'FOSTER') and not public._canon_person_has_base_location(auth.uid()) then
    raise exception 'BASE_LOCATION_REQUIRED';
  end if;
  if v_fn in ('SHELTER', 'NGO', 'VETERINARY', 'BUSINESS') then
    if p_organization_id is null then raise exception 'ORGANIZATION_REQUIRED'; end if;
    if not exists (
      select 1 from public.organizations o
      join public.organization_memberships m on m.organization_id = o.id
     where o.id = p_organization_id
       and m.person_id = auth.uid()
       and m.status = 'ACTIVE'
       and nullif(btrim(o.name), '') is not null
    ) then
      raise exception 'ORGANIZATION_INCOMPLETE';
    end if;
    if not exists (
      select 1 from public.organizations o
       where o.id = p_organization_id and o.base_location is not null
    ) then
      raise exception 'BASE_LOCATION_REQUIRED';
    end if;
  end if;
  if v_fn = 'PROFESSIONAL' then
    insert into public.professional_profiles (person_id)
    values (auth.uid())
    on conflict (person_id) do nothing;
  end if;

  insert into public.leover_verification_requests (
    subject_kind, person_id, organization_id, function_code, status, evidence
  ) values (
    case when p_organization_id is null then 'PERSON' else 'ORGANIZATION' end,
    auth.uid(), p_organization_id, v_fn, 'PENDING', coalesce(p_evidence, '{}'::jsonb)
  ) returning id into v_id;
  if p_organization_id is null and v_fn in ('RESCUER', 'FOSTER', 'PROFESSIONAL') then
    insert into public.person_capabilities (user_id, capability, active, verification_status, updated_at)
    values (auth.uid(), v_fn, true, 'PENDING', timezone('utc', now()))
    on conflict (user_id, capability) do update
      set verification_status = 'PENDING', updated_at = timezone('utc', now());
  end if;
  return v_id;
end;
$$;

create or replace function public.canon_list_my_leover_verifications()
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
      'function_code', r.function_code,
      'status', r.status,
      'review_note', r.review_note,
      'reviewed_at', r.reviewed_at,
      'created_at', r.created_at,
      'organization_id', r.organization_id
    ) order by r.created_at desc)
    from public.leover_verification_requests r
   where r.person_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_resubmit_leover_verification(
  p_id uuid,
  p_evidence jsonb default '{}'::jsonb
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.leover_verification_requests%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.leover_verification_requests where id = p_id for update;
  if not found or v_row.person_id is distinct from auth.uid() then
    raise exception 'NOT_FOUND';
  end if;
  if v_row.status not in ('REQUIRES_CORRECTION', 'REJECTED') then
    raise exception 'STATUS_INVALID';
  end if;
  if not coalesce((p_evidence ->> 'terms_accepted') in ('true', 'TRUE', '1'), false) then
    raise exception 'TERMS_REQUIRED';
  end if;
  update public.leover_verification_requests
     set status = 'PENDING',
         evidence = coalesce(p_evidence, v_row.evidence),
         review_note = null,
         updated_at = timezone('utc', now())
   where id = p_id;
  return true;
end;
$$;

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
set search_path = public
as $$
declare
  v_loc extensions.geography(Point, 4326);
  v_filter text := upper(btrim(coalesce(p_filter, 'ALL')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_lat is null or p_lng is null then raise exception 'LOCATION_INVALID'; end if;
  v_loc := ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::extensions.geography;
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

grant execute on function public.canon_request_leover_verification(text, jsonb, uuid) to authenticated;
grant execute on function public.canon_list_my_leover_verifications() to authenticated;
grant execute on function public.canon_resubmit_leover_verification(uuid, jsonb) to authenticated;
grant execute on function public.canon_list_community_nearby(double precision, double precision, text, integer) to authenticated;
revoke all on function public._canon_person_profile_complete(uuid) from public, anon, authenticated;
revoke all on function public._canon_person_has_contact(uuid) from public, anon, authenticated;
revoke all on function public._canon_person_has_base_location(uuid) from public, anon, authenticated;
