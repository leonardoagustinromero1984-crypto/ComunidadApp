-- M03 Etapa 4 — Invitaciones, equipos, sucursales y contexto organizacional.
-- Depends on: 019_organizations_foundation.sql (014–019 in repo; remote staging PENDING).
-- Do not edit prior migrations.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Extend audit reason codes (allowlist)
-- ---------------------------------------------------------------------------

create or replace function public.is_valid_org_audit_reason(p_code text)
returns boolean
language sql
immutable
as $$
    select lower(trim(coalesce(p_code, ''))) in (
        'org_created', 'org_updated', 'verification_requested',
        'resource_linked', 'resource_unlinked', 'status_changed',
        'invitation_created', 'invitation_accepted', 'invitation_declined',
        'invitation_revoked', 'invitation_expired',
        'member_role_changed', 'member_suspended', 'member_removed', 'member_left',
        'ownership_transferred', 'branch_created', 'branch_updated', 'branch_status_changed',
        'org_closed', 'ops_review', 'manual_admin', 'other'
    );
$$;

-- ---------------------------------------------------------------------------
-- Extend audit action allowlist
-- ---------------------------------------------------------------------------

alter table public.organization_audit_log
    drop constraint if exists organization_audit_log_action_allowed;

alter table public.organization_audit_log
    add constraint organization_audit_log_action_allowed check (
        action in (
            'CREATE', 'UPDATE', 'REQUEST_VERIFICATION',
            'LINK_RESOURCE', 'UNLINK_RESOURCE', 'STATUS_CHANGE',
            'INVITATION_CREATED', 'INVITATION_ACCEPTED', 'INVITATION_DECLINED',
            'INVITATION_REVOKED', 'INVITATION_EXPIRED',
            'MEMBER_JOINED', 'MEMBER_SUSPENDED', 'MEMBER_REMOVED', 'MEMBER_LEFT',
            'MEMBER_ROLE_CHANGED', 'OWNERSHIP_TRANSFERRED',
            'BRANCH_CREATED', 'BRANCH_UPDATED', 'BRANCH_STATUS_CHANGED',
            'ORG_CLOSED', 'OTHER'
        )
    );

-- ---------------------------------------------------------------------------
-- organization_invitations (token hash only)
-- ---------------------------------------------------------------------------

create table if not exists public.organization_invitations (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    invited_email extensions.citext null,
    invited_user_id uuid null references auth.users (id) on delete set null,
    role_code text not null,
    status text not null default 'PENDING',
    token_hash text not null,
    expires_at timestamptz not null,
    created_by uuid not null references auth.users (id),
    accepted_by uuid null references auth.users (id),
    accepted_at timestamptz null,
    revoked_by uuid null references auth.users (id),
    revoked_at timestamptz null,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint organization_invitations_role_allowed check (
        role_code in ('ADMIN', 'MANAGER', 'MEMBER', 'VIEWER')
    ),
    constraint organization_invitations_status_allowed check (
        status in ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')
    ),
    constraint organization_invitations_target_required check (
        invited_email is not null or invited_user_id is not null
    ),
    constraint organization_invitations_no_owner_invite check (
        role_code <> 'OWNER'
    )
);

create index if not exists organization_invitations_org_idx
    on public.organization_invitations (organization_id, status);

create index if not exists organization_invitations_token_hash_idx
    on public.organization_invitations (token_hash)
    where status = 'PENDING';

create index if not exists organization_invitations_target_user_idx
    on public.organization_invitations (invited_user_id)
    where status = 'PENDING';

-- ---------------------------------------------------------------------------
-- organization_branches (sin coordenadas / mapas)
-- ---------------------------------------------------------------------------

create table if not exists public.organization_branches (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    name text not null,
    address_line text null,
    city text null,
    province text null,
    country_code text null,
    postal_code text null,
    contact_phone text null,
    contact_phone_public boolean not null default false,
    opening_hours jsonb null,
    status text not null default 'ACTIVE',
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint organization_branches_name_len check (
        char_length(trim(name)) between 2 and 120
    ),
    constraint organization_branches_status_allowed check (
        status in ('ACTIVE', 'INACTIVE', 'CLOSED')
    ),
    constraint organization_branches_country_code_format check (
        country_code is null or country_code ~ '^[A-Z]{2}$'
    ),
    constraint organization_branches_phone_public_requires_value check (
        contact_phone_public = false or contact_phone is not null
    )
);

create index if not exists organization_branches_org_idx
    on public.organization_branches (organization_id, status);

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

create or replace function public.org_hash_invitation_token(p_token text)
returns text
language sql
immutable
as $$
    select encode(digest(trim(coalesce(p_token, '')), 'sha256'), 'hex');
$$;

create or replace function public.org_count_active_owners(p_organization_id uuid)
returns int
language sql
stable
security definer
set search_path = public
as $$
    select count(*)::int
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.role_code = 'OWNER'
      and m.status = 'ACTIVE';
