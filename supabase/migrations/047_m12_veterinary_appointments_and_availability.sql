-- =============================================================================
-- LeoVer M12 — migración 047: agenda, disponibilidad y solicitudes de turno
-- Bloque 3. Forward-only sobre 001–046. NO modifica 040–046 ni service_profiles/bookings.
-- Sin pagos, señas, checkout, historia clínica, diagnóstico, recetas ni laboratorio.
-- Slots calculados en RPC (sin tabla de slots pre-generados).
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 veterinary.* (deny-by-default vía has_org_permission)
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('veterinary.schedule.read', 'Ver configuración de agenda y disponibilidad veterinaria'),
  ('veterinary.schedule.manage', 'Gestionar agenda, reglas y excepciones de disponibilidad veterinaria'),
  ('veterinary.appointment.read', 'Ver solicitudes y turnos veterinarios gestionados'),
  ('veterinary.appointment.request', 'Solicitar turnos veterinarios'),
  ('veterinary.appointment.manage', 'Gestionar el ciclo de vida de turnos veterinarios')
on conflict (code) do nothing;

-- OWNER/ADMIN/MANAGER: todo veterinary.schedule.% y veterinary.appointment.% (incluye request).
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and (p.code like 'veterinary.schedule.%' or p.code like 'veterinary.appointment.%')
on conflict do nothing;

-- MEMBER: lectura de agenda, lectura de turnos y solicitud de turnos.
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code in (
    'veterinary.schedule.read',
    'veterinary.appointment.read',
    'veterinary.appointment.request'
  )
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.veterinary_schedule_settings (
  clinic_id uuid primary key references public.veterinary_clinic_profiles (id) on delete restrict,
  timezone_name text not null default 'America/Argentina/Buenos_Aires',
  booking_horizon_days integer not null default 30,
  minimum_notice_minutes integer not null default 60,
  cancellation_notice_minutes integer not null default 120,
  default_slot_duration_minutes integer not null default 30,
  active boolean not null default true,
  updated_by uuid references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_schedule_settings_horizon_chk
    check (booking_horizon_days between 1 and 365),
  constraint veterinary_schedule_settings_min_notice_chk
    check (minimum_notice_minutes between 0 and 10080),
  constraint veterinary_schedule_settings_cancel_notice_chk
    check (cancellation_notice_minutes between 0 and 43200),
  constraint veterinary_schedule_settings_slot_duration_chk
    check (default_slot_duration_minutes between 5 and 480),
  constraint veterinary_schedule_settings_timezone_chk
    check (char_length(trim(timezone_name)) > 0)
);

create index if not exists veterinary_schedule_settings_active_idx
  on public.veterinary_schedule_settings (active);

create table if not exists public.veterinary_availability_rules (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  professional_id uuid null references public.veterinary_professionals (id) on delete restrict,
  service_id uuid null references public.veterinary_services (id) on delete restrict,
  day_of_week integer not null,
  starts_at time not null,
  ends_at time not null,
  slot_duration_minutes integer not null,
  capacity_per_slot integer not null,
  valid_from date null,
  valid_until date null,
  active boolean not null default true,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  -- ISO day_of_week (1=lunes ... 7=domingo).
  constraint veterinary_availability_rule_dow_chk check (day_of_week between 1 and 7),
  constraint veterinary_availability_rule_window_chk check (ends_at > starts_at),
  constraint veterinary_availability_rule_slot_chk check (slot_duration_minutes between 5 and 480),
  constraint veterinary_availability_rule_capacity_chk check (capacity_per_slot between 1 and 50),
  constraint veterinary_availability_rule_validity_chk
    check (valid_from is null or valid_until is null or valid_until >= valid_from)
);

create index if not exists veterinary_availability_rules_clinic_idx
  on public.veterinary_availability_rules (clinic_id);
create index if not exists veterinary_availability_rules_professional_idx
  on public.veterinary_availability_rules (professional_id);
create index if not exists veterinary_availability_rules_service_idx
  on public.veterinary_availability_rules (service_id);
create index if not exists veterinary_availability_rules_dow_idx
  on public.veterinary_availability_rules (clinic_id, day_of_week) where active;

create table if not exists public.veterinary_availability_exceptions (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  rule_id uuid null references public.veterinary_availability_rules (id) on delete cascade,
  exception_date date not null,
  type text not null,
  starts_at time null,
  ends_at time null,
  capacity_per_slot integer null,
  reason text null,
  active boolean not null default true,
  created_by uuid not null references public.users (id),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_availability_exception_type_allowed
    check (type = any (array['CLOSED','CUSTOM_HOURS','CAPACITY_OVERRIDE']::text[])),
  constraint veterinary_availability_exception_capacity_chk
    check (capacity_per_slot is null or capacity_per_slot between 1 and 50),
  constraint veterinary_availability_exception_reason_chk
    check (reason is null or char_length(reason) <= 500),
  -- Coherencia por tipo:
  --   CLOSED: sin horas.
  --   CUSTOM_HOURS: ambas horas y ends > starts.
  --   CAPACITY_OVERRIDE: capacity_per_slot requerido.
  constraint veterinary_availability_exception_shape_chk check (
    (type = 'CLOSED' and starts_at is null and ends_at is null)
    or (type = 'CUSTOM_HOURS' and starts_at is not null and ends_at is not null and ends_at > starts_at)
    or (type = 'CAPACITY_OVERRIDE' and capacity_per_slot is not null)
  )
);

create index if not exists veterinary_availability_exceptions_clinic_date_idx
  on public.veterinary_availability_exceptions (clinic_id, exception_date) where active;
create index if not exists veterinary_availability_exceptions_rule_idx
  on public.veterinary_availability_exceptions (rule_id);

create table if not exists public.veterinary_appointments (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.veterinary_clinic_profiles (id) on delete restrict,
  professional_id uuid null references public.veterinary_professionals (id) on delete restrict,
  service_id uuid not null references public.veterinary_services (id) on delete restrict,
  pet_id uuid not null references public.pets (id) on delete restrict,
  requester_user_id uuid not null references public.users (id) on delete restrict,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  status text not null default 'REQUESTED',
  request_note text null,
  clinic_operational_note text null,
  rejection_reason text null,
  cancellation_reason text null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_appointment_status_allowed
    check (status = any (array[
      'REQUESTED','CONFIRMED','REJECTED','CANCELLED_BY_USER','CANCELLED_BY_CLINIC',
      'COMPLETED','NO_SHOW','EXPIRED'
    ]::text[])),
  constraint veterinary_appointment_window_chk check (ends_at > starts_at),
  constraint veterinary_appointment_request_note_chk
    check (request_note is null or char_length(request_note) <= 1000),
  constraint veterinary_appointment_operational_note_chk
    check (clinic_operational_note is null or char_length(clinic_operational_note) <= 1000),
  constraint veterinary_appointment_rejection_reason_chk
    check (rejection_reason is null or char_length(rejection_reason) <= 500),
  constraint veterinary_appointment_cancellation_reason_chk
    check (cancellation_reason is null or char_length(cancellation_reason) <= 500)
);

create index if not exists veterinary_appointments_clinic_idx
  on public.veterinary_appointments (clinic_id);
create index if not exists veterinary_appointments_requester_idx
  on public.veterinary_appointments (requester_user_id);
create index if not exists veterinary_appointments_pet_idx
  on public.veterinary_appointments (pet_id);
create index if not exists veterinary_appointments_status_idx
  on public.veterinary_appointments (status);
create index if not exists veterinary_appointments_starts_at_idx
  on public.veterinary_appointments (starts_at);
create index if not exists veterinary_appointments_clinic_status_starts_idx
  on public.veterinary_appointments (clinic_id, status, starts_at);

-- Evita duplicar exactamente la misma solicitud vigente del mismo actor/clínica/servicio/inicio.
create unique index if not exists veterinary_appointments_active_slot_uniq
  on public.veterinary_appointments (requester_user_id, clinic_id, service_id, starts_at)
  where status in ('REQUESTED','CONFIRMED');

create table if not exists public.veterinary_appointment_status_history (
  id uuid primary key default gen_random_uuid(),
  appointment_id uuid not null references public.veterinary_appointments (id) on delete cascade,
  from_status text null,
  to_status text not null,
  changed_by uuid not null references public.users (id),
  reason text null,
  changed_at timestamptz not null default timezone('utc', now()),
  constraint veterinary_appointment_history_to_status_allowed
    check (to_status = any (array[
      'REQUESTED','CONFIRMED','REJECTED','CANCELLED_BY_USER','CANCELLED_BY_CLINIC',
      'COMPLETED','NO_SHOW','EXPIRED'
    ]::text[])),
  constraint veterinary_appointment_history_from_status_allowed
    check (from_status is null or from_status = any (array[
      'REQUESTED','CONFIRMED','REJECTED','CANCELLED_BY_USER','CANCELLED_BY_CLINIC',
      'COMPLETED','NO_SHOW','EXPIRED'
    ]::text[])),
  constraint veterinary_appointment_history_reason_chk
    check (reason is null or char_length(reason) <= 500)
);

