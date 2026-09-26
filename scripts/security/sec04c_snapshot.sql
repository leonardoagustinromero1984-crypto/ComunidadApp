-- SEC-04C single-row snapshot so CLI returns everything.
select
  (select count(*)::int from supabase_migrations.schema_migrations) as migration_rows,
  (select max(version) from supabase_migrations.schema_migrations) as max_version,
  (select count(*)::int from supabase_migrations.schema_migrations
    where version in ('20260902140000','20260902180000','20260902200000','20260902210000','20260902220000')) as sec_1061_1065,
  (select count(*)::int from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r') as public_tables,
  (select count(*)::int from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r' and c.relrowsecurity) as rls_on,
  (select count(*)::int from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r' and not c.relrowsecurity) as rls_off,
  (select count(*)::int from pg_policy pol join pg_class c on c.oid=pol.polrelid
    join pg_namespace n on n.oid=c.relnamespace where n.nspname='public') as public_policies,
  (select count(*)::int from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public') as public_functions,
  (select count(*)::int from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.prosecdef) as security_definer_fns,
  (select count(*)::int from pg_constraint con join pg_class c on c.oid=con.conrelid
    join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and con.contype='f') as public_fks,
  (select relrowsecurity from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relname='country_markets') as country_markets_rls,
  exists(select 1 from pg_extension where extname='postgis') as has_postgis,
  (select n.nspname from pg_extension e join pg_namespace n on n.oid=e.extnamespace
    where e.extname='postgis') as postgis_schema,
  has_table_privilege('anon','public.country_markets','INSERT') as anon_insert_country_markets,
  has_table_privilege('anon','public.platform_admin_identities','SELECT') as anon_select_admin_identities,
  has_table_privilege('authenticated','public.platform_admin_identities','INSERT') as authn_insert_admin_identities,
  (select last_value from public.vitacora_public_number_seq) as seq_last_value,
  (select is_called from public.vitacora_public_number_seq) as seq_is_called,
  (select coalesce(max(public_vitacora_number),0) from public.vitacora_profiles) as max_vitacora_number,
  (select count(*)::int from public.platform_roles) as platform_roles,
  (select count(*)::int from public.platform_permissions) as platform_permissions,
  (select count(*)::int from public.platform_role_permissions) as role_permissions;
