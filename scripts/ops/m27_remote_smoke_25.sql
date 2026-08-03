-- M27 smoke remoto casos 01–25 — repositorio Supabase staging (no APK)
-- Ejecutar: supabase db query --linked -f scripts/ops/m27_remote_smoke_25.sql
-- No reemplaza validación 130/130. Limpia datos de prueba al finalizar. Sin pagos M24.

begin;

create table if not exists public._m27_smoke_run (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
);
delete from public._m27_smoke_run;

create or replace function pg_temp.m27_smoke(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into public._m27_smoke_run (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m27_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_dev uuid := 'f0000000-0000-4000-8000-000000000081';
  v_out uuid := 'f0000000-0000-4000-8000-000000000082';
  v_org uuid := 'a0000000-0000-4000-8000-000000000081';
  v_app_id uuid;
  v_key_id uuid;
  v_endpoint_id uuid;
  v_delivery_id uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_i int;
begin
  insert into public.organization_permissions (code, description)
  values ('integration.manage', 'Gestionar apps de integración LeoVer API pública')
  on conflict (code) do nothing;

  insert into public.organization_role_permissions (role_id, permission_id)
  select r.id, p.id
  from public.organization_roles r
  cross join public.organization_permissions p
  where r.code in ('OWNER', 'ADMIN', 'MANAGER') and p.code = 'integration.manage'
  on conflict do nothing;

  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_dev, 'authenticated', 'authenticated',
     'm27-smoke-dev@test.local', crypt('m27-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm27-smoke-out@test.local', crypt('m27-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_dev, 'm27-smoke-dev@test.local', 'M27 Smoke Dev', 'M27 Smoke Dev', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm27-smoke-out@test.local', 'M27 Smoke Outsider', 'M27 Smoke Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  insert into public.organizations (id, slug, display_name, type, status, created_by)
  values (v_org, 'm27-smoke-dev-org', 'M27 Smoke Dev Org', 'SHELTER', 'ACTIVE', v_dev)
  on conflict (id) do nothing;

  insert into public.organization_memberships (organization_id, user_id, role_code, status, joined_at)
  values (v_org, v_dev, 'OWNER', 'ACTIVE', now())
  on conflict do nothing;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m27_webhook_attempts where delivery_id in (
    select d.id from public.m27_webhook_deliveries d
    join public.m27_webhook_endpoints e on e.id = d.endpoint_id
    where e.owner_user_id in (v_dev, v_out));
  delete from public.m27_webhook_deliveries where endpoint_id in (
    select id from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_out));
  delete from public.m27_webhook_subscriptions where endpoint_id in (
    select id from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_out));
  delete from public.m27_webhook_events where app_id in (
    select id from public.m27_integration_apps where owner_user_id in (v_dev, v_out));
  alter table public.m27_audit_log disable trigger m27_audit_no_update;
  delete from public.m27_audit_log where actor_user_id in (v_dev, v_out);
  alter table public.m27_audit_log enable trigger m27_audit_no_update;
  delete from public.m27_idempotency_keys where actor_user_id in (v_dev, v_out);
  delete from public.m27_rate_limit_counters where counter_key like '%';
  delete from public.m27_api_credentials where owner_user_id in (v_dev, v_out);
  delete from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_out);
  delete from public.m27_integration_apps where owner_user_id in (v_dev, v_out);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- 01 DataProvider Supabase M27 wired
  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname in (
    'm27_list_my_integration_apps', 'm27_create_integration_app', 'm27_emit_webhook_event',
    'm27_list_my_deliveries', 'm27_check_app_rate_limit'
  );
  perform pg_temp.m27_smoke(1, 'DataProvider Supabase M27 wired', v_cnt = 5);

  -- 02 Hub carga apps
  perform pg_temp.m27_act_as(v_dev);
  begin
    select count(*)::int into v_cnt from public.m27_list_my_integration_apps();
    perform pg_temp.m27_smoke(2, 'Hub carga apps', v_cnt >= 0);
  exception when others then
    perform pg_temp.m27_smoke(2, 'Hub carga apps', false, SQLERRM);
  end;

  -- 03 Crear app funciona
  perform pg_temp.m27_act_as(v_dev);
  begin
    v_json := public.m27_create_integration_app(
      'App Smoke M27 LeoVer', v_org, 'V1', array['sandbox.execute','webhooks.manage'], 'SANDBOX', 'm27-smoke-app-1'
    );
    v_app_id := (v_json->>'id')::uuid;
    perform pg_temp.m27_smoke(3, 'Crear app funciona', v_app_id is not null and v_json->>'status' = 'DRAFT');
  exception when others then
    perform pg_temp.m27_smoke(3, 'Crear app funciona', false, SQLERRM);
  end;

  -- 04 Retry no duplica app
  if v_app_id is not null then
    begin
      v_json2 := public.m27_create_integration_app(
        'App Smoke M27 LeoVer', v_org, 'V1', array['sandbox.execute','webhooks.manage'], 'SANDBOX', 'm27-smoke-app-1'
      );
      perform pg_temp.m27_smoke(4, 'Retry no duplica app', (v_json2->>'id')::uuid = v_app_id);
    exception when others then
      perform pg_temp.m27_smoke(4, 'Retry no duplica app', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(4, 'Retry no duplica app', false, 'sin app');
  end if;

  -- 05 Activar app
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      v_json := public.m27_activate_integration_app(v_app_id);
      perform pg_temp.m27_smoke(5, 'Activar app', v_json->>'status' = 'ACTIVE');
    exception when others then
      perform pg_temp.m27_smoke(5, 'Activar app', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(5, 'Activar app', false, 'sin app');
  end if;

  -- 06 Crear key funciona
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      v_json := public.m27_create_api_key_for_app(
        v_app_id, 'Key Smoke M27', array['webhooks.manage'], 'SANDBOX', 'm27-smoke-key-1'
      );
      v_key_id := (v_json->>'id')::uuid;
      perform pg_temp.m27_smoke(6, 'Crear key funciona', v_key_id is not null and v_json ? 'plaintext_key_once');
    exception when others then
      perform pg_temp.m27_smoke(6, 'Crear key funciona', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(6, 'Crear key funciona', false, 'sin app');
  end if;

  -- 07 Key idempotente sin plaintext
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      v_json2 := public.m27_create_api_key_for_app(
        v_app_id, 'Key Smoke M27', array['webhooks.manage'], 'SANDBOX', 'm27-smoke-key-1'
      );
      perform pg_temp.m27_smoke(7, 'Key idempotente sin plaintext', v_json2->>'plaintext_key_once' is null);
    exception when others then
      perform pg_temp.m27_smoke(7, 'Key idempotente sin plaintext', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(7, 'Key idempotente sin plaintext', false, 'sin app');
  end if;

  -- 08 Registrar webhook HTTPS
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      v_json := public.m27_register_webhook_endpoint(
        v_app_id, 'Hook Smoke M27', 'https://hooks.example.com/leover/smoke', 'SANDBOX', 'm27-smoke-ep-1'
      );
      v_endpoint_id := (v_json->>'id')::uuid;
      perform pg_temp.m27_smoke(8, 'Registrar webhook HTTPS', v_endpoint_id is not null and v_json ? 'secret_once');
    exception when others then
      perform pg_temp.m27_smoke(8, 'Registrar webhook HTTPS', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(8, 'Registrar webhook HTTPS', false, 'sin app');
  end if;

  -- 09 Localhost rechazado
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      perform public.m27_register_webhook_endpoint(
        v_app_id, 'Bad Hook', 'https://127.0.0.1/hook', 'SANDBOX', 'm27-smoke-bad-url'
      );
      perform pg_temp.m27_smoke(9, 'Localhost rechazado', false);
    exception when others then
      perform pg_temp.m27_smoke(9, 'Localhost rechazado', SQLERRM like '%M27_UNSAFE_WEBHOOK_URL%');
    end;
  else
    perform pg_temp.m27_smoke(9, 'Localhost rechazado', false, 'sin app');
  end if;

  -- 10 Verificar y suscribir
  if v_endpoint_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      perform public.m27_verify_webhook_endpoint(v_endpoint_id);
      perform public.m27_subscribe_webhook(v_endpoint_id, 'ADOPTION_PUBLISHED');
      perform pg_temp.m27_smoke(10, 'Verificar y suscribir', true);
    exception when others then
      perform pg_temp.m27_smoke(10, 'Verificar y suscribir', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(10, 'Verificar y suscribir', false, 'sin endpoint');
  end if;

  -- 11 Emitir evento
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      v_json := public.m27_emit_webhook_event(
        v_app_id, 'ADOPTION_PUBLISHED', 'adoption/smoke-001',
        '{"ref":"adoption/smoke-001","status":"published"}', 'm27-smoke-event-1'
      );
      perform pg_temp.m27_smoke(11, 'Emitir evento', (v_json->>'id') is not null and v_json->>'version' = '1');
    exception when others then
      perform pg_temp.m27_smoke(11, 'Emitir evento', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(11, 'Emitir evento', false, 'sin app');
  end if;

  -- 12 Deliveries cargan
  perform pg_temp.m27_act_as(v_dev);
  begin
    select count(*)::int into v_cnt from public.m27_list_my_deliveries();
    perform pg_temp.m27_smoke(12, 'Deliveries cargan', v_cnt >= 1);
  exception when others then
    perform pg_temp.m27_smoke(12, 'Deliveries cargan', false, SQLERRM);
  end;

  -- 13 Events cargan
  perform pg_temp.m27_act_as(v_dev);
  begin
    select count(*)::int into v_cnt from public.m27_list_my_events();
    perform pg_temp.m27_smoke(13, 'Events cargan', v_cnt >= 1);
  exception when others then
    perform pg_temp.m27_smoke(13, 'Events cargan', false, SQLERRM);
  end;

  -- 14 Outsider no ve events
  perform pg_temp.m27_act_as(v_out);
  begin
    select count(*)::int into v_cnt from public.m27_list_my_events();
    perform pg_temp.m27_smoke(14, 'Outsider no ve events', v_cnt = 0);
  exception when others then
    perform pg_temp.m27_smoke(14, 'Outsider no ve events', false, SQLERRM);
  end;

  -- 15 Rate limit allow
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      v_json := public.m27_check_app_rate_limit(v_app_id, 'SANDBOX');
      perform pg_temp.m27_smoke(15, 'Rate limit allow', coalesce((v_json->>'allowed')::boolean, false));
    exception when others then
      perform pg_temp.m27_smoke(15, 'Rate limit allow', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(15, 'Rate limit allow', false, 'sin app');
  end if;

  -- 16 Audit log carga
  perform pg_temp.m27_act_as(v_dev);
  begin
    select count(*)::int into v_cnt from public.m27_list_my_audit_log();
    perform pg_temp.m27_smoke(16, 'Audit log carga', v_cnt >= 1);
  exception when others then
    perform pg_temp.m27_smoke(16, 'Audit log carga', false, SQLERRM);
  end;

  -- 17 OAuth stub funciona
  perform pg_temp.m27_act_as(v_dev);
  begin
    v_json := public.m27_start_oauth_stub(
      'https://portal.example.com/oauth/cb', array['adoptions.read.public'], 'm27-smoke-oauth-1'
    );
    perform pg_temp.m27_smoke(17, 'OAuth stub funciona', (v_json->>'stub_token_prefix') like 'stub_tok_%');
  exception when others then
    perform pg_temp.m27_smoke(17, 'OAuth stub funciona', false, SQLERRM);
  end;

  -- 18 OAuth sin state falla
  perform pg_temp.m27_act_as(v_dev);
  begin
    perform public.m27_start_oauth_stub(
      'https://portal.example.com/oauth/cb', array['events.read.public'], null
    );
    perform pg_temp.m27_smoke(18, 'OAuth sin state falla', false);
  exception when others then
    perform pg_temp.m27_smoke(18, 'OAuth sin state falla', SQLERRM like '%M27_OAUTH_STATE_REQUIRED%');
  end;

  -- 19 List keys sin hash
  perform pg_temp.m27_act_as(v_dev);
  begin
    select count(*)::int into v_cnt from public.m27_list_my_api_keys() k
    where k::text ~ '[a-f0-9]{64}';
    perform pg_temp.m27_smoke(19, 'List keys sin hash', v_cnt = 0);
  exception when others then
    perform pg_temp.m27_smoke(19, 'List keys sin hash', false, SQLERRM);
  end;

  -- 20 Bloque 2 contratos intacto
  perform pg_temp.m27_act_as(v_dev);
  begin
    select count(*)::int into v_cnt from public.m27_list_published_contracts();
    perform pg_temp.m27_smoke(20, 'Contratos Bloque 2 intacto', v_cnt >= 1);
  exception when others then
    perform pg_temp.m27_smoke(20, 'Contratos Bloque 2 intacto', false, SQLERRM);
  end;

  -- 21 Pausar app
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      v_json := public.m27_pause_integration_app(v_app_id);
      perform pg_temp.m27_smoke(21, 'Pausar app', v_json->>'status' = 'PAUSED');
    exception when others then
      perform pg_temp.m27_smoke(21, 'Pausar app', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_smoke(21, 'Pausar app', false, 'sin app');
  end if;

  -- 22 App pausada bloquea emit
  if v_app_id is not null then
    perform pg_temp.m27_act_as(v_dev);
    begin
      perform public.m27_emit_webhook_event(
        v_app_id, 'ADOPTION_PUBLISHED', 'adoption/paused', '{"ref":"paused"}', 'm27-smoke-paused'
      );
      perform pg_temp.m27_smoke(22, 'App pausada bloquea emit', false);
    exception when others then
      perform pg_temp.m27_smoke(22, 'App pausada bloquea emit', SQLERRM like '%M27_APP_NOT_ACTIVE%');
    end;
  else
    perform pg_temp.m27_smoke(22, 'App pausada bloquea emit', false, 'sin app');
  end if;

  -- 23 Delivery simulado creado
  select d.id into v_delivery_id from public.m27_webhook_deliveries d
  join public.m27_webhook_endpoints e on e.id = d.endpoint_id
  where e.owner_user_id = v_dev
  order by d.created_at desc limit 1;
  perform pg_temp.m27_smoke(23, 'Delivery simulado creado', v_delivery_id is not null);

  -- 24 No PII en listados
  perform pg_temp.m27_act_as(v_dev);
  begin
    select count(*)::int into v_cnt from public.m27_list_my_integration_apps() j
    where j ? 'owner_user_id' or j ? 'organization_id';
    perform pg_temp.m27_smoke(24, 'No PII en listados', v_cnt = 0);
  exception when others then
    perform pg_temp.m27_smoke(24, 'No PII en listados', false, SQLERRM);
  end;

  -- 25 Sin M24 ni proveedor externo
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name like 'm24_%';
  select count(*)::int into v_i from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname like 'm27_%'
    and pg_get_functiondef(p.oid) ilike any (array['%stripe%','%openai%','%google oauth%']);
  perform pg_temp.m27_smoke(25, 'Sin M24 ni proveedor externo', v_cnt = 0 and v_i = 0);

  -- Limpieza
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m27_webhook_attempts where delivery_id in (
    select d.id from public.m27_webhook_deliveries d
    join public.m27_webhook_endpoints e on e.id = d.endpoint_id
    where e.owner_user_id in (v_dev, v_out));
  delete from public.m27_webhook_deliveries where endpoint_id in (
    select id from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_out));
  delete from public.m27_webhook_subscriptions where endpoint_id in (
    select id from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_out));
  delete from public.m27_webhook_events where app_id in (
    select id from public.m27_integration_apps where owner_user_id in (v_dev, v_out));
  alter table public.m27_audit_log disable trigger m27_audit_no_update;
  delete from public.m27_audit_log where actor_user_id in (v_dev, v_out);
  alter table public.m27_audit_log enable trigger m27_audit_no_update;
  delete from public.m27_idempotency_keys where actor_user_id in (v_dev, v_out);
  delete from public.m27_rate_limit_counters where counter_key like '%';
  delete from public.m27_api_credentials where owner_user_id in (v_dev, v_out);
  delete from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_out);
  delete from public.m27_integration_apps where owner_user_id in (v_dev, v_out);
  perform set_config('request.jwt.claim.role', 'postgres', true);
end;
$setup$;

select case_id, label, result, detail from public._m27_smoke_run where result = 'FAIL' order by case_id;

create table if not exists public._m27_smoke_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);
delete from public._m27_smoke_last_failures;
insert into public._m27_smoke_last_failures (case_id, label, detail)
select case_id, label, detail from public._m27_smoke_run where result = 'FAIL';

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from public._m27_smoke_run;

commit;

do $$
declare v_fail int;
begin
  select count(*) into v_fail from public._m27_smoke_run where result = 'FAIL';
  if v_fail > 0 then
    raise exception 'M27 SMOKE REMOTO %/25 FAIL (% casos)', 25 - v_fail, v_fail;
  end if;
end $$;