create index if not exists veterinary_appointment_status_history_appt_idx
  on public.veterinary_appointment_status_history (appointment_id, changed_at);

-- ---------------------------------------------------------------------------
-- 2. Catálogo M07
-- ---------------------------------------------------------------------------
insert into public.observability_event_catalog (
  event_key, module, category, default_severity, sensitivity, organization_scoped,
  retention_policy_key, remote_persistence_allowed, analytics_allowed,
  allowed_metadata_keys, required_metadata_keys
) values
('VETERINARY_SCHEDULE_SETTINGS_UPDATED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_AVAILABILITY_RULE_CHANGED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_AVAILABILITY_EXCEPTION_CHANGED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_APPOINTMENT_REQUESTED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_APPOINTMENT_CONFIRMED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_APPOINTMENT_REJECTED','M12','AUDIT','NOTICE','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_APPOINTMENT_CANCELLED','M12','AUDIT','NOTICE','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_APPOINTMENT_COMPLETED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_APPOINTMENT_NO_SHOW','M12','AUDIT','NOTICE','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[]),
('VETERINARY_APPOINTMENT_EXPIRED','M12','AUDIT','INFO','INTERNAL',true,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','resource_type','resource_id','organization_id','correlation_id']::text[],
 ARRAY['result']::text[])
on conflict (event_key) do nothing;

-- ---------------------------------------------------------------------------
-- 3. Helpers (SECURITY DEFINER, search_path=public)
-- Reutiliza _m12_require_authenticated y _m12_require_clinic_manage de 046.
-- ---------------------------------------------------------------------------
create or replace function public._m12_require_schedule_manage(p_clinic_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_row public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_row.organization_id, 'veterinary.schedule.manage');
  return v_row;
end;
$$;

create or replace function public._m12_require_schedule_read(p_clinic_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_row public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if not (
    public.has_org_permission(v_row.organization_id, 'veterinary.schedule.read')
    or public.has_org_permission(v_row.organization_id, 'veterinary.schedule.manage')
  ) then
    raise exception 'VETERINARY_CLINIC_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public._m12_require_appointment_manage(p_clinic_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_row public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  perform public._m12_require_org_perm(v_row.organization_id, 'veterinary.appointment.manage');
  return v_row;
end;
$$;

create or replace function public._m12_require_appointment_read(p_clinic_id uuid)
returns public.veterinary_clinic_profiles
language plpgsql stable security definer set search_path = public as $$
declare v_row public.veterinary_clinic_profiles;
begin
  perform public._m12_require_authenticated();
  select * into v_row from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if not (
    public.has_org_permission(v_row.organization_id, 'veterinary.appointment.read')
    or public.has_org_permission(v_row.organization_id, 'veterinary.appointment.manage')
  ) then
    raise exception 'VETERINARY_CLINIC_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

-- Estados que consumen cupo de disponibilidad.
create or replace function public._m12_appointment_consumes_capacity(p_status text)
returns boolean
language sql immutable parallel safe security definer set search_path = public as $$
  select coalesce(p_status, '') in ('REQUESTED', 'CONFIRMED');
$$;

create or replace function public._m12_require_valid_timezone(p_timezone_name text)
returns void
language plpgsql stable security definer set search_path = public as $$
declare v_probe timestamptz;
begin
  if p_timezone_name is null or char_length(trim(p_timezone_name)) = 0 then
    raise exception 'VETERINARY_TIMEZONE_INVALID';
  end if;
  begin
    v_probe := timezone(p_timezone_name, timestamp '2000-01-01 00:00:00');
  exception when others then
    raise exception 'VETERINARY_TIMEZONE_INVALID';
  end;
  if v_probe is null then
    raise exception 'VETERINARY_TIMEZONE_INVALID';
  end if;
end;
$$;

-- Cuenta reservas vigentes (REQUESTED/CONFIRMED) que solapan la ventana del slot.
create or replace function public._m12_count_slot_reservations(
  p_clinic_id uuid,
  p_professional_id uuid,
  p_service_id uuid,
  p_starts_at timestamptz,
  p_ends_at timestamptz
) returns integer
language sql stable security definer set search_path = public as $$
  select count(*)::integer
  from public.veterinary_appointments a
  where a.clinic_id = p_clinic_id
    and a.service_id = p_service_id
    and a.status in ('REQUESTED', 'CONFIRMED')
    and a.starts_at < p_ends_at
    and a.ends_at > p_starts_at
    and (
      (p_professional_id is null and a.professional_id is null)
      or (p_professional_id is not null and a.professional_id = p_professional_id)
    );
$$;

-- Resuelve el cupo aplicable a un slot puntual combinando reglas + excepciones (en la zona de la clínica).
-- Devuelve 0 cuando no hay disponibilidad configurada o hay cierre.
create or replace function public._m12_resolve_slot_capacity(
  p_clinic_id uuid,
  p_professional_id uuid,
  p_service_id uuid,
  p_starts_at timestamptz,
  p_ends_at timestamptz
) returns integer
language plpgsql stable security definer set search_path = public as $$
declare
  v_tz text;
  v_active boolean;
  v_local_start timestamp;
  v_local_end timestamp;
  v_date date;
  v_dow integer;
  v_start_t time;
  v_end_t time;
  v_rule record;
  v_win_start time;
  v_win_end time;
  v_cap integer;
  v_exc_time record;
  v_exc_cap integer;
begin
  select timezone_name, active into v_tz, v_active
  from public.veterinary_schedule_settings where clinic_id = p_clinic_id;
  if v_tz is null or not coalesce(v_active, false) then
    return 0;
  end if;

  v_local_start := p_starts_at at time zone v_tz;
  v_local_end := p_ends_at at time zone v_tz;
  v_date := v_local_start::date;
  v_dow := extract(isodow from v_local_start)::integer;
  v_start_t := v_local_start::time;
  v_end_t := v_local_end::time;

  -- Cierre a nivel clínica.
  if exists (
    select 1 from public.veterinary_availability_exceptions e
    where e.clinic_id = p_clinic_id and e.active
      and e.exception_date = v_date and e.type = 'CLOSED' and e.rule_id is null
  ) then
    return 0;
  end if;

  for v_rule in
    select r.* from public.veterinary_availability_rules r
    where r.clinic_id = p_clinic_id
      and r.active
      and r.day_of_week = v_dow
      and (r.service_id is null or r.service_id = p_service_id)
      and (r.professional_id is null or r.professional_id = p_professional_id)
      and (r.valid_from is null or r.valid_from <= v_date)
      and (r.valid_until is null or r.valid_until >= v_date)
  loop
    -- Cierre puntual de esta regla.
    if exists (
      select 1 from public.veterinary_availability_exceptions e
      where e.clinic_id = p_clinic_id and e.active
        and e.exception_date = v_date and e.type = 'CLOSED' and e.rule_id = v_rule.id
    ) then
      continue;
    end if;

    v_win_start := v_rule.starts_at;
    v_win_end := v_rule.ends_at;
    v_cap := v_rule.capacity_per_slot;

    -- Horario custom: preferir excepción específica de la regla, luego a nivel clínica.
    select e.starts_at, e.ends_at into v_exc_time
    from public.veterinary_availability_exceptions e
    where e.clinic_id = p_clinic_id and e.active
      and e.exception_date = v_date and e.type = 'CUSTOM_HOURS'
      and e.rule_id = v_rule.id
    order by e.updated_at desc limit 1;
    if not found then
      select e.starts_at, e.ends_at into v_exc_time
      from public.veterinary_availability_exceptions e
      where e.clinic_id = p_clinic_id and e.active
        and e.exception_date = v_date and e.type = 'CUSTOM_HOURS'
        and e.rule_id is null
      order by e.updated_at desc limit 1;
    end if;
    if found and v_exc_time.starts_at is not null and v_exc_time.ends_at is not null then
      v_win_start := v_exc_time.starts_at;
      v_win_end := v_exc_time.ends_at;
    end if;

    -- Override de capacidad: específico de regla, luego clínica.
    select e.capacity_per_slot into v_exc_cap
    from public.veterinary_availability_exceptions e
    where e.clinic_id = p_clinic_id and e.active
      and e.exception_date = v_date and e.type = 'CAPACITY_OVERRIDE'
      and e.rule_id = v_rule.id
    order by e.updated_at desc limit 1;
    if not found then
      select e.capacity_per_slot into v_exc_cap
      from public.veterinary_availability_exceptions e
      where e.clinic_id = p_clinic_id and e.active
        and e.exception_date = v_date and e.type = 'CAPACITY_OVERRIDE'
        and e.rule_id is null
      order by e.updated_at desc limit 1;
    end if;
    if found and v_exc_cap is not null then
      v_cap := v_exc_cap;
    end if;

    if v_start_t >= v_win_start and v_end_t <= v_win_end then
      return greatest(coalesce(v_cap, 0), 0);
    end if;
  end loop;

  return 0;
end;
$$;

-- Verifica autoridad M08 del actor sobre la mascota para solicitar turno.
create or replace function public._m12_assert_pet_bookable(p_pet_id uuid, p_actor uuid)
returns void
language plpgsql stable security definer set search_path = public as $$
declare v_ok boolean;
begin
  if p_actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_pet_id is null then raise exception 'VETERINARY_APPOINTMENT_PET_FORBIDDEN'; end if;
  if not exists (select 1 from public.pets p where p.id = p_pet_id) then
    raise exception 'VETERINARY_APPOINTMENT_PET_FORBIDDEN';
  end if;

  begin
    v_ok := public.m08_actor_has_active_responsibility(p_pet_id, p_actor);
  exception when undefined_function then
    v_ok := null;
  end;

  if v_ok is null then
    -- Fallback: responsabilidad ACTIVA por persona o custodia organizacional (OWNER/ADMIN).
    v_ok := exists (
      select 1 from public.pet_responsibilities r
      where r.pet_id = p_pet_id
        and r.status = 'ACTIVE'
        and (r.ends_at is null or r.ends_at > timezone('utc', now()))
        and (
          r.person_id = p_actor
          or (
            r.organization_id is not null
            and exists (
              select 1 from public.organization_memberships m
              where m.organization_id = r.organization_id
                and m.user_id = p_actor
                and m.status = 'ACTIVE'
                and m.role_code in ('OWNER', 'ADMIN')
            )
          )
        )
    );
  end if;

  if not coalesce(v_ok, false) then
    raise exception 'VETERINARY_APPOINTMENT_PET_FORBIDDEN';
  end if;
end;
$$;

create or replace function public._m12_append_appointment_history(
  p_appointment_id uuid,
  p_from_status text,
  p_to_status text,
  p_changed_by uuid,
  p_reason text default null
) returns void
language plpgsql security definer set search_path = public as $$
begin
  insert into public.veterinary_appointment_status_history (
    appointment_id, from_status, to_status, changed_by, reason
  ) values (
    p_appointment_id, p_from_status, p_to_status, p_changed_by,
    nullif(trim(coalesce(p_reason, '')), '')
  );
end;
$$;

-- Núcleo de transición: valida matriz + guardas temporales, escribe estado, historial y auditoría.
create or replace function public._m12_transition_appointment(
  p_appointment_id uuid,
  p_to_status text,
  p_reason text default null,
  p_changed_by uuid default null
) returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_row public.veterinary_appointments;
  v_clinic public.veterinary_clinic_profiles;
  v_to text := upper(trim(coalesce(p_to_status, '')));
  v_from text;
  v_actor uuid := coalesce(p_changed_by, auth.uid());
  v_reason text := nullif(trim(coalesce(p_reason, '')), '');
  v_event text;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id for update;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  v_from := v_row.status;

  if v_from in ('REJECTED','CANCELLED_BY_USER','CANCELLED_BY_CLINIC','COMPLETED','NO_SHOW','EXPIRED') then
    raise exception 'VETERINARY_APPOINTMENT_ALREADY_FINAL';
  end if;

  if not (
    (v_from = 'REQUESTED' and v_to in ('CONFIRMED','REJECTED','CANCELLED_BY_USER','CANCELLED_BY_CLINIC','EXPIRED'))
    or (v_from = 'CONFIRMED' and v_to in ('CANCELLED_BY_USER','CANCELLED_BY_CLINIC','COMPLETED','NO_SHOW'))
  ) then
    raise exception 'VETERINARY_APPOINTMENT_INVALID_TRANSITION';
  end if;

  -- Guardas temporales.
  if v_to = 'CONFIRMED' and v_row.starts_at <= timezone('utc', now()) then
    raise exception 'VETERINARY_APPOINTMENT_PAST_SLOT';
  end if;
  if v_to = 'COMPLETED' and v_row.ends_at > timezone('utc', now()) then
    raise exception 'VETERINARY_APPOINTMENT_INVALID_TRANSITION';
  end if;
  if v_to = 'NO_SHOW' and v_row.starts_at > timezone('utc', now()) then
    raise exception 'VETERINARY_APPOINTMENT_INVALID_TRANSITION';
  end if;

  update public.veterinary_appointments set
    status = v_to,
    rejection_reason = case when v_to = 'REJECTED' then v_reason else rejection_reason end,
    cancellation_reason = case
      when v_to in ('CANCELLED_BY_USER','CANCELLED_BY_CLINIC') then v_reason
      else cancellation_reason end,
    updated_at = timezone('utc', now())
  where id = p_appointment_id returning * into v_row;

  perform public._m12_append_appointment_history(p_appointment_id, v_from, v_to, v_actor, v_reason);

  v_event := case v_to
    when 'CONFIRMED' then 'VETERINARY_APPOINTMENT_CONFIRMED'
    when 'REJECTED' then 'VETERINARY_APPOINTMENT_REJECTED'
    when 'CANCELLED_BY_USER' then 'VETERINARY_APPOINTMENT_CANCELLED'
    when 'CANCELLED_BY_CLINIC' then 'VETERINARY_APPOINTMENT_CANCELLED'
    when 'COMPLETED' then 'VETERINARY_APPOINTMENT_COMPLETED'
    when 'NO_SHOW' then 'VETERINARY_APPOINTMENT_NO_SHOW'
    when 'EXPIRED' then 'VETERINARY_APPOINTMENT_EXPIRED'
    else 'VETERINARY_APPOINTMENT_CONFIRMED'
  end;

  select * into v_clinic from public.veterinary_clinic_profiles where id = v_row.clinic_id;

  -- M06 hook: recordatorio/notificación de dominio preparado, NO se entrega push real aquí.
  null; -- M06 hook: v_event (client/domain)

  perform public.m07_best_effort_audit(
    v_event, 'STATUS_CHANGE', 'SUCCESS', v_corr,
    'veterinary_appointment', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12',
      'organization_id', v_clinic.organization_id,
      'from_status', v_from, 'to_status', v_to)
  );
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RLS + grants (mutaciones cliente solo vía RPC SECURITY DEFINER)
-- ---------------------------------------------------------------------------
alter table public.veterinary_schedule_settings enable row level security;
alter table public.veterinary_availability_rules enable row level security;
alter table public.veterinary_availability_exceptions enable row level security;
alter table public.veterinary_appointments enable row level security;
alter table public.veterinary_appointment_status_history enable row level security;

drop policy if exists veterinary_schedule_settings_select_m12 on public.veterinary_schedule_settings;
drop policy if exists veterinary_schedule_settings_ins_m12 on public.veterinary_schedule_settings;
drop policy if exists veterinary_schedule_settings_upd_m12 on public.veterinary_schedule_settings;
drop policy if exists veterinary_schedule_settings_del_m12 on public.veterinary_schedule_settings;
create policy veterinary_schedule_settings_select_m12 on public.veterinary_schedule_settings for select to authenticated using (false);
create policy veterinary_schedule_settings_ins_m12 on public.veterinary_schedule_settings for insert to authenticated with check (false);
create policy veterinary_schedule_settings_upd_m12 on public.veterinary_schedule_settings for update to authenticated using (false);
create policy veterinary_schedule_settings_del_m12 on public.veterinary_schedule_settings for delete to authenticated using (false);

drop policy if exists veterinary_availability_rules_select_m12 on public.veterinary_availability_rules;
drop policy if exists veterinary_availability_rules_ins_m12 on public.veterinary_availability_rules;
drop policy if exists veterinary_availability_rules_upd_m12 on public.veterinary_availability_rules;
drop policy if exists veterinary_availability_rules_del_m12 on public.veterinary_availability_rules;
create policy veterinary_availability_rules_select_m12 on public.veterinary_availability_rules for select to authenticated using (false);
create policy veterinary_availability_rules_ins_m12 on public.veterinary_availability_rules for insert to authenticated with check (false);
create policy veterinary_availability_rules_upd_m12 on public.veterinary_availability_rules for update to authenticated using (false);
create policy veterinary_availability_rules_del_m12 on public.veterinary_availability_rules for delete to authenticated using (false);

drop policy if exists veterinary_availability_exceptions_select_m12 on public.veterinary_availability_exceptions;
drop policy if exists veterinary_availability_exceptions_ins_m12 on public.veterinary_availability_exceptions;
drop policy if exists veterinary_availability_exceptions_upd_m12 on public.veterinary_availability_exceptions;
drop policy if exists veterinary_availability_exceptions_del_m12 on public.veterinary_availability_exceptions;
create policy veterinary_availability_exceptions_select_m12 on public.veterinary_availability_exceptions for select to authenticated using (false);
create policy veterinary_availability_exceptions_ins_m12 on public.veterinary_availability_exceptions for insert to authenticated with check (false);
create policy veterinary_availability_exceptions_upd_m12 on public.veterinary_availability_exceptions for update to authenticated using (false);
create policy veterinary_availability_exceptions_del_m12 on public.veterinary_availability_exceptions for delete to authenticated using (false);

drop policy if exists veterinary_appointments_select_m12 on public.veterinary_appointments;
drop policy if exists veterinary_appointments_ins_m12 on public.veterinary_appointments;
drop policy if exists veterinary_appointments_upd_m12 on public.veterinary_appointments;
drop policy if exists veterinary_appointments_del_m12 on public.veterinary_appointments;
create policy veterinary_appointments_select_m12 on public.veterinary_appointments for select to authenticated using (false);
create policy veterinary_appointments_ins_m12 on public.veterinary_appointments for insert to authenticated with check (false);
create policy veterinary_appointments_upd_m12 on public.veterinary_appointments for update to authenticated using (false);
create policy veterinary_appointments_del_m12 on public.veterinary_appointments for delete to authenticated using (false);

drop policy if exists veterinary_appointment_status_history_select_m12 on public.veterinary_appointment_status_history;
drop policy if exists veterinary_appointment_status_history_ins_m12 on public.veterinary_appointment_status_history;
drop policy if exists veterinary_appointment_status_history_upd_m12 on public.veterinary_appointment_status_history;
drop policy if exists veterinary_appointment_status_history_del_m12 on public.veterinary_appointment_status_history;
create policy veterinary_appointment_status_history_select_m12 on public.veterinary_appointment_status_history for select to authenticated using (false);
create policy veterinary_appointment_status_history_ins_m12 on public.veterinary_appointment_status_history for insert to authenticated with check (false);
create policy veterinary_appointment_status_history_upd_m12 on public.veterinary_appointment_status_history for update to authenticated using (false);
create policy veterinary_appointment_status_history_del_m12 on public.veterinary_appointment_status_history for delete to authenticated using (false);

-- Hardening estilo 044/046: sin DML/SELECT directo para public/anon/authenticated (solo RPC).
revoke all privileges on table public.veterinary_schedule_settings from public;
revoke all privileges on table public.veterinary_schedule_settings from anon;
revoke all privileges on table public.veterinary_schedule_settings from authenticated;
revoke all privileges on table public.veterinary_availability_rules from public;
revoke all privileges on table public.veterinary_availability_rules from anon;
revoke all privileges on table public.veterinary_availability_rules from authenticated;
revoke all privileges on table public.veterinary_availability_exceptions from public;
revoke all privileges on table public.veterinary_availability_exceptions from anon;
revoke all privileges on table public.veterinary_availability_exceptions from authenticated;
revoke all privileges on table public.veterinary_appointments from public;
revoke all privileges on table public.veterinary_appointments from anon;
revoke all privileges on table public.veterinary_appointments from authenticated;
revoke all privileges on table public.veterinary_appointment_status_history from public;
revoke all privileges on table public.veterinary_appointment_status_history from anon;
revoke all privileges on table public.veterinary_appointment_status_history from authenticated;

grant all on table public.veterinary_schedule_settings to service_role;
grant all on table public.veterinary_availability_rules to service_role;
grant all on table public.veterinary_availability_exceptions to service_role;
grant all on table public.veterinary_appointments to service_role;
grant all on table public.veterinary_appointment_status_history to service_role;

-- ---------------------------------------------------------------------------
-- 5. RPCs — configuración de agenda y disponibilidad
-- M06: hooks de dominio preparados; NO invocar push real aquí.
-- ---------------------------------------------------------------------------
create or replace function public.m12_upsert_veterinary_schedule_settings(
  p_clinic_id uuid,
  p_timezone_name text default 'America/Argentina/Buenos_Aires',
  p_booking_horizon_days integer default 30,
  p_minimum_notice_minutes integer default 60,
  p_cancellation_notice_minutes integer default 120,
  p_default_slot_duration_minutes integer default 30,
  p_active boolean default true
) returns public.veterinary_schedule_settings
language plpgsql security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
  v_actor uuid := public._m12_require_authenticated();
  v_tz text := trim(coalesce(p_timezone_name, ''));
  v_row public.veterinary_schedule_settings;
  v_corr text := public.m07_new_correlation_id();
begin
  v_clinic := public._m12_require_schedule_manage(p_clinic_id);
  perform public._m12_require_valid_timezone(v_tz);
  if p_booking_horizon_days is null or p_booking_horizon_days < 1 or p_booking_horizon_days > 365
     or p_minimum_notice_minutes is null or p_minimum_notice_minutes < 0 or p_minimum_notice_minutes > 10080
     or p_cancellation_notice_minutes is null or p_cancellation_notice_minutes < 0 or p_cancellation_notice_minutes > 43200
     or p_default_slot_duration_minutes is null or p_default_slot_duration_minutes < 5 or p_default_slot_duration_minutes > 480 then
    raise exception 'VETERINARY_SCHEDULE_SETTINGS_INVALID';
  end if;

  insert into public.veterinary_schedule_settings (
    clinic_id, timezone_name, booking_horizon_days, minimum_notice_minutes,
    cancellation_notice_minutes, default_slot_duration_minutes, active, updated_by, updated_at
  ) values (
    p_clinic_id, v_tz, p_booking_horizon_days, p_minimum_notice_minutes,
    p_cancellation_notice_minutes, p_default_slot_duration_minutes,
    coalesce(p_active, true), v_actor, timezone('utc', now())
  )
  on conflict (clinic_id) do update set
    timezone_name = excluded.timezone_name,
    booking_horizon_days = excluded.booking_horizon_days,
    minimum_notice_minutes = excluded.minimum_notice_minutes,
    cancellation_notice_minutes = excluded.cancellation_notice_minutes,
    default_slot_duration_minutes = excluded.default_slot_duration_minutes,
    active = excluded.active,
    updated_by = excluded.updated_by,
    updated_at = timezone('utc', now())
  returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_SCHEDULE_SETTINGS_UPDATED', 'UPSERT', 'SUCCESS', v_corr,
    'veterinary_schedule_settings', v_row.clinic_id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_get_veterinary_schedule_settings(p_clinic_id uuid)
returns setof public.veterinary_schedule_settings
language plpgsql stable security definer set search_path = public as $$
begin
  perform public._m12_require_schedule_read(p_clinic_id);
  return query
  select * from public.veterinary_schedule_settings where clinic_id = p_clinic_id;
end;
$$;

create or replace function public.m12_create_veterinary_availability_rule(
  p_clinic_id uuid,
  p_day_of_week integer,
  p_starts_at time,
  p_ends_at time,
  p_slot_duration_minutes integer,
  p_capacity_per_slot integer,
  p_professional_id uuid default null,
  p_service_id uuid default null,
  p_valid_from date default null,
  p_valid_until date default null,
  p_active boolean default true
) returns public.veterinary_availability_rules
language plpgsql security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_availability_rules;
  v_corr text := public.m07_new_correlation_id();
begin
  v_clinic := public._m12_require_schedule_manage(p_clinic_id);

  if p_day_of_week is null or p_day_of_week < 1 or p_day_of_week > 7
     or p_starts_at is null or p_ends_at is null or p_ends_at <= p_starts_at
     or p_slot_duration_minutes is null or p_slot_duration_minutes < 5 or p_slot_duration_minutes > 480
     or p_capacity_per_slot is null or p_capacity_per_slot < 1 or p_capacity_per_slot > 50 then
    raise exception 'VETERINARY_AVAILABILITY_RULE_INVALID';
  end if;
  -- La ventana debe alojar al menos un slot.
  if extract(epoch from (p_ends_at - p_starts_at)) < (p_slot_duration_minutes * 60) then
    raise exception 'VETERINARY_AVAILABILITY_RULE_INVALID';
  end if;
  if p_valid_from is not null and p_valid_until is not null and p_valid_until < p_valid_from then
    raise exception 'VETERINARY_AVAILABILITY_RULE_INVALID';
  end if;

  if p_professional_id is not null and not exists (
    select 1 from public.veterinary_clinic_professionals cp
    where cp.clinic_id = p_clinic_id and cp.professional_id = p_professional_id and cp.active
  ) then
    raise exception 'VETERINARY_PROFESSIONAL_NOT_LINKED';
  end if;
  if p_service_id is not null and not exists (
    select 1 from public.veterinary_services s
    where s.id = p_service_id and s.clinic_id = p_clinic_id and s.active and s.archived_at is null
  ) then
    raise exception 'VETERINARY_SERVICE_NOT_FOUND';
  end if;

  if exists (
    select 1 from public.veterinary_availability_rules r
    where r.clinic_id = p_clinic_id
      and r.active
      and r.day_of_week = p_day_of_week
      and coalesce(r.professional_id::text, '') = coalesce(p_professional_id::text, '')
      and coalesce(r.service_id::text, '') = coalesce(p_service_id::text, '')
      and r.starts_at < p_ends_at and r.ends_at > p_starts_at
      and (p_valid_from is null or r.valid_until is null or r.valid_until >= p_valid_from)
      and (p_valid_until is null or r.valid_from is null or r.valid_from <= p_valid_until)
  ) then
    raise exception 'VETERINARY_AVAILABILITY_RULE_OVERLAP';
  end if;

  insert into public.veterinary_availability_rules (
    clinic_id, professional_id, service_id, day_of_week, starts_at, ends_at,
    slot_duration_minutes, capacity_per_slot, valid_from, valid_until, active, created_by
  ) values (
    p_clinic_id, p_professional_id, p_service_id, p_day_of_week, p_starts_at, p_ends_at,
    p_slot_duration_minutes, p_capacity_per_slot, p_valid_from, p_valid_until,
    coalesce(p_active, true), v_actor
  ) returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_AVAILABILITY_RULE_CHANGED', 'CREATE', 'SUCCESS', v_corr,
    'veterinary_availability_rule', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_update_veterinary_availability_rule(
  p_rule_id uuid,
  p_day_of_week integer,
  p_starts_at time,
  p_ends_at time,
  p_slot_duration_minutes integer,
  p_capacity_per_slot integer,
  p_professional_id uuid default null,
  p_service_id uuid default null,
  p_valid_from date default null,
  p_valid_until date default null
) returns public.veterinary_availability_rules
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_availability_rules;
  v_clinic public.veterinary_clinic_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.veterinary_availability_rules where id = p_rule_id for update;
  if not found then raise exception 'VETERINARY_AVAILABILITY_RULE_INVALID'; end if;
  v_clinic := public._m12_require_schedule_manage(v_row.clinic_id);

  if p_day_of_week is null or p_day_of_week < 1 or p_day_of_week > 7
     or p_starts_at is null or p_ends_at is null or p_ends_at <= p_starts_at
     or p_slot_duration_minutes is null or p_slot_duration_minutes < 5 or p_slot_duration_minutes > 480
     or p_capacity_per_slot is null or p_capacity_per_slot < 1 or p_capacity_per_slot > 50 then
    raise exception 'VETERINARY_AVAILABILITY_RULE_INVALID';
  end if;
  if extract(epoch from (p_ends_at - p_starts_at)) < (p_slot_duration_minutes * 60) then
    raise exception 'VETERINARY_AVAILABILITY_RULE_INVALID';
  end if;
  if p_valid_from is not null and p_valid_until is not null and p_valid_until < p_valid_from then
    raise exception 'VETERINARY_AVAILABILITY_RULE_INVALID';
  end if;

  if p_professional_id is not null and not exists (
    select 1 from public.veterinary_clinic_professionals cp
    where cp.clinic_id = v_row.clinic_id and cp.professional_id = p_professional_id and cp.active
  ) then
    raise exception 'VETERINARY_PROFESSIONAL_NOT_LINKED';
  end if;
  if p_service_id is not null and not exists (
    select 1 from public.veterinary_services s
    where s.id = p_service_id and s.clinic_id = v_row.clinic_id and s.active and s.archived_at is null
  ) then
    raise exception 'VETERINARY_SERVICE_NOT_FOUND';
  end if;

  if exists (
    select 1 from public.veterinary_availability_rules r
    where r.clinic_id = v_row.clinic_id
      and r.id <> v_row.id
      and r.active
      and r.day_of_week = p_day_of_week
      and coalesce(r.professional_id::text, '') = coalesce(p_professional_id::text, '')
      and coalesce(r.service_id::text, '') = coalesce(p_service_id::text, '')
      and r.starts_at < p_ends_at and r.ends_at > p_starts_at
      and (p_valid_from is null or r.valid_until is null or r.valid_until >= p_valid_from)
      and (p_valid_until is null or r.valid_from is null or r.valid_from <= p_valid_until)
  ) then
    raise exception 'VETERINARY_AVAILABILITY_RULE_OVERLAP';
  end if;

  update public.veterinary_availability_rules set
    professional_id = p_professional_id,
    service_id = p_service_id,
    day_of_week = p_day_of_week,
    starts_at = p_starts_at,
    ends_at = p_ends_at,
    slot_duration_minutes = p_slot_duration_minutes,
    capacity_per_slot = p_capacity_per_slot,
    valid_from = p_valid_from,
    valid_until = p_valid_until,
    updated_at = timezone('utc', now())
  where id = p_rule_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_AVAILABILITY_RULE_CHANGED', 'UPDATE', 'SUCCESS', v_corr,
    'veterinary_availability_rule', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_change_veterinary_availability_rule_status(
  p_rule_id uuid,
  p_active boolean
) returns public.veterinary_availability_rules
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_availability_rules;
  v_clinic public.veterinary_clinic_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.veterinary_availability_rules where id = p_rule_id for update;
  if not found then raise exception 'VETERINARY_AVAILABILITY_RULE_INVALID'; end if;
  v_clinic := public._m12_require_schedule_manage(v_row.clinic_id);

  update public.veterinary_availability_rules set
    active = coalesce(p_active, active),
    updated_at = timezone('utc', now())
  where id = p_rule_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_AVAILABILITY_RULE_CHANGED', 'STATUS_CHANGE', 'SUCCESS', v_corr,
    'veterinary_availability_rule', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id,
      'active', v_row.active)
  );
  return v_row;
