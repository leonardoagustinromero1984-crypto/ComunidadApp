select c.relname
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relkind = 'r'
   and (
     c.relname ~* 'vitacora|import|person|profile|health'
     or c.relname in ('users','profiles','persons')
   )
 order by 1;
