-- =============================================================================
-- LeoVer M27 — migración 075: integraciones y API pública (Bloque 2).
-- Forward-only sobre 001–074. LOCAL ONLY: no aplicar a staging sin autorización.
-- Sin pagos M24. OAuth stub — sin proveedor externo.
-- =============================================================================

begin;

create table if not exists public.m27_webhook_endpoints (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references public.users(id) on delete restrict,
  label text not null,
  target_url text not null,
  secret_prefix text not null,
  status text not null default 'ACTIVE',
  environment text not null default 'PRODUCTION',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m27_webhook_status_chk check (status = any (array['ACTIVE','DISABLED','PENDING']::text[])),
  constraint m27_webhook_env_chk check (environment = any (array['PRODUCTION','SANDBOX']::text[])),
  constraint m27_webhook_label_chk check (char_length(trim(label)) between 3 and 80),
  constraint m27_webhook_url_chk check (target_url ~* '^https://[\\w.-]+(/[\\w./?#&=-]*)?$'),
  constraint m27_webhook_prefix_chk check (char_length(secret_prefix) between 4 and 32)
);

create table if not exists public.m27_oauth_applications (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references public.users(id) on delete restrict,
  name text not null,
  redirect_uri text not null,
  client_id_prefix text not null,
  scopes text[] not null,
  status text not null default 'ACTIVE',
  environment text not null default 'PRODUCTION',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m27_oauth_status_chk check (status = any (array['ACTIVE','REVOKED','PENDING']::text[])),
  constraint m27_oauth_env_chk check (environment = any (array['PRODUCTION','SANDBOX']::text[])),
  constraint m27_oauth_name_chk check (char_length(trim(name)) between 3 and 80),
  constraint m27_oauth_redirect_chk check (redirect_uri ~* '^https://[\\w.-]+(/[\\w./?#&=-]*)?$'),
  constraint m27_oauth_scopes_chk check (array_length(scopes, 1) between 1 and 20)
);

create table if not exists public.m27_api_credentials (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references public.users(id) on delete restrict,
  label text not null,
  key_prefix text not null,
  scopes text[] not null,
  status text not null default 'ACTIVE',
  environment text not null default 'PRODUCTION',
  created_at timestamptz not null default timezone('utc', now()),
  expires_at timestamptz,
  constraint m27_key_status_chk check (status = any (array['ACTIVE','REVOKED','EXPIRED']::text[])),
  constraint m27_key_env_chk check (environment = any (array['PRODUCTION','SANDBOX']::text[])),
  constraint m27_key_label_chk check (char_length(trim(label)) between 3 and 80),
  constraint m27_key_prefix_chk check (char_length(key_prefix) between 4 and 32),
  constraint m27_key_scopes_chk check (array_length(scopes, 1) between 1 and 20)
);

create table if not exists public.m27_rate_limit_quotas (
  environment text primary key,
  requests_per_minute integer not null,
  requests_per_day integer not null,
  burst_allowance integer not null,
  constraint m27_quota_env_chk check (environment = any (array['PRODUCTION','SANDBOX']::text[])),
  constraint m27_quota_rpm_chk check (requests_per_minute between 1 and 10000),
  constraint m27_quota_rpd_chk check (requests_per_day between 1 and 1000000),
  constraint m27_quota_burst_chk check (burst_allowance between 0 and 1000)
);

create table if not exists public.m27_api_contracts (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  version text not null,
  status text not null default 'DRAFT',
  summary text not null,
  published_at timestamptz,
  constraint m27_contract_status_chk check (status = any (array['DRAFT','PUBLISHED','DEPRECATED']::text[])),
  constraint m27_contract_version_chk check (version = any (array['V1','V2']::text[])),
  constraint m27_contract_title_chk check (char_length(trim(title)) between 3 and 160),
  constraint m27_contract_summary_chk check (char_length(trim(summary)) between 10 and 2000)
);

