-- =============================================================================
-- LeoVer M23 — migración 069: operaciones, concurrencia y paridad Bloque 4.
-- Forward-only sobre 068. Aplicar solo después de 068 en staging no productivo.
-- =============================================================================

begin;

create extension if not exists btree_gist;

alter table public.m23_bookings
  add column if not exists pet_id uuid references public.pets(id) on delete set null,
  add column if not exists rescheduled_from_booking_id uuid references public.m23_bookings(id) on delete set null;

alter table public.m23_booking_history
  add column if not exists private_reason text;

create index if not exists m23_bookings_rescheduled_from_idx
  on public.m23_bookings(rescheduled_from_booking_id)
  where rescheduled_from_booking_id is not null;

alter table public.m23_bookings drop constraint if exists m23_booking_no_overlap;
alter table public.m23_bookings add constraint m23_booking_no_overlap exclude using gist (
  provider_id with =,
  tstzrange(starts_at, ends_at, '[)') with &&
) where (status in ('REQUESTED', 'CONFIRMED'));

create or replace function public._m23_active_overlap_exists(
  p_provider_id uuid, p_starts_at timestamptz, p_ends_at timestamptz, p_exclude uuid default null
) returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.m23_bookings b
    where b.provider_id = p_provider_id
      and b.id is distinct from p_exclude
      and b.status in ('REQUESTED', 'CONFIRMED')
      and b.starts_at < p_ends_at and b.ends_at > p_starts_at
  );
$$;

create or replace function public.m23_confirm_booking(p_booking_id uuid)
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
  if v_booking.status = 'CONFIRMED' then return public._m23_booking_json(v_booking); end if;
  if v_booking.status <> 'REQUESTED' then raise exception 'M23_INVALID_STATUS_TRANSITION'; end if;
  if timezone('utc', now()) > v_booking.starts_at then raise exception 'M23_INVALID_STATUS_TRANSITION'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_booking.provider_id::text, 23));
  if public._m23_active_overlap_exists(v_booking.provider_id, v_booking.starts_at, v_booking.ends_at, v_booking.id) then
    raise exception 'M23_SLOT_UNAVAILABLE';
  end if;
  v_previous := v_booking.status;
  update public.m23_bookings set status = 'CONFIRMED', updated_at = timezone('utc', now()) where id = p_booking_id returning * into v_booking;
  insert into public.m23_booking_history(booking_id, actor_user_id, from_status, to_status, reason)
  values (v_booking.id, v_actor, v_previous, 'CONFIRMED', null);
  return public._m23_booking_json(v_booking);
end;
$$;

