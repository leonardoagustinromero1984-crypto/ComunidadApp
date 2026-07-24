-- =============================================================================
-- LeoVer M11 — migración 045: urgencias, eventos institucionales y reportes
-- Forward-only sobre 001–044. NO reescribe 040–044.
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 shelter.emergency / event / report
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('shelter.emergency.read', 'Ver urgencias operativas del refugio'),
  ('shelter.emergency.manage', 'Gestionar urgencias operativas del refugio'),
  ('shelter.event.read', 'Ver eventos institucionales del refugio'),
  ('shelter.event.manage', 'Gestionar eventos e inscripciones del refugio'),
  ('shelter.report.read', 'Ver reportes y estadísticas operativas del refugio'),
  ('shelter.report.export', 'Exportar reportes operativos del refugio')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in (
    'shelter.emergency.read', 'shelter.emergency.manage',
    'shelter.event.read', 'shelter.event.manage',
    'shelter.report.read', 'shelter.report.export'
  )
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code in (
    'shelter.emergency.read',
    'shelter.event.read',
    'shelter.report.read'
  )
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.shelter_emergencies (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.shelter_profiles (id) on delete restrict,
  pet_id uuid null references public.pets (id) on delete set null,
  title text not null,
  description text not null,
  category text not null,
  severity text not null,
  visibility text not null,
  status text not null default 'DRAFT',
  starts_at timestamptz not null default timezone('utc', now()),
  expires_at timestamptz,
  resolved_at timestamptz,
  resolution_notes text,
  evidence_ref text,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint shelter_emergencies_category_chk
    check (category = any (array[
      'MEDICAL','FOOD','MEDICATION','CAPACITY','TRANSPORT','INFRASTRUCTURE','RESCUE','OTHER'
    ]::text[])),
  constraint shelter_emergencies_severity_chk
    check (severity = any (array['LOW','MEDIUM','HIGH','CRITICAL']::text[])),
  constraint shelter_emergencies_visibility_chk
    check (visibility = any (array['PUBLIC','INTERNAL']::text[])),
  constraint shelter_emergencies_status_chk
    check (status = any (array['DRAFT','ACTIVE','RESOLVED','EXPIRED','CANCELLED']::text[])),
  constraint shelter_emergencies_evidence_ref_chk
    check (evidence_ref is null or evidence_ref ~* '^(m05://|file_asset:)'),
  constraint shelter_emergencies_title_chk check (char_length(trim(title)) > 0),
  constraint shelter_emergencies_description_chk check (char_length(trim(description)) > 0)
);

create index if not exists shelter_emergencies_shelter_idx
  on public.shelter_emergencies (shelter_profile_id);
create index if not exists shelter_emergencies_status_idx
  on public.shelter_emergencies (status);
create index if not exists shelter_emergencies_severity_idx
  on public.shelter_emergencies (severity);
create index if not exists shelter_emergencies_expires_idx
  on public.shelter_emergencies (expires_at);

create table if not exists public.shelter_events (
  id uuid primary key default gen_random_uuid(),
  shelter_profile_id uuid not null references public.shelter_profiles (id) on delete restrict,
  title text not null,
  description text not null,
  event_type text not null,
  visibility text not null,
  status text not null default 'DRAFT',
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  capacity integer,
  registered_count integer not null default 0,
  public_location_text text,
  private_location_text text,
  cover_asset_ref text,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint shelter_events_type_chk
    check (event_type = any (array[
      'VOLUNTEERING','ADOPTION_DAY','COLLECTION','TRAINING','OPEN_HOUSE','COMMUNITY','OTHER'
    ]::text[])),
  constraint shelter_events_visibility_chk
    check (visibility = any (array['PUBLIC','INTERNAL']::text[])),
  constraint shelter_events_status_chk
    check (status = any (array['DRAFT','PUBLISHED','FULL','COMPLETED','CANCELLED']::text[])),
  constraint shelter_events_ends_after_starts_chk check (ends_at > starts_at),
  constraint shelter_events_capacity_chk check (capacity is null or capacity > 0),
  constraint shelter_events_registered_count_chk check (registered_count >= 0),
  constraint shelter_events_cover_ref_chk
    check (cover_asset_ref is null or cover_asset_ref ~* '^(m05://|file_asset:)'),
  constraint shelter_events_title_chk check (char_length(trim(title)) > 0),
  constraint shelter_events_description_chk check (char_length(trim(description)) > 0)
);

create index if not exists shelter_events_shelter_idx
  on public.shelter_events (shelter_profile_id);
create index if not exists shelter_events_status_idx
  on public.shelter_events (status);
create index if not exists shelter_events_starts_idx
  on public.shelter_events (starts_at);

create table if not exists public.shelter_event_registrations (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.shelter_events (id) on delete restrict,
  user_id uuid not null references public.users (id),
  status text not null default 'REGISTERED',
  notes text,
  registered_at timestamptz not null default timezone('utc', now()),
  cancelled_at timestamptz,
  constraint shelter_event_reg_status_chk
    check (status = any (array[
      'REGISTERED','WAITLISTED','ATTENDED','NO_SHOW','CANCELLED'
    ]::text[]))
);

create unique index if not exists shelter_event_reg_open_uniq
  on public.shelter_event_registrations (event_id, user_id)
  where status = any (array['REGISTERED','WAITLISTED']::text[]);

create index if not exists shelter_event_reg_event_idx
  on public.shelter_event_registrations (event_id);
create index if not exists shelter_event_reg_user_idx
  on public.shelter_event_registrations (user_id);
