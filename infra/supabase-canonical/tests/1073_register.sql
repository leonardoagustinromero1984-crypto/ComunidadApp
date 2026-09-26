insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260905180000', '1073_care_inbox_and_media_origin.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260905180000'
);

select version, name
  from supabase_migrations.schema_migrations
 where version in ('20260905150000', '20260905180000')
 order by version;
