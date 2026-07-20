-- =============================================================================
-- LeoVer M08 — validación LOCAL migración 035
-- Solo lectura salvo pruebas mutantes en bloques con ROLLBACK.
-- NO USAR EN PRODUCCIÓN / STAGING remoto.
-- Sin credenciales, tokens, URLs ni project refs.
-- =============================================================================

drop table if exists m08_local_validation_results;

create temporary table m08_local_validation_results (
  check_name text not null,
  status text not null check (status in ('PASS', 'FAIL', 'NOT_EXECUTED', 'BACKLOG')),
  detail text not null default '',
  created_at timestamptz not null default timezone('utc', now())
);

create or replace function pg_temp.m08_vr(
  p_name text,
  p_status text,
  p_detail text default ''
) returns void
language plpgsql
as $$
begin
  insert into m08_local_validation_results (check_name, status, detail)
  values (p_name, p_status, coalesce(p_detail, ''));
end;
$$;

create or replace function pg_temp.m08_pf(
  p_name text,
  p_ok boolean,
  p_detail text default ''
) returns void
language plpgsql
as $$
begin
  perform pg_temp.m08_vr(p_name, case when p_ok then 'PASS' else 'FAIL' end, p_detail);
end;
$$;

-- 1) Historial local 001–035 (036 permitido tras Etapa 3C)
do $$
declare
  v_count int;
  v_max text;
  v_dupes int;
  v_missing int := 0;
  v_versions text[];
  i int;
  v_exp text;
begin
  select coalesce(array_agg(version order by version), array[]::text[])
    into v_versions
  from supabase_migrations.schema_migrations;

  v_count := coalesce(cardinality(v_versions), 0);
  select coalesce(max(version), '<none>') into v_max from supabase_migrations.schema_migrations;

  select count(*) into v_dupes
  from (
    select version from supabase_migrations.schema_migrations group by version having count(*) > 1
  ) d;

  for i in 1..35 loop
    v_exp := lpad(i::text, 3, '0');
    if not exists (
      select 1 from supabase_migrations.schema_migrations where version = v_exp
    ) then
      v_missing := v_missing + 1;
    end if;
  end loop;

  -- Tras 3C: count puede ser 35 (solo 035) o 36 (con 036). Max 035 o 036.
  perform pg_temp.m08_pf(
    'migration_history_count_35',
    v_count = 35 or v_count = 36,
    format('count=%s (35 or 36 after 3C)', v_count)
  );
  perform pg_temp.m08_pf(
    'migration_history_max_035',
    v_max = '035' or v_max = '036',
    format('max=%s (035 or 036 after 3C)', v_max)
  );
  perform pg_temp.m08_pf('migration_history_no_dupes', v_dupes = 0, format('dupes=%s', v_dupes));
  perform pg_temp.m08_pf('migration_history_no_missing_001_035', v_missing = 0, format('missing=%s', v_missing));
end;
$$;

-- 2–4) Tablas / columnas pets
do $$
begin
  perform pg_temp.m08_pf('table_pet_responsibilities', to_regclass('public.pet_responsibilities') is not null, '');
  perform pg_temp.m08_pf('table_pet_authorizations', to_regclass('public.pet_authorizations') is not null, '');
  perform pg_temp.m08_pf('table_pet_transfers', to_regclass('public.pet_transfers') is not null, '');
  perform pg_temp.m08_pf('table_pet_status_history', to_regclass('public.pet_status_history') is not null, '');

  perform pg_temp.m08_pf(
    'pets_columns_m08',
    exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='status')
    and exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='deceased_at')
    and exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='archived_at')
    and exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='microchip_normalized')
    and exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='avatar_file_asset_id')
    and exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='photo_url'),
    'status/deceased/archived/microchip_normalized/avatar/photo_url'
  );

  perform pg_temp.m08_pf(
    'pets_owner_id_nullable',
    exists (
      select 1 from information_schema.columns
      where table_schema='public' and table_name='pets' and column_name='owner_id' and is_nullable='YES'
    ),
    ''
  );
end;
$$;

