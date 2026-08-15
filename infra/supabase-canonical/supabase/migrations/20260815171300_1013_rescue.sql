-- LeoVer Canonical Baseline
-- Logical migration: 1013
-- Lost/found, adoption, foster. public_code foundations. Precise coords protected.

create table public.lost_found_alerts (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('LOST', 'FOUND')),
  pet_id uuid null references public.pets(id),
  created_by uuid not null references public.persons(user_id),
  public_code text not null default public.canon_public_code(),
  locality_id text null references public.location_nodes(id),
  precise_location extensions.geography(Point, 4326) null,
  status text not null default 'OPEN' check (status in ('OPEN', 'RESOLVED', 'HIDDEN')),
  created_at timestamptz not null default timezone('utc', now()),
  resolved_at timestamptz null
);

create unique index lost_found_alerts_public_code_uidx on public.lost_found_alerts (public_code);
create index lost_found_alerts_precise_gix on public.lost_found_alerts using gist (precise_location);

create table public.lost_found_sightings (
  id uuid primary key default gen_random_uuid(),
  alert_id uuid null references public.lost_found_alerts(id),
  reporter_user_id uuid not null references public.persons(user_id),
  locality_id text null references public.location_nodes(id),
  precise_location extensions.geography(Point, 4326) null,
  note text null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.lost_found_match_candidates (
  id uuid primary key default gen_random_uuid(),
  alert_id uuid not null references public.lost_found_alerts(id) on delete cascade,
  sighting_id uuid null references public.lost_found_sightings(id),
  score numeric(5,4) null,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'ACCEPTED', 'REJECTED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.lost_found_match_reviews (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references public.lost_found_match_candidates(id) on delete cascade,
  reviewer_user_id uuid not null references public.persons(user_id),
  decision text not null check (decision in ('ACCEPTED', 'REJECTED')),
  decided_at timestamptz not null default timezone('utc', now())
);

create table public.adoption_publications (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id),
  published_by uuid not null references public.persons(user_id),
  organization_id uuid null references public.organizations(id),
  public_code text not null default public.canon_public_code(),
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED', 'HIDDEN')),
  created_at timestamptz not null default timezone('utc', now())
);

create unique index adoption_publications_public_code_uidx on public.adoption_publications (public_code);

create table public.adoption_applications (
  id uuid primary key default gen_random_uuid(),
  publication_id uuid not null references public.adoption_publications(id) on delete cascade,
  applicant_user_id uuid not null references public.persons(user_id),
  status text not null default 'PENDING'
    check (status in ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.adoption_interviews (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references public.adoption_applications(id) on delete cascade,
  scheduled_at timestamptz not null,
  zone_id text not null default 'America/Argentina/Buenos_Aires',
  status text not null default 'SCHEDULED'
    check (status in ('SCHEDULED', 'DONE', 'CANCELLED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.adoption_agreements (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references public.adoption_applications(id) on delete cascade,
  decided_by uuid not null references public.persons(user_id),
  decided_at timestamptz not null default timezone('utc', now()),
  notes text null
);

create table public.adoption_followups (
  id uuid primary key default gen_random_uuid(),
  agreement_id uuid not null references public.adoption_agreements(id) on delete cascade,
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
  note text null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.foster_profiles (
  user_id uuid primary key references public.persons(user_id),
  capacity integer not null default 1 check (capacity >= 0),
  active boolean not null default true,
  locality_id text null references public.location_nodes(id),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.foster_placements (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id),
  foster_user_id uuid not null references public.foster_profiles(user_id),
  custody_id uuid null references public.pet_custody_records(id),
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED', 'CANCELLED')),
  starts_at timestamptz not null default timezone('utc', now()),
  ends_at timestamptz null,
  created_by uuid not null references public.persons(user_id)
);

create table public.foster_expenses (
  id uuid primary key default gen_random_uuid(),
  placement_id uuid not null references public.foster_placements(id) on delete cascade,
  amount numeric(12,2) not null,
  currency text not null default 'ARS',
  note text null,
  created_by uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now())
);