$$;

revoke all on function public.org_count_active_owners(uuid) from public;
grant execute on function public.org_count_active_owners(uuid) to authenticated;

create or replace function public.org_is_operational(p_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.organizations o
        where o.id = p_organization_id
          and o.status not in ('SUSPENDED', 'CLOSED', 'REJECTED')
    );
$$;

revoke all on function public.org_is_operational(uuid) from public;
grant execute on function public.org_is_operational(uuid) to authenticated;

create or replace function public.org_branch_public_json(b public.organization_branches)
returns jsonb
language sql
stable
as $$
    select jsonb_build_object(
        'id', b.id,
        'organization_id', b.organization_id,
        'name', b.name,
        'address_line', b.address_line,
        'city', b.city,
        'province', b.province,
        'country_code', b.country_code,
        'postal_code', b.postal_code,
        'contact_phone', case when b.contact_phone_public then b.contact_phone else null end,
        'opening_hours', b.opening_hours,
        'status', b.status
    );
$$;

-- ---------------------------------------------------------------------------
-- RPC: invite_organization_member
-- ---------------------------------------------------------------------------

create or replace function public.invite_organization_member(
    p_organization_id uuid,
    p_invited_email text default null,
    p_invited_user_id uuid default null,
    p_role_code text default 'MEMBER',
    p_expires_at timestamptz default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    v_role text := upper(trim(coalesce(p_role_code, '')));
    v_email extensions.citext := nullif(lower(trim(coalesce(p_invited_email, ''))), '')::extensions.citext;
    v_user uuid := p_invited_user_id;
    v_expires timestamptz := coalesce(p_expires_at, timezone('utc', now()) + interval '7 days');
    raw_token text;
    token_h text;
    new_inv public.organization_invitations;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;
    if not public.org_is_operational(p_organization_id) then
        raise exception 'ORGANIZATION_BLOCKED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.invite_members') then
        raise exception 'FORBIDDEN';
    end if;
    if v_email is null and v_user is null then
        raise exception 'INVITE_TARGET_REQUIRED';
    end if;
    if v_role not in ('ADMIN', 'MANAGER', 'MEMBER', 'VIEWER') then
        raise exception 'ROLE_NOT_INVITABLE';
    end if;
    if v_expires <= timezone('utc', now()) then
        raise exception 'EXPIRES_AT_INVALID';
    end if;

    -- Evitar invitar a quien ya es miembro activo.
    if v_user is not null and public.org_membership_is_active(p_organization_id, v_user) then
        raise exception 'ALREADY_MEMBER';
    end if;

    raw_token := encode(gen_random_bytes(32), 'hex');
    token_h := public.org_hash_invitation_token(raw_token);

    insert into public.organization_invitations (
        organization_id, invited_email, invited_user_id, role_code,
        status, token_hash, expires_at, created_by
    ) values (
        p_organization_id, v_email, v_user, v_role,
        'PENDING', token_h, v_expires, actor
    )
    returning * into new_inv;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        p_organization_id,
        'INVITATION_CREATED',
        null,
        jsonb_build_object(
            'invitation_id', new_inv.id,
            'role_code', v_role,
            'has_email', v_email is not null,
            'has_user_id', v_user is not null
        ),
        'invitation_created',
        null,
        null
    );

    return jsonb_build_object(
        'invitation', to_jsonb(new_inv) - 'token_hash',
        'token', raw_token
    );
end;
$$;

revoke all on function public.invite_organization_member(uuid, text, uuid, text, timestamptz) from public;
grant execute on function public.invite_organization_member(uuid, text, uuid, text, timestamptz) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: list_organization_invitations
-- ---------------------------------------------------------------------------

create or replace function public.list_organization_invitations(p_organization_id uuid)
returns setof public.organization_invitations
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.manage_members') then
        raise exception 'FORBIDDEN';
    end if;

    return query
    select i.*
    from public.organization_invitations i
    where i.organization_id = p_organization_id
    order by i.created_at desc;
end;
$$;

