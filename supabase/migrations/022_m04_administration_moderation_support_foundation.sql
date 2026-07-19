-- LeoVer — M04 Etapa 3: Persistencia, RLS, RPC y colas administrativas
-- Depends on: 012 (content_reports), 018 (permissions/has_permission), 019 (organizations).
-- Do NOT edit migrations 001–021.
-- Remote staging validation: PENDIENTE DE VALIDACIÓN REMOTA
--
-- CRITICAL: primer bloque endurece content_reports (sin ventana fail-open).

-- =============================================================================
-- 1) CRITICAL FIRST BLOCK — content_reports hardening
-- =============================================================================

-- 1.1 Drop open policies immediately (SELECT/UPDATE using true)
drop policy if exists content_reports_select on public.content_reports;
drop policy if exists content_reports_update on public.content_reports;
drop policy if exists content_reports_insert on public.content_reports;

-- 1.2 Compatible columns (IF NOT EXISTS)
alter table public.content_reports
    add column if not exists priority text not null default 'NORMAL',
    add column if not exists case_id uuid,
    add column if not exists reason_code text,
    add column if not exists reason_detail text,
    add column if not exists duplicate_of_report_id uuid,
    add column if not exists updated_at timestamptz not null default timezone('utc', now());

-- Self-FK for duplicates (idempotent)
do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'content_reports_duplicate_of_report_id_fkey'
    ) then
        alter table public.content_reports
            add constraint content_reports_duplicate_of_report_id_fkey
            foreign key (duplicate_of_report_id) references public.content_reports (id);
    end if;
end $$;

-- Priority allowlist
do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'content_reports_priority_allowed'
    ) then
        alter table public.content_reports
            add constraint content_reports_priority_allowed
            check (priority in ('LOW', 'NORMAL', 'HIGH', 'URGENT'));
    end if;
end $$;

-- Status: legacy OPEN|REVIEWED|DISMISSED|ACTIONED + Etapa 2 statuses
do $$
begin
    alter table public.content_reports drop constraint if exists content_reports_status_allowed;
    alter table public.content_reports
        add constraint content_reports_status_allowed
        check (status in (
            -- legacy
            'OPEN', 'REVIEWED', 'DISMISSED', 'ACTIONED',
            -- Etapa 2
            'TRIAGED', 'IN_REVIEW', 'ACTION_REQUIRED', 'RESOLVED', 'DUPLICATE', 'CLOSED'
        ));
exception when others then
    null;
end $$;

-- Target types: legacy POST|USER|COMMENT + domain ModerationTargetType
do $$
begin
    alter table public.content_reports drop constraint if exists content_reports_target_type_allowed;
    alter table public.content_reports
        add constraint content_reports_target_type_allowed
        check (target_type in (
            'POST', 'USER', 'COMMENT',
            'USER_PROFILE', 'ORGANIZATION', 'MESSAGE', 'PET_PROFILE',
            'ADOPTION_LISTING', 'LOST_FOUND_CASE', 'SERVICE_PROFILE',
            'PRODUCT', 'EVENT', 'OTHER'
        ));
exception when others then
    null;
end $$;

-- 1.3 Backfill reason_code from legacy reason when null
update public.content_reports
set reason_code = lower(trim(reason))
where reason_code is null
  and reason is not null
  and length(trim(reason)) > 0;

update public.content_reports
set reason_code = 'other'
where reason_code is null or length(trim(reason_code)) = 0;

-- Legacy REVIEWED/ACTIONED kept as-is (domain maps REVIEWED→IN_REVIEW, ACTIONED→ACTION_REQUIRED cosméticamente;
-- ACTIONED legacy ≠ medida real aplicada).

update public.content_reports
set updated_at = coalesce(updated_at, created_at, timezone('utc', now()))
where updated_at is null;

-- 1.4 Indexes for queue
create index if not exists content_reports_queue_idx
    on public.content_reports (status, priority, created_at desc);
create index if not exists content_reports_target_idx
    on public.content_reports (target_type, target_id);
create index if not exists content_reports_reporter_idx
    on public.content_reports (reporter_id, created_at desc);
create index if not exists content_reports_case_idx
    on public.content_reports (case_id)
    where case_id is not null;

-- 1.5 RLS — deny-by-default writes; staff via permission; reporter own
alter table public.content_reports enable row level security;

create policy content_reports_select on public.content_reports
    for select to authenticated
    using (
        reporter_id = auth.uid()
        or public.has_permission('moderation.view')
        or public.has_permission('moderation.manage_reports')
    );

create policy content_reports_insert on public.content_reports
    for insert to authenticated
    with check (
        auth.uid() is not null
        and auth.uid() = reporter_id
    );

-- Staff mutates only via SECURITY DEFINER RPC
create policy content_reports_update on public.content_reports
    for update to authenticated
    using (false);

create policy content_reports_delete on public.content_reports
    for delete to authenticated
    using (false);

revoke update, delete on public.content_reports from authenticated;
grant select, insert on public.content_reports to authenticated;

comment on table public.content_reports is
    'M04: reporter_id is sensitive for staff projections — strip via RPC unless moderation.view_sensitive.';

-- =============================================================================
-- 2) Tables — moderation
-- =============================================================================

create table if not exists public.moderation_cases (
    id uuid primary key default gen_random_uuid(),
    title text not null,
    status text not null default 'OPEN',
    priority text not null default 'NORMAL',
    assigned_to_user_id uuid null references auth.users (id),
    created_by_user_id uuid not null references auth.users (id),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    closed_at timestamptz null,
    close_reason_code text null,
    constraint moderation_cases_title_len check (
        char_length(trim(title)) between 3 and 160
    ),
    constraint moderation_cases_status_allowed check (
        status in ('OPEN', 'TRIAGED', 'IN_REVIEW', 'ACTION_REQUIRED', 'RESOLVED', 'DISMISSED', 'CLOSED')
    ),
    constraint moderation_cases_priority_allowed check (
        priority in ('LOW', 'NORMAL', 'HIGH', 'URGENT')
    )
);

create index if not exists moderation_cases_status_idx
    on public.moderation_cases (status, priority, updated_at desc);
create index if not exists moderation_cases_assigned_idx
    on public.moderation_cases (assigned_to_user_id)
    where assigned_to_user_id is not null;

-- FK case_id on content_reports (after moderation_cases exists)
do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'content_reports_case_id_fkey'
    ) then
        alter table public.content_reports
            add constraint content_reports_case_id_fkey
            foreign key (case_id) references public.moderation_cases (id);
    end if;
end $$;

create table if not exists public.moderation_case_reports (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null references public.moderation_cases (id) on delete cascade,
    report_id uuid not null references public.content_reports (id) on delete cascade,
    attached_at timestamptz not null default timezone('utc', now()),
    attached_by_user_id uuid not null references auth.users (id),
    detached_at timestamptz null
);

-- Unique active attachment: a report cannot belong to two active cases
create unique index if not exists moderation_case_reports_active_report_uniq
    on public.moderation_case_reports (report_id)
    where detached_at is null;

create index if not exists moderation_case_reports_case_idx
    on public.moderation_case_reports (case_id)
    where detached_at is null;

create table if not exists public.moderation_actions (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null references public.moderation_cases (id) on delete cascade,
    target_type text not null,
    target_id text not null,
    other_description text null,
    action_type text not null,
    reason_code text not null,
    reason_detail text null,
    applied_by_user_id uuid not null references auth.users (id),
    applied_at timestamptz not null default timezone('utc', now()),
    expires_at timestamptz null,
    reversed_at timestamptz null,
    reversed_by_user_id uuid null references auth.users (id),
    constraint moderation_actions_type_allowed check (
        action_type in (
            'NO_ACTION', 'CONTENT_HIDDEN', 'CONTENT_REMOVED', 'WARNING',
            'FEATURE_RESTRICTED', 'ACCOUNT_RESTRICTED', 'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED',
            'ORGANIZATION_RESTRICTED', 'ORGANIZATION_SUSPENDED',
            'VERIFICATION_REJECTED', 'VERIFICATION_REVOKED'
        )
    ),
    constraint moderation_actions_target_type_allowed check (
        target_type in (
            'POST', 'USER', 'COMMENT',
            'USER_PROFILE', 'ORGANIZATION', 'MESSAGE', 'PET_PROFILE',
            'ADOPTION_LISTING', 'LOST_FOUND_CASE', 'SERVICE_PROFILE',
            'PRODUCT', 'EVENT', 'OTHER'
        )
    ),
    constraint moderation_actions_detail_len check (
        reason_detail is null or char_length(reason_detail) <= 500
    ),
    constraint moderation_actions_target_id_len check (
        char_length(trim(target_id)) between 1 and 128
    )
);

create index if not exists moderation_actions_case_idx
    on public.moderation_actions (case_id, applied_at desc);

create table if not exists public.moderation_appeals (
    id uuid primary key default gen_random_uuid(),
    action_id uuid not null references public.moderation_actions (id) on delete cascade,
    submitted_by_user_id uuid not null references auth.users (id),
    statement text not null,
    status text not null default 'SUBMITTED',
    assigned_to_user_id uuid null references auth.users (id),
    reviewed_by_user_id uuid null references auth.users (id),
    decision_reason text null,
    created_at timestamptz not null default timezone('utc', now()),
    reviewed_at timestamptz null,
    constraint moderation_appeals_status_allowed check (
        status in (
            'SUBMITTED', 'UNDER_REVIEW', 'UPHELD', 'OVERTURNED',
            'PARTIALLY_OVERTURNED', 'REJECTED', 'CLOSED'
        )
    ),
    constraint moderation_appeals_statement_len check (
        char_length(trim(statement)) between 10 and 4000
    ),
    constraint moderation_appeals_decision_reason_len check (
        decision_reason is null or char_length(decision_reason) <= 1000
    )
);

create unique index if not exists moderation_appeals_active_uniq
    on public.moderation_appeals (action_id)
    where status in ('SUBMITTED', 'UNDER_REVIEW');

create index if not exists moderation_appeals_status_idx
    on public.moderation_appeals (status, created_at desc);

-- Logical evidence paths only (no http URLs)
create table if not exists public.moderation_evidence_refs (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null references public.moderation_cases (id) on delete cascade,
    storage_path_hint text null,
    content_type text null,
    created_by_user_id uuid not null references auth.users (id),
    created_at timestamptz not null default timezone('utc', now()),
    constraint moderation_evidence_refs_no_http check (
        storage_path_hint is null
        or storage_path_hint !~* '^https?://'
    )
);

