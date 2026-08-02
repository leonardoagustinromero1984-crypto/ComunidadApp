select version from supabase_migrations.schema_migrations where version in ('060','061') order by version;
select table_name from information_schema.tables where table_schema = 'public' and table_name like 'm19_%' order by 1;
select proname from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and proname like 'm19_%' order by 1;
