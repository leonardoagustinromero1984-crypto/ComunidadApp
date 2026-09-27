-- 1055: platform admin foundation (roles/permissions RPCs, content_reports RLS, audit).
-- Does not edit 1052–1054. Deny-by-default. ActiveContext is never authority.

-- ---------------------------------------------------------------------------
-- Permission catalog (Android PermissionCode) + role matrix
-- ---------------------------------------------------------------------------

insert into public.platform_permissions (code, description) values
  ('profile.read.own', 'Read own profile'),
  ('profile.update.own', 'Update own profile'),
  ('profile.read.public', 'Read public profile'),
  ('moderation.view', 'View moderation queue'),
  ('moderation.manage_reports', 'Triage content reports'),
  ('moderation.manage_cases', 'Manage moderation cases'),
  ('moderation.apply_actions', 'Apply moderation actions'),
  ('moderation.view_sensitive', 'View reporter identity'),
  ('moderation.review_appeals', 'Review appeals'),
  ('organizations.review_verification', 'Review org verification'),
  ('organizations.revoke_verification', 'Revoke org verification'),
  ('support.view', 'View support'),
  ('support.manage', 'Manage support'),
  ('support.view_sensitive', 'View sensitive support'),
  ('users.view_private', 'View private user fields'),
  ('users.change_status', 'Change account lifecycle'),
  ('roles.view', 'View platform roles'),
  ('roles.assign', 'Assign platform roles'),
  ('roles.revoke', 'Revoke platform roles'),
  ('audit.view', 'View administrative audit'),
  ('observability.view', 'View observability'),
  ('observability.manage', 'Manage observability'),
  ('audit.view_sensitive', 'View sensitive audit'),
  ('security.events.view', 'View security events'),
  ('export.audit_data', 'Export audit data'),
  ('alert.manage', 'Manage alerts'),
  ('retention.manage', 'Manage retention'),
  ('health.check.execute', 'Execute health checks'),
  ('pet.read', 'Staff pet read'),
  ('pet.create', 'Staff pet create'),
  ('pet.update', 'Staff pet update'),
  ('pet.manage_responsibilities', 'Staff pet responsibilities'),
  ('pet.manage_authorizations', 'Staff pet authorizations'),
  ('pet.initiate_transfer', 'Staff pet transfer initiate'),
  ('pet.accept_transfer', 'Staff pet transfer accept'),
  ('pet.cancel_transfer', 'Staff pet transfer cancel'),
  ('pet.mark_deceased', 'Staff pet deceased'),
  ('pet.archive', 'Staff pet archive'),
  ('pet.restore', 'Staff pet restore'),
  ('pet.manage_media', 'Staff pet media'),
  ('pet.view_history', 'Staff pet history'),
  ('pet.manage_health', 'Staff pet health')
on conflict (code) do nothing;

insert into public.platform_role_permissions (role_code, permission_code)
select r.role_code, p.code
from (
  values
    ('USER', 'profile.read.own'),
    ('USER', 'profile.update.own'),
    ('USER', 'profile.read.public'),
    ('MODERATOR', 'profile.read.own'),
    ('MODERATOR', 'profile.update.own'),
    ('MODERATOR', 'profile.read.public'),
    ('MODERATOR', 'moderation.view'),
    ('MODERATOR', 'moderation.manage_reports'),
    ('MODERATOR', 'moderation.manage_cases'),
    ('MODERATOR', 'moderation.review_appeals'),
    ('ADMIN', 'profile.read.own'),
    ('ADMIN', 'profile.update.own'),
    ('ADMIN', 'profile.read.public'),
    ('ADMIN', 'moderation.view'),
    ('ADMIN', 'moderation.manage_reports'),
    ('ADMIN', 'moderation.manage_cases'),
    ('ADMIN', 'moderation.review_appeals'),
    ('ADMIN', 'moderation.apply_actions'),
    ('ADMIN', 'moderation.view_sensitive'),
    ('ADMIN', 'organizations.review_verification'),
    ('ADMIN', 'support.view'),
    ('ADMIN', 'support.manage'),
    ('ADMIN', 'support.view_sensitive'),
    ('ADMIN', 'users.view_private'),
    ('ADMIN', 'users.change_status'),
    ('ADMIN', 'roles.view'),
    ('ADMIN', 'roles.assign'),
    ('ADMIN', 'roles.revoke'),
    ('ADMIN', 'audit.view'),
    ('ADMIN', 'observability.view'),
    ('ADMIN', 'observability.manage'),
    ('ADMIN', 'audit.view_sensitive'),
    ('ADMIN', 'security.events.view'),
    ('ADMIN', 'export.audit_data'),
    ('ADMIN', 'alert.manage'),
    ('ADMIN', 'retention.manage'),
    ('ADMIN', 'health.check.execute'),
    ('ADMIN', 'pet.read'),
    ('ADMIN', 'pet.view_history')
) as r(role_code, code)
join public.platform_permissions p on p.code = r.code
on conflict do nothing;

