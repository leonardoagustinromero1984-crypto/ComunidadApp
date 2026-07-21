-- =============================================================================
-- LeoVer STAGING validation 036 — no secrets
-- Read-mostly catalog validation. No fixtures, auth users, PII, or persistent data.
-- Run only after the separately confirmed manual apply.
-- =============================================================================

drop table if exists m08_staging_validation_results;
create temporary table m08_staging_validation_results (
  check_name text not null,
  status text not null check (status in ('PASS', 'FAIL', 'NOT_EXECUTED')),
  detail text not null default ''
);

create or replace function pg_temp.m08_stage_result(
  p_name text, p_ok boolean, p_detail text default ''
) returns void language plpgsql as $$
begin
  insert into m08_staging_validation_results(check_name, status, detail)
  values (p_name, case when p_ok then 'PASS' else 'FAIL' end, coalesce(p_detail, ''));
end;
$$;

do $$
declare
  v_max text;
  v_missing int;
  v_public int;
  v_anon int;
  v_authenticated int;
  v_bad_definer int;
  v_bad_search_path int;
  v_names text[] := array[
    'm08_update_pet_profile',
    'm08_update_pet_health',
    'm08_get_pet_access_context',
    'm08_list_accessible_pets'
  ];
begin
  select coalesce(max(version), '<none>')
    into v_max
  from supabase_migrations.schema_migrations;
  perform pg_temp.m08_stage_result(
    'migration_history_max_036',
    v_max = '036',
    format('max=%s; expected 036 after apply', v_max)
  );

  select count(*) into v_missing
  from unnest(v_names) as expected(name)
  where not exists (
    select 1
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = expected.name
  );
  perform pg_temp.m08_stage_result(
    'm08_036_rpcs_present', v_missing = 0, format('missing=%s', v_missing)
  );

  select count(*) into v_public
  from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = any(v_names)
    and grantee = 'PUBLIC' and privilege_type = 'EXECUTE';
  perform pg_temp.m08_stage_result(
    'no_public_execute_on_036_rpcs', v_public = 0, format('public=%s', v_public)
  );

  select count(*) into v_anon
  from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = any(v_names)
    and grantee = 'anon' and privilege_type = 'EXECUTE';
  perform pg_temp.m08_stage_result(
    'no_anon_execute_on_036_rpcs', v_anon = 0, format('anon=%s', v_anon)
  );

  select count(distinct routine_name) into v_authenticated
  from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = any(v_names)
    and grantee = 'authenticated' and privilege_type = 'EXECUTE';
  perform pg_temp.m08_stage_result(
    'authenticated_execute_on_036_rpcs',
    v_authenticated = cardinality(v_names),
    format('authenticated=%s expected=%s', v_authenticated, cardinality(v_names))
  );

  select count(*) into v_bad_definer
  from unnest(v_names) as expected(name)
  where not exists (
    select 1
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = expected.name and p.prosecdef
  );
  perform pg_temp.m08_stage_result(
    '036_rpcs_security_definer', v_bad_definer = 0,
    format('missing_definer=%s', v_bad_definer)
  );

  select count(*) into v_bad_search_path
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname = any(v_names)
    and not exists (
      select 1 from unnest(coalesce(p.proconfig, array[]::text[])) cfg
      where cfg like 'search_path=%'
    );
  perform pg_temp.m08_stage_result(
    '036_rpcs_search_path_set', v_bad_search_path = 0,
    format('missing_search_path=%s', v_bad_search_path)
  );

  perform pg_temp.m08_stage_result(
    'public_profile_rpc_absent',
    not exists (
      select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = 'm08_list_public_profile_pets'
    ),
    ''
  );
end;
$$;

insert into m08_staging_validation_results(check_name, status, detail)
values (
  'behavioral_jwt_tests',
  'NOT_EXECUTED',
  'deferred to controlled smoke after apply'
);

select
  'runner_summary' as check_name,
  case when count(*) filter (where status = 'FAIL') = 0 then 'PASS' else 'FAIL' end as status,
  format(
    'PASS=%s FAIL=%s NOT_EXECUTED=%s total=%s',
    count(*) filter (where status = 'PASS'),
    count(*) filter (where status = 'FAIL'),
    count(*) filter (where status = 'NOT_EXECUTED'),
    count(*)
  ) as detail
from m08_staging_validation_results
union all
select check_name, status, detail
from m08_staging_validation_results
order by 1;
