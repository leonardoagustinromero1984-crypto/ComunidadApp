-- =============================================================================
-- LeoVer STAGING validation 035 — no secrets
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
  v_missing_tables int;
  v_missing_rpcs int;
  v_public_execute int;
  v_bad_search_path int;
begin
  select coalesce(max(version), '<none>')
    into v_max
  from supabase_migrations.schema_migrations;
  perform pg_temp.m08_stage_result(
    'migration_history_at_least_035',
    case when v_max ~ '^[0-9]+$' then v_max::bigint >= 35 else false end,
    format('max=%s; expected ideally 035 or 036', v_max)
  );

  select count(*) into v_missing_tables
  from unnest(array[
    'pet_responsibilities',
    'pet_authorizations',
    'pet_transfers',
    'pet_status_history'
  ]) as expected(name)
  where to_regclass(format('public.%I', expected.name)) is null;
  perform pg_temp.m08_stage_result(
    'pet_tables_present', v_missing_tables = 0, format('missing=%s', v_missing_tables)
  );

  select count(*) into v_missing_rpcs
  from unnest(array[
    'm08_create_pet_with_principal',
    'm08_assign_pet_responsibility',
    'm08_revoke_pet_responsibility',
    'm08_grant_pet_authorization',
    'm08_revoke_pet_authorization',
    'm08_initiate_pet_transfer',
    'm08_accept_pet_transfer',
    'm08_reject_pet_transfer',
    'm08_cancel_pet_transfer',
    'm08_expire_pet_transfers',
    'm08_mark_pet_deceased',
    'm08_archive_pet',
    'm08_restore_pet',
    'm08_detect_pet_duplicate_candidates',
    'm08_set_pet_avatar_asset'
  ]) as expected(name)
  where not exists (
    select 1
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = expected.name
  );
  perform pg_temp.m08_stage_result(
    'm08_035_rpcs_present', v_missing_rpcs = 0, format('missing=%s', v_missing_rpcs)
  );

  select count(*) into v_public_execute
  from information_schema.routine_privileges
  where routine_schema = 'public'
    and routine_name like 'm08\_%' escape '\'
    and grantee = 'PUBLIC'
    and privilege_type = 'EXECUTE';
  perform pg_temp.m08_stage_result(
    'no_public_execute_on_m08', v_public_execute = 0,
    format('public_execute_grants=%s', v_public_execute)
  );

  select count(*) into v_bad_search_path
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and (p.proname like 'm08\_%' escape '\' or p.proname like '\_m08\_%' escape '\')
    and p.prosecdef
    and not exists (
      select 1 from unnest(coalesce(p.proconfig, array[]::text[])) cfg
      where cfg like 'search_path=%'
    );
  perform pg_temp.m08_stage_result(
    'security_definer_search_path_set', v_bad_search_path = 0,
    format('missing_search_path=%s', v_bad_search_path)
  );

  perform pg_temp.m08_stage_result(
    'pets_owner_id_nullable',
    exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'pets'
        and column_name = 'owner_id' and is_nullable = 'YES'
    ),
    ''
  );
  perform pg_temp.m08_stage_result(
    'microchip_index_present',
    exists (
      select 1 from pg_indexes
      where schemaname = 'public' and indexname = 'pets_microchip_active_uniq'
    ),
    ''
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
  'mutating_behavioral_tests',
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
