-- LeoVer Canonical
-- Logical migration: 1032
-- AUTH-06: Google OAuth must not fail handle_new_user (username/birth required).
-- PERSON is 1:1 with auth.users (user_id PK). Provision uses auth.uid() only.
-- Forward-only. Do not edit 1000-1031.
-- Staging only.

-- ---------------------------------------------------------------------------
-- handle_new_user: do not abort Google/OAuth inserts (no username/birth yet).
-- Do not activate PERSON before a verified identity (email_confirmed_at).
-- Email OTP confirmation re-runs this function on UPDATE.
-- Unverified auth.users stay without PERSON until confirmation or
-- canon_provision_my_person (auth.uid()).
-- ---------------------------------------------------------------------------

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_username text;
  v_birth date;
  v_name text;
begin
  v_username := lower(coalesce(new.raw_user_meta_data->>'username', ''));
  v_birth := nullif(new.raw_user_meta_data->>'birth_date', '')::date;
  v_name := coalesce(
    nullif(new.raw_user_meta_data->>'display_name', ''),
    nullif(new.raw_user_meta_data->>'name', ''),
    nullif(new.raw_user_meta_data->>'full_name', ''),
    v_username
  );
  -- Google / incomplete metadata: never raise; PERSON comes from onboarding RPC.
  if v_username = '' or v_birth is null then
    return new;
  end if;
  -- Email signup OTP pending: keep auth.users, do not activate PERSON yet.
  if new.email_confirmed_at is null then
    return new;
  end if;
  begin
    insert into public.persons (
      user_id, username, display_name, birth_date, email_verified_at, privacy_state
    ) values (
      new.id,
      v_username,
      v_name,
      v_birth,
      new.email_confirmed_at,
      case
        when public.person_age_band(v_birth) in ('TEEN_13_15', 'TEEN_16_17') then 'PRIVATE'
        else 'PUBLIC_LIMITED'
      end
    )
    on conflict (user_id) do nothing;
    insert into public.person_privacy_settings (user_id, profile_discoverable)
    values (
      new.id,
      public.person_age_band(v_birth) = 'ADULT_18_PLUS'
    )
    on conflict (user_id) do nothing;
    insert into public.person_contact_controls (user_id, allow_unknown_dms)
    values (
      new.id,
      public.person_age_band(v_birth) = 'ADULT_18_PLUS'
    )
    on conflict (user_id) do nothing;
    insert into public.notification_preferences (user_id) values (new.id)
    on conflict (user_id) do nothing;
  exception
    when unique_violation then
      -- Username taken after OTP window: do not roll back auth confirmation.
      return new;
  end;
  return new;
end;
$$;

drop trigger if exists on_auth_user_email_confirmed on auth.users;
create trigger on_auth_user_email_confirmed
  after update of email_confirmed_at on auth.users
  for each row
  when (old.email_confirmed_at is null and new.email_confirmed_at is not null)
  execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- First-time Google / incomplete PERSON: create or complete the caller's row.
-- Never accepts a client-supplied user id.
-- ---------------------------------------------------------------------------

create or replace function public.canon_provision_my_person(
  p_username text,
  p_display_name text,
  p_birth_date date,
  p_home_locality_id text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_username text;
  v_name text;
  v_exists boolean;
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

  v_username := lower(btrim(coalesce(p_username, '')));
  v_name := btrim(coalesce(p_display_name, ''));
  if v_name = '' then raise exception 'DISPLAY_NAME_INVALID'; end if;
  if p_home_locality_id is null or btrim(p_home_locality_id) = '' then
    raise exception 'HOME_LOCALITY_REQUIRED';
  end if;

  select exists(select 1 from public.persons p where p.user_id = v_uid) into v_exists;

  if v_exists then
    update public.persons
       set display_name = v_name,
           home_locality_id = btrim(p_home_locality_id)
     where user_id = v_uid;
    return v_uid;
  end if;

  if v_username = '' or p_birth_date is null then
    raise exception 'SIGNUP_REQUIRES_USERNAME_AND_BIRTH_DATE';
  end if;
  if public.person_is_under_13(p_birth_date) then
    raise exception 'UNDER_13_AUTONOMOUS_ACCOUNT_DENIED';
  end if;
  if exists (
    select 1 from public.persons p where lower(p.username) = v_username
  ) then
    raise exception 'USERNAME_UNAVAILABLE';
  end if;

  insert into public.persons (
    user_id, username, display_name, birth_date, email_verified_at,
    home_locality_id, privacy_state
  ) values (
    v_uid,
    v_username,
    v_name,
    p_birth_date,
    timezone('utc', now()),
    btrim(p_home_locality_id),
    case
      when public.person_age_band(p_birth_date) in ('TEEN_13_15', 'TEEN_16_17') then 'PRIVATE'
      else 'PUBLIC_LIMITED'
    end
  );

  insert into public.person_privacy_settings (user_id, profile_discoverable)
  values (
    v_uid,
    public.person_age_band(p_birth_date) = 'ADULT_18_PLUS'
  )
  on conflict (user_id) do nothing;
  insert into public.person_contact_controls (user_id, allow_unknown_dms)
  values (
    v_uid,
    public.person_age_band(p_birth_date) = 'ADULT_18_PLUS'
  )
  on conflict (user_id) do nothing;
  insert into public.notification_preferences (user_id) values (v_uid)
  on conflict (user_id) do nothing;

  return v_uid;
end;
$$;

revoke all on function public.canon_provision_my_person(text, text, date, text) from public, anon;
grant execute on function public.canon_provision_my_person(text, text, date, text) to authenticated;