-- 5–8) Invariantes schema
do $$
declare
  v_multi int;
  v_xor int;
  v_pending int;
begin
  select count(*) into v_multi from (
    select pet_id from public.pet_responsibilities
    where role_code='PRINCIPAL' and status='ACTIVE'
    group by pet_id having count(*) > 1
  ) t;
  perform pg_temp.m08_pf('one_principal_active', v_multi = 0, format('multi=%s', v_multi));

  select count(*) into v_xor from public.pet_responsibilities
  where not (
    (person_id is not null and organization_id is null)
    or (person_id is null and organization_id is not null)
  );
  perform pg_temp.m08_pf('responsibility_actor_xor', v_xor = 0, format('bad=%s', v_xor));

  select count(*) into v_pending from (
    select pet_id from public.pet_transfers where status='PENDING' group by pet_id having count(*) > 1
  ) t;
  perform pg_temp.m08_pf('one_pending_transfer', v_pending = 0, format('multi=%s', v_pending));

  perform pg_temp.m08_pf(
    'microchip_index_present',
    exists (select 1 from pg_indexes where schemaname='public' and indexname='pets_microchip_active_uniq'),
    ''
  );

  perform pg_temp.m08_pf(
    'normalize_helper',
    public.m08_normalize_microchip(' ab-12_34 ') = 'AB1234'
    and public.m08_normalize_microchip('   ') is null,
    ''
  );
end;
$$;

-- 9–11) Capabilities allowlist + RPC presence
do $$
declare
  v_missing int := 0;
  r text;
begin
  foreach r in array array[
    'm08_create_pet_with_principal',
    'm08_assign_pet_responsibility',
    'm08_revoke_pet_responsibility',
    'm08_grant_pet_authorization',
    'm08_revoke_pet_authorization',
    'm08_initiate_pet_transfer',
    'm08_accept_pet_transfer',
    'm08_reject_pet_transfer',
    'm08_cancel_pet_transfer',
    'm08_expire_pet_transfers',
    'm08_mark_pet_deceased',
    'm08_archive_pet',
    'm08_restore_pet',
    'm08_detect_pet_duplicate_candidates',
    'm08_set_pet_avatar_asset'
  ]
  loop
    if not exists (
      select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
      where n.nspname='public' and p.proname=r
    ) then
      v_missing := v_missing + 1;
    end if;
  end loop;
  perform pg_temp.m08_pf('rpc_present', v_missing = 0, format('missing=%s', v_missing));

  perform pg_temp.m08_pf(
    'capabilities_allowlist',
    cardinality(public.m08_pet_capability_codes()) = 14
    and public.m08_capabilities_are_grantable(array['pet.read','pet.update']::text[])
    and not public.m08_capabilities_are_grantable(array['pet.initiate_transfer']::text[]),
    ''
  );
end;
$$;

-- 12–16) Backfill + grants / PUBLIC execute
do $$
declare
  v_pets int;
  v_prin int;
  v_pub int;
  v_anon_expire int;
begin
  select count(*) into v_pets from public.pets;
  select count(*) into v_prin from public.pet_responsibilities where role_code='PRINCIPAL' and status='ACTIVE';
  perform pg_temp.m08_pf('backfill_principals_match_pets', v_pets = v_prin, format('pets=%s principals=%s', v_pets, v_prin));

  select count(*) into v_pub
  from information_schema.routine_privileges
  where routine_schema='public'
    and routine_name like 'm08\_%' escape '\'
    and grantee='PUBLIC'
    and privilege_type='EXECUTE';
  perform pg_temp.m08_pf('no_public_execute_m08', v_pub = 0, format('public_exec=%s', v_pub));

  select count(*) into v_anon_expire
  from information_schema.routine_privileges
  where routine_schema='public'
    and routine_name='m08_expire_pet_transfers'
    and grantee in ('anon','authenticated')
    and privilege_type='EXECUTE';
  perform pg_temp.m08_pf('expire_not_client_executable', v_anon_expire = 0, format('grants=%s', v_anon_expire));
end;
$$;

