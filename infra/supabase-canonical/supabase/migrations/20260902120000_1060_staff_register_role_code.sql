-- 1060: staff_register_identity role_code is ambiguous (42702).
-- PL/pgSQL variable `role_code` collides with user_platform_role_assignments.role_code
-- on INSERT ... ON CONFLICT (user_id, role_code).
-- createUser succeeded; register failed; Edge compensation deleted the auth user.
-- Does not edit 1057/1058/1059. Does not change PERSON / login / permissions.

create or replace function public.staff_register_identity(
  p_user_id uuid,
  p_username text,
  p_display_name text,
  p_role_code text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  uname text := lower(btrim(coalesce(p_username, '')));
  v_role_code text := upper(btrim(coalesce(p_role_code, '')));
  recent integer;
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.manage') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  if uname !~ '^[a-z0-9._-]{3,64}$' then raise exception 'USERNAME_INVALID'; end if;
  if btrim(coalesce(p_display_name, '')) = '' then raise exception 'NAME_REQUIRED'; end if;
  if v_role_code not in ('ADMIN', 'MODERATOR', 'SUPPORT') then raise exception 'ROLE_FORBIDDEN'; end if;
  if public._acl_is_protected_admin_root(p_user_id) then raise exception 'ROOT_PROTECTED'; end if;
  select count(*) into recent
  from public.security_audit_events
  where actor_user_id = actor
    and action = 'ADMIN_STAFF_CREATED'
    and occurred_at > timezone('utc', now()) - interval '10 minutes';
  if recent >= 10 then raise exception 'RATE_LIMITED'; end if;
  begin
    insert into public.platform_admin_identities (
      user_id, username_normalized, display_name, must_change_password, is_root
    ) values (
      p_user_id, uname, btrim(p_display_name), true, false
    );
  exception
    when unique_violation then
      raise exception 'USERNAME_TAKEN';
  end;
  insert into public.user_platform_role_assignments as ura (user_id, role_code, granted_by)
  values (p_user_id, v_role_code, actor)
  on conflict (user_id, role_code) do update
    set revoked_at = null, granted_by = actor, granted_at = timezone('utc', now());
  perform public._canon_admin_audit(
    'ADMIN_STAFF_CREATED', 'platform_admin_identities', p_user_id,
    jsonb_build_object('username', uname, 'role', v_role_code)
  );
  return jsonb_build_object('ok', true, 'user_id', p_user_id, 'username', uname);
end;
$$;

create or replace function public.staff_set_role(p_user_id uuid, p_role_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  v_role_code text := upper(btrim(coalesce(p_role_code, '')));
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('staff.manage') then raise exception 'FORBIDDEN'; end if;
  if p_user_id is null then raise exception 'TARGET_REQUIRED'; end if;
  if public._acl_is_protected_admin_root(p_user_id) then raise exception 'ROOT_PROTECTED'; end if;
  if public._acl_platform_role(p_user_id, 'SUPERADMIN') then raise exception 'ROLE_FORBIDDEN'; end if;
  if v_role_code not in ('ADMIN', 'MODERATOR', 'SUPPORT') then raise exception 'ROLE_FORBIDDEN'; end if;
  if not exists (select 1 from public.platform_admin_identities where user_id = p_user_id) then
    raise exception 'STAFF_NOT_FOUND';
  end if;
  update public.user_platform_role_assignments as ura
    set revoked_at = timezone('utc', now())
    where ura.user_id = p_user_id
      and ura.role_code in ('ADMIN', 'MODERATOR', 'SUPPORT')
      and ura.revoked_at is null;
  insert into public.user_platform_role_assignments as ura (user_id, role_code, granted_by)
  values (p_user_id, v_role_code, actor)
  on conflict (user_id, role_code) do update
    set revoked_at = null, granted_by = actor, granted_at = timezone('utc', now());
  perform public._canon_admin_audit(
    'ADMIN_STAFF_ROLE_CHANGED', 'user_platform_role_assignments', p_user_id,
    jsonb_build_object('role', v_role_code)
  );
  return jsonb_build_object('ok', true, 'role', v_role_code);
end;
$$;

revoke all on function public.staff_register_identity(uuid, text, text, text) from public, anon;
revoke all on function public.staff_set_role(uuid, text) from public, anon;
grant execute on function public.staff_register_identity(uuid, text, text, text) to authenticated;
grant execute on function public.staff_set_role(uuid, text) to authenticated;
