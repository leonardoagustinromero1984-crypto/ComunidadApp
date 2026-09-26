select p.proname,
       pg_get_functiondef(p.oid) as def
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in (
     'admin_clear_must_change_password',
     'get_admin_session',
     'get_admin_auth_state',
     'has_permission',
     'get_my_permissions',
     'list_admin_staff',
     'get_admin_staff',
     'list_admin_staff_audit'
   )
 order by 1;
