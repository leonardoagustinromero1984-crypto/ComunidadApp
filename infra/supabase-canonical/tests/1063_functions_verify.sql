select p.proname, pg_get_function_identity_arguments(p.oid) as args
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and (
     p.proname in (
       'canon_create_social_post',
       'canon_import_analyze',
       'canon_register_media',
       'canon_authorize_media_signed_url',
       'security_consume_rate_limit',
       'get_admin_session',
       'staff_register_identity'
     )
     or p.proname like '%\\_sec03\\_impl' escape '\'
   )
 order by 1, 2;
