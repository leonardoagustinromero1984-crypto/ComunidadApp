-- LeoVer — M07 Etapa 3: foundation audit_events / security_events / application_errors / export requests
-- Unique new migration. Does not edit 001–028.
-- Deny-by-default, append-only writes via SECURITY DEFINER, minimal grants.

begin;

create table if not exists public.observability_event_catalog (
    event_key text primary key,
    module text not null,
    category text not null,
    default_severity text not null,
    sensitivity text not null,
    organization_scoped boolean not null default false,
    retention_policy_key text not null,
    remote_persistence_allowed boolean not null default false,
    analytics_allowed boolean not null default false,
    allowed_metadata_keys text[] not null default '{}',
    required_metadata_keys text[] not null default '{}',
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.audit_events (
    id uuid primary key default gen_random_uuid(),
    event_key text not null references public.observability_event_catalog(event_key),
    category text not null,
    severity text not null,
    sensitivity text not null,
    actor_user_id uuid,
    actor_type text not null,
    actor_technical_id text,
    organization_id uuid,
    resource_type text,
    resource_id text,
    action text not null,
    result text not null,
    reason_code text,
    correlation_id text not null,
    request_id text,
    source text not null,
    metadata jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null default timezone('utc', now()),
    retention_policy_key text not null,
    retention_until timestamptz,
    created_at timestamptz not null default timezone('utc', now()),
    constraint audit_events_correlation_chk check (correlation_id ~ '^[A-Za-z0-9_-]{8,64}$')
);

create index if not exists audit_events_dedup_lookup_idx
    on public.audit_events (event_key, correlation_id, resource_id, action);

create index if not exists audit_events_occurred_idx on public.audit_events (occurred_at desc);
create index if not exists audit_events_org_idx on public.audit_events (organization_id, occurred_at desc);

create table if not exists public.security_events (
    id uuid primary key default gen_random_uuid(),
    event_key text not null references public.observability_event_catalog(event_key),
    severity text not null,
    sensitivity text not null default 'SECURITY_SENSITIVE',
    actor_user_id uuid,
    actor_type text not null,
    organization_id uuid,
    resource_type text,
    resource_id text,
    result text not null,
    reason_code text,
    permission_code text,
    correlation_id text not null,
    source text not null,
    metadata jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null default timezone('utc', now()),
    retention_policy_key text not null default 'SECURITY_24_MONTHS',
    created_at timestamptz not null default timezone('utc', now()),
    constraint security_events_correlation_chk check (correlation_id ~ '^[A-Za-z0-9_-]{8,64}$')
);

create index if not exists security_events_occurred_idx on public.security_events (occurred_at desc);
create index if not exists security_events_key_idx on public.security_events (event_key, occurred_at desc);

create table if not exists public.application_errors (
    id uuid primary key default gen_random_uuid(),
    error_code text not null,
    module text not null,
    layer text not null,
    severity text not null,
    sensitivity text not null default 'INTERNAL',
    correlation_id text not null,
    actor_user_id uuid,
    organization_id uuid,
    sanitized_message text not null,
    fingerprint text not null,
    is_retryable boolean not null default false,
    app_version text,
    platform text,
    environment text not null default 'unknown',
    metadata jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null default timezone('utc', now()),
    resolved_at timestamptz,
    created_at timestamptz not null default timezone('utc', now()),
    constraint application_errors_correlation_chk check (correlation_id ~ '^[A-Za-z0-9_-]{8,64}$'),
    constraint application_errors_message_len check (char_length(sanitized_message) <= 512)
);

create index if not exists application_errors_fp_window_idx
    on public.application_errors (fingerprint, occurred_at desc);

create table if not exists public.observability_export_requests (
    id uuid primary key default gen_random_uuid(),
    requested_by uuid not null,
    scope text not null,
    organization_id uuid,
    sensitivity text not null,
    filters jsonb not null default '{}'::jsonb,
    status text not null,
    reason text,
    correlation_id text not null,
    requested_at timestamptz not null default timezone('utc', now()),
    completed_at timestamptz,
    failure_code text,
    created_at timestamptz not null default timezone('utc', now()),
    constraint export_scope_chk check (scope in ('OWN_ORG','PLATFORM_AUDIT','SECURITY','ERRORS')),
    constraint export_status_chk check (status in ('REQUESTED','DENIED','READY_SIMULATED','FAILED')),
    constraint export_correlation_chk check (correlation_id ~ '^[A-Za-z0-9_-]{8,64}$')
);

delete from public.observability_event_catalog;
insert into public.observability_event_catalog (event_key, module, category, default_severity, sensitivity, organization_scoped, retention_policy_key, remote_persistence_allowed, analytics_allowed, allowed_metadata_keys, required_metadata_keys) values
('m00.config.loaded','M00','SYSTEM','INFO','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m00.config.missing','M00','ERROR','ERROR','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m00.feature_flag.evaluated','M00','SYSTEM','DEBUG','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m00.log.sanitized','M00','SECURITY','DEBUG','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m00.log.level_gate','M00','SYSTEM','INFO','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m00.build.debug_assemble','M00','JOB','INFO','INTERNAL',false,'TECHNICAL_30_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m00.ci.unit_tests','M00','JOB','INFO','INTERNAL',false,'TECHNICAL_30_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m00.ci.lint','M00','JOB','WARNING','INTERNAL',false,'TECHNICAL_30_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m00.error.app_result_failure','M00','ERROR','ERROR','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.auth.login_success','M01','SECURITY','INFO','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.auth.login_failure','M01','SECURITY','WARNING','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY['result']::text[]),
('m01.auth.logout','M01','SECURITY','INFO','INTERNAL',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY['result']::text[]),
('m01.auth.verify_email','M01','SECURITY','INFO','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.auth.password_recovery','M01','SECURITY','WARNING','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.auth.password_changed','M01','SECURITY','NOTICE','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.consent.recorded','M01','AUDIT','INFO','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.consent.gate_unavailable','M01','ERROR','WARNING','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.account.deletion_requested','M01','AUDIT','NOTICE','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.account.deletion_completed','M01','AUDIT','NOTICE','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m01.account.deletion_failed','M01','ERROR','ERROR','SECURITY_SENSITIVE',false,'UNTIL_RESOLUTION',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m02.profile.onboarding_completed','M02','AUDIT','INFO','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m02.profile.updated','M02','AUDIT','INFO','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m02.privacy.settings_changed','M02','AUDIT','INFO','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m02.role.assigned','M02','AUDIT','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m02.role.revoked','M02','AUDIT','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m02.role.expired','M02','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m02.status.changed','M02','AUDIT','WARNING','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m02.permission.denied','M02','AUTHORIZATION','WARNING','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY['permission_code','result']::text[]),
('m02.admin.audit_read','M02','DATA_ACCESS','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.org.created','M03','AUDIT','INFO','CONFIDENTIAL',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.invitation.created','M03','AUDIT','INFO','CONFIDENTIAL',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.invitation.accepted','M03','AUDIT','INFO','CONFIDENTIAL',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.invitation.declined','M03','AUDIT','INFO','CONFIDENTIAL',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.invitation.expired','M03','AUDIT','INFO','CONFIDENTIAL',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.member.role_changed','M03','AUDIT','NOTICE','CONFIDENTIAL',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.member.removed','M03','AUDIT','NOTICE','CONFIDENTIAL',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.ownership.transferred','M03','AUDIT','WARNING','RESTRICTED',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m03.branch.changed','M03','AUDIT','INFO','CONFIDENTIAL',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.report.created','M04','MODERATION','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.report.triaged','M04','MODERATION','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.report.marked_duplicate','M04','MODERATION','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.case.created','M04','MODERATION','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.case.report_attached','M04','MODERATION','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.case.assigned','M04','MODERATION','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.case.status_changed','M04','MODERATION','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.case.internal_note_added','M04','MODERATION','INFO','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.action.applied','M04','MODERATION','WARNING','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.moderation.action_applied','M04','MODERATION','WARNING','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.appeal.submitted','M04','MODERATION','INFO','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.appeal.assigned','M04','MODERATION','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.appeal.reviewed','M04','MODERATION','NOTICE','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.appeal.resolved','M04','MODERATION','NOTICE','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.verification.assigned','M04','MODERATION','INFO','RESTRICTED',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.verification.decided','M04','MODERATION','NOTICE','RESTRICTED',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.verification.notify','M04','MODERATION','NOTICE','SECURITY_SENSITIVE',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.support.ticket_created','M04','SUPPORT','INFO','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.support.assigned','M04','SUPPORT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.support.status_changed','M04','SUPPORT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.support.internal_message','M04','SUPPORT','INFO','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.support.visible_reply','M04','SUPPORT','INFO','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.support.internal_update','M04','SUPPORT','INFO','SECURITY_SENSITIVE',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.sensitive.access_projection','M04','DATA_ACCESS','INFO','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m04.audit.helper_write','M04','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.upload.session_started','M05','FILE','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.upload.completed','M05','FILE','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.upload.failed','M05','ERROR','WARNING','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.upload.cancelled','M05','FILE','INFO','CONFIDENTIAL',false,'TECHNICAL_30_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.verification_document.ready','M05','FILE','NOTICE','SECURITY_SENSITIVE',true,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.signed_url.issued','M05','DATA_ACCESS','INFO','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.download.performed','M05','DATA_ACCESS','INFO','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.file.deleted','M05','FILE','NOTICE','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.retention.expiry','M05','JOB','INFO','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m05.storage.error','M05','ERROR','ERROR','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.event.enqueued','M06','NOTIFICATION','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.recipient.materialized','M06','NOTIFICATION','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.inbox.read','M06','NOTIFICATION','DEBUG','CONFIDENTIAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.inbox.archived','M06','NOTIFICATION','DEBUG','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.inbox.deleted_logical','M06','NOTIFICATION','DEBUG','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.delivery.in_app','M06','NOTIFICATION','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.delivery.push_planned','M06','NOTIFICATION','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.delivery.push_claimed','M06','NOTIFICATION','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.delivery.push_result','M06','NOTIFICATION','INFO','CONFIDENTIAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.delivery.token_invalidated','M06','SECURITY','NOTICE','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.outbox.enqueued','M06','JOB','INFO','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.outbox.claimed','M06','JOB','INFO','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.outbox.processed','M06','JOB','INFO','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.outbox.failed','M06','JOB','WARNING','INTERNAL',false,'UNTIL_RESOLUTION',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.dead_letter.recorded','M06','ERROR','ERROR','INTERNAL',false,'UNTIL_RESOLUTION',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.emit.failed_swallowed','M06','ERROR','WARNING','INTERNAL',false,'UNTIL_RESOLUTION',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.installation.registered','M06','SECURITY','INFO','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.installation.token_rotated','M06','SECURITY','INFO','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.installation.revoked','M06','SECURITY','NOTICE','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.preference.updated','M06','AUDIT','INFO','CONFIDENTIAL',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.deep_link.resolved','M06','NOTIFICATION','DEBUG','CONFIDENTIAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.deep_link.permission_denied','M06','AUTHORIZATION','WARNING','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY['permission_code','result']::text[]),
('m06.access_audit.decision','M06','AUDIT','INFO','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.legacy.create_notification','M06','NOTIFICATION','INFO','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m06.edge.push_invoked','M06','INTEGRATION','INFO','SECURITY_SENSITIVE',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m07.event.accepted','M07','SYSTEM','INFO','INTERNAL',false,'TECHNICAL_30_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m07.event.rejected','M07','ERROR','WARNING','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m07.event.sanitized','M07','SECURITY','DEBUG','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m07.correlation.created','M07','SYSTEM','DEBUG','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m07.correlation.propagated','M07','SYSTEM','DEBUG','INTERNAL',false,'NO_REMOTE',false,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m07.error.captured','M07','ERROR','ERROR','INTERNAL',false,'TECHNICAL_90_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY['result']::text[]),
('m07.health.checked','M07','HEALTH','INFO','INTERNAL',false,'TECHNICAL_30_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY['result']::text[]),
('m07.export.requested','M07','EXPORT','NOTICE','RESTRICTED',false,'AUDIT_12_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]),
('m07.export.denied','M07','AUTHORIZATION','WARNING','SECURITY_SENSITIVE',false,'SECURITY_24_MONTHS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY['result']::text[]),
('m07.alert.rule_evaluated','M07','SYSTEM','INFO','INTERNAL',false,'TECHNICAL_30_DAYS',true,false, ARRAY['event_key','module','result','reason_code','permission_code','resource_type','resource_id','organization_id','channel','attempt_count','error_code','app_version','platform','environment','build_type','job_name','duration_ms','status_code','feature_flag','correlation_id','request_id','installation_fingerprint','file_type','file_size_bucket']::text[], ARRAY[]::text[]);



;


-- ---------------------------------------------------------------------------
-- Helpers + writers
-- ---------------------------------------------------------------------------

create or replace function public.m07_new_correlation_id()
returns text language plpgsql security definer set search_path = public as $$
begin
  return substr(replace(gen_random_uuid()::text, '-', ''), 1, 32);
end; $$;

create or replace function public.m07_validate_correlation_id(p_correlation_id text)
returns text language plpgsql security definer set search_path = public as $$
declare v text := trim(coalesce(p_correlation_id, ''));
begin
  if v = '' then return public.m07_new_correlation_id(); end if;
  if v !~ '^[A-Za-z0-9_-]{8,64}$' then
    raise exception using errcode = '22023', message = 'OBS_CORRELATION_INVALID';
  end if;
  if position('@' in v) > 0 or position('.' in v) > 0
     or lower(v) like 'user-%' or lower(v) like 'email-%' then
    raise exception using errcode = '22023', message = 'OBS_CORRELATION_INVALID';
  end if;
  return v;
end; $$;

create or replace function public.m07_validate_event_key(p_event_key text)
returns public.observability_event_catalog
language plpgsql security definer set search_path = public as $$
declare v_row public.observability_event_catalog;
begin
  select * into v_row from public.observability_event_catalog where event_key = trim(p_event_key);
  if not found then
    raise exception using errcode = '22023', message = 'OBS_EVENT_UNKNOWN';
  end if;
  if not v_row.remote_persistence_allowed then
    raise exception using errcode = '22023', message = 'OBS_WRITE_DENIED';
  end if;
  return v_row;
end; $$;

create or replace function public.m07_sanitize_reason_code(p_reason text)
returns text language plpgsql immutable set search_path = public as $$
declare v text := upper(trim(coalesce(p_reason, '')));
begin
  if v = '' then return null; end if;
  v := regexp_replace(v, '[^A-Z0-9_.-]', '', 'g');
  if char_length(v) > 64 then v := substr(v, 1, 64); end if;
  if v ~* 'PASSWORD|TOKEN|BEARER|JWT|SERVICE_ROLE|SIGNED|EMAIL|STACK|SQL' then
    return 'REDACTED';
  end if;
  return v;
end; $$;

create or replace function public.m07_validate_metadata(p_event_key text, p_metadata jsonb)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_cat public.observability_event_catalog;
  v_meta jsonb := coalesce(p_metadata, '{}'::jsonb);
  v_key text; v_req text; v_out jsonb := '{}'::jsonb; v_val text;
begin
  select * into v_cat from public.observability_event_catalog where event_key = trim(p_event_key);
  if not found then raise exception using errcode = '22023', message = 'OBS_EVENT_UNKNOWN'; end if;
  for v_key in select jsonb_object_keys(v_meta) loop
    if not (v_key = any (v_cat.allowed_metadata_keys)) then
      raise exception using errcode = '22023', message = 'OBS_METADATA_DENIED';
    end if;
    if lower(v_key) ~ '(password|token|jwt|bearer|secret|service_role|signed|email|phone|chat|internal|sql|stack|document|base64)' then
      raise exception using errcode = '22023', message = 'OBS_METADATA_DENIED';
    end if;
    v_val := left(coalesce(v_meta ->> v_key, ''), 256);
    v_val := regexp_replace(v_val, 'eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+', '[REDACTED_TOKEN]', 'g');
    v_val := regexp_replace(v_val, '(?i)bearer\s+[a-zA-Z0-9._\-]+', 'Bearer [REDACTED_TOKEN]', 'g');
    v_val := regexp_replace(v_val, '(?i)https?://\S+', '[REDACTED_URL]', 'g');
    v_out := v_out || jsonb_build_object(v_key, v_val);
  end loop;
  foreach v_req in array coalesce(v_cat.required_metadata_keys, array[]::text[]) loop
    if coalesce(v_out ->> v_req, '') = '' then
      raise exception using errcode = '22023', message = 'OBS_METADATA_DENIED';
    end if;
  end loop;
  return v_out;
end; $$;

create or replace function public.m07_require_actor()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED'; end if;
  return v;
end; $$;

create or replace function public.m07_resolve_actor_type(p_hint text default null)
returns text language plpgsql stable security definer set search_path = public as $$
begin
  if auth.uid() is null then return 'ANONYMOUS'; end if;
  if coalesce(current_setting('request.jwt.claim.role', true), '') = 'service_role'
     or coalesce(auth.role(), '') = 'service_role' then
    return coalesce(nullif(upper(trim(p_hint)), ''), 'SYSTEM');
  end if;
  if public.has_permission('audit.view') then return 'PLATFORM_STAFF'; end if;
  return 'AUTHENTICATED_USER';
end; $$;

create or replace function public.m07_resolve_organization_scope(p_organization_id uuid)
returns uuid language plpgsql stable security definer set search_path = public as $$
begin
  if p_organization_id is null then return null; end if;
  if public.has_permission('audit.view') then return p_organization_id; end if;
  if exists (
    select 1 from public.organization_memberships m
    where m.organization_id = p_organization_id and m.user_id = auth.uid()
      and coalesce(m.status, 'ACTIVE') = 'ACTIVE'
  ) then return p_organization_id; end if;
  raise exception using errcode = '42501', message = 'OBS_PERMISSION_DENIED';
end; $$;

create or replace function public.m07_retention_until(p_key text, p_from timestamptz)
returns timestamptz language plpgsql immutable set search_path = public as $$
begin
  return case p_key
    when 'TECHNICAL_30_DAYS' then p_from + interval '30 days'
    when 'TECHNICAL_90_DAYS' then p_from + interval '90 days'
    when 'AUDIT_12_MONTHS' then p_from + interval '365 days'
    when 'SECURITY_24_MONTHS' then p_from + interval '730 days'
    when 'AGGREGATE_24_MONTHS' then p_from + interval '730 days'
    else null
  end;
end; $$;

-- Client-allowlisted security keys (explicitly permitted from Android)
create or replace function public.m07_client_security_event_allowed(p_event_key text)
returns boolean language sql immutable set search_path = public as $$
  select trim(p_event_key) in (
    'm01.auth.login_failure',
    'm01.auth.logout',
    'm02.permission.denied',
    'm06.deep_link.permission_denied',
    'm07.export.denied'
  );
$$;

create or replace function public.m07_client_error_allowed(p_error_code text)
returns boolean language sql immutable set search_path = public as $$
  select upper(trim(p_error_code)) in (
    'OBS_UNKNOWN','OBS_WRITE_FAILED','OBS_CORRELATION_INVALID','OBS_METADATA_DENIED',
    'OBS_EVENT_UNKNOWN','OBS_PERMISSION_DENIED','M01_CONSENT_GATE_UNAVAILABLE',
    'M05_STORAGE_ERROR','M05_SIGNED_URL_ERROR','M06_DEEP_LINK_DENIED'
  );
$$;

create or replace function public.m07_write_audit_event(
  p_event_key text,
  p_action text,
  p_result text,
  p_correlation_id text,
  p_resource_type text default null,
  p_resource_id text default null,
  p_organization_id uuid default null,
  p_reason_code text default null,
  p_request_id text default null,
  p_source text default 'SERVER',
  p_metadata jsonb default '{}'::jsonb,
  p_actor_technical_id text default null
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_cat public.observability_event_catalog;
  v_id uuid;
  v_corr text;
  v_meta jsonb;
  v_actor uuid := auth.uid();
  v_org uuid;
  v_now timestamptz := timezone('utc', now());
begin
  begin
    v_cat := public.m07_validate_event_key(p_event_key);
    v_corr := public.m07_validate_correlation_id(p_correlation_id);
    v_meta := public.m07_validate_metadata(p_event_key, p_metadata);
    if v_cat.organization_scoped then
      v_org := public.m07_resolve_organization_scope(p_organization_id);
    else
      v_org := p_organization_id;
    end if;
    insert into public.audit_events(
      event_key, category, severity, sensitivity, actor_user_id, actor_type,
      actor_technical_id, organization_id, resource_type, resource_id, action, result,
      reason_code, correlation_id, request_id, source, metadata, occurred_at,
      retention_policy_key, retention_until
    ) values (
      v_cat.event_key, v_cat.category, v_cat.default_severity, v_cat.sensitivity,
      v_actor, public.m07_resolve_actor_type(null), nullif(trim(coalesce(p_actor_technical_id,'')),''),
      v_org, nullif(trim(coalesce(p_resource_type,'')),''), nullif(trim(coalesce(p_resource_id,'')),''),
      upper(trim(p_action)), upper(trim(p_result)),
      public.m07_sanitize_reason_code(p_reason_code), v_corr,
      nullif(trim(coalesce(p_request_id,'')),''), upper(trim(coalesce(p_source,'SERVER'))),
      v_meta, v_now, v_cat.retention_policy_key,
      public.m07_retention_until(v_cat.retention_policy_key, v_now)
    )
    returning id into v_id;
    return v_id;
  exception when others then
    -- best-effort: never leak SQL details
    raise exception using errcode = 'P0001', message = 'OBS_WRITE_FAILED';
  end;
end; $$;

create or replace function public.m07_write_security_event(
  p_event_key text,
  p_result text,
  p_correlation_id text,
  p_reason_code text default null,
  p_permission_code text default null,
  p_resource_type text default null,
  p_resource_id text default null,
  p_organization_id uuid default null,
  p_source text default 'SERVER',
  p_metadata jsonb default '{}'::jsonb
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_cat public.observability_event_catalog;
  v_id uuid;
  v_corr text;
  v_meta jsonb;
  v_actor uuid := auth.uid();
  v_role text := coalesce(auth.role(), '');
  v_now timestamptz := timezone('utc', now());
begin
  begin
    v_cat := public.m07_validate_event_key(p_event_key);
    -- Client callers may only write explicitly allowlisted security keys.
    if v_role <> 'service_role' and not public.m07_client_security_event_allowed(p_event_key) then
      raise exception using errcode = '42501', message = 'OBS_WRITE_DENIED';
    end if;
    v_corr := public.m07_validate_correlation_id(p_correlation_id);
    v_meta := public.m07_validate_metadata(p_event_key, coalesce(p_metadata,'{}'::jsonb) || jsonb_build_object('result', upper(trim(p_result))));
    insert into public.security_events(
      event_key, severity, sensitivity, actor_user_id, actor_type, organization_id,
      resource_type, resource_id, result, reason_code, permission_code, correlation_id,
      source, metadata, occurred_at, retention_policy_key
    ) values (
      v_cat.event_key, v_cat.default_severity, v_cat.sensitivity, v_actor,
      public.m07_resolve_actor_type(null), p_organization_id,
      nullif(trim(coalesce(p_resource_type,'')),''), nullif(trim(coalesce(p_resource_id,'')),''),
      upper(trim(p_result)), public.m07_sanitize_reason_code(p_reason_code),
      public.m07_sanitize_reason_code(p_permission_code), v_corr,
      upper(trim(coalesce(p_source,'SERVER'))), v_meta, v_now, v_cat.retention_policy_key
    ) returning id into v_id;
    return v_id;
  exception when others then
    raise exception using errcode = 'P0001', message = 'OBS_WRITE_FAILED';
  end;
end; $$;

create or replace function public.m07_write_application_error(
  p_error_code text,
  p_module text,
  p_layer text,
  p_correlation_id text,
  p_sanitized_message text,
  p_fingerprint text,
  p_severity text default 'ERROR',
  p_is_retryable boolean default false,
  p_app_version text default null,
  p_platform text default null,
  p_environment text default 'unknown',
  p_organization_id uuid default null,
  p_metadata jsonb default '{}'::jsonb
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
  v_corr text;
  v_msg text;
  v_fp text;
  v_actor uuid := auth.uid();
  v_role text := coalesce(auth.role(), '');
  v_existing uuid;
  v_now timestamptz := timezone('utc', now());
begin
  begin
    if v_role <> 'service_role' and not public.m07_client_error_allowed(p_error_code) then
      raise exception using errcode = '42501', message = 'OBS_WRITE_DENIED';
    end if;
    v_corr := public.m07_validate_correlation_id(p_correlation_id);
    v_msg := left(coalesce(p_sanitized_message, 'REDACTED'), 512);
    v_msg := regexp_replace(v_msg, 'eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+', '[REDACTED_TOKEN]', 'g');
    v_msg := regexp_replace(v_msg, '(?i)bearer\s+\S+', 'Bearer [REDACTED_TOKEN]', 'g');
    v_msg := regexp_replace(v_msg, '(?i)https?://\S+', '[REDACTED_URL]', 'g');
    if v_msg ~* 'at [A-Za-z0-9_.$]+\(|Caused by:' then
      v_msg := '[REDACTED_STACK]';
    end if;
    v_fp := left(regexp_replace(coalesce(p_fingerprint, 'fp_unknown'), '[^A-Za-z0-9_]', '', 'g'), 64);
    select id into v_existing from public.application_errors
    where fingerprint = v_fp and occurred_at > v_now - interval '15 minutes'
    order by occurred_at desc limit 1;
    if v_existing is not null then return v_existing; end if;
    insert into public.application_errors(
      error_code, module, layer, severity, sensitivity, correlation_id, actor_user_id,
      organization_id, sanitized_message, fingerprint, is_retryable, app_version, platform,
      environment, metadata, occurred_at
    ) values (
      upper(trim(p_error_code)), upper(trim(p_module)), upper(trim(p_layer)),
      upper(trim(coalesce(p_severity,'ERROR'))), 'INTERNAL', v_corr, v_actor,
      p_organization_id, v_msg, v_fp, coalesce(p_is_retryable,false),
      nullif(trim(coalesce(p_app_version,'')),''), nullif(trim(coalesce(p_platform,'')),''),
      coalesce(nullif(trim(p_environment),''),'unknown'),
      coalesce(p_metadata,'{}'::jsonb), v_now
    ) returning id into v_id;
    return v_id;
  exception when others then
    raise exception using errcode = 'P0001', message = 'OBS_WRITE_FAILED';
  end;
end; $$;

create or replace function public.m07_request_export(
  p_scope text,
  p_sensitivity text,
  p_correlation_id text,
  p_organization_id uuid default null,
  p_filters jsonb default '{}'::jsonb,
  p_reason text default null
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public.m07_require_actor();
  v_corr text;
  v_id uuid;
  v_status text := 'REQUESTED';
  v_sens text := upper(trim(p_sensitivity));
  v_scope text := upper(trim(p_scope));
  v_filters jsonb := coalesce(p_filters, '{}'::jsonb);
begin
  v_corr := public.m07_validate_correlation_id(p_correlation_id);
  if v_filters::text ~* 'signed_url|https?://|password|token|email' then
    raise exception using errcode = '22023', message = 'OBS_METADATA_DENIED';
  end if;
  if not public.has_permission('audit.view') then
    v_status := 'DENIED';
    insert into public.observability_export_requests(
      requested_by, scope, organization_id, sensitivity, filters, status, reason,
      correlation_id, failure_code
    ) values (
      v_actor, v_scope, p_organization_id, v_sens, v_filters, v_status,
      public.m07_sanitize_reason_code(p_reason), v_corr, 'OBS_EXPORT_DENIED'
    ) returning id into v_id;
    perform public.m07_write_security_event(
      'm07.export.denied', 'DENIED', v_corr, 'OBS_EXPORT_DENIED', 'audit.view',
      'export', v_id::text, p_organization_id, 'CLIENT',
      jsonb_build_object('result','DENIED','reason_code','OBS_EXPORT_DENIED')
    );
    raise exception using errcode = '42501', message = 'OBS_EXPORT_DENIED';
  end if;
  if v_sens in ('RESTRICTED','SECURITY_SENSITIVE') and not public.has_permission('audit.view') then
    -- elevated: reuse audit.view as proxy until dedicated export permission exists in M02
    null;
  end if;
  if v_scope = 'OWN_ORG' then
    perform public.m07_resolve_organization_scope(p_organization_id);
  end if;
  insert into public.observability_export_requests(
    requested_by, scope, organization_id, sensitivity, filters, status, reason, correlation_id
  ) values (
    v_actor, v_scope, p_organization_id, v_sens, v_filters, 'READY_SIMULATED',
    public.m07_sanitize_reason_code(p_reason), v_corr
  ) returning id into v_id;
  perform public.m07_write_audit_event(
    'm07.export.requested', 'REQUEST_EXPORT', 'SUCCESS', v_corr,
    'export', v_id::text, p_organization_id, 'EXPORT_REQUESTED', null, 'CLIENT',
    jsonb_build_object('result','SUCCESS')
  );
  return v_id;
end; $$;

create or replace function public.m07_list_audit_events(
  p_limit int default 50,
  p_offset int default 0,
  p_organization_id uuid default null,
  p_event_key text default null
) returns setof public.audit_events
language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public.m07_require_actor();
begin
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_READ_DENIED';
  end if;
  return query
  select a.* from public.audit_events a
  where (p_organization_id is null or a.organization_id = p_organization_id)
    and (p_event_key is null or a.event_key = p_event_key)
    and (
      a.sensitivity not in ('RESTRICTED','SECURITY_SENSITIVE')
      or public.has_permission('audit.view')
    )
  order by a.occurred_at desc
  limit greatest(1, least(coalesce(p_limit,50), 200))
  offset greatest(0, coalesce(p_offset,0));
end; $$;

create or replace function public.m07_list_security_events(
  p_limit int default 50,
  p_offset int default 0
) returns setof public.security_events
language plpgsql security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_READ_DENIED';
  end if;
  return query
  select s.* from public.security_events s
  order by s.occurred_at desc
  limit greatest(1, least(coalesce(p_limit,50), 200))
  offset greatest(0, coalesce(p_offset,0));
end; $$;

create or replace function public.m07_list_application_errors(
  p_limit int default 50,
  p_offset int default 0
) returns setof public.application_errors
language plpgsql security definer set search_path = public as $$
begin
  perform public.m07_require_actor();
  if not public.has_permission('audit.view') then
    raise exception using errcode = '42501', message = 'OBS_READ_DENIED';
  end if;
  return query
  select e.* from public.application_errors e
  order by e.occurred_at desc
  limit greatest(1, least(coalesce(p_limit,50), 200))
  offset greatest(0, coalesce(p_offset,0));
end; $$;

-- Best-effort internal helpers for instrumentation (service_role)
create or replace function public.m07_best_effort_security(
  p_event_key text, p_result text, p_correlation_id text,
  p_reason_code text default null, p_permission_code text default null,
  p_metadata jsonb default '{}'::jsonb
) returns void
language plpgsql security definer set search_path = public as $$
begin
  begin
    perform public.m07_write_security_event(
      p_event_key, p_result, p_correlation_id, p_reason_code, p_permission_code,
      null, null, null, 'SERVER', coalesce(p_metadata,'{}'::jsonb)
    );
  exception when others then
    null; -- never abort domain mutation
  end;
end; $$;

create or replace function public.m07_best_effort_audit(
  p_event_key text, p_action text, p_result text, p_correlation_id text,
  p_resource_type text default null, p_resource_id text default null,
  p_metadata jsonb default '{}'::jsonb
) returns void
language plpgsql security definer set search_path = public as $$
begin
  begin
    perform public.m07_write_audit_event(
      p_event_key, p_action, p_result, p_correlation_id,
      p_resource_type, p_resource_id, null, null, null, 'SERVER', coalesce(p_metadata,'{}'::jsonb)
    );
  exception when others then null;
  end;
end; $$;

-- ---------------------------------------------------------------------------
-- RLS deny-by-default
-- ---------------------------------------------------------------------------

alter table public.observability_event_catalog enable row level security;
alter table public.audit_events enable row level security;
alter table public.security_events enable row level security;
alter table public.application_errors enable row level security;
alter table public.observability_export_requests enable row level security;

drop policy if exists observability_event_catalog_no_client on public.observability_event_catalog;
create policy observability_event_catalog_no_client on public.observability_event_catalog
  for all to authenticated using (false) with check (false);

drop policy if exists audit_events_no_client_write on public.audit_events;
create policy audit_events_no_client_write on public.audit_events
  for all to authenticated using (false) with check (false);

drop policy if exists security_events_no_client_write on public.security_events;
create policy security_events_no_client_write on public.security_events
  for all to authenticated using (false) with check (false);

drop policy if exists application_errors_no_client_write on public.application_errors;
create policy application_errors_no_client_write on public.application_errors
  for all to authenticated using (false) with check (false);

drop policy if exists observability_export_requests_no_direct on public.observability_export_requests;
create policy observability_export_requests_no_direct on public.observability_export_requests
  for all to authenticated using (false) with check (false);

revoke all on table public.observability_event_catalog from public, anon, authenticated;
revoke all on table public.audit_events from public, anon, authenticated;
revoke all on table public.security_events from public, anon, authenticated;
revoke all on table public.application_errors from public, anon, authenticated;
revoke all on table public.observability_export_requests from public, anon, authenticated;

-- Grants: revoke PUBLIC/anon/authenticated from internal helpers; grant execute selectively
do $$
declare
  fn text;
begin
  foreach fn in array array[
    'm07_new_correlation_id()','m07_validate_correlation_id(text)',
    'm07_validate_event_key(text)','m07_validate_metadata(text,jsonb)',
    'm07_require_actor()','m07_resolve_actor_type(text)','m07_resolve_organization_scope(uuid)',
    'm07_retention_until(text,timestamptz)','m07_sanitize_reason_code(text)',
    'm07_client_security_event_allowed(text)','m07_client_error_allowed(text)',
    'm07_best_effort_security(text,text,text,text,text,jsonb)',
    'm07_best_effort_audit(text,text,text,text,text,text,jsonb)'
  ] loop
    execute format('revoke all on function public.%s from public, anon, authenticated', fn);
    execute format('grant execute on function public.%s to service_role', fn);
  end loop;
end $$;

revoke all on function public.m07_write_audit_event(text,text,text,text,text,text,uuid,text,text,text,jsonb,text) from public, anon, authenticated;
grant execute on function public.m07_write_audit_event(text,text,text,text,text,text,uuid,text,text,text,jsonb,text) to service_role;

revoke all on function public.m07_write_security_event(text,text,text,text,text,text,text,uuid,text,jsonb) from public, anon;
grant execute on function public.m07_write_security_event(text,text,text,text,text,text,text,uuid,text,jsonb) to authenticated, service_role;

revoke all on function public.m07_write_application_error(text,text,text,text,text,text,text,boolean,text,text,text,uuid,jsonb) from public, anon;
grant execute on function public.m07_write_application_error(text,text,text,text,text,text,text,boolean,text,text,text,uuid,jsonb) to authenticated, service_role;

revoke all on function public.m07_request_export(text,text,text,uuid,jsonb,text) from public, anon;
grant execute on function public.m07_request_export(text,text,text,uuid,jsonb,text) to authenticated, service_role;

revoke all on function public.m07_list_audit_events(int,int,uuid,text) from public, anon;
grant execute on function public.m07_list_audit_events(int,int,uuid,text) to authenticated, service_role;

revoke all on function public.m07_list_security_events(int,int) from public, anon;
grant execute on function public.m07_list_security_events(int,int) to authenticated, service_role;

revoke all on function public.m07_list_application_errors(int,int) from public, anon;
grant execute on function public.m07_list_application_errors(int,int) to authenticated, service_role;

comment on table public.observability_event_catalog is 'M07 server catalog; Kotlin ObservabilityEventCatalog is app contract. Drift detection: compare event_key sets in CI (future stage).';
comment on table public.audit_events is 'M07 append-only audit; client has no direct DML.';
comment on table public.security_events is 'M07 security events; no passwords/tokens/PII.';
comment on table public.application_errors is 'M07 sanitized errors; no raw Throwable/stack.';
comment on table public.observability_export_requests is 'M07 export requests only; no files/signed URLs in Etapa 3.';


-- ---------------------------------------------------------------------------
-- Client-limited data-access notes (M05) + triggers for M06 dead-letter / access
-- ---------------------------------------------------------------------------

create or replace function public.m07_client_note_data_access(
  p_event_key text,
  p_correlation_id text,
  p_result text default 'SUCCESS',
  p_resource_id text default null,
  p_error_code text default null,
  p_metadata jsonb default '{}'::jsonb
) returns uuid
language plpgsql security definer set search_path = public as $
declare
  v_key text := trim(p_event_key);
  v_id uuid;
begin
  if v_key not in ('m05.signed_url.issued','m05.download.performed','m05.storage.error',
                   'm06.access_audit.decision','m04.sensitive.access_projection','m02.admin.audit_read',
                   'm01.account.deletion_failed') then
    raise exception using errcode = '42501', message = 'OBS_WRITE_DENIED';
  end if;
  -- Never accept URL/path-like metadata
  if coalesce(p_metadata,'{}'::jsonb)::text ~* 'https?://|signed_url|/storage/v1|Bearer |eyJ' then
    raise exception using errcode = '22023', message = 'OBS_METADATA_DENIED';
  end if;
  begin
    if v_key = 'm05.storage.error' then
      v_id := public.m07_write_application_error(
        coalesce(nullif(trim(p_error_code),''),'M05_STORAGE_ERROR'),
        'M05','ANDROID', p_correlation_id,
        coalesce(p_error_code,'STORAGE_ERROR'), 'fp_m05_storage',
        'ERROR', true, null, 'ANDROID', 'client', null,
        coalesce(p_metadata,'{}'::jsonb) || jsonb_build_object('result', upper(trim(p_result)), 'error_code', coalesce(p_error_code,'M05_STORAGE_ERROR'))
      );
    elsif v_key in ('m05.signed_url.issued','m05.download.performed','m02.admin.audit_read','m04.sensitive.access_projection','m06.access_audit.decision') then
      v_id := public.m07_write_audit_event(
        v_key, upper(replace(split_part(v_key,'.',3),'_','')), upper(trim(p_result)),
        p_correlation_id, 'file', nullif(trim(coalesce(p_resource_id,'')),''), null, null, null, 'CLIENT',
        coalesce(p_metadata,'{}'::jsonb) || jsonb_build_object('result', upper(trim(p_result)))
      );
    else
      v_id := public.m07_write_audit_event(
        v_key, 'DELETE_ACCOUNT', upper(trim(p_result)), p_correlation_id,
        'account', null, null, coalesce(p_error_code,'DELETION_FAILED'), null, 'CLIENT',
        coalesce(p_metadata,'{}'::jsonb) || jsonb_build_object('result', upper(trim(p_result)), 'error_code', coalesce(p_error_code,'DELETION_FAILED'))
      );
    end if;
    return v_id;
  exception when others then
    raise exception using errcode = 'P0001', message = 'OBS_WRITE_FAILED';
  end;
end; $;

revoke all on function public.m07_client_note_data_access(text,text,text,text,text,jsonb) from public, anon;
grant execute on function public.m07_client_note_data_access(text,text,text,text,text,jsonb) to authenticated, service_role;

-- Trigger: dead-letter insert → M07 error/audit best-effort (no sensitive payload)
create or replace function public.m07_trg_dead_letter_observe()
returns trigger language plpgsql security definer set search_path = public as $
begin
  begin
    perform public.m07_write_application_error(
      coalesce(nullif(trim(new.error_code),''),'M06_DEAD_LETTER'),
      'M06','DATABASE', public.m07_new_correlation_id(),
      left(coalesce(new.error_code,'DEAD_LETTER'), 120),
      'fp_dl_' || substr(md5(coalesce(new.error_code,'') || coalesce(new.event_id::text,'')),1,16),
      'ERROR', true, null, null, 'server', null,
      jsonb_build_object('result','DEAD_LETTER','error_code', coalesce(new.error_code,'DEAD_LETTER'))
    );
  exception when others then null;
  end;
  return new;
end; $;

drop trigger if exists trg_m07_dead_letter_observe on public.notification_dead_letters;
create trigger trg_m07_dead_letter_observe
  after insert on public.notification_dead_letters
  for each row execute function public.m07_trg_dead_letter_observe();

revoke all on function public.m07_trg_dead_letter_observe() from public, anon, authenticated;
grant execute on function public.m07_trg_dead_letter_observe() to service_role;

commit;

