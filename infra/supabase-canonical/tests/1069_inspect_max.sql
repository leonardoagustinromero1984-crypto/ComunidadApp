select
  (select version from supabase_migrations.schema_migrations order by version desc limit 1) as max_version,
  (select name from supabase_migrations.schema_migrations order by version desc limit 1) as max_name,
  (select count(*) from supabase_migrations.schema_migrations where version = '20260905010000') as has_1068,
  (select count(*) from supabase_migrations.schema_migrations where version = '20260905020000') as has_1069,
  (select count(*) from supabase_migrations.schema_migrations where version = '20260905030000') as has_1070,
  to_regclass('public.social_post_pets') as social_post_pets,
  to_regclass('public.support_ticket_messages') as support_ticket_messages,
  (select count(*) from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'canon_remove_friendship') as remove_fn;