create index if not exists m27_webhook_owner_idx on public.m27_webhook_endpoints(owner_user_id, status);
create index if not exists m27_oauth_owner_idx on public.m27_oauth_applications(owner_user_id, status);
create index if not exists m27_key_owner_idx on public.m27_api_credentials(owner_user_id, status);
create index if not exists m27_contract_status_idx on public.m27_api_contracts(status, version);

insert into public.m27_rate_limit_quotas (environment, requests_per_minute, requests_per_day, burst_allowance)
values
  ('PRODUCTION', 120, 50000, 20),
  ('SANDBOX', 30, 5000, 5)
on conflict (environment) do nothing;

insert into public.m27_api_contracts (title, version, status, summary, published_at)
select 'LeoVer Public API v1', 'V1', 'PUBLISHED',
  'Contrato estable para adopciones, eventos y webhooks municipales.',
  timezone('utc', now())
where not exists (
  select 1 from public.m27_api_contracts where version = 'V1' and status = 'PUBLISHED'
);

-- ---------------------------------------------------------------------------
-- RLS deny-all
-- ---------------------------------------------------------------------------

alter table public.m27_webhook_endpoints enable row level security;
alter table public.m27_oauth_applications enable row level security;
alter table public.m27_api_credentials enable row level security;
alter table public.m27_rate_limit_quotas enable row level security;
alter table public.m27_api_contracts enable row level security;

create policy m27_webhooks_deny on public.m27_webhook_endpoints for all to authenticated using (false) with check (false);
create policy m27_oauth_deny on public.m27_oauth_applications for all to authenticated using (false) with check (false);
create policy m27_keys_deny on public.m27_api_credentials for all to authenticated using (false) with check (false);
create policy m27_quotas_deny on public.m27_rate_limit_quotas for all to authenticated using (false) with check (false);
create policy m27_contracts_deny on public.m27_api_contracts for all to authenticated using (false) with check (false);

revoke all on table public.m27_webhook_endpoints, public.m27_oauth_applications,
  public.m27_api_credentials, public.m27_rate_limit_quotas, public.m27_api_contracts
  from public, anon, authenticated;

grant all on table public.m27_webhook_endpoints, public.m27_oauth_applications,
  public.m27_api_credentials, public.m27_rate_limit_quotas, public.m27_api_contracts
  to service_role;

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

create or replace function public._m27_require_authenticated()
returns uuid language plpgsql security definer set search_path = public as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v_uid;
end;
$$;

create or replace function public._m27_scrub_public_text(p_text text)
returns text language sql immutable as $$
  select trim(regexp_replace(coalesce(p_text, ''), '(?i)(secret|token|bearer|api[_-]?key|client[_-]?secret)\s*[:=]\s*\S+', '[redactado]', 'g'));
$$;

create or replace function public._m27_public_webhook_json(p_row public.m27_webhook_endpoints)
returns jsonb language sql immutable as $$
  select jsonb_build_object(
    'label', public._m27_scrub_public_text(p_row.label),
    'target_url', public._m27_scrub_public_text(p_row.target_url),
    'secret_prefix', p_row.secret_prefix,
    'status', p_row.status,
    'environment', p_row.environment
  );
$$;

create or replace function public._m27_public_oauth_json(p_row public.m27_oauth_applications)
returns jsonb language sql immutable as $$
  select jsonb_build_object(
    'name', public._m27_scrub_public_text(p_row.name),
    'redirect_uri', public._m27_scrub_public_text(p_row.redirect_uri),
    'client_id_prefix', p_row.client_id_prefix,
    'scopes', to_jsonb(p_row.scopes),
    'status', p_row.status,
    'environment', p_row.environment
  );
$$;

create or replace function public._m27_public_key_json(p_row public.m27_api_credentials)
returns jsonb language sql immutable as $$
  select jsonb_build_object(
    'label', public._m27_scrub_public_text(p_row.label),
    'key_prefix', p_row.key_prefix,
    'scopes', to_jsonb(p_row.scopes),
    'status', p_row.status,
    'environment', p_row.environment
  );
$$;

