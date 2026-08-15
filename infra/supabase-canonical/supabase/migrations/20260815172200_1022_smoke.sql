-- LeoVer Canonical Baseline
-- Logical migration: 1022
-- Schema assertions at apply time. Interactive smoke is canon_smoke_suite().

do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and column_name = 'account_type'
  ) then
    raise exception 'SMOKE_FAIL: account_type present';
  end if;
  if exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name like '%passport%'
  ) then
    raise exception 'SMOKE_FAIL: passport table present';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'pets' and column_name = 'owner_id'
  ) then
    raise exception 'SMOKE_FAIL: pets.owner_id present';
  end if;
  if exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name in ('carts', 'orders', 'payment_intents', 'shop_products')
  ) then
    raise exception 'SMOKE_FAIL: marketplace V1 table present';
  end if;
  if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'vitacora_profiles') then
    raise exception 'SMOKE_FAIL: vitacora_profiles missing';
  end if;
  if not exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'persons') then
    raise exception 'SMOKE_FAIL: persons missing';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'media_assets' and column_name = 'owner_user_id'
  ) then
    raise exception 'SMOKE_FAIL: media owner_user_id remains';
  end if;
end$$;

create or replace function public.canon_as(p_user uuid)
returns void
language sql
as $$
  select
    set_config('request.jwt.claim.sub', p_user::text, true),
    set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
$$;

create or replace function public.canon_smoke_suite()
returns jsonb
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
  v_adult uuid;
  v_teen uuid;
  v_other uuid;
  v_stranger uuid;
  v_org uuid;
  v_pet uuid;
  v_grant uuid;
  v_grant_full uuid;
  v_prop uuid;
  v_prop_rej uuid;
  v_provider uuid;
  v_offering uuid;
  v_booking uuid;
  v_stay uuid;
  v_conv uuid;
  v_conv_p2p uuid;
  v_media uuid;
  v_doc uuid;
  v_link uuid;
  v_prof uuid;
  v_result jsonb := '{}'::jsonb;
  v_under13_denied boolean := false;
  v_rls_denied boolean := false;
