-- LeoVer — M06 Etapa 3: seguridad, outbox y bandeja in-app server-side
-- No edita migraciones 001-025. Evoluciona la tabla legacy public.notifications.

begin;

-- ---------------------------------------------------------------------------
-- Helpers allowlist / sanitizacion
-- ---------------------------------------------------------------------------

create or replace function public.m06_notification_categories()
returns text[]
language sql
stable
set search_path = public
as $$
    select array[
        'ACCOUNT','SECURITY','ORGANIZATION','INVITATION','MODERATION','APPEAL',
        'VERIFICATION','SUPPORT','PET','ADOPTION','FOSTER','SHELTER','LOST_FOUND',
        'DONATION','EVENT','SOCIAL','MESSAGE','SERVICE','APPOINTMENT','PAYMENT',
        'MARKETPLACE','SYSTEM','OTHER'
    ]::text[];
$$;

create or replace function public.m06_notification_priorities()
returns text[]
language sql
stable
set search_path = public
as $$ select array['LOW','NORMAL','HIGH','URGENT']::text[]; $$;

create or replace function public.m06_notification_sensitivities()
returns text[]
language sql
stable
set search_path = public
as $$ select array['PUBLIC_SUMMARY','PRIVATE','SENSITIVE','SECURITY_CRITICAL']::text[]; $$;

create or replace function public.m06_notification_states()
returns text[]
language sql
stable
set search_path = public
as $$ select array['UNREAD','READ','ARCHIVED','DELETED','EXPIRED']::text[]; $$;

create or replace function public.m06_deep_link_routes()
returns text[]
language sql
stable
set search_path = public
as $$
    select array[
        'NOTIFICATIONS_INBOX','PROFILE','ORGANIZATION','ORGANIZATION_INVITATION',
        'MODERATION_QUEUE','MODERATION_CASE','MODERATION_APPEAL',
        'ORGANIZATION_VERIFICATION','SUPPORT_TICKET','PET','ADOPTION',
        'LOST_FOUND_CASE','FILE_RESOURCE','CHAT','SAFE_HOME'
    ]::text[];
$$;

create or replace function public.m06_known_staff_permission(p_permission text)
returns boolean
language sql
stable
set search_path = public
as $$
    select p_permission is not null
       and length(trim(p_permission)) between 3 and 80
       and trim(p_permission) ~ '^[a-z][a-z0-9_.:-]*$'
       and (
            exists (select 1 from public.permissions p where p.code = trim(p_permission))
            or trim(p_permission) in (
                'moderation.view','moderation.manage','moderation.actions',
                'review_appeals','support.view','support.view_sensitive',
                'support.manage','organization.verification.review',
                'files.verification.view'
            )
       );
$$;

create or replace function public.m06_require_active_actor()
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_actor uuid := auth.uid();
begin
    if v_actor is null then
        raise exception using errcode = '28000', message = 'M06_NOT_AUTHENTICATED';
    end if;

    if exists (
        select 1
        from public.users u
        where u.id = v_actor
          and coalesce(u.account_status, 'ACTIVE') not in ('ACTIVE', 'PENDING_VERIFICATION')
    ) then
        raise exception using errcode = '42501', message = 'M06_ACTOR_NOT_ACTIVE';
    end if;

    return v_actor;
end;
$$;

create or replace function public.m06_validate_payload(p_payload jsonb)
returns boolean
language plpgsql
immutable
set search_path = public
as $$
declare
    v_text text := lower(coalesce(p_payload::text, '{}'));
begin
    if p_payload is null then
        return true;
    end if;
    if jsonb_typeof(p_payload) <> 'object' then
        raise exception using errcode = '22023', message = 'M06_PAYLOAD_OBJECT_REQUIRED';
    end if;
    if length(p_payload::text) > 8192 then
        raise exception using errcode = '22023', message = 'M06_PAYLOAD_TOO_LARGE';
    end if;
    if v_text ~ '(https?://|www\.|content://|file://|intent:|leover://)' then
        raise exception using errcode = '22023', message = 'M06_PAYLOAD_URL_REJECTED';
    end if;
    if v_text ~ '(signed[_-]?url|x-amz-signature|token|secret|apikey|api[_-]?key|authorization|bearer\s+[a-z0-9._-]+)' then
        raise exception using errcode = '22023', message = 'M06_PAYLOAD_SECRET_REJECTED';
    end if;
    if v_text ~ '(select\s+.*\s+from|insert\s+into|update\s+.+\s+set|delete\s+from|drop\s+table|alter\s+table|stacktrace|exception at|org\.postgresql|postgrest)' then
        raise exception using errcode = '22023', message = 'M06_PAYLOAD_TECHNICAL_LEAK_REJECTED';
    end if;
    if p_payload ?| array[
        'email','phone','phone_number','document','dni','address',
        'full_name','fullName','raw_token','access_token','refresh_token',
        'signed_url','signedUrl','sql','stack_trace','stackTrace'
    ] then
        raise exception using errcode = '22023', message = 'M06_PAYLOAD_PII_REJECTED';
    end if;
    return true;
