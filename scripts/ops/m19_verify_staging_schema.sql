-- Verificación rápida staging M19 (060/061) — solo lectura
select version from supabase_migrations.schema_migrations
where version in ('060','061') order by version;

select table_name from information_schema.tables
where table_schema = 'public' and table_name like 'm19_%' order by 1;

select proname from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and proname in (
  'm19_list_public_feed_page','m19_archive_post','m19_moderate_post',
  'm19_edit_comment','m19_archive_comment','m19_add_reaction'
) order by 1;

select relname, relrowsecurity from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and relname like 'm19_%' order by 1;
