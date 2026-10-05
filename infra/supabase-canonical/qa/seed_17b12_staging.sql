-- LeoVer 17B.12 STAGING fixture. Not a production migration.
-- Depends on scripts/qa/seed-community-care-actors.ps1 and
-- infra/supabase-canonical/qa/seed_community_care_02.sql (QA01–QA16 and both shelters).
-- Does not change passwords and does not create auth users.
-- Every new row is identifiable as QA17B12 and belongs to those actors.
--
-- Accidental execution is refused unless the runner has already set
-- leover.qa_target to the STAGING ref. The PowerShell guard is still required.
-- This file is not applied by tests or by a migration.

do $$
begin
  if current_setting('leover.qa_target', true) is distinct from 'tobqbddfcyitwgbkthhy' then
    raise exception 'QA17B12_ABORT_NOT_STAGING';
  end if;
end $$;

do $$
declare
  v_a uuid;
  v_b uuid;
  v_qa01 uuid;
  v_qa02 uuid;
  v_owner uuid;
  v_adopter uuid;
  v_foster uuid;
  v_vet uuid;
  v_pet_a uuid;
  v_pet_b uuid;
  v_provider uuid;
  v_offering uuid;
  v_request uuid;
  v_publication uuid;
  v_campaign_a uuid;
  v_campaign_b uuid;
  required_tables text[] := array[
    'public.organizations',
    'public.persons',
    'public.pets',
    'public.species',
    'public.donation_campaigns',
    'public.in_kind_offers',
    'public.community_events',
    'public.lost_found_alerts',
    'public.adoption_publications',
    'public.adoption_applications',
    'public.service_providers',
    'public.service_offerings',
    'public.service_categories',
    'public.bookings',
    'public.foster_care_requests',
    'public.foster_care_applications',
    'public.leover_verification_requests',
    'public.vitacora_update_proposals',
    'public.pet_responsibility_links'
  ];
  v_table text;