insert into public.platform_role_permissions (role_code, permission_code)
select 'SUPERADMIN', p.code from public.platform_permissions p
on conflict do nothing;

create or replace function public._acl_has_platform_permission(p_user_id uuid, p_code text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce((
    select exists (
      select 1
      from public.user_platform_role_assignments a
      join public.platform_role_permissions rp on rp.role_code = a.role_code
      where a.user_id = p_user_id
        and a.revoked_at is null
        and rp.permission_code = p_code
    )
    or (
      not exists (
        select 1 from public.user_platform_role_assignments a
        where a.user_id = p_user_id and a.revoked_at is null
      )
      and exists (
        select 1 from public.platform_role_permissions rp
        where rp.role_code = 'USER' and rp.permission_code = p_code
      )
    )
  ), false);
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

create or replace function public._canon_admin_audit(
  p_action text,
  p_table text,
  p_id uuid,
  p_meta jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.security_audit_events (actor_user_id, action, entity_table, entity_id, metadata)
  values (auth.uid(), p_action, p_table, p_id, coalesce(p_meta, '{}'::jsonb));
end;
$$;

-- ---------------------------------------------------------------------------
-- content_reports: compatible columns + deny-by-default RLS
-- ---------------------------------------------------------------------------

alter table public.content_reports enable row level security;

alter table public.content_reports
  add column if not exists target_type text,
  add column if not exists target_ref text,
  add column if not exists reason_detail text,
  add column if not exists priority text not null default 'NORMAL',
  add column if not exists updated_at timestamptz not null default timezone('utc', now());

alter table public.content_reports alter column target_id drop not null;

do $$
begin
  alter table public.content_reports drop constraint if exists content_reports_status_check;
exception when others then null;
end $$;

alter table public.content_reports
  add constraint content_reports_status_check
  check (status in (
    'OPEN', 'CLOSED', 'DISMISSED', 'ACTION_REQUIRED', 'TRIAGED', 'IN_REVIEW',
    'RESOLVED', 'DUPLICATE', 'REVIEWED', 'ACTIONED'
  ));

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'content_reports_priority_allowed'
  ) then
    alter table public.content_reports
      add constraint content_reports_priority_allowed
      check (priority in ('LOW', 'NORMAL', 'HIGH', 'URGENT'));
  end if;
end $$;

drop policy if exists content_reports_select on public.content_reports;
drop policy if exists content_reports_insert on public.content_reports;
drop policy if exists content_reports_update on public.content_reports;
drop policy if exists content_reports_delete on public.content_reports;

create policy content_reports_select on public.content_reports
  for select to authenticated
  using (reporter_user_id = auth.uid());

create policy content_reports_insert on public.content_reports
  for insert to authenticated
  with check (auth.uid() is not null and reporter_user_id = auth.uid());

create policy content_reports_update on public.content_reports
  for update to authenticated
  using (false);

create policy content_reports_delete on public.content_reports
  for delete to authenticated
  using (false);

grant select, insert on public.content_reports to authenticated;
revoke update, delete on public.content_reports from authenticated, anon;

create or replace function public._map_report_reason(p_raw text)
returns text
language sql
immutable
as $$
  select case upper(btrim(coalesce(p_raw, '')))
    when 'SPAM' then 'SPAM'
    when 'ABUSE' then 'ABUSE'
    when 'HARASSMENT' then 'ABUSE'
    when 'HATE' then 'ABUSE'
    when 'VIOLENCE' then 'ABUSE'
    when 'INAPPROPRIATE' then 'INAPPROPRIATE'
    when 'SCAM' then 'OTHER'
    when 'IMPERSONATION' then 'OTHER'
    when 'PRIVACY' then 'OTHER'
    when 'OTHER' then 'OTHER'
    else 'OTHER'
  end;
$$;

create or replace function public.create_content_report(
  p_target_type text,
  p_target_id text,
  p_reason_code text,
  p_description text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  v_type text := upper(btrim(coalesce(p_target_type, 'OTHER')));
  v_target text := btrim(coalesce(p_target_id, ''));
  v_reason text;
  v_id uuid;
  v_uuid uuid;
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if char_length(v_target) < 1 or char_length(v_target) > 128 then
    raise exception 'VALIDATION';
  end if;
  v_reason := public._map_report_reason(p_reason_code);
  begin
    v_uuid := v_target::uuid;
  exception when others then
    v_uuid := null;
  end;
  insert into public.content_reports (
    reporter_user_id, target_table, target_id, target_type, target_ref,
    reason_code, reason_detail, status, priority
  ) values (
    actor,
    lower(v_type),
    v_uuid,
    v_type,
    v_target,
    v_reason,
    nullif(btrim(coalesce(p_description, '')), ''),
    'OPEN',
    'NORMAL'
  )
  returning id into v_id;
  perform public._canon_admin_audit(
    'CREATE_CONTENT_REPORT', 'content_reports', v_id,
    jsonb_build_object('target_type', v_type, 'reason_code', v_reason)
  );
  return jsonb_build_object(
    'id', v_id,
    'target_type', v_type,
    'target_id', v_target,
    'reason_code', v_reason,
    'status', 'OPEN',
    'priority', 'NORMAL',
    'created_at', timezone('utc', now())
  );
end;
$$;

create or replace function public.get_my_content_reports(p_limit int default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', r.id,
      'target_type', coalesce(r.target_type, r.target_table),
      'target_id', coalesce(r.target_ref, r.target_id::text),
      'reason_code', r.reason_code,
      'status', r.status,
      'priority', r.priority,
      'created_at', r.created_at
    ) order by r.created_at desc)
    from (
      select * from public.content_reports
      where reporter_user_id = actor
      order by created_at desc
      limit lim
    ) r
  ), '[]'::jsonb);
