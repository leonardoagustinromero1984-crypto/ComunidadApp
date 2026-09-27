select
  count(*) filter (where not c.relrowsecurity) as rls_off,
  count(*) as public_tables
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public' and c.relkind = 'r';
