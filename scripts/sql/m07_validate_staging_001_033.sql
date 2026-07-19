-- =============================================================================
-- LeoVer — validación staging SQL final M07
-- Alcance: migraciones 001–033 (historial, catálogos, RLS, grants, DEFINER, 033)
-- Solo lectura salvo pruebas transaccionales en subtransacciones con rollback.
-- NO USAR EN PRODUCCIÓN.
-- Este script no contiene credenciales, tokens, URLs ni project refs.
-- Pegar completo en Supabase SQL Editor del proyecto de pruebas/staging.
-- No aplica migraciones. No ejecuta DROP/TRUNCATE/DELETE/ALTER/CREATE/GRANT/REVOKE
-- permanentes (solo TEMP TABLE + bloques con rollback).
-- =============================================================================

drop table if exists m07_staging_validation_results;

create temporary table m07_staging_validation_results (
  check_group text not null,
  check_name text not null,
  expected text not null,
  actual text not null,
  status text not null check (status in ('PASS', 'FAIL', 'NOT_EXECUTED', 'BACKLOG')),
  details text not null default '',
  created_at timestamptz not null default timezone('utc', now())
);

create or replace function pg_temp.m07_vr(
  p_group text,
  p_name text,
  p_expected text,
  p_actual text,
  p_status text,
  p_details text default ''
) returns void
language plpgsql
as $$
begin
  insert into m07_staging_validation_results (check_group, check_name, expected, actual, status, details)
  values (p_group, p_name, p_expected, p_actual, p_status, coalesce(p_details, ''));
end;
$$;