end;
$$;

create or replace function public.m12_create_veterinary_availability_exception(
  p_clinic_id uuid,
  p_exception_date date,
  p_type text,
  p_rule_id uuid default null,
  p_starts_at time default null,
  p_ends_at time default null,
  p_capacity_per_slot integer default null,
  p_reason text default null,
  p_active boolean default true
) returns public.veterinary_availability_exceptions
language plpgsql security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_availability_exceptions;
  v_type text := upper(trim(coalesce(p_type, '')));
  v_corr text := public.m07_new_correlation_id();
begin
  v_clinic := public._m12_require_schedule_manage(p_clinic_id);

  if p_exception_date is null or v_type not in ('CLOSED','CUSTOM_HOURS','CAPACITY_OVERRIDE') then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if p_rule_id is not null and not exists (
    select 1 from public.veterinary_availability_rules r
    where r.id = p_rule_id and r.clinic_id = p_clinic_id
  ) then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if v_type = 'CLOSED' and (p_starts_at is not null or p_ends_at is not null) then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if v_type = 'CUSTOM_HOURS' and (p_starts_at is null or p_ends_at is null or p_ends_at <= p_starts_at) then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if v_type = 'CAPACITY_OVERRIDE' and (p_capacity_per_slot is null or p_capacity_per_slot < 1 or p_capacity_per_slot > 50) then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if p_reason is not null and char_length(p_reason) > 500 then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;

  insert into public.veterinary_availability_exceptions (
    clinic_id, rule_id, exception_date, type, starts_at, ends_at,
    capacity_per_slot, reason, active, created_by
  ) values (
    p_clinic_id, p_rule_id, p_exception_date, v_type,
    case when v_type = 'CUSTOM_HOURS' then p_starts_at else null end,
    case when v_type = 'CUSTOM_HOURS' then p_ends_at else null end,
    case when v_type = 'CAPACITY_OVERRIDE' then p_capacity_per_slot else null end,
    nullif(trim(coalesce(p_reason, '')), ''), coalesce(p_active, true), v_actor
  ) returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_AVAILABILITY_EXCEPTION_CHANGED', 'CREATE', 'SUCCESS', v_corr,
    'veterinary_availability_exception', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_update_veterinary_availability_exception(
  p_exception_id uuid,
  p_exception_date date,
  p_type text,
  p_rule_id uuid default null,
  p_starts_at time default null,
  p_ends_at time default null,
  p_capacity_per_slot integer default null,
  p_reason text default null
) returns public.veterinary_availability_exceptions
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_availability_exceptions;
  v_clinic public.veterinary_clinic_profiles;
  v_type text := upper(trim(coalesce(p_type, '')));
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.veterinary_availability_exceptions where id = p_exception_id for update;
  if not found then raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID'; end if;
  v_clinic := public._m12_require_schedule_manage(v_row.clinic_id);

  if p_exception_date is null or v_type not in ('CLOSED','CUSTOM_HOURS','CAPACITY_OVERRIDE') then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if p_rule_id is not null and not exists (
    select 1 from public.veterinary_availability_rules r
    where r.id = p_rule_id and r.clinic_id = v_row.clinic_id
  ) then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if v_type = 'CLOSED' and (p_starts_at is not null or p_ends_at is not null) then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if v_type = 'CUSTOM_HOURS' and (p_starts_at is null or p_ends_at is null or p_ends_at <= p_starts_at) then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if v_type = 'CAPACITY_OVERRIDE' and (p_capacity_per_slot is null or p_capacity_per_slot < 1 or p_capacity_per_slot > 50) then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;
  if p_reason is not null and char_length(p_reason) > 500 then
    raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID';
  end if;

  update public.veterinary_availability_exceptions set
    rule_id = p_rule_id,
    exception_date = p_exception_date,
    type = v_type,
    starts_at = case when v_type = 'CUSTOM_HOURS' then p_starts_at else null end,
    ends_at = case when v_type = 'CUSTOM_HOURS' then p_ends_at else null end,
    capacity_per_slot = case when v_type = 'CAPACITY_OVERRIDE' then p_capacity_per_slot else null end,
    reason = nullif(trim(coalesce(p_reason, '')), ''),
    updated_at = timezone('utc', now())
  where id = p_exception_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_AVAILABILITY_EXCEPTION_CHANGED', 'UPDATE', 'SUCCESS', v_corr,
    'veterinary_availability_exception', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_change_veterinary_availability_exception_status(
  p_exception_id uuid,
  p_active boolean
) returns public.veterinary_availability_exceptions
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_availability_exceptions;
  v_clinic public.veterinary_clinic_profiles;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_row from public.veterinary_availability_exceptions where id = p_exception_id for update;
  if not found then raise exception 'VETERINARY_AVAILABILITY_EXCEPTION_INVALID'; end if;
  v_clinic := public._m12_require_schedule_manage(v_row.clinic_id);

  update public.veterinary_availability_exceptions set
    active = coalesce(p_active, active),
    updated_at = timezone('utc', now())
  where id = p_exception_id returning * into v_row;

  perform public.m07_best_effort_audit(
    'VETERINARY_AVAILABILITY_EXCEPTION_CHANGED', 'STATUS_CHANGE', 'SUCCESS', v_corr,
    'veterinary_availability_exception', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id,
      'active', v_row.active)
  );
  return v_row;
