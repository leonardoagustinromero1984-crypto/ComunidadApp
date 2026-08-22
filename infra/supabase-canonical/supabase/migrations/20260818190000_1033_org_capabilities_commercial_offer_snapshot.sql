-- LeoVer Canonical
-- Logical migration: 1033
-- ONB-03 / ORG-01 / COMMERCIAL-01:
--   extend organization capability allow-list;
--   snapshot LeoVer Comercial trial offers at activation.
-- Forward-only. Do not edit 1000-1032.
-- Staging only.

alter table public.organization_capabilities
  drop constraint if exists organization_capabilities_capability_check;

alter table public.organization_capabilities
  add constraint organization_capabilities_capability_check
  check (capability in (
    'SHELTER', 'NGO', 'VETERINARY_CLINIC', 'DAYCARE', 'PROVIDER', 'OTHER',
    'GROOMING', 'WALKING_CARE', 'TRAINING', 'BRAND'
  ));

create or replace function public.canon_create_organization(
  p_name text,
  p_slug text,
  p_capability text,
  p_home_locality_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_role uuid;
  v_capability text;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_age_allows(auth.uid(), 'org.create') then
    raise exception 'AGE_CAPABILITY_DENIED';
  end if;
  v_capability := upper(btrim(p_capability));
  if v_capability = 'BOARDING' then
    v_capability := 'DAYCARE';
  end if;
  if v_capability not in (
    'SHELTER', 'NGO', 'VETERINARY_CLINIC', 'DAYCARE', 'PROVIDER', 'OTHER',
    'GROOMING', 'WALKING_CARE', 'TRAINING', 'BRAND'
  ) then
    v_capability := 'OTHER';
  end if;

  select o.id into v_id
  from public.organizations o
  where lower(o.slug) = lower(btrim(p_slug))
  limit 1;

  if v_id is not null then
    if exists (
      select 1
      from public.organization_memberships m
      where m.organization_id = v_id
        and m.person_id = auth.uid()
        and m.status = 'ACTIVE'
    ) then
      return v_id;
    end if;
    raise exception 'ORGANIZATION_SLUG_TAKEN';
  end if;

  insert into public.organizations (name, slug, created_by_user_id, primary_label, home_locality_id)
  values (p_name, p_slug, auth.uid(), v_capability, p_home_locality_id)
  returning id into v_id;

  insert into public.organization_capabilities (organization_id, capability)
  values (v_id, v_capability)
  on conflict do nothing;

  insert into public.organization_roles (organization_id, code, name, is_system)
  values (v_id, 'OWNER', 'Administrador', true)
  returning id into v_role;

  insert into public.organization_role_permissions (role_id, permission_code)
  select v_role, code from public.permission_codes where scope = 'ORG';

  insert into public.organization_memberships (organization_id, person_id, role_id, status)
  values (v_id, auth.uid(), v_role, 'ACTIVE');

  insert into public.organization_public_profiles (organization_id)
  values (v_id)
  on conflict do nothing;

  perform public.canon_audit('org.create', 'organizations', v_id, '{}'::jsonb);
  return v_id;
end;
$$;

create table if not exists public.commercial_offer_snapshots (
  id uuid primary key default gen_random_uuid(),
  person_id uuid not null references public.persons(user_id),
  organization_id uuid null references public.organizations(id),
  offer_code text not null,
  commercial_tier text not null check (commercial_tier in (
    'LEOVER_COMMERCIAL_PROFESSIONAL',
    'LEOVER_COMMERCIAL_ORGANIZATION'
  )),
  trial_started_at timestamptz not null default timezone('utc', now()),
  trial_ends_at timestamptz not null,
  trial_days_snapshot integer not null,
  payment_required_at_activation_snapshot boolean not null default false,
  price_snapshot text null,
  currency text null,
  status text not null default 'TRIAL_ACTIVE' check (status in (
    'TRIAL_ACTIVE', 'ACTIVE', 'GRACE_PERIOD', 'EXPIRED', 'NONE'
  )),
  created_at timestamptz not null default timezone('utc', now())
);

create unique index if not exists commercial_offer_snapshots_person_org_tier_uidx
  on public.commercial_offer_snapshots (
    person_id,
    coalesce(organization_id, '00000000-0000-0000-0000-000000000000'::uuid),
    commercial_tier
  );

alter table public.commercial_offer_snapshots enable row level security;

drop policy if exists commercial_offer_snapshots_select_own on public.commercial_offer_snapshots;
create policy commercial_offer_snapshots_select_own
  on public.commercial_offer_snapshots
  for select
  to authenticated
  using (person_id = auth.uid());

create or replace function public.canon_activate_commercial_trial(
  p_tier text,
  p_organization_id uuid default null,
  p_offer_code text default 'LAUNCH_90_NO_CARD',
  p_trial_days integer default 90,
  p_payment_required boolean default false
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_ends timestamptz;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_tier not in ('LEOVER_COMMERCIAL_PROFESSIONAL', 'LEOVER_COMMERCIAL_ORGANIZATION') then
    raise exception 'COMMERCIAL_TIER_INVALID';
  end if;
  v_ends := timezone('utc', now()) + make_interval(days => p_trial_days);

  insert into public.commercial_offer_snapshots (
    person_id, organization_id, offer_code, commercial_tier,
    trial_ends_at, trial_days_snapshot, payment_required_at_activation_snapshot, status
  )
  select
    auth.uid(), p_organization_id, p_offer_code, p_tier,
    v_ends, p_trial_days, coalesce(p_payment_required, false), 'TRIAL_ACTIVE'
  where not exists (
    select 1 from public.commercial_offer_snapshots s
    where s.person_id = auth.uid()
      and s.commercial_tier = p_tier
      and s.organization_id is not distinct from p_organization_id
  )
  returning id into v_id;

  if v_id is null then
    select id into v_id
    from public.commercial_offer_snapshots
    where person_id = auth.uid()
      and commercial_tier = p_tier
      and organization_id is not distinct from p_organization_id
    limit 1;
  end if;

  return v_id;
end;
$$;

do $$
declare r record;
begin
  for r in
    select p.proname, pg_get_function_identity_arguments(p.oid) as args
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'canon_create_organization',
        'canon_activate_commercial_trial'
      )
  loop
    execute format('revoke all on function public.%I(%s) from public, anon', r.proname, r.args);
    execute format('grant execute on function public.%I(%s) to authenticated', r.proname, r.args);
  end loop;
end$$;
