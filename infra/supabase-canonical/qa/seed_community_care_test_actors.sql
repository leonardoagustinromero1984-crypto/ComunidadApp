-- LeoVer STAGING QA actors. Not a migration. Idempotent. Fail-closed.
-- Invoke only via scripts/qa/seed-community-care-actors.ps1.

do $seed$
declare
  expected_ref constant text := 'tobqbddfcyitwgbkthhy';
  given_ref text;
  loc_caba text := 'loc-ar-loc-caba';
  loc_palermo text := 'loc-ar-loc-palermo';
  breed_dog uuid;
  breed_cat uuid;
  u01 uuid; u02 uuid; u03 uuid; u04 uuid; u05 uuid; u06 uuid;
  u07 uuid; u08 uuid; u09 uuid; u10 uuid; u11 uuid; u12 uuid;
  u13 uuid; u14 uuid; u15 uuid; u16 uuid;
  o07 uuid; o08 uuid; o09 uuid; o10 uuid; o13 uuid; o16 uuid;
  r07 uuid; r08 uuid; r09 uuid; r10 uuid; r13 uuid; r16 uuid;
  pro11 uuid; pro12 uuid; pro16 uuid;
  pet_mora uuid; pet_milo uuid; pet_luna uuid; pet_bruno uuid;
  care_private uuid; care_visible uuid;
  grant_org uuid; grant_pro uuid;
  sp10 uuid; sp13 uuid; sp16 uuid;
  v_hours jsonb := '[
    {"weekday":1,"closed":false,"opens_at":"09:00","closes_at":"18:00"},
    {"weekday":2,"closed":false,"opens_at":"09:00","closes_at":"18:00"},
    {"weekday":3,"closed":false,"opens_at":"09:00","closes_at":"18:00"},
    {"weekday":4,"closed":false,"opens_at":"09:00","closes_at":"18:00"},
    {"weekday":5,"closed":false,"opens_at":"09:00","closes_at":"18:00"},
    {"weekday":6,"closed":false,"opens_at":"09:00","closes_at":"13:00"},
    {"weekday":7,"closed":true}
  ]'::jsonb;
  rec record;
