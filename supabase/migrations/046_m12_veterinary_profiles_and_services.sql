-- =============================================================================
-- LeoVer M12 — migración 046: perfiles de clínicas veterinarias y servicios
-- Bloque 2. Forward-only sobre 001–045. NO modifica 040–045 ni service_profiles.
-- Sin agenda, turnos, historia clínica, recetas, vacunación clínica ni pagos.
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 veterinary.* (deny-by-default vía has_org_permission)
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('veterinary.profile.read', 'Ver perfiles de clínicas veterinarias'),
  ('veterinary.profile.manage', 'Gestionar perfiles de clínicas veterinarias'),
  ('veterinary.professional.read', 'Ver profesionales veterinarios'),
  ('veterinary.professional.manage', 'Gestionar profesionales veterinarios'),
  ('veterinary.service.read', 'Ver servicios y horarios veterinarios'),
  ('veterinary.service.manage', 'Gestionar servicios y horarios veterinarios')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code like 'veterinary.%'
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code in (
    'veterinary.profile.read',
    'veterinary.professional.read',
    'veterinary.service.read'
  )
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.veterinary_clinic_profiles (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete restrict,
  branch_id uuid null references public.organization_branches (id) on delete set null,
  display_name text not null,
  description text,
  status text not null default 'DRAFT',
  verification_status text not null default 'UNVERIFIED',
  public_zone_text text not null,
  public_address_text text,
  public_contact_enabled boolean not null default false,
  public_phone text,
  public_email text,
  website_url text,
  social_links jsonb not null default '{}'::jsonb,
  logo_asset_ref text,
  cover_asset_ref text,
  offers_emergency_care boolean not null default false,
  is_open_24_hours boolean not null default false,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  archived_at timestamptz,
  constraint veterinary_clinic_status_chk
    check (status = any (array['DRAFT','ACTIVE','PAUSED','SUSPENDED','ARCHIVED']::text[])),
  constraint veterinary_clinic_verification_chk
    check (verification_status = any (array['UNVERIFIED','PENDING','VERIFIED','REJECTED','SUSPENDED']::text[])),
  constraint veterinary_clinic_display_name_chk check (char_length(trim(display_name)) > 0),
  constraint veterinary_clinic_zone_chk check (char_length(trim(public_zone_text)) > 0),
  constraint veterinary_clinic_logo_ref_chk
    check (logo_asset_ref is null or logo_asset_ref ~* '^(m05://|file_asset:)'),
  constraint veterinary_clinic_cover_ref_chk
    check (cover_asset_ref is null or cover_asset_ref ~* '^(m05://|file_asset:)'),
  constraint veterinary_clinic_website_chk
    check (website_url is null or website_url ~* '^https://'),
  constraint veterinary_clinic_social_links_chk
    check (jsonb_typeof(social_links) = 'object')
);

-- Un único perfil ACTIVE por organización + sede (UUID centinela para branch nulo, patrón 042).
create unique index if not exists veterinary_clinic_profiles_one_active_per_sede
  on public.veterinary_clinic_profiles (
    organization_id,
    coalesce(branch_id, '00000000-0000-0000-0000-000000000000'::uuid)
  )
  where status = 'ACTIVE';

create index if not exists veterinary_clinic_profiles_org_idx
  on public.veterinary_clinic_profiles (organization_id);
create index if not exists veterinary_clinic_profiles_branch_idx
  on public.veterinary_clinic_profiles (branch_id);
create index if not exists veterinary_clinic_profiles_status_idx
  on public.veterinary_clinic_profiles (status);
create index if not exists veterinary_clinic_profiles_verification_idx
  on public.veterinary_clinic_profiles (verification_status);
create index if not exists veterinary_clinic_profiles_zone_idx
  on public.veterinary_clinic_profiles (public_zone_text);

create table if not exists public.veterinary_professionals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid null references public.users (id) on delete set null,
  display_name text not null,
  license_number text,
  license_jurisdiction text,
  verification_status text not null default 'UNVERIFIED',
  biography text,
  public_contact_enabled boolean not null default false,
  avatar_asset_ref text,
  status text not null default 'ACTIVE',
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  archived_at timestamptz,
  constraint veterinary_professional_status_chk
    check (status = any (array['ACTIVE','INACTIVE','SUSPENDED','ARCHIVED']::text[])),
  constraint veterinary_professional_verification_chk
    check (verification_status = any (array['UNVERIFIED','PENDING','VERIFIED','REJECTED','SUSPENDED']::text[])),
  constraint veterinary_professional_display_name_chk check (char_length(trim(display_name)) > 0),
  constraint veterinary_professional_avatar_ref_chk
    check (avatar_asset_ref is null or avatar_asset_ref ~* '^(m05://|file_asset:)')
);

create index if not exists veterinary_professionals_user_idx
  on public.veterinary_professionals (user_id);
create index if not exists veterinary_professionals_status_idx
  on public.veterinary_professionals (status);
create index if not exists veterinary_professionals_verification_idx
  on public.veterinary_professionals (verification_status);

-- Unicidad parcial y normalizada de matrícula por jurisdicción (solo con datos completos).
create unique index if not exists veterinary_professionals_license_uniq
  on public.veterinary_professionals (
    lower(license_jurisdiction),
    lower(license_number)
  )
  where license_number is not null
    and license_jurisdiction is not null
    and archived_at is null;

create table if not exists public.veterinary_clinic_professionals (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  professional_id uuid not null references public.veterinary_professionals (id) on delete restrict,
  role_title text,
  active boolean not null default true,
  linked_by uuid not null references public.users (id),
  linked_at timestamptz not null default timezone('utc', now()),
  unlinked_at timestamptz
);

create unique index if not exists veterinary_clinic_professionals_active_uniq
  on public.veterinary_clinic_professionals (clinic_id, professional_id)
  where active;

create index if not exists veterinary_clinic_professionals_clinic_idx
  on public.veterinary_clinic_professionals (clinic_id);
create index if not exists veterinary_clinic_professionals_professional_idx
  on public.veterinary_clinic_professionals (professional_id);

create table if not exists public.veterinary_professional_specialties (
  id uuid primary key default gen_random_uuid(),
  professional_id uuid not null references public.veterinary_professionals (id) on delete cascade,
  specialty text not null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_specialty_chk
    check (specialty = any (array[
      'GENERAL_MEDICINE','EMERGENCY_AND_CRITICAL_CARE','SURGERY','INTERNAL_MEDICINE',
      'DERMATOLOGY','CARDIOLOGY','NEUROLOGY','ONCOLOGY','OPHTHALMOLOGY','TRAUMATOLOGY',
      'REPRODUCTION','EXOTIC_ANIMALS','DENTISTRY','DIAGNOSTIC_IMAGING','LABORATORY','OTHER'
    ]::text[]))
);

create unique index if not exists veterinary_professional_specialties_uniq
  on public.veterinary_professional_specialties (professional_id, specialty);

create index if not exists veterinary_professional_specialties_professional_idx
  on public.veterinary_professional_specialties (professional_id);
create index if not exists veterinary_professional_specialties_specialty_idx
  on public.veterinary_professional_specialties (specialty);

create table if not exists public.veterinary_services (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  name text not null,
  category text not null,
  description text,
  species text[] not null default '{}'::text[],
  active boolean not null default true,
  requires_appointment boolean not null default true,
  emergency_available boolean not null default false,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  archived_at timestamptz,
  constraint veterinary_service_name_chk check (char_length(trim(name)) > 0),
  constraint veterinary_service_category_chk
    check (category = any (array[
      'CONSULTATION','VACCINATION','SURGERY','HOSPITALIZATION','LABORATORY',
      'DIAGNOSTIC_IMAGING','EMERGENCY_GUARD','PREVENTIVE_CARE','DENTISTRY','PHARMACY','OTHER'
    ]::text[])),
  constraint veterinary_service_species_chk
    check (species <@ array[
      'DOG','CAT','BIRD','RABBIT','RODENT','REPTILE','HORSE','EXOTIC','OTHER'
    ]::text[])
);

create unique index if not exists veterinary_services_active_uniq
  on public.veterinary_services (clinic_id, lower(name), category)
  where active and archived_at is null;

create index if not exists veterinary_services_clinic_idx
  on public.veterinary_services (clinic_id);
create index if not exists veterinary_services_category_idx
  on public.veterinary_services (category);
create index if not exists veterinary_services_active_idx
  on public.veterinary_services (active);

create table if not exists public.veterinary_opening_hours (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  day_of_week integer not null,
  closed boolean not null default false,
  opens_at time,
  closes_at time,
  emergency_only boolean not null default false,
  updated_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_hours_day_chk check (day_of_week between 1 and 7),
  -- ISO day_of_week (1=lunes ... 7=domingo). 24 h se representa opens_at 00:00 / closes_at 23:59.
  constraint veterinary_hours_window_chk check (
    (closed and opens_at is null and closes_at is null)
    or (not closed and opens_at is not null and closes_at is not null and closes_at > opens_at)
  )
);

