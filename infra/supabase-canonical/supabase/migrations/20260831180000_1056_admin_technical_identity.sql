-- 1056: technical admin identity (not PERSON). Username login + root protection.
-- Does not seed credentials. Does not edit 1055.

create table if not exists public.platform_admin_identities (
  user_id uuid primary key references auth.users(id) on delete cascade,
  username_normalized text not null,
  must_change_password boolean not null default true,
  is_root boolean not null default false,
  disabled_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint platform_admin_identities_username_format
    check (username_normalized ~ '^[a-z0-9._-]{3,64}$')
);

create unique index if not exists platform_admin_identities_username_uidx
  on public.platform_admin_identities (username_normalized);

create unique index if not exists platform_admin_identities_single_root_uidx
  on public.platform_admin_identities (is_root)
  where is_root;

alter table public.platform_admin_identities enable row level security;
revoke all on public.platform_admin_identities from public, anon, authenticated;

do $$
declare
  cname text;
begin
  select con.conname into cname
  from pg_constraint con
  join pg_class rel on rel.oid = con.conrelid
  join pg_namespace nsp on nsp.oid = rel.relnamespace
  where nsp.nspname = 'public'
    and rel.relname = 'user_platform_role_assignments'
    and con.contype = 'f'
    and pg_get_constraintdef(con.oid) ilike '%user_id%persons%';
  if cname is not null then
    execute format(
      'alter table public.user_platform_role_assignments drop constraint %I',
      cname
    );
  end if;
end $$;

alter table public.user_platform_role_assignments
  drop constraint if exists user_platform_role_assignments_user_id_fkey;

alter table public.user_platform_role_assignments
  add constraint user_platform_role_assignments_user_id_fkey
  foreign key (user_id) references auth.users(id) on delete cascade;

do $$
declare
  cname text;
begin
  select con.conname into cname
  from pg_constraint con
  join pg_class rel on rel.oid = con.conrelid
  join pg_namespace nsp on nsp.oid = rel.relnamespace
  where nsp.nspname = 'public'
    and rel.relname = 'user_platform_role_assignments'
    and con.contype = 'f'
    and pg_get_constraintdef(con.oid) ilike '%granted_by%persons%';
  if cname is not null then
    execute format(
      'alter table public.user_platform_role_assignments drop constraint %I',
      cname
    );
  end if;
end $$;

alter table public.user_platform_role_assignments
  drop constraint if exists user_platform_role_assignments_granted_by_fkey;

alter table public.user_platform_role_assignments
  add constraint user_platform_role_assignments_granted_by_fkey
  foreign key (granted_by) references auth.users(id);

do $$
declare
  cname text;
begin
  select con.conname into cname
  from pg_constraint con
  join pg_class rel on rel.oid = con.conrelid
  join pg_namespace nsp on nsp.oid = rel.relnamespace
  where nsp.nspname = 'public'
    and rel.relname = 'person_status_history'
    and con.contype = 'f'
    and pg_get_constraintdef(con.oid) ilike '%changed_by%persons%';
  if cname is not null then
    execute format('alter table public.person_status_history drop constraint %I', cname);
  end if;
end $$;

alter table public.person_status_history
  drop constraint if exists person_status_history_changed_by_fkey;

alter table public.person_status_history
  add constraint person_status_history_changed_by_fkey
  foreign key (changed_by) references auth.users(id);

do $$
declare
  cname text;
begin
  select con.conname into cname
  from pg_constraint con
  join pg_class rel on rel.oid = con.conrelid
  join pg_namespace nsp on nsp.oid = rel.relnamespace
  where nsp.nspname = 'public'
    and rel.relname = 'security_audit_events'
    and con.contype = 'f'
    and pg_get_constraintdef(con.oid) ilike '%actor_user_id%persons%';
  if cname is not null then
    execute format('alter table public.security_audit_events drop constraint %I', cname);
  end if;
end $$;

alter table public.security_audit_events
  drop constraint if exists security_audit_events_actor_user_id_fkey;

alter table public.security_audit_events
  add constraint security_audit_events_actor_user_id_fkey
  foreign key (actor_user_id) references auth.users(id);

create or replace function public._acl_is_admin_identity(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.platform_admin_identities i
    where i.user_id = p_user_id
      and i.disabled_at is null
  );
$$;

create or replace function public._acl_is_protected_admin_root(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.platform_admin_identities i
    where i.user_id = p_user_id
      and i.is_root
      and i.disabled_at is null
  );
$$;

