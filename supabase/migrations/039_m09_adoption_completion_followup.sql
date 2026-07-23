-- =============================================================================
-- LeoVer M09 — migración 039: entrevistas, docs, acuerdo, finalización, follow-up
-- Forward-only sobre 001–038. NO reescribe 037/038.
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.adoption_interviews (
  id uuid primary key default gen_random_uuid(),
  adoption_id uuid not null references public.adoptions (id) on delete cascade,
  application_id uuid not null references public.adoption_applications (id) on delete cascade,
  scheduled_at timestamptz not null,
  interview_type text not null default 'IN_PERSON',
  location_or_link text,
  notes text,
  status text not null default 'SCHEDULED',
  created_by uuid not null references public.users (id),
  completed_at timestamptz,
  outcome text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint adoption_interviews_type_chk
    check (interview_type = any (array['IN_PERSON','VIDEO_CALL','PHONE']::text[])),
  constraint adoption_interviews_status_chk
    check (status = any (array['SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW']::text[]))
);

create index if not exists adoption_interviews_adoption_idx on public.adoption_interviews (adoption_id);
create index if not exists adoption_interviews_application_idx on public.adoption_interviews (application_id);
create index if not exists adoption_interviews_status_idx on public.adoption_interviews (status);

create table if not exists public.adoption_document_requirements (
  id uuid primary key default gen_random_uuid(),
  adoption_id uuid not null references public.adoptions (id) on delete cascade,
  application_id uuid not null references public.adoption_applications (id) on delete cascade,
  doc_type text not null,
  required boolean not null default true,
  status text not null default 'PENDING',
  storage_path text,
  rejection_reason text,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint adoption_docs_type_chk
    check (doc_type = any (array['IDENTITY','ADDRESS_PROOF','HOUSING_AUTHORIZATION','OTHER']::text[])),
  constraint adoption_docs_status_chk
    check (status = any (array['PENDING','SUBMITTED','APPROVED','REJECTED','NOT_REQUIRED']::text[])),
  constraint adoption_docs_no_public_leover_chk
    check (
      storage_path is null
      or storage_path !~* 'object/public/leover'
      or storage_path ~* '^(m05://|file_asset:)'
    )
);

create index if not exists adoption_docs_adoption_idx on public.adoption_document_requirements (adoption_id);
create index if not exists adoption_docs_application_idx on public.adoption_document_requirements (application_id);

create table if not exists public.adoption_agreements (
  id uuid primary key default gen_random_uuid(),
  adoption_id uuid not null references public.adoptions (id) on delete cascade,
  application_id uuid not null references public.adoption_applications (id) on delete cascade,
  adopter_user_id uuid not null references public.users (id),
  publisher_user_id uuid references public.users (id),
  publisher_organization_id uuid,
  terms_version text not null,
  terms_snapshot text not null,
  adopter_accepted_at timestamptz,
  publisher_accepted_at timestamptz,
  status text not null default 'DRAFT',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint adoption_agreements_status_chk
    check (status = any (array['DRAFT','PENDING_ADOPTER','PENDING_PUBLISHER','ACCEPTED','CANCELLED']::text[]))
);

create unique index if not exists adoption_agreements_one_active_uidx
  on public.adoption_agreements (adoption_id)
  where status <> 'CANCELLED';

create table if not exists public.adoption_finalizations (
  id uuid primary key default gen_random_uuid(),
  adoption_id uuid not null references public.adoptions (id) on delete cascade,
  application_id uuid not null references public.adoption_applications (id) on delete restrict,
  pet_id uuid references public.pets (id),
  adopter_user_id uuid not null references public.users (id),
  finalized_at timestamptz not null default timezone('utc', now()),
  finalized_by uuid not null references public.users (id),
  follow_up_plan_id uuid,
  created_at timestamptz not null default timezone('utc', now()),
  constraint adoption_finalizations_one_per_adoption unique (adoption_id)
);

create table if not exists public.adoption_followup_plans (
  id uuid primary key default gen_random_uuid(),
  adoption_id uuid not null references public.adoptions (id) on delete cascade,
  adopter_user_id uuid not null references public.users (id),
  status text not null default 'ACTIVE',
  created_at timestamptz not null default timezone('utc', now()),
  completed_at timestamptz,
  constraint adoption_followup_plans_status_chk
    check (status = any (array['ACTIVE','COMPLETED','CANCELLED']::text[])),
  constraint adoption_followup_plans_one_per_adoption unique (adoption_id)
);