end;
$$;

create or replace function public.m12_list_managed_veterinary_availability(p_clinic_id uuid)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_result jsonb;
begin
  perform public._m12_require_schedule_read(p_clinic_id);
  select jsonb_build_object(
    'clinic_id', p_clinic_id,
    'rules', coalesce((
      select jsonb_agg(to_jsonb(r) order by r.day_of_week, r.starts_at)
      from public.veterinary_availability_rules r
      where r.clinic_id = p_clinic_id
    ), '[]'::jsonb),
    'exceptions', coalesce((
      select jsonb_agg(to_jsonb(e) order by e.exception_date, e.type)
      from public.veterinary_availability_exceptions e
      where e.clinic_id = p_clinic_id
    ), '[]'::jsonb)
  ) into v_result;
  return v_result;
end;
$$;

-- Cálculo remoto de slots disponibles (sin tabla pre-generada).
create or replace function public.m12_list_available_veterinary_appointment_slots(
  p_clinic_id uuid,
  p_service_id uuid,
  p_date date,
  p_professional_id uuid default null
) returns table (
  clinic_id uuid,
  professional_id uuid,
  service_id uuid,
  starts_at timestamptz,
  ends_at timestamptz,
  capacity integer,
  reserved integer,
  available integer
)
language plpgsql stable security definer set search_path = public as $$
declare
  v_clinic public.veterinary_clinic_profiles;
  v_settings public.veterinary_schedule_settings;
  v_dow integer;
  v_today_local date;
  v_rule record;
  v_win_start time;
  v_win_end time;
  v_cap integer;
  v_dur integer;
  v_exc_time record;
  v_exc_cap integer;
  v_slot_start_t time;
  v_slot_end_t time;
  v_slot_prof uuid;
  v_starts timestamptz;
  v_ends timestamptz;
  v_reserved integer;
