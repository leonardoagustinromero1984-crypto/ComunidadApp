-- READ ONLY: column names/types and counts for the four new auth tables.
-- Never selects token/secret values.
select n.nspname as schema_name,
       c.relname as table_name,
       a.attname as column_name,
       format_type(a.atttypid, a.atttypmod) as data_type,
       a.attnotnull as not_null
from pg_attribute a
join pg_class c on c.oid = a.attrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'auth'
  and c.relkind = 'r'
  and c.relname in (
    'mfa_recovery_code_sets',
    'mfa_recovery_codes',
    'scim_tokens',
    'scim_users'
  )
  and a.attnum > 0
  and not a.attisdropped
order by c.relname, a.attnum;
