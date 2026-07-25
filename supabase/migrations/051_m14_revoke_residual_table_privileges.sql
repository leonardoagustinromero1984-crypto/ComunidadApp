-- LeoVer M14 hotfix — revoke residual client table privileges left after 050.
-- Forward-only. Does not alter tables, policies, RPC, or data.
-- 050 remains intact (already applied remotely).

begin;

revoke all privileges on table public.pet_passports
  from authenticated, anon;
revoke all privileges on table public.pet_passport_credentials
  from authenticated, anon;
revoke all privileges on table public.pet_passport_verification_requests
  from authenticated, anon;
revoke all privileges on table public.pet_passport_verification_decisions
  from authenticated, anon;
revoke all privileges on table public.pet_passport_status_history
  from authenticated, anon;

grant select on table public.pet_passports to authenticated;
grant select on table public.pet_passport_credentials to authenticated;
grant select on table public.pet_passport_verification_requests to authenticated;
grant select on table public.pet_passport_verification_decisions to authenticated;
grant select on table public.pet_passport_status_history to authenticated;

commit;