create index if not exists shelter_event_reg_status_idx
  on public.shelter_event_registrations (status);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m11_sync_event_registration_counts(p_event_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_event public.shelter_events;
  v_registered integer;
begin
  select * into v_event from public.shelter_events where id = p_event_id for update;
  if not found then return; end if;

  select count(*) into v_registered
  from public.shelter_event_registrations r
  where r.event_id = p_event_id
    and r.status = 'REGISTERED';

  update public.shelter_events set
    registered_count = coalesce(v_registered, 0),
    status = case
      when status = 'PUBLISHED'
        and capacity is not null
        and coalesce(v_registered, 0) >= capacity then 'FULL'
      when status = 'FULL'
        and (capacity is null or coalesce(v_registered, 0) < capacity) then 'PUBLISHED'
      else status
    end,
    updated_at = timezone('utc', now())
  where id = p_event_id;
end;
$$;

create or replace function public._m11_expire_shelter_emergencies(p_shelter_id uuid default null)
returns integer
language plpgsql security definer set search_path = public as $$
declare
  v_count integer;
begin
  update public.shelter_emergencies set
    status = 'EXPIRED',
    updated_at = timezone('utc', now())
  where status = 'ACTIVE'
    and expires_at is not null
    and expires_at < timezone('utc', now())
    and (p_shelter_id is null or shelter_profile_id = p_shelter_id);

  get diagnostics v_count = row_count;
  return coalesce(v_count, 0);
end;
$$;

create or replace function public._m11_require_shelter_report_range(
  p_from timestamptz,
  p_to timestamptz
) returns void
language plpgsql immutable as $$
begin
  if p_from is not null and p_to is not null and p_to <= p_from then
    raise exception 'SHELTER_REPORT_INVALID_RANGE';
  end if;
end;
$$;

create or replace function public._m11_require_shelter_report_read(p_shelter_id uuid)
returns public.shelter_profiles
language plpgsql stable security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
begin
  perform public._m11_require_authenticated();
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.report.read') then
    raise exception 'SHELTER_REPORT_FORBIDDEN';
  end if;
  return v_s;
end;
$$;

create or replace function public._m11_validate_emergency_pet(
  p_shelter_id uuid,
  p_pet_id uuid
) returns void
language plpgsql stable security definer set search_path = public as $$
begin
  if p_pet_id is null then return; end if;
  if not exists (select 1 from public.pets where id = p_pet_id) then
    raise exception 'PET_NOT_FOUND';
  end if;
  if not exists (
    select 1 from public.shelter_pet_placements pp
    where pp.shelter_profile_id = p_shelter_id
      and pp.pet_id = p_pet_id
      and pp.status = any (array['RESERVED','ACTIVE','QUARANTINE','MEDICAL_CARE']::text[])
  ) then
    raise exception 'SHELTER_PET_NOT_FOUND';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. Catálogo M07
-- ---------------------------------------------------------------------------
insert into public.observability_event_catalog (
  event_key, module, category, default_severity, sensitivity, organization_scoped,
  retention_policy_key, remote_persistence_allowed, analytics_allowed,
  allowed_metadata_keys, required_metadata_keys
) values
('SHELTER_EMERGENCY_CREATED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_EMERGENCY_STATUS_CHANGED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_EMERGENCY_RESOLVED','M11','AUDIT','NOTICE','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_EVENT_CREATED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_EVENT_STATUS_CHANGED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_EVENT_REGISTRATION','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_EVENT_REGISTRATION_CANCELLED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_EVENT_ATTENDANCE','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('SHELTER_REPORT_EXPORTED','M11','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[])
on conflict (event_key) do nothing;

-- ---------------------------------------------------------------------------
-- 4. RLS
-- ---------------------------------------------------------------------------
alter table public.shelter_emergencies enable row level security;
alter table public.shelter_events enable row level security;
alter table public.shelter_event_registrations enable row level security;

drop policy if exists shelter_emergencies_select_m11 on public.shelter_emergencies;
drop policy if exists shelter_emergencies_ins_m11 on public.shelter_emergencies;
drop policy if exists shelter_emergencies_upd_m11 on public.shelter_emergencies;
drop policy if exists shelter_emergencies_del_m11 on public.shelter_emergencies;
create policy shelter_emergencies_select_m11 on public.shelter_emergencies for select to authenticated
  using (
    (status = 'ACTIVE' and visibility = 'PUBLIC')
    or exists (
      select 1 from public.shelter_profiles s
      where s.id = shelter_profile_id
        and public.has_org_permission(s.organization_id, 'shelter.emergency.read')
    )
  );
create policy shelter_emergencies_ins_m11 on public.shelter_emergencies for insert to authenticated with check (false);
create policy shelter_emergencies_upd_m11 on public.shelter_emergencies for update to authenticated using (false);
create policy shelter_emergencies_del_m11 on public.shelter_emergencies for delete to authenticated using (false);

drop policy if exists shelter_events_select_m11 on public.shelter_events;
drop policy if exists shelter_events_ins_m11 on public.shelter_events;
drop policy if exists shelter_events_upd_m11 on public.shelter_events;
drop policy if exists shelter_events_del_m11 on public.shelter_events;
create policy shelter_events_select_m11 on public.shelter_events for select to authenticated
  using (
    (status in ('PUBLISHED','FULL') and visibility = 'PUBLIC')
    or exists (
      select 1 from public.shelter_profiles s
      where s.id = shelter_profile_id
        and public.has_org_permission(s.organization_id, 'shelter.event.read')
    )
  );
create policy shelter_events_ins_m11 on public.shelter_events for insert to authenticated with check (false);
create policy shelter_events_upd_m11 on public.shelter_events for update to authenticated using (false);
create policy shelter_events_del_m11 on public.shelter_events for delete to authenticated using (false);

drop policy if exists shelter_event_reg_select_m11 on public.shelter_event_registrations;
drop policy if exists shelter_event_reg_ins_m11 on public.shelter_event_registrations;
drop policy if exists shelter_event_reg_upd_m11 on public.shelter_event_registrations;
drop policy if exists shelter_event_reg_del_m11 on public.shelter_event_registrations;
create policy shelter_event_reg_select_m11 on public.shelter_event_registrations for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from public.shelter_events e
      join public.shelter_profiles s on s.id = e.shelter_profile_id
      where e.id = event_id
        and public.has_org_permission(s.organization_id, 'shelter.event.read')
    )
  );
create policy shelter_event_reg_ins_m11 on public.shelter_event_registrations for insert to authenticated with check (false);
create policy shelter_event_reg_upd_m11 on public.shelter_event_registrations for update to authenticated using (false);
create policy shelter_event_reg_del_m11 on public.shelter_event_registrations for delete to authenticated using (false);

revoke all privileges on table public.shelter_emergencies from public;
revoke all privileges on table public.shelter_emergencies from anon;
revoke all privileges on table public.shelter_emergencies from authenticated;
revoke all privileges on table public.shelter_events from public;
revoke all privileges on table public.shelter_events from anon;
revoke all privileges on table public.shelter_events from authenticated;
revoke all privileges on table public.shelter_event_registrations from public;
revoke all privileges on table public.shelter_event_registrations from anon;
revoke all privileges on table public.shelter_event_registrations from authenticated;

grant select on table public.shelter_emergencies to authenticated;
grant select on table public.shelter_events to authenticated;
grant select on table public.shelter_event_registrations to authenticated;
grant all on table public.shelter_emergencies to service_role;
grant all on table public.shelter_events to service_role;
grant all on table public.shelter_event_registrations to service_role;