create or replace function public.has_permission(permission_code text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  v_life text;
  v_code text := lower(btrim(coalesce(permission_code, '')));
begin
  if uid is null then return false; end if;
  if public._acl_is_admin_identity(uid) then
    return public._acl_has_platform_permission(uid, v_code);
  end if;
  select lifecycle_status into v_life from public.persons where user_id = uid;
  if v_life is null or v_life in ('PENDING_ERASURE', 'ERASED_MINIMIZED') then
    return false;
  end if;
  if v_life = 'SUSPENDED' and v_code not in (
    'profile.read.own', 'profile.update.own', 'profile.read.public'
  ) then
    return false;
  end if;
  return public._acl_has_platform_permission(uid, v_code);
end;
$$;

create or replace function public.get_my_platform_roles()
returns text[]
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  result text[];
begin
  if uid is null then return array[]::text[]; end if;
  select coalesce(array_agg(distinct a.role_code order by a.role_code), array[]::text[])
  into result
  from public.user_platform_role_assignments a
  where a.user_id = uid and a.revoked_at is null;
  if result is null or cardinality(result) = 0 then
    if public._acl_is_admin_identity(uid) then
      return array[]::text[];
    end if;
    return array['USER']::text[];
  end if;
  return result;
end;
$$;

create or replace function public.get_my_permissions()
returns text[]
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  result text[];
begin
  if uid is null then return array[]::text[]; end if;
  if public._acl_is_admin_identity(uid) then
    select coalesce(array_agg(distinct rp.permission_code order by rp.permission_code), array[]::text[])
    into result
    from public.user_platform_role_assignments a
    join public.platform_role_permissions rp on rp.role_code = a.role_code
    where a.user_id = uid and a.revoked_at is null;
    return coalesce(result, array[]::text[]);
  end if;
  if not exists (
    select 1 from public.persons p
    where p.user_id = uid and p.lifecycle_status in ('ACTIVE', 'SUSPENDED')
  ) then
    return array[]::text[];
  end if;
  select coalesce(array_agg(distinct rp.permission_code order by rp.permission_code), array[]::text[])
  into result
  from public.user_platform_role_assignments a
  join public.platform_role_permissions rp on rp.role_code = a.role_code
  where a.user_id = uid and a.revoked_at is null;
  if result is null or cardinality(result) = 0 then
    select coalesce(array_agg(rp.permission_code order by rp.permission_code), array[]::text[])
    into result
    from public.platform_role_permissions rp
    where rp.role_code = 'USER';
  end if;
  return coalesce(result, array[]::text[]);
end;
$$;

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
begin
  if char_length(coalesce(p_password, '')) < 1 then
    raise exception 'INVALID_CREDENTIALS';
  end if;
  select i.user_id, u.email, u.encrypted_password
    into v_user, v_email, v_hash
  from public.platform_admin_identities i
  join auth.users u on u.id = i.user_id
  where i.username_normalized = lower(btrim(coalesce(p_username, '')))
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
     and not public._acl_platform_role(v_user, 'MODERATOR') then
    raise exception 'INVALID_CREDENTIALS';
  end if;
  return jsonb_build_object('email', v_email);
end;
$$;

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
  if uid is null then return null; end if;
  select * into i from public.platform_admin_identities
  where user_id = uid and disabled_at is null;
  if not found then return null; end if;
  return jsonb_build_object(
    'is_admin_identity', true,
    'must_change_password', i.must_change_password,
    'is_root', i.is_root
  );
end;
$$;

create or replace function public.admin_clear_must_change_password()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_is_admin_identity(uid) then raise exception 'FORBIDDEN'; end if;
  update public.platform_admin_identities
    set must_change_password = false,
        updated_at = timezone('utc', now())
    where user_id = uid;
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.assign_platform_role(
  p_target_user_id uuid,
  p_role_code text,
  p_expires_at timestamptz default null,
  p_reason_code text default 'manual_admin',
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  role_code text := upper(btrim(coalesce(p_role_code, '')));
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_target_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  if actor = p_target_user_id then raise exception 'SELF_ASSIGNMENT_FORBIDDEN'; end if;
  if not public.has_permission('roles.assign') then raise exception 'FORBIDDEN'; end if;
  if public._acl_is_protected_admin_root(p_target_user_id)
     and not public._acl_is_protected_admin_root(actor) then
    raise exception 'ROOT_PROTECTED';
  end if;
  if not exists (select 1 from public.platform_roles where code = role_code) then
    raise exception 'ROLE_UNKNOWN';
  end if;
  if role_code in ('ADMIN', 'SUPERADMIN')
     and not public._acl_platform_role(actor, 'SUPERADMIN') then
    raise exception 'HIERARCHY_FORBIDDEN';
  end if;
  if public._acl_platform_role(actor, 'ADMIN')
     and not public._acl_platform_role(actor, 'SUPERADMIN')
     and role_code not in ('USER', 'MODERATOR') then
    raise exception 'HIERARCHY_FORBIDDEN';
  end if;
  insert into public.user_platform_role_assignments (user_id, role_code, granted_by)
  values (p_target_user_id, role_code, actor)
  on conflict (user_id, role_code) do update
    set revoked_at = null,
        granted_by = actor,
        granted_at = timezone('utc', now());
  perform public._canon_admin_audit(
    'ASSIGN_PLATFORM_ROLE', 'user_platform_role_assignments', p_target_user_id,
    jsonb_build_object('role', role_code, 'reason', p_reason_code, 'note', p_note)
  );
  return jsonb_build_object('ok', true, 'role', role_code);
end;
$$;

create or replace function public.revoke_platform_role(
  p_target_user_id uuid,
  p_role_code text,
  p_reason_code text default 'manual_admin',
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  role_code text := upper(btrim(coalesce(p_role_code, '')));
  v_left integer;
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if actor = p_target_user_id then raise exception 'SELF_REVOCATION_FORBIDDEN'; end if;
  if not public.has_permission('roles.revoke') then raise exception 'FORBIDDEN'; end if;
  if public._acl_is_protected_admin_root(p_target_user_id) then
    raise exception 'ROOT_PROTECTED';
  end if;
  if role_code in ('ADMIN', 'SUPERADMIN')
     and not public._acl_platform_role(actor, 'SUPERADMIN') then
    raise exception 'HIERARCHY_FORBIDDEN';
  end if;
  if role_code = 'SUPERADMIN' then
    select count(*) into v_left
    from public.user_platform_role_assignments
    where role_code = 'SUPERADMIN' and revoked_at is null;
    if v_left <= 1 then raise exception 'LAST_SUPERADMIN_REQUIRED'; end if;
  end if;
  update public.user_platform_role_assignments
    set revoked_at = timezone('utc', now())
    where user_id = p_target_user_id
      and role_code = role_code
      and revoked_at is null;
  perform public._canon_admin_audit(
    'REVOKE_PLATFORM_ROLE', 'user_platform_role_assignments', p_target_user_id,
    jsonb_build_object('role', role_code, 'reason', p_reason_code, 'note', p_note)
  );
  return jsonb_build_object('ok', true, 'role', role_code);
end;
$$;

create or replace function public.change_user_account_status(
  p_target_user_id uuid,
  p_new_status text,
  p_reason_code text default 'manual_admin',
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  v_new text := upper(btrim(coalesce(p_new_status, '')));
  v_life text;
  v_prev text;
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if actor = p_target_user_id then raise exception 'SELF_STATUS_CHANGE_FORBIDDEN'; end if;
  if not public.has_permission('users.change_status') then raise exception 'FORBIDDEN'; end if;
  if public._acl_is_protected_admin_root(p_target_user_id) then
    raise exception 'ROOT_PROTECTED';
  end if;
  if public._acl_is_admin_identity(p_target_user_id) then
    raise exception 'ADMIN_IDENTITY_NOT_PERSON';
  end if;
  v_life := case v_new
    when 'SUSPENDED' then 'SUSPENDED'
    when 'BANNED' then 'SUSPENDED'
    when 'RESTRICTED' then 'ACTIVE'
    when 'ACTIVE' then 'ACTIVE'
    else null
  end;
  if v_life is null then raise exception 'VALIDATION'; end if;
  select lifecycle_status into v_prev from public.persons where user_id = p_target_user_id;
  if v_prev is null then raise exception 'USER_NOT_FOUND'; end if;
  update public.persons set lifecycle_status = v_life where user_id = p_target_user_id;
  insert into public.person_status_history (
    person_id, previous_status, new_status, reason_code, note, changed_by
  ) values (
    p_target_user_id, v_prev, v_life, coalesce(nullif(btrim(p_reason_code), ''), 'manual_admin'),
    nullif(btrim(coalesce(p_note, '')), ''), actor
  );
  perform public._canon_admin_audit(
    'CHANGE_USER_ACCOUNT_STATUS', 'persons', p_target_user_id,
    jsonb_build_object('from', v_prev, 'to', v_life, 'reason', p_reason_code)
  );
  return jsonb_build_object('ok', true, 'status', v_life);
end;
$$;

revoke all on function public._acl_is_admin_identity(uuid) from public, anon, authenticated;
revoke all on function public._acl_is_protected_admin_root(uuid) from public, anon, authenticated;
revoke all on function public.admin_begin_login(text, text) from public;
revoke all on function public.get_admin_session() from public, anon;
revoke all on function public.admin_clear_must_change_password() from public, anon;

grant execute on function public.has_permission(text) to authenticated;
grant execute on function public.get_my_platform_roles() to authenticated;
grant execute on function public.get_my_permissions() to authenticated;
grant execute on function public.admin_begin_login(text, text) to anon, authenticated;
grant execute on function public.get_admin_session() to authenticated;
grant execute on function public.admin_clear_must_change_password() to authenticated;
grant execute on function public.assign_platform_role(uuid, text, timestamptz, text, text) to authenticated;
grant execute on function public.revoke_platform_role(uuid, text, text, text) to authenticated;
grant execute on function public.change_user_account_status(uuid, text, text, text) to authenticated;

notify pgrst, 'reload schema';
