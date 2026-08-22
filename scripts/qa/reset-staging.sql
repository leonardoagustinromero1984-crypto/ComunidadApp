-- LeoVer STAGING QA RESET
-- Destructive transactional wipe. Not a migration. Never run in the SQL editor.
-- Invoke only via scripts/qa/reset-staging.ps1, which aborts unless the linked
-- project ref is exactly tobqbddfcyitwgbkthhy.
--
-- Preserves schema, RPCs, triggers, RLS, catalogs, geography, legal templates,
-- platform role catalog, country markets, storage buckets, and
-- public.vitacora_public_number_seq (no RESTART / setval / DROP).
-- TRUNCATE uses CONTINUE IDENTITY (never RESTART IDENTITY).

do $reset$
declare
  expected_ref constant text := 'tobqbddfcyitwgbkthhy';
  given_ref text;
  confirm_flag text;
  preserve_public constant text[] := array[
    'age_capability_rules',
    'breeds',
    'care_event_types',
    'country_markets',
    'legal_documents',
    'location_nodes',
    'moderation_reason_codes',
    'permission_codes',
    'pet_friendly_venue_subtypes',
    'pet_health_products',
    'platform_permissions',
    'platform_role_permissions',
    'platform_roles',
    'service_categories',
    'species'
  ];
  preserve_auth constant text[] := array[
    'custom_oauth_providers',
    'instances',
    'oauth_clients',
    'saml_providers',
    'schema_migrations',
    'sso_domains',
    'sso_providers'
  ];
  seq_before bigint;
  seq_called boolean;
  seq_after bigint;
  seq_called_after boolean;
  cascade_hit text;
  unknown_public text;
  unknown_auth text;
  public_sql text;
  auth_sql text;
  countries_n bigint;
  provinces_n bigint;
  localities_n bigint;
  species_n bigint;
  markets_n bigint;
  legal_n bigint;
  admin_n bigint;
