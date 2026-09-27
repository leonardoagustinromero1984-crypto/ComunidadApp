select c.relname,
       greatest(c.reltuples::bigint, 0) as approx_rows
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relkind = 'r'
   and c.relname ~* '(person|profile|pet|vitacora|post|message|org|admin|import|report|staff|user|auth|conversation|media|storage)'
 order by 1;
