-- =============================================================================
-- LeoVer M14 — migration 050: pet passports, credentials and verification queue
-- Forward-only over 001–049. LOCAL ONLY: do not apply remotely without approval.
-- Decisions are intentionally prepared for Block 3; there is no client resolve RPC.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Canonical platform and organization permissions
-- ---------------------------------------------------------------------------
insert into public.permissions (code, description) values
  ('passport.read', 'Read authorized pet passports'),
  ('passport.create', 'Create pet passports'),
  ('passport.manage_own', 'Manage own responsible pet passports'),
  ('passport.manage_organization', 'Manage organization pet passports'),
  ('passport.verify', 'Verify passport credentials'),
  ('passport.moderate', 'Moderate pet passports'),
  ('passport.credential.issue', 'Issue passport credentials'),
  ('passport.credential.verify', 'Verify passport credentials'),
  ('passport.public.read', 'Read public redacted passports')
on conflict (code) do nothing;

insert into public.organization_permissions (code, description) values
  ('passport.read', 'Read organization pet passports'),
  ('passport.create', 'Create organization pet passports'),
  ('passport.manage_own', 'Manage own responsible pet passports'),
  ('passport.manage_organization', 'Manage organization pet passports'),
  ('passport.verify', 'Verify passport credentials'),
  ('passport.moderate', 'Moderate pet passports'),
  ('passport.credential.issue', 'Issue passport credentials'),
  ('passport.credential.verify', 'Verify passport credentials'),
  ('passport.public.read', 'Read public redacted passports')
on conflict (code) do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id from public.platform_roles r cross join public.permissions p
where r.code = 'MODERATOR' and p.code in ('passport.read','passport.verify','passport.moderate')
on conflict do nothing;
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id from public.platform_roles r cross join public.permissions p
where r.code = 'ADMIN' and p.code in ('passport.read','passport.verify','passport.moderate','passport.manage_organization')
on conflict do nothing;
insert into public.role_permissions (role_id, permission_id)
select r.id, p.id from public.platform_roles r cross join public.permissions p
where r.code = 'SUPERADMIN' and p.code like 'passport.%'
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id from public.organization_roles r cross join public.organization_permissions p
where r.code in ('OWNER','ADMIN','MANAGER')
  and p.code in ('passport.read','passport.create','passport.manage_own','passport.manage_organization','passport.verify','passport.credential.issue','passport.credential.verify','passport.public.read')
on conflict do nothing;
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id from public.organization_roles r cross join public.organization_permissions p
where r.code = 'MEMBER' and p.code in ('passport.read','passport.public.read')
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Persistent model. Passport data is a server-owned snapshot, not M08 data.
-- ---------------------------------------------------------------------------
create table if not exists public.pet_passports (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete restrict,
  passport_number text not null unique,
  public_code text not null unique,
  status text not null default 'DRAFT',
  visibility text not null default 'PRIVATE',
  display_name text not null,
  species text not null,
  breed_text text,
  sex text,
  birth_date date,
  primary_color text,
  distinctive_marks text,
  microchip_number_normalized text,
  created_by uuid not null references public.users(id) on delete restrict,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  activated_at timestamptz,
  archived_at timestamptz,
  constraint pet_passports_status_chk check (status in ('DRAFT','ACTIVE','SUSPENDED','REVOKED','ARCHIVED')),
  constraint pet_passports_visibility_chk check (visibility in ('PRIVATE','RESPONSIBLES','AUTHORIZED_ORGANIZATIONS','PUBLIC_REDACTED')),
  constraint pet_passports_name_chk check (char_length(trim(display_name)) > 0),
  constraint pet_passports_species_chk check (char_length(trim(species)) > 0)
);
create unique index if not exists pet_passports_one_non_final_per_pet
  on public.pet_passports(pet_id) where status not in ('REVOKED','ARCHIVED');
create index if not exists pet_passports_pet_idx on public.pet_passports(pet_id);
create index if not exists pet_passports_status_idx on public.pet_passports(status, updated_at desc);

