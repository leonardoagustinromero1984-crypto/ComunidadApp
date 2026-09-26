insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260904180000', '1066_sec_p1_closure.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260904180000'
);

select version, name
  from supabase_migrations.schema_migrations
 where version in ('20260902220000', '20260904180000')
 order by version;
