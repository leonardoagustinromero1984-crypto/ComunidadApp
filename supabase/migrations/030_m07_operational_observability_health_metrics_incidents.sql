-- LeoVer — M07 Etapa 4: operational metrics, health checks, alert rules/incidents, staff preferences
-- Unique new migration. Does not edit 001–029.
-- Deny-by-default, SECURITY DEFINER writers, minimal grants. No analytics/marketing tables.
-- No cron required. M06 staff notification integration: PENDIENTE (not simulated).

begin;

create table if not exists public.m07_metric_catalog (
    metric_key text primary key,
    module text not null,
    metric_type text not null,
    unit text not null,
    allowed_dimension_keys text[] not null default '{module,environment,job_name,channel,status_code,result}'
);

create table if not exists public.m07_health_check_catalog (
    check_key text primary key,
    module text not null,
    component text not null
);

create table if not exists public.performance_metrics (
    id uuid primary key default gen_random_uuid(),
    metric_key text not null references public.m07_metric_catalog(metric_key),
    module text not null,
    metric_type text not null,
    value_numeric numeric not null,
    unit text not null,
    dimensions jsonb not null default '{}'::jsonb,
    dimensions_hash text generated always as (md5(dimensions::text)) stored,
    window_start timestamptz not null,
    window_end timestamptz not null,
    sample_count bigint not null default 1,
    correlation_id text,
    source text not null,
    recorded_at timestamptz not null default timezone('utc', now()),
    retention_policy_key text not null default 'TECHNICAL_30_DAYS',
    created_at timestamptz not null default timezone('utc', now()),
    constraint performance_metrics_type_chk check (metric_type in ('COUNTER','GAUGE','DURATION','RATE','SIZE','QUEUE_DEPTH','SUCCESS_RATIO','FAILURE_RATIO')),
    constraint performance_metrics_unit_chk check (unit in ('ms','count','ratio','minutes','seconds','bytes','percent')),
    constraint performance_metrics_window_chk check (window_end >= window_start),
    constraint performance_metrics_correlation_chk check (correlation_id is null or correlation_id ~ '^[A-Za-z0-9_-]{8,64}$'),
    constraint performance_metrics_dedup unique (metric_key, window_start, window_end, dimensions_hash, source)
);

create index if not exists performance_metrics_recorded_idx
    on public.performance_metrics (recorded_at desc);
create index if not exists performance_metrics_module_idx
    on public.performance_metrics (module, metric_key, recorded_at desc);

create table if not exists public.health_checks (
    id uuid primary key default gen_random_uuid(),
    check_key text not null references public.m07_health_check_catalog(check_key),
    module text not null,
    component text not null,
    status text not null,
    severity text not null default 'INFO',
    latency_ms bigint,
    details jsonb not null default '{}'::jsonb,
    correlation_id text,
    checked_at timestamptz not null default timezone('utc', now()),
    expires_at timestamptz,
    source text not null,
    created_at timestamptz not null default timezone('utc', now()),
    constraint health_checks_status_chk check (status in ('HEALTHY','DEGRADED','UNHEALTHY','UNKNOWN','SKIPPED')),
    constraint health_checks_correlation_chk check (correlation_id is null or correlation_id ~ '^[A-Za-z0-9_-]{8,64}$')
);

create index if not exists health_checks_key_checked_idx
    on public.health_checks (check_key, checked_at desc);

create table if not exists public.alert_rules (
    id uuid primary key default gen_random_uuid(),
    rule_key text not null unique,
    name text not null,
    metric_key text references public.m07_metric_catalog(metric_key),
    health_check_key text references public.m07_health_check_catalog(check_key),
    condition_type text not null,
    threshold numeric not null default 0,
    window_seconds integer not null default 300,
    severity text not null default 'WARNING',
    enabled boolean not null default true,
    cooldown_seconds integer not null default 900,
    organization_id uuid,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint alert_rules_condition_chk check (condition_type in (
        'GREATER_THAN','GREATER_OR_EQUAL','LESS_THAN','LESS_OR_EQUAL','EQUALS','NOT_EQUALS','STATUS_IS','RATE_ABOVE','NO_DATA'
    )),
    constraint alert_rules_window_chk check (window_seconds > 0 and window_seconds <= 86400),
    constraint alert_rules_cooldown_chk check (cooldown_seconds >= 0 and cooldown_seconds <= 86400),
    constraint alert_rules_target_chk check (metric_key is not null or health_check_key is not null)
);

