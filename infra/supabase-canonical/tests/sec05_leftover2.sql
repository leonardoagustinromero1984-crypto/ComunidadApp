select
  (select count(*) from public.pets where name like 'SEC05-FIXTURE-%') as pets,
  (select count(*) from public.organizations where name like 'SEC05-FIXTURE-%') as orgs,
  (select count(*) from supabase_migrations.schema_migrations) as migration_rows,
  (select max(version) from supabase_migrations.schema_migrations) as max_version;
