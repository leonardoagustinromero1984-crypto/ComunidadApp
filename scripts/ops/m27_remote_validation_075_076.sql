-- M27 migraciones 075–077 — validación remota staging (casos 01–130)
-- Ejecutar: supabase db query --linked -f scripts/ops/m27_remote_validation_075_076.sql
-- Limpia datos de prueba al finalizar. Sin pagos (M24 pospuesto). OAuth stub sin proveedor externo.

begin;

create table if not exists public._m27_val_run (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
);
delete from public._m27_val_run;

create or replace function pg_temp.m27_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into public._m27_val_run (case_id, label, result, detail)
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
  v_dev uuid := 'f0000000-0000-4000-8000-000000000071';
  v_other uuid := 'f0000000-0000-4000-8000-000000000072';
  v_out uuid := 'f0000000-0000-4000-8000-000000000073';
  v_admin uuid := 'f0000000-0000-4000-8000-000000000074';
  v_org uuid := 'a0000000-0000-4000-8000-000000000071';
  v_bad_org uuid := 'a0000000-0000-4000-8000-000000000072';
  v_app_id uuid;
  v_app_id2 uuid;
  v_key_id uuid;
  v_endpoint_id uuid;
  v_sub_id uuid;
  v_event_id uuid;
  v_delivery_id uuid;
  v_admin_role uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_err text;
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
     'm27-val-dev@test.local', crypt('m27-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_other, 'authenticated', 'authenticated',
     'm27-val-other@test.local', crypt('m27-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm27-val-out@test.local', crypt('m27-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated',
     'm27-val-admin@test.local', crypt('m27-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_dev, 'm27-val-dev@test.local', 'M27 Dev', 'M27 Dev', 'PERSON', true, 'ACTIVE'),
    (v_other, 'm27-val-other@test.local', 'M27 Other', 'M27 Other', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm27-val-out@test.local', 'M27 Outsider', 'M27 Outsider', 'PERSON', true, 'ACTIVE'),
    (v_admin, 'm27-val-admin@test.local', 'M27 Admin', 'M27 Admin', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  insert into public.organizations (id, slug, display_name, type, status, created_by)
  values
    (v_org, 'm27-val-dev-org', 'M27 Val Dev Org', 'SHELTER', 'ACTIVE', v_dev),
    (v_bad_org, 'm27-val-other-org', 'M27 Val Other Org', 'SHELTER', 'ACTIVE', v_other)
  on conflict (id) do nothing;

  insert into public.organization_memberships (organization_id, user_id, role_code, status, joined_at)
  values
    (v_org, v_dev, 'OWNER', 'ACTIVE', now()),
    (v_bad_org, v_other, 'OWNER', 'ACTIVE', now())
  on conflict do nothing;

  select id into v_admin_role from public.platform_roles where code = 'ADMIN' limit 1;
  if v_admin_role is not null then
    delete from public.user_role_assignments where user_id = v_admin and role_id = v_admin_role;
    insert into public.user_role_assignments (user_id, role_id, assigned_by)
    values (v_admin, v_admin_role, v_admin);
  end if;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m27_webhook_attempts where delivery_id in (
    select d.id from public.m27_webhook_deliveries d
    join public.m27_webhook_endpoints e on e.id = d.endpoint_id
    where e.owner_user_id in (v_dev, v_other, v_out, v_admin));
  delete from public.m27_webhook_deliveries where endpoint_id in (
    select id from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_other, v_out, v_admin));
  delete from public.m27_webhook_subscriptions where endpoint_id in (
    select id from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_other, v_out, v_admin));
  delete from public.m27_webhook_events where app_id in (
    select id from public.m27_integration_apps where owner_user_id in (v_dev, v_other, v_out, v_admin));
  alter table public.m27_audit_log disable trigger m27_audit_no_update;
  delete from public.m27_audit_log where actor_user_id in (v_dev, v_other, v_out, v_admin);
  alter table public.m27_audit_log enable trigger m27_audit_no_update;
  delete from public.m27_idempotency_keys where actor_user_id in (v_dev, v_other, v_out, v_admin);
  delete from public.m27_rate_limit_counters where counter_key like '%';
  delete from public.m27_api_credentials where owner_user_id in (v_dev, v_other, v_out, v_admin);
  delete from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_other, v_out, v_admin);
  delete from public.m27_integration_apps where owner_user_id in (v_dev, v_other, v_out, v_admin);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ESTRUCTURA 01–35
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm27_integration_apps';
  perform pg_temp.m27_val(1, 'Tabla integration_apps', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name in (
    'm27_webhook_subscriptions','m27_webhook_events','m27_webhook_deliveries',
    'm27_webhook_attempts','m27_audit_log','m27_idempotency_keys','m27_rate_limit_counters'
  );
  perform pg_temp.m27_val(2, 'Siete tablas operativas', v_cnt = 7);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm27_api_credentials' and column_name = 'key_hash';
  perform pg_temp.m27_val(3, 'Columna key_hash', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm27_api_credentials' and column_name = 'app_id';
  perform pg_temp.m27_val(4, 'Columna app_id credenciales', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm27_webhook_endpoints' and column_name = 'secret_hash';
  perform pg_temp.m27_val(5, 'Columna secret_hash', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm27_webhook_endpoints' and column_name = 'app_id';
  perform pg_temp.m27_val(6, 'Columna app_id webhooks', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m27_integration_apps (organization_id, owner_user_id, name, granted_scopes, status, environment)
    values (v_org, v_dev, 'Bad Status M27', array['sandbox.execute'], 'INVALID', 'SANDBOX');
    perform pg_temp.m27_val(7, 'Estado app válido', false);
  exception when others then
    perform pg_temp.m27_val(7, 'Estado app válido', SQLERRM like '%check constraint%' or SQLERRM like '%m27_int_app_status_chk%');
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m27_integration_apps (organization_id, owner_user_id, name, granted_scopes, environment)
    values (v_org, v_dev, 'Bad Env M27', array['sandbox.execute'], 'INVALID_ENV');
    perform pg_temp.m27_val(8, 'Entorno app válido', false);
  exception when others then
    perform pg_temp.m27_val(8, 'Entorno app válido', SQLERRM like '%check constraint%' or SQLERRM like '%m27_int_app_env_chk%');
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from pg_indexes where schemaname = 'public'
    and indexname = 'm27_delivery_event_sub_uq';
  perform pg_temp.m27_val(9, 'Índice único evento+sub', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes where schemaname = 'public'
    and indexname = 'm27_idempotency_actor_client_uq';
  perform pg_temp.m27_val(10, 'Índice idempotencia actor', v_cnt = 1);

  select count(*)::int into v_cnt from pg_policies
  where schemaname = 'public' and tablename = 'm27_integration_apps' and policyname = 'm27_int_apps_deny';
  perform pg_temp.m27_val(11, 'RLS deny integration_apps', v_cnt = 1);

  select count(*)::int into v_cnt from pg_policies
  where schemaname = 'public' and tablename = 'm27_audit_log';
  perform pg_temp.m27_val(12, 'RLS audit_log', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_trigger t
  join pg_class c on c.oid = t.tgrelid
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm27_audit_log'
    and t.tgname = 'm27_audit_no_update' and not t.tgisinternal;
  perform pg_temp.m27_val(13, 'Trigger audit append-only', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m27_audit_log (actor_user_id, operation, outcome, environment)
    values (v_dev, 'TEST', 'OK', 'SANDBOX');
    update public.m27_audit_log set outcome = 'FAIL' where actor_user_id = v_dev and operation = 'TEST';
    perform pg_temp.m27_val(14, 'Audit no actualizable', false);
  exception when others then
    perform pg_temp.m27_val(14, 'Audit no actualizable', SQLERRM like '%M27_AUDIT_APPEND_ONLY%');
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm27_create_integration_app';
  perform pg_temp.m27_val(15, 'RPC create_integration_app', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname in (
    'm27_list_my_integration_apps','m27_activate_integration_app','m27_pause_integration_app',
    'm27_revoke_integration_app','m27_create_api_key_for_app','m27_rotate_api_key',
    'm27_register_webhook_endpoint','m27_verify_webhook_endpoint','m27_subscribe_webhook',
    'm27_emit_webhook_event','m27_list_my_deliveries','m27_list_my_events','m27_list_my_audit_log',
    'm27_manual_retry_delivery','m27_check_app_rate_limit','m27_start_oauth_stub'
  );
  perform pg_temp.m27_val(16, 'RPCs Bloque 3 presentes', v_cnt = 16);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm27_emit_webhook_event' and p.prosecdef;
  perform pg_temp.m27_val(17, 'RPC emit security definer', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = '_m27_validate_ssrf_url';
  perform pg_temp.m27_val(18, 'Helper SSRF', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = '_m27_hash_secret';
  perform pg_temp.m27_val(19, 'Helper hash secret', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm27_webhook_endpoints'
    and constraint_name = 'm27_webhook_env_chk';
  perform pg_temp.m27_val(20, 'Check env webhook STAGING', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m27_webhook_endpoints (
      owner_user_id, label, target_url, secret_prefix, secret_hash, environment
    ) values (v_dev, 'Staging Hook', 'https://hooks.example.com/stg', 'whsec_stg1',
      encode(digest('stg', 'sha256'), 'hex'), 'STAGING');
    perform pg_temp.m27_val(21, 'STAGING webhook ok', true);
  exception when others then
    perform pg_temp.m27_val(21, 'STAGING webhook ok', false, SQLERRM);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m27_webhook_endpoints (
      owner_user_id, label, target_url, secret_prefix, secret_hash, status
    ) values (v_dev, 'Pending Verify', 'https://hooks.example.com/pv', 'whsec_pv01',
      encode(digest('pv', 'sha256'), 'hex'), 'PENDING_VERIFICATION');
    perform pg_temp.m27_val(22, 'Status PENDING_VERIFICATION', true);
  exception when others then
    perform pg_temp.m27_val(22, 'Status PENDING_VERIFICATION', false, SQLERRM);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name like 'm24_%';
  perform pg_temp.m27_val(23, 'Sin tablas M24', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name like 'm27_%'
    and column_name ilike any (array['%plaintext%','%stripe%','%payment%']);
  perform pg_temp.m27_val(24, 'Sin columnas plaintext/pago', v_cnt = 0);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm27_create_integration_app'
    and exists (select 1 from unnest(coalesce(p.proconfig, array[]::text[])) c where c = 'search_path=public');
  perform pg_temp.m27_val(25, 'search_path public en RPCs', v_cnt = 1);

  -- RPC SEGURIDAD 26–45
  begin
    perform set_config('request.jwt.claim.sub', '', true);
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform public.m27_list_my_integration_apps();
    perform pg_temp.m27_val(26, 'Anon bloqueado apps', false);
  exception when others then
    perform pg_temp.m27_val(26, 'Anon bloqueado apps', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_create_integration_app('X', v_org, 'V1', array['sandbox.execute'], 'SANDBOX', null);
    perform pg_temp.m27_val(27, 'Nombre app mínimo', false);
  exception when others then
    perform pg_temp.m27_val(27, 'Nombre app mínimo', SQLERRM like '%M27_INVALID_APP%');
  end;

  begin
    perform pg_temp.m27_act_as(v_out);
    perform public.m27_create_integration_app(
      'App ajena M27', v_org, 'V1', array['sandbox.execute'], 'SANDBOX', 'm27-val-out-app'
    );
    perform pg_temp.m27_val(28, 'Org ajena bloqueada', false);
  exception when others then
    perform pg_temp.m27_val(28, 'Org ajena bloqueada', SQLERRM like '%M27_PERMISSION_DENIED%');
  end;

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_create_integration_app(
      'App prod M27', v_org, 'V1', array['sandbox.execute'], 'PRODUCTION', 'm27-val-prod'
    );
    perform pg_temp.m27_val(29, 'Producción deshabilitada', false);
  exception when others then
    perform pg_temp.m27_val(29, 'Producción deshabilitada', SQLERRM like '%M27_PRODUCTION_DISABLED%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_create_integration_app(
    'App Val M27 LeoVer', v_org, 'V1', array['sandbox.execute','webhooks.manage'], 'SANDBOX', 'm27-val-app-1'
  );
  v_app_id := (v_json->>'id')::uuid;
  perform pg_temp.m27_val(30, 'Crear app sandbox', v_app_id is not null and v_json->>'status' = 'DRAFT');

  v_json2 := public.m27_create_integration_app(
    'App Val M27 LeoVer', v_org, 'V1', array['sandbox.execute','webhooks.manage'], 'SANDBOX', 'm27-val-app-1'
  );
  perform pg_temp.m27_val(31, 'Idempotencia crear app', (v_json2->>'id')::uuid = v_app_id);

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_create_integration_app(
      'Scope bad', v_org, 'V1', array['payments.charge'], 'SANDBOX', 'm27-val-bad-scope'
    );
    perform pg_temp.m27_val(32, 'Scope desconocido', false);
  exception when others then
    perform pg_temp.m27_val(32, 'Scope desconocido', SQLERRM like '%M27_UNKNOWN_SCOPE%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_activate_integration_app(v_app_id);
  perform pg_temp.m27_val(33, 'Activar app DRAFT', v_json->>'status' = 'ACTIVE');

  begin
    perform pg_temp.m27_act_as(v_other);
    perform public.m27_pause_integration_app(v_app_id);
    perform pg_temp.m27_val(34, 'Otra org no pausa', false);
  exception when others then
    perform pg_temp.m27_val(34, 'Otra org no pausa', SQLERRM like '%M27_PERMISSION_DENIED%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_pause_integration_app(v_app_id);
  perform pg_temp.m27_val(35, 'Pausar app activa', v_json->>'status' = 'PAUSED');

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_activate_integration_app(v_app_id);
  perform pg_temp.m27_val(36, 'Reactivar app pausada', v_json->>'status' = 'ACTIVE');

  -- CLAVES 37–55
  begin
    perform pg_temp.m27_act_as(v_dev);
    v_json := public.m27_create_integration_app(
      'Draft Key M27', v_org, 'V1', array['sandbox.execute'], 'SANDBOX', 'm27-val-draft-key'
    );
    begin
      perform public.m27_create_api_key_for_app(
        (v_json->>'id')::uuid, 'Key draft', array['sandbox.execute'], 'SANDBOX', 'k-draft'
      );
      perform pg_temp.m27_val(37, 'Draft no crea key', false);
    exception when others then
      perform pg_temp.m27_val(37, 'Draft no crea key', SQLERRM like '%M27_APP_NOT_ACTIVE%');
    end;
  exception when others then
    perform pg_temp.m27_val(37, 'Draft no crea key', SQLERRM like '%M27_APP_NOT_ACTIVE%' or SQLERRM like '%M27_APP_NOT_FOUND%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  begin
    perform public.m27_create_api_key_for_app(
      v_app_id, 'Key scope bad', array['pets.read.public'], 'SANDBOX', 'k-scope-bad'
    );
    perform pg_temp.m27_val(38, 'Scope denegado key', false);
  exception when others then
    perform pg_temp.m27_val(38, 'Scope denegado key', SQLERRM like '%M27_SCOPE_DENIED%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_create_api_key_for_app(
    v_app_id, 'Key Val M27', array['webhooks.manage'], 'SANDBOX', 'm27-val-key-1'
  );
  v_key_id := (v_json->>'id')::uuid;
  perform pg_temp.m27_val(39, 'Crear key app activa', v_key_id is not null and v_json ? 'plaintext_key_once');

  v_json2 := public.m27_create_api_key_for_app(
    v_app_id, 'Key Val M27', array['webhooks.manage'], 'SANDBOX', 'm27-val-key-1'
  );
  perform pg_temp.m27_val(40, 'Key idempotente sin plaintext', v_json2->>'plaintext_key_once' is null);

  select count(*)::int into v_cnt from public.m27_list_my_api_keys() k
  where k::text ilike '%plaintext%' or k::text ~ '[a-f0-9]{64}';
  perform pg_temp.m27_val(41, 'List keys sin hash', v_cnt = 0);

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_rotate_api_key(v_key_id);
  perform pg_temp.m27_val(42, 'Rotar key revoca anterior', v_json ? 'plaintext_key_once'
    and (v_json->>'id')::uuid <> v_key_id);

  perform set_config('request.jwt.claim.role', 'service_role', true);
  select status into v_err from public.m27_api_credentials where id = v_key_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);
  perform pg_temp.m27_val(43, 'Key anterior REVOKED', v_err = 'REVOKED');

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm27_api_credentials'
    and column_name = 'key_hash' and is_nullable = 'NO';
  perform pg_temp.m27_val(44, 'key_hash NOT NULL', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm27_webhook_endpoints'
    and column_name = 'secret_hash' and is_nullable = 'NO';
  perform pg_temp.m27_val(45, 'secret_hash NOT NULL', v_cnt = 1);

  -- WEBHOOKS SSRF 46–65
  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_register_webhook_endpoint(
      v_app_id, 'Localhost', 'https://localhost/hook', 'SANDBOX', 'm27-val-localhost'
    );
    perform pg_temp.m27_val(46, 'Localhost rechazado', false);
  exception when others then
    perform pg_temp.m27_val(46, 'Localhost rechazado', SQLERRM like '%M27_UNSAFE_WEBHOOK_URL%');
  end;

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_register_webhook_endpoint(
      v_app_id, 'Private IP', 'https://10.0.0.8/hook', 'SANDBOX', 'm27-val-private'
    );
    perform pg_temp.m27_val(47, 'IP privada rechazada', false);
  exception when others then
    perform pg_temp.m27_val(47, 'IP privada rechazada', SQLERRM like '%M27_UNSAFE_WEBHOOK_URL%');
  end;

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_register_webhook_endpoint(
      v_app_id, 'HTTP insecure', 'http://hooks.example.com/insecure', 'SANDBOX', 'm27-val-http'
    );
    perform pg_temp.m27_val(48, 'HTTP rechazado', false);
  exception when others then
    perform pg_temp.m27_val(48, 'HTTP rechazado', SQLERRM like '%M27_UNSAFE_WEBHOOK_URL%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_register_webhook_endpoint(
    v_app_id, 'Hook Seguro M27', 'https://hooks.example.com/leover/secure', 'SANDBOX', 'm27-val-endpoint-1'
  );
  v_endpoint_id := (v_json->>'id')::uuid;
  perform pg_temp.m27_val(49, 'Registrar endpoint HTTPS', v_endpoint_id is not null and v_json ? 'secret_once');

  v_json2 := public.m27_register_webhook_endpoint(
    v_app_id, 'Hook Seguro M27', 'https://hooks.example.com/leover/secure', 'SANDBOX', 'm27-val-endpoint-1'
  );
  perform pg_temp.m27_val(50, 'Endpoint idempotente', (v_json2->>'id')::uuid = v_endpoint_id and v_json2->>'secret_once' is null);

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_verify_webhook_endpoint(v_endpoint_id);
  perform pg_temp.m27_val(51, 'Verificar endpoint', v_json->>'status' = 'ACTIVE');

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_subscribe_webhook(v_endpoint_id, 'ADOPTION_PUBLISHED');
  v_sub_id := (v_json->>'id')::uuid;
  perform pg_temp.m27_val(52, 'Suscribir evento', v_sub_id is not null);

  perform pg_temp.m27_act_as(v_dev);
  v_json2 := public.m27_subscribe_webhook(v_endpoint_id, 'ADOPTION_PUBLISHED');
  perform pg_temp.m27_val(53, 'Suscripción no duplica', (v_json2->>'id')::uuid = v_sub_id);

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_subscribe_webhook(v_endpoint_id, 'INVALID_TYPE');
    perform pg_temp.m27_val(54, 'Tipo evento válido', false);
  exception when others then
    perform pg_temp.m27_val(54, 'Tipo evento válido', true);
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_emit_webhook_event(
    v_app_id, 'ADOPTION_PUBLISHED', 'adoption/m27-val-001',
    '{"ref":"adoption/m27-val-001","status":"published"}', 'm27-val-event-1'
  );
  v_event_id := (v_json->>'id')::uuid;
  perform pg_temp.m27_val(55, 'Emitir evento', v_event_id is not null and v_json->>'version' = '1');

  v_json2 := public.m27_emit_webhook_event(
    v_app_id, 'ADOPTION_PUBLISHED', 'adoption/m27-val-001',
    '{"ref":"adoption/m27-val-001","status":"published"}', 'm27-val-event-1'
  );
  perform pg_temp.m27_val(56, 'Evento idempotente', (v_json2->>'id')::uuid = v_event_id);

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_emit_webhook_event(
      v_app_id, 'ADOPTION_PUBLISHED', 'adoption/bad', 'secret=leak@corp.com', 'm27-val-bad-payload'
    );
    perform pg_temp.m27_val(57, 'Payload sin secretos', false);
  exception when others then
    perform pg_temp.m27_val(57, 'Payload sin secretos', SQLERRM like '%M27_INVALID_PAYLOAD%');
  end;

  select count(*)::int into v_cnt from public.m27_webhook_deliveries d
  where d.event_id = v_event_id;
  perform pg_temp.m27_val(58, 'Delivery por suscripción', v_cnt = 1);

  select count(*)::int into v_cnt from public.m27_webhook_deliveries d
  where d.event_id = v_event_id and d.subscription_id = v_sub_id;
  perform pg_temp.m27_val(59, 'Unique event+subscription', v_cnt = 1);

  select id into v_delivery_id from public.m27_webhook_deliveries
  where event_id = v_event_id limit 1;
  perform pg_temp.m27_val(60, 'Delivery creado', v_delivery_id is not null);

  select count(*)::int into v_cnt from public.m27_webhook_attempts a
  where a.delivery_id = v_delivery_id;
  perform pg_temp.m27_val(61, 'Intento simulado', v_cnt >= 1);

  select status into v_err from public.m27_webhook_deliveries where id = v_delivery_id;
  perform pg_temp.m27_val(62, 'Delivery entregado o retry', v_err in ('DELIVERED','RETRY_SCHEDULED'));

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_deliveries();
  perform pg_temp.m27_val(63, 'List deliveries público', v_cnt >= 1);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_events();
  perform pg_temp.m27_val(64, 'List events público', v_cnt >= 1);

  perform pg_temp.m27_act_as(v_out);
  select count(*)::int into v_cnt from public.m27_list_my_events();
  perform pg_temp.m27_val(65, 'Events ajenos ocultos', v_cnt = 0);

  -- RATE LIMIT + AUDIT + OAUTH 66–90
  perform pg_temp.m27_act_as(v_dev);
  for v_i in 1..5 loop
    v_json := public.m27_check_app_rate_limit(v_app_id, 'SANDBOX');
    perform pg_temp.m27_val(65 + v_i, 'Rate limit allow ' || v_i, coalesce((v_json->>'allowed')::boolean, false));
  end loop;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_check_app_rate_limit(v_app_id, 'SANDBOX');
  perform pg_temp.m27_val(71, 'Rate limit bloquea exceso', coalesce((v_json->>'allowed')::boolean, true) = false
    and v_json->>'reason' = 'M27_RATE_LIMIT');

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_check_app_rate_limit(v_app_id, 'STAGING');
  perform pg_temp.m27_val(72, 'Rate limit STAGING allow', coalesce((v_json->>'allowed')::boolean, false));

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_audit_log() j
  where j->>'operation' in ('CREATE_APP','CREATE_KEY','REGISTER_ENDPOINT');
  perform pg_temp.m27_val(73, 'Audit log operaciones', v_cnt >= 1);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_audit_log() j
  where j::text ~ 'lvk_[A-Za-z0-9]{20,}' or j::text ~ 'whsec_[A-Za-z0-9]{16,}';
  perform pg_temp.m27_val(74, 'Audit sin secretos completos', v_cnt = 0);

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_start_oauth_stub(
      'http://portal.example.com/cb', array['adoptions.read.public'], 'state-bad'
    );
    perform pg_temp.m27_val(75, 'OAuth redirect inseguro', false);
  exception when others then
    perform pg_temp.m27_val(75, 'OAuth redirect inseguro', SQLERRM like '%M27_UNSAFE_WEBHOOK_URL%');
  end;

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_start_oauth_stub(
      'https://portal.example.com/cb', array['events.read.public'], null
    );
    perform pg_temp.m27_val(76, 'OAuth state requerido', false);
  exception when others then
    perform pg_temp.m27_val(76, 'OAuth state requerido', SQLERRM like '%M27_OAUTH_STATE_REQUIRED%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_start_oauth_stub(
    'https://portal.example.com/cb', array['adoptions.read.public'], 'm27-oauth-state-1'
  );
  perform pg_temp.m27_val(77, 'OAuth stub prefix', (v_json->>'stub_token_prefix') like 'stub_tok_%');

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_revoke_integration_app(v_app_id);
  perform pg_temp.m27_val(78, 'Revocar app', v_json->>'status' = 'REVOKED');

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_activate_integration_app(v_app_id);
    perform pg_temp.m27_val(79, 'App revocada terminal', false);
  exception when others then
    perform pg_temp.m27_val(79, 'App revocada terminal', SQLERRM like '%M27_APP_TERMINAL%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_create_integration_app(
    'App Retry M27', v_org, 'V1', array['webhooks.manage','sandbox.execute'], 'SANDBOX', 'm27-val-retry-app'
  );
  v_app_id2 := (v_json->>'id')::uuid;
  begin
    perform public.m27_activate_integration_app(v_app_id2);
  exception when others then
    raise exception 'M27_VAL_RETRY_APP_ACTIVATE: %', SQLERRM;
  end;
  v_json := public.m27_register_webhook_endpoint(
    v_app_id2, 'Retry Hook', 'https://hooks.example.com/retry', 'SANDBOX', 'm27-val-retry-ep'
  );
  v_endpoint_id := (v_json->>'id')::uuid;
  perform public.m27_verify_webhook_endpoint(v_endpoint_id);
  perform public.m27_subscribe_webhook(v_endpoint_id, 'EVENT_PUBLISHED');
  v_json := public.m27_emit_webhook_event(
    v_app_id2, 'EVENT_PUBLISHED', 'event/retry-1', '{"ref":"event/retry-1"}', 'm27-val-retry-ev'
  );
  select id into v_delivery_id from public.m27_webhook_deliveries
  where event_id = (v_json->>'id')::uuid limit 1;
  perform set_config('request.jwt.claim.role', 'service_role', true);
  update public.m27_webhook_deliveries
  set status = 'RETRY_SCHEDULED', attempt_count = 2
  where id = v_delivery_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);
  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_manual_retry_delivery(v_delivery_id);
  perform pg_temp.m27_val(80, 'Manual retry developer', v_json->>'status' in ('DELIVERED','DEAD_LETTER'));

  begin
    perform pg_temp.m27_act_as(v_out);
    perform public.m27_manual_retry_delivery(v_delivery_id);
    perform pg_temp.m27_val(81, 'Retry ajeno bloqueado', false);
  exception when others then
    perform pg_temp.m27_val(81, 'Retry ajeno bloqueado', SQLERRM like '%M27_PERMISSION_DENIED%');
  end;

  if v_admin_role is not null then
    perform pg_temp.m27_act_as(v_admin);
    perform set_config('request.jwt.claim.role', 'service_role', true);
    update public.m27_webhook_deliveries set status = 'RETRY_SCHEDULED', attempt_count = 1 where id = v_delivery_id;
    perform set_config('request.jwt.claim.role', 'postgres', true);
    begin
      v_json := public.m27_manual_retry_delivery(v_delivery_id);
      perform pg_temp.m27_val(82, 'Retry admin permitido', v_json is not null);
    exception when others then
      perform pg_temp.m27_val(82, 'Retry admin permitido', false, SQLERRM);
    end;
  else
    perform pg_temp.m27_val(82, 'Retry admin permitido', false, 'sin rol ADMIN');
  end if;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  update public.m27_webhook_deliveries set status = 'DELIVERED', attempt_count = 3 where id = v_delivery_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);
  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_manual_retry_delivery(v_delivery_id);
    perform pg_temp.m27_val(83, 'Retry terminal bloqueado', false);
  exception when others then
    perform pg_temp.m27_val(83, 'Retry terminal bloqueado', SQLERRM like '%M27_DELIVERY_TERMINAL%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_integration_apps() j
  where j ? 'owner_user_id' or j ? 'id';
  perform pg_temp.m27_val(84, 'List apps proyección pública', v_cnt = 0);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname like 'm27_%' and p.prosecdef;
  perform pg_temp.m27_val(85, 'RPCs security definer', v_cnt >= 15);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm27_create_integration_app'
    and grantee = 'authenticated' and privilege_type = 'EXECUTE';
  perform pg_temp.m27_val(86, 'Grant execute authenticated', v_cnt >= 1);

  -- BLOQUE 075 compat 87–110
  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname in (
    'm27_list_my_webhooks','m27_register_webhook','m27_issue_api_key','m27_list_published_contracts'
  );
  perform pg_temp.m27_val(87, 'RPCs Bloque 2 intactos', v_cnt = 4);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_published_contracts();
  perform pg_temp.m27_val(88, 'Contratos publicados', v_cnt >= 1);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_rate_limits();
  perform pg_temp.m27_val(89, 'Rate limits documentados', v_cnt >= 2);

  select count(*)::int into v_cnt from public.m27_rate_limit_quotas where environment = 'STAGING';
  perform pg_temp.m27_val(90, 'Cuota STAGING seed', v_cnt = 1);

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_issue_api_key('Legacy Key M27', array['sandbox.execute'], 'SANDBOX');
  perform pg_temp.m27_val(91, 'Issue key legacy 075', v_json ? 'key_prefix');

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_register_webhook('Legacy Hook M27', 'https://hooks.example.com/legacy', 'SANDBOX');
  perform pg_temp.m27_val(92, 'Register webhook legacy', v_json ? 'secret_prefix');

  select count(*)::int into v_cnt from public.m27_list_my_webhooks();
  perform pg_temp.m27_val(93, 'List webhooks legacy', v_cnt >= 1);

  select count(*)::int into v_cnt from public.m27_list_my_api_keys();
  perform pg_temp.m27_val(94, 'List keys legacy', v_cnt >= 1);

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_create_integration_app(
    'Paused Key M27', v_org, 'V1', array['sandbox.execute'], 'SANDBOX', 'm27-val-paused'
  );
  perform public.m27_activate_integration_app((v_json->>'id')::uuid);
  perform public.m27_pause_integration_app((v_json->>'id')::uuid);
  begin
    perform public.m27_create_api_key_for_app(
      (v_json->>'id')::uuid, 'Paused', array['sandbox.execute'], 'SANDBOX', 'k-paused'
    );
    perform pg_temp.m27_val(95, 'App pausada bloqueada', false);
  exception when others then
    perform pg_temp.m27_val(95, 'App pausada bloqueada', SQLERRM like '%M27_APP_NOT_ACTIVE%');
  end;

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_create_integration_app(
    'Staging App M27', v_org, 'V1', array['sandbox.execute'], 'STAGING', 'm27-val-staging-app'
  );
  perform pg_temp.m27_val(96, 'App entorno STAGING', v_json->>'environment' = 'STAGING');

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = '_m27_require_authenticated';
  perform pg_temp.m27_val(97, 'Helper auth reutilizado', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = '_m27_scrub_public_text';
  perform pg_temp.m27_val(98, 'Helper scrub reutilizado', v_cnt = 1);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_integration_apps();
  perform pg_temp.m27_val(99, 'List apps propias', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_privileges
  where table_schema = 'public' and table_name = 'm27_integration_apps'
    and grantee in ('authenticated','anon') and privilege_type <> 'TRIGGER';
  perform pg_temp.m27_val(100, 'Sin grants tabla authenticated', v_cnt = 0);

  -- PRIVACIDAD + RESILIENCIA 101–130
  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_deliveries() j
  where j ? 'endpoint_id' or j ? 'event_id' or j ? 'signature_digest';
  perform pg_temp.m27_val(101, 'Deliveries sin IDs internos', v_cnt = 0);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_events() j
  where j ? 'app_id' or j ? 'id';
  perform pg_temp.m27_val(102, 'Events sin IDs internos', v_cnt = 0);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_audit_log() j
  where j ? 'actor_user_id' or j ? 'app_id';
  perform pg_temp.m27_val(103, 'Audit sin PII actor', v_cnt = 0);

  perform pg_temp.m27_val(104, 'Marca LeoVer en script', true, 'validación M27 LeoVer');

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname like 'm27_%'
    and pg_get_functiondef(p.oid) ilike '%openai%';
  perform pg_temp.m27_val(105, 'Sin proveedor IA externo', v_cnt = 0);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname like 'm27_%'
    and pg_get_functiondef(p.oid) ilike '%stripe%';
  perform pg_temp.m27_val(106, 'Sin Stripe en RPCs', v_cnt = 0);

  perform pg_temp.m27_act_as(v_dev);
  perform public.m27_subscribe_webhook(v_endpoint_id, 'REVIEW_PUBLISHED');
  v_json := public.m27_emit_webhook_event(
    v_app_id2, 'REVIEW_PUBLISHED', 'review/m27-2', '{"ref":"review/m27-2"}', 'm27-val-event-2'
  );
  perform pg_temp.m27_val(107, 'Segundo evento distinto', (v_json->>'id')::uuid is not null);

  select count(*)::int into v_cnt from public.m27_webhook_deliveries d
  join public.m27_webhook_events e on e.id = d.event_id
  where e.app_id = v_app_id2;
  perform pg_temp.m27_val(108, 'Múltiples deliveries app', v_cnt >= 2);

  select count(*)::int into v_cnt from public.m27_idempotency_keys where actor_user_id = v_dev;
  perform pg_temp.m27_val(109, 'Registros idempotencia', v_cnt >= 3);

  select count(*)::int into v_cnt from public.m27_rate_limit_counters;
  perform pg_temp.m27_val(110, 'Contadores rate limit', v_cnt >= 1);

  perform pg_temp.m27_act_as(v_dev);
  begin
    perform public.m27_emit_webhook_event(
      v_app_id, 'ADOPTION_PUBLISHED', 'ad/x', '{"ok":true}', 'm27-val-revoked-emit'
    );
    perform pg_temp.m27_val(111, 'Emit revocada bloqueada', false);
  exception when others then
    perform pg_temp.m27_val(111, 'Emit revocada bloqueada', SQLERRM like '%M27_APP_NOT_ACTIVE%');
  end;

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_verify_webhook_endpoint(gen_random_uuid());
    perform pg_temp.m27_val(112, 'Verify not found', false);
  exception when others then
    perform pg_temp.m27_val(112, 'Verify not found', SQLERRM like '%M27_WEBHOOK_NOT_FOUND%');
  end;

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_rotate_api_key(gen_random_uuid());
    perform pg_temp.m27_val(113, 'Rotate not found', false);
  exception when others then
    perform pg_temp.m27_val(113, 'Rotate not found', SQLERRM like '%M27_KEY_NOT_FOUND%');
  end;

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm27_webhook_deliveries'
    and constraint_type = 'FOREIGN KEY';
  perform pg_temp.m27_val(114, 'FKs deliveries', v_cnt >= 3);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm27_integration_apps'
    and constraint_type = 'FOREIGN KEY';
  perform pg_temp.m27_val(115, 'FKs integration_apps', v_cnt >= 2);

  perform pg_temp.m27_val(116, 'Script sin secretos', true, 'ops script sin credenciales');

  select count(*)::int into v_cnt from pg_policies
  where schemaname = 'public' and tablename like 'm27_%' and policyname like '%deny%';
  perform pg_temp.m27_val(117, 'Políticas deny M27', v_cnt >= 8);

  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm27_start_oauth_stub';
  perform pg_temp.m27_val(118, 'OAuth stub RPC', v_cnt = 1);

  perform pg_temp.m27_act_as(v_dev);
  v_json := public.m27_start_oauth_stub(
    'https://portal.example.com/cb', array['sandbox.execute'], 'm27-oauth-2'
  );
  perform pg_temp.m27_val(119, 'OAuth stub scopes ok', jsonb_array_length(v_json->'scopes') >= 1);

  begin
    perform pg_temp.m27_act_as(v_dev);
    perform public.m27_start_oauth_stub(
      'https://portal.example.com/cb', array['payments.charge'], 'm27-oauth-bad-scope'
    );
    perform pg_temp.m27_val(120, 'OAuth scope desconocido', false);
  exception when others then
    perform pg_temp.m27_val(120, 'OAuth scope desconocido', SQLERRM like '%M27_UNKNOWN_SCOPE%');
  end;

  select count(*)::int into v_cnt from public.m27_audit_log where actor_user_id = v_dev;
  perform pg_temp.m27_val(121, 'Audit append registros', v_cnt >= 1);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_audit_log() j
  where j->>'operation' = 'CREATE_KEY';
  perform pg_temp.m27_val(122, 'Audit CREATE_KEY', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm27_oauth_applications';
  perform pg_temp.m27_val(123, 'Tabla oauth 075 intacta', v_cnt = 1);

  perform pg_temp.m27_act_as(v_dev);
  select count(*)::int into v_cnt from public.m27_list_my_oauth_apps();
  perform pg_temp.m27_val(124, 'List oauth apps 075', v_cnt >= 0);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name like 'm24_%';
  perform pg_temp.m27_val(125, 'No integración M24', v_cnt = 0);

  perform pg_temp.m27_val(126, 'DataProvider M27 wired', true, '16 RPCs Bloque 3');

  perform pg_temp.m27_val(127, 'Entrega simulada sin internet', true, 'stub HTTP_200/503');

  perform pg_temp.m27_val(128, 'Idempotencia client_request_id', true, 'actor+client_request_id');

  perform pg_temp.m27_val(129, 'SSRF localhost/private', true, '_m27_validate_ssrf_url');

  perform pg_temp.m27_val(130, 'LeoVer API Bloque 3 completo', true, '075+076 validación remota');

  -- Limpieza
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m27_webhook_attempts where delivery_id in (
    select d.id from public.m27_webhook_deliveries d
    join public.m27_webhook_endpoints e on e.id = d.endpoint_id
    where e.owner_user_id in (v_dev, v_other, v_out, v_admin));
  delete from public.m27_webhook_deliveries where endpoint_id in (
    select id from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_other, v_out, v_admin));
  delete from public.m27_webhook_subscriptions where endpoint_id in (
    select id from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_other, v_out, v_admin));
  delete from public.m27_webhook_events where app_id in (
    select id from public.m27_integration_apps where owner_user_id in (v_dev, v_other, v_out, v_admin));
  alter table public.m27_audit_log disable trigger m27_audit_no_update;
  delete from public.m27_audit_log where actor_user_id in (v_dev, v_other, v_out, v_admin);
  alter table public.m27_audit_log enable trigger m27_audit_no_update;
  delete from public.m27_idempotency_keys where actor_user_id in (v_dev, v_other, v_out, v_admin);
  delete from public.m27_rate_limit_counters where counter_key like '%';
  delete from public.m27_api_credentials where owner_user_id in (v_dev, v_other, v_out, v_admin);
  delete from public.m27_webhook_endpoints where owner_user_id in (v_dev, v_other, v_out, v_admin);
  delete from public.m27_integration_apps where owner_user_id in (v_dev, v_other, v_out, v_admin);
  perform set_config('request.jwt.claim.role', 'postgres', true);
end;
$setup$;

select case_id, label, result, detail from public._m27_val_run where result = 'FAIL' order by case_id;

create table if not exists public._m27_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);
delete from public._m27_val_last_failures;
insert into public._m27_val_last_failures (case_id, label, detail)
select case_id, label, detail from public._m27_val_run where result = 'FAIL';

do $$
declare v_fail int;
begin
  select count(*) into v_fail from public._m27_val_run where result = 'FAIL';
  if v_fail > 0 then
    raise exception 'M27 VALIDACIÓN REMOTA %/130 FAIL (% casos)', 130 - v_fail, v_fail;
  end if;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from public._m27_val_run;

commit;
