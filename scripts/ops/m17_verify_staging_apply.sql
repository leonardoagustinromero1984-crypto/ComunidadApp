-- Verificación post-aplicación M17 054/055 (solo lectura)
select version, name
from supabase_migrations.schema_migrations
where version in ('054', '055')
order by version;

select table_name
from information_schema.tables
where table_schema = 'public' and table_name like 'm17_%'
order by 1;

select count(*) as m17_table_count
from information_schema.tables
where table_schema = 'public' and table_name like 'm17_%';

select proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and proname like 'm17_%'
order by 1;

select tablename, rowsecurity
from pg_tables
where schemaname = 'public' and tablename like 'm17_%'
order by 1;

select pg_get_functiondef(p.oid) like '%platform_role_assignments%' as moderator_uses_wrong_table
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = '_m17_is_moderator';
