-- Read-only tooling probe. Lists insertable vs excluded restore columns.
select n.nspname as schema_name,
       c.relname as table_name,
       a.attname as column_name,
       case
         when a.attgenerated <> '' then 'GENERATED'
         when a.attidentity = 'a' then 'IDENTITY_ALWAYS'
         else 'INSERTABLE'
       end as restore_mode
from pg_attribute a
join pg_class c on c.oid = a.attrelid
join pg_namespace n on n.oid = c.relnamespace
where a.attnum > 0
  and not a.attisdropped
  and (
    (n.nspname = 'auth' and c.relname in ('users', 'identities'))
    or (n.nspname = 'public' and c.relname in (
      'persons',
      'user_platform_role_assignments',
      'platform_admin_identities'
    ))
  )
  and (
    a.attgenerated <> ''
    or a.attidentity = 'a'
    or a.attname in ('id', 'user_id', 'email_confirmed_at', 'phone_confirmed_at', 'encrypted_password')
  )
order by 1, 2, 4, 3;