create or replace function public._m27_public_contract_json(p_row public.m27_api_contracts)
returns jsonb language sql immutable as $$
  select jsonb_build_object(
    'title', public._m27_scrub_public_text(p_row.title),
    'version', p_row.version,
    'summary', public._m27_scrub_public_text(p_row.summary),
    'published_for_display', (p_row.status = 'PUBLISHED')
  );
$$;

-- ---------------------------------------------------------------------------
-- RPCs — lectura
-- ---------------------------------------------------------------------------

create or replace function public.m27_list_my_webhooks()
returns setof jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated();
begin
  return query
    select public._m27_public_webhook_json(w)
    from public.m27_webhook_endpoints w
    where w.owner_user_id = v_actor
    order by w.created_at desc;
end;
$$;

create or replace function public.m27_list_my_oauth_apps()
returns setof jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated();
begin
  return query
    select public._m27_public_oauth_json(o)
    from public.m27_oauth_applications o
    where o.owner_user_id = v_actor
    order by o.created_at desc;
end;
$$;

create or replace function public.m27_list_my_api_keys()
returns setof jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated();
begin
  return query
    select public._m27_public_key_json(k)
    from public.m27_api_credentials k
    where k.owner_user_id = v_actor
    order by k.created_at desc;
end;
$$;

create or replace function public.m27_list_published_contracts()
returns setof jsonb language plpgsql security definer set search_path = public as $$
begin
  perform public._m27_require_authenticated();
  return query
    select public._m27_public_contract_json(c)
    from public.m27_api_contracts c
    where c.status = 'PUBLISHED'
    order by c.published_at desc nulls last;
end;
$$;

create or replace function public.m27_list_rate_limits()
returns setof jsonb language plpgsql security definer set search_path = public as $$
begin
  perform public._m27_require_authenticated();
  return query
    select jsonb_build_object(
      'environment', q.environment,
      'requests_per_minute', q.requests_per_minute,
      'requests_per_day', q.requests_per_day
    )
    from public.m27_rate_limit_quotas q
    order by q.environment;
end;
$$;

-- ---------------------------------------------------------------------------
-- RPCs — escritura
-- ---------------------------------------------------------------------------

