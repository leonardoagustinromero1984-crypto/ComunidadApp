-- LeoVer Canonical Baseline
-- Logical migration: 1016
-- PERSON ≠ professional profile ≠ clinic organization. Admin non-vet cannot sign.

create table public.professional_profiles (
  id uuid primary key default gen_random_uuid(),
  person_id uuid not null references public.persons(user_id),
  license_number text null,
  active boolean not null default true,
  created_at timestamptz not null default timezone('utc', now()),
  unique (person_id)
);

create table public.clinic_affiliations (
  id uuid primary key default gen_random_uuid(),
  professional_profile_id uuid not null references public.professional_profiles(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'ENDED')),
  started_at timestamptz not null default timezone('utc', now()),
  ended_at timestamptz null,
  unique (professional_profile_id, organization_id)
);

create table public.veterinary_care_records (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id),
  actor_user_id uuid not null references public.persons(user_id),
  professional_profile_id uuid not null references public.professional_profiles(id),
  organization_id uuid not null references public.organizations(id),
  provenance text not null default 'PROFESSIONAL' check (provenance in (
    'DECLARED', 'PROFESSIONAL', 'THIRD_PARTY', 'VERIFIED', 'INFERRED', 'SYSTEM'
  )),
  summary text not null,
  care_on date not null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.veterinary_vaccination_records (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id),
  actor_user_id uuid not null references public.persons(user_id),
  professional_profile_id uuid not null references public.professional_profiles(id),
  organization_id uuid not null references public.organizations(id),
  vaccine_name text not null,
  administered_on date not null,
  provenance text not null default 'PROFESSIONAL',
  created_at timestamptz not null default timezone('utc', now())
);

create table public.veterinary_documents (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id),
  asset_id uuid not null references public.media_assets(id),
  actor_user_id uuid not null references public.persons(user_id),
  professional_profile_id uuid not null references public.professional_profiles(id),
  organization_id uuid not null references public.organizations(id),
  created_at timestamptz not null default timezone('utc', now())
);

alter table public.vitacora_update_proposals
  add constraint vitacora_update_proposals_professional_fk
  foreign key (professional_profile_id) references public.professional_profiles(id);