begin
  perform public._m12_require_authenticated();
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found then raise exception 'VETERINARY_CLINIC_NOT_FOUND'; end if;
  if v_clinic.status <> 'ACTIVE'
     and not public.has_org_permission(v_clinic.organization_id, 'veterinary.schedule.read')
     and not public.has_org_permission(v_clinic.organization_id, 'veterinary.schedule.manage')
     and not public.has_org_permission(v_clinic.organization_id, 'veterinary.appointment.read')
     and not public.has_org_permission(v_clinic.organization_id, 'veterinary.appointment.manage') then
    raise exception 'VETERINARY_CLINIC_NOT_FOUND';
  end if;

  if not exists (
    select 1 from public.veterinary_services s
    where s.id = p_service_id and s.clinic_id = p_clinic_id and s.active and s.archived_at is null
  ) then
    raise exception 'VETERINARY_SERVICE_NOT_FOUND';
  end if;
  if p_professional_id is not null and not exists (
    select 1 from public.veterinary_clinic_professionals cp
    where cp.clinic_id = p_clinic_id and cp.professional_id = p_professional_id and cp.active
  ) then
    raise exception 'VETERINARY_PROFESSIONAL_NOT_LINKED';
  end if;

  select * into v_settings from public.veterinary_schedule_settings where clinic_id = p_clinic_id;
  if not found or not v_settings.active or p_date is null then
    return;
  end if;

  v_today_local := (timezone(v_settings.timezone_name, now()))::date;
  if p_date < v_today_local or p_date > (v_today_local + v_settings.booking_horizon_days) then
    return;
  end if;

  v_dow := extract(isodow from p_date)::integer;

  -- Cierre a nivel clínica para la fecha.
  if exists (
    select 1 from public.veterinary_availability_exceptions e
    where e.clinic_id = p_clinic_id and e.active
      and e.exception_date = p_date and e.type = 'CLOSED' and e.rule_id is null
  ) then
    return;
  end if;

  for v_rule in
    select r.* from public.veterinary_availability_rules r
    where r.clinic_id = p_clinic_id
      and r.active
      and r.day_of_week = v_dow
      and (r.service_id is null or r.service_id = p_service_id)
      and (r.professional_id is null or r.professional_id = p_professional_id)
      and (r.valid_from is null or r.valid_from <= p_date)
      and (r.valid_until is null or r.valid_until >= p_date)
    order by r.starts_at
  loop
    if exists (
      select 1 from public.veterinary_availability_exceptions e
      where e.clinic_id = p_clinic_id and e.active
        and e.exception_date = p_date and e.type = 'CLOSED' and e.rule_id = v_rule.id
    ) then
      continue;
    end if;

    v_win_start := v_rule.starts_at;
    v_win_end := v_rule.ends_at;
    v_cap := v_rule.capacity_per_slot;
    v_dur := v_rule.slot_duration_minutes;
    v_slot_prof := coalesce(v_rule.professional_id, p_professional_id);

    -- Horario custom (regla, luego clínica).
    select e.starts_at, e.ends_at into v_exc_time
    from public.veterinary_availability_exceptions e
    where e.clinic_id = p_clinic_id and e.active
      and e.exception_date = p_date and e.type = 'CUSTOM_HOURS' and e.rule_id = v_rule.id
    order by e.updated_at desc limit 1;
    if not found then
      select e.starts_at, e.ends_at into v_exc_time
      from public.veterinary_availability_exceptions e
      where e.clinic_id = p_clinic_id and e.active
        and e.exception_date = p_date and e.type = 'CUSTOM_HOURS' and e.rule_id is null
      order by e.updated_at desc limit 1;
    end if;
    if found and v_exc_time.starts_at is not null and v_exc_time.ends_at is not null then
      v_win_start := v_exc_time.starts_at;
      v_win_end := v_exc_time.ends_at;
    end if;

    -- Override de capacidad (regla, luego clínica).
    select e.capacity_per_slot into v_exc_cap
    from public.veterinary_availability_exceptions e
    where e.clinic_id = p_clinic_id and e.active
      and e.exception_date = p_date and e.type = 'CAPACITY_OVERRIDE' and e.rule_id = v_rule.id
    order by e.updated_at desc limit 1;
    if not found then
      select e.capacity_per_slot into v_exc_cap
      from public.veterinary_availability_exceptions e
      where e.clinic_id = p_clinic_id and e.active
        and e.exception_date = p_date and e.type = 'CAPACITY_OVERRIDE' and e.rule_id is null
      order by e.updated_at desc limit 1;
    end if;
    if found and v_exc_cap is not null then
      v_cap := v_exc_cap;
    end if;

    if v_dur is null or v_dur < 5 then
      continue;
    end if;

    v_slot_start_t := v_win_start;
    loop
      v_slot_end_t := v_slot_start_t + make_interval(mins => v_dur);
      exit when v_slot_end_t <= v_slot_start_t;  -- wrap más allá de medianoche
      exit when v_slot_end_t > v_win_end;

      v_starts := (p_date + v_slot_start_t) at time zone v_settings.timezone_name;
      v_ends := (p_date + v_slot_end_t) at time zone v_settings.timezone_name;

      -- Respetar aviso mínimo.
      if v_starts >= now() + make_interval(mins => v_settings.minimum_notice_minutes) then
        v_reserved := public._m12_count_slot_reservations(
          p_clinic_id, v_slot_prof, p_service_id, v_starts, v_ends
        );
        clinic_id := p_clinic_id;
        professional_id := v_slot_prof;
        service_id := p_service_id;
        starts_at := v_starts;
        ends_at := v_ends;
        capacity := coalesce(v_cap, 0);
        reserved := v_reserved;
        available := greatest(coalesce(v_cap, 0) - v_reserved, 0);
        return next;
      end if;

      v_slot_start_t := v_slot_end_t;
    end loop;
  end loop;

  return;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPCs — solicitudes y ciclo de vida de turnos
