-- SEC-04C RPC hardening sample. Names only.
select p.proname,
       p.prosecdef as security_definer,
       coalesce(p.proconfig, array[]::text[]) as proconfig
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in (
     '_canon_require_admin_aal2',
     '_canon_admin_aal',
     '_canon_consume_rate_limit',
     'get_admin_session',
     'has_permission',
     'admin_begin_login',
     'canon_register_media'
   )
 order by p.proname;
