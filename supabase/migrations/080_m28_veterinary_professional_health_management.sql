-- =============================================================================
-- LeoVer M28 — migration 080: professional veterinary health management (Pilot Minimum)
-- Forward-only over 001–079. Extends M12 (046–047) and integrates M14 (050) via proposals.
-- Deny-by-default RLS; client mutations only via SECURITY DEFINER RPCs.
-- Permission namespace: veterinary.care.* (M12 convention — not m28.* prefix).
-- LOCAL ONLY until remote apply authorized.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permissions M03 veterinary.care.* (deny-by-default via has_org_permission)
-- Note: veterinary.care.* chosen over m28.* per M12/M03 convention (DEC-M28-08).
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('veterinary.care.read', 'Ver pacientes y atenciones profesionales de la clínica'),
  ('veterinary.care.write', 'Crear y editar atenciones profesionales (con grant del responsable)'),
  ('veterinary.care.correct', 'Registrar correcciones versionadas de atenciones'),
  ('veterinary.care.document.upload', 'Adjuntar documentos profesionales'),
  ('veterinary.care.export', 'Solicitar exportaciones de información clínica-operativa'),
  ('veterinary.care.passport.propose', 'Proponer actualizaciones al Pasaporte M14'),
  ('veterinary.care.grant.manage', 'Gestionar grants de acceso profesional (vista clínica)')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code like 'veterinary.care.%'
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code in ('veterinary.care.read')
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tables
-- ---------------------------------------------------------------------------
create table if not exists public.veterinary_care_type_catalog (
  code text primary key,
  label text not null,
  active boolean not null default true,
  sort_order integer not null default 0,
  constraint veterinary_care_type_code_chk check (char_length(trim(code)) > 0),
  constraint veterinary_care_type_label_chk check (char_length(trim(label)) > 0)
);

insert into public.veterinary_care_type_catalog (code, label, sort_order) values
  ('GENERAL_CONSULT', 'Consulta general', 10),
  ('CONTROL', 'Control', 20),
  ('VACCINATION', 'Vacunación', 30),
  ('SURGERY', 'Cirugía', 40),
  ('EMERGENCY', 'Emergencia', 50),
  ('DENTISTRY', 'Odontología', 60),
  ('LABORATORY', 'Laboratorio', 70),
  ('DIAGNOSTIC_IMAGING', 'Diagnóstico por imagen', 80),
  ('HOSPITALIZATION', 'Hospitalización', 90),
  ('PREVENTIVE_CARE', 'Cuidado preventivo', 100),
  ('OTHER', 'Otro', 999)
on conflict (code) do nothing;

create table if not exists public.veterinary_patient_relationships (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  status text not null default 'ACTIVE',
  primary_professional_id uuid null references public.veterinary_professionals (id) on delete set null,
  first_seen_at timestamptz not null default timezone('utc', now()),
  last_care_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_patient_relationship_status_chk
    check (status = any (array['ACTIVE','INACTIVE']::text[]))
);

create unique index if not exists veterinary_patient_relationships_active_uniq
  on public.veterinary_patient_relationships (pet_id, clinic_id)
  where status = 'ACTIVE';

create index if not exists veterinary_patient_relationships_clinic_idx
  on public.veterinary_patient_relationships (clinic_id, status);
create index if not exists veterinary_patient_relationships_pet_idx
  on public.veterinary_patient_relationships (pet_id);

create table if not exists public.veterinary_professional_access_grants (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  granted_by_user_id uuid not null references public.users (id) on delete restrict,
  clinic_id uuid null references public.veterinary_clinic_profiles (id) on delete restrict,
  professional_id uuid null references public.veterinary_professionals (id) on delete restrict,
  purposes text[] not null default '{}'::text[],
  status text not null default 'ACTIVE',
  valid_from timestamptz not null default timezone('utc', now()),
  valid_until timestamptz,
  revoked_at timestamptz,
  revoked_by uuid null references public.users (id) on delete set null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_access_grant_status_chk
    check (status = any (array['ACTIVE','REVOKED','EXPIRED']::text[])),
  constraint veterinary_access_grant_target_chk
    check (clinic_id is not null or professional_id is not null),
  constraint veterinary_access_grant_purposes_chk
    check (
      purposes <@ array[
        'CURRENT_CARE','HISTORICAL_READ','DOCUMENTS','PASSPORT_PROPOSAL'
      ]::text[]
      and cardinality(purposes) > 0
    ),
  constraint veterinary_access_grant_validity_chk
    check (valid_until is null or valid_until > valid_from)
);

-- Simplified: one active grant per pet + clinic + professional scope (null professional = clinic-wide).
create unique index if not exists veterinary_access_grants_active_scope_uniq
  on public.veterinary_professional_access_grants (
    pet_id,
    coalesce(clinic_id, '00000000-0000-0000-0000-000000000000'::uuid),
    coalesce(professional_id, '00000000-0000-0000-0000-000000000000'::uuid)
  )
  where status = 'ACTIVE';

create index if not exists veterinary_access_grants_pet_idx
  on public.veterinary_professional_access_grants (pet_id, status);
create index if not exists veterinary_access_grants_clinic_idx
  on public.veterinary_professional_access_grants (clinic_id, status);

create table if not exists public.veterinary_professional_cares (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  professional_id uuid not null references public.veterinary_professionals (id) on delete restrict,
  appointment_id uuid null references public.veterinary_appointments (id) on delete set null,
  patient_relationship_id uuid null references public.veterinary_patient_relationships (id) on delete set null,
  care_type_code text not null references public.veterinary_care_type_catalog (code),
  care_type_label_snapshot text not null,
  reason text,
  weight_kg numeric(8,3),
  findings_summary text,
  clinical_notes text,
  observations text,
  status text not null default 'DRAFT',
  version integer not null default 1,
  supersedes_care_id uuid null references public.veterinary_professional_cares (id) on delete restrict,
  correction_reason text,
  created_by uuid not null references public.users (id) on delete restrict,
  finalized_by uuid null references public.users (id) on delete set null,
  finalized_at timestamptz,
  client_request_id text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_professional_care_status_chk
    check (status = any (array['DRAFT','FINALIZED','CORRECTED','VOID']::text[])),
  constraint veterinary_professional_care_version_chk check (version >= 1),
  constraint veterinary_professional_care_weight_chk
    check (weight_kg is null or (weight_kg > 0 and weight_kg <= 9999.999)),
  constraint veterinary_professional_care_label_chk
    check (char_length(trim(care_type_label_snapshot)) > 0)
);

create unique index if not exists veterinary_professional_cares_appointment_finalized_uniq
  on public.veterinary_professional_cares (appointment_id)
  where appointment_id is not null and status in ('FINALIZED','CORRECTED');

create unique index if not exists veterinary_professional_cares_client_request_uniq
  on public.veterinary_professional_cares (clinic_id, created_by, client_request_id)
  where client_request_id is not null;

create index if not exists veterinary_professional_cares_pet_idx
  on public.veterinary_professional_cares (pet_id, created_at desc);
create index if not exists veterinary_professional_cares_clinic_idx
  on public.veterinary_professional_cares (clinic_id, status, created_at desc);
create index if not exists veterinary_professional_cares_appointment_idx
  on public.veterinary_professional_cares (appointment_id);

create table if not exists public.veterinary_vaccination_records (
  id uuid primary key default gen_random_uuid(),
  care_id uuid not null references public.veterinary_professional_cares (id) on delete restrict,
  pet_id uuid not null references public.pets (id) on delete restrict,
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  professional_id uuid not null references public.veterinary_professionals (id) on delete restrict,
  vaccine_code text not null,
  vaccine_label_snapshot text not null,
  administered_at timestamptz not null,
  dose text,
  batch_number text,
  manufacturer text,
  next_due_at timestamptz,
  notes text,
  provenance text not null default 'LOADED_BY_PROFESSIONAL',
  created_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_vaccination_label_chk check (char_length(trim(vaccine_label_snapshot)) > 0),
  constraint veterinary_vaccination_code_chk check (char_length(trim(vaccine_code)) > 0),
  constraint veterinary_vaccination_provenance_chk
    check (provenance = any (array['LOADED_BY_PROFESSIONAL']::text[]))
);

