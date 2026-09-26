insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260902210000', '1064_sec05_authz_gaps.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260902210000'
);

select version, name from supabase_migrations.schema_migrations
where version = '20260902210000';
