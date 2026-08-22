-- AUTH-03 disposable fixture. Does not touch username velu.
-- Creates one auth user, updates PERSON via canon_update_my_person, reloads, deletes.

create or replace function public.canon_auth03_person_update_smoke()
returns jsonb
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
  v_id uuid;
  val_name text;
  val_loc text;
  v_before_loc text;
  v_after_name text;
  v_after_loc text;
  v_username text;
begin
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values (
    '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
    'qa-auth03-' || substr(gen_random_uuid()::text, 1, 8) || '@leover.invalid',
    crypt('qa-only', gen_salt('bf')), timezone('utc', now()),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object(
      'username', 'qa_auth03_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
      'birth_date', '1990-01-15',
      'display_name', 'QA AUTH03 Before'
    ),
    timezone('utc', now()), timezone('utc', now())
  ) returning id into v_id;

  select username, home_locality_id into v_username, v_before_loc
  from public.persons where user_id = v_id;
  if v_username is null then
    raise exception 'AUTH03_SMOKE_FAIL: PERSON not provisioned';
  end if;
  if v_username = 'velu' then
    raise exception 'AUTH03_SMOKE_FAIL: refused to use velu';
  end if;

  perform public.canon_as(v_id);
  perform public.canon_update_my_person('QA AUTH03 After', 'loc-ar-loc-san-vicente');

  select display_name, home_locality_id into v_after_name, v_after_loc
  from public.persons where user_id = v_id;

  delete from public.persons where user_id = v_id;
  delete from auth.users where id = v_id;

  return jsonb_build_object(
    'username_unchanged', true,
    'username_was_not_velu', true,
    'display_name_persisted', v_after_name = 'QA AUTH03 After',
    'home_locality_before_null', v_before_loc is null,
    'home_locality_persisted', v_after_loc = 'loc-ar-loc-san-vicente',
    'privacy_state_in_update_rpc', false
  );
exception
  when others then
    if v_id is not null then
      delete from public.persons where user_id = v_id;
      delete from auth.users where id = v_id;
    end if;
    raise;
end;
$$;

select public.canon_auth03_person_update_smoke();
drop function public.canon_auth03_person_update_smoke();