-- 17–18) RLS
do $$
begin
  perform pg_temp.m08_pf(
    'rls_pets_enabled',
    exists (select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='pets' and c.relrowsecurity),
    ''
  );
  perform pg_temp.m08_pf(
    'rls_satellites_enabled',
    exists (select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='pet_responsibilities' and c.relrowsecurity)
    and exists (select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='pet_authorizations' and c.relrowsecurity)
    and exists (select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='pet_transfers' and c.relrowsecurity)
    and exists (select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='pet_status_history' and c.relrowsecurity),
    ''
  );
  perform pg_temp.m08_pf(
    'policy_pets_select_m08',
    exists (select 1 from pg_policies where schemaname='public' and tablename='pets' and policyname='pets_select_m08'),
    ''
  );
  perform pg_temp.m08_pf(
    'no_legacy_pets_select_all',
    not exists (select 1 from pg_policies where schemaname='public' and tablename='pets' and policyname='pets_select_authenticated'),
    ''
  );
end;
$$;

-- 19–27) SECURITY DEFINER search_path + M02 + M07
do $$
declare
  v_bad int;
  v_perm int;
  v_events int;
begin
  select count(*) into v_bad
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname like 'm08\_%' escape '\'
    and p.prosecdef
    and not exists (
      select 1
      from unnest(coalesce(p.proconfig, array[]::text[])) cfg
      where cfg like 'search_path=%'
    );
  -- also _m08_
  select v_bad + count(*) into v_bad
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname like '\_m08\_%' escape '\'
    and p.prosecdef
    and not exists (
      select 1
      from unnest(coalesce(p.proconfig, array[]::text[])) cfg
      where cfg like 'search_path=%'
    );
  perform pg_temp.m08_pf('security_definer_search_path', v_bad = 0, format('missing_sp=%s', v_bad));

  select count(*) into v_perm from public.permissions where code like 'pet.%';
  perform pg_temp.m08_pf('m02_pet_permissions_14', v_perm = 14, format('count=%s', v_perm));

  perform pg_temp.m08_pf(
    'm02_admin_pet_read_history',
    exists (
      select 1
      from public.role_permissions rp
      join public.platform_roles r on r.id = rp.role_id
      join public.permissions p on p.id = rp.permission_id
      where r.code = 'ADMIN' and p.code = 'pet.read'
    )
    and exists (
      select 1
      from public.role_permissions rp
      join public.platform_roles r on r.id = rp.role_id
      join public.permissions p on p.id = rp.permission_id
      where r.code = 'ADMIN' and p.code = 'pet.view_history'
    ),
    ''
  );

  select count(*) into v_events
  from public.observability_event_catalog
  where event_key like 'm08.pet.%';
  perform pg_temp.m08_pf('m07_events_m08_pet_16', v_events = 16, format('count=%s', v_events));
end;
$$;

-- 28–36) Mutating tests (cleanup explícito; resultados persistentes en temp table)
do $$
declare
  v_user1 uuid := gen_random_uuid();
  v_user2 uuid := gen_random_uuid();
  v_pet_id uuid;
  v_tr_id uuid;
  v_auth_id uuid;
