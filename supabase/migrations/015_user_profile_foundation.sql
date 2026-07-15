-- M02 Etapa 3 — Fundación de perfil (NO asumir aplicada en remoto hasta staging).
-- Extiende public.users; no crea segunda tabla profiles.
-- NO modifica migraciones anteriores aplicadas.

create extension if not exists citext with schema extensions;
-- fallback si extensions schema no disponible en algunos entornos
do $$
begin
  create extension if not exists citext;
exception when others then
  null;
end $$;

alter table public.users
    add column if not exists username citext null,
    add column if not exists display_name text null,
    add column if not exists avatar_path text null,
    add column if not exists city text null,
    add column if not exists province text null,
    add column if not exists country_code text null,
    add column if not exists locale text null,
    add column if not exists timezone text null,
    add column if not exists onboarding_status text not null default 'NOT_STARTED',
    add column if not exists account_status text not null default 'ACTIVE';

alter table public.users drop constraint if exists users_onboarding_status_allowed;
alter table public.users
    add constraint users_onboarding_status_allowed
        check (onboarding_status in ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'BLOCKED'));

alter table public.users drop constraint if exists users_account_status_allowed;
alter table public.users
    add constraint users_account_status_allowed
        check (account_status in ('ACTIVE', 'RESTRICTED', 'SUSPENDED', 'BANNED'));

alter table public.users drop constraint if exists users_username_format;
alter table public.users
    add constraint users_username_format
        check (
            username is null or (
                length(username::text) between 3 and 30
                and username::text ~ '^[a-z0-9][a-z0-9._]*[a-z0-9]$|^[a-z0-9]{3,30}$'
                and username::text !~ '\.\.'
                and username::text !~ '\.$'
            )
        );

alter table public.users drop constraint if exists users_display_name_len;
alter table public.users
    add constraint users_display_name_len
        check (display_name is null or (char_length(trim(display_name)) between 2 and 80));

alter table public.users drop constraint if exists users_country_code_format;
alter table public.users
    add constraint users_country_code_format
        check (country_code is null or country_code ~ '^[A-Z]{2}$');

create unique index if not exists users_username_unique_idx
    on public.users (username)
    where username is not null;

create index if not exists users_onboarding_status_idx
    on public.users (onboarding_status);

create index if not exists users_account_status_idx
    on public.users (account_status);

-- Backfill display_name; no inventar usernames.
update public.users
set display_name = name
where display_name is null
  and name is not null
  and length(trim(name)) > 0;

update public.users
set onboarding_status = 'IN_PROGRESS'
where username is null
  and onboarding_status = 'NOT_STARTED';

create table if not exists public.reserved_usernames (
    username citext primary key,
    reason text null,
    active boolean not null default true
);

insert into public.reserved_usernames (username, reason) values
    ('admin', 'platform'),
    ('administrator', 'platform'),
    ('moderator', 'platform'),
    ('support', 'platform'),
    ('soporte', 'platform'),
    ('leover', 'brand'),
    ('system', 'platform'),
    ('sistema', 'platform'),
    ('root', 'platform'),
    ('api', 'platform'),
    ('www', 'platform')
on conflict (username) do nothing;

create table if not exists public.user_privacy_settings (
    user_id uuid primary key references auth.users (id) on delete cascade,
    profile_visibility text not null default 'PRIVATE',
    show_location boolean not null default true,
    show_phone boolean not null default false,
    allow_friend_requests boolean not null default true,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint user_privacy_visibility_allowed
        check (profile_visibility in ('PUBLIC', 'FRIENDS', 'PRIVATE'))
);

-- Backfill desde profile_private / phone_public.
insert into public.user_privacy_settings (
    user_id, profile_visibility, show_location, show_phone, allow_friend_requests
)
select
    u.id,
    case when coalesce(u.profile_private, true) then 'PRIVATE' else 'PUBLIC' end,
    true,
    coalesce(u.phone_public, false),
    true
from public.users u
on conflict (user_id) do nothing;

alter table public.user_privacy_settings enable row level security;

drop policy if exists user_privacy_select_own on public.user_privacy_settings;
create policy user_privacy_select_own
    on public.user_privacy_settings for select to authenticated
    using (auth.uid() = user_id);

drop policy if exists user_privacy_update_own on public.user_privacy_settings;
create policy user_privacy_update_own
    on public.user_privacy_settings for update to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists user_privacy_insert_own on public.user_privacy_settings;
create policy user_privacy_insert_own
    on public.user_privacy_settings for insert to authenticated
    with check (auth.uid() = user_id);

-- Trigger: alta siempre + privacy default (sin inventar username).
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
    resolved_name text := coalesce(
        new.raw_user_meta_data ->> 'name',
        split_part(coalesce(new.email, ''), '@', 1)
    );
begin
    insert into public.users (
        id, email, name, display_name, account_type, email_verified,
        profile_private, onboarding_status, account_status
    )
    values (
        new.id,
        coalesce(new.email, ''),
        resolved_name,
        resolved_name,
        resolved_account_type,
        new.email_confirmed_at is not null,
        true,
        'NOT_STARTED',
        'ACTIVE'
    )
    on conflict (id) do update set
        email = excluded.email,
        name = excluded.name,
        display_name = coalesce(public.users.display_name, excluded.display_name),
        email_verified = excluded.email_verified,
        updated_at = timezone('utc', now());

    insert into public.user_privacy_settings (user_id, profile_visibility)
    values (new.id, 'PRIVATE')
    on conflict (user_id) do nothing;

    if to_regclass('public.user_consents') is not null
       and terms_v is not null
       and privacy_v is not null
       and consent_source in ('registration', 'post_login_gate') then
        insert into public.user_consents (
            user_id, terms_version, privacy_version, accepted_at, locale, source
        )
        values (
            new.id, terms_v, privacy_v, timezone('utc', now()), consent_locale, consent_source
        )
        on conflict (user_id, terms_version, privacy_version) do nothing;
    end if;

    return new;
end;
$$;