end;
$$;

create or replace function public.list_moderation_queue(
  p_status text default null,
  p_limit int default 50
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
  can_sensitive boolean;
  v_status text := nullif(upper(btrim(coalesce(p_status, ''))), '');
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('moderation.view') then raise exception 'FORBIDDEN'; end if;
  can_sensitive := public.has_permission('moderation.view_sensitive');
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', r.id,
      'reporter_id', case when can_sensitive then r.reporter_user_id else null end,
      'target_type', coalesce(r.target_type, r.target_table),
      'target_id', coalesce(r.target_ref, r.target_id::text),
      'reason_code', r.reason_code,
      'priority', r.priority,
      'status', r.status,
      'created_at', r.created_at,
      'updated_at', r.updated_at
    ) order by r.created_at asc)
    from (
      select * from public.content_reports
      where (v_status is null or status = v_status)
        and status not in ('CLOSED', 'DUPLICATE', 'DISMISSED', 'RESOLVED')
      order by created_at asc
      limit lim
    ) r
  ), '[]'::jsonb);
end;
$$;

create or replace function public.get_moderation_report_for_staff(p_report_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  can_sensitive boolean;
  r public.content_reports%rowtype;
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('moderation.view') then raise exception 'FORBIDDEN'; end if;
  can_sensitive := public.has_permission('moderation.view_sensitive');
  select * into r from public.content_reports where id = p_report_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  return jsonb_build_object(
    'id', r.id,
    'reporter_id', case when can_sensitive then r.reporter_user_id else null end,
    'target_type', coalesce(r.target_type, r.target_table),
    'target_id', coalesce(r.target_ref, r.target_id::text),
    'reason_code', r.reason_code,
    'reason_detail', r.reason_detail,
    'priority', r.priority,
    'status', r.status,
    'created_at', r.created_at,
    'updated_at', r.updated_at
  );
end;
$$;

create or replace function public.triage_content_report(
  p_report_id uuid,
  p_status text,
  p_priority text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  v_status text := upper(btrim(coalesce(p_status, '')));
  v_priority text;
  prev public.content_reports%rowtype;
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('moderation.manage_reports') then raise exception 'FORBIDDEN'; end if;
  if v_status not in (
    'TRIAGED', 'IN_REVIEW', 'ACTION_REQUIRED', 'DISMISSED', 'RESOLVED', 'CLOSED',
    'OPEN', 'REVIEWED', 'ACTIONED'
  ) then
    raise exception 'VALIDATION';
  end if;
  select * into prev from public.content_reports where id = p_report_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  v_priority := coalesce(nullif(upper(btrim(coalesce(p_priority, ''))), ''), prev.priority, 'NORMAL');
  update public.content_reports
    set status = v_status,
        priority = v_priority,
        updated_at = timezone('utc', now())
    where id = p_report_id;
  perform public._canon_admin_audit(
    'TRIAGE_CONTENT_REPORT', 'content_reports', p_report_id,
    jsonb_build_object('from', prev.status, 'to', v_status)
  );
  return jsonb_build_object('id', p_report_id, 'status', v_status, 'priority', v_priority);
end;
$$;

-- ---------------------------------------------------------------------------
-- Users / roles / lifecycle
-- ---------------------------------------------------------------------------

create table if not exists public.person_status_history (
  id uuid primary key default gen_random_uuid(),
  person_id uuid not null references public.persons(user_id) on delete cascade,
  previous_status text,
  new_status text not null,
  reason_code text not null,
  note text,
  changed_by uuid not null references public.persons(user_id),
  changed_at timestamptz not null default timezone('utc', now())
);

alter table public.person_status_history enable row level security;
revoke all on public.person_status_history from public, anon, authenticated;

create or replace function public._map_account_status(p_life text)
returns text
language sql
immutable
as $$
  select case upper(btrim(coalesce(p_life, '')))
    when 'SUSPENDED' then 'SUSPENDED'
    when 'PENDING_ERASURE' then 'BANNED'
    when 'ERASED_MINIMIZED' then 'BANNED'
    else 'ACTIVE'
  end;
$$;

create or replace function public.admin_search_users(p_query text, p_limit int default 20)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  actor uuid := auth.uid();
  q text := lower(btrim(coalesce(p_query, '')));
  lim int := least(greatest(coalesce(p_limit, 20), 1), 50);
  can_private boolean;
begin
  if actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public.has_permission('roles.view')
    or public.has_permission('users.change_status')
    or public.has_permission('users.view_private')
  ) then
    raise exception 'FORBIDDEN';
  end if;
  if char_length(q) < 2 then return '[]'::jsonb; end if;
  can_private := public.has_permission('users.view_private');
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', t.user_id,
      'display_name', t.display_name,
      'username', t.username,
      'account_status', public._map_account_status(t.lifecycle_status),
      'onboarding_status', null,
      'email', t.email,
      'created_at', t.created_at
    ) order by t.display_name, t.username)
    from (
      select
        p.user_id,
        p.display_name,
        p.username,
        p.lifecycle_status,
        p.created_at,
        case when can_private then u.email else null end as email
      from public.persons p
      left join auth.users u on u.id = p.user_id
      where p.display_name ilike '%' || q || '%'
         or p.username ilike '%' || q || '%'
         or (can_private and u.email ilike '%' || q || '%')
      order by p.display_name, p.username
      limit lim
    ) t
  ), '[]'::jsonb);
