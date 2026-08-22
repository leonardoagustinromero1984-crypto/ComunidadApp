-- REBASE-03G smoke. Not a productive migration.
-- Authorized + unauthorized Health read, lost/found, adoption.

create or replace function public.canon_03g_smoke_suite()
returns jsonb
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
  v_adult uuid;
  v_stranger uuid;
  v_pet uuid;
  v_health jsonb;
  v_denied boolean := false;
  v_allergy uuid;
  v_lf uuid;
  v_ad uuid;
begin
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-03g-adult-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object(
      'username', 'qa_03g_a_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
      'birth_date', '1990-01-15', 'display_name', 'QA 03G Adult'
    ),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_adult;

  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-03g-str-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object(
      'username', 'qa_03g_s_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
      'birth_date', '1991-02-20', 'display_name', 'QA 03G Stranger'
    ),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_stranger;

  perform set_config('request.jwt.claim.sub', v_adult::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);

  v_pet := public.canon_create_pet('QA 03G Pet', 'DOG', 'UNKNOWN');
  v_allergy := public.canon_record_pet_allergy(v_pet, 'QA pollen');
  v_health := public.canon_get_pet_health(v_pet);
  if not (v_health -> 'allergies' @> jsonb_build_array(jsonb_build_object('name', 'QA pollen'))) then
    raise exception 'HEALTH_READ_MISSING_ALLERGY';
  end if;

  perform set_config('request.jwt.claim.sub', v_stranger::text, true);
  begin
    perform public.canon_get_pet_health(v_pet);
  exception when others then
    v_denied := true;
  end;
  if not v_denied then
    raise exception 'HEALTH_READ_NOT_DENIED';
  end if;

  perform set_config('request.jwt.claim.sub', v_adult::text, true);
  v_lf := public.canon_create_lost_found('LOST', v_pet, null, 'DOG', 'QA lost note');
  v_ad := public.canon_create_adoption(v_pet, null, 'QA adopt note');

  delete from public.adoption_publications where id = v_ad;
  delete from public.lost_found_alerts where id = v_lf;
  delete from public.pet_allergies where id = v_allergy;
  delete from public.pet_permission_grants where pet_id = v_pet;
  delete from public.pet_responsibility_events where pet_id = v_pet;
  delete from public.pet_responsibility_links where pet_id = v_pet;
  delete from public.vitacora_profiles where pet_id = v_pet;
  delete from public.pet_lifecycle_events where pet_id = v_pet;
  delete from public.pets where id = v_pet;
  delete from public.persons where user_id in (v_adult, v_stranger);
  delete from auth.users where id in (v_adult, v_stranger);

  return jsonb_build_object(
    'health_write', v_allergy is not null,
    'health_read', true,
    'health_denied', v_denied,
    'lost_found', v_lf is not null,
    'adoption', v_ad is not null
  );
end;
$$;
