select
  exists(select 1 from public.platform_roles where code = 'SUPPORT') as support_role,
  exists(select 1 from public.platform_permissions where code = 'staff.view') as staff_view,
  exists(select 1 from public.platform_permissions where code = 'staff.manage') as staff_manage,
  exists(select 1 from public.platform_permissions where code = 'catalogs.view') as catalogs_view,
  exists(select 1 from public.platform_permissions where code = 'catalogs.manage') as catalogs_manage,
  exists(
    select 1 from public.platform_role_permissions
    where role_code = 'SUPPORT' and permission_code = 'support.view'
  ) as support_has_support_view,
  exists(
    select 1 from public.platform_role_permissions
    where role_code = 'SUPPORT' and permission_code = 'moderation.view'
  ) as support_has_moderation,
  exists(
    select 1 from public.platform_role_permissions
    where role_code = 'ADMIN' and permission_code = 'catalogs.manage'
  ) as admin_has_catalogs,
  exists(
    select 1 from public.platform_role_permissions
    where role_code = 'ADMIN' and permission_code = 'staff.manage'
  ) as admin_has_staff,
  exists(
    select 1 from public.platform_role_permissions
    where role_code = 'SUPERADMIN' and permission_code = 'staff.manage'
  ) as superadmin_has_staff,
  to_regprocedure('public.list_admin_staff(text)') is not null as list_staff,
  to_regprocedure('public.staff_register_identity(uuid,text,text,text)') is not null as register_staff,
  to_regprocedure('public.canon_admin_list_service_categories()') is not null as list_service_categories,
  exists(
    select 1 from information_schema.columns
    where table_schema = 'public'
      and table_name = 'platform_admin_identities'
      and column_name = 'display_name'
  ) as display_name_column;
