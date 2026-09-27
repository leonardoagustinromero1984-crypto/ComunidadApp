-- READ ONLY: auth tables, owners, PK, FKs, row counts. No secret values.
select c.relname as table_name,
       pg_get_userbyid(c.relowner) as table_owner,
       (select string_agg(a.attname, ',' order by a.attnum)
          from pg_index i
          join pg_attribute a on a.attrelid = i.indrelid and a.attnum = any (i.indkey)
         where i.indrelid = c.oid and i.indisprimary) as pk_cols,
       coalesce((
         select string_agg(
           format('%s -> %s.%s(%s)',
             con.conname,
             nf.nspname,
             cf.relname,
             (select string_agg(af.attname, ',' order by af.attnum)
                from unnest(con.confkey) with ordinality as k(attnum, ord)
                join pg_attribute af on af.attrelid = cf.oid and af.attnum = k.attnum)
           ),
           ' | '
           order by con.conname
         )
         from pg_constraint con
         join pg_class cf on cf.oid = con.confrelid
         join pg_namespace nf on nf.oid = cf.relnamespace
         where con.conrelid = c.oid and con.contype = 'f'
       ), '') as outgoing_fks,
       coalesce((
         select string_agg(
           format('%s.%s.%s',
             nsrc.nspname,
             csrc.relname,
             consrc.conname
           ),
           ' | '
           order by nsrc.nspname, csrc.relname
         )
         from pg_constraint consrc
         join pg_class csrc on csrc.oid = consrc.conrelid
         join pg_namespace nsrc on nsrc.oid = csrc.relnamespace
         where consrc.confrelid = c.oid and consrc.contype = 'f'
       ), '') as incoming_fks,
       exists (
         select 1
         from pg_constraint con
         join pg_class cf on cf.oid = con.confrelid
         join pg_namespace nf on nf.oid = cf.relnamespace
         where con.conrelid = c.oid
           and con.contype = 'f'
           and nf.nspname = 'auth'
           and cf.relname = 'users'
       ) as fk_to_auth_users,
       (xpath('//row/c/text()', query_to_xml(format('select count(*)::text as c from auth.%I', c.relname), false, true, '')))[1]::text::bigint as row_count
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'auth'
  and c.relkind = 'r'
order by c.relname;
