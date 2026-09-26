insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260904220000', '1067_sec_p1_signed_url_window.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260904220000'
);

select version, name
  from supabase_migrations.schema_migrations
 where version in ('20260904180000', '20260904220000')
 order by version;