begin -- STAGING_RESET_INJECT_GUC
  perform set_config('statement_timeout', '180s', true);
  perform set_config('lock_timeout', '30s', true);
  given_ref := current_setting('leover.staging_reset.project_ref', true);
  confirm_flag := current_setting('leover.staging_reset.confirm', true);

  if given_ref is distinct from expected_ref then
    raise exception using
      message = 'STAGING_RESET_ABORT_PROJECT_REF',
      detail = 'leover.staging_reset.project_ref must be tobqbddfcyitwgbkthhy. Nothing was deleted.';
  end if;

  if confirm_flag is distinct from 'LEOVER-STAGING-QA-RESET-CONFIRM' then
    raise exception using
      message = 'STAGING_RESET_ABORT_CONFIRM',
      detail = 'Missing explicit confirm GUC. Run scripts/qa/reset-staging.ps1 -Execute. Nothing was deleted.';
  end if;

  if current_database() ilike '%wystsapjfpdtoprlmizz%' then
    raise exception 'STAGING_RESET_ABORT_LEGACY_PROJECT';
  end if;

  select last_value, is_called
    into seq_before, seq_called
  from public.vitacora_public_number_seq;

  select string_agg(c.relname, ', ' order by c.relname)
    into cascade_hit
  from pg_constraint con
  join pg_class c on c.oid = con.conrelid
  join pg_namespace n on n.oid = c.relnamespace
  join pg_class cf on cf.oid = con.confrelid
  join pg_namespace nf on nf.oid = cf.relnamespace
  where con.contype = 'f'
    and n.nspname = 'public'
    and c.relname = any (preserve_public)
    and nf.nspname = 'public'
    and cf.relname <> all (preserve_public);

  if cascade_hit is not null then
    raise exception using
      message = 'STAGING_RESET_ABORT_CATALOG_FK',
      detail = 'A preserved catalog table references transactional data: ' || cascade_hit;
  end if;

  select string_agg(format('public.%I', c.relname), ', ' order by c.relname)
    into public_sql
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relkind = 'r'
    and c.relname <> all (preserve_public);

  if public_sql is null then
    raise exception 'STAGING_RESET_ABORT_NO_TRANSACTIONAL_TABLES';
  end if;

  select string_agg(c.relname, ', ' order by c.relname)
    into unknown_auth
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'auth'
    and c.relkind = 'r'
    and c.relname <> all (preserve_auth)
    and c.relname not in (
      'audit_log_entries',
      'flow_state',
      'identities',
      'mfa_amr_claims',
      'mfa_challenges',
      'mfa_factors',
      'oauth_authorizations',
      'oauth_client_states',
      'oauth_consents',
      'one_time_tokens',
      'refresh_tokens',
      'saml_relay_states',
      'sessions',
      'users',
      'webauthn_challenges',
      'webauthn_credentials'
    );

  if unknown_auth is not null then
    raise exception using
      message = 'STAGING_RESET_ABORT_UNKNOWN_AUTH_TABLE',
      detail = unknown_auth;
  end if;

  unknown_public := null;
  perform 1;

  drop table if exists pg_temp._qa_admin_users;
  drop table if exists pg_temp._qa_admin_identities;
  drop table if exists pg_temp._qa_admin_persons;
  drop table if exists pg_temp._qa_admin_roles;

  create temp table _qa_admin_users as
  select u.*
  from auth.users u
  where exists (
    select 1
    from public.user_platform_role_assignments a
    where a.user_id = u.id
      and a.role_code in ('ADMIN', 'SUPERADMIN')
      and a.revoked_at is null
  );

  create temp table _qa_admin_identities as
  select i.*
  from auth.identities i
  where i.user_id in (select id from _qa_admin_users);

  create temp table _qa_admin_persons as
  select p.*
  from public.persons p
  where p.user_id in (select id from _qa_admin_users);

  create temp table _qa_admin_roles as
  select a.*
  from public.user_platform_role_assignments a
  where a.user_id in (select id from _qa_admin_users)
    and a.role_code in ('ADMIN', 'SUPERADMIN')
    and a.revoked_at is null;

  execute 'truncate ' || public_sql || ' continue identity cascade';

  select string_agg(format('auth.%I', c.relname), ', ' order by c.relname)
    into auth_sql
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'auth'
    and c.relkind = 'r'
    and c.relname <> all (preserve_auth);

  if auth_sql is not null then
    execute 'truncate ' || auth_sql || ' continue identity cascade';
  end if;

  select count(*) into admin_n from _qa_admin_users;
  if admin_n > 0 then
    insert into auth.users select * from _qa_admin_users;
    insert into auth.identities select * from _qa_admin_identities;
    insert into public.persons select * from _qa_admin_persons;
    insert into public.user_platform_role_assignments select * from _qa_admin_roles;
  end if;

  select last_value, is_called
    into seq_after, seq_called_after
  from public.vitacora_public_number_seq;

  if seq_after is distinct from seq_before or seq_called_after is distinct from seq_called then
    raise exception using
      message = 'STAGING_RESET_ABORT_SEQUENCE_CHANGED',
      detail = format('before=%s called=%s after=%s called=%s', seq_before, seq_called, seq_after, seq_called_after);
  end if;

  select count(*) into countries_n from public.location_nodes where kind = 'COUNTRY';
  select count(*) into provinces_n from public.location_nodes where kind = 'PROVINCE';
  select count(*) into localities_n from public.location_nodes where kind = 'LOCALITY';
  select count(*) into species_n from public.species;
  select count(*) into markets_n from public.country_markets;
  select count(*) into legal_n from public.legal_documents;

  if countries_n < 1 or provinces_n < 20 or localities_n < 3000
     or species_n < 1 or markets_n < 1 or legal_n < 1 then
    raise exception using
      message = 'STAGING_RESET_ABORT_CATALOG_MISSING',
      detail = format(
        'countries=%s provinces=%s localities=%s species=%s markets=%s legal=%s',
        countries_n, provinces_n, localities_n, species_n, markets_n, legal_n
      );
  end if;

  raise notice 'STAGING_RESET_OK seq=%s public_truncated=%s', seq_after, public_sql;
end;
$reset$;
