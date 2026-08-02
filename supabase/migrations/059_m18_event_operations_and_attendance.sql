-- =============================================================================
-- LeoVer M18 — migración 059: operaciones de asistencia, estados extendidos,
-- corrección moderador y RPCs atómicas faltantes.
-- Forward-only sobre 058 aplicada. No modifica 058 retroactivamente.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Corregir _m18_is_moderator (idempotente si 058 ya incluye fix M02)
-- ---------------------------------------------------------------------------
create or replace function public._m18_is_moderator(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_user is not null and (
    public.user_has_active_role(p_user, 'MODERATOR')
    or public.user_has_active_role(p_user, 'ADMIN')
    or public.user_has_active_role(p_user, 'SUPERADMIN')
  );
$$;

revoke all on function public._m18_is_moderator(uuid) from public;
grant execute on function public._m18_is_moderator(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 2. Estados ATTENDED / REJECTED y timestamp de asistencia
-- ---------------------------------------------------------------------------
alter table public.m18_event_registrations
  add column if not exists attended_at timestamptz;

alter table public.m18_event_registrations
  drop constraint if exists m18_reg_status_chk;

alter table public.m18_event_registrations
  add constraint m18_reg_status_chk check (status = any (array[
    'REGISTERED','WAITLISTED','CANCELLED','CHECKED_IN','ATTENDED','NO_SHOW','REJECTED'
  ]::text[]));

-- ---------------------------------------------------------------------------
-- 3. Capacidad y stats incluyen ATTENDED
-- ---------------------------------------------------------------------------
create or replace function public._m18_capacity_summary(p_event_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_max integer;
  v_waitlist boolean;
  v_registered integer;
  v_waitlisted integer;
  v_available integer;
begin
  select max_capacity, waitlist_enabled into v_max, v_waitlist
  from public.m18_community_events where id = p_event_id;
  if not found then return null; end if;

  select count(*)::integer into v_registered
  from public.m18_event_registrations
  where event_id = p_event_id
    and status in ('REGISTERED','CHECKED_IN','ATTENDED');

  select count(*)::integer into v_waitlisted
  from public.m18_event_registrations
  where event_id = p_event_id and status = 'WAITLISTED';

  v_available := greatest(v_max - v_registered, 0);

  return jsonb_build_object(
    'max_capacity', v_max,
    'registered_count', v_registered,
    'waitlist_count', v_waitlisted,
    'available_spots', v_available,
    'is_full', v_available = 0,
    'is_waitlist_open', v_waitlist and v_available = 0
  );
end;
$$;

create or replace function public._m18_registration_stats(p_event_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'registered_count', count(*) filter (where status in ('REGISTERED','CHECKED_IN','ATTENDED')),
    'waitlist_count', count(*) filter (where status = 'WAITLISTED'),
    'checked_in_count', count(*) filter (where status in ('CHECKED_IN','ATTENDED'))
  )
  from public.m18_event_registrations
  where event_id = p_event_id;
$$;

create or replace function public._m18_internal_registration_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare r public.m18_event_registrations;
begin
  select * into r from public.m18_event_registrations where id = p_id;
  if not found then return null; end if;
  return jsonb_build_object(
    'id', r.id,
    'event_id', r.event_id,
    'user_id', r.user_id,
    'status', r.status,
    'attendee_display_name', r.attendee_display_name,
    'registered_at', r.registered_at,
    'checked_in_at', r.checked_in_at,
    'attended_at', r.attended_at,
    'reminder_scheduled', r.reminder_scheduled
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. Registro: rechazar evento pasado; idempotencia ATTENDED
-- ---------------------------------------------------------------------------
create or replace function public.m18_register_for_event(p_event_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m18_require_authenticated();
  v public.m18_community_events;
  v_existing public.m18_event_registrations;
  v_summary jsonb;
  v_status text;
  v_id uuid;
  v_now timestamptz := timezone('utc', now());
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  if v.event_status <> 'PUBLISHED' then
    if v.event_status in ('COMPLETED','CANCELLED') then raise exception 'M18_EVENT_TERMINAL';
    elsif v.event_status = 'PAUSED' then raise exception 'M18_EVENT_NOT_OPEN';
    else raise exception 'M18_EVENT_NOT_PUBLIC';
    end if;
  end if;
  if v.ends_at < v_now then raise exception 'M18_EVENT_TERMINAL'; end if;

  select * into v_existing
  from public.m18_event_registrations
  where event_id = p_event_id and user_id = v_actor;

  if found and v_existing.status in ('REGISTERED','WAITLISTED','CHECKED_IN','ATTENDED') then
    return public._m18_internal_registration_json(v_existing.id);
  end if;

  v_summary := public._m18_capacity_summary(p_event_id);
  if (v_summary->>'available_spots')::integer > 0 then
    v_status := 'REGISTERED';
  elsif (v_summary->>'is_waitlist_open')::boolean then
    v_status := 'WAITLISTED';
  else
    raise exception 'M18_EVENT_FULL';
  end if;

  if found then
    update public.m18_event_registrations set
      status = v_status,
      registered_at = v_now,
      checked_in_at = null,
      attended_at = null,
      updated_at = v_now
    where id = v_existing.id
    returning id into v_id;
  else
    insert into public.m18_event_registrations (event_id, user_id, status)
    values (p_event_id, v_actor, v_status)
    returning id into v_id;
  end if;
  return public._m18_internal_registration_json(v_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. Promoción waitlist pública (idempotente)
-- ---------------------------------------------------------------------------
create or replace function public.m18_promote_next_waitlisted(p_event_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m18_community_events;
  v_summary jsonb;
  v_next uuid;
  v_before uuid;
begin
  select * into v from public.m18_community_events where id = p_event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');

  v_summary := public._m18_capacity_summary(p_event_id);
  if (v_summary->>'available_spots')::integer <= 0 then
    return null;
  end if;

  select id into v_next
  from public.m18_event_registrations
  where event_id = p_event_id and status = 'WAITLISTED'
  order by registered_at asc
  limit 1;

  if v_next is null then return null; end if;

  select id into v_before
  from public.m18_event_registrations
  where id = v_next and status = 'REGISTERED';

  if v_before is not null then
    return public._m18_internal_registration_json(v_before);
  end if;

  update public.m18_event_registrations
  set status = 'REGISTERED', updated_at = timezone('utc', now())
  where id = v_next and status = 'WAITLISTED';

  return public._m18_internal_registration_json(v_next);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Marcar asistencia (ATTENDED)
-- ---------------------------------------------------------------------------
create or replace function public.m18_mark_attendance(p_registration_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_reg public.m18_event_registrations;
  v public.m18_community_events;
  v_now timestamptz := timezone('utc', now());
begin
  select * into v_reg from public.m18_event_registrations where id = p_registration_id;
  if not found then raise exception 'M18_REGISTRATION_NOT_FOUND'; end if;
  select * into v from public.m18_community_events where id = v_reg.event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');

  if v_reg.status = 'ATTENDED' then
    return public._m18_internal_registration_json(v_reg.id);
  end if;
  if v_reg.status not in ('REGISTERED','CHECKED_IN') then
    raise exception 'M18_INVALID_ATTENDANCE_STATE';
  end if;

  update public.m18_event_registrations set
    status = 'ATTENDED',
    attended_at = v_now,
    checked_in_at = coalesce(checked_in_at, v_now),
    updated_at = v_now
  where id = p_registration_id;

  return public._m18_internal_registration_json(p_registration_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. Marcar no-show (solo post-evento)
-- ---------------------------------------------------------------------------
create or replace function public.m18_mark_no_show(p_registration_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_reg public.m18_event_registrations;
  v public.m18_community_events;
  v_now timestamptz := timezone('utc', now());
begin
  select * into v_reg from public.m18_event_registrations where id = p_registration_id;
  if not found then raise exception 'M18_REGISTRATION_NOT_FOUND'; end if;
  select * into v from public.m18_community_events where id = v_reg.event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');

  if v_reg.status = 'NO_SHOW' then
    return public._m18_internal_registration_json(v_reg.id);
  end if;
  if v.ends_at > v_now then raise exception 'M18_EVENT_NOT_ENDED'; end if;
  if v_reg.status not in ('REGISTERED','CHECKED_IN') then
    raise exception 'M18_INVALID_NOSHOW_STATE';
  end if;

  update public.m18_event_registrations set
    status = 'NO_SHOW',
    updated_at = v_now
  where id = p_registration_id;

  return public._m18_internal_registration_json(p_registration_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Rechazar inscripción (terminal REJECTED)
-- ---------------------------------------------------------------------------
create or replace function public.m18_reject_registration(p_registration_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_reg public.m18_event_registrations;
  v public.m18_community_events;
begin
  select * into v_reg from public.m18_event_registrations where id = p_registration_id;
  if not found then raise exception 'M18_REGISTRATION_NOT_FOUND'; end if;
  select * into v from public.m18_community_events where id = v_reg.event_id;
  if not found then raise exception 'M18_EVENT_NOT_FOUND'; end if;
  perform public._m18_require_org_perm(v.organization_id, 'event.manage');

  if v_reg.status = 'REJECTED' then
    return public._m18_internal_registration_json(v_reg.id);
  end if;

  update public.m18_event_registrations set
    status = 'REJECTED',
    updated_at = timezone('utc', now())
  where id = p_registration_id;

  perform public._m18_promote_waitlist(v.id);

  return public._m18_internal_registration_json(p_registration_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 9. Grants
-- ---------------------------------------------------------------------------
revoke all on function public.m18_promote_next_waitlisted(uuid) from public;
grant execute on function public.m18_promote_next_waitlisted(uuid) to authenticated;
revoke all on function public.m18_mark_attendance(uuid) from public;
grant execute on function public.m18_mark_attendance(uuid) to authenticated;
revoke all on function public.m18_mark_no_show(uuid) from public;
grant execute on function public.m18_mark_no_show(uuid) to authenticated;
revoke all on function public.m18_reject_registration(uuid) from public;
grant execute on function public.m18_reject_registration(uuid) to authenticated;

-- Revocar acceso directo anon (superficie solo vía RPC sanitizadas)
revoke all on table public.m18_community_events from anon;
revoke all on table public.m18_event_registrations from anon;
revoke all on table public.m18_event_reminders from anon;

commit;
