insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260901200000', '1059_species_secondary_health_scope.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260901200000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260901200000';
