-- 1062: SEC-02 — MFA TOTP nativo + AAL2 obligatorio para staff técnico.
-- STAGING only. Does not edit 1057–1061.
--
-- Authority is the current Supabase session JWT claim auth.jwt()->>'aal'.
-- Do not persist TOTP secrets or a homemade verified flag.
--
-- ROOT RECOVERY (operational Auth admin only; no stored seed):
-- If the root SUPERADMIN loses the authenticator device:
--   1. Treat as a security incident. Record actor, time, and reason outside the app.
--   2. In Supabase Dashboard → Authentication → Users → root user,
--      remove MFA factors via the official Auth admin UI (or Auth Admin API
--      with a controlled service-role operation, never from Android).
--   3. Next login is password → AAL1 → enrollment → TOTP → AAL2 → Hub.
-- staff_reset_mfa MUST NOT target the root identity.
-- Another SUPERADMIN cannot reset root.
--
-- STAFF lost MFA: SUPERADMIN AAL2 calls staff_reset_mfa(target).
-- Factors are deleted server-side. Next login requires enrollment.
-- Event: ADMIN_MFA_RESET (actor + target + timestamp; never the secret).
--
-- Throttle IP: request.headers can be client-influenced on PostgREST.
-- Username-only hash is the throttle key. Do not trust Android-supplied IP.

-- ---------------------------------------------------------------------------
-- AAL helpers
-- ---------------------------------------------------------------------------
create or replace function public._canon_admin_aal()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select lower(coalesce(auth.jwt()->>'aal', ''));
$$;

create or replace function public._canon_is_admin_aal2()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.uid() is not null
     and public._canon_admin_aal() = 'aal2';
$$;

create or replace function public._canon_require_admin_aal2()
returns void
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if public._canon_admin_aal() is distinct from 'aal2' then
    raise exception 'MFA_REQUIRED';
  end if;
end;
$$;

revoke all on function public._canon_admin_aal() from public, anon, authenticated;
revoke all on function public._canon_is_admin_aal2() from public, anon, authenticated;
revoke all on function public._canon_require_admin_aal2() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Identification (AAL1 OK) vs operational session (AAL2)
-- ---------------------------------------------------------------------------
create or replace function public.get_admin_auth_state()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  i public.platform_admin_identities%rowtype;
  v_aal text := lower(coalesce(auth.jwt()->>'aal', 'aal1'));
begin
  if uid is null then
    return null;
  end if;
  select * into i
    from public.platform_admin_identities
   where user_id = uid
     and disabled_at is null;
  if not found then
    return null;
  end if;
  return jsonb_build_object(
    'is_admin_identity', true,
    'must_change_password', i.must_change_password,
    'is_root', i.is_root,
    'mfa_required', v_aal is distinct from 'aal2',
    'aal', v_aal
  );
end;
$$;

revoke all on function public.get_admin_auth_state() from public, anon;
grant execute on function public.get_admin_auth_state() to authenticated;

create or replace function public.get_admin_session()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  i public.platform_admin_identities%rowtype;
begin
  if uid is null then
    return null;
  end if;
  select * into i
    from public.platform_admin_identities
   where user_id = uid
     and disabled_at is null;
  if not found then
    return null;
  end if;
  if public._canon_admin_aal() is distinct from 'aal2' then
    raise exception 'MFA_REQUIRED';
  end if;
  return jsonb_build_object(
    'is_admin_identity', true,
    'must_change_password', i.must_change_password,
    'is_root', i.is_root,
    'aal', 'aal2'
  );
end;
$$;

revoke all on function public.get_admin_session() from public, anon;
grant execute on function public.get_admin_session() to authenticated;

