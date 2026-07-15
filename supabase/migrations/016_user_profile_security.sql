-- M02 Etapa 3 — Seguridad: RPC allowlist + RLS deny-by-default en public.users.
-- Actualizaciones sensibles solo vía funciones; perfiles ajenos solo por RPC.

create or replace function public.normalize_username(p_username text)
returns text
language sql
immutable
as $$
    select nullif(lower(trim(coalesce(p_username, ''))), '');
$$;

create or replace function public.is_valid_username_format(p_username text)
returns boolean
language plpgsql
immutable
set search_path = public
as $$
declare
    u text := public.normalize_username(p_username);
begin
    if u is null then return false; end if;
    if length(u) < 3 or length(u) > 30 then return false; end if;
    if u ~ '\s' then return false; end if;
    if u !~ '^[a-z0-9]' then return false; end if;
    if right(u, 1) = '.' then return false; end if;
    if position('..' in u) > 0 then return false; end if;
    if u !~ '^[a-z0-9._]+$' then return false; end if;
    return true;
end;
$$;

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
        where r.active and r.username = u::citext
    ) then
        return false;
    end if;
    if exists (
        select 1 from public.users x
        where x.username = u::citext
          and x.id <> coalesce(auth.uid(), '00000000-0000-0000-0000-000000000000'::uuid)
    ) then
        return false;
    end if;
    return true;
end;
$$;

revoke all on function public.is_username_available(text) from public;
grant execute on function public.is_username_available(text) to authenticated;

create or replace function public.are_accepted_friends(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.friend_connections f
        where f.status = 'ACCEPTED'
          and (
            (f.requester_id = a and f.addressee_id = b)
            or (f.requester_id = b and f.addressee_id = a)
          )
    );
$$;

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
        username = u::citext,
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