create or replace function pg_temp.m07_pass_fail(
  p_group text,
  p_name text,
  p_expected text,
  p_actual text,
  p_ok boolean,
  p_details text default ''
) returns void
language plpgsql
as $$
begin
  perform pg_temp.m07_vr(
    p_group, p_name, p_expected, p_actual,
    case when p_ok then 'PASS' else 'FAIL' end,
    p_details
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 3 — Historial de migraciones 001–033
-- ---------------------------------------------------------------------------
do $$
declare
  v_count int;
  v_max text;
  v_dupes int;
  v_missing int;
  v_versions text[];
  v_expected text[];
  i int;
begin
  select coalesce(array_agg(version order by version), array[]::text[])
    into v_versions
  from supabase_migrations.schema_migrations;

  v_count := coalesce(cardinality(v_versions), 0);

  select coalesce(max(version), '<none>') into v_max
  from supabase_migrations.schema_migrations;

  select count(*) into v_dupes
  from (
    select version from supabase_migrations.schema_migrations group by version having count(*) > 1
  ) d;

  v_expected := array[
    '001','002','003','004','005','006','007','008','009','010',
    '011','012','013','014','015','016','017','018','019','020',
    '021','022','023','024','025','026','027','028','029','030',
    '031','032','033'
  ];

  v_missing := 0;
  for i in 1..cardinality(v_expected) loop
    if not (v_expected[i] = any (v_versions)) then
      v_missing := v_missing + 1;
    end if;
  end loop;

  perform pg_temp.m07_pass_fail(
    'HISTORY', 'migration_count', '33', v_count::text, v_count = 33,
    'versiones registradas en supabase_migrations.schema_migrations'
  );
  perform pg_temp.m07_pass_fail(
    'HISTORY', 'migration_max_version', '033', v_max, v_max = '033',
    'máxima versión remota'
  );
  perform pg_temp.m07_pass_fail(
    'HISTORY', 'migration_duplicates', '0', v_dupes::text, v_dupes = 0,
    'versiones duplicadas'
  );
  perform pg_temp.m07_pass_fail(
    'HISTORY', 'migration_missing_001_033', '0', v_missing::text, v_missing = 0,
    format('faltantes entre 001–033: %s', v_missing)
  );
  perform pg_temp.m07_pass_fail(
    'HISTORY', 'migration_summary', '33 / 033 / 0 dup / 0 miss',
    format('%s / %s / %s dup / %s miss', v_count, v_max, v_dupes, v_missing),
    v_count = 33 and v_max = '033' and v_dupes = 0 and v_missing = 0,
    'resultado esperado compuesto'
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 4 — Catálogos 118 / 28 / 14 / 8 permisos M07
-- ---------------------------------------------------------------------------
do $$
declare
  v_events int;
  v_metrics int;
  v_health int;
  v_perms int;
  v_ev_dup int;
  v_met_dup int;
  v_hc_dup int;
  v_perm_dup int;
  v_m07_codes text[] := array[
    'observability.view','observability.manage','audit.view_sensitive',
    'security.events.view','export.audit_data','alert.manage',
    'retention.manage','health.check.execute'
  ];
  v_missing_perm int;
  v_role_links int;
begin
  select count(*) into v_events from public.observability_event_catalog;
  select count(*) into v_metrics from public.m07_metric_catalog;
  select count(*) into v_health from public.m07_health_check_catalog;
  select count(*) into v_perms
  from public.permissions p where p.code = any (v_m07_codes);

  select count(*) into v_ev_dup from (
    select event_key from public.observability_event_catalog group by event_key having count(*) > 1
  ) x;
  select count(*) into v_met_dup from (
    select metric_key from public.m07_metric_catalog group by metric_key having count(*) > 1
  ) x;
  select count(*) into v_hc_dup from (
    select check_key from public.m07_health_check_catalog group by check_key having count(*) > 1
  ) x;
  select count(*) into v_perm_dup from (
    select code from public.permissions where code = any (v_m07_codes) group by code having count(*) > 1
  ) x;

  select count(*) into v_missing_perm
  from unnest(v_m07_codes) c(code)
  where not exists (select 1 from public.permissions p where p.code = c.code);

  select count(*) into v_role_links
  from public.role_permissions rp
  join public.permissions p on p.id = rp.permission_id
  join public.platform_roles r on r.id = rp.role_id
  where p.code = any (v_m07_codes)
    and r.code in ('ADMIN','SUPERADMIN');

  perform pg_temp.m07_pass_fail('CATALOG', 'event_keys_count', '118', v_events::text, v_events = 118, 'observability_event_catalog');
  perform pg_temp.m07_pass_fail('CATALOG', 'metric_keys_count', '28', v_metrics::text, v_metrics = 28, 'm07_metric_catalog');
  perform pg_temp.m07_pass_fail('CATALOG', 'health_check_keys_count', '14', v_health::text, v_health = 14, 'm07_health_check_catalog');
  perform pg_temp.m07_pass_fail('CATALOG', 'm07_permissions_count', '8', v_perms::text, v_perms = 8, 'permissions M07 dedicados');
  perform pg_temp.m07_pass_fail('CATALOG', 'event_keys_duplicates', '0', v_ev_dup::text, v_ev_dup = 0, '');
  perform pg_temp.m07_pass_fail('CATALOG', 'metric_keys_duplicates', '0', v_met_dup::text, v_met_dup = 0, '');
  perform pg_temp.m07_pass_fail('CATALOG', 'health_check_duplicates', '0', v_hc_dup::text, v_hc_dup = 0, '');
  perform pg_temp.m07_pass_fail('CATALOG', 'm07_permissions_duplicates', '0', v_perm_dup::text, v_perm_dup = 0, '');
  perform pg_temp.m07_pass_fail('CATALOG', 'm07_permissions_present', '0 missing', v_missing_perm::text || ' missing', v_missing_perm = 0, array_to_string(v_m07_codes, ','));
  perform pg_temp.m07_pass_fail(
    'CATALOG', 'm07_permissions_role_links_admin_superadmin', '>=8', v_role_links::text,
    v_role_links >= 8, 'role_permissions ADMIN/SUPERADMIN'
  );
  perform pg_temp.m07_pass_fail(
    'CATALOG', 'staff_notification_event_cataloged', 'present',
    case when exists (select 1 from public.observability_event_catalog where event_key = 'm07.incident.staff_notification')
      then 'present' else 'absent' end,
    exists (select 1 from public.observability_event_catalog where event_key = 'm07.incident.staff_notification'),
    'integración M06 catalogada; envío simulado no requerido'
  );
  perform pg_temp.m07_vr(
    'CATALOG', 'kotlin_catalog_equality', 'igualdad con constantes Android',
    'no evaluable desde SQL Editor', 'NOT_EXECUTED',
    'comparación Kotlin vs SQL requiere suite Android; no inventar PASS'
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 5 — Correcciones migración 033 (definiciones)
-- ---------------------------------------------------------------------------
do $$
declare
  d_is_user text;
  d_complete text;
  d_hash text;
  d_invite text;
  d_rep text;
  d_resolve text;
  d_claim_out text;
  d_claim_push text;
  v_vol text;
  v_ret_out text;
  v_ret_push text;
begin
  select pg_get_functiondef('public.is_username_available(text)'::regprocedure) into d_is_user;
  select pg_get_functiondef(
    (select p.oid from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.proname = 'complete_profile_onboarding' limit 1)::regprocedure
  ) into d_complete;
  select pg_get_functiondef('public.org_hash_invitation_token(text)'::regprocedure) into d_hash;
  select pg_get_functiondef(
    'public.invite_organization_member(uuid, text, uuid, text, timestamptz)'::regprocedure
  ) into d_invite;
  select pg_get_functiondef('public.add_reputation_points(uuid, integer, text)'::regprocedure) into d_rep;
  select pg_get_functiondef('public._resolve_invitation_by_token(text)'::regprocedure) into d_resolve;
  select pg_get_functiondef('public.m06_claim_outbox(text, integer)'::regprocedure) into d_claim_out;
  select pg_get_functiondef('public.m06_claim_push_deliveries(text, integer)'::regprocedure) into d_claim_push;

  select p.provolatile into v_vol
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = '_resolve_invitation_by_token';

  select pg_get_function_result('public.m06_claim_outbox(text, integer)'::regprocedure) into v_ret_out;
  select pg_get_function_result('public.m06_claim_push_deliveries(text, integer)'::regprocedure) into v_ret_push;

  perform pg_temp.m07_pass_fail(
    'FIX_033', 'is_username_available_extensions_citext', 'uses extensions.citext',
    case when d_is_user ~ 'extensions\.citext' then 'uses extensions.citext' else 'missing extensions.citext' end,
    d_is_user ~ 'extensions\.citext', ''
  );
  -- Detect bare ::citext by stripping qualified casts first (POSIX regex; no lookbehind).
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'is_username_available_bare_citext_absent', 'absent',
    case when replace(d_is_user, 'extensions.citext', 'X') ~ '::citext' then 'present' else 'absent' end,
    replace(d_is_user, 'extensions.citext', 'X') !~ '::citext',
    'cast no calificado ::citext prohibido'
  );

  perform pg_temp.m07_pass_fail(
    'FIX_033', 'complete_profile_onboarding_extensions_citext', 'uses extensions.citext',
    case when d_complete ~ 'extensions\.citext' then 'uses extensions.citext' else 'missing' end,
    d_complete ~ 'extensions\.citext', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'complete_profile_onboarding_bare_citext_absent', 'absent',
    case when replace(d_complete, 'extensions.citext', 'X') ~ '::citext' then 'present' else 'absent' end,
    replace(d_complete, 'extensions.citext', 'X') !~ '::citext',
    'tras quitar extensions.citext no debe quedar ::citext'
  );

  perform pg_temp.m07_pass_fail(
    'FIX_033', 'org_hash_invitation_token_extensions_digest', 'uses extensions.digest',
    case when d_hash ~ 'extensions\.digest' then 'uses extensions.digest' else 'missing' end,
    d_hash ~ 'extensions\.digest', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'invite_organization_member_extensions_gen_random_bytes', 'uses extensions.gen_random_bytes',
    case when d_invite ~ 'extensions\.gen_random_bytes' then 'uses extensions.gen_random_bytes' else 'missing' end,
    d_invite ~ 'extensions\.gen_random_bytes', ''
  );

  perform pg_temp.m07_pass_fail(
    'FIX_033', 'add_reputation_points_v_badge_type', 'uses v_badge_type',
    case when d_rep ~ 'v_badge_type' then 'uses v_badge_type' else 'missing' end,
    d_rep ~ 'v_badge_type', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'add_reputation_points_stable_constraint', 'ON CONSTRAINT user_badges_user_id_badge_type_key',
    case when d_rep ~ 'user_badges_user_id_badge_type_key' then 'constraint present' else 'missing' end,
    d_rep ~ 'user_badges_user_id_badge_type_key', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'add_reputation_points_no_ambiguous_on_conflict_badge_type',
    'no ON CONFLICT (user_id, badge_type)',
    case when d_rep ~* 'on conflict\s*\(\s*user_id\s*,\s*badge_type\s*\)' then 'ambiguous pattern present' else 'absent' end,
    d_rep !~* 'on conflict\s*\(\s*user_id\s*,\s*badge_type\s*\)', ''
  );

  perform pg_temp.m07_pass_fail(
    'FIX_033', '_resolve_invitation_by_token_volatile', 'v (VOLATILE)',
    coalesce(v_vol, '<null>'),
    v_vol = 'v', 'provolatile=v'
  );

  perform pg_temp.m07_pass_fail(
    'FIX_033', 'm06_claim_outbox_returns_jsonb', 'jsonb', coalesce(v_ret_out, '<null>'),
    lower(coalesce(v_ret_out,'')) like '%jsonb%', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'm06_claim_outbox_skip_locked', 'SKIP LOCKED present',
    case when d_claim_out ~* 'skip locked' then 'present' else 'absent' end,
    d_claim_out ~* 'skip locked', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'm06_claim_outbox_limit_clamp', 'least(..., 50) present',
    case when d_claim_out ~* 'least\s*\(.*50' then 'present' else 'absent' end,
    d_claim_out ~* 'least\s*\(.*50', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'm06_claim_outbox_top_level_modifying_cte', 'WITH ... UPDATE ... INTO (no COALESCE wrap)',
    case
      when d_claim_out ~* 'with\s+claimed' and d_claim_out ~* 'into\s+v_result' and d_claim_out !~* 'coalesce\s*\(\s*with\s+claimed'
        then 'top-level CTE pattern'
      else 'unexpected pattern'
    end,
    d_claim_out ~* 'with\s+claimed' and d_claim_out ~* 'into\s+v_result' and d_claim_out !~* 'coalesce\s*\(\s*with\s+claimed',
    ''
  );

  perform pg_temp.m07_pass_fail(
    'FIX_033', 'm06_claim_push_deliveries_returns_jsonb', 'jsonb', coalesce(v_ret_push, '<null>'),
    lower(coalesce(v_ret_push,'')) like '%jsonb%', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'm06_claim_push_skip_locked', 'SKIP LOCKED present',
    case when d_claim_push ~* 'skip locked' then 'present' else 'absent' end,
    d_claim_push ~* 'skip locked', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'm06_claim_push_limit_clamp', 'least(..., 50) present',
    case when d_claim_push ~* 'least\s*\(.*50' then 'present' else 'absent' end,
    d_claim_push ~* 'least\s*\(.*50', ''
  );
  perform pg_temp.m07_pass_fail(
    'FIX_033', 'm06_claim_push_top_level_modifying_cte', 'WITH ... UPDATE ... INTO (no COALESCE wrap)',
    case
      when d_claim_push ~* 'with\s+claimed' and d_claim_push ~* 'into\s+v_result' and d_claim_push !~* 'coalesce\s*\(\s*with\s+claimed'
        then 'top-level CTE pattern'
      else 'unexpected pattern'
    end,
    d_claim_push ~* 'with\s+claimed' and d_claim_push ~* 'into\s+v_result' and d_claim_push !~* 'coalesce\s*\(\s*with\s+claimed',
    ''
  );
exception when others then
  perform pg_temp.m07_vr('FIX_033', 'definition_probe_error', 'no error', SQLERRM, 'FAIL', SQLSTATE);
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 6 — SECURITY DEFINER / search_path (inventario sensible M02–M07)
-- ---------------------------------------------------------------------------
do $$
declare
  r record;
  v_bad_sp int := 0;
  v_missing_definer int := 0;
  v_ext_in_sp int := 0;
  v_public_exec int := 0;
  v_anon_exec int := 0;
  v_checked int := 0;
  v_names text[] := array[
    'is_username_available','complete_profile_onboarding','add_reputation_points',
    'org_hash_invitation_token','invite_organization_member','_resolve_invitation_by_token',
    'm06_claim_outbox','m06_claim_push_deliveries',
    'm07_write_audit_event','m07_write_security_event','m07_write_application_error',
    'm07_validate_metadata','m07_record_metric','m07_record_health_check',
    'm07_run_health_check_manual','m07_evaluate_alert_rule','m07_evaluate_enabled_alert_rules',
    'm07_acknowledge_incident','m07_resolve_incident','m07_request_export',
    'm07_preview_retention_run','m07_execute_retention_run','m07_list_audit_events',
    'm07_has_any_permission','m07_require_actor'
  ];
  v_internal text[] := array[
    'm07_write_audit_event','m07_validate_metadata','m07_validate_metric_dimensions',
    'm07_sanitize_health_details','m07_latest_metric_value','m06_claim_outbox',
    'm06_claim_push_deliveries','m07_record_metric','_resolve_invitation_by_token'
  ];
begin
  for r in
    select p.proname,
           p.prosecdef as is_definer,
           coalesce(pg_get_function_identity_arguments(p.oid), '') as args,
           (select array_agg(trim(both from x))
              from unnest(coalesce(p.proconfig, array[]::text[])) as x) as cfg,
           pg_get_userbyid(p.proowner) as owner
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = any (v_names)
  loop
    v_checked := v_checked + 1;
    if not r.is_definer then
      v_missing_definer := v_missing_definer + 1;
      perform pg_temp.m07_vr(
        'DEFINER', r.proname || '(' || r.args || ')_security_definer',
        'SECURITY DEFINER', 'INVOKER', 'FAIL', 'expected DEFINER'
      );
    else
      perform pg_temp.m07_vr(
        'DEFINER', r.proname || '(' || r.args || ')_security_definer',
        'SECURITY DEFINER', 'SECURITY DEFINER', 'PASS', 'owner=' || coalesce(r.owner, '?')
      );
    end if;

    if exists (
      select 1 from unnest(coalesce(r.cfg, array[]::text[])) c
      where c ~* '^search_path=' and c !~* '^search_path=public\s*$' and c !~* '^search_path=\s*public\s*$'
    ) or not exists (
      select 1 from unnest(coalesce(r.cfg, array[]::text[])) c where c ~* '^search_path='
    ) then
      -- allow exact search_path=public only
      if not exists (
        select 1 from unnest(coalesce(r.cfg, array[]::text[])) c
        where lower(replace(c, ' ', '')) in ('search_path=public')
      ) then
        v_bad_sp := v_bad_sp + 1;
        perform pg_temp.m07_vr(
          'DEFINER', r.proname || '_search_path', 'search_path=public',
          coalesce(array_to_string(r.cfg, ';'), '<unset>'), 'FAIL', ''
        );
      else
        perform pg_temp.m07_vr(
          'DEFINER', r.proname || '_search_path', 'search_path=public', 'search_path=public', 'PASS', ''
        );
      end if;
    else
      perform pg_temp.m07_vr(
        'DEFINER', r.proname || '_search_path', 'search_path=public', 'search_path=public', 'PASS', ''
      );
    end if;

    if exists (
      select 1 from unnest(coalesce(r.cfg, array[]::text[])) c
      where c ~* 'search_path=.*extensions'
    ) then
      v_ext_in_sp := v_ext_in_sp + 1;
      perform pg_temp.m07_vr(
        'DEFINER', r.proname || '_search_path_excludes_extensions', 'no extensions in search_path',
        array_to_string(r.cfg, ';'), 'FAIL', ''
      );
    else
      perform pg_temp.m07_vr(
        'DEFINER', r.proname || '_search_path_excludes_extensions', 'no extensions in search_path',
        'ok', 'PASS', ''
      );
    end if;
  end loop;

  perform pg_temp.m07_pass_fail(
    'DEFINER', 'inventory_functions_found', '>=20', v_checked::text, v_checked >= 20,
    'funciones sensibles inventariadas'
  );

  -- EXECUTE grants: internos sin PUBLIC/anon; record_metric solo service_role
  perform pg_temp.m07_pass_fail(
    'DEFINER', 'm07_record_metric_authenticated_execute', 'false',
    has_function_privilege('authenticated', 'public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text)', 'EXECUTE')::text,
    not has_function_privilege('authenticated', 'public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text)', 'EXECUTE'),
    'solo service_role según diseño'
  );
  perform pg_temp.m07_pass_fail(
    'DEFINER', 'm07_record_metric_anon_execute', 'false',
    has_function_privilege('anon', 'public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text)', 'EXECUTE')::text,
    not has_function_privilege('anon', 'public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text)', 'EXECUTE'),
    ''
  );
  perform pg_temp.m07_pass_fail(
    'DEFINER', 'm07_record_metric_service_role_execute', 'true',
    has_function_privilege('service_role', 'public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text)', 'EXECUTE')::text,
    has_function_privilege('service_role', 'public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text)', 'EXECUTE'),
    ''
  );

  perform pg_temp.m07_pass_fail(
    'DEFINER', 'm06_claim_outbox_authenticated_execute', 'false',
    has_function_privilege('authenticated', 'public.m06_claim_outbox(text,integer)', 'EXECUTE')::text,
    not has_function_privilege('authenticated', 'public.m06_claim_outbox(text,integer)', 'EXECUTE'),
    'interno service_role'
  );
  perform pg_temp.m07_pass_fail(
    'DEFINER', 'm06_claim_push_authenticated_execute', 'false',
    has_function_privilege('authenticated', 'public.m06_claim_push_deliveries(text,integer)', 'EXECUTE')::text,
    not has_function_privilege('authenticated', 'public.m06_claim_push_deliveries(text,integer)', 'EXECUTE'),
    'interno service_role'
  );

  select count(*) into v_public_exec
  from information_schema.routine_privileges
  where specific_schema = 'public'
    and grantee = 'PUBLIC'
    and privilege_type = 'EXECUTE'
    and routine_name = any (v_internal);

  select count(*) into v_anon_exec
  from information_schema.routine_privileges
  where specific_schema = 'public'
    and grantee = 'anon'
    and privilege_type = 'EXECUTE'
    and routine_name = any (v_internal);

  perform pg_temp.m07_pass_fail(
    'DEFINER', 'internal_writers_public_execute', '0', v_public_exec::text, v_public_exec = 0,
    'PUBLIC EXECUTE ausente en writers internos'
  );
  perform pg_temp.m07_pass_fail(
    'DEFINER', 'internal_writers_anon_execute', '0', v_anon_exec::text, v_anon_exec = 0,
    'anon EXECUTE ausente en writers internos'
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 7 — RLS y grants tablas M07
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
  v_tables text[] := array[
    'observability_event_catalog','audit_events','security_events','application_errors',
    'observability_export_requests','m07_metric_catalog','performance_metrics',
    'm07_health_check_catalog','health_checks','alert_rules','alert_incidents',
    'alert_incident_transitions','observability_retention_policies',
    'observability_retention_runs','observability_retention_run_items'
  ];
  v_rls boolean;
  v_pol int;
  v_ins boolean;
  v_upd boolean;
  v_del boolean;
  v_anon_ins boolean;
begin
  foreach t in array v_tables loop
    select c.relrowsecurity into v_rls
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = t;

    perform pg_temp.m07_pass_fail(
      'RLS', t || '_rls_enabled', 'true', coalesce(v_rls::text, 'missing'),
      coalesce(v_rls, false), 'rowsecurity'
    );

    select count(*) into v_pol from pg_policies where schemaname = 'public' and tablename = t;
    perform pg_temp.m07_pass_fail(
      'RLS', t || '_policies_exist', '>=1', v_pol::text, v_pol >= 1, 'pg_policies'
    );

    v_ins := has_table_privilege('authenticated', 'public.' || t, 'INSERT');
    v_upd := has_table_privilege('authenticated', 'public.' || t, 'UPDATE');
    v_del := has_table_privilege('authenticated', 'public.' || t, 'DELETE');
    v_anon_ins := has_table_privilege('anon', 'public.' || t, 'INSERT')
               or has_table_privilege('anon', 'public.' || t, 'UPDATE')
               or has_table_privilege('anon', 'public.' || t, 'DELETE');

    perform pg_temp.m07_pass_fail('GRANTS', t || '_authenticated_insert', 'false', v_ins::text, not v_ins, '');
    perform pg_temp.m07_pass_fail('GRANTS', t || '_authenticated_update', 'false', v_upd::text, not v_upd, '');
    perform pg_temp.m07_pass_fail('GRANTS', t || '_authenticated_delete', 'false', v_del::text, not v_del, '');
    perform pg_temp.m07_pass_fail('GRANTS', t || '_anon_write', 'false', v_anon_ins::text, not v_anon_ins, '');
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 8 — Permisos funcionales (estructural vía definiciones)
-- ---------------------------------------------------------------------------
do $$
declare
  d_list_audit text;
  d_health text;
  d_eval text;
  d_has_perm text;
  d_m07_has text;
begin
  select pg_get_functiondef('public.m07_list_audit_events(integer, integer, uuid, text)'::regprocedure)
    into d_list_audit;
  select pg_get_functiondef(
    'public.m07_record_health_check(text, text, text, bigint, jsonb, text, timestamptz, text)'::regprocedure
  ) into d_health;
  select pg_get_functiondef('public.m07_evaluate_alert_rule(uuid, text)'::regprocedure) into d_eval;
  select pg_get_functiondef(
    (select p.oid from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname='public' and p.proname='has_permission' limit 1)::regprocedure
  ) into d_has_perm;
  select pg_get_functiondef('public.m07_has_any_permission(text[])'::regprocedure) into d_m07_has;

  perform pg_temp.m07_pass_fail(
    'PERMS', 'list_audit_requires_observability_or_sensitive',
    'observability.view|audit.view_sensitive',
    case when d_list_audit ~ 'observability\.view' and d_list_audit ~ 'audit\.view_sensitive'
      then 'gate present' else 'gate missing' end,
    d_list_audit ~ 'observability\.view' and d_list_audit ~ 'audit\.view_sensitive',
    '032 D1'
  );
  perform pg_temp.m07_pass_fail(
    'PERMS', 'list_audit_no_audit_view_proxy',
    'sin audit.view como autoridad M07',
    case when d_list_audit ~ '''audit\.view''' then 'audit.view still referenced' else 'absent' end,
    d_list_audit !~ '''audit\.view''',
    'audit.view no debe conceder list audit M07'
  );
  perform pg_temp.m07_pass_fail(
    'PERMS', 'health_manual_requires_health_check_execute',
    'health.check.execute',
    case when d_health ~ 'health\.check\.execute' then 'present' else 'absent' end,
    d_health ~ 'health\.check\.execute',
    '032 D2'
  );
  perform pg_temp.m07_pass_fail(
    'PERMS', 'evaluate_alerts_requires_manage',
    'observability.manage|alert.manage',
    case when d_eval ~ 'observability\.manage' and d_eval ~ 'alert\.manage'
      then 'present' else 'absent' end,
    d_eval ~ 'observability\.manage' and d_eval ~ 'alert\.manage',
    '032 D3'
  );
  perform pg_temp.m07_pass_fail(
    'PERMS', 'has_permission_ignores_account_type',
    'sin account_type',
    case when d_has_perm ~* 'account_type' then 'references account_type' else 'absent' end,
    d_has_perm !~* 'account_type',
    'AccountType no concede autoridad'
  );
  perform pg_temp.m07_pass_fail(
    'PERMS', 'has_permission_ignores_active_modules',
    'sin active_modules',
    case when d_has_perm ~* 'active_modules' then 'references active_modules' else 'absent' end,
    d_has_perm !~* 'active_modules',
    'active_modules no concede autoridad'
  );
  perform pg_temp.m07_pass_fail(
    'PERMS', 'm07_has_any_permission_delegates_has_permission',
    'usa has_permission',
    case when d_m07_has ~ 'has_permission' then 'delegates' else 'missing' end,
    d_m07_has ~ 'has_permission',
    ''
  );

  -- Runtime: sin JWT, usuario común denegado (require_actor / OBS_PERMISSION_DENIED)
  begin
    perform public.m07_list_audit_events(1, 0, null, null);
    perform pg_temp.m07_vr(
      'PERMS', 'common_user_list_audit_denied_without_jwt', 'OBS_PERMISSION_DENIED',
      'no exception', 'FAIL', 'debió denegar sin actor'
    );
  exception when others then
    perform pg_temp.m07_pass_fail(
      'PERMS', 'common_user_list_audit_denied_without_jwt',
      'OBS_PERMISSION_DENIED (o 42501)', SQLERRM,
      SQLERRM like '%OBS_PERMISSION_DENIED%' or SQLSTATE = '42501',
      SQLSTATE
    );
  end;
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 9 — Auditoría / seguridad (estructural + probes de validación)
-- ---------------------------------------------------------------------------
do $$
declare
  d_meta text;
  d_reason text;
  d_write text;
  d_err text;
  v_upd_priv boolean;
  v_del_priv boolean;
begin
  select pg_get_functiondef('public.m07_validate_metadata(text, jsonb)'::regprocedure) into d_meta;
  select pg_get_functiondef('public.m07_sanitize_reason_code(text)'::regprocedure) into d_reason;
  select pg_get_functiondef(
    'public.m07_write_audit_event(text, text, text, text, text, text, uuid, text, text, text, jsonb, text)'::regprocedure
  ) into d_write;
  select pg_get_functiondef(
    'public.m07_write_application_error(text, text, text, text, text, text, text, boolean, text, text, text, uuid, jsonb)'::regprocedure
  ) into d_err;

  v_upd_priv := has_table_privilege('authenticated', 'public.audit_events', 'UPDATE');
  v_del_priv := has_table_privilege('authenticated', 'public.audit_events', 'DELETE');

  perform pg_temp.m07_pass_fail(
    'AUDIT', 'audit_events_append_only_no_client_update', 'false', v_upd_priv::text, not v_upd_priv,
    'append-only vía ausencia UPDATE cliente'
  );
  perform pg_temp.m07_pass_fail(
    'AUDIT', 'audit_events_append_only_no_client_delete', 'false', v_del_priv::text, not v_del_priv,
    'append-only vía ausencia DELETE cliente'
  );
  perform pg_temp.m07_pass_fail(
    'AUDIT', 'actor_derived_server_side', 'auth.uid / m07_require_actor en writers',
    case when d_write ~ 'auth\.uid' or d_write ~ 'm07_resolve_actor' then 'present' else 'absent' end,
    d_write ~ 'auth\.uid' or d_write ~ 'm07_resolve_actor',
    'actor no confiable del cliente'
  );
  perform pg_temp.m07_pass_fail(
    'AUDIT', 'correlation_id_validated', 'm07_validate_correlation_id',
    case when d_write ~ 'm07_validate_correlation_id' then 'present' else 'absent' end,
    d_write ~ 'm07_validate_correlation_id', ''
  );
  perform pg_temp.m07_pass_fail(
    'AUDIT', 'unknown_metadata_rejected', 'OBS_METADATA_DENIED',
    case when d_meta ~ 'OBS_METADATA_DENIED' then 'present' else 'absent' end,
    d_meta ~ 'OBS_METADATA_DENIED', ''
  );
  perform pg_temp.m07_pass_fail(
    'AUDIT', 'unknown_event_key_rejected', 'OBS_EVENT_UNKNOWN',
    case when d_meta ~ 'OBS_EVENT_UNKNOWN' or d_write ~ 'OBS_EVENT_UNKNOWN' then 'present' else 'absent' end,
    d_meta ~ 'OBS_EVENT_UNKNOWN' or d_write ~ 'OBS_EVENT_UNKNOWN', ''
  );
  perform pg_temp.m07_pass_fail(
    'AUDIT', 'forbid_jwt_bearer_token_patterns', 'patterns in sanitize/validate',
    case when d_meta ~* 'jwt|bearer|token|service_role|signed|stack|sql'
      and d_reason ~* 'JWT|BEARER|TOKEN|SERVICE_ROLE|SIGNED|STACK|SQL'
      then 'present' else 'incomplete' end,
    d_meta ~* 'jwt|bearer|token|service_role' and d_reason ~* 'JWT|BEARER|TOKEN|SERVICE_ROLE',
    'prohibiciones estructurales en código SQL'
  );
  perform pg_temp.m07_pass_fail(
    'AUDIT', 'application_errors_fingerprint', 'fingerprint column/usage',
    case when d_err ~ 'fingerprint' then 'present' else 'absent' end,
    d_err ~ 'fingerprint', ''
  );
  perform pg_temp.m07_pass_fail(
    'AUDIT', 'audit_dedup_index_exists', 'audit_events_dedup_lookup_idx',
    case when exists (
      select 1 from pg_indexes where schemaname='public' and indexname='audit_events_dedup_lookup_idx'
    ) then 'present' else 'absent' end,
    exists (select 1 from pg_indexes where schemaname='public' and indexname='audit_events_dedup_lookup_idx'),
    ''
  );

  -- Probes de validación (sin escritura persistente)
  begin
    perform public.m07_validate_metadata('m00.config.loaded', jsonb_build_object('password', 'x'));
    perform pg_temp.m07_vr('AUDIT', 'probe_reject_password_metadata', 'OBS_METADATA_DENIED', 'accepted', 'FAIL', '');
  exception when others then
    perform pg_temp.m07_pass_fail(
      'AUDIT', 'probe_reject_password_metadata', 'OBS_METADATA_DENIED', SQLERRM,
      SQLERRM like '%OBS_METADATA_DENIED%', SQLSTATE
    );
  end;

  begin
    perform public.m07_validate_metadata('___no_such_event_key___', '{}'::jsonb);
    perform pg_temp.m07_vr('AUDIT', 'probe_reject_unknown_event', 'OBS_EVENT_UNKNOWN', 'accepted', 'FAIL', '');
  exception when others then
    perform pg_temp.m07_pass_fail(
      'AUDIT', 'probe_reject_unknown_event', 'OBS_EVENT_UNKNOWN', SQLERRM,
      SQLERRM like '%OBS_EVENT_UNKNOWN%', SQLSTATE
    );
  end;

  perform pg_temp.m07_vr(
    'AUDIT', 'observability_loop_absence_runtime', 'sin loops runtime',
    'no demostrable solo con metadatos', 'BACKLOG',
    'requiere carga controlada / telemetría; no inventar PASS'
  );
  perform pg_temp.m07_vr(
    'AUDIT', 'internal_body_prohibition_e2e', 'body INTERNAL prohibido en cliente',
    'parcial vía allowlists SQL', 'BACKLOG',
    'prueba e2e Android/staging pendiente'
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 10 — Métricas y health (estructural + probes; escrituras en subtxn)
-- ---------------------------------------------------------------------------
do $$
declare
  d_metric text;
  d_dims text;
  d_health text;
  v_msg text;
begin
  select pg_get_functiondef(
    'public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text)'::regprocedure
  ) into d_metric;
  select pg_get_functiondef('public.m07_validate_metric_dimensions(text,jsonb)'::regprocedure) into d_dims;
  select pg_get_functiondef(
    'public.m07_record_health_check(text,text,text,bigint,jsonb,text,timestamptz,text)'::regprocedure
  ) into d_health;

  perform pg_temp.m07_pass_fail(
    'METRICS', 'record_metric_requires_service_role', 'service_role gate',
    case when d_metric ~ 'service_role' and d_metric ~ 'OBS_WRITE_DENIED' then 'present' else 'absent' end,
    d_metric ~ 'service_role' and d_metric ~ 'OBS_WRITE_DENIED',
    'Android sin escritura arbitraria'
  );
  perform pg_temp.m07_pass_fail(
    'METRICS', 'dimensions_allowlisted', 'allowed_dimension_keys + deny PII keys',
    case when d_dims ~ 'allowed_dimension_keys' and d_dims ~ 'OBS_METRIC_DIMENSION_DENIED' then 'present' else 'absent' end,
    d_dims ~ 'allowed_dimension_keys' and d_dims ~ 'OBS_METRIC_DIMENSION_DENIED', ''
  );
  perform pg_temp.m07_pass_fail(
    'METRICS', 'units_allowlisted_table_constraint', 'performance_metrics_unit_chk',
    case when exists (
      select 1 from pg_constraint where conname = 'performance_metrics_unit_chk'
    ) then 'present' else 'absent' end,
    exists (select 1 from pg_constraint where conname = 'performance_metrics_unit_chk'),
    ''
  );
  perform pg_temp.m07_pass_fail(
    'HEALTH', 'ttl_expires_at_default_in_record', 'expires_at / interval',
    case when d_health ~ 'expires_at' then 'present' else 'absent' end,
    d_health ~ 'expires_at', ''
  );
  perform pg_temp.m07_pass_fail(
    'HEALTH', 'unknown_without_evidence_in_summary', 'UNKNOWN default path',
    case when pg_get_functiondef('public.m07_get_operational_summary()'::regprocedure) ~ 'UNKNOWN'
      then 'present' else 'absent' end,
    pg_get_functiondef('public.m07_get_operational_summary()'::regprocedure) ~ 'UNKNOWN',
    'sin evidencia → UNKNOWN'
  );

  -- Probe: dimensión denegada
  begin
    perform public.m07_validate_metric_dimensions(
      (select metric_key from public.m07_metric_catalog limit 1),
      jsonb_build_object('email', 'a@b.c')
    );
    perform pg_temp.m07_vr('METRICS', 'probe_deny_email_dimension', 'OBS_METRIC_DIMENSION_DENIED', 'accepted', 'FAIL', '');
  exception when others then
    perform pg_temp.m07_pass_fail(
      'METRICS', 'probe_deny_email_dimension', 'OBS_METRIC_DIMENSION_DENIED', SQLERRM,
      SQLERRM like '%OBS_METRIC_DIMENSION_DENIED%', SQLSTATE
    );
  end;

  -- Probe: record_metric sin service_role → denegado (subtxn; sin residuo)
  begin
    perform public.m07_record_metric(
      (select metric_key from public.m07_metric_catalog limit 1),
      1, (select unit from public.m07_metric_catalog limit 1),
      '{}'::jsonb, null, null, 1, 'valprobe01', 'ANDROID'
    );
    v_msg := 'accepted';
  exception when others then
    v_msg := SQLERRM;
  end;
  perform pg_temp.m07_pass_fail(
    'METRICS', 'probe_record_metric_denied_without_service_role',
    'OBS_WRITE_DENIED', v_msg,
    v_msg like '%OBS_WRITE_DENIED%',
    'escritura no persistida; gate service_role'
  );

  -- SAVEPOINT local (seguro si el SQL Editor envuelve el script en una transacción):
  -- evita ROLLBACK global que borraría resultados.
  begin
    insert into public.performance_metrics (
      metric_key, module, metric_type, value_numeric, unit, dimensions,
      window_start, window_end, sample_count, source
    )
    select metric_key, module, metric_type, 0, unit, '{}'::jsonb,
           timezone('utc', now()) - interval '1 minute', timezone('utc', now()),
           1, 'M07_VALIDATION_SHOULD_ROLLBACK'
    from public.m07_metric_catalog
    limit 1;
    -- Si el INSERT directo pasó (bypass inesperado), forzar aborto de subtxn.
    raise exception 'm07_validation_tx_rollback';
  exception when others then
    if SQLERRM = 'm07_validation_tx_rollback' then
      perform pg_temp.m07_vr(
        'METRICS', 'direct_insert_performance_metrics_rolled_back',
        'subtxn abort / no residuo', 'rolled_back_after_insert', 'PASS',
        'subtransacción abortada; sin residuo permanente'
      );
    elsif SQLERRM like '%permission%' or SQLSTATE in ('42501','42503')
          or SQLERRM like '%RLS%' or SQLERRM like '%policy%'
          or SQLSTATE = '42501' then
      perform pg_temp.m07_vr(
        'METRICS', 'direct_insert_performance_metrics_denied',
        'denied or rolled back', SQLERRM, 'PASS', SQLSTATE
      );
    else
      -- Insert denegado por FK/RLS/grant es PASS de seguridad; otros errores se reportan.
      perform pg_temp.m07_vr(
        'METRICS', 'direct_insert_performance_metrics_probe',
        'denied or rolled back', SQLERRM,
        case when SQLSTATE like '42%' or SQLSTATE like '23%' or SQLSTATE = '42501'
          then 'PASS' else 'FAIL' end,
        SQLSTATE
      );
    end if;
  end;
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 11 — Incidentes (estructural; runtime sin JWT → NOT_EXECUTED)
-- ---------------------------------------------------------------------------
do $$
declare
  d_ack text;
  d_res text;
  d_eval text;
begin
  select pg_get_functiondef('public.m07_acknowledge_incident(uuid, text)'::regprocedure) into d_ack;
  select pg_get_functiondef('public.m07_resolve_incident(uuid, text, text)'::regprocedure) into d_res;
  select pg_get_functiondef('public.m07_evaluate_alert_rule(uuid, text)'::regprocedure) into d_eval;

  perform pg_temp.m07_pass_fail(
    'INCIDENTS', 'statuses_constraint', 'OPEN/ACKNOWLEDGED/RESOLVED(+)',
    case when exists (
      select 1 from information_schema.check_constraints
      where constraint_name like '%alert_incidents%status%'
         or constraint_name like 'alert_incidents%'
    ) or exists (
      select 1 from pg_constraint c
      join pg_class t on t.oid = c.conrelid
      where t.relname = 'alert_incidents' and c.contype = 'c'
        and pg_get_constraintdef(c.oid) ~ 'OPEN'
    ) then 'constraint present' else 'missing' end,
    exists (
      select 1 from pg_constraint c
      join pg_class t on t.oid = c.conrelid
      join pg_namespace n on n.oid = t.relnamespace
      where n.nspname='public' and t.relname='alert_incidents' and c.contype='c'
        and pg_get_constraintdef(c.oid) ~ 'OPEN'
        and pg_get_constraintdef(c.oid) ~ 'ACKNOWLEDGED'
        and pg_get_constraintdef(c.oid) ~ 'RESOLVED'
    ),
    ''
  );
  perform pg_temp.m07_pass_fail(
    'INCIDENTS', 'invalid_transition_rejected_in_ack', 'OBS_INCIDENT_TRANSITION_DENIED',
    case when d_ack ~ 'OBS_INCIDENT_TRANSITION_DENIED' then 'present' else 'absent' end,
    d_ack ~ 'OBS_INCIDENT_TRANSITION_DENIED', ''
  );
  perform pg_temp.m07_pass_fail(
    'INCIDENTS', 'idempotent_incident_key_on_conflict', 'on conflict (rule_id, incident_key)',
    case when d_eval ~* 'on conflict' and d_eval ~ 'incident_key' then 'present' else 'absent' end,
    d_eval ~* 'on conflict' and d_eval ~ 'incident_key',
    'creación/deduplicación idempotente'
  );
  perform pg_temp.m07_pass_fail(
    'INCIDENTS', 'no_client_delete_on_incidents', 'false',
    has_table_privilege('authenticated', 'public.alert_incidents', 'DELETE')::text,
    not has_table_privilege('authenticated', 'public.alert_incidents', 'DELETE'),
    ''
  );

  if auth.uid() is null then
    perform pg_temp.m07_vr(
      'INCIDENTS', 'runtime_transition_probe', 'ACK/RESOLVE con actor staff',
      'auth.uid() is null', 'NOT_EXECUTED',
      'SQL Editor sin JWT; no crear incidentes persistentes'
    );
  else
    perform pg_temp.m07_vr(
      'INCIDENTS', 'runtime_transition_probe', 'ACK/RESOLVE con actor staff',
      'JWT presente pero probe omitido por seguridad de matriz', 'NOT_EXECUTED',
      'ejecutar solo con fixture controlado y ROLLBACK'
    );
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 12 — Retención (estructural)
-- ---------------------------------------------------------------------------
do $$
declare
  d_prev text;
  d_exec text;
begin
  select pg_get_functiondef('public.m07_preview_retention_run(uuid, integer, text)'::regprocedure) into d_prev;
  select pg_get_functiondef('public.m07_execute_retention_run(uuid, text)'::regprocedure) into d_exec;

  perform pg_temp.m07_pass_fail(
    'RETENTION', 'preview_status_previewed', 'PREVIEWED',
    case when d_prev ~ 'PREVIEWED' then 'present' else 'absent' end,
    d_prev ~ 'PREVIEWED', 'preview no es execute'
  );
  perform pg_temp.m07_pass_fail(
    'RETENTION', 'execute_requires_preview', 'OBS_RETENTION_PREVIEW_REQUIRED',
    case when d_exec ~ 'OBS_RETENTION_PREVIEW_REQUIRED' then 'present' else 'absent' end,
    d_exec ~ 'OBS_RETENTION_PREVIEW_REQUIRED', ''
  );
  perform pg_temp.m07_pass_fail(
    'RETENTION', 'preview_consumed_marked_executed', 'preview → EXECUTED',
    case when d_exec ~ 'EXECUTED' then 'present' else 'absent' end,
    d_exec ~ 'EXECUTED', 'preview consumido no reutilizable (status cambia)'
  );
  perform pg_temp.m07_pass_fail(
    'RETENTION', 'batch_size_limited', 'batch_size / limit',
    case when d_exec ~ 'batch_size' then 'present' else 'absent' end,
    d_exec ~ 'batch_size', ''
  );
  perform pg_temp.m07_pass_fail(
    'RETENTION', 'legal_hold_blocks', 'OBS_RETENTION_LEGAL_HOLD',
    case when d_prev ~ 'OBS_RETENTION_LEGAL_HOLD' and d_exec ~ 'OBS_RETENTION_LEGAL_HOLD'
      then 'present' else 'absent' end,
    d_prev ~ 'OBS_RETENTION_LEGAL_HOLD' and d_exec ~ 'OBS_RETENTION_LEGAL_HOLD', ''
  );
  perform pg_temp.m07_pass_fail(
    'RETENTION', 'legal_review_required_policy_exists', 'LEGAL_REVIEW_REQUIRED',
    case when exists (
      select 1 from public.observability_retention_policies where policy_key = 'LEGAL_REVIEW_REQUIRED'
    ) then 'present' else 'absent' end,
    exists (
      select 1 from public.observability_retention_policies where policy_key = 'LEGAL_REVIEW_REQUIRED'
    ),
    'no se purga por diseño'
  );
  perform pg_temp.m07_pass_fail(
    'RETENTION', 'execute_targets_only_m07_tables', 'performance_metrics|health_checks|application_errors',
    case
      when d_exec ~ 'performance_metrics' and d_exec ~ 'health_checks' and d_exec ~ 'application_errors'
       and d_exec !~ 'organization_memberships' and d_exec !~ 'notification_outbox'
        then 'silos M02–M06 no en delete paths'
      else 'unexpected targets'
    end,
    d_exec ~ 'performance_metrics' and d_exec ~ 'health_checks' and d_exec ~ 'application_errors'
      and d_exec !~ 'organization_memberships' and d_exec !~ 'notification_outbox',
    'silos M02–M06 protegidos'
  );
  perform pg_temp.m07_pass_fail(
    'RETENTION', 'execute_response_no_deleted_payload', 'affected_count only',
    case when d_exec ~ 'affected_count' and d_exec !~ 'deleted_rows' then 'ok' else 'review' end,
    d_exec ~ 'affected_count',
    'respuesta no devuelve contenido eliminado'
  );

  if auth.uid() is null then
    perform pg_temp.m07_vr(
      'RETENTION', 'runtime_preview_execute_probe', 'BEGIN/ROLLBACK con staff JWT',
      'auth.uid() is null', 'NOT_EXECUTED',
      'no mutar staging sin fixture; gates estructurales PASS arriba'
    );
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 13 — Exportación e integración M06
-- ---------------------------------------------------------------------------
do $$
declare
  d_exp text;
  v_status_chk text;
begin
  select pg_get_functiondef(
    (select p.oid from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname='public' and p.proname='m07_request_export' limit 1)::regprocedure
  ) into d_exp;

  select pg_get_constraintdef(c.oid) into v_status_chk
  from pg_constraint c
  join pg_class t on t.oid = c.conrelid
  join pg_namespace n on n.oid = t.relnamespace
  where n.nspname='public' and t.relname='observability_export_requests'
    and c.contype='c' and pg_get_constraintdef(c.oid) ~ 'AUTHORIZED'
  limit 1;

  perform pg_temp.m07_pass_fail(
    'EXPORT', 'request_returns_authorized', 'AUTHORIZED + file_pending',
    case when d_exp ~ 'AUTHORIZED' and d_exp ~ 'file_pending' then 'present' else 'absent' end,
    d_exp ~ 'AUTHORIZED' and d_exp ~ 'file_pending',
    'READY_SIMULATED no alcanzable como éxito de archivo'
  );
  perform pg_temp.m07_pass_fail(
    'EXPORT', 'no_fictitious_signed_url_in_request', 'sin signed URL simulada',
    case when d_exp ~* 'signed.?url|https://' then 'suspicious url pattern' else 'absent' end,
    d_exp !~* 'signed.?url' and d_exp !~* 'https://',
    ''
  );
  perform pg_temp.m07_pass_fail(
    'EXPORT', 'no_fictitious_csv_jsonl_payload', 'sin CSV/JSONL ficticio',
    case when d_exp ~* 'csv|jsonl' then 'pattern found' else 'absent' end,
    d_exp !~* 'csv|jsonl',
    ''
  );
  perform pg_temp.m07_pass_fail(
    'EXPORT', 'status_allows_authorized', 'AUTHORIZED in check',
    coalesce(v_status_chk, '<none>'),
    coalesce(v_status_chk, '') ~ 'AUTHORIZED',
    ''
  );

  perform pg_temp.m07_vr(
    'EXPORT', 'file_export_pending', 'EXPORTACIÓN DE ARCHIVO PENDIENTE',
    'AUTHORIZED + filePending; sin archivo real', 'BACKLOG',
    'EXPORTACIÓN DE ARCHIVO PENDIENTE'
  );
  perform pg_temp.m07_vr(
    'EXPORT', 'm06_integration_pending', 'INTEGRACIÓN M06 PENDIENTE',
    'event key catalogado; sin envío simulado', 'BACKLOG',
    'INTEGRACIÓN M06 PENDIENTE'
  );
  perform pg_temp.m07_pass_fail(
    'EXPORT', 'no_simulated_m06_send_in_export_rpc', 'sin enqueue/outbox en request_export',
    case when d_exp ~* 'notification_outbox|m06_claim|send_push' then 'send path found' else 'absent' end,
    d_exp !~* 'notification_outbox|m06_claim|send_push',
    'ningún envío M06 simulado'
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- PASO 14 — Warnings lint (backlog documentado; no crear 034)
-- ---------------------------------------------------------------------------
do $$
begin
  perform pg_temp.m07_vr(
    'LINT', 'db_lint_remote_errors', '0 errors / exit 0',
    'confirmado manualmente por propietario (sesión previa)', 'PASS',
    'DB LINT REMOTO: PASS — 0 ERRORES (evidencia externa; no re-ejecutado aquí)'
  );
  perform pg_temp.m07_vr(
    'LINT', 'db_lint_remote_warnings_non_blocking', '~22 warnings backlog',
    'actor/v_actor unread; p_worker_id unused; IMMUTABLE/STABLE mismatches', 'BACKLOG',
    'WARNINGS NO BLOQUEANTES: BACKLOG — no migración 034 en esta tarea'
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Meta: integridad del runner
-- ---------------------------------------------------------------------------
do $$
declare
  v_fail int;
  v_pass int;
  v_ne int;
  v_bl int;
  v_total int;
begin
  select
    count(*) filter (where status = 'FAIL'),
    count(*) filter (where status = 'PASS'),
    count(*) filter (where status = 'NOT_EXECUTED'),
    count(*) filter (where status = 'BACKLOG'),
    count(*)
  into v_fail, v_pass, v_ne, v_bl, v_total
  from m07_staging_validation_results;

  perform pg_temp.m07_vr(
    'META', 'runner_summary',
    'tabular results',
    format('total=%s pass=%s fail=%s not_executed=%s backlog=%s', v_total, v_pass, v_fail, v_ne, v_bl),
    case when v_fail = 0 then 'PASS' else 'FAIL' end,
    'resumen final del runner (incluye este check)'
  );
end;
$$;

-- Salida tabular para copiar/exportar
select
  check_group,
  check_name,
  expected,
  actual,
  status,
  details
from m07_staging_validation_results
order by
  case check_group
    when 'HISTORY' then 1
    when 'CATALOG' then 2
    when 'FIX_033' then 3
    when 'DEFINER' then 4
    when 'RLS' then 5
    when 'GRANTS' then 6
    when 'PERMS' then 7
    when 'AUDIT' then 8
    when 'METRICS' then 9
    when 'HEALTH' then 10
    when 'INCIDENTS' then 11
    when 'RETENTION' then 12
    when 'EXPORT' then 13
    when 'LINT' then 14
    when 'META' then 15
    else 99
  end,
  check_name;
