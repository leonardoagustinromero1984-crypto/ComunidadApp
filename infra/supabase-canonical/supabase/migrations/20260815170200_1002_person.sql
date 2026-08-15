-- LeoVer Canonical Baseline
-- Logical migration: 1002
-- PERSON identity. No account_type. Age is derived. Birth date private.

create table public.persons (
  user_id uuid primary key references auth.users(id) on delete restrict,
  username text not null,
  display_name text not null,
  avatar_asset_id uuid null,
  birth_date date not null,
  age_assurance text not null default 'SELF_DECLARED'
    check (age_assurance in ('SELF_DECLARED', 'ACCOUNT_CONFIRMED', 'DOCUMENT_VERIFIED')),
  lifecycle_status text not null default 'ACTIVE'
    check (lifecycle_status in ('ACTIVE', 'SUSPENDED', 'PENDING_ERASURE', 'ERASED_MINIMIZED')),
  privacy_state text not null default 'PRIVATE'
    check (privacy_state in ('PRIVATE', 'PUBLIC_LIMITED')),
  protection_override text null
    check (protection_override in ('LOCKED', 'STANDARD')),
  email_verified_at timestamptz null,
  home_locality_id text null references public.location_nodes(id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  archived_at timestamptz null,
  constraint persons_username_format check (username ~ '^[a-z0-9._]{3,30}$')
);

create unique index persons_username_uidx on public.persons (lower(username));
create index persons_birth_date_idx on public.persons (birth_date);
create index persons_lifecycle_idx on public.persons (lifecycle_status);

create trigger persons_set_updated_at
  before update on public.persons
  for each row execute function public.set_updated_at();

create or replace function public.persons_enforce_age()
returns trigger
language plpgsql
as $$
begin
  if public.person_is_under_13(new.birth_date) then
    raise exception 'UNDER_13_AUTONOMOUS_ACCOUNT_DENIED';
  end if;
  return new;
end;
$$;

create trigger persons_enforce_age_ins
  before insert on public.persons
  for each row execute function public.persons_enforce_age();

create trigger persons_enforce_age_upd
  before update of birth_date on public.persons
  for each row execute function public.persons_enforce_age();

create table public.person_privacy_settings (
  user_id uuid primary key references public.persons(user_id) on delete cascade,
  profile_discoverable boolean not null default false,
  show_last_seen boolean not null default false,
  allow_indexing boolean not null default false,
  updated_at timestamptz not null default timezone('utc', now())
);

create table public.person_contact_controls (
  user_id uuid primary key references public.persons(user_id) on delete cascade,
  allow_unknown_dms boolean not null default false,
  allow_institutional_contact boolean not null default true,
  updated_at timestamptz not null default timezone('utc', now())
);

create table public.device_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.persons(user_id) on delete cascade,
  token text not null,
  platform text not null check (platform in ('ANDROID', 'IOS', 'WEB')),
  revoked_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now()),
  unique (user_id, token)
);

create table public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.persons(user_id),
  addressee_id uuid not null references public.persons(user_id),
  status text not null check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'BLOCKED')),
  created_at timestamptz not null default timezone('utc', now()),
  responded_at timestamptz null,
  constraint friendships_not_self check (requester_id <> addressee_id),
  unique (requester_id, addressee_id)
);

create table public.notification_preferences (
  user_id uuid primary key references public.persons(user_id) on delete cascade,
  operational_enabled boolean not null default true,
  quiet_hours_start time null,
  quiet_hours_end time null,
  updated_at timestamptz not null default timezone('utc', now())
);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_username text;
  v_birth date;
  v_name text;
begin
  v_username := lower(coalesce(new.raw_user_meta_data->>'username', ''));
  v_birth := nullif(new.raw_user_meta_data->>'birth_date', '')::date;
  v_name := coalesce(nullif(new.raw_user_meta_data->>'display_name', ''), v_username);
  if v_username = '' or v_birth is null then
    raise exception 'SIGNUP_REQUIRES_USERNAME_AND_BIRTH_DATE';
  end if;
  insert into public.persons (
    user_id, username, display_name, birth_date, email_verified_at, privacy_state
  ) values (
    new.id,
    v_username,
    v_name,
    v_birth,
    new.email_confirmed_at,
    case
      when public.person_age_band(v_birth) in ('TEEN_13_15', 'TEEN_16_17') then 'PRIVATE'
      else 'PUBLIC_LIMITED'
    end
  );
  insert into public.person_privacy_settings (user_id, profile_discoverable)
  values (
    new.id,
    public.person_age_band(v_birth) = 'ADULT_18_PLUS'
  );
  insert into public.person_contact_controls (user_id, allow_unknown_dms)
  values (
    new.id,
    public.person_age_band(v_birth) = 'ADULT_18_PLUS'
  );
  insert into public.notification_preferences (user_id) values (new.id);
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
