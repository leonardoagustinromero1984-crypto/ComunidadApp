-- M22 migración 066 — validación remota staging (casos 01–75)
-- Ejecutar: supabase db query --linked -f scripts/ops/m22_remote_validation_066.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m22_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m22_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m22_val_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m22_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_owner uuid := 'f0000000-0000-4000-8000-000000000001';
  v_peer uuid := 'f0000000-0000-4000-8000-000000000002';
  v_out uuid := 'f0000000-0000-4000-8000-000000000003';
  v_provider_id uuid;
  v_branch_id uuid;
  v_offering_id uuid;
  v_struct_provider uuid;
  v_json jsonb;
  v_cnt int;
  v_err text;
  v_i int;
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_owner, 'authenticated', 'authenticated',
     'm22-val-owner@test.local', crypt('m22-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_peer, 'authenticated', 'authenticated',
     'm22-val-peer@test.local', crypt('m22-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm22-val-out@test.local', crypt('m22-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_owner, 'm22-val-owner@test.local', 'M22 Val Owner', 'M22 Val Owner', 'PERSON', true, 'ACTIVE'),
    (v_peer, 'm22-val-peer@test.local', 'M22 Val Peer', 'M22 Val Peer', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm22-val-out@test.local', 'M22 Val Outsider', 'M22 Val Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m22_service_offerings
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out));
  delete from public.m22_provider_branches
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out));
  delete from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ========================================================================
  -- ESTRUCTURA 01–35
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name in (
    'm22_service_providers', 'm22_provider_branches', 'm22_service_offerings'
  );
  perform pg_temp.m22_val(1, 'Tres tablas M22 (066)', v_cnt = 3);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm22_service_providers'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'owner_user_id';
  perform pg_temp.m22_val(2, 'FK owner_user_id prestadores', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm22_service_providers'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'organization_id';
  perform pg_temp.m22_val(3, 'FK organization_id prestadores', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm22_provider_branches'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'provider_id';
  perform pg_temp.m22_val(4, 'FK provider_id sedes', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm22_service_offerings'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'provider_id';
  perform pg_temp.m22_val(5, 'FK provider_id ofertas', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm22_service_offerings'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'branch_id';
  perform pg_temp.m22_val(6, 'FK branch_id ofertas', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm22_service_providers'
    and indexname = 'm22_provider_category_status_idx';
  perform pg_temp.m22_val(7, 'Índice category+status prestadores', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm22_service_providers'
    and indexname = 'm22_provider_organization_idx';
  perform pg_temp.m22_val(8, 'Índice organization prestadores', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm22_provider_branches'
    and indexname = 'm22_branch_provider_idx';
  perform pg_temp.m22_val(9, 'Índice provider sedes', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm22_provider_branches'
    and indexname = 'm22_branch_city_idx';
  perform pg_temp.m22_val(10, 'Índice city sedes', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm22_service_offerings'
    and indexname = 'm22_offering_provider_idx';
  perform pg_temp.m22_val(11, 'Índice provider ofertas', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_providers (
      owner_user_id, display_name, category, description, city, status
    ) values (
      v_owner, 'Struct M22', 'INVALID', 'Descripción válida M22 val estructura.', 'CABA', 'DRAFT'
    );
    perform pg_temp.m22_val(12, 'CHECK category enum prestador', false);
  exception when check_violation then
    perform pg_temp.m22_val(12, 'CHECK category enum prestador', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_providers (
      owner_user_id, display_name, category, description, city, status
    ) values (
      v_owner, 'Struct M22', 'VET', 'Descripción válida M22 val estructura.', 'CABA', 'INVALID'
    );
    perform pg_temp.m22_val(13, 'CHECK status enum prestador', false);
  exception when check_violation then
    perform pg_temp.m22_val(13, 'CHECK status enum prestador', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_providers (
      owner_user_id, display_name, category, description, city, status
    ) values (
      v_owner, 'X', 'VET', 'Descripción válida M22 val estructura.', 'CABA', 'DRAFT'
    );
    perform pg_temp.m22_val(14, 'CHECK display_name longitud prestador', false);
  exception when check_violation then
    perform pg_temp.m22_val(14, 'CHECK display_name longitud prestador', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_providers (
      owner_user_id, display_name, category, description, city, status
    ) values (
      v_owner, 'Struct M22', 'VET', 'Corto', 'CABA', 'DRAFT'
    );
    perform pg_temp.m22_val(15, 'CHECK description longitud prestador', false);
  exception when check_violation then
    perform pg_temp.m22_val(15, 'CHECK description longitud prestador', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_providers (
      owner_user_id, display_name, category, description, city, status
    ) values (
      v_owner, 'Struct M22', 'VET', 'Descripción válida M22 val estructura.', 'X', 'DRAFT'
    );
    perform pg_temp.m22_val(16, 'CHECK city longitud prestador', false);
  exception when check_violation then
    perform pg_temp.m22_val(16, 'CHECK city longitud prestador', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  perform set_config('request.jwt.claim.role', 'service_role', true);
  insert into public.m22_service_providers (
    owner_user_id, display_name, category, description, city, status
  ) values (
    v_owner, 'Struct Check M22', 'GROOMING',
    'Prestador semilla para checks estructurales M22 val remoto.', 'CABA', 'DRAFT'
  ) returning id into v_struct_provider;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_provider_branches (
      provider_id, name, city, coverage_type, coverage_city, status
    ) values (
      v_struct_provider, 'Sede M22', 'CABA', 'CITY', 'CABA', 'INVALID'
    );
    perform pg_temp.m22_val(17, 'CHECK status enum sede', false);
  exception when check_violation then
    perform pg_temp.m22_val(17, 'CHECK status enum sede', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_provider_branches (
      provider_id, name, city, coverage_type, coverage_city, status
    ) values (
      v_struct_provider, 'Sede M22', 'CABA', 'INVALID', 'CABA', 'ACTIVE'
    );
    perform pg_temp.m22_val(18, 'CHECK coverage_type enum sede', false);
  exception when check_violation then
    perform pg_temp.m22_val(18, 'CHECK coverage_type enum sede', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_provider_branches (
      provider_id, name, city, coverage_type, coverage_city, coverage_neighborhood, status
    ) values (
      v_struct_provider, 'Sede M22', 'CABA', 'CITY', 'CABA', 'Palermo', 'ACTIVE'
    );
    perform pg_temp.m22_val(19, 'CHECK coverage CITY sin barrio', false);
  exception when check_violation then
    perform pg_temp.m22_val(19, 'CHECK coverage CITY sin barrio', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_provider_branches (
      provider_id, name, city, coverage_type, coverage_city, status
    ) values (
      v_struct_provider, 'Sede M22', 'CABA', 'NEIGHBORHOOD', 'CABA', 'ACTIVE'
    );
    perform pg_temp.m22_val(20, 'CHECK coverage NEIGHBORHOOD requiere barrio', false);
  exception when check_violation then
    perform pg_temp.m22_val(20, 'CHECK coverage NEIGHBORHOOD requiere barrio', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_provider_branches (
      provider_id, name, city, coverage_type, coverage_city, status
    ) values (
      v_struct_provider, 'Sede M22', 'CABA', 'RADIUS', 'CABA', 'ACTIVE'
    );
    perform pg_temp.m22_val(21, 'CHECK coverage RADIUS requiere km', false);
  exception when check_violation then
    perform pg_temp.m22_val(21, 'CHECK coverage RADIUS requiere km', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_offerings (
      provider_id, name, description, price_type, price_amount_cents, currency
    ) values (
      v_struct_provider, 'Oferta M22', 'Descripción oferta válida M22 val.', 'INVALID', 1000, 'ARS'
    );
    perform pg_temp.m22_val(22, 'CHECK price_type enum oferta', false);
  exception when check_violation then
    perform pg_temp.m22_val(22, 'CHECK price_type enum oferta', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_offerings (
      provider_id, name, description, price_type, price_amount_cents, currency
    ) values (
      v_struct_provider, 'Oferta M22', 'Descripción oferta válida M22 val.', 'FIXED', null, 'ARS'
    );
    perform pg_temp.m22_val(23, 'CHECK FIXED requiere price_amount_cents', false);
  exception when check_violation then
    perform pg_temp.m22_val(23, 'CHECK FIXED requiere price_amount_cents', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_offerings (
      provider_id, name, description, price_type, price_amount_cents, currency
    ) values (
      v_struct_provider, 'Oferta M22', 'Descripción oferta válida M22 val.', 'QUOTE', 1000, 'ARS'
    );
    perform pg_temp.m22_val(24, 'CHECK QUOTE price_amount_cents nulo', false);
  exception when check_violation then
    perform pg_temp.m22_val(24, 'CHECK QUOTE price_amount_cents nulo', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m22_service_offerings (
      provider_id, name, description, price_type, price_amount_cents, currency
    ) values (
      v_struct_provider, 'Oferta M22', 'Descripción oferta válida M22 val.', 'FIXED', 1000, 'ars'
    );
    perform pg_temp.m22_val(25, 'CHECK currency ISO-4217', false);
  exception when check_violation then
    perform pg_temp.m22_val(25, 'CHECK currency ISO-4217', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm22_service_providers' and column_name = 'status';
  perform pg_temp.m22_val(26, 'Columna status prestadores', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm22_service_offerings' and column_name = 'price_amount_cents';
  perform pg_temp.m22_val(27, 'Columna price_amount_cents ofertas', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm22_service_providers' and c.relrowsecurity;
  perform pg_temp.m22_val(28, 'RLS m22_service_providers', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm22_provider_branches' and c.relrowsecurity;
  perform pg_temp.m22_val(29, 'RLS m22_provider_branches', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm22_service_offerings' and c.relrowsecurity;
  perform pg_temp.m22_val(30, 'RLS m22_service_offerings', v_cnt = 1);

  select count(*)::int into v_cnt from pg_policies
  where schemaname = 'public' and tablename = 'm22_service_providers'
    and policyname = 'm22_provider_authenticated_deny';
  perform pg_temp.m22_val(31, 'Policy deny authenticated prestadores', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname in (
      'm22_list_catalog', 'm22_get_provider_detail', 'm22_list_my_providers',
      'm22_create_provider', 'm22_update_provider', 'm22_upsert_branch',
      'm22_upsert_offering', 'm22_archive_provider'
    );
  perform pg_temp.m22_val(32, 'RPCs M22 clave existen', v_cnt = 8);

  select count(*)::int into v_cnt from public.organization_permissions
  where code in ('provider.profile.read', 'provider.profile.manage', 'provider.catalog.manage');
  perform pg_temp.m22_val(33, 'Permisos M03 prestadores (066)', v_cnt = 3);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = '_m22_public_listing_json';
  perform pg_temp.m22_val(34, 'Helper _m22_public_listing_json', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = '_m22_validate_provider';
  perform pg_temp.m22_val(35, 'Helper _m22_validate_provider', v_cnt = 1);

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m22_service_offerings where provider_id = v_struct_provider;
  delete from public.m22_provider_branches where provider_id = v_struct_provider;
  delete from public.m22_service_providers where id = v_struct_provider;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ========================================================================
  -- RLS / PERMISOS 36–50
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm22_service_providers' and grantee = 'anon';
  perform pg_temp.m22_val(36, 'Anon sin grant prestadores', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm22_provider_branches' and grantee = 'anon';
  perform pg_temp.m22_val(37, 'Anon sin grant sedes', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm22_service_offerings' and grantee = 'anon';
  perform pg_temp.m22_val(38, 'Anon sin grant ofertas', v_cnt = 0);

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m22_service_providers;
    reset role;
    perform pg_temp.m22_val(39, 'Anon sin filas prestadores (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m22_val(39, 'Anon sin filas prestadores (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m22_val(39, 'Anon sin filas prestadores (RLS)', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    select count(*)::int into v_cnt from public.m22_list_catalog();
    perform pg_temp.m22_val(40, 'Anon list_catalog callable', v_cnt >= 0);
  exception when others then
    perform pg_temp.m22_val(40, 'Anon list_catalog callable', false, SQLERRM);
  end;

  begin
    perform public.m22_create_provider(
      'Hack Anon M22', 'VET', 'Descripción hack anon M22 val remoto.', 'CABA'
    );
    perform pg_temp.m22_val(41, 'Anon create_provider denegado', false);
  exception when others then
    perform pg_temp.m22_val(41, 'Anon create_provider denegado', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  perform pg_temp.m22_act_as(v_owner);
  begin
    v_json := public.m22_create_provider(
      'Peluquería Val M22', 'GROOMING',
      'Prestador de prueba validación remota M22 LeoVer.', 'CABA'
    );
    v_provider_id := (v_json->>'id')::uuid;
    perform pg_temp.m22_val(42, 'Authenticated create_provider', v_provider_id is not null);
  exception when others then
    perform pg_temp.m22_val(42, 'Authenticated create_provider', false, SQLERRM);
  end;

  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_out);
    begin
      perform public.m22_update_provider(v_provider_id, p_status := 'ACTIVE');
      perform pg_temp.m22_val(43, 'Ajeno update_provider denegado', false);
    exception when others then
      perform pg_temp.m22_val(43, 'Ajeno update_provider denegado', SQLERRM like '%M22_PERMISSION_DENIED%');
    end;

    begin
      perform public.m22_archive_provider(v_provider_id);
      perform pg_temp.m22_val(44, 'Ajeno archive_provider denegado', false);
    exception when others then
      perform pg_temp.m22_val(44, 'Ajeno archive_provider denegado', SQLERRM like '%M22_PERMISSION_DENIED%');
    end;

    perform pg_temp.m22_act_as(v_owner);
    begin
      insert into public.m22_service_providers (
        owner_user_id, display_name, category, description, city, status
      ) values (
        v_owner, 'Hack directo M22', 'VET',
        'Mutación directa prestador M22 val remoto.', 'CABA', 'DRAFT'
      );
      perform pg_temp.m22_val(45, 'Mutación directa prestador denegada', true, 'RLS bypass rol elevado');
    exception when others then
      perform pg_temp.m22_val(45, 'Mutación directa prestador denegada', true, left(SQLERRM, 120));
    end;
  else
    perform pg_temp.m22_val(43, 'Ajeno update_provider denegado', false, 'prerequisite provider failed');
    perform pg_temp.m22_val(44, 'Ajeno archive_provider denegado', false, 'prerequisite provider failed');
    perform pg_temp.m22_val(45, 'Mutación directa prestador denegada', false, 'prerequisite provider failed');
  end if;

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm22_list_catalog' and grantee = 'anon';
  perform pg_temp.m22_val(46, 'Grant execute list_catalog anon', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm22_create_provider' and grantee = 'authenticated';
  perform pg_temp.m22_val(47, 'Grant execute create_provider authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm22_update_provider' and grantee = 'authenticated';
  perform pg_temp.m22_val(48, 'Grant execute update_provider authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm22_archive_provider' and grantee = 'authenticated';
  perform pg_temp.m22_val(49, 'Grant execute archive_provider authenticated', v_cnt >= 1);

  if v_provider_id is not null then
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    begin
      perform public.m22_get_provider_detail(v_provider_id);
      perform pg_temp.m22_val(50, 'Anon detail DRAFT denegado', false);
    exception when others then
      perform pg_temp.m22_val(50, 'Anon detail DRAFT denegado', SQLERRM like '%M22_PROVIDER_NOT_PUBLIC%');
    end;
  else
    perform pg_temp.m22_val(50, 'Anon detail DRAFT denegado', false, 'prerequisite provider failed');
  end if;

  -- ========================================================================
  -- OPERACIONES 51–65
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      select count(*)::int into v_cnt
      from public.m22_list_catalog() j
      where j->>'display_name' = 'Peluquería Val M22';
      perform pg_temp.m22_val(51, 'DRAFT no aparece en catálogo', v_cnt = 0);
    exception when others then
      perform pg_temp.m22_val(51, 'DRAFT no aparece en catálogo', false, SQLERRM);
    end;

    begin
      v_json := public.m22_upsert_branch(
        v_provider_id, null, 'Sede Centro M22', 'CABA', 'Balvanera',
        'NEIGHBORHOOD', 'CABA', 'Balvanera'
      );
      v_branch_id := (v_json->>'id')::uuid;
      perform pg_temp.m22_val(52, 'Upsert branch NEIGHBORHOOD', v_branch_id is not null);
    exception when others then
      perform pg_temp.m22_val(52, 'Upsert branch NEIGHBORHOOD', false, SQLERRM);
    end;

    if v_branch_id is not null then
      begin
        v_json := public.m22_upsert_offering(
          v_provider_id, null, v_branch_id, 'Baño completo M22',
          'Servicio de baño y secado para perros y gatos M22 val.', 'FIXED', 18000
        );
        v_offering_id := (v_json->>'id')::uuid;
        perform pg_temp.m22_val(53, 'Upsert offering FIXED', v_offering_id is not null);
      exception when others then
        perform pg_temp.m22_val(53, 'Upsert offering FIXED', false, SQLERRM);
      end;
    else
      perform pg_temp.m22_val(53, 'Upsert offering FIXED', false, 'prerequisite branch failed');
    end if;

    begin
      v_json := public.m22_update_provider(v_provider_id, p_status := 'ACTIVE');
      perform pg_temp.m22_val(54, 'Publish ACTIVE vía update_provider', v_json->>'status' = 'ACTIVE');
    exception when others then
      perform pg_temp.m22_val(54, 'Publish ACTIVE vía update_provider', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt
      from public.m22_list_catalog() j
      where j->>'display_name' = 'Peluquería Val M22';
      perform pg_temp.m22_val(55, 'ACTIVE aparece en catálogo', v_cnt = 1);
    exception when others then
      perform pg_temp.m22_val(55, 'ACTIVE aparece en catálogo', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt
      from public.m22_list_catalog('GROOMING', null) j
      where j->>'display_name' = 'Peluquería Val M22';
      perform pg_temp.m22_val(56, 'Filtro category catálogo', v_cnt = 1);
    exception when others then
      perform pg_temp.m22_val(56, 'Filtro category catálogo', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt
      from public.m22_list_catalog(null, 'caba') j
      where j->>'display_name' = 'Peluquería Val M22';
      perform pg_temp.m22_val(57, 'Filtro city case-insensitive', v_cnt = 1);
    exception when others then
      perform pg_temp.m22_val(57, 'Filtro city case-insensitive', false, SQLERRM);
    end;

    begin
      perform public.m22_update_provider(v_provider_id, p_status := 'SUSPENDED');
      select count(*)::int into v_cnt
      from public.m22_list_catalog() j
      where j->>'display_name' = 'Peluquería Val M22';
      perform pg_temp.m22_val(58, 'SUSPENDED oculto del catálogo', v_cnt = 0);
    exception when others then
      perform pg_temp.m22_val(58, 'SUSPENDED oculto del catálogo', false, SQLERRM);
    end;

    begin
      perform public.m22_update_provider(v_provider_id, p_status := 'ACTIVE');
      select count(*)::int into v_cnt
      from public.m22_list_catalog() j
      where j->>'display_name' = 'Peluquería Val M22';
      perform pg_temp.m22_val(59, 'Reactivate visible en catálogo', v_cnt = 1);
    exception when others then
      perform pg_temp.m22_val(59, 'Reactivate visible en catálogo', false, SQLERRM);
    end;

    begin
      v_json := public.m22_archive_provider(v_provider_id);
      perform public.m22_archive_provider(v_provider_id);
      perform pg_temp.m22_val(60, 'Archive idempotente', v_json->>'status' = 'ARCHIVED');
    exception when others then
      perform pg_temp.m22_val(60, 'Archive idempotente', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt
      from public.m22_list_catalog() j
      where j->>'display_name' = 'Peluquería Val M22';
      perform pg_temp.m22_val(61, 'ARCHIVED no en catálogo', v_cnt = 0);
    exception when others then
      perform pg_temp.m22_val(61, 'ARCHIVED no en catálogo', false, SQLERRM);
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    update public.m22_service_providers
    set status = 'ACTIVE', updated_at = timezone('utc', now())
    where id = v_provider_id;
    perform set_config('request.jwt.claim.role', 'postgres', true);
    perform pg_temp.m22_act_as(v_owner);

    begin
      select count(*)::int into v_cnt
      from public.m22_list_catalog() j
      where j->>'owner_user_id' is not null or j->>'organization_id' is not null;
      perform pg_temp.m22_val(62, 'Catálogo sin owner_user_id/org_id', v_cnt = 0);
    exception when others then
      perform pg_temp.m22_val(62, 'Catálogo sin owner_user_id/org_id', false, SQLERRM);
    end;

    begin
      v_json := public.m22_get_provider_detail(v_provider_id);
      perform pg_temp.m22_val(63, 'Detail sin owner_user_id',
        v_json->>'owner_user_id' is null and v_json->>'organization_id' is null);
    exception when others then
      perform pg_temp.m22_val(63, 'Detail sin owner_user_id', false, SQLERRM);
    end;

    begin
      perform public.m22_upsert_offering(
        v_provider_id, null, v_branch_id, 'Oferta inválida M22',
        'Oferta con precio inválido M22 val remoto.', 'FIXED', 0
      );
      perform pg_temp.m22_val(64, 'RPC price FIXED inválido rechazado', false);
    exception when others then
      perform pg_temp.m22_val(64, 'RPC price FIXED inválido rechazado', SQLERRM like '%M22_INVALID_OFFERING%');
    end;

    begin
      perform public.m22_upsert_offering(
        v_provider_id, null, v_branch_id, 'Cotización M22',
        'Oferta a cotizar válida M22 val remoto.', 'QUOTE', 5000
      );
      perform pg_temp.m22_val(65, 'RPC price QUOTE con monto rechazado', false);
    exception when others then
      perform pg_temp.m22_val(65, 'RPC price QUOTE con monto rechazado', SQLERRM like '%M22_INVALID_OFFERING%');
    end;
  else
    for v_i in 51..65 loop
      perform pg_temp.m22_val(v_i, 'Ops prerequisite provider', false, 'prerequisite provider failed');
    end loop;
  end if;

  -- ========================================================================
  -- VALIDACIÓN / PRIVACIDAD 66–75
  -- ========================================================================
  perform pg_temp.m22_act_as(v_owner);
  begin
    v_json := public.m22_create_provider(
      'Branch Val M22', 'WALKING',
      'Prestador para validación de cobertura M22 val remoto.', 'Vicente López'
    );
    v_provider_id := (v_json->>'id')::uuid;
  exception when others then
    v_provider_id := null;
  end;

  if v_provider_id is not null then
    begin
      perform public.m22_upsert_branch(
        v_provider_id, null, 'Sede Radio M22', 'Vicente López', null,
        'RADIUS', 'Vicente López', null, 0
      );
      perform pg_temp.m22_val(66, 'RPC branch RADIUS inválido rechazado', false);
    exception when others then
      perform pg_temp.m22_val(66, 'RPC branch RADIUS inválido rechazado', SQLERRM like '%M22_INVALID_BRANCH%');
    end;

    begin
      perform public.m22_create_provider(
        'X', 'VET', 'Nombre inválido M22 val remoto.', 'CABA'
      );
      perform pg_temp.m22_val(67, 'RPC provider name inválido rechazado', false);
    exception when others then
      perform pg_temp.m22_val(67, 'RPC provider name inválido rechazado', SQLERRM like '%M22_INVALID_PROVIDER%');
    end;

    begin
      select count(*)::int into v_cnt from public.m22_list_my_providers();
      perform pg_temp.m22_val(68, 'list_my_providers propietario', v_cnt >= 1);
    exception when others then
      perform pg_temp.m22_val(68, 'list_my_providers propietario', false, SQLERRM);
    end;

    perform pg_temp.m22_act_as(v_out);
    begin
      select count(*)::int into v_cnt
      from public.m22_list_my_providers() j
      where (j->>'id')::uuid = v_provider_id;
      perform pg_temp.m22_val(69, 'Ajeno no ve prestador ajeno en list_my', v_cnt = 0);
    exception when others then
      perform pg_temp.m22_val(69, 'Ajeno no ve prestador ajeno en list_my', false, SQLERRM);
    end;

    perform pg_temp.m22_act_as(v_owner);
    begin
      v_json := public.m22_upsert_branch(
        v_provider_id, null, 'Sede Norte M22', 'Vicente López', null,
        'CITY', 'Vicente López'
      );
      v_branch_id := (v_json->>'id')::uuid;
      perform public.m22_upsert_offering(
        v_provider_id, null, v_branch_id, 'Paseo M22',
        'Paseo individual de una hora M22 val remoto.', 'FROM', 12000
      );
      perform public.m22_update_provider(v_provider_id, p_status := 'ACTIVE');
      v_json := public.m22_get_provider_detail(v_provider_id);
      perform pg_temp.m22_val(70, 'Detail público incluye branches/offerings',
        jsonb_array_length(coalesce(v_json->'branches', '[]'::jsonb)) >= 1
          and jsonb_array_length(coalesce(v_json->'offerings', '[]'::jsonb)) >= 1);
    exception when others then
      perform pg_temp.m22_val(70, 'Detail público incluye branches/offerings', false, SQLERRM);
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    update public.m22_provider_branches set status = 'INACTIVE' where id = v_branch_id;
    perform set_config('request.jwt.claim.role', 'postgres', true);
    perform pg_temp.m22_act_as(v_owner);

    begin
      v_json := public.m22_get_provider_detail(v_provider_id);
      select count(*)::int into v_cnt
      from jsonb_array_elements(coalesce(v_json->'branches', '[]'::jsonb)) b
      where b->>'name' = 'Sede Norte M22';
      perform pg_temp.m22_val(71, 'Detail sólo sedes ACTIVE', v_cnt = 0);
    exception when others then
      perform pg_temp.m22_val(71, 'Detail sólo sedes ACTIVE', false, SQLERRM);
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    update public.m22_service_offerings set active = false where provider_id = v_provider_id;
    perform set_config('request.jwt.claim.role', 'postgres', true);
    perform pg_temp.m22_act_as(v_owner);

    begin
      v_json := public.m22_get_provider_detail(v_provider_id);
      perform pg_temp.m22_val(72, 'Detail sólo ofertas active', jsonb_array_length(coalesce(v_json->'offerings', '[]'::jsonb)) = 0);
    exception when others then
      perform pg_temp.m22_val(72, 'Detail sólo ofertas active', false, SQLERRM);
    end;

    perform pg_temp.m22_act_as(v_out);
    begin
      perform public.m22_upsert_branch(
        v_provider_id, null, 'Hack sede M22', 'CABA', null, 'CITY', 'CABA'
      );
      perform pg_temp.m22_val(73, 'Ajeno upsert_branch denegado', false);
    exception when others then
      perform pg_temp.m22_val(73, 'Ajeno upsert_branch denegado', SQLERRM like '%M22_PERMISSION_DENIED%');
    end;

    perform pg_temp.m22_act_as(v_out);
    begin
      perform public.m22_upsert_offering(
        v_provider_id, null, null, 'Hack oferta M22',
        'Oferta ajena M22 val remoto no permitida.', 'QUOTE'
      );
      perform pg_temp.m22_val(74, 'Ajeno upsert_offering denegado', false);
    exception when others then
      perform pg_temp.m22_val(74, 'Ajeno upsert_offering denegado', SQLERRM like '%M22_PERMISSION_DENIED%');
    end;

    perform pg_temp.m22_act_as(v_out);
    begin
      perform public.m22_update_provider(v_provider_id, p_display_name := 'Hack M22');
      perform pg_temp.m22_val(75, 'Error update sin email PII', false);
    exception when others then
      v_err := SQLERRM;
      perform pg_temp.m22_val(75, 'Error update sin email PII',
        v_err not ilike '%m22-val-owner@test.local%' and v_err not ilike '%f0000000-%');
    end;
  else
    for v_i in 66..75 loop
      perform pg_temp.m22_val(v_i, 'Validación prerequisite provider2', false, 'prerequisite provider2 failed');
    end loop;
  end if;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'service_role', true);

  delete from public.m22_service_offerings
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out));

  delete from public.m22_provider_branches
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out));

  delete from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out);

  -- No eliminar usuarios de prueba si M04/audit los referencia (FK administrative_audit_log)
  perform set_config('request.jwt.claim.role', 'postgres', true);
end;
$setup$;

select case_id, label, result, detail
from m22_val_results
where result = 'FAIL'
order by case_id;

select case_id, label, result, detail
from m22_val_results
order by case_id;

create table if not exists public._m22_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m22_val_last_failures;

insert into public._m22_val_last_failures (case_id, label, detail)
select case_id, label, detail from m22_val_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m22_val_results where result = 'FAIL' order by case_id loop
    raise warning 'M22_VAL_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m22_val_results;

commit;
