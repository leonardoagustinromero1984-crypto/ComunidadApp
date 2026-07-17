-- LeoVer — M07 Etapa 6: blocking residual audit.view proxy hardening
-- Defects (documented):
--   D1: m07_list_audit_events still OR'd audit.view as M07 authority
--   D2: m07_record_health_check MANUAL still required audit.view (breaks health.check.execute)
--   D3: m07_evaluate_alert_rule / m07_evaluate_enabled_alert_rules still required audit.view
-- Does NOT edit 029–031 files. Minimal CREATE OR REPLACE — permission gates only. No new features.

begin;

-- D1 -----------------------------------------------------------------
create or replace function public.m07_list_audit_events(
  p_limit int default 50, p_offset int default 0,
  p_organization_id uuid default null, p_event_key text default null
) returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.m07_has_any_permission('observability.view','audit.view_sensitive') then
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

-- D2 -----------------------------------------------------------------
create or replace function public.m07_record_health_check(
  p_check_key text,
  p_status text,
  p_severity text default 'INFO',
  p_latency_ms bigint default null,
  p_details jsonb default '{}'::jsonb,
  p_correlation_id text default null,
  p_expires_at timestamptz default null,
  p_source text default 'SERVER'
) returns uuid
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
    if v_source <> 'MANUAL' or not public.has_permission('health.check.execute') then
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

-- D3a ----------------------------------------------------------------
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
  if v_role <> 'service_role'
     and not public.m07_has_any_permission('observability.manage','alert.manage') then
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

-- D3b ----------------------------------------------------------------
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
  if v_role <> 'service_role'
     and not public.m07_has_any_permission('observability.manage','alert.manage') then
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

revoke all on function public.m07_list_audit_events(int,int,uuid,text) from public, anon;
grant execute on function public.m07_list_audit_events(int,int,uuid,text) to authenticated, service_role;

revoke all on function public.m07_record_health_check(text,text,text,bigint,jsonb,text,timestamptz,text)
  from public, anon, authenticated;
grant execute on function public.m07_record_health_check(text,text,text,bigint,jsonb,text,timestamptz,text)
  to service_role;

revoke all on function public.m07_evaluate_alert_rule(uuid,text) from public, anon;
grant execute on function public.m07_evaluate_alert_rule(uuid,text) to authenticated, service_role;

revoke all on function public.m07_evaluate_enabled_alert_rules(text) from public, anon;
grant execute on function public.m07_evaluate_enabled_alert_rules(text) to authenticated, service_role;

comment on function public.m07_list_audit_events(int,int,uuid,text) is
  'M07 Etapa 6 D1: removed audit.view proxy; observability.view or audit.view_sensitive.';
comment on function public.m07_record_health_check(text,text,text,bigint,jsonb,text,timestamptz,text) is
  'M07 Etapa 6 D2: MANUAL requires health.check.execute; service_role for system writers.';

commit;
