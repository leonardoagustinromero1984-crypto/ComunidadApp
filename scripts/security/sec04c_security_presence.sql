-- SEC-04C security control presence. Structure only.
select
  (select count(*)::int from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r' and c.relrowsecurity) as rls_on,
  (select count(*)::int from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r' and not c.relrowsecurity) as rls_off,
  (select relrowsecurity from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relname='country_markets') as country_markets_rls,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='_canon_require_admin_aal2') as aal2_wrapper,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='_canon_admin_aal') as admin_aal,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='_canon_consume_rate_limit') as consume_rate,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='admin_begin_login') as admin_begin_login,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='get_admin_session') as get_admin_session,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='public' and p.proname='has_permission') as has_permission,
  (select prosecdef from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname='_canon_require_admin_aal2' limit 1) as aal2_security_definer,
  (select count(*)::int from public.security_rate_limit_policies) as rate_policies,
  (select count(*)::int from public.security_feature_flags) as feature_flags,
  (select count(*)::int from public.platform_roles) as platform_roles,
  (select count(*)::int from public.platform_permissions) as platform_permissions;

select last_value, is_called
  from public.vitacora_public_number_seq;

select coalesce(max(public_vitacora_number),0) as max_public_vitacora_number
  from public.vitacora_profiles;