alter table public.adoption_finalizations
  drop constraint if exists adoption_finalizations_follow_up_fk;
alter table public.adoption_finalizations
  add constraint adoption_finalizations_follow_up_fk
  foreign key (follow_up_plan_id) references public.adoption_followup_plans (id);

create table if not exists public.adoption_followup_checks (
  id uuid primary key default gen_random_uuid(),
  plan_id uuid not null references public.adoption_followup_plans (id) on delete cascade,
  adoption_id uuid not null references public.adoptions (id) on delete cascade,
  due_at timestamptz not null,
  completed_at timestamptz,
  status text not null default 'PENDING',
  notes text,
  welfare_status text,
  evidence_ref text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint adoption_followup_checks_status_chk
    check (status = any (array['PENDING','COMPLETED','OVERDUE','CANCELLED']::text[])),
  constraint adoption_followup_checks_welfare_chk
    check (
      welfare_status is null
      or welfare_status = any (array['GOOD','NEEDS_ATTENTION','CRITICAL','UNKNOWN']::text[])
    )
);

create index if not exists adoption_followup_checks_plan_idx on public.adoption_followup_checks (plan_id);
create index if not exists adoption_followup_checks_due_idx on public.adoption_followup_checks (due_at);

-- ---------------------------------------------------------------------------
-- 2. RLS (nunca públicas; writes directos denegados)
-- ---------------------------------------------------------------------------
alter table public.adoption_interviews enable row level security;
alter table public.adoption_document_requirements enable row level security;
alter table public.adoption_agreements enable row level security;
alter table public.adoption_finalizations enable row level security;
alter table public.adoption_followup_plans enable row level security;
alter table public.adoption_followup_checks enable row level security;

create or replace function public._m09_can_access_adoption_process(p_adoption_id uuid, p_actor uuid default auth.uid())
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_actor is null or p_adoption_id is null then
    return false;
  end if;
  if public._m09_actor_can_manage_adoption(p_adoption_id, p_actor) then
    return true;
  end if;
  return exists (
    select 1 from public.adoption_applications a
    where a.adoption_id = p_adoption_id
      and a.applicant_user_id = p_actor
      and a.status = 'ACCEPTED'
  );
end;
$$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'adoption_interviews',
    'adoption_document_requirements',
    'adoption_agreements',
    'adoption_finalizations',
    'adoption_followup_plans',
    'adoption_followup_checks'
  ]
  loop
    execute format('drop policy if exists %I_select_m09 on public.%I', t, t);
    execute format('drop policy if exists %I_write_m09 on public.%I', t, t);
    execute format(
      'create policy %I_select_m09 on public.%I for select to authenticated using (public._m09_can_access_adoption_process(adoption_id, auth.uid()))',
      t, t
    );
    execute format(
      'create policy %I_ins_m09 on public.%I for insert to authenticated with check (false)',
      t, t
    );
    execute format(
      'create policy %I_upd_m09 on public.%I for update to authenticated using (false)',
      t, t
    );
    execute format(
      'create policy %I_del_m09 on public.%I for delete to authenticated using (false)',
      t, t
    );
    execute format('revoke all on table public.%I from public, anon', t);
    execute format('grant select on table public.%I to authenticated', t);
    execute format('grant all on table public.%I to service_role', t);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. RPCs entrevistas
-- ---------------------------------------------------------------------------
create or replace function public.m09_schedule_interview(
  p_adoption_id uuid,
  p_application_id uuid,
  p_scheduled_at timestamptz,
  p_type text default 'IN_PERSON',
  p_location_or_link text default null,
  p_notes text default null
) returns public.adoption_interviews
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_app public.adoption_applications;
  v_row public.adoption_interviews;