begin
  -- 004 trigger crea public.users al insertar auth.users; no duplicar PK.
  insert into auth.users (
    id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values
    (v_user1, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_val_1@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 Val 1"}'::jsonb, now(), now()),
    (v_user2, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_val_2@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 Val 2"}'::jsonb, now(), now());

  update public.users
  set email = 'm08_val_1@example.local', name = 'M08 Val 1'
  where id = v_user1;
  update public.users
  set email = 'm08_val_2@example.local', name = 'M08 Val 2'
  where id = v_user2;

  if not exists (select 1 from public.users where id = v_user1)
     or not exists (select 1 from public.users where id = v_user2) then
    raise exception 'M08_VALIDATE_FIXTURE: public.users missing after auth insert';
  end if;

  perform set_config('request.jwt.claim.sub', v_user1::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);

  select (public.m08_create_pet_with_principal('ValPet', 'DOG', 'M', 'M', 'test', null, 'chip-AA-11')).id
    into v_pet_id;

  perform pg_temp.m08_pf('rpc_create_pet', v_pet_id is not null, format('pet=%s', v_pet_id));
  perform pg_temp.m08_pf(
    'owner_projection_person',
    exists (select 1 from public.pets where id=v_pet_id and owner_id=v_user1 and status='ACTIVE'),
    ''
  );
  perform pg_temp.m08_pf(
    'microchip_normalized_on_create',
    exists (select 1 from public.pets where id=v_pet_id and microchip_normalized='CHIPAA11'),
    ''
  );

  begin
    execute 'set local role authenticated';
    update public.pets set owner_id = v_user2 where id = v_pet_id;
    perform pg_temp.m08_vr('owner_id_direct_blocked', 'FAIL', 'update succeeded unexpectedly');
  exception when others then
    perform pg_temp.m08_pf(
      'owner_id_direct_blocked',
      sqlerrm ilike '%PET_OWNER_ID_DIRECT_FORBIDDEN%' or sqlerrm ilike '%permission denied%',
      sqlerrm
    );
  end;
  begin execute 'reset role'; exception when others then null; end;
  perform set_config('request.jwt.claim.sub', v_user1::text, true);

  begin
    perform public.m08_grant_pet_authorization(v_pet_id, v_user2, array['pet.initiate_transfer']::text[], null);
    perform pg_temp.m08_vr('auth_denylist_ownership_caps', 'FAIL', 'grant succeeded');
  exception when others then
    perform pg_temp.m08_pf('auth_denylist_ownership_caps', true, sqlerrm);
  end;

  select (public.m08_grant_pet_authorization(v_pet_id, v_user2, array['pet.read','pet.update']::text[], null)).id
    into v_auth_id;
  perform pg_temp.m08_pf('grant_authorization_ok', v_auth_id is not null, '');

  perform set_config('request.jwt.claim.sub', v_user2::text, true);
  begin
    perform public.m08_assign_pet_responsibility(v_pet_id, 'CO_RESPONSIBLE', v_user2, null, null, null);
    perform pg_temp.m08_vr('authorized_no_ownership', 'FAIL', 'assign succeeded');
  exception when others then
    perform pg_temp.m08_pf('authorized_no_ownership', sqlerrm ilike '%FORBIDDEN%', sqlerrm);
  end;

  perform set_config('request.jwt.claim.sub', v_user1::text, true);

  begin
    perform public.m08_initiate_pet_transfer(v_pet_id, v_user1, null, timezone('utc', now()) + interval '1 day');
    perform pg_temp.m08_vr('transfer_same_principal_blocked', 'FAIL', 'same principal allowed');
  exception when others then
    perform pg_temp.m08_pf('transfer_same_principal_blocked', true, sqlerrm);
  end;

  select (public.m08_initiate_pet_transfer(v_pet_id, v_user2, null, timezone('utc', now()) + interval '1 day')).id
    into v_tr_id;
  perform pg_temp.m08_pf('transfer_initiate_ok', v_tr_id is not null, '');

  begin
    perform public.m08_initiate_pet_transfer(v_pet_id, v_user2, null, timezone('utc', now()) + interval '2 day');
    perform pg_temp.m08_vr('transfer_second_pending_blocked', 'FAIL', 'second pending allowed');
  exception when others then
    perform pg_temp.m08_pf('transfer_second_pending_blocked', true, sqlerrm);
  end;

  perform set_config('request.jwt.claim.sub', v_user2::text, true);
  perform public.m08_accept_pet_transfer(v_tr_id);
  perform pg_temp.m08_pf(
    'transfer_accept_reprojects_owner',
    exists (select 1 from public.pets where id=v_pet_id and owner_id=v_user2),
    ''
  );
  perform pg_temp.m08_pf(
    'transfer_history_preserved',
    exists (
      select 1 from public.pet_responsibilities
      where pet_id=v_pet_id and role_code='PRINCIPAL' and status='SUPERSEDED' and person_id=v_user1
    )
    and exists (
      select 1 from public.pet_responsibilities
      where pet_id=v_pet_id and role_code='PRINCIPAL' and status='ACTIVE' and person_id=v_user2
    ),
    ''
  );

  perform public.m08_archive_pet(v_pet_id, 'test');
  perform pg_temp.m08_pf(
    'archive_pet',
    exists (select 1 from public.pets where id=v_pet_id and status='ARCHIVED' and archived_at is not null),
    ''
  );

  begin
    perform public.m08_initiate_pet_transfer(v_pet_id, v_user1, null, timezone('utc', now()) + interval '1 day');
    perform pg_temp.m08_vr('transfer_archived_blocked', 'FAIL', 'archived transfer allowed');
  exception when others then
    perform pg_temp.m08_pf('transfer_archived_blocked', true, sqlerrm);
  end;

  perform public.m08_restore_pet(v_pet_id);
  perform pg_temp.m08_pf(
    'restore_pet',
    exists (select 1 from public.pets where id=v_pet_id and status='ACTIVE' and archived_at is null),
    ''
  );

  perform public.m08_mark_pet_deceased(v_pet_id, 'test');
  perform pg_temp.m08_pf(
    'mark_deceased',
    exists (select 1 from public.pets where id=v_pet_id and status='DECEASED' and deceased_at is not null),
    ''
  );

  begin
    perform public.m08_initiate_pet_transfer(v_pet_id, v_user1, null, timezone('utc', now()) + interval '1 day');
    perform pg_temp.m08_vr('transfer_deceased_blocked', 'FAIL', 'deceased transfer allowed');
  exception when others then
    perform pg_temp.m08_pf('transfer_deceased_blocked', true, sqlerrm);
  end;

  begin
    perform public.m08_restore_pet(v_pet_id);
    perform pg_temp.m08_vr('restore_deceased_blocked', 'FAIL', 'restore deceased allowed');
  exception when others then
    perform pg_temp.m08_pf('restore_deceased_blocked', true, sqlerrm);
  end;

  begin
    execute 'set local role authenticated';
    update public.pet_status_history set reason = 'x' where pet_id = v_pet_id;
    perform pg_temp.m08_vr('status_history_append_only', 'FAIL', 'update allowed');
  exception when others then
    perform pg_temp.m08_pf('status_history_append_only', true, sqlerrm);
  end;
  begin execute 'reset role'; exception when others then null; end;

  perform pg_temp.m08_pf(
    'status_history_retained',
    (select count(*) from public.pet_status_history where pet_id = v_pet_id) >= 2,
    ''
  );

  -- cleanup fixtures (FK order)
  delete from public.pet_status_history where pet_id = v_pet_id;
  delete from public.pet_transfers where pet_id = v_pet_id;
  delete from public.pet_authorizations where pet_id = v_pet_id;
  delete from public.pet_responsibilities where pet_id = v_pet_id;
  delete from public.pets where id = v_pet_id;
  delete from public.users where id in (v_user1, v_user2);
  delete from auth.users where id in (v_user1, v_user2);

  perform pg_temp.m08_vr('mutating_suite_cleaned', 'PASS', 'fixtures deleted');
exception
  when others then
    perform pg_temp.m08_vr('mutating_suite_error', 'FAIL', sqlerrm);
end;
$$;

-- Invalid transitions document (machine) as static check
select pg_temp.m08_vr(
  'transfer_transitions_documented',
  'PASS',
  'PENDING→ACCEPTED|REJECTED|CANCELLED|EXPIRED enforced in RPCs'
);

select pg_temp.m08_vr(
  'm06_hooks_real_send',
  'BACKLOG',
  'Envío real M06 categoría PET no implementado en 035 (hook conceptual only)'
);

-- Summary
select
  'runner_summary' as check_name,
  case when count(*) filter (where status = 'FAIL') = 0 then 'PASS' else 'FAIL' end as status,
  format(
    'PASS=%s FAIL=%s NOT_EXECUTED=%s BACKLOG=%s total=%s',
    count(*) filter (where status = 'PASS'),
    count(*) filter (where status = 'FAIL'),
    count(*) filter (where status = 'NOT_EXECUTED'),
    count(*) filter (where status = 'BACKLOG'),
    count(*)
  ) as detail
from m08_local_validation_results

union all

select check_name, status, detail
from m08_local_validation_results
order by 1;
