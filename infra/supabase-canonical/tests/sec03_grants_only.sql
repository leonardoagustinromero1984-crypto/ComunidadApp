select
  has_function_privilege('anon', 'public.get_admin_session()', 'EXECUTE') as anon_admin_session,
  has_function_privilege('anon', 'public._canon_require_admin_aal2()', 'EXECUTE') as anon_aal2,
  has_function_privilege('anon', 'public._canon_consume_rate_limit(text,bigint)', 'EXECUTE') as anon_consume,
  has_table_privilege('anon', 'public.security_rate_limit_policies', 'SELECT') as anon_policies,
  has_table_privilege('authenticated', 'public.social_posts', 'INSERT') as auth_posts_insert,
  has_table_privilege('authenticated', 'public.content_reports', 'INSERT') as auth_reports_insert;
