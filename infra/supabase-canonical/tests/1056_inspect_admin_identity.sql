select
  to_regclass('public.platform_admin_identities') is not null as table_exists,
  (select count(*) from public.platform_admin_identities) as identities,
  (select count(*) from public.platform_admin_identities where is_root) as roots,
  exists(
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'admin_begin_login'
  ) as begin_login_exists;
