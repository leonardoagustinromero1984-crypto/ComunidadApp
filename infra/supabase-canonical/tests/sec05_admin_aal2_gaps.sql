select p.proname,
       pg_get_functiondef(p.oid) like '%_canon_require_admin_aal2%' as has_aal2
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in (
     'list_moderation_queue',
     'get_moderation_report_for_staff',
     'triage_content_report',
     'assign_platform_role',
     'revoke_platform_role',
     'admin_search_users',
     'admin_list_audit',
     'staff_register_identity',
     'staff_set_role',
     'staff_reset_mfa',
     'get_admin_session',
     'has_permission',
     'canon_admin_list_species',
     'canon_admin_upsert_species',
     'canon_revoke_vitacora',
     'canon_hide_integration',
     'canon_create_proposal',
     'canon_record_vet_care'
   )
 order by 1;
