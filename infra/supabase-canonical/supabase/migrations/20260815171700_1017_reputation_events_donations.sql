-- LeoVer Canonical Baseline
-- Logical migration: 1017
-- Reviews after eligible interaction. Donations: direct transfer, 0% LeoVer, no checkout.

create table public.reviews (
  id uuid primary key default gen_random_uuid(),
  source_booking_id uuid null references public.bookings(id),
  reviewer_user_id uuid not null references public.persons(user_id),
  reviewee_person_id uuid null references public.persons(user_id),
  reviewee_organization_id uuid null references public.organizations(id),
  rating integer not null check (rating between 1 and 5),
  body text null,
  hidden_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now()),
  unique (source_booking_id)
);

create table public.review_disputes (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.reviews(id) on delete cascade,
  opened_by uuid not null references public.persons(user_id),
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.actor_verifications (
  id uuid primary key default gen_random_uuid(),
  subject_kind text not null check (subject_kind in ('PERSON', 'ORGANIZATION')),
  subject_person_id uuid null references public.persons(user_id),
  subject_organization_id uuid null references public.organizations(id),
  kind text not null,
  status text not null default 'GRANTED' check (status in ('GRANTED', 'REVOKED')),
  granted_at timestamptz not null default timezone('utc', now()),
  revoked_at timestamptz null
);

create table public.community_events (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid null references public.organizations(id),
  created_by uuid not null references public.persons(user_id),
  title text not null,
  starts_at timestamptz not null,
  zone_id text not null,
  locality_id text null references public.location_nodes(id),
  hidden_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.event_registrations (
  event_id uuid not null references public.community_events(id) on delete cascade,
  user_id uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  primary key (event_id, user_id)
);

create table public.donation_campaigns (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid null references public.organizations(id),
  created_by uuid not null references public.persons(user_id),
  title text not null,
  alias_cbu text null,
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.donation_contributions (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.donation_campaigns(id) on delete cascade,
  contributor_user_id uuid null references public.persons(user_id),
  transfer_reference text not null,
  amount numeric(12,2) null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.in_kind_offers (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid null references public.donation_campaigns(id),
  offered_by uuid not null references public.persons(user_id),
  description text not null,
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.volunteer_offers (
  id uuid primary key default gen_random_uuid(),
  offered_by uuid not null references public.persons(user_id),
  description text not null,
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now())
);
