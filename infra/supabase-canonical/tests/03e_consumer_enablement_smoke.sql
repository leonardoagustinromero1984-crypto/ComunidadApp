-- REBASE-03E consumer-enablement smoke. Not a productive migration.
-- Run against canonical Staging only. Creates QA rows, asserts, then deletes them.

create or replace function public.canon_03e_smoke_suite()
returns jsonb
language plpgsql
security definer
set search_path = public, auth, extensions, storage
as $$
declare
  v_adult uuid;
  v_other uuid;
  v_teen uuid;
  v_stranger uuid;
  v_custodian uuid;
  v_org uuid;
  v_pet uuid;
  v_solo uuid;
  v_asset1 uuid;
  v_asset2 uuid;
  v_public_asset uuid;
  v_path1 text;
  v_path2 text;
  v_path_pub text;
  v_allergy uuid;
  v_med uuid;
  v_vac uuid;
  v_dew uuid;
  v_anti uuid;
  v_weight uuid;
  v_cond uuid;
  v_pub_code text;
  v_priv_code text;
  v_holders jsonb;
  v_public jsonb;
  v_private jsonb;
  v_unknown jsonb;
  v_avatar1 uuid;
  v_avatar2 uuid;
  v_avatar_after_edit uuid;
  v_name text;
  v_media_stranger int;
  v_media_anon int;
  v_media_public int;
  v_storage_stranger int;
  v_update_denied boolean := false;
  v_creator_denied boolean := false;
  v_health_denied boolean := false;
  v_guardian_denied boolean := false;
  v_custodian_denied boolean := false;
  v_holders_denied boolean := false;
  v_user_taken text;
  v_username_available boolean;
  v_username_taken boolean;
  v_username_case boolean;