create unique index if not exists veterinary_opening_hours_day_uniq
  on public.veterinary_opening_hours (clinic_id, day_of_week);

create index if not exists veterinary_opening_hours_clinic_idx
  on public.veterinary_opening_hours (clinic_id);

-- ---------------------------------------------------------------------------
-- 2. Catálogo M07
-- ---------------------------------------------------------------------------
insert into public.observability_event_catalog (
  event_key, module, category, default_severity, sensitivity, organization_scoped,
  retention_policy_key, remote_persistence_allowed, analytics_allowed,
  allowed_metadata_keys, required_metadata_keys
) values
('VETERINARY_CLINIC_CREATED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_CLINIC_UPDATED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_CLINIC_STATUS_CHANGED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_CLINIC_VERIFICATION_CHANGED','M12','AUDIT','NOTICE','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_PROFESSIONAL_CREATED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_PROFESSIONAL_LINKED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_PROFESSIONAL_UNLINKED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_SERVICE_CHANGED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_OPENING_HOURS_REPLACED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[])
on conflict (event_key) do nothing;

-- ---------------------------------------------------------------------------
-- 3. Helpers (SECURITY DEFINER, search_path=public)
-- ---------------------------------------------------------------------------
create or replace function public._m12_require_authenticated()
returns uuid
language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v;
end;
$$;

create or replace function public._m12_is_safe_media_ref(p_ref text)
returns boolean
language sql immutable parallel safe as $$
  select p_ref is null
    or (
      p_ref ~* '^(m05://|file_asset:)'
      and p_ref !~* '^https?://'
      and p_ref !~* 'object/public/leover'
    );
$$;

create or replace function public._m12_require_safe_media(p_ref text)
returns void
language plpgsql immutable as $$
begin
  if not public._m12_is_safe_media_ref(p_ref) then
    raise exception 'VETERINARY_MEDIA_REF_INVALID';
  end if;
end;
$$;

create or replace function public._m12_require_org_perm(p_org_id uuid, p_perm text)
returns void
language plpgsql stable security definer set search_path = public as $$
begin
  if p_org_id is null then raise exception 'ORGANIZATION_NOT_ELIGIBLE'; end if;
  if not exists (
    select 1 from public.organizations o where o.id = p_org_id and o.status = 'ACTIVE'
  ) then
    raise exception 'ORGANIZATION_NOT_ELIGIBLE';
  end if;
  if not public.has_org_permission(p_org_id, p_perm) then
    raise exception 'VETERINARY_CLINIC_FORBIDDEN';
  end if;
end;
$$;

create or replace function public._m12_require_valid_branch(p_org_id uuid, p_branch_id uuid)
returns void
language plpgsql stable security definer set search_path = public as $$
begin
  if p_branch_id is null then return; end if;
  if not exists (
    select 1 from public.organization_branches b
    where b.id = p_branch_id and b.organization_id = p_org_id
  ) then
    raise exception 'VETERINARY_BRANCH_INVALID';
  end if;
end;
$$;

create or replace function public._m12_require_clinic_manage(p_clinic_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_row public.veterinary_clinic_profiles;
begin
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_row.organization_id, 'veterinary.profile.manage');
  return v_row;
end;
$$;

-- Autoridad de profesionales: gestión veterinaria en alguna org con membresía activa.
create or replace function public._m12_require_professional_manage()
returns void
language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m12_require_authenticated();
begin
  if not exists (
    select 1
    from public.organization_memberships m
    where m.user_id = v_actor
      and m.status = 'ACTIVE'
      and public.has_org_permission(m.organization_id, 'veterinary.professional.manage')
  ) then
    raise exception 'VETERINARY_CLINIC_FORBIDDEN';
  end if;
end;
$$;

create or replace function public._m12_require_m04_review()
returns void
language plpgsql stable security definer set search_path = public as $$
begin
  begin
    perform public.m04_require_permission('organizations.review_verification');
  exception when others then
    raise exception 'VETERINARY_VERIFICATION_FORBIDDEN';
  end;
end;
$$;

create or replace function public._m12_validate_hours_row(
  p_day_of_week integer,
  p_closed boolean,
  p_opens_at time,
  p_closes_at time
) returns void
language plpgsql immutable as $$
begin
  if p_day_of_week is null or p_day_of_week < 1 or p_day_of_week > 7 then
    raise exception 'VETERINARY_OPENING_HOURS_INVALID';
  end if;
  if coalesce(p_closed, false) then
    if p_opens_at is not null or p_closes_at is not null then
      raise exception 'VETERINARY_OPENING_HOURS_INVALID';
    end if;
  else
    if p_opens_at is null or p_closes_at is null then
      raise exception 'VETERINARY_OPENING_HOURS_INCOMPLETE';
    end if;
    if p_closes_at <= p_opens_at then
      raise exception 'VETERINARY_OPENING_HOURS_INVALID';
    end if;
  end if;
end;
$$;

-- Requisitos de activación: zona pública, (24h o algún día abierto) y al menos un servicio activo.
create or replace function public._m12_assert_activation_requirements(p_clinic_id uuid)
returns void
language plpgsql stable security definer set search_path = public as $$
declare v_row public.veterinary_clinic_profiles;
begin
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if char_length(trim(coalesce(v_row.public_zone_text, ''))) = 0 then
    raise exception 'VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS';
  end if;
  if not coalesce(v_row.is_open_24_hours, false) then
    if not exists (
      select 1 from public.veterinary_opening_hours h
      where h.clinic_id = p_clinic_id and h.closed = false
    ) then
      raise exception 'VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS';
    end if;
  end if;
  if not exists (
    select 1 from public.veterinary_services s
    where s.clinic_id = p_clinic_id and s.active = true and s.archived_at is null
  ) then
    raise exception 'VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RLS + grants (mutaciones cliente solo vía RPC SECURITY DEFINER)
-- ---------------------------------------------------------------------------
alter table public.veterinary_clinic_profiles enable row level security;
alter table public.veterinary_professionals enable row level security;
alter table public.veterinary_clinic_professionals enable row level security;
alter table public.veterinary_professional_specialties enable row level security;
alter table public.veterinary_services enable row level security;
alter table public.veterinary_opening_hours enable row level security;

drop policy if exists veterinary_clinic_profiles_select_m12 on public.veterinary_clinic_profiles;
drop policy if exists veterinary_clinic_profiles_ins_m12 on public.veterinary_clinic_profiles;
drop policy if exists veterinary_clinic_profiles_upd_m12 on public.veterinary_clinic_profiles;
drop policy if exists veterinary_clinic_profiles_del_m12 on public.veterinary_clinic_profiles;
create policy veterinary_clinic_profiles_select_m12 on public.veterinary_clinic_profiles for select to authenticated
  using (
    status = 'ACTIVE'
    or public.has_org_permission(organization_id, 'veterinary.profile.read')
  );
create policy veterinary_clinic_profiles_ins_m12 on public.veterinary_clinic_profiles for insert to authenticated with check (false);
create policy veterinary_clinic_profiles_upd_m12 on public.veterinary_clinic_profiles for update to authenticated using (false);
create policy veterinary_clinic_profiles_del_m12 on public.veterinary_clinic_profiles for delete to authenticated using (false);

drop policy if exists veterinary_professionals_select_m12 on public.veterinary_professionals;
drop policy if exists veterinary_professionals_ins_m12 on public.veterinary_professionals;
drop policy if exists veterinary_professionals_upd_m12 on public.veterinary_professionals;
drop policy if exists veterinary_professionals_del_m12 on public.veterinary_professionals;
create policy veterinary_professionals_select_m12 on public.veterinary_professionals for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1
      from public.veterinary_clinic_professionals cp
      join public.veterinary_clinic_profiles c on c.id = cp.clinic_id
      where cp.professional_id = veterinary_professionals.id
        and (
          (cp.active and c.status = 'ACTIVE')
          or public.has_org_permission(c.organization_id, 'veterinary.professional.read')
        )
    )
  );
create policy veterinary_professionals_ins_m12 on public.veterinary_professionals for insert to authenticated with check (false);
create policy veterinary_professionals_upd_m12 on public.veterinary_professionals for update to authenticated using (false);
create policy veterinary_professionals_del_m12 on public.veterinary_professionals for delete to authenticated using (false);

