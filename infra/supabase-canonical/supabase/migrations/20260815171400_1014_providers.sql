-- LeoVer Canonical Baseline
-- Logical migration: 1014
-- M22 providers: PERSON or ORGANIZATION. Shared structure, not one table per category.

create table public.service_providers (
  id uuid primary key default gen_random_uuid(),
  holder_kind text not null check (holder_kind in ('PERSON', 'ORGANIZATION')),
  holder_person_id uuid null references public.persons(user_id),
  holder_organization_id uuid null references public.organizations(id),
  display_name text not null,
  lifecycle_status text not null default 'ACTIVE'
    check (lifecycle_status in ('ACTIVE', 'ARCHIVED')),
  created_at timestamptz not null default timezone('utc', now()),
  constraint service_providers_holder_xor check (
    public.holder_xor_ok(holder_kind, holder_person_id, holder_organization_id)
  )
);

create table public.service_offerings (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.service_providers(id) on delete cascade,
  category_code text not null references public.service_categories(code),
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.provider_coverage_areas (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.service_providers(id) on delete cascade,
  locality_id text not null references public.location_nodes(id),
  created_at timestamptz not null default timezone('utc', now()),
  unique (provider_id, locality_id)
);