create or replace function public.m23_reject_booking(
  p_booking_id uuid, p_public_reason text default null, p_private_reason text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
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
  if v_booking.status = 'REJECTED' then return public._m23_booking_json(v_booking); end if;
  if v_booking.status <> 'REQUESTED' then raise exception 'M23_INVALID_STATUS_TRANSITION'; end if;
  v_previous := v_booking.status;
  update public.m23_bookings set status = 'REJECTED', updated_at = timezone('utc', now()) where id = p_booking_id returning * into v_booking;
  insert into public.m23_booking_history(booking_id, actor_user_id, from_status, to_status, reason, private_reason)
  values (v_booking.id, v_actor, v_previous, 'REJECTED', nullif(trim(p_public_reason), ''), nullif(trim(p_private_reason), ''));
  return public._m23_booking_json(v_booking);
end;
$$;

create or replace function public.m23_complete_booking(p_booking_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_booking public.m23_bookings;
begin
  select * into v_booking from public.m23_bookings where id = p_booking_id;
  if not found then raise exception 'M23_BOOKING_NOT_FOUND'; end if;
  if timezone('utc', now()) < v_booking.ends_at then raise exception 'M23_COMPLETE_TOO_EARLY'; end if;
  return public._m23_transition(p_booking_id, 'COMPLETED');
end;
$$;

create or replace function public.m23_mark_booking_no_show(p_booking_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_booking public.m23_bookings;
  v_grace integer := 15;
begin
  select * into v_booking from public.m23_bookings where id = p_booking_id;
  if not found then raise exception 'M23_BOOKING_NOT_FOUND'; end if;
  v_grace := coalesce((v_booking.policy_snapshot -> 'noShow' ->> 'graceMinutes')::integer, 15);
  if timezone('utc', now()) < v_booking.starts_at + make_interval(mins => v_grace) then
    raise exception 'M23_NO_SHOW_TOO_EARLY';
  end if;
  return public._m23_transition(p_booking_id, 'NO_SHOW');
end;
$$;

create or replace function public.m23_expire_booking(p_booking_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m23_actor();
  v_booking public.m23_bookings;
  v_provider public.m22_service_providers;
  v_previous text;
  v_window integer := 1440;
begin
  select * into v_booking from public.m23_bookings where id = p_booking_id for update;
  if not found then raise exception 'M23_BOOKING_NOT_FOUND'; end if;
  select * into v_provider from public.m22_service_providers where id = v_booking.provider_id;
  if not public._m23_can_manage(v_provider, v_actor) then raise exception 'M23_PERMISSION_DENIED'; end if;
  if v_booking.status = 'EXPIRED' then return public._m23_booking_json(v_booking); end if;
  if v_booking.status <> 'REQUESTED' then raise exception 'M23_INVALID_STATUS_TRANSITION'; end if;
  if timezone('utc', now()) < v_booking.created_at + make_interval(mins => v_window) then
    raise exception 'M23_EXPIRE_TOO_EARLY';
  end if;
  v_previous := v_booking.status;
  update public.m23_bookings set status = 'EXPIRED', updated_at = timezone('utc', now()) where id = p_booking_id returning * into v_booking;
  insert into public.m23_booking_history(booking_id, actor_user_id, from_status, to_status, reason)
  values (v_booking.id, v_actor, v_previous, 'EXPIRED', null);
  return public._m23_booking_json(v_booking);
end;
$$;

create or replace function public.m23_reschedule_booking(
  p_booking_id uuid, p_starts_at timestamptz, p_ends_at timestamptz, p_zone_id text,
  p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m23_actor();
  v_old public.m23_bookings;
  v_new public.m23_bookings;
  v_provider public.m22_service_providers;
  v_previous text;
begin
  select * into v_old from public.m23_bookings where id = p_booking_id for update;
  if not found then raise exception 'M23_BOOKING_NOT_FOUND'; end if;
  select * into v_provider from public.m22_service_providers where id = v_old.provider_id;
  if v_old.customer_user_id <> v_actor and not public._m23_can_manage(v_provider, v_actor) then
    raise exception 'M23_PERMISSION_DENIED';
  end if;
  if p_client_request_id is not null then
    select * into v_new from public.m23_bookings where customer_user_id = v_old.customer_user_id and client_request_id = p_client_request_id;
    if found then return public._m23_booking_json(v_new); end if;
  end if;
  if v_old.status not in ('REQUESTED', 'CONFIRMED') then raise exception 'M23_INVALID_STATUS_TRANSITION'; end if;
  if p_starts_at is null or p_ends_at is null or p_ends_at <= p_starts_at then raise exception 'M23_INVALID_BOOKING'; end if;
  if timezone('utc', now()) > p_starts_at then raise exception 'M23_INVALID_BOOKING'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_old.provider_id::text, 23));
  if public._m23_active_overlap_exists(v_old.provider_id, p_starts_at, p_ends_at, v_old.id) then
    raise exception 'M23_SLOT_UNAVAILABLE';
  end if;
  v_previous := v_old.status;
  update public.m23_bookings set status = 'CANCELLED_BY_CUSTOMER', updated_at = timezone('utc', now()) where id = v_old.id;
  insert into public.m23_booking_history(booking_id, actor_user_id, from_status, to_status, reason)
  values (v_old.id, v_actor, v_previous, 'CANCELLED_BY_CUSTOMER', 'RESCHEDULED');
  insert into public.m23_bookings(
    provider_id, offering_id, branch_id, organization_id, customer_user_id, pet_id,
    starts_at, ends_at, zone_id, modality, customer_note, policy_snapshot, client_request_id, rescheduled_from_booking_id
  ) values (
    v_old.provider_id, v_old.offering_id, v_old.branch_id, v_old.organization_id, v_old.customer_user_id, v_old.pet_id,
    p_starts_at, p_ends_at, trim(p_zone_id), v_old.modality, v_old.customer_note, v_old.policy_snapshot,
    nullif(trim(p_client_request_id), ''), v_old.id
  ) returning * into v_new;
  insert into public.m23_booking_history(booking_id, actor_user_id, from_status, to_status, reason)
  values (v_new.id, v_actor, null, 'REQUESTED', null);
  return public._m23_booking_json(v_new);
end;
$$;

create or replace function public.m23_list_booking_history(p_booking_id uuid)
returns setof jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m23_actor();
  v_booking public.m23_bookings;
  v_provider public.m22_service_providers;
  v_manage boolean;
begin
  select * into v_booking from public.m23_bookings where id = p_booking_id;
  if not found then raise exception 'M23_BOOKING_NOT_FOUND'; end if;
  select * into v_provider from public.m22_service_providers where id = v_booking.provider_id;
  v_manage := public._m23_can_manage(v_provider, v_actor);
  if v_booking.customer_user_id <> v_actor and not v_manage and not public._m23_can_view(v_provider, v_actor) then
    raise exception 'M23_PERMISSION_DENIED';
  end if;
  return query
  select jsonb_build_object(
    'from_status', h.from_status, 'to_status', h.to_status,
    'reason', h.reason, 'created_at', h.created_at
  )
  from public.m23_booking_history h
  where h.booking_id = p_booking_id
  order by h.created_at;
end;
$$;

revoke all on function public._m23_active_overlap_exists(uuid, timestamptz, timestamptz, uuid) from public, anon, authenticated;
revoke all on function public.m23_confirm_booking(uuid) from public, anon;
revoke all on function public.m23_reject_booking(uuid, text, text) from public, anon;
revoke all on function public.m23_complete_booking(uuid) from public, anon;
revoke all on function public.m23_mark_booking_no_show(uuid) from public, anon;
revoke all on function public.m23_expire_booking(uuid) from public, anon;
revoke all on function public.m23_reschedule_booking(uuid, timestamptz, timestamptz, text, text) from public, anon;
revoke all on function public.m23_list_booking_history(uuid) from public, anon;

grant execute on function public.m23_confirm_booking(uuid) to authenticated;
grant execute on function public.m23_reject_booking(uuid, text, text) to authenticated;
grant execute on function public.m23_complete_booking(uuid) to authenticated;
grant execute on function public.m23_mark_booking_no_show(uuid) to authenticated;
grant execute on function public.m23_expire_booking(uuid) to authenticated;
grant execute on function public.m23_reschedule_booking(uuid, timestamptz, timestamptz, text, text) to authenticated;
grant execute on function public.m23_list_booking_history(uuid) to authenticated;

commit;
