-- 1089: verification states, availability, community nearby, adoption profile,
-- membership trial contract, vet pending-owner invite. Does not edit 1087/1088.

alter table public.person_capabilities
  drop constraint if exists person_capabilities_verification_status_check;
alter table public.person_capabilities
  add constraint person_capabilities_verification_status_check
  check (verification_status in (
    'NOT_REQUESTED', 'PENDING', 'REQUIRES_CORRECTION', 'VERIFIED', 'REJECTED', 'SUSPENDED', 'EXPIRED'
  ));

alter table public.organizations
  drop constraint if exists organizations_verification_status_check;
alter table public.organizations
  add constraint organizations_verification_status_check
  check (verification_status in (
    'NOT_REQUESTED', 'PENDING', 'REQUIRES_CORRECTION', 'VERIFIED', 'REJECTED', 'SUSPENDED', 'EXPIRED'
  ));

create table if not exists public.leover_verification_requests (
  id uuid primary key default gen_random_uuid(),
  subject_kind text not null check (subject_kind in ('PERSON', 'ORGANIZATION')),
  person_id uuid null references public.persons(user_id),
  organization_id uuid null references public.organizations(id),
  function_code text not null,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'REQUIRES_CORRECTION', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
  evidence jsonb not null default '{}'::jsonb,
  reviewer_user_id uuid null references public.persons(user_id),
  review_note text null,
  reviewed_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);

create index if not exists leover_verification_requests_person_idx
  on public.leover_verification_requests (person_id, created_at desc);
alter table public.leover_verification_requests enable row level security;
revoke all on table public.leover_verification_requests from anon, authenticated;

create table if not exists public.adoption_general_profiles (
  person_id uuid primary key references public.persons(user_id),
  housing_type text null,
  housing_tenure text null,
  animals_allowed boolean null,
  adults_count integer null,
  children_count integer null,
  allergies text null,
  other_pets text null,
  experience text null,
  hours_alone text null,
  primary_caretaker text null,
  vet_reference text null,
  motivation text null,
  notes text null,
  updated_at timestamptz not null default timezone('utc', now())
);
alter table public.adoption_general_profiles enable row level security;
revoke all on table public.adoption_general_profiles from anon, authenticated;

create table if not exists public.membership_entitlements (
  person_id uuid not null references public.persons(user_id),
  function_code text not null,
  trial_started_at timestamptz null,
  trial_ends_at timestamptz null,
  status text not null default 'TRIAL'
    check (status in ('TRIAL', 'ACTIVE', 'EXPIRED', 'GRACE')),
  paywall_enforced boolean not null default false,
  primary key (person_id, function_code)
);
alter table public.membership_entitlements enable row level security;
revoke all on table public.membership_entitlements from anon, authenticated;
comment on table public.membership_entitlements is
  '90-day trial contract for commercial/professional functions. paywall_enforced stays false until billing V2.';

create table if not exists public.vet_pending_owner_invites (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id),
  organization_id uuid null references public.organizations(id),
  invited_email_normalized text not null,
  status text not null default 'PENDING_OWNER_EMAIL'
    check (status in ('PENDING_OWNER_EMAIL', 'ACCEPTED', 'CANCELLED')),
  created_by uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  accepted_person_id uuid null references public.persons(user_id)
);
create unique index if not exists vet_pending_owner_invites_open_uidx
  on public.vet_pending_owner_invites (pet_id, invited_email_normalized)
  where status = 'PENDING_OWNER_EMAIL';
alter table public.vet_pending_owner_invites enable row level security;
revoke all on table public.vet_pending_owner_invites from anon, authenticated;

alter table public.foster_profiles
  add column if not exists species_pref text null,
  add column if not exists age_pref text null,
  add column if not exists accepts_treatment boolean null,
  add column if not exists other_animals_ok boolean null,
  add column if not exists notes text null;

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
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_fn not in ('RESCUER', 'SHELTER', 'NGO', 'FOSTER', 'PROFESSIONAL', 'VETERINARY', 'BUSINESS') then
    raise exception 'FUNCTION_INVALID';
  end if;
  insert into public.leover_verification_requests (
    subject_kind, person_id, organization_id, function_code, status, evidence
  ) values (
    case when p_organization_id is null then 'PERSON' else 'ORGANIZATION' end,
    auth.uid(), p_organization_id, v_fn, 'PENDING', coalesce(p_evidence, '{}'::jsonb)
  ) returning id into v_id;
  if p_organization_id is null and v_fn in ('RESCUER', 'FOSTER') then
    insert into public.person_capabilities (user_id, capability, active, verification_status, updated_at)
    values (auth.uid(), v_fn, true, 'PENDING', timezone('utc', now()))
    on conflict (user_id, capability) do update
      set verification_status = 'PENDING', updated_at = timezone('utc', now());
  end if;
  return v_id;
end;
$$;

