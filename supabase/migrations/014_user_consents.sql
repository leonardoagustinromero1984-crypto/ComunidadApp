-- M01 Etapa 4 — corrección D-M01-10 sobre 014 (aún NO aplicada en remoto compartido).
-- Estrategia: siempre crear public.users; insertar user_consents SOLO si metadata legal válida.
-- NO inventar consentimiento. NO fallar altas admin/invite por falta de metadata.

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
        check (source in ('registration', 'post_login_gate')),
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

-- Sin INSERT directo cliente: altas iniciales por trigger; posteriores vía RPC security definer.

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
    -- Siempre crear/actualizar public.users (conserva 009: profile_private).
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

    -- Consentimiento solo si metadata válida; no inventar aceptación.
    if terms_v is not null
       and privacy_v is not null
       and consent_source in ('registration', 'post_login_gate') then
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
    end if;

    return new;
end;
$$;

do $$
begin
    if not exists (
        select 1 from pg_trigger where tgname = 'on_auth_user_created'
    ) then
        create trigger on_auth_user_created
            after insert on auth.users
            for each row execute function public.handle_new_user();
    end if;
end $$;

-- Aceptación posterior (gate LegalConsentRequired): usa auth.uid(), nunca user_id libre.
create or replace function public.accept_legal_consents(
    p_terms_version text,
    p_privacy_version text,
    p_locale text default null,
    p_source text default 'post_login_gate'
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    uid uuid := auth.uid();
    terms_v text := nullif(trim(coalesce(p_terms_version, '')), '');
    privacy_v text := nullif(trim(coalesce(p_privacy_version, '')), '');
    src text := coalesce(nullif(trim(coalesce(p_source, '')), ''), 'post_login_gate');
begin
    if uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if terms_v is null or privacy_v is null then
        raise exception 'CONSENT_VERSIONS_REQUIRED';
    end if;
    if src not in ('registration', 'post_login_gate') then
        raise exception 'CONSENT_SOURCE_INVALID';
    end if;

    insert into public.user_consents (
        user_id, terms_version, privacy_version, accepted_at, locale, source
    ) values (
        uid, terms_v, privacy_v, timezone('utc', now()), nullif(trim(coalesce(p_locale, '')), ''), src
    )
    on conflict (user_id, terms_version, privacy_version) do nothing;
end;
$$;

revoke all on function public.accept_legal_consents(text, text, text, text) from public;
grant execute on function public.accept_legal_consents(text, text, text, text) to authenticated;

-- Solicitudes de eliminación (idempotencia / soporte; sin PII).
create table if not exists public.account_deletion_requests (
    id uuid primary key default gen_random_uuid(),
    user_id uuid null references auth.users (id) on delete set null,
    status text not null,
    requested_at timestamptz not null default timezone('utc', now()),
    completed_at timestamptz null,
    failure_code text null,
    created_at timestamptz not null default timezone('utc', now()),
    constraint account_deletion_requests_status_allowed
        check (status in ('pending', 'completed', 'failed'))
);

create index if not exists account_deletion_requests_user_id_idx
    on public.account_deletion_requests (user_id);

alter table public.account_deletion_requests enable row level security;

-- Lectura propia opcional; insert/update solo service role (Edge Function).
drop policy if exists account_deletion_requests_select_own on public.account_deletion_requests;
create policy account_deletion_requests_select_own
    on public.account_deletion_requests
    for select
    to authenticated
    using (auth.uid() = user_id);
