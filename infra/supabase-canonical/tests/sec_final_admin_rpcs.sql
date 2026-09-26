select p.proname,
       pg_get_functiondef(p.oid) like '%_canon_require_admin_aal2%' as require_aal2,
       pg_get_functiondef(p.oid) like '%_canon_admin_aal%' as checks_aal
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and (
     p.proname like 'admin_%'
     or p.proname like 'staff_%'
     or p.proname like 'canon_admin_%'
     or p.proname like '%moderation%'
     or p.proname in (
       'triage_content_report',
       'assign_platform_role',
       'revoke_platform_role',
       'change_user_account_status',
       'list_moderation_queue',
       'get_moderation_report_for_staff'
     )
   )
   and p.proname not like '%\_impl'
 order by 1;