begin
  if p_adoption_id is null or p_application_id is null then
    raise exception 'INTERVIEW_NOT_FOUND';
  end if;
  if not public._m09_actor_can_manage_adoption(p_adoption_id, v_actor) then
    raise exception 'INTERVIEW_NOT_ALLOWED';
  end if;
  select * into v_app from public.adoption_applications where id = p_application_id;
  if not found or v_app.adoption_id is distinct from p_adoption_id then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  if v_app.status is distinct from 'ACCEPTED' then
    raise exception 'INTERVIEW_NOT_ALLOWED';
  end if;
  if exists (select 1 from public.adoption_finalizations f where f.adoption_id = p_adoption_id) then
    raise exception 'ADOPTION_ALREADY_FINALIZED';
  end if;
  insert into public.adoption_interviews (
    adoption_id, application_id, scheduled_at, interview_type, location_or_link, notes, status, created_by
  ) values (
    p_adoption_id, p_application_id, p_scheduled_at,
    coalesce(nullif(upper(trim(p_type)), ''), 'IN_PERSON'),
    nullif(trim(coalesce(p_location_or_link, '')), ''),
    nullif(trim(coalesce(p_notes, '')), ''),
    'SCHEDULED', v_actor
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m09_confirm_interview(p_interview_id uuid)
returns public.adoption_interviews
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_interviews;
  v_app public.adoption_applications;
begin
  if p_interview_id is null then raise exception 'INTERVIEW_NOT_FOUND'; end if;
  select * into v_row from public.adoption_interviews where id = p_interview_id;
  if not found then raise exception 'INTERVIEW_NOT_FOUND'; end if;
  select * into v_app from public.adoption_applications where id = v_row.application_id;
  if v_app.applicant_user_id is distinct from v_actor
     and not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'INTERVIEW_NOT_ALLOWED';
  end if;
  if v_row.status = 'CONFIRMED' then return v_row; end if;
  if v_row.status is distinct from 'SCHEDULED' then
    raise exception 'INTERVIEW_INVALID_TRANSITION';
  end if;
  update public.adoption_interviews set status = 'CONFIRMED', updated_at = timezone('utc', now())
  where id = p_interview_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m09_complete_interview(p_interview_id uuid, p_outcome text default null)
returns public.adoption_interviews
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_interviews;
begin
  if p_interview_id is null then raise exception 'INTERVIEW_NOT_FOUND'; end if;
  select * into v_row from public.adoption_interviews where id = p_interview_id;
  if not found then raise exception 'INTERVIEW_NOT_FOUND'; end if;
  if not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'INTERVIEW_NOT_ALLOWED';
  end if;
  if v_row.status = 'COMPLETED' then return v_row; end if;
  if v_row.status not in ('SCHEDULED', 'CONFIRMED') then
    raise exception 'INTERVIEW_INVALID_TRANSITION';
  end if;
  update public.adoption_interviews set
    status = 'COMPLETED',
    completed_at = timezone('utc', now()),
    outcome = nullif(trim(coalesce(p_outcome, '')), ''),
    updated_at = timezone('utc', now())
  where id = p_interview_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m09_cancel_interview(p_interview_id uuid)
returns public.adoption_interviews
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_interviews;
begin
  if p_interview_id is null then raise exception 'INTERVIEW_NOT_FOUND'; end if;
  select * into v_row from public.adoption_interviews where id = p_interview_id;
  if not found then raise exception 'INTERVIEW_NOT_FOUND'; end if;
  if not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'INTERVIEW_NOT_ALLOWED';
  end if;
  if v_row.status = 'CANCELLED' then return v_row; end if;
  if v_row.status = 'COMPLETED' then raise exception 'INTERVIEW_ALREADY_COMPLETED'; end if;
  update public.adoption_interviews set status = 'CANCELLED', updated_at = timezone('utc', now())
  where id = p_interview_id returning * into v_row;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RPCs documentos
-- ---------------------------------------------------------------------------
create or replace function public.m09_request_document(
  p_adoption_id uuid,
  p_application_id uuid,
  p_doc_type text,
  p_required boolean default true
) returns public.adoption_document_requirements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_app public.adoption_applications;
  v_row public.adoption_document_requirements;
begin
  if not public._m09_actor_can_manage_adoption(p_adoption_id, v_actor) then
    raise exception 'DOCUMENT_FORBIDDEN';
  end if;
  select * into v_app from public.adoption_applications where id = p_application_id;
  if not found or v_app.adoption_id is distinct from p_adoption_id or v_app.status <> 'ACCEPTED' then
    raise exception 'DOCUMENT_FORBIDDEN';
  end if;
  insert into public.adoption_document_requirements (
    adoption_id, application_id, doc_type, required, status
  ) values (
    p_adoption_id, p_application_id, upper(trim(p_doc_type)),
    coalesce(p_required, true),
    case when coalesce(p_required, true) then 'PENDING' else 'NOT_REQUIRED' end
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m09_submit_document_reference(
  p_requirement_id uuid,
  p_storage_path text
) returns public.adoption_document_requirements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_document_requirements;
  v_app public.adoption_applications;
  v_path text := trim(coalesce(p_storage_path, ''));
begin
  if p_requirement_id is null then raise exception 'DOCUMENT_REQUIREMENT_NOT_FOUND'; end if;
  select * into v_row from public.adoption_document_requirements where id = p_requirement_id;
  if not found then raise exception 'DOCUMENT_REQUIREMENT_NOT_FOUND'; end if;
  select * into v_app from public.adoption_applications where id = v_row.application_id;
  if v_app.applicant_user_id is distinct from v_actor then
    raise exception 'DOCUMENT_FORBIDDEN';
  end if;
  if v_path = '' or v_path ~* 'object/public/leover' or (
       v_path ~* '^https?://' and v_path ~* '/leover/'
     ) then
    raise exception 'DOCUMENT_UNSAFE_REFERENCE';
  end if;
  update public.adoption_document_requirements set
    storage_path = v_path,
    status = 'SUBMITTED',
    submitted_at = timezone('utc', now()),
    updated_at = timezone('utc', now())
  where id = p_requirement_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m09_review_document(
  p_requirement_id uuid,
  p_approve boolean,
  p_rejection_reason text default null
) returns public.adoption_document_requirements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_document_requirements;
begin
  if p_requirement_id is null then raise exception 'DOCUMENT_REQUIREMENT_NOT_FOUND'; end if;
  select * into v_row from public.adoption_document_requirements where id = p_requirement_id;
  if not found then raise exception 'DOCUMENT_REQUIREMENT_NOT_FOUND'; end if;
  if not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'DOCUMENT_FORBIDDEN';
  end if;
  if v_row.status = 'APPROVED' and p_approve then return v_row; end if;
  if v_row.status is distinct from 'SUBMITTED' then
    raise exception 'DOCUMENT_FORBIDDEN';
  end if;
  update public.adoption_document_requirements set
    status = case when p_approve then 'APPROVED' else 'REJECTED' end,
    rejection_reason = case when p_approve then null else nullif(trim(coalesce(p_rejection_reason, '')), '') end,
    reviewed_at = timezone('utc', now()),
    updated_at = timezone('utc', now())
  where id = p_requirement_id returning * into v_row;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs acuerdo
-- ---------------------------------------------------------------------------
create or replace function public.m09_create_adoption_agreement(
  p_adoption_id uuid,
  p_application_id uuid,
  p_terms_version text,
  p_terms_snapshot text
) returns public.adoption_agreements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_app public.adoption_applications;
  v_ad public.adoptions;
  v_row public.adoption_agreements;
begin
  if not public._m09_actor_can_manage_adoption(p_adoption_id, v_actor) then
    raise exception 'AGREEMENT_FORBIDDEN';
  end if;
  select * into v_app from public.adoption_applications where id = p_application_id;
  if not found or v_app.adoption_id <> p_adoption_id or v_app.status <> 'ACCEPTED' then
    raise exception 'AGREEMENT_FORBIDDEN';
  end if;
  if exists (select 1 from public.adoption_agreements a where a.adoption_id = p_adoption_id and a.status <> 'CANCELLED') then
    raise exception 'AGREEMENT_ALREADY_EXISTS';
  end if;
  select * into v_ad from public.adoptions where id = p_adoption_id;
  insert into public.adoption_agreements (
    adoption_id, application_id, adopter_user_id, publisher_user_id, publisher_organization_id,
    terms_version, terms_snapshot, status
  ) values (
    p_adoption_id, p_application_id, v_app.applicant_user_id, v_ad.publisher_id, v_ad.publisher_organization_id,
    coalesce(nullif(trim(p_terms_version), ''), '1.0'),
    trim(p_terms_snapshot),
    'PENDING_ADOPTER'
  ) returning * into v_row;
  return v_row;
exception when unique_violation then
  raise exception 'AGREEMENT_ALREADY_EXISTS';
end;
$$;

create or replace function public.m09_accept_adoption_agreement(p_agreement_id uuid)
returns public.adoption_agreements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_agreements;
begin
  if p_agreement_id is null then raise exception 'AGREEMENT_NOT_FOUND'; end if;
  select * into v_row from public.adoption_agreements where id = p_agreement_id for update;
  if not found then raise exception 'AGREEMENT_NOT_FOUND'; end if;
  if v_row.status = 'ACCEPTED' then raise exception 'AGREEMENT_ALREADY_ACCEPTED'; end if;
  if v_row.status = 'CANCELLED' then raise exception 'AGREEMENT_NOT_FOUND'; end if;

  if v_row.adopter_user_id = v_actor and v_row.adopter_accepted_at is null then
    v_row.adopter_accepted_at := timezone('utc', now());
  elsif public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor)
        and v_row.publisher_accepted_at is null then
    v_row.publisher_accepted_at := timezone('utc', now());
  else
    raise exception 'AGREEMENT_FORBIDDEN';
  end if;

  if v_row.adopter_accepted_at is not null and v_row.publisher_accepted_at is not null then
    v_row.status := 'ACCEPTED';
  elsif v_row.adopter_accepted_at is null then
    v_row.status := 'PENDING_ADOPTER';
  else
    v_row.status := 'PENDING_PUBLISHER';
  end if;

  update public.adoption_agreements set
    adopter_accepted_at = v_row.adopter_accepted_at,
    publisher_accepted_at = v_row.publisher_accepted_at,
    status = v_row.status,
    updated_at = timezone('utc', now())
  where id = p_agreement_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m09_cancel_adoption_agreement(p_agreement_id uuid)
returns public.adoption_agreements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_agreements;
begin
  select * into v_row from public.adoption_agreements where id = p_agreement_id;
  if not found then raise exception 'AGREEMENT_NOT_FOUND'; end if;
  if not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'AGREEMENT_FORBIDDEN';
  end if;
  if v_row.status = 'ACCEPTED' then raise exception 'AGREEMENT_ALREADY_ACCEPTED'; end if;
  if v_row.status = 'CANCELLED' then return v_row; end if;
  update public.adoption_agreements set status = 'CANCELLED', updated_at = timezone('utc', now())
  where id = p_agreement_id returning * into v_row;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Finalización transaccional
-- ---------------------------------------------------------------------------
create or replace function public.m09_finalize_adoption(p_adoption_id uuid)
returns public.adoption_finalizations
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_ad public.adoptions;
  v_app public.adoption_applications;
  v_fin public.adoption_finalizations;
  v_plan public.adoption_followup_plans;
  v_pet public.pets;
  v_prev text;
  v_now timestamptz := timezone('utc', now());
begin
  if p_adoption_id is null then raise exception 'ADOPTION_NOT_FOUND'; end if;
  if not public._m09_actor_can_manage_adoption(p_adoption_id, v_actor) then
    raise exception 'FORBIDDEN';
  end if;

  select * into v_fin from public.adoption_finalizations where adoption_id = p_adoption_id;
  if found then
    return v_fin; -- idempotente
  end if;

  select * into v_ad from public.adoptions where id = p_adoption_id for update;
  if not found then raise exception 'ADOPTION_NOT_FOUND'; end if;
  if v_ad.status = 'ADOPTED' then raise exception 'ADOPTION_ALREADY_FINALIZED'; end if;
  if v_ad.status is distinct from 'PAUSED' then
    raise exception 'ADOPTION_NOT_READY_TO_FINALIZE';
  end if;

  select * into v_app from public.adoption_applications
  where adoption_id = p_adoption_id and status = 'ACCEPTED';
  if not found then raise exception 'ADOPTION_NOT_READY_TO_FINALIZE'; end if;
  if (select count(*) from public.adoption_applications
      where adoption_id = p_adoption_id and status = 'ACCEPTED') <> 1 then
    raise exception 'ADOPTION_NOT_READY_TO_FINALIZE';
  end if;

  if not exists (
    select 1 from public.adoption_interviews i
    where i.adoption_id = p_adoption_id and i.status = 'COMPLETED'
  ) then
    raise exception 'ADOPTION_NOT_READY_TO_FINALIZE';
  end if;

  if not exists (
    select 1 from public.adoption_document_requirements d
    where d.adoption_id = p_adoption_id and d.required
  ) then
    raise exception 'DOCUMENT_NOT_APPROVED';
  end if;
  if exists (
    select 1 from public.adoption_document_requirements d
    where d.adoption_id = p_adoption_id and d.required
      and d.status not in ('APPROVED', 'NOT_REQUIRED')
  ) then
    raise exception 'DOCUMENT_NOT_APPROVED';
  end if;

  if not exists (
    select 1 from public.adoption_agreements g
    where g.adoption_id = p_adoption_id and g.status = 'ACCEPTED'
  ) then
    raise exception 'AGREEMENT_NOT_ACCEPTED';
  end if;

  -- Transferencia de PRINCIPAL al adoptante (mascota ACTIVE)
  if v_ad.pet_id is not null then
    select * into v_pet from public.pets where id = v_ad.pet_id for update;
    if not found then raise exception 'ADOPTION_TRANSFER_FAILED'; end if;
    if v_pet.status is distinct from 'ACTIVE' then
      raise exception 'ADOPTION_TRANSFER_FAILED';
    end if;

    update public.pet_responsibilities set
      status = 'SUPERSEDED',
      ends_at = coalesce(ends_at, greatest(starts_at, v_now)),
      revoked_at = coalesce(revoked_at, v_now),
      revoked_by = coalesce(revoked_by, v_actor)
    where pet_id = v_ad.pet_id and role_code = 'PRINCIPAL' and status = 'ACTIVE';

    insert into public.pet_responsibilities (
      pet_id, role_code, person_id, organization_id, status,
      starts_at, created_by, accepted_at, reason
    ) values (
      v_ad.pet_id, 'PRINCIPAL', v_app.applicant_user_id, null, 'ACTIVE',
      v_now, v_actor, v_now, 'ADOPTION_FINALIZED'
    );

    perform public._m08_sync_owner_id_from_principal(v_ad.pet_id);

    v_prev := v_pet.status;
    -- M08 no tiene ciclo ADOPTED: ARCHIVED + historial reason ADOPTED
    update public.pets set
      status = 'ARCHIVED',
      archived_at = v_now,
      updated_at = v_now
    where id = v_ad.pet_id;

    insert into public.pet_status_history (
      pet_id, previous_status, new_status, reason, changed_by, changed_at
    ) values (
      v_ad.pet_id, v_prev, 'ARCHIVED', 'ADOPTED', v_actor, v_now
    );
  end if;

  update public.adoptions set status = 'ADOPTED', updated_at = v_now
  where id = p_adoption_id;

  insert into public.adoption_followup_plans (adoption_id, adopter_user_id, status, created_at)
  values (p_adoption_id, v_app.applicant_user_id, 'ACTIVE', v_now)
  returning * into v_plan;

  insert into public.adoption_followup_checks (plan_id, adoption_id, due_at, status)
  values
    (v_plan.id, p_adoption_id, v_now + interval '7 days', 'PENDING'),
    (v_plan.id, p_adoption_id, v_now + interval '30 days', 'PENDING'),
    (v_plan.id, p_adoption_id, v_now + interval '90 days', 'PENDING');

  insert into public.adoption_finalizations (
    adoption_id, application_id, pet_id, adopter_user_id, finalized_at, finalized_by, follow_up_plan_id
  ) values (
    p_adoption_id, v_app.id, v_ad.pet_id, v_app.applicant_user_id, v_now, v_actor, v_plan.id
  ) returning * into v_fin;

  return v_fin;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. Follow-up RPCs
-- ---------------------------------------------------------------------------
create or replace function public.m09_list_followup_plans(p_adoption_id uuid default null)
returns setof public.adoption_followup_plans
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
begin
  return query
  select p.* from public.adoption_followup_plans p
  where (p_adoption_id is null or p.adoption_id = p_adoption_id)
    and public._m09_can_access_adoption_process(p.adoption_id, v_actor)
  order by p.created_at desc;
end;
$$;

create or replace function public.m09_get_followup_plan(p_adoption_id uuid)
returns public.adoption_followup_plans
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_followup_plans;
begin
  if p_adoption_id is null then raise exception 'FOLLOWUP_NOT_FOUND'; end if;
  select * into v_row from public.adoption_followup_plans where adoption_id = p_adoption_id;
  if not found then raise exception 'FOLLOWUP_NOT_FOUND'; end if;
  if not public._m09_can_access_adoption_process(p_adoption_id, v_actor) then
    raise exception 'FOLLOWUP_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m09_complete_followup_check(
  p_check_id uuid,
  p_notes text default null,
  p_welfare_status text default 'UNKNOWN',
  p_evidence_ref text default null
) returns public.adoption_followup_checks
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_followup_checks;
  v_plan public.adoption_followup_plans;
