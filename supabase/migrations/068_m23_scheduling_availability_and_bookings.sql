-- =============================================================================
-- LeoVer M23 — migración 068: disponibilidad y reservas (Bloque 2).
-- Forward-only sobre 001–067. CREADA, NO APLICADA.
-- Sin pagos ni materialización de slots.
-- =============================================================================

begin;

insert into public.organization_permissions (code, description) values
  ('booking.view', 'Ver reservas de prestadores de la organización'),
  ('booking.manage', 'Gestionar agenda y reservas de prestadores de la organización')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in ('booking.view', 'booking.manage')
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER' and p.code = 'booking.view'
on conflict do nothing;

create table if not exists public.m23_availability_rules (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.m22_service_providers(id) on delete cascade,
  offering_id uuid not null references public.m22_service_offerings(id) on delete cascade,
  branch_id uuid references public.m22_provider_branches(id) on delete set null,
  organization_id uuid references public.organizations(id) on delete set null,
  day_of_week smallint not null,
  start_time time not null,
  end_time time not null,
  slot_duration_minutes integer not null,
  zone_id text not null default 'UTC',
  status text not null default 'ACTIVE',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m23_rule_day_chk check (day_of_week between 1 and 7),
  constraint m23_rule_window_chk check (end_time > start_time),
  constraint m23_rule_duration_chk check (slot_duration_minutes between 5 and 480),
  constraint m23_rule_status_chk check (status in ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

create table if not exists public.m23_availability_exceptions (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.m22_service_providers(id) on delete cascade,
  offering_id uuid references public.m22_service_offerings(id) on delete cascade,
  branch_id uuid references public.m22_provider_branches(id) on delete set null,
  organization_id uuid references public.organizations(id) on delete set null,
  exception_date date not null,
  start_time time,
  end_time time,
  type text not null,
  note text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m23_exception_type_chk check (type in ('BLOCKED', 'SPECIAL_OPENING', 'HOLIDAY', 'PERSONAL_LEAVE', 'ORGANIZATION_CLOSURE', 'OTHER')),
  constraint m23_exception_window_chk check (
    (start_time is null and end_time is null) or (start_time is not null and end_time is not null and end_time > start_time)
  )
);

create table if not exists public.m23_bookings (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.m22_service_providers(id) on delete restrict,
  offering_id uuid not null references public.m22_service_offerings(id) on delete restrict,
  branch_id uuid references public.m22_provider_branches(id) on delete set null,
  organization_id uuid references public.organizations(id) on delete set null,
  customer_user_id uuid not null references public.users(id) on delete restrict,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  zone_id text not null,
  modality text not null,
  status text not null default 'REQUESTED',
  customer_note text,
  policy_snapshot jsonb not null default '{}'::jsonb,
  client_request_id text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m23_booking_window_chk check (ends_at > starts_at),
  constraint m23_booking_modality_chk check (modality in ('IN_PERSON', 'REMOTE', 'AT_CUSTOMER_LOCATION')),
  constraint m23_booking_status_chk check (status in ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED_BY_CUSTOMER', 'CANCELLED_BY_PROVIDER', 'COMPLETED', 'NO_SHOW', 'EXPIRED')),
  constraint m23_booking_client_request_uniq unique (customer_user_id, client_request_id)
);

create table if not exists public.m23_booking_history (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null references public.m23_bookings(id) on delete cascade,
  actor_user_id uuid references public.users(id) on delete set null,
  from_status text,
  to_status text not null,
  reason text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m23_history_status_chk check (to_status in ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED_BY_CUSTOMER', 'CANCELLED_BY_PROVIDER', 'COMPLETED', 'NO_SHOW', 'EXPIRED'))
);

create index if not exists m23_rules_provider_offering_idx on public.m23_availability_rules(provider_id, offering_id, status);
create index if not exists m23_exceptions_provider_date_idx on public.m23_availability_exceptions(provider_id, exception_date);
create index if not exists m23_bookings_provider_window_idx on public.m23_bookings(provider_id, starts_at, ends_at);
create index if not exists m23_bookings_customer_idx on public.m23_bookings(customer_user_id, updated_at desc);
create index if not exists m23_history_booking_idx on public.m23_booking_history(booking_id, created_at);

alter table public.m23_availability_rules enable row level security;
alter table public.m23_availability_exceptions enable row level security;
alter table public.m23_bookings enable row level security;
alter table public.m23_booking_history enable row level security;

create policy m23_rules_authenticated_deny on public.m23_availability_rules for all to authenticated using (false) with check (false);
create policy m23_exceptions_authenticated_deny on public.m23_availability_exceptions for all to authenticated using (false) with check (false);
create policy m23_bookings_authenticated_deny on public.m23_bookings for all to authenticated using (false) with check (false);
create policy m23_history_authenticated_deny on public.m23_booking_history for all to authenticated using (false) with check (false);

revoke all on table public.m23_availability_rules, public.m23_availability_exceptions, public.m23_bookings, public.m23_booking_history from public, anon, authenticated;
grant all on table public.m23_availability_rules, public.m23_availability_exceptions, public.m23_bookings, public.m23_booking_history to service_role;

create or replace function public._m23_actor()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := auth.uid();
begin
  if v_actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v_actor;
end;
$$;

create or replace function public._m23_can_manage(p_provider public.m22_service_providers, p_actor uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_provider.owner_user_id = p_actor
    or (p_provider.organization_id is not null and public.has_org_permission(p_provider.organization_id, 'booking.manage'));
$$;

create or replace function public._m23_can_view(p_provider public.m22_service_providers, p_actor uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public._m23_can_manage(p_provider, p_actor)
    or (p_provider.organization_id is not null and public.has_org_permission(p_provider.organization_id, 'booking.view'));
$$;

create or replace function public._m23_booking_json(b public.m23_bookings)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', b.id, 'provider_id', b.provider_id, 'offering_id', b.offering_id, 'branch_id', b.branch_id,
    'customer_user_id', b.customer_user_id, 'starts_at', b.starts_at, 'ends_at', b.ends_at,
    'zone_id', b.zone_id, 'modality', b.modality, 'status', b.status, 'customer_note', b.customer_note,
    'created_at', b.created_at, 'updated_at', b.updated_at, 'client_request_id', b.client_request_id
  );
$$;

create or replace function public._m23_assert_provider_offering(p_provider_id uuid, p_offering_id uuid, p_branch_id uuid)
returns public.m22_service_providers language plpgsql security definer set search_path = public as $$
declare v_provider public.m22_service_providers;
begin
  select * into v_provider from public.m22_service_providers where id = p_provider_id;
  if not found then raise exception 'M23_PROVIDER_NOT_FOUND'; end if;
  if not exists (select 1 from public.m22_service_offerings o where o.id = p_offering_id and o.provider_id = p_provider_id and o.active) then
    raise exception 'M23_OFFERING_NOT_AVAILABLE';
  end if;
  if p_branch_id is not null and not exists (select 1 from public.m22_provider_branches b where b.id = p_branch_id and b.provider_id = p_provider_id) then
    raise exception 'M23_BRANCH_NOT_FOUND';
  end if;
  return v_provider;
end;
$$;

create or replace function public._m23_transition(p_booking_id uuid, p_target text, p_reason text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m23_actor();
  v_booking public.m23_bookings;
  v_provider public.m22_service_providers;
  v_previous text;
begin
  select * into v_booking from public.m23_bookings where id = p_booking_id for update;
  if not found then raise exception 'M23_BOOKING_NOT_FOUND'; end if;
  select * into v_provider from public.m22_service_providers where id = v_booking.provider_id;
  if not public._m23_can_manage(v_provider, v_actor) then raise exception 'M23_PERMISSION_DENIED'; end if;
  if v_booking.status = p_target then return public._m23_booking_json(v_booking); end if;
  if (p_target in ('CONFIRMED', 'REJECTED') and v_booking.status <> 'REQUESTED')
    or (p_target in ('COMPLETED', 'NO_SHOW') and v_booking.status <> 'CONFIRMED')
    or (p_target = 'CANCELLED_BY_PROVIDER' and v_booking.status not in ('REQUESTED', 'CONFIRMED')) then
    raise exception 'M23_INVALID_STATUS_TRANSITION';
  end if;
  v_previous := v_booking.status;
  update public.m23_bookings set status = p_target, updated_at = timezone('utc', now()) where id = p_booking_id returning * into v_booking;
  insert into public.m23_booking_history(booking_id, actor_user_id, from_status, to_status, reason)
  values (v_booking.id, v_actor, v_previous, p_target, p_reason);
  return public._m23_booking_json(v_booking);
end;
$$;

create or replace function public.m23_get_public_available_slots(
  p_provider_id uuid, p_offering_id uuid, p_from date, p_to date
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_provider public.m22_service_providers;
begin
  select * into v_provider from public.m22_service_providers where id = p_provider_id and status = 'ACTIVE';
  if not found or not exists (select 1 from public.m22_service_offerings where id = p_offering_id and provider_id = p_provider_id and active) then
    raise exception 'M23_OFFERING_NOT_AVAILABLE';
  end if;
  if p_from is null or p_to is null or p_to < p_from or p_to - p_from > 31 then raise exception 'M23_INVALID_DATE_RANGE'; end if;
  return jsonb_build_object('days', coalesce((
    select jsonb_agg(jsonb_build_object('date', d.day, 'slots', d.slots) order by d.day)
    from (
      select day,
        coalesce(jsonb_agg(jsonb_build_object('starts_at', starts_at, 'ends_at', starts_at + make_interval(mins => slot_duration_minutes), 'modality', 'IN_PERSON') order by starts_at), '[]'::jsonb) as slots
      from (
        select gs::date as day, s.starts_at, r.slot_duration_minutes
        from public.m23_availability_rules r
        cross join generate_series(p_from, p_to, interval '1 day') gs
        cross join lateral generate_series(
          ((gs::date + r.start_time)::timestamp at time zone r.zone_id),
          ((gs::date + r.end_time)::timestamp at time zone r.zone_id) - make_interval(mins => r.slot_duration_minutes),
          make_interval(mins => r.slot_duration_minutes)
        ) s(starts_at)
        where r.provider_id = p_provider_id and r.offering_id = p_offering_id and r.status = 'ACTIVE'
          and r.day_of_week = extract(isodow from gs)::smallint
          and not exists (
            select 1 from public.m23_availability_exceptions e where e.provider_id = r.provider_id
              and (e.offering_id is null or e.offering_id = r.offering_id) and e.exception_date = gs::date
              and e.type <> 'SPECIAL_OPENING'
              and (e.start_time is null or s.starts_at < ((gs::date + e.end_time)::timestamp at time zone r.zone_id)
                and s.starts_at + make_interval(mins => r.slot_duration_minutes) > ((gs::date + e.start_time)::timestamp at time zone r.zone_id))
          )
          and not exists (
            select 1 from public.m23_bookings b where b.provider_id = r.provider_id
              and b.status in ('REQUESTED', 'CONFIRMED', 'COMPLETED', 'NO_SHOW')
              and b.starts_at < s.starts_at + make_interval(mins => r.slot_duration_minutes) and b.ends_at > s.starts_at
          )
      ) available group by day
    ) d
  ), '[]'::jsonb));
end;
$$;

create or replace function public.m23_create_booking_request(
  p_provider_id uuid, p_offering_id uuid, p_branch_id uuid, p_starts_at timestamptz, p_ends_at timestamptz,
  p_zone_id text, p_modality text, p_customer_note text default null, p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m23_actor(); v_provider public.m22_service_providers; v_booking public.m23_bookings;
begin
  if p_starts_at is null or p_ends_at is null or p_ends_at <= p_starts_at or upper(coalesce(p_modality, '')) not in ('IN_PERSON', 'REMOTE', 'AT_CUSTOMER_LOCATION') then
    raise exception 'M23_INVALID_BOOKING';
  end if;
  if p_client_request_id is not null then
    select * into v_booking from public.m23_bookings where customer_user_id = v_actor and client_request_id = p_client_request_id;
    if found then return public._m23_booking_json(v_booking); end if;
  end if;
  v_provider := public._m23_assert_provider_offering(p_provider_id, p_offering_id, p_branch_id);
  if not exists (
    select 1
    from public.m23_availability_rules r
    where r.provider_id = p_provider_id
      and r.offering_id = p_offering_id
      and r.status = 'ACTIVE'
      and r.zone_id = trim(p_zone_id)
      and r.day_of_week = extract(isodow from (p_starts_at at time zone r.zone_id))::smallint
      and (p_starts_at at time zone r.zone_id)::time >= r.start_time
      and (p_ends_at at time zone r.zone_id)::time <= r.end_time
      and p_ends_at = p_starts_at + make_interval(mins => r.slot_duration_minutes)
  ) then raise exception 'M23_SLOT_NOT_AVAILABLE'; end if;
  if exists (
    select 1
    from public.m23_availability_exceptions e
    where e.provider_id = p_provider_id
      and (e.offering_id is null or e.offering_id = p_offering_id)
      and e.exception_date = (p_starts_at at time zone p_zone_id)::date
      and e.type <> 'SPECIAL_OPENING'
      and (
        e.start_time is null
        or (p_starts_at at time zone p_zone_id)::time < e.end_time
          and (p_ends_at at time zone p_zone_id)::time > e.start_time
      )
  ) then raise exception 'M23_SLOT_NOT_AVAILABLE'; end if;
  perform pg_advisory_xact_lock(hashtextextended(p_provider_id::text, 23));
  if exists (
    select 1 from public.m23_bookings b where b.provider_id = p_provider_id
      and b.status in ('REQUESTED', 'CONFIRMED', 'COMPLETED', 'NO_SHOW')
      and b.starts_at < p_ends_at and b.ends_at > p_starts_at
  ) then raise exception 'M23_SLOT_UNAVAILABLE'; end if;
  insert into public.m23_bookings(provider_id, offering_id, branch_id, organization_id, customer_user_id, starts_at, ends_at, zone_id, modality, customer_note, policy_snapshot, client_request_id)
  values (p_provider_id, p_offering_id, p_branch_id, v_provider.organization_id, v_actor, p_starts_at, p_ends_at, trim(p_zone_id), upper(p_modality), nullif(trim(p_customer_note), ''), '{}'::jsonb, nullif(trim(p_client_request_id), ''))
  returning * into v_booking;
  insert into public.m23_booking_history(booking_id, actor_user_id, from_status, to_status, reason)
  values (v_booking.id, v_actor, null, 'REQUESTED', null);
  return public._m23_booking_json(v_booking);
end;
$$;

create or replace function public.m23_list_my_bookings()
returns setof jsonb language sql security definer set search_path = public as $$
  select public._m23_booking_json(b) || jsonb_build_object('provider_display_name', p.display_name, 'offering_name', o.name)
  from public.m23_bookings b
  join public.m22_service_providers p on p.id = b.provider_id
  join public.m22_service_offerings o on o.id = b.offering_id
  where b.customer_user_id = public._m23_actor() order by b.starts_at desc;
$$;

create or replace function public.m23_get_my_booking(p_booking_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_booking public.m23_bookings;
begin
  select * into v_booking from public.m23_bookings where id = p_booking_id and customer_user_id = public._m23_actor();
  if not found then raise exception 'M23_BOOKING_NOT_FOUND'; end if;
  return public._m23_booking_json(v_booking);
end;
$$;

create or replace function public.m23_cancel_own_booking(p_booking_id uuid, p_reason text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m23_actor(); v_booking public.m23_bookings; v_previous text;
begin
  select * into v_booking from public.m23_bookings where id = p_booking_id and customer_user_id = v_actor for update;
  if not found then raise exception 'M23_BOOKING_NOT_FOUND'; end if;
  if v_booking.status = 'CANCELLED_BY_CUSTOMER' then return public._m23_booking_json(v_booking); end if;
  if v_booking.status not in ('REQUESTED', 'CONFIRMED') then raise exception 'M23_INVALID_STATUS_TRANSITION'; end if;
  v_previous := v_booking.status;
  update public.m23_bookings set status = 'CANCELLED_BY_CUSTOMER', updated_at = timezone('utc', now()) where id = p_booking_id returning * into v_booking;
  insert into public.m23_booking_history(booking_id, actor_user_id, from_status, to_status, reason) values (v_booking.id, v_actor, v_previous, 'CANCELLED_BY_CUSTOMER', nullif(trim(p_reason), ''));
  return public._m23_booking_json(v_booking);
end;
$$;

create or replace function public.m23_list_provider_bookings(p_provider_id uuid)
returns setof jsonb language sql security definer set search_path = public as $$
  select public._m23_booking_json(b) from public.m23_bookings b
  join public.m22_service_providers p on p.id = b.provider_id
  where b.provider_id = p_provider_id and public._m23_can_view(p, public._m23_actor()) order by b.starts_at desc;
$$;

create or replace function public.m23_confirm_booking(p_booking_id uuid) returns jsonb language sql security definer set search_path = public as $$
  select public._m23_transition(p_booking_id, 'CONFIRMED');
$$;
create or replace function public.m23_reject_booking(p_booking_id uuid) returns jsonb language sql security definer set search_path = public as $$
  select public._m23_transition(p_booking_id, 'REJECTED');
$$;
create or replace function public.m23_cancel_booking_by_provider(p_booking_id uuid) returns jsonb language sql security definer set search_path = public as $$
  select public._m23_transition(p_booking_id, 'CANCELLED_BY_PROVIDER');
$$;
create or replace function public.m23_complete_booking(p_booking_id uuid) returns jsonb language sql security definer set search_path = public as $$
  select public._m23_transition(p_booking_id, 'COMPLETED');
$$;
create or replace function public.m23_mark_booking_no_show(p_booking_id uuid) returns jsonb language sql security definer set search_path = public as $$
  select public._m23_transition(p_booking_id, 'NO_SHOW');
$$;

create or replace function public.m23_list_availability_rules(p_provider_id uuid)
returns setof jsonb language sql security definer set search_path = public as $$
  select jsonb_build_object('id', r.id, 'provider_id', r.provider_id, 'offering_id', r.offering_id, 'day_of_week', r.day_of_week,
    'start_time', r.start_time, 'end_time', r.end_time, 'slot_duration_minutes', r.slot_duration_minutes, 'zone_id', r.zone_id, 'status', r.status)
  from public.m23_availability_rules r join public.m22_service_providers p on p.id = r.provider_id
  where r.provider_id = p_provider_id and public._m23_can_view(p, public._m23_actor()) order by r.day_of_week, r.start_time;
$$;

create or replace function public.m23_create_availability_rule(
  p_provider_id uuid, p_offering_id uuid, p_branch_id uuid, p_day_of_week smallint, p_start_time time,
  p_end_time time, p_slot_duration_minutes integer, p_zone_id text, p_status text default 'ACTIVE'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m23_actor(); v_provider public.m22_service_providers; v_rule public.m23_availability_rules;
begin
  v_provider := public._m23_assert_provider_offering(p_provider_id, p_offering_id, p_branch_id);
  if not public._m23_can_manage(v_provider, v_actor) then raise exception 'M23_PERMISSION_DENIED'; end if;
  if p_day_of_week not between 1 and 7 or p_end_time <= p_start_time or p_slot_duration_minutes not between 5 and 480
    or upper(coalesce(p_status, '')) not in ('ACTIVE', 'INACTIVE', 'ARCHIVED') then raise exception 'M23_INVALID_AVAILABILITY_RULE'; end if;
  insert into public.m23_availability_rules(provider_id, offering_id, branch_id, organization_id, day_of_week, start_time, end_time, slot_duration_minutes, zone_id, status)
  values (p_provider_id, p_offering_id, p_branch_id, v_provider.organization_id, p_day_of_week, p_start_time, p_end_time, p_slot_duration_minutes, trim(p_zone_id), upper(p_status))
  returning * into v_rule;
  return jsonb_build_object('id', v_rule.id, 'provider_id', v_rule.provider_id, 'offering_id', v_rule.offering_id, 'day_of_week', v_rule.day_of_week,
    'start_time', v_rule.start_time, 'end_time', v_rule.end_time, 'slot_duration_minutes', v_rule.slot_duration_minutes, 'zone_id', v_rule.zone_id, 'status', v_rule.status);
end;
$$;

create or replace function public.m23_create_availability_exception(
  p_provider_id uuid, p_offering_id uuid, p_branch_id uuid, p_exception_date date, p_start_time time,
  p_end_time time, p_type text, p_note text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m23_actor(); v_provider public.m22_service_providers; v_exception public.m23_availability_exceptions;
begin
  select * into v_provider from public.m22_service_providers where id = p_provider_id;
  if not found then raise exception 'M23_PROVIDER_NOT_FOUND'; end if;
  if p_offering_id is not null and not exists (
    select 1 from public.m22_service_offerings where id = p_offering_id and provider_id = p_provider_id and active
  ) then raise exception 'M23_OFFERING_NOT_AVAILABLE'; end if;
  if p_branch_id is not null and not exists (
    select 1 from public.m22_provider_branches where id = p_branch_id and provider_id = p_provider_id
  ) then raise exception 'M23_BRANCH_NOT_FOUND'; end if;
  if not public._m23_can_manage(v_provider, v_actor) then raise exception 'M23_PERMISSION_DENIED'; end if;
  if p_exception_date is null or upper(coalesce(p_type, '')) not in ('BLOCKED', 'SPECIAL_OPENING', 'HOLIDAY', 'PERSONAL_LEAVE', 'ORGANIZATION_CLOSURE', 'OTHER')
    or ((p_start_time is null) <> (p_end_time is null)) or (p_start_time is not null and p_end_time <= p_start_time) then raise exception 'M23_INVALID_AVAILABILITY_EXCEPTION'; end if;
  insert into public.m23_availability_exceptions(provider_id, offering_id, branch_id, organization_id, exception_date, start_time, end_time, type, note)
  values (p_provider_id, p_offering_id, p_branch_id, v_provider.organization_id, p_exception_date, p_start_time, p_end_time, upper(p_type), nullif(trim(p_note), ''))
  returning * into v_exception;
  return jsonb_build_object('id', v_exception.id, 'provider_id', v_exception.provider_id, 'offering_id', v_exception.offering_id, 'date', v_exception.exception_date,
    'start_time', v_exception.start_time, 'end_time', v_exception.end_time, 'type', v_exception.type, 'note', v_exception.note);
end;
$$;

revoke all on function public._m23_actor(), public._m23_can_manage(public.m22_service_providers, uuid), public._m23_can_view(public.m22_service_providers, uuid), public._m23_booking_json(public.m23_bookings), public._m23_assert_provider_offering(uuid, uuid, uuid), public._m23_transition(uuid, text, text) from public, anon, authenticated;
revoke all on function public.m23_get_public_available_slots(uuid, uuid, date, date), public.m23_create_booking_request(uuid, uuid, uuid, timestamptz, timestamptz, text, text, text, text), public.m23_list_my_bookings(), public.m23_get_my_booking(uuid), public.m23_cancel_own_booking(uuid, text), public.m23_list_provider_bookings(uuid), public.m23_confirm_booking(uuid), public.m23_reject_booking(uuid), public.m23_cancel_booking_by_provider(uuid), public.m23_complete_booking(uuid), public.m23_mark_booking_no_show(uuid), public.m23_list_availability_rules(uuid), public.m23_create_availability_rule(uuid, uuid, uuid, smallint, time, time, integer, text, text), public.m23_create_availability_exception(uuid, uuid, uuid, date, time, time, text, text) from public, anon;
grant execute on function public.m23_get_public_available_slots(uuid, uuid, date, date) to anon, authenticated;
grant execute on function public.m23_create_booking_request(uuid, uuid, uuid, timestamptz, timestamptz, text, text, text, text), public.m23_list_my_bookings(), public.m23_get_my_booking(uuid), public.m23_cancel_own_booking(uuid, text), public.m23_list_provider_bookings(uuid), public.m23_confirm_booking(uuid), public.m23_reject_booking(uuid), public.m23_cancel_booking_by_provider(uuid), public.m23_complete_booking(uuid), public.m23_mark_booking_no_show(uuid), public.m23_list_availability_rules(uuid), public.m23_create_availability_rule(uuid, uuid, uuid, smallint, time, time, integer, text, text), public.m23_create_availability_exception(uuid, uuid, uuid, date, time, time, text, text) to authenticated;

commit;
