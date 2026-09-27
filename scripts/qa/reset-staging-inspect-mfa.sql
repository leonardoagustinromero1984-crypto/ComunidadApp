select
  (select count(*) from auth.mfa_factors) as mfa_factors,
  (select count(*) from auth.mfa_factors f
    where exists (
      select 1 from public.user_platform_role_assignments a
      where a.user_id = f.user_id
        and a.revoked_at is null
        and a.role_code in ('ADMIN', 'SUPERADMIN', 'MODERATOR', 'SUPPORT')
    )
    or exists (
      select 1 from public.platform_admin_identities i where i.user_id = f.user_id
    )) as mfa_factors_staff,
  (select count(*) from information_schema.columns
    where table_schema = 'auth' and table_name = 'webauthn_credentials') as webauthn_cols,
  (select string_agg(a.attname, ',' order by a.attnum)
     from pg_attribute a
     join pg_class c on c.oid = a.attrelid
     join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'auth' and c.relname = 'mfa_factors'
      and a.attnum > 0 and not a.attisdropped
      and (a.attgenerated <> '' or a.attidentity = 'a')) as mfa_generated;
