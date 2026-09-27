insert into supabase_migrations.schema_migrations (version, name, statements)
select '20260905040000', '1071_canonical_care_transfers.sql', array[]::text[]
where not exists (
  select 1 from supabase_migrations.schema_migrations where version = '20260905040000'
);

select version, name
  from supabase_migrations.schema_migrations
 where version in ('20260905030000', '20260905040000')
 order by version;