-- ---------------------------------------------------------------------------
-- 5. RPCs — urgencias
-- M06: hooks Android/dominio preparados; NO invocar m06_emit_domain_notification aquí.
-- ---------------------------------------------------------------------------
create or replace function public.m11_create_shelter_emergency(
  p_shelter_id uuid,
  p_title text,
  p_description text,
  p_category text,
  p_severity text,
  p_visibility text,
  p_pet_id uuid default null,
  p_starts_at timestamptz default null,
  p_expires_at timestamptz default null,
  p_evidence_ref text default null,
  p_activate boolean default false
) returns public.shelter_emergencies
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
  v_row public.shelter_emergencies;
  v_status text := case when coalesce(p_activate, false) then 'ACTIVE' else 'DRAFT' end;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.emergency.manage');
  if char_length(trim(coalesce(p_title, ''))) = 0
     or char_length(trim(coalesce(p_description, ''))) = 0 then
    raise exception 'SHELTER_EMERGENCY_INVALID_TRANSITION';
  end if;
  perform public._m11_require_safe_evidence(p_evidence_ref);
  perform public._m11_validate_emergency_pet(p_shelter_id, p_pet_id);
  if v_status = 'ACTIVE' and v_s.status <> 'ACTIVE' then
    raise exception 'SHELTER_NOT_ACTIVE';
  end if;

  insert into public.shelter_emergencies (
    shelter_profile_id, pet_id, title, description, category, severity, visibility, status,
    starts_at, expires_at, evidence_ref, created_by
  ) values (
    p_shelter_id, p_pet_id, trim(p_title), trim(p_description),
    upper(trim(p_category)), upper(trim(p_severity)), upper(trim(p_visibility)), v_status,
    coalesce(p_starts_at, timezone('utc', now())), p_expires_at,
    nullif(trim(coalesce(p_evidence_ref, '')), ''), v_actor
  ) returning * into v_row;

  if upper(trim(p_severity)) = 'CRITICAL' and v_status = 'ACTIVE' then
    null; -- M06 hook: critical emergency notification (client/domain)
  end if;

  perform public.m07_best_effort_audit(
    'SHELTER_EMERGENCY_CREATED', 'CREATE', 'SUCCESS', v_corr,
    'shelter_emergency', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m11_update_shelter_emergency(
  p_id uuid,
  p_title text,
  p_description text,
  p_category text,
  p_severity text,
  p_visibility text,
  p_pet_id uuid default null,
  p_starts_at timestamptz default null,
  p_expires_at timestamptz default null,
  p_evidence_ref text default null
) returns public.shelter_emergencies
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_emergencies;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_emergencies where id = p_id for update;
  if not found then raise exception 'SHELTER_EMERGENCY_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.emergency.manage');
  if v_row.status in ('RESOLVED', 'EXPIRED', 'CANCELLED') then
    raise exception 'SHELTER_EMERGENCY_INVALID_TRANSITION';
  end if;
  if char_length(trim(coalesce(p_title, ''))) = 0
     or char_length(trim(coalesce(p_description, ''))) = 0 then
    raise exception 'SHELTER_EMERGENCY_INVALID_TRANSITION';
  end if;
  perform public._m11_require_safe_evidence(p_evidence_ref);
  perform public._m11_validate_emergency_pet(v_row.shelter_profile_id, p_pet_id);

  update public.shelter_emergencies set
    title = trim(p_title),
    description = trim(p_description),
    category = upper(trim(p_category)),
    severity = upper(trim(p_severity)),
    visibility = upper(trim(p_visibility)),
    pet_id = p_pet_id,
    starts_at = coalesce(p_starts_at, starts_at),
    expires_at = p_expires_at,
    evidence_ref = nullif(trim(coalesce(p_evidence_ref, '')), ''),
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m11_change_shelter_emergency_status(
  p_id uuid,
  p_status text
) returns public.shelter_emergencies
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_emergencies;
  v_s public.shelter_profiles;
  v_status text := upper(trim(p_status));
  v_corr text := public.m07_new_correlation_id();
begin
  perform public._m11_expire_shelter_emergencies(null);
  select * into v_row from public.shelter_emergencies where id = p_id for update;
  if not found then raise exception 'SHELTER_EMERGENCY_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.emergency.manage');
  if v_status not in ('DRAFT','ACTIVE','RESOLVED','EXPIRED','CANCELLED') then
    raise exception 'SHELTER_EMERGENCY_INVALID_TRANSITION';
  end if;
  if v_row.status = v_status then return v_row; end if;
  if v_status = 'ACTIVE' then
    if v_s.status <> 'ACTIVE' then raise exception 'SHELTER_NOT_ACTIVE'; end if;
    if v_row.expires_at is not null and v_row.expires_at < timezone('utc', now()) then
      raise exception 'SHELTER_EMERGENCY_EXPIRED';
    end if;
  end if;
  if v_status = 'RESOLVED' then
    raise exception 'SHELTER_EMERGENCY_RESOLUTION_REQUIRED';
  end if;

  update public.shelter_emergencies set
    status = v_status,
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;

  if v_row.severity = 'CRITICAL' and v_status = 'ACTIVE' then
    null; -- M06 hook: critical emergency notification (client/domain)
  end if;

  perform public.m07_best_effort_audit(
    'SHELTER_EMERGENCY_STATUS_CHANGED', 'STATUS_CHANGE', 'SUCCESS', v_corr,
    'shelter_emergency', v_row.id::text,
    jsonb_build_object(
      'result','SUCCESS','module','M11','organization_id', v_s.organization_id,
      'new_status', v_status
    )
  );
  return v_row;
end;
$$;

create or replace function public.m11_resolve_shelter_emergency(
  p_id uuid,
  p_resolution_notes text,
  p_evidence_ref text default null
) returns public.shelter_emergencies
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_emergencies;
  v_s public.shelter_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  perform public._m11_expire_shelter_emergencies(null);
  select * into v_row from public.shelter_emergencies where id = p_id for update;
  if not found then raise exception 'SHELTER_EMERGENCY_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.emergency.manage');
  if v_row.status not in ('ACTIVE', 'DRAFT') then
    raise exception 'SHELTER_EMERGENCY_INVALID_TRANSITION';
  end if;
  if char_length(trim(coalesce(p_resolution_notes, ''))) = 0 then
    raise exception 'SHELTER_EMERGENCY_RESOLUTION_REQUIRED';
  end if;
  perform public._m11_require_safe_evidence(p_evidence_ref);

  update public.shelter_emergencies set
    status = 'RESOLVED',
    resolved_at = timezone('utc', now()),
    resolution_notes = trim(p_resolution_notes),
    evidence_ref = coalesce(nullif(trim(coalesce(p_evidence_ref, '')), ''), evidence_ref),
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'SHELTER_EMERGENCY_RESOLVED', 'RESOLVE', 'SUCCESS', v_corr,
    'shelter_emergency', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m11_get_shelter_emergency(p_id uuid)
returns public.shelter_emergencies
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_emergencies;
  v_s public.shelter_profiles;
  v_can_read boolean;
begin
  perform public._m11_expire_shelter_emergencies(null);
  select * into v_row from public.shelter_emergencies where id = p_id;
  if not found then raise exception 'SHELTER_EMERGENCY_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  v_can_read := public.has_org_permission(v_s.organization_id, 'shelter.emergency.read');
  if v_row.visibility = 'PUBLIC' and v_row.status = 'ACTIVE' then
    if not v_can_read then
      v_row.resolution_notes := null;
    end if;
    return v_row;
  end if;
  if not v_can_read then
    raise exception 'SHELTER_EMERGENCY_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m11_list_public_shelter_emergencies()