create index if not exists moderation_evidence_refs_case_idx
    on public.moderation_evidence_refs (case_id, created_at desc);

create table if not exists public.moderation_case_notes (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null references public.moderation_cases (id) on delete cascade,
    author_user_id uuid not null references auth.users (id),
    body text not null,
    created_at timestamptz not null default timezone('utc', now()),
    constraint moderation_case_notes_body_len check (
        char_length(trim(body)) between 1 and 2000
    )
);

create index if not exists moderation_case_notes_case_idx
    on public.moderation_case_notes (case_id, created_at desc);

-- =============================================================================
-- 3) Tables — organization verification (M04 review; M03 requests)
-- =============================================================================

create table if not exists public.organization_verification_reviews (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    requested_by_user_id uuid not null references auth.users (id),
    assigned_to_user_id uuid null references auth.users (id),
    status text not null default 'PENDING_REVIEW',
    decision text null,
    review_note text null,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    decided_at timestamptz null,
    decided_by_user_id uuid null references auth.users (id),
    constraint organization_verification_reviews_status_allowed check (
        status in (
            'PENDING_REVIEW', 'MORE_INFO_REQUESTED', 'APPROVED',
            'REJECTED', 'REVOKED', 'EXPIRED'
        )
    ),
    constraint organization_verification_reviews_decision_allowed check (
        decision is null or decision in (
            'APPROVE', 'REJECT', 'REQUEST_MORE_INFORMATION', 'REVOKE', 'MARK_EXPIRED'
        )
    ),
    constraint organization_verification_reviews_note_len check (
        review_note is null or char_length(review_note) <= 2000
    )
);

create index if not exists organization_verification_reviews_queue_idx
    on public.organization_verification_reviews (status, updated_at desc);
create index if not exists organization_verification_reviews_org_idx
    on public.organization_verification_reviews (organization_id, created_at desc);

create table if not exists public.organization_verification_document_refs (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    review_id uuid null references public.organization_verification_reviews (id) on delete set null,
    logical_name text not null,
    storage_path_hint text null,
    created_at timestamptz not null default timezone('utc', now()),
    created_by_user_id uuid null references auth.users (id),
    constraint organization_verification_document_refs_no_http check (
        storage_path_hint is null
        or storage_path_hint !~* '^https?://'
    ),
    constraint organization_verification_document_refs_name_len check (
        char_length(trim(logical_name)) between 1 and 200
    )
);

create index if not exists organization_verification_document_refs_org_idx
    on public.organization_verification_document_refs (organization_id);

-- =============================================================================
-- 4) Tables — support
-- =============================================================================

create table if not exists public.support_tickets (
    id uuid primary key default gen_random_uuid(),
    requester_user_id uuid not null references auth.users (id),
    category text not null,
    subject text not null,
    description text not null,
    priority text not null default 'NORMAL',
    status text not null default 'OPEN',
    assigned_to_user_id uuid null references auth.users (id),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    resolved_at timestamptz null,
    closed_at timestamptz null,
    close_reason_code text null,
    linked_moderation_case_id uuid null references public.moderation_cases (id),
    constraint support_tickets_category_allowed check (
        category in (
            'ACCOUNT_ACCESS', 'PROFILE', 'ORGANIZATION', 'TECHNICAL',
            'PRIVACY', 'SAFETY', 'CONTENT', 'OTHER'
        )
    ),
    constraint support_tickets_priority_allowed check (
        priority in ('LOW', 'NORMAL', 'HIGH', 'URGENT')
    ),
    constraint support_tickets_status_allowed check (
        status in (
            'OPEN', 'IN_PROGRESS', 'WAITING_USER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED'
        )
    ),
    constraint support_tickets_subject_len check (
        char_length(trim(subject)) between 3 and 160
    ),
    constraint support_tickets_description_len check (
        char_length(trim(description)) between 1 and 4000
    )
);

create index if not exists support_tickets_requester_idx
    on public.support_tickets (requester_user_id, created_at desc);
create index if not exists support_tickets_queue_idx
    on public.support_tickets (status, priority, updated_at desc);

create table if not exists public.support_ticket_messages (
    id uuid primary key default gen_random_uuid(),
    ticket_id uuid not null references public.support_tickets (id) on delete cascade,
    author_user_id uuid not null references auth.users (id),
    visibility text not null,
    body text not null,
    created_at timestamptz not null default timezone('utc', now()),
    evidence_ref_id uuid null,
    constraint support_ticket_messages_visibility_allowed check (
        visibility in ('REQUESTER_VISIBLE', 'INTERNAL')
    ),
    constraint support_ticket_messages_body_len check (
        char_length(trim(body)) between 1 and 4000
    )
);

create index if not exists support_ticket_messages_ticket_idx
    on public.support_ticket_messages (ticket_id, created_at);

-- =============================================================================
-- 5) Tables — assignments & administrative audit
-- =============================================================================

create table if not exists public.administrative_assignments (
    id uuid primary key default gen_random_uuid(),
    resource_type text not null,
    resource_id uuid not null,
    assigned_to uuid not null references auth.users (id),
    assigned_by uuid not null references auth.users (id),
    assigned_at timestamptz not null default timezone('utc', now()),
    revoked_at timestamptz null,
    constraint administrative_assignments_resource_type_allowed check (
        resource_type in ('MODERATION_CASE', 'VERIFICATION_REVIEW', 'SUPPORT_TICKET', 'MODERATION_APPEAL')
    )
);

create unique index if not exists administrative_assignments_active_uniq
    on public.administrative_assignments (resource_type, resource_id)
    where revoked_at is null;

create index if not exists administrative_assignments_assignee_idx
    on public.administrative_assignments (assigned_to)
    where revoked_at is null;

create table if not exists public.administrative_audit_log (
    id uuid primary key default gen_random_uuid(),
    actor_user_id uuid not null references auth.users (id),
    action text not null,
    resource_type text not null,
    resource_id uuid null,
    previous_value jsonb null,
    new_value jsonb null,
    reason_code text null,
    note text null,
    request_id text null,
    created_at timestamptz not null default timezone('utc', now()),
    constraint administrative_audit_log_note_len check (
        note is null or char_length(note) <= 500
    )
);

create index if not exists administrative_audit_log_created_idx
    on public.administrative_audit_log (created_at desc);
create index if not exists administrative_audit_log_resource_idx
    on public.administrative_audit_log (resource_type, resource_id, created_at desc);

-- =============================================================================
-- 6) Permission seeds (idempotent) + role matrix
-- =============================================================================

insert into public.permissions (code, description) values
    ('moderation.manage_cases', 'Crear/asignar/cambiar estado de casos de moderación'),
    ('moderation.apply_actions', 'Aplicar medidas de moderación'),
    ('moderation.view_sensitive', 'Ver reporter_id, notas y evidencia sensible'),
    ('moderation.review_appeals', 'Revisar apelaciones de moderación'),
    ('organizations.review_verification', 'Revisar verificación de organizaciones'),
    ('organizations.revoke_verification', 'Revocar verificación de organizaciones'),
    ('support.view', 'Ver cola de soporte'),
    ('support.manage', 'Gestionar tickets de soporte'),
    ('support.view_sensitive', 'Ver tickets PRIVACY/SAFETY y datos sensibles de soporte')
on conflict (code) do nothing;

-- MODERATOR: + manage_cases, review_appeals
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'MODERATOR'
  and p.code in (
    'moderation.manage_cases',
    'moderation.review_appeals'
  )
on conflict do nothing;

-- ADMIN: + apply_actions, view_sensitive, review_verification, support.*, audit.view
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'ADMIN'
  and p.code in (
    'moderation.manage_cases',
    'moderation.apply_actions',
    'moderation.view_sensitive',
    'moderation.review_appeals',
    'organizations.review_verification',
    'support.view',
    'support.manage',
    'support.view_sensitive',
    'audit.view'
  )
on conflict do nothing;

-- SUPERADMIN: all new M04 permissions (plus existing via prior seeds)
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code = 'SUPERADMIN'
  and p.code in (
    'moderation.manage_cases',
    'moderation.apply_actions',
    'moderation.view_sensitive',
    'moderation.review_appeals',
    'organizations.review_verification',
    'organizations.revoke_verification',
    'support.view',
    'support.manage',
    'support.view_sensitive'
  )
on conflict do nothing;

-- =============================================================================
-- 7) Helpers
-- =============================================================================

create or replace function public.m04_require_permission(permission_code text)
returns void
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if not public.has_permission(permission_code) then
        raise exception 'FORBIDDEN';
    end if;
end;
$$;

revoke all on function public.m04_require_permission(text) from public;

create or replace function public.m04_require_active_actor()
returns uuid
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
        raise exception 'NOT_AUTHENTICATED';
    end if;
    select u.account_status into acct from public.users u where u.id = uid;
    if acct is null or acct <> 'ACTIVE' then
        raise exception 'FORBIDDEN';
    end if;
    return uid;
end;
$$;

revoke all on function public.m04_require_active_actor() from public;

