insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260905030000', '1070_support_tickets_canonical.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260905030000'
);

select version, name
  from supabase_migrations.schema_migrations
 where version in ('20260905020000', '20260905030000')
 order by version;