begin
  if p_check_id is null then raise exception 'FOLLOWUP_NOT_FOUND'; end if;
  select * into v_row from public.adoption_followup_checks where id = p_check_id for update;
  if not found then raise exception 'FOLLOWUP_NOT_FOUND'; end if;
  if not public._m09_can_access_adoption_process(v_row.adoption_id, v_actor) then
    raise exception 'FOLLOWUP_FORBIDDEN';
  end if;
  if v_row.status = 'COMPLETED' then raise exception 'FOLLOWUP_ALREADY_COMPLETED'; end if;

  update public.adoption_followup_checks set
    status = 'COMPLETED',
    completed_at = timezone('utc', now()),
    notes = nullif(trim(coalesce(p_notes, '')), ''),
    welfare_status = coalesce(nullif(upper(trim(p_welfare_status)), ''), 'UNKNOWN'),
    evidence_ref = nullif(trim(coalesce(p_evidence_ref, '')), ''),
    updated_at = timezone('utc', now())
  where id = p_check_id returning * into v_row;

  if not exists (
    select 1 from public.adoption_followup_checks c
    where c.plan_id = v_row.plan_id and c.status not in ('COMPLETED', 'CANCELLED')
  ) then
    update public.adoption_followup_plans set
      status = 'COMPLETED',
      completed_at = timezone('utc', now())
    where id = v_row.plan_id;
  end if;

  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Grants