create table if not exists public.pet_passport_credentials (
  id uuid primary key default gen_random_uuid(),
  passport_id uuid not null references public.pet_passports(id) on delete restrict,
  type text not null,
  title text not null,
  issuer_organization_id uuid null references public.organizations(id) on delete restrict,
  issuer_professional_id uuid null references public.veterinary_professionals(id) on delete restrict,
  issued_at timestamptz,
  expires_at timestamptz,
  status text not null default 'DRAFT',
  visibility text not null default 'PRIVATE',
  media_refs text[] not null default '{}'::text[],
  external_reference_masked text,
  note_private text,
  created_by uuid not null references public.users(id) on delete restrict,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint pet_passport_credentials_type_chk check (type in ('IDENTITY','MICROCHIP','ADOPTION','OWNERSHIP','STERILIZATION_ATTESTATION','VACCINATION_ATTESTATION','TRAVEL_DOCUMENT','OTHER')),
  constraint pet_passport_credentials_status_chk check (status in ('DRAFT','PENDING_VERIFICATION','VERIFIED','REJECTED','EXPIRED','REVOKED')),
  constraint pet_passport_credentials_visibility_chk check (visibility in ('PRIVATE','RESPONSIBLES','AUTHORIZED_ORGANIZATIONS','PUBLIC_REDACTED')),
  constraint pet_passport_credentials_title_chk check (char_length(trim(title)) > 0),
  constraint pet_passport_credentials_dates_chk check (expires_at is null or issued_at is null or expires_at > issued_at)
);
create index if not exists pet_passport_credentials_passport_idx on public.pet_passport_credentials(passport_id, created_at desc);
create index if not exists pet_passport_credentials_status_idx on public.pet_passport_credentials(status, expires_at);
create index if not exists pet_passport_credentials_issuer_org_idx on public.pet_passport_credentials(issuer_organization_id);
create index if not exists pet_passport_credentials_issuer_professional_idx on public.pet_passport_credentials(issuer_professional_id);

create table if not exists public.pet_passport_verification_requests (
  id uuid primary key default gen_random_uuid(),
  credential_id uuid not null references public.pet_passport_credentials(id) on delete restrict,
  requested_by uuid not null references public.users(id) on delete restrict,
  target_organization_id uuid null references public.organizations(id) on delete restrict,
  target_professional_id uuid null references public.veterinary_professionals(id) on delete restrict,
  status text not null default 'PENDING',
  requested_at timestamptz not null default timezone('utc', now()),
  resolved_at timestamptz,
  resolution_reason text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint pet_passport_verification_requests_status_chk check (status in ('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED')),
  constraint pet_passport_verification_requests_target_chk check (target_organization_id is not null or target_professional_id is not null)
);
create unique index if not exists pet_passport_verification_requests_one_pending
  on public.pet_passport_verification_requests(credential_id) where status = 'PENDING';
create index if not exists pet_passport_verification_requests_target_org_idx on public.pet_passport_verification_requests(target_organization_id, status);
create index if not exists pet_passport_verification_requests_target_prof_idx on public.pet_passport_verification_requests(target_professional_id, status);

create table if not exists public.pet_passport_verification_decisions (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null unique references public.pet_passport_verification_requests(id) on delete restrict,
  decision text not null,
  actor_user_id uuid not null references public.users(id) on delete restrict,
  actor_authority text not null,
  reason_code text,
  note_private text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint pet_passport_verification_decisions_decision_chk check (decision in ('APPROVED','REJECTED'))
);

