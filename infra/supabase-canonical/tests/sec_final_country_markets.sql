select c.relname,
       c.relrowsecurity as rls,
       exists (
         select 1 from pg_policy p
          where p.polrelid = c.oid and p.polcmd = 'r'
       ) as has_select_policy,
       exists (
         select 1 from pg_policy p
          where p.polrelid = c.oid and p.polcmd in ('a','w','d','*')
       ) as has_write_policy
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relname = 'country_markets';