returns setof public.shelter_emergencies
language plpgsql security definer set search_path = public as $$
begin
  perform public._m11_expire_shelter_emergencies(null);
  return query
  select e.*
  from public.shelter_emergencies e
  where e.status = 'ACTIVE'
    and e.visibility = 'PUBLIC'
  order by e.severity desc, e.updated_at desc;
end;
$$;

create or replace function public.m11_list_shelter_emergencies(p_shelter_id uuid)
returns setof public.shelter_emergencies
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
begin
  perform public._m11_expire_shelter_emergencies(p_shelter_id);
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.emergency.read') then
    raise exception 'SHELTER_EMERGENCY_FORBIDDEN';
  end if;
  return query
  select * from public.shelter_emergencies
  where shelter_profile_id = p_shelter_id
  order by updated_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPCs — eventos e inscripciones
-- M06: hooks Android/dominio preparados; NO invocar m06_emit_domain_notification aquí.
-- ---------------------------------------------------------------------------
create or replace function public.m11_create_shelter_event(
  p_shelter_id uuid,
  p_title text,
  p_description text,
  p_event_type text,
  p_visibility text,
  p_starts_at timestamptz,
  p_ends_at timestamptz,
  p_capacity integer default null,
  p_public_location_text text default null,
  p_private_location_text text default null,
  p_cover_asset_ref text default null,
  p_publish boolean default false
) returns public.shelter_events
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
  v_row public.shelter_events;
  v_status text := case when coalesce(p_publish, false) then 'PUBLISHED' else 'DRAFT' end;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.event.manage');
  if char_length(trim(coalesce(p_title, ''))) = 0
     or char_length(trim(coalesce(p_description, ''))) = 0 then
    raise exception 'SHELTER_EVENT_INVALID';
  end if;
  if p_ends_at <= p_starts_at then raise exception 'SHELTER_EVENT_INVALID'; end if;
  if p_capacity is not null and p_capacity <= 0 then raise exception 'SHELTER_EVENT_INVALID'; end if;
  perform public._m11_require_safe_evidence(p_cover_asset_ref);
  if v_status = 'PUBLISHED' and v_s.status <> 'ACTIVE' then
    raise exception 'SHELTER_NOT_ACTIVE';
  end if;

  insert into public.shelter_events (
    shelter_profile_id, title, description, event_type, visibility, status,
    starts_at, ends_at, capacity, public_location_text, private_location_text,
    cover_asset_ref, created_by
  ) values (
    p_shelter_id, trim(p_title), trim(p_description),
    upper(trim(p_event_type)), upper(trim(p_visibility)), v_status,
    p_starts_at, p_ends_at, p_capacity,
    nullif(trim(coalesce(p_public_location_text, '')), ''),
    nullif(trim(coalesce(p_private_location_text, '')), ''),
    nullif(trim(coalesce(p_cover_asset_ref, '')), ''), v_actor
  ) returning * into v_row;

  if v_status = 'PUBLISHED' then
    null; -- M06 hook: event published notification (client/domain)
  end if;

  perform public.m07_best_effort_audit(
    'SHELTER_EVENT_CREATED', 'CREATE', 'SUCCESS', v_corr,
    'shelter_event', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m11_update_shelter_event(
  p_id uuid,
  p_title text,
  p_description text,
  p_event_type text,
  p_visibility text,
  p_starts_at timestamptz,
  p_ends_at timestamptz,
  p_capacity integer default null,
  p_public_location_text text default null,
  p_private_location_text text default null,
  p_cover_asset_ref text default null
) returns public.shelter_events
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_events;
  v_s public.shelter_profiles;
begin
  select * into v_row from public.shelter_events where id = p_id for update;
  if not found then raise exception 'SHELTER_EVENT_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.event.manage');
  if v_row.status in ('COMPLETED', 'CANCELLED') then
    raise exception 'SHELTER_EVENT_INVALID';
  end if;
  if char_length(trim(coalesce(p_title, ''))) = 0
     or char_length(trim(coalesce(p_description, ''))) = 0 then
    raise exception 'SHELTER_EVENT_INVALID';
  end if;
  if p_ends_at <= p_starts_at then raise exception 'SHELTER_EVENT_INVALID'; end if;
  if p_capacity is not null and p_capacity <= 0 then raise exception 'SHELTER_EVENT_INVALID'; end if;
  perform public._m11_require_safe_evidence(p_cover_asset_ref);

  update public.shelter_events set
    title = trim(p_title),
    description = trim(p_description),
    event_type = upper(trim(p_event_type)),
    visibility = upper(trim(p_visibility)),
    starts_at = p_starts_at,
    ends_at = p_ends_at,
    capacity = p_capacity,
    public_location_text = nullif(trim(coalesce(p_public_location_text, '')), ''),
    private_location_text = nullif(trim(coalesce(p_private_location_text, '')), ''),
    cover_asset_ref = nullif(trim(coalesce(p_cover_asset_ref, '')), ''),
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;

  perform public._m11_sync_event_registration_counts(p_id);
  select * into v_row from public.shelter_events where id = p_id;
  return v_row;
end;
$$;

create or replace function public.m11_change_shelter_event_status(
  p_id uuid,
  p_status text
) returns public.shelter_events
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_events;
  v_s public.shelter_profiles;
  v_status text := upper(trim(p_status));
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.shelter_events where id = p_id for update;
  if not found then raise exception 'SHELTER_EVENT_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.event.manage');
  if v_status not in ('DRAFT','PUBLISHED','FULL','COMPLETED','CANCELLED') then
    raise exception 'SHELTER_EVENT_INVALID';
  end if;
  if v_row.status = v_status then return v_row; end if;
  if v_status in ('PUBLISHED', 'FULL') then
    if v_s.status <> 'ACTIVE' then raise exception 'SHELTER_NOT_ACTIVE'; end if;
  end if;

  if v_status = 'CANCELLED' then
    update public.shelter_event_registrations set
      status = 'CANCELLED',
      cancelled_at = timezone('utc', now())
    where event_id = p_id
      and status = any (array['REGISTERED','WAITLISTED']::text[]);
    null; -- M06 hook: event cancelled notification (client/domain)
  end if;

  update public.shelter_events set
    status = v_status,
    updated_at = timezone('utc', now())
  where id = p_id returning * into v_row;

  if v_status in ('PUBLISHED', 'FULL') then
    perform public._m11_sync_event_registration_counts(p_id);
    select * into v_row from public.shelter_events where id = p_id;
  end if;

  perform public.m07_best_effort_audit(
    'SHELTER_EVENT_STATUS_CHANGED', 'STATUS_CHANGE', 'SUCCESS', v_corr,
    'shelter_event', v_row.id::text,
    jsonb_build_object(
      'result','SUCCESS','module','M11','organization_id', v_s.organization_id,
      'new_status', v_status
    )
  );
  return v_row;
end;
$$;

