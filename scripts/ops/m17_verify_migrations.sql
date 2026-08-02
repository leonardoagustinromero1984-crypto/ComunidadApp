select version, name from supabase_migrations.schema_migrations
where version in ('054','055','056','057') order by version;
