select c.relname as table_name,
       has_table_privilege('anon', c.oid, 'SELECT') as anon_sel,
       has_table_privilege('anon', c.oid, 'INSERT') as anon_ins,
       has_table_privilege('authenticated', c.oid, 'SELECT') as auth_sel,
       has_table_privilege('authenticated', c.oid, 'INSERT') as auth_ins,
       has_table_privilege('authenticated', c.oid, 'UPDATE') as auth_upd,
       has_table_privilege('authenticated', c.oid, 'DELETE') as auth_del
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relkind = 'r'
   and (
     has_table_privilege('anon', c.oid, 'SELECT')
     or has_table_privilege('anon', c.oid, 'INSERT')
     or has_table_privilege('authenticated', c.oid, 'INSERT')
     or has_table_privilege('authenticated', c.oid, 'UPDATE')
     or has_table_privilege('authenticated', c.oid, 'DELETE')
   )
 order by 1;
