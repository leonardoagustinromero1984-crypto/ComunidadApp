-- LeoVer Canonical Baseline
-- Logical migration: 1007
-- Pet identity, multi-owner, family permissions, history. No owner_id. No age_years SoT.

create table public.pets (
  id uuid primary key default gen_random_uuid(),
  created_by_user_id uuid not null references public.persons(user_id),
  name text not null,
  species_code text not null references public.species(code),
  breed_id uuid null references public.breeds(id),
  sex text null check (sex in ('FEMALE', 'MALE', 'UNKNOWN')),
  size text null check (size in ('SMALL', 'MEDIUM', 'LARGE', 'UNKNOWN')),
  avatar_asset_id uuid null references public.media_assets(id),
  public_code text not null default public.canon_public_code(),
  home_locality_id text null references public.location_nodes(id),
  birth_precision text not null default 'UNKNOWN'
    check (birth_precision in (
      'EXACT_DATE', 'MONTH_PRECISION', 'YEAR_PRECISION', 'ESTIMATED', 'UNKNOWN'
    )),
  birth_date date null,
  birth_year integer null,
  birth_month integer null check (birth_month between 1 and 12),
  estimated_age_months integer null check (estimated_age_months >= 0),
  estimated_as_of date null,
  lifecycle_status text not null default 'ACTIVE'
    check (lifecycle_status in ('ACTIVE', 'DECEASED', 'ARCHIVED')),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  archived_at timestamptz null,
  constraint pets_birth_precision_ok check (
    (birth_precision = 'EXACT_DATE' and birth_date is not null)
    or (birth_precision = 'MONTH_PRECISION' and birth_year is not null and birth_month is not null)
    or (birth_precision = 'YEAR_PRECISION' and birth_year is not null)
    or (birth_precision = 'ESTIMATED' and estimated_age_months is not null and estimated_as_of is not null)
    or (birth_precision = 'UNKNOWN')
  )
);

create unique index pets_public_code_uidx on public.pets (public_code);
create index pets_created_by_idx on public.pets (created_by_user_id);
create index pets_lifecycle_idx on public.pets (lifecycle_status);

create trigger pets_set_updated_at
  before update on public.pets
  for each row execute function public.set_updated_at();

create table public.pet_lifecycle_events (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  from_status text null,
  to_status text not null,
  actor_user_id uuid null references public.persons(user_id),
  occurred_at timestamptz not null default timezone('utc', now()),
  note text null
);

create table public.pet_responsibility_links (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  holder_kind text not null check (holder_kind in ('PERSON', 'ORGANIZATION')),
  holder_person_id uuid null references public.persons(user_id),
  holder_organization_id uuid null references public.organizations(id),
  role text not null check (role in ('OWNER', 'AUTHORIZED', 'RESPONSIBLE')),
  status text not null default 'ACTIVE'
    check (status in ('PENDING', 'ACTIVE', 'ENDED')),
  valid_from timestamptz not null default timezone('utc', now()),
  valid_until timestamptz null,
  granted_by_actor_user_id uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  constraint pet_responsibility_holder_xor check (
    public.holder_xor_ok(holder_kind, holder_person_id, holder_organization_id)
  ),
  constraint pet_responsibility_role_kind check (
    (holder_kind = 'PERSON' and role in ('OWNER', 'AUTHORIZED'))
    or (holder_kind = 'ORGANIZATION' and role = 'RESPONSIBLE')
  )
);

create index pet_responsibility_links_pet_status_idx
  on public.pet_responsibility_links (pet_id, status);
create index pet_responsibility_links_person_status_idx
  on public.pet_responsibility_links (holder_person_id, status);
create index pet_responsibility_links_org_status_idx
  on public.pet_responsibility_links (holder_organization_id, status);

create unique index pet_responsibility_one_org_active_uidx
  on public.pet_responsibility_links (pet_id)
  where holder_kind = 'ORGANIZATION' and status = 'ACTIVE';

create table public.pet_responsibility_events (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  link_id uuid null references public.pet_responsibility_links(id),
  actor_user_id uuid not null references public.persons(user_id),
  event_type text not null,
  occurred_at timestamptz not null default timezone('utc', now()),
  metadata jsonb not null default '{}'::jsonb
);

create table public.pet_family_invitations (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  invitee_user_id uuid not null references public.persons(user_id),
  role text not null check (role in ('OWNER', 'AUTHORIZED')),
  invited_by uuid not null references public.persons(user_id),
  status text not null default 'PENDING'
    check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
  expires_at timestamptz not null,
  created_at timestamptz not null default timezone('utc', now()),
  accepted_at timestamptz null
);

create table public.pet_permission_grants (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  link_id uuid null references public.pet_responsibility_links(id) on delete cascade,
  subject_person_id uuid null references public.persons(user_id),
  subject_organization_id uuid null references public.organizations(id),
  permission_code text not null references public.permission_codes(code),
  granted_by uuid not null references public.persons(user_id),
  granted_at timestamptz not null default timezone('utc', now()),
  revoked_at timestamptz null
);

create index pet_permission_grants_pet_subject_idx
  on public.pet_permission_grants (pet_id, subject_person_id);
