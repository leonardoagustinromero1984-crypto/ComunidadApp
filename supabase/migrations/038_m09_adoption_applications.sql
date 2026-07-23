-- =============================================================================
-- LeoVer M09 — migración 038: postulaciones a publicaciones de adopción
-- Forward-only sobre 001–037. NO reescribe migraciones anteriores.
-- Asume 037 aplicada previamente. LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Catálogo de estados de postulación
-- ---------------------------------------------------------------------------
create or replace function public.m09_adoption_application_statuses()
returns text[]
language sql
immutable
parallel safe
as $$
  select array['SUBMITTED', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED', 'WITHDRAWN']::text[];
$$;

create or replace function public.m09_adoption_application_active_statuses()
returns text[]
language sql
immutable
parallel safe
as $$
  select array['SUBMITTED', 'UNDER_REVIEW', 'ACCEPTED']::text[];
$$;

-- ---------------------------------------------------------------------------
-- 2. Tabla adoption_applications (nueva; legacy adoption_requests queda intacta)
-- ---------------------------------------------------------------------------
create table if not exists public.adoption_applications (
  id uuid primary key default gen_random_uuid(),
  adoption_id uuid not null references public.adoptions (id) on delete cascade,
  applicant_user_id uuid not null references public.users (id) on delete cascade,
  message text not null,
  housing_type text,
  has_other_pets boolean,
  previous_experience text,
  contact_phone text,
  status text not null default 'SUBMITTED',
  submitted_at timestamptz not null default timezone('utc', now()),
  reviewed_at timestamptz,
  reviewed_by uuid references public.users (id) on delete set null,
  rejection_reason text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint adoption_applications_status_chk
    check (status = any (public.m09_adoption_application_statuses())),
  constraint adoption_applications_message_len
    check (char_length(trim(message)) between 1 and 2000),
  constraint adoption_applications_phone_len
    check (contact_phone is null or char_length(contact_phone) <= 40)
);

create index if not exists adoption_applications_adoption_id_idx
  on public.adoption_applications (adoption_id);

create index if not exists adoption_applications_applicant_user_id_idx
  on public.adoption_applications (applicant_user_id);

create index if not exists adoption_applications_status_idx
  on public.adoption_applications (status);

-- Una sola postulación activa por usuario y publicación
create unique index if not exists adoption_applications_one_active_uidx
  on public.adoption_applications (adoption_id, applicant_user_id)
  where status = any (public.m09_adoption_application_active_statuses());

-- Como máximo una postulación ACCEPTED por publicación
create unique index if not exists adoption_applications_one_accepted_uidx
  on public.adoption_applications (adoption_id)
  where status = 'ACCEPTED';

-- ---------------------------------------------------------------------------
-- 3. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m09_actor_can_manage_adoption(
  p_adoption_id uuid,
  p_actor uuid default auth.uid()
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row public.adoptions;
begin
  if p_actor is null or p_adoption_id is null then
    return false;
  end if;
  select * into v_row from public.adoptions a where a.id = p_adoption_id;
  if not found then
    return false;
  end if;
  if v_row.publisher_id = p_actor then
    return true;
  end if;
  if v_row.pet_id is not null and public._m09_actor_can_manage_pet(v_row.pet_id, p_actor) then
    return true;
  end if;
  return false;
end;
$$;

create or replace function public._m09_application_enrich(p_app public.adoption_applications)
returns table (
  id uuid,
  adoption_id uuid,
  applicant_user_id uuid,
  applicant_name text,
  message text,
  housing_type text,
  has_other_pets boolean,
  previous_experience text,
  contact_phone text,
  status text,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  reviewed_by uuid,
  rejection_reason text,
  adoption_title text,
  pet_name text,
  pet_photo_url text,
  created_at timestamptz,
  updated_at timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
  select
    p_app.id,
    p_app.adoption_id,
    p_app.applicant_user_id,
    coalesce(u.name, '')::text as applicant_name,
    p_app.message,
    p_app.housing_type,
    p_app.has_other_pets,
    p_app.previous_experience,
    p_app.contact_phone,
    p_app.status,
    p_app.submitted_at,
    p_app.reviewed_at,
    p_app.reviewed_by,
    p_app.rejection_reason,
    coalesce(nullif(trim(a.title), ''), a.name, '')::text as adoption_title,
    coalesce(a.name, '')::text as pet_name,
    a.photo_url as pet_photo_url,
    p_app.created_at,
    p_app.updated_at
  from (select 1) as _
  left join public.adoptions a on a.id = p_app.adoption_id
  left join public.users u on u.id = p_app.applicant_user_id;
$$;

-- ---------------------------------------------------------------------------
-- 4. RLS — postulaciones nunca públicas; writes directos denegados
-- ---------------------------------------------------------------------------
alter table public.adoption_applications enable row level security;

drop policy if exists adoption_applications_select_m09 on public.adoption_applications;
drop policy if exists adoption_applications_insert_m09 on public.adoption_applications;
drop policy if exists adoption_applications_update_m09 on public.adoption_applications;
drop policy if exists adoption_applications_delete_m09 on public.adoption_applications;

create policy adoption_applications_select_m09
  on public.adoption_applications for select to authenticated
  using (
    applicant_user_id = auth.uid()
    or public._m09_actor_can_manage_adoption(adoption_id, auth.uid())
  );

create policy adoption_applications_insert_m09
  on public.adoption_applications for insert to authenticated
  with check (false);

create policy adoption_applications_update_m09
  on public.adoption_applications for update to authenticated
  using (false);

create policy adoption_applications_delete_m09
  on public.adoption_applications for delete to authenticated
  using (false);

revoke all on table public.adoption_applications from public, anon;
grant select on table public.adoption_applications to authenticated;
grant all on table public.adoption_applications to service_role;

-- ---------------------------------------------------------------------------
-- 5. RPCs
-- ---------------------------------------------------------------------------
create or replace function public.m09_submit_application(
  p_adoption_id uuid,
  p_message text,
  p_housing_type text default null,
  p_has_other_pets boolean default null,
  p_previous_experience text default null,
  p_contact_phone text default null
)
returns public.adoption_applications
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_adoption public.adoptions;
  v_row public.adoption_applications;
  v_msg text := trim(coalesce(p_message, ''));
begin
  if p_adoption_id is null then
    raise exception 'ADOPTION_NOT_FOUND';
  end if;
  if char_length(v_msg) < 1 then
    raise exception 'APPLICATION_MESSAGE_REQUIRED';
  end if;
  if char_length(v_msg) > 2000 then
    raise exception 'APPLICATION_MESSAGE_REQUIRED';
  end if;

  select * into v_adoption from public.adoptions a where a.id = p_adoption_id;
  if not found then
    raise exception 'ADOPTION_NOT_FOUND';
  end if;
  if v_adoption.status is distinct from 'PUBLISHED' then
    raise exception 'ADOPTION_NOT_ACCEPTING_APPLICATIONS';
  end if;
  if v_adoption.publisher_id = v_actor
     or (v_adoption.pet_id is not null and public._m09_actor_can_manage_pet(v_adoption.pet_id, v_actor)) then
    raise exception 'CANNOT_APPLY_TO_OWN_ADOPTION';
  end if;
  if exists (
    select 1 from public.adoption_applications x
    where x.adoption_id = p_adoption_id
      and x.applicant_user_id = v_actor
      and x.status = any (public.m09_adoption_application_active_statuses())
  ) then
    raise exception 'APPLICATION_ALREADY_EXISTS';
  end if;

  insert into public.adoption_applications (
    adoption_id, applicant_user_id, message, housing_type, has_other_pets,
    previous_experience, contact_phone, status, submitted_at, created_at, updated_at
  ) values (
    p_adoption_id, v_actor, v_msg,
    nullif(trim(coalesce(p_housing_type, '')), ''),
    p_has_other_pets,
    nullif(trim(coalesce(p_previous_experience, '')), ''),
    nullif(trim(coalesce(p_contact_phone, '')), ''),
    'SUBMITTED', timezone('utc', now()), timezone('utc', now()), timezone('utc', now())
  )
  returning * into v_row;

  return v_row;
exception
  when unique_violation then
    raise exception 'APPLICATION_ALREADY_EXISTS';
end;
$$;

create or replace function public.m09_withdraw_application(p_application_id uuid)
returns public.adoption_applications
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_applications;
begin
  if p_application_id is null then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  select * into v_row from public.adoption_applications a where a.id = p_application_id;
  if not found then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  if v_row.applicant_user_id is distinct from v_actor then
    raise exception 'APPLICATION_FORBIDDEN';
  end if;
  if v_row.status = 'WITHDRAWN' then
    return v_row;
  end if;
  if v_row.status not in ('SUBMITTED', 'UNDER_REVIEW') then
    raise exception 'APPLICATION_NOT_ACTIVE';
  end if;

  update public.adoption_applications
  set status = 'WITHDRAWN',
      updated_at = timezone('utc', now())
  where id = p_application_id
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.m09_mark_application_under_review(p_application_id uuid)
returns public.adoption_applications
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_applications;
begin
  if p_application_id is null then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  select * into v_row from public.adoption_applications a where a.id = p_application_id;
  if not found then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  if not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'APPLICATION_FORBIDDEN';
  end if;
  if v_row.status = 'UNDER_REVIEW' then
    return v_row;
  end if;
  if v_row.status is distinct from 'SUBMITTED' then
    raise exception 'APPLICATION_INVALID_TRANSITION';
  end if;

  update public.adoption_applications
  set status = 'UNDER_REVIEW',
      reviewed_at = coalesce(reviewed_at, timezone('utc', now())),
      reviewed_by = v_actor,
      updated_at = timezone('utc', now())
  where id = p_application_id
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.m09_accept_application(p_application_id uuid)
returns public.adoption_applications
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_applications;
  v_adoption public.adoptions;
begin
  if p_application_id is null then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;

  select * into v_row from public.adoption_applications a where a.id = p_application_id for update;
  if not found then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  if not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'APPLICATION_FORBIDDEN';
  end if;

  select * into v_adoption from public.adoptions a where a.id = v_row.adoption_id for update;
  if not found then
    raise exception 'ADOPTION_NOT_FOUND';
  end if;

  if v_row.status = 'ACCEPTED' then
    return v_row; -- idempotente
  end if;
  if v_row.status = 'REJECTED' then
    raise exception 'APPLICATION_ALREADY_REJECTED';
  end if;
  if v_row.status = 'WITHDRAWN' then
    raise exception 'APPLICATION_ALREADY_WITHDRAWN';
  end if;
  if v_row.status not in ('SUBMITTED', 'UNDER_REVIEW') then
    raise exception 'APPLICATION_INVALID_TRANSITION';
  end if;
  if v_adoption.status is distinct from 'PUBLISHED' then
    raise exception 'ADOPTION_NOT_ACCEPTING_APPLICATIONS';
  end if;
  if exists (
    select 1 from public.adoption_applications x
    where x.adoption_id = v_row.adoption_id
      and x.status = 'ACCEPTED'
      and x.id is distinct from p_application_id
  ) then
    raise exception 'APPLICATION_ALREADY_ACCEPTED';
  end if;

  update public.adoption_applications
  set status = 'ACCEPTED',
      reviewed_at = timezone('utc', now()),
      reviewed_by = v_actor,
      updated_at = timezone('utc', now())
  where id = p_application_id
  returning * into v_row;

  update public.adoption_applications
  set status = 'REJECTED',
      reviewed_at = timezone('utc', now()),
      reviewed_by = v_actor,
      rejection_reason = coalesce(nullif(trim(rejection_reason), ''), 'Se seleccionó otra postulación'),
      updated_at = timezone('utc', now())
  where adoption_id = v_row.adoption_id
    and id is distinct from p_application_id
    and status in ('SUBMITTED', 'UNDER_REVIEW');

  update public.adoptions
  set status = 'PAUSED',
      updated_at = timezone('utc', now())
  where id = v_row.adoption_id
    and status = 'PUBLISHED';

  return v_row;
end;
$$;

create or replace function public.m09_reject_application(
  p_application_id uuid,
  p_rejection_reason text default null
)
returns public.adoption_applications
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_applications;
begin
  if p_application_id is null then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  select * into v_row from public.adoption_applications a where a.id = p_application_id;
  if not found then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  if not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'APPLICATION_FORBIDDEN';
  end if;
  if v_row.status = 'REJECTED' then
    return v_row;
  end if;
  if v_row.status = 'ACCEPTED' then
    raise exception 'APPLICATION_ALREADY_ACCEPTED';
  end if;
  if v_row.status = 'WITHDRAWN' then
    raise exception 'APPLICATION_ALREADY_WITHDRAWN';
  end if;
  if v_row.status not in ('SUBMITTED', 'UNDER_REVIEW') then
    raise exception 'APPLICATION_INVALID_TRANSITION';
  end if;

  update public.adoption_applications
  set status = 'REJECTED',
      reviewed_at = timezone('utc', now()),
      reviewed_by = v_actor,
      rejection_reason = nullif(trim(coalesce(p_rejection_reason, '')), ''),
      updated_at = timezone('utc', now())
  where id = p_application_id
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.m09_list_my_applications()
returns table (
  id uuid,
  adoption_id uuid,
  applicant_user_id uuid,
  applicant_name text,
  message text,
  housing_type text,
  has_other_pets boolean,
  previous_experience text,
  contact_phone text,
  status text,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  reviewed_by uuid,
  rejection_reason text,
  adoption_title text,
  pet_name text,
  pet_photo_url text,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
begin
  return query
  select e.*
  from public.adoption_applications a
  cross join lateral public._m09_application_enrich(a) e
  where a.applicant_user_id = v_actor
  order by a.submitted_at desc;
end;
$$;

create or replace function public.m09_list_received_applications(p_status text default null)
returns table (
  id uuid,
  adoption_id uuid,
  applicant_user_id uuid,
  applicant_name text,
  message text,
  housing_type text,
  has_other_pets boolean,
  previous_experience text,
  contact_phone text,
  status text,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  reviewed_by uuid,
  rejection_reason text,
  adoption_title text,
  pet_name text,
  pet_photo_url text,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_status text := nullif(upper(trim(coalesce(p_status, ''))), '');
begin
  if v_status is not null
     and not (v_status = any (public.m09_adoption_application_statuses())) then
    v_status := null;
  end if;

  return query
  select e.*
  from public.adoption_applications a
  cross join lateral public._m09_application_enrich(a) e
  where public._m09_actor_can_manage_adoption(a.adoption_id, v_actor)
    and (v_status is null or a.status = v_status)
  order by a.submitted_at desc;
end;
$$;

create or replace function public.m09_get_application(p_application_id uuid)
returns table (
  id uuid,
  adoption_id uuid,
  applicant_user_id uuid,
  applicant_name text,
  message text,
  housing_type text,
  has_other_pets boolean,
  previous_experience text,
  contact_phone text,
  status text,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  reviewed_by uuid,
  rejection_reason text,
  adoption_title text,
  pet_name text,
  pet_photo_url text,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m09_require_authenticated();
  v_row public.adoption_applications;
begin
  if p_application_id is null then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  select * into v_row from public.adoption_applications a where a.id = p_application_id;
  if not found then
    raise exception 'APPLICATION_NOT_FOUND';
  end if;
  if v_row.applicant_user_id is distinct from v_actor
     and not public._m09_actor_can_manage_adoption(v_row.adoption_id, v_actor) then
    raise exception 'APPLICATION_FORBIDDEN';
  end if;
  return query select * from public._m09_application_enrich(v_row);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Grants
-- ---------------------------------------------------------------------------
revoke all on function public.m09_adoption_application_statuses() from public;
revoke all on function public.m09_adoption_application_active_statuses() from public;
revoke all on function public._m09_actor_can_manage_adoption(uuid, uuid) from public;
revoke all on function public._m09_application_enrich(public.adoption_applications) from public;
revoke all on function public.m09_submit_application(uuid, text, text, boolean, text, text) from public;
revoke all on function public.m09_withdraw_application(uuid) from public;
revoke all on function public.m09_mark_application_under_review(uuid) from public;
revoke all on function public.m09_accept_application(uuid) from public;
revoke all on function public.m09_reject_application(uuid, text) from public;
revoke all on function public.m09_list_my_applications() from public;
revoke all on function public.m09_list_received_applications(text) from public;
revoke all on function public.m09_get_application(uuid) from public;

grant execute on function public.m09_adoption_application_statuses() to authenticated;
grant execute on function public.m09_adoption_application_active_statuses() to authenticated;
grant execute on function public.m09_submit_application(uuid, text, text, boolean, text, text) to authenticated;
grant execute on function public.m09_withdraw_application(uuid) to authenticated;
grant execute on function public.m09_mark_application_under_review(uuid) to authenticated;
grant execute on function public.m09_accept_application(uuid) to authenticated;
grant execute on function public.m09_reject_application(uuid, text) to authenticated;
grant execute on function public.m09_list_my_applications() to authenticated;
grant execute on function public.m09_list_received_applications(text) to authenticated;
grant execute on function public.m09_get_application(uuid) to authenticated;

comment on table public.adoption_applications is
  'M09: postulaciones a publicaciones; nunca públicas; mutaciones solo vía RPC m09_*.';
comment on function public.m09_accept_application(uuid) is
  'M09: acepta postulación, rechaza otras activas y pausa la publicación en una transacción.';

commit;
