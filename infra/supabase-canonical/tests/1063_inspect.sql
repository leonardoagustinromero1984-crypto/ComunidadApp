select p.proname, pg_get_function_identity_arguments(p.oid) as args
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in (
     'canon_create_social_post',
     'canon_import_analyze',
     'canon_register_media',
     'canon_authorize_media_signed_url',
     'security_consume_rate_limit',
     'get_admin_session',
     'staff_register_identity'
   )
 order by 1, 2;

select operation_key, limit_count, window_seconds, scope_kind, fail_closed, exceed_code
  from public.security_rate_limit_policies
 order by operation_key;

select flag_key, enabled from public.security_feature_flags order by 1;

select has_table_privilege('authenticated', 'public.security_rate_limit_policies', 'INSERT') as pol_insert,
       has_table_privilege('authenticated', 'public.social_posts', 'INSERT') as posts_insert,
       has_function_privilege('anon', 'public._canon_consume_rate_limit(text,bigint)', 'EXECUTE') as helper_anon;
