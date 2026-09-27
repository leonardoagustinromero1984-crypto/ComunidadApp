select count(*) as migration_rows,
       max(version) as max_version
  from supabase_migrations.schema_migrations;

select last_value as vitacora_seq_last, is_called
  from public.vitacora_public_number_seq;
