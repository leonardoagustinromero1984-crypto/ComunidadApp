-- =============================================================================
-- LeoVer / ComunidadApp
-- DEVELOPMENT ONLY
-- DESTRUCTIVE
-- DO NOT RUN IN PRODUCTION
--
-- DO NOT USE FOR FULL RESET on the current shared backend
-- (project ref wystsapjfpdtoprlmizz — Android + leover.com.ar).
-- That environment has real users. Use scripts/dev-clean-qa-data.sql.
-- This file is only for a future disposable DEV/STAGING project.
--
-- Reset de datos de desarrollo / staging. NO es una migración numerada.
-- NO borra 001–082 ni recrea schema. SCHEMA se conserva; solo se limpian datos.
-- NO autoejecutar en CI, deploy, db push ni seed de producción.
--
-- Procedimiento:
-- 1) Ejecutar scripts/dev-reset-inventory.sql (solo lectura) y guardar conteos.
-- 2) Confirmar TARGET_ENV = DEV o STAGING y el project ref en Dashboard.
-- 3) Confirmar que NO hay datos reales que deban conservarse.
-- 4) En ESTA misma sesión, descomentar el bloque CONFIRM y ejecutar el resto.
--
-- Por defecto este archivo SE DETIENE antes de borrar.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- A. PRESERVAR — infraestructura / catálogo
-- ---------------------------------------------------------------------------
-- supabase_migrations.schema_migrations
-- public.platform_roles
-- public.permissions
-- public.role_permissions
-- public.organization_roles
-- public.organization_permissions
-- public.organization_role_permissions
-- public.reserved_usernames
-- public.file_retention_policies
-- public.observability_event_catalog
-- public.observability_retention_policies
-- public.m07_metric_catalog
-- public.m07_health_check_catalog
-- public.veterinary_care_type_catalog
-- public.m27_api_contracts
-- public.m27_rate_limit_quotas
-- public.alert_rules
-- storage.buckets (se conservan; se pueden borrar objects)
-- M29: no hay tablas SQL
-- Catálogo de ubicaciones: seed Android (ArgentinaLocationSeed), no tabla SQL

-- ---------------------------------------------------------------------------
-- B. LIMPIAR — datos de usuario / prueba
--   Todas las tablas public que no estén en A ni C.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- C. REVISAR MANUALMENTE
-- public.users                  (se conservan ADMIN/SUPERADMIN; el resto se borra)
-- public.user_role_assignments  (se conservan asignaciones admin activas)
-- auth.users                    (NO se borra automáticamente)
-- =============================================================================

-- Paso 4 (misma sesión, DESPUÉS del inventario y de confirmar project ref):
-- select set_config('leover.dev_reset.env', 'STAGING', false);
-- select set_config('leover.dev_reset.confirm', 'LEOVER-DEV-RESET-CONFIRM', false);

do $$
begin
  if current_setting('leover.dev_reset.confirm', true)
       is distinct from 'LEOVER-DEV-RESET-CONFIRM'
     or current_setting('leover.dev_reset.env', true) not in ('DEV', 'STAGING')
  then
    raise exception using
      message = 'LEOVER DEV RESET BLOCKED',
      detail = 'Este script es destructivo. Primero corré scripts/dev-reset-inventory.sql. '
               || 'Si el entorno es DEV/STAGING confirmado, en ESTA sesión ejecutá: '
               || 'select set_config(''leover.dev_reset.env'', ''STAGING'', false); '
               || 'select set_config(''leover.dev_reset.confirm'', ''LEOVER-DEV-RESET-CONFIRM'', false); '
               || 'y volvé a correr este archivo. Nunca en producción.',
      hint = 'NO se borró nada.';
  end if;
end $$;

begin;

create temporary table _leover_keep_admins (
  user_id uuid primary key,
  email text,
  role_code text not null
) on commit drop;

insert into _leover_keep_admins (user_id, email, role_code)
select distinct u.id, au.email, r.code
from public.users u
join auth.users au on au.id = u.id
join public.user_role_assignments a on a.user_id = u.id and a.revoked_at is null
join public.platform_roles r on r.id = a.role_id
where r.code in ('ADMIN', 'SUPERADMIN');

do $$
declare
  stmt text;
  preserve text[] := array[
    'platform_roles',
    'permissions',
    'role_permissions',
    'organization_roles',
    'organization_permissions',
    'organization_role_permissions',
    'reserved_usernames',
    'file_retention_policies',
    'observability_event_catalog',
    'observability_retention_policies',
    'm07_metric_catalog',
    'm07_health_check_catalog',
    'veterinary_care_type_catalog',
    'm27_api_contracts',
    'm27_rate_limit_quotas',
    'alert_rules',
    'users',
    'user_role_assignments'
  ];
begin
  select string_agg(format('%I.%I', n.nspname, c.relname), ', ' order by c.relname)
    into stmt
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relkind = 'r'
    and not c.relispartition
    and c.relname <> all (preserve);

  if stmt is null then
    raise notice 'No operational public tables to truncate';
  else
    execute 'truncate table ' || stmt || ' restart identity cascade';
  end if;
end $$;

-- Perfiles de prueba: se eliminan. Admins se conservan y se normalizan a PERSON.
delete from public.user_role_assignments a
where not exists (
  select 1 from _leover_keep_admins k where k.user_id = a.user_id
);

delete from public.users u
where not exists (
  select 1 from _leover_keep_admins k where k.user_id = u.id
);

update public.users
set account_type = 'PERSON'
where coalesce(account_type, 'PERSON') <> 'PERSON';

-- Reasegurar asignaciones admin (por si CASCADE las tocó)
insert into public.user_role_assignments (user_id, role_id, assigned_by)
select k.user_id, r.id, k.user_id
from _leover_keep_admins k
join public.platform_roles r on r.code = k.role_code
where not exists (
  select 1
  from public.user_role_assignments a
  where a.user_id = k.user_id
    and a.role_id = r.id
    and a.revoked_at is null
);

-- Storage de prueba. No se borran buckets ni objetos de marca (no hay bucket de marca).
delete from storage.objects
where bucket_id in (
  'profile-avatars',
  'organization-media',
  'public-media',
  'organization-documents',
  'moderation-evidence',
  'support-attachments',
  'leover'
);

-- auth.users NO se toca. Los auth huérfanos de prueba se revisan en Dashboard.

commit;

select
  (select count(*) from auth.users) as auth_users_remaining,
  (select count(*) from public.users) as public_users_remaining,
  (select count(*) from public.organizations) as organizations_remaining,
  (select count(*) from public.pets) as pets_remaining,
  (select count(*) from public.platform_roles) as platform_roles_remaining,
  (select count(*) from public.organization_roles) as organization_roles_remaining,
  (select count(*) from storage.objects) as storage_objects_remaining;
