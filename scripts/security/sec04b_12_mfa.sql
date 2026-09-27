select count(*)::int as mfa_factors,
       count(*) filter (where status = 'verified')::int as mfa_verified
  from auth.mfa_factors;