drop policy if exists veterinary_clinic_professionals_select_m12 on public.veterinary_clinic_professionals;
drop policy if exists veterinary_clinic_professionals_ins_m12 on public.veterinary_clinic_professionals;
drop policy if exists veterinary_clinic_professionals_upd_m12 on public.veterinary_clinic_professionals;
drop policy if exists veterinary_clinic_professionals_del_m12 on public.veterinary_clinic_professionals;
create policy veterinary_clinic_professionals_select_m12 on public.veterinary_clinic_professionals for select to authenticated
  using (
    exists (
      select 1 from public.veterinary_clinic_profiles c
      where c.id = clinic_id
        and (
          (active and c.status = 'ACTIVE')
          or public.has_org_permission(c.organization_id, 'veterinary.professional.read')
        )
    )
  );
create policy veterinary_clinic_professionals_ins_m12 on public.veterinary_clinic_professionals for insert to authenticated with check (false);
create policy veterinary_clinic_professionals_upd_m12 on public.veterinary_clinic_professionals for update to authenticated using (false);
create policy veterinary_clinic_professionals_del_m12 on public.veterinary_clinic_professionals for delete to authenticated using (false);

drop policy if exists veterinary_professional_specialties_select_m12 on public.veterinary_professional_specialties;
drop policy if exists veterinary_professional_specialties_ins_m12 on public.veterinary_professional_specialties;
drop policy if exists veterinary_professional_specialties_upd_m12 on public.veterinary_professional_specialties;
drop policy if exists veterinary_professional_specialties_del_m12 on public.veterinary_professional_specialties;
create policy veterinary_professional_specialties_select_m12 on public.veterinary_professional_specialties for select to authenticated
  using (
    exists (
      select 1
      from public.veterinary_clinic_professionals cp
      join public.veterinary_clinic_profiles c on c.id = cp.clinic_id
      where cp.professional_id = veterinary_professional_specialties.professional_id
        and (
          (cp.active and c.status = 'ACTIVE')
          or public.has_org_permission(c.organization_id, 'veterinary.professional.read')
        )
    )
  );
create policy veterinary_professional_specialties_ins_m12 on public.veterinary_professional_specialties for insert to authenticated with check (false);
create policy veterinary_professional_specialties_upd_m12 on public.veterinary_professional_specialties for update to authenticated using (false);
create policy veterinary_professional_specialties_del_m12 on public.veterinary_professional_specialties for delete to authenticated using (false);

drop policy if exists veterinary_services_select_m12 on public.veterinary_services;
drop policy if exists veterinary_services_ins_m12 on public.veterinary_services;
drop policy if exists veterinary_services_upd_m12 on public.veterinary_services;
drop policy if exists veterinary_services_del_m12 on public.veterinary_services;
create policy veterinary_services_select_m12 on public.veterinary_services for select to authenticated
  using (
    exists (
      select 1 from public.veterinary_clinic_profiles c
      where c.id = clinic_id
        and (
          (c.status = 'ACTIVE' and active and archived_at is null)
          or public.has_org_permission(c.organization_id, 'veterinary.service.read')
        )
    )
  );
create policy veterinary_services_ins_m12 on public.veterinary_services for insert to authenticated with check (false);
create policy veterinary_services_upd_m12 on public.veterinary_services for update to authenticated using (false);
create policy veterinary_services_del_m12 on public.veterinary_services for delete to authenticated using (false);

drop policy if exists veterinary_opening_hours_select_m12 on public.veterinary_opening_hours;
drop policy if exists veterinary_opening_hours_ins_m12 on public.veterinary_opening_hours;
drop policy if exists veterinary_opening_hours_upd_m12 on public.veterinary_opening_hours;
drop policy if exists veterinary_opening_hours_del_m12 on public.veterinary_opening_hours;
create policy veterinary_opening_hours_select_m12 on public.veterinary_opening_hours for select to authenticated
  using (
    exists (
      select 1 from public.veterinary_clinic_profiles c
      where c.id = clinic_id
        and (
          c.status = 'ACTIVE'
          or public.has_org_permission(c.organization_id, 'veterinary.service.read')
        )
    )
  );
create policy veterinary_opening_hours_ins_m12 on public.veterinary_opening_hours for insert to authenticated with check (false);
create policy veterinary_opening_hours_upd_m12 on public.veterinary_opening_hours for update to authenticated using (false);
create policy veterinary_opening_hours_del_m12 on public.veterinary_opening_hours for delete to authenticated using (false);

-- Hardening estilo 044: sin DML/SELECT directo para public/anon/authenticated (solo RPC).
revoke all privileges on table public.veterinary_clinic_profiles from public;
revoke all privileges on table public.veterinary_clinic_profiles from anon;
revoke all privileges on table public.veterinary_clinic_profiles from authenticated;
revoke all privileges on table public.veterinary_professionals from public;
revoke all privileges on table public.veterinary_professionals from anon;
revoke all privileges on table public.veterinary_professionals from authenticated;
revoke all privileges on table public.veterinary_clinic_professionals from public;
revoke all privileges on table public.veterinary_clinic_professionals from anon;
revoke all privileges on table public.veterinary_clinic_professionals from authenticated;
revoke all privileges on table public.veterinary_professional_specialties from public;
revoke all privileges on table public.veterinary_professional_specialties from anon;
revoke all privileges on table public.veterinary_professional_specialties from authenticated;
revoke all privileges on table public.veterinary_services from public;
revoke all privileges on table public.veterinary_services from anon;
revoke all privileges on table public.veterinary_services from authenticated;
revoke all privileges on table public.veterinary_opening_hours from public;
revoke all privileges on table public.veterinary_opening_hours from anon;
revoke all privileges on table public.veterinary_opening_hours from authenticated;

grant all on table public.veterinary_clinic_profiles to service_role;
grant all on table public.veterinary_professionals to service_role;
grant all on table public.veterinary_clinic_professionals to service_role;
grant all on table public.veterinary_professional_specialties to service_role;
grant all on table public.veterinary_services to service_role;
grant all on table public.veterinary_opening_hours to service_role;

