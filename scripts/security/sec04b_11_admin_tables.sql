select c.relname
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relkind = 'r'
   and c.relname ~* '(role|admin|staff|moderat|permission|profile|assign)'
 order by 1;
