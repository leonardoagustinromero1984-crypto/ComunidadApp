-- LeoVer STAGING QA shelters for 17B.12.
-- Not a migration. Idempotent. Fail-closed.
-- Invoke only via scripts/qa/bootstrap-staging-qa.ps1 after QA01–QA16 persons exist.
-- Does not create auth users, does not change passwords, and does not attach
-- these organizations to a person outside the QA username allowlist.

do $bootstrap$
declare
  expected_ref constant text := 'tobqbddfcyitwgbkthhy';
  given_ref text;
  qa_usernames constant text[] := array[
    'qa01owner', 'qa02finder', 'qa03rescuer', 'qa04rescuer2',
    'qa05unavailable', 'qa06foster', 'qa07shelter', 'qa08pending',
    'qa09noreq', 'qa10vet', 'qa11proa', 'qa12proind',
    'qa13shop', 'qa14adopter', 'qa15adopter2', 'qa16vetnorte'
  ];
  u07 uuid;
  u08 uuid;
  org_a uuid;
  org_b uuid;
  role_a uuid;
  role_b uuid;
begin -- QA_BOOTSTRAP_INJECT_GUC
  given_ref := current_setting('leover.staging_reset.project_ref', true);
  if given_ref is distinct from expected_ref then
    raise exception using
      message = 'QA_BOOTSTRAP_ABORT_PROJECT_REF',
      detail = 'leover.staging_reset.project_ref must be tobqbddfcyitwgbkthhy. Nothing was written.';
  end if;

  if (
    select count(*)
      from public.persons
     where username = any (qa_usernames)
  ) <> 16 then
    raise exception using
      message = 'QA_BOOTSTRAP_ABORT_ACTORS_MISSING',
      detail = 'QA01-QA16 persons must already exist. Real persons are not used as a fallback.';
  end if;

  select user_id into u07 from public.persons where username = 'qa07shelter';
  select user_id into u08 from public.persons where username = 'qa08pending';
  if u07 is null or u08 is null or u07 = u08 then
    raise exception 'QA_BOOTSTRAP_ABORT_SHELTER_ACTORS';
  end if;

  if exists (
    select 1
      from public.persons
     where user_id in (u07, u08)
       and username not in ('qa07shelter', 'qa08pending')
  ) then
    raise exception 'QA_BOOTSTRAP_ABORT_REAL_USER';
  end if;

  insert into public.organizations (
    name, slug, primary_label, home_locality_id, created_by_user_id, lifecycle_status
  )
  select 'QA - Refugio Norte', 'qa-cc02-shelter-n', 'SHELTER', 'loc-ar-loc-caba', u07, 'ACTIVE'
   where not exists (
     select 1 from public.organizations where slug = 'qa-cc02-shelter-n'
   );

  insert into public.organizations (
    name, slug, primary_label, home_locality_id, created_by_user_id, lifecycle_status
  )
  select 'QA - Refugio Sur no verificado', 'qa-cc02-shelter-u', 'SHELTER', 'loc-ar-loc-caba', u08, 'ACTIVE'
   where not exists (
     select 1 from public.organizations where slug = 'qa-cc02-shelter-u'
   );

  select id into org_a from public.organizations where slug = 'qa-cc02-shelter-n';
  select id into org_b from public.organizations where slug = 'qa-cc02-shelter-u';
  if org_a is null or org_b is null or org_a = org_b then
    raise exception 'QA_BOOTSTRAP_ABORT_ORGS';
  end if;

  update public.organizations
     set name = 'QA - Refugio Norte',
         lifecycle_status = 'ACTIVE',
         base_location = public._canon_geo_point(-58.4508, -34.5497),
         address_line = 'Belgrano, CABA',
         receive_nearby_cases = true,
         verification_status = 'VERIFIED',
         updated_at = timezone('utc', now())
   where id = org_a
     and slug = 'qa-cc02-shelter-n';

  update public.organizations
     set name = 'QA - Refugio Sur no verificado',
         lifecycle_status = 'ACTIVE',
         base_location = public._canon_geo_point(-58.3730, -34.7200),
         address_line = 'Avellaneda, Buenos Aires',
         receive_nearby_cases = true,
         verification_status = 'NOT_REQUESTED',
         updated_at = timezone('utc', now())
   where id = org_b
     and slug = 'qa-cc02-shelter-u';

  insert into public.organization_capabilities (organization_id, capability)
  values (org_a, 'SHELTER'), (org_b, 'SHELTER')
  on conflict do nothing;

  insert into public.organization_public_profiles (organization_id, bio, published)
  values
    (org_a, 'QA - Refugio Norte · bootstrap QA.', true),
    (org_b, 'QA - Refugio Sur no verificado · bootstrap QA.', true)
  on conflict (organization_id) do update
    set published = excluded.published,
        bio = excluded.bio;

  insert into public.organization_roles (organization_id, code, name, is_system)
  values
    (org_a, 'ADMIN', 'Admin', true),
    (org_b, 'ADMIN', 'Admin', true)
  on conflict (organization_id, code) do nothing;

  select id into role_a
    from public.organization_roles
   where organization_id = org_a and code = 'ADMIN';
  select id into role_b
    from public.organization_roles
   where organization_id = org_b and code = 'ADMIN';

  insert into public.organization_role_permissions (role_id, permission_code)
  select role_id, c.code
    from (values (role_a), (role_b)) as roles(role_id)
    join public.permission_codes c on c.scope = 'ORG'
  on conflict do nothing;

  insert into public.organization_memberships (organization_id, person_id, role_id, status)
  select org_a, u07, role_a, 'ACTIVE'
   where not exists (
     select 1 from public.organization_memberships m
      where m.organization_id = org_a
        and m.person_id = u07
        and m.status = 'ACTIVE'
   );

  insert into public.organization_memberships (organization_id, person_id, role_id, status)
  select org_b, u08, role_b, 'ACTIVE'
   where not exists (
     select 1 from public.organization_memberships m
      where m.organization_id = org_b
        and m.person_id = u08
        and m.status = 'ACTIVE'
   );
end;
$bootstrap$;