create table if not exists public.alert_incidents (
    id uuid primary key default gen_random_uuid(),
    rule_id uuid not null references public.alert_rules(id),
    incident_key text not null,
    status text not null,
    severity text not null,
    title_code text not null,
    summary text not null,
    first_detected_at timestamptz not null default timezone('utc', now()),
    last_detected_at timestamptz not null default timezone('utc', now()),
    occurrence_count bigint not null default 1,
    acknowledged_by uuid,
    acknowledged_at timestamptz,
    resolved_by uuid,
    resolved_at timestamptz,
    resolution_code text,
    correlation_id text,
    organization_id uuid,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint alert_incidents_status_chk check (status in ('OPEN','ACKNOWLEDGED','RESOLVED','SUPPRESSED')),
    constraint alert_incidents_summary_len check (char_length(summary) <= 256),
    constraint alert_incidents_correlation_chk check (correlation_id is null or correlation_id ~ '^[A-Za-z0-9_-]{8,64}$'),
    constraint alert_incidents_dedup unique (rule_id, incident_key)
);

create table if not exists public.alert_incident_transitions (
    id uuid primary key default gen_random_uuid(),
    incident_id uuid not null references public.alert_incidents(id),
    from_status text,
    to_status text not null,
    actor_user_id uuid,
    correlation_id text,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.observability_dashboard_preferences (
    user_id uuid primary key,
    layout jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.observability_saved_filters (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    name text not null,
    filter jsonb not null default '{}'::jsonb,
    is_private boolean not null default true,
    updated_at timestamptz not null default timezone('utc', now()),
    constraint observability_saved_filters_name_uq unique (user_id, name)
);

delete from public.m07_metric_catalog;
insert into public.m07_metric_catalog (metric_key, module, metric_type, unit) values
('m00.ci.build_duration_ms','M00','DURATION','ms'),
('m00.ci.test_duration_ms','M00','DURATION','ms'),
('m00.ci.lint_duration_ms','M00','DURATION','ms'),
('m00.ci.test_count','M00','COUNTER','count'),
('m00.ci.failure_count','M00','COUNTER','count'),
('m01.auth.login_failure_rate','M01','FAILURE_RATIO','ratio'),
('m01.account.deletion_failure_count','M01','COUNTER','count'),
('m02.authorization.denied_count','M02','COUNTER','count'),
('m04.moderation.open_cases','M04','GAUGE','count'),
('m04.moderation.unassigned_cases','M04','GAUGE','count'),
('m04.support.open_tickets','M04','GAUGE','count'),
('m04.support.first_response_age_minutes','M04','GAUGE','minutes'),
('m04.verification.pending_reviews','M04','GAUGE','count'),
('m05.upload.pending_count','M05','GAUGE','count'),
('m05.upload.failure_rate','M05','FAILURE_RATIO','ratio'),
('m05.storage.error_count','M05','COUNTER','count'),
('m05.retention.overdue_count','M05','GAUGE','count'),
('m06.outbox.queue_depth','M06','QUEUE_DEPTH','count'),
('m06.outbox.oldest_pending_age_seconds','M06','GAUGE','seconds'),
('m06.delivery.success_rate','M06','SUCCESS_RATIO','ratio'),
('m06.delivery.retryable_failure_count','M06','COUNTER','count'),
('m06.dead_letter.count','M06','GAUGE','count'),
('m06.installation.revoked_count','M06','COUNTER','count'),
('m07.audit.writer_failure_count','M07','COUNTER','count'),
('m07.security.denial_rate','M07','FAILURE_RATIO','ratio'),
('m07.error.unique_fingerprint_count','M07','GAUGE','count'),
('m07.health.unhealthy_count','M07','GAUGE','count'),
('m07.incident.open_count','M07','GAUGE','count');

delete from public.m07_health_check_catalog;
insert into public.m07_health_check_catalog (check_key, module, component) values
('database.rpc_ping','M00','database'),
('database.catalog_consistency','M00','database'),
('database.migration_visibility','M00','database'),
('m05.storage.readiness','M05','storage'),
('m05.upload_pipeline','M05','upload'),
('m06.outbox_backlog','M06','outbox'),
('m06.push_delivery_pipeline','M06','push'),
('m06.dead_letter_growth','M06','dead_letter'),
('m07.audit_writer','M07','audit'),
('m07.security_writer','M07','security'),
('m07.error_writer','M07','error'),
('edge.push.readiness','M06','edge_push'),
('edge.delete_account.readiness','M01','edge_delete_account'),
('ci.latest_build','M00','ci');

insert into public.alert_rules (rule_key, name, metric_key, condition_type, threshold, severity, enabled)
values
('m07_unhealthy_count','Unhealthy checks','m07.health.unhealthy_count','GREATER_THAN',0,'WARNING',true),
('m06_dead_letter','Dead letter growth','m06.dead_letter.count','GREATER_THAN',10,'ERROR',true),
('m06_outbox_depth','Outbox backlog','m06.outbox.queue_depth','GREATER_THAN',100,'WARNING',true)
on conflict (rule_key) do nothing;

create or replace function public.m07_sanitize_health_details(p_details jsonb)
returns jsonb
language plpgsql
immutable
set search_path = public
as $$
declare
  v_out jsonb := '{}'::jsonb;
  v_key text;
  v_val text;
begin
  if p_details is null then return '{}'::jsonb; end if;
  for v_key, v_val in select key, value #>> '{}' from jsonb_each(p_details)
  loop
    if lower(v_key) in ('user_id','email','ip','ip_address','jwt','token','fcm_token','password','url','signed_url') then
      continue;
    end if;
    if v_val ~* '(https?://|Bearer |eyJ|service_role)' then
      continue;
    end if;
    v_out := v_out || jsonb_build_object(left(v_key,64), left(coalesce(v_val,''),128));
  end loop;
  return v_out;
end;
$$;

create or replace function public.m07_validate_metric_dimensions(p_metric_key text, p_dimensions jsonb)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_cat public.m07_metric_catalog;
  v_key text;
  v_out jsonb := '{}'::jsonb;
begin
  select * into v_cat from public.m07_metric_catalog where metric_key = trim(p_metric_key);
  if not found then
    raise exception using errcode = 'P0001', message = 'OBS_METRIC_UNKNOWN';
  end if;
  if p_dimensions is null then return '{}'::jsonb; end if;
  for v_key in select jsonb_object_keys(p_dimensions)
  loop
    if lower(v_key) in ('user_id','email','ip','ip_address','device_fingerprint','fcm_token','jwt','password','latitude','longitude') then
      raise exception using errcode = 'P0001', message = 'OBS_METRIC_DIMENSION_DENIED';
    end if;
    if not (v_key = any (v_cat.allowed_dimension_keys)) then
      raise exception using errcode = 'P0001', message = 'OBS_METRIC_DIMENSION_DENIED';
    end if;
    v_out := v_out || jsonb_build_object(v_key, left(coalesce(p_dimensions->>v_key,''),128));
  end loop;
  return v_out;
end;
$$;

create or replace function public.m07_record_metric(
  p_metric_key text,
  p_value_numeric numeric,
  p_unit text,
  p_dimensions jsonb default '{}'::jsonb,
  p_window_start timestamptz default null,
  p_window_end timestamptz default null,
  p_sample_count bigint default 1,
  p_correlation_id text default null,
  p_source text default 'SERVER'
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_cat public.m07_metric_catalog;
  v_dims jsonb;
  v_corr text;
  v_ws timestamptz;
  v_we timestamptz;
  v_id uuid;
  v_role text := coalesce(auth.role(), current_setting('request.jwt.claim.role', true), '');
begin
  if v_role <> 'service_role' then
    raise exception using errcode = '42501', message = 'OBS_WRITE_DENIED';
  end if;
  select * into v_cat from public.m07_metric_catalog where metric_key = trim(p_metric_key);
  if not found then
    raise exception using errcode = 'P0001', message = 'OBS_METRIC_UNKNOWN';
  end if;
  if coalesce(nullif(trim(p_unit),''), v_cat.unit) <> v_cat.unit then
    raise exception using errcode = 'P0001', message = 'OBS_METRIC_UNKNOWN';
  end if;
  v_dims := public.m07_validate_metric_dimensions(p_metric_key, coalesce(p_dimensions,'{}'::jsonb));
  v_ws := coalesce(p_window_start, timezone('utc', now()) - interval '5 minutes');
  v_we := coalesce(p_window_end, timezone('utc', now()));
  if v_we < v_ws then
    raise exception using errcode = 'P0001', message = 'OBS_METRIC_UNKNOWN';
  end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  insert into public.performance_metrics (
    metric_key, module, metric_type, value_numeric, unit, dimensions,
    window_start, window_end, sample_count, correlation_id, source, retention_policy_key
  ) values (
    v_cat.metric_key, v_cat.module, v_cat.metric_type, p_value_numeric, v_cat.unit, v_dims,
    v_ws, v_we, greatest(coalesce(p_sample_count,1),1), v_corr,
    upper(trim(coalesce(p_source,'SERVER'))), 'TECHNICAL_30_DAYS'
  )
  on conflict (metric_key, window_start, window_end, dimensions_hash, source)
  do update set
    value_numeric = excluded.value_numeric,
    sample_count = excluded.sample_count,
    recorded_at = timezone('utc', now())
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.m07_record_health_check(
  p_check_key text,
  p_status text,
  p_severity text default 'INFO',
  p_latency_ms bigint default null,
  p_details jsonb default '{}'::jsonb,
  p_correlation_id text default null,
  p_expires_at timestamptz default null,
  p_source text default 'SERVER'
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_cat public.m07_health_check_catalog;
  v_status text := upper(trim(p_status));
  v_source text := upper(trim(coalesce(p_source,'SERVER')));
  v_role text := coalesce(auth.role(), current_setting('request.jwt.claim.role', true), '');
  v_id uuid;
  v_corr text;
begin
  select * into v_cat from public.m07_health_check_catalog where check_key = trim(p_check_key);
  if not found then
    raise exception using errcode = 'P0001', message = 'OBS_HEALTH_CHECK_UNKNOWN';
  end if;
  if v_status not in ('HEALTHY','DEGRADED','UNHEALTHY','UNKNOWN','SKIPPED') then
    raise exception using errcode = 'P0001', message = 'OBS_HEALTH_CHECK_UNKNOWN';
  end if;
  if v_role <> 'service_role' then
    if v_source <> 'MANUAL' or not public.has_permission('audit.view') then
      raise exception using errcode = '42501', message = 'OBS_HEALTH_EXECUTION_DENIED';
    end if;
  end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  insert into public.health_checks (
    check_key, module, component, status, severity, latency_ms, details,
    correlation_id, expires_at, source
  ) values (
    v_cat.check_key, v_cat.module, v_cat.component, v_status,
    upper(trim(coalesce(p_severity,'INFO'))), p_latency_ms,
    public.m07_sanitize_health_details(coalesce(p_details,'{}'::jsonb)),
    v_corr,
    coalesce(p_expires_at, timezone('utc', now()) + interval '15 minutes'),
    v_source
  ) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.m07_run_health_check_manual(
  p_check_key text,
  p_correlation_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_id uuid;
  v_status text := 'UNKNOWN';
  v_latency bigint := null;
  v_t0 timestamptz;
  v_row public.health_checks;
begin
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_HEALTH_EXECUTION_DENIED';
  end if;
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
    p_correlation_id, null, 'MANUAL'
  );
  select * into v_row from public.health_checks where id = v_id;
  return to_jsonb(v_row);
end;
$$;

create or replace function public.m07_latest_metric_value(p_metric_key text, p_window_seconds integer)
returns numeric
language sql
stable
security definer
set search_path = public
as $$
  select pm.value_numeric
  from public.performance_metrics pm
  where pm.metric_key = p_metric_key
    and pm.recorded_at >= timezone('utc', now()) - make_interval(secs => greatest(p_window_seconds, 60))
  order by pm.recorded_at desc
  limit 1;
$$;

create or replace function public.m07_evaluate_alert_rule(
  p_rule_id uuid,
  p_correlation_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_rule public.alert_rules;
  v_val numeric;
  v_status text;
  v_breached boolean := false;
  v_corr text;
  v_incident_key text;
  v_id uuid;
  v_existing public.alert_incidents;
  v_role text := coalesce(auth.role(), '');
begin
  if v_role <> 'service_role' and not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  select * into v_rule from public.alert_rules where id = p_rule_id;
  if not found or not v_rule.enabled then
    return null;
  end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  if v_rule.metric_key is not null then
    v_val := public.m07_latest_metric_value(v_rule.metric_key, v_rule.window_seconds);
    if v_rule.condition_type = 'NO_DATA' then
      v_breached := v_val is null;
    elsif v_val is null then
      v_breached := false;
    else
      v_breached := case v_rule.condition_type
        when 'GREATER_THAN' then v_val > v_rule.threshold
        when 'GREATER_OR_EQUAL' then v_val >= v_rule.threshold
        when 'LESS_THAN' then v_val < v_rule.threshold
        when 'LESS_OR_EQUAL' then v_val <= v_rule.threshold
        when 'EQUALS' then v_val = v_rule.threshold
        when 'NOT_EQUALS' then v_val <> v_rule.threshold
        when 'RATE_ABOVE' then v_val > v_rule.threshold
        else false
      end;
    end if;
  elsif v_rule.health_check_key is not null then
    select hc.status into v_status
    from public.health_checks hc
    where hc.check_key = v_rule.health_check_key
    order by hc.checked_at desc limit 1;
    if v_rule.condition_type = 'STATUS_IS' then
      v_breached := coalesce(v_status,'UNKNOWN') = upper(trim(v_rule.threshold::text));
    elsif v_rule.condition_type = 'NO_DATA' then
      v_breached := v_status is null;
    end if;
  end if;
  if not v_breached then return null; end if;

  v_incident_key := v_rule.rule_key || ':' || to_char(date_trunc('hour', timezone('utc', now())), 'YYYYMMDDHH24');
  select * into v_existing from public.alert_incidents
    where rule_id = v_rule.id and incident_key = v_incident_key;
  if found then
    if v_existing.status in ('OPEN','ACKNOWLEDGED')
       and v_existing.last_detected_at > timezone('utc', now()) - make_interval(secs => v_rule.cooldown_seconds) then
      update public.alert_incidents
        set occurrence_count = occurrence_count + 1,
            last_detected_at = timezone('utc', now()),
            updated_at = timezone('utc', now()),
            correlation_id = coalesce(v_corr, correlation_id)
      where id = v_existing.id
      returning id into v_id;
      return v_id;
    end if;
    if v_existing.status = 'RESOLVED'
       and v_existing.resolved_at > timezone('utc', now()) - make_interval(secs => v_rule.cooldown_seconds) then
      return v_existing.id;
    end if;
  end if;

  insert into public.alert_incidents (
    rule_id, incident_key, status, severity, title_code, summary,
    correlation_id, organization_id
  ) values (
    v_rule.id, v_incident_key, 'OPEN', v_rule.severity, 'THRESHOLD_BREACHED', 'THRESHOLD_BREACHED',
    v_corr, v_rule.organization_id
  )
  on conflict (rule_id, incident_key) do update set
    occurrence_count = public.alert_incidents.occurrence_count + 1,
    last_detected_at = timezone('utc', now()),
    updated_at = timezone('utc', now()),
    status = case when public.alert_incidents.status = 'SUPPRESSED' then 'SUPPRESSED' else 'OPEN' end
  returning id into v_id;

  insert into public.alert_incident_transitions (incident_id, from_status, to_status, correlation_id)
  values (v_id, null, 'OPEN', v_corr);

  perform public.m07_best_effort_audit(
    'm07.alert.rule_triggered', 'ALERT_OPEN', 'SUCCESS', v_corr, 'alert_incident', v_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  return v_id;
end;
$$;

create or replace function public.m07_evaluate_enabled_alert_rules(
  p_correlation_id text default null
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
  v_count int := 0;
  v_role text := coalesce(auth.role(), '');
begin
  if v_role <> 'service_role' and not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  for r in select id from public.alert_rules where enabled = true
  loop
    if public.m07_evaluate_alert_rule(r.id, p_correlation_id) is not null then
      v_count := v_count + 1;
    end if;
  end loop;
  return v_count;
end;
$$;

create or replace function public.m07_acknowledge_incident(
  p_incident_id uuid,
  p_correlation_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_row public.alert_incidents;
  v_corr text;
begin
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_INCIDENT_TRANSITION_DENIED';
  end if;
  select * into v_row from public.alert_incidents where id = p_incident_id;
  if not found then
    raise exception using errcode = 'P0001', message = 'OBS_INCIDENT_NOT_FOUND';
  end if;
  if v_row.status <> 'OPEN' then
    raise exception using errcode = 'P0001', message = 'OBS_INCIDENT_TRANSITION_DENIED';
  end if;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  update public.alert_incidents set
    status = 'ACKNOWLEDGED',
    acknowledged_by = v_actor,
    acknowledged_at = timezone('utc', now()),
    updated_at = timezone('utc', now()),
    correlation_id = coalesce(v_corr, correlation_id)
  where id = p_incident_id
  returning * into v_row;
  insert into public.alert_incident_transitions (incident_id, from_status, to_status, actor_user_id, correlation_id)
  values (p_incident_id, 'OPEN', 'ACKNOWLEDGED', v_actor, v_corr);
  perform public.m07_best_effort_audit(
    'm07.alert.acknowledged', 'ALERT_ACK', 'SUCCESS', v_corr, 'alert_incident', p_incident_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  return to_jsonb(v_row);
end;
$$;

create or replace function public.m07_resolve_incident(
  p_incident_id uuid,
  p_resolution_code text default 'RESOLVED',
  p_correlation_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_row public.alert_incidents;
  v_corr text;
  v_from text;
begin
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_INCIDENT_TRANSITION_DENIED';
  end if;
  select * into v_row from public.alert_incidents where id = p_incident_id;
  if not found then
    raise exception using errcode = 'P0001', message = 'OBS_INCIDENT_NOT_FOUND';
  end if;
  if v_row.status not in ('OPEN','ACKNOWLEDGED') then
    raise exception using errcode = 'P0001', message = 'OBS_INCIDENT_TRANSITION_DENIED';
  end if;
  v_from := v_row.status;
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  update public.alert_incidents set
    status = 'RESOLVED',
    resolved_by = v_actor,
    resolved_at = timezone('utc', now()),
    resolution_code = left(upper(trim(coalesce(p_resolution_code,'RESOLVED'))),64),
    updated_at = timezone('utc', now()),
    correlation_id = coalesce(v_corr, correlation_id)
  where id = p_incident_id
  returning * into v_row;
  insert into public.alert_incident_transitions (incident_id, from_status, to_status, actor_user_id, correlation_id)
  values (p_incident_id, v_from, 'RESOLVED', v_actor, v_corr);
  perform public.m07_best_effort_audit(
    'm07.alert.resolved', 'ALERT_RESOLVE', 'SUCCESS', v_corr, 'alert_incident', p_incident_id::text,
    jsonb_build_object('result','SUCCESS','module','M07')
  );
  return to_jsonb(v_row);
end;
$$;

create or replace function public.m07_list_metrics(
  p_limit int default 50,
  p_offset int default 0,
  p_module text default null,
  p_metric_key text default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.recorded_at desc)
    from (
      select *
      from public.performance_metrics pm
      where (p_module is null or pm.module = upper(trim(p_module)))
        and (p_metric_key is null or pm.metric_key = trim(p_metric_key))
      order by pm.recorded_at desc
      offset greatest(p_offset,0)
      limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end;
$$;

create or replace function public.m07_list_health_checks(
  p_limit int default 50,
  p_offset int default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(x))
    from (
      select distinct on (hc.check_key) hc.*
      from public.health_checks hc
      order by hc.check_key, hc.checked_at desc
      offset greatest(p_offset,0)
      limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end;
$$;

create or replace function public.m07_list_alert_rules()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  return coalesce((select jsonb_agg(to_jsonb(r) order by r.rule_key) from public.alert_rules r), '[]'::jsonb);
end;
$$;

create or replace function public.m07_list_incidents(
  p_limit int default 50,
  p_offset int default 0,
  p_status text default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.last_detected_at desc)
    from (
      select *
      from public.alert_incidents ai
      where (p_status is null or ai.status = upper(trim(p_status)))
      order by ai.last_detected_at desc
      offset greatest(p_offset,0)
      limit least(greatest(p_limit,1),200)
    ) x
  ), '[]'::jsonb);
end;
$$;

create or replace function public.m07_get_operational_summary()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('audit.view') then
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
      from (
        select distinct on (check_key) status from public.health_checks order by check_key, checked_at desc
      ) s
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
    'staging_status', 'PENDIENTE'
  );
end;
$$;

create or replace function public.m07_save_dashboard_preferences(p_layout jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public.m07_require_actor();
begin
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_DASHBOARD_PERMISSION_DENIED';
  end if;
  insert into public.observability_dashboard_preferences(user_id, layout, updated_at)
  values (v_actor, coalesce(p_layout,'{}'::jsonb), timezone('utc', now()))
  on conflict (user_id) do update set layout = excluded.layout, updated_at = excluded.updated_at;
  return jsonb_build_object('user_id', v_actor, 'layout', coalesce(p_layout,'{}'::jsonb));
end;
$$;

create or replace function public.m07_save_filter(p_name text, p_filter jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_key text;
  v_id uuid;
begin
  if not public.has_permission('audit.view') then
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
end;
$$;

alter table public.m07_metric_catalog enable row level security;
alter table public.m07_health_check_catalog enable row level security;
alter table public.performance_metrics enable row level security;
alter table public.health_checks enable row level security;
alter table public.alert_rules enable row level security;
alter table public.alert_incidents enable row level security;
alter table public.alert_incident_transitions enable row level security;
alter table public.observability_dashboard_preferences enable row level security;
alter table public.observability_saved_filters enable row level security;

drop policy if exists m07_metric_catalog_no_client on public.m07_metric_catalog;
create policy m07_metric_catalog_no_client on public.m07_metric_catalog for all to authenticated using (false) with check (false);
drop policy if exists m07_health_catalog_no_client on public.m07_health_check_catalog;
create policy m07_health_catalog_no_client on public.m07_health_check_catalog for all to authenticated using (false) with check (false);
drop policy if exists performance_metrics_no_client on public.performance_metrics;
create policy performance_metrics_no_client on public.performance_metrics for all to authenticated using (false) with check (false);
drop policy if exists health_checks_no_client on public.health_checks;
create policy health_checks_no_client on public.health_checks for all to authenticated using (false) with check (false);
drop policy if exists alert_rules_no_client on public.alert_rules;
create policy alert_rules_no_client on public.alert_rules for all to authenticated using (false) with check (false);
drop policy if exists alert_incidents_no_client on public.alert_incidents;
create policy alert_incidents_no_client on public.alert_incidents for all to authenticated using (false) with check (false);
drop policy if exists alert_transitions_no_client on public.alert_incident_transitions;
create policy alert_transitions_no_client on public.alert_incident_transitions for all to authenticated using (false) with check (false);
drop policy if exists obs_prefs_no_direct on public.observability_dashboard_preferences;
create policy obs_prefs_no_direct on public.observability_dashboard_preferences for all to authenticated using (false) with check (false);
drop policy if exists obs_filters_no_direct on public.observability_saved_filters;
create policy obs_filters_no_direct on public.observability_saved_filters for all to authenticated using (false) with check (false);

revoke all on table public.m07_metric_catalog from public, anon, authenticated;
revoke all on table public.m07_health_check_catalog from public, anon, authenticated;
revoke all on table public.performance_metrics from public, anon, authenticated;
revoke all on table public.health_checks from public, anon, authenticated;
revoke all on table public.alert_rules from public, anon, authenticated;
revoke all on table public.alert_incidents from public, anon, authenticated;
revoke all on table public.alert_incident_transitions from public, anon, authenticated;
revoke all on table public.observability_dashboard_preferences from public, anon, authenticated;
revoke all on table public.observability_saved_filters from public, anon, authenticated;

revoke all on function public.m07_validate_metric_dimensions(text,jsonb) from public, anon, authenticated;
grant execute on function public.m07_validate_metric_dimensions(text,jsonb) to service_role;
revoke all on function public.m07_sanitize_health_details(jsonb) from public, anon, authenticated;
grant execute on function public.m07_sanitize_health_details(jsonb) to service_role;
revoke all on function public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text) from public, anon, authenticated;
grant execute on function public.m07_record_metric(text,numeric,text,jsonb,timestamptz,timestamptz,bigint,text,text) to service_role;
revoke all on function public.m07_latest_metric_value(text,integer) from public, anon, authenticated;
grant execute on function public.m07_latest_metric_value(text,integer) to service_role;
revoke all on function public.m07_record_health_check(text,text,text,bigint,jsonb,text,timestamptz,text) from public, anon;
grant execute on function public.m07_record_health_check(text,text,text,bigint,jsonb,text,timestamptz,text) to authenticated, service_role;
revoke all on function public.m07_run_health_check_manual(text,text) from public, anon;
grant execute on function public.m07_run_health_check_manual(text,text) to authenticated, service_role;
revoke all on function public.m07_evaluate_alert_rule(uuid,text) from public, anon;
grant execute on function public.m07_evaluate_alert_rule(uuid,text) to authenticated, service_role;
revoke all on function public.m07_evaluate_enabled_alert_rules(text) from public, anon;
grant execute on function public.m07_evaluate_enabled_alert_rules(text) to authenticated, service_role;
revoke all on function public.m07_acknowledge_incident(uuid,text) from public, anon;
grant execute on function public.m07_acknowledge_incident(uuid,text) to authenticated, service_role;
revoke all on function public.m07_resolve_incident(uuid,text,text) from public, anon;
grant execute on function public.m07_resolve_incident(uuid,text,text) to authenticated, service_role;
revoke all on function public.m07_list_metrics(int,int,text,text) from public, anon;
grant execute on function public.m07_list_metrics(int,int,text,text) to authenticated, service_role;
revoke all on function public.m07_list_health_checks(int,int) from public, anon;
grant execute on function public.m07_list_health_checks(int,int) to authenticated, service_role;
revoke all on function public.m07_list_alert_rules() from public, anon;
grant execute on function public.m07_list_alert_rules() to authenticated, service_role;
revoke all on function public.m07_list_incidents(int,int,text) from public, anon;
grant execute on function public.m07_list_incidents(int,int,text) to authenticated, service_role;
revoke all on function public.m07_get_operational_summary() from public, anon;
grant execute on function public.m07_get_operational_summary() to authenticated, service_role;
revoke all on function public.m07_save_dashboard_preferences(jsonb) from public, anon;
grant execute on function public.m07_save_dashboard_preferences(jsonb) to authenticated, service_role;
revoke all on function public.m07_save_filter(text,jsonb) from public, anon;
grant execute on function public.m07_save_filter(text,jsonb) to authenticated, service_role;

comment on table public.performance_metrics is 'M07 aggregated operational metrics only; no user identity; Android cannot write arbitrarily.';
comment on table public.health_checks is 'M07 health evidence with TTL; UNKNOWN when no evidence; no domain mutation.';
comment on table public.alert_rules is 'M07 internal rules; no free SQL; no external alert providers.';
comment on table public.alert_incidents is 'M07 incidents; M06 staff notify integration PENDIENTE.';

commit;
