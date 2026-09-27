-- 1093: transit request/select, adoption pause/no-transfer, follow-up, vet invite email + auto-link.
-- Does not edit 1092 or earlier.

-- ---------- Transit ----------
create table if not exists public.foster_care_requests (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id),
  requested_by uuid not null references public.persons(user_id),
  needs text null,
  notes text null,
  status text not null default 'REQUESTED'
    check (status in ('REQUESTED', 'MATCHED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
  selected_application_id uuid null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);
create index if not exists foster_care_requests_pet_idx on public.foster_care_requests (pet_id, created_at desc);
alter table public.foster_care_requests enable row level security;
revoke all on table public.foster_care_requests from anon, authenticated;

create table if not exists public.foster_care_applications (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.foster_care_requests(id) on delete cascade,
  foster_user_id uuid not null references public.persons(user_id),
  status text not null default 'PENDING'
    check (status in ('PENDING', 'SELECTED', 'NOT_SELECTED', 'WITHDRAWN')),
  created_at timestamptz not null default timezone('utc', now()),
  unique (request_id, foster_user_id)
);
alter table public.foster_care_applications enable row level security;
revoke all on table public.foster_care_applications from anon, authenticated;

create or replace function public.canon_upsert_foster_profile(
  p_capacity integer default 1,
  p_locality_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.foster_profiles (user_id, capacity, locality_id, active)
  values (
    auth.uid(),
    case when coalesce(p_capacity, 1) <= 1 then 1 when p_capacity = 2 then 2 else 3 end,
    p_locality_id,
    true
  )
  on conflict (user_id) do update
    set capacity = excluded.capacity,
        locality_id = excluded.locality_id,
        active = true;
  return auth.uid();
end;
$$;

create or replace function public.canon_update_foster_preferences(
  p_capacity integer default 1,
  p_locality_id text default null,
  p_active boolean default true,
  p_species_pref text default null,
  p_age_pref text default null,
  p_accepts_treatment boolean default null,
  p_other_animals_ok boolean default null,
  p_notes text default null
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public.canon_upsert_foster_profile(p_capacity, p_locality_id);
  update public.foster_profiles
     set active = coalesce(p_active, active),
         species_pref = p_species_pref,
         age_pref = p_age_pref,
         accepts_treatment = p_accepts_treatment,
         other_animals_ok = p_other_animals_ok,
         notes = p_notes
   where user_id = auth.uid();
  return true;
end;
$$;

create or replace function public.canon_request_foster_for_pet(
  p_pet_id uuid,
  p_needs text default null,
  p_notes text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_pet_holder(auth.uid(), p_pet_id) then raise exception 'FORBIDDEN'; end if;
  insert into public.foster_care_requests (pet_id, requested_by, needs, notes)
  values (p_pet_id, auth.uid(), p_needs, p_notes)
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_apply_to_foster_request(p_request_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_req public.foster_care_requests%rowtype;
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_req from public.foster_care_requests where id = p_request_id for update;
  if not found or v_req.status <> 'REQUESTED' then raise exception 'NOT_FOUND'; end if;
  if not exists (
    select 1
      from public.foster_profiles fp
      join public.person_capabilities c on c.user_id = fp.user_id and c.capability = 'FOSTER'
     where fp.user_id = auth.uid()
       and fp.active
       and c.verification_status = 'VERIFIED'
       and c.active
  ) then
    raise exception 'FOSTER_NOT_ELIGIBLE';
  end if;
  if not public._canon_person_has_base_location(auth.uid()) then
    raise exception 'BASE_LOCATION_REQUIRED';
  end if;
  insert into public.foster_care_applications (request_id, foster_user_id)
  values (p_request_id, auth.uid())
  on conflict (request_id, foster_user_id) do update set status = 'PENDING'
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_select_foster_applicant(p_application_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_app public.foster_care_applications%rowtype;
  v_req public.foster_care_requests%rowtype;
  v_place uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_app from public.foster_care_applications where id = p_application_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  select * into v_req from public.foster_care_requests where id = v_app.request_id for update;
  if v_req.requested_by is distinct from auth.uid()
     and not public._acl_pet_holder(auth.uid(), v_req.pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v_req.status not in ('REQUESTED', 'MATCHED') then raise exception 'STATUS_INVALID'; end if;

  update public.foster_care_applications
     set status = 'SELECTED'
   where id = p_application_id;
  update public.foster_care_applications
     set status = 'NOT_SELECTED'
   where request_id = v_req.id and id is distinct from p_application_id and status = 'PENDING';
  update public.foster_care_requests
     set status = 'ACTIVE',
         selected_application_id = p_application_id,
         updated_at = timezone('utc', now())
   where id = v_req.id;

  insert into public.foster_placements (pet_id, foster_user_id, status, created_by)
  values (v_req.pet_id, v_app.foster_user_id, 'OPEN', auth.uid())
  returning id into v_place;

  if not exists (
    select 1 from public.pet_responsibility_links l
     where l.pet_id = v_req.pet_id and l.holder_person_id = v_app.foster_user_id and l.status = 'ACTIVE'
  ) then
    insert into public.pet_responsibility_links (
      pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
    ) values (v_req.pet_id, 'PERSON', v_app.foster_user_id, 'AUTHORIZED', auth.uid());
  end if;
  return v_place;
end;
$$;

create or replace function public.canon_list_open_foster_requests()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', r.id,
      'pet_id', r.pet_id,
      'pet_name', p.name,
      'species', p.species_code,
      'sex', p.sex,
      'needs', r.needs,
      'notes', r.notes,
      'status', r.status,
      'created_at', r.created_at
    ) order by r.created_at desc)
    from public.foster_care_requests r
    join public.pets p on p.id = r.pet_id
   where r.status = 'REQUESTED'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_foster_request_applications(p_request_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_req public.foster_care_requests%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_req from public.foster_care_requests where id = p_request_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_req.requested_by is distinct from auth.uid()
     and not public._acl_pet_holder(auth.uid(), v_req.pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', a.id,
      'foster_user_id', a.foster_user_id,
      'foster_name', coalesce(p.display_name, p.username),
      'status', a.status,
      'locality_id', fp.locality_id,
      'created_at', a.created_at
    ) order by a.created_at)
    from public.foster_care_applications a
    join public.persons p on p.user_id = a.foster_user_id
    left join public.foster_profiles fp on fp.user_id = a.foster_user_id
   where a.request_id = p_request_id
  ), '[]'::jsonb);
end;
$$;

-- ---------- Adoption statuses + accept/pause ----------
alter table public.adoption_applications
  drop constraint if exists adoption_applications_status_check;
alter table public.adoption_applications
  add constraint adoption_applications_status_check
  check (status in (
    'PENDING', 'SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PAUSED',
    'REJECTED', 'WITHDRAWN', 'COMPLETED', 'CLOSED'
  ));

create or replace function public.canon_accept_adoption_application(p_application_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_app public.adoption_applications%rowtype;
  v_pub public.adoption_publications%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_app from public.adoption_applications where id = p_application_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  select * into v_pub from public.adoption_publications where id = v_app.publication_id for update;
  if v_pub.published_by is distinct from auth.uid()
     and (v_pub.pet_id is null or not public._acl_pet_holder(auth.uid(), v_pub.pet_id)) then
    raise exception 'FORBIDDEN';
  end if;
  update public.adoption_applications
     set status = 'ACCEPTED'
   where id = p_application_id;
  update public.adoption_applications
     set status = 'PAUSED'
   where publication_id = v_app.publication_id
     and id is distinct from p_application_id
     and status in ('PENDING', 'SUBMITTED', 'IN_REVIEW');
  return true;
end;
$$;

create or replace function public.canon_reactivate_adoption_application(p_application_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_app public.adoption_applications%rowtype;
  v_pub public.adoption_publications%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_app from public.adoption_applications where id = p_application_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  select * into v_pub from public.adoption_publications where id = v_app.publication_id;
  if v_pub.published_by is distinct from auth.uid()
     and not public._acl_pet_holder(auth.uid(), v_pub.pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v_app.status <> 'PAUSED' then raise exception 'STATUS_INVALID'; end if;
  if exists (
    select 1 from public.adoption_applications x
     where x.publication_id = v_app.publication_id and x.status = 'ACCEPTED'
  ) then
    raise exception 'APPLICATION_ALREADY_ACCEPTED';
  end if;
  update public.adoption_applications set status = 'SUBMITTED' where id = p_application_id;
  return true;
end;
$$;

create or replace function public.canon_open_adoption_application_channel(p_application_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_app public.adoption_applications%rowtype;
  v_pub public.adoption_publications%rowtype;
  v_conv uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_app from public.adoption_applications where id = p_application_id;
  select * into v_pub from public.adoption_publications where id = v_app.publication_id;
  if auth.uid() is distinct from v_app.applicant_user_id
     and auth.uid() is distinct from v_pub.published_by
     and not public._acl_pet_holder(auth.uid(), v_pub.pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  v_conv := public.canon_start_conversation(
    'PERSON',
    case when auth.uid() = v_app.applicant_user_id then v_pub.published_by else v_app.applicant_user_id end,
    null,
    'Hola, te escribo por la postulación de adopción.'
  );
  return v_conv;
end;
$$;

-- Deprecate M09 transfer-on-finalize without deleting history.
do $m09$
begin
  if exists (
    select 1 from information_schema.columns
     where table_schema = 'public' and table_name = 'adoption_applications' and column_name = 'adoption_id'
  ) and exists (select 1 from pg_proc where proname = 'm09_adoption_application_statuses') then
    execute $fn$
      create or replace function public.m09_adoption_application_statuses()
      returns text[] language sql immutable as $$
        select array['SUBMITTED','UNDER_REVIEW','ACCEPTED','REJECTED','WITHDRAWN','PAUSED','COMPLETED','CLOSED']::text[];
      $$;
    $fn$;
  end if;
  if exists (select 1 from pg_proc where proname = 'm09_accept_application') then
    execute $fn$
      create or replace function public.m09_accept_application(p_application_id uuid)
      returns public.adoption_applications
      language plpgsql security definer set search_path = public as $$
      declare
        v_actor uuid := public._m09_require_authenticated();
        v_row public.adoption_applications%rowtype;
      begin
        select * into v_row from public.adoption_applications a where a.id = p_application_id for update;
        if not found then raise exception 'APPLICATION_NOT_FOUND'; end if;
        update public.adoption_applications set status = 'ACCEPTED', reviewed_at = timezone('utc', now()), reviewed_by = v_actor, updated_at = timezone('utc', now())
         where id = p_application_id returning * into v_row;
        update public.adoption_applications
           set status = 'PAUSED', reviewed_at = timezone('utc', now()), reviewed_by = v_actor, updated_at = timezone('utc', now())
         where adoption_id = v_row.adoption_id and id is distinct from p_application_id
           and status in ('SUBMITTED', 'UNDER_REVIEW', 'PENDING');
        return v_row;
      end;
      $$;
    $fn$;
  end if;
  if exists (select 1 from pg_proc where proname = 'm09_finalize_adoption') then
    execute $fn$
      create or replace function public.m09_finalize_adoption(p_adoption_id uuid)
      returns public.adoption_finalizations
      language plpgsql security definer set search_path = public as $$
      begin
        raise exception 'ADOPTION_USE_CANONICAL_TRANSFER';
      end;
      $$;
    $fn$;
  end if;
end;
$m09$;

-- Transfer completion closes adoption apps + optional follow-up permission.
create table if not exists public.vitacora_followup_plans (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id),
  transfer_id uuid null,
  created_by uuid not null references public.persons(user_id),
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now())
);
create table if not exists public.vitacora_followup_items (
  id uuid primary key default gen_random_uuid(),
  plan_id uuid not null references public.vitacora_followup_plans(id) on delete cascade,
  title text not null,
  due_at timestamptz null,
  evidence_required text null,
  status text not null default 'PENDING' check (status in ('PENDING', 'DONE', 'SKIPPED')),
  notes text null,
  created_at timestamptz not null default timezone('utc', now())
);
alter table public.vitacora_followup_plans enable row level security;
alter table public.vitacora_followup_items enable row level security;
revoke all on table public.vitacora_followup_plans from anon, authenticated;
revoke all on table public.vitacora_followup_items from anon, authenticated;

create or replace function public._canon_complete_adoptions_for_pet(p_pet_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.adoption_applications a
     set status = 'COMPLETED'
    from public.adoption_publications p
   where p.id = a.publication_id
     and p.pet_id = p_pet_id
     and a.status = 'ACCEPTED';
  update public.adoption_applications a
     set status = 'CLOSED'
    from public.adoption_publications p
   where p.id = a.publication_id
     and p.pet_id = p_pet_id
     and a.status in ('PAUSED', 'PENDING', 'SUBMITTED', 'IN_REVIEW');
end;
$$;

create or replace function public._canon_on_care_transfer_accepted()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.status = 'ACCEPTED' and old.status is distinct from 'ACCEPTED' then
    perform public._canon_complete_adoptions_for_pet(new.pet_id);
  end if;
  return new;
end;
$$;

drop trigger if exists pet_care_transfers_adoption_close on public.pet_care_transfers;
create trigger pet_care_transfers_adoption_close
  after update of status on public.pet_care_transfers
  for each row execute function public._canon_on_care_transfer_accepted();

create or replace function public.canon_upsert_followup_plan(
  p_pet_id uuid,
  p_items jsonb default '[]'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_item jsonb;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_pet_holder(auth.uid(), p_pet_id) then raise exception 'FORBIDDEN'; end if;
  insert into public.vitacora_followup_plans (pet_id, created_by)
  values (p_pet_id, auth.uid())
  returning id into v_id;
  for v_item in select * from jsonb_array_elements(coalesce(p_items, '[]'::jsonb))
  loop
    insert into public.vitacora_followup_items (plan_id, title, due_at, evidence_required)
    values (
      v_id,
      coalesce(v_item ->> 'title', 'Seguimiento'),
      nullif(v_item ->> 'due_at', '')::timestamptz,
      v_item ->> 'evidence_required'
    );
  end loop;
  return v_id;
end;
$$;

create or replace function public.canon_list_followup_plan(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_build_object(
      'id', pl.id,
      'status', pl.status,
      'items', coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', i.id, 'title', i.title, 'due_at', i.due_at,
          'evidence_required', i.evidence_required, 'status', i.status, 'notes', i.notes
        ) order by i.created_at)
        from public.vitacora_followup_items i where i.plan_id = pl.id
      ), '[]'::jsonb)
    )
    from public.vitacora_followup_plans pl
   where pl.pet_id = p_pet_id
   order by pl.created_at desc
   limit 1
  ), '{}'::jsonb);
end;
$$;

-- ---------- Email outbox + vet invite ----------
create table if not exists public.email_outbox (
  id uuid primary key default gen_random_uuid(),
  to_email text not null,
  template_code text not null,
  payload jsonb not null default '{}'::jsonb,
  status text not null default 'PENDING' check (status in ('PENDING', 'SENT', 'FAILED')),
  idempotency_key text not null unique,
  attempts integer not null default 0,
  last_error text null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);
alter table public.email_outbox enable row level security;
revoke all on table public.email_outbox from anon, authenticated;
comment on table public.email_outbox is
  'Transactional email abstraction. STAGING needs Auth SMTP or Edge worker to drain PENDING. No provider secret in repo.';

create or replace function public._canon_enqueue_email(
  p_to text,
  p_template text,
  p_key text,
  p_payload jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  insert into public.email_outbox (to_email, template_code, payload, idempotency_key)
  values (lower(btrim(p_to)), p_template, coalesce(p_payload, '{}'::jsonb), p_key)
  on conflict (idempotency_key) do update set updated_at = timezone('utc', now())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public._canon_link_pending_pet_owners_for_user(p_user_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_email text;
  v_count integer := 0;
  r record;
  v_link uuid;
begin
  select lower(btrim(u.email)) into v_email
    from auth.users u
   where u.id = p_user_id
     and u.email_confirmed_at is not null;
  if v_email is null then
    return 0;
  end if;
  for r in
    select i.*
      from public.vet_pending_owner_invites i
     where i.status = 'PENDING_OWNER_EMAIL'
       and i.invited_email_normalized = v_email
  loop
    if not exists (
      select 1 from public.pet_responsibility_links l
       where l.pet_id = r.pet_id and l.holder_person_id = p_user_id and l.status = 'ACTIVE'
    ) then
      insert into public.pet_responsibility_links (
        pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
      ) values (r.pet_id, 'PERSON', p_user_id, 'OWNER', coalesce(r.created_by, p_user_id))
      returning id into v_link;
    end if;
    update public.vet_pending_owner_invites
       set status = 'ACCEPTED', accepted_person_id = p_user_id
     where id = r.id and status = 'PENDING_OWNER_EMAIL';
    v_count := v_count + 1;
  end loop;
  return v_count;
end;
$$;

create or replace function public._canon_on_email_verified_link_pets()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.email_confirmed_at is not null then
    perform public._canon_link_pending_pet_owners_for_user(new.id);
  end if;
  return new;
end;
$$;

drop trigger if exists on_auth_user_link_pending_pets on auth.users;
create trigger on_auth_user_link_pending_pets
  after update of email_confirmed_at on auth.users
  for each row
  when (old.email_confirmed_at is null and new.email_confirmed_at is not null)
  execute function public._canon_on_email_verified_link_pets();

create or replace function public.canon_invite_pending_pet_owner(
  p_pet_id uuid,
  p_email text,
  p_organization_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_existing uuid;
  v_confirmed timestamptz;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_email is null or position('@' in v_email) = 0 then raise exception 'EMAIL_REQUIRED'; end if;
  if not public._acl_pet_holder(auth.uid(), p_pet_id) then raise exception 'FORBIDDEN'; end if;
  insert into public.vet_pending_owner_invites (
    pet_id, organization_id, invited_email_normalized, created_by
  ) values (p_pet_id, p_organization_id, v_email, auth.uid())
  on conflict do nothing
  returning id into v_id;
  if v_id is null then
    select id into v_id
      from public.vet_pending_owner_invites
     where pet_id = p_pet_id
       and invited_email_normalized = v_email
       and status = 'PENDING_OWNER_EMAIL'
     limit 1;
  end if;
  perform public._canon_enqueue_email(
    v_email,
    'vet_pending_owner_invite',
    'vet_invite:' || coalesce(v_id::text, p_pet_id::text) || ':' || v_email,
    jsonb_build_object(
      'subject', 'Una veterinaria agregó a tu mascota a LeoVer',
      'cta', 'Creá o ingresá a LeoVer y verificá este email'
    )
  );
  select p.user_id, u.email_confirmed_at into v_existing, v_confirmed
    from public.persons p
    join auth.users u on u.id = p.user_id
   where lower(btrim(coalesce(u.email, ''))) = v_email
   limit 1;
  if v_existing is not null then
    perform public._canon_emit_lf_event(
      v_existing, p_pet_id, 'vet.pending_owner',
      'Mascota en LeoVer',
      'Una veterinaria cargó una mascota asociada a tu email.',
      'Ver mascota',
      'vet_invite:' || coalesce(v_id::text, p_pet_id::text)
    );
    if v_confirmed is not null then
      perform public._canon_link_pending_pet_owners_for_user(v_existing);
    end if;
  end if;
  return v_id;
end;
$$;

create or replace function public.canon_create_vet_patient(
  p_name text,
  p_species text,
  p_owner_email text,
  p_organization_id uuid default null,
  p_sex text default 'UNKNOWN',
  p_size text default 'UNKNOWN'
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pet uuid;
  v_email text := lower(btrim(coalesce(p_owner_email, '')));
  v_species text := upper(btrim(coalesce(p_species, 'DOG')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_email is null or position('@' in v_email) = 0 then raise exception 'EMAIL_REQUIRED'; end if;
  if not exists (select 1 from public.species s where s.code = v_species) then v_species := 'DOG'; end if;
  insert into public.pets (
    created_by_user_id, name, species_code, sex, size, origin_kind,
    current_custodian_kind, current_custodian_person_id, current_custodian_organization_id
  ) values (
    auth.uid(), coalesce(nullif(btrim(p_name), ''), 'Paciente'),
    v_species,
    case when upper(coalesce(p_sex,'UNKNOWN')) in ('FEMALE','MALE','UNKNOWN') then upper(p_sex) else 'UNKNOWN' end,
    case when upper(coalesce(p_size,'UNKNOWN')) in ('SMALL','MEDIUM','LARGE','UNKNOWN') then upper(p_size) else 'UNKNOWN' end,
    'STANDARD',
    case when p_organization_id is null then 'PERSON' else 'ORGANIZATION' end,
    case when p_organization_id is null then auth.uid() else null end,
    p_organization_id
  ) returning id into v_pet;
  insert into public.vitacora_profiles (pet_id) values (v_pet);
  if p_organization_id is not null then
    insert into public.pet_responsibility_links (
      pet_id, holder_kind, holder_organization_id, role, granted_by_actor_user_id
    ) values (v_pet, 'ORGANIZATION', p_organization_id, 'RESPONSIBLE', auth.uid());
  else
    insert into public.pet_responsibility_links (
      pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
    ) values (v_pet, 'PERSON', auth.uid(), 'AUTHORIZED', auth.uid());
  end if;
  perform public.canon_invite_pending_pet_owner(v_pet, v_email, p_organization_id);
  return v_pet;
end;
$$;

create or replace function public.canon_claim_pending_pet_invites()
returns integer
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return public._canon_link_pending_pet_owners_for_user(auth.uid());
end;
$$;

create or replace function public.canon_list_my_adoption_general_profile()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select to_jsonb(p) from public.adoption_general_profiles p where p.person_id = auth.uid()
  ), '{}'::jsonb);
end;
$$;

grant execute on function public.canon_upsert_foster_profile(integer, text) to authenticated;
grant execute on function public.canon_update_foster_preferences(integer, text, boolean, text, text, boolean, boolean, text) to authenticated;
grant execute on function public.canon_request_foster_for_pet(uuid, text, text) to authenticated;
grant execute on function public.canon_apply_to_foster_request(uuid) to authenticated;
grant execute on function public.canon_select_foster_applicant(uuid) to authenticated;
grant execute on function public.canon_list_open_foster_requests() to authenticated;
grant execute on function public.canon_list_foster_request_applications(uuid) to authenticated;
grant execute on function public.canon_accept_adoption_application(uuid) to authenticated;
grant execute on function public.canon_reactivate_adoption_application(uuid) to authenticated;
grant execute on function public.canon_open_adoption_application_channel(uuid) to authenticated;
grant execute on function public.canon_upsert_followup_plan(uuid, jsonb) to authenticated;
grant execute on function public.canon_list_followup_plan(uuid) to authenticated;
grant execute on function public.canon_invite_pending_pet_owner(uuid, text, uuid) to authenticated;
grant execute on function public.canon_create_vet_patient(text, text, text, uuid, text, text) to authenticated;
grant execute on function public.canon_claim_pending_pet_invites() to authenticated;
grant execute on function public.canon_list_my_adoption_general_profile() to authenticated;
