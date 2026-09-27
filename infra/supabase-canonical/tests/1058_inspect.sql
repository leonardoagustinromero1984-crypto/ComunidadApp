select
  to_regprocedure('public.canon_get_breed(uuid)') is not null as get_breed,
  exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'breeds' and policyname = 'breeds_read'
  ) as breeds_active_select,
  exists (
    select 1 from public.platform_role_permissions
    where role_code = 'SUPERADMIN' and permission_code = 'staff.manage'
  ) as superadmin_staff,
  exists (
    select 1 from public.platform_role_permissions
    where role_code = 'ADMIN' and permission_code = 'staff.manage'
  ) as admin_staff,
  exists (
    select 1 from public.platform_role_permissions
    where role_code = 'MODERATOR' and permission_code = 'staff.manage'
  ) as moderator_staff,
  exists (
    select 1 from public.platform_role_permissions
    where role_code = 'SUPPORT' and permission_code = 'staff.manage'
  ) as support_staff;
