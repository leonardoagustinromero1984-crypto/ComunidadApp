-- M18 remote validation prep (058) — structural checks only; NO auto-apply
-- Entorno: staging autorizado. NO producción sin autorización explícita.

\set ON_ERROR_STOP on

DO $$ BEGIN
  RAISE NOTICE 'M18 validation 058 — structural prep';
END $$;

-- 1. schema_migrations
SELECT CASE WHEN EXISTS (
  SELECT 1 FROM public.schema_migrations WHERE version = '058'
) THEN 'INFO: 058 already registered' ELSE 'EXPECT: 058 not yet registered' END AS migration_058_status;

-- 2. Expected tables (fail if missing after apply)
SELECT tablename FROM pg_tables
WHERE schemaname = 'public' AND tablename LIKE 'm18_%'
ORDER BY tablename;

-- 3. RPC smoke names
SELECT proname FROM pg_proc p
JOIN pg_namespace n ON p.pronamespace = n.oid
WHERE n.nspname = 'public' AND proname LIKE 'm18_%'
ORDER BY proname;

-- 4. RLS enabled check (post-apply)
SELECT c.relname, c.relrowsecurity
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relname LIKE 'm18_%';

-- Rollback note: DROP only in staging test; preserve registrations for audit in real rollback plan.
