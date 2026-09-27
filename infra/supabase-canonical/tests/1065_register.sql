insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260902220000', '1065_sec_final_least_privilege.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260902220000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260902220000';