revoke all on function public.list_organization_invitations(uuid) from public;
grant execute on function public.list_organization_invitations(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: list_my_pending_invitations
-- ---------------------------------------------------------------------------

create or replace function public.list_my_pending_invitations()
returns setof public.organization_invitations
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    actor_email extensions.citext;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    select u.email::extensions.citext into actor_email
    from auth.users u
    where u.id = actor;

    return query
    select i.*
    from public.organization_invitations i
    where i.status = 'PENDING'
      and i.expires_at > timezone('utc', now())
      and (
        i.invited_user_id = actor
        or (i.invited_user_id is null and i.invited_email is not null and i.invited_email = actor_email)
      )
    order by i.created_at desc;
end;
$$;

revoke all on function public.list_my_pending_invitations() from public;
grant execute on function public.list_my_pending_invitations() to authenticated;

-- ---------------------------------------------------------------------------
-- Internal: resolve invitation by token hash
-- ---------------------------------------------------------------------------

create or replace function public._resolve_invitation_by_token(p_token text)
returns public.organization_invitations
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    token_h text := public.org_hash_invitation_token(p_token);
    inv public.organization_invitations;
begin
    select * into inv
    from public.organization_invitations i
    where i.token_hash = token_h
    limit 1;

    if inv.id is null then
        raise exception 'INVITATION_INVALID';
    end if;

    if inv.status = 'PENDING' and inv.expires_at <= timezone('utc', now()) then
        update public.organization_invitations
        set status = 'EXPIRED', updated_at = timezone('utc', now())
        where id = inv.id and status = 'PENDING';
        raise exception 'INVITATION_EXPIRED';
    end if;

    return inv;
end;
$$;

revoke all on function public._resolve_invitation_by_token(text) from public;

-- ---------------------------------------------------------------------------
-- RPC: accept_organization_invitation
-- ---------------------------------------------------------------------------

create or replace function public.accept_organization_invitation(p_token text)
returns public.organization_memberships
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    inv public.organization_invitations;
    actor_email extensions.citext;
    new_membership public.organization_memberships;
    existing public.organization_memberships;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if not public.org_is_platform_account_allowed(actor) then
        raise exception 'ACCOUNT_BLOCKED';
    end if;
    if coalesce(trim(p_token), '') = '' then
        raise exception 'INVITATION_INVALID';
    end if;

    begin
        inv := public._resolve_invitation_by_token(p_token);
    exception
        when others then
            raise exception 'INVITATION_INVALID';
    end;

    if inv.status <> 'PENDING' then
        raise exception 'INVITATION_INVALID';
    end if;
    if not public.org_is_operational(inv.organization_id) then
        raise exception 'ORGANIZATION_BLOCKED';
    end if;

    select u.email::extensions.citext into actor_email from auth.users u where u.id = actor;

    if inv.invited_user_id is not null and inv.invited_user_id <> actor then
        raise exception 'INVITATION_INVALID';
    end if;
    if inv.invited_user_id is null and (
        inv.invited_email is null or actor_email is null or inv.invited_email <> actor_email
    ) then
        raise exception 'INVITATION_INVALID';
    end if;

    -- Idempotencia: ya miembro activo.
    if public.org_membership_is_active(inv.organization_id, actor) then
        update public.organization_invitations
        set status = 'ACCEPTED',
            accepted_by = actor,
            accepted_at = timezone('utc', now()),
            updated_at = timezone('utc', now())
        where id = inv.id and status = 'PENDING';

        select * into existing
        from public.organization_memberships m
        where m.organization_id = inv.organization_id
          and m.user_id = actor
          and m.status = 'ACTIVE'
        limit 1;

        return existing;
    end if;

    select * into existing
    from public.organization_memberships m
    where m.organization_id = inv.organization_id
      and m.user_id = actor
    order by m.updated_at desc
    limit 1;

    if existing.id is not null then
        update public.organization_memberships
        set role_code = inv.role_code,
            status = 'ACTIVE',
            invited_by = inv.created_by,
            joined_at = coalesce(existing.joined_at, timezone('utc', now())),
            updated_at = timezone('utc', now())
        where id = existing.id
        returning * into new_membership;
    else
        insert into public.organization_memberships (
            organization_id, user_id, role_code, status, invited_by, joined_at
        ) values (
            inv.organization_id, actor, inv.role_code, 'ACTIVE',
            inv.created_by, timezone('utc', now())
        )
        returning * into new_membership;
    end if;

    update public.organization_invitations
    set status = 'ACCEPTED',
        accepted_by = actor,
        accepted_at = timezone('utc', now()),
        updated_at = timezone('utc', now())
    where id = inv.id and status = 'PENDING';

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        inv.organization_id,
        'INVITATION_ACCEPTED',
        jsonb_build_object('invitation_id', inv.id),
        jsonb_build_object('membership_id', new_membership.id, 'role_code', inv.role_code),
        'invitation_accepted',
        null,
        null
    );

    return new_membership;
end;
$$;

revoke all on function public.accept_organization_invitation(text) from public;
grant execute on function public.accept_organization_invitation(text) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: decline_organization_invitation
-- ---------------------------------------------------------------------------

