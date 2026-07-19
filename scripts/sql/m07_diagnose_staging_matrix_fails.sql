-- =============================================================================
-- LeoVer — diagnostico read-only de FAIL matriz staging M07 (v2)
-- Alcance: internal_writers_anon_execute + org_hash_invitation_token
-- Solo SELECT / CTE. No muta datos ni privilegios.
-- NO USAR EN PRODUCCION. Sin credenciales ni project refs.
-- Pegar en Supabase SQL Editor del proyecto de pruebas.
--
-- Nota: PUBLIC es pseudorol; NO usar has_function_privilege('PUBLIC', ...).
-- PUBLIC EXECUTE se detecta con aclexplode(...).grantee = 0.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- A) Writers internos: metadatos + ACL (PUBLIC / anon directo / efectivo)
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
    pg_get_userbyid(p.proowner) as owner_name,
    case when p.prosecdef then 'SECURITY DEFINER' else 'SECURITY INVOKER' end as security_mode,
    case p.provolatile
      when 'i' then 'IMMUTABLE'
      when 's' then 'STABLE'
      when 'v' then 'VOLATILE'
      else p.provolatile::text
    end as volatility,
    coalesce(array_to_string(p.proconfig, '; '), '<unset>') as search_path_or_config,
    p.oid,
    p.proowner,
    p.proacl
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join internal_names i on i.name = p.proname
  where n.nspname = 'public'
),
acl_flags as (
  select
    pr.*,
    exists (
      select 1
      from aclexplode(coalesce(pr.proacl, acldefault('f', pr.proowner))) as acl
      where acl.grantee = 0
        and acl.privilege_type = 'EXECUTE'
    ) as public_execute,
    exists (
      select 1
      from aclexplode(coalesce(pr.proacl, acldefault('f', pr.proowner))) as acl
      join pg_roles r on r.oid = acl.grantee
      where r.rolname = 'anon'
        and acl.privilege_type = 'EXECUTE'
    ) as anon_execute_direct,
    has_function_privilege('anon', pr.oid, 'EXECUTE') as anon_execute_effective,
    has_function_privilege('authenticated', pr.oid, 'EXECUTE') as authenticated_execute_effective,
    has_function_privilege('service_role', pr.oid, 'EXECUTE') as service_role_execute_effective
  from procs pr
)
select
  'A_internal_writers'::text as section,
  schema_name,
  function_name,
  identity_args,
  owner_name,
  security_mode,
  volatility,
  search_path_or_config,
  public_execute,
  anon_execute_direct,
  anon_execute_effective,
  (public_execute and anon_execute_effective and not anon_execute_direct) as anon_execute_via_public,
  authenticated_execute_effective,
  service_role_execute_effective
from acl_flags
order by function_name, identity_args;

-- ---------------------------------------------------------------------------
-- B) Grants DIRECTOS via ACL explode + information_schema (referencia)
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
acl_direct as (
  select
    n.nspname as schema_name,
    p.proname as function_name,
    pg_get_function_identity_arguments(p.oid) as identity_args,
    case
      when acl.grantee = 0 then 'PUBLIC'
      else coalesce((select r.rolname from pg_roles r where r.oid = acl.grantee), acl.grantee::text)
    end as grantee_label,
    acl.privilege_type,
    acl.is_grantable
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join internal_names i on i.name = p.proname
  cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) as acl
  where n.nspname = 'public'
    and acl.privilege_type = 'EXECUTE'
)
select
  'B_acl_direct_execute'::text as section,
  schema_name,
  function_name,
  identity_args,
  grantee_label,
  privilege_type,
  is_grantable
from acl_direct
where grantee_label in ('PUBLIC', 'anon', 'authenticated', 'service_role', 'postgres')
order by function_name, grantee_label;

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
  'B_information_schema_direct'::text as section,
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
-- C) Quien explica internal_writers_anon_execute = 1 (conteo estilo matriz)
--     + vista ACL (directo / efectivo / via PUBLIC)
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
  'C_anon_information_schema_hits'::text as section,
  (select count(*) from anon_rows) as matrix_style_count,
  routine_name,
  specific_name,
  grantee,
  privilege_type,
  'filas que cuentan en internal_writers_anon_execute'::text as note