create or replace function public.m04_audit(
    p_action text,
    p_resource_type text,
    p_resource_id uuid,
    p_previous_value jsonb default null,
    p_new_value jsonb default null,
    p_reason_code text default null,
    p_note text default null,
    p_request_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := auth.uid();
    new_id uuid;
begin
    if actor is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    insert into public.administrative_audit_log (
        actor_user_id, action, resource_type, resource_id,
        previous_value, new_value, reason_code, note, request_id
    ) values (
        actor,
        upper(trim(p_action)),
        upper(trim(p_resource_type)),
        p_resource_id,
        p_previous_value,
        p_new_value,
        nullif(lower(trim(coalesce(p_reason_code, ''))), ''),
        nullif(left(trim(coalesce(p_note, '')), 500), ''),
        nullif(trim(coalesce(p_request_id, '')), '')
    )
    returning id into new_id;
    return new_id;
end;
$$;

revoke all on function public.m04_audit(text, text, uuid, jsonb, jsonb, text, text, text) from public;

create or replace function public.m04_upsert_assignment(
    p_resource_type text,
    p_resource_id uuid,
    p_assigned_to uuid,
    p_assigned_by uuid
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_type text := upper(trim(p_resource_type));
    existing_id uuid;
begin
    update public.administrative_assignments
    set revoked_at = timezone('utc', now())
    where resource_type = v_type
      and resource_id = p_resource_id
      and revoked_at is null;

    insert into public.administrative_assignments (
        resource_type, resource_id, assigned_to, assigned_by
    ) values (
        v_type, p_resource_id, p_assigned_to, p_assigned_by
    )
    returning id into existing_id;
    return existing_id;
end;
$$;

revoke all on function public.m04_upsert_assignment(text, uuid, uuid, uuid) from public;

-- Normalize target_type for storage (USER_PROFILE → USER for legacy compatibility when requested)
create or replace function public.m04_normalize_report_target_type(p_type text)
returns text
language sql
immutable
as $$
    select case upper(trim(coalesce(p_type, '')))
        when 'USER_PROFILE' then 'USER'
        else upper(trim(coalesce(p_type, '')))
    end;
$$;

-- =============================================================================
-- 8) RPCs — reports & queue
-- =============================================================================

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
    actor uuid;
    v_type text;
    v_target text := trim(coalesce(p_target_id, ''));
    v_reason text := lower(trim(coalesce(p_reason_code, '')));
    v_desc text := nullif(trim(coalesce(p_description, '')), '');
    new_row public.content_reports;
begin
    actor := public.m04_require_active_actor();
    v_type := public.m04_normalize_report_target_type(p_target_type);

    if v_type not in (
        'POST', 'USER', 'COMMENT',
        'USER_PROFILE', 'ORGANIZATION', 'MESSAGE', 'PET_PROFILE',
        'ADOPTION_LISTING', 'LOST_FOUND_CASE', 'SERVICE_PROFILE',
        'PRODUCT', 'EVENT', 'OTHER'
    ) then
        raise exception 'VALIDATION';
    end if;
    if char_length(v_target) < 1 or char_length(v_target) > 128 then
        raise exception 'VALIDATION';
    end if;
    if v_reason not in (
        'spam', 'harassment', 'hate', 'scam', 'impersonation',
        'inappropriate', 'violence', 'privacy', 'other'
    ) then
        raise exception 'VALIDATION';
    end if;
    if v_desc is not null and char_length(v_desc) > 2000 then
        raise exception 'VALIDATION';
    end if;

    insert into public.content_reports (
        reporter_id, target_type, target_id, reason, details,
        reason_code, reason_detail, status, priority
    ) values (
        actor, v_type, v_target, v_reason, v_desc,
        v_reason, v_desc, 'OPEN', 'NORMAL'
    )
    returning * into new_row;

    perform public.m04_audit(
        'CREATE_CONTENT_REPORT', 'CONTENT_REPORT', new_row.id,
        null,
        jsonb_build_object(
            'target_type', new_row.target_type,
            'target_id', new_row.target_id,
            'reason_code', new_row.reason_code,
            'status', new_row.status
        ),
        v_reason, null, null
    );

    return jsonb_build_object(
        'id', new_row.id,
        'target_type', new_row.target_type,
        'target_id', new_row.target_id,
        'reason_code', new_row.reason_code,
        'status', new_row.status,
        'priority', new_row.priority,
        'created_at', new_row.created_at,
        'updated_at', new_row.updated_at
    );
end;
$$;

revoke all on function public.create_content_report(text, text, text, text) from public;
grant execute on function public.create_content_report(text, text, text, text) to authenticated;

create or replace function public.get_my_content_reports(p_limit int default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
    payload jsonb;
begin
    actor := public.m04_require_active_actor();

    select coalesce(jsonb_agg(row_to_json(t)::jsonb order by t.created_at desc), '[]'::jsonb)
    into payload
    from (
        select
            r.id,
            r.target_type,
            r.target_id,
            r.reason_code,
            r.reason_detail,
            r.priority,
            r.status,
            r.case_id,
            r.duplicate_of_report_id,
            r.created_at,
            r.updated_at
        from public.content_reports r
        where r.reporter_id = actor
        order by r.created_at desc
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.get_my_content_reports(int) from public;
grant execute on function public.get_my_content_reports(int) to authenticated;

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
    actor uuid;
    lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
    can_sensitive boolean;
    v_status text := nullif(upper(trim(coalesce(p_status, ''))), '');
    payload jsonb;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.view');
    can_sensitive := public.has_permission('moderation.view_sensitive');

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select
            r.id,
            case when can_sensitive then r.reporter_id else null end as reporter_id,
            r.target_type,
            r.target_id,
            r.reason_code,
            r.priority,
            r.status,
            r.case_id,
            r.created_at,
            r.updated_at
        from public.content_reports r
        where (v_status is null or r.status = v_status)
          and r.status not in ('CLOSED', 'DUPLICATE', 'DISMISSED', 'RESOLVED')
        order by
            case r.priority
                when 'URGENT' then 1
                when 'HIGH' then 2
                when 'NORMAL' then 3
                else 4
            end,
            r.created_at asc
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.list_moderation_queue(text, int) from public;
grant execute on function public.list_moderation_queue(text, int) to authenticated;

create or replace function public.get_moderation_report_for_staff(p_report_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    can_sensitive boolean;
    r public.content_reports;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.view');
    can_sensitive := public.has_permission('moderation.view_sensitive');

    select * into r from public.content_reports where id = p_report_id;
    if r.id is null then
        raise exception 'NOT_FOUND';
    end if;

    return jsonb_build_object(
        'id', r.id,
        'reporter_id', case when can_sensitive then r.reporter_id else null end,
        'target_type', r.target_type,
        'target_id', r.target_id,
        'reason_code', r.reason_code,
        'reason_detail', case when can_sensitive then r.reason_detail else null end,
        'priority', r.priority,
        'status', r.status,
        'case_id', r.case_id,
        'duplicate_of_report_id', r.duplicate_of_report_id,
        'created_at', r.created_at,
        'updated_at', r.updated_at,
        'reviewed_at', r.reviewed_at,
        'reviewed_by', case when can_sensitive then r.reviewed_by else null end
    );
end;
$$;

revoke all on function public.get_moderation_report_for_staff(uuid) from public;
grant execute on function public.get_moderation_report_for_staff(uuid) to authenticated;

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
    actor uuid;
    v_status text := upper(trim(coalesce(p_status, '')));
    v_priority text;
    prev public.content_reports;
    updated public.content_reports;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.manage_reports');

    -- Incluye legacy REVIEWED/ACTIONED (ACTIONED ≠ medida real aplicada).
    if v_status not in (
        'TRIAGED', 'IN_REVIEW', 'ACTION_REQUIRED', 'DISMISSED', 'RESOLVED', 'CLOSED', 'OPEN',
        'REVIEWED', 'ACTIONED'
    ) then
        raise exception 'VALIDATION';
    end if;

    select * into prev from public.content_reports where id = p_report_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;

    v_priority := coalesce(nullif(upper(trim(coalesce(p_priority, ''))), ''), prev.priority);
    if v_priority not in ('LOW', 'NORMAL', 'HIGH', 'URGENT') then
        raise exception 'VALIDATION';
    end if;

    update public.content_reports
    set status = v_status,
        priority = v_priority,
        reviewed_at = timezone('utc', now()),
        reviewed_by = actor,
        updated_at = timezone('utc', now())
    where id = p_report_id
    returning * into updated;

    perform public.m04_audit(
        'TRIAGE_CONTENT_REPORT', 'CONTENT_REPORT', p_report_id,
        jsonb_build_object('status', prev.status, 'priority', prev.priority),
        jsonb_build_object('status', updated.status, 'priority', updated.priority),
        null, null, null
    );

    return jsonb_build_object(
        'id', updated.id,
        'status', updated.status,
        'priority', updated.priority,
        'updated_at', updated.updated_at
    );
end;
$$;

revoke all on function public.triage_content_report(uuid, text, text) from public;
grant execute on function public.triage_content_report(uuid, text, text) to authenticated;

create or replace function public.mark_content_report_duplicate(
    p_report_id uuid,
    p_duplicate_of_report_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    prev public.content_reports;
    updated public.content_reports;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.manage_reports');

    if p_duplicate_of_report_id is null then
        raise exception 'VALIDATION';
    end if;
    if p_report_id = p_duplicate_of_report_id then
        raise exception 'VALIDATION';
    end if;
    if not exists (select 1 from public.content_reports where id = p_duplicate_of_report_id) then
        raise exception 'NOT_FOUND';
    end if;

    select * into prev from public.content_reports where id = p_report_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;

    update public.content_reports
    set status = 'DUPLICATE',
        duplicate_of_report_id = p_duplicate_of_report_id,
        reviewed_at = timezone('utc', now()),
        reviewed_by = actor,
        updated_at = timezone('utc', now())
    where id = p_report_id
    returning * into updated;

    perform public.m04_audit(
        'MARK_CONTENT_REPORT_DUPLICATE', 'CONTENT_REPORT', p_report_id,
        jsonb_build_object('status', prev.status),
        jsonb_build_object('status', 'DUPLICATE', 'duplicate_of_report_id', p_duplicate_of_report_id),
        null, null, null
    );

    return jsonb_build_object(
        'id', updated.id,
        'status', updated.status,
        'duplicate_of_report_id', updated.duplicate_of_report_id
    );
end;
$$;

revoke all on function public.mark_content_report_duplicate(uuid, uuid) from public;
grant execute on function public.mark_content_report_duplicate(uuid, uuid) to authenticated;

-- =============================================================================
-- 9) RPCs — cases
-- =============================================================================

create or replace function public.create_moderation_case(
    p_title text,
    p_priority text default 'NORMAL'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_title text := trim(coalesce(p_title, ''));
    v_priority text := upper(trim(coalesce(p_priority, 'NORMAL')));
    new_row public.moderation_cases;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.manage_cases');

    if char_length(v_title) < 3 or char_length(v_title) > 160 then
        raise exception 'VALIDATION';
    end if;
    if v_priority not in ('LOW', 'NORMAL', 'HIGH', 'URGENT') then
        raise exception 'VALIDATION';
    end if;

    insert into public.moderation_cases (
        title, status, priority, created_by_user_id
    ) values (
        v_title, 'OPEN', v_priority, actor
    )
    returning * into new_row;

    perform public.m04_audit(
        'CREATE_MODERATION_CASE', 'MODERATION_CASE', new_row.id,
        null,
        jsonb_build_object('title', new_row.title, 'status', new_row.status),
        null, null, null
    );

    return row_to_json(new_row)::jsonb;
end;
$$;

revoke all on function public.create_moderation_case(text, text) from public;
grant execute on function public.create_moderation_case(text, text) to authenticated;

create or replace function public.attach_report_to_moderation_case(
    p_case_id uuid,
    p_report_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    c public.moderation_cases;
    r public.content_reports;
    link_id uuid;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.manage_cases');

    select * into c from public.moderation_cases where id = p_case_id for update;
    if c.id is null then
        raise exception 'NOT_FOUND';
    end if;
    if c.status = 'CLOSED' then
        raise exception 'VALIDATION';
    end if;

    select * into r from public.content_reports where id = p_report_id for update;
    if r.id is null then
        raise exception 'NOT_FOUND';
    end if;

    if r.case_id is not null
       and r.case_id <> p_case_id
       and r.status not in ('CLOSED', 'DISMISSED', 'DUPLICATE') then
        raise exception 'VALIDATION';
    end if;

    if exists (
        select 1 from public.moderation_case_reports
        where report_id = p_report_id and detached_at is null and case_id <> p_case_id
    ) then
        raise exception 'VALIDATION';
    end if;

    select id into link_id
    from public.moderation_case_reports
    where case_id = p_case_id and report_id = p_report_id and detached_at is null;

    if link_id is null then
        insert into public.moderation_case_reports (case_id, report_id, attached_by_user_id)
        values (p_case_id, p_report_id, actor)
        returning id into link_id;
    end if;

    update public.content_reports
    set case_id = p_case_id,
        status = case when status = 'OPEN' then 'IN_REVIEW' else status end,
        updated_at = timezone('utc', now())
    where id = p_report_id;

    update public.moderation_cases
    set updated_at = timezone('utc', now())
    where id = p_case_id;

    perform public.m04_audit(
        'ATTACH_REPORT_TO_CASE', 'MODERATION_CASE', p_case_id,
        null,
        jsonb_build_object('report_id', p_report_id, 'link_id', link_id),
        null, null, null
    );

    return jsonb_build_object(
        'case_id', p_case_id,
        'report_id', p_report_id,
        'link_id', link_id
    );
end;
$$;

revoke all on function public.attach_report_to_moderation_case(uuid, uuid) from public;
grant execute on function public.attach_report_to_moderation_case(uuid, uuid) to authenticated;

create or replace function public.list_moderation_cases(
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
    actor uuid;
    lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
    v_status text := nullif(upper(trim(coalesce(p_status, ''))), '');
    payload jsonb;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.view');

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select c.*
        from public.moderation_cases c
        where v_status is null or c.status = v_status
        order by c.updated_at desc
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.list_moderation_cases(text, int) from public;
grant execute on function public.list_moderation_cases(text, int) to authenticated;

create or replace function public.get_moderation_case(p_case_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    can_sensitive boolean;
    c public.moderation_cases;
    reports jsonb;
    notes jsonb;
    actions jsonb;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.view');
    can_sensitive := public.has_permission('moderation.view_sensitive');

    select * into c from public.moderation_cases where id = p_case_id;
    if c.id is null then
        raise exception 'NOT_FOUND';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'id', r.id,
        'reporter_id', case when can_sensitive then r.reporter_id else null end,
        'target_type', r.target_type,
        'target_id', r.target_id,
        'reason_code', r.reason_code,
        'status', r.status,
        'priority', r.priority
    )), '[]'::jsonb)
    into reports
    from public.content_reports r
    where r.case_id = p_case_id;

    if can_sensitive then
        select coalesce(jsonb_agg(row_to_json(n)::jsonb order by n.created_at), '[]'::jsonb)
        into notes
        from public.moderation_case_notes n
        where n.case_id = p_case_id;
    else
        notes := '[]'::jsonb;
    end if;

    select coalesce(jsonb_agg(row_to_json(a)::jsonb order by a.applied_at), '[]'::jsonb)
    into actions
    from public.moderation_actions a
    where a.case_id = p_case_id;

    return jsonb_build_object(
        'case', row_to_json(c)::jsonb,
        'reports', reports,
        'notes', notes,
        'actions', actions
    );
end;
$$;

revoke all on function public.get_moderation_case(uuid) from public;
grant execute on function public.get_moderation_case(uuid) to authenticated;

create or replace function public.assign_moderation_case(
    p_case_id uuid,
    p_assigned_to_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    prev public.moderation_cases;
    updated public.moderation_cases;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.manage_cases');

    if p_assigned_to_user_id is null then
        raise exception 'VALIDATION';
    end if;

    select * into prev from public.moderation_cases where id = p_case_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;

    update public.moderation_cases
    set assigned_to_user_id = p_assigned_to_user_id,
        updated_at = timezone('utc', now())
    where id = p_case_id
    returning * into updated;

    perform public.m04_upsert_assignment(
        'MODERATION_CASE', p_case_id, p_assigned_to_user_id, actor
    );

    perform public.m04_audit(
        'ASSIGN_MODERATION_CASE', 'MODERATION_CASE', p_case_id,
        jsonb_build_object('assigned_to_user_id', prev.assigned_to_user_id),
        jsonb_build_object('assigned_to_user_id', updated.assigned_to_user_id),
        null, null, null
    );

    return row_to_json(updated)::jsonb;
end;
$$;

revoke all on function public.assign_moderation_case(uuid, uuid) from public;
grant execute on function public.assign_moderation_case(uuid, uuid) to authenticated;

create or replace function public.change_moderation_case_status(
    p_case_id uuid,
    p_status text,
    p_close_reason_code text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_status text := upper(trim(coalesce(p_status, '')));
    v_close text := nullif(lower(trim(coalesce(p_close_reason_code, ''))), '');
    prev public.moderation_cases;
    updated public.moderation_cases;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.manage_cases');

    if v_status not in (
        'OPEN', 'TRIAGED', 'IN_REVIEW', 'ACTION_REQUIRED', 'RESOLVED', 'DISMISSED', 'CLOSED'
    ) then
        raise exception 'VALIDATION';
    end if;

    select * into prev from public.moderation_cases where id = p_case_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;

    if v_status = 'CLOSED' then
        if prev.status = 'CLOSED' then
            raise exception 'VALIDATION';
        end if;
        if prev.status not in ('RESOLVED', 'DISMISSED') and v_close is null then
            raise exception 'VALIDATION';
        end if;
    end if;

    update public.moderation_cases
    set status = v_status,
        close_reason_code = case when v_status = 'CLOSED' then coalesce(v_close, close_reason_code) else close_reason_code end,
        closed_at = case when v_status = 'CLOSED' then timezone('utc', now()) else closed_at end,
        updated_at = timezone('utc', now())
    where id = p_case_id
    returning * into updated;

    perform public.m04_audit(
        'CHANGE_MODERATION_CASE_STATUS', 'MODERATION_CASE', p_case_id,
        jsonb_build_object('status', prev.status),
        jsonb_build_object('status', updated.status, 'close_reason_code', updated.close_reason_code),
        v_close, null, null
    );

    return row_to_json(updated)::jsonb;
end;
$$;

revoke all on function public.change_moderation_case_status(uuid, text, text) from public;
grant execute on function public.change_moderation_case_status(uuid, text, text) to authenticated;

create or replace function public.add_moderation_internal_note(
    p_case_id uuid,
    p_body text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_body text := trim(coalesce(p_body, ''));
    new_row public.moderation_case_notes;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.manage_cases');
    -- notes are sensitive; require view_sensitive to write/read
    perform public.m04_require_permission('moderation.view_sensitive');

    if char_length(v_body) < 1 or char_length(v_body) > 2000 then
        raise exception 'VALIDATION';
    end if;
    if not exists (select 1 from public.moderation_cases where id = p_case_id) then
        raise exception 'NOT_FOUND';
    end if;

    insert into public.moderation_case_notes (case_id, author_user_id, body)
    values (p_case_id, actor, v_body)
    returning * into new_row;

    update public.moderation_cases
    set updated_at = timezone('utc', now())
    where id = p_case_id;

    perform public.m04_audit(
        'ADD_MODERATION_INTERNAL_NOTE', 'MODERATION_CASE', p_case_id,
        null,
        jsonb_build_object('note_id', new_row.id),
        null, null, null
    );

    return row_to_json(new_row)::jsonb;
end;
$$;

revoke all on function public.add_moderation_internal_note(uuid, text) from public;
grant execute on function public.add_moderation_internal_note(uuid, text) to authenticated;

-- =============================================================================
-- 10) RPCs — actions & appeals
-- =============================================================================

create or replace function public.apply_moderation_action(
    p_case_id uuid,
    p_target_type text,
    p_target_id text,
    p_action_type text,
    p_reason_code text,
    p_reason_detail text default null,
    p_expires_at timestamptz default null,
    p_other_description text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_type text := upper(trim(coalesce(p_target_type, '')));
    v_target text := trim(coalesce(p_target_id, ''));
    v_action text := upper(trim(coalesce(p_action_type, '')));
    v_reason text := lower(trim(coalesce(p_reason_code, '')));
    v_detail text := nullif(trim(coalesce(p_reason_detail, '')), '');
    now_ts timestamptz := timezone('utc', now());
    is_temp boolean;
    is_perm boolean;
    new_row public.moderation_actions;
    target_user uuid;
    prev_acct text;
    new_acct text;
    org_id uuid;
    prev_org_status text;
    new_org_status text;
    prev_verif text;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.apply_actions');

    if not exists (select 1 from public.moderation_cases where id = p_case_id) then
        raise exception 'NOT_FOUND';
    end if;

    if v_type = 'USER_PROFILE' then
        v_type := 'USER';
    end if;

    if v_action not in (
        'NO_ACTION', 'CONTENT_HIDDEN', 'CONTENT_REMOVED', 'WARNING',
        'FEATURE_RESTRICTED', 'ACCOUNT_RESTRICTED', 'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED',
        'ORGANIZATION_RESTRICTED', 'ORGANIZATION_SUSPENDED',
        'VERIFICATION_REJECTED', 'VERIFICATION_REVOKED'
    ) then
        raise exception 'VALIDATION';
    end if;

    if v_reason not in (
        'policy_violation', 'safety', 'spam', 'legal', 'ops_review',
        'appeal_accepted', 'appeal_rejected', 'other'
    ) then
        raise exception 'VALIDATION';
    end if;

    if char_length(v_target) < 1 or char_length(v_target) > 128 then
        raise exception 'VALIDATION';
    end if;
    if v_detail is not null and char_length(v_detail) > 500 then
        raise exception 'VALIDATION';
    end if;

    is_temp := v_action in (
        'FEATURE_RESTRICTED', 'ACCOUNT_RESTRICTED', 'ACCOUNT_SUSPENDED',
        'ORGANIZATION_RESTRICTED', 'ORGANIZATION_SUSPENDED', 'CONTENT_HIDDEN'
    );
    is_perm := v_action in (
        'CONTENT_REMOVED', 'ACCOUNT_BANNED', 'VERIFICATION_REJECTED',
        'VERIFICATION_REVOKED', 'WARNING', 'NO_ACTION'
    );

    if v_action = 'NO_ACTION' and p_expires_at is not null then
        raise exception 'VALIDATION';
    elsif is_temp and (p_expires_at is null or p_expires_at <= now_ts) then
        raise exception 'VALIDATION';
    elsif is_perm and p_expires_at is not null then
        raise exception 'VALIDATION';
    end if;

    -- Account status side-effects (reuse M02 semantics inline)
    if v_action in ('ACCOUNT_RESTRICTED', 'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED') then
        perform public.m04_require_permission('users.change_status');
        begin
            target_user := v_target::uuid;
        exception when others then
            raise exception 'VALIDATION';
        end;
        new_acct := case v_action
            when 'ACCOUNT_RESTRICTED' then 'RESTRICTED'
            when 'ACCOUNT_SUSPENDED' then 'SUSPENDED'
            when 'ACCOUNT_BANNED' then 'BANNED'
        end;
        select account_status into prev_acct from public.users where id = target_user;
        if prev_acct is null then
            raise exception 'NOT_FOUND';
        end if;
        if actor = target_user then
            raise exception 'FORBIDDEN';
        end if;
        if prev_acct <> new_acct then
            update public.users
            set account_status = new_acct,
                updated_at = now_ts
            where id = target_user;
            insert into public.user_status_history (
                user_id, previous_status, new_status, reason_code, note, changed_by
            ) values (
                target_user, prev_acct, new_acct, v_reason, v_detail, actor
            );
        end if;
    end if;

    -- Organization status side-effects (M03)
    if v_action in ('ORGANIZATION_RESTRICTED', 'ORGANIZATION_SUSPENDED') then
        begin
            org_id := v_target::uuid;
        exception when others then
            raise exception 'VALIDATION';
        end;
        new_org_status := case v_action
            when 'ORGANIZATION_RESTRICTED' then 'RESTRICTED'
            when 'ORGANIZATION_SUSPENDED' then 'SUSPENDED'
        end;
        select status into prev_org_status from public.organizations where id = org_id;
        if prev_org_status is null then
            raise exception 'NOT_FOUND';
        end if;
        if prev_org_status <> new_org_status then
            update public.organizations
            set status = new_org_status,
                updated_at = now_ts
            where id = org_id;
            insert into public.organization_status_history (
                organization_id, previous_status, new_status, reason_code, note, changed_by
            ) values (
                org_id, prev_org_status, new_org_status, v_reason, v_detail, actor
            );
        end if;
    end if;

    if v_action = 'VERIFICATION_REVOKED' then
        perform public.m04_require_permission('organizations.revoke_verification');
        begin
            org_id := v_target::uuid;
        exception when others then
            raise exception 'VALIDATION';
        end;
        select verification_status into prev_verif from public.organizations where id = org_id;
        if prev_verif is null then
            raise exception 'NOT_FOUND';
        end if;
        if prev_verif <> 'VERIFIED' then
            raise exception 'VALIDATION';
        end if;
        update public.organizations
        set verification_status = 'EXPIRED',
            updated_at = now_ts
        where id = org_id;
    end if;

    if v_action = 'VERIFICATION_REJECTED' then
        perform public.m04_require_permission('organizations.review_verification');
        begin
            org_id := v_target::uuid;
        exception when others then
            raise exception 'VALIDATION';
        end;
        select verification_status into prev_verif from public.organizations where id = org_id;
        if prev_verif is null then
            raise exception 'NOT_FOUND';
        end if;
        if prev_verif <> 'PENDING' then
            raise exception 'VALIDATION';
        end if;
        update public.organizations
        set verification_status = 'REJECTED',
            updated_at = now_ts
        where id = org_id;
    end if;

    insert into public.moderation_actions (
        case_id, target_type, target_id, other_description,
        action_type, reason_code, reason_detail,
        applied_by_user_id, applied_at, expires_at
    ) values (
        p_case_id, v_type, v_target, nullif(trim(coalesce(p_other_description, '')), ''),
        v_action, v_reason, v_detail,
        actor, now_ts, p_expires_at
    )
    returning * into new_row;

    update public.moderation_cases
    set status = case when status in ('RESOLVED', 'CLOSED', 'DISMISSED') then status else 'ACTION_REQUIRED' end,
        updated_at = now_ts
    where id = p_case_id;

    perform public.m04_audit(
        'APPLY_MODERATION_ACTION', 'MODERATION_ACTION', new_row.id,
        null,
        jsonb_build_object(
            'case_id', p_case_id,
            'action_type', v_action,
            'target_type', v_type,
            'target_id', v_target
        ),
        v_reason, v_detail, null
    );

    return row_to_json(new_row)::jsonb;
end;
$$;

revoke all on function public.apply_moderation_action(uuid, text, text, text, text, text, timestamptz, text) from public;
grant execute on function public.apply_moderation_action(uuid, text, text, text, text, text, timestamptz, text) to authenticated;

create or replace function public.submit_moderation_appeal(
    p_action_id uuid,
    p_statement text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    a public.moderation_actions;
    v_statement text := trim(coalesce(p_statement, ''));
    new_row public.moderation_appeals;
    window_end timestamptz;
begin
    actor := public.m04_require_active_actor();

    select * into a from public.moderation_actions where id = p_action_id;
    if a.id is null then
        raise exception 'NOT_FOUND';
    end if;
    if a.reversed_at is not null then
        raise exception 'VALIDATION';
    end if;

    -- Affected party: only account targets can self-appeal in this foundation
    if a.target_type not in ('USER', 'USER_PROFILE') then
        raise exception 'FORBIDDEN';
    end if;
    begin
        if a.target_id::uuid is distinct from actor then
            raise exception 'FORBIDDEN';
        end if;
    exception
        when invalid_text_representation then
            raise exception 'FORBIDDEN';
    end;

    window_end := a.applied_at + interval '14 days';
    if timezone('utc', now()) > window_end then
        raise exception 'VALIDATION';
    end if;

    if char_length(v_statement) < 10 or char_length(v_statement) > 4000 then
        raise exception 'VALIDATION';
    end if;

    if exists (
        select 1 from public.moderation_appeals
        where action_id = p_action_id
          and status in ('SUBMITTED', 'UNDER_REVIEW')
    ) then
        raise exception 'VALIDATION';
    end if;

    insert into public.moderation_appeals (
        action_id, submitted_by_user_id, statement, status
    ) values (
        p_action_id, actor, v_statement, 'SUBMITTED'
    )
    returning * into new_row;

    perform public.m04_audit(
        'SUBMIT_MODERATION_APPEAL', 'MODERATION_APPEAL', new_row.id,
        null,
        jsonb_build_object('action_id', p_action_id),
        null, null, null
    );

    return row_to_json(new_row)::jsonb;
end;
$$;

revoke all on function public.submit_moderation_appeal(uuid, text) from public;
grant execute on function public.submit_moderation_appeal(uuid, text) to authenticated;

create or replace function public.list_moderation_appeals(
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
    actor uuid;
    lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
    v_status text := nullif(upper(trim(coalesce(p_status, ''))), '');
    payload jsonb;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.review_appeals');

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select ap.*
        from public.moderation_appeals ap
        where v_status is null or ap.status = v_status
        order by ap.created_at desc
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.list_moderation_appeals(text, int) from public;
grant execute on function public.list_moderation_appeals(text, int) to authenticated;

create or replace function public.assign_moderation_appeal(
    p_appeal_id uuid,
    p_assigned_to_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    prev public.moderation_appeals;
    updated public.moderation_appeals;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.review_appeals');

    select * into prev from public.moderation_appeals where id = p_appeal_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;
    if prev.status not in ('SUBMITTED', 'UNDER_REVIEW') then
        raise exception 'VALIDATION';
    end if;

    update public.moderation_appeals
    set assigned_to_user_id = p_assigned_to_user_id,
        status = 'UNDER_REVIEW'
    where id = p_appeal_id
    returning * into updated;

    perform public.m04_upsert_assignment(
        'MODERATION_APPEAL', p_appeal_id, p_assigned_to_user_id, actor
    );

    perform public.m04_audit(
        'ASSIGN_MODERATION_APPEAL', 'MODERATION_APPEAL', p_appeal_id,
        jsonb_build_object('assigned_to_user_id', prev.assigned_to_user_id),
        jsonb_build_object('assigned_to_user_id', updated.assigned_to_user_id),
        null, null, null
    );

    return row_to_json(updated)::jsonb;
end;
$$;

revoke all on function public.assign_moderation_appeal(uuid, uuid) from public;
grant execute on function public.assign_moderation_appeal(uuid, uuid) to authenticated;

create or replace function public.review_moderation_appeal(
    p_appeal_id uuid,
    p_decision text,
    p_decision_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_decision text := upper(trim(coalesce(p_decision, '')));
    v_reason text := trim(coalesce(p_decision_reason, ''));
    prev public.moderation_appeals;
    updated public.moderation_appeals;
    act public.moderation_actions;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('moderation.review_appeals');

    if v_decision not in ('UPHELD', 'OVERTURNED', 'PARTIALLY_OVERTURNED', 'REJECTED') then
        raise exception 'VALIDATION';
    end if;
    if char_length(v_reason) < 1 or char_length(v_reason) > 1000 then
        raise exception 'VALIDATION';
    end if;

    select * into prev from public.moderation_appeals where id = p_appeal_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;
    if prev.status not in ('SUBMITTED', 'UNDER_REVIEW') then
        raise exception 'VALIDATION';
    end if;

    select * into act from public.moderation_actions where id = prev.action_id;
    if act.id is null then
        raise exception 'NOT_FOUND';
    end if;
    if actor = act.applied_by_user_id then
        raise exception 'FORBIDDEN';
    end if;

    update public.moderation_appeals
    set status = v_decision,
        decision_reason = v_reason,
        reviewed_by_user_id = actor,
        reviewed_at = timezone('utc', now())
    where id = p_appeal_id
    returning * into updated;

    perform public.m04_audit(
        'REVIEW_MODERATION_APPEAL', 'MODERATION_APPEAL', p_appeal_id,
        jsonb_build_object('status', prev.status),
        jsonb_build_object('status', updated.status, 'decision_reason', v_reason),
        null, v_reason, null
    );

    return row_to_json(updated)::jsonb;
end;
$$;

revoke all on function public.review_moderation_appeal(uuid, text, text) from public;
grant execute on function public.review_moderation_appeal(uuid, text, text) to authenticated;

-- =============================================================================
-- 11) RPCs — organization verification
-- =============================================================================

create or replace function public.m04_ensure_verification_review(p_organization_id uuid)
returns public.organization_verification_reviews
language plpgsql
security definer
set search_path = public
as $$
declare
    org public.organizations;
    rev public.organization_verification_reviews;
    requester uuid;
begin
    select * into org from public.organizations where id = p_organization_id;
    if org.id is null then
        raise exception 'NOT_FOUND';
    end if;

    select * into rev
    from public.organization_verification_reviews
    where organization_id = p_organization_id
      and status in ('PENDING_REVIEW', 'MORE_INFO_REQUESTED')
    order by created_at desc
    limit 1;

    if rev.id is not null then
        return rev;
    end if;

    requester := coalesce(
        (select m.user_id
         from public.organization_memberships m
         where m.organization_id = p_organization_id
           and m.status = 'ACTIVE'
           and m.role_code = 'OWNER'
         limit 1),
        org.created_by
    );

    insert into public.organization_verification_reviews (
        organization_id, requested_by_user_id, status
    ) values (
        p_organization_id, requester, 'PENDING_REVIEW'
    )
    returning * into rev;

    return rev;
end;
$$;

revoke all on function public.m04_ensure_verification_review(uuid) from public;

create or replace function public.list_organization_verification_queue(p_limit int default 50)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
    org_rec record;
    payload jsonb := '[]'::jsonb;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('organizations.review_verification');

    for org_rec in
        select o.id
        from public.organizations o
        where o.verification_status = 'PENDING'
        order by o.updated_at asc
        limit lim
    loop
        perform public.m04_ensure_verification_review(org_rec.id);
    end loop;

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select
            r.id,
            r.organization_id,
            o.display_name,
            o.slug::text as slug,
            o.verification_status as org_verification_status,
            r.status,
            r.assigned_to_user_id,
            r.requested_by_user_id,
            r.created_at,
            r.updated_at
        from public.organization_verification_reviews r
        join public.organizations o on o.id = r.organization_id
        where r.status in ('PENDING_REVIEW', 'MORE_INFO_REQUESTED')
           or o.verification_status = 'PENDING'
        order by r.updated_at asc
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.list_organization_verification_queue(int) from public;
grant execute on function public.list_organization_verification_queue(int) to authenticated;

create or replace function public.get_organization_verification_review(p_review_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    r public.organization_verification_reviews;
    docs jsonb;
    can_sensitive boolean;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('organizations.review_verification');
    can_sensitive := public.has_permission('moderation.view_sensitive')
        or public.has_permission('support.view_sensitive');

    select * into r from public.organization_verification_reviews where id = p_review_id;
    if r.id is null then
        raise exception 'NOT_FOUND';
    end if;

    select coalesce(jsonb_agg(row_to_json(d)::jsonb), '[]'::jsonb)
    into docs
    from public.organization_verification_document_refs d
    where d.organization_id = r.organization_id
      and (d.review_id is null or d.review_id = r.id);

    return jsonb_build_object(
        'review', row_to_json(r)::jsonb,
        'documents', case when can_sensitive then docs else '[]'::jsonb end,
        'review_note', case when can_sensitive then r.review_note else null end
    );
end;
$$;

revoke all on function public.get_organization_verification_review(uuid) from public;
grant execute on function public.get_organization_verification_review(uuid) to authenticated;

create or replace function public.assign_organization_verification_review(
    p_review_id uuid,
    p_assigned_to_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    prev public.organization_verification_reviews;
    updated public.organization_verification_reviews;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('organizations.review_verification');

    select * into prev from public.organization_verification_reviews where id = p_review_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;

    if public.org_membership_is_active(prev.organization_id, p_assigned_to_user_id) then
        raise exception 'FORBIDDEN';
    end if;

    update public.organization_verification_reviews
    set assigned_to_user_id = p_assigned_to_user_id,
        updated_at = timezone('utc', now())
    where id = p_review_id
    returning * into updated;

    perform public.m04_upsert_assignment(
        'VERIFICATION_REVIEW', p_review_id, p_assigned_to_user_id, actor
    );

    perform public.m04_audit(
        'ASSIGN_VERIFICATION_REVIEW', 'VERIFICATION_REVIEW', p_review_id,
        jsonb_build_object('assigned_to_user_id', prev.assigned_to_user_id),
        jsonb_build_object('assigned_to_user_id', updated.assigned_to_user_id),
        null, null, null
    );

    return row_to_json(updated)::jsonb;
end;
$$;

revoke all on function public.assign_organization_verification_review(uuid, uuid) from public;
grant execute on function public.assign_organization_verification_review(uuid, uuid) to authenticated;

create or replace function public.record_organization_verification_decision(
    p_review_id uuid,
    p_decision text,
    p_review_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_decision text := upper(trim(coalesce(p_decision, '')));
    v_note text := nullif(trim(coalesce(p_review_note, '')), '');
    prev public.organization_verification_reviews;
    updated public.organization_verification_reviews;
    org public.organizations;
    new_verif text;
    new_review_status text;
    now_ts timestamptz := timezone('utc', now());
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('organizations.review_verification');

    if v_decision not in (
        'APPROVE', 'REJECT', 'REQUEST_MORE_INFORMATION', 'REVOKE', 'MARK_EXPIRED'
    ) then
        raise exception 'VALIDATION';
    end if;
    if v_note is not null and char_length(v_note) > 2000 then
        raise exception 'VALIDATION';
    end if;

    select * into prev from public.organization_verification_reviews where id = p_review_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;

    if public.org_membership_is_active(prev.organization_id, actor) then
        raise exception 'FORBIDDEN';
    end if;

    select * into org from public.organizations where id = prev.organization_id for update;
    if org.id is null then
        raise exception 'NOT_FOUND';
    end if;

    if v_decision = 'REVOKE' then
        perform public.m04_require_permission('organizations.revoke_verification');
    end if;

    case v_decision
        when 'APPROVE' then
            if org.verification_status <> 'PENDING' then
                raise exception 'VALIDATION';
            end if;
            new_verif := 'VERIFIED';
            new_review_status := 'APPROVED';
        when 'REJECT' then
            if org.verification_status <> 'PENDING' then
                raise exception 'VALIDATION';
            end if;
            new_verif := 'REJECTED';
            new_review_status := 'REJECTED';
        when 'REQUEST_MORE_INFORMATION' then
            if org.verification_status <> 'PENDING' then
                raise exception 'VALIDATION';
            end if;
            new_verif := 'PENDING'; -- stays PENDING
            new_review_status := 'MORE_INFO_REQUESTED';
        when 'REVOKE' then
            if org.verification_status <> 'VERIFIED' then
                raise exception 'VALIDATION';
            end if;
            new_verif := 'EXPIRED';
            new_review_status := 'REVOKED';
        when 'MARK_EXPIRED' then
            if org.verification_status not in ('VERIFIED', 'PENDING') then
                raise exception 'VALIDATION';
            end if;
            new_verif := 'EXPIRED';
            new_review_status := 'EXPIRED';
    end case;

    update public.organizations
    set verification_status = new_verif,
        updated_at = now_ts
    where id = org.id;

    update public.organization_verification_reviews
    set status = new_review_status,
        decision = v_decision,
        review_note = v_note,
        decided_at = now_ts,
        decided_by_user_id = actor,
        updated_at = now_ts
    where id = p_review_id
    returning * into updated;

    perform public.m04_audit(
        'RECORD_VERIFICATION_DECISION', 'VERIFICATION_REVIEW', p_review_id,
        jsonb_build_object(
            'org_verification_status', org.verification_status,
            'review_status', prev.status
        ),
        jsonb_build_object(
            'org_verification_status', new_verif,
            'review_status', new_review_status,
            'decision', v_decision
        ),
        lower(v_decision), v_note, null
    );

    return jsonb_build_object(
        'review', row_to_json(updated)::jsonb,
        'organization_id', org.id,
        'verification_status', new_verif
    );
end;
$$;

revoke all on function public.record_organization_verification_decision(uuid, text, text) from public;
grant execute on function public.record_organization_verification_decision(uuid, text, text) to authenticated;

-- =============================================================================
-- 12) RPCs — support
-- =============================================================================

create or replace function public.create_support_ticket(
    p_category text,
    p_subject text,
    p_description text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_cat text := upper(trim(coalesce(p_category, '')));
    v_subject text := trim(coalesce(p_subject, ''));
    v_desc text := trim(coalesce(p_description, ''));
    new_row public.support_tickets;
    msg_id uuid;
begin
    actor := public.m04_require_active_actor();

    if v_cat not in (
        'ACCOUNT_ACCESS', 'PROFILE', 'ORGANIZATION', 'TECHNICAL',
        'PRIVACY', 'SAFETY', 'CONTENT', 'OTHER'
    ) then
        raise exception 'VALIDATION';
    end if;
    if char_length(v_subject) < 3 or char_length(v_subject) > 160 then
        raise exception 'VALIDATION';
    end if;
    if char_length(v_desc) < 1 or char_length(v_desc) > 4000 then
        raise exception 'VALIDATION';
    end if;

    insert into public.support_tickets (
        requester_user_id, category, subject, description, priority, status
    ) values (
        actor, v_cat, v_subject, v_desc, 'NORMAL', 'OPEN'
    )
    returning * into new_row;

    insert into public.support_ticket_messages (
        ticket_id, author_user_id, visibility, body
    ) values (
        new_row.id, actor, 'REQUESTER_VISIBLE', v_desc
    )
    returning id into msg_id;

    perform public.m04_audit(
        'CREATE_SUPPORT_TICKET', 'SUPPORT_TICKET', new_row.id,
        null,
        jsonb_build_object('category', v_cat, 'subject', v_subject),
        null, null, null
    );

    return jsonb_build_object(
        'ticket', row_to_json(new_row)::jsonb,
        'initial_message_id', msg_id
    );
end;
$$;

revoke all on function public.create_support_ticket(text, text, text) from public;
grant execute on function public.create_support_ticket(text, text, text) to authenticated;

create or replace function public.get_my_support_tickets(p_limit int default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
    payload jsonb;
begin
    actor := public.m04_require_active_actor();

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select
            s.id, s.category, s.subject, s.priority, s.status,
            s.created_at, s.updated_at, s.resolved_at, s.closed_at
        from public.support_tickets s
        where s.requester_user_id = actor
        order by s.updated_at desc
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.get_my_support_tickets(int) from public;
grant execute on function public.get_my_support_tickets(int) to authenticated;

create or replace function public.get_support_ticket_for_requester(p_ticket_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    t public.support_tickets;
    msgs jsonb;
begin
    actor := public.m04_require_active_actor();

    select * into t from public.support_tickets where id = p_ticket_id;
    if t.id is null or t.requester_user_id <> actor then
        raise exception 'FORBIDDEN';
    end if;

    -- INTERNAL messages never returned to requester
    select coalesce(jsonb_agg(row_to_json(m)::jsonb order by m.created_at), '[]'::jsonb)
    into msgs
    from public.support_ticket_messages m
    where m.ticket_id = p_ticket_id
      and m.visibility = 'REQUESTER_VISIBLE';

    return jsonb_build_object(
        'ticket', jsonb_build_object(
            'id', t.id,
            'category', t.category,
            'subject', t.subject,
            'description', t.description,
            'priority', t.priority,
            'status', t.status,
            'created_at', t.created_at,
            'updated_at', t.updated_at,
            'resolved_at', t.resolved_at,
            'closed_at', t.closed_at,
            'close_reason_code', t.close_reason_code
        ),
        'messages', msgs
    );
end;
$$;

revoke all on function public.get_support_ticket_for_requester(uuid) from public;
grant execute on function public.get_support_ticket_for_requester(uuid) to authenticated;

create or replace function public.list_support_queue(
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
    actor uuid;
    lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
    v_status text := nullif(upper(trim(coalesce(p_status, ''))), '');
    can_sensitive boolean;
    payload jsonb;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('support.view');
    can_sensitive := public.has_permission('support.view_sensitive');

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select
            s.id,
            s.requester_user_id,
            s.category,
            s.subject,
            s.priority,
            s.status,
            s.assigned_to_user_id,
            s.created_at,
            s.updated_at
        from public.support_tickets s
        where (v_status is null or s.status = v_status)
          and (
              can_sensitive
              or s.category not in ('PRIVACY', 'SAFETY')
          )
          and s.status not in ('CLOSED')
        order by
            case s.priority
                when 'URGENT' then 1
                when 'HIGH' then 2
                when 'NORMAL' then 3
                else 4
            end,
            s.updated_at asc
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.list_support_queue(text, int) from public;
grant execute on function public.list_support_queue(text, int) to authenticated;

create or replace function public.get_support_ticket_for_staff(p_ticket_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    t public.support_tickets;
    msgs jsonb;
    can_sensitive boolean;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('support.view');
    can_sensitive := public.has_permission('support.view_sensitive');

    select * into t from public.support_tickets where id = p_ticket_id;
    if t.id is null then
        raise exception 'NOT_FOUND';
    end if;

    if t.category in ('PRIVACY', 'SAFETY') and not can_sensitive then
        raise exception 'FORBIDDEN';
    end if;

    select coalesce(jsonb_agg(row_to_json(m)::jsonb order by m.created_at), '[]'::jsonb)
    into msgs
    from public.support_ticket_messages m
    where m.ticket_id = p_ticket_id;

    return jsonb_build_object(
        'ticket', row_to_json(t)::jsonb,
        'messages', msgs
    );
end;
$$;

revoke all on function public.get_support_ticket_for_staff(uuid) from public;
grant execute on function public.get_support_ticket_for_staff(uuid) to authenticated;

create or replace function public.assign_support_ticket(
    p_ticket_id uuid,
    p_assigned_to_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    prev public.support_tickets;
    updated public.support_tickets;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('support.manage');

    select * into prev from public.support_tickets where id = p_ticket_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;

    update public.support_tickets
    set assigned_to_user_id = p_assigned_to_user_id,
        status = case when status = 'OPEN' then 'IN_PROGRESS' else status end,
        updated_at = timezone('utc', now())
    where id = p_ticket_id
    returning * into updated;

    perform public.m04_upsert_assignment(
        'SUPPORT_TICKET', p_ticket_id, p_assigned_to_user_id, actor
    );

    perform public.m04_audit(
        'ASSIGN_SUPPORT_TICKET', 'SUPPORT_TICKET', p_ticket_id,
        jsonb_build_object('assigned_to_user_id', prev.assigned_to_user_id),
        jsonb_build_object('assigned_to_user_id', updated.assigned_to_user_id),
        null, null, null
    );

    return row_to_json(updated)::jsonb;
end;
$$;

revoke all on function public.assign_support_ticket(uuid, uuid) from public;
grant execute on function public.assign_support_ticket(uuid, uuid) to authenticated;

create or replace function public.change_support_ticket_status(
    p_ticket_id uuid,
    p_status text,
    p_close_reason_code text default null,
    p_priority text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_status text := upper(trim(coalesce(p_status, '')));
    v_close text := nullif(lower(trim(coalesce(p_close_reason_code, ''))), '');
    v_priority text;
    prev public.support_tickets;
    updated public.support_tickets;
    now_ts timestamptz := timezone('utc', now());
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('support.manage');

    if v_status not in (
        'OPEN', 'IN_PROGRESS', 'WAITING_USER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED'
    ) then
        raise exception 'VALIDATION';
    end if;

    select * into prev from public.support_tickets where id = p_ticket_id for update;
    if prev.id is null then
        raise exception 'NOT_FOUND';
    end if;

    if v_status = 'CLOSED' then
        if prev.status = 'CLOSED' then
            raise exception 'VALIDATION';
        end if;
        if prev.status <> 'RESOLVED' and v_close is null then
            raise exception 'VALIDATION';
        end if;
    end if;

    v_priority := coalesce(nullif(upper(trim(coalesce(p_priority, ''))), ''), prev.priority);
    if v_priority not in ('LOW', 'NORMAL', 'HIGH', 'URGENT') then
        raise exception 'VALIDATION';
    end if;

    update public.support_tickets
    set status = v_status,
        priority = v_priority,
        close_reason_code = case when v_status = 'CLOSED' then coalesce(v_close, close_reason_code) else close_reason_code end,
        resolved_at = case when v_status = 'RESOLVED' then now_ts else resolved_at end,
        closed_at = case when v_status = 'CLOSED' then now_ts else closed_at end,
        updated_at = now_ts
    where id = p_ticket_id
    returning * into updated;

    perform public.m04_audit(
        'CHANGE_SUPPORT_TICKET_STATUS', 'SUPPORT_TICKET', p_ticket_id,
        jsonb_build_object('status', prev.status, 'priority', prev.priority),
        jsonb_build_object('status', updated.status, 'priority', updated.priority),
        v_close, null, null
    );

    return row_to_json(updated)::jsonb;
end;
$$;

revoke all on function public.change_support_ticket_status(uuid, text, text, text) from public;
grant execute on function public.change_support_ticket_status(uuid, text, text, text) to authenticated;

create or replace function public.add_support_requester_message(
    p_ticket_id uuid,
    p_body text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_body text := trim(coalesce(p_body, ''));
    t public.support_tickets;
    new_row public.support_ticket_messages;
begin
    actor := public.m04_require_active_actor();

    select * into t from public.support_tickets where id = p_ticket_id for update;
    if t.id is null or t.requester_user_id <> actor then
        raise exception 'FORBIDDEN';
    end if;
    if t.status in ('CLOSED') then
        raise exception 'VALIDATION';
    end if;
    if char_length(v_body) < 1 or char_length(v_body) > 4000 then
        raise exception 'VALIDATION';
    end if;

    insert into public.support_ticket_messages (
        ticket_id, author_user_id, visibility, body
    ) values (
        p_ticket_id, actor, 'REQUESTER_VISIBLE', v_body
    )
    returning * into new_row;

    update public.support_tickets
    set status = case when status = 'WAITING_USER' then 'IN_PROGRESS' else status end,
        updated_at = timezone('utc', now())
    where id = p_ticket_id;

    return row_to_json(new_row)::jsonb;
end;
$$;

revoke all on function public.add_support_requester_message(uuid, text) from public;
grant execute on function public.add_support_requester_message(uuid, text) to authenticated;

create or replace function public.add_support_internal_message(
    p_ticket_id uuid,
    p_body text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid;
    v_body text := trim(coalesce(p_body, ''));
    new_row public.support_ticket_messages;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('support.manage');

    if not exists (select 1 from public.support_tickets where id = p_ticket_id) then
        raise exception 'NOT_FOUND';
    end if;
    if char_length(v_body) < 1 or char_length(v_body) > 4000 then
        raise exception 'VALIDATION';
    end if;

    insert into public.support_ticket_messages (
        ticket_id, author_user_id, visibility, body
    ) values (
        p_ticket_id, actor, 'INTERNAL', v_body
    )
    returning * into new_row;

    update public.support_tickets
    set updated_at = timezone('utc', now())
    where id = p_ticket_id;

    perform public.m04_audit(
        'ADD_SUPPORT_INTERNAL_MESSAGE', 'SUPPORT_TICKET', p_ticket_id,
        null,
        jsonb_build_object('message_id', new_row.id),
        null, null, null
    );

    return row_to_json(new_row)::jsonb;
end;
$$;

revoke all on function public.add_support_internal_message(uuid, text) from public;
grant execute on function public.add_support_internal_message(uuid, text) to authenticated;

-- =============================================================================
-- 13) RPCs — audit read
-- =============================================================================

create or replace function public.list_administrative_events(
    p_resource_type text default null,
    p_resource_id uuid default null,
    p_limit int default 50
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    lim int := least(greatest(coalesce(p_limit, 50), 1), 100);
    v_type text := nullif(upper(trim(coalesce(p_resource_type, ''))), '');
    payload jsonb;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('audit.view');

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select
            a.id,
            a.actor_user_id,
            a.action,
            a.resource_type,
            a.resource_id,
            a.previous_value,
            a.new_value,
            a.reason_code,
            a.created_at
        from public.administrative_audit_log a
        where (v_type is null or a.resource_type = v_type)
          and (p_resource_id is null or a.resource_id = p_resource_id)
        order by a.created_at desc
        limit lim
    ) t;

    return coalesce(payload, '[]'::jsonb);
end;
$$;

revoke all on function public.list_administrative_events(text, uuid, int) from public;
grant execute on function public.list_administrative_events(text, uuid, int) to authenticated;

-- =============================================================================
-- 14) RLS — new tables (deny-by-default writes)
-- =============================================================================

alter table public.moderation_cases enable row level security;
alter table public.moderation_case_reports enable row level security;
alter table public.moderation_actions enable row level security;
alter table public.moderation_appeals enable row level security;
alter table public.moderation_evidence_refs enable row level security;
alter table public.moderation_case_notes enable row level security;
alter table public.organization_verification_reviews enable row level security;
alter table public.organization_verification_document_refs enable row level security;
alter table public.support_tickets enable row level security;
alter table public.support_ticket_messages enable row level security;
alter table public.administrative_assignments enable row level security;
alter table public.administrative_audit_log enable row level security;

-- moderation_cases
drop policy if exists moderation_cases_select on public.moderation_cases;
create policy moderation_cases_select on public.moderation_cases
    for select to authenticated
    using (public.has_permission('moderation.view'));
drop policy if exists moderation_cases_write_deny on public.moderation_cases;
create policy moderation_cases_insert_deny on public.moderation_cases
    for insert to authenticated with check (false);
create policy moderation_cases_update_deny on public.moderation_cases
    for update to authenticated using (false);
create policy moderation_cases_delete_deny on public.moderation_cases
    for delete to authenticated using (false);

-- moderation_case_reports
drop policy if exists moderation_case_reports_select on public.moderation_case_reports;
create policy moderation_case_reports_select on public.moderation_case_reports
    for select to authenticated
    using (public.has_permission('moderation.view'));
create policy moderation_case_reports_insert_deny on public.moderation_case_reports
    for insert to authenticated with check (false);
create policy moderation_case_reports_update_deny on public.moderation_case_reports
    for update to authenticated using (false);
create policy moderation_case_reports_delete_deny on public.moderation_case_reports
    for delete to authenticated using (false);

-- moderation_actions
drop policy if exists moderation_actions_select on public.moderation_actions;
create policy moderation_actions_select on public.moderation_actions
    for select to authenticated
    using (
        public.has_permission('moderation.view')
        or (
            target_type in ('USER', 'USER_PROFILE')
            and target_id = auth.uid()::text
        )
    );
create policy moderation_actions_insert_deny on public.moderation_actions
    for insert to authenticated with check (false);
create policy moderation_actions_update_deny on public.moderation_actions
    for update to authenticated using (false);
create policy moderation_actions_delete_deny on public.moderation_actions
    for delete to authenticated using (false);

-- moderation_appeals
drop policy if exists moderation_appeals_select on public.moderation_appeals;
create policy moderation_appeals_select on public.moderation_appeals
    for select to authenticated
    using (
        submitted_by_user_id = auth.uid()
        or public.has_permission('moderation.review_appeals')
        or public.has_permission('moderation.view')
    );
create policy moderation_appeals_insert_deny on public.moderation_appeals
    for insert to authenticated with check (false);
create policy moderation_appeals_update_deny on public.moderation_appeals
    for update to authenticated using (false);
create policy moderation_appeals_delete_deny on public.moderation_appeals
    for delete to authenticated using (false);

-- evidence (sensitive)
drop policy if exists moderation_evidence_refs_select on public.moderation_evidence_refs;
create policy moderation_evidence_refs_select on public.moderation_evidence_refs
    for select to authenticated
    using (public.has_permission('moderation.view_sensitive'));
create policy moderation_evidence_refs_insert_deny on public.moderation_evidence_refs
    for insert to authenticated with check (false);
create policy moderation_evidence_refs_update_deny on public.moderation_evidence_refs
    for update to authenticated using (false);
create policy moderation_evidence_refs_delete_deny on public.moderation_evidence_refs
    for delete to authenticated using (false);

-- notes (sensitive)
drop policy if exists moderation_case_notes_select on public.moderation_case_notes;
create policy moderation_case_notes_select on public.moderation_case_notes
    for select to authenticated
    using (public.has_permission('moderation.view_sensitive'));
create policy moderation_case_notes_insert_deny on public.moderation_case_notes
    for insert to authenticated with check (false);
create policy moderation_case_notes_update_deny on public.moderation_case_notes
    for update to authenticated using (false);
create policy moderation_case_notes_delete_deny on public.moderation_case_notes
    for delete to authenticated using (false);

-- verification reviews
drop policy if exists organization_verification_reviews_select on public.organization_verification_reviews;
create policy organization_verification_reviews_select on public.organization_verification_reviews
    for select to authenticated
    using (public.has_permission('organizations.review_verification'));
create policy organization_verification_reviews_insert_deny on public.organization_verification_reviews
    for insert to authenticated with check (false);
create policy organization_verification_reviews_update_deny on public.organization_verification_reviews
    for update to authenticated using (false);
create policy organization_verification_reviews_delete_deny on public.organization_verification_reviews
    for delete to authenticated using (false);

-- verification docs (sensitive)
drop policy if exists organization_verification_document_refs_select on public.organization_verification_document_refs;
create policy organization_verification_document_refs_select on public.organization_verification_document_refs
    for select to authenticated
    using (
        public.has_permission('organizations.review_verification')
        and (
            public.has_permission('moderation.view_sensitive')
            or public.has_permission('support.view_sensitive')
        )
    );
create policy organization_verification_document_refs_insert_deny on public.organization_verification_document_refs
    for insert to authenticated with check (false);
create policy organization_verification_document_refs_update_deny on public.organization_verification_document_refs
    for update to authenticated using (false);
create policy organization_verification_document_refs_delete_deny on public.organization_verification_document_refs
    for delete to authenticated using (false);

-- support tickets
drop policy if exists support_tickets_select on public.support_tickets;
create policy support_tickets_select on public.support_tickets
    for select to authenticated
    using (
        requester_user_id = auth.uid()
        or (
            public.has_permission('support.view')
            and (
                category not in ('PRIVACY', 'SAFETY')
                or public.has_permission('support.view_sensitive')
            )
        )
    );
create policy support_tickets_insert_deny on public.support_tickets
    for insert to authenticated with check (false);
create policy support_tickets_update_deny on public.support_tickets
    for update to authenticated using (false);
create policy support_tickets_delete_deny on public.support_tickets
    for delete to authenticated using (false);

-- support messages: requester only REQUESTER_VISIBLE; staff with support.view
drop policy if exists support_ticket_messages_select on public.support_ticket_messages;
create policy support_ticket_messages_select on public.support_ticket_messages
    for select to authenticated
    using (
        (
            visibility = 'REQUESTER_VISIBLE'
            and exists (
                select 1 from public.support_tickets t
                where t.id = ticket_id and t.requester_user_id = auth.uid()
            )
        )
        or public.has_permission('support.view')
        or public.has_permission('support.manage')
    );
create policy support_ticket_messages_insert_deny on public.support_ticket_messages
    for insert to authenticated with check (false);
create policy support_ticket_messages_update_deny on public.support_ticket_messages
    for update to authenticated using (false);
create policy support_ticket_messages_delete_deny on public.support_ticket_messages
    for delete to authenticated using (false);

-- assignments
drop policy if exists administrative_assignments_select on public.administrative_assignments;
create policy administrative_assignments_select on public.administrative_assignments
    for select to authenticated
    using (
        assigned_to = auth.uid()
        or public.has_permission('moderation.view')
        or public.has_permission('support.view')
        or public.has_permission('organizations.review_verification')
    );
create policy administrative_assignments_insert_deny on public.administrative_assignments
    for insert to authenticated with check (false);
create policy administrative_assignments_update_deny on public.administrative_assignments
    for update to authenticated using (false);
create policy administrative_assignments_delete_deny on public.administrative_assignments
    for delete to authenticated using (false);

-- audit log: no direct select (RPC only) — deny-by-default
drop policy if exists administrative_audit_log_select on public.administrative_audit_log;
create policy administrative_audit_log_select on public.administrative_audit_log
    for select to authenticated
    using (false);
create policy administrative_audit_log_insert_deny on public.administrative_audit_log
    for insert to authenticated with check (false);
create policy administrative_audit_log_update_deny on public.administrative_audit_log
    for update to authenticated using (false);
create policy administrative_audit_log_delete_deny on public.administrative_audit_log
    for delete to authenticated using (false);

-- Grants / revokes
revoke insert, update, delete on public.moderation_cases from authenticated;
revoke insert, update, delete on public.moderation_case_reports from authenticated;
revoke insert, update, delete on public.moderation_actions from authenticated;
revoke insert, update, delete on public.moderation_appeals from authenticated;
revoke insert, update, delete on public.moderation_evidence_refs from authenticated;
revoke insert, update, delete on public.moderation_case_notes from authenticated;
revoke insert, update, delete on public.organization_verification_reviews from authenticated;
revoke insert, update, delete on public.organization_verification_document_refs from authenticated;
revoke insert, update, delete on public.support_tickets from authenticated;
revoke insert, update, delete on public.support_ticket_messages from authenticated;
revoke insert, update, delete on public.administrative_assignments from authenticated;
revoke all on public.administrative_audit_log from authenticated;

grant select on public.moderation_cases to authenticated;
grant select on public.moderation_case_reports to authenticated;
grant select on public.moderation_actions to authenticated;
grant select on public.moderation_appeals to authenticated;
grant select on public.moderation_evidence_refs to authenticated;
grant select on public.moderation_case_notes to authenticated;
grant select on public.organization_verification_reviews to authenticated;
grant select on public.organization_verification_document_refs to authenticated;
grant select on public.support_tickets to authenticated;
grant select on public.support_ticket_messages to authenticated;
grant select on public.administrative_assignments to authenticated;

comment on table public.moderation_cases is 'M04 Etapa 3 — casos de moderación; mutación solo vía RPC.';
comment on table public.administrative_audit_log is 'M04 — auditoría administrativa; escritura solo helpers/RPC SECURITY DEFINER; lectura vía list_administrative_events.';
