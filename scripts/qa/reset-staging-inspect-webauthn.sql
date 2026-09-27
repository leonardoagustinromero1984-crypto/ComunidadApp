select a.attname as column_name
from pg_attribute a
join pg_class c on c.oid = a.attrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'auth'
  and c.relname = 'webauthn_credentials'
  and a.attnum > 0
  and not a.attisdropped
  and a.attname in ('user_id', 'id', 'friendly_name')
order by a.attnum;
