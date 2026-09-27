select count(*) as migration_rows, max(version) as max_version
  from supabase_migrations.schema_migrations;
