select proname, operation_key
  from public.security_rate_limit_rpc_bindings
 order by 1;

select flag_key, enabled from public.security_feature_flags order by 1;

select role_code, permission_code
  from public.platform_role_permissions
 order by 1, 2;