end;
$$;

create or replace function public.m06_sanitize_error_code(p_code text)
returns text
language sql
immutable
set search_path = public
as $$
    select upper(regexp_replace(coalesce(nullif(trim(p_code), ''), 'UNKNOWN'), '[^A-Za-z0-9_.:-]', '_', 'g'));
$$;

-- ---------------------------------------------------------------------------
-- Evolucion public.notifications como bandeja por usuario
-- ---------------------------------------------------------------------------

alter table public.notifications
    add column if not exists event_id uuid,
    add column if not exists category text not null default 'OTHER',
    add column if not exists priority text not null default 'NORMAL',
    add column if not exists sensitivity text not null default 'PRIVATE',
    add column if not exists state text not null default 'UNREAD',
    add column if not exists is_read boolean not null default false,
    add column if not exists archived_at timestamptz,
    add column if not exists deleted_at timestamptz,
    add column if not exists expires_at timestamptz,
    add column if not exists organization_id uuid,
    add column if not exists deep_link_type text not null default 'SAFE_HOME',
    add column if not exists deep_link_resource_type text,
    add column if not exists deep_link_resource_id text,
    add column if not exists deep_link_required_permission text,
    add column if not exists payload jsonb not null default '{}'::jsonb,
    add column if not exists deduplication_key text,
    add column if not exists created_by uuid references auth.users(id) on delete set null,
    add column if not exists updated_at timestamptz not null default timezone('utc', now());

update public.notifications
set is_read = read_at is not null,
    state = case
        when deleted_at is not null then 'DELETED'
        when archived_at is not null then 'ARCHIVED'
        when read_at is not null then 'READ'
        else coalesce(nullif(state, ''), 'UNREAD')
    end,
    category = case
        when category = 'OTHER' and type in ('FRIEND_REQUEST','FRIEND_ACCEPTED') then 'SOCIAL'
        when category = 'OTHER' and type = 'CHAT_MESSAGE' then 'MESSAGE'
        when category = 'OTHER' and type in ('ADOPTION_REQUEST','FOSTER_REQUEST') then 'ADOPTION'
        when category = 'OTHER' and type = 'SIGHTING' then 'LOST_FOUND'
        when category = 'OTHER' and type = 'BOOKING' then 'SERVICE'
        when category = 'OTHER' and type = 'SYSTEM' then 'SYSTEM'
        else category
    end,
    deduplication_key = coalesce(deduplication_key, 'legacy:' || id::text),
    updated_at = coalesce(updated_at, created_at, timezone('utc', now()));

alter table public.notifications
    drop constraint if exists notifications_m06_category_check,
    add constraint notifications_m06_category_check check (category = any(public.m06_notification_categories())),
    drop constraint if exists notifications_m06_priority_check,
    add constraint notifications_m06_priority_check check (priority = any(public.m06_notification_priorities())),
    drop constraint if exists notifications_m06_sensitivity_check,
    add constraint notifications_m06_sensitivity_check check (sensitivity = any(public.m06_notification_sensitivities())),
    drop constraint if exists notifications_m06_state_check,
    add constraint notifications_m06_state_check check (state = any(public.m06_notification_states())),
    drop constraint if exists notifications_m06_deep_link_check,
    add constraint notifications_m06_deep_link_check check (deep_link_type = any(public.m06_deep_link_routes())),
    drop constraint if exists notifications_m06_payload_check,
    add constraint notifications_m06_payload_check check (public.m06_validate_payload(payload));

create index if not exists notifications_user_state_idx
    on public.notifications(user_id, state, created_at desc);
create index if not exists notifications_user_unread_idx
    on public.notifications(user_id, created_at desc)
    where state = 'UNREAD' and deleted_at is null;
create unique index if not exists notifications_user_dedup_active_idx
    on public.notifications(user_id, deduplication_key)
    where deduplication_key is not null and deleted_at is null;

create or replace function public.m06_sync_notification_read_state()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if new.deleted_at is not null then
        new.state := 'DELETED';
    elsif new.archived_at is not null then
        new.state := 'ARCHIVED';
    elsif new.read_at is not null or new.is_read then
        new.state := 'READ';
        new.is_read := true;
        new.read_at := coalesce(new.read_at, timezone('utc', now()));
    else
        if new.state = 'READ' then
            new.is_read := true;
            new.read_at := coalesce(new.read_at, timezone('utc', now()));
        else
            new.is_read := false;
            if new.state = 'UNREAD' then
                new.read_at := null;
            end if;
        end if;
    end if;
    if new.expires_at is not null and new.expires_at <= timezone('utc', now()) and new.state = 'UNREAD' then
        new.state := 'EXPIRED';
    end if;
    new.updated_at := timezone('utc', now());
    return new;
end;
$$;

drop trigger if exists m06_notifications_sync_read_state on public.notifications;
create trigger m06_notifications_sync_read_state
before insert or update on public.notifications
for each row execute function public.m06_sync_notification_read_state();

