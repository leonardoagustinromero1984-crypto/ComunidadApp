-- SEC-04C auth linkage. Counts only. No emails.
select
  (select count(*)::int from public.persons) as persons,
  (select count(*)::int from public.persons p
    where exists (select 1 from auth.users u where u.id = p.user_id)) as persons_auth_match,
  (select count(*)::int from public.platform_admin_identities) as admin_identities,
  (select count(*)::int from public.platform_admin_identities a
    where exists (select 1 from auth.users u where u.id = a.user_id)) as admin_auth_match,
  (select count(*)::int from public.user_platform_role_assignments) as role_assignments,
  (select count(*)::int from auth.users) as auth_users,
  (select count(*)::int from auth.identities) as auth_identities,
  (select count(*)::int from auth.mfa_factors) as mfa_factors,
  (select count(*)::int from auth.mfa_factors where status = 'verified') as mfa_verified;