create index if not exists veterinary_vaccination_records_care_idx
  on public.veterinary_vaccination_records (care_id);
create index if not exists veterinary_vaccination_records_pet_idx
  on public.veterinary_vaccination_records (pet_id, administered_at desc);

create table if not exists public.veterinary_professional_documents (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  care_id uuid null references public.veterinary_professional_cares (id) on delete set null,
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  uploaded_by uuid not null references public.users (id) on delete restrict,
  asset_ref text not null,
  document_type text not null,
  title text not null,
  visibility text not null default 'CLINIC_STAFF',
  provenance text not null default 'LOADED_BY_PROFESSIONAL',
  created_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_professional_document_asset_chk
    check (asset_ref ~* '^m05://'),
  constraint veterinary_professional_document_title_chk check (char_length(trim(title)) > 0),
  constraint veterinary_professional_document_type_chk
    check (document_type = any (array[
      'PDF','IMAGE','REPORT','CERTIFICATE','STUDY','INDICATION','OTHER'
    ]::text[])),
  constraint veterinary_professional_document_visibility_chk
    check (visibility = any (array[
      'CLINIC_STAFF','RESPONSIBLE_SHARED','PROFESSIONAL_ONLY'
    ]::text[])),
  constraint veterinary_professional_document_provenance_chk
    check (provenance = any (array['LOADED_BY_PROFESSIONAL']::text[]))
);

create index if not exists veterinary_professional_documents_pet_idx
  on public.veterinary_professional_documents (pet_id, created_at desc);
create index if not exists veterinary_professional_documents_care_idx
  on public.veterinary_professional_documents (care_id);
create index if not exists veterinary_professional_documents_clinic_idx
  on public.veterinary_professional_documents (clinic_id);

create table if not exists public.veterinary_follow_ups (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  care_id uuid not null references public.veterinary_professional_cares (id) on delete restrict,
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  professional_id uuid null references public.veterinary_professionals (id) on delete set null,
  follow_up_type_code text not null,
  status text not null default 'PENDING',
  due_at timestamptz,
  notes text,
  completed_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_follow_up_status_chk
    check (status = any (array[
      'PENDING','SCHEDULED','COMPLETED','CANCELLED','OVERDUE'
    ]::text[])),
  constraint veterinary_follow_up_type_chk check (char_length(trim(follow_up_type_code)) > 0)
);

create index if not exists veterinary_follow_ups_clinic_status_idx
  on public.veterinary_follow_ups (clinic_id, status, due_at);
create index if not exists veterinary_follow_ups_pet_idx
  on public.veterinary_follow_ups (pet_id, due_at);

create table if not exists public.veterinary_passport_update_proposals (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets (id) on delete restrict,
  passport_id uuid not null references public.pet_passports (id) on delete restrict,
  source_care_id uuid null references public.veterinary_professional_cares (id) on delete set null,
  source_vaccination_id uuid null references public.veterinary_vaccination_records (id) on delete set null,
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  proposed_by_professional_id uuid not null references public.veterinary_professionals (id) on delete restrict,
  proposal_type text not null,
  previous_value jsonb not null default '{}'::jsonb,
  proposed_value jsonb not null default '{}'::jsonb,
  status text not null default 'PENDING',
  decision_note text,
  decided_by uuid null references public.users (id) on delete set null,
  decided_at timestamptz,
  client_request_id text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_passport_proposal_type_chk
    check (proposal_type = any (array[
      'VACCINATION','WEIGHT','CONTROL_EVENT','HEALTH_DOCUMENT','OTHER'
    ]::text[])),
  constraint veterinary_passport_proposal_status_chk
    check (status = any (array[
      'PENDING','ACCEPTED','REJECTED','CANCELLED','SUPERSEDED'
    ]::text[])),
  constraint veterinary_passport_proposal_previous_chk
    check (jsonb_typeof(previous_value) = 'object'),
  constraint veterinary_passport_proposal_proposed_chk
    check (jsonb_typeof(proposed_value) = 'object')
);

create unique index if not exists veterinary_passport_proposals_pending_source_uniq
  on public.veterinary_passport_update_proposals (source_care_id, proposal_type)
  where status = 'PENDING' and source_care_id is not null;

create index if not exists veterinary_passport_proposals_passport_idx
  on public.veterinary_passport_update_proposals (passport_id, status);
create index if not exists veterinary_passport_proposals_pet_idx
  on public.veterinary_passport_update_proposals (pet_id, status);

create table if not exists public.veterinary_export_requests (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  pet_id uuid null references public.pets (id) on delete restrict,
  requested_by uuid not null references public.users (id) on delete restrict,
  status text not null default 'REQUESTED',
  export_format text not null default 'PDF',
  snapshot jsonb,
  completed_at timestamptz,
  client_request_id text not null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_export_status_chk
    check (status = any (array['REQUESTED','COMPLETED','FAILED']::text[])),
  constraint veterinary_export_format_chk check (export_format = 'PDF'),
  constraint veterinary_export_client_request_chk check (char_length(trim(client_request_id)) > 0)
);

create unique index if not exists veterinary_export_requests_client_request_uniq
  on public.veterinary_export_requests (client_request_id);