create or replace function public.decline_organization_invitation(p_token text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    inv public.organization_invitations;
    actor_email extensions.citext;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if coalesce(trim(p_token), '') = '' then
        raise exception 'INVITATION_INVALID';
    end if;

    begin
        inv := public._resolve_invitation_by_token(p_token);
    exception
        when others then
            raise exception 'INVITATION_INVALID';
    end;

    if inv.status <> 'PENDING' then
        raise exception 'INVITATION_INVALID';
    end if;

    select u.email::extensions.citext into actor_email from auth.users u where u.id = actor;

    if inv.invited_user_id is not null and inv.invited_user_id <> actor then
        raise exception 'INVITATION_INVALID';
    end if;
    if inv.invited_user_id is null and (
        inv.invited_email is null or actor_email is null or inv.invited_email <> actor_email
    ) then
        raise exception 'INVITATION_INVALID';
    end if;

    update public.organization_invitations
    set status = 'DECLINED', updated_at = timezone('utc', now())
    where id = inv.id and status = 'PENDING';

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        inv.organization_id,
        'INVITATION_DECLINED',
        jsonb_build_object('invitation_id', inv.id),
        null,
        'invitation_declined',
        null,
        null
    );

    return true;
end;
$$;

revoke all on function public.decline_organization_invitation(text) from public;
grant execute on function public.decline_organization_invitation(text) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: revoke_organization_invitation
-- ---------------------------------------------------------------------------

create or replace function public.revoke_organization_invitation(p_invitation_id uuid)
returns public.organization_invitations
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    inv public.organization_invitations;
    updated public.organization_invitations;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_invitation_id is null then
        raise exception 'INVITATION_REQUIRED';
    end if;

    select * into inv from public.organization_invitations where id = p_invitation_id;
    if inv.id is null then
        raise exception 'INVITATION_NOT_FOUND';
    end if;
    if not public.has_org_permission(inv.organization_id, 'organization.manage_members') then
        raise exception 'FORBIDDEN';
    end if;
    if inv.status <> 'PENDING' then
        raise exception 'NOT_PENDING';
    end if;

    update public.organization_invitations
    set status = 'REVOKED',
        revoked_by = actor,
        revoked_at = timezone('utc', now()),
        updated_at = timezone('utc', now())
    where id = p_invitation_id
    returning * into updated;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        inv.organization_id,
        'INVITATION_REVOKED',
        jsonb_build_object('invitation_id', inv.id),
        null,
        'invitation_revoked',
        null,
        null
    );

    return updated;
end;
$$;