end;
$$;

create or replace function public.admin_get_user_roles(p_target_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if auth.uid() is distinct from p_target_user_id
     and not public.has_permission('roles.view') then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'role_code', a.role_code,
      'assigned_at', a.granted_at,
      'expires_at', null
    ) order by a.role_code)
    from public.user_platform_role_assignments a
    where a.user_id = p_target_user_id and a.revoked_at is null
  ), '[]'::jsonb);
end;
$$;

create or replace function public.admin_get_user_status_history(
  p_target_user_id uuid,
  p_limit int default 20
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  lim int := least(greatest(coalesce(p_limit, 20), 1), 50);
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public.has_permission('audit.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', h.id,
      'previous_status', h.previous_status,
      'new_status', h.new_status,
      'reason_code', h.reason_code,
      'changed_by', h.changed_by,
      'changed_at', h.changed_at
    ) order by h.changed_at desc)
    from (
      select * from public.person_status_history
      where person_id = p_target_user_id
      order by changed_at desc
      limit lim
    ) h
  ), '[]'::jsonb);
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

create or replace function public.canon_admin_dashboard_summary()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public.has_permission('moderation.view')
    or public.has_permission('roles.view')
    or public.has_permission('users.change_status')
    or public.has_permission('users.view_private')
  ) then
    raise exception 'FORBIDDEN';
  end if;
  return jsonb_build_object(
    'users', (select count(*) from public.persons where lifecycle_status <> 'ERASED_MINIMIZED'),
    'organizations', (select count(*) from public.organizations),
    'open_reports', (
      select count(*) from public.content_reports
      where status not in ('CLOSED', 'DUPLICATE', 'DISMISSED', 'RESOLVED')
    )
  );
