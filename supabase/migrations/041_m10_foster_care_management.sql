-- =============================================================================
-- LeoVer M10 — migración 041: gastos, evolución, ayuda y finalización
-- Forward-only sobre 001–040. NO reescribe 040.
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Disponibilidad coherente (refuerzo si 040 ya estaba aplicada con regla vieja)
-- ocupación+reservas = 0 → AVAILABLE; >0 y <capacidad → LIMITED; >=capacidad → FULL
-- ---------------------------------------------------------------------------
create or replace function public._m10_recompute_availability(
  p_status text,
  p_capacity integer,
  p_occupancy integer,
  p_reserved integer
) returns text
language sql
immutable
as $$
  select case
    when p_status is distinct from 'ACTIVE' then 'UNAVAILABLE'
    when greatest(coalesce(p_occupancy, 0), 0) + greatest(coalesce(p_reserved, 0), 0)
         >= greatest(coalesce(p_capacity, 0), 0) then 'FULL'
    when greatest(coalesce(p_occupancy, 0), 0) + greatest(coalesce(p_reserved, 0), 0) > 0
         then 'LIMITED'
    else 'AVAILABLE'
  end;
$$;

-- ---------------------------------------------------------------------------
-- 1. Columnas de finalización en foster_placements
-- ---------------------------------------------------------------------------
alter table public.foster_placements
  add column if not exists end_notes text,
  add column if not exists ended_by uuid references public.users (id);

-- ---------------------------------------------------------------------------
-- 2. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.foster_expenses (
  id uuid primary key default gen_random_uuid(),
  placement_id uuid not null references public.foster_placements (id) on delete cascade,
  category text not null,
  description text not null,
  amount_minor bigint not null,
  currency text not null default 'ARS',
  occurred_at timestamptz not null,
  receipt_ref text,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  constraint foster_expenses_category_chk
    check (category = any (array['FOOD','MEDICATION','VETERINARY','TRANSPORT','HYGIENE','SUPPLIES','OTHER']::text[])),
  constraint foster_expenses_amount_chk check (amount_minor > 0),
  constraint foster_expenses_currency_chk check (char_length(currency) between 3 and 3),
  constraint foster_expenses_receipt_safe_chk
    check (
      receipt_ref is null
      or receipt_ref ~* '^(m05://|file_asset:)'
    )
);

create index if not exists foster_expenses_placement_idx on public.foster_expenses (placement_id);
create index if not exists foster_expenses_occurred_idx on public.foster_expenses (occurred_at desc);

create table if not exists public.foster_evolution_entries (
  id uuid primary key default gen_random_uuid(),
  placement_id uuid not null references public.foster_placements (id) on delete cascade,
  title text not null,
  description text not null,
  health_status text not null default 'UNKNOWN',
  weight_grams integer,
  occurred_at timestamptz not null,
  media_refs text[] not null default '{}',
  visibility text not null default 'PARTICIPANTS',
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  constraint foster_evolution_health_chk
    check (health_status = any (array['GOOD','STABLE','NEEDS_ATTENTION','CRITICAL','UNKNOWN']::text[])),
  constraint foster_evolution_visibility_chk
    check (visibility = any (array['PARTICIPANTS','PUBLIC','PRIVATE_HOME']::text[]))
);

create index if not exists foster_evolution_placement_idx on public.foster_evolution_entries (placement_id);
create index if not exists foster_evolution_occurred_idx on public.foster_evolution_entries (occurred_at desc);

create table if not exists public.foster_help_requests (
  id uuid primary key default gen_random_uuid(),
  placement_id uuid not null references public.foster_placements (id) on delete cascade,
  help_type text not null,
  title text not null,
  description text not null,
  target_amount_minor bigint,
  currency text,
  quantity_needed integer,
  status text not null default 'OPEN',
  urgency text not null default 'NORMAL',
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  closed_at timestamptz,
  received_amount_minor bigint not null default 0,
  received_quantity integer not null default 0,
  constraint foster_help_type_chk
    check (help_type = any (array['FOOD','MEDICATION','VETERINARY','TRANSPORT','SUPPLIES','VOLUNTEER','MONEY','OTHER']::text[])),
  constraint foster_help_status_chk
    check (status = any (array['OPEN','PAUSED','FULFILLED','CANCELLED']::text[])),
  constraint foster_help_urgency_chk
    check (urgency = any (array['NORMAL','HIGH','EMERGENCY']::text[])),
  constraint foster_help_target_chk
    check (target_amount_minor is null or target_amount_minor > 0),
  constraint foster_help_qty_chk
    check (quantity_needed is null or quantity_needed > 0)
);

