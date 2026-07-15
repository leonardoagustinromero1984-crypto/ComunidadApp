-- M02 Etapa 4 — Roles, permisos, historial y RPCs de administración.
-- Pretende aplicarse DESPUÉS de 014–017.
-- Estado remoto de 014–017: NO verificado en esta etapa (solo existen en repo).
-- No edita migraciones anteriores.
-- Bootstrap SUPERADMIN: usar script docs/04-calidad/M02-bootstrap-superadmin.md
--   (UUID fuera del repositorio). Nunca versionar UUID/email reales.

-- ---------------------------------------------------------------------------
-- Catálogo
-- ---------------------------------------------------------------------------

create table if not exists public.platform_roles (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    description text null,
    is_system boolean not null default true,
    created_at timestamptz not null default timezone('utc', now()),
    constraint platform_roles_code_upper check (code = upper(code))
);

create table if not exists public.permissions (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    description text null,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.role_permissions (
    role_id uuid not null references public.platform_roles (id) on delete cascade,
    permission_id uuid not null references public.permissions (id) on delete cascade,
    primary key (role_id, permission_id)
);

create table if not exists public.user_role_assignments (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    role_id uuid not null references public.platform_roles (id) on delete restrict,
    assigned_by uuid null references auth.users (id),
    assigned_at timestamptz not null default timezone('utc', now()),
    expires_at timestamptz null,
    revoked_at timestamptz null,
    revoked_by uuid null references auth.users (id),
    constraint user_role_assignments_dates check (
        expires_at is null or expires_at > assigned_at
    )
);

create unique index if not exists user_role_assignments_active_uniq
    on public.user_role_assignments (user_id, role_id)
    where revoked_at is null;

create index if not exists user_role_assignments_user_idx
    on public.user_role_assignments (user_id)
    where revoked_at is null;

create table if not exists public.user_status_history (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    previous_status text null,
    new_status text not null,
    reason_code text not null,
    note text null,
    changed_by uuid not null references auth.users (id),
    changed_at timestamptz not null default timezone('utc', now()),
    request_id text null,
    constraint user_status_history_status_allowed check (
        new_status in ('ACTIVE', 'RESTRICTED', 'SUSPENDED', 'BANNED')
        and (previous_status is null or previous_status in ('ACTIVE', 'RESTRICTED', 'SUSPENDED', 'BANNED'))
    ),
    constraint user_status_history_note_len check (note is null or char_length(note) <= 280)
);

create table if not exists public.role_assignment_history (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    role_code text not null,
    action text not null,
    previous_state text null,
    new_state text null,
    reason_code text not null,
    note text null,
    changed_by uuid not null references auth.users (id),
    changed_at timestamptz not null default timezone('utc', now()),
    request_id text null,
    constraint role_assignment_history_action_allowed check (
        action in ('ASSIGN', 'REVOKE', 'EXPIRE')
    ),
    constraint role_assignment_history_note_len check (note is null or char_length(note) <= 280)
);

-- ---------------------------------------------------------------------------
-- Seeds idempotentes
-- ---------------------------------------------------------------------------

insert into public.platform_roles (code, name, description) values
    ('USER', 'Usuario', 'Rol base'),
    ('MODERATOR', 'Moderador', 'Moderación de contenido'),
    ('ADMIN', 'Administrador', 'Administración de plataforma'),
    ('SUPERADMIN', 'Superadmin', 'Administración total')
on conflict (code) do nothing;

insert into public.permissions (code, description) values
    ('profile.read.own', 'Leer perfil propio'),
    ('profile.update.own', 'Actualizar perfil propio'),
    ('profile.read.public', 'Leer perfiles públicos'),
    ('moderation.view', 'Ver cola de moderación'),
    ('moderation.manage_reports', 'Gestionar reportes'),
    ('users.view_private', 'Ver datos privados de usuarios'),
    ('users.change_status', 'Cambiar account_status'),
    ('roles.view', 'Ver roles de usuarios'),
    ('roles.assign', 'Asignar roles'),
    ('roles.revoke', 'Revocar roles'),
    ('audit.view', 'Ver historiales de auditoría')
on conflict (code) do nothing;

-- USER
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'USER'
  and p.code in ('profile.read.own', 'profile.update.own', 'profile.read.public')
on conflict do nothing;

-- MODERATOR = USER + mod
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'MODERATOR'
  and p.code in (
    'profile.read.own', 'profile.update.own', 'profile.read.public',
    'moderation.view', 'moderation.manage_reports'
  )
on conflict do nothing;

-- ADMIN = MODERATOR + admin
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'ADMIN'
  and p.code in (
    'profile.read.own', 'profile.update.own', 'profile.read.public',
    'moderation.view', 'moderation.manage_reports',
    'users.view_private', 'users.change_status',
    'roles.view', 'roles.assign', 'roles.revoke', 'audit.view'
  )
on conflict do nothing;

-- SUPERADMIN = all
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'SUPERADMIN'
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

create or replace function public.is_valid_status_reason(p_code text)
returns boolean
language sql
immutable
as $$
    select lower(trim(coalesce(p_code, ''))) in (
        'policy_violation', 'abuse', 'spam', 'fraud', 'legal',
        'safety', 'ops_review', 'appeal_accepted', 'manual_admin', 'other'
    );
$$;

create or replace function public.is_valid_role_reason(p_code text)
returns boolean
language sql
immutable
as $$
    select lower(trim(coalesce(p_code, ''))) in (
        'staff_onboarding', 'staff_offboarding', 'ops_need',
        'incident_response', 'manual_admin', 'other'
    );
$$;

create or replace function public.role_rank(p_code text)
returns int
language sql
immutable
as $$
    select case upper(trim(coalesce(p_code, '')))
        when 'USER' then 1
        when 'MODERATOR' then 2
        when 'ADMIN' then 3
        when 'SUPERADMIN' then 4
        else 0
    end;
$$;

create or replace function public.user_has_active_role(p_user_id uuid, p_role_code text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.user_role_assignments a
        join public.platform_roles r on r.id = a.role_id
        where a.user_id = p_user_id
          and r.code = upper(trim(p_role_code))
          and a.revoked_at is null
          and (a.expires_at is null or a.expires_at > timezone('utc', now()))
    );
$$;

create or replace function public.user_highest_role_rank(p_user_id uuid)
returns int
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(max(public.role_rank(r.code)), 0)
    from public.user_role_assignments a
    join public.platform_roles r on r.id = a.role_id
    where a.user_id = p_user_id
      and a.revoked_at is null
      and (a.expires_at is null or a.expires_at > timezone('utc', now()));
$$;

create or replace function public.count_active_superadmins()
returns int
language sql
stable
security definer
set search_path = public
as $$
    select count(distinct a.user_id)::int
    from public.user_role_assignments a
    join public.platform_roles r on r.id = a.role_id
    where r.code = 'SUPERADMIN'
      and a.revoked_at is null
      and (a.expires_at is null or a.expires_at > timezone('utc', now()));
$$;

-- ---------------------------------------------------------------------------
-- has_permission / introspección propia
-- ---------------------------------------------------------------------------

create or replace function public.has_permission(permission_code text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    uid uuid := auth.uid();
    acct text;
begin
    if uid is null then
        return false;
    end if;

    select u.account_status into acct from public.users u where u.id = uid;
    if acct is null or acct <> 'ACTIVE' then
        -- RESTRICTED: permisos de perfil propios sí; elevados no.
        if acct = 'RESTRICTED' then
            if lower(trim(permission_code)) not in (
                'profile.read.own', 'profile.update.own', 'profile.read.public'
            ) then
                return false;
            end if;
        else
            return false;
        end if;
    end if;

    return exists (
        select 1
        from public.user_role_assignments a
        join public.role_permissions rp on rp.role_id = a.role_id
        join public.permissions p on p.id = rp.permission_id
        where a.user_id = uid
          and a.revoked_at is null
          and (a.expires_at is null or a.expires_at > timezone('utc', now()))
          and p.code = lower(trim(permission_code))
    );
end;
$$;

revoke all on function public.has_permission(text) from public;
grant execute on function public.has_permission(text) to authenticated;

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
    if uid is null then
        return array[]::text[];
    end if;
    if not exists (
        select 1 from public.users u
        where u.id = uid and u.account_status in ('ACTIVE', 'RESTRICTED')
    ) then
        return array[]::text[];
    end if;

    select coalesce(array_agg(distinct p.code order by p.code), array[]::text[])
    into result
    from public.user_role_assignments a
    join public.role_permissions rp on rp.role_id = a.role_id
    join public.permissions p on p.id = rp.permission_id
    where a.user_id = uid
      and a.revoked_at is null
      and (a.expires_at is null or a.expires_at > timezone('utc', now()));

    -- RESTRICTED: filtrar a perfil
    if exists (
        select 1 from public.users u where u.id = uid and u.account_status = 'RESTRICTED'
    ) then
        select coalesce(array_agg(x order by x), array[]::text[])
        into result
        from unnest(result) as x
        where x in ('profile.read.own', 'profile.update.own', 'profile.read.public');
    end if;

    return coalesce(result, array[]::text[]);
end;
$$;

revoke all on function public.get_my_permissions() from public;
grant execute on function public.get_my_permissions() to authenticated;

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
    if uid is null then
        return array[]::text[];
    end if;

    select coalesce(array_agg(distinct r.code order by r.code), array[]::text[])
    into result
    from public.user_role_assignments a
    join public.platform_roles r on r.id = a.role_id
    where a.user_id = uid
      and a.revoked_at is null
      and (a.expires_at is null or a.expires_at > timezone('utc', now()));

    return coalesce(result, array[]::text[]);
end;
$$;

revoke all on function public.get_my_platform_roles() from public;
grant execute on function public.get_my_platform_roles() to authenticated;

-- Asignación USER por defecto al completar onboarding (si no tiene roles)
create or replace function public.ensure_default_user_role(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    role_user uuid;
begin
    if p_user_id is null then
        return;
    end if;
    select id into role_user from public.platform_roles where code = 'USER';
    if role_user is null then
        return;
    end if;
    if exists (
        select 1 from public.user_role_assignments
        where user_id = p_user_id and revoked_at is null
    ) then
        return;
    end if;
    insert into public.user_role_assignments (user_id, role_id, assigned_by)
    values (p_user_id, role_user, null);
end;
$$;

-- ---------------------------------------------------------------------------
-- Administración: assign / revoke / status
-- ---------------------------------------------------------------------------

create or replace function public.assign_platform_role(
    p_target_user_id uuid,
    p_role_code text,
    p_expires_at timestamptz default null,
    p_reason_code text default 'manual_admin',
    p_note text default null,
    p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    role_code text := upper(trim(coalesce(p_role_code, '')));
    role_row public.platform_roles%rowtype;
    actor_rank int;
    target_rank int;
    new_rank int;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_target_user_id is null then
        raise exception 'TARGET_REQUIRED';
    end if;
    if actor = p_target_user_id then
        raise exception 'SELF_ASSIGNMENT_FORBIDDEN';
    end if;
    if not public.is_valid_role_reason(p_reason_code) then
        raise exception 'REASON_INVALID';
    end if;
    if not public.has_permission('roles.assign') then
        raise exception 'FORBIDDEN';
    end if;

    select * into role_row from public.platform_roles where code = role_code;
    if role_row.id is null then
        raise exception 'ROLE_UNKNOWN';
    end if;

    actor_rank := public.user_highest_role_rank(actor);
    target_rank := public.user_highest_role_rank(p_target_user_id);
    new_rank := public.role_rank(role_code);

    -- Solo SUPERADMIN asigna ADMIN / SUPERADMIN
    if role_code in ('ADMIN', 'SUPERADMIN') and not public.user_has_active_role(actor, 'SUPERADMIN') then
        raise exception 'HIERARCHY_FORBIDDEN';
    end if;
    -- ADMIN puede asignar MODERATOR; no elevar a su mismo nivel o superior
    if public.user_has_active_role(actor, 'ADMIN')
       and not public.user_has_active_role(actor, 'SUPERADMIN') then
        if role_code <> 'MODERATOR' and role_code <> 'USER' then
            raise exception 'HIERARCHY_FORBIDDEN';
        end if;
    end if;
    if new_rank >= actor_rank and not public.user_has_active_role(actor, 'SUPERADMIN') then
        raise exception 'HIERARCHY_FORBIDDEN';
    end if;
    if target_rank >= actor_rank and not public.user_has_active_role(actor, 'SUPERADMIN') then
        raise exception 'HIERARCHY_FORBIDDEN';
    end if;

    if exists (
        select 1 from public.user_role_assignments a
        where a.user_id = p_target_user_id
          and a.role_id = role_row.id
          and a.revoked_at is null
          and (a.expires_at is null or a.expires_at > timezone('utc', now()))
    ) then
        raise exception 'ROLE_ALREADY_ASSIGNED';
    end if;

    insert into public.user_role_assignments (user_id, role_id, assigned_by, expires_at)
    values (p_target_user_id, role_row.id, actor, p_expires_at);

    insert into public.role_assignment_history (
        user_id, role_code, action, previous_state, new_state,
        reason_code, note, changed_by, request_id
    ) values (
        p_target_user_id, role_code, 'ASSIGN', null, 'ACTIVE',
        lower(trim(p_reason_code)),
        nullif(left(trim(coalesce(p_note, '')), 280), ''),
        actor, nullif(trim(coalesce(p_request_id, '')), '')
    );

    return jsonb_build_object(
        'user_id', p_target_user_id,
        'role_code', role_code,
        'action', 'ASSIGN'
    );
end;
$$;

revoke all on function public.assign_platform_role(uuid, text, timestamptz, text, text, text) from public;
grant execute on function public.assign_platform_role(uuid, text, timestamptz, text, text, text) to authenticated;

create or replace function public.revoke_platform_role(
    p_target_user_id uuid,
    p_role_code text,
    p_reason_code text default 'manual_admin',
    p_note text default null,
    p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    role_code text := upper(trim(coalesce(p_role_code, '')));
    role_row public.platform_roles%rowtype;
    actor_rank int;
    target_rank int;
    affected int := 0;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_target_user_id is null then
        raise exception 'TARGET_REQUIRED';
    end if;
    if actor = p_target_user_id then
        raise exception 'SELF_REVOCATION_FORBIDDEN';
    end if;
    if not public.is_valid_role_reason(p_reason_code) then
        raise exception 'REASON_INVALID';
    end if;
    if not public.has_permission('roles.revoke') then
        raise exception 'FORBIDDEN';
    end if;

    select * into role_row from public.platform_roles where code = role_code;
    if role_row.id is null then
        raise exception 'ROLE_UNKNOWN';
    end if;

    actor_rank := public.user_highest_role_rank(actor);
    target_rank := public.user_highest_role_rank(p_target_user_id);

    if role_code in ('ADMIN', 'SUPERADMIN') and not public.user_has_active_role(actor, 'SUPERADMIN') then
        raise exception 'HIERARCHY_FORBIDDEN';
    end if;
    if public.user_has_active_role(actor, 'ADMIN')
       and not public.user_has_active_role(actor, 'SUPERADMIN')
       and role_code not in ('MODERATOR', 'USER') then
        raise exception 'HIERARCHY_FORBIDDEN';
    end if;
    if target_rank >= actor_rank and not public.user_has_active_role(actor, 'SUPERADMIN') then
        raise exception 'HIERARCHY_FORBIDDEN';
    end if;

    -- No dejar a la plataforma sin SUPERADMIN
    if role_code = 'SUPERADMIN'
       and public.user_has_active_role(p_target_user_id, 'SUPERADMIN')
       and public.count_active_superadmins() <= 1 then
        raise exception 'LAST_SUPERADMIN_PROTECTED';
    end if;

    update public.user_role_assignments
    set revoked_at = timezone('utc', now()),
        revoked_by = actor
    where user_id = p_target_user_id
      and role_id = role_row.id
      and revoked_at is null;

    get diagnostics affected = row_count;
    if affected = 0 then
        raise exception 'ROLE_NOT_ASSIGNED';
    end if;

    insert into public.role_assignment_history (
        user_id, role_code, action, previous_state, new_state,
        reason_code, note, changed_by, request_id
    ) values (
        p_target_user_id, role_code, 'REVOKE', 'ACTIVE', 'REVOKED',
        lower(trim(p_reason_code)),
        nullif(left(trim(coalesce(p_note, '')), 280), ''),
        actor, nullif(trim(coalesce(p_request_id, '')), '')
    );

    return jsonb_build_object(
        'user_id', p_target_user_id,
        'role_code', role_code,
        'action', 'REVOKE'
    );
end;
$$;

revoke all on function public.revoke_platform_role(uuid, text, text, text, text) from public;
grant execute on function public.revoke_platform_role(uuid, text, text, text, text) to authenticated;

create or replace function public.change_user_account_status(
    p_target_user_id uuid,
    p_new_status text,
    p_reason_code text default 'manual_admin',
    p_note text default null,
    p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    new_status text := upper(trim(coalesce(p_new_status, '')));
    prev_status text;
    actor_rank int;
    target_rank int;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_target_user_id is null then
        raise exception 'TARGET_REQUIRED';
    end if;
    if actor = p_target_user_id then
        raise exception 'SELF_STATUS_CHANGE_FORBIDDEN';
    end if;
    if new_status not in ('ACTIVE', 'RESTRICTED', 'SUSPENDED', 'BANNED') then
        raise exception 'STATUS_INVALID';
    end if;
    if not public.is_valid_status_reason(p_reason_code) then
        raise exception 'REASON_INVALID';
    end if;
    if not public.has_permission('users.change_status') then
        raise exception 'FORBIDDEN';
    end if;

    actor_rank := public.user_highest_role_rank(actor);
    target_rank := public.user_highest_role_rank(p_target_user_id);

    -- MODERATOR no suspende administradores
    if public.user_has_active_role(actor, 'MODERATOR')
       and not public.user_has_active_role(actor, 'ADMIN')
       and not public.user_has_active_role(actor, 'SUPERADMIN') then
        if target_rank >= public.role_rank('ADMIN') then
            raise exception 'HIERARCHY_FORBIDDEN';
        end if;
    end if;
    -- ADMIN no modifica SUPERADMIN
    if public.user_has_active_role(actor, 'ADMIN')
       and not public.user_has_active_role(actor, 'SUPERADMIN')
       and public.user_has_active_role(p_target_user_id, 'SUPERADMIN') then
        raise exception 'HIERARCHY_FORBIDDEN';
    end if;
    if target_rank >= actor_rank and not public.user_has_active_role(actor, 'SUPERADMIN') then
        raise exception 'HIERARCHY_FORBIDDEN';
    end if;

    select account_status into prev_status from public.users where id = p_target_user_id;
    if prev_status is null then
        raise exception 'USER_NOT_FOUND';
    end if;
    if prev_status = new_status then
        return jsonb_build_object(
            'user_id', p_target_user_id,
            'previous_status', prev_status,
            'new_status', new_status,
            'unchanged', true
        );
    end if;

    update public.users
    set account_status = new_status,
        updated_at = timezone('utc', now())
    where id = p_target_user_id;

    insert into public.user_status_history (
        user_id, previous_status, new_status, reason_code, note, changed_by, request_id
    ) values (
        p_target_user_id, prev_status, new_status,
        lower(trim(p_reason_code)),
        nullif(left(trim(coalesce(p_note, '')), 280), ''),
        actor, nullif(trim(coalesce(p_request_id, '')), '')
    );

    return jsonb_build_object(
        'user_id', p_target_user_id,
        'previous_status', prev_status,
        'new_status', new_status,
        'unchanged', false
    );
end;
$$;

revoke all on function public.change_user_account_status(uuid, text, text, text, text) from public;
grant execute on function public.change_user_account_status(uuid, text, text, text, text) to authenticated;

-- Lectura admin de roles/historial (allowlist)
create or replace function public.admin_get_user_roles(p_target_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    payload jsonb;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if not (
        public.has_permission('roles.view')
        or actor = p_target_user_id
    ) then
        raise exception 'FORBIDDEN';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'role_code', r.code,
        'assigned_at', a.assigned_at,
        'expires_at', a.expires_at,
        'revoked_at', a.revoked_at
    ) order by r.code), '[]'::jsonb)
    into payload
    from public.user_role_assignments a
    join public.platform_roles r on r.id = a.role_id
    where a.user_id = p_target_user_id
      and a.revoked_at is null
      and (a.expires_at is null or a.expires_at > timezone('utc', now()));

    return payload;
end;
$$;

revoke all on function public.admin_get_user_roles(uuid) from public;
grant execute on function public.admin_get_user_roles(uuid) to authenticated;

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
    actor uuid := auth.uid();
    lim int := least(greatest(coalesce(p_limit, 20), 1), 50);
    payload jsonb;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if not public.has_permission('audit.view') then
        raise exception 'FORBIDDEN';
    end if;

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select
            h.id,
            h.previous_status,
            h.new_status,
            h.reason_code,
            h.changed_by,
            h.changed_at,
            h.request_id
        from public.user_status_history h
        where h.user_id = p_target_user_id
        order by h.changed_at desc
        limit lim
    ) t;

    return payload;
end;
$$;

revoke all on function public.admin_get_user_status_history(uuid, int) from public;
grant execute on function public.admin_get_user_status_history(uuid, int) to authenticated;

create or replace function public.admin_search_users(
    p_query text,
    p_limit int default 20
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    q text := lower(trim(coalesce(p_query, '')));
    lim int := least(greatest(coalesce(p_limit, 20), 1), 50);
    can_private boolean;
    payload jsonb;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if not (
        public.has_permission('roles.view')
        or public.has_permission('users.change_status')
        or public.has_permission('moderation.view')
    ) then
        raise exception 'FORBIDDEN';
    end if;
    if char_length(q) < 2 then
        return '[]'::jsonb;
    end if;
    can_private := public.has_permission('users.view_private');

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select
            u.id,
            coalesce(u.display_name, u.name) as display_name,
            u.username::text as username,
            u.account_status,
            u.onboarding_status,
            case when can_private then u.email else null end as email
        from public.users u
        where
            u.username::text ilike '%' || q || '%'
            or coalesce(u.display_name, u.name) ilike '%' || q || '%'
            or (can_private and u.email ilike '%' || q || '%')
        order by coalesce(u.display_name, u.name), u.id
        limit lim
    ) t;

    return payload;
end;
$$;

revoke all on function public.admin_search_users(text, int) from public;
grant execute on function public.admin_search_users(text, int) to authenticated;

-- ---------------------------------------------------------------------------
-- RLS: catálogo lectura; assignments/history sin escritura cliente
-- ---------------------------------------------------------------------------

alter table public.platform_roles enable row level security;
alter table public.permissions enable row level security;
alter table public.role_permissions enable row level security;
alter table public.user_role_assignments enable row level security;
alter table public.user_status_history enable row level security;
alter table public.role_assignment_history enable row level security;

drop policy if exists platform_roles_select on public.platform_roles;
create policy platform_roles_select
    on public.platform_roles for select to authenticated
    using (true);

drop policy if exists permissions_select on public.permissions;
create policy permissions_select
    on public.permissions for select to authenticated
    using (true);

drop policy if exists role_permissions_select on public.role_permissions;
create policy role_permissions_select
    on public.role_permissions for select to authenticated
    using (true);

drop policy if exists user_role_assignments_select_own on public.user_role_assignments;
create policy user_role_assignments_select_own
    on public.user_role_assignments for select to authenticated
    using (auth.uid() = user_id);

drop policy if exists user_status_history_select_none on public.user_status_history;
create policy user_status_history_select_none
    on public.user_status_history for select to authenticated
    using (false);

drop policy if exists role_assignment_history_select_none on public.role_assignment_history;
create policy role_assignment_history_select_none
    on public.role_assignment_history for select to authenticated
    using (false);

revoke insert, update, delete on public.platform_roles from authenticated;
revoke insert, update, delete on public.permissions from authenticated;
revoke insert, update, delete on public.role_permissions from authenticated;
revoke insert, update, delete on public.user_role_assignments from authenticated;
revoke insert, update, delete on public.user_status_history from authenticated;
revoke insert, update, delete on public.role_assignment_history from authenticated;

grant select on public.platform_roles to authenticated;
grant select on public.permissions to authenticated;
grant select on public.role_permissions to authenticated;
grant select on public.user_role_assignments to authenticated;
-- historiales: solo vía RPC (policies using false); sin grant select necesario
revoke all on public.user_status_history from authenticated;
revoke all on public.role_assignment_history from authenticated;

-- Tras onboarding, asegurar rol USER (best-effort; se llama desde app o trigger opcional)
comment on function public.ensure_default_user_role(uuid) is
    'Asigna USER si el usuario no tiene roles activos. Solo service/RPC.';

create or replace function public.ensure_my_default_user_role()
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        return false;
    end if;
    perform public.ensure_default_user_role(uid);
    return true;
end;
$$;

revoke all on function public.ensure_my_default_user_role() from public;
grant execute on function public.ensure_my_default_user_role() to authenticated;