-- ---------------------------------------------------------------------------
-- 5. RPCs — clínicas
-- M06: hooks de dominio preparados; NO invocar push real aquí.
-- ---------------------------------------------------------------------------
create or replace function public.m12_create_veterinary_clinic_draft(
  p_organization_id uuid,
  p_display_name text,
  p_public_zone_text text,
  p_branch_id uuid default null,
  p_description text default null,
  p_public_address_text text default null,
  p_public_contact_enabled boolean default false,
  p_public_phone text default null,
  p_public_email text default null,
  p_website_url text default null,
  p_social_links jsonb default '{}'::jsonb,
  p_logo_asset_ref text default null,
  p_cover_asset_ref text default null,
  p_offers_emergency_care boolean default false,
  p_is_open_24_hours boolean default false
) returns public.veterinary_clinic_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_clinic_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  if p_organization_id is null then raise exception 'VETERINARY_ORGANIZATION_REQUIRED'; end if;
  perform public._m12_require_org_perm(p_organization_id, 'veterinary.profile.manage');
  perform public._m12_require_valid_branch(p_organization_id, p_branch_id);
  if char_length(trim(coalesce(p_display_name, ''))) = 0
     or char_length(trim(coalesce(p_public_zone_text, ''))) = 0 then
    raise exception 'VETERINARY_CLINIC_INVALID';
  end if;
  perform public._m12_require_safe_media(p_logo_asset_ref);
  perform public._m12_require_safe_media(p_cover_asset_ref);
  if p_website_url is not null and trim(p_website_url) <> '' and p_website_url !~* '^https://' then
    raise exception 'VETERINARY_CLINIC_INVALID';
  end if;
  if p_social_links is not null and jsonb_typeof(p_social_links) <> 'object' then
    raise exception 'VETERINARY_CLINIC_INVALID';
  end if;

  insert into public.veterinary_clinic_profiles (
    organization_id, branch_id, display_name, description, status, verification_status,
    public_zone_text, public_address_text, public_contact_enabled, public_phone, public_email,
    website_url, social_links, logo_asset_ref, cover_asset_ref,
    offers_emergency_care, is_open_24_hours, created_by
  ) values (
    p_organization_id, p_branch_id, trim(p_display_name),
    nullif(trim(coalesce(p_description, '')), ''), 'DRAFT', 'UNVERIFIED',
    trim(p_public_zone_text), nullif(trim(coalesce(p_public_address_text, '')), ''),
    coalesce(p_public_contact_enabled, false),
    nullif(trim(coalesce(p_public_phone, '')), ''), nullif(trim(coalesce(p_public_email, '')), ''),
    nullif(trim(coalesce(p_website_url, '')), ''), coalesce(p_social_links, '{}'::jsonb),
    nullif(trim(coalesce(p_logo_asset_ref, '')), ''), nullif(trim(coalesce(p_cover_asset_ref, '')), ''),
    coalesce(p_offers_emergency_care, false), coalesce(p_is_open_24_hours, false), v_actor
  ) returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_CLINIC_CREATED', 'CREATE', 'SUCCESS', v_corr,
    'veterinary_clinic', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_row.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_update_veterinary_clinic_profile(
  p_clinic_id uuid,
  p_display_name text,
  p_public_zone_text text,
  p_description text default null,
  p_public_address_text text default null,
  p_public_contact_enabled boolean default false,
  p_public_phone text default null,
  p_public_email text default null,
  p_website_url text default null,
  p_social_links jsonb default '{}'::jsonb,
  p_logo_asset_ref text default null,
  p_cover_asset_ref text default null,
  p_offers_emergency_care boolean default false,
  p_is_open_24_hours boolean default false
) returns public.veterinary_clinic_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_clinic_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  v_row := public._m12_require_clinic_manage(p_clinic_id);
  if v_row.status = 'ARCHIVED' then raise exception 'VETERINARY_CLINIC_INVALID_TRANSITION'; end if;
  if char_length(trim(coalesce(p_display_name, ''))) = 0
     or char_length(trim(coalesce(p_public_zone_text, ''))) = 0 then
    raise exception 'VETERINARY_CLINIC_INVALID';
  end if;
  perform public._m12_require_safe_media(p_logo_asset_ref);
  perform public._m12_require_safe_media(p_cover_asset_ref);
  if p_website_url is not null and trim(p_website_url) <> '' and p_website_url !~* '^https://' then
    raise exception 'VETERINARY_CLINIC_INVALID';
  end if;
  if p_social_links is not null and jsonb_typeof(p_social_links) <> 'object' then
    raise exception 'VETERINARY_CLINIC_INVALID';
  end if;

  update public.veterinary_clinic_profiles set
    display_name = trim(p_display_name),
    description = nullif(trim(coalesce(p_description, '')), ''),
    public_zone_text = trim(p_public_zone_text),
    public_address_text = nullif(trim(coalesce(p_public_address_text, '')), ''),
    public_contact_enabled = coalesce(p_public_contact_enabled, false),
    public_phone = nullif(trim(coalesce(p_public_phone, '')), ''),
    public_email = nullif(trim(coalesce(p_public_email, '')), ''),
    website_url = nullif(trim(coalesce(p_website_url, '')), ''),
    social_links = coalesce(p_social_links, '{}'::jsonb),
    logo_asset_ref = nullif(trim(coalesce(p_logo_asset_ref, '')), ''),
    cover_asset_ref = nullif(trim(coalesce(p_cover_asset_ref, '')), ''),
    offers_emergency_care = coalesce(p_offers_emergency_care, false),
    is_open_24_hours = coalesce(p_is_open_24_hours, false),
    updated_at = timezone('utc', now())
  where id = p_clinic_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_CLINIC_UPDATED', 'UPDATE', 'SUCCESS', v_corr,
    'veterinary_clinic', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_row.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_change_veterinary_clinic_status(
  p_clinic_id uuid,
  p_status text
) returns public.veterinary_clinic_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_clinic_profiles;
  v_status text := upper(trim(coalesce(p_status, '')));
  v_needs_admin boolean;
  v_corr text := public.m07_new_correlation_id();
begin
  v_row := public._m12_require_clinic_manage(p_clinic_id);
  if v_status not in ('DRAFT','ACTIVE','PAUSED','SUSPENDED','ARCHIVED') then
    raise exception 'VETERINARY_CLINIC_INVALID_TRANSITION';
  end if;
  if v_row.status = v_status then return v_row; end if;

  -- Transiciones permitidas (Bloque 2).
  if not (
    (v_row.status = 'DRAFT' and v_status in ('ACTIVE','ARCHIVED'))
    or (v_row.status = 'ACTIVE' and v_status in ('PAUSED','SUSPENDED','ARCHIVED'))
    or (v_row.status = 'PAUSED' and v_status in ('ACTIVE','ARCHIVED'))
    or (v_row.status = 'SUSPENDED' and v_status in ('ACTIVE','ARCHIVED'))
  ) then
    raise exception 'VETERINARY_CLINIC_INVALID_TRANSITION';
  end if;

  -- Suspender o reactivar desde suspensión requiere autoridad M04.
  v_needs_admin := (v_status = 'SUSPENDED') or (v_row.status = 'SUSPENDED' and v_status = 'ACTIVE');
  if v_needs_admin then
    perform public._m12_require_m04_review();
  end if;

  if v_status = 'ACTIVE' then
    if not exists (
      select 1 from public.organizations o where o.id = v_row.organization_id and o.status = 'ACTIVE'
    ) then
      raise exception 'ORGANIZATION_NOT_ELIGIBLE';
    end if;
    perform public._m12_assert_activation_requirements(p_clinic_id);
    if exists (
      select 1 from public.veterinary_clinic_profiles c
      where c.id <> p_clinic_id
        and c.organization_id = v_row.organization_id
        and coalesce(c.branch_id, '00000000-0000-0000-0000-000000000000'::uuid)
            = coalesce(v_row.branch_id, '00000000-0000-0000-0000-000000000000'::uuid)
        and c.status = 'ACTIVE'
    ) then
      raise exception 'VETERINARY_CLINIC_ALREADY_EXISTS';
    end if;
  end if;

  update public.veterinary_clinic_profiles set
    status = v_status,
    archived_at = case when v_status = 'ARCHIVED' then timezone('utc', now()) else archived_at end,
    updated_at = timezone('utc', now())
  where id = p_clinic_id returning * into v_row;

  null; -- M06 hook: VETERINARY_CLINIC_STATUS_CHANGED (client/domain)

  perform public.m07_best_effort_audit(
    'VETERINARY_CLINIC_STATUS_CHANGED', 'STATUS_CHANGE', 'SUCCESS', v_corr,
    'veterinary_clinic', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_row.organization_id,
      'new_status', v_status)
  );
  return v_row;
end;
$$;

create or replace function public.m12_request_veterinary_clinic_verification(p_clinic_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_clinic_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  v_row := public._m12_require_clinic_manage(p_clinic_id);
  if v_row.verification_status not in ('UNVERIFIED','REJECTED') then
    raise exception 'VETERINARY_VERIFICATION_INVALID_TRANSITION';
  end if;

  update public.veterinary_clinic_profiles set
    verification_status = 'PENDING',
    updated_at = timezone('utc', now())
  where id = p_clinic_id returning * into v_row;

  null; -- M06 hook: VETERINARY_CLINIC_VERIFICATION_REQUESTED (client/domain)

  perform public.m07_best_effort_audit(
    'VETERINARY_CLINIC_VERIFICATION_CHANGED', 'VERIFICATION_REQUEST', 'SUCCESS', v_corr,
    'veterinary_clinic', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_row.organization_id,
      'verification_status', 'PENDING')
  );
  return v_row;
end;
$$;

create or replace function public.m12_review_veterinary_clinic_verification(
  p_clinic_id uuid,
  p_decision text
) returns public.veterinary_clinic_profiles
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_clinic_profiles;
  v_decision text := upper(trim(coalesce(p_decision, '')));
  v_corr text := public.m07_new_correlation_id();
begin
  perform public._m12_require_m04_review();
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id for update;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if v_decision not in ('VERIFIED','REJECTED','SUSPENDED','PENDING') then
    raise exception 'VETERINARY_VERIFICATION_INVALID_TRANSITION';
  end if;

  if not (
    (v_row.verification_status = 'PENDING' and v_decision in ('VERIFIED','REJECTED'))
    or (v_row.verification_status = 'VERIFIED' and v_decision = 'SUSPENDED')
    or (v_row.verification_status = 'REJECTED' and v_decision = 'PENDING')
    or (v_row.verification_status = 'SUSPENDED' and v_decision in ('PENDING','VERIFIED'))
  ) then
    raise exception 'VETERINARY_VERIFICATION_INVALID_TRANSITION';
  end if;

  update public.veterinary_clinic_profiles set
    verification_status = v_decision,
    updated_at = timezone('utc', now())
  where id = p_clinic_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_CLINIC_VERIFICATION_CHANGED', 'VERIFICATION_REVIEW', 'SUCCESS', v_corr,
    'veterinary_clinic', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_row.organization_id,
      'verification_status', v_decision)
  );
  return v_row;
end;
$$;

create or replace function public.m12_get_public_veterinary_clinic(p_clinic_id uuid)
returns setof public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare
  v_row public.veterinary_clinic_profiles;
  v_can_read boolean;
begin
  perform public._m12_require_authenticated();
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  v_can_read := public.has_org_permission(v_row.organization_id, 'veterinary.profile.read');
  if v_row.status <> 'ACTIVE' and not v_can_read then
    raise exception 'VETERINARY_CLINIC_NOT_FOUND';
  end if;
  if not coalesce(v_row.public_contact_enabled, false) then
    v_row.public_phone := null;
    v_row.public_email := null;
  end if;
  return next v_row;
