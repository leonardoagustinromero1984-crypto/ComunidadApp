-- LeoVer 17B.12 STAGING fixture. Not a production migration.
-- Depends on scripts/qa/seed-community-care-actors.ps1 and
-- infra/supabase-canonical/qa/seed_community_care_02.sql (QA01–QA16 and both shelters).
-- Does not change passwords and does not create auth users.
-- Every new row is identifiable as QA17B12.
--
-- Accidental execution is refused unless the runner has already set
-- leover.qa_target to the STAGING ref. The PowerShell guard is still required.

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
  v_owner uuid;
  v_adopter uuid;
  v_foster uuid;
  v_vet uuid;
  v_pet_a uuid;
  v_pet_b uuid;
  v_provider uuid;
  v_offering uuid;
  v_category text;
  v_request uuid;
  v_publication uuid;
  required_tables text[] := array[
    'public.organizations',
    'public.persons',
    'public.pets',
    'public.m17_donation_campaigns',
    'public.m17_in_kind_needs',
    'public.m17_volunteer_opportunities',
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
    'public.vitacora_update_proposals'
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
  select user_id into v_owner from public.persons where username = 'qa07shelter';
  select user_id into v_adopter from public.persons where username = 'qa14adopter';
  select user_id into v_foster from public.persons where username = 'qa06foster';
  select user_id into v_vet from public.persons where username = 'qa10vet';
  if v_owner is null or v_adopter is null or v_foster is null or v_vet is null then
    raise exception 'QA17B12_ABORT_ACTORS_MISSING run seed-community-care-actors.ps1';
  end if;

  select id into v_pet_a from public.pets order by created_at limit 1;
  select id into v_pet_b from public.pets where id is distinct from v_pet_a order by created_at limit 1;
  if v_pet_a is null then
    raise exception 'QA17B12_ABORT_PRIOR_SEED_PET_MISSING';
  end if;

  delete from public.bookings
   where provider_id in (
     select id from public.service_providers where display_name like 'QA17B12 %'
   );
  delete from public.service_offerings
   where provider_id in (
     select id from public.service_providers where display_name like 'QA17B12 %'
   );
  delete from public.service_providers where display_name like 'QA17B12 %';
  delete from public.adoption_applications
   where publication_id in (
     select id from public.adoption_publications where note like 'QA17B12 %' or title like 'QA17B12 %'
   );
  delete from public.adoption_publications where note like 'QA17B12 %' or title like 'QA17B12 %';
  delete from public.foster_care_applications
   where request_id in (select id from public.foster_care_requests where notes like 'QA17B12 %');
  delete from public.foster_care_requests where notes like 'QA17B12 %';
  delete from public.vitacora_update_proposals where payload->>'marker' = 'QA17B12';
  delete from public.leover_verification_requests where evidence->>'marker' = 'QA17B12';
  delete from public.lost_found_alerts where location_label like 'QA17B12 %' or note like 'QA17B12 %';
  delete from public.m17_volunteer_opportunities where title like 'QA17B12 %';
  delete from public.m17_in_kind_needs where title like 'QA17B12 %';
  delete from public.m17_donation_campaigns where title like 'QA17B12 %';

  insert into public.m17_donation_campaigns (
    organization_id, title, description, campaign_type, campaign_status,
    goal_amount_minor, currency, public_location_text, moderation_status, published_at
  ) values
    (v_a, 'QA17B12 Aporte Norte', 'Campaña monetaria del refugio A.', 'MEDICAL', 'PUBLISHED',
     15000000, 'ARS', 'Belgrano, CABA', 'APPROVED', timezone('utc', now())),
    (v_b, 'QA17B12 Aporte Sur', 'Campaña monetaria del refugio B.', 'FOOD_AND_SUPPLIES', 'PUBLISHED',
     8000000, 'ARS', 'Avellaneda', 'APPROVED', timezone('utc', now()));

  insert into public.m17_in_kind_needs (
    organization_id, category, title, description, quantity_needed, quantity_unit,
    status, public_location_text, moderation_status, published_at
  ) values
    (v_a, 'FOOD', 'QA17B12 Alimento Norte', 'Balanceado del refugio A.', 20, 'bolsas',
     'PUBLISHED', 'Belgrano, CABA', 'APPROVED', timezone('utc', now())),
    (v_b, 'HYGIENE', 'QA17B12 Higiene Sur', 'Limpieza del refugio B.', 10, 'unidades',
     'PUBLISHED', 'Avellaneda', 'APPROVED', timezone('utc', now()));

  insert into public.m17_volunteer_opportunities (
    organization_id, opportunity_type, title, description, required_people, accepted_people,
    status, public_location_text, moderation_status, published_at
  ) values
    (v_a, 'ANIMAL_CARE', 'QA17B12 Paseos Norte', 'Paseos del refugio A.', 6, 0,
     'PUBLISHED', 'Belgrano, CABA', 'APPROVED', timezone('utc', now())),
    (v_a, 'SHELTER_SUPPORT', 'QA17B12 Cupo Norte', 'Cupo completo del refugio A.', 2, 2,
     'PUBLISHED', 'Belgrano, CABA', 'APPROVED', timezone('utc', now())),
    (v_b, 'EVENTS', 'QA17B12 Sin postulantes Sur', 'Convocatoria del refugio B.', 4, 0,
     'PUBLISHED', 'Avellaneda', 'APPROVED', timezone('utc', now()));

  insert into public.lost_found_alerts (
    kind, created_by, status, created_at, location_label, note, species_code
  ) values
    ('LOST', v_owner, 'OPEN', timezone('utc', now()) - interval '30 days',
     'QA17B12 Perdido histórico', 'QA17B12 caso activo anterior', 
     (select code from public.species where code = 'DOG')),
    ('FOUND', v_owner, 'OPEN', timezone('utc', now()),
     'QA17B12 Encontrado reciente', 'QA17B12 caso posterior',
     (select code from public.species where code = 'DOG')),
    ('LOST', v_owner, 'RESOLVED', timezone('utc', now()) - interval '2 days',
     'QA17B12 Perdido resuelto', 'QA17B12 estado no visible',
     (select code from public.species where code = 'DOG'));

  insert into public.adoption_publications (
    pet_id, published_by, organization_id, status, note, title
  ) values (
    v_pet_a, v_owner, v_a, 'OPEN', 'QA17B12 adopción A', 'QA17B12 Adopción Norte'
  ) returning id into v_publication;

  if v_pet_b is not null then
    insert into public.adoption_publications (
      pet_id, published_by, organization_id, status, note, title
    ) values (
      v_pet_b, v_owner, v_b, 'OPEN', 'QA17B12 adopción B', 'QA17B12 Adopción Sur'
    );
  end if;

  insert into public.adoption_applications (publication_id, applicant_user_id, status)
  values (v_publication, v_adopter, 'PENDING');

  insert into public.foster_care_requests (pet_id, requested_by, needs, notes, status)
  values (v_pet_a, v_owner, 'QA17B12 tránsito', 'QA17B12 tránsito A', 'REQUESTED')
  returning id into v_request;

  insert into public.foster_care_applications (request_id, foster_user_id, status)
  values (v_request, v_foster, 'PENDING');

  select code into v_category from public.service_categories order by code limit 1;
  if v_category is null then
    raise exception 'QA17B12_ABORT_SERVICE_CATEGORY_MISSING';
  end if;

  insert into public.service_providers (holder_kind, holder_person_id, display_name, lifecycle_status)
  values ('PERSON', v_vet, 'QA17B12 Veterinaria Norte', 'ACTIVE')
  returning id into v_provider;

  insert into public.service_offerings (provider_id, category_code, name, active)
  values (v_provider, v_category, 'QA17B12 Consulta', true)
  returning id into v_offering;

  insert into public.bookings (
    offering_id, provider_id, pet_id, booked_by, starts_at, zone_id, status
  ) values (
    v_offering, v_provider, v_pet_a, v_owner,
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

  raise notice 'QA17B12_SEED_OK a=% b=%', v_a, v_b;
end $$;
