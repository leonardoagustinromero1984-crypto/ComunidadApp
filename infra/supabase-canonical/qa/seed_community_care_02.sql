-- QA COMMUNITY CARE 02 dataset. STAGING only. Idempotent.
-- Does not run in production. Requires existing QA persons when available;
-- otherwise attaches to the first ACTIVE person so nearby has visible rows.

do $$
declare
  v_actor uuid;
  v_rescuer uuid;
  v_foster uuid;
  v_pro uuid;
  v_org uuid;
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
begin
  select user_id into v_actor from public.persons
   where username in (
     'qa07shelter', 'qa10vet', 'qa03rescuer',
     'qa07_shelter', 'qa10_vet', 'qa03_rescuer',
     'qa_org_admin', 'qa_vet', 'qa_rescuer'
   )
   order by case username
     when 'qa07shelter' then 0
     when 'qa10vet' then 1
     when 'qa03rescuer' then 2
     when 'qa07_shelter' then 3
     when 'qa10_vet' then 4
     when 'qa03_rescuer' then 5
     when 'qa_org_admin' then 6
     when 'qa_vet' then 7
     else 8
   end
   limit 1;
  if v_actor is null then
    select user_id into v_actor from public.persons
     where lifecycle_status = 'ACTIVE'
     order by created_at
     limit 1;
  end if;
  if v_actor is null then
    raise exception 'QA_COMMUNITY_PERSONS_MISSING';
  end if;

  select user_id into v_rescuer from public.persons
   where username in ('qa03rescuer', 'qa03_rescuer', 'qa_rescuer')
   order by case username when 'qa03rescuer' then 0 when 'qa03_rescuer' then 1 else 2 end
   limit 1;
  select user_id into v_foster from public.persons
   where username in ('qa06foster', 'qa06_foster', 'qa_public')
   order by case username when 'qa06foster' then 0 when 'qa06_foster' then 1 else 2 end
   limit 1;
  select user_id into v_pro from public.persons
   where username in ('qa12proind', 'qa12_pro', 'qa_provider')
   order by case username when 'qa12proind' then 0 when 'qa12_pro' then 1 else 2 end
   limit 1;
  v_rescuer := coalesce(v_rescuer, v_actor);
  v_foster := coalesce(v_foster, v_actor);
  v_pro := coalesce(v_pro, v_actor);

  if not exists (select 1 from public.persons where username in ('qa03rescuer', 'qa03_rescuer')) then
    update public.persons
       set base_location = public._canon_geo_point(-58.3816, -34.6037),
           base_address = 'QA - Microcentro, CABA',
           receive_nearby_cases = true,
           home_locality_id = coalesce(home_locality_id, 'loc-ar-loc-caba')
     where user_id = v_rescuer;
  end if;
  if not exists (select 1 from public.persons where username in ('qa06foster', 'qa06_foster')) then
    update public.persons
       set base_location = public._canon_geo_point(-58.4500, -34.5500),
           base_address = 'QA - Belgrano, CABA',
           receive_nearby_cases = true
     where user_id = v_foster;
  end if;
  if not exists (select 1 from public.persons where username in ('qa12proind', 'qa12_pro')) then
    update public.persons
       set base_location = public._canon_geo_point(-58.3731, -34.6083),
           base_address = 'QA - Congreso, CABA',
           receive_nearby_cases = true
     where user_id = v_pro;
  end if;

  insert into public.person_capabilities (user_id, capability, active, verification_status)
  values
    (v_rescuer, 'RESCUER', true, 'VERIFIED'),
    (v_foster, 'FOSTER', true, 'VERIFIED')
  on conflict (user_id, capability) do update
    set active = true,
        verification_status = case
          when public.person_capabilities.verification_status = 'VERIFIED' then 'VERIFIED'
          else excluded.verification_status
        end,
        updated_at = timezone('utc', now());

  insert into public.professional_profiles (person_id)
  values (v_pro)
  on conflict (person_id) do nothing;

  for rec in
    select * from (values
      ('qa-cc02-shelter-n', 'QA - Refugio Norte', 'SHELTER', 'SHELTER', true, -58.4508, -34.5497, 'Belgrano, CABA', 'VERIFIED'),
      ('qa-cc02-shelter-u', 'QA - Refugio Sur no verificado', 'SHELTER', 'SHELTER', true, -58.3730, -34.7200, 'Avellaneda, Buenos Aires', 'NOT_REQUESTED'),
      ('qa-cc02-vet-centro', 'QA - Veterinaria Centro', 'VETERINARY', 'VETERINARY_CLINIC', true, -58.3816, -34.6037, 'San Nicolás, CABA', 'VERIFIED'),
      ('qa-cc02-vet-cerrada', 'QA - Veterinaria Cerrada', 'VETERINARY', 'VETERINARY_CLINIC', true, -58.4450, -34.6100, 'Caballito, CABA', 'NOT_REQUESTED'),
      ('qa-cc02-shop', 'QA - Pet Shop Palermo', 'PROVIDER', 'PROVIDER', true, -58.4300, -34.5880, 'Palermo, CABA', 'PENDING'),
      ('qa-cc02-far', 'QA - Veterinaria Lejos', 'VETERINARY', 'VETERINARY_CLINIC', true, -58.5400, -34.8500, 'San Vicente, Buenos Aires', 'VERIFIED')
    ) as t(slug, name, label, cap, published, lng, lat, address, vstatus)
  loop
    insert into public.organizations (name, slug, primary_label, home_locality_id, created_by_user_id, lifecycle_status)
    select rec.name, rec.slug, rec.label, 'loc-ar-loc-caba', v_actor, 'ACTIVE'
     where not exists (select 1 from public.organizations o where o.slug = rec.slug);

    select id into v_org from public.organizations where slug = rec.slug;
    update public.organizations
       set name = rec.name,
           lifecycle_status = 'ACTIVE',
           base_location = public._canon_geo_point(rec.lng, rec.lat),
           address_line = rec.address,
           receive_nearby_cases = true,
           verification_status = rec.vstatus,
           updated_at = timezone('utc', now())
     where id = v_org;

    insert into public.organization_capabilities (organization_id, capability)
    values (v_org, rec.cap)
    on conflict do nothing;

    insert into public.organization_public_profiles (organization_id, bio, published)
    values (v_org, rec.name || ' · dataset QA Community Care 02.', rec.published)
    on conflict (organization_id) do update
      set published = excluded.published,
          bio = excluded.bio;

    insert into public.organization_roles (organization_id, code, name, is_system)
    values (v_org, 'ADMIN', 'Admin', true)
    on conflict (organization_id, code) do nothing;

    insert into public.organization_memberships (organization_id, person_id, role_id, status)
    select v_org, v_actor, r.id, 'ACTIVE'
      from public.organization_roles r
     where r.organization_id = v_org and r.code = 'ADMIN'
       and not exists (
         select 1 from public.organization_memberships m
          where m.organization_id = v_org and m.person_id = v_actor and m.status = 'ACTIVE'
       );
  end loop;
end;
$$;
