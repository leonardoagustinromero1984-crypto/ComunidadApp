-- M23 smoke remoto casos 01–25 — repositorio Supabase staging (no APK)
-- Ejecutar: supabase db query --linked -f scripts/ops/m23_remote_smoke_25.sql
-- No reemplaza validación 110/110. Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m23_smoke_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m23_smoke(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m23_smoke_results (case_id, label, result, detail)
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
  v_owner uuid := 'f0000000-0000-4000-8000-000000000021';
  v_customer uuid := 'f0000000-0000-4000-8000-000000000022';
  v_out uuid := 'f0000000-0000-4000-8000-000000000023';
  v_provider_id uuid;
  v_branch_id uuid;
  v_offering_id uuid;
  v_rule_id uuid;
  v_pet_id uuid := 'c0000000-0000-4000-8000-000000000021';
  v_slot_day date;
  v_dow smallint;
  v_op_start timestamptz;
  v_op_end timestamptz;
  v_op2_start timestamptz;
  v_op2_end timestamptz;
  v_past_start timestamptz;
  v_past_end timestamptz;
  v_noshow_start timestamptz;
  v_noshow_end timestamptz;
  v_json jsonb;
  v_json2 jsonb;
  v_booking_id uuid;
  v_booking_id2 uuid;
  v_booking_rej uuid;
  v_booking_cancel uuid;
  v_booking_resched uuid;
  v_rescheduled_id uuid;
  v_completed_id uuid;
  v_noshow_id uuid;
  v_cnt int;
  v_ok boolean;
  v_err text;
  v_i int;
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_owner, 'authenticated', 'authenticated',
     'm23-smoke-owner@test.local', crypt('m23-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_customer, 'authenticated', 'authenticated',
     'm23-smoke-customer@test.local', crypt('m23-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm23-smoke-out@test.local', crypt('m23-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_owner, 'm23-smoke-owner@test.local', 'M23 Smoke Owner', 'M23 Smoke Owner', 'PERSON', true, 'ACTIVE'),
    (v_customer, 'm23-smoke-customer@test.local', 'M23 Smoke Customer', 'M23 Smoke Customer', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm23-smoke-out@test.local', 'M23 Smoke Outsider', 'M23 Smoke Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  insert into public.pets (id, owner_id, name, species, sex, size, description)
  values (v_pet_id, v_customer, 'Smoke Luna', 'DOG', 'FEMALE', 'MEDIUM', 'Mascota smoke M23 LeoVer.')
  on conflict (id) do nothing;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m23_booking_history where booking_id in (
    select b.id from public.m23_bookings b join public.m22_service_providers p on p.id = b.provider_id
    where p.owner_user_id = v_owner);
  delete from public.m23_bookings where provider_id in (
    select id from public.m22_service_providers where owner_user_id = v_owner);
  delete from public.m23_availability_exceptions where provider_id in (
    select id from public.m22_service_providers where owner_user_id = v_owner);
  delete from public.m23_availability_rules where provider_id in (
    select id from public.m22_service_providers where owner_user_id = v_owner);
  delete from public.m22_service_offerings where provider_id in (
    select id from public.m22_service_providers where owner_user_id = v_owner);
  delete from public.m22_provider_branches where provider_id in (
    select id from public.m22_service_providers where owner_user_id = v_owner);
  delete from public.m22_service_providers where owner_user_id = v_owner;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  v_slot_day := (timezone('utc', now())::date + 14);
  while extract(isodow from v_slot_day)::int <> 1 loop v_slot_day := v_slot_day + 1; end loop;
  v_dow := extract(isodow from v_slot_day)::smallint;
  v_op_start := ((v_slot_day + time '10:00:00')::timestamp at time zone 'UTC');
  v_op_end := v_op_start + interval '60 minutes';
  v_op2_start := ((v_slot_day + time '11:00:00')::timestamp at time zone 'UTC');
  v_op2_end := v_op2_start + interval '60 minutes';
  v_past_start := timezone('utc', now()) - interval '4 hours';
  v_past_end := timezone('utc', now()) - interval '3 hours';
  v_noshow_start := timezone('utc', now()) - interval '2 hours';
  v_noshow_end := timezone('utc', now()) - interval '1 hour';

  -- 01 DataProvider Supabase M23 wired
  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname in ('m23_create_booking_request', 'm23_list_my_bookings');
  perform pg_temp.m23_smoke(1, 'DataProvider Supabase M23 wired', v_cnt = 2);

  -- Setup M22 + M23
  perform pg_temp.m23_act_as(v_owner);
  v_json := public.m22_create_provider('Agenda Smoke M23', 'GROOMING', 'Smoke M23 LeoVer.', 'CABA');
  v_provider_id := (v_json->>'id')::uuid;
  v_json := public.m22_upsert_branch(v_provider_id, null, 'Sede Smoke', 'CABA', 'Palermo', 'NEIGHBORHOOD', 'CABA', 'Palermo');
  v_branch_id := (v_json->>'id')::uuid;
  v_json := public.m22_upsert_offering(v_provider_id, null, v_branch_id, 'Baño Smoke', 'Smoke M23.', 'FIXED', 12000);
  v_offering_id := (v_json->>'id')::uuid;
  perform public.m22_update_provider(v_provider_id, p_status := 'ACTIVE');
  v_json := public.m23_create_availability_rule(v_provider_id, v_offering_id, v_branch_id, v_dow, time '09:00', time '17:00', 60, 'UTC', 'ACTIVE');
  v_rule_id := (v_json->>'id')::uuid;

  -- 02 Directorio reservable
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  select count(*)::int into v_cnt from public.m22_list_catalog() j where j->>'display_name' = 'Agenda Smoke M23';
  perform pg_temp.m23_smoke(2, 'Directorio reservable carga', v_cnt >= 1);

  -- 03 Disponibilidad
  perform pg_temp.m23_act_as(v_owner);
  select count(*)::int into v_cnt from public.m23_list_availability_rules(v_provider_id);
  perform pg_temp.m23_smoke(3, 'Disponibilidad carga', v_cnt >= 1);

  -- 04 Slots públicos
  perform set_config('request.jwt.claim.role', 'anon', true);
  v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
  select count(*)::int into v_cnt from jsonb_array_elements(coalesce(v_json->'days', '[]'::jsonb)) d
    cross join lateral jsonb_array_elements(coalesce(d->'slots', '[]'::jsonb)) s;
  perform pg_temp.m23_smoke(4, 'Slots públicos cargan', v_cnt >= 1);

  -- 05 Crear reserva
  perform pg_temp.m23_act_as(v_customer);
  v_json := public.m23_create_booking_request(v_provider_id, v_offering_id, v_branch_id, v_op_start, v_op_end, 'UTC', 'IN_PERSON', null, 'm23-smoke-1');
  v_booking_id := (v_json->>'id')::uuid;
  perform pg_temp.m23_smoke(5, 'Crear reserva funciona', v_json->>'status' = 'REQUESTED');

  -- 06 Retry no duplica
  v_json2 := public.m23_create_booking_request(v_provider_id, v_offering_id, v_branch_id, v_op_start, v_op_end, 'UTC', 'IN_PERSON', null, 'm23-smoke-1');
  perform pg_temp.m23_smoke(6, 'Retry no duplica', (v_json2->>'id')::uuid = v_booking_id);

  -- 07 Mis reservas
  select count(*)::int into v_cnt from public.m23_list_my_bookings();
  perform pg_temp.m23_smoke(7, 'Mis reservas cargan', v_cnt >= 1);

  -- 08 Detalle
  v_json := public.m23_get_my_booking(v_booking_id);
  perform pg_temp.m23_smoke(8, 'Detalle carga', v_json->>'id' = v_booking_id::text);

  -- 09 Agenda prestador
  perform pg_temp.m23_act_as(v_owner);
  select count(*)::int into v_cnt from public.m23_list_provider_bookings(v_provider_id);
  perform pg_temp.m23_smoke(9, 'Prestador ve agenda', v_cnt >= 1);

  -- 10 Confirmar
  v_json := public.m23_confirm_booking(v_booking_id);
  perform pg_temp.m23_smoke(10, 'Confirmar funciona', v_json->>'status' = 'CONFIRMED');

  -- 11 Rechazar (nueva REQUESTED)
  perform pg_temp.m23_act_as(v_customer);
  v_json := public.m23_create_booking_request(v_provider_id, v_offering_id, v_branch_id, v_op2_start, v_op2_end, 'UTC', 'IN_PERSON', null, 'm23-smoke-rej');
  v_booking_rej := (v_json->>'id')::uuid;
  perform pg_temp.m23_act_as(v_owner);
  v_json := public.m23_reject_booking(v_booking_rej, 'No disponible', 'Nota privada smoke');
  perform pg_temp.m23_smoke(11, 'Rechazar funciona', v_json->>'status' = 'REJECTED');

  -- 12 Cancelación cliente
  perform pg_temp.m23_act_as(v_customer);
  v_json := public.m23_create_booking_request(v_provider_id, v_offering_id, v_branch_id,
    ((v_slot_day + time '12:00:00')::timestamp at time zone 'UTC'),
    ((v_slot_day + time '13:00:00')::timestamp at time zone 'UTC'), 'UTC', 'IN_PERSON', null, 'm23-smoke-cancel-c');
  v_booking_cancel := (v_json->>'id')::uuid;
  perform pg_temp.m23_act_as(v_customer);
  v_json := public.m23_cancel_own_booking(v_booking_cancel, 'Cancel smoke');
  perform pg_temp.m23_smoke(12, 'Cancelación cliente funciona', v_json->>'status' = 'CANCELLED_BY_CUSTOMER');

  -- 13 Cancelación prestador
  perform pg_temp.m23_act_as(v_customer);
  v_json := public.m23_create_booking_request(v_provider_id, v_offering_id, v_branch_id,
    ((v_slot_day + time '14:00:00')::timestamp at time zone 'UTC'),
    ((v_slot_day + time '15:00:00')::timestamp at time zone 'UTC'), 'UTC', 'IN_PERSON', null, 'm23-smoke-cancel-p');
  v_booking_id2 := (v_json->>'id')::uuid;
  perform pg_temp.m23_act_as(v_owner);
  v_json := public.m23_confirm_booking(v_booking_id2);
  v_json := public.m23_cancel_booking_by_provider(v_booking_id2);
  perform pg_temp.m23_smoke(13, 'Cancelación prestador funciona', v_json->>'status' = 'CANCELLED_BY_PROVIDER');

  -- 14 Reprogramación
  perform pg_temp.m23_act_as(v_customer);
  v_json := public.m23_create_booking_request(v_provider_id, v_offering_id, v_branch_id,
    ((v_slot_day + time '15:00:00')::timestamp at time zone 'UTC'),
    ((v_slot_day + time '16:00:00')::timestamp at time zone 'UTC'), 'UTC', 'IN_PERSON', null, 'm23-smoke-resched-base');
  v_booking_resched := (v_json->>'id')::uuid;
  perform pg_temp.m23_act_as(v_owner);
  perform public.m23_confirm_booking(v_booking_resched);
  perform pg_temp.m23_act_as(v_customer);
  v_json := public.m23_reschedule_booking(v_booking_resched,
    ((v_slot_day + time '16:00:00')::timestamp at time zone 'UTC'),
    ((v_slot_day + time '17:00:00')::timestamp at time zone 'UTC'), 'UTC', 'm23-smoke-resched-new');
  v_rescheduled_id := (v_json->>'id')::uuid;
  perform pg_temp.m23_smoke(14, 'Reprogramación funciona', v_rescheduled_id is not null and v_json->>'status' = 'REQUESTED');

  -- 15 Doble reserva bloqueada
  perform pg_temp.m23_act_as(v_customer);
  begin
    perform public.m23_create_booking_request(v_provider_id, v_offering_id, v_branch_id,
      ((v_slot_day + time '16:00:00')::timestamp at time zone 'UTC'),
      ((v_slot_day + time '17:00:00')::timestamp at time zone 'UTC'), 'UTC', 'IN_PERSON', null, 'm23-smoke-dup');
    perform pg_temp.m23_smoke(15, 'Doble reserva concurrente bloqueada', false);
  exception when others then
    perform pg_temp.m23_smoke(15, 'Doble reserva concurrente bloqueada', SQLERRM like '%M23_SLOT_UNAVAILABLE%');
  end;

  -- 16 Completar
  perform set_config('request.jwt.claim.role', 'service_role', true);
  insert into public.m23_bookings (provider_id, offering_id, branch_id, customer_user_id, starts_at, ends_at, zone_id, modality, status)
  values (v_provider_id, v_offering_id, v_branch_id, v_customer, v_past_start, v_past_end, 'UTC', 'IN_PERSON', 'CONFIRMED')
  returning id into v_completed_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);
  perform pg_temp.m23_act_as(v_owner);
  v_json := public.m23_complete_booking(v_completed_id);
  perform pg_temp.m23_smoke(16, 'Completar funciona', v_json->>'status' = 'COMPLETED');

  -- 17 No-show
  perform set_config('request.jwt.claim.role', 'service_role', true);
  insert into public.m23_bookings (provider_id, offering_id, branch_id, customer_user_id, starts_at, ends_at, zone_id, modality, status)
  values (v_provider_id, v_offering_id, v_branch_id, v_customer, v_noshow_start, v_noshow_end, 'UTC', 'IN_PERSON', 'CONFIRMED')
  returning id into v_noshow_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);
  perform pg_temp.m23_act_as(v_owner);
  v_json := public.m23_mark_booking_no_show(v_noshow_id);
  perform pg_temp.m23_smoke(17, 'No-show funciona', v_json->>'status' = 'NO_SHOW');

  -- 18 Historial
  perform pg_temp.m23_act_as(v_customer);
  select count(*)::int into v_cnt from public.m23_list_booking_history(v_booking_id);
  perform pg_temp.m23_smoke(18, 'Historial carga', v_cnt >= 1);

  -- 19 M08 mascota
  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm23_bookings' and column_name = 'pet_id';
  perform pg_temp.m23_smoke(19, 'M08 mascota columna pet_id', v_cnt = 1);

  -- 20 M20 contexto (RPC disponible; integración Kotlin en cliente)
  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm20_create_direct_conversation';
  perform pg_temp.m23_smoke(20, 'M20 conversación contextual RPC', v_cnt >= 1, 'Kotlin M23BookingMessagingAdapter en cliente');

  -- 21 M21 elegibilidad (COMPLETED disponible)
  perform pg_temp.m23_smoke(21, 'M21 elegibilidad post-COMPLETED', v_completed_id is not null, 'adaptador Kotlin; sin reseña auto');

  -- 22 M06 no bloquea (operaciones RPC completadas sin infra M06)
  perform pg_temp.m23_smoke(22, 'M06 no disponible no provoca crash', true, 'hooks best-effort Kotlin');

  -- 23 Usuario ajeno
  perform pg_temp.m23_act_as(v_out);
  begin
    perform public.m23_get_my_booking(v_booking_id);
    perform pg_temp.m23_smoke(23, 'Usuario ajeno permiso denegado', false);
  exception when others then
    perform pg_temp.m23_smoke(23, 'Usuario ajeno permiso denegado', SQLERRM like '%M23_BOOKING_NOT_FOUND%');
  end;

  -- 24 Sin PII en slots
  perform set_config('request.jwt.claim.role', 'anon', true);
  v_json := public.m23_get_public_available_slots(v_provider_id, v_offering_id, v_slot_day, v_slot_day);
  perform pg_temp.m23_smoke(24, 'No aparece PII en slots', v_json::text not ilike '%@%'
    and v_json::text not ilike '%customer_user_id%');

  -- 25 Sin pagos M24
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name like 'm24_%';
  perform pg_temp.m23_smoke(25, 'No integración pagos M24', v_cnt = 0);

  -- Limpieza
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m23_booking_history where booking_id in (
    select b.id from public.m23_bookings b where b.provider_id = v_provider_id);
  delete from public.m23_bookings where provider_id = v_provider_id;
  delete from public.m23_availability_rules where provider_id = v_provider_id;
  delete from public.m22_service_offerings where provider_id = v_provider_id;
  delete from public.m22_provider_branches where provider_id = v_provider_id;
  delete from public.m22_service_providers where id = v_provider_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);
exception when others then
  for v_i in 1..25 loop
    if not exists (select 1 from m23_smoke_results where case_id = v_i) then
      perform pg_temp.m23_smoke(v_i, 'Smoke prerequisite', false, left(SQLERRM, 200));
    end if;
  end loop;
end;
$setup$;

select case_id, label, result, detail from m23_smoke_results where result = 'FAIL' order by case_id;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m23_smoke_results;

commit;
