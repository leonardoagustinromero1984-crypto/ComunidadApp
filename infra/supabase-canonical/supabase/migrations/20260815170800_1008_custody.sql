-- LeoVer Canonical Baseline
-- Logical migration: 1008
-- Temporary custody projection. Authority is the source domain record.

create table public.pet_custody_records (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  custodian_kind text not null check (custodian_kind in ('PERSON', 'ORGANIZATION')),
  custodian_person_id uuid null references public.persons(user_id),
  custodian_organization_id uuid null references public.organizations(id),
  source_domain text not null check (source_domain in (
    'FOSTER', 'DAYCARE', 'TRANSPORT', 'OTHER'
  )),
  source_record_id uuid not null,
  purpose text not null,
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'ENDED', 'CANCELLED')),
  starts_at timestamptz not null default timezone('utc', now()),
  ends_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint pet_custody_holder_xor check (
    public.holder_xor_ok(custodian_kind, custodian_person_id, custodian_organization_id)
  )
);

create unique index pet_custody_one_active_uidx
  on public.pet_custody_records (pet_id)
  where status = 'ACTIVE';

create index pet_custody_pet_idx on public.pet_custody_records (pet_id);
