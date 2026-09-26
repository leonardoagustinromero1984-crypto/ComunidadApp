insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260905010000', '1068_social_saves_and_visible_post.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260905010000'
);

select version, name
  from supabase_migrations.schema_migrations
 where version in ('20260904220000', '20260905010000')
 order by version;