end;
$$;

create or replace function public.m12_get_managed_veterinary_clinic(p_clinic_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_row public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if not public.has_org_permission(v_row.organization_id, 'veterinary.profile.read') then
    raise exception 'VETERINARY_CLINIC_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m12_list_public_veterinary_clinics(
  p_query text default null,
  p_zone text default null,
  p_specialty text default null,
  p_service_category text default null,
  p_emergency_only boolean default false,
  p_open_24_only boolean default false,
  p_verified_only boolean default false
) returns setof public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare
  v_row public.veterinary_clinic_profiles;
  v_query text := nullif(trim(coalesce(p_query, '')), '');
  v_zone text := nullif(trim(coalesce(p_zone, '')), '');
  v_specialty text := nullif(upper(trim(coalesce(p_specialty, ''))), '');
  v_category text := nullif(upper(trim(coalesce(p_service_category, ''))), '');
begin
  perform public._m12_require_authenticated();
  for v_row in
    select c.*
    from public.veterinary_clinic_profiles c
    where c.status = 'ACTIVE'
      and (v_query is null or c.display_name ilike '%' || v_query || '%')
      and (v_zone is null or c.public_zone_text ilike '%' || v_zone || '%')
      and (not coalesce(p_emergency_only, false) or c.offers_emergency_care = true)
      and (not coalesce(p_open_24_only, false) or c.is_open_24_hours = true)
      and (not coalesce(p_verified_only, false) or c.verification_status = 'VERIFIED')
      and (
        v_specialty is null
        or exists (
          select 1
          from public.veterinary_clinic_professionals cp
          join public.veterinary_professional_specialties ps on ps.professional_id = cp.professional_id
          where cp.clinic_id = c.id and cp.active and ps.specialty = v_specialty
        )
      )
      and (
        v_category is null
        or exists (
          select 1 from public.veterinary_services s
          where s.clinic_id = c.id and s.active and s.archived_at is null and s.category = v_category
        )
      )
    order by c.verification_status = 'VERIFIED' desc, c.display_name asc
  loop
    if not coalesce(v_row.public_contact_enabled, false) then
      v_row.public_phone := null;
      v_row.public_email := null;
    end if;
    return next v_row;
  end loop;
end;
$$;

create or replace function public.m12_list_managed_veterinary_clinics()
returns setof public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m12_require_authenticated();
begin
  return query
  select c.* from public.veterinary_clinic_profiles c
  where public.has_org_permission(c.organization_id, 'veterinary.profile.read')
  order by c.updated_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPCs — profesionales y especialidades
-- ---------------------------------------------------------------------------
create or replace function public.m12_create_veterinary_professional(
  p_display_name text,
  p_user_id uuid default null,
  p_license_number text default null,
  p_license_jurisdiction text default null,
  p_biography text default null,
  p_public_contact_enabled boolean default false,
  p_avatar_asset_ref text default null
) returns public.veterinary_professionals
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_professionals;
  v_corr text := public.m07_new_correlation_id();
begin
  perform public._m12_require_professional_manage();
  if char_length(trim(coalesce(p_display_name, ''))) = 0 then
    raise exception 'VETERINARY_PROFESSIONAL_INVALID';
  end if;
  perform public._m12_require_safe_media(p_avatar_asset_ref);
  if p_user_id is not null and not exists (select 1 from public.users u where u.id = p_user_id) then
    raise exception 'VETERINARY_PROFESSIONAL_INVALID';
  end if;

  insert into public.veterinary_professionals (
    user_id, display_name, license_number, license_jurisdiction, verification_status,
    biography, public_contact_enabled, avatar_asset_ref, status, created_by
  ) values (
    p_user_id, trim(p_display_name),
    nullif(trim(coalesce(p_license_number, '')), ''),
    nullif(trim(coalesce(p_license_jurisdiction, '')), ''),
    'UNVERIFIED', nullif(trim(coalesce(p_biography, '')), ''),
    coalesce(p_public_contact_enabled, false),
    nullif(trim(coalesce(p_avatar_asset_ref, '')), ''), 'ACTIVE', v_actor
  ) returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_PROFESSIONAL_CREATED', 'CREATE', 'SUCCESS', v_corr,
    'veterinary_professional', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12')
  );
  return v_row;
end;
$$;

create or replace function public.m12_update_veterinary_professional(
  p_professional_id uuid,
  p_display_name text,
  p_license_number text default null,
  p_license_jurisdiction text default null,
  p_biography text default null,
  p_public_contact_enabled boolean default false,
  p_avatar_asset_ref text default null,
  p_status text default null
) returns public.veterinary_professionals
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_professionals;
  v_status text := nullif(upper(trim(coalesce(p_status, ''))), '');
