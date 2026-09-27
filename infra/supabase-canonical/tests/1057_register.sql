insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260901140000', '1057_admin_staff_and_catalogs.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260901140000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260901140000';
