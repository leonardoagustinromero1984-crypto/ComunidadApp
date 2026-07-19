-- =============================================================================
-- LeoVer — diagnostico read-only de FAIL matriz staging M07
-- Alcance: internal_writers_anon_execute + org_hash_invitation_token
-- Solo SELECT / CTE. No muta datos ni privilegios.
-- NO USAR EN PRODUCCION. Sin credenciales ni project refs.
-- Pegar en Supabase SQL Editor del proyecto de pruebas.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- A) Writers internos inventariados por la matriz (misma lista v_internal)
-- ---------------------------------------------------------------------------
with internal_names(name) as (
  values
    ('m07_write_audit_event'),
    ('m07_validate_metadata'),
    ('m07_validate_metric_dimensions'),
    ('m07_sanitize_health_details'),
    ('m07_latest_metric_value'),
    ('m06_claim_outbox'),
    ('m06_claim_push_deliveries'),
    ('m07_record_metric'),
    ('_resolve_invitation_by_token')
),
procs as (
  select
    n.nspname as schema_name,
    p.proname as function_name,
    pg_get_function_identity_arguments(p.oid) as identity_args,
    pg_get_function_result(p.oid) as result_type,
    pg_get_userbyid(p.proowner) as owner_name,
    case when p.prosecdef then 'SECURITY DEFINER' else 'SECURITY INVOKER' end as security_mode,
    case p.provolatile
      when 'i' then 'IMMUTABLE'
      when 's' then 'STABLE'
      when 'v' then 'VOLATILE'
      else p.provolatile::text
    end as volatility,
    coalesce(array_to_string(p.proconfig, '; '), '<unset>') as proconfig,
    p.oid
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join internal_names i on i.name = p.proname
  where n.nspname = 'public'
)
select
  'A_internal_writers'::text as section,
  schema_name,
  function_name,
  identity_args,
  owner_name,
  security_mode,
  volatility,
  proconfig as search_path_or_config,
  has_function_privilege('PUBLIC', oid, 'EXECUTE') as public_execute_effective,
  has_function_privilege('anon', oid, 'EXECUTE') as anon_execute_effective,
  has_function_privilege('authenticated', oid, 'EXECUTE') as authenticated_execute_effective,
  has_function_privilege('service_role', oid, 'EXECUTE') as service_role_execute_effective
from procs
order by function_name, identity_args;

-- ---------------------------------------------------------------------------
-- B) Grants DIRECTOS en information_schema (PUBLIC / anon / authenticated / service_role)
-- ---------------------------------------------------------------------------
with internal_names(name) as (
  values
    ('m07_write_audit_event'),
    ('m07_validate_metadata'),
    ('m07_validate_metric_dimensions'),
    ('m07_sanitize_health_details'),
    ('m07_latest_metric_value'),
    ('m06_claim_outbox'),
    ('m06_claim_push_deliveries'),
    ('m07_record_metric'),
    ('_resolve_invitation_by_token')
)
select
  'B_direct_grants'::text as section,
  r.specific_schema,
  r.routine_name,
  r.specific_name,
  r.grantee,
  r.privilege_type,
  r.is_grantable
from information_schema.routine_privileges r
join internal_names i on i.name = r.routine_name
where r.specific_schema = 'public'
  and r.privilege_type = 'EXECUTE'
  and r.grantee in ('PUBLIC', 'anon', 'authenticated', 'service_role', 'postgres')
order by r.routine_name, r.grantee;

-- ---------------------------------------------------------------------------
-- C) Quien explica internal_writers_anon_execute = 1 (conteo matriz)
-- ---------------------------------------------------------------------------
with internal_names(name) as (
  values
    ('m07_write_audit_event'),
    ('m07_validate_metadata'),
    ('m07_validate_metric_dimensions'),
    ('m07_sanitize_health_details'),
    ('m07_latest_metric_value'),
    ('m06_claim_outbox'),
    ('m06_claim_push_deliveries'),
    ('m07_record_metric'),
    ('_resolve_invitation_by_token')
),
anon_rows as (
  select
    r.routine_name,
    r.specific_name,
    r.grantee,
    r.privilege_type
  from information_schema.routine_privileges r
  join internal_names i on i.name = r.routine_name
  where r.specific_schema = 'public'
    and r.grantee = 'anon'
    and r.privilege_type = 'EXECUTE'
)
select
  'C_anon_execute_hits'::text as section,
  (select count(*) from anon_rows) as matrix_style_count,
  routine_name,
  specific_name,
  grantee,
  privilege_type,
  'probable causa del FAIL internal_writers_anon_execute'::text as note
