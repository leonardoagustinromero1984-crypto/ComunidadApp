-- LeoVer SEC-04 — backup readiness verification.
-- Read-only. No dump. No secrets. Staging or isolated restore target.
-- Usage:
--   npx supabase --workdir infra/supabase-canonical db query --linked -f scripts/security/verify-backup-readiness.sql

select
  (select count(*) from supabase_migrations.schema_migrations) as migration_rows,
  (select max(version) from supabase_migrations.schema_migrations) as max_version,
  (select count(*) from supabase_migrations.schema_migrations
    where version in (
      '20260902140000',
      '20260902180000',
      '20260902200000'
    )) as sec_01_03_registered;

select version
  from supabase_migrations.schema_migrations
 group by version
having count(*) > 1;

select
  exists(select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public' and c.relname = 'vitacora_public_number_seq') as vitacora_seq,
  exists(select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public' and c.relname = 'persons') as persons,
  exists(select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public' and c.relname = 'pets') as pets,
  exists(select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public' and c.relname = 'platform_admin_identities') as admin_identities,
  exists(select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public' and c.relname = 'security_rate_limit_policies') as rate_policies,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
         where n.nspname = 'public' and p.proname = '_canon_require_admin_aal2') as aal2_helper,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
         where n.nspname = 'public' and p.proname = 'canon_register_media') as media_rpc;

select last_value, is_called
  from public.vitacora_public_number_seq;

select id, public, file_size_limit
  from storage.buckets
 order by id;
