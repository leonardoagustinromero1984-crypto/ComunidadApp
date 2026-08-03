-- =============================================================================
-- LeoVer M27 — migración 076: operaciones, seguridad y entrega de webhooks (Bloque 3).
-- Forward-only sobre 001–075. LOCAL ONLY: no aplicar a staging sin autorización.
-- Sin pagos M24. OAuth stub — sin proveedor externo.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos organización integration.manage
-- ---------------------------------------------------------------------------

insert into public.organization_permissions (code, description)
values ('integration.manage', 'Gestionar apps de integración LeoVer API pública')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code = 'integration.manage'
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Tablas operativas Bloque 3
-- ---------------------------------------------------------------------------

create table if not exists public.m27_integration_apps (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete restrict,
  owner_user_id uuid not null references public.users(id) on delete restrict,
  name text not null,
  contract_version text not null default 'V1',
  granted_scopes text[] not null,
  status text not null default 'DRAFT',
  environment text not null default 'SANDBOX',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m27_int_app_status_chk check (
    status = any (array[
      'DRAFT','ACTIVE','PAUSED','REVOKED','SUSPENDED','ARCHIVED'
    ]::text[])
  ),
  constraint m27_int_app_env_chk check (
    environment = any (array['SANDBOX','STAGING','PRODUCTION']::text[])
  ),
  constraint m27_int_app_contract_chk check (contract_version = any (array['V1','V2']::text[])),
  constraint m27_int_app_name_chk check (char_length(trim(name)) between 3 and 80),
  constraint m27_int_app_scopes_chk check (array_length(granted_scopes, 1) between 1 and 20)
);

create index if not exists m27_int_app_org_idx
  on public.m27_integration_apps (organization_id, status);
create index if not exists m27_int_app_owner_idx
  on public.m27_integration_apps (owner_user_id, status);

-- Extender credenciales y webhooks de 075
alter table public.m27_api_credentials add column if not exists key_hash text;
alter table public.m27_api_credentials add column if not exists app_id uuid;

alter table public.m27_webhook_endpoints add column if not exists secret_hash text;
alter table public.m27_webhook_endpoints add column if not exists app_id uuid;

update public.m27_api_credentials
set key_hash = encode(digest('legacy-m27-key:' || id::text, 'sha256'), 'hex')
where key_hash is null;

update public.m27_webhook_endpoints
set secret_hash = encode(digest('legacy-m27-wh:' || id::text, 'sha256'), 'hex')
where secret_hash is null;

alter table public.m27_api_credentials alter column key_hash set not null;
alter table public.m27_webhook_endpoints alter column secret_hash set not null;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'm27_key_app_fk'
  ) then
    alter table public.m27_api_credentials
      add constraint m27_key_app_fk
      foreign key (app_id) references public.m27_integration_apps(id) on delete restrict;
  end if;
  if not exists (
    select 1 from pg_constraint where conname = 'm27_webhook_app_fk'
  ) then
    alter table public.m27_webhook_endpoints
      add constraint m27_webhook_app_fk
      foreign key (app_id) references public.m27_integration_apps(id) on delete restrict;
  end if;
end $$;

alter table public.m27_webhook_endpoints drop constraint if exists m27_webhook_status_chk;
alter table public.m27_webhook_endpoints add constraint m27_webhook_status_chk check (
  status = any (array[
    'ACTIVE','DISABLED','PENDING','PENDING_VERIFICATION','PAUSED','REVOKED'
  ]::text[])
);

alter table public.m27_webhook_endpoints drop constraint if exists m27_webhook_env_chk;
alter table public.m27_webhook_endpoints add constraint m27_webhook_env_chk check (
  environment = any (array['PRODUCTION','SANDBOX','STAGING']::text[])
);

alter table public.m27_api_credentials drop constraint if exists m27_key_env_chk;
alter table public.m27_api_credentials add constraint m27_key_env_chk check (
  environment = any (array['PRODUCTION','SANDBOX','STAGING']::text[])
);

alter table public.m27_rate_limit_quotas drop constraint if exists m27_quota_env_chk;
alter table public.m27_rate_limit_quotas add constraint m27_quota_env_chk check (
  environment = any (array['PRODUCTION','SANDBOX','STAGING']::text[])
);

insert into public.m27_rate_limit_quotas (environment, requests_per_minute, requests_per_day, burst_allowance)
values ('STAGING', 60, 20000, 10)
on conflict (environment) do nothing;

create table if not exists public.m27_webhook_subscriptions (
  id uuid primary key default gen_random_uuid(),
  endpoint_id uuid not null references public.m27_webhook_endpoints(id) on delete restrict,
  event_type text not null,
  active boolean not null default true,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m27_sub_event_type_chk check (
    event_type = any (array[
      'PET_UPDATED','ADOPTION_PUBLISHED','ADOPTION_STATUS_CHANGED','EVENT_PUBLISHED',
      'BOOKING_STATUS_CHANGED','ORDER_STATUS_CHANGED','REVIEW_PUBLISHED'
    ]::text[])
  )
);

create unique index if not exists m27_webhook_sub_endpoint_event_uq
  on public.m27_webhook_subscriptions (endpoint_id, event_type)
  where active;