-- M06: hooks de dominio preparados; NO invocar push real aquí.
-- ---------------------------------------------------------------------------
create or replace function public.m12_request_veterinary_appointment(
  p_clinic_id uuid,
  p_service_id uuid,
  p_pet_id uuid,
  p_starts_at timestamptz,
  p_ends_at timestamptz,
  p_professional_id uuid default null,
  p_request_note text default null
) returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_clinic public.veterinary_clinic_profiles;
  v_settings public.veterinary_schedule_settings;
  v_row public.veterinary_appointments;
  v_capacity integer;
  v_reserved integer;
  v_corr text := public.m07_new_correlation_id();
begin
  select * into v_clinic from public.veterinary_clinic_profiles where id = p_clinic_id;
  if not found or v_clinic.status <> 'ACTIVE' then
    raise exception 'VETERINARY_CLINIC_NOT_FOUND';
  end if;

  -- Autoridad M08 sobre la mascota (nunca confiar en requester del cliente).
  perform public._m12_assert_pet_bookable(p_pet_id, v_actor);

  if not exists (
    select 1 from public.veterinary_services s
    where s.id = p_service_id and s.clinic_id = p_clinic_id and s.active and s.archived_at is null
  ) then
    raise exception 'VETERINARY_SERVICE_NOT_FOUND';
  end if;
  if p_professional_id is not null and not exists (
    select 1 from public.veterinary_clinic_professionals cp
    where cp.clinic_id = p_clinic_id and cp.professional_id = p_professional_id and cp.active
  ) then
    raise exception 'VETERINARY_PROFESSIONAL_NOT_LINKED';
  end if;

  if p_starts_at is null or p_ends_at is null or p_ends_at <= p_starts_at then
    raise exception 'VETERINARY_SLOT_NOT_AVAILABLE';
  end if;
  if p_starts_at <= timezone('utc', now()) then
    raise exception 'VETERINARY_APPOINTMENT_PAST_SLOT';
  end if;
  if p_request_note is not null and char_length(p_request_note) > 1000 then
    raise exception 'VETERINARY_SLOT_NOT_AVAILABLE';
  end if;

  -- Fila canónica de agenda bloqueada para serializar cupos de la clínica.
  select * into v_settings from public.veterinary_schedule_settings where clinic_id = p_clinic_id for update;
  if not found or not v_settings.active then
    raise exception 'VETERINARY_SLOT_NOT_AVAILABLE';
  end if;
  if p_starts_at < now() + make_interval(mins => v_settings.minimum_notice_minutes) then
    raise exception 'VETERINARY_SLOT_NOT_AVAILABLE';
  end if;
  if (p_starts_at at time zone v_settings.timezone_name)::date
       > ((timezone(v_settings.timezone_name, now()))::date + v_settings.booking_horizon_days) then
    raise exception 'VETERINARY_SLOT_NOT_AVAILABLE';
  end if;

  v_capacity := public._m12_resolve_slot_capacity(
    p_clinic_id, p_professional_id, p_service_id, p_starts_at, p_ends_at
  );
  if v_capacity <= 0 then
    raise exception 'VETERINARY_SLOT_NOT_AVAILABLE';
  end if;

  v_reserved := public._m12_count_slot_reservations(
    p_clinic_id, p_professional_id, p_service_id, p_starts_at, p_ends_at
  );
  if v_reserved + 1 > v_capacity then
    raise exception 'VETERINARY_SLOT_CAPACITY_EXHAUSTED';
  end if;

  insert into public.veterinary_appointments (
    clinic_id, professional_id, service_id, pet_id, requester_user_id,
    starts_at, ends_at, status, request_note
  ) values (
    p_clinic_id, p_professional_id, p_service_id, p_pet_id, v_actor,
    p_starts_at, p_ends_at, 'REQUESTED', nullif(trim(coalesce(p_request_note, '')), '')
  ) returning * into v_row;

  perform public._m12_append_appointment_history(v_row.id, null, 'REQUESTED', v_actor, null);

  -- M06 hook: recordatorio/notificación de dominio preparado, NO se entrega push real aquí.
  null; -- M06 hook: VETERINARY_APPOINTMENT_REQUESTED (client/domain)

  perform public.m07_best_effort_audit(
    'VETERINARY_APPOINTMENT_REQUESTED', 'CREATE', 'SUCCESS', v_corr,
    'veterinary_appointment', v_row.id::text,
    jsonb_build_object('result','SUCCESS','module','M12','organization_id', v_clinic.organization_id)
  );
  return v_row;
