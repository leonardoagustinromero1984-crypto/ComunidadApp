-- LeoVer — M06 Etapa 4: push deliveries, instalaciones y wiring M03–M05
-- No edita migraciones 001-026.

begin;

-- ---------------------------------------------------------------------------
-- Preferencias efectivas / quiet hours (server-side)
-- ---------------------------------------------------------------------------

create or replace function public.m06_category_in_app_mandatory(p_category text)
returns boolean
language sql
immutable
set search_path = public
as $$
    select upper(p_category) in (
        'ACCOUNT','SECURITY','INVITATION','MODERATION','APPEAL','VERIFICATION','PAYMENT'
    );
$$;

create or replace function public.m06_preference_push_enabled(p_user_id uuid, p_category text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_enabled boolean;
begin
    select push_enabled into v_enabled
    from public.notification_preferences
    where user_id = p_user_id and category = upper(p_category);

    if not found then
        return upper(p_category) <> 'SECURITY';
    end if;
    return coalesce(v_enabled, true);
end;
$$;

create or replace function public.m06_in_quiet_hours(p_user_id uuid, p_category text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_start time;
    v_end time;
    v_tz text;
    v_local time;
    v_days int[];
    v_dow int;
begin
    if upper(p_category) = 'SECURITY' then
        return false;
    end if;

    select quiet_hours_start, quiet_hours_end, quiet_hours_days, timezone
    into v_start, v_end, v_days, v_tz
    from public.notification_preferences
    where user_id = p_user_id and category = upper(p_category);

    if v_start is null or v_end is null then
        return false;
    end if;

    begin
        v_local := (timezone(coalesce(nullif(v_tz, ''), 'UTC'), timezone('utc', now())))::time;
        v_dow := extract(isodow from timezone(coalesce(nullif(v_tz, ''), 'UTC'), timezone('utc', now())))::int;
    exception when others then
        return false;
    end;

    if v_days is not null and array_length(v_days, 1) is not null and not (v_dow = any (v_days)) then
        return false;
    end if;

    if v_start < v_end then
        return v_local >= v_start and v_local < v_end;
    elsif v_start > v_end then
        return v_local >= v_start or v_local < v_end;
    else
        return false;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Plan / claim / update push deliveries
-- ---------------------------------------------------------------------------

create or replace function public.m06_plan_push_deliveries(p_notification_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_n public.notifications;
    v_count int := 0;
    r record;
begin
    select * into v_n from public.notifications where id = p_notification_id;
    if not found then
        return 0;
    end if;
    if v_n.deleted_at is not null then
        return 0;
    end if;
    if v_n.expires_at is not null and v_n.expires_at <= timezone('utc', now()) then
        return 0;
    end if;
    if not public.m06_preference_push_enabled(v_n.user_id, v_n.category) then
        return 0;
    end if;
    if public.m06_in_quiet_hours(v_n.user_id, v_n.category) then
        insert into public.notification_deliveries(notification_id, channel, status)
        values (v_n.id, 'PUSH', 'SKIPPED_QUIET_HOURS')
        on conflict do nothing;
        return 0;
    end if;

    for r in
        select installation_id
        from public.notification_device_installations
        where user_id = v_n.user_id
          and enabled = true
          and revoked_at is null
          and token_protected_or_token_reference is not null
    loop
        insert into public.notification_deliveries(
            notification_id, channel, installation_id, status
        ) values (
            v_n.id, 'PUSH', r.installation_id, 'PENDING'
        )
        on conflict do nothing;
        v_count := v_count + 1;
    end loop;

    return v_count;
end;
$$;

create unique index if not exists notification_deliveries_push_install_uniq
    on public.notification_deliveries(notification_id, channel, installation_id)
    where channel = 'PUSH' and installation_id is not null;

create or replace function public.m06_claim_push_deliveries(
    p_worker_id text default 'edge-push',
    p_limit int default 25
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
    return coalesce((
        with claimed as (
            select d.id
            from public.notification_deliveries d
            join public.notifications n on n.id = d.notification_id
            where d.channel = 'PUSH'
              and d.status in ('PENDING', 'FAILED_RETRYABLE')
              and (d.next_attempt_at is null or d.next_attempt_at <= timezone('utc', now()))
              and n.deleted_at is null
              and (n.expires_at is null or n.expires_at > timezone('utc', now()))
            order by coalesce(d.next_attempt_at, d.created_at), d.created_at
            limit greatest(1, least(coalesce(p_limit, 25), 50))
            for update of d skip locked
        ), updated as (
            update public.notification_deliveries d
            set status = 'PROCESSING',
                attempt_count = attempt_count + 1,
                last_attempt_at = timezone('utc', now()),
                updated_at = timezone('utc', now())
            from claimed
            where d.id = claimed.id
            returning d.*
        )
        select jsonb_agg(
            jsonb_build_object(
                'delivery_id', u.id,
                'notification_id', u.notification_id,
                'installation_id', u.installation_id,
                'attempt_count', u.attempt_count,
                'user_id', n.user_id,
                'category', n.category,
                'priority', n.priority,
                'sensitivity', n.sensitivity,
                'title', case
                    when n.sensitivity in ('SENSITIVE', 'SECURITY_CRITICAL')
                        then 'Actualización en LeoVer'
                    else n.title
                end,
                'body', case
                    when n.sensitivity in ('SENSITIVE', 'SECURITY_CRITICAL')
                        then 'Tenés una actualización. Abrí LeoVer para verla.'
                    else n.body
                end,
                'deep_link_type', n.deep_link_type,
                'deep_link_resource_type', n.deep_link_resource_type,
                'deep_link_resource_id', n.deep_link_resource_id,
                'organization_id', n.organization_id,
                'token_reference', i.token_protected_or_token_reference,
                'token_fingerprint', i.token_fingerprint,
                'expires_at', n.expires_at
            )
        )
        from updated u
        join public.notifications n on n.id = u.notification_id
        left join public.notification_device_installations i
          on i.installation_id = u.installation_id
         and i.user_id = n.user_id
    ), '[]'::jsonb);
end;
$$;

create or replace function public.m06_mark_delivery_result(
    p_delivery_id uuid,
    p_status text,
    p_failure_code text default null,
    p_provider_message_id text default null
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_row public.notification_deliveries;
    v_status text := upper(trim(p_status));
    v_install text;
    v_user uuid;
begin
    if v_status not in (
        'DELIVERED','FAILED_RETRYABLE','FAILED_PERMANENT','SKIPPED_PREFERENCE',
        'SKIPPED_PERMISSION','SKIPPED_EXPIRED','SKIPPED_QUIET_HOURS','CANCELLED','DEAD_LETTER'
    ) then
        raise exception using errcode = '22023', message = 'M06_DELIVERY_STATUS_INVALID';
    end if;

    update public.notification_deliveries
    set status = v_status,
        failure_code = public.m06_sanitize_error_code(p_failure_code),
        provider_message_id = left(nullif(p_provider_message_id, ''), 120),
        delivered_at = case when v_status = 'DELIVERED' then timezone('utc', now()) else delivered_at end,
        next_attempt_at = case
            when v_status = 'FAILED_RETRYABLE'
                then timezone('utc', now()) + make_interval(secs => least(3600, power(2, greatest(attempt_count, 1))::int * 30))
            else next_attempt_at
        end,
        updated_at = timezone('utc', now())
    where id = p_delivery_id
    returning * into v_row;

    if not found then
        raise exception using errcode = 'P0002', message = 'M06_DELIVERY_NOT_FOUND';
    end if;

    if v_status = 'FAILED_RETRYABLE' and v_row.attempt_count >= 3 then
        update public.notification_deliveries
        set status = 'DEAD_LETTER', updated_at = timezone('utc', now())
        where id = p_delivery_id
        returning * into v_row;

        insert into public.notification_dead_letters(outbox_id, event_id, error_code, attempts, sanitized_context)
        values (
            null,
            null,
            public.m06_sanitize_error_code(coalesce(p_failure_code, 'MAX_ATTEMPTS')),
            v_row.attempt_count,
            jsonb_build_object('delivery_id', v_row.id, 'notification_id', v_row.notification_id)
        );
    end if;

    if v_status = 'FAILED_PERMANENT'
       and public.m06_sanitize_error_code(p_failure_code) in ('INVALID_TOKEN', 'UNREGISTERED', 'NOT_FOUND') then
        select installation_id, n.user_id
        into v_install, v_user
        from public.notification_deliveries d
        join public.notifications n on n.id = d.notification_id
        where d.id = p_delivery_id;

        if v_install is not null then
            update public.notification_device_installations
            set enabled = false,
                revoked_at = coalesce(revoked_at, timezone('utc', now())),
                updated_at = timezone('utc', now())
            where installation_id = v_install
              and user_id = v_user;
        end if;
    end if;

    -- Never mark notification READ on push delivery
    return jsonb_build_object(
        'delivery_id', v_row.id,
        'status', v_row.status,
        'attempt_count', v_row.attempt_count
    );
end;
$$;

-- Store token reference on register/rotate without returning it
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
        token_protected_or_token_reference = coalesce(
            excluded.token_protected_or_token_reference,
            public.notification_device_installations.token_protected_or_token_reference
        ),
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
        token_protected_or_token_reference = coalesce(nullif(p_token_reference, ''), token_protected_or_token_reference),
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

-- Materialize + plan push after inbox creation
create or replace function public.m06_emit_domain_notification(
    p_recipient_user_id uuid,
    p_event_key text,
    p_origin_module text,
    p_origin_type text,
    p_category text,
    p_priority text,
    p_sensitivity text,
    p_title text,
    p_body text,
    p_resource_type text default null,
    p_resource_id text default null,
    p_organization_id uuid default null,
    p_deep_link_type text default 'SAFE_HOME',
    p_deep_link_resource_type text default null,
    p_deep_link_resource_id text default null,
    p_deep_link_required_permission text default null,
    p_payload jsonb default '{}'::jsonb,
    p_deduplication_key text default null,
    p_idempotency_key text default null,
    p_expires_at timestamptz default null,
    p_is_internal boolean default false
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_event_id uuid;
    v_notification_id uuid;
    v_dedup text := coalesce(nullif(trim(p_deduplication_key), ''), trim(p_event_key) || ':' || p_recipient_user_id::text);
    v_idem text := coalesce(nullif(trim(p_idempotency_key), ''), v_dedup);
begin
    if p_recipient_user_id is null then
        return null;
    end if;
    if coalesce(p_is_internal, false) and p_deep_link_required_permission is null then
        return null;
    end if;

    v_event_id := public.m06_enqueue_domain_event(
        p_event_key := trim(p_event_key),
        p_origin_module := upper(trim(p_origin_module)),
        p_origin_type := upper(trim(p_origin_type)),
        p_category := upper(trim(p_category)),
        p_priority := upper(trim(p_priority)),
        p_sensitivity := upper(trim(p_sensitivity)),
        p_resource_type := p_resource_type,
        p_resource_id := p_resource_id,
        p_organization_id := p_organization_id,
        p_payload := coalesce(p_payload, '{}'::jsonb),
        p_deduplication_key := v_dedup,
        p_idempotency_key := v_idem,
        p_expires_at := p_expires_at,
        p_is_internal := coalesce(p_is_internal, false)
    );

    v_notification_id := public.m06_materialize_in_app_notification(
        p_event_id := v_event_id,
        p_recipient_user_id := p_recipient_user_id,
        p_title := p_title,
        p_body := p_body,
        p_deep_link_type := coalesce(p_deep_link_type, 'SAFE_HOME'),
        p_deep_link_resource_type := p_deep_link_resource_type,
        p_deep_link_resource_id := p_deep_link_resource_id,
        p_deep_link_required_permission := p_deep_link_required_permission
    );

    perform public.m06_plan_push_deliveries(v_notification_id);
    update public.notification_outbox
    set state = 'PROCESSED', updated_at = timezone('utc', now())
    where event_id = v_event_id
      and state in ('PENDING', 'CLAIMED', 'FAILED_RETRYABLE');

    return v_notification_id;
exception when others then
    -- Domain mutation must not fail because of notification wiring
    return null;
end;
$$;

-- ---------------------------------------------------------------------------
-- Wiring triggers M03 / M04 / M05 (server-derived, best-effort)
-- ---------------------------------------------------------------------------

create or replace function public.m06_trg_org_invitation_notify()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_owners uuid;
begin
    if tg_op = 'INSERT' and new.status = 'PENDING' and new.invited_user_id is not null then
        perform public.m06_emit_domain_notification(
            p_recipient_user_id := new.invited_user_id,
            p_event_key := 'm03.invitation.created',
            p_origin_module := 'M03',
            p_origin_type := 'INVITATION_CREATED',
            p_category := 'INVITATION',
            p_priority := 'HIGH',
            p_sensitivity := 'PRIVATE',
            p_title := 'Invitación a organización',
            p_body := 'Recibiste una invitación para unirte a una organización en LeoVer.',
            p_resource_type := 'ORGANIZATION_INVITATION',
            p_resource_id := new.id::text,
            p_organization_id := new.organization_id,
            p_deep_link_type := 'ORGANIZATION_INVITATION',
            p_deep_link_resource_type := 'ORGANIZATION_INVITATION',
            p_deep_link_resource_id := new.id::text,
            p_deduplication_key := 'invite:' || new.id::text,
            p_idempotency_key := 'invite-created:' || new.id::text,
            p_expires_at := new.expires_at
        );
    elsif tg_op = 'UPDATE' and new.status is distinct from old.status then
        if new.status in ('ACCEPTED','DECLINED','EXPIRED') then
            for v_owners in
                select m.user_id
                from public.organization_memberships m
                where m.organization_id = new.organization_id
                  and m.status = 'ACTIVE'
                  and m.role_code in ('OWNER','ADMIN')
            loop
                perform public.m06_emit_domain_notification(
                    p_recipient_user_id := v_owners,
                    p_event_key := 'm03.invitation.' || lower(new.status),
                    p_origin_module := 'M03',
                    p_origin_type := 'INVITATION_' || new.status,
                    p_category := 'ORGANIZATION',
                    p_priority := 'NORMAL',
                    p_sensitivity := 'PRIVATE',
                    p_title := 'Actualización de invitación',
                    p_body := 'Una invitación de tu organización cambió de estado.',
                    p_resource_type := 'ORGANIZATION_INVITATION',
                    p_resource_id := new.id::text,
                    p_organization_id := new.organization_id,
                    p_deep_link_type := 'ORGANIZATION',
                    p_deep_link_resource_type := 'ORGANIZATION',
                    p_deep_link_resource_id := new.organization_id::text,
                    p_deduplication_key := 'invite-resp:' || new.id::text || ':' || new.status,
                    p_idempotency_key := 'invite-resp:' || new.id::text || ':' || new.status
                );
            end loop;
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists m06_org_invitation_notify on public.organization_invitations;
create trigger m06_org_invitation_notify
after insert or update of status on public.organization_invitations
for each row execute function public.m06_trg_org_invitation_notify();

create or replace function public.m06_trg_org_membership_notify()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'UPDATE' then
        if new.role_code is distinct from old.role_code and new.status = 'ACTIVE' then
            perform public.m06_emit_domain_notification(
                p_recipient_user_id := new.user_id,
                p_event_key := 'm03.member.role_changed',
                p_origin_module := 'M03',
                p_origin_type := 'MEMBER_ROLE_CHANGED',
                p_category := 'ORGANIZATION',
                p_priority := 'HIGH',
                p_sensitivity := 'PRIVATE',
                p_title := 'Cambio de rol',
                p_body := 'Tu rol en una organización de LeoVer fue actualizado.',
                p_resource_type := 'ORGANIZATION',
                p_resource_id := new.organization_id::text,
                p_organization_id := new.organization_id,
                p_deep_link_type := 'ORGANIZATION',
                p_deep_link_resource_id := new.organization_id::text,
                p_deduplication_key := 'org-role:' || new.organization_id::text || ':' || new.user_id::text || ':' || new.role_code,
                p_idempotency_key := 'org-role:' || new.id::text || ':' || new.role_code || ':' || new.updated_at::text
            );
        end if;
        if new.status = 'REMOVED' and old.status is distinct from 'REMOVED' then
            perform public.m06_emit_domain_notification(
                p_recipient_user_id := new.user_id,
                p_event_key := 'm03.member.removed',
                p_origin_module := 'M03',
                p_origin_type := 'MEMBER_REMOVED',
                p_category := 'ORGANIZATION',
                p_priority := 'HIGH',
                p_sensitivity := 'PRIVATE',
                p_title := 'Saliste de una organización',
                p_body := 'Tu membresía en una organización fue removida.',
                p_resource_type := 'ORGANIZATION',
                p_resource_id := new.organization_id::text,
                p_organization_id := new.organization_id,
                p_deep_link_type := 'SAFE_HOME',
                p_deduplication_key := 'org-removed:' || new.organization_id::text || ':' || new.user_id::text,
                p_idempotency_key := 'org-removed:' || new.id::text || ':' || coalesce(new.updated_at::text, '')
            );
        end if;
        if new.role_code = 'OWNER' and old.role_code is distinct from 'OWNER' and new.status = 'ACTIVE' then
            perform public.m06_emit_domain_notification(
                p_recipient_user_id := new.user_id,
                p_event_key := 'm03.ownership.transferred',
                p_origin_module := 'M03',
                p_origin_type := 'OWNERSHIP_TRANSFERRED',
                p_category := 'ORGANIZATION',
                p_priority := 'HIGH',
                p_sensitivity := 'PRIVATE',
                p_title := 'Transferencia de ownership',
                p_body := 'Ahora sos owner de una organización en LeoVer.',
                p_resource_type := 'ORGANIZATION',
                p_resource_id := new.organization_id::text,
                p_organization_id := new.organization_id,
                p_deep_link_type := 'ORGANIZATION',
                p_deep_link_resource_id := new.organization_id::text,
                p_deduplication_key := 'org-owner:' || new.organization_id::text || ':' || new.user_id::text,
                p_idempotency_key := 'org-owner:' || new.id::text || ':' || coalesce(new.updated_at::text, '')
            );
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists m06_org_membership_notify on public.organization_memberships;
create trigger m06_org_membership_notify
after update of role_code, status on public.organization_memberships
for each row execute function public.m06_trg_org_membership_notify();

create or replace function public.m06_trg_moderation_action_notify()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.target_type = 'USER' and new.target_id ~* '^[0-9a-f-]{36}$' then
        perform public.m06_emit_domain_notification(
            p_recipient_user_id := new.target_id::uuid,
            p_event_key := 'm04.moderation.action_applied',
            p_origin_module := 'M04',
            p_origin_type := 'MODERATION_ACTION',
            p_category := 'MODERATION',
            p_priority := 'HIGH',
            p_sensitivity := 'SENSITIVE',
            p_title := 'Medida de moderación',
            p_body := 'Hay una actualización sobre una medida aplicada a tu cuenta.',
            p_resource_type := 'MODERATION_ACTION',
            p_resource_id := new.id::text,
            p_deep_link_type := 'MODERATION_CASE',
            p_deep_link_resource_type := 'MODERATION_CASE',
            p_deep_link_resource_id := new.case_id::text,
            p_deep_link_required_permission := null,
            p_deduplication_key := 'action:' || new.id::text,
            p_idempotency_key := 'action:' || new.id::text
        );
    end if;
    return new;
end;
$$;

drop trigger if exists m06_moderation_action_notify on public.moderation_actions;
create trigger m06_moderation_action_notify
after insert on public.moderation_actions
for each row execute function public.m06_trg_moderation_action_notify();

create or replace function public.m06_trg_moderation_appeal_notify()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        perform public.m06_emit_domain_notification(
            p_recipient_user_id := new.submitted_by_user_id,
            p_event_key := 'm04.appeal.submitted',
            p_origin_module := 'M04',
            p_origin_type := 'APPEAL_SUBMITTED',
            p_category := 'APPEAL',
            p_priority := 'HIGH',
            p_sensitivity := 'SENSITIVE',
            p_title := 'Apelación presentada',
            p_body := 'Recibimos tu apelación. Te avisaremos cuando haya una resolución.',
            p_resource_type := 'MODERATION_APPEAL',
            p_resource_id := new.id::text,
            p_deep_link_type := 'MODERATION_APPEAL',
            p_deep_link_resource_id := new.id::text,
            p_deduplication_key := 'appeal:' || new.id::text || ':SUBMITTED',
            p_idempotency_key := 'appeal:' || new.id::text || ':SUBMITTED'
        );
    elsif tg_op = 'UPDATE' and new.status is distinct from old.status
          and new.status in ('UPHELD','OVERTURNED','PARTIALLY_OVERTURNED','REJECTED','CLOSED') then
        perform public.m06_emit_domain_notification(
            p_recipient_user_id := new.submitted_by_user_id,
            p_event_key := 'm04.appeal.resolved',
            p_origin_module := 'M04',
            p_origin_type := 'APPEAL_RESOLVED',
            p_category := 'APPEAL',
            p_priority := 'HIGH',
            p_sensitivity := 'SENSITIVE',
            p_title := 'Apelación actualizada',
            p_body := 'Hay una actualización sobre tu apelación.',
            p_resource_type := 'MODERATION_APPEAL',
            p_resource_id := new.id::text,
            p_deep_link_type := 'MODERATION_APPEAL',
            p_deep_link_resource_id := new.id::text,
            p_deduplication_key := 'appeal:' || new.id::text || ':' || new.status,
            p_idempotency_key := 'appeal:' || new.id::text || ':' || new.status
        );
    end if;
    return new;
end;
$$;

drop trigger if exists m06_moderation_appeal_notify on public.moderation_appeals;
create trigger m06_moderation_appeal_notify
after insert or update of status on public.moderation_appeals
for each row execute function public.m06_trg_moderation_appeal_notify();

create or replace function public.m06_trg_verification_notify()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_owner uuid;
begin
    if tg_op = 'UPDATE' and new.status is distinct from old.status
       and new.status in ('MORE_INFO_REQUESTED','APPROVED','REJECTED','REVOKED') then
        for v_owner in
            select m.user_id
            from public.organization_memberships m
            where m.organization_id = new.organization_id
              and m.status = 'ACTIVE'
              and m.role_code in ('OWNER','ADMIN')
        loop
            perform public.m06_emit_domain_notification(
                p_recipient_user_id := v_owner,
                p_event_key := 'm04.verification.' || lower(new.status),
                p_origin_module := 'M04',
                p_origin_type := 'VERIFICATION_' || new.status,
                p_category := 'VERIFICATION',
                p_priority := 'HIGH',
                p_sensitivity := 'SENSITIVE',
                p_title := 'Verificación de organización',
                p_body := 'Hay una actualización sobre la verificación de tu organización.',
                p_resource_type := 'ORGANIZATION_VERIFICATION',
                p_resource_id := new.id::text,
                p_organization_id := new.organization_id,
                p_deep_link_type := 'ORGANIZATION_VERIFICATION',
                p_deep_link_resource_id := new.id::text,
                p_deduplication_key := 'verif:' || new.organization_id::text || ':' || new.id::text || ':' || new.status,
                p_idempotency_key := 'verif:' || new.id::text || ':' || new.status
            );
        end loop;
    end if;
    return new;
end;
$$;

drop trigger if exists m06_verification_notify on public.organization_verification_reviews;
create trigger m06_verification_notify
after update of status on public.organization_verification_reviews
for each row execute function public.m06_trg_verification_notify();

create or replace function public.m06_trg_support_notify()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_ticket public.support_tickets;
    v_staff uuid;
begin
    if tg_op = 'INSERT' and tg_table_name = 'support_tickets' then
        perform public.m06_emit_domain_notification(
            p_recipient_user_id := new.requester_user_id,
            p_event_key := 'm04.support.ticket_created',
            p_origin_module := 'M04',
            p_origin_type := 'SUPPORT_TICKET_CREATED',
            p_category := 'SUPPORT',
            p_priority := 'NORMAL',
            p_sensitivity := 'PRIVATE',
            p_title := 'Ticket de soporte creado',
            p_body := 'Recibimos tu solicitud de soporte.',
            p_resource_type := 'SUPPORT_TICKET',
            p_resource_id := new.id::text,
            p_deep_link_type := 'SUPPORT_TICKET',
            p_deep_link_resource_id := new.id::text,
            p_deduplication_key := 'support:' || new.id::text || ':CREATED',
            p_idempotency_key := 'support:' || new.id::text || ':CREATED'
        );
        return new;
    end if;

    if tg_table_name = 'support_ticket_messages' then
        select * into v_ticket from public.support_tickets where id = new.ticket_id;
        if not found then
            return new;
        end if;
        if new.visibility = 'REQUESTER_VISIBLE' and new.author_user_id is distinct from v_ticket.requester_user_id then
            perform public.m06_emit_domain_notification(
                p_recipient_user_id := v_ticket.requester_user_id,
                p_event_key := 'm04.support.visible_reply',
                p_origin_module := 'M04',
                p_origin_type := 'SUPPORT_VISIBLE_REPLY',
                p_category := 'SUPPORT',
                p_priority := 'HIGH',
                p_sensitivity := 'PRIVATE',
                p_title := 'Nueva respuesta de soporte',
                p_body := 'Tenés una nueva respuesta en tu ticket de soporte.',
                p_resource_type := 'SUPPORT_TICKET',
                p_resource_id := new.ticket_id::text,
                p_deep_link_type := 'SUPPORT_TICKET',
                p_deep_link_resource_id := new.ticket_id::text,
                p_deduplication_key := 'support-msg:' || new.id::text,
                p_idempotency_key := 'support-msg:' || new.id::text
            );
        elsif new.visibility = 'INTERNAL' then
            -- INTERNAL never to requester; notify assigned staff only when present
            if v_ticket.assigned_to_user_id is not null then
                perform public.m06_emit_domain_notification(
                    p_recipient_user_id := v_ticket.assigned_to_user_id,
                    p_event_key := 'm04.support.internal_update',
                    p_origin_module := 'M04',
                    p_origin_type := 'SUPPORT_INTERNAL',
                    p_category := 'SUPPORT',
                    p_priority := 'NORMAL',
                    p_sensitivity := 'SENSITIVE',
                    p_title := 'Actualización interna de soporte',
                    p_body := 'Hay una nota interna en un ticket asignado.',
                    p_resource_type := 'SUPPORT_TICKET',
                    p_resource_id := new.ticket_id::text,
                    p_deep_link_type := 'SUPPORT_TICKET',
                    p_deep_link_resource_id := new.ticket_id::text,
                    p_deep_link_required_permission := 'support.view_sensitive',
                    p_is_internal := true,
                    p_deduplication_key := 'support-int:' || new.id::text,
                    p_idempotency_key := 'support-int:' || new.id::text
                );
            end if;
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists m06_support_ticket_notify on public.support_tickets;
create trigger m06_support_ticket_notify
after insert on public.support_tickets
for each row execute function public.m06_trg_support_notify();

drop trigger if exists m06_support_message_notify on public.support_ticket_messages;
create trigger m06_support_message_notify
after insert on public.support_ticket_messages
for each row execute function public.m06_trg_support_notify();

create or replace function public.m06_trg_file_upload_notify()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_asset public.file_assets;
    v_staff uuid;
begin
    if new.state is distinct from old.state
       and new.state in ('COMPLETED','FAILED','CANCELLED') then
        select * into v_asset from public.file_assets where id = new.asset_id;
        if found and v_asset.owner_kind = 'USER' and v_asset.owner_user_id is not null then
            perform public.m06_emit_domain_notification(
                p_recipient_user_id := v_asset.owner_user_id,
                p_event_key := 'm05.upload.' || lower(new.state),
                p_origin_module := 'M05',
                p_origin_type := 'UPLOAD_' || new.state,
                p_category := 'SYSTEM',
                p_priority := case when new.state = 'COMPLETED' then 'LOW' else 'NORMAL' end,
                p_sensitivity := 'PRIVATE',
                p_title := case
                    when new.state = 'COMPLETED' then 'Archivo listo'
                    when new.state = 'FAILED' then 'Upload fallido'
                    else 'Upload cancelado'
                end,
                p_body := 'Hay una actualización sobre tu archivo en LeoVer.',
                p_resource_type := 'FILE_ASSET',
                p_resource_id := v_asset.id::text,
                p_deep_link_type := 'FILE_RESOURCE',
                p_deep_link_resource_id := v_asset.id::text,
                p_deduplication_key := 'upload:' || new.id::text || ':' || new.state,
                p_idempotency_key := 'upload:' || new.id::text || ':' || new.state
            );
        end if;

        if new.state = 'COMPLETED'
           and found
           and v_asset.purpose = 'ORGANIZATION_VERIFICATION_DOCUMENT' then
            -- staff-only availability notice without signed URLs
            for v_staff in
                select distinct ura.user_id
                from public.user_role_assignments ura
                join public.role_permissions rp on rp.role_id = ura.role_id
                join public.permissions p on p.id = rp.permission_id
                where ura.revoked_at is null
                  and p.code in ('organization.verification.review', 'files.verification.view', 'moderation.view')
            loop
                perform public.m06_emit_domain_notification(
                    p_recipient_user_id := v_staff,
                    p_event_key := 'm05.verification_document.ready',
                    p_origin_module := 'M05',
                    p_origin_type := 'VERIFICATION_DOCUMENT_READY',
                    p_category := 'VERIFICATION',
                    p_priority := 'NORMAL',
                    p_sensitivity := 'SENSITIVE',
                    p_title := 'Documento de verificación disponible',
                    p_body := 'Hay un documento de verificación listo para revisión.',
                    p_resource_type := 'FILE_ASSET',
                    p_resource_id := v_asset.id::text,
                    p_organization_id := v_asset.owner_organization_id,
                    p_deep_link_type := 'ORGANIZATION_VERIFICATION',
                    p_deep_link_resource_id := coalesce(v_asset.owner_organization_id::text, v_asset.id::text),
                    p_deep_link_required_permission := 'organization.verification.review',
                    p_is_internal := true,
                    p_deduplication_key := 'verif-doc:' || v_asset.id::text,
                    p_idempotency_key := 'verif-doc:' || new.id::text || ':COMPLETED'
                );
            end loop;
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists m06_file_upload_notify on public.file_upload_sessions;
create trigger m06_file_upload_notify
after update of state on public.file_upload_sessions
for each row execute function public.m06_trg_file_upload_notify();

-- ---------------------------------------------------------------------------
-- Grants: claim/update push only for service role (Edge Function)
-- ---------------------------------------------------------------------------

revoke all on function public.m06_claim_push_deliveries(text, int) from public, anon, authenticated;
revoke all on function public.m06_mark_delivery_result(uuid, text, text, text) from public, anon, authenticated;
revoke all on function public.m06_plan_push_deliveries(uuid) from public, anon, authenticated;
revoke all on function public.m06_emit_domain_notification(uuid, text, text, text, text, text, text, text, text, text, text, uuid, text, text, text, text, jsonb, text, text, timestamptz, boolean) from public, anon, authenticated;

grant execute on function public.m06_claim_push_deliveries(text, int) to service_role;
grant execute on function public.m06_mark_delivery_result(uuid, text, text, text) to service_role;
grant execute on function public.m06_plan_push_deliveries(uuid) to service_role;
grant execute on function public.m06_emit_domain_notification(uuid, text, text, text, text, text, text, text, text, text, text, uuid, text, text, text, text, jsonb, text, text, timestamptz, boolean) to service_role;

grant execute on function public.m06_register_installation(text, text, text, text, text, text) to authenticated;
grant execute on function public.m06_rotate_installation_token(text, text, text) to authenticated;

comment on function public.m06_claim_push_deliveries(text, int)
is 'M06 Etapa 4: claim atómico de deliveries PUSH para Edge Function. No grant a authenticated.';

comment on table public.device_tokens
is 'Legacy FCM tokens retained; Etapa 4 envía push desde notification_device_installations para evitar doble envío.';

commit;