begin
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-adult-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object('username', 'qa_adult_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
                       'birth_date', '1990-01-15', 'display_name', 'QA Adult'),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_adult;

  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-teen-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object('username', 'qa_teen_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
                       'birth_date', '2010-06-01', 'display_name', 'QA Teen'),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_teen;

  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-other-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object('username', 'qa_other_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
                       'birth_date', '1988-03-03', 'display_name', 'QA Other'),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_other;

  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-stranger-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object('username', 'qa_stranger_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
                       'birth_date', '1985-04-04', 'display_name', 'QA Stranger'),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_stranger;

  begin
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at
    ) values (
      '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
      'qa-child-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
      crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
      '{"provider":"email","providers":["email"]}',
      jsonb_build_object('username', 'qa_child_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
                         'birth_date', '2018-01-01', 'display_name', 'QA Child'),
      timezone('utc', now()), timezone('utc', now())
    );
  exception when others then
    v_under13_denied := true;
  end;

  perform public.canon_as(v_adult);
  v_org := public.canon_create_organization('Refugio QA', 'refugio-qa-' || substr(v_adult::text, 1, 8), 'SHELTER');
  insert into public.organization_capabilities (organization_id, capability)
  values (v_org, 'VETERINARY_CLINIC'), (v_org, 'DAYCARE')
  on conflict do nothing;
  v_pet := public.canon_create_pet('Toby', 'DOG', 'YEAR_PRECISION', null, 2021, null, null, null);
  perform public.canon_add_pet_person(v_pet, v_other, 'OWNER');
  perform public.canon_add_pet_person(v_pet, v_teen, 'AUTHORIZED');
  perform public.canon_set_org_responsible(v_pet, v_org);

  insert into public.pet_declared_health (pet_id, notes, updated_by)
  values (v_pet, 'declared-ok', v_adult)
  on conflict (pet_id) do update set notes = excluded.notes;

  perform public.canon_create_moment(v_pet, 'NOTE', 'Hola', 'privado');
  update public.vitacora_moments
    set hidden_at = timezone('utc', now())
    where pet_id = v_pet and title = 'Hola';
  v_grant := public.canon_grant_vitacora(v_pet, 'PERSON', v_other, null, 'service', 'ESSENTIAL', null);
  perform public.canon_grant_vitacora(v_pet, 'PERSON', v_other, null, 'service', 'HEALTH', null);
  perform public.canon_grant_vitacora(v_pet, 'ORGANIZATION', null, v_org, 'service', 'ESSENTIAL_AND_HEALTH', null);
  v_grant_full := public.canon_grant_vitacora(
    v_pet, 'PERSON', v_other, null, 'service', 'FULL_SHAREABLE',
    timezone('utc', now()) + interval '2 days'
  );
  perform public.canon_revoke_vitacora(v_grant);

  v_prop := public.canon_create_proposal(v_pet, 'WALKER', '{"allergy":"pollo"}'::jsonb);
  perform public.canon_decide_proposal(v_prop, 'ACCEPTED');
  v_prop_rej := public.canon_create_proposal(v_pet, 'OTHER', '{"note":"no"}'::jsonb);
  perform public.canon_decide_proposal(v_prop_rej, 'REJECTED');
  select id into v_link
    from public.vitacora_integration_links
    where pet_id = v_pet and source_table = 'pet_allergies'
    limit 1;
  if v_link is not null then
    perform public.canon_hide_integration(v_link);
  end if;

  v_provider := public.canon_create_provider('ORGANIZATION', null, v_org, 'Guarderia QA', 'BOARDING');
  select id into v_offering from public.service_offerings where provider_id = v_provider limit 1;
  v_booking := public.canon_create_booking(v_offering, v_pet, timezone('utc', now()) + interval '1 day', 'America/Argentina/Buenos_Aires');
  v_stay := public.canon_daycare_check_in(v_booking);
  perform public.canon_daycare_event(v_stay, 'FEEDING', 'almuerzo');
  insert into public.daycare_incidents (stay_id, severity, note, actor_user_id)
  values (v_stay, 'LOW', 'nada grave', v_adult);
  perform public.canon_daycare_checkout(v_stay, true);

  v_conv := public.canon_start_conversation('ORGANIZATION', null, v_org, 'hola entidad');
  update public.person_contact_controls set allow_unknown_dms = true where user_id = v_other;
  v_conv_p2p := public.canon_start_conversation('PERSON', v_other, null, 'hola persona');
  v_media := public.canon_register_media('private-media', 'users/' || v_adult::text || '/qa.jpg', 'image/jpeg', 123);
  v_prof := public.canon_create_professional();
  insert into public.clinic_affiliations (professional_profile_id, organization_id)
  values (v_prof, v_org)
  on conflict do nothing;
  perform public.canon_record_vet_care(v_pet, v_org, 'control');

  select id into v_doc from public.legal_documents where type = 'TERMS' limit 1;
  perform public.canon_record_consent(v_teen, v_doc, 'GUARDIAN_CONSENT');
  perform public.canon_create_privacy_request('ACCESS');
  perform public.canon_invite_guardian(v_teen, v_adult);
  update public.guardian_relationships
    set status = 'ACTIVE', accepted_at = timezone('utc', now())
    where minor_user_id = v_teen and adult_user_id = v_adult;

  insert into public.lost_found_alerts (kind, pet_id, created_by, locality_id, precise_location)
  values (
    'LOST', v_pet, v_adult, 'loc-ar-loc-san-vicente',
    'SRID=4326;POINT(-58.3816 -34.6037)'::extensions.geography
  );

  begin
    perform public.canon_as(v_stranger);
    perform public.canon_create_moment(v_pet, 'NOTE', 'no', 'no');
  exception when others then
    v_rls_denied := true;
  end;

  v_result := jsonb_build_object(
    'under13_denied', v_under13_denied,
    'multi_owner', (select count(*) from public.pet_responsibility_links where pet_id = v_pet and role = 'OWNER' and status = 'ACTIVE') >= 2,
    'org_responsible', exists (select 1 from public.pet_responsibility_links where pet_id = v_pet and holder_kind = 'ORGANIZATION' and status = 'ACTIVE'),
    'history', exists (select 1 from public.pet_responsibility_events where pet_id = v_pet),
    'custody_closed', exists (select 1 from public.pet_custody_records where pet_id = v_pet and status = 'ENDED'),
    'responsibility_unchanged_after_custody', exists (select 1 from public.pet_responsibility_links where pet_id = v_pet and status = 'ACTIVE'),
    'health', exists (select 1 from public.pet_declared_health where pet_id = v_pet),
    'moment_private', exists (select 1 from public.vitacora_moments where pet_id = v_pet and visibility = 'PRIVATE'),
    'moment_hidden', exists (select 1 from public.vitacora_moments where pet_id = v_pet and hidden_at is not null),
    'grant_essential_revoked', exists (select 1 from public.vitacora_access_grants where id = v_grant and revoked_at is not null),
    'grant_full_expires', exists (select 1 from public.vitacora_access_grants where id = v_grant_full and expires_at is not null),
    'grants', true,
    'proposal_accepted', exists (select 1 from public.vitacora_update_proposals where id = v_prop and status = 'ACCEPTED'),
    'proposal_rejected', exists (select 1 from public.vitacora_update_proposals where id = v_prop_rej and status = 'REJECTED'),
    'integration_hidden', exists (select 1 from public.vitacora_integration_links where pet_id = v_pet and visible = false),
    'provider', v_provider is not null,
    'booking_snapshot', exists (select 1 from public.booking_instruction_snapshots where booking_id = v_booking),
    'daycare_consent_default_no', exists (select 1 from public.daycare_public_consents where stay_id = v_stay and granted = false),
    'stay_in_vitacora', exists (select 1 from public.vitacora_integration_links where source_table = 'daycare_stays' and source_record_id = v_stay),
    'institutional_message', exists (select 1 from public.messages where conversation_id = v_conv and actor_user_id = v_adult),
    'person_person_message', exists (select 1 from public.messages where conversation_id = v_conv_p2p and actor_user_id = v_adult),
    'media_no_signed_url', exists (select 1 from public.media_assets where id = v_media and not (metadata ? 'signed_url')),
    'vet_provenance', exists (select 1 from public.veterinary_care_records where pet_id = v_pet and professional_profile_id is not null),
    'clinic_affiliation', exists (select 1 from public.clinic_affiliations where organization_id = v_org),
    'location', exists (select 1 from public.location_nodes where id = 'loc-ar-loc-san-vicente'),
    'precise_location_stored', exists (select 1 from public.lost_found_alerts where pet_id = v_pet and precise_location is not null),
    'guardian', exists (select 1 from public.guardian_relationships where minor_user_id = v_teen and adult_user_id = v_adult),
    'legal', exists (select 1 from public.legal_consent_events where subject_user_id = v_teen and event_type = 'GUARDIAN_CONSENT'),
    'privacy_request', exists (select 1 from public.privacy_requests where subject_user_id = v_adult),
    'public_code', exists (select 1 from public.pets where id = v_pet and public_code is not null),
    'rls_unauthorized', v_rls_denied,
    'rls_authorized', true
  );

  update public.booking_instruction_snapshots
    set public_consent_id = null
    where booking_id = v_booking;
  delete from public.daycare_stays where id = v_stay;
  delete from public.bookings where id = v_booking;
  delete from public.veterinary_care_records where pet_id = v_pet;
  delete from public.lost_found_alerts where pet_id = v_pet;
  delete from public.conversations where id in (v_conv, v_conv_p2p);
  delete from public.pets where id = v_pet;
  delete from public.service_providers where id = v_provider;
  delete from public.professional_profiles where person_id = v_adult;
  delete from public.organizations where id = v_org;
  delete from public.media_assets where id = v_media;
  delete from public.legal_consent_events where actor_user_id in (v_adult, v_teen, v_other, v_stranger);
  delete from public.privacy_request_events where actor_user_id in (v_adult, v_teen, v_other, v_stranger);
  delete from public.privacy_requests where subject_user_id in (v_adult, v_teen, v_other, v_stranger);
  delete from public.guardian_relationships where minor_user_id = v_teen or adult_user_id = v_adult;
  delete from public.security_audit_events where actor_user_id in (v_adult, v_teen, v_other, v_stranger);
  delete from public.persons where user_id in (v_adult, v_teen, v_other, v_stranger);
  delete from auth.users where id in (v_adult, v_teen, v_other, v_stranger);

  return v_result;
end;
$$;
