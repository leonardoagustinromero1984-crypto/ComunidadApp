insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260902140000', '1061_sec01_p0_hardening.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260902140000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260902140000';