revoke all on function public.revoke_organization_invitation(uuid) from public;
grant execute on function public.revoke_organization_invitation(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: list_organization_members
-- ---------------------------------------------------------------------------

create or replace function public.list_organization_members(p_organization_id uuid)
returns setof public.organization_memberships
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.manage_members') then
        raise exception 'FORBIDDEN';
    end if;

    return query
    select m.*
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.status in ('ACTIVE', 'SUSPENDED', 'INVITED')
    order by m.role_code, m.joined_at nulls last, m.created_at;
end;
$$;

revoke all on function public.list_organization_members(uuid) from public;
grant execute on function public.list_organization_members(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Internal: hierarchy guard for member admin
-- ---------------------------------------------------------------------------

create or replace function public._org_can_admin_target_member(
    p_organization_id uuid,
    p_actor uuid,
    p_target_user_id uuid,
    p_new_role text default null
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor_role text;
    target_role text;
    owner_count int;
begin
    select m.role_code into actor_role
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = p_actor
      and m.status = 'ACTIVE';

    select m.role_code into target_role
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = p_target_user_id
      and m.status in ('ACTIVE', 'SUSPENDED');

    if actor_role is null or target_role is null then
        return false;
    end if;

    owner_count := public.org_count_active_owners(p_organization_id);

    -- ADMIN no gestiona OWNER.
    if actor_role = 'ADMIN' and target_role = 'OWNER' then
        return false;
    end if;

    -- Nadie se autoeleva a OWNER (salvo transfer RPC).
    if p_new_role = 'OWNER' and p_actor = p_target_user_id and actor_role <> 'OWNER' then
        return false;
    end if;

    -- ADMIN no asigna OWNER.
    if actor_role = 'ADMIN' and p_new_role = 'OWNER' then
        return false;
    end if;

    -- Último OWNER protegido.
    if target_role = 'OWNER' and owner_count <= 1 then
        if p_new_role is not null and p_new_role <> 'OWNER' then
            return false;
        end if;
        if p_new_role is null then
            return false;
        end if;
    end if;

    -- Jerarquía mínima: OWNER todo; ADMIN gestiona no-OWNER.
    if actor_role = 'OWNER' then
        return true;
    end if;
    if actor_role = 'ADMIN' and target_role <> 'OWNER' then
        return true;
    end if;

    return false;
end;
$$;

revoke all on function public._org_can_admin_target_member(uuid, uuid, uuid, text) from public;

-- ---------------------------------------------------------------------------
-- RPC: change_organization_member_role
-- ---------------------------------------------------------------------------

create or replace function public.change_organization_member_role(
    p_organization_id uuid,
    p_target_user_id uuid,
    p_role_code text,
    p_reason_code text default 'member_role_changed'
)
returns public.organization_memberships
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    v_role text := upper(trim(coalesce(p_role_code, '')));
    prev public.organization_memberships;
    updated public.organization_memberships;
    v_reason text := lower(trim(coalesce(p_reason_code, 'member_role_changed')));
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null or p_target_user_id is null then
        raise exception 'ARGS_REQUIRED';
    end if;
    if not public.org_is_operational(p_organization_id) then
        raise exception 'ORGANIZATION_BLOCKED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.manage_roles') then
        raise exception 'FORBIDDEN';
    end if;
    if v_role not in ('OWNER', 'ADMIN', 'MANAGER', 'MEMBER', 'VIEWER') then
        raise exception 'ROLE_INVALID';
    end if;
    if v_role = 'OWNER' then
        raise exception 'USE_TRANSFER_OWNERSHIP';
    end if;
    if not public.is_valid_org_audit_reason(v_reason) then
        raise exception 'REASON_INVALID';
    end if;
    if not public._org_can_admin_target_member(p_organization_id, actor, p_target_user_id, v_role) then
        raise exception 'FORBIDDEN';
    end if;

    select * into prev
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = p_target_user_id
      and m.status = 'ACTIVE';

    if prev.id is null then
        raise exception 'MEMBER_NOT_FOUND';
    end if;

    update public.organization_memberships
    set role_code = v_role, updated_at = timezone('utc', now())
    where id = prev.id
    returning * into updated;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        p_organization_id,
        'MEMBER_ROLE_CHANGED',
        jsonb_build_object('user_id', p_target_user_id, 'role_code', prev.role_code),
        jsonb_build_object('user_id', p_target_user_id, 'role_code', v_role),
        v_reason,
        null,
        null
    );

    return updated;
end;
$$;

revoke all on function public.change_organization_member_role(uuid, uuid, text, text) from public;
grant execute on function public.change_organization_member_role(uuid, uuid, text, text) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: suspend_organization_member
-- ---------------------------------------------------------------------------

create or replace function public.suspend_organization_member(
    p_organization_id uuid,
    p_target_user_id uuid,
    p_reason_code text default 'member_suspended'
)
returns public.organization_memberships
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    prev public.organization_memberships;
    updated public.organization_memberships;
    v_reason text := lower(trim(coalesce(p_reason_code, 'member_suspended')));
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if not public.org_is_operational(p_organization_id) then
        raise exception 'ORGANIZATION_BLOCKED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.manage_members') then
        raise exception 'FORBIDDEN';
    end if;
    if not public.is_valid_org_audit_reason(v_reason) then
        raise exception 'REASON_INVALID';
    end if;
    if not public._org_can_admin_target_member(p_organization_id, actor, p_target_user_id, null) then
        raise exception 'FORBIDDEN';
    end if;

    select * into prev
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = p_target_user_id
      and m.status = 'ACTIVE';

    if prev.id is null then
        raise exception 'MEMBER_NOT_FOUND';
    end if;

    update public.organization_memberships
    set status = 'SUSPENDED', updated_at = timezone('utc', now())
    where id = prev.id
    returning * into updated;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor, p_organization_id, 'MEMBER_SUSPENDED',
        jsonb_build_object('user_id', p_target_user_id, 'status', prev.status),
        jsonb_build_object('user_id', p_target_user_id, 'status', 'SUSPENDED'),
        v_reason, null, null
    );

    return updated;
end;
$$;

revoke all on function public.suspend_organization_member(uuid, uuid, text) from public;
grant execute on function public.suspend_organization_member(uuid, uuid, text) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: remove_organization_member
-- ---------------------------------------------------------------------------

create or replace function public.remove_organization_member(
    p_organization_id uuid,
    p_target_user_id uuid,
    p_reason_code text default 'member_removed'
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    prev public.organization_memberships;
    v_reason text := lower(trim(coalesce(p_reason_code, 'member_removed')));
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if not public.org_is_operational(p_organization_id) then
        raise exception 'ORGANIZATION_BLOCKED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.remove_members') then
        raise exception 'FORBIDDEN';
    end if;
    if not public.is_valid_org_audit_reason(v_reason) then
        raise exception 'REASON_INVALID';
    end if;
    if not public._org_can_admin_target_member(p_organization_id, actor, p_target_user_id, null) then
        raise exception 'FORBIDDEN';
    end if;

    select * into prev
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = p_target_user_id
      and m.status in ('ACTIVE', 'SUSPENDED');

    if prev.id is null then
        raise exception 'MEMBER_NOT_FOUND';
    end if;

    update public.organization_memberships
    set status = 'REMOVED', updated_at = timezone('utc', now())
    where id = prev.id;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor, p_organization_id, 'MEMBER_REMOVED',
        jsonb_build_object('user_id', p_target_user_id, 'role_code', prev.role_code),
        jsonb_build_object('user_id', p_target_user_id, 'status', 'REMOVED'),
        v_reason, null, null
    );

    return true;
end;
$$;

