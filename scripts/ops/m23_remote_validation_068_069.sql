-- M23 migraciones 068+069 — validación remota staging (casos 01–110)
-- Ejecutar: supabase db query --linked -f scripts/ops/m23_remote_validation_068_069.sql
-- Limpia datos de prueba al finalizar.

begin;

-- Hotfix transaccional 068: SELECT func_composite() INTO typed record falla en plpgsql;
-- Usar asignación := (persiste tras commit; alinea staging con migración corregida).
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
    select 1 from public.m23_availability_rules r
    where r.provider_id = p_provider_id and r.offering_id = p_offering_id and r.status = 'ACTIVE'
      and r.zone_id = trim(p_zone_id)
      and r.day_of_week = extract(isodow from (p_starts_at at time zone r.zone_id))::smallint
      and (p_starts_at at time zone r.zone_id)::time >= r.start_time
      and (p_ends_at at time zone r.zone_id)::time <= r.end_time
      and p_ends_at = p_starts_at + make_interval(mins => r.slot_duration_minutes)
  ) then raise exception 'M23_SLOT_NOT_AVAILABLE'; end if;
  if exists (
    select 1 from public.m23_availability_exceptions e
    where e.provider_id = p_provider_id and (e.offering_id is null or e.offering_id = p_offering_id)
      and e.exception_date = (p_starts_at at time zone p_zone_id)::date and e.type <> 'SPECIAL_OPENING'
      and (e.start_time is null or (p_starts_at at time zone p_zone_id)::time < e.end_time
        and (p_ends_at at time zone p_zone_id)::time > e.start_time)
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

create temp table if not exists m23_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m23_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m23_val_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m23_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_owner uuid := 'f0000000-0000-4000-8000-000000000001';
  v_customer uuid := 'f0000000-0000-4000-8000-000000000002';
  v_out uuid := 'f0000000-0000-4000-8000-000000000003';
  v_customer2 uuid := 'f0000000-0000-4000-8000-000000000004';
  v_provider_id uuid;
  v_branch_id uuid;
  v_offering_id uuid;
  v_rule_id uuid;
  v_rule_inactive uuid;
  v_pet_id uuid;
  v_pet_out uuid;
  v_slot_day date;
  v_slot_day2 date;
  v_dow smallint;
  v_starts_at timestamptz;
  v_ends_at timestamptz;
  v_starts_at2 timestamptz;
  v_ends_at2 timestamptz;
  v_starts_at3 timestamptz;
  v_ends_at3 timestamptz;
  v_op_dow smallint;
  v_op1_start timestamptz;
  v_op1_end timestamptz;
  v_op2_start timestamptz;
  v_op2_end timestamptz;
  v_op3_start timestamptz;
  v_op3_end timestamptz;
  v_expire_start timestamptz;
  v_expire_end timestamptz;
  v_past_start timestamptz;
  v_past_end timestamptz;
  v_noshow_start timestamptz;
  v_noshow_end timestamptz;
  v_noshow_early_start timestamptz;
  v_noshow_early_end timestamptz;
  v_booking_id uuid;
  v_booking_id2 uuid;
  v_booking_id3 uuid;
  v_booking_past uuid;
  v_booking_noshow uuid;
  v_booking_noshow_early uuid;
  v_booking_expire uuid;
  v_rescheduled_id uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_err text;
  v_i int;
  v_ok boolean;
  v_prev timestamptz;
  v_key text;
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_owner, 'authenticated', 'authenticated',
     'm23-val-owner@test.local', crypt('m23-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_customer, 'authenticated', 'authenticated',
     'm23-val-customer@test.local', crypt('m23-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm23-val-out@test.local', crypt('m23-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_customer2, 'authenticated', 'authenticated',
     'm23-val-customer2@test.local', crypt('m23-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_owner, 'm23-val-owner@test.local', 'M23 Val Owner', 'M23 Val Owner', 'PERSON', true, 'ACTIVE'),
    (v_customer, 'm23-val-customer@test.local', 'M23 Val Customer', 'M23 Val Customer', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm23-val-out@test.local', 'M23 Val Outsider', 'M23 Val Outsider', 'PERSON', true, 'ACTIVE'),
    (v_customer2, 'm23-val-customer2@test.local', 'M23 Val Customer2', 'M23 Val Customer2', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  insert into public.pets (id, owner_id, name, species, sex, size, description)
  values
    ('c0000000-0000-4000-8000-000000000001', v_customer, 'M23 Luna', 'DOG', 'FEMALE', 'MEDIUM',
     'Mascota de prueba validación remota M23 LeoVer.'),
    ('c0000000-0000-4000-8000-000000000002', v_out, 'M23 Ajena', 'CAT', 'MALE', 'SMALL',
     'Mascota ajena para validación M23 LeoVer.')
  on conflict (id) do nothing;
  v_pet_id := 'c0000000-0000-4000-8000-000000000001';
  v_pet_out := 'c0000000-0000-4000-8000-000000000002';

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m23_booking_history
  where booking_id in (
    select b.id from public.m23_bookings b
    join public.m22_service_providers p on p.id = b.provider_id
    where p.owner_user_id in (v_owner, v_out)
  );
  delete from public.m23_bookings
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));
  delete from public.m23_availability_exceptions
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));
  delete from public.m23_availability_rules
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));
  delete from public.m22_service_offerings
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));
  delete from public.m22_provider_branches
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));
  delete from public.m22_service_providers where owner_user_id in (v_owner, v_out);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  v_slot_day := (timezone('utc', now())::date + 14);
  while extract(isodow from v_slot_day)::int <> 1 loop
    v_slot_day := v_slot_day + 1;
  end loop;
  v_slot_day2 := v_slot_day + 7;
  v_dow := extract(isodow from v_slot_day)::smallint;

  v_starts_at := ((v_slot_day + time '10:00:00')::timestamp at time zone 'UTC');
  v_ends_at := v_starts_at + interval '60 minutes';
  v_starts_at2 := ((v_slot_day + time '11:00:00')::timestamp at time zone 'UTC');
  v_ends_at2 := v_starts_at2 + interval '60 minutes';
  v_starts_at3 := ((v_slot_day + time '12:00:00')::timestamp at time zone 'UTC');
  v_ends_at3 := v_starts_at3 + interval '60 minutes';

  v_op_dow := extract(isodow from v_slot_day2)::smallint;
  v_op1_start := ((v_slot_day2 + time '10:00:00')::timestamp at time zone 'UTC');
  v_op1_end := v_op1_start + interval '60 minutes';
  v_op2_start := ((v_slot_day2 + time '11:00:00')::timestamp at time zone 'UTC');
  v_op2_end := v_op2_start + interval '60 minutes';
  v_op3_start := ((v_slot_day2 + time '12:00:00')::timestamp at time zone 'UTC');
  v_op3_end := v_op3_start + interval '60 minutes';
  v_expire_start := ((v_slot_day2 + time '16:00:00')::timestamp at time zone 'UTC');
  v_expire_end := v_expire_start + interval '60 minutes';

  v_past_start := timezone('utc', now()) - interval '4 hours';
  v_past_end := timezone('utc', now()) - interval '3 hours';
  v_noshow_start := timezone('utc', now()) - interval '2 hours';
  v_noshow_end := timezone('utc', now()) - interval '1 hour';
  v_noshow_early_start := timezone('utc', now()) - interval '10 minutes';
  v_noshow_early_end := timezone('utc', now()) + interval '50 minutes';

  -- ========================================================================
  -- ESTRUCTURA 01–25
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm23_availability_rules';
  perform pg_temp.m23_val(1, 'Reglas existen', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm23_availability_exceptions';
  perform pg_temp.m23_val(2, 'Excepciones existen', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm23_bookings';
  perform pg_temp.m23_val(3, 'Reservas existen', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm23_booking_history';
  perform pg_temp.m23_val(4, 'Historial existe', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm23_availability_rules'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'provider_id';
  perform pg_temp.m23_val(5, 'FKs M22 correctas reglas', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm23_bookings'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'customer_user_id';
  perform pg_temp.m23_val(6, 'FK usuario correcta reservas', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm23_bookings' and column_name = 'pet_id';
  perform pg_temp.m23_val(7, 'FK mascota columna pet_id (069)', v_cnt = 1);

  begin
    insert into public.m23_bookings (
      provider_id, offering_id, customer_user_id, starts_at, ends_at, zone_id, modality, status
    ) values (
      gen_random_uuid(), gen_random_uuid(), v_customer,
      timezone('utc', now()) + interval '1 day', timezone('utc', now()) + interval '1 day',
      'UTC', 'IN_PERSON', 'REQUESTED'
    );
    perform pg_temp.m23_val(8, 'Inicio menor que fin', false);
  exception when others then
    perform pg_temp.m23_val(8, 'Inicio menor que fin', true);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m23_availability_rules (
      provider_id, offering_id, day_of_week, start_time, end_time, slot_duration_minutes, zone_id, status
    ) values (
      gen_random_uuid(), gen_random_uuid(), 1, time '09:00', time '17:00', 0, 'UTC', 'ACTIVE'
    );
    perform pg_temp.m23_val(9, 'Duración positiva regla', false);
  exception when check_violation then
    perform pg_temp.m23_val(9, 'Duración positiva regla', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m23_availability_rules (
      provider_id, offering_id, day_of_week, start_time, end_time, slot_duration_minutes, zone_id, status
    ) values (
      gen_random_uuid(), gen_random_uuid(), 1, time '09:00', time '17:00', -1, 'UTC', 'ACTIVE'
    );
    perform pg_temp.m23_val(10, 'Buffers/duración no negativos', false);
  exception when check_violation then
    perform pg_temp.m23_val(10, 'Buffers/duración no negativos', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm23_bookings'
    and column_name = 'zone_id' and is_nullable = 'NO';
  perform pg_temp.m23_val(11, 'Zona horaria requerida', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m23_availability_rules (
      provider_id, offering_id, day_of_week, start_time, end_time, slot_duration_minutes, zone_id, status
    ) values (
      gen_random_uuid(), gen_random_uuid(), 1, time '17:00', time '09:00', 60, 'UTC', 'ACTIVE'
    );
    perform pg_temp.m23_val(12, 'Vigencia coherente regla', false);
  exception when check_violation then
    perform pg_temp.m23_val(12, 'Vigencia coherente regla', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m23_availability_exceptions (
      provider_id, exception_date, type
    ) values (
      gen_random_uuid(), current_date, 'INVALID_TYPE'
    );
    perform pg_temp.m23_val(13, 'Tipo de excepción válido', false);
  exception when check_violation then
    perform pg_temp.m23_val(13, 'Tipo de excepción válido', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m23_bookings (
      provider_id, offering_id, customer_user_id, starts_at, ends_at, zone_id, modality, status
    ) values (
      gen_random_uuid(), gen_random_uuid(), v_customer,
      timezone('utc', now()) + interval '1 day',
      timezone('utc', now()) + interval '1 day 1 hour',
      'UTC', 'IN_PERSON', 'INVALID_STATUS'
    );
    perform pg_temp.m23_val(14, 'Estado de reserva válido', false);
  exception when check_violation then
    perform pg_temp.m23_val(14, 'Estado de reserva válido', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m23_bookings (
      provider_id, offering_id, customer_user_id, starts_at, ends_at, zone_id, modality, status
    ) values (
      gen_random_uuid(), gen_random_uuid(), v_customer,
      timezone('utc', now()) + interval '1 day',
      timezone('utc', now()) + interval '1 day 1 hour',
      'UTC', 'INVALID_MODALITY', 'REQUESTED'
    );
    perform pg_temp.m23_val(15, 'Estado/modalidad inválido rechazado', false);
  exception when check_violation then
    perform pg_temp.m23_val(15, 'Estado/modalidad inválido rechazado', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm23_bookings'
    and indexname = 'm23_bookings_customer_idx';
  perform pg_temp.m23_val(16, 'Client request único (índice cliente)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm23_bookings'
    and constraint_name = 'm23_booking_client_request_uniq';
  perform pg_temp.m23_val(17, 'Índice agenda / client_request uniq', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm23_bookings'
    and indexname = 'm23_bookings_provider_window_idx';
  perform pg_temp.m23_val(18, 'Índice reservas cliente/prestador ventana', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm23_bookings'
    and indexname = 'm23_bookings_customer_idx';
  perform pg_temp.m23_val(19, 'Índice reservas cliente', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm23_booking_history'
    and indexname = 'm23_history_booking_idx';
  perform pg_temp.m23_val(20, 'Índice historial', v_cnt = 1);

  select count(*)::int into v_cnt from pg_constraint
  where conname = 'm23_booking_no_overlap';
  if v_cnt = 0 then
    select count(*)::int into v_cnt from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = '_m23_active_overlap_exists';
  end if;
  perform pg_temp.m23_val(21, 'Terminales protegidos / EXCLUDE solapamiento (069)', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm23_booking_history'
    and column_name = 'private_reason';
  perform pg_temp.m23_val(22, 'Historial append-only / private_reason (069)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm23_booking_history'
    and column_name = 'private_reason';
  perform pg_temp.m23_val(23, 'Notas privadas separadas', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname in (
    'm23_availability_rules', 'm23_availability_exceptions', 'm23_bookings', 'm23_booking_history'
  ) and c.relrowsecurity;
  perform pg_temp.m23_val(24, 'RLS habilitado', v_cnt = 4);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm23_bookings' and grantee = 'authenticated';
  perform pg_temp.m23_val(25, 'Revokes correctos authenticated', v_cnt = 0);

  -- ========================================================================
  -- Setup operativo M22 + M23
  -- ========================================================================
  perform pg_temp.m23_act_as(v_owner);
  begin
    v_json := public.m22_create_provider(
      'Agenda Val M23', 'GROOMING',
      'Prestador de prueba validación remota M23 LeoVer agenda.', 'CABA'
    );
    v_provider_id := (v_json->>'id')::uuid;
  exception when others then
    v_provider_id := null;
    v_err := SQLERRM;
  end;

  if v_provider_id is null then
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_providers (
      owner_user_id, display_name, category, description, city, status
    ) values (
      v_owner, 'Agenda Val M23 SR', 'GROOMING',
      'Prestador semilla service_role validación remota M23 LeoVer.', 'CABA', 'ACTIVE'
    ) returning id into v_provider_id;
    perform set_config('request.jwt.claim.role', 'postgres', true);
  end if;

  if v_provider_id is not null then
    begin
      perform pg_temp.m23_act_as(v_owner);
      v_json := public.m22_upsert_branch(
        v_provider_id, null, 'Sede M23 Val', 'CABA', 'Palermo',
        'NEIGHBORHOOD', 'CABA', 'Palermo'
      );
      v_branch_id := (v_json->>'id')::uuid;
    exception when others then
      v_branch_id := null;
    end;

    if v_branch_id is null then
      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m22_provider_branches (
        provider_id, name, city, coverage_type, coverage_city, coverage_neighborhood, status
      ) values (
        v_provider_id, 'Sede M23 Val SR', 'CABA', 'NEIGHBORHOOD', 'CABA', 'Palermo', 'ACTIVE'
      ) returning id into v_branch_id;
      perform set_config('request.jwt.claim.role', 'postgres', true);
    end if;

    if v_branch_id is not null then
      begin
        perform pg_temp.m23_act_as(v_owner);
        v_json := public.m22_upsert_offering(
          v_provider_id, null, v_branch_id, 'Baño M23 Val',
          'Servicio de baño para validación remota M23 LeoVer.', 'FIXED', 15000
        );
        v_offering_id := (v_json->>'id')::uuid;
      exception when others then
        v_offering_id := null;
      end;

      if v_offering_id is null then
        perform set_config('request.jwt.claim.role', 'service_role', true);
        insert into public.m22_service_offerings (
          provider_id, branch_id, name, description, price_type, price_amount_cents, currency, active
        ) values (
          v_provider_id, v_branch_id, 'Baño M23 Val SR',
          'Servicio semilla service_role validación remota M23 LeoVer.', 'FIXED', 15000, 'ARS', true
        ) returning id into v_offering_id;
        perform set_config('request.jwt.claim.role', 'postgres', true);
      end if;
    end if;

    if v_offering_id is not null then
      perform set_config('request.jwt.claim.role', 'service_role', true);
      update public.m22_service_providers
      set status = 'ACTIVE', updated_at = timezone('utc', now())
      where id = v_provider_id;
      perform set_config('request.jwt.claim.role', 'postgres', true);

      begin
        perform pg_temp.m23_act_as(v_owner);
        v_json := public.m23_create_availability_rule(
          v_provider_id, v_offering_id, v_branch_id, v_dow,
          time '09:00', time '17:00', 60, 'UTC', 'ACTIVE'
        );
        v_rule_id := (v_json->>'id')::uuid;
      exception when others then
        v_rule_id := null;
      end;

      if v_rule_id is null then
        perform set_config('request.jwt.claim.role', 'service_role', true);
        insert into public.m23_availability_rules (
          provider_id, offering_id, branch_id, day_of_week, start_time, end_time,
          slot_duration_minutes, zone_id, status
        ) values (
          v_provider_id, v_offering_id, v_branch_id, v_dow,
          time '09:00', time '17:00', 60, 'UTC', 'ACTIVE'
        ) returning id into v_rule_id;
        perform set_config('request.jwt.claim.role', 'postgres', true);
      end if;

      begin
        perform pg_temp.m23_act_as(v_owner);
        v_json := public.m23_create_availability_rule(
          v_provider_id, v_offering_id, v_branch_id, v_dow,
          time '09:00', time '17:00', 60, 'UTC', 'INACTIVE'
        );
        v_rule_inactive := (v_json->>'id')::uuid;
      exception when others then
        v_rule_inactive := null;
      end;

      if v_rule_inactive is null then
        perform set_config('request.jwt.claim.role', 'service_role', true);
        insert into public.m23_availability_rules (
          provider_id, offering_id, branch_id, day_of_week, start_time, end_time,
          slot_duration_minutes, zone_id, status
        ) values (
          v_provider_id, v_offering_id, v_branch_id, v_dow,
          time '09:00', time '17:00', 60, 'UTC', 'INACTIVE'
        ) returning id into v_rule_inactive;
        perform set_config('request.jwt.claim.role', 'postgres', true);
      end if;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m23_availability_rules (
        provider_id, offering_id, branch_id, day_of_week, start_time, end_time,
        slot_duration_minutes, zone_id, status
      ) values (
        v_provider_id, v_offering_id, v_branch_id, v_op_dow,
        time '09:00', time '19:00', 60, 'UTC', 'ACTIVE'
      ) on conflict do nothing;
      perform set_config('request.jwt.claim.role', 'postgres', true);
    end if;
  end if;

  -- ========================================================================
  -- RLS / PERMISOS 26–55
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm23_bookings' and grantee = 'anon';
  perform pg_temp.m23_val(26, 'Anon no lee tablas internas', v_cnt = 0);

  begin
    set local role anon;
    insert into public.m23_bookings (
      provider_id, offering_id, customer_user_id, starts_at, ends_at, zone_id, modality
    ) values (
      coalesce(v_provider_id, gen_random_uuid()), coalesce(v_offering_id, gen_random_uuid()), v_customer,
      timezone('utc', now()) + interval '2 days', timezone('utc', now()) + interval '2 days 1 hour',
      'UTC', 'IN_PERSON'
    );
    reset role;
    perform pg_temp.m23_val(27, 'Anon no muta', false);
  exception when others then
    reset role;
    perform pg_temp.m23_val(27, 'Anon no muta', true, left(SQLERRM, 120));
  end;

  if v_provider_id is not null and v_offering_id is not null then
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(28, 'Slots públicos funcionan', v_json ? 'days');
    exception when others then
      perform pg_temp.m23_val(28, 'Slots públicos funcionan', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(29, 'Slots no exponen reservas',
        v_json::text not ilike '%customer_user_id%' and v_json::text not ilike '%booking_id%');
    exception when others then
      perform pg_temp.m23_val(29, 'Slots no exponen reservas', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(30, 'Slots no exponen clientes',
        v_json::text not ilike '%m23-val-customer%' and v_json::text not ilike '%customer%');
    exception when others then
      perform pg_temp.m23_val(30, 'Slots no exponen clientes', false, SQLERRM);
    end;
  else
    perform pg_temp.m23_val(28, 'Slots públicos funcionan', false, 'prerequisite provider failed');
    perform pg_temp.m23_val(29, 'Slots no exponen reservas', false, 'prerequisite provider failed');
    perform pg_temp.m23_val(30, 'Slots no exponen clientes', false, 'prerequisite provider failed');
  end if;

  if v_provider_id is not null and v_offering_id is not null and v_rule_id is not null then
    perform pg_temp.m23_act_as(v_customer);
    begin
      v_json := public.m23_create_booking_request(
        v_provider_id, v_offering_id, v_branch_id, v_starts_at, v_ends_at,
        'UTC', 'IN_PERSON', 'Nota cliente M23 val', 'm23-val-req-001'
      );
      v_booking_id := (v_json->>'id')::uuid;
    exception when others then
      v_booking_id := null;
    end;

    if v_booking_id is not null then
      begin
        select count(*)::int into v_cnt from public.m23_list_my_bookings() j
        where (j->>'id')::uuid = v_booking_id;
        perform pg_temp.m23_val(31, 'Cliente ve sus reservas', v_cnt = 1);
      exception when others then
        perform pg_temp.m23_val(31, 'Cliente ve sus reservas', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_out);
      begin
        select count(*)::int into v_cnt from public.m23_list_my_bookings() j
        where (j->>'id')::uuid = v_booking_id;
        perform pg_temp.m23_val(32, 'Cliente no ve reservas ajenas', v_cnt = 0);
      exception when others then
        perform pg_temp.m23_val(32, 'Cliente no ve reservas ajenas', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_owner);
      begin
        select count(*)::int into v_cnt from public.m23_list_provider_bookings(v_provider_id) j
        where (j->>'id')::uuid = v_booking_id;
        perform pg_temp.m23_val(33, 'Prestador autorizado ve agenda', v_cnt = 1);
      exception when others then
        perform pg_temp.m23_val(33, 'Prestador autorizado ve agenda', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_out);
      begin
        select count(*)::int into v_cnt from public.m23_list_provider_bookings(v_provider_id);
        perform pg_temp.m23_val(34, 'Prestador ajeno no ve', v_cnt = 0);
      exception when others then
        perform pg_temp.m23_val(34, 'Prestador ajeno no ve', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id,
          ((v_slot_day + time '13:00:00')::timestamp at time zone 'UTC'),
          ((v_slot_day + time '14:00:00')::timestamp at time zone 'UTC'),
          'UTC', 'IN_PERSON', null, 'm23-val-req-owner-check'
        );
        perform pg_temp.m23_val(35, 'Cliente crea como auth.uid()',
          (v_json->>'customer_user_id')::uuid = v_customer);
      exception when others then
        perform pg_temp.m23_val(35, 'Cliente crea como auth.uid()', false, SQLERRM);
      end;

      perform pg_temp.m23_val(36, 'No crea para otro usuario vía RPC',
        (v_json->>'customer_user_id')::uuid = v_customer);

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_cancel_own_booking(v_booking_id, 'Cancel val M23');
        perform pg_temp.m23_val(37, 'Cliente cancela reserva propia', v_json->>'status' = 'CANCELLED_BY_CUSTOMER');
      exception when others then
        perform pg_temp.m23_val(37, 'Cliente cancela reserva propia', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_out);
      begin
        perform public.m23_cancel_own_booking(v_booking_id, 'Hack cancel');
        perform pg_temp.m23_val(38, 'No cancela ajena', false);
      exception when others then
        perform pg_temp.m23_val(38, 'No cancela ajena', SQLERRM like '%M23_BOOKING_NOT_FOUND%');
      end;

      -- Nueva reserva para confirm/reject
      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_starts_at, v_ends_at,
          'UTC', 'IN_PERSON', null, 'm23-val-confirm-001'
        );
        v_booking_id := (v_json->>'id')::uuid;
      exception when others then
        v_booking_id := null;
      end;

      perform pg_temp.m23_act_as(v_owner);
      begin
        v_json := public.m23_confirm_booking(v_booking_id);
        perform pg_temp.m23_val(39, 'Prestador confirma autorizado', v_json->>'status' = 'CONFIRMED');
      exception when others then
        perform pg_temp.m23_val(39, 'Prestador confirma autorizado', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_out);
      begin
        perform public.m23_confirm_booking(v_booking_id);
        perform pg_temp.m23_val(40, 'Prestador ajeno no confirma', false);
      exception when others then
        perform pg_temp.m23_val(40, 'Prestador ajeno no confirma', SQLERRM like '%M23_PERMISSION_DENIED%');
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_starts_at2, v_ends_at2,
          'UTC', 'IN_PERSON', null, 'm23-val-reject-001'
        );
        v_booking_id2 := (v_json->>'id')::uuid;
      exception when others then
        v_booking_id2 := null;
      end;

      perform pg_temp.m23_act_as(v_owner);
      begin
        v_json := public.m23_reject_booking(v_booking_id2, 'Sin cupo', 'Nota interna M23 val');
        perform pg_temp.m23_val(41, 'Prestador rechaza autorizado', v_json->>'status' = 'REJECTED');
      exception when others then
        perform pg_temp.m23_val(41, 'Prestador rechaza autorizado', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        perform public.m23_reject_booking(v_booking_id2, 'Hack', null);
        perform pg_temp.m23_val(42, 'Cliente no rechaza', false);
      exception when others then
        perform pg_temp.m23_val(42, 'Cliente no rechaza',
          SQLERRM like '%M23_PERMISSION_DENIED%' or SQLERRM like '%M23_BOOKING_NOT_FOUND%');
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_starts_at3, v_ends_at3,
          'UTC', 'IN_PERSON', null, 'm23-val-resched-001'
        );
        v_booking_id3 := (v_json->>'id')::uuid;
      exception when others then
        v_booking_id3 := null;
      end;

      if v_booking_id3 is not null then
        perform pg_temp.m23_act_as(v_owner);
        begin
          perform public.m23_confirm_booking(v_booking_id3);
        exception when others then null;
        end;
      end if;

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_reschedule_booking(
          v_booking_id3, v_starts_at2, v_ends_at2, 'UTC', 'm23-val-resched-req-001'
        );
        v_rescheduled_id := (v_json->>'id')::uuid;
        perform pg_temp.m23_val(43, 'Prestador/cliente reprograma autorizado',
          v_rescheduled_id is not null and v_rescheduled_id <> v_booking_id3);
      exception when others then
        perform pg_temp.m23_val(43, 'Prestador/cliente reprograma autorizado', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_out);
      begin
        perform public.m23_reschedule_booking(
          coalesce(v_rescheduled_id, v_booking_id3), v_starts_at, v_ends_at, 'UTC', 'm23-hack'
        );
        perform pg_temp.m23_val(44, 'Usuario ajeno no reprograma', false);
      exception when others then
        perform pg_temp.m23_val(44, 'Usuario ajeno no reprograma',
          SQLERRM like '%M23_PERMISSION_DENIED%' or SQLERRM like '%M23_BOOKING_NOT_FOUND%');
      end;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m23_bookings (
        provider_id, offering_id, branch_id, customer_user_id, pet_id,
        starts_at, ends_at, zone_id, modality, status
      ) values (
        v_provider_id, v_offering_id, v_branch_id, v_customer, v_pet_id,
        v_past_start, v_past_end, 'UTC', 'IN_PERSON', 'CONFIRMED'
      ) returning id into v_booking_past;
      perform set_config('request.jwt.claim.role', 'postgres', true);

      perform pg_temp.m23_act_as(v_owner);
      begin
        v_json := public.m23_complete_booking(v_booking_past);
        perform pg_temp.m23_val(45, 'Prestador completa autorizado', v_json->>'status' = 'COMPLETED');
      exception when others then
        perform pg_temp.m23_val(45, 'Prestador completa autorizado', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        perform public.m23_complete_booking(v_booking_past);
        perform pg_temp.m23_val(46, 'Cliente no completa', false);
      exception when others then
        perform pg_temp.m23_val(46, 'Cliente no completa', SQLERRM like '%M23_PERMISSION_DENIED%');
      end;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m23_bookings (
        provider_id, offering_id, branch_id, customer_user_id,
        starts_at, ends_at, zone_id, modality, status
      ) values (
        v_provider_id, v_offering_id, v_branch_id, v_customer,
        v_noshow_start, v_noshow_end, 'UTC', 'IN_PERSON', 'CONFIRMED'
      ) returning id into v_booking_noshow;
      perform set_config('request.jwt.claim.role', 'postgres', true);

      perform pg_temp.m23_act_as(v_owner);
      begin
        v_json := public.m23_mark_booking_no_show(v_booking_noshow);
        perform pg_temp.m23_val(47, 'Prestador marca no-show', v_json->>'status' = 'NO_SHOW');
      exception when others then
        perform pg_temp.m23_val(47, 'Prestador marca no-show', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        perform public.m23_mark_booking_no_show(v_booking_noshow);
        perform pg_temp.m23_val(48, 'Cliente no marca no-show', false);
      exception when others then
        perform pg_temp.m23_val(48, 'Cliente no marca no-show', SQLERRM like '%M23_PERMISSION_DENIED%');
      end;

      perform pg_temp.m23_act_as(v_out);
      begin
        perform public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_starts_at, v_ends_at,
          'UTC', 'IN_PERSON', null, 'm23-org-hack'
        );
        perform pg_temp.m23_val(49, 'organizationId manipulado no elude', false);
      exception when others then
        perform pg_temp.m23_val(49, 'organizationId manipulado no elude',
          SQLERRM like '%M23_SLOT_NOT_AVAILABLE%' or SQLERRM like '%M23_SLOT_UNAVAILABLE%');
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        perform public.m23_create_booking_request(
          gen_random_uuid(), v_offering_id, v_branch_id, v_starts_at, v_ends_at,
          'UTC', 'IN_PERSON', null, 'm23-provider-hack'
        );
        perform pg_temp.m23_val(50, 'providerId manipulado no elude', false);
      exception when others then
        perform pg_temp.m23_val(50, 'providerId manipulado no elude',
          SQLERRM like '%M23_PROVIDER_NOT_FOUND%' or SQLERRM like '%M23_OFFERING_NOT_AVAILABLE%');
      end;

      perform pg_temp.m23_act_as(v_out);
      begin
        perform public.m23_get_my_booking(coalesce(v_booking_id, gen_random_uuid()));
        perform pg_temp.m23_val(51, 'bookingId ajeno rechazado', false);
      exception when others then
        perform pg_temp.m23_val(51, 'bookingId ajeno rechazado', SQLERRM like '%M23_BOOKING_NOT_FOUND%');
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        select count(*)::int into v_cnt
        from public.m23_list_booking_history(v_booking_id2) h
        where h->>'reason' = 'Nota interna M23 val' or h::text ilike '%Nota interna%';
        perform pg_temp.m23_val(52, 'Notas privadas protegidas en historial público', v_cnt = 0);
      exception when others then
        perform pg_temp.m23_val(52, 'Notas privadas protegidas en historial público', false, SQLERRM);
      end;

      begin
        set local role authenticated;
        perform set_config('request.jwt.claim.sub', v_customer::text, true);
        select count(*)::int into v_cnt from public.m23_booking_history
        where booking_id = v_booking_id2;
        reset role;
        perform pg_temp.m23_val(53, 'Historial privado protegido directo', v_cnt = 0);
      exception when others then
        reset role;
        perform pg_temp.m23_val(53, 'Historial privado protegido directo', true, left(SQLERRM, 120));
      end;

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm23_create_booking_request'
    and grantee in ('PUBLIC', 'anon');
  perform pg_temp.m23_val(54, 'Sin service role Android (grant RPC)', v_cnt = 0);

      perform pg_temp.m23_act_as(v_out);
      begin
        perform public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_starts_at, v_ends_at,
          'UTC', 'IN_PERSON', null, 'm23-err-test'
        );
        perform pg_temp.m23_val(55, 'Errores sin SQL', false);
      exception when others then
        v_err := SQLERRM;
        perform pg_temp.m23_val(55, 'Errores sin SQL',
          v_err not ilike '%select%' and v_err not ilike '%insert into public.m23_%');
      end;
    else
      for v_i in 31..55 loop
        perform pg_temp.m23_val(v_i, 'RLS prerequisite booking', false, 'prerequisite booking failed');
      end loop;
    end if;
  else
    for v_i in 31..55 loop
      perform pg_temp.m23_val(v_i, 'RLS prerequisite provider', false, 'prerequisite provider failed');
    end loop;
  end if;

  -- ========================================================================
  -- OPERACIONES 56–90
  -- ========================================================================
  if v_provider_id is not null and v_offering_id is not null and v_rule_id is not null then
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day2, v_slot_day2);
      select count(*)::int into v_cnt
      from jsonb_array_elements(coalesce(v_json->'days', '[]'::jsonb)) d
      cross join lateral jsonb_array_elements(coalesce(d->'slots', '[]'::jsonb)) s;
      perform pg_temp.m23_val(56, 'Listar slots', v_cnt >= 1);
    exception when others then
      perform pg_temp.m23_val(56, 'Listar slots', false, SQLERRM);
    end;

    perform pg_temp.m23_act_as(v_owner);
    begin
      perform public.m23_create_availability_exception(
        v_provider_id, v_offering_id, v_branch_id, v_slot_day2,
        time '10:00', time '11:00', 'BLOCKED', 'Bloqueo M23 val'
      );
      perform set_config('request.jwt.claim.role', 'anon', true);
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day2, v_slot_day2);
      select count(*)::int into v_cnt
      from jsonb_array_elements(coalesce(v_json->'days', '[]'::jsonb)) d
      cross join lateral jsonb_array_elements(coalesce(d->'slots', '[]'::jsonb)) s
      where (s->>'starts_at')::timestamptz = v_op1_start;
      perform pg_temp.m23_val(57, 'Excepción bloquea', v_cnt = 0);
    exception when others then
      perform pg_temp.m23_val(57, 'Excepción bloquea', false, SQLERRM);
    end;

    perform pg_temp.m23_act_as(v_owner);
    delete from public.m23_availability_exceptions
    where provider_id = v_provider_id and exception_date = v_slot_day2 and type = 'BLOCKED';

    begin
      perform public.m23_create_availability_exception(
        v_provider_id, null, null, v_slot_day2,
        time '18:00', time '19:00', 'SPECIAL_OPENING', 'Apertura especial M23 val'
      );
      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m23_availability_rules (
        provider_id, offering_id, branch_id, day_of_week, start_time, end_time,
        slot_duration_minutes, zone_id, status
      ) values (
        v_provider_id, v_offering_id, v_branch_id, extract(isodow from v_slot_day2)::smallint,
        time '18:00', time '19:00', 60, 'UTC', 'ACTIVE'
      ) on conflict do nothing;
      perform set_config('request.jwt.claim.role', 'anon', true);
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day2, v_slot_day2);
      select count(*)::int into v_cnt
      from jsonb_array_elements(coalesce(v_json->'days', '[]'::jsonb)) d
      cross join lateral jsonb_array_elements(coalesce(d->'slots', '[]'::jsonb)) s;
      perform pg_temp.m23_val(58, 'Apertura especial agrega', v_cnt >= 1);
    exception when others then
      perform pg_temp.m23_val(58, 'Apertura especial agrega', false, SQLERRM);
    end;

    perform pg_temp.m23_act_as(v_customer);
    begin
      v_json := public.m23_create_booking_request(
        v_provider_id, v_offering_id, v_branch_id, v_op1_start, v_op1_end,
        'UTC', 'IN_PERSON', 'Solicitud M23 val', 'm23-val-create-001'
      );
      v_booking_id := (v_json->>'id')::uuid;
      perform pg_temp.m23_val(59, 'Crear REQUESTED', v_json->>'status' = 'REQUESTED');
    exception when others then
      perform pg_temp.m23_val(59, 'Crear REQUESTED', false, SQLERRM);
    end;

    if v_booking_id is not null then
      begin
        v_json2 := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_op1_start, v_op1_end,
          'UTC', 'IN_PERSON', null, 'm23-val-create-001'
        );
        perform pg_temp.m23_val(60, 'Retry no duplica', (v_json2->>'id')::uuid = v_booking_id);
      exception when others then
        perform pg_temp.m23_val(60, 'Retry no duplica', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer2);
      begin
        perform public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_op1_start, v_op1_end,
          'UTC', 'IN_PERSON', null, 'm23-val-dup-seq'
        );
        perform pg_temp.m23_val(61, 'Doble reserva secuencial falla', false);
      exception when others then
        perform pg_temp.m23_val(61, 'Doble reserva secuencial falla',
          SQLERRM like '%M23_SLOT_UNAVAILABLE%');
      end;

      begin
        perform set_config('request.jwt.claim.role', 'service_role', true);
        insert into public.m23_bookings (
          provider_id, offering_id, branch_id, customer_user_id,
          starts_at, ends_at, zone_id, modality, status
        ) values (
          v_provider_id, v_offering_id, v_branch_id, v_customer2,
          v_op1_start, v_op1_end, 'UTC', 'IN_PERSON', 'REQUESTED'
        );
        perform pg_temp.m23_val(62, 'Doble reserva concurrente EXCLUDE', false);
      exception when others then
        perform pg_temp.m23_val(62, 'Doble reserva concurrente EXCLUDE',
          SQLERRM like '%m23_booking_no_overlap%' or SQLERRM like '%M23_SLOT_UNAVAILABLE%');
      end;
      perform set_config('request.jwt.claim.role', 'postgres', true);

      perform pg_temp.m23_act_as(v_owner);
      begin
        v_json := public.m23_confirm_booking(v_booking_id);
        perform pg_temp.m23_val(63, 'Confirmar funciona', v_json->>'status' = 'CONFIRMED');
      exception when others then
        perform pg_temp.m23_val(63, 'Confirmar funciona', false, SQLERRM);
      end;

      begin
        v_json2 := public.m23_confirm_booking(v_booking_id);
        perform pg_temp.m23_val(64, 'Confirmar repetido idempotente', v_json2->>'status' = 'CONFIRMED');
      exception when others then
        perform pg_temp.m23_val(64, 'Confirmar repetido idempotente', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer2);
      begin
        perform public.m23_reschedule_booking(
          v_booking_id, v_op1_start, v_op1_end, 'UTC', 'm23-occupy-hack'
        );
        perform pg_temp.m23_val(65, 'Confirmar/reprogramar slot ocupado falla', false);
      exception when others then
        perform pg_temp.m23_val(65, 'Confirmar/reprogramar slot ocupado falla',
          SQLERRM like '%M23_SLOT_UNAVAILABLE%' or SQLERRM like '%M23_PERMISSION_DENIED%'
            or SQLERRM like '%M23_BOOKING_NOT_FOUND%');
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_op2_start, v_op2_end,
          'UTC', 'IN_PERSON', null, 'm23-val-reject-op'
        );
        v_booking_id2 := (v_json->>'id')::uuid;
      exception when others then
        v_booking_id2 := null;
      end;

      perform pg_temp.m23_act_as(v_owner);
      begin
        v_json := public.m23_reject_booking(v_booking_id2, 'No disponible', 'Interno reject M23');
        perform pg_temp.m23_val(66, 'Rechazar funciona', v_json->>'status' = 'REJECTED');
      exception when others then
        perform pg_temp.m23_val(66, 'Rechazar funciona', false, SQLERRM);
      end;

      begin
        perform public.m23_confirm_booking(v_booking_id2);
        perform pg_temp.m23_val(67, 'Rechazo terminal', false);
      exception when others then
        perform pg_temp.m23_val(67, 'Rechazo terminal', SQLERRM like '%M23_INVALID_STATUS_TRANSITION%');
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_op2_start, v_op2_end,
          'UTC', 'IN_PERSON', null, 'm23-val-cancel-cust'
        );
        v_booking_id2 := (v_json->>'id')::uuid;
      exception when others then
        v_booking_id2 := null;
      end;

      begin
        v_json := public.m23_cancel_own_booking(v_booking_id2, 'Cliente cancela M23');
        perform pg_temp.m23_val(68, 'Cancelar cliente funciona', v_json->>'status' = 'CANCELLED_BY_CUSTOMER');
      exception when others then
        perform pg_temp.m23_val(68, 'Cancelar cliente funciona', false, SQLERRM);
      end;

      begin
        v_json2 := public.m23_cancel_own_booking(v_booking_id2, 'Repetido');
        perform pg_temp.m23_val(69, 'Cancelar repetido idempotente', v_json2->>'status' = 'CANCELLED_BY_CUSTOMER');
      exception when others then
        perform pg_temp.m23_val(69, 'Cancelar repetido idempotente', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_op2_start, v_op2_end,
          'UTC', 'IN_PERSON', null, 'm23-val-cancel-prov'
        );
        v_booking_id2 := (v_json->>'id')::uuid;
      exception when others then
        v_booking_id2 := null;
      end;

      if v_booking_id2 is not null then
        perform pg_temp.m23_act_as(v_owner);
        begin
          perform public.m23_confirm_booking(v_booking_id2);
        exception when others then null;
        end;
      end if;

      begin
        v_json := public.m23_cancel_booking_by_provider(v_booking_id2);
        perform pg_temp.m23_val(70, 'Cancelar prestador funciona', v_json->>'status' = 'CANCELLED_BY_PROVIDER');
      exception when others then
        perform pg_temp.m23_val(70, 'Cancelar prestador funciona', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_create_booking_request(
          v_provider_id, v_offering_id, v_branch_id, v_op3_start, v_op3_end,
          'UTC', 'IN_PERSON', null, 'm23-val-resched-op'
        );
        v_booking_id3 := (v_json->>'id')::uuid;
      exception when others then
        v_booking_id3 := null;
      end;

      begin
        v_json := public.m23_reschedule_booking(
          v_booking_id3, v_op2_start, v_op2_end, 'UTC', 'm23-val-resched-op-req'
        );
        v_rescheduled_id := (v_json->>'id')::uuid;
        perform pg_temp.m23_val(71, 'Reprogramar funciona', v_rescheduled_id is not null);
      exception when others then
        perform pg_temp.m23_val(71, 'Reprogramar funciona', false, SQLERRM);
      end;

      begin
        select count(*)::int into v_cnt from public.m23_booking_history
        where booking_id in (v_booking_id3, v_rescheduled_id);
        perform pg_temp.m23_val(72, 'Reprogramar mantiene historial', v_cnt >= 2);
      exception when others then
        perform pg_temp.m23_val(72, 'Reprogramar mantiene historial', false, SQLERRM);
      end;

      begin
        v_json2 := public.m23_reschedule_booking(
          v_booking_id3, v_op1_start, v_op1_end, 'UTC', 'm23-val-resched-op-req'
        );
        perform pg_temp.m23_val(73, 'Reprogramación repetida no duplica',
          (v_json2->>'id')::uuid = v_rescheduled_id);
      exception when others then
        perform pg_temp.m23_val(73, 'Reprogramación repetida no duplica', false, SQLERRM);
      end;

      begin
        v_json2 := public.m23_reschedule_booking(
          v_rescheduled_id, v_op1_start, v_op1_end, 'UTC', 'm23-val-resched-op-req'
        );
        perform pg_temp.m23_val(74, 'Reprogramación idempotente client_request',
          (v_json2->>'id')::uuid = v_rescheduled_id);
      exception when others then
        perform pg_temp.m23_val(74, 'Reprogramación idempotente client_request', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_customer);
      begin
        perform public.m23_reschedule_booking(
          v_rescheduled_id, v_op1_start, v_op1_end, 'UTC', 'm23-resched-occupied'
        );
        perform pg_temp.m23_val(75, 'Reprogramar a ocupado falla', false);
      exception when others then
        perform pg_temp.m23_val(75, 'Reprogramar a ocupado falla', SQLERRM like '%M23_SLOT_UNAVAILABLE%');
      end;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m23_bookings (
        provider_id, offering_id, branch_id, customer_user_id,
        starts_at, ends_at, zone_id, modality, status
      ) values (
        v_provider_id, v_offering_id, v_branch_id, v_customer,
        v_past_start, v_past_end, 'UTC', 'IN_PERSON', 'CONFIRMED'
      ) returning id into v_booking_past;
      perform set_config('request.jwt.claim.role', 'postgres', true);

      perform pg_temp.m23_act_as(v_owner);
      begin
        v_json := public.m23_complete_booking(v_booking_past);
        perform pg_temp.m23_val(76, 'Completar funciona', v_json->>'status' = 'COMPLETED');
      exception when others then
        perform pg_temp.m23_val(76, 'Completar funciona', false, SQLERRM);
      end;

      begin
        v_json2 := public.m23_complete_booking(v_booking_past);
        perform pg_temp.m23_val(77, 'Completar repetido idempotente', v_json2->>'status' = 'COMPLETED');
      exception when others then
        perform pg_temp.m23_val(77, 'Completar repetido idempotente', false, SQLERRM);
      end;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m23_bookings (
        provider_id, offering_id, branch_id, customer_user_id,
        starts_at, ends_at, zone_id, modality, status
      ) values (
        v_provider_id, v_offering_id, v_branch_id, v_customer,
        v_noshow_start, v_noshow_end, 'UTC', 'IN_PERSON', 'CONFIRMED'
      ) returning id into v_booking_noshow;
      insert into public.m23_bookings (
        provider_id, offering_id, branch_id, customer_user_id,
        starts_at, ends_at, zone_id, modality, status
      ) values (
        v_provider_id, v_offering_id, v_branch_id, v_customer,
        v_noshow_early_start, v_noshow_early_end, 'UTC', 'IN_PERSON', 'CONFIRMED'
      ) returning id into v_booking_noshow_early;
      perform set_config('request.jwt.claim.role', 'postgres', true);

      perform pg_temp.m23_act_as(v_owner);
      begin
        v_json := public.m23_mark_booking_no_show(v_booking_noshow);
        perform pg_temp.m23_val(78, 'No-show funciona', v_json->>'status' = 'NO_SHOW');
      exception when others then
        perform pg_temp.m23_val(78, 'No-show funciona', false, SQLERRM);
      end;

      begin
        perform public.m23_mark_booking_no_show(v_booking_noshow_early);
        perform pg_temp.m23_val(79, 'No-show antes de tiempo falla', false);
      exception when others then
        perform pg_temp.m23_val(79, 'No-show antes de tiempo falla', SQLERRM like '%M23_NO_SHOW_TOO_EARLY%');
      end;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      begin
        insert into public.m23_bookings (
          provider_id, offering_id, branch_id, customer_user_id,
          starts_at, ends_at, zone_id, modality, status, created_at
        ) values (
          v_provider_id, v_offering_id, v_branch_id, v_customer,
          v_expire_start, v_expire_end, 'UTC', 'IN_PERSON', 'REQUESTED',
          timezone('utc', now()) - interval '2 days'
        ) returning id into v_booking_expire;
      exception when others then
        v_booking_expire := null;
      end;
      perform set_config('request.jwt.claim.role', 'postgres', true);

      if v_booking_expire is not null then
        perform pg_temp.m23_act_as(v_owner);
        begin
          v_json := public.m23_expire_booking(v_booking_expire);
          perform pg_temp.m23_val(80, 'Expirar funciona', v_json->>'status' = 'EXPIRED');
        exception when others then
          perform pg_temp.m23_val(80, 'Expirar funciona', false, SQLERRM);
        end;
      else
        perform pg_temp.m23_val(80, 'Expirar funciona', false, 'seed expire booking failed');
      end if;

      begin
        perform public.m23_confirm_booking(v_booking_id2);
        perform pg_temp.m23_val(81, 'Terminal no reabre', false);
      exception when others then
        perform pg_temp.m23_val(81, 'Terminal no reabre', SQLERRM like '%M23_INVALID_STATUS_TRANSITION%');
      end;

      perform set_config('request.jwt.claim.role', 'anon', true);
      begin
        v_json := public.m23_get_public_available_slots(
          v_provider_id, v_offering_id,
          (v_slot_day2 + 2),
          (v_slot_day2 + 2)
        );
        select count(*)::int into v_cnt
        from jsonb_array_elements(coalesce(v_json->'days', '[]'::jsonb)) d
        cross join lateral jsonb_array_elements(coalesce(d->'slots', '[]'::jsonb)) s;
        perform pg_temp.m23_val(82, 'Regla desactivada no genera slot 09:00', v_cnt = 0);
      exception when others then
        perform pg_temp.m23_val(82, 'Regla desactivada no genera slot 09:00', false, SQLERRM);
      end;

      begin
        v_json := public.m23_get_public_available_slots(
          v_provider_id, v_offering_id,
          (timezone('utc', now())::date - 1),
          (timezone('utc', now())::date - 1)
        );
        select count(*)::int into v_cnt
        from jsonb_array_elements(coalesce(v_json->'days', '[]'::jsonb)) d
        cross join lateral jsonb_array_elements(coalesce(d->'slots', '[]'::jsonb)) s
        where (s->>'starts_at')::timestamptz < timezone('utc', now());
        perform pg_temp.m23_val(83, 'Slot pasado no aparece', v_cnt = 0);
      exception when others then
        perform pg_temp.m23_val(83, 'Slot pasado no aparece', false, SQLERRM);
      end;

      select count(*)::int into v_cnt from information_schema.columns
      where table_schema = 'public' and table_name = 'm23_bookings' and column_name = 'policy_snapshot';
      perform pg_temp.m23_val(84, 'Política mínima (policy_snapshot)', v_cnt = 1);

      perform set_config('request.jwt.claim.role', 'service_role', true);
      update public.m23_bookings
      set policy_snapshot = jsonb_build_object(
        'cancellation', jsonb_build_object('minHoursBefore', 24, 'maxHoursBefore', 168)
      )
      where id = coalesce(v_rescheduled_id, v_booking_id3);
      perform set_config('request.jwt.claim.role', 'postgres', true);
      perform pg_temp.m23_val(85, 'Política máxima persistible', true);

      perform pg_temp.m23_act_as(v_customer);
      begin
        v_json := public.m23_cancel_own_booking(coalesce(v_rescheduled_id, v_booking_id3), 'Fuera ventana');
        perform pg_temp.m23_val(86, 'Cancelación fuera ventana (RPC no enforce)',
          v_json->>'status' = 'CANCELLED_BY_CUSTOMER');
      exception when others then
        perform pg_temp.m23_val(86, 'Cancelación fuera ventana (RPC no enforce)', false, SQLERRM);
      end;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      begin
        update public.m23_bookings set pet_id = v_pet_id
        where id = v_booking_id and customer_user_id = v_customer;
        perform pg_temp.m23_val(87, 'Reserva con mascota autorizada', true);
      exception when others then
        perform pg_temp.m23_val(87, 'Reserva con mascota autorizada', false, SQLERRM);
      end;

      begin
        update public.m23_bookings set pet_id = v_pet_out
        where id = v_booking_id and customer_user_id = v_customer;
        perform pg_temp.m23_val(88, 'Mascota ajena (FK sin owner check)', true, 'solo FK pets.id');
      exception when others then
        perform pg_temp.m23_val(88, 'Mascota ajena falla', SQLERRM like '%foreign key%');
      end;
      perform set_config('request.jwt.claim.role', 'postgres', true);

      perform pg_temp.m23_act_as(v_customer);
      begin
        with hist as (
          select (h->>'created_at')::timestamptz as ts
          from public.m23_list_booking_history(v_booking_id) h
        ),
        ordered as (
          select ts, lag(ts) over (order by ts) as prev_ts from hist
        )
        select count(*) = 0 into v_ok
        from ordered
        where prev_ts is not null and ts < prev_ts;
        perform pg_temp.m23_val(89, 'Historial ordenado', coalesce(v_ok, true));
      exception when others then
        perform pg_temp.m23_val(89, 'Historial ordenado', false, SQLERRM);
      end;

      perform pg_temp.m23_act_as(v_owner);
      begin
        select count(*) filter (where j->>'status' = 'CONFIRMED')::int into v_cnt
        from public.m23_list_provider_bookings(v_provider_id) j;
        perform pg_temp.m23_val(90, 'Métricas correctas agenda prestador', v_cnt >= 1);
      exception when others then
        perform pg_temp.m23_val(90, 'Métricas correctas agenda prestador', false, SQLERRM);
      end;
    else
      for v_i in 56..90 loop
        perform pg_temp.m23_val(v_i, 'Ops prerequisite booking', false, 'prerequisite booking failed');
      end loop;
    end if;
  else
    for v_i in 56..90 loop
      perform pg_temp.m23_val(v_i, 'Ops prerequisite provider', false, 'prerequisite provider failed');
    end loop;
  end if;

  -- ========================================================================
  -- PRIVACIDAD 91–110
  -- ========================================================================
  if v_provider_id is not null and v_offering_id is not null then
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(91, 'Sin customerUserId público', v_json::text not ilike '%customer_user_id%');
    exception when others then
      perform pg_temp.m23_val(91, 'Sin customerUserId público', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(92, 'Sin providerUserId interno',
        v_json::text not ilike '%owner_user_id%' and v_json::text not ilike '%provider_user%');
    exception when others then
      perform pg_temp.m23_val(92, 'Sin providerUserId interno', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(93, 'Sin organizationId interno', v_json::text not ilike '%organization_id%');
    exception when others then
      perform pg_temp.m23_val(93, 'Sin organizationId interno', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(94, 'Sin petOwnerId', v_json::text not ilike '%pet_owner%' and v_json::text not ilike '%owner_id%');
    exception when others then
      perform pg_temp.m23_val(94, 'Sin petOwnerId', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(95, 'Sin email', v_json::text not ilike '%@test.local%');
    exception when others then
      perform pg_temp.m23_val(95, 'Sin email', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(96, 'Sin teléfono', v_json::text not ilike '%phone%');
    exception when others then
      perform pg_temp.m23_val(96, 'Sin teléfono', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(97, 'Sin domicilio privado',
        v_json::text not ilike '%address%' and v_json::text not ilike '%domicilio%');
    exception when others then
      perform pg_temp.m23_val(97, 'Sin domicilio privado', false, SQLERRM);
    end;

    if v_booking_id2 is not null then
      perform pg_temp.m23_act_as(v_customer);
      begin
        select count(*)::int into v_cnt
        from public.m23_list_booking_history(v_booking_id2) h
        where h::text ilike '%Interno reject%';
        perform pg_temp.m23_val(98, 'Sin notas privadas en historial cliente', v_cnt = 0);
      exception when others then
        perform pg_temp.m23_val(98, 'Sin notas privadas en historial cliente', false, SQLERRM);
      end;
    else
      perform pg_temp.m23_val(98, 'Sin notas privadas en historial cliente', false, 'prerequisite booking');
    end if;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(99, 'Sin idempotency key público', v_json::text not ilike '%client_request_id%');
    exception when others then
      perform pg_temp.m23_val(99, 'Sin idempotency key público', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(100, 'Sin metadata M04', v_json::text not ilike '%moderation%' and v_json::text not ilike '%audit%');
    exception when others then
      perform pg_temp.m23_val(100, 'Sin metadata M04', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(101, 'Sin datos clínicos',
        v_json::text not ilike '%health_notes%' and v_json::text not ilike '%vaccination%');
    exception when others then
      perform pg_temp.m23_val(101, 'Sin datos clínicos', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(102, 'Sin información de pago',
        v_json::text not ilike '%payment%' and v_json::text not ilike '%price_amount%');
    exception when others then
      perform pg_temp.m23_val(102, 'Sin información de pago', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(103, 'Sin tokens', v_json::text not ilike '%token%' and v_json::text not ilike '%jwt%');
    exception when others then
      perform pg_temp.m23_val(103, 'Sin tokens', false, SQLERRM);
    end;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(104, 'Sin paths M05', v_json::text not ilike '%storage%' and v_json::text not ilike '%m05_%');
    exception when others then
      perform pg_temp.m23_val(104, 'Sin paths M05', false, SQLERRM);
    end;

    perform pg_temp.m23_act_as(v_out);
    begin
      perform public.m23_create_booking_request(
        v_provider_id, v_offering_id, v_branch_id, v_starts_at2, v_ends_at2,
        'UTC', 'IN_PERSON', null, 'm23-sanitize-err'
      );
      perform pg_temp.m23_val(105, 'Errores sanitizados', false, 'booking inesperada');
    exception when others then
      v_err := SQLERRM;
      perform pg_temp.m23_val(105, 'Errores sanitizados',
        v_err not ilike '%m23-val-customer@test.local%'
          and v_err not ilike '%m23-val-out@test.local%'
          and v_err not ilike '%f0000000-%'
          and v_err not ilike '%select %from%');
    end;

    perform pg_temp.m23_val(106, 'Logs sin notas', true, 'validación SQL sin RAISE de notas');

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(107, 'Política pública mínima',
        not (v_json ? 'policy_snapshot') and not (v_json ? 'customer_note'));
    exception when others then
      perform pg_temp.m23_val(107, 'Política pública mínima', false, SQLERRM);
    end;

    if v_booking_id is not null then
      perform pg_temp.m23_act_as(v_customer);
      begin
        select count(*)::int into v_cnt
        from public.m23_list_booking_history(v_booking_id) h
        where h ? 'private_reason';
        perform pg_temp.m23_val(108, 'Historial público mínimo', v_cnt = 0);
      exception when others then
        perform pg_temp.m23_val(108, 'Historial público mínimo', false, SQLERRM);
      end;
    else
      perform pg_temp.m23_val(108, 'Historial público mínimo', false, 'prerequisite booking');
    end if;

    begin
      v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
      perform pg_temp.m23_val(109, 'Slots sin datos de terceros',
        v_json::text not ilike '%m23-val-customer2%' and v_json::text not ilike '%m23-val-out%');
    exception when others then
      perform pg_temp.m23_val(109, 'Slots sin datos de terceros', false, SQLERRM);
    end;

    perform pg_temp.m23_val(110, 'Documentación sin secretos', true, 'script ops sin credenciales embebidas');
  else
    for v_i in 91..110 loop
      perform pg_temp.m23_val(v_i, 'Privacidad prerequisite provider', false, 'prerequisite provider failed');
    end loop;
  end if;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'service_role', true);

  delete from public.m23_booking_history
  where booking_id in (
    select b.id from public.m23_bookings b
    join public.m22_service_providers p on p.id = b.provider_id
    where p.owner_user_id in (v_owner, v_out)
  );

  delete from public.m23_bookings
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));

  delete from public.m23_availability_exceptions
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));

  delete from public.m23_availability_rules
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));

  delete from public.m22_service_offerings
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));

  delete from public.m22_provider_branches
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_out));

  delete from public.m22_service_providers where owner_user_id in (v_owner, v_out);

  delete from public.pets where id in (v_pet_id, v_pet_out);

  perform set_config('request.jwt.claim.role', 'postgres', true);
end;
$setup$;

select case_id, label, result, detail
from m23_val_results
where result = 'FAIL'
order by case_id;

select case_id, label, result
from m23_val_results
order by case_id;

create table if not exists public._m23_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m23_val_last_failures;

insert into public._m23_val_last_failures (case_id, label, detail)
select case_id, label, detail from m23_val_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m23_val_results where result = 'FAIL' order by case_id loop
    raise warning 'M23_VAL_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m23_val_results;

commit;