create index if not exists foster_help_placement_idx on public.foster_help_requests (placement_id);
create index if not exists foster_help_status_idx on public.foster_help_requests (status);

create table if not exists public.foster_help_contributions (
  id uuid primary key default gen_random_uuid(),
  help_request_id uuid not null references public.foster_help_requests (id) on delete cascade,
  contributor_user_id uuid references public.users (id),
  description text not null,
  amount_minor bigint,
  quantity integer,
  status text not null default 'RECEIVED',
  recorded_at timestamptz not null default timezone('utc', now()),
  constraint foster_contrib_status_chk
    check (status = any (array['PLEDGED','RECEIVED','CANCELLED']::text[])),
  constraint foster_contrib_amount_chk
    check (amount_minor is null or amount_minor > 0),
  constraint foster_contrib_qty_chk
    check (quantity is null or quantity > 0)
);

create index if not exists foster_contrib_help_idx on public.foster_help_contributions (help_request_id);

-- ---------------------------------------------------------------------------
-- 3. Helpers acceso
-- ---------------------------------------------------------------------------
create or replace function public._m10_can_access_placement(p_placement_id uuid, p_actor uuid default auth.uid())
returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  v_p public.foster_placements;
begin
  if p_placement_id is null or p_actor is null then return false; end if;
  select * into v_p from public.foster_placements where id = p_placement_id;
  if not found then return false; end if;
  if v_p.foster_user_id = p_actor or v_p.requester_user_id = p_actor then
    return true;
  end if;
  if public._m10_actor_can_manage_pet(v_p.pet_id, p_actor) then
    return true;
  end if;
  return exists (
    select 1 from public.foster_home_profiles h
    where h.id = v_p.foster_home_id and h.owner_user_id = p_actor
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RLS
-- ---------------------------------------------------------------------------
alter table public.foster_expenses enable row level security;
alter table public.foster_evolution_entries enable row level security;
alter table public.foster_help_requests enable row level security;
alter table public.foster_help_contributions enable row level security;

drop policy if exists foster_expenses_select_m10 on public.foster_expenses;
drop policy if exists foster_expenses_ins_m10 on public.foster_expenses;
drop policy if exists foster_expenses_upd_m10 on public.foster_expenses;
drop policy if exists foster_expenses_del_m10 on public.foster_expenses;
create policy foster_expenses_select_m10 on public.foster_expenses for select to authenticated
  using (public._m10_can_access_placement(placement_id, auth.uid()));
create policy foster_expenses_ins_m10 on public.foster_expenses for insert to authenticated with check (false);
create policy foster_expenses_upd_m10 on public.foster_expenses for update to authenticated using (false);
create policy foster_expenses_del_m10 on public.foster_expenses for delete to authenticated using (false);

drop policy if exists foster_evolution_select_m10 on public.foster_evolution_entries;
drop policy if exists foster_evolution_ins_m10 on public.foster_evolution_entries;
drop policy if exists foster_evolution_upd_m10 on public.foster_evolution_entries;
drop policy if exists foster_evolution_del_m10 on public.foster_evolution_entries;
create policy foster_evolution_select_m10 on public.foster_evolution_entries for select to authenticated
  using (
    visibility = 'PUBLIC'
    or public._m10_can_access_placement(placement_id, auth.uid())
  );
create policy foster_evolution_ins_m10 on public.foster_evolution_entries for insert to authenticated with check (false);
create policy foster_evolution_upd_m10 on public.foster_evolution_entries for update to authenticated using (false);
create policy foster_evolution_del_m10 on public.foster_evolution_entries for delete to authenticated using (false);

drop policy if exists foster_help_select_m10 on public.foster_help_requests;
drop policy if exists foster_help_ins_m10 on public.foster_help_requests;
drop policy if exists foster_help_upd_m10 on public.foster_help_requests;
drop policy if exists foster_help_del_m10 on public.foster_help_requests;
create policy foster_help_select_m10 on public.foster_help_requests for select to authenticated
  using (
    status = 'OPEN'
    or public._m10_can_access_placement(placement_id, auth.uid())
  );
create policy foster_help_ins_m10 on public.foster_help_requests for insert to authenticated with check (false);
create policy foster_help_upd_m10 on public.foster_help_requests for update to authenticated using (false);
create policy foster_help_del_m10 on public.foster_help_requests for delete to authenticated using (false);

drop policy if exists foster_contrib_select_m10 on public.foster_help_contributions;
drop policy if exists foster_contrib_ins_m10 on public.foster_help_contributions;
drop policy if exists foster_contrib_upd_m10 on public.foster_help_contributions;
drop policy if exists foster_contrib_del_m10 on public.foster_help_contributions;
create policy foster_contrib_select_m10 on public.foster_help_contributions for select to authenticated
  using (
    exists (
      select 1 from public.foster_help_requests h
      where h.id = help_request_id
        and public._m10_can_access_placement(h.placement_id, auth.uid())
    )
  );
create policy foster_contrib_ins_m10 on public.foster_help_contributions for insert to authenticated with check (false);
create policy foster_contrib_upd_m10 on public.foster_help_contributions for update to authenticated using (false);
create policy foster_contrib_del_m10 on public.foster_help_contributions for delete to authenticated using (false);

revoke all on table public.foster_expenses from public, anon;
revoke all on table public.foster_evolution_entries from public, anon;
revoke all on table public.foster_help_requests from public, anon;
revoke all on table public.foster_help_contributions from public, anon;
grant select on table public.foster_expenses to authenticated;
grant select on table public.foster_evolution_entries to authenticated;
grant select on table public.foster_help_requests to authenticated;
grant select on table public.foster_help_contributions to authenticated;
grant all on table public.foster_expenses to service_role;
grant all on table public.foster_evolution_entries to service_role;
grant all on table public.foster_help_requests to service_role;
grant all on table public.foster_help_contributions to service_role;

-- ---------------------------------------------------------------------------
-- 5. RPCs gastos / evolución / ayuda
-- ---------------------------------------------------------------------------
create or replace function public.m10_add_foster_expense(
  p_placement_id uuid,
  p_category text,
  p_description text,
  p_amount_minor bigint,
  p_currency text default 'ARS',
  p_occurred_at timestamptz default timezone('utc', now()),
  p_receipt_ref text default null
) returns public.foster_expenses
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_p public.foster_placements;
  v_row public.foster_expenses;
begin
  if p_placement_id is null then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  if coalesce(p_amount_minor, 0) <= 0 then raise exception 'FOSTER_EXPENSE_INVALID_AMOUNT'; end if;
  select * into v_p from public.foster_placements where id = p_placement_id;
  if not found then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  if v_p.status <> 'ACTIVE' then raise exception 'FOSTER_PLACEMENT_NOT_ACTIVE'; end if;
  if v_p.foster_user_id <> v_actor then raise exception 'FOSTER_EXPENSE_FORBIDDEN'; end if;
  if p_receipt_ref is not null and (
       p_receipt_ref ~* 'object/public/leover'
       or (p_receipt_ref ~* '^https?://' and p_receipt_ref !~* '^(m05://|file_asset:)')
     ) then
    raise exception 'FOSTER_EVOLUTION_INVALID_MEDIA_REF';
  end if;

  insert into public.foster_expenses (
    placement_id, category, description, amount_minor, currency, occurred_at, receipt_ref, created_by
  ) values (
    p_placement_id, upper(trim(p_category)), trim(p_description), p_amount_minor,
    upper(trim(coalesce(p_currency, 'ARS'))), coalesce(p_occurred_at, timezone('utc', now())),
    nullif(trim(coalesce(p_receipt_ref, '')), ''), v_actor
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_list_foster_expenses(p_placement_id uuid)
returns setof public.foster_expenses
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
begin
  if not public._m10_can_access_placement(p_placement_id, v_actor) then
    raise exception 'FOSTER_EXPENSE_FORBIDDEN';
  end if;
  return query
  select e.* from public.foster_expenses e
  where e.placement_id = p_placement_id
  order by e.occurred_at desc;
end;
$$;

create or replace function public.m10_add_foster_evolution(
  p_placement_id uuid,
  p_title text,
  p_description text,
  p_health_status text default 'UNKNOWN',
  p_weight_grams integer default null,
  p_occurred_at timestamptz default timezone('utc', now()),
  p_media_refs text[] default '{}',
  p_visibility text default 'PARTICIPANTS'
) returns public.foster_evolution_entries
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_p public.foster_placements;
  v_row public.foster_evolution_entries;
  v_ref text;
begin
  select * into v_p from public.foster_placements where id = p_placement_id;
  if not found then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  if v_p.status <> 'ACTIVE' then raise exception 'FOSTER_PLACEMENT_NOT_ACTIVE'; end if;
  if v_p.foster_user_id <> v_actor then raise exception 'FOSTER_EVOLUTION_FORBIDDEN'; end if;
  foreach v_ref in array coalesce(p_media_refs, '{}') loop
    if v_ref ~* 'object/public/leover' or v_ref ~* '^https?://' then
      raise exception 'FOSTER_EVOLUTION_INVALID_MEDIA_REF';
    end if;
  end loop;

  insert into public.foster_evolution_entries (
    placement_id, title, description, health_status, weight_grams, occurred_at, media_refs, visibility, created_by
  ) values (
    p_placement_id, trim(p_title),
    case when upper(trim(p_visibility)) = 'PUBLIC' then left(trim(p_description), 280) else trim(p_description) end,
    upper(trim(coalesce(p_health_status, 'UNKNOWN'))),
    p_weight_grams, coalesce(p_occurred_at, timezone('utc', now())),
    coalesce(p_media_refs, '{}'), upper(trim(coalesce(p_visibility, 'PARTICIPANTS'))), v_actor
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_list_foster_evolution(p_placement_id uuid)
returns setof public.foster_evolution_entries
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_access boolean := public._m10_can_access_placement(p_placement_id, v_actor);
begin
  return query
  select e.* from public.foster_evolution_entries e
  where e.placement_id = p_placement_id
    and (v_access or e.visibility = 'PUBLIC')
  order by e.occurred_at desc;
end;
$$;

create or replace function public.m10_create_help_request(
  p_placement_id uuid,
  p_type text,
  p_title text,
  p_description text,
  p_target_amount_minor bigint default null,
  p_currency text default null,
  p_quantity_needed integer default null,
  p_urgency text default 'NORMAL'
) returns public.foster_help_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_p public.foster_placements;
  v_row public.foster_help_requests;
begin
  select * into v_p from public.foster_placements where id = p_placement_id;
  if not found then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  if v_p.status <> 'ACTIVE' then raise exception 'FOSTER_PLACEMENT_NOT_ACTIVE'; end if;
  if not public._m10_can_access_placement(p_placement_id, v_actor) then
    raise exception 'FOSTER_HELP_REQUEST_FORBIDDEN';
  end if;
  if p_description ~* '(cbu|alias bancario|tarjeta|cvv)' then
    raise exception 'FOSTER_CONTRIBUTION_INVALID';
  end if;

  insert into public.foster_help_requests (
    placement_id, help_type, title, description, target_amount_minor, currency,
    quantity_needed, status, urgency, created_by
  ) values (
    p_placement_id, upper(trim(p_type)), trim(p_title), trim(p_description),
    p_target_amount_minor, nullif(upper(trim(coalesce(p_currency, ''))), ''),
    p_quantity_needed, 'OPEN', upper(trim(coalesce(p_urgency, 'NORMAL'))), v_actor
  ) returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_update_help_request_status(
  p_help_request_id uuid,
  p_status text
) returns public.foster_help_requests
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_help_requests;
  v_status text := upper(trim(p_status));
begin
  select * into v_row from public.foster_help_requests where id = p_help_request_id for update;
  if not found then raise exception 'FOSTER_HELP_REQUEST_NOT_FOUND'; end if;
  if v_row.status = v_status then return v_row; end if;
  if v_row.status not in ('OPEN', 'PAUSED') then raise exception 'FOSTER_HELP_REQUEST_NOT_EDITABLE'; end if;
  if not public._m10_can_access_placement(v_row.placement_id, v_actor) then
    raise exception 'FOSTER_HELP_REQUEST_FORBIDDEN';
  end if;
  update public.foster_help_requests set
    status = v_status,
    closed_at = case when v_status in ('FULFILLED', 'CANCELLED') then timezone('utc', now()) else closed_at end
  where id = p_help_request_id returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.m10_list_help_requests(p_placement_id uuid)
returns setof public.foster_help_requests
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
begin
  return query
  select h.* from public.foster_help_requests h
  where h.placement_id = p_placement_id
    and (h.status = 'OPEN' or public._m10_can_access_placement(p_placement_id, v_actor))
  order by h.created_at desc;
end;
$$;

create or replace function public.m10_get_help_request(p_help_request_id uuid)
returns public.foster_help_requests
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_row public.foster_help_requests;
begin
  select * into v_row from public.foster_help_requests where id = p_help_request_id;
  if not found then raise exception 'FOSTER_HELP_REQUEST_NOT_FOUND'; end if;
  if not public._m10_can_access_placement(v_row.placement_id, v_actor)
     and v_row.status <> 'OPEN' then
    raise exception 'FOSTER_HELP_REQUEST_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m10_record_help_contribution(
  p_help_request_id uuid,
  p_description text,
  p_amount_minor bigint default null,
  p_quantity integer default null,
  p_status text default 'RECEIVED'
) returns public.foster_help_contributions
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_hr public.foster_help_requests;
  v_row public.foster_help_contributions;
  v_status text := upper(trim(coalesce(p_status, 'RECEIVED')));
begin
  select * into v_hr from public.foster_help_requests where id = p_help_request_id for update;
  if not found then raise exception 'FOSTER_HELP_REQUEST_NOT_FOUND'; end if;
  if v_hr.status not in ('OPEN', 'PAUSED') then raise exception 'FOSTER_HELP_REQUEST_NOT_EDITABLE'; end if;
  if not public._m10_can_access_placement(v_hr.placement_id, v_actor) then
    raise exception 'FOSTER_HELP_REQUEST_FORBIDDEN';
  end if;
  if p_amount_minor is not null and p_amount_minor <= 0 then raise exception 'FOSTER_CONTRIBUTION_INVALID'; end if;
  if p_quantity is not null and p_quantity <= 0 then raise exception 'FOSTER_CONTRIBUTION_INVALID'; end if;

  insert into public.foster_help_contributions (
    help_request_id, contributor_user_id, description, amount_minor, quantity, status
  ) values (
    p_help_request_id, v_actor, trim(p_description), p_amount_minor, p_quantity, v_status
  ) returning * into v_row;

  if v_status = 'RECEIVED' then
    update public.foster_help_requests set
      received_amount_minor = received_amount_minor + coalesce(p_amount_minor, 0),
      received_quantity = received_quantity + coalesce(p_quantity, 0)
    where id = p_help_request_id
    returning * into v_hr;

    if (v_hr.help_type = 'MONEY' and v_hr.target_amount_minor is not null
          and v_hr.received_amount_minor >= v_hr.target_amount_minor)
       or (v_hr.quantity_needed is not null and v_hr.received_quantity >= v_hr.quantity_needed) then
      update public.foster_help_requests set
        status = 'FULFILLED', closed_at = timezone('utc', now())
      where id = p_help_request_id;
    end if;
  end if;
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Cancelación RESERVED / Finalización ACTIVE
-- ---------------------------------------------------------------------------
create or replace function public.m10_cancel_foster_placement(
  p_placement_id uuid,
  p_reason text default null
) returns public.foster_placements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_p public.foster_placements;
  v_home public.foster_home_profiles;
  v_now timestamptz := timezone('utc', now());
begin
  select * into v_p from public.foster_placements where id = p_placement_id for update;
  if not found then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  if v_p.status = 'CANCELLED' then return v_p; end if;
  if v_p.status <> 'RESERVED' then raise exception 'FOSTER_PLACEMENT_INVALID_TRANSITION'; end if;
  if not public._m10_can_access_placement(p_placement_id, v_actor) then
    raise exception 'FOSTER_PLACEMENT_COMPLETION_FORBIDDEN';
  end if;

  select * into v_home from public.foster_home_profiles where id = v_p.foster_home_id for update;

  update public.foster_placements set
    status = 'CANCELLED',
    ended_at = v_now,
    end_reason = 'CANCELLED_BEFORE_START',
    end_notes = nullif(trim(coalesce(p_reason, '')), ''),
    ended_by = v_actor,
    updated_at = v_now
  where id = p_placement_id returning * into v_p;

  update public.foster_home_profiles set
    reserved_count = greatest(reserved_count - 1, 0),
    availability_status = public._m10_recompute_availability(
      status, total_capacity, current_occupancy, greatest(reserved_count - 1, 0)
    ),
    updated_at = v_now
  where id = v_home.id;

  return v_p;
end;
$$;

create or replace function public.m10_complete_foster_placement(
  p_placement_id uuid,
  p_end_reason text,
  p_end_notes text default null
) returns public.foster_placements
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
  v_p public.foster_placements;
  v_home public.foster_home_profiles;
  v_now timestamptz := timezone('utc', now());
  v_reason text := upper(trim(p_end_reason));
begin
  if p_placement_id is null then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  select * into v_p from public.foster_placements where id = p_placement_id for update;
  if not found then raise exception 'FOSTER_PLACEMENT_NOT_FOUND'; end if;
  if v_p.status = 'COMPLETED' then return v_p; end if;
  if v_p.status <> 'ACTIVE' then raise exception 'FOSTER_PLACEMENT_NOT_ACTIVE'; end if;
  if not public._m10_can_access_placement(p_placement_id, v_actor) then
    raise exception 'FOSTER_PLACEMENT_COMPLETION_FORBIDDEN';
  end if;
  if v_reason not in (
    'RETURNED_TO_OWNER','MOVED_TO_ANOTHER_FOSTER_HOME','ADOPTED',
    'TRANSFERRED_TO_ORGANIZATION','HOSPITALIZED','CANCELLED_BEFORE_START','OTHER'
  ) then
    raise exception 'FOSTER_PLACEMENT_INVALID_TRANSITION';
  end if;

  select * into v_home from public.foster_home_profiles where id = v_p.foster_home_id for update;

  -- Revoke TEMPORARY_CUSTODIAN (PRINCIPAL untouched)
  if v_p.temporary_responsibility_id is not null then
    update public.pet_responsibilities set
      status = 'REVOKED',
      ends_at = coalesce(ends_at, v_now),
      revoked_at = coalesce(revoked_at, v_now),
      revoked_by = coalesce(revoked_by, v_actor)
    where id = v_p.temporary_responsibility_id
      and role_code = 'TEMPORARY_CUSTODIAN'
      and status = 'ACTIVE';
    if not found then
      -- try by placement reason match
      update public.pet_responsibilities set
        status = 'REVOKED',
        ends_at = coalesce(ends_at, v_now),
        revoked_at = coalesce(revoked_at, v_now),
        revoked_by = coalesce(revoked_by, v_actor)
      where pet_id = v_p.pet_id
        and person_id = v_p.foster_user_id
        and role_code = 'TEMPORARY_CUSTODIAN'
        and status = 'ACTIVE';
    end if;
  else
    update public.pet_responsibilities set
      status = 'REVOKED',
      ends_at = coalesce(ends_at, v_now),
      revoked_at = coalesce(revoked_at, v_now),
      revoked_by = coalesce(revoked_by, v_actor)
    where pet_id = v_p.pet_id
      and person_id = v_p.foster_user_id
      and role_code = 'TEMPORARY_CUSTODIAN'
      and status = 'ACTIVE';
  end if;

  update public.foster_placements set
    status = 'COMPLETED',
    ended_at = v_now,
    end_reason = v_reason,
    end_notes = nullif(trim(coalesce(p_end_notes, '')), ''),
    ended_by = v_actor,
    updated_at = v_now
  where id = p_placement_id returning * into v_p;

  update public.foster_home_profiles set
    current_occupancy = greatest(current_occupancy - 1, 0),
    availability_status = public._m10_recompute_availability(
      status, total_capacity, greatest(current_occupancy - 1, 0), reserved_count
    ),
    updated_at = v_now
  where id = v_home.id;

  update public.foster_help_requests set
    status = 'CANCELLED',
    closed_at = v_now
  where placement_id = p_placement_id and status in ('OPEN', 'PAUSED');

  return v_p;
end;
$$;

create or replace function public.m10_list_foster_history()
returns setof public.foster_placements
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m10_require_authenticated();
begin
  return query
  select p.* from public.foster_placements p
  where p.foster_user_id = v_actor
     or p.requester_user_id = v_actor
     or public._m10_can_access_placement(p.id, v_actor)
  order by coalesce(p.ended_at, p.started_at) desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. Grants
-- ---------------------------------------------------------------------------
revoke all on function public._m10_can_access_placement(uuid, uuid) from public;
revoke all on function public.m10_add_foster_expense(uuid, text, text, bigint, text, timestamptz, text) from public;
revoke all on function public.m10_list_foster_expenses(uuid) from public;
revoke all on function public.m10_add_foster_evolution(uuid, text, text, text, integer, timestamptz, text[], text) from public;
revoke all on function public.m10_list_foster_evolution(uuid) from public;
revoke all on function public.m10_create_help_request(uuid, text, text, text, bigint, text, integer, text) from public;
revoke all on function public.m10_update_help_request_status(uuid, text) from public;
revoke all on function public.m10_list_help_requests(uuid) from public;
revoke all on function public.m10_get_help_request(uuid) from public;
revoke all on function public.m10_record_help_contribution(uuid, text, bigint, integer, text) from public;
revoke all on function public.m10_cancel_foster_placement(uuid, text) from public;
revoke all on function public.m10_complete_foster_placement(uuid, text, text) from public;
revoke all on function public.m10_list_foster_history() from public;

grant execute on function public.m10_add_foster_expense(uuid, text, text, bigint, text, timestamptz, text) to authenticated;
grant execute on function public.m10_list_foster_expenses(uuid) to authenticated;
grant execute on function public.m10_add_foster_evolution(uuid, text, text, text, integer, timestamptz, text[], text) to authenticated;
grant execute on function public.m10_list_foster_evolution(uuid) to authenticated;
grant execute on function public.m10_create_help_request(uuid, text, text, text, bigint, text, integer, text) to authenticated;
grant execute on function public.m10_update_help_request_status(uuid, text) to authenticated;
grant execute on function public.m10_list_help_requests(uuid) to authenticated;
grant execute on function public.m10_get_help_request(uuid) to authenticated;
grant execute on function public.m10_record_help_contribution(uuid, text, bigint, integer, text) to authenticated;
grant execute on function public.m10_cancel_foster_placement(uuid, text) to authenticated;
grant execute on function public.m10_complete_foster_placement(uuid, text, text) to authenticated;
grant execute on function public.m10_list_foster_history() to authenticated;

comment on function public.m10_complete_foster_placement(uuid, text, text) is
  'M10: complete ACTIVE placement — free occupancy, revoke TEMPORARY_CUSTODIAN, keep PRINCIPAL, close open help.';

commit;