revoke all on function public.remove_organization_member(uuid, uuid, text) from public;
grant execute on function public.remove_organization_member(uuid, uuid, text) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: leave_organization
-- ---------------------------------------------------------------------------

create or replace function public.leave_organization(p_organization_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    prev public.organization_memberships;
    owner_count int;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;

    select * into prev
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = actor
      and m.status = 'ACTIVE';

    if prev.id is null then
        raise exception 'NOT_A_MEMBER';
    end if;

    owner_count := public.org_count_active_owners(p_organization_id);
    if prev.role_code = 'OWNER' and owner_count <= 1 then
        raise exception 'LAST_OWNER_PROTECTED';
    end if;

    update public.organization_memberships
    set status = 'LEFT', updated_at = timezone('utc', now())
    where id = prev.id;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor, p_organization_id, 'MEMBER_LEFT',
        jsonb_build_object('user_id', actor, 'role_code', prev.role_code),
        jsonb_build_object('user_id', actor, 'status', 'LEFT'),
        'member_left', null, null
    );

    return true;
end;
$$;

revoke all on function public.leave_organization(uuid) from public;
grant execute on function public.leave_organization(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: transfer_organization_ownership
-- ---------------------------------------------------------------------------

create or replace function public.transfer_organization_ownership(
    p_organization_id uuid,
    p_target_user_id uuid,
    p_actor_new_role text default 'ADMIN',
    p_reason_code text default 'ownership_transferred'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    actor_membership public.organization_memberships;
    target_membership public.organization_memberships;
    v_actor_role text := upper(trim(coalesce(p_actor_new_role, 'ADMIN')));
    v_reason text := lower(trim(coalesce(p_reason_code, 'ownership_transferred')));
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null or p_target_user_id is null then
        raise exception 'ARGS_REQUIRED';
    end if;
    if not public.org_is_operational(p_organization_id) then
        raise exception 'ORGANIZATION_BLOCKED';
    end if;
    if not public.is_valid_org_audit_reason(v_reason) then
        raise exception 'REASON_INVALID';
    end if;
    if v_actor_role not in ('ADMIN', 'MANAGER', 'MEMBER', 'VIEWER') then
        raise exception 'ROLE_INVALID';
    end if;

    select * into actor_membership
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = actor
      and m.status = 'ACTIVE';

    if actor_membership.role_code <> 'OWNER' then
        raise exception 'FORBIDDEN';
    end if;

    select * into target_membership
    from public.organization_memberships m
    where m.organization_id = p_organization_id
      and m.user_id = p_target_user_id
      and m.status = 'ACTIVE';

    if target_membership.id is null then
        raise exception 'TARGET_NOT_MEMBER';
    end if;
    if not public.org_is_platform_account_allowed(p_target_user_id) then
        raise exception 'TARGET_BLOCKED';
    end if;

    -- Idempotente: destino ya OWNER.
    if target_membership.role_code = 'OWNER' and actor_membership.role_code <> 'OWNER' then
        return jsonb_build_object('status', 'already_transferred');
    end if;

    update public.organization_memberships
    set role_code = 'OWNER', updated_at = timezone('utc', now())
    where id = target_membership.id;

    update public.organization_memberships
    set role_code = v_actor_role, updated_at = timezone('utc', now())
    where id = actor_membership.id;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor,
        p_organization_id,
        'OWNERSHIP_TRANSFERRED',
        jsonb_build_object(
            'from_user_id', actor,
            'to_user_id', p_target_user_id
        ),
        jsonb_build_object(
            'from_new_role', v_actor_role,
            'to_new_role', 'OWNER'
        ),
        v_reason,
        null,
        null
    );

    return jsonb_build_object(
        'from_user_id', actor,
        'to_user_id', p_target_user_id,
        'from_new_role', v_actor_role,
        'to_new_role', 'OWNER'
    );
end;
$$;

revoke all on function public.transfer_organization_ownership(uuid, uuid, text, text) from public;
grant execute on function public.transfer_organization_ownership(uuid, uuid, text, text) to authenticated;

-- ---------------------------------------------------------------------------
-- RPC: close_organization
-- ---------------------------------------------------------------------------

create or replace function public.close_organization(
    p_organization_id uuid,
    p_reason_code text default 'org_closed'
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
    v_reason text := lower(trim(coalesce(p_reason_code, 'org_closed')));
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.close') then
        raise exception 'FORBIDDEN';
    end if;
    if not public.is_valid_org_audit_reason(v_reason) then
        raise exception 'REASON_INVALID';
    end if;

    select * into prev from public.organizations where id = p_organization_id;
    if prev.id is null then
        raise exception 'ORGANIZATION_NOT_FOUND';
    end if;
    if prev.status = 'CLOSED' then
        return prev;
    end if;

    update public.organizations
    set status = 'CLOSED', updated_at = timezone('utc', now())
    where id = p_organization_id
    returning * into updated;

    -- Revocar invitaciones pendientes.
    update public.organization_invitations
    set status = 'REVOKED',
        revoked_by = actor,
        revoked_at = timezone('utc', now()),
        updated_at = timezone('utc', now())
    where organization_id = p_organization_id
      and status = 'PENDING';

    insert into public.organization_status_history (
        organization_id, previous_status, new_status, reason_code, changed_by
    ) values (
        p_organization_id, prev.status, 'CLOSED', v_reason, actor
    );

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor, p_organization_id, 'ORG_CLOSED',
        jsonb_build_object('status', prev.status),
        jsonb_build_object('status', 'CLOSED'),
        v_reason, null, null
    );

    return updated;
end;
$$;

revoke all on function public.close_organization(uuid, text) from public;
grant execute on function public.close_organization(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------
-- Branch RPCs
-- ---------------------------------------------------------------------------

create or replace function public.create_organization_branch(
    p_organization_id uuid,
    p_name text,
    p_address_line text default null,
    p_city text default null,
    p_province text default null,
    p_country_code text default null,
    p_postal_code text default null,
    p_contact_phone text default null,
    p_contact_phone_public boolean default false,
    p_opening_hours jsonb default null
)
returns public.organization_branches
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    v_name text := trim(coalesce(p_name, ''));
    v_country text := nullif(upper(trim(coalesce(p_country_code, ''))), '');
    new_branch public.organization_branches;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;
    if not public.org_is_operational(p_organization_id) then
        raise exception 'ORGANIZATION_BLOCKED';
    end if;
    if not public.has_org_permission(p_organization_id, 'organization.manage_branches') then
        raise exception 'FORBIDDEN';
    end if;
    if char_length(v_name) < 2 then
        raise exception 'NAME_INVALID';
    end if;
    if p_contact_phone_public and nullif(trim(coalesce(p_contact_phone, '')), '') is null then
        raise exception 'PHONE_REQUIRED_FOR_PUBLIC';
    end if;

    insert into public.organization_branches (
        organization_id, name, address_line, city, province, country_code,
        postal_code, contact_phone, contact_phone_public, opening_hours
    ) values (
        p_organization_id, v_name,
        nullif(trim(coalesce(p_address_line, '')), ''),
        nullif(trim(coalesce(p_city, '')), ''),
        nullif(trim(coalesce(p_province, '')), ''),
        v_country,
        nullif(trim(coalesce(p_postal_code, '')), ''),
        nullif(trim(coalesce(p_contact_phone, '')), ''),
        coalesce(p_contact_phone_public, false),
        p_opening_hours
    )
    returning * into new_branch;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor, p_organization_id, 'BRANCH_CREATED', null,
        jsonb_build_object('branch_id', new_branch.id, 'name', new_branch.name),
        'branch_created', null, null
    );

    return new_branch;
end;
$$;

revoke all on function public.create_organization_branch(uuid, text, text, text, text, text, text, text, boolean, jsonb) from public;
grant execute on function public.create_organization_branch(uuid, text, text, text, text, text, text, text, boolean, jsonb) to authenticated;

create or replace function public.update_organization_branch(
    p_branch_id uuid,
    p_name text default null,
    p_address_line text default null,
    p_city text default null,
    p_province text default null,
    p_country_code text default null,
    p_postal_code text default null,
    p_contact_phone text default null,
    p_contact_phone_public boolean default null,
    p_opening_hours jsonb default null
)
returns public.organization_branches
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    prev public.organization_branches;
    updated public.organization_branches;
    v_country text;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_branch_id is null then
        raise exception 'BRANCH_REQUIRED';
    end if;

    select * into prev from public.organization_branches where id = p_branch_id;
    if prev.id is null then
        raise exception 'BRANCH_NOT_FOUND';
    end if;
    if not public.org_is_operational(prev.organization_id) then
        raise exception 'ORGANIZATION_BLOCKED';
    end if;
    if not public.has_org_permission(prev.organization_id, 'organization.manage_branches') then
        raise exception 'FORBIDDEN';
    end if;

    v_country := case
        when p_country_code is null then prev.country_code
        else nullif(upper(trim(p_country_code)), '')
    end;

    update public.organization_branches
    set
        name = coalesce(nullif(trim(coalesce(p_name, '')), ''), name),
        address_line = coalesce(nullif(trim(coalesce(p_address_line, '')), ''), address_line),
        city = coalesce(nullif(trim(coalesce(p_city, '')), ''), city),
        province = coalesce(nullif(trim(coalesce(p_province, '')), ''), province),
        country_code = v_country,
        postal_code = coalesce(nullif(trim(coalesce(p_postal_code, '')), ''), postal_code),
        contact_phone = coalesce(nullif(trim(coalesce(p_contact_phone, '')), ''), contact_phone),
        contact_phone_public = coalesce(p_contact_phone_public, contact_phone_public),
        opening_hours = coalesce(p_opening_hours, opening_hours),
        updated_at = timezone('utc', now())
    where id = p_branch_id
    returning * into updated;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor, prev.organization_id, 'BRANCH_UPDATED',
        jsonb_build_object('branch_id', prev.id),
        jsonb_build_object('branch_id', updated.id, 'name', updated.name),
        'branch_updated', null, null
    );

    return updated;
