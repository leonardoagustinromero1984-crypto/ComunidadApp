-- LeoVer — M06 Etapa 5: hardening grants enqueue/materialize/outbox + fix enqueue idempotency
-- Defectos bloqueantes detectados en auditoría Stage 5.
-- No edita migraciones 001-027.

begin;

-- ---------------------------------------------------------------------------
-- B2: enqueue ON CONFLICT no debe referenciar notification_events.updated_at
-- (columna inexistente → error en reintento idempotente, enmascarado por emit).
-- ---------------------------------------------------------------------------

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
    v_idem text := trim(p_idempotency_key);
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
        trim(p_deduplication_key), v_idem, p_expires_at, v_actor,
        coalesce(p_is_internal, false)
    )
    on conflict (idempotency_key) do nothing
    returning id into v_event_id;

    if v_event_id is null then
        select id into v_event_id
        from public.notification_events
        where idempotency_key = v_idem;
    end if;

    if v_event_id is null then
        raise exception using errcode = 'P0002', message = 'M06_EVENT_ENQUEUE_FAILED';
    end if;

    insert into public.notification_outbox(event_id)
    values (v_event_id)
    on conflict (event_id) do nothing;

    return v_event_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- B3: emit registra fallo sanitizado en dead-letter en lugar de silencio total
-- ---------------------------------------------------------------------------

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
    v_sqlstate text;
    v_msg text;
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
    get stacked diagnostics v_sqlstate = returned_sqlstate, v_msg = message_text;
    begin
        insert into public.notification_dead_letters(outbox_id, event_id, error_code, attempts, sanitized_context)
        values (
            null,
            null,
            public.m06_sanitize_error_code(coalesce(v_sqlstate, 'M06_EMIT_FAILED')),
            1,
            jsonb_build_object(
                'event_key', left(coalesce(p_event_key, ''), 80),
                'origin_module', left(coalesce(p_origin_module, ''), 16),
                'message', left(public.m06_sanitize_error_code(v_msg), 120)
            )
        );
    exception when others then
        null;
    end;
    -- Domain mutation must not fail because of notification wiring
    return null;
end;
$$;

-- ---------------------------------------------------------------------------
-- B1 / N1: revoke PUBLIC EXECUTE on SECURITY DEFINER enqueue/materialize/outbox
-- ---------------------------------------------------------------------------

revoke all on function public.m06_enqueue_domain_event(text, text, text, text, text, text, text, text, uuid, jsonb, text, text, timestamptz, boolean)
    from public, anon, authenticated;
revoke all on function public.m06_materialize_in_app_notification(uuid, uuid, text, text, text, text, text, text)
    from public, anon, authenticated;

grant execute on function public.m06_enqueue_domain_event(text, text, text, text, text, text, text, text, uuid, jsonb, text, text, timestamptz, boolean)
    to service_role;
grant execute on function public.m06_materialize_in_app_notification(uuid, uuid, text, text, text, text, text, text)
    to service_role;

-- Outbox claim/mark: only service_role (not PUBLIC + moderation.view)
revoke all on function public.m06_claim_outbox(text, int) from public, anon, authenticated;
revoke all on function public.m06_mark_outbox_processed(uuid) from public, anon, authenticated;
revoke all on function public.m06_mark_outbox_failed(uuid, text, boolean) from public, anon, authenticated;

grant execute on function public.m06_claim_outbox(text, int) to service_role;
grant execute on function public.m06_mark_outbox_processed(uuid) to service_role;
grant execute on function public.m06_mark_outbox_failed(uuid, text, boolean) to service_role;

-- Reaffirm push path remains service_role-only
revoke all on function public.m06_claim_push_deliveries(text, int) from public, anon, authenticated;
revoke all on function public.m06_mark_delivery_result(uuid, text, text, text) from public, anon, authenticated;
revoke all on function public.m06_plan_push_deliveries(uuid) from public, anon, authenticated;
revoke all on function public.m06_emit_domain_notification(uuid, text, text, text, text, text, text, text, text, text, text, uuid, text, text, text, text, jsonb, text, text, timestamptz, boolean)
    from public, anon, authenticated;

grant execute on function public.m06_claim_push_deliveries(text, int) to service_role;
grant execute on function public.m06_mark_delivery_result(uuid, text, text, text) to service_role;
grant execute on function public.m06_plan_push_deliveries(uuid) to service_role;
grant execute on function public.m06_emit_domain_notification(uuid, text, text, text, text, text, text, text, text, text, text, uuid, text, text, text, text, jsonb, text, text, timestamptz, boolean)
    to service_role;

-- create_notification remains authenticated self-only SYSTEM (026). Do not reopen cross-user.
-- Client INSERT on notifications remains denied (026).

comment on function public.m06_enqueue_domain_event(text, text, text, text, text, text, text, text, uuid, jsonb, text, text, timestamptz, boolean)
is 'M06 Etapa 5: enqueue idempotente sin updated_at; EXECUTE solo service_role / owner.';

comment on function public.m06_materialize_in_app_notification(uuid, uuid, text, text, text, text, text, text)
is 'M06 Etapa 5: materialize in-app; EXECUTE revocado de PUBLIC/anon/authenticated.';

commit;
