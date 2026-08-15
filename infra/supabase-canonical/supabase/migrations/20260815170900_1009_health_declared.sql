-- LeoVer Canonical Baseline
-- Logical migration: 1009
-- Declared health SoT (M08). Professional records live in 1016. No VitaCora copies.

create table public.pet_declared_health (
  pet_id uuid primary key references public.pets(id) on delete cascade,
  notes text null,
  updated_by uuid not null references public.persons(user_id),
  updated_at timestamptz not null default timezone('utc', now())
);

create table public.pet_allergies (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  name text not null,
  source text not null check (source in (
    'DECLARED', 'PROFESSIONAL', 'THIRD_PARTY', 'VERIFIED', 'INFERRED', 'SYSTEM'
  )),
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'ENDED')),
  actor_user_id uuid null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.pet_medications (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  name text not null,
  instructions text null,
  source text not null check (source in (
    'DECLARED', 'PROFESSIONAL', 'THIRD_PARTY', 'VERIFIED', 'INFERRED', 'SYSTEM'
  )),
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'ENDED')),
  actor_user_id uuid null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.pet_declared_vaccinations (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  vaccine_name text not null,
  administered_on date null,
  source text not null default 'DECLARED' check (source in (
    'DECLARED', 'THIRD_PARTY', 'VERIFIED', 'INFERRED', 'SYSTEM'
  )),
  actor_user_id uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.pet_parasite_treatments (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  kind text not null check (kind in ('DEWORMING', 'ANTIPARASITIC')),
  product_name text null,
  treated_on date not null,
  source text not null check (source in (
    'DECLARED', 'PROFESSIONAL', 'THIRD_PARTY', 'VERIFIED', 'INFERRED', 'SYSTEM'
  )),
  actor_user_id uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.pet_conditions (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  name text not null,
  source text not null check (source in (
    'DECLARED', 'PROFESSIONAL', 'THIRD_PARTY', 'VERIFIED', 'INFERRED', 'SYSTEM'
  )),
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'RESOLVED')),
  actor_user_id uuid null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.pet_weights (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  kilograms numeric(6,2) not null check (kilograms > 0),
  measured_on date not null,
  source text not null check (source in (
    'DECLARED', 'PROFESSIONAL', 'THIRD_PARTY', 'VERIFIED', 'INFERRED', 'SYSTEM'
  )),
  actor_user_id uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.pet_care_instructions (
  pet_id uuid primary key references public.pets(id) on delete cascade,
  feeding text null,
  medication text null,
  specials text null,
  updated_by uuid not null references public.persons(user_id),
  updated_at timestamptz not null default timezone('utc', now())
);