-- ---------------------------------------------------------------------------
-- _acl_is_admin: close UUID oracle for authenticated clients.
-- RLS already calls _acl_is_admin(auth.uid()). A PERSON asking about another
-- UUID must not learn staff status. Triggers without JWT still work.
-- ---------------------------------------------------------------------------
create or replace function public._acl_is_admin(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    (auth.uid() is null or p_user_id is not distinct from auth.uid())
    and (
      public._acl_platform_role(p_user_id, 'ADMIN')
      or public._acl_platform_role(p_user_id, 'SUPERADMIN')
    );
$$;

revoke all on function public._acl_is_admin(uuid) from public, anon;
grant execute on function public._acl_is_admin(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Login throttle: username-only. Do not mix spoofable request headers.
-- ---------------------------------------------------------------------------
create or replace function public._admin_login_throttle_touch(p_username text)
returns boolean
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_uname text := lower(btrim(coalesce(p_username, '')));
  v_key text;
  v_now timestamptz := timezone('utc', now());
  v_count integer := 0;
  v_window timestamptz;
  v_until timestamptz;
begin
  v_key := encode(digest(convert_to(v_uname, 'UTF8'), 'sha256'), 'hex');

  delete from public.admin_login_throttle
   where updated_at < v_now - interval '24 hours';

  insert into public.admin_login_throttle (key_hash, fail_count, window_started_at, updated_at)
  values (v_key, 0, v_now, v_now)
  on conflict (key_hash) do nothing;

  select fail_count, window_started_at, blocked_until
    into v_count, v_window, v_until
    from public.admin_login_throttle
   where key_hash = v_key;

  if v_window < v_now - interval '15 minutes' then
    v_count := 0;
    v_until := null;
    update public.admin_login_throttle
       set fail_count = 0, window_started_at = v_now, blocked_until = null, updated_at = v_now
     where key_hash = v_key;
  end if;

  if v_until is not null and v_until > v_now then
    return true;
  end if;

  v_count := v_count + 1;
  if v_count >= 12 then
    v_until := v_now + interval '15 minutes';
  elsif v_count >= 8 then
    v_until := v_now + interval '2 minutes';
  elsif v_count >= 5 then
    v_until := v_now + interval '30 seconds';
  else
    v_until := null;
  end if;

  update public.admin_login_throttle
     set fail_count = v_count,
         blocked_until = v_until,
         updated_at = v_now
   where key_hash = v_key;

  if v_until is not null then
    insert into public.security_audit_events (actor_user_id, action, entity_table, entity_id, metadata)
    values (
      null, 'ADMIN_LOGIN_THROTTLED', 'admin_login_throttle', null,
      jsonb_build_object('fail_count', v_count)
    );
  end if;
  return false;
end;
$$;

revoke all on function public._admin_login_throttle_touch(text) from public, anon, authenticated;

create or replace function public.admin_begin_login(p_username text, p_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_user uuid;
  v_email text;
  v_hash text;
  v_dummy text := crypt('leover-admin-timing', gen_salt('bf', 4));
  v_ok boolean := false;
  v_uname text := lower(btrim(coalesce(p_username, '')));
  v_key text;
begin
  if char_length(coalesce(p_password, '')) < 1 then
    raise exception 'INVALID_CREDENTIALS';
  end if;
  if public._admin_login_throttle_touch(v_uname) then
    perform crypt(coalesce(p_password, 'x'), v_dummy);
    raise exception 'INVALID_CREDENTIALS';
  end if;
  select i.user_id, u.email, u.encrypted_password
    into v_user, v_email, v_hash
    from public.platform_admin_identities i
    join auth.users u on u.id = i.user_id
   where i.username_normalized = v_uname
     and i.disabled_at is null
   limit 1;
  v_ok := (
    v_user is not null
    and v_hash is not null
    and crypt(p_password, v_hash) = v_hash
  );
  if not v_ok then
    perform crypt(coalesce(p_password, 'x'), coalesce(v_hash, v_dummy));
    raise exception 'INVALID_CREDENTIALS';
  end if;
  if not public._acl_platform_role(v_user, 'SUPERADMIN')
     and not public._acl_platform_role(v_user, 'ADMIN')
     and not public._acl_platform_role(v_user, 'MODERATOR')
     and not public._acl_platform_role(v_user, 'SUPPORT') then
    raise exception 'INVALID_CREDENTIALS';
  end if;

  v_key := encode(digest(convert_to(v_uname, 'UTF8'), 'sha256'), 'hex');
  delete from public.admin_login_throttle where key_hash = v_key;

  return jsonb_build_object('email', v_email);
end;
$$;

revoke all on function public.admin_begin_login(text, text) from public;
grant execute on function public.admin_begin_login(text, text) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- MFA reset: SUPERADMIN AAL2 only. Root is never a target.
-- Deletes Auth factors; does not read or store secrets.
-- ---------------------------------------------------------------------------
create or replace function public.staff_reset_mfa(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  actor uuid := auth.uid();
begin
  perform public._canon_require_admin_aal2();
  if actor is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_user_id is null then
    raise exception 'TARGET_REQUIRED';
  end if;
  if not public._acl_platform_role(actor, 'SUPERADMIN') then
    raise exception 'FORBIDDEN';
  end if;
  if public._acl_is_protected_admin_root(p_user_id) then
    raise exception 'ROOT_PROTECTED';
  end if;
  if not exists (
    select 1
      from public.platform_admin_identities i
     where i.user_id = p_user_id
  ) then
    raise exception 'STAFF_NOT_FOUND';
  end if;

  delete from auth.mfa_factors where user_id = p_user_id;

  perform public._canon_admin_audit(
    'ADMIN_MFA_RESET',
    'platform_admin_identities',
    p_user_id,
    jsonb_build_object('actor', actor)
  );
  return jsonb_build_object('ok', true);
end;
$$;

revoke all on function public.staff_reset_mfa(uuid) from public, anon;
grant execute on function public.staff_reset_mfa(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Wrap privileged admin RPCs: authenticated + AAL2 + existing permission checks.
-- AAL2 alone never grants admin.
-- ---------------------------------------------------------------------------
do $$
declare
  r record;
  impl_name text;
  call_args text;
  create_sql text;
  ret_clause text;
  ident text;
  args text;
begin
  for r in
    select p.oid,
           p.proname,
           p.proretset,
           p.pronargs,
           p.proargnames,
           p.proargmodes
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.proname not like '%\_sec02\_impl' escape '\'
       and (
         p.proname like 'canon_admin_%'
         or p.proname in (
           'staff_register_identity',
           'staff_set_disabled',
           'staff_set_role',
           'staff_force_password_change',
           'staff_on_password_reset',
           'assign_platform_role',
           'revoke_platform_role',
           'change_user_account_status',
           'list_admin_staff',
           'get_admin_staff',
           'list_admin_staff_audit'
         )
       )
  loop
    ident := pg_get_function_identity_arguments(r.oid);
    args := pg_get_function_arguments(r.oid);
    ret_clause := pg_get_function_result(r.oid);
    impl_name := r.proname || '_sec02_impl';

    if exists (
      select 1
        from pg_proc p2
        join pg_namespace n2 on n2.oid = p2.pronamespace
       where n2.nspname = 'public'
         and p2.proname = impl_name
         and pg_get_function_identity_arguments(p2.oid) = ident
    ) then
      continue;
    end if;

    if r.pronargs = 0 then
      call_args := '';
    elsif r.proargnames is not null then
      select string_agg(quote_ident(r.proargnames[i]), ', ' order by i)
        into call_args
        from generate_series(1, r.pronargs) as i
       where r.proargmodes is null
          or r.proargmodes[i] in ('i', 'b', 'v');
      call_args := coalesce(call_args, '');
    else
      select string_agg('$' || i::text, ', ')
        into call_args
        from generate_series(1, r.pronargs) i;
    end if;

    execute format('alter function public.%I(%s) rename to %I', r.proname, ident, impl_name);

    if ret_clause = 'void' then
      create_sql := format(
        $f$
        create function public.%I(%s)
        returns void
        language plpgsql
        security definer
        set search_path = public
        as $body$
        begin
          perform public._canon_require_admin_aal2();
          perform public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, impl_name, call_args
      );
    elsif ret_clause ilike 'setof%' or ret_clause ilike 'table%' then
      create_sql := format(
        $f$
        create function public.%I(%s)
        returns %s
        language plpgsql
        security definer
        set search_path = public
        as $body$
        begin
          perform public._canon_require_admin_aal2();
          return query select * from public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, ret_clause, impl_name, call_args
      );
    else
      create_sql := format(
        $f$
        create function public.%I(%s)
        returns %s
        language plpgsql
        security definer
        set search_path = public
        as $body$
        begin
          perform public._canon_require_admin_aal2();
          return public.%I(%s);
        end;
        $body$;
        $f$, r.proname, args, ret_clause, impl_name, call_args
      );
    end if;

    execute create_sql;
    execute format(
      'revoke all on function public.%I(%s) from public, anon, authenticated',
      impl_name, ident
    );
    execute format(
      'revoke all on function public.%I(%s) from public, anon',
      r.proname, ident
    );
    execute format(
      'grant execute on function public.%I(%s) to authenticated',
      r.proname, ident
    );
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- country_markets: SEC-01 P1. Enable RLS; keep public/authenticated SELECT.
-- Writes remain owner/DEFINER only (no INSERT/UPDATE/DELETE grants).
-- ---------------------------------------------------------------------------
alter table public.country_markets enable row level security;

drop policy if exists country_markets_select on public.country_markets;
create policy country_markets_select
  on public.country_markets
  for select
  to anon, authenticated
  using (true);

grant select on public.country_markets to anon, authenticated;

notify pgrst, 'reload schema';