create table if not exists public.m27_webhook_events (
  id uuid primary key default gen_random_uuid(),
  app_id uuid not null references public.m27_integration_apps(id) on delete restrict,
  event_type text not null,
  version text not null default '1',
  resource_ref text not null,
  sanitized_payload text not null,
  occurred_at timestamptz not null default timezone('utc', now()),
  constraint m27_event_type_chk check (
    event_type = any (array[
      'PET_UPDATED','ADOPTION_PUBLISHED','ADOPTION_STATUS_CHANGED','EVENT_PUBLISHED',
      'BOOKING_STATUS_CHANGED','ORDER_STATUS_CHANGED','REVIEW_PUBLISHED'
    ]::text[])
  ),
  constraint m27_event_resource_chk check (char_length(trim(resource_ref)) between 1 and 200),
  constraint m27_event_payload_chk check (char_length(trim(sanitized_payload)) between 2 and 4000)
);

create index if not exists m27_webhook_events_app_idx
  on public.m27_webhook_events (app_id, occurred_at desc);

create table if not exists public.m27_webhook_deliveries (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.m27_webhook_events(id) on delete restrict,
  subscription_id uuid not null references public.m27_webhook_subscriptions(id) on delete restrict,
  endpoint_id uuid not null references public.m27_webhook_endpoints(id) on delete restrict,
  status text not null default 'PENDING',
  attempt_count integer not null default 0,
  max_attempts integer not null default 3,
  signature_version text not null default 'v1',
  signature_digest text not null default '',
  last_attempt_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m27_delivery_status_chk check (
    status = any (array[
      'PENDING','DELIVERING','DELIVERED','RETRY_SCHEDULED','FAILED','DEAD_LETTER','CANCELLED'
    ]::text[])
  ),
  constraint m27_delivery_attempts_chk check (attempt_count between 0 and 20),
  constraint m27_delivery_max_chk check (max_attempts between 1 and 10)
);

create unique index if not exists m27_delivery_event_sub_uq
  on public.m27_webhook_deliveries (event_id, subscription_id);

create index if not exists m27_webhook_deliveries_endpoint_idx
  on public.m27_webhook_deliveries (endpoint_id, status);

create table if not exists public.m27_webhook_attempts (
  id uuid primary key default gen_random_uuid(),
  delivery_id uuid not null references public.m27_webhook_deliveries(id) on delete restrict,
  attempt_number integer not null,
  outcome text not null,
  sanitized_error text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m27_attempt_number_chk check (attempt_number between 1 and 20),
  constraint m27_attempt_outcome_chk check (char_length(trim(outcome)) between 2 and 40)
);

create index if not exists m27_webhook_attempts_delivery_idx
  on public.m27_webhook_attempts (delivery_id, attempt_number);

create table if not exists public.m27_audit_log (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid not null references public.users(id) on delete restrict,
  app_id uuid references public.m27_integration_apps(id) on delete set null,
  operation text not null,
  outcome text not null,
  environment text not null,
  sanitized_reason text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m27_audit_op_chk check (char_length(trim(operation)) between 2 and 80),
  constraint m27_audit_outcome_chk check (char_length(trim(outcome)) between 2 and 40),
  constraint m27_audit_env_chk check (
    environment = any (array['SANDBOX','STAGING','PRODUCTION']::text[])
  )
);

create index if not exists m27_audit_log_app_idx
  on public.m27_audit_log (app_id, created_at desc);

create table if not exists public.m27_idempotency_keys (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid not null references public.users(id) on delete restrict,
  client_request_id text not null,
  operation text not null,
  resource_id uuid not null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m27_idem_client_len_chk check (char_length(trim(client_request_id)) between 1 and 120)
);

create unique index if not exists m27_idempotency_actor_client_uq
  on public.m27_idempotency_keys (actor_user_id, client_request_id);

create table if not exists public.m27_rate_limit_counters (
  counter_key text not null,
  window_bucket timestamptz not null,
  request_count integer not null default 0,
  primary key (counter_key, window_bucket),
  constraint m27_rl_count_chk check (request_count >= 0)
);

-- ---------------------------------------------------------------------------
-- 2. RLS deny-all + revocación directa
-- ---------------------------------------------------------------------------

alter table public.m27_integration_apps enable row level security;
alter table public.m27_webhook_subscriptions enable row level security;
alter table public.m27_webhook_events enable row level security;
alter table public.m27_webhook_deliveries enable row level security;
alter table public.m27_webhook_attempts enable row level security;
alter table public.m27_audit_log enable row level security;
alter table public.m27_idempotency_keys enable row level security;
alter table public.m27_rate_limit_counters enable row level security;

create policy m27_int_apps_deny on public.m27_integration_apps for all to authenticated using (false) with check (false);
create policy m27_subs_deny on public.m27_webhook_subscriptions for all to authenticated using (false) with check (false);
create policy m27_events_deny on public.m27_webhook_events for all to authenticated using (false) with check (false);
create policy m27_deliveries_deny on public.m27_webhook_deliveries for all to authenticated using (false) with check (false);
create policy m27_attempts_deny on public.m27_webhook_attempts for all to authenticated using (false) with check (false);
create policy m27_audit_deny on public.m27_audit_log for all to authenticated using (false) with check (false);
create policy m27_idem_deny on public.m27_idempotency_keys for all to authenticated using (false) with check (false);
create policy m27_rl_counters_deny on public.m27_rate_limit_counters for all to authenticated using (false) with check (false);

revoke all on table
  public.m27_integration_apps, public.m27_webhook_subscriptions, public.m27_webhook_events,
  public.m27_webhook_deliveries, public.m27_webhook_attempts, public.m27_audit_log,
  public.m27_idempotency_keys, public.m27_rate_limit_counters
from public, anon, authenticated;

grant all on table
  public.m27_integration_apps, public.m27_webhook_subscriptions, public.m27_webhook_events,
  public.m27_webhook_deliveries, public.m27_webhook_attempts, public.m27_audit_log,
  public.m27_idempotency_keys, public.m27_rate_limit_counters
