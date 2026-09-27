select nspname as schema
  from pg_namespace
 where nspname not in ('pg_catalog','information_schema','pg_toast')
   and nspname not like 'pg_temp%'
   and nspname not like 'pg_toast_temp%'
 order by 1;
