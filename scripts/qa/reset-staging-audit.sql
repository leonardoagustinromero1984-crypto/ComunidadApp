-- LeoVer STAGING QA RESET — read-only live inventory.
-- Use scripts/qa/reset-staging.ps1 without -Execute for the orchestrated dry run.
-- Detailed counts: reset-staging-verify.sql
-- Sequence: reset-staging-sequence.sql

select n.nspname as schema_name,
       c.relname as table_name
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where c.relkind = 'r'
  and n.nspname in ('public', 'auth', 'storage')
order by 1, 2;
