-- LeoVer Canonical Baseline
-- Logical migration: 1000
-- Extensions and immutable/stable helpers. No AccountType. No passport_*.

create extension if not exists pgcrypto with schema extensions;
create extension if not exists postgis with schema extensions;
create extension if not exists pg_trgm with schema extensions;

create or replace function public.canon_now()
returns timestamptz
language sql
stable
as $$
  select timezone('utc', now());
$$;

create or replace function public.person_age_years(p_birth_date date, p_as_of date default current_date)
returns integer
language sql
stable
as $$
  select case
    when p_birth_date is null then null
    else ((p_as_of - p_birth_date) / 365.2425)::int
  end;
$$;

create or replace function public.person_age_band(p_birth_date date, p_as_of date default current_date)
returns text
language sql
stable
as $$
  select case
    when p_birth_date is null then null
    when public.person_age_years(p_birth_date, p_as_of) < 13 then 'UNDER_13'
    when public.person_age_years(p_birth_date, p_as_of) < 16 then 'TEEN_13_15'
    when public.person_age_years(p_birth_date, p_as_of) < 18 then 'TEEN_16_17'
    else 'ADULT_18_PLUS'
  end;
$$;

create or replace function public.person_is_under_13(p_birth_date date, p_as_of date default current_date)
returns boolean
language sql
stable
as $$
  select public.person_age_band(p_birth_date, p_as_of) = 'UNDER_13';
$$;

create or replace function public.holder_xor_ok(
  p_kind text,
  p_person_id uuid,
  p_organization_id uuid
)
returns boolean
language sql
immutable
as $$
  select (
    (p_kind = 'PERSON' and p_person_id is not null and p_organization_id is null)
    or
    (p_kind = 'ORGANIZATION' and p_organization_id is not null and p_person_id is null)
  );
$$;

create or replace function public.canon_public_code()
returns text
language sql
volatile
as $$
  select upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
$$;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = public.canon_now();
  return new;
end;
$$;