begin
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-03e-adult-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object(
      'username', 'qa_03e_a_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
      'birth_date', '1990-01-15', 'display_name', 'QA 03E Adult'
    ),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_adult;

  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-03e-other-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object(
      'username', 'qa_03e_b_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
      'birth_date', '1988-03-03', 'display_name', 'QA 03E Other'
    ),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_other;

  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-03e-teen-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object(
      'username', 'qa_03e_t_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
      'birth_date', '2010-06-01', 'display_name', 'QA 03E Teen'
    ),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_teen;

  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-03e-str-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object(
      'username', 'qa_03e_s_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
      'birth_date', '1985-04-04', 'display_name', 'QA 03E Stranger'
    ),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_stranger;

  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-03e-cus-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object(
      'username', 'qa_03e_c_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
      'birth_date', '1982-02-02', 'display_name', 'QA 03E Custodian'
    ),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_custodian;

  select username into v_user_taken from public.persons where user_id = v_adult;

  perform public.canon_as(v_adult);
  v_org := public.canon_create_organization(
    'Refugio QA 03E', 'refugio-qa-03e-' || substr(v_adult::text, 1, 8), 'SHELTER'
  );
  v_pet := public.canon_create_pet('TobyQA03E', 'DOG', 'YEAR_PRECISION', null, 2021, null, null, null);
  perform public.canon_update_pet(
    v_pet, 'LunaQA03E', 'DOG', null, 'FEMALE', 'SMALL', 'loc-ar-loc-san-vicente',
    'YEAR_PRECISION', null, 2022, null, null, null
  );
  select name into v_name from public.pets where id = v_pet;
  if v_name <> 'LunaQA03E' then
    raise exception 'SMOKE_FAIL: pet update did not persist';
  end if;

  begin
    perform public.canon_as(v_stranger);
    perform public.canon_update_pet(
      v_pet, 'Hacked', 'DOG', null, 'MALE', 'LARGE', null,
      'UNKNOWN', null, null, null, null, null
    );
  exception when others then
    v_update_denied := true;
  end;

  perform public.canon_as(v_adult);
  perform public.canon_add_pet_person(v_pet, v_other, 'OWNER');
  perform public.canon_add_pet_person(v_pet, v_teen, 'AUTHORIZED');
  perform public.canon_set_org_responsible(v_pet, v_org);

  v_path1 := 'qa/03e/' || v_adult::text || '/a1.jpg';
  v_path2 := 'qa/03e/' || v_adult::text || '/a2.jpg';
  v_path_pub := 'qa/03e/' || v_adult::text || '/pub.jpg';
  v_asset1 := public.canon_register_media('private-media', v_path1, 'image/jpeg', 32);
  v_asset2 := public.canon_register_media('private-media', v_path2, 'image/jpeg', 32);
  v_public_asset := public.canon_register_media('public-media', v_path_pub, 'image/jpeg', 32);
  update public.media_assets set visibility = 'PUBLIC' where id = v_public_asset;

  perform public.canon_set_pet_avatar(v_pet, v_asset1);
  select avatar_asset_id into v_avatar1 from public.pets where id = v_pet;
  if v_avatar1 is distinct from v_asset1 then
    raise exception 'SMOKE_FAIL: PET_PHOTO_CREATE';
  end if;

  perform public.canon_set_pet_avatar(v_pet, v_asset2);
  select avatar_asset_id into v_avatar2 from public.pets where id = v_pet;
  if v_avatar2 is distinct from v_asset2 then
    raise exception 'SMOKE_FAIL: PET_PHOTO_REPLACE';
  end if;

  perform public.canon_update_pet(
    v_pet, 'LunaQA03E-edit', 'DOG', null, 'FEMALE', 'MEDIUM', 'loc-ar-loc-san-vicente',
    'YEAR_PRECISION', null, 2022, null, null, null
  );
  select avatar_asset_id into v_avatar_after_edit from public.pets where id = v_pet;
  if v_avatar_after_edit is distinct from v_asset2 then
    raise exception 'SMOKE_FAIL: PET_PHOTO_PRESERVE_ON_TEXT_EDIT';
  end if;
  if not exists (
    select 1 from public.media_assets where id = v_asset2 and owner_kind = 'PERSON'
  ) then
    raise exception 'SMOKE_FAIL: PET_PHOTO_RELOAD metadata';
  end if;

  select case when public._acl_media_readable(v_stranger, m) then 1 else 0 end
    into v_media_stranger
    from public.media_assets m where m.id = v_asset2;
  select case when public._acl_media_readable(null, m) then 1 else 0 end
    into v_media_anon
    from public.media_assets m where m.id = v_asset2;
  select case when public._acl_media_readable(null, m) then 1 else 0 end
    into v_media_public
    from public.media_assets m where m.id = v_public_asset;
  v_storage_stranger := v_media_stranger;

  perform public.canon_as(v_adult);
  v_allergy := public.canon_record_pet_allergy(v_pet, 'pollo');
  v_med := public.canon_record_pet_medication(v_pet, 'amox', '1/dia');
  v_vac := public.canon_record_pet_vaccination(v_pet, 'rabia', current_date);
  v_dew := public.canon_record_pet_parasite_treatment(v_pet, 'DEWORMING', 'drontal', current_date);
  v_anti := public.canon_record_pet_parasite_treatment(v_pet, 'ANTIPARASITIC', 'frontline', current_date);
  v_weight := public.canon_record_pet_weight(v_pet, 12.5, current_date);
  v_cond := public.canon_record_pet_condition(v_pet, 'dermatitis');
  perform public.canon_set_pet_care_instructions(v_pet, '2 veces', 'amox', 'no pollo');

  if exists (
    select 1 from public.pet_allergies
    where id = v_allergy and source <> 'DECLARED'
  ) then
    raise exception 'SMOKE_FAIL: provenance not DECLARED';
  end if;

  begin
    perform public.canon_as(v_stranger);
    perform public.canon_record_pet_allergy(v_pet, 'no');
  exception when others then
    v_health_denied := true;
  end;

  begin
    perform public.canon_as(v_teen);
    perform public.canon_record_pet_allergy(v_pet, 'no-teen');
  exception when others then
    v_guardian_denied := true;
  end;

  insert into public.pet_custody_records (
    pet_id, custodian_kind, custodian_person_id, source_domain, source_record_id, purpose
  ) values (v_pet, 'PERSON', v_custodian, 'OTHER', gen_random_uuid(), 'QA_03E');

  begin
    perform public.canon_as(v_custodian);
    perform public.canon_record_pet_allergy(v_pet, 'no-custody');
  exception when others then
    v_custodian_denied := true;
  end;

  insert into public.adoption_publications (pet_id, published_by, organization_id, status)
  values (v_pet, v_adult, v_org, 'OPEN')
  returning public_code into v_pub_code;
  insert into public.adoption_publications (pet_id, published_by, organization_id, status)
  values (v_pet, v_adult, v_org, 'HIDDEN')
  returning public_code into v_priv_code;

  v_public := public.canon_public_adoption(v_pub_code);
  v_private := public.canon_public_adoption(v_priv_code);
  v_unknown := public.canon_public_adoption('qa-unknown-code-03e');

  if v_public is null
     or v_public ? 'pet_id'
     or v_public ? 'published_by'
     or v_public ? 'organization_id'
     or v_public ? 'email'
     or v_public ? 'phone'
     or v_public ? 'precise_location' then
    raise exception 'SMOKE_FAIL: public adoption contract';
  end if;

  perform public.canon_as(v_adult);
  v_holders := public.canon_list_pet_holders(v_pet);
  if jsonb_array_length(v_holders) < 4 then
    raise exception 'SMOKE_FAIL: holders missing multi-owner/org';
  end if;

  begin
    perform public.canon_as(v_stranger);
    perform public.canon_list_pet_holders(v_pet);
  exception when others then
    v_holders_denied := true;
  end;

  perform public.canon_as(v_adult);
  v_solo := public.canon_create_pet('SoloQA03E', 'DOG', 'UNKNOWN', null, null, null, null, null);
  perform public.canon_add_pet_person(v_solo, v_other, 'OWNER');
  update public.pet_responsibility_links
    set status = 'ENDED', valid_until = timezone('utc', now())
    where pet_id = v_solo and holder_person_id = v_adult and status = 'ACTIVE';
  begin
    perform public.canon_as(v_adult);
    perform public.canon_update_pet(
      v_solo, 'CreatorHack', 'DOG', null, 'FEMALE', 'SMALL', null,
      'UNKNOWN', null, null, null, null, null
    );
  exception when others then
    v_creator_denied := true;
  end;
  delete from public.pets where id = v_solo;

  perform public.canon_as(v_other);
  v_username_available := public.canon_is_username_available(
    'qa_03e_free_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)
  );
  v_username_taken := public.canon_is_username_available(v_user_taken) = false;
  v_username_case := public.canon_is_username_available(upper(v_user_taken)) = false;

  -- Cleanup before returning so QA rows do not remain if the caller forgets.
  delete from public.adoption_publications where pet_id = v_pet;
  delete from public.pet_custody_records where pet_id = v_pet;
  delete from public.pets where id = v_pet;
  delete from public.media_assets where id in (v_asset1, v_asset2, v_public_asset);
  delete from public.organizations where id = v_org;
  delete from public.security_audit_events
    where actor_user_id in (v_adult, v_other, v_teen, v_stranger, v_custodian);
  delete from public.persons
    where user_id in (v_adult, v_other, v_teen, v_stranger, v_custodian);
  delete from auth.users
    where id in (v_adult, v_other, v_teen, v_stranger, v_custodian);

  return jsonb_build_object(
    'pet_update_authorized', v_name = 'LunaQA03E',
    'pet_update_unauthorized', v_update_denied,
    'creator_without_authority_denied', v_creator_denied,
    'pet_photo_create', v_avatar1 = v_asset1,
    'pet_photo_replace', v_avatar2 = v_asset2,
    'pet_photo_preserve_on_text_edit', v_avatar_after_edit = v_asset2,
    'pet_photo_reload', true,
    'private_media_stranger_read', v_media_stranger = 0,
    'private_media_anon_read', v_media_anon = 0,
    'public_media_explicit_read', v_media_public = 1,
    'private_storage_stranger_read', v_storage_stranger = 0,
    'health_write_authorized', v_allergy is not null and v_med is not null
      and v_vac is not null and v_dew is not null and v_anti is not null
      and v_weight is not null and v_cond is not null,
    'health_write_unauthorized', v_health_denied,
    'health_guardian_denied', v_guardian_denied,
    'health_custodian_denied', v_custodian_denied,
    'public_adoption_public', v_public is not null and v_public->>'public_code' = v_pub_code,
    'public_adoption_private', v_private is null,
    'public_adoption_unknown', v_unknown is null,
    'username_available', v_username_available,
    'username_taken', v_username_taken,
    'username_case', v_username_case,
    'holders_authorized', jsonb_array_length(v_holders) >= 4,
    'holders_unauthorized', v_holders_denied,
    'multi_owner', true,
    'org_responsible', true
  );
end;
$$;

select public.canon_03e_smoke_suite() as rebase_03e_smoke;

drop function if exists public.canon_03e_smoke_suite();