create or replace function public.m11_get_shelter_event(p_id uuid)
returns public.shelter_events
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_events;
  v_s public.shelter_profiles;
  v_can_read boolean;
  v_is_registered boolean;
begin
  select * into v_row from public.shelter_events where id = p_id;
  if not found then raise exception 'SHELTER_EVENT_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_row.shelter_profile_id;
  v_can_read := public.has_org_permission(v_s.organization_id, 'shelter.event.read');
  v_is_registered := exists (
    select 1 from public.shelter_event_registrations r
    where r.event_id = p_id
      and r.user_id = v_actor
      and r.status = any (array['REGISTERED','WAITLISTED','ATTENDED']::text[])
  );

  if v_row.visibility = 'PUBLIC'
     and v_row.status = any (array['PUBLISHED','FULL']::text[]) then
    if not v_can_read and not v_is_registered then
      v_row.private_location_text := null;
    end if;
    return v_row;
  end if;

  if not v_can_read then
    raise exception 'SHELTER_EVENT_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m11_list_public_shelter_events()
returns setof public.shelter_events
language sql stable security definer set search_path = public as $$
  select e.*
  from public.shelter_events e
  where e.status in ('PUBLISHED','FULL')
    and e.visibility = 'PUBLIC'
  order by e.starts_at asc;
$$;

create or replace function public.m11_list_shelter_events(p_shelter_id uuid)
returns setof public.shelter_events
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_s public.shelter_profiles;
begin
  select * into v_s from public.shelter_profiles where id = p_shelter_id;
  if not found then raise exception 'SHELTER_NOT_FOUND'; end if;
  if not public.has_org_permission(v_s.organization_id, 'shelter.event.read') then
    raise exception 'SHELTER_EVENT_FORBIDDEN';
  end if;
  return query
  select * from public.shelter_events
  where shelter_profile_id = p_shelter_id
  order by starts_at desc;
end;
$$;

create or replace function public.m11_register_shelter_event(
  p_event_id uuid,
  p_notes text default null
) returns public.shelter_event_registrations
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_event public.shelter_events;
  v_s public.shelter_profiles;
  v_row public.shelter_event_registrations;
  v_status text;
  v_registered integer;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_event from public.shelter_events where id = p_event_id for update;
  if not found then raise exception 'SHELTER_EVENT_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_event.shelter_profile_id;
  if v_event.status not in ('PUBLISHED', 'FULL') then
    raise exception 'SHELTER_EVENT_INVALID';
  end if;
  if exists (
    select 1 from public.shelter_event_registrations r
    where r.event_id = p_event_id
      and r.user_id = v_actor
      and r.status = any (array['REGISTERED','WAITLISTED']::text[])
  ) then
    raise exception 'SHELTER_EVENT_ALREADY_REGISTERED';
  end if;

  select count(*) into v_registered
  from public.shelter_event_registrations r
  where r.event_id = p_event_id and r.status = 'REGISTERED';

  if v_event.capacity is not null and coalesce(v_registered, 0) >= v_event.capacity then
    v_status := 'WAITLISTED';
    null; -- M06 hook: waitlist notification (client/domain)
  else
    v_status := 'REGISTERED';
  end if;

  insert into public.shelter_event_registrations (
    event_id, user_id, status, notes
  ) values (
    p_event_id, v_actor, v_status, nullif(trim(coalesce(p_notes, '')), '')
  ) returning * into v_row;

  perform public._m11_sync_event_registration_counts(p_event_id);

  perform public.m07_best_effort_audit(
    'SHELTER_EVENT_REGISTRATION', 'REGISTER', 'SUCCESS', v_corr,
    'shelter_event_registration', v_row.id::text,
    jsonb_build_object(
      'result','SUCCESS','module','M11','organization_id', v_s.organization_id,
      'registration_status', v_status
    )
  );
  return v_row;
end;
$$;

create or replace function public.m11_cancel_shelter_event_registration(p_registration_id uuid)
returns public.shelter_event_registrations
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_event_registrations;
  v_event public.shelter_events;
  v_s public.shelter_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.shelter_event_registrations where id = p_registration_id for update;
  if not found then raise exception 'SHELTER_EVENT_REGISTRATION_NOT_FOUND'; end if;
  if v_row.user_id <> v_actor
     and not exists (
       select 1 from public.shelter_events e
       join public.shelter_profiles s on s.id = e.shelter_profile_id
       where e.id = v_row.event_id
         and public.has_org_permission(s.organization_id, 'shelter.event.manage')
     ) then
    raise exception 'SHELTER_EVENT_FORBIDDEN';
  end if;
  if v_row.status not in ('REGISTERED', 'WAITLISTED') then
    raise exception 'SHELTER_EVENT_INVALID';
  end if;

  update public.shelter_event_registrations set
    status = 'CANCELLED',
    cancelled_at = timezone('utc', now())
  where id = p_registration_id returning * into v_row;

  select * into v_event from public.shelter_events where id = v_row.event_id;
  select * into v_s from public.shelter_profiles where id = v_event.shelter_profile_id;
  perform public._m11_sync_event_registration_counts(v_row.event_id);

  perform public.m07_best_effort_audit(
    'SHELTER_EVENT_REGISTRATION_CANCELLED', 'CANCEL', 'SUCCESS', v_corr,
    'shelter_event_registration', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m11_mark_shelter_event_attendance(
  p_registration_id uuid,
  p_attended boolean default true
) returns public.shelter_event_registrations
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_row public.shelter_event_registrations;
  v_event public.shelter_events;
  v_s public.shelter_profiles;
  v_status text := case when coalesce(p_attended, true) then 'ATTENDED' else 'NO_SHOW' end;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.shelter_event_registrations where id = p_registration_id for update;
  if not found then raise exception 'SHELTER_EVENT_REGISTRATION_NOT_FOUND'; end if;
  select * into v_event from public.shelter_events where id = v_row.event_id;
  select * into v_s from public.shelter_profiles where id = v_event.shelter_profile_id;
  perform public._m11_require_org_manage(v_s.organization_id, v_actor, 'shelter.event.manage');
  if v_row.status not in ('REGISTERED', 'WAITLISTED', 'ATTENDED', 'NO_SHOW') then
    raise exception 'SHELTER_EVENT_ATTENDANCE_FORBIDDEN';
  end if;

  update public.shelter_event_registrations set
    status = v_status
  where id = p_registration_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'SHELTER_EVENT_ATTENDANCE', 'ATTENDANCE', 'SUCCESS', v_corr,
    'shelter_event_registration', v_row.id::text,
    jsonb_build_object(
      'result','SUCCESS','module','M11','organization_id', v_s.organization_id,
      'attendance_status', v_status
    )
  );
  return v_row;
end;
$$;

create or replace function public.m11_list_shelter_event_registrations(p_event_id uuid)
returns setof public.shelter_event_registrations
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m11_require_authenticated();
  v_event public.shelter_events;
  v_s public.shelter_profiles;
