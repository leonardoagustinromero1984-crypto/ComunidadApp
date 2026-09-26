select
  (select count(*) from pg_tables
    where schemaname = 'public' and tablename = 'social_post_saves') as saves_table,
  (select relrowsecurity from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public' and c.relname = 'social_post_saves') as saves_rls,
  (select count(*) from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public'
     and p.proname in (
       'canon_toggle_save_social_post',
       'canon_list_saved_social_posts',
       'canon_list_saved_social_post_ids',
       'canon_get_visible_social_post'
     )) as save_rpcs;