create or replace function public.canon_review_leover_verification(
  p_id uuid,
  p_status text,
  p_note text default null
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.leover_verification_requests%rowtype;
  v_status text := upper(btrim(coalesce(p_status, '')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_status not in ('VERIFIED', 'REQUIRES_CORRECTION', 'REJECTED', 'SUSPENDED') then
    raise exception 'STATUS_INVALID';
  end if;
  if not public.has_permission('staff.manage') then
    raise exception 'FORBIDDEN';
  end if;
  select * into v_row from public.leover_verification_requests where id = p_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  update public.leover_verification_requests
     set status = v_status,
         reviewer_user_id = auth.uid(),
         review_note = p_note,
         reviewed_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = p_id;
  if v_row.person_id is not null and v_row.function_code in ('RESCUER', 'FOSTER') then
    update public.person_capabilities
       set verification_status = v_status,
           verified_at = case when v_status = 'VERIFIED' then timezone('utc', now()) else verified_at end,
           verified_by = case when v_status = 'VERIFIED' then auth.uid() else verified_by end,
           updated_at = timezone('utc', now())
     where user_id = v_row.person_id and capability = v_row.function_code;
  end if;
  if v_row.organization_id is not null then
    update public.organizations
       set verification_status = v_status, updated_at = timezone('utc', now())
     where id = v_row.organization_id;
  end if;
  return true;
end;
$$;

create or replace function public.canon_upsert_adoption_general_profile(
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
  p_notes text default null
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
    allergies, other_pets, experience, hours_alone, primary_caretaker, vet_reference, motivation, notes
  ) values (
    auth.uid(), p_housing_type, p_housing_tenure, p_animals_allowed, p_adults_count, p_children_count,
    p_allergies, p_other_pets, p_experience, p_hours_alone, p_primary_caretaker, p_vet_reference, p_motivation, p_notes
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
    updated_at = timezone('utc', now());
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
               null::text as hours_json
          from public.persons p
          join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'RESCUER' and c.active
         where p.base_location is not null
           and ST_DWithin(p.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'RESCUER')
        union all
        select 'FOSTER', p.user_id::text, coalesce(p.display_name, 'Tránsito'),
               p.home_locality_id, c.verification_status,
               ST_Distance(p.base_location, v_loc), null
          from public.persons p
          join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'FOSTER' and c.active
         where p.base_location is not null
           and ST_DWithin(p.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'FOSTER')
        union all
        select case when oc.capability = 'SHELTER' then 'SHELTER' else oc.capability end,
               o.id::text, o.name, o.home_locality_id, o.verification_status,
               ST_Distance(o.base_location, v_loc), null
          from public.organizations o
          join public.organization_capabilities oc on oc.organization_id = o.id
         where o.base_location is not null
           and o.lifecycle_status = 'ACTIVE'
           and ST_DWithin(o.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'SHELTER', 'SERVICES')
           and oc.capability in ('SHELTER', 'NGO')
      ) q
      order by meters
      limit 80
    ) x
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_invite_pending_pet_owner(
  p_pet_id uuid,
  p_email text,
  p_organization_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_existing uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_email is null or position('@' in v_email) = 0 then raise exception 'EMAIL_REQUIRED'; end if;
  if not public._acl_pet_holder(auth.uid(), p_pet_id) then raise exception 'FORBIDDEN'; end if;
  insert into public.vet_pending_owner_invites (
    pet_id, organization_id, invited_email_normalized, created_by
  ) values (p_pet_id, p_organization_id, v_email, auth.uid())
  returning id into v_id;
  select p.user_id into v_existing
    from public.persons p
    join auth.users u on u.id = p.user_id
   where lower(btrim(coalesce(u.email, ''))) = v_email
   limit 1;
  return v_id;
end;
$$;

-- _canon_emit_lf_event requires alert uuid; invite uses dummy-safe path — skip if p_alert_id null
-- Recreate invite notify without that helper if needed. Keep insert-only if emit fails.

create or replace function public.canon_start_membership_trial(p_function_code text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.membership_entitlements (
    person_id, function_code, trial_started_at, trial_ends_at, status, paywall_enforced
  ) values (
    auth.uid(), upper(btrim(p_function_code)), timezone('utc', now()),
    timezone('utc', now()) + interval '90 days', 'TRIAL', false
  )
  on conflict (person_id, function_code) do nothing;
  return true;
end;
$$;

grant execute on function public.canon_request_leover_verification(text, jsonb, uuid) to authenticated;
grant execute on function public.canon_review_leover_verification(uuid, text, text) to authenticated;
grant execute on function public.canon_upsert_adoption_general_profile(text, text, boolean, integer, integer, text, text, text, text, text, text, text, text) to authenticated;
grant execute on function public.canon_list_community_nearby(double precision, double precision, text, integer) to authenticated;
grant execute on function public.canon_invite_pending_pet_owner(uuid, text, uuid) to authenticated;
grant execute on function public.canon_start_membership_trial(text) to authenticated;