begin
  select * into v_event from public.shelter_events where id = p_event_id;
  if not found then raise exception 'SHELTER_EVENT_NOT_FOUND'; end if;
  select * into v_s from public.shelter_profiles where id = v_event.shelter_profile_id;
  if public.has_org_permission(v_s.organization_id, 'shelter.event.read') then
    return query
    select * from public.shelter_event_registrations
    where event_id = p_event_id
    order by registered_at asc;
    return;
  end if;
  return query
  select * from public.shelter_event_registrations
  where event_id = p_event_id and user_id = v_actor
  order by registered_at asc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. RPCs — reportes agregados (jsonb, sin PII)
-- Exportación cliente: usar datos autorizados + SHELTER_REPORT_EXPORTED (M07).
-- ---------------------------------------------------------------------------
create or replace function public.m11_get_shelter_capacity_metrics(p_shelter_id uuid)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
begin
  v_s := public._m11_require_shelter_report_read(p_shelter_id);
  return jsonb_build_object(
    'shelter_profile_id', v_s.id,
    'total_capacity', v_s.total_capacity,
    'current_occupancy', v_s.current_occupancy,
    'reserved_capacity', v_s.reserved_capacity,
    'available_capacity', greatest(v_s.total_capacity - v_s.current_occupancy - v_s.reserved_capacity, 0),
    'status', v_s.status
  );
end;
$$;

