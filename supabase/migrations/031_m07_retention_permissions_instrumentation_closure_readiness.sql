-- LeoVer — M07 Etapa 5: dedicated permissions, retention governance, read/export audit, closure readiness
-- Unique new migration. Does not edit 001–030.
-- Replaces audit.view proxy on M07 RPCs with dedicated permissions.
-- EXPORTACIÓN DE ARCHIVO PENDIENTE — request/authorize only, no file artifact.
-- INTEGRACIÓN M06 PENDIENTE — origin_module allowlist excludes M07; not simulated.

begin;

-- ---------------------------------------------------------------------------
-- Dedicated M07 permissions (additive; does not remove existing permissions)
-- ---------------------------------------------------------------------------

insert into public.permissions (code, description) values
    ('observability.view', 'Ver overview, métricas y health M07'),
    ('observability.manage', 'Administrar reglas e incidentes M07'),
    ('audit.view_sensitive', 'Ver auditoría sensible M07'),
    ('security.events.view', 'Ver eventos de seguridad M07'),
    ('export.audit_data', 'Solicitar exportaciones de observabilidad'),
    ('alert.manage', 'Acknowledge/resolve incidentes y alertas'),
    ('retention.manage', 'Preview/execute retención M07'),
    ('health.check.execute', 'Ejecutar health checks manuales')
on conflict (code) do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code in ('ADMIN', 'SUPERADMIN')
  and p.code in (
    'observability.view', 'observability.manage', 'audit.view_sensitive',
    'security.events.view', 'export.audit_data', 'alert.manage',
    'retention.manage', 'health.check.execute'
  )
on conflict do nothing;

create or replace function public.m07_has_any_permission(variadic p_codes text[])
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare v text;
begin
  foreach v in array p_codes loop
    if public.has_permission(v) then return true; end if;
  end loop;
  return false;
end;
$$;

-- ---------------------------------------------------------------------------
-- Catalog: add Stage 5 event keys (keep existing 108; add 10 → 118 total)
-- ---------------------------------------------------------------------------

