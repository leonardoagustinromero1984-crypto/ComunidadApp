-- SEC-04C post-restore verification. Read-only. No PII.
-- Run on TARGET (repo-root --linked) and STAGING (canonical --linked) separately.

select current_database() as db;

select
  (select count(*) from pg_namespace where nspname not in ('pg_catalog','information_schema','pg_toast','pg_temp_1','pg_toast_temp_1')) as schema_count,
  (select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r') as public_tables,
  (select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r' and not c.relrowsecurity) as public_rls_off,
  (select count(*) from pg_policy pol join pg_class c on c.oid=pol.polrelid
    join pg_namespace n on n.oid=c.relnamespace where n.nspname='public') as public_policies,
  (select count(*) from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public') as public_functions,
  (select count(*) from pg_constraint con join pg_class c on c.oid=con.conrelid
    join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and con.contype='f') as public_fks;

select version, name
  from supabase_migrations.schema_migrations
 where version in (
   '20260902140000','20260902180000','20260902200000','20260902210000','20260902220000'
 )
 order by version;

select count(*)::int as migration_rows,
       max(version) as max_version
  from supabase_migrations.schema_migrations;

select
  to_regclass('public.persons') is not null as has_persons,
  to_regclass('public.pets') is not null as has_pets,
  to_regclass('public.platform_admin_identities') is not null as has_admin,
  to_regclass('public.service_providers') is not null as has_service_providers,
  to_regclass('public.lost_found_alerts') is not null as has_lost_found,
  to_regclass('public.security_rate_limit_policies') is not null as has_rate,
  to_regclass('public.security_feature_flags') is not null as has_flags,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='_canon_require_admin_aal2') as has_aal2,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='_canon_consume_rate_limit') as has_rate_fn,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='get_admin_session') as has_admin_session;