create or replace function public.update_my_profile(
    p_display_name text default null,
    p_bio text default null,
    p_city text default null,
    p_province text default null,
    p_country_code text default null,
    p_locale text default null,
    p_timezone text default null,
    p_avatar_path text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    uid uuid := auth.uid();
    dname text := nullif(trim(coalesce(p_display_name, '')), '');
    cc text := nullif(upper(trim(coalesce(p_country_code, ''))), '');
    result_row public.users%rowtype;
begin
    if uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if dname is not null and (char_length(dname) < 2 or char_length(dname) > 80) then
        raise exception 'DISPLAY_NAME_INVALID';
    end if;
    if cc is not null and cc !~ '^[A-Z]{2}$' then
        raise exception 'COUNTRY_CODE_INVALID';
    end if;
    if p_avatar_path is not null
       and p_avatar_path not like ('users/' || uid::text || '/avatar/%') then
        raise exception 'AVATAR_PATH_INVALID';
    end if;

    update public.users set
        display_name = coalesce(dname, display_name),
        name = coalesce(dname, name),
        bio = case when p_bio is null then bio else nullif(trim(p_bio), '') end,
        city = case when p_city is null then city else nullif(trim(p_city), '') end,
        province = case when p_province is null then province else nullif(trim(p_province), '') end,
        country_code = coalesce(cc, country_code),
        locale = case when p_locale is null then locale else nullif(trim(p_locale), '') end,
        timezone = case when p_timezone is null then timezone else nullif(trim(p_timezone), '') end,
        avatar_path = coalesce(p_avatar_path, avatar_path),
        location_text = nullif(trim(concat_ws(', ',
            coalesce(case when p_city is null then city else nullif(trim(p_city), '') end, city),
            coalesce(case when p_province is null then province else nullif(trim(p_province), '') end, province)
        )), ''),
        updated_at = timezone('utc', now())
    where id = uid
    returning * into result_row;

    if result_row.id is null then
        raise exception 'USER_NOT_FOUND';
    end if;

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

revoke all on function public.update_my_profile(
    text, text, text, text, text, text, text, text
) from public;
grant execute on function public.update_my_profile(
    text, text, text, text, text, text, text, text
) to authenticated;

create or replace function public.get_public_user_profile(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    viewer uuid := auth.uid();
    u public.users%rowtype;
    priv public.user_privacy_settings%rowtype;
    is_self boolean;
    can_view boolean := false;
    loc text;
begin
    if viewer is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    select * into u from public.users where id = p_user_id;
    if u.id is null then
        return null;
    end if;

    is_self := (viewer = u.id);

    if not is_self and u.account_status in ('SUSPENDED', 'BANNED') then
        return null;
    end if;
    if not is_self and u.onboarding_status <> 'COMPLETED' then
        return null;
    end if;

    select * into priv from public.user_privacy_settings where user_id = u.id;
    if priv.user_id is null then
        priv.profile_visibility := case when u.profile_private then 'PRIVATE' else 'PUBLIC' end;
        priv.show_location := true;
    end if;

    if is_self then
        can_view := true;
    elsif priv.profile_visibility = 'PUBLIC' then
        can_view := true;
    elsif priv.profile_visibility = 'FRIENDS' then
        can_view := public.are_accepted_friends(viewer, u.id);
    else
        can_view := false;
    end if;

    if not can_view then
        return null;
    end if;

    if is_self or priv.show_location then
        loc := nullif(trim(concat_ws(', ', u.city, u.province, u.country_code)), '');
        if loc is null then loc := u.location_text; end if;
    else
        loc := null;
    end if;

    return jsonb_build_object(
        'id', u.id,
        'username', u.username,
        'display_name', coalesce(u.display_name, u.name),
        'avatar_path', u.avatar_path,
        'bio', u.bio,
        'location_text', loc,
        'city', case when is_self or priv.show_location then u.city else null end,
        'province', case when is_self or priv.show_location then u.province else null end,
        'country_code', case when is_self or priv.show_location then u.country_code else null end,
        'created_at', u.created_at
    );
end;
$$;

revoke all on function public.get_public_user_profile(uuid) from public;
grant execute on function public.get_public_user_profile(uuid) to authenticated;

create or replace function public.search_public_user_profiles(
    p_query text,
    p_limit int default 20,
    p_offset int default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    viewer uuid := auth.uid();
    q text := lower(trim(coalesce(p_query, '')));
    lim int := least(greatest(coalesce(p_limit, 20), 1), 50);
    off int := greatest(coalesce(p_offset, 0), 0);
    payload jsonb;
begin
    if viewer is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if char_length(q) < 2 then
        return '[]'::jsonb;
    end if;

    select coalesce(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    into payload
    from (
        select
            u.id,
            u.username::text as username,
            coalesce(u.display_name, u.name) as display_name,
            u.avatar_path,
            u.bio,
            case when p.show_location then
                nullif(trim(concat_ws(', ', u.city, u.province)), '')
            else null end as location_text
        from public.users u
        left join public.user_privacy_settings p on p.user_id = u.id
        where u.onboarding_status = 'COMPLETED'
          and u.account_status = 'ACTIVE'
          and u.id <> viewer
          and coalesce(p.profile_visibility, case when u.profile_private then 'PRIVATE' else 'PUBLIC' end)
              in ('PUBLIC', 'FRIENDS')
          and (
              coalesce(p.profile_visibility, 'PRIVATE') = 'PUBLIC'
              or public.are_accepted_friends(viewer, u.id)
          )
          and (
              u.username::text ilike '%' || q || '%'
              or coalesce(u.display_name, u.name) ilike '%' || q || '%'
          )
        order by coalesce(u.display_name, u.name), u.id
        limit lim offset off
    ) t;

    return payload;
end;
$$;

revoke all on function public.search_public_user_profiles(text, int, int) from public;
grant execute on function public.search_public_user_profiles(text, int, int) to authenticated;

-- RLS: solo fila propia por SELECT directo; sin UPDATE/INSERT/DELETE cliente.
drop policy if exists users_select_authenticated on public.users;
drop policy if exists users_insert_own on public.users;
drop policy if exists users_update_own on public.users;

drop policy if exists users_select_own on public.users;
create policy users_select_own
    on public.users for select to authenticated
    using (auth.uid() = id);

-- INSERT/UPDATE/DELETE directos revocados para authenticated (RPC + trigger definer).
revoke insert, update, delete on public.users from authenticated;
grant select on public.users to authenticated;
