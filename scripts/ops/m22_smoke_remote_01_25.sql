-- M22 smoke remoto casos 01–25 — validación staging (SQL/RPC, no Android)
-- Ejecutar: supabase db query --linked -f scripts/ops/m22_smoke_remote_01_25.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m22_smoke_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m22_smoke(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m22_smoke_results (case_id, label, result, detail)
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
  v_json jsonb;
  v_cnt int;
  v_ok boolean;
  v_err text;
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_owner, 'authenticated', 'authenticated',
     'm22-smoke-owner@test.local', crypt('m22-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_peer, 'authenticated', 'authenticated',
     'm22-smoke-peer@test.local', crypt('m22-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm22-smoke-out@test.local', crypt('m22-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_owner, 'm22-smoke-owner@test.local', 'M22 Smoke Owner', 'M22 Smoke Owner', 'PERSON', true, 'ACTIVE'),
    (v_peer, 'm22-smoke-peer@test.local', 'M22 Smoke Peer', 'M22 Smoke Peer', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm22-smoke-out@test.local', 'M22 Smoke Outsider', 'M22 Smoke Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m22_service_offerings
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out));
  delete from public.m22_provider_branches
  where provider_id in (select id from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out));
  delete from public.m22_service_providers where owner_user_id in (v_owner, v_peer, v_out);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ========================================================================
  -- 01 SupabaseM22ProviderRepository wired
  -- ========================================================================
  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm22_list_my_providers';
  v_ok := v_cnt >= 1;

  perform pg_temp.m22_act_as(v_owner);
  begin
    select count(*)::int into v_cnt from public.m22_list_my_providers();
    perform pg_temp.m22_smoke(1, 'SupabaseM22ProviderRepository wired',
      v_ok and v_cnt >= 0,
      case when v_ok then 'RPC m22_list_my_providers callable' else 'RPC missing' end);
  exception when others then
    perform pg_temp.m22_smoke(1, 'SupabaseM22ProviderRepository wired', false, SQLERRM);
  end;

  -- ========================================================================
  -- 02 Create provider draft
  -- ========================================================================
  perform pg_temp.m22_act_as(v_owner);
  begin
    v_json := public.m22_create_provider(
      'Patitas Smoke M22', 'GROOMING',
      'Prestador smoke remoto M22 LeoVer para validación staging.', 'CABA'
    );
    v_provider_id := (v_json->>'id')::uuid;
    perform pg_temp.m22_smoke(2, 'Create provider draft',
      v_provider_id is not null and v_json->>'status' = 'DRAFT');
  exception when others then
    perform pg_temp.m22_smoke(2, 'Create provider draft', false, SQLERRM);
  end;

  -- ========================================================================
  -- 03 Draft not in catalog
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    select count(*)::int into v_cnt
    from public.m22_list_catalog() j
    where j->>'display_name' = 'Patitas Smoke M22';
    perform pg_temp.m22_smoke(3, 'Draft not in catalog', v_cnt = 0);
  exception when others then
    perform pg_temp.m22_smoke(3, 'Draft not in catalog', false, SQLERRM);
  end;

  -- ========================================================================
  -- 04 Upsert branch
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      v_json := public.m22_upsert_branch(
        v_provider_id, null, 'Sede Centro Smoke', 'CABA', 'Balvanera',
        'NEIGHBORHOOD', 'CABA', 'Balvanera'
      );
      v_branch_id := (v_json->>'id')::uuid;
      perform pg_temp.m22_smoke(4, 'Upsert branch', v_branch_id is not null);
    exception when others then
      perform pg_temp.m22_smoke(4, 'Upsert branch', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(4, 'Upsert branch', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 05 Upsert offering FIXED
  -- ========================================================================
  if v_provider_id is not null and v_branch_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      v_json := public.m22_upsert_offering(
        v_provider_id, null, v_branch_id, 'Baño smoke M22',
        'Servicio de baño completo smoke remoto M22 LeoVer.', 'FIXED', 18000
      );
      perform pg_temp.m22_smoke(5, 'Upsert offering FIXED', v_json->>'price_type' = 'FIXED');
    exception when others then
      perform pg_temp.m22_smoke(5, 'Upsert offering FIXED', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(5, 'Upsert offering FIXED', false, 'prerequisite branch failed');
  end if;

  -- ========================================================================
  -- 06 Publish via update status ACTIVE
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      v_json := public.m22_update_provider(v_provider_id, p_status := 'ACTIVE');
      perform pg_temp.m22_smoke(6, 'Publish via update ACTIVE',
        v_json->>'status' = 'ACTIVE');
    exception when others then
      perform pg_temp.m22_smoke(6, 'Publish via update ACTIVE', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(6, 'Publish via update ACTIVE', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 07 List catalog includes provider
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    select count(*)::int into v_cnt
    from public.m22_list_catalog() j
    where j->>'display_name' = 'Patitas Smoke M22';
    perform pg_temp.m22_smoke(7, 'List catalog includes provider', v_cnt = 1);
  exception when others then
    perform pg_temp.m22_smoke(7, 'List catalog includes provider', false, SQLERRM);
  end;

  -- ========================================================================
  -- 08 Filter city
  -- ========================================================================
  begin
    select count(*)::int into v_cnt
    from public.m22_list_catalog(null, 'caba') j
    where j->>'display_name' = 'Patitas Smoke M22';
    perform pg_temp.m22_smoke(8, 'Filter city', v_cnt = 1);
  exception when others then
    perform pg_temp.m22_smoke(8, 'Filter city', false, SQLERRM);
  end;

  -- ========================================================================
  -- 09 Filter category
  -- ========================================================================
  begin
    select count(*)::int into v_cnt
    from public.m22_list_catalog('GROOMING', null) j
    where j->>'display_name' = 'Patitas Smoke M22';
    perform pg_temp.m22_smoke(9, 'Filter category', v_cnt = 1);
  exception when others then
    perform pg_temp.m22_smoke(9, 'Filter category', false, SQLERRM);
  end;

  -- ========================================================================
  -- 10 Public detail
  -- ========================================================================
  if v_provider_id is not null then
    begin
      v_json := public.m22_get_provider_detail(v_provider_id);
      perform pg_temp.m22_smoke(10, 'Public detail',
        v_json->>'display_name' = 'Patitas Smoke M22'
          and jsonb_array_length(coalesce(v_json->'branches', '[]'::jsonb)) >= 1
          and jsonb_array_length(coalesce(v_json->'offerings', '[]'::jsonb)) >= 1);
    exception when others then
      perform pg_temp.m22_smoke(10, 'Public detail', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(10, 'Public detail', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 11 Suspend provider
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      v_json := public.m22_update_provider(v_provider_id, p_status := 'SUSPENDED');
      perform pg_temp.m22_smoke(11, 'Suspend provider', v_json->>'status' = 'SUSPENDED');
    exception when others then
      perform pg_temp.m22_smoke(11, 'Suspend provider', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(11, 'Suspend provider', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 12 Suspended not in catalog
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    select count(*)::int into v_cnt
    from public.m22_list_catalog() j
    where j->>'display_name' = 'Patitas Smoke M22';
    perform pg_temp.m22_smoke(12, 'Suspended not in catalog', v_cnt = 0);
  exception when others then
    perform pg_temp.m22_smoke(12, 'Suspended not in catalog', false, SQLERRM);
  end;

  -- ========================================================================
  -- 13 Reactivate provider
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      v_json := public.m22_update_provider(v_provider_id, p_status := 'ACTIVE');
      perform pg_temp.m22_smoke(13, 'Reactivate provider', v_json->>'status' = 'ACTIVE');
    exception when others then
      perform pg_temp.m22_smoke(13, 'Reactivate provider', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(13, 'Reactivate provider', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 14 Reactivated in catalog
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    select count(*)::int into v_cnt
    from public.m22_list_catalog() j
    where j->>'display_name' = 'Patitas Smoke M22';
    perform pg_temp.m22_smoke(14, 'Reactivated in catalog', v_cnt = 1);
  exception when others then
    perform pg_temp.m22_smoke(14, 'Reactivated in catalog', false, SQLERRM);
  end;

  -- ========================================================================
  -- 15 Archive provider
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      v_json := public.m22_archive_provider(v_provider_id);
      perform pg_temp.m22_smoke(15, 'Archive provider', v_json->>'status' = 'ARCHIVED');
    exception when others then
      perform pg_temp.m22_smoke(15, 'Archive provider', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(15, 'Archive provider', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 16 Archived not in catalog
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    select count(*)::int into v_cnt
    from public.m22_list_catalog() j
    where j->>'display_name' = 'Patitas Smoke M22';
    perform pg_temp.m22_smoke(16, 'Archived not in catalog', v_cnt = 0);
  exception when others then
    perform pg_temp.m22_smoke(16, 'Archived not in catalog', false, SQLERRM);
  end;

  -- ========================================================================
  -- 17 Archive idempotent
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      v_json := public.m22_archive_provider(v_provider_id);
      perform pg_temp.m22_smoke(17, 'Archive idempotent', v_json->>'status' = 'ARCHIVED');
    exception when others then
      perform pg_temp.m22_smoke(17, 'Archive idempotent', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(17, 'Archive idempotent', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 18 Permission denied outsider update
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'service_role', true);
  update public.m22_service_providers set status = 'DRAFT' where id = v_provider_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_out);
    begin
      perform public.m22_update_provider(v_provider_id, p_status := 'ACTIVE');
      perform pg_temp.m22_smoke(18, 'Permission denied outsider update', false);
    exception when others then
      perform pg_temp.m22_smoke(18, 'Permission denied outsider update', SQLERRM like '%M22_PERMISSION_DENIED%');
    end;
  else
    perform pg_temp.m22_smoke(18, 'Permission denied outsider update', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 19 Permission denied outsider archive
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_out);
    begin
      perform public.m22_archive_provider(v_provider_id);
      perform pg_temp.m22_smoke(19, 'Permission denied outsider archive', false);
    exception when others then
      perform pg_temp.m22_smoke(19, 'Permission denied outsider archive', SQLERRM like '%M22_PERMISSION_DENIED%');
    end;
  else
    perform pg_temp.m22_smoke(19, 'Permission denied outsider archive', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 20–23 No PII in public JSON
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'service_role', true);
  update public.m22_service_providers set status = 'ACTIVE' where id = v_provider_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);

  if v_provider_id is not null then
    begin
      select count(*)::int into v_cnt
      from public.m22_list_catalog() j
      where j->>'owner_user_id' is not null;
      perform pg_temp.m22_smoke(20, 'No owner_user_id in catalog JSON', v_cnt = 0);
    exception when others then
      perform pg_temp.m22_smoke(20, 'No owner_user_id in catalog JSON', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt
      from public.m22_list_catalog() j
      where j->>'organization_id' is not null;
      perform pg_temp.m22_smoke(21, 'No organization_id in catalog JSON', v_cnt = 0);
    exception when others then
      perform pg_temp.m22_smoke(21, 'No organization_id in catalog JSON', false, SQLERRM);
    end;

    begin
      v_json := public.m22_get_provider_detail(v_provider_id);
      perform pg_temp.m22_smoke(22, 'No owner_user_id in detail JSON',
        v_json->>'owner_user_id' is null);
    exception when others then
      perform pg_temp.m22_smoke(22, 'No owner_user_id in detail JSON', false, SQLERRM);
    end;

    begin
      v_json := public.m22_get_provider_detail(v_provider_id);
      perform pg_temp.m22_smoke(23, 'No organization_id in detail JSON',
        v_json->>'organization_id' is null);
    exception when others then
      perform pg_temp.m22_smoke(23, 'No organization_id in detail JSON', false, SQLERRM);
    end;
  else
    perform pg_temp.m22_smoke(20, 'No owner_user_id in catalog JSON', false, 'prerequisite case 2 failed');
    perform pg_temp.m22_smoke(21, 'No organization_id in catalog JSON', false, 'prerequisite case 2 failed');
    perform pg_temp.m22_smoke(22, 'No owner_user_id in detail JSON', false, 'prerequisite case 2 failed');
    perform pg_temp.m22_smoke(23, 'No organization_id in detail JSON', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 24 Invalid price FIXED rejected
  -- ========================================================================
  if v_provider_id is not null and v_branch_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      perform public.m22_upsert_offering(
        v_provider_id, null, v_branch_id, 'Oferta inválida smoke',
        'Oferta smoke con precio inválido M22 remoto.', 'FIXED', 0
      );
      perform pg_temp.m22_smoke(24, 'Invalid price FIXED rejected', false);
    exception when others then
      perform pg_temp.m22_smoke(24, 'Invalid price FIXED rejected', SQLERRM like '%M22_INVALID_OFFERING%');
    end;
  else
    perform pg_temp.m22_smoke(24, 'Invalid price FIXED rejected', false, 'prerequisite branch failed');
  end if;

  -- ========================================================================
  -- 25 Invalid branch coverage rejected
  -- ========================================================================
  if v_provider_id is not null then
    perform pg_temp.m22_act_as(v_owner);
    begin
      perform public.m22_upsert_branch(
        v_provider_id, null, 'Sede inválida smoke', 'CABA', null,
        'RADIUS', 'CABA', null, 0
      );
      perform pg_temp.m22_smoke(25, 'Invalid branch coverage rejected', false);
    exception when others then
      perform pg_temp.m22_smoke(25, 'Invalid branch coverage rejected', SQLERRM like '%M22_INVALID_BRANCH%');
    end;
  else
    perform pg_temp.m22_smoke(25, 'Invalid branch coverage rejected', false, 'prerequisite case 2 failed');
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

  -- No eliminar usuarios si M04/audit los referencia (FK administrative_audit_log)
  perform set_config('request.jwt.claim.role', 'postgres', true);
end;
$setup$;

select case_id, label, result, detail
from m22_smoke_results
where result = 'FAIL'
order by case_id;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m22_smoke_results;

create table if not exists public._m22_smoke_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m22_smoke_last_failures;

insert into public._m22_smoke_last_failures (case_id, label, detail)
select case_id, label, detail from m22_smoke_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m22_smoke_results where result = 'FAIL' order by case_id loop
    raise warning 'M22_SMOKE_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

commit;
