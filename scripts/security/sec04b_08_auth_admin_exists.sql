select to_regclass('auth.mfa_factors') is not null as has_mfa_factors,
       to_regclass('public.platform_admin_identities') is not null as has_platform_admin_identities,
       to_regclass('public.user_platform_role_assignments') is not null as has_role_assignments,
       to_regclass('public.platform_roles') is not null as has_platform_roles;
