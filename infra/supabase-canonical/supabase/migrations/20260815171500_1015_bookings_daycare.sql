-- LeoVer Canonical Baseline
-- Logical migration: 1015
-- M23 bookings + daycare stay. Booking != VitaCora grant. Public consent DEFAULT NO.

create table public.availability_slots (
  id uuid primary key default gen_random_uuid(),
  offering_id uuid not null references public.service_offerings(id) on delete cascade,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  zone_id text not null,
  status text not null default 'OPEN' check (status in ('OPEN', 'BOOKED', 'CANCELLED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.bookings (
  id uuid primary key default gen_random_uuid(),
  offering_id uuid not null references public.service_offerings(id),
  provider_id uuid not null references public.service_providers(id),
  pet_id uuid not null references public.pets(id),
  booked_by uuid not null references public.persons(user_id),
  vitacora_grant_id uuid null references public.vitacora_access_grants(id),
  starts_at timestamptz not null,
  ends_at timestamptz null,
  zone_id text not null,
  status text not null default 'REQUESTED'
    check (status in (
      'REQUESTED', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW'
    )),
  created_at timestamptz not null default timezone('utc', now())
);

create index bookings_starts_zone_idx on public.bookings (starts_at, zone_id);
create index bookings_provider_starts_idx on public.bookings (provider_id, starts_at);
create index bookings_pet_status_idx on public.bookings (pet_id, status);

create table public.booking_participants (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null references public.bookings(id) on delete cascade,
  person_id uuid null references public.persons(user_id),
  organization_id uuid null references public.organizations(id),
  role text not null default 'CLIENT'
);

create table public.booking_instruction_snapshots (
  booking_id uuid primary key references public.bookings(id) on delete cascade,
  feeding text null,
  medication text null,
  public_consent_id uuid null,
  agreed_scope text null,
  emergency_contact text null,
  specials text null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.daycare_stays (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null unique references public.bookings(id),
  pet_id uuid not null references public.pets(id),
  custody_id uuid null references public.pet_custody_records(id),
  checked_in_at timestamptz null,
  checked_out_at timestamptz null,
  status text not null default 'RESERVED'
    check (status in ('RESERVED', 'IN_STAY', 'COMPLETED', 'CANCELLED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.daycare_care_events (
  id uuid primary key default gen_random_uuid(),
  stay_id uuid not null references public.daycare_stays(id) on delete cascade,
  event_type text not null references public.care_event_types(code),
  note text null,
  asset_id uuid null references public.media_assets(id),
  actor_user_id uuid not null references public.persons(user_id),
  occurred_at timestamptz not null default timezone('utc', now())
);

create table public.daycare_incidents (
  id uuid primary key default gen_random_uuid(),
  stay_id uuid not null references public.daycare_stays(id) on delete cascade,
  severity text not null check (severity in ('LOW', 'MEDIUM', 'HIGH')),
  note text not null,
  actor_user_id uuid not null references public.persons(user_id),
  occurred_at timestamptz not null default timezone('utc', now())
);

create table public.daycare_public_consents (
  id uuid primary key default gen_random_uuid(),
  stay_id uuid not null unique references public.daycare_stays(id) on delete cascade,
  granted boolean not null default false,
  granted_by uuid not null references public.persons(user_id),
  granted_at timestamptz null,
  revoked_at timestamptz null
);

alter table public.booking_instruction_snapshots
  add constraint booking_instruction_snapshots_public_consent_fk
  foreign key (public_consent_id) references public.daycare_public_consents(id);
