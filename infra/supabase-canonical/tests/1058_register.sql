insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260901180000', '1058_resolve_historical_breed.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260901180000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260901180000';