from anon_rows
order by routine_name;

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
acl_anon as (
  select
    p.proname as function_name,
    pg_get_function_identity_arguments(p.oid) as identity_args,
    exists (
      select 1
      from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) as acl
      where acl.grantee = 0
        and acl.privilege_type = 'EXECUTE'
    ) as public_execute,
    exists (
      select 1
      from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) as acl
      join pg_roles r on r.oid = acl.grantee
      where r.rolname = 'anon'
        and acl.privilege_type = 'EXECUTE'
    ) as anon_execute_direct,
    has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute_effective
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join internal_names i on i.name = p.proname
  where n.nspname = 'public'
)
select
  'C_anon_acl_breakdown'::text as section,
  function_name,
  identity_args,
  public_execute,
  anon_execute_direct,
  anon_execute_effective,
  (public_execute and anon_execute_effective and not anon_execute_direct) as anon_execute_via_public
from acl_anon
where anon_execute_effective or anon_execute_direct or public_execute
order by function_name, identity_args;

-- ---------------------------------------------------------------------------
-- D) org_hash_invitation_token — metadatos, ACL, definicion
-- ---------------------------------------------------------------------------
with org_hash as (
  select
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
    coalesce(array_to_string(p.proconfig, '; '), '<unset>') as search_path_or_config,
    p.oid,
    p.proowner,
    p.proacl,
    pg_get_functiondef(p.oid) as function_definition
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname = 'org_hash_invitation_token'
),
org_hash_acl as (
  select
    oh.*,
    exists (
      select 1
      from aclexplode(coalesce(oh.proacl, acldefault('f', oh.proowner))) as acl
      where acl.grantee = 0
        and acl.privilege_type = 'EXECUTE'
    ) as public_execute,
    exists (
      select 1
      from aclexplode(coalesce(oh.proacl, acldefault('f', oh.proowner))) as acl
      join pg_roles r on r.oid = acl.grantee
      where r.rolname = 'anon'
        and acl.privilege_type = 'EXECUTE'
    ) as anon_execute_direct,
    has_function_privilege('anon', oh.oid, 'EXECUTE') as anon_execute_effective,
    has_function_privilege('authenticated', oh.oid, 'EXECUTE') as authenticated_execute_effective,
    has_function_privilege('service_role', oh.oid, 'EXECUTE') as service_role_execute_effective
  from org_hash oh
)
select
  'D_org_hash_meta'::text as section,
  schema_name,
  function_name,
  identity_args,
  owner_name,
  security_mode,
  volatility,
  search_path_or_config,
  public_execute,
  anon_execute_direct,
  anon_execute_effective,
  (public_execute and anon_execute_effective and not anon_execute_direct) as anon_execute_via_public,
  authenticated_execute_effective,
  service_role_execute_effective,
  (function_definition like '%extensions.digest%') as uses_extensions_digest,
  (replace(function_definition, 'extensions.digest', 'X') like '%digest(%') as has_bare_digest_after_strip
from org_hash_acl;

select
  'D_org_hash_definition'::text as section,
  pg_get_functiondef(p.oid) as function_definition
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname = 'org_hash_invitation_token';

with org_hash_grants as (
  select
    case
      when acl.grantee = 0 then 'PUBLIC'
      else coalesce((select r.rolname from pg_roles r where r.oid = acl.grantee), acl.grantee::text)
    end as grantee_label,
    acl.privilege_type,
    acl.is_grantable
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) as acl
  where n.nspname = 'public'
    and p.proname = 'org_hash_invitation_token'
    and acl.privilege_type = 'EXECUTE'
)
select
  'D_org_hash_acl_grants'::text as section,
  grantee_label,
  privilege_type,
  is_grantable
from org_hash_grants
order by grantee_label;

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
-- F) Resumen clasificacion preliminar (sin cambio hasta nueva evidencia remota)
-- ---------------------------------------------------------------------------
select * from (
  values
    (
      'internal_writers_anon_execute',
      '1 (matriz)',
      'DEFECTO_REAL',
      'Preliminar: _resolve_invitation_by_token — REVOKE solo FROM PUBLIC en 020/033. Confirmar C (directo vs via PUBLIC).'
    ),
    (
      'org_hash_invitation_token(p_token text)_security_definer',
      'INVOKER',
      'FALSO_POSITIVO',
      'Helper IMMUTABLE SQL: solo extensions.digest; no lee/escribe tablas. INVOKER correcto. Matriz exige DEFINER sin respaldo.'
    ),
    (
      'org_hash_invitation_token_search_path',
      '<unset>',
      'FALSO_POSITIVO',
      'digest calificado extensions.digest; search_path=public no requisito funcional. Hardening opcional no bloqueante.'
    )
) as s(check_name, actual, classification_candidate, details);