from anon_rows
order by routine_name;

-- ---------------------------------------------------------------------------
-- D) org_hash_invitation_token — metadatos, grants, definicion
-- ---------------------------------------------------------------------------
select
  'D_org_hash_meta'::text as section,
  n.nspname as schema_name,
  p.proname as function_name,
  pg_get_function_identity_arguments(p.oid) as identity_args,
  pg_get_userbyid(p.proowner) as owner_name,
  case when p.prosecdef then 'SECURITY DEFINER' else 'SECURITY INVOKER' end as security_mode,
  case p.provolatile
    when 'i' then 'IMMUTABLE'
    when 's' then 'STABLE'
    when 'v' then 'VOLATILE'
    else p.provolatile::text
  end as volatility,
  coalesce(array_to_string(p.proconfig, '; '), '<unset>') as proconfig,
  has_function_privilege('PUBLIC', p.oid, 'EXECUTE') as public_execute_effective,
  has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute_effective,
  has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute_effective,
  has_function_privilege('service_role', p.oid, 'EXECUTE') as service_role_execute_effective,
  (pg_get_functiondef(p.oid) like '%extensions.digest%') as uses_extensions_digest,
  (replace(pg_get_functiondef(p.oid), 'extensions.digest', 'X') like '%digest(%') as has_bare_digest_after_strip
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname = 'org_hash_invitation_token';

select
  'D_org_hash_definition'::text as section,
  pg_get_functiondef(p.oid) as function_definition
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname = 'org_hash_invitation_token';

select
  'D_org_hash_direct_grants'::text as section,
  r.grantee,
  r.privilege_type,
  r.is_grantable
from information_schema.routine_privileges r
where r.specific_schema = 'public'
  and r.routine_name = 'org_hash_invitation_token'
  and r.privilege_type = 'EXECUTE'
order by r.grantee;

-- ---------------------------------------------------------------------------
-- E) Callers inferidos (definiciones en public que mencionan el nombre)
-- ---------------------------------------------------------------------------
select
  'E_callers_of_org_hash'::text as section,
  n.nspname as caller_schema,
  p.proname as caller_name,
  pg_get_function_identity_arguments(p.oid) as caller_args,
  case when p.prosecdef then 'SECURITY DEFINER' else 'SECURITY INVOKER' end as caller_security
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname <> 'org_hash_invitation_token'
  and pg_get_functiondef(p.oid) ilike '%org_hash_invitation_token%'
order by p.proname;

-- ---------------------------------------------------------------------------
-- F) Resumen clasificacion preliminar (evidencia de repo + filas A–E)
-- ---------------------------------------------------------------------------
select * from (
  values
    (
      'internal_writers_anon_execute',
      '1 (matriz)',
      'DEFECTO_REAL',
      'Probable: _resolve_invitation_by_token — REVOKE solo FROM PUBLIC en 020/033; no REVOKE FROM anon. Confirmar en seccion C.'
    ),
    (
      'org_hash_invitation_token(p_token text)_security_definer',
      'INVOKER',
      'FALSO_POSITIVO',
      'Helper IMMUTABLE SQL: solo extensions.digest; no lee/escribe tablas. INVOKER es la opcion correcta. Matriz exige DEFINER a todo v_names sin respaldo.'
    ),
    (
      'org_hash_invitation_token_search_path',
      '<unset>',
      'FALSO_POSITIVO',
      'digest calificado como extensions.digest; search_path=public no es requisito funcional. Hardening opcional SET search_path=public no bloqueante.'
    )
) as s(check_name, actual, classification_candidate, details);
