select
  to_regclass('public.security_rate_limit_policies') as rate_policies,
  (select count(*) from public.security_rate_limit_policies) as rate_policy_rows,
  (select count(*) from public.security_rate_limit_policies
    where operation_key = 'signed_url.request') as signed_url_rows,
  to_regclass('public.security_rate_limit_windows') as rate_windows,
  to_regclass('public.security_rate_limit_rpc_bindings') as rpc_bindings,
  (select count(*) from public.security_rate_limit_rpc_bindings) as rpc_binding_rows;
