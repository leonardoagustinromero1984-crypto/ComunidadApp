-- LeoVer M07 — migración 033: correcciones runtime de db lint (staging)
-- No edita 001–032. Solo CREATE OR REPLACE de funciones con defectos L05–L11.
-- Mantener search_path = public; tipos/funciones de extensiones calificados.

begin;

-- ---------------------------------------------------------------------------
-- L05 — is_username_available: citext no resuelve con search_path = public
-- SQLSTATE 42704
-- ---------------------------------------------------------------------------
create or replace function public.is_username_available(p_username text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    u text := public.normalize_username(p_username);
begin
    if not public.is_valid_username_format(u) then
        return false;
    end if;
    if exists (
        select 1 from public.reserved_usernames r
        where r.active and r.username = u::extensions.citext
    ) then
        return false;
    end if;
    if exists (
        select 1 from public.users x
        where x.username = u::extensions.citext
          and x.id <> coalesce(auth.uid(), '00000000-0000-0000-0000-000000000000'::uuid)
    ) then
        return false;
    end if;
    return true;
end;
$$;

revoke all on function public.is_username_available(text) from public;
grant execute on function public.is_username_available(text) to authenticated;

-- ---------------------------------------------------------------------------
-- L06 — add_reputation_points: badge_type ambiguo (parámetro vs columna)
-- SQLSTATE 42702
-- ---------------------------------------------------------------------------
create or replace function public.add_reputation_points(
    target_user_id uuid,
    points integer,
    badge_type text default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_badge_type text := add_reputation_points.badge_type;
begin
    update public.users
    set reputation_score = coalesce(reputation_score, 0) + points
    where id = target_user_id;

    if v_badge_type is not null then
        insert into public.user_badges (user_id, badge_type)
        values (target_user_id, v_badge_type)
        on conflict on constraint user_badges_user_id_badge_type_key do nothing;
    end if;
end;
$$;

grant execute on function public.add_reputation_points(uuid, integer, text) to authenticated;

-- ---------------------------------------------------------------------------
-- L07 — complete_profile_onboarding: citext no resuelve con search_path = public
-- SQLSTATE 42704
-- ---------------------------------------------------------------------------
create or replace function public.complete_profile_onboarding(
    p_display_name text,
    p_username text,
    p_city text default null,
    p_province text default null,
    p_country_code text default null,
    p_bio text default null,
    p_avatar_path text default null,
    p_profile_visibility text default 'PRIVATE',
    p_show_location boolean default true,
    p_show_phone boolean default false,
    p_allow_friend_requests boolean default true,
    p_locale text default null,
    p_timezone text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    uid uuid := auth.uid();
    u text := public.normalize_username(p_username);
    dname text := nullif(trim(coalesce(p_display_name, '')), '');
    vis text := upper(nullif(trim(coalesce(p_profile_visibility, '')), ''));
    cc text := nullif(upper(trim(coalesce(p_country_code, ''))), '');
    result_row public.users%rowtype;
begin
    if uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if dname is null or char_length(dname) < 2 or char_length(dname) > 80 then
        raise exception 'DISPLAY_NAME_INVALID';
    end if;
    if not public.is_username_available(u) then
        raise exception 'USERNAME_UNAVAILABLE';
    end if;
    if vis is null or vis not in ('PUBLIC', 'FRIENDS', 'PRIVATE') then
        vis := 'PRIVATE';
    end if;
    if cc is not null and cc !~ '^[A-Z]{2}$' then
        raise exception 'COUNTRY_CODE_INVALID';
    end if;
    if p_avatar_path is not null
       and p_avatar_path not like ('users/' || uid::text || '/avatar/%') then
        raise exception 'AVATAR_PATH_INVALID';
    end if;

    update public.users set
        display_name = dname,
        name = dname,
        username = u::extensions.citext,
        city = nullif(trim(coalesce(p_city, '')), ''),
        province = nullif(trim(coalesce(p_province, '')), ''),
        country_code = cc,
        bio = nullif(trim(coalesce(p_bio, '')), ''),
        avatar_path = coalesce(p_avatar_path, avatar_path),
        locale = nullif(trim(coalesce(p_locale, '')), ''),
        timezone = nullif(trim(coalesce(p_timezone, '')), ''),
        location_text = nullif(trim(concat_ws(', ',
            nullif(trim(coalesce(p_city, '')), ''),
            nullif(trim(coalesce(p_province, '')), '')
        )), ''),
        profile_private = (vis = 'PRIVATE'),
        phone_public = p_show_phone,
        onboarding_status = 'COMPLETED',
        updated_at = timezone('utc', now())
    where id = uid
    returning * into result_row;

    if result_row.id is null then
        raise exception 'USER_NOT_FOUND';
    end if;

    insert into public.user_privacy_settings (
        user_id, profile_visibility, show_location, show_phone, allow_friend_requests, updated_at
    ) values (
        uid, vis, coalesce(p_show_location, true), coalesce(p_show_phone, false),
        coalesce(p_allow_friend_requests, true), timezone('utc', now())
    )
    on conflict (user_id) do update set
        profile_visibility = excluded.profile_visibility,
        show_location = excluded.show_location,
        show_phone = excluded.show_phone,
        allow_friend_requests = excluded.allow_friend_requests,
        updated_at = timezone('utc', now());

    return jsonb_build_object(
        'id', result_row.id,
        'username', result_row.username,
        'display_name', result_row.display_name,
        'avatar_path', result_row.avatar_path,
        'bio', result_row.bio,
        'city', result_row.city,
        'province', result_row.province,
        'country_code', result_row.country_code,
        'onboarding_status', result_row.onboarding_status,
        'account_status', result_row.account_status
    );
end;
$$;

revoke all on function public.complete_profile_onboarding(
    text, text, text, text, text, text, text, text, boolean, boolean, boolean, text, text
) from public;
grant execute on function public.complete_profile_onboarding(
    text, text, text, text, text, text, text, text, boolean, boolean, boolean, text, text
) to authenticated;

-- ---------------------------------------------------------------------------
-- L08 — org_hash_invitation_token: digest no resuelve con search_path = public
-- SQLSTATE 42883 (superficie en _resolve_invitation_by_token)
-- ---------------------------------------------------------------------------
create or replace function public.org_hash_invitation_token(p_token text)
returns text
language sql
immutable
as $$
    select encode(extensions.digest(trim(coalesce(p_token, '')), 'sha256'), 'hex');
$$;

-- ---------------------------------------------------------------------------
-- L09 — invite_organization_member: gen_random_bytes no resuelve con search_path = public
-- SQLSTATE 42883
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

    if v_user is not null and public.org_membership_is_active(p_organization_id, v_user) then
        raise exception 'ALREADY_MEMBER';
    end if;

    raw_token := encode(extensions.gen_random_bytes(32), 'hex');
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
-- L10 — m06_claim_outbox: CTE con UPDATE dentro de COALESCE (SQLSTATE 0A000)
-- ---------------------------------------------------------------------------
create or replace function public.m06_claim_outbox(p_worker_id text default 'manual', p_limit int default 10)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid := public.m06_require_active_actor();
    v_result jsonb;
begin
    if not public.has_permission('notifications.process') and not public.has_permission('moderation.view') then
        raise exception using errcode = '42501', message = 'M06_OUTBOX_FORBIDDEN';
    end if;

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
    select coalesce(jsonb_agg(to_jsonb(updated)), '[]'::jsonb)
    into v_result
    from updated;

    return v_result;
end;
$$;

revoke all on function public.m06_claim_outbox(text, int) from public, anon, authenticated;
grant execute on function public.m06_claim_outbox(text, int) to service_role;

-- ---------------------------------------------------------------------------
-- L11 — m06_claim_push_deliveries: CTE con UPDATE dentro de COALESCE (SQLSTATE 0A000)
-- ---------------------------------------------------------------------------
create or replace function public.m06_claim_push_deliveries(
    p_worker_id text default 'edge-push',
    p_limit int default 25
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_result jsonb;
begin
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
    select coalesce(jsonb_agg(
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
    ), '[]'::jsonb)
    into v_result
    from updated u
    join public.notifications n on n.id = u.notification_id
    left join public.notification_device_installations i
      on i.installation_id = u.installation_id
     and i.user_id = n.user_id;

    return v_result;
end;
$$;

revoke all on function public.m06_claim_push_deliveries(text, int) from public, anon, authenticated;
grant execute on function public.m06_claim_push_deliveries(text, int) to service_role;

comment on function public.is_username_available(text) is
  'M07-033 L05: username checks use extensions.citext under search_path=public.';
comment on function public.add_reputation_points(uuid, integer, text) is
  'M07-033 L06: v_badge_type + ON CONFLICT ON CONSTRAINT user_badges_user_id_badge_type_key.';
comment on function public.complete_profile_onboarding(text, text, text, text, text, text, text, text, boolean, boolean, boolean, text, text) is
  'M07-033 L07: username assignment uses extensions.citext under search_path=public.';
comment on function public.org_hash_invitation_token(text) is
  'M07-033 L08: uses extensions.digest for pgcrypto under callers with search_path=public.';
comment on function public.invite_organization_member(uuid, text, uuid, text, timestamptz) is
  'M07-033 L09: uses extensions.gen_random_bytes under search_path=public.';

-- ---------------------------------------------------------------------------
-- L08b — _resolve_invitation_by_token: UPDATE bajo marca STABLE (SQLSTATE 0A000)
-- Defecto propio adicional detectado por lint tras L08; debe ser VOLATILE.
-- ---------------------------------------------------------------------------
create or replace function public._resolve_invitation_by_token(p_token text)
returns public.organization_invitations
language plpgsql
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

comment on function public._resolve_invitation_by_token(text) is
  'M07-033 L08b: VOLATILE (was incorrectly STABLE while performing UPDATE).';

-- ---------------------------------------------------------------------------
-- L09 — invite_organization_member continues above; claim functions below
-- (invite body already replaced earlier in this migration)
-- ---------------------------------------------------------------------------

comment on function public.m06_claim_outbox(text, int) is
  'M07-033 L10: data-modifying CTE as top-level statement (not inside COALESCE).';
comment on function public.m06_claim_push_deliveries(text, int) is
  'M07-033 L11: data-modifying CTE as top-level statement (not inside COALESCE).';

commit;