create or replace function public.m27_register_webhook(
  p_label text, p_target_url text, p_environment text default 'SANDBOX'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_webhook_endpoints;
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
begin
  if char_length(trim(coalesce(p_label, ''))) not between 3 and 80
    or trim(coalesce(p_target_url, '')) !~* '^https://[\\w.-]+(/[\\w./?#&=-]*)?$'
    or v_env not in ('PRODUCTION','SANDBOX') then
    raise exception 'M27_INVALID_WEBHOOK';
  end if;
  insert into public.m27_webhook_endpoints (owner_user_id, label, target_url, secret_prefix, environment)
  values (v_actor, trim(p_label), trim(p_target_url), 'whsec_' || lpad((random() * 9999)::int::text, 4, '0'), v_env)
  returning * into v_row;
  return public._m27_public_webhook_json(v_row)
    || jsonb_build_object('id', v_row.id, 'owner_user_id', v_row.owner_user_id,
         'created_at', v_row.created_at, 'updated_at', v_row.updated_at);
end;
$$;

create or replace function public.m27_disable_webhook(p_webhook_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated(); v_row public.m27_webhook_endpoints;
begin
  select * into v_row from public.m27_webhook_endpoints where id = p_webhook_id;
  if not found then raise exception 'M27_WEBHOOK_NOT_FOUND'; end if;
  if v_row.owner_user_id <> v_actor then raise exception 'M27_PERMISSION_DENIED'; end if;
  update public.m27_webhook_endpoints set status = 'DISABLED', updated_at = timezone('utc', now())
  where id = p_webhook_id returning * into v_row;
  return public._m27_public_webhook_json(v_row)
    || jsonb_build_object('id', v_row.id);
end;
$$;

create or replace function public.m27_register_oauth_app(
  p_name text, p_redirect_uri text, p_scopes text[], p_environment text default 'SANDBOX'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_oauth_applications;
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
begin
  if char_length(trim(coalesce(p_name, ''))) not between 3 and 80
    or trim(coalesce(p_redirect_uri, '')) !~* '^https://[\\w.-]+(/[\\w./?#&=-]*)?$'
    or p_scopes is null or array_length(p_scopes, 1) is null
    or v_env not in ('PRODUCTION','SANDBOX') then
    raise exception 'M27_INVALID_OAUTH';
  end if;
  insert into public.m27_oauth_applications (owner_user_id, name, redirect_uri, client_id_prefix, scopes, environment)
  values (v_actor, trim(p_name), trim(p_redirect_uri), 'lv_cli_' || lpad((random() * 999)::int::text, 3, '0'), p_scopes, v_env)
  returning * into v_row;
  return public._m27_public_oauth_json(v_row)
    || jsonb_build_object('id', v_row.id, 'owner_user_id', v_row.owner_user_id,
         'created_at', v_row.created_at, 'updated_at', v_row.updated_at);
end;
$$;

create or replace function public.m27_revoke_oauth_app(p_app_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated(); v_row public.m27_oauth_applications;
begin
  select * into v_row from public.m27_oauth_applications where id = p_app_id;
  if not found then raise exception 'M27_OAUTH_NOT_FOUND'; end if;
  if v_row.owner_user_id <> v_actor then raise exception 'M27_PERMISSION_DENIED'; end if;
  update public.m27_oauth_applications set status = 'REVOKED', updated_at = timezone('utc', now())
  where id = p_app_id returning * into v_row;
  return public._m27_public_oauth_json(v_row) || jsonb_build_object('id', v_row.id);
end;
$$;

create or replace function public.m27_issue_api_key(
  p_label text, p_scopes text[], p_environment text default 'SANDBOX'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_api_credentials;
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
  v_prefix text;
begin
  if char_length(trim(coalesce(p_label, ''))) not between 3 and 80
    or p_scopes is null or array_length(p_scopes, 1) is null
    or v_env not in ('PRODUCTION','SANDBOX') then
    raise exception 'M27_INVALID_API_KEY';
  end if;
  v_prefix := case when v_env = 'SANDBOX' then 'lvk_sbx_' else 'lvk_prod_' end
    || lpad((random() * 999)::int::text, 3, '0');
  insert into public.m27_api_credentials (owner_user_id, label, key_prefix, scopes, environment)
  values (v_actor, trim(p_label), v_prefix, p_scopes, v_env)
  returning * into v_row;
  return public._m27_public_key_json(v_row)
    || jsonb_build_object('id', v_row.id, 'owner_user_id', v_row.owner_user_id, 'created_at', v_row.created_at);
end;
$$;

create or replace function public.m27_revoke_api_key(p_key_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m27_require_authenticated(); v_row public.m27_api_credentials;
begin
  select * into v_row from public.m27_api_credentials where id = p_key_id;
  if not found then raise exception 'M27_KEY_NOT_FOUND'; end if;
  if v_row.owner_user_id <> v_actor then raise exception 'M27_PERMISSION_DENIED'; end if;
  update public.m27_api_credentials set status = 'REVOKED'
  where id = p_key_id returning * into v_row;
  return public._m27_public_key_json(v_row) || jsonb_build_object('id', v_row.id);
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants RPC
-- ---------------------------------------------------------------------------

grant execute on function public.m27_list_my_webhooks() to authenticated;
grant execute on function public.m27_list_my_oauth_apps() to authenticated;
grant execute on function public.m27_list_my_api_keys() to authenticated;
grant execute on function public.m27_list_published_contracts() to authenticated;
grant execute on function public.m27_list_rate_limits() to authenticated;
grant execute on function public.m27_register_webhook(text, text, text) to authenticated;
grant execute on function public.m27_disable_webhook(uuid) to authenticated;
grant execute on function public.m27_register_oauth_app(text, text, text[], text) to authenticated;
grant execute on function public.m27_revoke_oauth_app(uuid) to authenticated;
grant execute on function public.m27_issue_api_key(text, text[], text) to authenticated;
grant execute on function public.m27_revoke_api_key(uuid) to authenticated;

commit;