to service_role;

-- Append-only audit
create or replace function public._m27_audit_append_only_guard()
returns trigger language plpgsql as $$
begin
  raise exception 'M27_AUDIT_APPEND_ONLY';
end;
$$;

drop trigger if exists m27_audit_no_update on public.m27_audit_log;
create trigger m27_audit_no_update
  before update or delete on public.m27_audit_log
  for each row execute function public._m27_audit_append_only_guard();

-- ---------------------------------------------------------------------------
-- 3. Helpers de dominio
-- ---------------------------------------------------------------------------

create or replace function public._m27_hash_secret(p_raw text)
returns text language sql immutable as $$
  select encode(digest(p_raw, 'sha256'), 'hex');
$$;

create or replace function public._m27_key_prefix(p_raw text, p_env text)
returns text language sql immutable as $$
  select case
    when upper(p_env) = 'SANDBOX' then 'lvk_sbx_' || right(p_raw, 4)
    when upper(p_env) = 'STAGING' then 'lvk_stg_' || right(p_raw, 4)
    else 'lvk_prod_' || right(p_raw, 4)
  end;
$$;

create or replace function public._m27_allowed_scopes()
returns text[] language sql immutable as $$
  select array[
    'pets.read.public','adoptions.read.public','events.read.public',
    'providers.read.public','marketplace.read.public','bookings.read.own',
    'webhooks.manage','sandbox.execute'
  ]::text[];
$$;

create or replace function public._m27_validate_scope_list(p_scopes text[])
returns void language plpgsql as $$
begin
  if p_scopes is null or array_length(p_scopes, 1) is null then
    raise exception 'M27_INVALID_SCOPE';
  end if;
  if exists (
    select 1 from unnest(p_scopes) s
    where s <> all (public._m27_allowed_scopes())
  ) then
    raise exception 'M27_UNKNOWN_SCOPE';
  end if;
end;
$$;

create or replace function public._m27_validate_requested_scopes(p_granted text[], p_requested text[])
returns void language plpgsql as $$
begin
  perform public._m27_validate_scope_list(p_requested);
  if exists (select 1 from unnest(p_requested) r where r <> all (p_granted)) then
    raise exception 'M27_SCOPE_DENIED';
  end if;
end;
$$;

create or replace function public._m27_filter_allowed_scopes(p_scopes text[])
returns text[] language sql immutable as $$
  select coalesce(array_agg(s order by s), '{}'::text[])
  from unnest(p_scopes) s
  where s = any (public._m27_allowed_scopes());
$$;

create or replace function public._m27_can_manage_org(p_actor uuid, p_org_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.organization_memberships m
    where m.organization_id = p_org_id
      and m.user_id = p_actor
      and m.status = 'ACTIVE'
      and (
        m.role_code in ('OWNER', 'ADMIN', 'MANAGER')
        or public.has_org_permission(p_org_id, 'integration.manage')
      )
  );
$$;

create or replace function public._m27_require_app(p_app_id uuid)
returns public.m27_integration_apps language plpgsql security definer set search_path = public as $$
declare v_app public.m27_integration_apps;
begin
  select * into v_app from public.m27_integration_apps where id = p_app_id;
  if not found then raise exception 'M27_APP_NOT_FOUND'; end if;
  return v_app;
end;
$$;

create or replace function public._m27_require_app_manager(p_actor uuid, p_app public.m27_integration_apps)
returns void language plpgsql security definer set search_path = public as $$
begin
  if p_app.owner_user_id = p_actor then return; end if;
  if public._m27_can_manage_org(p_actor, p_app.organization_id) then return; end if;
  raise exception 'M27_PERMISSION_DENIED';
end;
$$;

create or replace function public._m27_app_can_operate(p_status text)
returns boolean language sql immutable as $$
  select upper(p_status) = 'ACTIVE';
$$;

create or replace function public._m27_validate_app_transition(p_current text, p_target text)
returns void language plpgsql as $$
declare
  v_current text := upper(p_current);
  v_target text := upper(p_target);
begin
  if v_current = v_target then return; end if;
  if v_current in ('REVOKED', 'ARCHIVED') then
    raise exception 'M27_APP_TERMINAL';
  end if;
  if v_current = 'DRAFT' and v_target <> 'ACTIVE' then
    raise exception 'M27_INVALID_APP_TRANSITION';
  elsif v_current = 'ACTIVE' and v_target not in ('PAUSED','REVOKED','SUSPENDED','ARCHIVED') then
    raise exception 'M27_INVALID_APP_TRANSITION';
  elsif v_current = 'PAUSED' and v_target not in ('ACTIVE','REVOKED','ARCHIVED') then
    raise exception 'M27_INVALID_APP_TRANSITION';
  elsif v_current = 'SUSPENDED' and v_target not in ('ACTIVE','REVOKED','ARCHIVED') then
    raise exception 'M27_INVALID_APP_TRANSITION';
  elsif v_current not in ('DRAFT','ACTIVE','PAUSED','SUSPENDED') then
    raise exception 'M27_INVALID_APP_TRANSITION';
  end if;
end;
$$;

create or replace function public._m27_validate_ssrf_url(p_url text)
returns void language plpgsql as $$
declare
  v_url text := trim(coalesce(p_url, ''));
  v_host text;
