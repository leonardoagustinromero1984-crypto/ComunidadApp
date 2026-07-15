-- M01 Etapa 3 — user_consents + actualización idempotente de handle_new_user
-- Base: lógica vigente en 009_profile_privacy.sql (conserva public.users + profile_private).
-- 004 y 009 NO se sobrescriben; esta migración reemplaza solo la función.

create table if not exists public.user_consents (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    terms_version text not null,
    privacy_version text not null,
    accepted_at timestamptz not null default timezone('utc', now()),
    locale text null,
    source text not null,
    created_at timestamptz not null default timezone('utc', now()),
    constraint user_consents_terms_version_not_blank
        check (length(trim(terms_version)) > 0),
    constraint user_consents_privacy_version_not_blank
        check (length(trim(privacy_version)) > 0),
    constraint user_consents_source_allowed
        check (source in ('registration')),
    constraint user_consents_user_versions_unique
        unique (user_id, terms_version, privacy_version)
);

create index if not exists user_consents_user_id_idx
    on public.user_consents (user_id);

alter table public.user_consents enable row level security;

drop policy if exists user_consents_select_own on public.user_consents;
create policy user_consents_select_own
    on public.user_consents
    for select
    to authenticated
    using (auth.uid() = user_id);

-- Sin INSERT/UPDATE/DELETE para authenticated: el alta inicial la hace el trigger (security definer).
-- service_role conserva acceso administrativo por defecto en Supabase.

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
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
begin
    -- Solo PERSON en alta M01 (no confiar en account_type del cliente).
    -- Conservamos columna account_type del perfil con default técnico PERSON.
    if terms_v is null or privacy_v is null then
        raise exception 'CONSENT_REQUIRED: terms_version and privacy_version metadata are required';
    end if;

    if consent_source <> 'registration' then
        raise exception 'CONSENT_SOURCE_INVALID: only registration is allowed at signup';
    end if;

    insert into public.users (id, email, name, account_type, email_verified, profile_private)
    values (
        new.id,
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data ->> 'name', split_part(coalesce(new.email, ''), '@', 1)),
        resolved_account_type,
        new.email_confirmed_at is not null,
        true
    )
    on conflict (id) do update set
        email = excluded.email,
        name = excluded.name,
        account_type = excluded.account_type,
        email_verified = excluded.email_verified,
        updated_at = timezone('utc', now());

    insert into public.user_consents (
        user_id,
        terms_version,
        privacy_version,
        accepted_at,
        locale,
        source
    )
    values (
        new.id,
        terms_v,
        privacy_v,
        timezone('utc', now()),
        consent_locale,
        consent_source
    )
    on conflict (user_id, terms_version, privacy_version) do nothing;

    return new;
end;
$$;

-- Trigger ya existe desde 004; no recrear a menos que falte.
do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'on_auth_user_created'
    ) then
        create trigger on_auth_user_created
            after insert on auth.users
            for each row execute function public.handle_new_user();
    end if;
end $$;