create or replace function public.m11_get_shelter_pet_metrics(
  p_shelter_id uuid,
  p_from timestamptz default null,
  p_to timestamptz default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
begin
  perform public._m11_require_shelter_report_range(p_from, p_to);
  v_s := public._m11_require_shelter_report_read(p_shelter_id);
  return jsonb_build_object(
    'shelter_profile_id', v_s.id,
    'active', (
      select count(*) from public.shelter_pet_placements p
      where p.shelter_profile_id = p_shelter_id
        and p.status = 'ACTIVE'
    ),
    'quarantine', (
      select count(*) from public.shelter_pet_placements p
      where p.shelter_profile_id = p_shelter_id
        and p.status = 'QUARANTINE'
    ),
    'medical_care', (
      select count(*) from public.shelter_pet_placements p
      where p.shelter_profile_id = p_shelter_id
        and p.status = 'MEDICAL_CARE'
    ),
    'released_in_range', (
      select count(*) from public.shelter_pet_placements p
      where p.shelter_profile_id = p_shelter_id
        and p.status = 'RELEASED'
        and (p_from is null or p.ended_at >= p_from)
        and (p_to is null or p.ended_at <= p_to)
    ),
    'adopted_in_range', (
      select count(*) from public.shelter_pet_placements p
      where p.shelter_profile_id = p_shelter_id
        and p.status = 'ADOPTED'
        and (p_from is null or p.ended_at >= p_from)
        and (p_to is null or p.ended_at <= p_to)
    ),
    'range_from', p_from,
    'range_to', p_to
  );
end;
$$;

create or replace function public.m11_get_shelter_volunteer_metrics(
  p_shelter_id uuid,
  p_from timestamptz default null,
  p_to timestamptz default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
begin
  perform public._m11_require_shelter_report_range(p_from, p_to);
  v_s := public._m11_require_shelter_report_read(p_shelter_id);
  return jsonb_build_object(
    'shelter_profile_id', v_s.id,
    'active', (
      select count(*) from public.shelter_volunteer_assignments v
      where v.shelter_profile_id = p_shelter_id and v.status = 'ACTIVE'
    ),
    'paused', (
      select count(*) from public.shelter_volunteer_assignments v
      where v.shelter_profile_id = p_shelter_id and v.status = 'PAUSED'
    ),
    'ended_in_range', (
      select count(*) from public.shelter_volunteer_assignments v
      where v.shelter_profile_id = p_shelter_id
        and v.status = 'ENDED'
        and (p_from is null or v.ends_at >= p_from)
        and (p_to is null or v.ends_at <= p_to)
    ),
    'range_from', p_from,
    'range_to', p_to
  );
end;
$$;

create or replace function public.m11_get_shelter_campaign_metrics(
  p_shelter_id uuid,
  p_from timestamptz default null,
  p_to timestamptz default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
begin
  perform public._m11_require_shelter_report_range(p_from, p_to);
  v_s := public._m11_require_shelter_report_read(p_shelter_id);
  return jsonb_build_object(
    'shelter_profile_id', v_s.id,
    'active', (
      select count(*) from public.shelter_campaigns c
      where c.shelter_profile_id = p_shelter_id and c.status = 'ACTIVE'
    ),
    'completed_in_range', (
      select count(*) from public.shelter_campaigns c
      where c.shelter_profile_id = p_shelter_id
        and c.status = 'COMPLETED'
        and (p_from is null or c.updated_at >= p_from)
        and (p_to is null or c.updated_at <= p_to)
    ),
    'cancelled', (
      select count(*) from public.shelter_campaigns c
      where c.shelter_profile_id = p_shelter_id and c.status = 'CANCELLED'
    ),
    'range_from', p_from,
    'range_to', p_to
  );
end;
$$;

create or replace function public.m11_get_shelter_supply_metrics(
  p_shelter_id uuid,
  p_from timestamptz default null,
  p_to timestamptz default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
begin
  perform public._m11_require_shelter_report_range(p_from, p_to);
  v_s := public._m11_require_shelter_report_read(p_shelter_id);
  return jsonb_build_object(
    'shelter_profile_id', v_s.id,
    'open_requests', (
      select count(*) from public.shelter_supply_requests r
      where r.shelter_profile_id = p_shelter_id
        and r.status = any (array[
          'OPEN','PARTIALLY_COMMITTED','FULLY_COMMITTED','PARTIALLY_RECEIVED'
        ]::text[])
    ),
    'fulfilled_in_range', (
      select count(*) from public.shelter_supply_requests r
      where r.shelter_profile_id = p_shelter_id
        and r.status = 'FULFILLED'
        and (p_from is null or r.updated_at >= p_from)
        and (p_to is null or r.updated_at <= p_to)
    ),
    'quantity_received_in_range', (
      select coalesce(sum(r.quantity_received), 0) from public.shelter_supply_requests r
      where r.shelter_profile_id = p_shelter_id
        and (p_from is null or r.updated_at >= p_from)
        and (p_to is null or r.updated_at <= p_to)
    ),
    'range_from', p_from,
    'range_to', p_to
  );
end;
$$;

create or replace function public.m11_get_shelter_emergency_metrics(
  p_shelter_id uuid,
  p_from timestamptz default null,
  p_to timestamptz default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
begin
  perform public._m11_expire_shelter_emergencies(p_shelter_id);
  perform public._m11_require_shelter_report_range(p_from, p_to);
  v_s := public._m11_require_shelter_report_read(p_shelter_id);
  return jsonb_build_object(
    'shelter_profile_id', v_s.id,
    'active', (
      select count(*) from public.shelter_emergencies e
      where e.shelter_profile_id = p_shelter_id and e.status = 'ACTIVE'
    ),
    'critical_active', (
      select count(*) from public.shelter_emergencies e
      where e.shelter_profile_id = p_shelter_id
        and e.status = 'ACTIVE' and e.severity = 'CRITICAL'
    ),
    'resolved_in_range', (
      select count(*) from public.shelter_emergencies e
      where e.shelter_profile_id = p_shelter_id
        and e.status = 'RESOLVED'
        and (p_from is null or e.resolved_at >= p_from)
        and (p_to is null or e.resolved_at <= p_to)
    ),
    'expired', (
      select count(*) from public.shelter_emergencies e
      where e.shelter_profile_id = p_shelter_id and e.status = 'EXPIRED'
    ),
    'range_from', p_from,
    'range_to', p_to
  );
end;
$$;

create or replace function public.m11_get_shelter_event_metrics(
  p_shelter_id uuid,
  p_from timestamptz default null,
  p_to timestamptz default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
begin
  perform public._m11_require_shelter_report_range(p_from, p_to);
  v_s := public._m11_require_shelter_report_read(p_shelter_id);
  return jsonb_build_object(
    'shelter_profile_id', v_s.id,
    'upcoming', (
      select count(*) from public.shelter_events e
      where e.shelter_profile_id = p_shelter_id
        and e.status in ('PUBLISHED','FULL')
        and e.starts_at >= timezone('utc', now())
    ),
    'completed_in_range', (
      select count(*) from public.shelter_events e
      where e.shelter_profile_id = p_shelter_id
        and e.status = 'COMPLETED'
        and (p_from is null or e.ends_at >= p_from)
        and (p_to is null or e.ends_at <= p_to)
    ),
    'registrations_in_range', (
      select count(*) from public.shelter_event_registrations r
      join public.shelter_events e on e.id = r.event_id
      where e.shelter_profile_id = p_shelter_id
        and (p_from is null or r.registered_at >= p_from)
        and (p_to is null or r.registered_at <= p_to)
    ),
    'attended_in_range', (
      select count(*) from public.shelter_event_registrations r
      join public.shelter_events e on e.id = r.event_id
      where e.shelter_profile_id = p_shelter_id
        and r.status = 'ATTENDED'
        and (p_from is null or r.registered_at >= p_from)
        and (p_to is null or r.registered_at <= p_to)
    ),
    'range_from', p_from,
    'range_to', p_to
  );
end;
$$;

create or replace function public.m11_get_shelter_operational_summary(
  p_shelter_id uuid,
  p_from timestamptz default null,
  p_to timestamptz default null,
  p_record_export_audit boolean default false
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_s public.shelter_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  perform public._m11_require_shelter_report_range(p_from, p_to);
  v_s := public._m11_require_shelter_report_read(p_shelter_id);
  if coalesce(p_record_export_audit, false) then
    if not public.has_org_permission(v_s.organization_id, 'shelter.report.export') then
      raise exception 'SHELTER_REPORT_FORBIDDEN';
    end if;
    perform public.m07_best_effort_audit(
      'SHELTER_REPORT_EXPORTED', 'EXPORT', 'SUCCESS', v_corr,
      'shelter_profile', v_s.id::text,
      jsonb_build_object('result','SUCCESS','module','M11','organization_id', v_s.organization_id)
    );
  end if;
  return jsonb_build_object(
    'shelter_profile_id', v_s.id,
    'generated_at', timezone('utc', now()),
    'range_from', p_from,
    'range_to', p_to,
    'capacity', public.m11_get_shelter_capacity_metrics(p_shelter_id),
    'pets', public.m11_get_shelter_pet_metrics(p_shelter_id, p_from, p_to),
    'volunteers', public.m11_get_shelter_volunteer_metrics(p_shelter_id, p_from, p_to),
    'campaigns', public.m11_get_shelter_campaign_metrics(p_shelter_id, p_from, p_to),
    'supply', public.m11_get_shelter_supply_metrics(p_shelter_id, p_from, p_to),
    'emergencies', public.m11_get_shelter_emergency_metrics(p_shelter_id, p_from, p_to),
    'events', public.m11_get_shelter_event_metrics(p_shelter_id, p_from, p_to)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Grants
-- ---------------------------------------------------------------------------
revoke all on function public._m11_sync_event_registration_counts(uuid) from public, anon, authenticated;
revoke all on function public._m11_expire_shelter_emergencies(uuid) from public, anon, authenticated;
revoke all on function public._m11_require_shelter_report_range(timestamptz, timestamptz) from public, anon, authenticated;
revoke all on function public._m11_require_shelter_report_read(uuid) from public, anon, authenticated;
revoke all on function public._m11_validate_emergency_pet(uuid, uuid) from public, anon, authenticated;

revoke all on function public.m11_create_shelter_emergency(uuid,text,text,text,text,text,uuid,timestamptz,timestamptz,text,boolean) from public;
revoke all on function public.m11_create_shelter_emergency(uuid,text,text,text,text,text,uuid,timestamptz,timestamptz,text,boolean) from anon;
revoke all on function public.m11_update_shelter_emergency(uuid,text,text,text,text,text,uuid,timestamptz,timestamptz,text) from public;
revoke all on function public.m11_update_shelter_emergency(uuid,text,text,text,text,text,uuid,timestamptz,timestamptz,text) from anon;
revoke all on function public.m11_change_shelter_emergency_status(uuid,text) from public;
revoke all on function public.m11_change_shelter_emergency_status(uuid,text) from anon;
revoke all on function public.m11_resolve_shelter_emergency(uuid,text,text) from public;
revoke all on function public.m11_resolve_shelter_emergency(uuid,text,text) from anon;
revoke all on function public.m11_get_shelter_emergency(uuid) from public;
revoke all on function public.m11_get_shelter_emergency(uuid) from anon;
revoke all on function public.m11_list_public_shelter_emergencies() from public;
revoke all on function public.m11_list_public_shelter_emergencies() from anon;
revoke all on function public.m11_list_shelter_emergencies(uuid) from public;
revoke all on function public.m11_list_shelter_emergencies(uuid) from anon;
revoke all on function public.m11_create_shelter_event(uuid,text,text,text,text,timestamptz,timestamptz,integer,text,text,text,boolean) from public;
revoke all on function public.m11_create_shelter_event(uuid,text,text,text,text,timestamptz,timestamptz,integer,text,text,text,boolean) from anon;
revoke all on function public.m11_update_shelter_event(uuid,text,text,text,text,timestamptz,timestamptz,integer,text,text,text) from public;
revoke all on function public.m11_update_shelter_event(uuid,text,text,text,text,timestamptz,timestamptz,integer,text,text,text) from anon;
revoke all on function public.m11_change_shelter_event_status(uuid,text) from public;
revoke all on function public.m11_change_shelter_event_status(uuid,text) from anon;
revoke all on function public.m11_get_shelter_event(uuid) from public;
revoke all on function public.m11_get_shelter_event(uuid) from anon;
revoke all on function public.m11_list_public_shelter_events() from public;
revoke all on function public.m11_list_public_shelter_events() from anon;
revoke all on function public.m11_list_shelter_events(uuid) from public;
revoke all on function public.m11_list_shelter_events(uuid) from anon;
revoke all on function public.m11_register_shelter_event(uuid,text) from public;
revoke all on function public.m11_register_shelter_event(uuid,text) from anon;
revoke all on function public.m11_cancel_shelter_event_registration(uuid) from public;
revoke all on function public.m11_cancel_shelter_event_registration(uuid) from anon;
revoke all on function public.m11_mark_shelter_event_attendance(uuid,boolean) from public;
revoke all on function public.m11_mark_shelter_event_attendance(uuid,boolean) from anon;
revoke all on function public.m11_list_shelter_event_registrations(uuid) from public;
revoke all on function public.m11_list_shelter_event_registrations(uuid) from anon;
revoke all on function public.m11_get_shelter_operational_summary(uuid,timestamptz,timestamptz,boolean) from public;
revoke all on function public.m11_get_shelter_operational_summary(uuid,timestamptz,timestamptz,boolean) from anon;
revoke all on function public.m11_get_shelter_capacity_metrics(uuid) from public;
revoke all on function public.m11_get_shelter_capacity_metrics(uuid) from anon;
revoke all on function public.m11_get_shelter_pet_metrics(uuid,timestamptz,timestamptz) from public;
revoke all on function public.m11_get_shelter_pet_metrics(uuid,timestamptz,timestamptz) from anon;
revoke all on function public.m11_get_shelter_volunteer_metrics(uuid,timestamptz,timestamptz) from public;
revoke all on function public.m11_get_shelter_volunteer_metrics(uuid,timestamptz,timestamptz) from anon;
revoke all on function public.m11_get_shelter_campaign_metrics(uuid,timestamptz,timestamptz) from public;
revoke all on function public.m11_get_shelter_campaign_metrics(uuid,timestamptz,timestamptz) from anon;
revoke all on function public.m11_get_shelter_supply_metrics(uuid,timestamptz,timestamptz) from public;
revoke all on function public.m11_get_shelter_supply_metrics(uuid,timestamptz,timestamptz) from anon;
revoke all on function public.m11_get_shelter_emergency_metrics(uuid,timestamptz,timestamptz) from public;
revoke all on function public.m11_get_shelter_emergency_metrics(uuid,timestamptz,timestamptz) from anon;
revoke all on function public.m11_get_shelter_event_metrics(uuid,timestamptz,timestamptz) from public;
revoke all on function public.m11_get_shelter_event_metrics(uuid,timestamptz,timestamptz) from anon;

grant execute on function public.m11_create_shelter_emergency(uuid,text,text,text,text,text,uuid,timestamptz,timestamptz,text,boolean) to authenticated;
grant execute on function public.m11_update_shelter_emergency(uuid,text,text,text,text,text,uuid,timestamptz,timestamptz,text) to authenticated;
grant execute on function public.m11_change_shelter_emergency_status(uuid,text) to authenticated;
grant execute on function public.m11_resolve_shelter_emergency(uuid,text,text) to authenticated;
grant execute on function public.m11_get_shelter_emergency(uuid) to authenticated;
grant execute on function public.m11_list_public_shelter_emergencies() to authenticated;
grant execute on function public.m11_list_shelter_emergencies(uuid) to authenticated;
grant execute on function public.m11_create_shelter_event(uuid,text,text,text,text,timestamptz,timestamptz,integer,text,text,text,boolean) to authenticated;
grant execute on function public.m11_update_shelter_event(uuid,text,text,text,text,timestamptz,timestamptz,integer,text,text,text) to authenticated;
grant execute on function public.m11_change_shelter_event_status(uuid,text) to authenticated;
grant execute on function public.m11_get_shelter_event(uuid) to authenticated;
grant execute on function public.m11_list_public_shelter_events() to authenticated;
grant execute on function public.m11_list_shelter_events(uuid) to authenticated;
grant execute on function public.m11_register_shelter_event(uuid,text) to authenticated;
grant execute on function public.m11_cancel_shelter_event_registration(uuid) to authenticated;
grant execute on function public.m11_mark_shelter_event_attendance(uuid,boolean) to authenticated;
grant execute on function public.m11_list_shelter_event_registrations(uuid) to authenticated;
grant execute on function public.m11_get_shelter_operational_summary(uuid,timestamptz,timestamptz,boolean) to authenticated;
grant execute on function public.m11_get_shelter_capacity_metrics(uuid) to authenticated;
grant execute on function public.m11_get_shelter_pet_metrics(uuid,timestamptz,timestamptz) to authenticated;
grant execute on function public.m11_get_shelter_volunteer_metrics(uuid,timestamptz,timestamptz) to authenticated;
grant execute on function public.m11_get_shelter_campaign_metrics(uuid,timestamptz,timestamptz) to authenticated;
grant execute on function public.m11_get_shelter_supply_metrics(uuid,timestamptz,timestamptz) to authenticated;
grant execute on function public.m11_get_shelter_emergency_metrics(uuid,timestamptz,timestamptz) to authenticated;
grant execute on function public.m11_get_shelter_event_metrics(uuid,timestamptz,timestamptz) to authenticated;

-- ---------------------------------------------------------------------------
-- 9. Comentarios técnicos
-- ---------------------------------------------------------------------------
comment on table public.shelter_emergencies is
  'M11 Bloque 3: urgencias operativas del refugio; evidencia M05; sin datos monetarios.';
comment on table public.shelter_events is
  'M11 Bloque 3: eventos institucionales y voluntariado; inscripciones gratuitas; sin pagos.';
comment on table public.shelter_event_registrations is
  'M11 Bloque 3: inscripciones/waitlist por evento; un registro abierto por usuario.';
comment on function public._m11_sync_event_registration_counts(uuid) is
  'Recalcula registered_count y estado FULL/PUBLISHED según cupo.';
comment on function public._m11_expire_shelter_emergencies(uuid) is
  'Marca ACTIVE con expires_at vencido como EXPIRED; shelter opcional.';
comment on function public.m11_resolve_shelter_emergency(uuid,text,text) is
  'Resuelve urgencia con notas obligatorias. M06 hooks preparados; sin m06_emit_domain_notification.';
comment on function public.m11_register_shelter_event(uuid,text) is
  'Inscripción gratuita; WAITLISTED si cupo alcanzado. M06 hooks preparados.';
comment on function public.m11_get_shelter_operational_summary(uuid,timestamptz,timestamptz,boolean) is
  'Resumen agregado jsonb sin PII; p_record_export_audit requiere shelter.report.export.';

commit;
