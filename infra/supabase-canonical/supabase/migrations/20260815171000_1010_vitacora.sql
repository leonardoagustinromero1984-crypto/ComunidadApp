-- LeoVer Canonical Baseline
-- Logical migration: 1010
-- M14 VitaCora: composition, moments, grants, proposals, integration links.
-- No passport_*. No duplicate health tables.

create table public.vitacora_profiles (
  pet_id uuid primary key references public.pets(id) on delete cascade,
  visibility text not null default 'PRIVATE'
    check (visibility in ('PRIVATE', 'PUBLIC_REDACTED')),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);

create trigger vitacora_profiles_set_updated_at
  before update on public.vitacora_profiles
  for each row execute function public.set_updated_at();

create table public.vitacora_moments (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  kind text not null check (kind in (
    'ARRIVAL', 'BIRTHDAY', 'MEMORY', 'PHOTO', 'TRIP', 'MILESTONE', 'NOTE'
  )),
  title text null,
  body text null,
  asset_id uuid null references public.media_assets(id),
  visibility text not null default 'PRIVATE'
    check (visibility in ('PRIVATE', 'SHARED')),
  occurred_on date null,
  created_by uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  hidden_at timestamptz null
);

create table public.vitacora_access_grants (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  grantee_kind text not null check (grantee_kind in ('PERSON', 'ORGANIZATION')),
  grantee_person_id uuid null references public.persons(user_id),
  grantee_organization_id uuid null references public.organizations(id),
  purpose text not null,
  scope text not null check (scope in (
    'ESSENTIAL', 'HEALTH', 'ESSENTIAL_AND_HEALTH', 'FULL_SHAREABLE'
  )),
  granted_by_actor_user_id uuid not null references public.persons(user_id),
  granted_at timestamptz not null default timezone('utc', now()),
  expires_at timestamptz null,
  revoked_at timestamptz null,
  revoked_by uuid null references public.persons(user_id),
  constraint vitacora_grant_holder_xor check (
    public.holder_xor_ok(grantee_kind, grantee_person_id, grantee_organization_id)
  )
);

create index vitacora_access_grants_pet_idx
  on public.vitacora_access_grants (pet_id);
create index vitacora_access_grants_person_idx
  on public.vitacora_access_grants (grantee_person_id);
create index vitacora_access_grants_org_idx
  on public.vitacora_access_grants (grantee_organization_id);
create index vitacora_access_grants_expires_idx
  on public.vitacora_access_grants (expires_at)
  where revoked_at is null;

create table public.vitacora_update_proposals (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  origin_kind text not null check (origin_kind in (
    'VET', 'WALKER', 'TRAINER', 'CAREGIVER', 'DAYCARE', 'TRANSPORT', 'FOSTER', 'OTHER'
  )),
  payload jsonb not null,
  source_table text null,
  source_record_id uuid null,
  status text not null default 'PENDING'
    check (status in (
      'PENDING', 'ACCEPTED', 'REJECTED', 'CORRECTION_REQUESTED', 'CANCELLED'
    )),
  actor_user_id uuid not null references public.persons(user_id),
  organization_id uuid null references public.organizations(id),
  professional_profile_id uuid null,
  decided_by uuid null references public.persons(user_id),
  decided_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.vitacora_integration_links (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  source_table text not null,
  source_record_id uuid not null,
  visible boolean not null default true,
  created_by uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  hidden_at timestamptz null,
  unique (pet_id, source_table, source_record_id)
);