end;
$$;

create or replace function public.m12_get_veterinary_appointment(p_appointment_id uuid)
returns public.veterinary_appointments
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
  v_clinic public.veterinary_clinic_profiles;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  if v_row.requester_user_id = v_actor then
    return v_row;
  end if;
  select * into v_clinic from public.veterinary_clinic_profiles where id = v_row.clinic_id;
  if v_clinic.id is null
     or not (
       public.has_org_permission(v_clinic.organization_id, 'veterinary.appointment.read')
       or public.has_org_permission(v_clinic.organization_id, 'veterinary.appointment.manage')
     ) then
    raise exception 'VETERINARY_APPOINTMENT_FORBIDDEN';
  end if;
  return v_row;
end;
$$;

create or replace function public.m12_list_my_veterinary_appointments()
returns setof public.veterinary_appointments
language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m12_require_authenticated();
begin
  return query
  select * from public.veterinary_appointments
  where requester_user_id = v_actor
  order by starts_at desc;
end;
$$;

create or replace function public.m12_list_managed_veterinary_appointments(p_clinic_id uuid)
returns setof public.veterinary_appointments
language plpgsql stable security definer set search_path = public as $$
begin
  perform public._m12_require_appointment_read(p_clinic_id);
  return query
  select * from public.veterinary_appointments
  where clinic_id = p_clinic_id
  order by starts_at desc;
end;
$$;

create or replace function public.m12_confirm_veterinary_appointment(p_appointment_id uuid)
returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  perform public._m12_require_appointment_manage(v_row.clinic_id);
  return public._m12_transition_appointment(p_appointment_id, 'CONFIRMED', null, v_actor);
end;
$$;

create or replace function public.m12_reject_veterinary_appointment(
  p_appointment_id uuid,
  p_reason text default null
) returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  perform public._m12_require_appointment_manage(v_row.clinic_id);
  return public._m12_transition_appointment(p_appointment_id, 'REJECTED', p_reason, v_actor);
end;
$$;

create or replace function public.m12_cancel_my_veterinary_appointment(
  p_appointment_id uuid,
  p_reason text default null
) returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
  v_settings public.veterinary_schedule_settings;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  if v_row.requester_user_id <> v_actor then
    raise exception 'VETERINARY_APPOINTMENT_FORBIDDEN';
  end if;

  -- Ventana de cancelación solo aplica a turnos confirmados.
  if v_row.status = 'CONFIRMED' then
    select * into v_settings from public.veterinary_schedule_settings where clinic_id = v_row.clinic_id;
    if found and v_row.starts_at < now() + make_interval(mins => v_settings.cancellation_notice_minutes) then
      raise exception 'VETERINARY_APPOINTMENT_CANCELLATION_WINDOW';
    end if;
  end if;

  return public._m12_transition_appointment(p_appointment_id, 'CANCELLED_BY_USER', p_reason, v_actor);
end;
$$;

create or replace function public.m12_cancel_managed_veterinary_appointment(
  p_appointment_id uuid,
  p_reason text default null
) returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  perform public._m12_require_appointment_manage(v_row.clinic_id);
  return public._m12_transition_appointment(p_appointment_id, 'CANCELLED_BY_CLINIC', p_reason, v_actor);
end;
$$;

create or replace function public.m12_complete_veterinary_appointment(p_appointment_id uuid)
returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  perform public._m12_require_appointment_manage(v_row.clinic_id);
  return public._m12_transition_appointment(p_appointment_id, 'COMPLETED', null, v_actor);
end;
$$;

create or replace function public.m12_mark_veterinary_appointment_no_show(p_appointment_id uuid)
returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  perform public._m12_require_appointment_manage(v_row.clinic_id);
  return public._m12_transition_appointment(p_appointment_id, 'NO_SHOW', null, v_actor);
end;
$$;

create or replace function public.m12_expire_veterinary_appointment(p_appointment_id uuid)
returns public.veterinary_appointments
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  perform public._m12_require_appointment_manage(v_row.clinic_id);
  return public._m12_transition_appointment(p_appointment_id, 'EXPIRED', null, v_actor);
end;
$$;

create or replace function public.m12_list_veterinary_appointment_history(p_appointment_id uuid)
returns setof public.veterinary_appointment_status_history
language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m12_require_authenticated();
  v_row public.veterinary_appointments;
  v_clinic public.veterinary_clinic_profiles;
begin
  select * into v_row from public.veterinary_appointments where id = p_appointment_id;
  if not found then raise exception 'VETERINARY_APPOINTMENT_NOT_FOUND'; end if;
  if v_row.requester_user_id <> v_actor then
    select * into v_clinic from public.veterinary_clinic_profiles where id = v_row.clinic_id;
    if v_clinic.id is null
       or not (
         public.has_org_permission(v_clinic.organization_id, 'veterinary.appointment.read')
         or public.has_org_permission(v_clinic.organization_id, 'veterinary.appointment.manage')
       ) then
      raise exception 'VETERINARY_APPOINTMENT_FORBIDDEN';
    end if;
  end if;

  return query
  select * from public.veterinary_appointment_status_history
  where appointment_id = p_appointment_id
  order by changed_at asc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. Grants — helpers internos (sin EXECUTE para public/anon/authenticated)