create index if not exists veterinary_export_requests_clinic_idx
  on public.veterinary_export_requests (clinic_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 2. Observability catalog M07
-- ---------------------------------------------------------------------------
insert into public.observability_event_catalog (
  event_key, module, category, default_severity, sensitivity, organization_scoped,
  retention_policy_key, remote_persistence_allowed, analytics_allowed,
  allowed_metadata_keys, required_metadata_keys
) values
('VETERINARY_CARE_GRANT_CHANGED','M28','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_CARE_CREATED','M28','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_CARE_FINALIZED','M28','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_CARE_CORRECTED','M28','AUDIT','NOTICE','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_DOCUMENT_UPLOADED','M28','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_PASSPORT_PROPOSAL_CREATED','M28','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_PASSPORT_PROPOSAL_DECIDED','M28','AUDIT','NOTICE','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_EXPORT_REQUESTED','M28','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_EXPORT_COMPLETED','M28','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[])
on conflict (event_key) do nothing;

-- ---------------------------------------------------------------------------
-- 3. Helpers (SECURITY DEFINER, search_path=public)
-- Reuses _m12_require_authenticated, _m12_require_org_perm, _m12_is_safe_media_ref,
-- m08_actor_has_active_responsibility from prior migrations.
-- ---------------------------------------------------------------------------
create or replace function public._m28_best_effort_audit(
  p_event_key text,
  p_action text,
  p_resource_type text,
  p_resource_id text,
  p_organization_id uuid default null,
  p_metadata jsonb default '{}'::jsonb
) returns void
language plpgsql security definer set search_path = public as $$
declare v_corr text := public.m07_new_correlation_id();
begin
  perform public.m07_best_effort_audit(
    p_event_key, p_action, 'SUCCESS', v_corr,
    p_resource_type, p_resource_id,
    coalesce(p_metadata, '{}'::jsonb) || jsonb_build_object(
      'result', 'SUCCESS', 'module', 'M28',
      'organization_id', p_organization_id
    )
  );
exception when others then null;
end;
$$;

create or replace function public._m28_normalize_purposes(p_purposes text[])
returns text[]
language plpgsql immutable as $$
declare
  v_item text;
  v_norm text;
  v_out text[] := '{}'::text[];
begin
  if p_purposes is null then return v_out; end if;
  foreach v_item in array p_purposes loop
    v_norm := upper(trim(coalesce(v_item, '')));
    if v_norm = '' then continue; end if;
    if v_norm not in ('CURRENT_CARE','HISTORICAL_READ','DOCUMENTS','PASSPORT_PROPOSAL') then
      raise exception 'VETERINARY_CARE_GRANT_INVALID';
    end if;
    if not (v_norm = any (v_out)) then
      v_out := array_append(v_out, v_norm);
    end if;
  end loop;
  if cardinality(v_out) = 0 then raise exception 'VETERINARY_CARE_GRANT_INVALID'; end if;
  return v_out;
end;
$$;

create or replace function public._m28_responsible_can_manage_grants(p_pet_id uuid)
returns void
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_ok boolean := false;
begin
  if p_pet_id is null then raise exception 'VETERINARY_CARE_PET_FORBIDDEN'; end if;
  if not exists (select 1 from public.pets p where p.id = p_pet_id) then
    raise exception 'VETERINARY_CARE_PET_NOT_FOUND';
  end if;
  begin
    v_ok := public.m08_actor_has_active_responsibility(p_pet_id, v_actor);
  exception when undefined_function then
    v_ok := false;
  end;
  if not coalesce(v_ok, false) then
    raise exception 'VETERINARY_CARE_GRANT_FORBIDDEN';
  end if;
end;
$$;

create or replace function public._m28_active_grant_allows(
  p_pet_id uuid,
  p_clinic_id uuid,
  p_purpose text
) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1
    from public.veterinary_professional_access_grants g
    where g.pet_id = p_pet_id
      and g.status = 'ACTIVE'
      and g.valid_from <= timezone('utc', now())
      and (g.valid_until is null or g.valid_until > timezone('utc', now()))
      and upper(trim(p_purpose)) = any (g.purposes)
      and (
        (g.clinic_id = p_clinic_id and g.professional_id is null)
        or g.clinic_id = p_clinic_id
        or exists (
          select 1
          from public.veterinary_clinic_professionals cp
          where cp.clinic_id = p_clinic_id
            and cp.professional_id = g.professional_id
            and cp.active
        )
      )
  );
$$;

create or replace function public._m28_require_care_read(p_clinic_id uuid, p_pet_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.read');
  if not (
    public._m28_active_grant_allows(p_pet_id, p_clinic_id, 'HISTORICAL_READ')
    or public._m28_active_grant_allows(p_pet_id, p_clinic_id, 'CURRENT_CARE')
  ) then
    raise exception 'VETERINARY_CARE_GRANT_REVOKED';
  end if;
  return v_clinic;
end;
$$;

create or replace function public._m28_require_care_write(p_clinic_id uuid, p_pet_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.write');
  if not public._m28_active_grant_allows(p_pet_id, p_clinic_id, 'CURRENT_CARE') then
    raise exception 'VETERINARY_CARE_GRANT_REVOKED';
  end if;
  return v_clinic;
end;
$$;

create or replace function public._m28_resolve_actor_professional(p_clinic_id uuid, p_professional_id uuid default null)
returns uuid
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_prof uuid;
begin
  if p_professional_id is not null then
    if not exists (
      select 1 from public.veterinary_clinic_professionals cp
      where cp.clinic_id = p_clinic_id and cp.professional_id = p_professional_id and cp.active
    ) then
      raise exception 'VETERINARY_PROFESSIONAL_NOT_LINKED';
    end if;
    return p_professional_id;
  end if;
  select vp.id into v_prof
  from public.veterinary_professionals vp
  join public.veterinary_clinic_professionals cp on cp.professional_id = vp.id
  where vp.user_id = v_actor and cp.clinic_id = p_clinic_id and cp.active and vp.status = 'ACTIVE'
  limit 1;
  if v_prof is null then raise exception 'VETERINARY_PROFESSIONAL_NOT_LINKED'; end if;
  return v_prof;
end;
$$;

create or replace function public._m28_assert_pet_care_eligible(p_pet_id uuid)
returns void
language plpgsql stable security definer set search_path = public as $$
declare v_status text;
begin
  select p.status into v_status from public.pets p where p.id = p_pet_id;
  if not found then raise exception 'VETERINARY_CARE_PET_NOT_FOUND'; end if;
  if v_status = 'DECEASED' then raise exception 'VETERINARY_CARE_PET_NOT_ELIGIBLE'; end if;
end;
$$;

create or replace function public._m28_upsert_patient_relationship_internal(
  p_pet_id uuid,
  p_clinic_id uuid,
  p_primary_professional_id uuid default null
) returns public.veterinary_patient_relationships
language plpgsql security definer set search_path = public as $$
declare v_row public.veterinary_patient_relationships;
begin
  select * into v_row
  from public.veterinary_patient_relationships
  where pet_id = p_pet_id and clinic_id = p_clinic_id and status = 'ACTIVE'
  for update;
  if found then
    if p_primary_professional_id is not null then
      update public.veterinary_patient_relationships set
        primary_professional_id = p_primary_professional_id,
        updated_at = timezone('utc', now())
      where id = v_row.id returning * into v_row;
    end if;
    return v_row;
  end if;
  insert into public.veterinary_patient_relationships (
    pet_id, clinic_id, status, primary_professional_id, first_seen_at
  ) values (
    p_pet_id, p_clinic_id, 'ACTIVE', p_primary_professional_id, timezone('utc', now())
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public._m28_apply_passport_proposal(p_proposal_id uuid)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_prop public.veterinary_passport_update_proposals;
  v_passport public.pet_passports;
  v_clinic public.veterinary_clinic_profiles;
  v_cred public.pet_passport_credentials;
  v_actor uuid := public._m12_require_authenticated();
  v_title text;
  v_issued timestamptz;
  v_expires timestamptz;
begin
  select * into v_prop from public.veterinary_passport_update_proposals where id = p_proposal_id for update;
  if not found then raise exception 'VETERINARY_PASSPORT_PROPOSAL_NOT_FOUND'; end if;
  if v_prop.status <> 'PENDING' then raise exception 'VETERINARY_PASSPORT_PROPOSAL_ALREADY_RESOLVED'; end if;

  select * into v_passport from public.pet_passports where id = v_prop.passport_id;
  if not found then raise exception 'PASSPORT_NOT_FOUND'; end if;
  if v_passport.status not in ('DRAFT','ACTIVE') then raise exception 'INVALID_PASSPORT_STATUS'; end if;

  select * into v_clinic from public.veterinary_clinic_profiles where id = v_prop.clinic_id;

  if v_prop.proposal_type = 'VACCINATION' then
    v_title := coalesce(v_prop.proposed_value->>'title', v_prop.proposed_value->>'vaccine_label', 'Vacunación');
    v_issued := coalesce(
      nullif(v_prop.proposed_value->>'administered_at', '')::timestamptz,
      timezone('utc', now())
    );
    v_expires := nullif(v_prop.proposed_value->>'next_due_at', '')::timestamptz;
    insert into public.pet_passport_credentials (
      passport_id, type, title,
      issuer_organization_id, issuer_professional_id,
      issued_at, expires_at, status, visibility,
      media_refs, note_private, created_by, created_at, updated_at
    ) values (
      v_prop.passport_id, 'VACCINATION_ATTESTATION', v_title,
      v_clinic.organization_id, v_prop.proposed_by_professional_id,
      v_issued, v_expires, 'DRAFT', 'PRIVATE',
      coalesce(
        case when v_prop.proposed_value ? 'media_refs'
          then array(select jsonb_array_elements_text(v_prop.proposed_value->'media_refs'))
          else '{}'::text[] end,
        '{}'::text[]
      ),
      nullif(trim(coalesce(v_prop.proposed_value->>'notes', '')), ''),
      v_actor, timezone('utc', now()), timezone('utc', now())
    ) returning * into v_cred;
    return v_cred.id;
  end if;

  -- Non-vaccination proposals: no direct passport mutation without responsible (Pilot Minimum).
  return null;
end;
$$;

create or replace function public._m28_build_export_snapshot(
  p_clinic_id uuid,
  p_pet_id uuid default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
  v_disclaimer text := 'Registro operativo LeoVer — no constituye historia clínica oficial.';
begin
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  return jsonb_build_object(
    'disclaimer', v_disclaimer,
    'exported_at', timezone('utc', now()),
    'clinic', jsonb_build_object('id', v_clinic.id, 'display_name', v_clinic.display_name),
    'pet_id', p_pet_id,
    'cares', coalesce((
      select jsonb_agg(to_jsonb(c) order by c.created_at desc)
      from public.veterinary_professional_cares c
      where c.clinic_id = p_clinic_id
        and (p_pet_id is null or c.pet_id = p_pet_id)
        and c.status in ('FINALIZED','CORRECTED')
    ), '[]'::jsonb),
    'vaccinations', coalesce((
      select jsonb_agg(to_jsonb(v) order by v.administered_at desc)
      from public.veterinary_vaccination_records v
      where v.clinic_id = p_clinic_id
        and (p_pet_id is null or v.pet_id = p_pet_id)
    ), '[]'::jsonb),
    'documents', coalesce((
      select jsonb_agg(to_jsonb(d) order by d.created_at desc)
      from public.veterinary_professional_documents d
      where d.clinic_id = p_clinic_id
        and (p_pet_id is null or d.pet_id = p_pet_id)
    ), '[]'::jsonb),
    'follow_ups', coalesce((
      select jsonb_agg(to_jsonb(f) order by f.due_at nulls last)
      from public.veterinary_follow_ups f
      where f.clinic_id = p_clinic_id
        and (p_pet_id is null or f.pet_id = p_pet_id)
    ), '[]'::jsonb)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RLS + table grants (RPC-only pattern like M12 047)
-- ---------------------------------------------------------------------------
alter table public.veterinary_care_type_catalog enable row level security;
alter table public.veterinary_patient_relationships enable row level security;
alter table public.veterinary_professional_access_grants enable row level security;
alter table public.veterinary_professional_cares enable row level security;
alter table public.veterinary_vaccination_records enable row level security;
alter table public.veterinary_professional_documents enable row level security;
alter table public.veterinary_follow_ups enable row level security;
alter table public.veterinary_passport_update_proposals enable row level security;
alter table public.veterinary_export_requests enable row level security;

do $$ declare t text; begin
  foreach t in array array[
    'veterinary_care_type_catalog',
    'veterinary_patient_relationships',
    'veterinary_professional_access_grants',
    'veterinary_professional_cares',
    'veterinary_vaccination_records',
    'veterinary_professional_documents',
    'veterinary_follow_ups',
    'veterinary_passport_update_proposals',
    'veterinary_export_requests'
  ] loop
    execute format('drop policy if exists %I_select_m28 on public.%I', t, t);
    execute format('drop policy if exists %I_ins_m28 on public.%I', t, t);
    execute format('drop policy if exists %I_upd_m28 on public.%I', t, t);
    execute format('drop policy if exists %I_del_m28 on public.%I', t, t);
    execute format(
      'create policy %I_select_m28 on public.%I for select to authenticated using (false)',
      t, t
    );
    execute format(
      'create policy %I_ins_m28 on public.%I for insert to authenticated with check (false)',
      t, t
    );
    execute format(
      'create policy %I_upd_m28 on public.%I for update to authenticated using (false)',
      t, t
    );
    execute format(
      'create policy %I_del_m28 on public.%I for delete to authenticated using (false)',
      t, t
    );
    execute format('revoke all privileges on table public.%I from public', t);
    execute format('revoke all privileges on table public.%I from anon', t);
    execute format('revoke all privileges on table public.%I from authenticated', t);
    execute format('grant all on table public.%I to service_role', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- 5. RPCs — grants and patient relationships
-- ---------------------------------------------------------------------------
create or replace function public.m28_grant_professional_access(
  p_pet_id uuid,
  p_clinic_id uuid default null,
  p_professional_id uuid default null,
  p_purposes text[] default array['CURRENT_CARE']::text[],
  p_valid_from timestamptz default null,
  p_valid_until timestamptz default null
) returns public.veterinary_professional_access_grants
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_purposes text[] := public._m28_normalize_purposes(p_purposes);
  v_row public.veterinary_professional_access_grants;
  v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m28_responsible_can_manage_grants(p_pet_id);
  if p_clinic_id is null and p_professional_id is null then
    raise exception 'VETERINARY_CARE_GRANT_INVALID';
  end if;
  if p_clinic_id is not null then
    select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
    if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  end if;
  if p_professional_id is not null and not exists (
    select 1 from public.veterinary_professionals vp where vp.id = p_professional_id
  ) then
    raise exception 'VETERINARY_PROFESSIONAL_NOT_FOUND';
  end if;

  insert into public.veterinary_professional_access_grants (
    pet_id, granted_by_user_id, clinic_id, professional_id, purposes, status,
    valid_from, valid_until
  ) values (
    p_pet_id, v_actor, p_clinic_id, p_professional_id, v_purposes, 'ACTIVE',
    coalesce(p_valid_from, timezone('utc', now())), p_valid_until
  ) returning * into v_row;

  perform public._m28_best_effort_audit(
    'VETERINARY_CARE_GRANT_CHANGED', 'GRANT', 'veterinary_access_grant', v_row.id::text,
    v_clinic.organization_id,
    jsonb_build_object('pet_id', p_pet_id, 'status', 'ACTIVE')
  );
  return v_row;
exception when unique_violation then
  raise exception 'VETERINARY_CARE_GRANT_DUPLICATE';
end;
$$;

create or replace function public.m28_revoke_professional_access(p_grant_id uuid)
returns public.veterinary_professional_access_grants
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_professional_access_grants;
  v_clinic public.veterinary_clinic_profiles;
begin
  select * into v_row from public.veterinary_professional_access_grants where id = p_grant_id for update;
  if not found then raise exception 'VETERINARY_CARE_GRANT_NOT_FOUND'; end if;
  perform public._m28_responsible_can_manage_grants(v_row.pet_id);
  if v_row.status <> 'ACTIVE' then return v_row; end if;

  update public.veterinary_professional_access_grants set
    status = 'REVOKED',
    revoked_at = timezone('utc', now()),
    revoked_by = v_actor,
    updated_at = timezone('utc', now())
  where id = p_grant_id returning * into v_row;

  if v_row.clinic_id is not null then
    select * into v_clinic from public.veterinary_clinic_profiles where id = v_row.clinic_id;
  end if;

  perform public._m28_best_effort_audit(
    'VETERINARY_CARE_GRANT_CHANGED', 'REVOKE', 'veterinary_access_grant', v_row.id::text,
    v_clinic.organization_id,
    jsonb_build_object('pet_id', v_row.pet_id, 'status', 'REVOKED')
  );
  return v_row;
end;
$$;

create or replace function public.m28_list_my_grants_for_pet(p_pet_id uuid)
returns setof public.veterinary_professional_access_grants
language plpgsql stable security definer set search_path = public as $$
begin
  perform public._m28_responsible_can_manage_grants(p_pet_id);
  return query
  select * from public.veterinary_professional_access_grants
  where pet_id = p_pet_id
  order by created_at desc;
end;
$$;

create or replace function public.m28_list_grants_for_responsible(p_pet_id uuid)
returns setof public.veterinary_professional_access_grants
language plpgsql stable security definer set search_path = public as $$
begin
  return query select * from public.m28_list_my_grants_for_pet(p_pet_id);
end;
$$;

create or replace function public.m28_upsert_patient_relationship(
  p_clinic_id uuid,
  p_pet_id uuid,
  p_primary_professional_id uuid default null
) returns public.veterinary_patient_relationships
language plpgsql security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
begin
  v_clinic := public._m28_require_care_write(p_clinic_id, p_pet_id);
  perform public._m28_assert_pet_care_eligible(p_pet_id);
  if p_primary_professional_id is not null then
    perform public._m28_resolve_actor_professional(p_clinic_id, p_primary_professional_id);
  end if;
  return public._m28_upsert_patient_relationship_internal(p_pet_id, p_clinic_id, p_primary_professional_id);
end;
$$;

create or replace function public.m28_list_clinic_patients(p_clinic_id uuid)
returns setof public.veterinary_patient_relationships
language plpgsql stable security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.read');

  return query
  select pr.*
  from public.veterinary_patient_relationships pr
  where pr.clinic_id = p_clinic_id
    and pr.status = 'ACTIVE'
    and (
      public._m28_active_grant_allows(pr.pet_id, p_clinic_id, 'HISTORICAL_READ')
      or public._m28_active_grant_allows(pr.pet_id, p_clinic_id, 'CURRENT_CARE')
    )
  order by coalesce(pr.last_care_at, pr.first_seen_at) desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPCs — professional cares
-- ---------------------------------------------------------------------------
create or replace function public.m28_create_care_draft(
  p_clinic_id uuid,
  p_pet_id uuid,
  p_care_type_code text,
  p_reason text default null,
  p_appointment_id uuid default null,
  p_professional_id uuid default null,
  p_client_request_id text default null
) returns public.veterinary_professional_cares
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_clinic public.veterinary_clinic_profiles;
  v_prof uuid;
  v_catalog public.veterinary_care_type_catalog;
  v_rel public.veterinary_patient_relationships;
  v_existing public.veterinary_professional_cares;
  v_row public.veterinary_professional_cares;
  v_code text := upper(trim(coalesce(p_care_type_code, '')));
begin
  v_clinic := public._m28_require_care_write(p_clinic_id, p_pet_id);
  perform public._m28_assert_pet_care_eligible(p_pet_id);
  v_prof := public._m28_resolve_actor_professional(p_clinic_id, p_professional_id);

  if nullif(trim(coalesce(p_client_request_id, '')), '') is not null then
    select * into v_existing
    from public.veterinary_professional_cares
    where clinic_id = p_clinic_id
      and created_by = v_actor
      and client_request_id = trim(p_client_request_id);
    if found then return v_existing; end if;
  end if;

  select * into v_catalog from public.veterinary_care_type_catalog where code = v_code and active;
  if not found then raise exception 'VETERINARY_CARE_TYPE_INVALID'; end if;

  if p_appointment_id is not null then
    if not exists (
      select 1 from public.veterinary_appointments a
      where a.id = p_appointment_id and a.clinic_id = p_clinic_id and a.pet_id = p_pet_id
    ) then
      raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND';
    end if;
  end if;

  v_rel := public._m28_upsert_patient_relationship_internal(p_pet_id, p_clinic_id, v_prof);

  insert into public.veterinary_professional_cares (
    pet_id, clinic_id, professional_id, appointment_id, patient_relationship_id,
    care_type_code, care_type_label_snapshot, reason, status, created_by, client_request_id
  ) values (
    p_pet_id, p_clinic_id, v_prof, p_appointment_id, v_rel.id,
    v_catalog.code, v_catalog.label, nullif(trim(coalesce(p_reason, '')), ''),
    'DRAFT', v_actor, nullif(trim(coalesce(p_client_request_id, '')), '')
  ) returning * into v_row;

  perform public._m28_best_effort_audit(
    'VETERINARY_CARE_CREATED', 'CREATE', 'veterinary_professional_care', v_row.id::text,
    v_clinic.organization_id, jsonb_build_object('pet_id', p_pet_id, 'status', 'DRAFT')
  );
  return v_row;
exception when unique_violation then
  if nullif(trim(coalesce(p_client_request_id, '')), '') is not null then
    select * into v_existing
    from public.veterinary_professional_cares
    where clinic_id = p_clinic_id and created_by = v_actor and client_request_id = trim(p_client_request_id);
    if found then return v_existing; end if;
  end if;
  raise;
end;
$$;

create or replace function public.m28_update_care_draft(
  p_care_id uuid,
  p_reason text default null,
  p_weight_kg numeric default null,
  p_findings_summary text default null,
  p_clinical_notes text default null,
  p_observations text default null
) returns public.veterinary_professional_cares
language plpgsql security definer set search_path = public as $$
declare
  v_row public.veterinary_professional_cares;
begin
  select * into v_row from public.veterinary_professional_cares where id = p_care_id for update;
  if not found then raise exception 'VETERINARY_CARE_NOT_FOUND'; end if;
  perform public._m28_require_care_write(v_row.clinic_id, v_row.pet_id);
  if v_row.status <> 'DRAFT' then raise exception 'VETERINARY_CARE_INVALID_TRANSITION'; end if;
  if p_weight_kg is not null and (p_weight_kg <= 0 or p_weight_kg > 9999.999) then
    raise exception 'VETERINARY_CARE_INVALID';
  end if;

  update public.veterinary_professional_cares set
    reason = coalesce(nullif(trim(coalesce(p_reason, '')), ''), reason),
    weight_kg = coalesce(p_weight_kg, weight_kg),
    findings_summary = coalesce(nullif(trim(coalesce(p_findings_summary, '')), ''), findings_summary),
    clinical_notes = coalesce(nullif(trim(coalesce(p_clinical_notes, '')), ''), clinical_notes),
    observations = coalesce(nullif(trim(coalesce(p_observations, '')), ''), observations),
    updated_at = timezone('utc', now())
  where id = p_care_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m28_finalize_care(
  p_care_id uuid,
  p_appointment_id uuid default null,
  p_client_request_id text default null
) returns public.veterinary_professional_cares
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_professional_cares;
  v_clinic public.veterinary_clinic_profiles;
  v_appt_id uuid := coalesce(p_appointment_id, null);
begin
  select * into v_row from public.veterinary_professional_cares where id = p_care_id for update;
  if not found then raise exception 'VETERINARY_CARE_NOT_FOUND'; end if;
  v_clinic := public._m28_require_care_write(v_row.clinic_id, v_row.pet_id);

  if v_row.status = 'FINALIZED' then return v_row; end if;
  if v_row.status <> 'DRAFT' then raise exception 'VETERINARY_CARE_INVALID_TRANSITION'; end if;

  v_appt_id := coalesce(v_appt_id, v_row.appointment_id);
  if v_appt_id is not null then
    if exists (
      select 1 from public.veterinary_professional_cares c
      where c.appointment_id = v_appt_id
        and c.id <> v_row.id
        and c.status in ('FINALIZED','CORRECTED')
    ) then
      raise exception 'VETERINARY_CARE_DUPLICATE';
    end if;
    if not exists (
      select 1 from public.veterinary_appointments a
      where a.id = v_appt_id and a.clinic_id = v_row.clinic_id and a.pet_id = v_row.pet_id
    ) then
      raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND';
    end if;
  end if;

  update public.veterinary_professional_cares set
    appointment_id = v_appt_id,
    status = 'FINALIZED',
    finalized_by = v_actor,
    finalized_at = timezone('utc', now()),
    client_request_id = coalesce(nullif(trim(coalesce(p_client_request_id, '')), ''), client_request_id),
    updated_at = timezone('utc', now())
  where id = p_care_id returning * into v_row;

  update public.veterinary_patient_relationships set
    last_care_at = timezone('utc', now()),
    updated_at = timezone('utc', now())
  where id = v_row.patient_relationship_id;

  perform public._m28_best_effort_audit(
    'VETERINARY_CARE_FINALIZED', 'FINALIZE', 'veterinary_professional_care', v_row.id::text,
    v_clinic.organization_id, jsonb_build_object('pet_id', v_row.pet_id, 'appointment_id', v_appt_id)
  );
  return v_row;
exception when unique_violation then
  raise exception 'VETERINARY_CARE_DUPLICATE';
end;
$$;

create or replace function public.m28_supersede_care(
  p_care_id uuid,
  p_correction_reason text,
  p_reason text default null,
  p_weight_kg numeric default null,
  p_findings_summary text default null,
  p_clinical_notes text default null,
  p_observations text default null
) returns public.veterinary_professional_cares
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_orig public.veterinary_professional_cares;
  v_clinic public.veterinary_clinic_profiles;
  v_new public.veterinary_professional_cares;
begin
  select * into v_orig from public.veterinary_professional_cares where id = p_care_id for update;
  if not found then raise exception 'VETERINARY_CARE_NOT_FOUND'; end if;
  v_clinic := public._m28_require_care_write(v_orig.clinic_id, v_orig.pet_id);
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.correct');
  if char_length(trim(coalesce(p_correction_reason, ''))) = 0 then
    raise exception 'VETERINARY_CARE_CORRECTION_REASON_REQUIRED';
  end if;
  if v_orig.status not in ('FINALIZED','CORRECTED') then
    raise exception 'VETERINARY_CARE_INVALID_TRANSITION';
  end if;

  update public.veterinary_professional_cares set
    status = 'CORRECTED',
    updated_at = timezone('utc', now())
  where id = v_orig.id;

  insert into public.veterinary_professional_cares (
    pet_id, clinic_id, professional_id, appointment_id, patient_relationship_id,
    care_type_code, care_type_label_snapshot, reason, weight_kg, findings_summary,
    clinical_notes, observations, status, version, supersedes_care_id, correction_reason,
    created_by, finalized_by, finalized_at
  ) values (
    v_orig.pet_id, v_orig.clinic_id, v_orig.professional_id, v_orig.appointment_id, v_orig.patient_relationship_id,
    v_orig.care_type_code, v_orig.care_type_label_snapshot,
    coalesce(nullif(trim(coalesce(p_reason, '')), ''), v_orig.reason),
    coalesce(p_weight_kg, v_orig.weight_kg),
    coalesce(nullif(trim(coalesce(p_findings_summary, '')), ''), v_orig.findings_summary),
    coalesce(nullif(trim(coalesce(p_clinical_notes, '')), ''), v_orig.clinical_notes),
    coalesce(nullif(trim(coalesce(p_observations, '')), ''), v_orig.observations),
    'FINALIZED', v_orig.version + 1, v_orig.id, trim(p_correction_reason),
    v_actor, v_actor, timezone('utc', now())
  ) returning * into v_new;

  perform public._m28_best_effort_audit(
    'VETERINARY_CARE_CORRECTED', 'SUPERSEDE', 'veterinary_professional_care', v_new.id::text,
    v_clinic.organization_id,
    jsonb_build_object('supersedes_care_id', v_orig.id, 'pet_id', v_orig.pet_id)
  );
  return v_new;
end;
$$;

create or replace function public.m28_get_care(p_care_id uuid)
returns public.veterinary_professional_cares
language plpgsql stable security definer set search_path = public as $$
declare v_row public.veterinary_professional_cares;
begin
  select * into v_row from public.veterinary_professional_cares where id = p_care_id;
  if not found then raise exception 'VETERINARY_CARE_NOT_FOUND'; end if;
  perform public._m28_require_care_read(v_row.clinic_id, v_row.pet_id);
  return v_row;
end;
$$;

create or replace function public.m28_list_pet_cares(
  p_clinic_id uuid,
  p_pet_id uuid
) returns setof public.veterinary_professional_cares
language plpgsql stable security definer set search_path = public as $$
begin
  perform public._m28_require_care_read(p_clinic_id, p_pet_id);
  return query
  select * from public.veterinary_professional_cares
  where clinic_id = p_clinic_id and pet_id = p_pet_id
  order by created_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. RPCs — vaccinations, documents, follow-ups
-- ---------------------------------------------------------------------------
create or replace function public.m28_create_vaccination_record(
  p_care_id uuid,
  p_vaccine_code text,
  p_vaccine_label_snapshot text,
  p_administered_at timestamptz,
  p_dose text default null,
  p_batch_number text default null,
  p_manufacturer text default null,
  p_next_due_at timestamptz default null,
  p_notes text default null
) returns public.veterinary_vaccination_records
language plpgsql security definer set search_path = public as $$
declare
  v_care public.veterinary_professional_cares;
  v_row public.veterinary_vaccination_records;
begin
  select * into v_care from public.veterinary_professional_cares where id = p_care_id;
  if not found then raise exception 'VETERINARY_CARE_NOT_FOUND'; end if;
  perform public._m28_require_care_write(v_care.clinic_id, v_care.pet_id);
  if v_care.status = 'VOID' then raise exception 'VETERINARY_CARE_INVALID_TRANSITION'; end if;
  if char_length(trim(coalesce(p_vaccine_code, ''))) = 0
     or char_length(trim(coalesce(p_vaccine_label_snapshot, ''))) = 0
     or p_administered_at is null then
    raise exception 'VETERINARY_VACCINATION_INVALID';
  end if;

  insert into public.veterinary_vaccination_records (
    care_id, pet_id, clinic_id, professional_id,
    vaccine_code, vaccine_label_snapshot, administered_at,
    dose, batch_number, manufacturer, next_due_at, notes, provenance
  ) values (
    v_care.id, v_care.pet_id, v_care.clinic_id, v_care.professional_id,
    upper(trim(p_vaccine_code)), trim(p_vaccine_label_snapshot), p_administered_at,
    nullif(trim(coalesce(p_dose, '')), ''),
    nullif(trim(coalesce(p_batch_number, '')), ''),
    nullif(trim(coalesce(p_manufacturer, '')), ''),
    p_next_due_at,
    nullif(trim(coalesce(p_notes, '')), ''),
    'LOADED_BY_PROFESSIONAL'
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m28_create_professional_document(
  p_clinic_id uuid,
  p_pet_id uuid,
  p_asset_ref text,
  p_document_type text,
  p_title text,
  p_care_id uuid default null,
  p_visibility text default 'CLINIC_STAFF'
) returns public.veterinary_professional_documents
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_clinic public.veterinary_clinic_profiles;
  v_care public.veterinary_professional_cares;
  v_row public.veterinary_professional_documents;
  v_type text := upper(trim(coalesce(p_document_type, '')));
  v_vis text := upper(trim(coalesce(p_visibility, 'CLINIC_STAFF')));
begin
  v_clinic := public._m28_require_care_write(p_clinic_id, p_pet_id);
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.document.upload');
  if not public._m12_is_safe_media_ref(p_asset_ref) or p_asset_ref !~* '^m05://' then
    raise exception 'VETERINARY_DOCUMENT_INVALID';
  end if;
  if char_length(trim(coalesce(p_title, ''))) = 0 then raise exception 'VETERINARY_DOCUMENT_INVALID'; end if;
  if v_type not in ('PDF','IMAGE','REPORT','CERTIFICATE','STUDY','INDICATION','OTHER') then
    raise exception 'VETERINARY_DOCUMENT_INVALID';
  end if;
  if v_vis not in ('CLINIC_STAFF','RESPONSIBLE_SHARED','PROFESSIONAL_ONLY') then
    raise exception 'VETERINARY_DOCUMENT_INVALID';
  end if;
  if p_care_id is not null then
    select * into v_care from public.veterinary_professional_cares where id = p_care_id;
    if not found or v_care.clinic_id <> p_clinic_id or v_care.pet_id <> p_pet_id then
      raise exception 'VETERINARY_CARE_NOT_FOUND';
    end if;
  end if;

  insert into public.veterinary_professional_documents (
    pet_id, care_id, clinic_id, uploaded_by, asset_ref, document_type, title, visibility, provenance
  ) values (
    p_pet_id, p_care_id, p_clinic_id, v_actor, trim(p_asset_ref), v_type, trim(p_title), v_vis,
    'LOADED_BY_PROFESSIONAL'
  ) returning * into v_row;

  perform public._m28_best_effort_audit(
    'VETERINARY_DOCUMENT_UPLOADED', 'CREATE', 'veterinary_professional_document', v_row.id::text,
    v_clinic.organization_id, jsonb_build_object('pet_id', p_pet_id)
  );
  return v_row;
end;
$$;

create or replace function public.m28_create_follow_up(
  p_care_id uuid,
  p_follow_up_type_code text,
  p_due_at timestamptz default null,
  p_notes text default null,
  p_professional_id uuid default null
) returns public.veterinary_follow_ups
language plpgsql security definer set search_path = public as $$
declare
  v_care public.veterinary_professional_cares;
  v_prof uuid;
  v_row public.veterinary_follow_ups;
  v_status text := case when p_due_at is null then 'PENDING' else 'SCHEDULED' end;
begin
  select * into v_care from public.veterinary_professional_cares where id = p_care_id;
  if not found then raise exception 'VETERINARY_CARE_NOT_FOUND'; end if;
  perform public._m28_require_care_write(v_care.clinic_id, v_care.pet_id);
  if char_length(trim(coalesce(p_follow_up_type_code, ''))) = 0 then
    raise exception 'VETERINARY_FOLLOW_UP_INVALID';
  end if;
  v_prof := case
    when p_professional_id is not null then public._m28_resolve_actor_professional(v_care.clinic_id, p_professional_id)
    else v_care.professional_id
  end;

  insert into public.veterinary_follow_ups (
    pet_id, care_id, clinic_id, professional_id, follow_up_type_code, status, due_at, notes
  ) values (
    v_care.pet_id, v_care.id, v_care.clinic_id, v_prof,
    upper(trim(p_follow_up_type_code)), v_status, p_due_at,
    nullif(trim(coalesce(p_notes, '')), '')
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m28_update_follow_up_status(
  p_follow_up_id uuid,
  p_status text,
  p_notes text default null
) returns public.veterinary_follow_ups
language plpgsql security definer set search_path = public as $$
declare
  v_row public.veterinary_follow_ups;
  v_status text := upper(trim(coalesce(p_status, '')));
begin
  select * into v_row from public.veterinary_follow_ups where id = p_follow_up_id for update;
  if not found then raise exception 'VETERINARY_FOLLOW_UP_NOT_FOUND'; end if;
  perform public._m28_require_care_write(v_row.clinic_id, v_row.pet_id);
  if v_status not in ('PENDING','SCHEDULED','COMPLETED','CANCELLED','OVERDUE') then
    raise exception 'VETERINARY_FOLLOW_UP_INVALID';
  end if;

  update public.veterinary_follow_ups set
    status = v_status,
    notes = coalesce(nullif(trim(coalesce(p_notes, '')), ''), notes),
    completed_at = case when v_status = 'COMPLETED' then timezone('utc', now()) else completed_at end,
    updated_at = timezone('utc', now())
  where id = p_follow_up_id returning * into v_row;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. RPCs — passport proposals
-- ---------------------------------------------------------------------------
create or replace function public.m28_create_passport_update_proposal(
  p_passport_id uuid,
  p_proposal_type text,
  p_proposed_value jsonb,
  p_source_care_id uuid default null,
  p_source_vaccination_id uuid default null,
  p_previous_value jsonb default '{}'::jsonb,
  p_client_request_id text default null
) returns public.veterinary_passport_update_proposals
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_passport public.pet_passports;
  v_care public.veterinary_professional_cares;
  v_vacc public.veterinary_vaccination_records;
  v_clinic public.veterinary_clinic_profiles;
  v_prof uuid;
  v_type text := upper(trim(coalesce(p_proposal_type, '')));
  v_row public.veterinary_passport_update_proposals;
begin
  select * into v_passport from public.pet_passports where id = p_passport_id;
  if not found then raise exception 'PASSPORT_NOT_FOUND'; end if;

  if p_source_care_id is not null then
    select * into v_care from public.veterinary_professional_cares where id = p_source_care_id;
    if not found then raise exception 'VETERINARY_CARE_NOT_FOUND'; end if;
    v_clinic := public._m28_require_care_read(v_care.clinic_id, v_care.pet_id);
    perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.passport.propose');
    if not public._m28_active_grant_allows(v_care.pet_id, v_care.clinic_id, 'PASSPORT_PROPOSAL') then
      raise exception 'VETERINARY_CARE_GRANT_REVOKED';
    end if;
    v_prof := v_care.professional_id;
  elsif p_source_vaccination_id is not null then
    select * into v_vacc from public.veterinary_vaccination_records where id = p_source_vaccination_id;
    if not found then raise exception 'VETERINARY_VACCINATION_NOT_FOUND'; end if;
    select * into v_care from public.veterinary_professional_cares where id = v_vacc.care_id;
    v_clinic := public._m28_require_care_read(v_vacc.clinic_id, v_vacc.pet_id);
    perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.passport.propose');
    if not public._m28_active_grant_allows(v_vacc.pet_id, v_vacc.clinic_id, 'PASSPORT_PROPOSAL') then
      raise exception 'VETERINARY_CARE_GRANT_REVOKED';
    end if;
    v_prof := v_vacc.professional_id;
  else
    raise exception 'VETERINARY_PASSPORT_PROPOSAL_INVALID';
  end if;

  if v_passport.pet_id <> coalesce(v_care.pet_id, v_vacc.pet_id) then
    raise exception 'VETERINARY_PASSPORT_PROPOSAL_INVALID';
  end if;
  if v_type not in ('VACCINATION','WEIGHT','CONTROL_EVENT','HEALTH_DOCUMENT','OTHER') then
    raise exception 'VETERINARY_PASSPORT_PROPOSAL_INVALID';
  end if;
  if jsonb_typeof(p_proposed_value) <> 'object' then raise exception 'VETERINARY_PASSPORT_PROPOSAL_INVALID'; end if;

  insert into public.veterinary_passport_update_proposals (
    pet_id, passport_id, source_care_id, source_vaccination_id, clinic_id,
    proposed_by_professional_id, proposal_type, previous_value, proposed_value,
    status, client_request_id
  ) values (
    v_passport.pet_id, p_passport_id, p_source_care_id, p_source_vaccination_id, v_clinic.id,
    v_prof, v_type, coalesce(p_previous_value, '{}'::jsonb), p_proposed_value,
    'PENDING', nullif(trim(coalesce(p_client_request_id, '')), '')
  ) returning * into v_row;

  perform public._m28_best_effort_audit(
    'VETERINARY_PASSPORT_PROPOSAL_CREATED', 'CREATE', 'veterinary_passport_proposal', v_row.id::text,
    v_clinic.organization_id, jsonb_build_object('passport_id', p_passport_id, 'proposal_type', v_type)
  );
  return v_row;
exception when unique_violation then
  raise exception 'VETERINARY_PASSPORT_PROPOSAL_DUPLICATE';
end;
$$;

create or replace function public.m28_list_passport_update_proposals_for_responsible(p_pet_id uuid)
returns setof public.veterinary_passport_update_proposals
language plpgsql stable security definer set search_path = public as $$
begin
  perform public._m28_responsible_can_manage_grants(p_pet_id);
  return query
  select * from public.veterinary_passport_update_proposals
  where pet_id = p_pet_id
  order by created_at desc;
end;
$$;

create or replace function public.m28_list_clinic_proposals(p_clinic_id uuid)
returns setof public.veterinary_passport_update_proposals
language plpgsql stable security definer set search_path = public as $$
declare v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.read');
  return query
  select * from public.veterinary_passport_update_proposals
  where clinic_id = p_clinic_id
  order by created_at desc;
end;
$$;

create or replace function public.m28_decide_passport_update_proposal(
  p_proposal_id uuid,
  p_decision text,
  p_decision_note text default null
) returns public.veterinary_passport_update_proposals
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_passport_update_proposals;
  v_clinic public.veterinary_clinic_profiles;
  v_decision text := upper(trim(coalesce(p_decision, '')));
  v_cred_id uuid;
begin
  select * into v_row from public.veterinary_passport_update_proposals where id = p_proposal_id for update;
  if not found then raise exception 'VETERINARY_PASSPORT_PROPOSAL_NOT_FOUND'; end if;
  perform public._m28_responsible_can_manage_grants(v_row.pet_id);
  if v_row.status <> 'PENDING' then raise exception 'VETERINARY_PASSPORT_PROPOSAL_ALREADY_RESOLVED'; end if;
  if v_decision not in ('ACCEPT','REJECT','CORRECTION_REQUESTED') then
    raise exception 'VETERINARY_PASSPORT_PROPOSAL_INVALID';
  end if;

  select * into v_clinic from public.veterinary_clinic_profiles where id = v_row.clinic_id;

  if v_decision = 'ACCEPT' then
    v_cred_id := public._m28_apply_passport_proposal(p_proposal_id);
    update public.veterinary_passport_update_proposals set
      status = 'ACCEPTED',
      decision_note = nullif(trim(coalesce(p_decision_note, '')), ''),
      decided_by = v_actor,
      decided_at = timezone('utc', now()),
      updated_at = timezone('utc', now())
    where id = p_proposal_id returning * into v_row;
  elsif v_decision = 'REJECT' then
    update public.veterinary_passport_update_proposals set
      status = 'REJECTED',
      decision_note = nullif(trim(coalesce(p_decision_note, '')), ''),
      decided_by = v_actor,
      decided_at = timezone('utc', now()),
      updated_at = timezone('utc', now())
    where id = p_proposal_id returning * into v_row;
  else
    update public.veterinary_passport_update_proposals set
      decision_note = nullif(trim(coalesce(p_decision_note, '')), ''),
      decided_by = v_actor,
      decided_at = timezone('utc', now()),
      updated_at = timezone('utc', now())
    where id = p_proposal_id returning * into v_row;
  end if;

  perform public._m28_best_effort_audit(
    'VETERINARY_PASSPORT_PROPOSAL_DECIDED', v_decision, 'veterinary_passport_proposal', v_row.id::text,
    v_clinic.organization_id,
    jsonb_build_object('status', v_row.status, 'credential_id', v_cred_id)
  );
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 9. RPCs — export and dashboard
-- ---------------------------------------------------------------------------
create or replace function public.m28_request_export(
  p_clinic_id uuid,
  p_client_request_id text,
  p_pet_id uuid default null
) returns public.veterinary_export_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_clinic public.veterinary_clinic_profiles;
  v_existing public.veterinary_export_requests;
  v_snapshot jsonb;
  v_row public.veterinary_export_requests;
  v_req text := trim(coalesce(p_client_request_id, ''));
begin
  if char_length(v_req) = 0 then raise exception 'VETERINARY_EXPORT_INVALID'; end if;
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.export');

  select * into v_existing from public.veterinary_export_requests where client_request_id = v_req;
  if found then return v_existing; end if;

  if p_pet_id is not null then
    perform public._m28_require_care_read(p_clinic_id, p_pet_id);
  end if;

  v_snapshot := public._m28_build_export_snapshot(p_clinic_id, p_pet_id);

  insert into public.veterinary_export_requests (
    clinic_id, pet_id, requested_by, status, export_format, snapshot, completed_at, client_request_id
  ) values (
    p_clinic_id, p_pet_id, v_actor, 'COMPLETED', 'PDF', v_snapshot, timezone('utc', now()), v_req
  ) returning * into v_row;

  perform public._m28_best_effort_audit(
    'VETERINARY_EXPORT_REQUESTED', 'REQUEST', 'veterinary_export_request', v_row.id::text,
    v_clinic.organization_id, jsonb_build_object('pet_id', p_pet_id)
  );
  perform public._m28_best_effort_audit(
    'VETERINARY_EXPORT_COMPLETED', 'COMPLETE', 'veterinary_export_request', v_row.id::text,
    v_clinic.organization_id, jsonb_build_object('pet_id', p_pet_id)
  );
  return v_row;
exception when unique_violation then
  select * into v_existing from public.veterinary_export_requests where client_request_id = v_req;
  if found then return v_existing; end if;
  raise;
end;
$$;

create or replace function public.m28_get_export_snapshot(p_export_request_id uuid)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_row public.veterinary_export_requests;
  v_clinic public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_row from public.veterinary_export_requests where id = p_export_request_id;
  if not found then raise exception 'VETERINARY_EXPORT_NOT_FOUND'; end if;
  select * into v_clinic from public.veterinary_clinic_profiles where id = v_row.clinic_id;
  if v_row.requested_by <> auth.uid()
     and not public.has_org_permission(v_clinic.organization_id, 'veterinary.care.export') then
    raise exception 'VETERINARY_EXPORT_FORBIDDEN';
  end if;
  if v_row.snapshot is null then
    return public._m28_build_export_snapshot(v_row.clinic_id, v_row.pet_id);
  end if;
  return v_row.snapshot;
end;
$$;

create or replace function public.m28_list_clinic_dashboard_summary(p_clinic_id uuid)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
  v_today_start timestamptz := date_trunc('day', timezone('utc', now()));
  v_today_end timestamptz := v_today_start + interval '1 day';
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_clinic.organization_id, 'veterinary.care.read');

  return jsonb_build_object(
    'clinic_id', p_clinic_id,
    'appointments_today', (
      select count(*) from public.veterinary_appointments a
      where a.clinic_id = p_clinic_id
        and a.starts_at >= v_today_start and a.starts_at < v_today_end
        and a.status in ('REQUESTED','CONFIRMED','COMPLETED')
    ),
    'care_drafts', (
      select count(*) from public.veterinary_professional_cares c
      where c.clinic_id = p_clinic_id and c.status = 'DRAFT'
    ),
    'follow_ups_pending', (
      select count(*) from public.veterinary_follow_ups f
      where f.clinic_id = p_clinic_id and f.status in ('PENDING','SCHEDULED','OVERDUE')
    ),
    'passport_proposals_pending', (
      select count(*) from public.veterinary_passport_update_proposals p
      where p.clinic_id = p_clinic_id and p.status = 'PENDING'
    ),
    'active_patients', (
      select count(*) from public.veterinary_patient_relationships pr
      where pr.clinic_id = p_clinic_id and pr.status = 'ACTIVE'
    )
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 10. Grants — helpers internal (no EXECUTE for public/anon/authenticated)
-- ---------------------------------------------------------------------------
revoke all on function public._m28_best_effort_audit(text,text,text,text,uuid,jsonb) from public, anon, authenticated;
revoke all on function public._m28_normalize_purposes(text[]) from public, anon, authenticated;
revoke all on function public._m28_responsible_can_manage_grants(uuid) from public, anon, authenticated;
revoke all on function public._m28_active_grant_allows(uuid,uuid,text) from public, anon, authenticated;
revoke all on function public._m28_require_care_read(uuid,uuid) from public, anon, authenticated;
revoke all on function public._m28_require_care_write(uuid,uuid) from public, anon, authenticated;
revoke all on function public._m28_resolve_actor_professional(uuid,uuid) from public, anon, authenticated;
revoke all on function public._m28_assert_pet_care_eligible(uuid) from public, anon, authenticated;
revoke all on function public._m28_upsert_patient_relationship_internal(uuid,uuid,uuid) from public, anon, authenticated;
revoke all on function public._m28_apply_passport_proposal(uuid) from public, anon, authenticated;
revoke all on function public._m28_build_export_snapshot(uuid,uuid) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 11. Grants — client RPCs (revoke public/anon; grant execute authenticated)
-- ---------------------------------------------------------------------------
do $$ declare f text; begin
  foreach f in array array[
    'm28_grant_professional_access(uuid,uuid,uuid,text[],timestamptz,timestamptz)',
    'm28_revoke_professional_access(uuid)',
    'm28_list_my_grants_for_pet(uuid)',
    'm28_list_grants_for_responsible(uuid)',
    'm28_upsert_patient_relationship(uuid,uuid,uuid)',
    'm28_list_clinic_patients(uuid)',
    'm28_create_care_draft(uuid,uuid,text,text,uuid,uuid,text)',
    'm28_update_care_draft(uuid,text,numeric,text,text,text)',
    'm28_finalize_care(uuid,uuid,text)',
    'm28_supersede_care(uuid,text,text,numeric,text,text,text)',
    'm28_get_care(uuid)',
    'm28_list_pet_cares(uuid,uuid)',
    'm28_create_vaccination_record(uuid,text,text,timestamptz,text,text,text,timestamptz,text)',
    'm28_create_professional_document(uuid,uuid,text,text,text,uuid,text)',
    'm28_create_follow_up(uuid,text,timestamptz,text,uuid)',
    'm28_update_follow_up_status(uuid,text,text)',
    'm28_create_passport_update_proposal(uuid,text,jsonb,uuid,uuid,jsonb,text)',
    'm28_list_passport_update_proposals_for_responsible(uuid)',
    'm28_list_clinic_proposals(uuid)',
    'm28_decide_passport_update_proposal(uuid,text,text)',
    'm28_request_export(uuid,text,uuid)',
    'm28_get_export_snapshot(uuid)',
    'm28_list_clinic_dashboard_summary(uuid)'
  ] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- 12. Comments
-- ---------------------------------------------------------------------------
comment on table public.veterinary_care_type_catalog is
  'M28 Pilot: catálogo de tipos de atención; códigos estables + labels configurables.';
comment on table public.veterinary_patient_relationships is
  'M28 Pilot: vínculo operativo clínica ↔ mascota M08; no duplica identidad de mascota.';
comment on table public.veterinary_professional_access_grants is
  'M28 Pilot: consentimiento del responsable M08 para acceso profesional acotado por purposes.';
comment on table public.veterinary_professional_cares is
  'M28 Pilot: atenciones profesionales trazables; corrección vía supersede; no historia clínica oficial.';
comment on table public.veterinary_passport_update_proposals is
  'M28 Pilot: propuestas hacia Pasaporte M14; nunca escritura directa sin decisión del responsable.';
comment on function public.m28_decide_passport_update_proposal(uuid,text,text) is
  'Decisión responsable ACCEPT/REJECT/CORRECTION_REQUESTED; ACCEPT VACCINATION inserta pet_passport_credentials DRAFT.';
comment on function public.m28_grant_professional_access(uuid,uuid,uuid,text[],timestamptz,timestamptz) is
  'M28 Pilot: permisos org usan namespace veterinary.care.* (convención M12/M03), no prefijo m28.*.';

commit;