-- ---------------------------------------------------------------------------
-- Tablas M06
-- ---------------------------------------------------------------------------

create table if not exists public.notification_events (
    id uuid primary key default gen_random_uuid(),
    event_key text not null,
    origin_module text not null check (origin_module in ('M01','M02','M03','M04','M05')),
    origin_type text not null,
    category text not null check (category = any(public.m06_notification_categories())),
    priority text not null check (priority = any(public.m06_notification_priorities())),
    sensitivity text not null check (sensitivity = any(public.m06_notification_sensitivities())),
    resource_type text,
    resource_id text,
    organization_id uuid,
    payload jsonb not null default '{}'::jsonb check (public.m06_validate_payload(payload)),
    deduplication_key text not null,
    idempotency_key text not null unique,
    occurred_at timestamptz not null default timezone('utc', now()),
    expires_at timestamptz,
    created_by uuid references auth.users(id) on delete set null,
    is_internal boolean not null default false,
    created_at timestamptz not null default timezone('utc', now()),
    check (length(trim(event_key)) > 0),
    check (length(trim(deduplication_key)) > 0),
    check (length(trim(idempotency_key)) > 0),
    check (expires_at is null or expires_at > occurred_at),
    check (not is_internal or sensitivity in ('SENSITIVE','SECURITY_CRITICAL'))
);

create table if not exists public.notification_preferences (
    user_id uuid not null references auth.users(id) on delete cascade,
    category text not null check (category = any(public.m06_notification_categories())),
    in_app_enabled boolean not null default true,
    push_enabled boolean not null default true,
    email_enabled boolean not null default false,
    marketing_consent boolean not null default false,
    quiet_hours_start time,
    quiet_hours_end time,
    quiet_hours_days int[] not null default array[1,2,3,4,5,6,7],
    timezone text not null default 'UTC',
    updated_at timestamptz not null default timezone('utc', now()),
    primary key(user_id, category),
    check (timezone ~ '^[A-Za-z0-9_./+-]{1,64}$'),
    check (category not in ('ACCOUNT','SECURITY','INVITATION','MODERATION','APPEAL','VERIFICATION','PAYMENT') or in_app_enabled)
);

create table if not exists public.notification_device_installations (
    id uuid primary key default gen_random_uuid(),
    installation_id text not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    platform text not null default 'ANDROID',
    token_protected_or_token_reference text,
    token_fingerprint text not null,
    enabled boolean not null default true,
    app_version text,
    device_label text,
    last_seen_at timestamptz not null default timezone('utc', now()),
    revoked_at timestamptz,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    unique(installation_id),
    check (platform in ('ANDROID','IOS','WEB','UNKNOWN')),
    check (installation_id ~ '^[A-Za-z0-9_.:-]{1,128}$'),
    check (token_fingerprint ~ '^[a-f0-9]{8,128}$')
);