begin
  if v_url = '' or position('@' in v_url) > 0 then
    raise exception 'M27_UNSAFE_WEBHOOK_URL';
  end if;
  if v_url !~* '^https://[[:alnum:].-]+(/[[:alnum:]./?#&=-]*)?$' then
    raise exception 'M27_UNSAFE_WEBHOOK_URL';
  end if;
  v_host := lower(substring(v_url from '^https://([^/:]+)'));
  if v_host is null then raise exception 'M27_UNSAFE_WEBHOOK_URL'; end if;
  if v_host ~* '^(localhost|127\.|0\.0\.0\.0|10\.|172\.(1[6-9]|2[0-9]|3[01])\.|192\.168\.|169\.254\.)'
     or v_host ~* '^\[::1\]' or v_host ~* '^\[fc' or v_host ~* '^\[fd' or v_host ~* '^\[fe80:' then
    raise exception 'M27_UNSAFE_WEBHOOK_URL';
  end if;
end;
$$;

create or replace function public._m27_scrub_payload(p_text text)
returns text language sql immutable as $$
  select trim(regexp_replace(coalesce(p_text, ''), '(?i)(secret|token|bearer|api[_-]?key|client[_-]?secret)\s*[:=]\s*\S+', '[redactado]', 'g'));
$$;

create or replace function public._m27_audit_append(
  p_actor uuid, p_app_id uuid, p_operation text, p_outcome text, p_environment text, p_reason text default null
) returns void language plpgsql security definer set search_path = public as $$
begin
  insert into public.m27_audit_log (actor_user_id, app_id, operation, outcome, environment, sanitized_reason)
  values (
    p_actor, p_app_id, p_operation, p_outcome, upper(p_environment),
    case when p_reason is null then null else public._m27_scrub_public_text(p_reason) end
  );
end;
$$;

create or replace function public._m27_idempotency_lookup(p_actor uuid, p_client_request_id text)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
  if p_client_request_id is null or trim(p_client_request_id) = '' then return null; end if;
  select resource_id into v_id
  from public.m27_idempotency_keys
  where actor_user_id = p_actor and client_request_id = trim(p_client_request_id);
  return v_id;
end;
$$;

create or replace function public._m27_idempotency_store(
  p_actor uuid, p_client_request_id text, p_operation text, p_resource_id uuid
) returns void language plpgsql security definer set search_path = public as $$
begin
  if p_client_request_id is null or trim(p_client_request_id) = '' then return; end if;
  insert into public.m27_idempotency_keys (actor_user_id, client_request_id, operation, resource_id)
  values (p_actor, trim(p_client_request_id), p_operation, p_resource_id)
  on conflict (actor_user_id, client_request_id) do nothing;
end;
$$;

create or replace function public._m27_public_integration_app_json(p_row public.m27_integration_apps)
returns jsonb language sql immutable as $$
  select jsonb_build_object(
    'name', public._m27_scrub_public_text(p_row.name),
    'contract_version', p_row.contract_version,
    'scopes', to_jsonb(p_row.granted_scopes),
    'status', p_row.status,
    'environment', p_row.environment
  );
$$;

create or replace function public._m27_integration_app_json(p_row public.m27_integration_apps)
returns jsonb language sql immutable as $$
  select public._m27_public_integration_app_json(p_row)
    || jsonb_build_object(
      'id', p_row.id,
      'owner_user_id', p_row.owner_user_id,
      'organization_id', p_row.organization_id,
      'created_at', p_row.created_at,
      'updated_at', p_row.updated_at
    );
$$;

create or replace function public._m27_api_credential_json(p_row public.m27_api_credentials, p_plaintext_once text default null)
returns jsonb language sql immutable as $$
  select public._m27_public_key_json(p_row)
    || jsonb_build_object(
      'id', p_row.id,
      'owner_user_id', p_row.owner_user_id,
      'app_id', p_row.app_id,
      'key_hash', p_row.key_hash,
      'created_at', p_row.created_at,
      'expires_at', p_row.expires_at,
      'plaintext_key_once', p_plaintext_once
    );
$$;

create or replace function public._m27_webhook_endpoint_json(p_row public.m27_webhook_endpoints, p_secret_once text default null)
returns jsonb language sql immutable as $$
  select public._m27_public_webhook_json(p_row)
    || jsonb_build_object(
      'id', p_row.id,
      'owner_user_id', p_row.owner_user_id,
      'app_id', p_row.app_id,
      'secret_hash', p_row.secret_hash,
      'created_at', p_row.created_at,
      'updated_at', p_row.updated_at,
      'secret_once', p_secret_once
    );
$$;

create or replace function public._m27_delivery_next_status(p_attempt int, p_success boolean)
returns text language sql immutable as $$
  select case
    when p_success then 'DELIVERED'
    when p_attempt >= 3 then 'DEAD_LETTER'
    else 'RETRY_SCHEDULED'
  end;
$$;