begin -- QA_ACTORS_INJECT_GUC
  given_ref := current_setting('leover.staging_reset.project_ref', true);
  if given_ref is distinct from expected_ref then
    raise exception using
      message = 'QA_ACTORS_ABORT_PROJECT_REF',
      detail = 'leover.staging_reset.project_ref must be tobqbddfcyitwgbkthhy. Nothing was seeded.';
  end if;

  if not exists (select 1 from public.location_nodes where id = loc_caba) then
    select id into loc_caba from public.location_nodes
     where kind = 'LOCALITY' and id like 'loc-ar%' order by id limit 1;
  end if;
  if not exists (select 1 from public.location_nodes where id = loc_palermo) then
    loc_palermo := loc_caba;
  end if;

  select id into breed_dog from public.breeds where species_code = 'DOG' order by name limit 1;
  select id into breed_cat from public.breeds where species_code = 'CAT' order by name limit 1;

  -- Provision PERSON if Admin API created auth.users but trigger missed a row.
  for rec in
    select * from (values
      ('qa01.owner@leoverapp.com', 'qa01owner', 'QA01 Owner'),
      ('qa02.finder@leoverapp.com', 'qa02finder', 'QA02 Finder'),
      ('qa03.rescuer@leoverapp.com', 'qa03rescuer', 'QA03 Rescuer Near'),
      ('qa04.rescuer2@leoverapp.com', 'qa04rescuer2', 'QA04 Rescuer Second'),
      ('qa05.unavail@leoverapp.com', 'qa05unavailable', 'QA05 Rescuer Unavailable'),
      ('qa06.foster@leoverapp.com', 'qa06foster', 'QA06 Foster'),
      ('qa07.shelter@leoverapp.com', 'qa07shelter', 'QA07 Shelter Verified'),
      ('qa08.pending@leoverapp.com', 'qa08pending', 'QA08 Shelter Pending'),
      ('qa09.noreq@leoverapp.com', 'qa09noreq', 'QA09 Shelter Not Requested'),
      ('qa10.vet@leoverapp.com', 'qa10vet', 'QA10 Veterinary Admin'),
      ('qa11.pro.a@leoverapp.com', 'qa11proa', 'QA11 Veterinary Professional A'),
      ('qa12.pro.ind@leoverapp.com', 'qa12proind', 'QA12 Professional Independent'),
      ('qa13.shop@leoverapp.com', 'qa13shop', 'QA13 Business Admin'),
      ('qa14.adopter.a@leoverapp.com', 'qa14adopter', 'QA14 Adopter A'),
      ('qa15.adopter.b@leoverapp.com', 'qa15adopter2', 'QA15 Adopter B'),
      ('qa16.vet.norte@leoverapp.com', 'qa16vetnorte', 'QA16 Veterinary Professional B')
    ) as t(email, username, display_name)
  loop
    if not exists (select 1 from auth.users u where lower(u.email) = rec.email) then
      raise exception using
        message = 'QA_ACTORS_AUTH_MISSING',
        detail = rec.email;
    end if;
    insert into public.persons (
      user_id, username, display_name, birth_date, email_verified_at,
      privacy_state, lifecycle_status, locale, timezone, country_iso, home_locality_id
    )
    select u.id, rec.username, rec.display_name, date '1990-01-15',
           coalesce(u.email_confirmed_at, timezone('utc', now())),
           'PUBLIC_LIMITED', 'ACTIVE', 'es-AR', 'America/Argentina/Buenos_Aires', 'AR', loc_caba
      from auth.users u
     where lower(u.email) = rec.email
    on conflict (user_id) do update
      set username = excluded.username,
          display_name = excluded.display_name,
          lifecycle_status = 'ACTIVE',
          privacy_state = 'PUBLIC_LIMITED',
          locale = 'es-AR',
          timezone = 'America/Argentina/Buenos_Aires',
          country_iso = 'AR',
          home_locality_id = coalesce(public.persons.home_locality_id, excluded.home_locality_id),
          email_verified_at = coalesce(public.persons.email_verified_at, excluded.email_verified_at),
          updated_at = timezone('utc', now());
    insert into public.person_privacy_settings (user_id, profile_discoverable)
    select u.id, true from auth.users u where lower(u.email) = rec.email
    on conflict (user_id) do update set profile_discoverable = true;
    insert into public.person_contact_controls (user_id, allow_unknown_dms)
    select u.id, true from auth.users u where lower(u.email) = rec.email
    on conflict (user_id) do nothing;
    insert into public.notification_preferences (user_id)
    select u.id from auth.users u where lower(u.email) = rec.email
    on conflict (user_id) do nothing;
  end loop;

  select user_id into u01 from public.persons where username = 'qa01owner';
  select user_id into u02 from public.persons where username = 'qa02finder';
  select user_id into u03 from public.persons where username = 'qa03rescuer';
  select user_id into u04 from public.persons where username = 'qa04rescuer2';
  select user_id into u05 from public.persons where username = 'qa05unavailable';
  select user_id into u06 from public.persons where username = 'qa06foster';
  select user_id into u07 from public.persons where username = 'qa07shelter';
  select user_id into u08 from public.persons where username = 'qa08pending';
  select user_id into u09 from public.persons where username = 'qa09noreq';
  select user_id into u10 from public.persons where username = 'qa10vet';
  select user_id into u11 from public.persons where username = 'qa11proa';
  select user_id into u12 from public.persons where username = 'qa12proind';
  select user_id into u13 from public.persons where username = 'qa13shop';
  select user_id into u14 from public.persons where username = 'qa14adopter';
  select user_id into u15 from public.persons where username = 'qa15adopter2';
  select user_id into u16 from public.persons where username = 'qa16vetnorte';

  if u01 is null or u02 is null or u03 is null or u04 is null or u05 is null
     or u06 is null or u07 is null or u08 is null or u09 is null or u10 is null
     or u11 is null or u12 is null or u13 is null or u14 is null or u15 is null
     or u16 is null then
    raise exception 'QA_ACTORS_PERSONS_MISSING';
  end if;

  -- Synthetic CABA pins. Central = Obelisco / Microcentro. Not real homes.
  update public.persons set
    e164_phone = '+5491155550101',
    base_address = 'QA - Microcentro sintético, CABA',
    base_location = public._canon_geo_point(-58.3808, -34.6031),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u01;
  update public.persons set
    e164_phone = '+5491155550102',
    base_address = 'QA - Finder 250m, CABA',
    base_location = public._canon_geo_point(-58.3838, -34.6055),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u02;
  update public.persons set
    e164_phone = '+5491155550103',
    base_address = 'QA - Rescatista cercano 400m, CABA',
    base_location = public._canon_geo_point(-58.3816, -34.6001),
    receive_nearby_cases = true,
    home_locality_id = loc_caba
  where user_id = u03;
  update public.persons set
    e164_phone = '+5491155550104',
    base_address = 'QA - Rescatista segundo 3.2km, CABA',
    base_location = public._canon_geo_point(-58.4160, -34.6060),
    receive_nearby_cases = true,
    home_locality_id = loc_caba
  where user_id = u04;
  update public.persons set
    e164_phone = '+5491155550105',
    base_address = 'QA - Rescatista no disponible 500m, CABA',
    base_location = public._canon_geo_point(-58.3760, -34.6037),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u05;
  update public.persons set
    e164_phone = '+5491155550106',
    base_address = 'QA - Foster 1.8km, CABA',
    base_location = public._canon_geo_point(-58.3816, -34.6195),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u06;
  update public.persons set
    e164_phone = '+5491155550107',
    base_address = 'QA - Admin refugio verificado',
    base_location = public._canon_geo_point(-58.4200, -34.6300),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u07;
  update public.persons set
    e164_phone = '+5491155550108',
    base_address = 'QA - Admin refugio pending',
    base_location = public._canon_geo_point(-58.3500, -34.6400),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u08;
  update public.persons set
    e164_phone = '+5491155550109',
    base_address = 'QA - Admin refugio sin solicitud',
    base_location = public._canon_geo_point(-58.4200, -34.5750),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u09;
  update public.persons set
    e164_phone = '+5491155550110',
    base_address = 'QA - Admin veterinaria centro',
    base_location = public._canon_geo_point(-58.3720, -34.5950),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u10;
  update public.persons set
    e164_phone = '+5491155550111',
    base_address = 'QA - Profesional A en veterinaria centro',
    base_location = public._canon_geo_point(-58.3724, -34.5954),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u11;
  update public.persons set
    e164_phone = '+5491155550112',
    base_address = 'QA - Profesional independiente 2km',
    base_location = public._canon_geo_point(-58.3600, -34.6037),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u12;
  update public.persons set
    e164_phone = '+5491155550113',
    base_address = 'QA - Pet shop admin',
    base_location = public._canon_geo_point(-58.3816, -34.5820),
    receive_nearby_cases = false,
    home_locality_id = loc_palermo
  where user_id = u13;
  update public.persons set
    e164_phone = '+5491155550114',
    base_address = 'QA - Adoptante A',
    base_location = public._canon_geo_point(-58.3900, -34.6080),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u14;
  update public.persons set
    e164_phone = '+5491155550115',
    base_address = 'QA - Adoptante B',
    base_location = public._canon_geo_point(-58.3880, -34.6100),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u15;
  update public.persons set
    e164_phone = '+5491155550116',
    base_address = 'QA - Profesional B veterinaria norte',
    base_location = public._canon_geo_point(-58.4560, -34.5280),
    receive_nearby_cases = false,
    home_locality_id = loc_caba
  where user_id = u16;

  insert into public.person_capabilities (user_id, capability, active, verification_status, verified_at, updated_at)
  values
    (u03, 'RESCUER', true, 'VERIFIED', timezone('utc', now()), timezone('utc', now())),
    (u04, 'RESCUER', true, 'VERIFIED', timezone('utc', now()), timezone('utc', now())),
    (u05, 'RESCUER', true, 'VERIFIED', timezone('utc', now()), timezone('utc', now())),
    (u06, 'FOSTER', true, 'VERIFIED', timezone('utc', now()), timezone('utc', now()))
  on conflict (user_id, capability) do update
    set active = true,
        verification_status = excluded.verification_status,
        verified_at = coalesce(public.person_capabilities.verified_at, excluded.verified_at),
        deactivated_at = null,
        updated_at = timezone('utc', now());

  insert into public.foster_profiles (
    user_id, capacity, active, locality_id, species_pref, age_pref,
    accepts_treatment, other_animals_ok, notes
  ) values (
    u06, 2, true, loc_caba, 'DOG,CAT', 'PUPPY,ADULT',
    true, true, 'QA foster listo. Perros y gatos, cachorros y adultos. Acepta tratamientos.'
  )
  on conflict (user_id) do update
    set capacity = 2,
        active = true,
        locality_id = excluded.locality_id,
        species_pref = excluded.species_pref,
        age_pref = excluded.age_pref,
        accepts_treatment = true,
        other_animals_ok = true,
        notes = excluded.notes;

  insert into public.professional_profiles (person_id, license_number, active)
  values
    (u11, 'QA-MN-1111', true),
    (u12, 'QA-MN-1212', true),
    (u16, 'QA-MN-1616', true)
  on conflict (person_id) do update
    set license_number = excluded.license_number,
        active = true;
  select id into pro11 from public.professional_profiles where person_id = u11;
  select id into pro12 from public.professional_profiles where person_id = u12;
  select id into pro16 from public.professional_profiles where person_id = u16;

  -- Organizations
  insert into public.organizations (
    name, slug, primary_label, home_locality_id, created_by_user_id, lifecycle_status,
    timezone, country_iso, address_line, verification_status, receive_nearby_cases
  )
  select v.name, v.slug, v.label, loc_caba, v.uid, 'ACTIVE',
         'America/Argentina/Buenos_Aires', 'AR', v.address, v.vstatus, v.recv
    from (values
      ('QA - Refugio Verificado', 'qa-cc-shelter-verified', 'SHELTER', u07,
       'QA - Av. sintético 4500, CABA', 'VERIFIED', true),
      ('QA - Refugio Pending', 'qa-cc-shelter-pending', 'SHELTER', u08,
       'QA - Av. sintético 7800, CABA', 'PENDING', false),
      ('QA - Refugio Sin Solicitud', 'qa-cc-shelter-noreq', 'SHELTER', u09,
       'QA - Av. sintético 1200, CABA', 'NOT_REQUESTED', false),
      ('QA - Veterinaria Centro', 'qa-cc-vet-centro', 'VETERINARY', u10,
       'QA - Tucumán sintético 900, CABA', 'VERIFIED', false),
      ('QA - Pet Shop Centro', 'qa-cc-petshop-centro', 'PROVIDER', u13,
       'QA - Florida sintético 600, CABA', 'NOT_REQUESTED', false),
      ('QA - Veterinaria Norte', 'qa-cc-vet-norte', 'VETERINARY', u16,
       'QA - Cabildo sintético 2100, CABA', 'VERIFIED', false)
    ) as v(name, slug, label, uid, address, vstatus, recv)
   where not exists (select 1 from public.organizations o where o.slug = v.slug);

  select id into o07 from public.organizations where slug = 'qa-cc-shelter-verified';
  select id into o08 from public.organizations where slug = 'qa-cc-shelter-pending';
  select id into o09 from public.organizations where slug = 'qa-cc-shelter-noreq';
  select id into o10 from public.organizations where slug = 'qa-cc-vet-centro';
  select id into o13 from public.organizations where slug = 'qa-cc-petshop-centro';
  select id into o16 from public.organizations where slug = 'qa-cc-vet-norte';

  update public.organizations set
    name = 'QA - Refugio Verificado',
    lifecycle_status = 'ACTIVE',
    verification_status = 'VERIFIED',
    receive_nearby_cases = true,
    address_line = 'QA - Av. sintético 4500, CABA',
    base_location = public._canon_geo_point(-58.4200, -34.6300),
    home_locality_id = loc_caba,
    updated_at = timezone('utc', now())
  where id = o07;
  update public.organizations set
    name = 'QA - Refugio Pending',
    lifecycle_status = 'ACTIVE',
    verification_status = 'PENDING',
    receive_nearby_cases = false,
    address_line = 'QA - Av. sintético 7800, CABA',
    base_location = public._canon_geo_point(-58.3500, -34.6400),
    updated_at = timezone('utc', now())
  where id = o08;
  update public.organizations set
    name = 'QA - Refugio Sin Solicitud',
    lifecycle_status = 'ACTIVE',
    verification_status = 'NOT_REQUESTED',
    receive_nearby_cases = false,
    address_line = 'QA - Av. sintético 1200, CABA',
    base_location = public._canon_geo_point(-58.4200, -34.5750),
    updated_at = timezone('utc', now())
  where id = o09;
  update public.organizations set
    name = 'QA - Veterinaria Centro',
    lifecycle_status = 'ACTIVE',
    verification_status = 'VERIFIED',
    receive_nearby_cases = false,
    address_line = 'QA - Tucumán sintético 900, CABA',
    base_location = public._canon_geo_point(-58.3720, -34.5950),
    updated_at = timezone('utc', now())
  where id = o10;
  update public.organizations set
    name = 'QA - Pet Shop Centro',
    lifecycle_status = 'ACTIVE',
    verification_status = 'NOT_REQUESTED',
    receive_nearby_cases = false,
    address_line = 'QA - Florida sintético 600, CABA',
    base_location = public._canon_geo_point(-58.3816, -34.5820),
    updated_at = timezone('utc', now())
  where id = o13;
  update public.organizations set
    name = 'QA - Veterinaria Norte',
    lifecycle_status = 'ACTIVE',
    verification_status = 'VERIFIED',
    receive_nearby_cases = false,
    address_line = 'QA - Cabildo sintético 2100, CABA',
    base_location = public._canon_geo_point(-58.4560, -34.5280),
    updated_at = timezone('utc', now())
  where id = o16;

  insert into public.organization_capabilities (organization_id, capability) values
    (o07, 'SHELTER'),
    (o08, 'SHELTER'),
    (o09, 'SHELTER'),
    (o10, 'VETERINARY_CLINIC'),
    (o13, 'PROVIDER'),
    (o16, 'VETERINARY_CLINIC')
  on conflict do nothing;

  insert into public.organization_public_profiles (organization_id, bio, published) values
    (o07, 'Refugio QA verificado. Custodia, tránsito y adopción.', true),
    (o08, 'Refugio QA con verificación PENDING.', true),
    (o09, 'Refugio QA listo para solicitar verificación. Solicitud NO enviada.', true),
    (o10, 'Veterinaria QA Centro. Consultorio, pacientes y agenda. Lun-Vie 09:00-18:00. Sábado 09:00-13:00. Tel +54 11 5555-0110. qa10.vet@leoverapp.com', true),
    (o13, 'QA - Pet Shop Centro. Aún no verificado. Tel +54 11 5555-0113. Lun-Vie 10:00-19:00.', true),
    (o16, 'Veterinaria QA Norte. Historias privadas aisladas de Centro.', true)
  on conflict (organization_id) do update
    set bio = excluded.bio,
        published = true;

  insert into public.organization_roles (organization_id, code, name, is_system) values
    (o07, 'ADMIN', 'Admin', true),
    (o08, 'ADMIN', 'Admin', true),
    (o09, 'ADMIN', 'Admin', true),
    (o10, 'ADMIN', 'Admin', true),
    (o13, 'ADMIN', 'Admin', true),
    (o16, 'ADMIN', 'Admin', true)
  on conflict (organization_id, code) do nothing;
  select id into r07 from public.organization_roles where organization_id = o07 and code = 'ADMIN';
  select id into r08 from public.organization_roles where organization_id = o08 and code = 'ADMIN';
  select id into r09 from public.organization_roles where organization_id = o09 and code = 'ADMIN';
  select id into r10 from public.organization_roles where organization_id = o10 and code = 'ADMIN';
  select id into r13 from public.organization_roles where organization_id = o13 and code = 'ADMIN';
  select id into r16 from public.organization_roles where organization_id = o16 and code = 'ADMIN';

  -- Product OWNER/ADMIN roles receive every ORG permission, including
  -- org.pets.transfer (1071 backfill from org.pets.manage, and
  -- canon_create_organization / _canon_ensure_org_role). These QA ADMIN
  -- roles are inserted directly, so they never received that set.
  -- QA07 (qa07shelter / QA - Refugio Verificado / qa-cc-shelter-verified)
  -- must be able to initiate the canonical care transfer as shelter admin.
  -- This is the normal role-permission mechanism, not a production exception.
  insert into public.organization_role_permissions (role_id, permission_code)
  select r.id, c.code
    from public.organization_roles r
    join public.permission_codes c on c.scope = 'ORG'
   where r.id in (r07, r08, r09, r10, r13, r16)
  on conflict do nothing;

  insert into public.organization_memberships (organization_id, person_id, role_id, status)
  select v.oid, v.pid, v.rid, 'ACTIVE'
    from (values
      (o07, u07, r07),
      (o08, u08, r08),
      (o09, u09, r09),
      (o10, u10, r10),
      (o10, u11, r10),
      (o13, u13, r13),
      (o16, u16, r16)
    ) as v(oid, pid, rid)
   where not exists (
     select 1 from public.organization_memberships m
      where m.organization_id = v.oid and m.person_id = v.pid and m.status = 'ACTIVE'
   );

  insert into public.clinic_affiliations (professional_profile_id, organization_id, status)
  values (pro11, o10, 'ACTIVE')
  on conflict (professional_profile_id, organization_id) do update set status = 'ACTIVE';
  insert into public.clinic_affiliations (professional_profile_id, organization_id, status)
  values (pro16, o16, 'ACTIVE')
  on conflict (professional_profile_id, organization_id) do update set status = 'ACTIVE';

  insert into public.leover_verification_requests (
    subject_kind, person_id, organization_id, function_code, status, evidence, reviewed_at
  )
  select v.kind, v.pid, v.oid, v.fn, v.status,
         jsonb_build_object('terms_accepted', 'true', 'seed', 'qa-cc-actors-01'),
         case when v.status = 'VERIFIED' then timezone('utc', now()) else null end
    from (values
      ('PERSON', u03, null::uuid, 'RESCUER', 'VERIFIED'),
      ('PERSON', u04, null, 'RESCUER', 'VERIFIED'),
      ('PERSON', u05, null, 'RESCUER', 'VERIFIED'),
      ('PERSON', u06, null, 'FOSTER', 'VERIFIED'),
      ('ORGANIZATION', u07, o07, 'SHELTER', 'VERIFIED'),
      ('ORGANIZATION', u08, o08, 'SHELTER', 'PENDING'),
      ('ORGANIZATION', u10, o10, 'VETERINARY', 'VERIFIED'),
      ('PERSON', u11, null, 'PROFESSIONAL', 'VERIFIED'),
      ('PERSON', u12, null, 'PROFESSIONAL', 'VERIFIED'),
      ('ORGANIZATION', u16, o16, 'VETERINARY', 'VERIFIED'),
      ('PERSON', u16, null, 'PROFESSIONAL', 'VERIFIED')
    ) as v(kind, pid, oid, fn, status)
   where not exists (
     select 1 from public.leover_verification_requests r
      where r.person_id = v.pid
        and r.function_code = v.fn
        and r.organization_id is not distinct from v.oid
   );

  insert into public.actor_verifications (subject_kind, subject_person_id, subject_organization_id, kind, status)
  select 'PERSON', u03, null, 'RESCUER', 'GRANTED'
   where not exists (select 1 from public.actor_verifications a where a.subject_person_id = u03 and a.kind = 'RESCUER' and a.status = 'GRANTED');
  insert into public.actor_verifications (subject_kind, subject_organization_id, kind, status)
  select 'ORGANIZATION', o07, 'SHELTER', 'GRANTED'
   where not exists (select 1 from public.actor_verifications a where a.subject_organization_id = o07 and a.kind = 'SHELTER' and a.status = 'GRANTED');
  insert into public.actor_verifications (subject_kind, subject_organization_id, kind, status)
  select 'ORGANIZATION', o10, 'VETERINARY', 'GRANTED'
   where not exists (select 1 from public.actor_verifications a where a.subject_organization_id = o10 and a.kind = 'VETERINARY' and a.status = 'GRANTED');

  insert into public.membership_entitlements (person_id, function_code, trial_started_at, trial_ends_at, status, paywall_enforced)
  values
    (u10, 'VETERINARY', timezone('utc', now()), timezone('utc', now()) + interval '90 days', 'TRIAL', false),
    (u11, 'PROFESSIONAL', timezone('utc', now()), timezone('utc', now()) + interval '90 days', 'TRIAL', false),
    (u12, 'PROFESSIONAL', timezone('utc', now()), timezone('utc', now()) + interval '90 days', 'TRIAL', false),
    (u13, 'BUSINESS', timezone('utc', now()), timezone('utc', now()) + interval '90 days', 'TRIAL', false),
    (u16, 'VETERINARY', timezone('utc', now()), timezone('utc', now()) + interval '90 days', 'TRIAL', false)
  on conflict (person_id, function_code) do update
    set status = 'TRIAL',
        paywall_enforced = false;

  insert into public.adoption_general_profiles (
    person_id, housing_type, housing_tenure, animals_allowed, adults_count, children_count,
    allergies, other_pets, experience, hours_alone, primary_caretaker, vet_reference, motivation, notes
  ) values
    (u01, 'APARTMENT', 'OWNER', true, 2, 0, 'Ninguna', 'No', 'Dueño de Mora y Milo', '4h', 'QA01', 'QA10', 'Dueño QA', 'Perfil dueño para postular si hace falta.'),
    (u14, 'HOUSE', 'OWNER', true, 2, 1, 'Ninguna', 'Un gato propio', 'Adopciones previas', '3h', 'QA14', 'Veterinaria de barrio', 'Quiero adoptar con seguimiento', 'Perfil adopción A completo.'),
    (u15, 'APARTMENT', 'RENT', true, 1, 0, 'Ninguna', 'No', 'Primera adopción', '5h', 'QA15', 'QA10', 'Hogar responsable', 'Perfil adopción B completo.')
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

  -- Pets (ASCII names; rename + keep oldest if a prior run mojibake-duplicated)
  update public.pets
     set name = 'QA - Luna Adopcion'
   where name like 'QA - Luna%'
     and name is distinct from 'QA - Luna Adopcion';
  update public.pets
     set name = 'QA - Bruno Transito'
   where name like 'QA - Bruno%'
     and name is distinct from 'QA - Bruno Transito';

  delete from public.adoption_publications a
   using public.pets p
   where a.pet_id = p.id
     and p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (
       select min(p2.created_at) from public.pets p2 where p2.name = p.name
     );
  delete from public.vitacora_update_proposals x
   using public.pets p
   where x.pet_id = p.id
     and p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (select min(p2.created_at) from public.pets p2 where p2.name = p.name);
  delete from public.vitacora_integration_links x
   using public.pets p
   where x.pet_id = p.id
     and p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (select min(p2.created_at) from public.pets p2 where p2.name = p.name);
  delete from public.vitacora_profiles x
   using public.pets p
   where x.pet_id = p.id
     and p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (select min(p2.created_at) from public.pets p2 where p2.name = p.name);
  delete from public.pet_permission_grants x
   using public.pets p
   where x.pet_id = p.id
     and p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (select min(p2.created_at) from public.pets p2 where p2.name = p.name);
  delete from public.pet_responsibility_events x
   using public.pets p
   where x.pet_id = p.id
     and p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (select min(p2.created_at) from public.pets p2 where p2.name = p.name);
  delete from public.pet_lifecycle_events x
   using public.pets p
   where x.pet_id = p.id
     and p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (select min(p2.created_at) from public.pets p2 where p2.name = p.name);
  delete from public.pet_responsibility_links x
   using public.pets p
   where x.pet_id = p.id
     and p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (select min(p2.created_at) from public.pets p2 where p2.name = p.name);
  delete from public.pets p
   where p.name in ('QA - Luna Adopcion', 'QA - Bruno Transito')
     and p.created_at > (select min(p2.created_at) from public.pets p2 where p2.name = p.name);

  insert into public.pets (
    created_by_user_id, name, species_code, breed_id, sex, size, home_locality_id,
    birth_precision, birth_date, lifecycle_status, origin_kind,
    current_custodian_kind, current_custodian_person_id
  )
  select u01, 'QA - Mora', 'DOG', breed_dog, 'FEMALE', 'MEDIUM', loc_caba,
         'EXACT_DATE', date '2021-04-12', 'ACTIVE', 'STANDARD', 'PERSON', u01
   where not exists (select 1 from public.pets p where p.name = 'QA - Mora');
  insert into public.pets (
    created_by_user_id, name, species_code, breed_id, sex, size, home_locality_id,
    birth_precision, birth_year, lifecycle_status, origin_kind,
    current_custodian_kind, current_custodian_person_id
  )
  select u01, 'QA - Milo', 'CAT', breed_cat, 'MALE', 'SMALL', loc_caba,
         'YEAR_PRECISION', 2022, 'ACTIVE', 'STANDARD', 'PERSON', u01
   where not exists (select 1 from public.pets p where p.name = 'QA - Milo');
  insert into public.pets (
    created_by_user_id, name, species_code, breed_id, sex, size, home_locality_id,
    birth_precision, birth_date, lifecycle_status, origin_kind,
    current_custodian_kind, current_custodian_organization_id
  )
  select u07, 'QA - Luna Adopcion', 'DOG', breed_dog, 'FEMALE', 'MEDIUM', loc_caba,
         'EXACT_DATE', date '2023-08-01', 'ACTIVE', 'STANDARD', 'ORGANIZATION', o07
   where not exists (select 1 from public.pets p where p.name like 'QA - Luna%');
  insert into public.pets (
    created_by_user_id, name, species_code, breed_id, sex, size, home_locality_id,
    birth_precision, birth_year, lifecycle_status, origin_kind,
    current_custodian_kind, current_custodian_organization_id
  )
  select u07, 'QA - Bruno Transito', 'DOG', breed_dog, 'MALE', 'LARGE', loc_caba,
         'YEAR_PRECISION', 2020, 'ACTIVE', 'STANDARD', 'ORGANIZATION', o07
   where not exists (select 1 from public.pets p where p.name like 'QA - Bruno%');

  select id into pet_mora from public.pets where name = 'QA - Mora' limit 1;
  select id into pet_milo from public.pets where name = 'QA - Milo' limit 1;
  select id into pet_luna from public.pets where name = 'QA - Luna Adopcion' limit 1;
  select id into pet_bruno from public.pets where name = 'QA - Bruno Transito' limit 1;

  update public.pets set
    lifecycle_status = 'ACTIVE',
    origin_kind = 'STANDARD',
    species_code = 'DOG',
    sex = 'FEMALE',
    size = 'MEDIUM',
    breed_id = coalesce(breed_id, breed_dog),
    current_custodian_kind = 'PERSON',
    current_custodian_person_id = u01,
    current_custodian_organization_id = null
  where id = pet_mora;
  update public.pets set
    lifecycle_status = 'ACTIVE',
    origin_kind = 'STANDARD',
    species_code = 'CAT',
    sex = 'MALE',
    size = 'SMALL',
    current_custodian_kind = 'PERSON',
    current_custodian_person_id = u01
  where id = pet_milo;
  update public.pets set
    lifecycle_status = 'ACTIVE',
    origin_kind = 'STANDARD',
    current_custodian_kind = 'ORGANIZATION',
    current_custodian_organization_id = o07,
    current_custodian_person_id = null
  where id = pet_luna;
  update public.pets set
    lifecycle_status = 'ACTIVE',
    origin_kind = 'STANDARD',
    current_custodian_kind = 'ORGANIZATION',
    current_custodian_organization_id = o07,
    current_custodian_person_id = null
  where id = pet_bruno;

  insert into public.vitacora_profiles (pet_id, visibility)
  values (pet_mora, 'PRIVATE'), (pet_milo, 'PRIVATE'), (pet_luna, 'PRIVATE'), (pet_bruno, 'PRIVATE')
  on conflict (pet_id) do nothing;

  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, holder_organization_id, role, status, granted_by_actor_user_id
  )
  select * from (values
    (pet_mora, 'PERSON', u01, null::uuid, 'OWNER', 'ACTIVE', u01),
    (pet_milo, 'PERSON', u01, null, 'OWNER', 'ACTIVE', u01),
    (pet_luna, 'ORGANIZATION', null, o07, 'RESPONSIBLE', 'ACTIVE', u07),
    (pet_bruno, 'ORGANIZATION', null, o07, 'RESPONSIBLE', 'ACTIVE', u07)
  ) as v(pet_id, holder_kind, holder_person_id, holder_organization_id, role, status, granted_by)
   where not exists (
     select 1 from public.pet_responsibility_links l
      where l.pet_id = v.pet_id
        and l.status = 'ACTIVE'
        and l.holder_kind = v.holder_kind
        and l.holder_person_id is not distinct from v.holder_person_id
        and l.holder_organization_id is not distinct from v.holder_organization_id
        and l.role = v.role
   );

  insert into public.pet_declared_health (pet_id, notes, updated_by)
  values (pet_mora, 'QA Mora: vacunas al día. Dataset STAGING.', u01)
  on conflict (pet_id) do update set notes = excluded.notes;

  insert into public.adoption_publications (pet_id, published_by, organization_id, status)
  select pet_luna, u07, o07, 'OPEN'
   where not exists (
     select 1 from public.adoption_publications a
      where a.pet_id = pet_luna and a.status = 'OPEN'
   );

  -- Professional access to Mora without transferring ownership.
  insert into public.vitacora_access_grants (
    pet_id, grantee_kind, grantee_person_id, grantee_organization_id,
    purpose, scope, granted_by_actor_user_id
  )
  select pet_mora, 'ORGANIZATION', null, o10, 'VETERINARY_CARE', 'ESSENTIAL_AND_HEALTH', u01
   where not exists (
     select 1 from public.vitacora_access_grants g
      where g.pet_id = pet_mora and g.grantee_organization_id = o10 and g.revoked_at is null
   )
  returning id into grant_org;
  insert into public.vitacora_access_grants (
    pet_id, grantee_kind, grantee_person_id, purpose, scope, granted_by_actor_user_id
  )
  select pet_mora, 'PERSON', u11, 'VETERINARY_CARE', 'ESSENTIAL_AND_HEALTH', u01
   where not exists (
     select 1 from public.vitacora_access_grants g
      where g.pet_id = pet_mora and g.grantee_person_id = u11 and g.revoked_at is null
   )
  returning id into grant_pro;

  insert into public.pet_permission_grants (pet_id, subject_person_id, subject_organization_id, permission_code, granted_by)
  select pet_mora, u11, null, code, u01
    from public.permission_codes
   where code in ('pet.view', 'vitacora.view', 'health.manage_declared')
     and not exists (
       select 1 from public.pet_permission_grants g
        where g.pet_id = pet_mora and g.subject_person_id = u11
          and g.permission_code = permission_codes.code and g.revoked_at is null
     );
  insert into public.pet_permission_grants (pet_id, subject_organization_id, permission_code, granted_by)
  select pet_mora, o10, code, u01
    from public.permission_codes
   where code in ('pet.view', 'vitacora.view', 'health.manage_declared')
     and not exists (
       select 1 from public.pet_permission_grants g
        where g.pet_id = pet_mora and g.subject_organization_id = o10
          and g.permission_code = permission_codes.code and g.revoked_at is null
     );

  insert into public.veterinary_care_records (
    pet_id, actor_user_id, professional_profile_id, organization_id, provenance, summary, care_on
  )
  select pet_mora, u11, pro11, o10, 'PROFESSIONAL',
         'QA privado Centro: control clínico de Mora. Solo historial de QA10/QA11.',
         current_date - 7
   where not exists (
     select 1 from public.veterinary_care_records r
      where r.pet_id = pet_mora and r.organization_id = o10 and r.summary like 'QA privado Centro%'
   )
  returning id into care_private;

  insert into public.veterinary_care_records (
    pet_id, actor_user_id, professional_profile_id, organization_id, provenance, summary, care_on
  )
  select pet_mora, u11, pro11, o10, 'PROFESSIONAL',
         'QA visible VitaCora: desparasitación aceptada por dueño.',
         current_date - 21
   where not exists (
     select 1 from public.veterinary_care_records r
      where r.pet_id = pet_mora and r.summary like 'QA visible VitaCora%'
   )
  returning id into care_visible;

  if care_private is null then
    select id into care_private from public.veterinary_care_records
     where pet_id = pet_mora and summary like 'QA privado Centro%' limit 1;
  end if;
  if care_visible is null then
    select id into care_visible from public.veterinary_care_records
     where pet_id = pet_mora and summary like 'QA visible VitaCora%' limit 1;
  end if;

  insert into public.vitacora_update_proposals (
    pet_id, origin_kind, payload, source_table, source_record_id, status,
    actor_user_id, organization_id, professional_profile_id
  )
  select pet_mora, 'VET',
         jsonb_build_object('kind', 'CARE', 'summary', 'Propuesta PENDING: vacuna anual sugerida'),
         'veterinary_care_records', care_private, 'PENDING', u11, o10, pro11
   where care_private is not null
     and not exists (
       select 1 from public.vitacora_update_proposals p
        where p.pet_id = pet_mora and p.status = 'PENDING' and p.organization_id = o10
     );

  insert into public.vitacora_update_proposals (
    pet_id, origin_kind, payload, source_table, source_record_id, status,
    actor_user_id, organization_id, professional_profile_id, decided_by, decided_at
  )
  select pet_mora, 'VET',
         jsonb_build_object('kind', 'CARE', 'summary', 'Desparasitación aceptada en VitaCora'),
         'veterinary_care_records', care_visible, 'ACCEPTED', u11, o10, pro11, u01, timezone('utc', now())
   where care_visible is not null
     and not exists (
       select 1 from public.vitacora_update_proposals p
        where p.pet_id = pet_mora and p.status = 'ACCEPTED' and p.source_record_id = care_visible
     );

  if care_visible is not null then
    insert into public.vitacora_integration_links (
      pet_id, source_table, source_record_id, visible, created_by
    )
    values (pet_mora, 'veterinary_care_records', care_visible, true, u01)
    on conflict (pet_id, source_table, source_record_id) do update
      set visible = true,
          hidden_at = null;
  end if;

  -- Community hours / public premises for vet and shop.
  insert into public.service_providers (holder_kind, holder_organization_id, display_name, public_geo, geo_is_public_premises, public_address_text)
  select 'ORGANIZATION', o10, 'QA - Veterinaria Centro',
         public._canon_geo_point(-58.3720, -34.5950), true, 'QA - Tucumán sintético 900, CABA'
   where not exists (select 1 from public.service_providers s where s.display_name = 'QA - Veterinaria Centro');
  insert into public.service_providers (holder_kind, holder_organization_id, display_name, public_geo, geo_is_public_premises, public_address_text)
  select 'ORGANIZATION', o13, 'QA - Pet Shop Centro',
         public._canon_geo_point(-58.3816, -34.5820), true, 'QA - Florida sintético 600, CABA'
   where not exists (select 1 from public.service_providers s where s.display_name = 'QA - Pet Shop Centro');
  insert into public.service_providers (holder_kind, holder_organization_id, display_name, public_geo, geo_is_public_premises, public_address_text)
  select 'ORGANIZATION', o16, 'QA - Veterinaria Norte',
         public._canon_geo_point(-58.4560, -34.5280), true, 'QA - Cabildo sintético 2100, CABA'
   where not exists (select 1 from public.service_providers s where s.display_name = 'QA - Veterinaria Norte');
  insert into public.service_providers (holder_kind, holder_person_id, display_name, public_geo, geo_is_public_premises, public_address_text)
  select 'PERSON', u12, 'QA12 Profesional Independiente',
         public._canon_geo_point(-58.3600, -34.6037), false, null
   where not exists (select 1 from public.service_providers s where s.display_name = 'QA12 Profesional Independiente');

  select id into sp10 from public.service_providers where display_name = 'QA - Veterinaria Centro';
  select id into sp13 from public.service_providers where display_name = 'QA - Pet Shop Centro';
  select id into sp16 from public.service_providers where display_name = 'QA - Veterinaria Norte';

  delete from public.provider_weekly_hours where provider_id in (sp10, sp13, sp16);
  insert into public.provider_weekly_hours (provider_id, weekday, closed, opens_at, closes_at)
  select sp10, (h->>'weekday')::smallint, (h->>'closed')::boolean,
         nullif(h->>'opens_at', '')::time, nullif(h->>'closes_at', '')::time
    from jsonb_array_elements(v_hours) h
   where sp10 is not null;
  insert into public.provider_weekly_hours (provider_id, weekday, closed, opens_at, closes_at)
  values
    (sp13, 1, false, '10:00', '19:00'),
    (sp13, 2, false, '10:00', '19:00'),
    (sp13, 3, false, '10:00', '19:00'),
    (sp13, 4, false, '10:00', '19:00'),
    (sp13, 5, false, '10:00', '19:00'),
    (sp13, 6, false, '10:00', '14:00'),
    (sp13, 7, true, null, null);
  insert into public.provider_weekly_hours (provider_id, weekday, closed, opens_at, closes_at)
  select sp16, (h->>'weekday')::smallint, (h->>'closed')::boolean,
         nullif(h->>'opens_at', '')::time, nullif(h->>'closes_at', '')::time
    from jsonb_array_elements(v_hours) h
   where sp16 is not null;

  -- No LOST/FOUND, no adoption applications, no foster placement, no fake owner-email E2E.
end;
$seed$;

select 'QA_ACTORS_SQL_OK' as k, 1 as n;
