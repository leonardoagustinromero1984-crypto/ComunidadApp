-- LeoVer M27 — migración 077: idempotencia con recurso ausente (correctiva forward-only).
-- Limpia claves idempotentes huérfanas tras borrado de apps/credenciales/endpoints/eventos.

begin;

create extension if not exists pgcrypto with schema extensions;

create or replace function public._m27_hash_secret(p_raw text)
returns text language sql immutable set search_path = public, extensions as $$
  select encode(extensions.digest(p_raw, 'sha256'), 'hex');
$$;

create or replace function public._m27_validate_ssrf_url(p_url text)
returns void language plpgsql set search_path = public as $$
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

create or replace function public.m27_register_webhook(
  p_label text, p_target_url text, p_environment text default 'SANDBOX'
) returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_webhook_endpoints;
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
  v_secret text;
begin
  if char_length(trim(coalesce(p_label, ''))) not between 3 and 80
    or v_env not in ('PRODUCTION','SANDBOX','STAGING') then
    raise exception 'M27_INVALID_WEBHOOK';
  end if;
  perform public._m27_validate_ssrf_url(trim(p_target_url));
  v_secret := 'whsec_' || replace(gen_random_uuid()::text, '-', '');
  insert into public.m27_webhook_endpoints (
    owner_user_id, label, target_url, secret_prefix, secret_hash, environment
  ) values (
    v_actor, trim(p_label), trim(p_target_url), left(v_secret, 10),
    public._m27_hash_secret(v_secret), v_env
  ) returning * into v_row;
  return public._m27_public_webhook_json(v_row)
    || jsonb_build_object('id', v_row.id, 'owner_user_id', v_row.owner_user_id,
         'created_at', v_row.created_at, 'updated_at', v_row.updated_at);
end;
$$;

create or replace function public.m27_issue_api_key(
  p_label text, p_scopes text[], p_environment text default 'SANDBOX'
) returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_row public.m27_api_credentials;
  v_env text := upper(trim(coalesce(p_environment, 'SANDBOX')));
  v_raw text;
  v_prefix text;
begin
  if char_length(trim(coalesce(p_label, ''))) not between 3 and 80
    or p_scopes is null or array_length(p_scopes, 1) is null
    or v_env not in ('PRODUCTION','SANDBOX','STAGING') then
    raise exception 'M27_INVALID_API_KEY';
  end if;
  v_raw := 'lvk_' || replace(gen_random_uuid()::text, '-', '');
  v_prefix := public._m27_key_prefix(v_raw, v_env);
  insert into public.m27_api_credentials (owner_user_id, label, key_prefix, key_hash, scopes, environment)
  values (v_actor, trim(p_label), v_prefix, public._m27_hash_secret(v_raw), p_scopes, v_env)
  returning * into v_row;
  return public._m27_public_key_json(v_row)
    || jsonb_build_object('id', v_row.id, 'owner_user_id', v_row.owner_user_id, 'created_at', v_row.created_at);
end;
$$;

create or replace function public._m27_simulate_delivery(p_delivery_id uuid)
returns void language plpgsql security definer set search_path = public, extensions as $$
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

alter table public.m27_webhook_endpoints drop constraint if exists m27_webhook_url_chk;
alter table public.m27_webhook_endpoints add constraint m27_webhook_url_chk check (
  target_url ~* '^https://[[:alnum:].-]+(/[[:alnum:]./?#&=-]*)?$'
);

create or replace function public._m27_clear_idempotency(
  p_actor uuid,
  p_client_request_id text
) returns void language plpgsql security definer set search_path = public as $$
begin
  if p_client_request_id is null or trim(p_client_request_id) = '' then return; end if;
  delete from public.m27_idempotency_keys
  where actor_user_id = p_actor and client_request_id = trim(p_client_request_id);
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
    begin
      return public._m27_integration_app_json(public._m27_require_app(v_existing));
    exception when others then
      perform public._m27_clear_idempotency(v_actor, p_client_request_id);
    end;
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

create or replace function public.m27_rotate_api_key(p_key_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m27_require_authenticated();
  v_current public.m27_api_credentials;
  v_app public.m27_integration_apps;
begin
  select * into v_current from public.m27_api_credentials where id = p_key_id;
  if not found then raise exception 'M27_KEY_NOT_FOUND'; end if;
  if v_current.app_id is null then
    raise exception 'M27_KEY_NOT_FOUND';
  end if;
  v_app := public._m27_require_app(v_current.app_id);
  perform public._m27_require_app_manager(v_actor, v_app);
  update public.m27_api_credentials set status = 'REVOKED' where id = p_key_id;
  return public.m27_create_api_key_for_app(
    v_current.app_id, v_current.label, v_current.scopes, v_current.environment, null
  );
end;
$$;

revoke all on function public._m27_clear_idempotency(uuid, text) from public, anon, authenticated;
grant execute on function public._m27_clear_idempotency(uuid, text) to service_role;

commit;
