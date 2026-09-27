insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260905020000', '1069_social_reel_pets_unfriend.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260905020000'
);

select version, name
  from supabase_migrations.schema_migrations
 where version in ('20260905010000', '20260905020000')
 order by version;
