-- 1067 SEC-P1-FINAL-CLEANUP
-- Restore signed_url.request to the SEC-03 approved STAGING policy:
-- 60 requests / 10 minutes, USER, fail-closed.
-- Does not edit 1063 or 1066. Does not change other commercial limits.

update public.security_rate_limit_policies
   set limit_count = 60,
       window_seconds = 600,
       fail_closed = true,
       updated_at = timezone('utc', now())
 where operation_key = 'signed_url.request';
