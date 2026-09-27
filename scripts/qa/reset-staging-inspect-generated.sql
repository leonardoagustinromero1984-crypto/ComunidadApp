-- Read-only: generated / identity-always columns on tables the reset writes back.
select n.nspname as schema_name,
       c.relname as table_name,
       a.attname as column_name,
       a.attgenerated as generated_kind,
       a.attidentity as identity_kind
from pg_attribute a
join pg_class c on c.oid = a.attrelid
join pg_namespace n on n.oid = c.relnamespace
where a.attnum > 0
  and not a.attisdropped
  and (
    a.attgenerated <> ''
    or a.attidentity = 'a'
  )
  and (
    (n.nspname = 'auth' and c.relname in ('users', 'identities'))
    or (n.nspname = 'public' and c.relname in (
      'persons',
      'user_platform_role_assignments',
      'platform_admin_identities'
    ))
  )
order by 1, 2, 3;
