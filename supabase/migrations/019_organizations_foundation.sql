-- M03 Etapa 3 — Organizations foundation (tables, RLS, RPCs, storage).
-- Depends on: 014–018 (in repo; remote staging validation still PENDING).
-- Next number after 018. Do not edit prior migrations.
-- NOT deployed to remote in this stage unless separately validated.
-- No AccountType auto-migration. FOSTER_HOME is personal capacity (not org type).

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------

create extension if not exists citext with schema extensions;
-- fallback si extensions schema no disponible en algunos entornos
do $$
begin
  create extension if not exists citext;
exception when others then
  null;
end $$;

-- ---------------------------------------------------------------------------
-- Catálogo: roles y permisos internos de organización
-- ---------------------------------------------------------------------------

create table if not exists public.organization_roles (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    description text null,
    is_system boolean not null default true,
    created_at timestamptz not null default timezone('utc', now()),
    constraint organization_roles_code_upper check (code = upper(code))
);

create table if not exists public.organization_permissions (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    description text null,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.organization_role_permissions (
    role_id uuid not null references public.organization_roles (id) on delete cascade,
    permission_id uuid not null references public.organization_permissions (id) on delete cascade,
    primary key (role_id, permission_id)
);

-- ---------------------------------------------------------------------------
-- organizations
-- ---------------------------------------------------------------------------

create table if not exists public.organizations (
    id uuid primary key default gen_random_uuid(),
    slug citext not null unique,
    legal_name text null,
    display_name text not null,
    type text not null,
    other_type_description text null,
    description text null,
    status text not null default 'DRAFT',
    verification_status text not null default 'NOT_REQUESTED',
    country_code text null,
    province text null,
    city text null,
    contact_email text null,
    contact_phone text null,
    contact_email_public boolean not null default false,
    contact_phone_public boolean not null default false,
    logo_path text null,
    cover_path text null,
    created_by uuid not null references auth.users (id),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint organizations_type_allowed check (
        type in (
            'SHELTER', 'RESCUE_GROUP', 'NGO', 'VETERINARY_CLINIC',
            'PET_SHOP', 'TRAINING_CENTER', 'WALKER_AGENCY', 'OTHER'
        )
    ),
    constraint organizations_other_type_description check (
        (type <> 'OTHER' and (other_type_description is null or length(trim(other_type_description)) = 0))
        or (type = 'OTHER' and other_type_description is not null and length(trim(other_type_description)) > 0)
    ),
    constraint organizations_status_allowed check (
        status in ('DRAFT', 'ACTIVE', 'RESTRICTED', 'SUSPENDED', 'CLOSED', 'REJECTED')
    ),
    constraint organizations_verification_allowed check (
        verification_status in ('NOT_REQUESTED', 'PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')
    ),
    constraint organizations_slug_format check (
        length(slug::text) between 3 and 50
        and slug::text ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'
        and slug::text !~ '--'
    ),
    constraint organizations_contact_email_public_requires_value check (
        contact_email_public = false or contact_email is not null
    ),
    constraint organizations_contact_phone_public_requires_value check (
        contact_phone_public = false or contact_phone is not null
    ),
    constraint organizations_country_code_format check (
        country_code is null or country_code ~ '^[A-Z]{2}$'
    ),
    constraint organizations_display_name_len check (
        char_length(trim(display_name)) between 2 and 120
    )
);

create index if not exists organizations_status_idx
    on public.organizations (status);

create index if not exists organizations_type_idx
    on public.organizations (type);

create index if not exists organizations_created_by_idx
    on public.organizations (created_by);

-- ---------------------------------------------------------------------------
-- organization_memberships
-- ---------------------------------------------------------------------------

create table if not exists public.organization_memberships (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    role_code text not null,
    status text not null,
    invited_by uuid null references auth.users (id),
    joined_at timestamptz null,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint organization_memberships_role_allowed check (
        role_code in ('OWNER', 'ADMIN', 'MANAGER', 'MEMBER', 'VIEWER')
    ),
    constraint organization_memberships_status_allowed check (
        status in ('ACTIVE', 'INVITED', 'SUSPENDED', 'LEFT', 'REMOVED')
    )
);

create unique index if not exists organization_memberships_active_uniq
    on public.organization_memberships (organization_id, user_id)
    where status = 'ACTIVE';

create index if not exists organization_memberships_user_idx
    on public.organization_memberships (user_id)
    where status = 'ACTIVE';

create index if not exists organization_memberships_org_idx
    on public.organization_memberships (organization_id)
    where status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- Historial y auditoría
-- ---------------------------------------------------------------------------

create table if not exists public.organization_status_history (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    previous_status text null,
    new_status text not null,
    reason_code text not null,
    note text null,
    changed_by uuid not null references auth.users (id),
    changed_at timestamptz not null default timezone('utc', now()),
    request_id text null,
    constraint organization_status_history_status_allowed check (
        new_status in ('DRAFT', 'ACTIVE', 'RESTRICTED', 'SUSPENDED', 'CLOSED', 'REJECTED')
        and (
            previous_status is null
            or previous_status in ('DRAFT', 'ACTIVE', 'RESTRICTED', 'SUSPENDED', 'CLOSED', 'REJECTED')
        )
    ),
    constraint organization_status_history_note_len check (
        note is null or char_length(note) <= 280
    )
);

create index if not exists organization_status_history_org_idx
    on public.organization_status_history (organization_id, changed_at desc);

create table if not exists public.organization_audit_log (
    id uuid primary key default gen_random_uuid(),
    actor_user_id uuid not null references auth.users (id),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    action text not null,
    previous_value jsonb null,
    new_value jsonb null,
    reason_code text not null,
    note text null,
    created_at timestamptz not null default timezone('utc', now()),
    request_id text null,
    constraint organization_audit_log_action_allowed check (
        action in (
            'CREATE', 'UPDATE', 'REQUEST_VERIFICATION',
            'LINK_RESOURCE', 'UNLINK_RESOURCE', 'STATUS_CHANGE', 'OTHER'
        )
    ),
    constraint organization_audit_log_note_len check (
        note is null or char_length(note) <= 280
    )
);

create index if not exists organization_audit_log_org_idx
    on public.organization_audit_log (organization_id, created_at desc);

-- ---------------------------------------------------------------------------
-- Vinculación legacy (sin AccountType)
-- ---------------------------------------------------------------------------

create table if not exists public.organization_resource_links (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    resource_type text not null,
    resource_id uuid not null,
    linked_by uuid not null references auth.users (id),
    created_at timestamptz not null default timezone('utc', now()),
    constraint organization_resource_links_type_allowed check (
        resource_type in ('SHELTER_LISTING', 'SERVICE_PROFILE')
    )
);

create unique index if not exists organization_resource_links_resource_uniq
    on public.organization_resource_links (resource_type, resource_id);

create index if not exists organization_resource_links_org_idx
    on public.organization_resource_links (organization_id);

-- ---------------------------------------------------------------------------
-- Seeds idempotentes (matriz = OrganizationRolePermissionMatrix Android)
-- ---------------------------------------------------------------------------

insert into public.organization_roles (code, name, description) values
    ('OWNER', 'Propietario', 'Control total de la organización'),
    ('ADMIN', 'Administrador', 'Administración sin cierre'),
    ('MANAGER', 'Gestor', 'Operación y publicación'),
    ('MEMBER', 'Miembro', 'Lectura privada'),
    ('VIEWER', 'Observador', 'Solo lectura básica')
on conflict (code) do nothing;

insert into public.organization_permissions (code, description) values
    ('organization.view', 'Ver organización'),
    ('organization.update', 'Actualizar perfil institucional'),
    ('organization.view_private', 'Ver datos privados de la organización'),
    ('organization.manage_members', 'Gestionar miembros'),
    ('organization.invite_members', 'Invitar miembros'),
    ('organization.remove_members', 'Remover miembros'),
    ('organization.manage_roles', 'Gestionar roles internos'),
    ('organization.manage_branches', 'Gestionar sucursales'),
    ('organization.publish', 'Publicar organización'),
    ('organization.request_verification', 'Solicitar verificación'),
    ('organization.close', 'Cerrar organización')
on conflict (code) do nothing;

-- VIEWER: view
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'VIEWER'
  and p.code in ('organization.view')
on conflict do nothing;

-- MEMBER: VIEWER + view_private
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code in ('organization.view', 'organization.view_private')
on conflict do nothing;

-- MANAGER: MEMBER + update, manage_branches, publish, request_verification
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MANAGER'
  and p.code in (
    'organization.view',
    'organization.view_private',
    'organization.update',
    'organization.manage_branches',
    'organization.publish',
    'organization.request_verification'
  )
on conflict do nothing;

-- ADMIN: MANAGER + manage_members, invite_members, remove_members, manage_roles
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'ADMIN'
  and p.code in (
    'organization.view',
    'organization.view_private',
    'organization.update',
    'organization.manage_branches',
    'organization.publish',
    'organization.request_verification',
    'organization.manage_members',
    'organization.invite_members',
    'organization.remove_members',
    'organization.manage_roles'
  )
on conflict do nothing;

-- OWNER: ADMIN + close
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'OWNER'
  and p.code in (
    'organization.view',
    'organization.view_private',
    'organization.update',
    'organization.manage_branches',
    'organization.publish',
    'organization.request_verification',
    'organization.manage_members',
    'organization.invite_members',
    'organization.remove_members',
    'organization.manage_roles',
    'organization.close'
  )
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Reason codes (allowlist, estilo M02)
-- ---------------------------------------------------------------------------

create or replace function public.is_valid_org_audit_reason(p_code text)
returns boolean
language sql
immutable
as $$
    select lower(trim(coalesce(p_code, ''))) in (
        'org_created', 'org_updated', 'verification_requested',
        'resource_linked', 'resource_unlinked', 'status_changed',
        'ops_review', 'manual_admin', 'other'
    );
$$;

-- ---------------------------------------------------------------------------
-- Helpers (SECURITY DEFINER, search_path = public)
-- ---------------------------------------------------------------------------

create or replace function public.org_is_platform_account_allowed(p_uid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.users u
        where u.id = p_uid
          and u.account_status in ('ACTIVE', 'RESTRICTED')
    );
$$;

revoke all on function public.org_is_platform_account_allowed(uuid) from public;
grant execute on function public.org_is_platform_account_allowed(uuid) to authenticated;

create or replace function public.org_membership_is_active(p_organization_id uuid, p_uid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.organization_memberships m
        where m.organization_id = p_organization_id
          and m.user_id = p_uid
          and m.status = 'ACTIVE'
    );
$$;

revoke all on function public.org_membership_is_active(uuid, uuid) from public;
grant execute on function public.org_membership_is_active(uuid, uuid) to authenticated;

-- Evita recursión RLS al consultar membresía desde políticas.
create or replace function public.is_org_member(p_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.org_membership_is_active(p_organization_id, auth.uid());
$$;

revoke all on function public.is_org_member(uuid) from public;
grant execute on function public.is_org_member(uuid) to authenticated;

create or replace function public.has_org_permission(
    p_organization_id uuid,
    p_permission_code text
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    uid uuid := auth.uid();
    org_status text;
    perm text := lower(trim(coalesce(p_permission_code, '')));
    role text;
begin
    if uid is null or p_organization_id is null or perm = '' then
        return false;
    end if;

    -- SUSPENDED / BANNED (y ausente) deniegan todo.
    if not public.org_is_platform_account_allowed(uid) then
        return false;
    end if;

    select o.status into org_status
    from public.organizations o
    where o.id = p_organization_id;

    if org_status is null then
        return false;
    end if;

    -- Org bloqueada: solo organization.view para miembros activos.
    if org_status in ('SUSPENDED', 'CLOSED', 'REJECTED') then
        if perm <> 'organization.view' then
            return false;
        end if;
    end if;

    if not public.org_membership_is_active(p_organization_id, uid) then
        return false;
    end if;

    select m.role_code into role
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = uid
      and m.status = 'ACTIVE'
    limit 1;

    if role is null then
        return false;
    end if;

    return exists (
        select 1
        from public.organization_roles r
        join public.organization_role_permissions rp on rp.role_id = r.id
        join public.organization_permissions p on p.id = rp.permission_id
        where r.code = role
          and p.code = perm
    );
exception
    when others then
        return false;
end;
$$;

revoke all on function public.has_org_permission(uuid, text) from public;
grant execute on function public.has_org_permission(uuid, text) to authenticated;

create or replace function public.get_my_org_permissions(p_organization_id uuid)
returns text[]
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    uid uuid := auth.uid();
    org_status text;
    role text;
    result text[];
begin
    if uid is null or p_organization_id is null then
        return array[]::text[];
    end if;

    if not public.org_is_platform_account_allowed(uid) then
        return array[]::text[];
    end if;

    if not public.org_membership_is_active(p_organization_id, uid) then
        return array[]::text[];
    end if;

    select o.status into org_status
    from public.organizations o
    where o.id = p_organization_id;

    if org_status is null then
        return array[]::text[];
    end if;

    select m.role_code into role
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = uid
      and m.status = 'ACTIVE'
    limit 1;

    if role is null then
        return array[]::text[];
    end if;

    select coalesce(array_agg(p.code order by p.code), array[]::text[])
    into result
    from public.organization_roles r
    join public.organization_role_permissions rp on rp.role_id = r.id
    join public.organization_permissions p on p.id = rp.permission_id
    where r.code = role;

    -- Org bloqueada: solo view.
    if org_status in ('SUSPENDED', 'CLOSED', 'REJECTED') then
        select coalesce(array_agg(x order by x), array[]::text[])
        into result
        from unnest(coalesce(result, array[]::text[])) as x
        where x = 'organization.view';
    end if;

    return coalesce(result, array[]::text[]);
exception
    when others then
        return array[]::text[];
end;
$$;

revoke all on function public.get_my_org_permissions(uuid) from public;
grant execute on function public.get_my_org_permissions(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Proyección pública allowlist (sin PII privada)
-- ---------------------------------------------------------------------------

create or replace function public.organization_public_json(o public.organizations)
returns jsonb
language sql
stable
as $$
    select jsonb_build_object(
        'id', o.id,
        'slug', o.slug::text,
        'display_name', o.display_name,
        'type', o.type,
        'description', o.description,
        'city', o.city,
        'province', o.province,
        'country_code', o.country_code,
        'status', o.status,
        'verification_status', o.verification_status,
        'logo_path', o.logo_path,
        'cover_path', o.cover_path,
        'contact_email', case when o.contact_email_public then o.contact_email else null end,
        'contact_phone', case when o.contact_phone_public then o.contact_phone else null end
    );
$$;

-- ---------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------

create or replace function public.create_organization(
    p_display_name text,
    p_slug text,
    p_type text,
    p_other_type_description text default null,
    p_legal_name text default null,
    p_description text default null,
    p_country_code text default null,
    p_province text default null,
    p_city text default null,
    p_contact_email text default null,
    p_contact_phone text default null
)
returns public.organizations
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    acct text;
    v_slug text := lower(trim(coalesce(p_slug, '')));
    v_type text := upper(trim(coalesce(p_type, '')));
    v_display text := trim(coalesce(p_display_name, ''));
    v_other text := nullif(trim(coalesce(p_other_type_description, '')), '');
    v_country text := nullif(upper(trim(coalesce(p_country_code, ''))), '');
    new_org public.organizations;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    select u.account_status into acct from public.users u where u.id = actor;
    if acct is null or acct <> 'ACTIVE' then
        raise exception 'ACCOUNT_NOT_ALLOWED';
    end if;

    if char_length(v_display) < 2 or char_length(v_display) > 120 then
        raise exception 'DISPLAY_NAME_INVALID';
    end if;

    if length(v_slug) < 3 or length(v_slug) > 50
       or v_slug !~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'
       or v_slug ~ '--' then
        raise exception 'SLUG_INVALID';
    end if;

    if v_type not in (
        'SHELTER', 'RESCUE_GROUP', 'NGO', 'VETERINARY_CLINIC',
        'PET_SHOP', 'TRAINING_CENTER', 'WALKER_AGENCY', 'OTHER'
    ) then
        raise exception 'TYPE_INVALID';
    end if;

    if v_type = 'OTHER' and v_other is null then
        raise exception 'OTHER_TYPE_DESCRIPTION_REQUIRED';
    end if;

    if v_type <> 'OTHER' then
        v_other := null;
    end if;

    if v_country is not null and v_country !~ '^[A-Z]{2}$' then
        raise exception 'COUNTRY_CODE_INVALID';
    end if;

    insert into public.organizations (
        slug, legal_name, display_name, type, other_type_description, description,
        status, verification_status,
        country_code, province, city, contact_email, contact_phone,
        contact_email_public, contact_phone_public,
        created_by
    ) values (
        v_slug,
        nullif(trim(coalesce(p_legal_name, '')), ''),
        v_display,
        v_type,
        v_other,
        nullif(trim(coalesce(p_description, '')), ''),
        'DRAFT',
        'NOT_REQUESTED',
        v_country,
        nullif(trim(coalesce(p_province, '')), ''),
        nullif(trim(coalesce(p_city, '')), ''),
        nullif(trim(coalesce(p_contact_email, '')), ''),
        nullif(trim(coalesce(p_contact_phone, '')), ''),
        false,
        false,
        actor
    )
    returning * into new_org;

    insert into public.organization_memberships (
        organization_id, user_id, role_code, status, invited_by, joined_at
    ) values (
        new_org.id, actor, 'OWNER', 'ACTIVE', null, timezone('utc', now())
    );

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        new_org.id,
        'CREATE',
        null,
        jsonb_build_object(
            'id', new_org.id,
            'slug', new_org.slug::text,
            'display_name', new_org.display_name,
            'type', new_org.type,
            'status', new_org.status
        ),
        'org_created',
        null,
        null
    );

    return new_org;
end;
$$;

revoke all on function public.create_organization(
    text, text, text, text, text, text, text, text, text, text, text
) from public;
grant execute on function public.create_organization(
    text, text, text, text, text, text, text, text, text, text, text
) to authenticated;

create or replace function public.update_my_organization(
    p_organization_id uuid,
    p_display_name text default null,
    p_legal_name text default null,
    p_description text default null,
    p_country_code text default null,
    p_province text default null,
    p_city text default null,
    p_contact_email text default null,
    p_contact_phone text default null,
    p_contact_email_public boolean default null,
    p_contact_phone_public boolean default null,
    p_logo_path text default null,
    p_cover_path text default null
)
returns public.organizations
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    prev public.organizations;
    updated public.organizations;
    v_country text;
    v_email text;
    v_phone text;
    v_email_public boolean;
    v_phone_public boolean;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.update') then
        raise exception 'FORBIDDEN';
    end if;

    select * into prev from public.organizations where id = p_organization_id;
    if prev.id is null then
        raise exception 'ORGANIZATION_NOT_FOUND';
    end if;

    -- No permite cambiar status ni verification desde el cliente.
    if p_country_code is not null then
        v_country := nullif(upper(trim(p_country_code)), '');
        if v_country is not null and v_country !~ '^[A-Z]{2}$' then
            raise exception 'COUNTRY_CODE_INVALID';
        end if;
    else
        v_country := prev.country_code;
    end if;

    v_email := case
        when p_contact_email is null then prev.contact_email
        else nullif(trim(p_contact_email), '')
    end;
    v_phone := case
        when p_contact_phone is null then prev.contact_phone
        else nullif(trim(p_contact_phone), '')
    end;
    v_email_public := coalesce(p_contact_email_public, prev.contact_email_public);
    v_phone_public := coalesce(p_contact_phone_public, prev.contact_phone_public);

    if v_email_public and v_email is null then
        raise exception 'CONTACT_EMAIL_REQUIRED_FOR_PUBLIC';
    end if;
    if v_phone_public and v_phone is null then
        raise exception 'CONTACT_PHONE_REQUIRED_FOR_PUBLIC';
    end if;

    if p_display_name is not null then
        if char_length(trim(p_display_name)) < 2 or char_length(trim(p_display_name)) > 120 then
            raise exception 'DISPLAY_NAME_INVALID';
        end if;
    end if;

    update public.organizations
    set
        display_name = case
            when p_display_name is null then display_name
            else trim(p_display_name)
        end,
        legal_name = case
            when p_legal_name is null then legal_name
            else nullif(trim(p_legal_name), '')
        end,
        description = case
            when p_description is null then description
            else nullif(trim(p_description), '')
        end,
        country_code = v_country,
        province = case
            when p_province is null then province
            else nullif(trim(p_province), '')
        end,
        city = case
            when p_city is null then city
            else nullif(trim(p_city), '')
        end,
        contact_email = v_email,
        contact_phone = v_phone,
        contact_email_public = v_email_public,
        contact_phone_public = v_phone_public,
        logo_path = case
            when p_logo_path is null then logo_path
            else nullif(trim(p_logo_path), '')
        end,
        cover_path = case
            when p_cover_path is null then cover_path
            else nullif(trim(p_cover_path), '')
        end,
        updated_at = timezone('utc', now())
    where id = p_organization_id
    returning * into updated;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        p_organization_id,
        'UPDATE',
        jsonb_build_object(
            'display_name', prev.display_name,
            'legal_name', prev.legal_name,
            'description', prev.description,
            'country_code', prev.country_code,
            'province', prev.province,
            'city', prev.city,
            'contact_email_public', prev.contact_email_public,
            'contact_phone_public', prev.contact_phone_public,
            'logo_path', prev.logo_path,
            'cover_path', prev.cover_path
        ),
        jsonb_build_object(
            'display_name', updated.display_name,
            'legal_name', updated.legal_name,
            'description', updated.description,
            'country_code', updated.country_code,
            'province', updated.province,
            'city', updated.city,
            'contact_email_public', updated.contact_email_public,
            'contact_phone_public', updated.contact_phone_public,
            'logo_path', updated.logo_path,
            'cover_path', updated.cover_path
        ),
        'org_updated',
        null,
        null
    );

    return updated;
end;
$$;

revoke all on function public.update_my_organization(
    uuid, text, text, text, text, text, text, text, text, boolean, boolean, text, text
) from public;
grant execute on function public.update_my_organization(
    uuid, text, text, text, text, text, text, text, text, boolean, boolean, text, text
) to authenticated;

create or replace function public.get_my_organizations()
returns setof public.organizations
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if not public.org_is_platform_account_allowed(uid) then
        return;
    end if;

    return query
    select o.*
    from public.organizations o
    join public.organization_memberships m
      on m.organization_id = o.id
    where m.user_id = uid
      and m.status = 'ACTIVE'
    order by o.display_name, o.id;
end;
$$;

revoke all on function public.get_my_organizations() from public;
grant execute on function public.get_my_organizations() to authenticated;

create or replace function public.get_public_organization_by_slug(p_slug text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_slug text := lower(trim(coalesce(p_slug, '')));
    org public.organizations;
begin
    if length(v_slug) < 3 then
        return null;
    end if;

    select * into org
    from public.organizations o
    where o.slug = v_slug
      and o.status in ('ACTIVE', 'RESTRICTED')
    limit 1;

    if org.id is null then
        return null;
    end if;

    return public.organization_public_json(org);
end;
$$;

revoke all on function public.get_public_organization_by_slug(text) from public;
grant execute on function public.get_public_organization_by_slug(text) to authenticated;
grant execute on function public.get_public_organization_by_slug(text) to anon;

create or replace function public.search_public_organizations(
    p_query text,
    p_type text default null,
    p_city text default null,
    p_limit int default 20
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    q text := lower(trim(coalesce(p_query, '')));
    v_type text := nullif(upper(trim(coalesce(p_type, ''))), '');
    v_city text := nullif(trim(coalesce(p_city, '')), '');
    lim int := least(greatest(coalesce(p_limit, 20), 1), 50);
    payload jsonb;
begin
    if char_length(q) < 2 then
        return '[]'::jsonb;
    end if;

    select coalesce(jsonb_agg(public.organization_public_json(t) order by t.display_name, t.id), '[]'::jsonb)
    into payload
    from (
        select o.*
        from public.organizations o
        where o.status in ('ACTIVE', 'RESTRICTED')
          and (
            o.display_name ilike '%' || q || '%'
            or o.slug::text ilike '%' || q || '%'
            or coalesce(o.description, '') ilike '%' || q || '%'
          )
          and (v_type is null or o.type = v_type)
          and (v_city is null or o.city ilike v_city)
        order by o.display_name, o.id
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.search_public_organizations(text, text, text, int) from public;
grant execute on function public.search_public_organizations(text, text, text, int) to authenticated;
grant execute on function public.search_public_organizations(text, text, text, int) to anon;

create or replace function public.request_organization_verification(p_organization_id uuid)
returns public.organizations
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    prev public.organizations;
    updated public.organizations;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.request_verification') then
        raise exception 'FORBIDDEN';
    end if;

    select * into prev from public.organizations where id = p_organization_id;
    if prev.id is null then
        raise exception 'ORGANIZATION_NOT_FOUND';
    end if;

    if prev.verification_status not in ('NOT_REQUESTED', 'REJECTED', 'EXPIRED') then
        raise exception 'VERIFICATION_NOT_REQUESTABLE';
    end if;

    -- Nunca auto-VERIFIED.
    update public.organizations
    set verification_status = 'PENDING',
        updated_at = timezone('utc', now())
    where id = p_organization_id
    returning * into updated;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        p_organization_id,
        'REQUEST_VERIFICATION',
        jsonb_build_object('verification_status', prev.verification_status),
        jsonb_build_object('verification_status', updated.verification_status),
        'verification_requested',
        null,
        null
    );

    return updated;
end;
$$;

revoke all on function public.request_organization_verification(uuid) from public;
grant execute on function public.request_organization_verification(uuid) to authenticated;

create or replace function public.link_organization_resource(
    p_organization_id uuid,
    p_resource_type text,
    p_resource_id uuid
)
returns public.organization_resource_links
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    v_type text := upper(trim(coalesce(p_resource_type, '')));
    new_link public.organization_resource_links;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null or p_resource_id is null then
        raise exception 'ARGS_REQUIRED';
    end if;
    if v_type not in ('SHELTER_LISTING', 'SERVICE_PROFILE') then
        raise exception 'RESOURCE_TYPE_INVALID';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.update') then
        raise exception 'FORBIDDEN';
    end if;

    if exists (
        select 1
        from public.organization_resource_links l
        where l.resource_type = v_type
          and l.resource_id = p_resource_id
    ) then
        raise exception 'RESOURCE_ALREADY_LINKED';
    end if;

    insert into public.organization_resource_links (
        organization_id, resource_type, resource_id, linked_by
    ) values (
        p_organization_id, v_type, p_resource_id, actor
    )
    returning * into new_link;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        p_organization_id,
        'LINK_RESOURCE',
        null,
        jsonb_build_object(
            'resource_type', v_type,
            'resource_id', p_resource_id,
            'link_id', new_link.id
        ),
        'resource_linked',
        null,
        null
    );

    return new_link;
end;
$$;

revoke all on function public.link_organization_resource(uuid, text, uuid) from public;
grant execute on function public.link_organization_resource(uuid, text, uuid) to authenticated;

create or replace function public.unlink_organization_resource(
    p_organization_id uuid,
    p_resource_type text,
    p_resource_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    v_type text := upper(trim(coalesce(p_resource_type, '')));
    deleted_id uuid;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null or p_resource_id is null then
        raise exception 'ARGS_REQUIRED';
    end if;
    if v_type not in ('SHELTER_LISTING', 'SERVICE_PROFILE') then
        raise exception 'RESOURCE_TYPE_INVALID';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.update') then
        raise exception 'FORBIDDEN';
    end if;

    delete from public.organization_resource_links
    where organization_id = p_organization_id
      and resource_type = v_type
      and resource_id = p_resource_id
    returning id into deleted_id;

    if deleted_id is null then
        raise exception 'RESOURCE_LINK_NOT_FOUND';
    end if;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        p_organization_id,
        'UNLINK_RESOURCE',
        jsonb_build_object(
            'resource_type', v_type,
            'resource_id', p_resource_id,
            'link_id', deleted_id
        ),
        null,
        'resource_unlinked',
        null,
        null
    );

    return true;
end;
$$;

revoke all on function public.unlink_organization_resource(uuid, text, uuid) from public;
grant execute on function public.unlink_organization_resource(uuid, text, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.organization_roles enable row level security;
alter table public.organization_permissions enable row level security;
alter table public.organization_role_permissions enable row level security;
alter table public.organizations enable row level security;
alter table public.organization_memberships enable row level security;
alter table public.organization_status_history enable row level security;
alter table public.organization_audit_log enable row level security;
alter table public.organization_resource_links enable row level security;

-- Catálogo: SELECT autenticado; sin escritura
drop policy if exists organization_roles_select on public.organization_roles;
create policy organization_roles_select
    on public.organization_roles for select to authenticated
    using (true);

drop policy if exists organization_permissions_select on public.organization_permissions;
create policy organization_permissions_select
    on public.organization_permissions for select to authenticated
    using (true);

drop policy if exists organization_role_permissions_select on public.organization_role_permissions;
create policy organization_role_permissions_select
    on public.organization_role_permissions for select to authenticated
    using (true);

-- organizations: SELECT solo membresía activa; escritura solo vía RPC
drop policy if exists organizations_select_member on public.organizations;
create policy organizations_select_member
    on public.organizations for select to authenticated
    using (public.is_org_member(id));

-- memberships: propias o view_private
drop policy if exists organization_memberships_select on public.organization_memberships;
create policy organization_memberships_select
    on public.organization_memberships for select to authenticated
    using (
        user_id = auth.uid()
        or public.has_org_permission(organization_id, 'organization.view_private')
    );

-- historial / audit: view_private
drop policy if exists organization_status_history_select on public.organization_status_history;
create policy organization_status_history_select
    on public.organization_status_history for select to authenticated
    using (public.has_org_permission(organization_id, 'organization.view_private'));

drop policy if exists organization_audit_log_select on public.organization_audit_log;
create policy organization_audit_log_select
    on public.organization_audit_log for select to authenticated
    using (public.has_org_permission(organization_id, 'organization.view_private'));

-- resource links: SELECT si membresía activa; escritura solo RPC
drop policy if exists organization_resource_links_select on public.organization_resource_links;
create policy organization_resource_links_select
    on public.organization_resource_links for select to authenticated
    using (public.is_org_member(organization_id));

revoke insert, update, delete on public.organization_roles from authenticated;
revoke insert, update, delete on public.organization_permissions from authenticated;
revoke insert, update, delete on public.organization_role_permissions from authenticated;
revoke insert, update, delete on public.organizations from authenticated;
revoke insert, update, delete on public.organization_memberships from authenticated;
revoke insert, update, delete on public.organization_status_history from authenticated;
revoke insert, update, delete on public.organization_audit_log from authenticated;
revoke insert, update, delete on public.organization_resource_links from authenticated;

grant select on public.organization_roles to authenticated;
grant select on public.organization_permissions to authenticated;
grant select on public.organization_role_permissions to authenticated;
grant select on public.organizations to authenticated;
grant select on public.organization_memberships to authenticated;
grant select on public.organization_status_history to authenticated;
grant select on public.organization_audit_log to authenticated;
grant select on public.organization_resource_links to authenticated;

-- ---------------------------------------------------------------------------
-- Storage: organization-media (privado, 5MB, jpeg/png/webp)
-- Paths: organizations/{orgId}/logo|{cover}/{file}
-- ---------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'organization-media',
    'organization-media',
    false,
    5242880,
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create or replace function public.storage_org_media_path_valid(object_name text)
returns boolean
language sql
immutable
as $$
    select
        object_name is not null
        and object_name not like '%..%'
        and object_name ~* (
            '^organizations/'
            || '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
            || '(logo|cover)/'
            || '[^/]+$'
        );
$$;

create or replace function public.storage_org_media_org_id(object_name text)
returns uuid
language plpgsql
stable
as $$
declare
    part text;
begin
    if not public.storage_org_media_path_valid(object_name) then
        return null;
    end if;
    part := split_part(object_name, '/', 2);
    begin
        return part::uuid;
    exception
        when others then
            return null;
    end;
end;
$$;

create or replace function public.storage_org_media_can_write(object_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    org_id uuid;
begin
    if auth.uid() is null then
        return false;
    end if;
    if not public.storage_org_media_path_valid(object_name) then
        return false;
    end if;
    org_id := public.storage_org_media_org_id(object_name);
    if org_id is null then
        return false;
    end if;
    return public.has_org_permission(org_id, 'organization.update');
exception
    when others then
        return false;
end;
$$;

revoke all on function public.storage_org_media_path_valid(text) from public;
grant execute on function public.storage_org_media_path_valid(text) to authenticated;

revoke all on function public.storage_org_media_org_id(text) from public;
grant execute on function public.storage_org_media_org_id(text) to authenticated;

revoke all on function public.storage_org_media_can_write(text) from public;
grant execute on function public.storage_org_media_can_write(text) to authenticated;

drop policy if exists organization_media_select on storage.objects;
create policy organization_media_select
    on storage.objects for select to authenticated
    using (
        bucket_id = 'organization-media'
        and public.storage_org_media_path_valid(name)
        and public.is_org_member(public.storage_org_media_org_id(name))
    );

drop policy if exists organization_media_insert on storage.objects;
create policy organization_media_insert
    on storage.objects for insert to authenticated
    with check (
        bucket_id = 'organization-media'
        and public.storage_org_media_can_write(name)
    );

drop policy if exists organization_media_update on storage.objects;
create policy organization_media_update
    on storage.objects for update to authenticated
    using (
        bucket_id = 'organization-media'
        and public.storage_org_media_can_write(name)
    )
    with check (
        bucket_id = 'organization-media'
        and public.storage_org_media_can_write(name)
    );

drop policy if exists organization_media_delete on storage.objects;
create policy organization_media_delete
    on storage.objects for delete to authenticated
    using (
        bucket_id = 'organization-media'
        and public.storage_org_media_can_write(name)
    );

comment on table public.organizations is
    'M03 Etapa 3 — Entidad organización. Sin auto-creación desde AccountType.';
comment on function public.has_org_permission(uuid, text) is
    'Deny-by-default: cuenta M02, estado org, membresía ACTIVE y matriz de rol.';
comment on function public.create_organization(text, text, text, text, text, text, text, text, text, text, text) is
    'Crea DRAFT + membresía OWNER ACTIVE + audit. Sin listing legacy.';
