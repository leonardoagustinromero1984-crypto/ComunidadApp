-- SEC-02 directed server checks. No secrets. Staging only.
select public._canon_admin_aal() as current_aal_without_jwt;

select
  (select relrowsecurity from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = 'country_markets') as country_markets_rls;

select exists (
  select 1 from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'get_admin_auth_state'
) as has_admin_auth_state;

select exists (
  select 1 from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = '_canon_require_admin_aal2'
) as has_aal2_helper;