begin
  perform public._m12_require_professional_manage();
  select * into v_row from public.veterinary_professionals where id = p_professional_id for update;
  if not found then raise exception 'VETERINARY_PROFESSIONAL_NOT_FOUND'; end if;
  if char_length(trim(coalesce(p_display_name, ''))) = 0 then
    raise exception 'VETERINARY_PROFESSIONAL_INVALID';
  end if;
  perform public._m12_require_safe_media(p_avatar_asset_ref);
  if v_status is not null and v_status not in ('ACTIVE','INACTIVE','SUSPENDED','ARCHIVED') then
    raise exception 'VETERINARY_PROFESSIONAL_INVALID';
  end if;

  update public.veterinary_professionals set
    display_name = trim(p_display_name),
    license_number = nullif(trim(coalesce(p_license_number, '')), ''),
    license_jurisdiction = nullif(trim(coalesce(p_license_jurisdiction, '')), ''),
    biography = nullif(trim(coalesce(p_biography, '')), ''),
    public_contact_enabled = coalesce(p_public_contact_enabled, false),
    avatar_asset_ref = nullif(trim(coalesce(p_avatar_asset_ref, '')), ''),
    status = coalesce(v_status, status),
    archived_at = case when v_status = 'ARCHIVED' then timezone('utc', now()) else archived_at end,
    updated_at = timezone('utc', now())
  where id = p_professional_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m12_link_veterinary_professional(
  p_clinic_id uuid,
  p_professional_id uuid,
  p_role_title text default null
) returns public.veterinary_clinic_professionals
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_clinic public.veterinary_clinic_profiles;
  v_row public.veterinary_clinic_professionals;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.professional.manage');
  if not exists (select 1 from public.veterinary_professionals p where p.id = p_professional_id) then
    raise exception 'VETERINARY_PROFESSIONAL_NOT_FOUND';
  end if;
  if exists (
    select 1 from public.veterinary_clinic_professionals cp
    where cp.clinic_id = p_clinic_id and cp.professional_id = p_professional_id and cp.active
  ) then
    raise exception 'VETERINARY_PROFESSIONAL_ALREADY_LINKED';
  end if;

  insert into public.veterinary_clinic_professionals (
    clinic_id, professional_id, role_title, active, linked_by
  ) values (
    p_clinic_id, p_professional_id, nullif(trim(coalesce(p_role_title, '')), ''), true, v_actor
  ) returning * into v_row;

  null; -- M06 hook: VETERINARY_PROFESSIONAL_LINKED (client/domain)

  perform public.m07_best_effort_audit(
    'VETERINARY_PROFESSIONAL_LINKED', 'LINK', 'SUCCESS', v_corr,
    'veterinary_clinic_professional', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_unlink_veterinary_professional(
  p_clinic_id uuid,
  p_professional_id uuid
) returns public.veterinary_clinic_professionals
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_clinic public.veterinary_clinic_profiles;
  v_row public.veterinary_clinic_professionals;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.professional.manage');
  select * into v_row from public.veterinary_clinic_professionals
  where clinic_id = p_clinic_id and professional_id = p_professional_id and active
  for update;
  if not found then raise exception 'VETERINARY_PROFESSIONAL_NOT_LINKED'; end if;

  update public.veterinary_clinic_professionals set
    active = false,
    unlinked_at = timezone('utc', now())
  where id = v_row.id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_PROFESSIONAL_UNLINKED', 'UNLINK', 'SUCCESS', v_corr,
    'veterinary_clinic_professional', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_replace_veterinary_professional_specialties(
  p_professional_id uuid,
  p_specialties text[]
) returns setof public.veterinary_professional_specialties
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_item text;
  v_norm text;
begin
  perform public._m12_require_professional_manage();
  if not exists (select 1 from public.veterinary_professionals p where p.id = p_professional_id) then
    raise exception 'VETERINARY_PROFESSIONAL_NOT_FOUND';
  end if;

  delete from public.veterinary_professional_specialties where professional_id = p_professional_id;

  if p_specialties is not null then
    foreach v_item in array p_specialties loop
      v_norm := upper(trim(coalesce(v_item, '')));
      if v_norm = '' then continue; end if;
      if v_norm not in (
        'GENERAL_MEDICINE','EMERGENCY_AND_CRITICAL_CARE','SURGERY','INTERNAL_MEDICINE',
        'DERMATOLOGY','CARDIOLOGY','NEUROLOGY','ONCOLOGY','OPHTHALMOLOGY','TRAUMATOLOGY',
        'REPRODUCTION','EXOTIC_ANIMALS','DENTISTRY','DIAGNOSTIC_IMAGING','LABORATORY','OTHER'
      ) then
        raise exception 'VETERINARY_SPECIALTY_INVALID';
      end if;
      insert into public.veterinary_professional_specialties (professional_id, specialty)
      values (p_professional_id, v_norm)
      on conflict (professional_id, specialty) do nothing;
    end loop;
  end if;

  return query
  select * from public.veterinary_professional_specialties
  where professional_id = p_professional_id
  order by specialty;
end;
$$;

create or replace function public.m12_request_veterinary_professional_verification(p_professional_id uuid)
returns public.veterinary_professionals
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_professionals;
  v_corr text := public.m07_new_correlation_id();
begin
  perform public._m12_require_professional_manage();
  select * into v_row from public.veterinary_professionals where id = p_professional_id for update;
  if not found then raise exception 'VETERINARY_PROFESSIONAL_NOT_FOUND'; end if;
  if v_row.verification_status not in ('UNVERIFIED','REJECTED') then
    raise exception 'VETERINARY_VERIFICATION_INVALID_TRANSITION';
  end if;

  update public.veterinary_professionals set
    verification_status = 'PENDING',
    updated_at = timezone('utc', now())
  where id = p_professional_id returning * into v_row;

  null; -- M06 hook: VETERINARY_PROFESSIONAL_VERIFICATION_REQUESTED (client/domain)
  return v_row;
end;
$$;

create or replace function public.m12_review_veterinary_professional_verification(
  p_professional_id uuid,
  p_decision text
) returns public.veterinary_professionals
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_professionals;
  v_decision text := upper(trim(coalesce(p_decision, '')));
begin
  perform public._m12_require_m04_review();
  select * into v_row from public.veterinary_professionals where id = p_professional_id for update;
  if not found then raise exception 'VETERINARY_PROFESSIONAL_NOT_FOUND'; end if;
  if v_decision not in ('VERIFIED','REJECTED','SUSPENDED','PENDING') then
    raise exception 'VETERINARY_VERIFICATION_INVALID_TRANSITION';
  end if;
  if not (
    (v_row.verification_status = 'PENDING' and v_decision in ('VERIFIED','REJECTED'))
    or (v_row.verification_status = 'VERIFIED' and v_decision = 'SUSPENDED')
    or (v_row.verification_status = 'REJECTED' and v_decision = 'PENDING')
    or (v_row.verification_status = 'SUSPENDED' and v_decision in ('PENDING','VERIFIED'))
  ) then
    raise exception 'VETERINARY_VERIFICATION_INVALID_TRANSITION';
  end if;

  update public.veterinary_professionals set
    verification_status = v_decision,
    updated_at = timezone('utc', now())
  where id = p_professional_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m12_list_public_veterinary_professionals(p_clinic_id uuid)
returns setof public.veterinary_professionals
language plpgsql stable security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
  v_row public.veterinary_professionals;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if v_clinic.status <> 'ACTIVE'
     and not public.has_org_permission(v_clinic.organization_id, 'veterinary.professional.read') then
    raise exception 'VETERINARY_CLINIC_NOT_FOUND';
  end if;

  for v_row in
    select p.*
    from public.veterinary_professionals p
    join public.veterinary_clinic_professionals cp on cp.professional_id = p.id
    where cp.clinic_id = p_clinic_id and cp.active and p.status = 'ACTIVE'
    order by p.display_name asc
  loop
    -- Nunca exponer matrícula completa en proyección pública.
    v_row.license_number := null;
    v_row.license_jurisdiction := null;
    return next v_row;
  end loop;
end;
$$;

create or replace function public.m12_list_managed_veterinary_professionals(p_clinic_id uuid)
returns setof public.veterinary_professionals
language plpgsql stable security definer set search_path = public as $$
declare v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if not public.has_org_permission(v_clinic.organization_id, 'veterinary.professional.read') then
    raise exception 'VETERINARY_CLINIC_FORBIDDEN';
  end if;

  return query
  select p.*
  from public.veterinary_professionals p
  join public.veterinary_clinic_professionals cp on cp.professional_id = p.id
  where cp.clinic_id = p_clinic_id and cp.active
  order by p.display_name asc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. RPCs — servicios y horarios
-- ---------------------------------------------------------------------------
create or replace function public.m12_create_veterinary_service(
  p_clinic_id uuid,
  p_name text,
  p_category text,
  p_description text default null,
  p_species text[] default '{}'::text[],
  p_requires_appointment boolean default true,
  p_emergency_available boolean default false,
  p_active boolean default true
) returns public.veterinary_services
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_clinic public.veterinary_clinic_profiles;
  v_row public.veterinary_services;
  v_category text := upper(trim(coalesce(p_category, '')));
  v_species text[];
  v_item text;
  v_norm text;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.service.manage');
  if char_length(trim(coalesce(p_name, ''))) = 0 then raise exception 'VETERINARY_SERVICE_INVALID'; end if;
  if v_category not in (
    'CONSULTATION','VACCINATION','SURGERY','HOSPITALIZATION','LABORATORY',
    'DIAGNOSTIC_IMAGING','EMERGENCY_GUARD','PREVENTIVE_CARE','DENTISTRY','PHARMACY','OTHER'
  ) then
    raise exception 'VETERINARY_SERVICE_INVALID';
  end if;
  if coalesce(p_emergency_available, false) and not coalesce(v_clinic.offers_emergency_care, false) then
    raise exception 'VETERINARY_SERVICE_INVALID';
  end if;

  v_species := '{}'::text[];
  if p_species is not null then
    foreach v_item in array p_species loop
      v_norm := upper(trim(coalesce(v_item, '')));
      if v_norm = '' then continue; end if;
      if v_norm not in ('DOG','CAT','BIRD','RABBIT','RODENT','REPTILE','HORSE','EXOTIC','OTHER') then
        raise exception 'VETERINARY_SERVICE_INVALID';
      end if;
      if not (v_norm = any (v_species)) then
        v_species := array_append(v_species, v_norm);
      end if;
    end loop;
  end if;

  if coalesce(p_active, true) and exists (
    select 1 from public.veterinary_services s
    where s.clinic_id = p_clinic_id
      and lower(s.name) = lower(trim(p_name))
      and s.category = v_category
      and s.active and s.archived_at is null
  ) then
    raise exception 'VETERINARY_SERVICE_DUPLICATE';
  end if;

  insert into public.veterinary_services (
    clinic_id, name, category, description, species, active,
    requires_appointment, emergency_available, created_by
  ) values (
    p_clinic_id, trim(p_name), v_category, nullif(trim(coalesce(p_description, '')), ''),
    v_species, coalesce(p_active, true),
    coalesce(p_requires_appointment, true), coalesce(p_emergency_available, false), v_actor
  ) returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_SERVICE_CHANGED', 'CREATE', 'SUCCESS', v_corr,
    'veterinary_service', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_update_veterinary_service(
  p_service_id uuid,
  p_name text,
  p_category text,
  p_description text default null,
  p_species text[] default '{}'::text[],
  p_requires_appointment boolean default true,
  p_emergency_available boolean default false
) returns public.veterinary_services
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_services;
  v_clinic public.veterinary_clinic_profiles;
  v_category text := upper(trim(coalesce(p_category, '')));
  v_species text[];
  v_item text;
  v_norm text;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.veterinary_services where id = p_service_id for update;
  if not found then raise exception 'VETERINARY_SERVICE_NOT_FOUND'; end if;
  select * into v_clinic from public.veterinary_clinic_profiles where id = v_row.clinic_id;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.service.manage');
  if char_length(trim(coalesce(p_name, ''))) = 0 then raise exception 'VETERINARY_SERVICE_INVALID'; end if;
  if v_category not in (
    'CONSULTATION','VACCINATION','SURGERY','HOSPITALIZATION','LABORATORY',
    'DIAGNOSTIC_IMAGING','EMERGENCY_GUARD','PREVENTIVE_CARE','DENTISTRY','PHARMACY','OTHER'
  ) then
    raise exception 'VETERINARY_SERVICE_INVALID';
  end if;
  if coalesce(p_emergency_available, false) and not coalesce(v_clinic.offers_emergency_care, false) then
    raise exception 'VETERINARY_SERVICE_INVALID';
  end if;

  v_species := '{}'::text[];
  if p_species is not null then
    foreach v_item in array p_species loop
      v_norm := upper(trim(coalesce(v_item, '')));
      if v_norm = '' then continue; end if;
      if v_norm not in ('DOG','CAT','BIRD','RABBIT','RODENT','REPTILE','HORSE','EXOTIC','OTHER') then
        raise exception 'VETERINARY_SERVICE_INVALID';
      end if;
      if not (v_norm = any (v_species)) then
        v_species := array_append(v_species, v_norm);
      end if;
    end loop;
  end if;

  if v_row.active and v_row.archived_at is null and exists (
    select 1 from public.veterinary_services s
    where s.clinic_id = v_row.clinic_id
      and s.id <> v_row.id
      and lower(s.name) = lower(trim(p_name))
      and s.category = v_category
      and s.active and s.archived_at is null
  ) then
    raise exception 'VETERINARY_SERVICE_DUPLICATE';
  end if;

  update public.veterinary_services set
    name = trim(p_name),
    category = v_category,
    description = nullif(trim(coalesce(p_description, '')), ''),
    species = v_species,
    requires_appointment = coalesce(p_requires_appointment, true),
    emergency_available = coalesce(p_emergency_available, false),
    updated_at = timezone('utc', now())
  where id = p_service_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_SERVICE_CHANGED', 'UPDATE', 'SUCCESS', v_corr,
    'veterinary_service', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_change_veterinary_service_status(
  p_service_id uuid,
  p_active boolean,
  p_archive boolean default false
) returns public.veterinary_services
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_services;
  v_clinic public.veterinary_clinic_profiles;
  v_active boolean;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.veterinary_services where id = p_service_id for update;
  if not found then raise exception 'VETERINARY_SERVICE_NOT_FOUND'; end if;
  select * into v_clinic from public.veterinary_clinic_profiles where id = v_row.clinic_id;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.service.manage');

  v_active := case when coalesce(p_archive, false) then false else coalesce(p_active, v_row.active) end;

  if v_active and coalesce(p_archive, false) = false and exists (
    select 1 from public.veterinary_services s
    where s.clinic_id = v_row.clinic_id
      and s.id <> v_row.id
      and lower(s.name) = lower(v_row.name)
      and s.category = v_row.category
      and s.active and s.archived_at is null
  ) then
    raise exception 'VETERINARY_SERVICE_DUPLICATE';
  end if;

  update public.veterinary_services set
    active = v_active,
    archived_at = case when coalesce(p_archive, false) then timezone('utc', now()) else archived_at end,
    updated_at = timezone('utc', now())
  where id = p_service_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_SERVICE_CHANGED', 'STATUS_CHANGE', 'SUCCESS', v_corr,
    'veterinary_service', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id,
      'active', v_active, 'archived', coalesce(p_archive, false))
  );
  return v_row;