-- ---------------------------------------------------------------------------
revoke all on function public._m12_require_schedule_manage(uuid) from public, anon, authenticated;
revoke all on function public._m12_require_schedule_read(uuid) from public, anon, authenticated;
revoke all on function public._m12_require_appointment_manage(uuid) from public, anon, authenticated;
revoke all on function public._m12_require_appointment_read(uuid) from public, anon, authenticated;
revoke all on function public._m12_appointment_consumes_capacity(text) from public, anon, authenticated;
revoke all on function public._m12_require_valid_timezone(text) from public, anon, authenticated;
revoke all on function public._m12_count_slot_reservations(uuid, uuid, uuid, timestamptz, timestamptz) from public, anon, authenticated;
revoke all on function public._m12_resolve_slot_capacity(uuid, uuid, uuid, timestamptz, timestamptz) from public, anon, authenticated;
revoke all on function public._m12_assert_pet_bookable(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m12_append_appointment_history(uuid, text, text, uuid, text) from public, anon, authenticated;
revoke all on function public._m12_transition_appointment(uuid, text, text, uuid) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 8. Grants — RPCs cliente (revoke public/anon; grant execute authenticated)
-- ---------------------------------------------------------------------------
revoke all on function public.m12_upsert_veterinary_schedule_settings(uuid,text,integer,integer,integer,integer,boolean) from public;
revoke all on function public.m12_upsert_veterinary_schedule_settings(uuid,text,integer,integer,integer,integer,boolean) from anon;
grant execute on function public.m12_upsert_veterinary_schedule_settings(uuid,text,integer,integer,integer,integer,boolean) to authenticated;

revoke all on function public.m12_get_veterinary_schedule_settings(uuid) from public;
revoke all on function public.m12_get_veterinary_schedule_settings(uuid) from anon;
grant execute on function public.m12_get_veterinary_schedule_settings(uuid) to authenticated;

revoke all on function public.m12_create_veterinary_availability_rule(uuid,integer,time,time,integer,integer,uuid,uuid,date,date,boolean) from public;
revoke all on function public.m12_create_veterinary_availability_rule(uuid,integer,time,time,integer,integer,uuid,uuid,date,date,boolean) from anon;
grant execute on function public.m12_create_veterinary_availability_rule(uuid,integer,time,time,integer,integer,uuid,uuid,date,date,boolean) to authenticated;

revoke all on function public.m12_update_veterinary_availability_rule(uuid,integer,time,time,integer,integer,uuid,uuid,date,date) from public;
revoke all on function public.m12_update_veterinary_availability_rule(uuid,integer,time,time,integer,integer,uuid,uuid,date,date) from anon;
grant execute on function public.m12_update_veterinary_availability_rule(uuid,integer,time,time,integer,integer,uuid,uuid,date,date) to authenticated;

revoke all on function public.m12_change_veterinary_availability_rule_status(uuid,boolean) from public;
revoke all on function public.m12_change_veterinary_availability_rule_status(uuid,boolean) from anon;
grant execute on function public.m12_change_veterinary_availability_rule_status(uuid,boolean) to authenticated;

revoke all on function public.m12_create_veterinary_availability_exception(uuid,date,text,uuid,time,time,integer,text,boolean) from public;
revoke all on function public.m12_create_veterinary_availability_exception(uuid,date,text,uuid,time,time,integer,text,boolean) from anon;
grant execute on function public.m12_create_veterinary_availability_exception(uuid,date,text,uuid,time,time,integer,text,boolean) to authenticated;

revoke all on function public.m12_update_veterinary_availability_exception(uuid,date,text,uuid,time,time,integer,text) from public;
revoke all on function public.m12_update_veterinary_availability_exception(uuid,date,text,uuid,time,time,integer,text) from anon;
grant execute on function public.m12_update_veterinary_availability_exception(uuid,date,text,uuid,time,time,integer,text) to authenticated;

revoke all on function public.m12_change_veterinary_availability_exception_status(uuid,boolean) from public;
revoke all on function public.m12_change_veterinary_availability_exception_status(uuid,boolean) from anon;
grant execute on function public.m12_change_veterinary_availability_exception_status(uuid,boolean) to authenticated;

revoke all on function public.m12_list_managed_veterinary_availability(uuid) from public;
revoke all on function public.m12_list_managed_veterinary_availability(uuid) from anon;
grant execute on function public.m12_list_managed_veterinary_availability(uuid) to authenticated;

revoke all on function public.m12_list_available_veterinary_appointment_slots(uuid,uuid,date,uuid) from public;
revoke all on function public.m12_list_available_veterinary_appointment_slots(uuid,uuid,date,uuid) from anon;
grant execute on function public.m12_list_available_veterinary_appointment_slots(uuid,uuid,date,uuid) to authenticated;

revoke all on function public.m12_request_veterinary_appointment(uuid,uuid,uuid,timestamptz,timestamptz,uuid,text) from public;
revoke all on function public.m12_request_veterinary_appointment(uuid,uuid,uuid,timestamptz,timestamptz,uuid,text) from anon;
grant execute on function public.m12_request_veterinary_appointment(uuid,uuid,uuid,timestamptz,timestamptz,uuid,text) to authenticated;

revoke all on function public.m12_get_veterinary_appointment(uuid) from public;
revoke all on function public.m12_get_veterinary_appointment(uuid) from anon;
grant execute on function public.m12_get_veterinary_appointment(uuid) to authenticated;

revoke all on function public.m12_list_my_veterinary_appointments() from public;
revoke all on function public.m12_list_my_veterinary_appointments() from anon;
grant execute on function public.m12_list_my_veterinary_appointments() to authenticated;

revoke all on function public.m12_list_managed_veterinary_appointments(uuid) from public;
revoke all on function public.m12_list_managed_veterinary_appointments(uuid) from anon;
grant execute on function public.m12_list_managed_veterinary_appointments(uuid) to authenticated;

revoke all on function public.m12_confirm_veterinary_appointment(uuid) from public;
revoke all on function public.m12_confirm_veterinary_appointment(uuid) from anon;
grant execute on function public.m12_confirm_veterinary_appointment(uuid) to authenticated;

revoke all on function public.m12_reject_veterinary_appointment(uuid,text) from public;
revoke all on function public.m12_reject_veterinary_appointment(uuid,text) from anon;
grant execute on function public.m12_reject_veterinary_appointment(uuid,text) to authenticated;

revoke all on function public.m12_cancel_my_veterinary_appointment(uuid,text) from public;
revoke all on function public.m12_cancel_my_veterinary_appointment(uuid,text) from anon;
grant execute on function public.m12_cancel_my_veterinary_appointment(uuid,text) to authenticated;

revoke all on function public.m12_cancel_managed_veterinary_appointment(uuid,text) from public;
revoke all on function public.m12_cancel_managed_veterinary_appointment(uuid,text) from anon;
grant execute on function public.m12_cancel_managed_veterinary_appointment(uuid,text) to authenticated;

revoke all on function public.m12_complete_veterinary_appointment(uuid) from public;
revoke all on function public.m12_complete_veterinary_appointment(uuid) from anon;
grant execute on function public.m12_complete_veterinary_appointment(uuid) to authenticated;

revoke all on function public.m12_mark_veterinary_appointment_no_show(uuid) from public;
revoke all on function public.m12_mark_veterinary_appointment_no_show(uuid) from anon;
grant execute on function public.m12_mark_veterinary_appointment_no_show(uuid) to authenticated;

revoke all on function public.m12_expire_veterinary_appointment(uuid) from public;
revoke all on function public.m12_expire_veterinary_appointment(uuid) from anon;
grant execute on function public.m12_expire_veterinary_appointment(uuid) to authenticated;

revoke all on function public.m12_list_veterinary_appointment_history(uuid) from public;
revoke all on function public.m12_list_veterinary_appointment_history(uuid) from anon;
grant execute on function public.m12_list_veterinary_appointment_history(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 9. Comentarios técnicos
-- ---------------------------------------------------------------------------
comment on table public.veterinary_schedule_settings is
  'M12 Bloque 3: configuración de agenda por clínica; zona horaria IANA; sin pagos.';
comment on table public.veterinary_availability_rules is
  'M12 Bloque 3: reglas recurrentes de disponibilidad por clínica/profesional/servicio; slots calculados en RPC.';
comment on table public.veterinary_availability_exceptions is
  'M12 Bloque 3: excepciones por fecha (cierre, horario custom, override de capacidad).';
comment on table public.veterinary_appointments is
  'M12 Bloque 3: solicitudes/turnos veterinarios; notas privadas no clínicas; sin diagnóstico, receta ni pagos.';
comment on table public.veterinary_appointment_status_history is
  'M12 Bloque 3: historial auditable de transiciones de turno; una fila por cambio de estado.';
comment on function public.m12_list_available_veterinary_appointment_slots(uuid,uuid,date,uuid) is
  'Cálculo remoto de slots en la zona de la clínica; combina reglas, excepciones y cupos vigentes; sin tabla pre-generada.';
comment on function public.m12_request_veterinary_appointment(uuid,uuid,uuid,timestamptz,timestamptz,uuid,text) is
  'Solicita turno con autoridad M08 sobre la mascota; serializa cupo con FOR UPDATE sobre veterinary_schedule_settings.';
comment on function public._m12_transition_appointment(uuid,text,text,uuid) is
  'Núcleo de transición de estados de turno con guardas temporales; genera historial y auditoría M07; hooks M06 preparados sin push real.';

commit;