end;
$$;

revoke all on function public.update_organization_branch(uuid, text, text, text, text, text, text, text, boolean, jsonb) from public;
grant execute on function public.update_organization_branch(uuid, text, text, text, text, text, text, text, boolean, jsonb) to authenticated;

create or replace function public.set_organization_branch_status(
    p_branch_id uuid,
    p_status text
)
returns public.organization_branches
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    prev public.organization_branches;
    updated public.organization_branches;
    v_status text := upper(trim(coalesce(p_status, '')));
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if p_branch_id is null then
        raise exception 'BRANCH_REQUIRED';
    end if;
    if v_status not in ('ACTIVE', 'INACTIVE', 'CLOSED') then
        raise exception 'STATUS_INVALID';
    end if;

    select * into prev from public.organization_branches where id = p_branch_id;
    if prev.id is null then
        raise exception 'BRANCH_NOT_FOUND';
    end if;
    if not public.has_org_permission(prev.organization_id, 'organization.manage_branches') then
        raise exception 'FORBIDDEN';
    end if;

    update public.organization_branches
    set status = v_status, updated_at = timezone('utc', now())
    where id = p_branch_id
    returning * into updated;

    insert into public.organization_audit_log (
        actor_user_id, organization_id, action, previous_value, new_value,
        reason_code, note, request_id
    ) values (
        actor, prev.organization_id, 'BRANCH_STATUS_CHANGED',
        jsonb_build_object('branch_id', prev.id, 'status', prev.status),
        jsonb_build_object('branch_id', updated.id, 'status', v_status),
        'branch_status_changed', null, null
    );

    return updated;
