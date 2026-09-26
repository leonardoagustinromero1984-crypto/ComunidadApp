select n.nspname as schema, c.relkind, count(*)::int as n
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname not in ('pg_catalog', 'information_schema', 'pg_toast')
   and n.nspname not like 'pg_temp%'
   and c.relkind in ('r', 'v', 'm', 'p')
 group by 1, 2
 order by 1, 2;