end;
$$;

create or replace function public.m12_list_public_veterinary_services(p_clinic_id uuid)
returns setof public.veterinary_services
language plpgsql stable security definer set search_path = public as $$
declare v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if v_clinic.status <> 'ACTIVE'
     and not public.has_org_permission(v_clinic.organization_id, 'veterinary.service.read') then
    raise exception 'VETERINARY_CLINIC_NOT_FOUND';
  end if;

  return query
  select * from public.veterinary_services
  where clinic_id = p_clinic_id and active and archived_at is null
  order by category, name;
end;
$$;

create or replace function public.m12_list_managed_veterinary_services(p_clinic_id uuid)
returns setof public.veterinary_services
language plpgsql stable security definer set search_path = public as $$
declare v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if not public.has_org_permission(v_clinic.organization_id, 'veterinary.service.read') then
    raise exception 'VETERINARY_CLINIC_FORBIDDEN';
  end if;

  return query
  select * from public.veterinary_services
  where clinic_id = p_clinic_id
  order by archived_at nulls first, category, name;
end;
$$;

-- Reemplazo semanal transaccional de horarios.
create or replace function public.m12_replace_veterinary_opening_hours(
  p_clinic_id uuid,
  p_hours jsonb
) returns setof public.veterinary_opening_hours
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_clinic public.veterinary_clinic_profiles;
  v_item jsonb;
  v_dow integer;
  v_closed boolean;
  v_opens time;
  v_closes time;
  v_emerg boolean;
  v_seen integer[] := '{}'::integer[];
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.service.manage');
  if p_hours is not null and jsonb_typeof(p_hours) <> 'array' then
    raise exception 'VETERINARY_OPENING_HOURS_INVALID';
  end if;

  delete from public.veterinary_opening_hours where clinic_id = p_clinic_id;

  if p_hours is not null then
    for v_item in select * from jsonb_array_elements(p_hours) loop
      v_dow := nullif(v_item->>'day_of_week', '')::integer;
      v_closed := coalesce((v_item->>'closed')::boolean, false);
      v_opens := nullif(v_item->>'opens_at', '')::time;
      v_closes := nullif(v_item->>'closes_at', '')::time;
      v_emerg := coalesce((v_item->>'emergency_only')::boolean, false);

      if v_dow is null or v_dow < 1 or v_dow > 7 then
        raise exception 'VETERINARY_OPENING_HOURS_INVALID';
      end if;
      if v_dow = any (v_seen) then
        raise exception 'VETERINARY_OPENING_HOURS_DUPLICATE_DAY';
      end if;
      v_seen := array_append(v_seen, v_dow);

      perform public._m12_validate_hours_row(v_dow, v_closed, v_opens, v_closes);

      insert into public.veterinary_opening_hours (
        clinic_id, day_of_week, closed, opens_at, closes_at, emergency_only, updated_by
      ) values (
        p_clinic_id, v_dow, v_closed, v_opens, v_closes, v_emerg, v_actor
      );
    end loop;
  end if;

  perform public.m07_best_effort_audit(
    'VETERINARY_OPENING_HOURS_REPLACED', 'REPLACE', 'SUCCESS', v_corr,
    'veterinary_clinic', p_clinic_id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );

  return query
  select * from public.veterinary_opening_hours
  where clinic_id = p_clinic_id
  order by day_of_week;
end;
$$;

create or replace function public.m12_list_public_veterinary_opening_hours(p_clinic_id uuid)
returns setof public.veterinary_opening_hours
language plpgsql stable security definer set search_path = public as $$
declare v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if v_clinic.status <> 'ACTIVE'
     and not public.has_org_permission(v_clinic.organization_id, 'veterinary.service.read') then
    raise exception 'VETERINARY_CLINIC_NOT_FOUND';
  end if;

  return query
  select * from public.veterinary_opening_hours
  where clinic_id = p_clinic_id
  order by day_of_week;
end;
$$;