end;
$$;

revoke all on function public.has_permission(text) from public, anon;
revoke all on function public.get_my_platform_roles() from public, anon;
revoke all on function public.get_my_permissions() from public, anon;
revoke all on function public.create_content_report(text, text, text, text) from public, anon;
revoke all on function public.get_my_content_reports(int) from public, anon;
revoke all on function public.list_moderation_queue(text, int) from public, anon;
revoke all on function public.get_moderation_report_for_staff(uuid) from public, anon;
revoke all on function public.triage_content_report(uuid, text, text) from public, anon;
revoke all on function public.admin_search_users(text, int) from public, anon;
revoke all on function public.admin_get_user_roles(uuid) from public, anon;
revoke all on function public.admin_get_user_status_history(uuid, int) from public, anon;
revoke all on function public.assign_platform_role(uuid, text, timestamptz, text, text) from public, anon;
revoke all on function public.revoke_platform_role(uuid, text, text, text) from public, anon;
revoke all on function public.change_user_account_status(uuid, text, text, text) from public, anon;
revoke all on function public.canon_admin_dashboard_summary() from public, anon;

grant execute on function public.has_permission(text) to authenticated;
grant execute on function public.get_my_platform_roles() to authenticated;
grant execute on function public.get_my_permissions() to authenticated;
grant execute on function public.create_content_report(text, text, text, text) to authenticated;
grant execute on function public.get_my_content_reports(int) to authenticated;
grant execute on function public.list_moderation_queue(text, int) to authenticated;
grant execute on function public.get_moderation_report_for_staff(uuid) to authenticated;
grant execute on function public.triage_content_report(uuid, text, text) to authenticated;
grant execute on function public.admin_search_users(text, int) to authenticated;
grant execute on function public.admin_get_user_roles(uuid) to authenticated;
grant execute on function public.admin_get_user_status_history(uuid, int) to authenticated;
grant execute on function public.assign_platform_role(uuid, text, timestamptz, text, text) to authenticated;
grant execute on function public.revoke_platform_role(uuid, text, text, text) to authenticated;
grant execute on function public.change_user_account_status(uuid, text, text, text) to authenticated;
grant execute on function public.canon_admin_dashboard_summary() to authenticated;

revoke all on function public._acl_has_platform_permission(uuid, text) from public, anon, authenticated;
revoke all on function public._canon_admin_audit(text, text, uuid, jsonb) from public, anon, authenticated;
revoke all on function public._map_report_reason(text) from public, anon, authenticated;
revoke all on function public._map_account_status(text) from public, anon, authenticated;

notify pgrst, 'reload schema';