insert into public.observability_event_catalog (
  event_key, module, category, default_severity, sensitivity, organization_scoped,
  retention_policy_key, remote_persistence_allowed, analytics_allowed,
  allowed_metadata_keys, required_metadata_keys
) values
('m07.audit.read','M07','DATA_ACCESS','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.security.read','M07','DATA_ACCESS','INFO','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.error.read','M07','DATA_ACCESS','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.retention.previewed','M07','AUDIT','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.retention.executed','M07','AUDIT','WARNING','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.retention.legal_hold_changed','M07','AUDIT','WARNING','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.health.manual_check','M07','HEALTH','INFO','INTERNAL',false,'TECHNICAL_30_DAYS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.incident.acknowledged','M07','AUDIT','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.incident.resolved','M07','AUDIT','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[]),
('m07.incident.staff_notification','M07','NOTIFICATION','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false,
 ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[],
 ARRAY['result']::text[])
on conflict (event_key) do nothing;

-- ---------------------------------------------------------------------------
-- Retention tables
-- ---------------------------------------------------------------------------

create table if not exists public.observability_retention_policies (
    id uuid primary key default gen_random_uuid(),
    policy_key text not null,
    target_table text not null,
    retention_days integer,
    delete_mode text not null,
    enabled boolean not null default true,
    legal_hold boolean not null default false,
    legal_hold_supported boolean not null default true,
    description_code text not null,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint retention_policy_key_chk check (policy_key in (
        'NO_REMOTE','DEBUG_7_DAYS','TECHNICAL_30_DAYS','TECHNICAL_90_DAYS',
        'AUDIT_12_MONTHS','SECURITY_24_MONTHS','AGGREGATE_24_MONTHS',
        'UNTIL_RESOLUTION','LEGAL_REVIEW_REQUIRED'
    )),
    constraint retention_delete_mode_chk check (delete_mode in (
        'HARD_DELETE','ANONYMIZE','KEEP_UNTIL_RESOLVED','NO_DELETE','LEGAL_REVIEW'
    )),
    constraint retention_target_chk check (target_table in (
        'audit_events','security_events','application_errors','performance_metrics',
        'health_checks','observability_export_requests'
    )),
    constraint retention_policy_unique unique (policy_key, target_table)
);

create table if not exists public.observability_retention_runs (
    id uuid primary key default gen_random_uuid(),
    policy_id uuid not null references public.observability_retention_policies(id),
    run_kind text not null,
    status text not null,
    actor_user_id uuid,
    correlation_id text not null,
    estimated_count bigint not null default 0,
    affected_count bigint not null default 0,
    batch_size integer not null default 100,
    preview_expires_at timestamptz,
    executed_at timestamptz,
    failure_code text,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint retention_run_kind_chk check (run_kind in ('PREVIEW','EXECUTE')),
    constraint retention_run_status_chk check (status in (
        'PREVIEWED','EXPIRED','EXECUTED','DENIED','FAILED','BLOCKED_LEGAL_HOLD'
    )),
    constraint retention_run_correlation_chk check (correlation_id ~ '^[A-Za-z0-9_-]{8,64}$')
);

create table if not exists public.observability_retention_run_items (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null references public.observability_retention_runs(id),
    target_table text not null,
    opaque_resource_id text,
    action_code text not null,
    created_at timestamptz not null default timezone('utc', now()),
    constraint retention_item_action_chk check (action_code in (
        'ESTIMATED','DELETED','ANONYMIZED','SKIPPED_OPEN_INCIDENT','SKIPPED_EXPORT_IN_PROGRESS','SKIPPED_LEGAL'
    ))
);

insert into public.observability_retention_policies (policy_key, target_table, retention_days, delete_mode, description_code) values
('TECHNICAL_30_DAYS','performance_metrics',30,'HARD_DELETE','TECH_METRICS_30'),
('TECHNICAL_30_DAYS','health_checks',30,'HARD_DELETE','TECH_HEALTH_30'),
('TECHNICAL_90_DAYS','application_errors',90,'ANONYMIZE','TECH_ERRORS_90'),
('AUDIT_12_MONTHS','audit_events',365,'ANONYMIZE','AUDIT_12M'),
('SECURITY_24_MONTHS','security_events',730,'KEEP_UNTIL_RESOLVED','SECURITY_24M'),
('AGGREGATE_24_MONTHS','performance_metrics',730,'HARD_DELETE','AGG_METRICS_24M'),
('LEGAL_REVIEW_REQUIRED','audit_events',null,'LEGAL_REVIEW','LEGAL_HOLD_AUDIT'),
('NO_REMOTE','observability_export_requests',0,'NO_DELETE','EXPORT_NO_REMOTE')
on conflict (policy_key, target_table) do nothing;

-- Export status expansion (file generation remains PENDIENTE)
alter table public.observability_export_requests drop constraint if exists export_status_chk;
alter table public.observability_export_requests
  add constraint export_status_chk check (status in (
    'REQUESTED','AUTHORIZED','PROCESSING','COMPLETED','FAILED','DENIED','EXPIRED','CANCELLED','READY_SIMULATED'
  ));
alter table public.observability_export_requests
  add column if not exists file_pending boolean not null default true;
alter table public.observability_export_requests
  add column if not exists expires_at timestamptz;
alter table public.observability_export_requests
  add column if not exists row_limit integer not null default 1000;

comment on column public.observability_export_requests.file_pending is
  'EXPORTACIÓN DE ARCHIVO PENDIENTE — Etapa 5 no genera CSV/JSONL ni signed URLs.';

-- ---------------------------------------------------------------------------
-- Replace M07 RPC permission gates (CREATE OR REPLACE; 029/030 files untouched)
-- DROP required: PostgreSQL cannot change return type via CREATE OR REPLACE
-- (029 returned setof/uuid; 031 returns jsonb for list/export RPCs).
-- ---------------------------------------------------------------------------

drop function if exists public.m07_list_audit_events(int, int, uuid, text);
drop function if exists public.m07_list_security_events(int, int);
drop function if exists public.m07_list_application_errors(int, int);
drop function if exists public.m07_request_export(text, text, text, uuid, jsonb, text);

create or replace function public.m07_list_audit_events(
  p_limit int default 50, p_offset int default 0,
  p_organization_id uuid default null, p_event_key text default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public.m07_require_actor();
begin
  if not public.m07_has_any_permission('observability.view','audit.view_sensitive','audit.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  perform public.m07_best_effort_audit(
    'm07.audit.read', 'AUDIT_READ', 'SUCCESS', public.m07_new_correlation_id(),
    'audit_events', null, jsonb_build_object('result','SUCCESS','module','M07')
  );
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.occurred_at desc)
    from (
      select id, event_key, category, severity, sensitivity, actor_type, organization_id,
             resource_type, resource_id, action, result, reason_code, correlation_id, occurred_at
      from public.audit_events ae
      where (p_organization_id is null or ae.organization_id = p_organization_id
             or public.has_permission('audit.view_sensitive'))
        and (p_event_key is null or ae.event_key = trim(p_event_key))
        and (ae.sensitivity <> 'SECURITY_SENSITIVE'
             or public.has_permission('audit.view_sensitive'))
      order by ae.occurred_at desc
      offset greatest(p_offset,0) limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end; $$;

create or replace function public.m07_list_security_events(p_limit int default 50, p_offset int default 0)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('security.events.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  perform public.m07_best_effort_audit(
    'm07.security.read', 'SECURITY_READ', 'SUCCESS', public.m07_new_correlation_id(),
    'security_events', null, jsonb_build_object('result','SUCCESS','module','M07')
  );
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.occurred_at desc)
    from (
      select id, event_key, severity, result, reason_code, permission_code, correlation_id, occurred_at
      from public.security_events
      order by occurred_at desc
      offset greatest(p_offset,0) limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end; $$;

create or replace function public.m07_list_application_errors(p_limit int default 50, p_offset int default 0)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.m07_has_any_permission('observability.view','observability.manage') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  perform public.m07_best_effort_audit(
    'm07.error.read', 'ERROR_READ', 'SUCCESS', public.m07_new_correlation_id(),
    'application_errors', null, jsonb_build_object('result','SUCCESS','module','M07')
  );
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.occurred_at desc)
    from (
      select id, error_code, module, severity, fingerprint, is_retryable, correlation_id, occurred_at
      from public.application_errors
      order by occurred_at desc
      offset greatest(p_offset,0) limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end; $$;

create or replace function public.m07_request_export(
  p_scope text, p_sensitivity text, p_reason text,
  p_organization_id uuid default null, p_filters jsonb default '{}'::jsonb,
  p_correlation_id text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_corr text := public.m07_validate_correlation_id(p_correlation_id);
  v_id uuid;
  v_sens text := upper(trim(coalesce(p_sensitivity,'RESTRICTED')));
begin
  if not public.has_permission('export.audit_data') then
    insert into public.observability_export_requests(
      requested_by, scope, organization_id, sensitivity, filters, status, reason, correlation_id, file_pending
    ) values (
      v_actor, coalesce(nullif(trim(p_scope),''),'PLATFORM_AUDIT'), p_organization_id, v_sens,
      '{}'::jsonb, 'DENIED', public.m07_sanitize_reason_code(p_reason), v_corr, true
    ) returning id into v_id;
    perform public.m07_write_security_event(
      'm07.export.denied', 'DENIED', v_corr, 'OBS_EXPORT_DENIED', 'export.audit_data',
      'export', v_id::text, p_organization_id, 'CLIENT',
      jsonb_build_object('result','DENIED','permission_code','export.audit_data')
    );
    raise exception using errcode = '42501', message = 'OBS_EXPORT_SCOPE_DENIED';
  end if;
  insert into public.observability_export_requests(
    requested_by, scope, organization_id, sensitivity, filters, status, reason, correlation_id,
    file_pending, expires_at
  ) values (
    v_actor, upper(trim(p_scope)), p_organization_id, v_sens,
    coalesce(p_filters,'{}'::jsonb), 'AUTHORIZED',
    public.m07_sanitize_reason_code(p_reason), v_corr, true,
    timezone('utc', now()) + interval '24 hours'
  ) returning id into v_id;
  perform public.m07_best_effort_audit(
    'm07.export.requested', 'REQUEST_EXPORT', 'SUCCESS', v_corr,
    'export', v_id::text, jsonb_build_object('result','SUCCESS','module','M07')
  );
  return jsonb_build_object(
    'id', v_id, 'status', 'AUTHORIZED', 'file_pending', true,
    'note', 'EXPORTACION_DE_ARCHIVO_PENDIENTE'
  );
end; $$;

create or replace function public.m07_list_metrics(
  p_limit int default 50, p_offset int default 0,
  p_module text default null, p_metric_key text default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('observability.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.recorded_at desc)
    from (
      select * from public.performance_metrics pm
      where (p_module is null or pm.module = upper(trim(p_module)))
        and (p_metric_key is null or pm.metric_key = trim(p_metric_key))
      order by pm.recorded_at desc
      offset greatest(p_offset,0) limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end; $$;

create or replace function public.m07_list_health_checks(p_limit int default 50, p_offset int default 0)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('observability.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(x))
    from (
      select distinct on (hc.check_key) hc.*
      from public.health_checks hc
      order by hc.check_key, hc.checked_at desc
      offset greatest(p_offset,0) limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end; $$;

create or replace function public.m07_get_operational_summary()
returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('observability.view') then
    raise exception using errcode = '42501', message = 'OBS_DASHBOARD_PERMISSION_DENIED';
  end if;
  return jsonb_build_object(
    'overall_status', coalesce((
      select case
        when bool_or(status = 'UNHEALTHY') then 'UNHEALTHY'
        when bool_or(status = 'DEGRADED') then 'DEGRADED'
        when bool_or(status = 'HEALTHY') and not bool_or(status = 'UNKNOWN') then 'HEALTHY'
        else 'UNKNOWN'
      end
      from (select distinct on (check_key) status from public.health_checks order by check_key, checked_at desc) s
    ), 'UNKNOWN'),
    'healthy_count', (select count(*) from (select distinct on (check_key) status from public.health_checks order by check_key, checked_at desc) s where status='HEALTHY'),
    'degraded_count', (select count(*) from (select distinct on (check_key) status from public.health_checks order by check_key, checked_at desc) s where status='DEGRADED'),
    'unhealthy_count', (select count(*) from (select distinct on (check_key) status from public.health_checks order by check_key, checked_at desc) s where status='UNHEALTHY'),
    'unknown_count', (select count(*) from (select distinct on (check_key) status from public.health_checks order by check_key, checked_at desc) s where status in ('UNKNOWN','SKIPPED')),
    'open_incidents', (select count(*) from public.alert_incidents where status in ('OPEN','ACKNOWLEDGED')),
    'dead_letter_count', public.m07_latest_metric_value('m06.dead_letter.count', 86400),
    'outbox_backlog', public.m07_latest_metric_value('m06.outbox.queue_depth', 86400),
    'unique_error_fingerprints', public.m07_latest_metric_value('m07.error.unique_fingerprint_count', 86400),
    'authorization_denials', public.m07_latest_metric_value('m02.authorization.denied_count', 86400),
    'upload_failures', public.m07_latest_metric_value('m05.upload.failure_rate', 86400),
    'open_moderation_cases', public.m07_latest_metric_value('m04.moderation.open_cases', 86400),
    'open_support_tickets', public.m07_latest_metric_value('m04.support.open_tickets', 86400),
    'last_updated_at', timezone('utc', now()),
    'staging_status', 'PENDIENTE',
    'm06_incident_notification', 'PENDIENTE',
    'export_file', 'PENDIENTE'
  );
end; $$;

create or replace function public.m07_list_alert_rules()
returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('observability.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  return coalesce((select jsonb_agg(to_jsonb(r) order by r.rule_key) from public.alert_rules r), '[]'::jsonb);
end; $$;

create or replace function public.m07_list_incidents(
  p_limit int default 50, p_offset int default 0, p_status text default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('observability.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.last_detected_at desc)
    from (
      select * from public.alert_incidents ai
      where (p_status is null or ai.status = upper(trim(p_status)))
      order by ai.last_detected_at desc
      offset greatest(p_offset,0) limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end; $$;

create or replace function public.m07_acknowledge_incident(p_incident_id uuid, p_correlation_id text default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_row public.alert_incidents;
  v_corr text;
begin
  if not public.m07_has_any_permission('alert.manage','observability.manage') then
    raise exception using errcode = '42501', message = 'OBS_INCIDENT_TRANSITION_DENIED';
  end if;
  select * into v_row from public.alert_incidents where id = p_incident_id;
  if not found then raise exception using errcode = 'P0001', message = 'OBS_INCIDENT_NOT_FOUND'; end if;
  if v_row.status <> 'OPEN' then raise exception using errcode = 'P0001', message = 'OBS_INCIDENT_TRANSITION_DENIED'; end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  update public.alert_incidents set
    status = 'ACKNOWLEDGED', acknowledged_by = v_actor, acknowledged_at = timezone('utc', now()),
    updated_at = timezone('utc', now()), correlation_id = coalesce(v_corr, correlation_id)
  where id = p_incident_id returning * into v_row;
  insert into public.alert_incident_transitions (incident_id, from_status, to_status, actor_user_id, correlation_id)
  values (p_incident_id, 'OPEN', 'ACKNOWLEDGED', v_actor, v_corr);
  perform public.m07_best_effort_audit(
    'm07.incident.acknowledged', 'ALERT_ACK', 'SUCCESS', v_corr, 'alert_incident', p_incident_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  -- INTEGRACIÓN M06 PENDIENTE: origin_module allowlist excludes M07
  return to_jsonb(v_row);
end; $$;

create or replace function public.m07_resolve_incident(
  p_incident_id uuid, p_resolution_code text default 'RESOLVED', p_correlation_id text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_row public.alert_incidents;
  v_corr text;
  v_from text;
begin
  if not public.m07_has_any_permission('alert.manage','observability.manage') then
    raise exception using errcode = '42501', message = 'OBS_INCIDENT_TRANSITION_DENIED';
  end if;
  select * into v_row from public.alert_incidents where id = p_incident_id;
  if not found then raise exception using errcode = 'P0001', message = 'OBS_INCIDENT_NOT_FOUND'; end if;
  if v_row.status not in ('OPEN','ACKNOWLEDGED') then
    raise exception using errcode = 'P0001', message = 'OBS_INCIDENT_TRANSITION_DENIED';
  end if;
  v_from := v_row.status;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  update public.alert_incidents set
    status = 'RESOLVED', resolved_by = v_actor, resolved_at = timezone('utc', now()),
    resolution_code = left(upper(trim(coalesce(p_resolution_code,'RESOLVED'))),64),
    updated_at = timezone('utc', now()), correlation_id = coalesce(v_corr, correlation_id)
  where id = p_incident_id returning * into v_row;
  insert into public.alert_incident_transitions (incident_id, from_status, to_status, actor_user_id, correlation_id)
  values (p_incident_id, v_from, 'RESOLVED', v_actor, v_corr);
  perform public.m07_best_effort_audit(
    'm07.incident.resolved', 'ALERT_RESOLVE', 'SUCCESS', v_corr, 'alert_incident', p_incident_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  return to_jsonb(v_row);
end; $$;

create or replace function public.m07_run_health_check_manual(p_check_key text, p_correlation_id text default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_id uuid;
  v_status text := 'UNKNOWN';
  v_latency bigint := null;
  v_t0 timestamptz;
  v_row public.health_checks;
  v_corr text;
begin
  if not public.has_permission('health.check.execute') then
    raise exception using errcode = '42501', message = 'OBS_HEALTH_EXECUTION_DENIED';
  end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  if trim(p_check_key) = 'database.rpc_ping' then
    v_t0 := clock_timestamp();
    perform 1;
    v_latency := greatest(1, (extract(epoch from (clock_timestamp() - v_t0)) * 1000)::bigint);
    v_status := 'HEALTHY';
  elsif trim(p_check_key) = 'database.catalog_consistency' then
    if (select count(*) from public.m07_metric_catalog) >= 28
       and (select count(*) from public.m07_health_check_catalog) = 14 then
      v_status := 'HEALTHY';
    else
      v_status := 'DEGRADED';
    end if;
  else
    v_status := 'UNKNOWN';
  end if;
  v_id := public.m07_record_health_check(
    p_check_key, v_status, 'INFO', v_latency,
    jsonb_build_object('reason', case when v_status='UNKNOWN' then 'NO_EVIDENCE' else 'OK' end),
    v_corr, null, 'MANUAL'
  );
  perform public.m07_best_effort_audit(
    'm07.health.manual_check', 'HEALTH_MANUAL', 'SUCCESS', v_corr, 'health_check', trim(p_check_key),
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  select * into v_row from public.health_checks where id = v_id;
  return to_jsonb(v_row);
end; $$;

create or replace function public.m07_save_dashboard_preferences(p_layout jsonb default '{}'::jsonb)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public.m07_require_actor();
begin
  if not public.has_permission('observability.view') then
    raise exception using errcode = '42501', message = 'OBS_DASHBOARD_PERMISSION_DENIED';
  end if;
  insert into public.observability_dashboard_preferences(user_id, layout, updated_at)
  values (v_actor, coalesce(p_layout,'{}'::jsonb), timezone('utc', now()))
  on conflict (user_id) do update set layout = excluded.layout, updated_at = excluded.updated_at;
  return jsonb_build_object('user_id', v_actor, 'layout', coalesce(p_layout,'{}'::jsonb));
end; $$;

create or replace function public.m07_save_filter(p_name text, p_filter jsonb default '{}'::jsonb)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_key text;
  v_id uuid;
begin
  if not public.has_permission('observability.view') then
    raise exception using errcode = '42501', message = 'OBS_FILTER_INVALID';
  end if;
  if coalesce(trim(p_name),'') = '' or char_length(p_name) > 64 then
    raise exception using errcode = 'P0001', message = 'OBS_FILTER_INVALID';
  end if;
  for v_key in select jsonb_object_keys(coalesce(p_filter,'{}'::jsonb))
  loop
    if lower(v_key) in ('user_id','email','ip','password','sql') then
      raise exception using errcode = 'P0001', message = 'OBS_FILTER_INVALID';
    end if;
    if coalesce(p_filter->>v_key,'') ~* 'select\\s+|insert\\s+|delete\\s+' then
      raise exception using errcode = 'P0001', message = 'OBS_FILTER_INVALID';
    end if;
  end loop;
  insert into public.observability_saved_filters(user_id, name, filter, is_private, updated_at)
  values (v_actor, trim(p_name), coalesce(p_filter,'{}'::jsonb), true, timezone('utc', now()))
  on conflict (user_id, name) do update set filter = excluded.filter, updated_at = excluded.updated_at
  returning id into v_id;
  return jsonb_build_object('id', v_id, 'name', trim(p_name));
end; $$;

-- ---------------------------------------------------------------------------
-- Retention RPCs
-- ---------------------------------------------------------------------------

create or replace function public.m07_list_retention_policies()
returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.m07_has_any_permission('retention.manage','observability.view') then
    raise exception using errcode = '42501', message = 'OBS_RETENTION_EXECUTION_DENIED';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(p) order by p.policy_key, p.target_table)
    from public.observability_retention_policies p
  ), '[]'::jsonb);
end; $$;

create or replace function public.m07_preview_retention_run(
  p_policy_id uuid, p_batch_size int default 100, p_correlation_id text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_pol public.observability_retention_policies;
  v_corr text;
  v_est bigint := 0;
  v_run_id uuid;
  v_cutoff timestamptz;
begin
  if not public.has_permission('retention.manage') then
    raise exception using errcode = '42501', message = 'OBS_RETENTION_EXECUTION_DENIED';
  end if;
  select * into v_pol from public.observability_retention_policies where id = p_policy_id;
  if not found then raise exception using errcode = 'P0001', message = 'OBS_RETENTION_POLICY_UNKNOWN'; end if;
  if v_pol.policy_key = 'LEGAL_REVIEW_REQUIRED' or v_pol.delete_mode = 'LEGAL_REVIEW' then
    raise exception using errcode = 'P0001', message = 'OBS_RETENTION_LEGAL_HOLD';
  end if;
  if v_pol.legal_hold then
    raise exception using errcode = 'P0001', message = 'OBS_RETENTION_LEGAL_HOLD';
  end if;
  if v_pol.delete_mode in ('NO_DELETE','KEEP_UNTIL_RESOLVED') and v_pol.policy_key <> 'TECHNICAL_90_DAYS' then
    v_est := 0;
  else
    v_cutoff := timezone('utc', now()) - make_interval(days => greatest(coalesce(v_pol.retention_days, 3650), 1));
    if v_pol.target_table = 'performance_metrics' then
      select count(*) into v_est from public.performance_metrics where recorded_at < v_cutoff;
    elsif v_pol.target_table = 'health_checks' then
      select count(*) into v_est from public.health_checks where checked_at < v_cutoff;
    elsif v_pol.target_table = 'application_errors' then
      select count(*) into v_est from public.application_errors where occurred_at < v_cutoff and resolved_at is not null;
    elsif v_pol.target_table = 'audit_events' then
      select count(*) into v_est from public.audit_events where occurred_at < v_cutoff;
    elsif v_pol.target_table = 'security_events' then
      select count(*) into v_est from public.security_events where occurred_at < v_cutoff;
    else
      v_est := 0;
    end if;
  end if;
  v_est := least(v_est, least(greatest(coalesce(p_batch_size,100),1), 500));
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  insert into public.observability_retention_runs (
    policy_id, run_kind, status, actor_user_id, correlation_id, estimated_count, batch_size, preview_expires_at
  ) values (
    v_pol.id, 'PREVIEW', 'PREVIEWED', v_actor, v_corr, v_est,
    least(greatest(coalesce(p_batch_size,100),1), 500),
    timezone('utc', now()) + interval '15 minutes'
  ) returning id into v_run_id;
  perform public.m07_best_effort_audit(
    'm07.retention.previewed', 'RETENTION_PREVIEW', 'SUCCESS', v_corr, 'retention_run', v_run_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  return jsonb_build_object(
    'run_id', v_run_id, 'status', 'PREVIEWED', 'estimated_count', v_est,
    'target_table', v_pol.target_table, 'policy_key', v_pol.policy_key,
    'preview_expires_at', timezone('utc', now()) + interval '15 minutes',
    'correlation_id', v_corr
  );
end; $$;

create or replace function public.m07_execute_retention_run(
  p_preview_run_id uuid, p_correlation_id text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_prev public.observability_retention_runs;
  v_pol public.observability_retention_policies;
  v_corr text;
  v_cutoff timestamptz;
  v_affected bigint := 0;
  v_exec_id uuid;
begin
  if not public.has_permission('retention.manage') then
    raise exception using errcode = '42501', message = 'OBS_RETENTION_EXECUTION_DENIED';
  end if;
  select * into v_prev from public.observability_retention_runs where id = p_preview_run_id;
  if not found or v_prev.run_kind <> 'PREVIEW' or v_prev.status <> 'PREVIEWED' then
    raise exception using errcode = 'P0001', message = 'OBS_RETENTION_PREVIEW_REQUIRED';
  end if;
  if v_prev.preview_expires_at is null or v_prev.preview_expires_at < timezone('utc', now()) then
    update public.observability_retention_runs set status = 'EXPIRED', updated_at = timezone('utc', now())
    where id = p_preview_run_id;
    raise exception using errcode = 'P0001', message = 'OBS_RETENTION_PREVIEW_EXPIRED';
  end if;
  select * into v_pol from public.observability_retention_policies where id = v_prev.policy_id;
  if v_pol.legal_hold or v_pol.policy_key = 'LEGAL_REVIEW_REQUIRED' or v_pol.delete_mode = 'LEGAL_REVIEW' then
    raise exception using errcode = 'P0001', message = 'OBS_RETENTION_LEGAL_HOLD';
  end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  v_cutoff := timezone('utc', now()) - make_interval(days => greatest(coalesce(v_pol.retention_days, 3650), 1));

  if v_pol.target_table = 'performance_metrics' and v_pol.delete_mode = 'HARD_DELETE' then
    with doomed as (
      select id from public.performance_metrics where recorded_at < v_cutoff
      order by recorded_at asc limit v_prev.batch_size
    ), del as (delete from public.performance_metrics p using doomed d where p.id = d.id returning p.id)
    select count(*) into v_affected from del;
  elsif v_pol.target_table = 'health_checks' and v_pol.delete_mode = 'HARD_DELETE' then
    with doomed as (
      select id from public.health_checks where checked_at < v_cutoff
      order by checked_at asc limit v_prev.batch_size
    ), del as (delete from public.health_checks h using doomed d where h.id = d.id returning h.id)
    select count(*) into v_affected from del;
  elsif v_pol.target_table = 'application_errors' and v_pol.delete_mode = 'ANONYMIZE' then
    with doomed as (
      select id from public.application_errors
      where occurred_at < v_cutoff and resolved_at is not null
      order by occurred_at asc limit v_prev.batch_size
    ), upd as (
      update public.application_errors e set sanitized_message = 'REDACTED', metadata = '{}'::jsonb
      from doomed d where e.id = d.id returning e.id
    )
    select count(*) into v_affected from upd;
  else
    v_affected := 0;
  end if;

  insert into public.observability_retention_runs (
    policy_id, run_kind, status, actor_user_id, correlation_id,
    estimated_count, affected_count, batch_size, executed_at
  ) values (
    v_pol.id, 'EXECUTE', 'EXECUTED', v_actor, v_corr,
    v_prev.estimated_count, v_affected, v_prev.batch_size, timezone('utc', now())
  ) returning id into v_exec_id;

  update public.observability_retention_runs set status = 'EXECUTED', updated_at = timezone('utc', now())
  where id = p_preview_run_id;

  perform public.m07_best_effort_audit(
    'm07.retention.executed', 'RETENTION_EXECUTE', 'SUCCESS', v_corr, 'retention_run', v_exec_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  return jsonb_build_object(
    'run_id', v_exec_id, 'status', 'EXECUTED', 'affected_count', v_affected,
    'target_table', v_pol.target_table, 'correlation_id', v_corr
  );
exception when others then
  if SQLERRM like 'OBS_%' then raise; end if;
  raise exception using errcode = 'P0001', message = 'OBS_RETENTION_RUN_FAILED';
end; $$;

create or replace function public.m07_list_retention_runs(p_limit int default 50, p_offset int default 0)
returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('retention.manage') then
    raise exception using errcode = '42501', message = 'OBS_RETENTION_EXECUTION_DENIED';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.created_at desc)
    from (
      select * from public.observability_retention_runs
      order by created_at desc
      offset greatest(p_offset,0) limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end; $$;

create or replace function public.m07_set_legal_hold(p_policy_id uuid, p_correlation_id text default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_corr text;
begin
  if not public.has_permission('retention.manage') then
    raise exception using errcode = '42501', message = 'OBS_RETENTION_EXECUTION_DENIED';
  end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  update public.observability_retention_policies
    set legal_hold = true, updated_at = timezone('utc', now())
  where id = p_policy_id;
  if not found then raise exception using errcode = 'P0001', message = 'OBS_RETENTION_POLICY_UNKNOWN'; end if;
  perform public.m07_best_effort_audit(
    'm07.retention.legal_hold_changed', 'LEGAL_HOLD_SET', 'SUCCESS', v_corr, 'retention_policy', p_policy_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  return jsonb_build_object('policy_id', p_policy_id, 'legal_hold', true);
end; $$;

create or replace function public.m07_release_legal_hold(p_policy_id uuid, p_correlation_id text default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_corr text;
  v_pol public.observability_retention_policies;
begin
  if not public.has_permission('retention.manage') then
    raise exception using errcode = '42501', message = 'OBS_RETENTION_EXECUTION_DENIED';
  end if;
  select * into v_pol from public.observability_retention_policies where id = p_policy_id;
  if not found then raise exception using errcode = 'P0001', message = 'OBS_RETENTION_POLICY_UNKNOWN'; end if;
  if v_pol.policy_key = 'LEGAL_REVIEW_REQUIRED' then
    raise exception using errcode = 'P0001', message = 'OBS_RETENTION_LEGAL_HOLD';
  end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  update public.observability_retention_policies
    set legal_hold = false, updated_at = timezone('utc', now())
  where id = p_policy_id;
  perform public.m07_best_effort_audit(
    'm07.retention.legal_hold_changed', 'LEGAL_HOLD_RELEASE', 'SUCCESS', v_corr, 'retention_policy', p_policy_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  return jsonb_build_object('policy_id', p_policy_id, 'legal_hold', false);
end; $$;

-- ---------------------------------------------------------------------------
-- RLS + grants
-- ---------------------------------------------------------------------------

alter table public.observability_retention_policies enable row level security;
alter table public.observability_retention_runs enable row level security;
alter table public.observability_retention_run_items enable row level security;

drop policy if exists retention_policies_no_client on public.observability_retention_policies;
create policy retention_policies_no_client on public.observability_retention_policies
  for all to authenticated using (false) with check (false);
drop policy if exists retention_runs_no_client on public.observability_retention_runs;
create policy retention_runs_no_client on public.observability_retention_runs
  for all to authenticated using (false) with check (false);
drop policy if exists retention_items_no_client on public.observability_retention_run_items;
create policy retention_items_no_client on public.observability_retention_run_items
  for all to authenticated using (false) with check (false);

revoke all on table public.observability_retention_policies from public, anon, authenticated;
revoke all on table public.observability_retention_runs from public, anon, authenticated;
revoke all on table public.observability_retention_run_items from public, anon, authenticated;

revoke all on function public.m07_has_any_permission(text[]) from public, anon;
grant execute on function public.m07_has_any_permission(text[]) to authenticated, service_role;

revoke all on function public.m07_list_retention_policies() from public, anon;
grant execute on function public.m07_list_retention_policies() to authenticated, service_role;
revoke all on function public.m07_preview_retention_run(uuid,int,text) from public, anon;
grant execute on function public.m07_preview_retention_run(uuid,int,text) to authenticated, service_role;
revoke all on function public.m07_execute_retention_run(uuid,text) from public, anon;
grant execute on function public.m07_execute_retention_run(uuid,text) to authenticated, service_role;
revoke all on function public.m07_list_retention_runs(int,int) from public, anon;
grant execute on function public.m07_list_retention_runs(int,int) to authenticated, service_role;
revoke all on function public.m07_set_legal_hold(uuid,text) from public, anon;
grant execute on function public.m07_set_legal_hold(uuid,text) to authenticated, service_role;
revoke all on function public.m07_release_legal_hold(uuid,text) from public, anon;
grant execute on function public.m07_release_legal_hold(uuid,text) to authenticated, service_role;

comment on table public.observability_retention_policies is
  'M07 retention policies; LEGAL_REVIEW_REQUIRED never auto-purges; technical config pending legal review.';
comment on table public.observability_retention_runs is
  'M07 retention preview/execute audit trail; no deleted row content returned.';

commit;
