insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260831180000', '1056_admin_technical_identity.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260831180000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260831180000';
