-- LeoVer Canonical Baseline
-- Logical migration: 1001
-- System catalogs and geography. Seeds of lookup rows are in 1021.

create table public.location_nodes (
  id text primary key,
  kind text not null check (kind in ('COUNTRY', 'PROVINCE', 'LOCALITY')),
  parent_id text null references public.location_nodes(id),
  name text not null,
  iso_code text null,
  sort_key integer not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default timezone('utc', now()),
  constraint location_nodes_parent_kind check (
    (kind = 'COUNTRY' and parent_id is null)
    or (kind = 'PROVINCE' and parent_id is not null)
    or (kind = 'LOCALITY' and parent_id is not null)
  )
);

create index location_nodes_parent_kind_idx
  on public.location_nodes (parent_id, kind);

create table public.species (
  code text primary key,
  name text not null,
  sort_key integer not null default 0,
  active boolean not null default true
);

create table public.breeds (
  id uuid primary key default gen_random_uuid(),
  species_code text not null references public.species(code),
  name text not null,
  sort_key integer not null default 0,
  active boolean not null default true,
  unique (species_code, name)
);

create table public.permission_codes (
  code text primary key,
  scope text not null check (scope in ('PET', 'VITACORA', 'ORG', 'PLATFORM')),
  description text not null,
  active boolean not null default true
);

create table public.age_capability_rules (
  action_code text primary key,
  min_age_band text not null check (min_age_band in (
    'UNDER_13', 'TEEN_13_15', 'TEEN_16_17', 'ADULT_18_PLUS'
  )),
  requires_guardian_confirmation boolean not null default false,
  requires_contextual_consent boolean not null default false,
  notes text null
);

create table public.service_categories (
  code text primary key,
  name text not null,
  sort_key integer not null default 0,
  active boolean not null default true
);

create table public.care_event_types (
  code text primary key,
  name text not null,
  sort_key integer not null default 0,
  active boolean not null default true
);

create table public.moderation_reason_codes (
  code text primary key,
  name text not null,
  sort_key integer not null default 0,
  active boolean not null default true
);
