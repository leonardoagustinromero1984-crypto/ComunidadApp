-- SEC-05 inventory. No PII. Staging only.
select count(*) as migration_rows, max(version) as max_version
  from supabase_migrations.schema_migrations;

select count(*) as public_tables
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public' and c.relkind = 'r';
