-- 1061: SEC-01 P0 hardening.
-- Demonstrated on STAGING via PostgREST + anon JWT:
--   canon_audit → HTTP 204 write as anon
--   _canon_ensure_org_role → function reachable as anon (409 FK)
--   _acl_is_admin(uuid) → HTTP 200 boolean oracle
--   admin_begin_login → unlimited attempts, same INVALID_CREDENTIALS
-- Does not edit 1057–1060. Does not change unified login architecture.

-- ---------------------------------------------------------------------------
-- Audit: callers must be authenticated. Client grants removed.
-- DEFINER RPCs still execute this as owner.
-- ---------------------------------------------------------------------------
create or replace function public.canon_audit(
  p_action text, p_table text, p_id uuid, p_meta jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  insert into public.security_audit_events (actor_user_id, action, entity_table, entity_id, metadata)
  values (auth.uid(), p_action, p_table, p_id, coalesce(p_meta, '{}'::jsonb));
end;
$$;

revoke all on function public.canon_audit(text, text, uuid, jsonb) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Internal helpers: not a public API. RLS still uses _acl_* as authenticated.
-- ---------------------------------------------------------------------------
revoke all on function public._canon_ensure_org_role(uuid, text, text) from public, anon, authenticated;
revoke all on function public._canon_active_admin_count(uuid) from public, anon, authenticated;
revoke all on function public._acl_is_admin(uuid) from public, anon;
revoke all on function public._acl_is_staff(uuid) from public, anon;
revoke all on function public._acl_platform_role(uuid, text) from public, anon;

do $$
declare
  r record;
begin
  for r in
    select p.oid::regprocedure as sig
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and (
        p.proname like 'canon_admin_%'
        or p.proname = 'vitacora_assign_public_number'
      )
  loop
    execute format('revoke all on function %s from public, anon', r.sig);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- admin_begin_login: progressive throttle. Same public error. No password/IP stored.
-- ---------------------------------------------------------------------------
create table if not exists public.admin_login_throttle (
  key_hash text primary key,
  fail_count integer not null default 0,
  window_started_at timestamptz not null default timezone('utc', now()),
  blocked_until timestamptz null,
  updated_at timestamptz not null default timezone('utc', now())
);

alter table public.admin_login_throttle enable row level security;
revoke all on public.admin_login_throttle from public, anon, authenticated;

create or replace function public._admin_login_throttle_touch(p_username text)
returns boolean
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_headers jsonb := '{}'::jsonb;
  v_ip text := '';
  v_uname text := lower(btrim(coalesce(p_username, '')));
  v_key text;
  v_now timestamptz := timezone('utc', now());
  v_count integer := 0;
  v_window timestamptz;
  v_until timestamptz;
  v_blocked boolean := false;
begin
  begin
    v_headers := coalesce(current_setting('request.headers', true)::jsonb, '{}'::jsonb);
  exception when others then
    v_headers := '{}'::jsonb;
  end;
  v_ip := split_part(coalesce(v_headers->>'cf-connecting-ip', v_headers->>'x-real-ip', v_headers->>'x-forwarded-for', ''), ',', 1);
  v_ip := btrim(v_ip);
  v_key := encode(digest(convert_to(v_uname || chr(31) || v_ip, 'UTF8'), 'sha256'), 'hex');

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
  v_headers jsonb := '{}'::jsonb;
  v_ip text := '';
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

  begin
    v_headers := coalesce(current_setting('request.headers', true)::jsonb, '{}'::jsonb);
  exception when others then
    v_headers := '{}'::jsonb;
  end;
  v_ip := split_part(coalesce(v_headers->>'cf-connecting-ip', v_headers->>'x-real-ip', v_headers->>'x-forwarded-for', ''), ',', 1);
  v_key := encode(digest(convert_to(v_uname || chr(31) || btrim(v_ip), 'UTF8'), 'sha256'), 'hex');
  delete from public.admin_login_throttle where key_hash = v_key;

  return jsonb_build_object('email', v_email);
end;
$$;

revoke all on function public.admin_begin_login(text, text) from public;
grant execute on function public.admin_begin_login(text, text) to anon, authenticated;

notify pgrst, 'reload schema';
