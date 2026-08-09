-- RC1.2 — Username obligatorio solo para altas NUEVAS.
-- NO modifica, renombra ni backfillea usernames de cuentas existentes.
-- NO hace NOT NULL sobre users.username (sigue permitiendo null histórico).

-- Reservados adicionales (idempotente).
insert into public.reserved_usernames (username, reason) values
    ('administrador', 'platform'),
    ('seguridad', 'platform'),
    ('security', 'platform'),
    ('oficial', 'brand'),
    ('official', 'brand'),
    ('moderacion', 'platform'),
    ('moderation', 'platform'),
    ('auth', 'platform'),
    ('login', 'platform'),
    ('register', 'platform'),
    ('user', 'platform'),
    ('usuario', 'platform'),
    ('sistemas', 'platform'),
    ('null', 'platform'),
    ('undefined', 'platform'),
    ('help', 'platform')
on conflict (username) do nothing;

-- Disponibilidad consultable en el alta (pre-auth). Solo booleano; sin datos sensibles.
revoke all on function public.is_username_available(text) from public;
grant execute on function public.is_username_available(text) to anon, authenticated;

-- Trigger: si el signup trae username en metadata, lo valida y lo asigna atómicamente.
-- Si falta o no está disponible, falla el alta (rollback de auth.users vía excepción).
-- Cuentas existentes no pasan por este camino.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    resolved_account_type text := 'PERSON';
    terms_v text := nullif(trim(coalesce(new.raw_user_meta_data ->> 'terms_version', '')), '');
    privacy_v text := nullif(trim(coalesce(new.raw_user_meta_data ->> 'privacy_version', '')), '');
    consent_locale text := nullif(trim(coalesce(new.raw_user_meta_data ->> 'consent_locale', '')), '');
    consent_source text := coalesce(
        nullif(trim(coalesce(new.raw_user_meta_data ->> 'consent_source', '')), ''),
        'registration'
    );
    resolved_name text := coalesce(
        nullif(trim(coalesce(new.raw_user_meta_data ->> 'name', '')), ''),
        split_part(coalesce(new.email, ''), '@', 1),
        'Usuario'
    );
    raw_username text := coalesce(new.raw_user_meta_data ->> 'username', '');
    u text := public.normalize_username(
        case when left(trim(raw_username), 1) = '@'
             then substr(trim(raw_username), 2)
             else trim(raw_username)
        end
    );
begin
    if u is null or not public.is_valid_username_format(u) then
        raise exception 'USERNAME_REQUIRED';
    end if;
    if exists (
        select 1 from public.reserved_usernames r
        where r.active and r.username = u::extensions.citext
    ) then
        raise exception 'USERNAME_RESERVED';
    end if;
    if exists (
        select 1 from public.users x
        where x.username = u::extensions.citext
    ) then
        raise exception 'USERNAME_UNAVAILABLE';
    end if;

    insert into public.users (
        id, email, name, display_name, username, account_type, email_verified,
        profile_private, onboarding_status, account_status
    )
    values (
        new.id,
        coalesce(new.email, ''),
        resolved_name,
        resolved_name,
        u::extensions.citext,
        resolved_account_type,
        new.email_confirmed_at is not null,
        true,
        'COMPLETED',
        'ACTIVE'
    )
    on conflict (id) do update set
        email = excluded.email,
        name = coalesce(nullif(trim(public.users.name), ''), excluded.name),
        display_name = coalesce(nullif(trim(public.users.display_name), ''), excluded.display_name),
        -- Nunca sobrescribe username existente de cuentas previas.
        username = coalesce(public.users.username, excluded.username),
        email_verified = excluded.email_verified,
        updated_at = timezone('utc', now());

    insert into public.user_privacy_settings (user_id)
    values (new.id)
    on conflict (user_id) do nothing;

    if terms_v is not null and privacy_v is not null then
        insert into public.user_consents (
            user_id, terms_version, privacy_version, locale, source
        )
        values (new.id, terms_v, privacy_v, consent_locale, consent_source)
        on conflict do nothing;
    end if;

    return new;
end;
$$;

comment on function public.handle_new_user() is
    'RC1.2: altas nuevas requieren username válido/único en metadata; no altera usernames existentes.';

comment on function public.is_username_available(text) is
    'Disponibilidad de username (anon+authenticated). No modifica filas.';
