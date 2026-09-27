select
  (select count(*) from pg_tables
    where schemaname = 'public' and tablename = 'social_post_pets') as social_post_pets,
  (select relrowsecurity from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public' and c.relname = 'social_post_pets') as pets_rls,
  (select count(*) from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public'
     and p.proname in (
       'canon_register_media',
       '_canon_can_tag_pet',
       '_canon_attach_social_post_pets',
       'canon_create_social_post',
       'canon_remove_friendship'
     )) as social_rpcs,
  (select pg_get_functiondef(p.oid)
     from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'canon_register_media'
    limit 1) like '%FILE_TOO_LARGE%' as register_file_too_large;