create or replace function public.m12_list_managed_veterinary_opening_hours(p_clinic_id uuid)
returns setof public.veterinary_opening_hours
language plpgsql stable security definer set search_path = public as $$
declare v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if not public.has_org_permission(v_clinic.organization_id, 'veterinary.service.read') then
    raise exception 'VETERINARY_CLINIC_FORBIDDEN';
  end if;

  return query
  select * from public.veterinary_opening_hours
  where clinic_id = p_clinic_id
  order by day_of_week;
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Grants — helpers internos (sin EXECUTE para public/anon/authenticated)
-- ---------------------------------------------------------------------------
revoke all on function public._m12_require_authenticated() from public, anon, authenticated;
revoke all on function public._m12_is_safe_media_ref(text) from public, anon, authenticated;
revoke all on function public._m12_require_safe_media(text) from public, anon, authenticated;
revoke all on function public._m12_require_org_perm(uuid, text) from public, anon, authenticated;
revoke all on function public._m12_require_valid_branch(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m12_require_clinic_manage(uuid) from public, anon, authenticated;
revoke all on function public._m12_require_professional_manage() from public, anon, authenticated;
revoke all on function public._m12_require_m04_review() from public, anon, authenticated;
revoke all on function public._m12_validate_hours_row(integer, boolean, time, time) from public, anon, authenticated;
revoke all on function public._m12_assert_activation_requirements(uuid) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 9. Grants — RPCs cliente (revoke public/anon; grant execute authenticated)
-- ---------------------------------------------------------------------------
revoke all on function public.m12_create_veterinary_clinic_draft(uuid,text,text,uuid,text,text,boolean,text,text,text,jsonb,text,text,boolean,boolean) from public;
revoke all on function public.m12_create_veterinary_clinic_draft(uuid,text,text,uuid,text,text,boolean,text,text,text,jsonb,text,text,boolean,boolean) from anon;
grant execute on function public.m12_create_veterinary_clinic_draft(uuid,text,text,uuid,text,text,boolean,text,text,text,jsonb,text,text,boolean,boolean) to authenticated;

revoke all on function public.m12_update_veterinary_clinic_profile(uuid,text,text,text,text,boolean,text,text,text,jsonb,text,text,boolean,boolean) from public;
revoke all on function public.m12_update_veterinary_clinic_profile(uuid,text,text,text,text,boolean,text,text,text,jsonb,text,text,boolean,boolean) from anon;
grant execute on function public.m12_update_veterinary_clinic_profile(uuid,text,text,text,text,boolean,text,text,text,jsonb,text,text,boolean,boolean) to authenticated;

revoke all on function public.m12_change_veterinary_clinic_status(uuid,text) from public;
revoke all on function public.m12_change_veterinary_clinic_status(uuid,text) from anon;
grant execute on function public.m12_change_veterinary_clinic_status(uuid,text) to authenticated;

revoke all on function public.m12_request_veterinary_clinic_verification(uuid) from public;
revoke all on function public.m12_request_veterinary_clinic_verification(uuid) from anon;
grant execute on function public.m12_request_veterinary_clinic_verification(uuid) to authenticated;

revoke all on function public.m12_review_veterinary_clinic_verification(uuid,text) from public;
revoke all on function public.m12_review_veterinary_clinic_verification(uuid,text) from anon;
grant execute on function public.m12_review_veterinary_clinic_verification(uuid,text) to authenticated;

revoke all on function public.m12_get_public_veterinary_clinic(uuid) from public;
revoke all on function public.m12_get_public_veterinary_clinic(uuid) from anon;
grant execute on function public.m12_get_public_veterinary_clinic(uuid) to authenticated;

revoke all on function public.m12_get_managed_veterinary_clinic(uuid) from public;
revoke all on function public.m12_get_managed_veterinary_clinic(uuid) from anon;
grant execute on function public.m12_get_managed_veterinary_clinic(uuid) to authenticated;

revoke all on function public.m12_list_public_veterinary_clinics(text,text,text,text,boolean,boolean,boolean) from public;
revoke all on function public.m12_list_public_veterinary_clinics(text,text,text,text,boolean,boolean,boolean) from anon;
grant execute on function public.m12_list_public_veterinary_clinics(text,text,text,text,boolean,boolean,boolean) to authenticated;

revoke all on function public.m12_list_managed_veterinary_clinics() from public;
revoke all on function public.m12_list_managed_veterinary_clinics() from anon;
grant execute on function public.m12_list_managed_veterinary_clinics() to authenticated;

revoke all on function public.m12_create_veterinary_professional(text,uuid,text,text,text,boolean,text) from public;
revoke all on function public.m12_create_veterinary_professional(text,uuid,text,text,text,boolean,text) from anon;
grant execute on function public.m12_create_veterinary_professional(text,uuid,text,text,text,boolean,text) to authenticated;

revoke all on function public.m12_update_veterinary_professional(uuid,text,text,text,text,boolean,text,text) from public;
revoke all on function public.m12_update_veterinary_professional(uuid,text,text,text,text,boolean,text,text) from anon;
grant execute on function public.m12_update_veterinary_professional(uuid,text,text,text,text,boolean,text,text) to authenticated;

revoke all on function public.m12_link_veterinary_professional(uuid,uuid,text) from public;
revoke all on function public.m12_link_veterinary_professional(uuid,uuid,text) from anon;
grant execute on function public.m12_link_veterinary_professional(uuid,uuid,text) to authenticated;

revoke all on function public.m12_unlink_veterinary_professional(uuid,uuid) from public;
revoke all on function public.m12_unlink_veterinary_professional(uuid,uuid) from anon;
grant execute on function public.m12_unlink_veterinary_professional(uuid,uuid) to authenticated;

revoke all on function public.m12_replace_veterinary_professional_specialties(uuid,text[]) from public;
revoke all on function public.m12_replace_veterinary_professional_specialties(uuid,text[]) from anon;
grant execute on function public.m12_replace_veterinary_professional_specialties(uuid,text[]) to authenticated;

revoke all on function public.m12_request_veterinary_professional_verification(uuid) from public;
revoke all on function public.m12_request_veterinary_professional_verification(uuid) from anon;
grant execute on function public.m12_request_veterinary_professional_verification(uuid) to authenticated;

revoke all on function public.m12_review_veterinary_professional_verification(uuid,text) from public;
revoke all on function public.m12_review_veterinary_professional_verification(uuid,text) from anon;
grant execute on function public.m12_review_veterinary_professional_verification(uuid,text) to authenticated;

revoke all on function public.m12_list_public_veterinary_professionals(uuid) from public;
revoke all on function public.m12_list_public_veterinary_professionals(uuid) from anon;
grant execute on function public.m12_list_public_veterinary_professionals(uuid) to authenticated;

revoke all on function public.m12_list_managed_veterinary_professionals(uuid) from public;
revoke all on function public.m12_list_managed_veterinary_professionals(uuid) from anon;
grant execute on function public.m12_list_managed_veterinary_professionals(uuid) to authenticated;

revoke all on function public.m12_create_veterinary_service(uuid,text,text,text,text[],boolean,boolean,boolean) from public;
revoke all on function public.m12_create_veterinary_service(uuid,text,text,text,text[],boolean,boolean,boolean) from anon;
grant execute on function public.m12_create_veterinary_service(uuid,text,text,text,text[],boolean,boolean,boolean) to authenticated;

revoke all on function public.m12_update_veterinary_service(uuid,text,text,text,text[],boolean,boolean) from public;
revoke all on function public.m12_update_veterinary_service(uuid,text,text,text,text[],boolean,boolean) from anon;
grant execute on function public.m12_update_veterinary_service(uuid,text,text,text,text[],boolean,boolean) to authenticated;

revoke all on function public.m12_change_veterinary_service_status(uuid,boolean,boolean) from public;
revoke all on function public.m12_change_veterinary_service_status(uuid,boolean,boolean) from anon;
grant execute on function public.m12_change_veterinary_service_status(uuid,boolean,boolean) to authenticated;

revoke all on function public.m12_list_public_veterinary_services(uuid) from public;
revoke all on function public.m12_list_public_veterinary_services(uuid) from anon;
grant execute on function public.m12_list_public_veterinary_services(uuid) to authenticated;

revoke all on function public.m12_list_managed_veterinary_services(uuid) from public;
revoke all on function public.m12_list_managed_veterinary_services(uuid) from anon;
grant execute on function public.m12_list_managed_veterinary_services(uuid) to authenticated;

revoke all on function public.m12_replace_veterinary_opening_hours(uuid,jsonb) from public;
revoke all on function public.m12_replace_veterinary_opening_hours(uuid,jsonb) from anon;
grant execute on function public.m12_replace_veterinary_opening_hours(uuid,jsonb) to authenticated;

revoke all on function public.m12_list_public_veterinary_opening_hours(uuid) from public;
revoke all on function public.m12_list_public_veterinary_opening_hours(uuid) from anon;
grant execute on function public.m12_list_public_veterinary_opening_hours(uuid) to authenticated;

revoke all on function public.m12_list_managed_veterinary_opening_hours(uuid) from public;
revoke all on function public.m12_list_managed_veterinary_opening_hours(uuid) from anon;
grant execute on function public.m12_list_managed_veterinary_opening_hours(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 10. Comentarios técnicos
-- ---------------------------------------------------------------------------
comment on table public.veterinary_clinic_profiles is
  'M12 Bloque 2: perfiles de clínicas veterinarias por organización/sede M03; media M05; sin precios ni turnos.';
comment on table public.veterinary_professionals is
  'M12 Bloque 2: profesionales veterinarios; matrícula declarada; sin autoridad administrativa automática.';
comment on table public.veterinary_clinic_professionals is
  'M12 Bloque 2: vínculo clínica-profesional; una vinculación activa por clínica/profesional; historial preservado.';
comment on table public.veterinary_professional_specialties is
  'M12 Bloque 2: especialidades declaradas por profesional; reemplazo transaccional.';
comment on table public.veterinary_services is
  'M12 Bloque 2: catálogo de servicios veterinarios; especies tipadas; sin precios ni pagos.';
comment on table public.veterinary_opening_hours is
  'M12 Bloque 2: horarios semanales por clínica; 24 h = opens_at 00:00 / closes_at 23:59; reemplazo transaccional.';
comment on function public.m12_review_veterinary_clinic_verification(uuid,text) is
  'Revisión de verificación de clínica; requiere autoridad M04 organizations.review_verification.';
comment on function public.m12_replace_veterinary_opening_hours(uuid,jsonb) is
  'Reemplazo semanal transaccional de horarios; rechaza días duplicados y ventanas inválidas.';

commit;
