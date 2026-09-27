select p.proname,
       pg_get_functiondef(p.oid) like '%_canon_require_admin_aal2%' as require_aal2,
       pg_get_functiondef(p.oid) like '%_canon_admin_aal%' as checks_aal
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in (
     'list_admin_staff',
     'get_admin_staff',
     'list_admin_staff_audit',
     'admin_clear_must_change_password'
   )
 order by 1;