create table if not exists public.notification_deliveries (
    id uuid primary key default gen_random_uuid(),
    notification_id uuid not null references public.notifications(id) on delete cascade,
    channel text not null check (channel in ('IN_APP','PUSH','EMAIL','LOCAL')),
    installation_id text,
    status text not null default 'PENDING' check (status in (
        'PENDING','PROCESSING','DELIVERED','FAILED_RETRYABLE','FAILED_PERMANENT',
        'SKIPPED_PREFERENCE','SKIPPED_PERMISSION','SKIPPED_EXPIRED',
        'SKIPPED_QUIET_HOURS','CANCELLED','DEAD_LETTER'
    )),
    attempt_count int not null default 0 check (attempt_count >= 0),
    next_attempt_at timestamptz,
    last_attempt_at timestamptz,
    delivered_at timestamptz,
    failure_code text,
    provider_message_id text,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.notification_outbox (
    id uuid primary key default gen_random_uuid(),
    event_id uuid not null references public.notification_events(id) on delete cascade,
    state text not null default 'PENDING' check (state in (
        'PENDING','CLAIMED','PROCESSED','FAILED_RETRYABLE','FAILED_PERMANENT','DEAD','CANCELLED'
    )),
    attempt_count int not null default 0 check (attempt_count >= 0),
    available_at timestamptz not null default timezone('utc', now()),
    claimed_at timestamptz,
    claimed_by text,
    last_error_code text,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    unique(event_id)
);

create table if not exists public.notification_dead_letters (
    id uuid primary key default gen_random_uuid(),
    outbox_id uuid references public.notification_outbox(id) on delete set null,
    event_id uuid references public.notification_events(id) on delete set null,
    error_code text not null,
    attempts int not null default 0,
    sanitized_context jsonb not null default '{}'::jsonb check (public.m06_validate_payload(sanitized_context)),
    reprocess_state text not null default 'PENDING' check (reprocess_state in ('PENDING','REVIEWED','REQUEUED','DISCARDED')),
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.notification_access_audit (
    id uuid primary key default gen_random_uuid(),
    actor_id uuid references auth.users(id) on delete set null,
    notification_id uuid,
    event_id uuid,
    action text not null,
    decision text not null,
    reason_code text,
    created_at timestamptz not null default timezone('utc', now()),
    check (action ~ '^[A-Z0-9_.:-]{2,80}$'),
    check (decision in ('ALLOWED','DENIED'))
);

create index if not exists notification_events_origin_idx
    on public.notification_events(origin_module, origin_type, occurred_at desc);
create index if not exists notification_outbox_claim_idx
    on public.notification_outbox(state, available_at, created_at);
create index if not exists notification_deliveries_status_idx
    on public.notification_deliveries(status, next_attempt_at);
create index if not exists notification_installations_user_idx
    on public.notification_device_installations(user_id, enabled, revoked_at);

alter table public.notification_events enable row level security;
alter table public.notification_preferences enable row level security;
alter table public.notification_device_installations enable row level security;
alter table public.notification_deliveries enable row level security;
alter table public.notification_outbox enable row level security;
alter table public.notification_dead_letters enable row level security;
alter table public.notification_access_audit enable row level security;

-- ---------------------------------------------------------------------------
-- RLS deny-by-default
-- ---------------------------------------------------------------------------

drop policy if exists notifications_insert on public.notifications;
drop policy if exists notifications_select on public.notifications;
create policy notifications_select on public.notifications
for select to authenticated
using (auth.uid() = user_id and deleted_at is null);

drop policy if exists notifications_update on public.notifications;
create policy notifications_update_own_state on public.notifications
for update to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists notifications_no_insert_client on public.notifications;
create policy notifications_no_insert_client on public.notifications
for insert to authenticated
with check (false);

drop policy if exists notifications_no_delete_client on public.notifications;
create policy notifications_no_delete_client on public.notifications
for delete to authenticated
using (false);

drop policy if exists notification_preferences_select_own on public.notification_preferences;
create policy notification_preferences_select_own on public.notification_preferences
for select to authenticated using (auth.uid() = user_id);
drop policy if exists notification_preferences_write_own on public.notification_preferences;
create policy notification_preferences_write_own on public.notification_preferences
for all to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists notification_installations_select_own on public.notification_device_installations;
create policy notification_installations_select_own on public.notification_device_installations
for select to authenticated using (auth.uid() = user_id);
drop policy if exists notification_installations_write_own on public.notification_device_installations;
create policy notification_installations_write_own on public.notification_device_installations
for all to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists notification_events_no_client on public.notification_events;
create policy notification_events_no_client on public.notification_events
for all to authenticated using (false) with check (false);
drop policy if exists notification_deliveries_no_client on public.notification_deliveries;
create policy notification_deliveries_no_client on public.notification_deliveries
for all to authenticated using (false) with check (false);
drop policy if exists notification_outbox_no_client on public.notification_outbox;
create policy notification_outbox_no_client on public.notification_outbox
for all to authenticated using (false) with check (false);
drop policy if exists notification_dead_letters_no_client on public.notification_dead_letters;
create policy notification_dead_letters_no_client on public.notification_dead_letters
for all to authenticated using (false) with check (false);
drop policy if exists notification_access_audit_no_client on public.notification_access_audit;
create policy notification_access_audit_no_client on public.notification_access_audit
for all to authenticated using (false) with check (false);

-- ---------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------

create or replace function public.m06_can_read_notification(p_notification_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.notifications n
        where n.id = p_notification_id
          and n.user_id = auth.uid()
          and n.deleted_at is null
    );
$$;

create or replace function public.m06_notification_json(n public.notifications)
returns jsonb
language sql
stable
set search_path = public
as $$
    select jsonb_build_object(
        'id', n.id,
        'user_id', n.user_id,
        'type', n.type,
        'title', n.title,
        'body', case when n.sensitivity in ('SENSITIVE','SECURITY_CRITICAL') then coalesce(n.title, 'Actualización') else n.body end,
        'related_id', n.related_id,
        'related_type', n.related_type,
        'read_at', n.read_at,
        'is_read', n.is_read,
        'state', n.state,
        'category', n.category,
        'priority', n.priority,
        'sensitivity', n.sensitivity,
        'expires_at', n.expires_at,
        'archived_at', n.archived_at,
        'deleted_at', n.deleted_at,
        'deep_link_type', n.deep_link_type,
        'deep_link_resource_type', n.deep_link_resource_type,
        'deep_link_resource_id', n.deep_link_resource_id,
        'deep_link_required_permission', n.deep_link_required_permission,
        'payload', n.payload,
        'deduplication_key', n.deduplication_key,
        'created_at', n.created_at,
        'updated_at', n.updated_at
    );
$$;

create or replace function public.m06_get_inbox(p_limit int default 50, p_offset int default 0)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
begin
    return coalesce((
        select jsonb_agg(public.m06_notification_json(n) order by n.created_at desc)
        from (
            select *
            from public.notifications
            where user_id = v_actor
              and deleted_at is null
              and (expires_at is null or expires_at > timezone('utc', now()))
            order by created_at desc
            limit greatest(1, least(coalesce(p_limit, 50), 100))
            offset greatest(coalesce(p_offset, 0), 0)
        ) n
    ), '[]'::jsonb);
end;
$$;

create or replace function public.m06_get_unread_count()
returns integer
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
begin
    return (
        select count(*)::int
        from public.notifications n
        where n.user_id = v_actor
          and n.deleted_at is null
          and n.state = 'UNREAD'
          and n.read_at is null
          and (n.expires_at is null or n.expires_at > timezone('utc', now()))
    );
end;
$$;

create or replace function public.m06_mark_notification_read(p_notification_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_row public.notifications;
begin
    update public.notifications
    set read_at = coalesce(read_at, timezone('utc', now())),
        is_read = true,
        state = case when state in ('ARCHIVED','DELETED','EXPIRED') then state else 'READ' end
    where id = p_notification_id
      and user_id = v_actor
      and deleted_at is null
    returning * into v_row;

    if not found then
        raise exception using errcode = '42501', message = 'M06_NOTIFICATION_NOT_FOUND';
    end if;
    return public.m06_notification_json(v_row);
end;
$$;

create or replace function public.m06_mark_all_notifications_read()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_count int;
begin
    update public.notifications
    set read_at = coalesce(read_at, timezone('utc', now())),
        is_read = true,
        state = 'READ'
    where user_id = v_actor
      and deleted_at is null
      and state = 'UNREAD'
      and (expires_at is null or expires_at > timezone('utc', now()));
    get diagnostics v_count = row_count;
    return v_count;
end;
$$;

create or replace function public.m06_archive_notification(p_notification_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_row public.notifications;
begin
    update public.notifications
    set archived_at = coalesce(archived_at, timezone('utc', now())),
        state = 'ARCHIVED'
    where id = p_notification_id
      and user_id = v_actor
      and deleted_at is null
    returning * into v_row;
    if not found then
        raise exception using errcode = '42501', message = 'M06_NOTIFICATION_NOT_FOUND';
    end if;
    return public.m06_notification_json(v_row);
end;
$$;

create or replace function public.m06_delete_notification_logical(p_notification_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_row public.notifications;
begin
    update public.notifications
    set deleted_at = coalesce(deleted_at, timezone('utc', now())),
        state = 'DELETED'
    where id = p_notification_id
      and user_id = v_actor
      and deleted_at is null
    returning * into v_row;
    if not found then
        raise exception using errcode = '42501', message = 'M06_NOTIFICATION_NOT_FOUND';
    end if;
    return public.m06_notification_json(v_row);
end;
$$;

create or replace function public.m06_get_preferences()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
begin
    insert into public.notification_preferences(user_id, category, push_enabled)
    select v_actor, c, c <> 'SECURITY'
    from unnest(public.m06_notification_categories()) c
    on conflict (user_id, category) do nothing;

    return (
        select jsonb_agg(to_jsonb(p) order by p.category)
        from public.notification_preferences p
        where p.user_id = v_actor
    );
end;
$$;

create or replace function public.m06_update_preference(
    p_category text,
    p_in_app_enabled boolean,
    p_push_enabled boolean,
    p_email_enabled boolean,
    p_marketing_consent boolean default false,
    p_quiet_hours_start time default null,
    p_quiet_hours_end time default null,
    p_quiet_hours_days int[] default null,
    p_timezone text default 'UTC'
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_category text := upper(trim(p_category));
    v_row public.notification_preferences;
begin
    if v_category <> all(public.m06_notification_categories()) then
        raise exception using errcode = '22023', message = 'M06_CATEGORY_INVALID';
    end if;
    if v_category in ('ACCOUNT','SECURITY','INVITATION','MODERATION','APPEAL','VERIFICATION','PAYMENT')
       and not coalesce(p_in_app_enabled, true) then
        raise exception using errcode = '22023', message = 'M06_IN_APP_MANDATORY';
    end if;
    if coalesce(p_timezone, '') !~ '^[A-Za-z0-9_./+-]{1,64}$' then
        raise exception using errcode = '22023', message = 'M06_TIMEZONE_INVALID';
    end if;

    insert into public.notification_preferences(
        user_id, category, in_app_enabled, push_enabled, email_enabled,
        marketing_consent, quiet_hours_start, quiet_hours_end, quiet_hours_days,
        timezone, updated_at
    ) values (
        v_actor, v_category, coalesce(p_in_app_enabled, true), coalesce(p_push_enabled, true),
        coalesce(p_email_enabled, false), coalesce(p_marketing_consent, false),
        p_quiet_hours_start, p_quiet_hours_end,
        coalesce(p_quiet_hours_days, array[1,2,3,4,5,6,7]),
        p_timezone, timezone('utc', now())
    )
    on conflict (user_id, category) do update set
        in_app_enabled = excluded.in_app_enabled,
        push_enabled = excluded.push_enabled,
        email_enabled = excluded.email_enabled,
        marketing_consent = excluded.marketing_consent,
        quiet_hours_start = excluded.quiet_hours_start,
        quiet_hours_end = excluded.quiet_hours_end,
        quiet_hours_days = excluded.quiet_hours_days,
        timezone = excluded.timezone,
        updated_at = excluded.updated_at
    returning * into v_row;

    return to_jsonb(v_row);
end;
$$;

create or replace function public.m06_register_installation(
    p_installation_id text,
    p_platform text,
    p_token_fingerprint text,
    p_token_reference text default null,
    p_app_version text default null,
    p_device_label text default null
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_row public.notification_device_installations;
begin
    insert into public.notification_device_installations(
        installation_id, user_id, platform, token_fingerprint,
        token_protected_or_token_reference, app_version, device_label,
        enabled, revoked_at, last_seen_at, updated_at
    ) values (
        trim(p_installation_id), v_actor, upper(coalesce(p_platform, 'ANDROID')),
        lower(trim(p_token_fingerprint)), nullif(p_token_reference, ''),
        nullif(p_app_version, ''), nullif(p_device_label, ''),
        true, null, timezone('utc', now()), timezone('utc', now())
    )
    on conflict (installation_id) do update set
        user_id = excluded.user_id,
        platform = excluded.platform,
        token_fingerprint = excluded.token_fingerprint,
        token_protected_or_token_reference = excluded.token_protected_or_token_reference,
        app_version = excluded.app_version,
        device_label = excluded.device_label,
        enabled = true,
        revoked_at = null,
        last_seen_at = excluded.last_seen_at,
        updated_at = excluded.updated_at
    returning * into v_row;

    return to_jsonb(v_row) - 'token_protected_or_token_reference';
end;
$$;

create or replace function public.m06_rotate_installation_token(
    p_installation_id text,
    p_token_fingerprint text,
    p_token_reference text default null
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_row public.notification_device_installations;
begin
    update public.notification_device_installations
    set token_fingerprint = lower(trim(p_token_fingerprint)),
        token_protected_or_token_reference = nullif(p_token_reference, ''),
        enabled = true,
        revoked_at = null,
        last_seen_at = timezone('utc', now()),
        updated_at = timezone('utc', now())
    where installation_id = trim(p_installation_id)
      and user_id = v_actor
    returning * into v_row;
    if not found then
        raise exception using errcode = '42501', message = 'M06_INSTALLATION_NOT_FOUND';
    end if;
    return to_jsonb(v_row) - 'token_protected_or_token_reference';
end;
$$;

create or replace function public.m06_revoke_current_installation(p_installation_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_row public.notification_device_installations;
begin
    update public.notification_device_installations
    set enabled = false,
        revoked_at = coalesce(revoked_at, timezone('utc', now())),
        last_seen_at = timezone('utc', now()),
        updated_at = timezone('utc', now())
    where installation_id = trim(p_installation_id)
      and user_id = v_actor
    returning * into v_row;
    if not found then
        raise exception using errcode = '42501', message = 'M06_INSTALLATION_NOT_FOUND';
    end if;
    return to_jsonb(v_row) - 'token_protected_or_token_reference';
end;
$$;

create or replace function public.m06_enqueue_domain_event(
    p_event_key text,
    p_origin_module text,
    p_origin_type text,
    p_category text,
    p_priority text,
    p_sensitivity text,
    p_resource_type text,
    p_resource_id text,
    p_organization_id uuid,
    p_payload jsonb,
    p_deduplication_key text,
    p_idempotency_key text,
    p_expires_at timestamptz default null,
    p_is_internal boolean default false
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_event_id uuid;
begin
    if upper(p_origin_module) not in ('M01','M02','M03','M04','M05') then
        raise exception using errcode = '22023', message = 'M06_ORIGIN_INVALID';
    end if;
    if upper(p_category) <> all(public.m06_notification_categories()) then
        raise exception using errcode = '22023', message = 'M06_CATEGORY_INVALID';
    end if;
    perform public.m06_validate_payload(coalesce(p_payload, '{}'::jsonb));

    insert into public.notification_events(
        event_key, origin_module, origin_type, category, priority, sensitivity,
        resource_type, resource_id, organization_id, payload, deduplication_key,
        idempotency_key, expires_at, created_by, is_internal
    ) values (
        trim(p_event_key), upper(trim(p_origin_module)), upper(trim(p_origin_type)),
        upper(trim(p_category)), upper(trim(p_priority)), upper(trim(p_sensitivity)),
        nullif(trim(coalesce(p_resource_type, '')), ''),
        nullif(trim(coalesce(p_resource_id, '')), ''),
        p_organization_id, coalesce(p_payload, '{}'::jsonb),
        trim(p_deduplication_key), trim(p_idempotency_key), p_expires_at, v_actor,
        coalesce(p_is_internal, false)
    )
    on conflict (idempotency_key) do update set updated_at = public.notification_events.created_at
    returning id into v_event_id;

    insert into public.notification_outbox(event_id)
    values (v_event_id)
    on conflict (event_id) do nothing;

    return v_event_id;
end;
$$;

create or replace function public.m06_materialize_in_app_notification(
    p_event_id uuid,
    p_recipient_user_id uuid,
    p_title text,
    p_body text,
    p_deep_link_type text default 'SAFE_HOME',
    p_deep_link_resource_type text default null,
    p_deep_link_resource_id text default null,
    p_deep_link_required_permission text default null
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_event public.notification_events;
    v_id uuid;
begin
    select * into v_event from public.notification_events where id = p_event_id;
    if not found then
        raise exception using errcode = 'P0002', message = 'M06_EVENT_NOT_FOUND';
    end if;
    if v_event.expires_at is not null and v_event.expires_at <= timezone('utc', now()) then
        raise exception using errcode = '22023', message = 'M06_EVENT_EXPIRED';
    end if;
    if v_event.is_internal and not public.m06_known_staff_permission(p_deep_link_required_permission) then
        raise exception using errcode = '42501', message = 'M06_INTERNAL_REQUIRES_STAFF';
    end if;
    if upper(coalesce(p_deep_link_type, 'SAFE_HOME')) <> all(public.m06_deep_link_routes()) then
        raise exception using errcode = '22023', message = 'M06_DEEP_LINK_INVALID';
    end if;

    insert into public.notifications(
        user_id, type, title, body, event_id, category, priority, sensitivity,
        state, is_read, organization_id, deep_link_type, deep_link_resource_type,
        deep_link_resource_id, deep_link_required_permission, payload,
        deduplication_key, created_by, expires_at, related_id, related_type
    ) values (
        p_recipient_user_id, v_event.origin_type,
        left(coalesce(nullif(p_title, ''), 'Notificación'), 160),
        left(coalesce(nullif(p_body, ''), 'Tenés una actualización en LeoVer.'), 500),
        v_event.id, v_event.category, v_event.priority, v_event.sensitivity,
        'UNREAD', false, v_event.organization_id,
        upper(coalesce(p_deep_link_type, 'SAFE_HOME')),
        nullif(p_deep_link_resource_type, ''),
        nullif(p_deep_link_resource_id, ''),
        nullif(p_deep_link_required_permission, ''),
        v_event.payload, v_event.deduplication_key,
        v_event.created_by, v_event.expires_at,
        v_event.resource_id, v_event.resource_type
    )
    on conflict (user_id, deduplication_key) where deduplication_key is not null and deleted_at is null
    do update set updated_at = public.notifications.updated_at
    returning id into v_id;

    insert into public.notification_deliveries(notification_id, channel, status)
    values (v_id, 'IN_APP', 'DELIVERED')
    on conflict do nothing;

    return v_id;
end;
$$;

create or replace function public.m06_claim_outbox(p_worker_id text default 'manual', p_limit int default 10)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
begin
    if not public.has_permission('notifications.process') and not public.has_permission('moderation.view') then
        raise exception using errcode = '42501', message = 'M06_OUTBOX_FORBIDDEN';
    end if;

    return coalesce((
        with claimed as (
            select id
            from public.notification_outbox
            where state in ('PENDING','FAILED_RETRYABLE')
              and available_at <= timezone('utc', now())
            order by available_at, created_at
            limit greatest(1, least(coalesce(p_limit, 10), 50))
            for update skip locked
        ), updated as (
            update public.notification_outbox o
            set state = 'CLAIMED',
                claimed_at = timezone('utc', now()),
                claimed_by = left(coalesce(nullif(p_worker_id, ''), v_actor::text), 120),
                attempt_count = attempt_count + 1,
                updated_at = timezone('utc', now())
            from claimed
            where o.id = claimed.id
            returning o.*
        )
        select jsonb_agg(to_jsonb(updated)) from updated
    ), '[]'::jsonb);
end;
$$;

create or replace function public.m06_mark_outbox_processed(p_outbox_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_row public.notification_outbox;
begin
    perform public.m06_require_active_actor();
    if not public.has_permission('notifications.process') and not public.has_permission('moderation.view') then
        raise exception using errcode = '42501', message = 'M06_OUTBOX_FORBIDDEN';
    end if;
    update public.notification_outbox
    set state = 'PROCESSED', updated_at = timezone('utc', now())
    where id = p_outbox_id
    returning * into v_row;
    return to_jsonb(v_row);
end;
$$;

create or replace function public.m06_mark_outbox_failed(p_outbox_id uuid, p_error_code text, p_retryable boolean default true)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_row public.notification_outbox;
    v_state text;
begin
    perform public.m06_require_active_actor();
    if not public.has_permission('notifications.process') and not public.has_permission('moderation.view') then
        raise exception using errcode = '42501', message = 'M06_OUTBOX_FORBIDDEN';
    end if;

    select case
        when coalesce(p_retryable, true) and attempt_count < 3 then 'FAILED_RETRYABLE'
        when coalesce(p_retryable, true) then 'DEAD'
        else 'FAILED_PERMANENT'
    end
    into v_state
    from public.notification_outbox
    where id = p_outbox_id;

    update public.notification_outbox
    set state = v_state,
        last_error_code = public.m06_sanitize_error_code(p_error_code),
        available_at = case when v_state = 'FAILED_RETRYABLE'
            then timezone('utc', now()) + make_interval(secs => least(3600, power(2, greatest(attempt_count, 1))::int * 30))
            else available_at end,
        updated_at = timezone('utc', now())
    where id = p_outbox_id
    returning * into v_row;

    if v_row.state = 'DEAD' then
        insert into public.notification_dead_letters(outbox_id, event_id, error_code, attempts)
        values (v_row.id, v_row.event_id, public.m06_sanitize_error_code(p_error_code), v_row.attempt_count)
        on conflict do nothing;
    end if;

    return to_jsonb(v_row);
end;
$$;

-- Legacy RPC replacement: self-only, non-sensitive, no arbitrary recipient.
create or replace function public.create_notification(
    target_user_id uuid,
    notif_type text,
    notif_title text,
    notif_body text,
    related_id text default null,
    related_type text default null
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_id uuid;
    v_type text := upper(trim(coalesce(notif_type, 'SYSTEM')));
begin
    if target_user_id is distinct from v_actor then
        raise exception using errcode = '42501', message = 'M06_CREATE_NOTIFICATION_CROSS_USER_DENIED';
    end if;
    if v_type not in ('SYSTEM') then
        raise exception using errcode = '42501', message = 'M06_CREATE_NOTIFICATION_LEGACY_TYPE_DENIED';
    end if;
    perform public.m06_validate_payload(jsonb_build_object(
        'title_key', left(coalesce(notif_title, ''), 80),
        'body_key', left(coalesce(notif_body, ''), 80)
    ));

    insert into public.notifications(
        user_id, type, title, body, related_id, related_type, category,
        priority, sensitivity, state, is_read, deep_link_type,
        deduplication_key, created_by
    ) values (
        v_actor, v_type, left(coalesce(nullif(notif_title, ''), 'Notificación'), 160),
        left(coalesce(nullif(notif_body, ''), 'Tenés una actualización en LeoVer.'), 500),
        nullif(related_id, ''), nullif(related_type, ''),
        'SYSTEM', 'LOW', 'PRIVATE', 'UNREAD', false, 'SAFE_HOME',
        'legacy-self:' || v_actor::text || ':' || md5(v_type || ':' || coalesce(related_type, '') || ':' || coalesce(related_id, '') || ':' || coalesce(notif_title, '')),
        v_actor
    )
    on conflict (user_id, deduplication_key) where deduplication_key is not null and deleted_at is null
    do update set updated_at = public.notifications.updated_at
    returning id into v_id;

    return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants mínimos
-- ---------------------------------------------------------------------------

revoke all on table public.notification_events from authenticated;
revoke all on table public.notification_deliveries from authenticated;
revoke all on table public.notification_outbox from authenticated;
revoke all on table public.notification_dead_letters from authenticated;
revoke all on table public.notification_access_audit from authenticated;

grant select on public.notifications to authenticated;
grant select, insert, update on public.notification_preferences to authenticated;
grant select, insert, update on public.notification_device_installations to authenticated;

revoke execute on function public.m06_enqueue_domain_event(text,text,text,text,text,text,text,text,uuid,jsonb,text,text,timestamptz,boolean) from authenticated;
revoke execute on function public.m06_materialize_in_app_notification(uuid,uuid,text,text,text,text,text,text) from authenticated;
grant execute on function public.m06_get_inbox(int, int) to authenticated;
grant execute on function public.m06_get_unread_count() to authenticated;
grant execute on function public.m06_mark_notification_read(uuid) to authenticated;
grant execute on function public.m06_mark_all_notifications_read() to authenticated;
grant execute on function public.m06_archive_notification(uuid) to authenticated;
grant execute on function public.m06_delete_notification_logical(uuid) to authenticated;
grant execute on function public.m06_get_preferences() to authenticated;
grant execute on function public.m06_update_preference(text, boolean, boolean, boolean, boolean, time, time, int[], text) to authenticated;
grant execute on function public.m06_register_installation(text, text, text, text, text, text) to authenticated;
grant execute on function public.m06_rotate_installation_token(text, text, text) to authenticated;
grant execute on function public.m06_revoke_current_installation(text) to authenticated;
grant execute on function public.create_notification(uuid, text, text, text, text, text) to authenticated;

comment on function public.create_notification(uuid, text, text, text, text, text)
is 'M06 Etapa 3: compatibilidad legacy self-only. Deniega cross-user y tipos cliente que calculan destinatario.';

comment on table public.device_tokens
is 'Legacy FCM tokens retained during M06 Etapa 3. Do not delete until migration plan replaces push path.';

commit;
