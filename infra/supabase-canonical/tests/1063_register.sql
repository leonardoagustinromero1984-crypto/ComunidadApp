insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260902200000', '1063_sec03_rate_limits_quotas.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260902200000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260902200000';