begin
  foreach v_table in array required_tables loop
    if to_regclass(v_table) is null then
      raise exception 'QA17B12_ABORT_TABLE_MISSING:%', v_table;
    end if;
  end loop;
  select id into v_a from public.organizations where slug = 'qa-cc02-shelter-n';
  select id into v_b from public.organizations where slug = 'qa-cc02-shelter-u';
  if v_a is null or v_b is null then
    raise exception 'QA17B12_ABORT_ORGS_MISSING run seed_community_care_02.sql';
  end if;
  select user_id into v_qa01 from public.persons where username = 'qa01owner';
  select user_id into v_qa02 from public.persons where username = 'qa02finder';
  select user_id into v_owner from public.persons where username = 'qa07shelter';
  select user_id into v_adopter from public.persons where username = 'qa14adopter';
  select user_id into v_foster from public.persons where username = 'qa06foster';
  select user_id into v_vet from public.persons where username = 'qa10vet';
  if v_qa01 is null or v_qa02 is null or v_owner is null or v_adopter is null
     or v_foster is null or v_vet is null then
    raise exception 'QA17B12_ABORT_ACTORS_MISSING run seed-community-care-actors.ps1';
  end if;
  if not exists (select 1 from public.species where code = 'DOG') then
    raise exception 'QA17B12_ABORT_SPECIES_DOG_MISSING';
  end if;
  if not exists (select 1 from public.service_categories where code = 'VETERINARY') then
    raise exception 'QA17B12_ABORT_SERVICE_CATEGORY_MISSING';
  end if;

  delete from public.bookings
   where pet_id in (select id from public.pets where name like 'QA17B12 %')
      or provider_id in (
        select id from public.service_providers where display_name like 'QA17B12 %'
      );
  delete from public.service_offerings
   where name like 'QA17B12 %'
      or provider_id in (
        select id from public.service_providers where display_name like 'QA17B12 %'
      );
  delete from public.service_providers where display_name like 'QA17B12 %';
  delete from public.adoption_applications
   where publication_id in (
     select id from public.adoption_publications
      where title like 'QA17B12 %' or note like 'QA17B12 %'
         or pet_id in (select id from public.pets where name like 'QA17B12 %')
   );
  delete from public.adoption_publications
   where title like 'QA17B12 %' or note like 'QA17B12 %'
      or pet_id in (select id from public.pets where name like 'QA17B12 %');
  delete from public.foster_care_applications
   where request_id in (
     select id from public.foster_care_requests
      where notes like 'QA17B12 %'
         or pet_id in (select id from public.pets where name like 'QA17B12 %')
   );
  delete from public.foster_care_requests
   where notes like 'QA17B12 %'
      or pet_id in (select id from public.pets where name like 'QA17B12 %');
  delete from public.vitacora_update_proposals where payload->>'marker' = 'QA17B12';
  delete from public.leover_verification_requests where evidence->>'marker' = 'QA17B12';
  delete from public.lost_found_alerts
   where location_label like 'QA17B12 %' or note like 'QA17B12 %'
      or pet_id in (select id from public.pets where name like 'QA17B12 %');
  delete from public.in_kind_offers where description like 'QA17B12 %';
  delete from public.donation_campaigns where title like 'QA17B12 %';
  delete from public.community_events where title like 'QA17B12 %';
  delete from public.pet_responsibility_links
   where pet_id in (select id from public.pets where name like 'QA17B12 %');
  delete from public.pets where name like 'QA17B12 %';

  insert into public.pets (
    created_by_user_id, name, species_code, sex, size, birth_precision
  ) values (
    v_qa01, 'QA17B12 Mascota Norte', 'DOG', 'UNKNOWN', 'MEDIUM', 'UNKNOWN'
  ) returning id into v_pet_a;

  insert into public.pets (
    created_by_user_id, name, species_code, sex, size, birth_precision
  ) values (
    v_qa02, 'QA17B12 Mascota Sur', 'DOG', 'UNKNOWN', 'MEDIUM', 'UNKNOWN'
  ) returning id into v_pet_b;

  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, role, status, granted_by_actor_user_id
  ) values
    (v_pet_a, 'PERSON', v_qa01, 'OWNER', 'ACTIVE', v_qa01),
    (v_pet_b, 'PERSON', v_qa02, 'OWNER', 'ACTIVE', v_qa02);

  insert into public.donation_campaigns (
    organization_id, created_by, title, status
  ) values (
    v_a, v_owner, 'QA17B12 Aporte Norte', 'OPEN'
  ) returning id into v_campaign_a;

  insert into public.donation_campaigns (
    organization_id, created_by, title, status
  ) values (
    v_b, v_owner, 'QA17B12 Aporte Sur', 'OPEN'
  ) returning id into v_campaign_b;

  insert into public.in_kind_offers (campaign_id, offered_by, description, status) values
    (v_campaign_a, v_owner, 'QA17B12 Alimento Norte', 'OPEN'),
    (v_campaign_b, v_owner, 'QA17B12 Higiene Sur', 'OPEN');

  insert into public.community_events (
    organization_id, created_by, title, starts_at, zone_id
  ) values
    (v_a, v_owner, 'QA17B12 Paseos Norte', timezone('utc', now()) + interval '3 days',
     'America/Argentina/Buenos_Aires'),
    (v_a, v_owner, 'QA17B12 Cupo Norte', timezone('utc', now()) + interval '4 days',
     'America/Argentina/Buenos_Aires'),
    (v_b, v_owner, 'QA17B12 Voluntariado Sur', timezone('utc', now()) + interval '5 days',
     'America/Argentina/Buenos_Aires');

  insert into public.lost_found_alerts (
    kind, pet_id, created_by, status, created_at, location_label, note, species_code
  ) values
    ('LOST', v_pet_a, v_qa01, 'OPEN', timezone('utc', now()) - interval '30 days',
     'QA17B12 Perdido histórico', 'QA17B12 caso activo anterior', 'DOG'),
    ('FOUND', v_pet_b, v_qa02, 'OPEN', timezone('utc', now()),
     'QA17B12 Encontrado reciente', 'QA17B12 caso posterior', 'DOG'),
    ('LOST', v_pet_a, v_qa01, 'CLAIMED', timezone('utc', now()) - interval '10 days',
     'QA17B12 Perdido reclamado', 'QA17B12 estado visible', 'DOG'),
    ('FOUND', v_pet_b, v_qa02, 'IN_CARE', timezone('utc', now()) - interval '1 day',
     'QA17B12 Encontrado en cuidado', 'QA17B12 estado visible', 'DOG'),
    ('LOST', v_pet_a, v_qa01, 'RESOLVED', timezone('utc', now()) - interval '2 days',
     'QA17B12 Perdido resuelto', 'QA17B12 estado no visible', 'DOG');

  insert into public.adoption_publications (
    pet_id, published_by, organization_id, status, note, title
  ) values (
    v_pet_a, v_owner, v_a, 'OPEN', 'QA17B12 adopción A', 'QA17B12 Adopción Norte'
  ) returning id into v_publication;

  insert into public.adoption_publications (
    pet_id, published_by, organization_id, status, note, title
  ) values (
    v_pet_b, v_owner, v_b, 'OPEN', 'QA17B12 adopción B', 'QA17B12 Adopción Sur'
  );

  insert into public.adoption_applications (publication_id, applicant_user_id, status)
  values (v_publication, v_adopter, 'PENDING');

  insert into public.foster_care_requests (pet_id, requested_by, needs, notes, status)
  values (v_pet_a, v_owner, 'QA17B12 tránsito', 'QA17B12 tránsito A', 'REQUESTED')
  returning id into v_request;

  insert into public.foster_care_applications (request_id, foster_user_id, status)
  values (v_request, v_foster, 'PENDING');

  insert into public.service_providers (holder_kind, holder_person_id, display_name, lifecycle_status)
  values ('PERSON', v_vet, 'QA17B12 Veterinaria Norte', 'ACTIVE')
  returning id into v_provider;

  insert into public.service_offerings (provider_id, category_code, name, active)
  values (v_provider, 'VETERINARY', 'QA17B12 Consulta', true)
  returning id into v_offering;

  insert into public.bookings (
    offering_id, provider_id, pet_id, booked_by, starts_at, zone_id, status
  ) values (
    v_offering, v_provider, v_pet_a, v_qa01,
    timezone('utc', now()) + interval '2 days',
    'America/Argentina/Buenos_Aires',
    'REQUESTED'
  );

  insert into public.leover_verification_requests (
    subject_kind, person_id, function_code, status, evidence
  )
  select 'PERSON', v_vet, 'VETERINARY', 'PENDING', '{"marker":"QA17B12"}'::jsonb
  where not exists (
    select 1 from public.leover_verification_requests
    where person_id = v_vet and function_code = 'VETERINARY' and status = 'PENDING'
  );

  insert into public.vitacora_update_proposals (
    pet_id, origin_kind, payload, status, actor_user_id, organization_id
  ) values (
    v_pet_a, 'VET', '{"marker":"QA17B12"}'::jsonb, 'PENDING', v_vet, v_a
  );

  raise notice 'QA17B12_SEED_OK a=% b=% pets=% %', v_a, v_b, v_pet_a, v_pet_b;
end $$;
