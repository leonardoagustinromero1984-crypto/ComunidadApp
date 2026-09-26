select c.relname as table_name,
       (xpath('//row/c/text()', query_to_xml(format('select count(*)::text as c from public.%I', c.relname), false, true, '')))[1]::text::bigint as n
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
  and (
    c.relname like 'security_%'
    or c.relname like '%rate_limit%'
  )
order by 1;
