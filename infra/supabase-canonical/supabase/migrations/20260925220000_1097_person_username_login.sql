-- 1097: Unified "Correo o usuario" must resolve PERSON username before staff.
-- Root cause: identifier without @ went only to admin_begin_login
-- (platform_admin_identities). Real persons.username then got INVALID_CREDENTIALS.
-- This RPC is SECURITY DEFINER. Returns {email} only after password verify.
-- Same public error always. Does not expose auth.users, emails, or existence.
-- Precedence: if persons.username matches, never fall through to staff.
-- admin_begin_login is unchanged (AdminLoginViewModel / staff MFA).

create or replace function public.canon_begin_username_login(p_username text, p_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_uname text;
  v_person uuid;
  v_lifecycle text;
  v_email text;
  v_hash text;
  v_dummy text := crypt('leover-person-timing', gen_salt('bf', 4));
  v_ok boolean := false;
  v_key text;
begin
  v_uname := lower(btrim(coalesce(p_username, '')));
  while left(v_uname, 1) = '@' loop
    v_uname := btrim(substring(v_uname from 2));
  end loop;

  if char_length(coalesce(p_password, '')) < 1
     or char_length(v_uname) < 3
     or char_length(v_uname) > 30
     or position('@' in v_uname) > 0
     or v_uname !~ '^[a-z0-9._]+$'
  then
    perform crypt(coalesce(p_password, 'x'), v_dummy);
    raise exception 'INVALID_CREDENTIALS';
  end if;

  select p.user_id, p.lifecycle_status, u.email, u.encrypted_password
    into v_person, v_lifecycle, v_email, v_hash
    from public.persons p
    join auth.users u on u.id = p.user_id
   where lower(p.username) = v_uname
   limit 1;

  if v_person is not null then
    if public._admin_login_throttle_touch(v_uname) then
      perform crypt(coalesce(p_password, 'x'), v_dummy);
      raise exception 'INVALID_CREDENTIALS';
    end if;
    v_ok := (
      v_lifecycle = 'ACTIVE'
      and v_hash is not null
      and crypt(p_password, v_hash) = v_hash
    );
    if not v_ok then
      perform crypt(coalesce(p_password, 'x'), coalesce(v_hash, v_dummy));
      raise exception 'INVALID_CREDENTIALS';
    end if;
    v_key := encode(digest(convert_to(v_uname, 'UTF8'), 'sha256'), 'hex');
    delete from public.admin_login_throttle where key_hash = v_key;
    return jsonb_build_object('email', v_email);
  end if;

  return public.admin_begin_login(v_uname, p_password);
end;
$$;

revoke all on function public.canon_begin_username_login(text, text) from public;
grant execute on function public.canon_begin_username_login(text, text) to anon, authenticated;

comment on function public.canon_begin_username_login(text, text) is
  'PERSON username+password first; staff admin_begin_login only if no persons.username. Same INVALID_CREDENTIALS.';