create table if not exists public.pet_passport_status_history (
  id uuid primary key default gen_random_uuid(),
  passport_id uuid not null references public.pet_passports(id) on delete restrict,
  from_status text,
  to_status text not null,
  actor_user_id uuid not null references public.users(id) on delete restrict,
  reason text,
  created_at timestamptz not null default timezone('utc', now()),
  metadata jsonb not null default '{}'::jsonb,
  constraint pet_passport_status_history_from_chk check (from_status is null or from_status in ('DRAFT','ACTIVE','SUSPENDED','REVOKED','ARCHIVED')),
  constraint pet_passport_status_history_to_chk check (to_status in ('DRAFT','ACTIVE','SUSPENDED','REVOKED','ARCHIVED')),
  constraint pet_passport_status_history_metadata_chk check (jsonb_typeof(metadata) = 'object')
);
create index if not exists pet_passport_status_history_passport_idx on public.pet_passport_status_history(passport_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 2. Private helpers. All client entrypoints derive actor from auth.uid().
-- ---------------------------------------------------------------------------
create or replace function public._m14_require_auth() returns uuid
language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := auth.uid(); begin
  if v_actor is null then raise exception using errcode = 'P0001', message = 'UNAUTHORIZED'; end if;
  return v_actor;
end; $$;

create or replace function public._m14_is_safe_media_ref(p_ref text) returns boolean
language sql immutable security definer set search_path = public as $$
  select p_ref is not null
    and p_ref ~ '^(m05://|file_asset:)'
    and p_ref !~* '^https?://'
    and p_ref !~* 'leover.*public';
$$;

create or replace function public._m14_assert_media_refs(p_refs text[]) returns void
language plpgsql security definer set search_path = public as $$
declare v_ref text; begin
  foreach v_ref in array coalesce(p_refs, '{}'::text[]) loop
    if not public._m14_is_safe_media_ref(v_ref) then
      raise exception using errcode = 'P0001', message = 'INVALID_MEDIA_REFERENCE';
    end if;
  end loop;
end; $$;

create or replace function public._m14_generate_passport_number() returns text
language plpgsql volatile security definer set search_path = public as $$
declare v_number text; v_try integer := 0; begin
  loop
    v_try := v_try + 1;
    v_number := 'LV-AR-' || to_char(timezone('America/Argentina/Buenos_Aires', now()), 'YYYY') || '-' || upper(encode(extensions.gen_random_bytes(4), 'hex'));
    exit when not exists (select 1 from public.pet_passports where passport_number = v_number);
    if v_try >= 20 then raise exception using errcode = 'P0001', message = 'PASSPORT_NUMBER_GENERATION_FAILED'; end if;
  end loop;
  return v_number;
end; $$;

create or replace function public._m14_generate_public_code() returns text
language plpgsql volatile security definer set search_path = public as $$
declare v_code text; v_try integer := 0; begin
  loop
    v_try := v_try + 1;
    v_code := 'PUB-' || upper(encode(extensions.gen_random_bytes(16), 'hex'));
    exit when not exists (select 1 from public.pet_passports where public_code = v_code);
    if v_try >= 20 then raise exception using errcode = 'P0001', message = 'PUBLIC_CODE_GENERATION_FAILED'; end if;
  end loop;
  return v_code;
end; $$;

create or replace function public._m14_mask_microchip(p_value text) returns text
language sql immutable security definer set search_path = public as $$
  select case when nullif(trim(p_value), '') is null then null
    when char_length(trim(p_value)) <= 4 then repeat('*', char_length(trim(p_value)))
    else repeat('*', greatest(char_length(trim(p_value)) - 4, 4)) || right(trim(p_value), 4) end;
$$;

create or replace function public._m14_can_manage_pet(p_pet_id uuid, p_actor uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select p_actor is not null and (
    public.m08_actor_has_active_responsibility(p_pet_id, p_actor)
    or public.has_permission('passport.moderate')
    or public.has_permission('passport.manage_organization')
  );
$$;

create or replace function public._m14_pet_eligible(p_pet_id uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.pets where id = p_pet_id and status = 'ACTIVE');
$$;

create or replace function public._m14_can_manage_passport(p_passport_id uuid, p_actor uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.pet_passports p where p.id = p_passport_id and public._m14_can_manage_pet(p.pet_id, p_actor));
$$;

create or replace function public._m14_can_manage_request(p_request_id uuid, p_actor uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.pet_passport_verification_requests r
    left join public.veterinary_professionals vp on vp.id = r.target_professional_id
    where r.id = p_request_id and (public.has_permission('passport.verify') or public.has_permission('passport.moderate')
      or (r.target_organization_id is not null and public.has_org_permission(r.target_organization_id, 'passport.verify'))
      or (vp.user_id = p_actor and vp.status = 'ACTIVE'))
  );
$$;

create or replace function public._m14_append_passport_history(p_passport_id uuid, p_from text, p_to text, p_actor uuid, p_reason text default null, p_metadata jsonb default '{}'::jsonb) returns void
language plpgsql security definer set search_path = public as $$
begin
  insert into public.pet_passport_status_history(passport_id, from_status, to_status, actor_user_id, reason, created_at, metadata)
  values (p_passport_id, p_from, p_to, p_actor, nullif(trim(p_reason), ''), timezone('utc', now()), coalesce(p_metadata, '{}'::jsonb));
end; $$;

-- Event keys stay lowercase/dotted (for example m14.passport.created). The M07
-- catalogue may impose a controlled ceiling, therefore auditing is best effort.
create or replace function public._m14_best_effort_audit(p_event_key text, p_action text, p_resource_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if to_regprocedure('public.m07_best_effort_audit(text,text,text,text,text,text,jsonb)') is not null then
    execute 'select public.m07_best_effort_audit($1,$2,$3,$4,$5,$6,$7)'
      using p_event_key, p_action, 'SUCCESS', replace(gen_random_uuid()::text, '-', ''), 'pet_passport', p_resource_id::text,
        jsonb_build_object('module','M14','result','SUCCESS');
  end if;
exception when others then null;
end; $$;

create or replace function public._m14_passport_json(p public.pet_passports) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id',p.id,'pet_id',p.pet_id,'passport_number',p.passport_number,'public_code',p.public_code,'status',p.status,'visibility',p.visibility,'display_name',p.display_name,'species',p.species,'breed_text',p.breed_text,'sex',p.sex,'birth_date',p.birth_date,'primary_color',p.primary_color,'distinctive_marks',p.distinctive_marks,'microchip_masked',public._m14_mask_microchip(p.microchip_number_normalized),'created_at',p.created_at,'updated_at',p.updated_at,'activated_at',p.activated_at,'archived_at',p.archived_at);
$$;

create or replace function public._m14_credential_json(p public.pet_passport_credentials, p_include_note boolean default false) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_strip_nulls(jsonb_build_object('id',p.id,'passport_id',p.passport_id,'type',p.type,'title',p.title,'issuer_organization_id',p.issuer_organization_id,'issuer_professional_id',p.issuer_professional_id,'issued_at',p.issued_at,'expires_at',p.expires_at,'status',p.status,'visibility',p.visibility,'media_refs',p.media_refs,'external_reference_masked',p.external_reference_masked,'note_private',case when p_include_note then p.note_private else null end,'created_at',p.created_at,'updated_at',p.updated_at));
$$;

-- ---------------------------------------------------------------------------
-- 3. Client RPCs — passport lifecycle
-- ---------------------------------------------------------------------------
create or replace function public.m14_create_pet_passport(p_pet_id uuid, p_display_name text, p_species text, p_breed_text text default null, p_sex text default null, p_birth_date date default null, p_primary_color text default null, p_distinctive_marks text default null, p_microchip_raw text default null, p_visibility text default 'PRIVATE') returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); v_row public.pet_passports; v_try integer := 0; begin
  if not exists(select 1 from public.pets where id=p_pet_id) then raise exception using errcode='P0001', message='PET_NOT_FOUND'; end if;
  if not public._m14_pet_eligible(p_pet_id) then raise exception using errcode='P0001', message='PET_NOT_ELIGIBLE'; end if;
  if not public._m14_can_manage_pet(p_pet_id, actor) then raise exception using errcode='P0001', message='UNAUTHORIZED'; end if;
  if coalesce(trim(p_display_name),'')='' or coalesce(trim(p_species),'')='' then raise exception using errcode='P0001', message='INVALID_PASSPORT_STATUS'; end if;
  if exists(select 1 from public.pet_passports where pet_id=p_pet_id and status not in ('REVOKED','ARCHIVED')) then raise exception using errcode='P0001', message='PASSPORT_ALREADY_EXISTS'; end if;
  loop begin
    v_try:=v_try+1;
    insert into public.pet_passports(pet_id,passport_number,public_code,status,visibility,display_name,species,breed_text,sex,birth_date,primary_color,distinctive_marks,microchip_number_normalized,created_by,created_at,updated_at)
    values(p_pet_id,public._m14_generate_passport_number(),public._m14_generate_public_code(),'DRAFT',coalesce(p_visibility,'PRIVATE'),trim(p_display_name),trim(p_species),nullif(trim(p_breed_text),''),nullif(trim(p_sex),''),p_birth_date,nullif(trim(p_primary_color),''),nullif(trim(p_distinctive_marks),''),public.m08_normalize_microchip(p_microchip_raw),actor,timezone('utc',now()),timezone('utc',now())) returning * into v_row;
    exit;
  exception when unique_violation then if v_try>=5 then raise exception using errcode='P0001', message='CONFLICT'; end if; end; end loop;
  perform public._m14_append_passport_history(v_row.id,null,'DRAFT',actor,'CREATED'); perform public._m14_best_effort_audit('m14.passport.created','CREATE',v_row.id); return public._m14_passport_json(v_row);
end; $$;

create or replace function public.m14_get_pet_passport(p_passport_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); v public.pet_passports; begin
 select * into v from public.pet_passports where id=p_passport_id; if not found then raise exception using errcode='P0001',message='PASSPORT_NOT_FOUND'; end if;
 if not public._m14_can_manage_pet(v.pet_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if; return public._m14_passport_json(v); end; $$;

create or replace function public.m14_get_pet_passport_by_pet(p_pet_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); v public.pet_passports; begin
 if not public._m14_can_manage_pet(p_pet_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if;
 select * into v from public.pet_passports where pet_id=p_pet_id and status not in ('REVOKED','ARCHIVED') order by created_at desc limit 1;
 if not found then raise exception using errcode='P0001',message='PASSPORT_NOT_FOUND'; end if; return public._m14_passport_json(v); end; $$;

create or replace function public.m14_list_my_pet_passports() returns setof jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); begin
 return query select public._m14_passport_json(p) from public.pet_passports p where public._m14_can_manage_pet(p.pet_id,actor) order by p.updated_at desc; end; $$;

create or replace function public.m14_update_my_pet_passport(p_passport_id uuid, p_display_name text default null, p_breed_text text default null, p_sex text default null, p_birth_date date default null, p_primary_color text default null, p_distinctive_marks text default null, p_microchip_raw text default null, p_visibility text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); v public.pet_passports; begin
 select * into v from public.pet_passports where id=p_passport_id for update; if not found then raise exception using errcode='P0001',message='PASSPORT_NOT_FOUND'; end if;
 if not public._m14_can_manage_pet(v.pet_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if;
 if v.status in ('REVOKED','ARCHIVED') then raise exception using errcode='P0001',message='INVALID_TRANSITION'; end if;
 update public.pet_passports set display_name=coalesce(nullif(trim(p_display_name),''),display_name),breed_text=coalesce(nullif(trim(p_breed_text),''),breed_text),sex=coalesce(nullif(trim(p_sex),''),sex),birth_date=coalesce(p_birth_date,birth_date),primary_color=coalesce(nullif(trim(p_primary_color),''),primary_color),distinctive_marks=coalesce(nullif(trim(p_distinctive_marks),''),distinctive_marks),microchip_number_normalized=case when p_microchip_raw is null then microchip_number_normalized else public.m08_normalize_microchip(p_microchip_raw) end,visibility=coalesce(p_visibility,visibility),updated_at=timezone('utc',now()) where id=v.id returning * into v;
 perform public._m14_best_effort_audit('m14.passport.updated','UPDATE',v.id); return public._m14_passport_json(v); end; $$;

create or replace function public.m14_activate_my_pet_passport(p_passport_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); v public.pet_passports; begin
 select * into v from public.pet_passports where id=p_passport_id for update; if not found then raise exception using errcode='P0001',message='PASSPORT_NOT_FOUND'; end if;
 if not public._m14_can_manage_pet(v.pet_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if;
 if not public._m14_pet_eligible(v.pet_id) then raise exception using errcode='P0001',message='PET_NOT_ELIGIBLE'; end if;
 if v.status='ACTIVE' then return public._m14_passport_json(v); end if; if v.status<>'DRAFT' then raise exception using errcode='P0001',message='INVALID_TRANSITION'; end if;
 update public.pet_passports set status='ACTIVE',activated_at=timezone('utc',now()),updated_at=timezone('utc',now()) where id=v.id returning * into v;
 perform public._m14_append_passport_history(v.id,'DRAFT','ACTIVE',actor,'ACTIVATED'); perform public._m14_best_effort_audit('m14.passport.activated','ACTIVATE',v.id); return public._m14_passport_json(v); end; $$;

create or replace function public.m14_archive_my_pet_passport(p_passport_id uuid, p_reason text default null) returns jsonb
language plpgsql security definer set search_path = public as $
declare
  actor uuid := public._m14_require_auth();
  v public.pet_passports;
  v_from text;
begin
  select * into v from public.pet_passports where id = p_passport_id for update;
  if not found then raise exception using errcode = 'P0001', message = 'PASSPORT_NOT_FOUND'; end if;
  if not public._m14_can_manage_pet(v.pet_id, actor) then raise exception using errcode = 'P0001', message = 'UNAUTHORIZED'; end if;
  if v.status in ('REVOKED', 'ARCHIVED') then raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION'; end if;
  v_from := v.status;
  update public.pet_passports
    set status = 'ARCHIVED', archived_at = timezone('utc', now()), updated_at = timezone('utc', now())
  where id = v.id
  returning * into v;
  perform public._m14_append_passport_history(v.id, v_from, 'ARCHIVED', actor, p_reason);
  perform public._m14_best_effort_audit('m14.passport.archived', 'ARCHIVE', v.id);
  return public._m14_passport_json(v);
end;
$;

-- Public endpoint deliberately has no auth requirement and never exposes identifiers,
-- full microchips, media references, notes, creators, pet ids, or passport numbers.
create or replace function public.m14_get_public_pet_passport(p_public_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v public.pet_passports; v_credentials jsonb; begin
 select * into v from public.pet_passports where public_code=trim(coalesce(p_public_code,'')) and status='ACTIVE' and visibility='PUBLIC_REDACTED';
 if not found then raise exception using errcode='P0001',message='PUBLIC_PASSPORT_NOT_AVAILABLE'; end if;
 select coalesce(jsonb_agg(jsonb_build_object('type',c.type,'title',c.title,'issued_at',c.issued_at,'expires_at',c.expires_at,'status',c.status,'issuer',case when c.issuer_organization_id is null then null else (select o.display_name from public.organizations o where o.id=c.issuer_organization_id) end) order by c.created_at),'[]'::jsonb) into v_credentials from public.pet_passport_credentials c where c.passport_id=v.id and c.visibility='PUBLIC_REDACTED' and c.status='VERIFIED';
 return jsonb_strip_nulls(jsonb_build_object('display_name',v.display_name,'species',v.species,'breed_text',v.breed_text,'sex',v.sex,'birth_date',v.birth_date,'primary_color',v.primary_color,'distinctive_marks',v.distinctive_marks,'microchip_masked',public._m14_mask_microchip(v.microchip_number_normalized),'status','ACTIVE','credentials',v_credentials,'updated_at',date_trunc('day',v.updated_at)));
end; $$;

-- ---------------------------------------------------------------------------
-- 4. Client RPCs — credential lifecycle
-- ---------------------------------------------------------------------------
create or replace function public.m14_create_passport_credential(p_passport_id uuid, p_type text, p_title text, p_issued_at timestamptz default null, p_expires_at timestamptz default null, p_visibility text default 'PRIVATE', p_media_refs text[] default '{}', p_external_reference_masked text default null, p_note_private text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); p public.pet_passports; c public.pet_passport_credentials; begin
 select * into p from public.pet_passports where id=p_passport_id; if not found then raise exception using errcode='P0001',message='PASSPORT_NOT_FOUND'; end if; if not public._m14_can_manage_pet(p.pet_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if;
 if p.status not in ('DRAFT','ACTIVE') then raise exception using errcode='P0001',message='INVALID_PASSPORT_STATUS'; end if; if p_expires_at is not null and p_issued_at is not null and p_expires_at<=p_issued_at then raise exception using errcode='P0001',message='INVALID_CREDENTIAL_DATES'; end if;
 perform public._m14_assert_media_refs(p_media_refs); insert into public.pet_passport_credentials(passport_id,type,title,issued_at,expires_at,status,visibility,media_refs,external_reference_masked,note_private,created_by,created_at,updated_at) values(p.id,upper(trim(p_type)),trim(p_title),p_issued_at,p_expires_at,'DRAFT',coalesce(p_visibility,'PRIVATE'),coalesce(p_media_refs,'{}'::text[]),nullif(trim(p_external_reference_masked),''),nullif(trim(p_note_private),''),actor,timezone('utc',now()),timezone('utc',now())) returning * into c;
 perform public._m14_best_effort_audit('m14.credential.created','CREATE',p.id); return public._m14_credential_json(c,true); end; $$;

create or replace function public.m14_update_my_passport_credential(p_credential_id uuid, p_title text default null, p_visibility text default null, p_media_refs text[] default null, p_external_reference_masked text default null, p_note_private text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); c public.pet_passport_credentials; begin
 select c0.* into c from public.pet_passport_credentials c0 join public.pet_passports p on p.id=c0.passport_id where c0.id=p_credential_id for update; if not found then raise exception using errcode='P0001',message='CREDENTIAL_NOT_FOUND'; end if;
 if not public._m14_can_manage_passport(c.passport_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if; if c.status<>'DRAFT' then raise exception using errcode='P0001',message='INVALID_TRANSITION'; end if; if p_media_refs is not null then perform public._m14_assert_media_refs(p_media_refs); end if;
 update public.pet_passport_credentials set title=coalesce(nullif(trim(p_title),''),title),visibility=coalesce(p_visibility,visibility),media_refs=coalesce(p_media_refs,media_refs),external_reference_masked=coalesce(nullif(trim(p_external_reference_masked),''),external_reference_masked),note_private=coalesce(nullif(trim(p_note_private),''),note_private),updated_at=timezone('utc',now()) where id=c.id returning * into c; return public._m14_credential_json(c,true); end; $$;

create or replace function public.m14_withdraw_my_passport_credential(p_credential_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); c public.pet_passport_credentials; begin
 select * into c from public.pet_passport_credentials where id=p_credential_id for update; if not found then raise exception using errcode='P0001',message='CREDENTIAL_NOT_FOUND'; end if; if not public._m14_can_manage_passport(c.passport_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if;
 if c.status not in ('DRAFT','PENDING_VERIFICATION') then raise exception using errcode='P0001',message='INVALID_TRANSITION'; end if; update public.pet_passport_credentials set status='REVOKED',updated_at=timezone('utc',now()) where id=c.id returning * into c; return public._m14_credential_json(c,true); end; $$;

create or replace function public.m14_get_passport_credential(p_credential_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); c public.pet_passport_credentials; begin select * into c from public.pet_passport_credentials where id=p_credential_id; if not found then raise exception using errcode='P0001',message='CREDENTIAL_NOT_FOUND'; end if; if not public._m14_can_manage_passport(c.passport_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if; return public._m14_credential_json(c,true); end; $$;

create or replace function public.m14_list_passport_credentials(p_passport_id uuid) returns setof jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); begin if not public._m14_can_manage_passport(p_passport_id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if; return query select public._m14_credential_json(c,true) from public.pet_passport_credentials c where c.passport_id=p_passport_id order by c.created_at desc; end; $$;

-- ---------------------------------------------------------------------------
-- 5. Client RPCs — verification request queue only; no final decision endpoint.
-- ---------------------------------------------------------------------------
create or replace function public.m14_create_verification_request(p_credential_id uuid, p_target_organization_id uuid default null) returns jsonb
language plpgsql security definer set search_path = public as $
declare
  actor uuid := public._m14_require_auth();
  c public.pet_passport_credentials;
  r public.pet_passport_verification_requests;
begin
  select * into c from public.pet_passport_credentials where id = p_credential_id for update;
  if not found then raise exception using errcode = 'P0001', message = 'CREDENTIAL_NOT_FOUND'; end if;
  if not public._m14_can_manage_passport(c.passport_id, actor) then raise exception using errcode = 'P0001', message = 'UNAUTHORIZED'; end if;
  if c.status <> 'DRAFT' then raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION'; end if;
  -- Anti-autoverification: requester cannot be an active member of the target org when provided.
  if p_target_organization_id is not null
     and exists (
       select 1 from public.organization_memberships m
       where m.organization_id = p_target_organization_id
         and m.user_id = actor
         and m.status = 'ACTIVE'
     ) then
    raise exception using errcode = 'P0001', message = 'UNAUTHORIZED';
  end if;
  begin
    insert into public.pet_passport_verification_requests(
      credential_id, requested_by, target_organization_id, status, requested_at, created_at, updated_at
    ) values (
      c.id, actor, p_target_organization_id, 'PENDING', timezone('utc', now()), timezone('utc', now()), timezone('utc', now())
    ) returning * into r;
  exception when unique_violation then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_REQUEST_ALREADY_PENDING';
  end;
  update public.pet_passport_credentials
    set status = 'PENDING_VERIFICATION', updated_at = timezone('utc', now())
  where id = c.id;
  perform public._m14_best_effort_audit('m14.verification.requested', 'CREATE', c.passport_id);
  return jsonb_build_object('id', r.id, 'credential_id', r.credential_id, 'status', r.status, 'requested_at', r.requested_at);
end;
$;

create or replace function public.m14_cancel_my_verification_request(p_request_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); r public.pet_passport_verification_requests; begin select * into r from public.pet_passport_verification_requests where id=p_request_id for update; if not found then raise exception using errcode='P0001',message='CREDENTIAL_NOT_FOUND'; end if; if r.requested_by<>actor then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if; if r.status<>'PENDING' then raise exception using errcode='P0001',message='INVALID_TRANSITION'; end if; update public.pet_passport_verification_requests set status='CANCELLED',resolved_at=timezone('utc',now()),updated_at=timezone('utc',now()) where id=r.id returning * into r; update public.pet_passport_credentials set status='DRAFT',updated_at=timezone('utc',now()) where id=r.credential_id and status='PENDING_VERIFICATION'; perform public._m14_best_effort_audit('m14.verification.cancelled','UPDATE',r.credential_id); return jsonb_build_object('id',r.id,'credential_id',r.credential_id,'status',r.status,'resolved_at',r.resolved_at); end; $$;

create or replace function public.m14_get_verification_request(p_request_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); r public.pet_passport_verification_requests; begin select * into r from public.pet_passport_verification_requests where id=p_request_id; if not found then raise exception using errcode='P0001',message='CREDENTIAL_NOT_FOUND'; end if; if r.requested_by<>actor and not public._m14_can_manage_request(r.id,actor) then raise exception using errcode='P0001',message='UNAUTHORIZED'; end if; return jsonb_build_object('id',r.id,'credential_id',r.credential_id,'status',r.status,'requested_at',r.requested_at,'resolved_at',r.resolved_at); end; $$;

create or replace function public.m14_list_my_verification_requests() returns setof jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); begin return query select jsonb_build_object('id',r.id,'credential_id',r.credential_id,'status',r.status,'requested_at',r.requested_at,'resolved_at',r.resolved_at) from public.pet_passport_verification_requests r where r.requested_by=actor order by r.requested_at desc; end; $$;

create or replace function public.m14_list_managed_verification_requests() returns setof jsonb
language plpgsql security definer set search_path = public as $$
declare actor uuid := public._m14_require_auth(); begin return query select jsonb_build_object('id',r.id,'credential_id',r.credential_id,'status',r.status,'requested_at',r.requested_at,'target_organization_id',r.target_organization_id,'target_professional_id',r.target_professional_id) from public.pet_passport_verification_requests r where public._m14_can_manage_request(r.id,actor) order by r.requested_at desc; end; $$;

-- ---------------------------------------------------------------------------
-- 6. RLS is defence in depth. Writes are RPC-only, with no direct client DML.
-- ---------------------------------------------------------------------------
alter table public.pet_passports enable row level security;
alter table public.pet_passport_credentials enable row level security;
alter table public.pet_passport_verification_requests enable row level security;
alter table public.pet_passport_verification_decisions enable row level security;
alter table public.pet_passport_status_history enable row level security;

create policy pet_passports_select_m14 on public.pet_passports for select to authenticated using (public._m14_can_manage_pet(pet_id, auth.uid()));
create policy pet_passport_credentials_select_m14 on public.pet_passport_credentials for select to authenticated using (public._m14_can_manage_passport(passport_id, auth.uid()));
create policy pet_passport_verification_requests_select_m14 on public.pet_passport_verification_requests for select to authenticated using (requested_by=auth.uid() or public._m14_can_manage_request(id, auth.uid()));
create policy pet_passport_verification_decisions_select_m14 on public.pet_passport_verification_decisions for select to authenticated using (public._m14_can_manage_request(request_id, auth.uid()));
create policy pet_passport_status_history_select_m14 on public.pet_passport_status_history for select to authenticated using (public._m14_can_manage_passport(passport_id, auth.uid()));

revoke insert, update, delete on public.pet_passports from authenticated, anon;
revoke insert, update, delete on public.pet_passport_credentials from authenticated, anon;
revoke insert, update, delete on public.pet_passport_verification_requests from authenticated, anon;
revoke insert, update, delete on public.pet_passport_verification_decisions from authenticated, anon;
revoke insert, update, delete on public.pet_passport_status_history from authenticated, anon;
revoke all on table public.pet_passports from anon;
revoke all on table public.pet_passport_credentials from anon;
revoke all on table public.pet_passport_verification_requests from anon;
revoke all on table public.pet_passport_verification_decisions from anon;
revoke all on table public.pet_passport_status_history from anon;

-- ---------------------------------------------------------------------------
-- 7. Function grants. Helpers remain inaccessible to every client role.
-- ---------------------------------------------------------------------------
do $$ declare f text; begin
 foreach f in array array[
  '_m14_require_auth()','_m14_is_safe_media_ref(text)','_m14_assert_media_refs(text[])','_m14_generate_passport_number()','_m14_generate_public_code()','_m14_mask_microchip(text)','_m14_can_manage_pet(uuid,uuid)','_m14_pet_eligible(uuid)','_m14_can_manage_passport(uuid,uuid)','_m14_can_manage_request(uuid,uuid)','_m14_append_passport_history(uuid,text,text,uuid,text,jsonb)','_m14_best_effort_audit(text,text,uuid)','_m14_passport_json(pet_passports)','_m14_credential_json(pet_passport_credentials,boolean)'
 ] loop execute format('revoke all on function public.%s from public, anon, authenticated', f); end loop;
end $$;

do $$ declare f text; begin
 foreach f in array array[
  'm14_create_pet_passport(uuid,text,text,text,text,date,text,text,text,text)','m14_get_pet_passport(uuid)','m14_get_pet_passport_by_pet(uuid)','m14_list_my_pet_passports()','m14_update_my_pet_passport(uuid,text,text,text,date,text,text,text,text)','m14_activate_my_pet_passport(uuid)','m14_archive_my_pet_passport(uuid,text)','m14_create_passport_credential(uuid,text,text,timestamptz,timestamptz,text,text[],text,text)','m14_update_my_passport_credential(uuid,text,text,text[],text,text)','m14_withdraw_my_passport_credential(uuid)','m14_get_passport_credential(uuid)','m14_list_passport_credentials(uuid)','m14_create_verification_request(uuid,uuid)','m14_cancel_my_verification_request(uuid)','m14_get_verification_request(uuid)','m14_list_my_verification_requests()','m14_list_managed_verification_requests()'
 ] loop execute format('revoke all on function public.%s from public, anon', f); execute format('grant execute on function public.%s to authenticated', f); end loop;
end $$;
revoke all on function public.m14_get_public_pet_passport(text) from public;
grant execute on function public.m14_get_public_pet_passport(text) to anon, authenticated;

-- Append-only history and decision rows are intentionally only written by future
-- trusted server workflows. Block 3 will add controlled decision resolution.
commit;