end;
$$;

revoke all on function public.set_organization_branch_status(uuid, text) from public;
grant execute on function public.set_organization_branch_status(uuid, text) to authenticated;

create or replace function public.list_organization_branches(
    p_organization_id uuid,
    p_include_private boolean default true
)
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
    if p_organization_id is null then
        raise exception 'ORGANIZATION_REQUIRED';
    end if;

    if p_include_private then
        if actor is null then
            raise exception 'NOT_AUTHENTICATED';
        end if;
        if not public.has_org_permission(p_organization_id, 'organization.manage_branches') then
            raise exception 'FORBIDDEN';
        end if;
        select coalesce(jsonb_agg(to_jsonb(b) order by b.name, b.id), '[]'::jsonb)
        into payload
        from public.organization_branches b
        where b.organization_id = p_organization_id
          and b.status <> 'CLOSED';
    else
        select coalesce(
            jsonb_agg(public.org_branch_public_json(b) order by b.name, b.id),
            '[]'::jsonb
        )
        into payload
        from public.organization_branches b
        join public.organizations o on o.id = b.organization_id
        where b.organization_id = p_organization_id
          and b.status = 'ACTIVE'
          and o.status in ('ACTIVE', 'RESTRICTED');
    end if;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.list_organization_branches(uuid, boolean) from public;
grant execute on function public.list_organization_branches(uuid, boolean) to authenticated;
grant execute on function public.list_organization_branches(uuid, boolean) to anon;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.organization_invitations enable row level security;
alter table public.organization_branches enable row level security;

-- Invitaciones: propias pendientes o manage_members de la org.
drop policy if exists organization_invitations_select on public.organization_invitations;
create policy organization_invitations_select
    on public.organization_invitations for select to authenticated
    using (
        (
            status = 'PENDING'
            and (
                invited_user_id = auth.uid()
                or (
                    invited_user_id is null
                    and invited_email is not null
                    and invited_email = (
                        select u.email::extensions.citext from auth.users u where u.id = auth.uid()
                    )
                )
            )
        )
        or public.has_org_permission(organization_id, 'organization.manage_members')
    );

-- Sucursales: miembros con view_private o lectura pública vía RPC.
drop policy if exists organization_branches_select on public.organization_branches;
create policy organization_branches_select
    on public.organization_branches for select to authenticated
    using (
        public.has_org_permission(organization_id, 'organization.view_private')
        or (
            status = 'ACTIVE'
            and exists (
                select 1 from public.organizations o
                where o.id = organization_id
                  and o.status in ('ACTIVE', 'RESTRICTED')
            )
        )
    );

revoke insert, update, delete on public.organization_invitations from authenticated;
revoke insert, update, delete on public.organization_branches from authenticated;

grant select on public.organization_invitations to authenticated;
grant select on public.organization_branches to authenticated;