create or replace function public._m27_simulate_delivery(p_delivery_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_delivery public.m27_webhook_deliveries;
  v_endpoint public.m27_webhook_endpoints;
  v_event public.m27_webhook_events;
  v_attempt int;
  v_success boolean;
  v_outcome text;
  v_sig text;
  v_canonical text;
  v_ts bigint;
begin
  select * into v_delivery from public.m27_webhook_deliveries where id = p_delivery_id for update;
  if not found then return; end if;
  select * into v_endpoint from public.m27_webhook_endpoints where id = v_delivery.endpoint_id;
  select * into v_event from public.m27_webhook_events where id = v_delivery.event_id;
  v_attempt := v_delivery.attempt_count + 1;
  v_success := upper(v_endpoint.environment) <> 'SANDBOX' or v_attempt <= 2;
  v_outcome := case when v_success then 'HTTP_200' else 'HTTP_503' end;
  v_canonical := v_event.id::text || '.' || v_delivery.subscription_id::text || '.' || trim(v_event.sanitized_payload);
  v_ts := (extract(epoch from v_event.occurred_at) * 1000)::bigint;
  v_sig := encode(extensions.hmac(v_ts::text || '.' || v_canonical, v_endpoint.secret_hash, 'sha256'), 'hex');
  insert into public.m27_webhook_attempts (delivery_id, attempt_number, outcome, sanitized_error)
  values (
    v_delivery.id, v_attempt, v_outcome,
    case when v_success then null else 'Servicio no disponible (simulado)' end
  );
  update public.m27_webhook_deliveries
  set attempt_count = v_attempt,
      status = public._m27_delivery_next_status(v_attempt, v_success),
      signature_digest = left(v_sig, 12),
      last_attempt_at = timezone('utc', now())
  where id = v_delivery.id;
end;
$$;

create or replace function public._m27_create_deliveries_for_event(p_event_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_event public.m27_webhook_events;
  v_sub record;
  v_delivery_id uuid;
begin
  select * into v_event from public.m27_webhook_events where id = p_event_id;
  if not found then return; end if;
  for v_sub in
    select s.*
    from public.m27_webhook_subscriptions s
    join public.m27_webhook_endpoints e on e.id = s.endpoint_id
    where s.active
      and s.event_type = v_event.event_type
      and e.app_id = v_event.app_id
      and e.status = 'ACTIVE'
  loop
    begin
      insert into public.m27_webhook_deliveries (
        event_id, subscription_id, endpoint_id, status, max_attempts, signature_version
      ) values (
        v_event.id, v_sub.id, v_sub.endpoint_id, 'PENDING', 3, 'v1'
      ) returning id into v_delivery_id;
      perform public._m27_simulate_delivery(v_delivery_id);
    exception when unique_violation then
      null;
    end;
  end loop;
end;
$$;

create or replace function public._m27_rate_limit_for_env(p_env text)
returns int language sql immutable as $$
  select case upper(p_env)
    when 'SANDBOX' then 5
    when 'STAGING' then 30
    else 30
  end;
$$;

create or replace function public._m27_can_manual_retry(p_actor uuid, p_app_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.m27_integration_apps a
    where a.id = p_app_id and a.owner_user_id = p_actor
  )
  or exists (
    select 1
    from public.user_role_assignments ura
    join public.platform_roles pr on pr.id = ura.role_id
    where ura.user_id = p_actor
      and pr.code in ('ADMIN', 'MODERATOR')
  );
$$;

-- ---------------------------------------------------------------------------
-- 4. RPCs — apps de integración
-- ---------------------------------------------------------------------------

create or replace function public.m27_list_my_integration_apps()
returns setof jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated();
begin
  return query
    select public._m27_public_integration_app_json(a)
    from public.m27_integration_apps a
    where a.owner_user_id = v_actor
       or public._m27_can_manage_org(v_actor, a.organization_id)
    order by a.created_at desc;
end;
$$;

create or replace function public.m27_create_integration_app(
  p_name text,
  p_organization_id uuid,
  p_contract_version text default 'V1',
  p_scopes text[] default array['sandbox.execute']::text[],
  p_environment text default 'SANDBOX',
  p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
  v_existing uuid;
  v_row public.m27_integration_apps;
begin
  if v_env = 'PRODUCTION' then raise exception 'M27_PRODUCTION_DISABLED'; end if;
  if char_length(trim(coalesce(p_name, ''))) not between 3 and 80 then
    raise exception 'M27_INVALID_APP';
  end if;
  if p_organization_id is null then raise exception 'M27_INVALID_APP'; end if;
  if not public._m27_can_manage_org(v_actor, p_organization_id) then
    raise exception 'M27_PERMISSION_DENIED';
  end if;
  perform public._m27_validate_scope_list(p_scopes);
  v_existing := public._m27_idempotency_lookup(v_actor, p_client_request_id);
  if v_existing is not null then
    return public._m27_integration_app_json(public._m27_require_app(v_existing));
  end if;
  insert into public.m27_integration_apps (
    organization_id, owner_user_id, name, contract_version, granted_scopes, environment
  ) values (
    p_organization_id, v_actor, trim(p_name), upper(trim(coalesce(p_contract_version, 'V1'))),
    public._m27_filter_allowed_scopes(p_scopes), v_env
  ) returning * into v_row;
  perform public._m27_idempotency_store(v_actor, p_client_request_id, 'CREATE_APP', v_row.id);
  perform public._m27_audit_append(v_actor, v_row.id, 'CREATE_APP', 'OK', v_row.environment, null);
  return public._m27_integration_app_json(v_row);
end;
$$;

create or replace function public.m27_activate_integration_app(p_app_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_integration_apps;
begin
  v_row := public._m27_require_app(p_app_id);
  perform public._m27_require_app_manager(v_actor, v_row);
  perform public._m27_validate_app_transition(v_row.status, 'ACTIVE');
  update public.m27_integration_apps
  set status = 'ACTIVE', updated_at = timezone('utc', now())
  where id = p_app_id returning * into v_row;
  perform public._m27_audit_append(v_actor, v_row.id, 'APP_ACTIVE', 'OK', v_row.environment, null);
  return public._m27_integration_app_json(v_row);
end;
$$;

create or replace function public.m27_pause_integration_app(p_app_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_integration_apps;
begin
  v_row := public._m27_require_app(p_app_id);
  perform public._m27_require_app_manager(v_actor, v_row);
  perform public._m27_validate_app_transition(v_row.status, 'PAUSED');
  update public.m27_integration_apps
  set status = 'PAUSED', updated_at = timezone('utc', now())
  where id = p_app_id returning * into v_row;
  perform public._m27_audit_append(v_actor, v_row.id, 'APP_PAUSED', 'OK', v_row.environment, null);
  return public._m27_integration_app_json(v_row);
end;
$$;

create or replace function public.m27_revoke_integration_app(p_app_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_integration_apps;
begin
  v_row := public._m27_require_app(p_app_id);
  perform public._m27_require_app_manager(v_actor, v_row);
  perform public._m27_validate_app_transition(v_row.status, 'REVOKED');
  update public.m27_integration_apps
  set status = 'REVOKED', updated_at = timezone('utc', now())
  where id = p_app_id returning * into v_row;
  perform public._m27_audit_append(v_actor, v_row.id, 'APP_REVOKED', 'OK', v_row.environment, null);
  return public._m27_integration_app_json(v_row);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs — credenciales
-- ---------------------------------------------------------------------------

create or replace function public.m27_create_api_key_for_app(
  p_app_id uuid,
  p_label text,
  p_scopes text[],
  p_environment text default 'SANDBOX',
  p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_app public.m27_integration_apps;
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
  v_existing uuid;
  v_row public.m27_api_credentials;
  v_raw text;
begin
  v_app := public._m27_require_app(p_app_id);
  perform public._m27_require_app_manager(v_actor, v_app);
  if not public._m27_app_can_operate(v_app.status) then raise exception 'M27_APP_NOT_ACTIVE'; end if;
  perform public._m27_validate_requested_scopes(v_app.granted_scopes, p_scopes);
  if char_length(trim(coalesce(p_label, ''))) not between 3 and 80 then
    raise exception 'M27_INVALID_API_KEY';
  end if;
  if v_env not in ('SANDBOX','STAGING','PRODUCTION') then raise exception 'M27_INVALID_API_KEY'; end if;
  v_existing := public._m27_idempotency_lookup(v_actor, p_client_request_id);
  if v_existing is not null then
    select * into v_row from public.m27_api_credentials where id = v_existing;
    if found then
      return public._m27_api_credential_json(v_row, null);
    end if;
  end if;
  v_raw := 'lvk_' || replace(gen_random_uuid()::text, '-', '');
  insert into public.m27_api_credentials (
    owner_user_id, app_id, label, key_prefix, key_hash, scopes, environment
  ) values (
    v_actor, v_app.id, trim(p_label),
    public._m27_key_prefix(v_raw, v_env),
    public._m27_hash_secret(v_raw), p_scopes, v_env
  ) returning * into v_row;
  perform public._m27_idempotency_store(v_actor, p_client_request_id, 'CREATE_KEY', v_row.id);
  perform public._m27_audit_append(
    v_actor, v_app.id, 'CREATE_KEY', 'OK', v_row.environment, 'prefix=' || v_row.key_prefix
  );
  return public._m27_api_credential_json(v_row, v_raw);
end;
$$;

create or replace function public.m27_rotate_api_key(p_key_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_current public.m27_api_credentials;
  v_app public.m27_integration_apps;
begin
  select * into v_current from public.m27_api_credentials where id = p_key_id;
  if not found then raise exception 'M27_KEY_NOT_FOUND'; end if;
  if v_current.app_id is not null then
    v_app := public._m27_require_app(v_current.app_id);
    perform public._m27_require_app_manager(v_actor, v_app);
  elsif v_current.owner_user_id <> v_actor then
    raise exception 'M27_PERMISSION_DENIED';
  end if;
  update public.m27_api_credentials set status = 'REVOKED' where id = p_key_id;
  return public.m27_create_api_key_for_app(
    v_current.app_id, v_current.label, v_current.scopes, v_current.environment, null
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPCs — webhooks operativos
-- ---------------------------------------------------------------------------

create or replace function public.m27_register_webhook_endpoint(
  p_app_id uuid,
  p_label text,
  p_target_url text,
  p_environment text default 'SANDBOX',
  p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_app public.m27_integration_apps;
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
  v_existing uuid;
  v_row public.m27_webhook_endpoints;
  v_secret text;
begin
  v_app := public._m27_require_app(p_app_id);
  perform public._m27_require_app_manager(v_actor, v_app);
  if not public._m27_app_can_operate(v_app.status) then raise exception 'M27_APP_NOT_ACTIVE'; end if;
  perform public._m27_validate_ssrf_url(p_target_url);
  if char_length(trim(coalesce(p_label, ''))) not between 3 and 80 then
    raise exception 'M27_INVALID_WEBHOOK';
  end if;
  if v_env not in ('SANDBOX','STAGING','PRODUCTION') then raise exception 'M27_INVALID_WEBHOOK'; end if;
  v_existing := public._m27_idempotency_lookup(v_actor, p_client_request_id);
  if v_existing is not null then
    select * into v_row from public.m27_webhook_endpoints where id = v_existing;
    if found then return public._m27_webhook_endpoint_json(v_row, null); end if;
  end if;
  v_secret := 'whsec_' || replace(gen_random_uuid()::text, '-', '');
  insert into public.m27_webhook_endpoints (
    owner_user_id, app_id, label, target_url, secret_prefix, secret_hash, status, environment
  ) values (
    v_actor, v_app.id, trim(p_label), trim(p_target_url),
    left(v_secret, 10), public._m27_hash_secret(v_secret), 'PENDING_VERIFICATION', v_env
  ) returning * into v_row;
  perform public._m27_idempotency_store(v_actor, p_client_request_id, 'REGISTER_ENDPOINT', v_row.id);
  perform public._m27_audit_append(v_actor, v_app.id, 'REGISTER_ENDPOINT', 'OK', v_row.environment, null);
  return public._m27_webhook_endpoint_json(v_row, v_secret);
end;
$$;

create or replace function public.m27_verify_webhook_endpoint(p_endpoint_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_webhook_endpoints;
begin
  select * into v_row from public.m27_webhook_endpoints where id = p_endpoint_id;
  if not found then raise exception 'M27_WEBHOOK_NOT_FOUND'; end if;
  if v_row.owner_user_id <> v_actor then raise exception 'M27_PERMISSION_DENIED'; end if;
  update public.m27_webhook_endpoints
  set status = 'ACTIVE', updated_at = timezone('utc', now())
  where id = p_endpoint_id returning * into v_row;
  return public._m27_webhook_endpoint_json(v_row, null);
end;
$$;

create or replace function public.m27_subscribe_webhook(p_endpoint_id uuid, p_event_type text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_endpoint public.m27_webhook_endpoints;
  v_row public.m27_webhook_subscriptions;
  v_type text := upper(trim(coalesce(p_event_type, '')));
begin
  select * into v_endpoint from public.m27_webhook_endpoints where id = p_endpoint_id;
  if not found then raise exception 'M27_WEBHOOK_NOT_FOUND'; end if;
  if v_endpoint.owner_user_id <> v_actor then raise exception 'M27_PERMISSION_DENIED'; end if;
  if v_endpoint.status <> 'ACTIVE' then raise exception 'M27_ENDPOINT_NOT_ACTIVE'; end if;
  select * into v_row
  from public.m27_webhook_subscriptions
  where endpoint_id = p_endpoint_id and event_type = v_type and active
  limit 1;
  if found then
    return jsonb_build_object(
      'id', v_row.id, 'endpoint_id', v_row.endpoint_id, 'event_type', v_row.event_type,
      'active', v_row.active, 'created_at', v_row.created_at
    );
  end if;
  insert into public.m27_webhook_subscriptions (endpoint_id, event_type)
  values (p_endpoint_id, v_type)
  returning * into v_row;
  return jsonb_build_object(
    'id', v_row.id, 'endpoint_id', v_row.endpoint_id, 'event_type', v_row.event_type,
    'active', v_row.active, 'created_at', v_row.created_at
  );
end;
$$;

create or replace function public.m27_emit_webhook_event(
  p_app_id uuid,
  p_event_type text,
  p_resource_ref text,
  p_sanitized_payload text,
  p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_app public.m27_integration_apps;
  v_existing uuid;
  v_row public.m27_webhook_events;
  v_payload text := trim(coalesce(p_sanitized_payload, ''));
  v_type text := upper(trim(coalesce(p_event_type, '')));
begin
  v_app := public._m27_require_app(p_app_id);
  perform public._m27_require_app_manager(v_actor, v_app);
  if not public._m27_app_can_operate(v_app.status) then raise exception 'M27_APP_NOT_ACTIVE'; end if;
  if public._m27_scrub_payload(v_payload) <> v_payload then raise exception 'M27_INVALID_PAYLOAD'; end if;
  if char_length(v_payload) < 2 then raise exception 'M27_INVALID_PAYLOAD'; end if;
  v_existing := public._m27_idempotency_lookup(v_actor, p_client_request_id);
  if v_existing is not null then
    select * into v_row from public.m27_webhook_events where id = v_existing;
    if found then
      return jsonb_build_object(
        'id', v_row.id, 'app_id', v_row.app_id, 'event_type', v_row.event_type,
        'version', v_row.version, 'resource_ref', v_row.resource_ref,
        'sanitized_payload', v_row.sanitized_payload, 'occurred_at', v_row.occurred_at
      );
    end if;
  end if;
  insert into public.m27_webhook_events (app_id, event_type, resource_ref, sanitized_payload)
  values (v_app.id, v_type, trim(p_resource_ref), v_payload)
  returning * into v_row;
  perform public._m27_idempotency_store(v_actor, p_client_request_id, 'EMIT_EVENT', v_row.id);
  perform public._m27_create_deliveries_for_event(v_row.id);
  return jsonb_build_object(
    'id', v_row.id, 'app_id', v_row.app_id, 'event_type', v_row.event_type,
    'version', v_row.version, 'resource_ref', v_row.resource_ref,
    'sanitized_payload', v_row.sanitized_payload, 'occurred_at', v_row.occurred_at
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. RPCs — lectura operativa, rate limit, OAuth stub
-- ---------------------------------------------------------------------------

create or replace function public.m27_list_my_deliveries()
returns setof jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated();
begin
  return query
    select jsonb_build_object(
      'status', d.status,
      'attempt_count', d.attempt_count,
      'max_attempts', d.max_attempts,
      'signature_version', d.signature_version
    )
    from public.m27_webhook_deliveries d
    join public.m27_webhook_endpoints e on e.id = d.endpoint_id
    where e.owner_user_id = v_actor
    order by d.created_at desc;
end;
$$;

create or replace function public.m27_list_my_events()
returns setof jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated();
begin
  return query
    select jsonb_build_object(
      'event_type', ev.event_type,
      'version', ev.version,
      'resource_ref', ev.resource_ref,
      'sanitized_payload', ev.sanitized_payload,
      'occurred_at', ev.occurred_at
    )
    from public.m27_webhook_events ev
    join public.m27_integration_apps a on a.id = ev.app_id
    where a.owner_user_id = v_actor or public._m27_can_manage_org(v_actor, a.organization_id)
    order by ev.occurred_at desc;
end;
$$;

create or replace function public.m27_list_my_audit_log()
returns setof jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated();
begin
  return query
    select jsonb_build_object(
      'operation', l.operation,
      'outcome', l.outcome,
      'environment', l.environment,
      'sanitized_reason', l.sanitized_reason,
      'created_at', l.created_at
    )
    from public.m27_audit_log l
    where l.actor_user_id = v_actor
       or (l.app_id is not null and exists (
         select 1 from public.m27_integration_apps a
         where a.id = l.app_id and public._m27_can_manage_org(v_actor, a.organization_id)
       ))
    order by l.created_at desc;
end;
$$;

create or replace function public.m27_manual_retry_delivery(p_delivery_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_delivery public.m27_webhook_deliveries;
  v_event public.m27_webhook_events;
begin
  select * into v_delivery from public.m27_webhook_deliveries where id = p_delivery_id;
  if not found then raise exception 'M27_DELIVERY_NOT_FOUND'; end if;
  select * into v_event from public.m27_webhook_events where id = v_delivery.event_id;
  if not public._m27_can_manual_retry(v_actor, v_event.app_id) then
    raise exception 'M27_PERMISSION_DENIED';
  end if;
  if v_delivery.status in ('DELIVERED','DEAD_LETTER','CANCELLED') then
    raise exception 'M27_DELIVERY_TERMINAL';
  end if;
  perform public._m27_simulate_delivery(p_delivery_id);
  select * into v_delivery from public.m27_webhook_deliveries where id = p_delivery_id;
  return jsonb_build_object(
    'id', v_delivery.id,
    'event_id', v_delivery.event_id,
    'subscription_id', v_delivery.subscription_id,
    'endpoint_id', v_delivery.endpoint_id,
    'status', v_delivery.status,
    'attempt_count', v_delivery.attempt_count,
    'max_attempts', v_delivery.max_attempts,
    'signature_version', v_delivery.signature_version,
    'signature_digest', v_delivery.signature_digest,
    'last_attempt_at', v_delivery.last_attempt_at,
    'created_at', v_delivery.created_at
  );
end;
$$;

create or replace function public.m27_check_app_rate_limit(p_app_id uuid, p_environment text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_app public.m27_integration_apps;
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
  v_key text;
  v_bucket timestamptz := date_trunc('minute', timezone('utc', now()));
  v_count int;
  v_limit int;
begin
  v_app := public._m27_require_app(p_app_id);
  perform public._m27_require_app_manager(v_actor, v_app);
  v_key := v_app.id::text || ':' || v_env;
  v_limit := public._m27_rate_limit_for_env(v_env);
  insert into public.m27_rate_limit_counters (counter_key, window_bucket, request_count)
  values (v_key, v_bucket, 0)
  on conflict (counter_key, window_bucket) do nothing;
  select request_count into v_count
  from public.m27_rate_limit_counters
  where counter_key = v_key and window_bucket = v_bucket
  for update;
  if v_count >= v_limit then
    return jsonb_build_object('allowed', false, 'reason', 'M27_RATE_LIMIT', 'retry_after_seconds', 60);
  end if;
  update public.m27_rate_limit_counters
  set request_count = request_count + 1
  where counter_key = v_key and window_bucket = v_bucket;
  return jsonb_build_object('allowed', true, 'reason', null, 'retry_after_seconds', null);
end;
$$;

create or replace function public.m27_start_oauth_stub(
  p_redirect_uri text,
  p_scopes text[],
  p_state text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_state text := trim(coalesce(p_state, ''));
begin
  perform public._m27_require_authenticated();
  if v_state = '' then raise exception 'M27_OAUTH_STATE_REQUIRED'; end if;
  perform public._m27_validate_ssrf_url(p_redirect_uri);
  perform public._m27_validate_scope_list(p_scopes);
  return jsonb_build_object(
    'state', v_state,
    'redirect_uri', trim(p_redirect_uri),
    'scopes', to_jsonb(p_scopes),
    'stub_token_prefix', 'stub_tok_' || lpad((random() * 999)::int::text, 3, '0'),
    'expires_at', timezone('utc', now()) + interval '1 hour'
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Grants RPC (authenticated only)
-- ---------------------------------------------------------------------------

grant execute on function public.m27_list_my_integration_apps() to authenticated;
grant execute on function public.m27_create_integration_app(text, uuid, text, text[], text, text) to authenticated;
grant execute on function public.m27_activate_integration_app(uuid) to authenticated;
grant execute on function public.m27_pause_integration_app(uuid) to authenticated;
grant execute on function public.m27_revoke_integration_app(uuid) to authenticated;
grant execute on function public.m27_create_api_key_for_app(uuid, text, text[], text, text) to authenticated;
grant execute on function public.m27_rotate_api_key(uuid) to authenticated;
grant execute on function public.m27_register_webhook_endpoint(uuid, text, text, text, text) to authenticated;
grant execute on function public.m27_verify_webhook_endpoint(uuid) to authenticated;
grant execute on function public.m27_subscribe_webhook(uuid, text) to authenticated;
grant execute on function public.m27_emit_webhook_event(uuid, text, text, text, text) to authenticated;
grant execute on function public.m27_list_my_deliveries() to authenticated;
grant execute on function public.m27_list_my_events() to authenticated;
grant execute on function public.m27_list_my_audit_log() to authenticated;
grant execute on function public.m27_manual_retry_delivery(uuid) to authenticated;
grant execute on function public.m27_check_app_rate_limit(uuid, text) to authenticated;
grant execute on function public.m27_start_oauth_stub(text, text[], text) to authenticated;

commit;