-- ---------------------------------------------------------------------------
revoke all on function public._m09_can_access_adoption_process(uuid, uuid) from public;
revoke all on function public.m09_schedule_interview(uuid, uuid, timestamptz, text, text, text) from public;
revoke all on function public.m09_confirm_interview(uuid) from public;
revoke all on function public.m09_complete_interview(uuid, text) from public;
revoke all on function public.m09_cancel_interview(uuid) from public;
revoke all on function public.m09_request_document(uuid, uuid, text, boolean) from public;
revoke all on function public.m09_submit_document_reference(uuid, text) from public;
revoke all on function public.m09_review_document(uuid, boolean, text) from public;
revoke all on function public.m09_create_adoption_agreement(uuid, uuid, text, text) from public;
revoke all on function public.m09_accept_adoption_agreement(uuid) from public;
revoke all on function public.m09_cancel_adoption_agreement(uuid) from public;
revoke all on function public.m09_finalize_adoption(uuid) from public;
revoke all on function public.m09_list_followup_plans(uuid) from public;
revoke all on function public.m09_get_followup_plan(uuid) from public;
revoke all on function public.m09_complete_followup_check(uuid, text, text, text) from public;

grant execute on function public.m09_schedule_interview(uuid, uuid, timestamptz, text, text, text) to authenticated;
grant execute on function public.m09_confirm_interview(uuid) to authenticated;
grant execute on function public.m09_complete_interview(uuid, text) to authenticated;
grant execute on function public.m09_cancel_interview(uuid) to authenticated;
grant execute on function public.m09_request_document(uuid, uuid, text, boolean) to authenticated;
grant execute on function public.m09_submit_document_reference(uuid, text) to authenticated;
grant execute on function public.m09_review_document(uuid, boolean, text) to authenticated;
grant execute on function public.m09_create_adoption_agreement(uuid, uuid, text, text) to authenticated;
grant execute on function public.m09_accept_adoption_agreement(uuid) to authenticated;
grant execute on function public.m09_cancel_adoption_agreement(uuid) to authenticated;
grant execute on function public.m09_finalize_adoption(uuid) to authenticated;
grant execute on function public.m09_list_followup_plans(uuid) to authenticated;
grant execute on function public.m09_get_followup_plan(uuid) to authenticated;
grant execute on function public.m09_complete_followup_check(uuid, text, text, text) to authenticated;

comment on function public.m09_finalize_adoption(uuid) is
  'M09: finalize adoption — validate process, ADOPTED publication, ARCHIVED pet+history ADOPTED, transfer PRINCIPAL, create follow-up 7/30/90.';

commit;
