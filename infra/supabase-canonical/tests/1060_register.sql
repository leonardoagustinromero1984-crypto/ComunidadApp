insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260902120000', '1060_staff_register_role_code.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260902120000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260902120000';
