-- LeoVer Canonical Baseline
-- Logical migration: 1005
-- Organizations are multicapability. type/label is not authority.

create table public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text not null,
  primary_label text null,
  logo_asset_id uuid null,
  cover_asset_id uuid null,
  home_locality_id text null references public.location_nodes(id),
  lifecycle_status text not null default 'ACTIVE'
    check (lifecycle_status in ('ACTIVE', 'ARCHIVED')),
  created_by_user_id uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  archived_at timestamptz null,
  constraint organizations_slug_format check (slug ~ '^[a-z0-9-]{3,60}$')
);

create unique index organizations_slug_uidx on public.organizations (lower(slug));

create trigger organizations_set_updated_at
  before update on public.organizations
  for each row execute function public.set_updated_at();

create table public.organization_capabilities (
  organization_id uuid not null references public.organizations(id) on delete cascade,
  capability text not null check (capability in (
    'SHELTER', 'NGO', 'VETERINARY_CLINIC', 'DAYCARE', 'PROVIDER', 'OTHER'
  )),
  created_at timestamptz not null default timezone('utc', now()),
  primary key (organization_id, capability)
);

create table public.organization_branches (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  name text not null,
  locality_id text null references public.location_nodes(id),
  lifecycle_status text not null default 'ACTIVE'
    check (lifecycle_status in ('ACTIVE', 'ARCHIVED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.organization_public_profiles (
  organization_id uuid primary key references public.organizations(id) on delete cascade,
  public_code text not null default public.canon_public_code(),
  bio text null,
  published boolean not null default false,
  updated_at timestamptz not null default timezone('utc', now()),
  unique (public_code)
);

create table public.organization_roles (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid null references public.organizations(id) on delete cascade,
  code text not null,
  name text not null,
  is_system boolean not null default false,
  unique (organization_id, code)
);

create table public.organization_role_permissions (
  role_id uuid not null references public.organization_roles(id) on delete cascade,
  permission_code text not null references public.permission_codes(code),
  primary key (role_id, permission_code)
);

create table public.organization_memberships (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  person_id uuid not null references public.persons(user_id),
  role_id uuid not null references public.organization_roles(id),
  status text not null default 'ACTIVE'
    check (status in ('INVITED', 'ACTIVE', 'ENDED')),
  valid_from timestamptz not null default timezone('utc', now()),
  valid_until timestamptz null,
  created_at timestamptz not null default timezone('utc', now())
);

create unique index organization_memberships_active_uidx
  on public.organization_memberships (organization_id, person_id)
  where status = 'ACTIVE';

create index organization_memberships_person_status_idx
  on public.organization_memberships (person_id, status);

create table public.organization_invitations (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  email text null,
  invitee_user_id uuid null references public.persons(user_id),
  role_id uuid not null references public.organization_roles(id),
  invited_by uuid not null references public.persons(user_id),
  status text not null default 'PENDING'
    check (status in ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
  expires_at timestamptz not null,
  created_at timestamptz not null default timezone('utc', now()),
  accepted_at timestamptz null
);
