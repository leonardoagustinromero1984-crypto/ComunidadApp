select
  (select count(*) from pg_tables
    where schemaname = 'public' and tablename = 'social_post_saves') as saves_table,
  (select count(*) from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public'
     and p.proname in (
       'canon_toggle_save_social_post',
       'canon_list_saved_social_posts',
       'canon_list_saved_social_post_ids',
       'canon_get_visible_social_post'
     )) as save_rpcs,
  (select limit_count from public.security_rate_limit_policies
    where operation_key = 'signed_url.request' limit 1) as signed_url_limit,
  (select window_seconds from public.security_rate_limit_policies
    where operation_key = 'signed_url.request' limit 1) as signed_url_window,
  (select fail_closed from public.security_rate_limit_policies
    where operation_key = 'signed_url.request' limit 1) as signed_url_fail_closed,
  (select version from supabase_migrations.schema_migrations order by version desc limit 1) as max_version;
