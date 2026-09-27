select operation_key, limit_count, window_seconds, fail_closed
  from public.security_rate_limit_policies
 order by operation_key;
