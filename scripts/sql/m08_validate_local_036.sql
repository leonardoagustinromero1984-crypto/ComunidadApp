-- =============================================================================
-- LeoVer M08 Etapa 3C — validación LOCAL migración 036
-- RPCs de compatibilidad de repositorios (profile/health/context/list).
-- Solo lectura salvo pruebas mutantes con cleanup explícito.
-- NO USAR EN PRODUCCIÓN / STAGING remoto.
-- Sin credenciales, tokens, URLs, project refs ni modo linked remoto.
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

-- ---------------------------------------------------------------------------
-- 1–3) Historial de migraciones 001–036
-- ---------------------------------------------------------------------------
do $$
declare
  v_count int;
  v_max text;
  v_dupes int;
  v_missing int := 0;
  v_036 int;
  v_037 int;
  i int;
  v_exp text;
  v_ok boolean;
begin
  select count(*) into v_count from supabase_migrations.schema_migrations;
  select coalesce(max(version), '<none>') into v_max from supabase_migrations.schema_migrations;

  select count(*) into v_dupes
  from (
    select version from supabase_migrations.schema_migrations group by version having count(*) > 1
  ) d;

  for i in 1..36 loop
    v_exp := lpad(i::text, 3, '0');
    if not exists (
      select 1 from supabase_migrations.schema_migrations where version = v_exp
    ) then
      v_missing := v_missing + 1;
    end if;
  end loop;

  v_ok := (v_count = 36 and v_max = '036' and v_dupes = 0 and v_missing = 0);
  perform pg_temp.m08_pf(
    'migration_history_001_036',
    v_ok,
    format('count=%s max=%s dupes=%s missing=%s', v_count, v_max, v_dupes, v_missing)
  );

  select count(*) into v_036 from supabase_migrations.schema_migrations where version = '036';
  perform pg_temp.m08_pf('migration_036_unique', v_036 = 1, format('count_036=%s', v_036));

  select count(*) into v_037
  from supabase_migrations.schema_migrations
  where version = '037' or version like '037%';
  perform pg_temp.m08_pf('migration_037_absent', v_037 = 0, format('count_037=%s', v_037));
end;
$$;

-- ---------------------------------------------------------------------------
-- 4–5) RPCs presentes + firmas
-- ---------------------------------------------------------------------------
do $$
declare
  v_missing int := 0;
  r text;
  v_sig_ok boolean := true;
  v_detail text := '';
  v_args text;
begin
  foreach r in array array[
    'm08_update_pet_profile',
    'm08_update_pet_health',
    'm08_get_pet_access_context',
    'm08_list_accessible_pets'
  ]
  loop
    if not exists (
      select 1 from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = r
    ) then
      v_missing := v_missing + 1;
    end if;
  end loop;
  perform pg_temp.m08_pf('rpc_four_present', v_missing = 0, format('missing=%s', v_missing));

  select pg_get_function_identity_arguments(p.oid) into v_args
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm08_update_pet_profile'
  limit 1;
  if coalesce(v_args, '') <> 'p_pet_id uuid, p_name text, p_species text, p_breed text, p_sex text, p_size text, p_description text, p_age_years integer, p_age_months integer, p_color text, p_microchip_id text' then
    v_sig_ok := false;
    v_detail := v_detail || 'profile=[' || coalesce(v_args, '<none>') || '] ';
  end if;

  select pg_get_function_identity_arguments(p.oid) into v_args
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm08_update_pet_health'
  limit 1;
  if coalesce(v_args, '') <> 'p_pet_id uuid, p_vaccinations jsonb, p_reminders jsonb, p_last_deworming text, p_deworming_product text, p_last_flea_treatment text, p_flea_treatment_product text, p_sterilized text, p_last_vet_visit text, p_health_notes text, p_weight_kg real' then
    v_sig_ok := false;
    v_detail := v_detail || 'health=[' || coalesce(v_args, '<none>') || '] ';
  end if;

  select pg_get_function_identity_arguments(p.oid) into v_args
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm08_get_pet_access_context'
  limit 1;
  if coalesce(v_args, '') <> 'p_pet_id uuid' then
    v_sig_ok := false;
    v_detail := v_detail || 'context=[' || coalesce(v_args, '<none>') || '] ';
  end if;

  select pg_get_function_identity_arguments(p.oid) into v_args
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm08_list_accessible_pets'
  limit 1;
  if coalesce(v_args, '') <> 'p_status text' then
    v_sig_ok := false;
    v_detail := v_detail || 'list=[' || coalesce(v_args, '<none>') || '] ';
  end if;

  perform pg_temp.m08_pf('rpc_signatures_ok', v_sig_ok, nullif(trim(v_detail), ''));
end;
$$;

-- ---------------------------------------------------------------------------
-- 6–8) Grants PUBLIC / anon / authenticated
-- ---------------------------------------------------------------------------
do $$
declare
  v_pub int;
  v_anon int;
  v_auth int;
  v_names text[] := array[
    'm08_update_pet_profile',
    'm08_update_pet_health',
    'm08_get_pet_access_context',
    'm08_list_accessible_pets'
  ];
begin
  select count(*) into v_pub
  from information_schema.routine_privileges
  where routine_schema = 'public'
    and routine_name = any (v_names)
    and grantee = 'PUBLIC'
    and privilege_type = 'EXECUTE';
  perform pg_temp.m08_pf('grants_public_execute_false', v_pub = 0, format('public_exec=%s', v_pub));

  select count(*) into v_anon
  from information_schema.routine_privileges
  where routine_schema = 'public'
    and routine_name = any (v_names)
    and grantee = 'anon'
    and privilege_type = 'EXECUTE';
  perform pg_temp.m08_pf('grants_anon_execute_false', v_anon = 0, format('anon_exec=%s', v_anon));

  select count(distinct routine_name) into v_auth
  from information_schema.routine_privileges
  where routine_schema = 'public'
    and routine_name = any (v_names)
    and grantee = 'authenticated'
    and privilege_type = 'EXECUTE';
  perform pg_temp.m08_pf('grants_authenticated_execute_true', v_auth = 4, format('auth_exec=%s', v_auth));
end;
$$;

-- ---------------------------------------------------------------------------
-- 9–10) search_path + SECURITY DEFINER
-- ---------------------------------------------------------------------------
do $$
declare
  v_bad_sp int := 0;
  v_bad_def int := 0;
  r text;
  v_names text[] := array[
    'm08_update_pet_profile',
    'm08_update_pet_health',
    'm08_get_pet_access_context',
    'm08_list_accessible_pets',
    '_m08_actor_relation_code',
    '_m08_actor_effective_capabilities'
  ];
  v_rpc text[] := array[
    'm08_update_pet_profile',
    'm08_update_pet_health',
    'm08_get_pet_access_context',
    'm08_list_accessible_pets'
  ];
begin
  foreach r in array v_names
  loop
    if not exists (
      select 1
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public'
        and p.proname = r
        and exists (
          select 1
          from unnest(coalesce(p.proconfig, array[]::text[])) cfg
          where cfg like 'search_path=%'
        )
    ) then
      v_bad_sp := v_bad_sp + 1;
    end if;
  end loop;
  perform pg_temp.m08_pf('search_path_set', v_bad_sp = 0, format('missing_sp=%s', v_bad_sp));

  foreach r in array v_rpc
  loop
    if not exists (
      select 1
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = r and p.prosecdef
    ) then
      v_bad_def := v_bad_def + 1;
    end if;
  end loop;
  perform pg_temp.m08_pf('security_definer_mode', v_bad_def = 0, format('missing_definer=%s', v_bad_def));
end;
$$;

-- ---------------------------------------------------------------------------
-- 11–14) Privacidad pública / CRUD directo / helpers / 035 conceptual
-- ---------------------------------------------------------------------------
do $$
begin
  perform pg_temp.m08_pf(
    'list_public_profile_pets_absent',
    not exists (
      select 1 from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = 'm08_list_public_profile_pets'
    ),
    ''
  );

  perform pg_temp.m08_pf(
    'pets_direct_insert_revoked',
    not has_table_privilege('authenticated', 'public.pets', 'INSERT'),
    ''
  );
  perform pg_temp.m08_pf(
    'pets_direct_update_revoked',
    not has_table_privilege('authenticated', 'public.pets', 'UPDATE'),
    ''
  );
  perform pg_temp.m08_pf(
    'pets_direct_delete_revoked',
    not has_table_privilege('authenticated', 'public.pets', 'DELETE'),
    ''
  );

  perform pg_temp.m08_pf(
    'helpers_authenticated_execute_false',
    (
      select count(*)
      from information_schema.routine_privileges
      where routine_schema = 'public'
        and routine_name in ('_m08_actor_relation_code', '_m08_actor_effective_capabilities')
        and grantee in ('authenticated', 'anon', 'PUBLIC')
        and privilege_type = 'EXECUTE'
    ) = 0,
    ''
  );

  perform pg_temp.m08_pf(
    'm08_create_pet_with_principal_present',
    exists (
      select 1 from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = 'm08_create_pet_with_principal'
    ),
    '035 conceptual: create RPC intact'
  );

  perform pg_temp.m08_pf(
    'pets_no_birth_date',
    not exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'pets' and column_name = 'birth_date'
    ),
    '036 must not invent birth_date'
  );

  perform pg_temp.m08_pf(
    'pets_columns_035_intact',
    exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='status')
    and exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='microchip_normalized')
    and exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='avatar_file_asset_id')
    and exists (select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='owner_id' and is_nullable='YES'),
    'status/microchip_normalized/avatar/owner_id nullable'
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 15+) Suite mutante JWT (cleanup al final; sin residuo)
-- ---------------------------------------------------------------------------
do $$
declare
  v_principal uuid := gen_random_uuid();
  v_co uuid := gen_random_uuid();
  v_auth_upd uuid := gen_random_uuid();
  v_auth_health uuid := gen_random_uuid();
  v_auth_read uuid := gen_random_uuid();
  v_stranger uuid := gen_random_uuid();
  v_staff uuid := gen_random_uuid();
  v_all uuid[];

  v_pet uuid;
  v_pet_arch uuid;
  v_pet_dec uuid;
  v_pet_chip uuid;
  v_pet_org uuid;
  v_org uuid;

  v_owner_before uuid;
  v_status_before text;
  v_avatar_before uuid;
  v_photo_before text;
  v_health_notes text;
  v_weight real;
  v_vacc jsonb;
  v_ster text;

  v_ctx record;
  v_caps text[];
  v_list_ids uuid[];
  v_admin_role uuid;
begin
  v_all := array[v_principal, v_co, v_auth_upd, v_auth_health, v_auth_read, v_stranger, v_staff];

  -- auth.users → trigger crea public.users
  insert into auth.users (
    id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) values
    (v_principal, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_036_principal@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 036 Principal"}'::jsonb, now(), now()),
    (v_co, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_036_co@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 036 Co"}'::jsonb, now(), now()),
    (v_auth_upd, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_036_auth_upd@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 036 AuthUpd"}'::jsonb, now(), now()),
    (v_auth_health, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_036_auth_health@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 036 AuthHealth"}'::jsonb, now(), now()),
    (v_auth_read, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_036_auth_read@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 036 AuthRead"}'::jsonb, now(), now()),
    (v_stranger, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_036_stranger@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 036 Stranger"}'::jsonb, now(), now()),
    (v_staff, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
     'm08_036_staff@example.local', crypt('x', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}'::jsonb,
     '{"name":"M08 036 Staff"}'::jsonb, now(), now());

  update public.users set email = 'm08_036_principal@example.local', name = 'M08 036 Principal', account_status = 'ACTIVE' where id = v_principal;
  update public.users set email = 'm08_036_co@example.local', name = 'M08 036 Co', account_status = 'ACTIVE' where id = v_co;
  update public.users set email = 'm08_036_auth_upd@example.local', name = 'M08 036 AuthUpd', account_status = 'ACTIVE' where id = v_auth_upd;
  update public.users set email = 'm08_036_auth_health@example.local', name = 'M08 036 AuthHealth', account_status = 'ACTIVE' where id = v_auth_health;
  update public.users set email = 'm08_036_auth_read@example.local', name = 'M08 036 AuthRead', account_status = 'ACTIVE' where id = v_auth_read;
  update public.users set email = 'm08_036_stranger@example.local', name = 'M08 036 Stranger', account_status = 'ACTIVE' where id = v_stranger;
  update public.users set email = 'm08_036_staff@example.local', name = 'M08 036 Staff', account_status = 'ACTIVE' where id = v_staff;

  if (select count(*) from public.users where id = any (v_all)) <> 7 then
    raise exception 'M08_VALIDATE_036_FIXTURE: public.users missing after auth insert';
  end if;

  -- STAFF = ADMIN (pet.read / pet.view_history)
  select id into v_admin_role from public.platform_roles where code = 'ADMIN' limit 1;
  if v_admin_role is null then
    raise exception 'M08_VALIDATE_036_FIXTURE: ADMIN role missing';
  end if;
  insert into public.user_role_assignments (user_id, role_id, assigned_by)
  values (v_staff, v_admin_role, v_principal);

  -- Org mínima + membership OWNER
  insert into public.organizations (
    slug, display_name, type, status, verification_status, created_by
  ) values (
    'm08-val-org-036', 'M08 Val Org 036', 'SHELTER', 'ACTIVE', 'NOT_REQUESTED', v_principal
  ) returning id into v_org;

  insert into public.organization_memberships (
    organization_id, user_id, role_code, status, joined_at
  ) values (
    v_org, v_principal, 'OWNER', 'ACTIVE', timezone('utc', now())
  );

  -- JWT principal
  perform set_config('request.jwt.claim.sub', v_principal::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);

  select (public.m08_create_pet_with_principal('Val036', 'DOG', 'M', 'M', '036 test', null, 'chip-036-aa')).id
    into v_pet;
  select (public.m08_create_pet_with_principal('Val036Arch', 'DOG', 'F', 'S', 'arch', null, null)).id
    into v_pet_arch;
  select (public.m08_create_pet_with_principal('Val036Dec', 'CAT', 'M', 'S', 'dec', null, null)).id
    into v_pet_dec;
  select (public.m08_create_pet_with_principal('Val036Chip', 'DOG', 'M', 'L', 'chip2', null, null)).id
    into v_pet_chip;
  select (public.m08_create_pet_with_principal('Val036Org', 'DOG', 'F', 'M', 'org pet', v_org, null)).id
    into v_pet_org;

  perform pg_temp.m08_pf('org_principal_create', v_pet_org is not null, format('pet_org=%s', v_pet_org));
  perform pg_temp.m08_pf(
    'org_pet_owner_id_null',
    exists (select 1 from public.pets where id = v_pet_org and owner_id is null and status = 'ACTIVE'),
    ''
  );

  -- Seed health + avatar markers (superuser) for preservation checks
  update public.pets
  set
    health_notes = 'KEEP_HEALTH',
    weight_kg = 7.25,
    vaccinations = '[{"v":"seed"}]'::jsonb,
    sterilized = 'YES',
    photo_url = 'https://example.local/036.jpg',
    avatar_file_asset_id = null
  where id = v_pet;

  select owner_id, status, avatar_file_asset_id, photo_url, health_notes, weight_kg, vaccinations, sterilized
    into v_owner_before, v_status_before, v_avatar_before, v_photo_before, v_health_notes, v_weight, v_vacc, v_ster
  from public.pets where id = v_pet;

  -- 15) Principal update profile
  begin
    perform public.m08_update_pet_profile(
      v_pet, 'Val036Renamed', 'DOG', 'Mestizo', 'F', 'L', 'desc-036', 2, 3, 'negro', 'chip-036-bb'
    );
    perform pg_temp.m08_pf('profile_update_principal', true, '');
  exception when others then
    perform pg_temp.m08_pf('profile_update_principal', false, sqlerrm);
  end;

  perform pg_temp.m08_pf(
    'microchip_normalized_on_profile',
    exists (select 1 from public.pets where id = v_pet and microchip_normalized = 'CHIP036BB' and microchip_id = 'chip-036-bb'),
    ''
  );

  perform pg_temp.m08_pf(
    'profile_preserves_owner_status_avatar',
    exists (
      select 1 from public.pets
      where id = v_pet
        and owner_id is not distinct from v_owner_before
        and status = v_status_before
        and avatar_file_asset_id is not distinct from v_avatar_before
        and photo_url is not distinct from v_photo_before
    ),
    ''
  );

  perform pg_temp.m08_pf(
    'profile_preserves_health_fields',
    exists (
      select 1 from public.pets
      where id = v_pet
        and health_notes is not distinct from v_health_notes
        and weight_kg is not distinct from v_weight
        and vaccinations = v_vacc
        and sterilized is not distinct from v_ster
    ),
    ''
  );

  -- empty microchip → NULL
  perform public.m08_update_pet_profile(
    v_pet, 'Val036Renamed', 'DOG', 'Mestizo', 'F', 'L', 'desc-036', 2, 3, 'negro', ''
  );
  perform pg_temp.m08_pf(
    'microchip_empty_to_null',
    exists (select 1 from public.pets where id = v_pet and microchip_id is null and microchip_normalized is null),
    ''
  );

  -- restore unique chip on main pet for later conflict
  perform public.m08_update_pet_profile(
    v_pet, 'Val036Renamed', 'DOG', 'Mestizo', 'F', 'L', 'desc-036', 2, 3, 'negro', 'CHIP-036-UNIQUE'
  );

  -- 16) Authorized with only pet.update
  perform public.m08_grant_pet_authorization(v_pet, v_auth_upd, array['pet.update']::text[], null);
  perform set_config('request.jwt.claim.sub', v_auth_upd::text, true);
  begin
    perform public.m08_update_pet_profile(
      v_pet, 'ByAuthUpd', 'DOG', 'X', 'M', 'M', 'auth-upd', 1, 0, 'gris', 'CHIP-036-UNIQUE'
    );
    perform pg_temp.m08_pf('profile_update_authorized_pet_update', true, '');
  exception when others then
    perform pg_temp.m08_pf('profile_update_authorized_pet_update', false, sqlerrm);
  end;

  -- pet.update alone must NOT allow health
  begin
    perform public.m08_update_pet_health(
      v_pet, '[]'::jsonb, '[]'::jsonb, null, null, null, null, null, null, 'should-fail', 1.0
    );
    perform pg_temp.m08_vr('pet_update_alone_no_health', 'FAIL', 'health update unexpectedly allowed');
  exception when others then
    perform pg_temp.m08_pf('pet_update_alone_no_health', sqlerrm ilike '%FORBIDDEN%', sqlerrm);
  end;

  -- 17) Stranger FORBIDDEN
  perform set_config('request.jwt.claim.sub', v_stranger::text, true);
  begin
    perform public.m08_update_pet_profile(
      v_pet, 'Hack', 'DOG', null, 'M', 'M', '', 0, 0, null, null
    );
    perform pg_temp.m08_vr('profile_update_stranger_forbidden', 'FAIL', 'stranger update allowed');
  exception when others then
    perform pg_temp.m08_pf('profile_update_stranger_forbidden', sqlerrm ilike '%FORBIDDEN%', sqlerrm);
  end;

  -- 18) ARCHIVED / DECEASED → PET_NOT_ACTIVE
  perform set_config('request.jwt.claim.sub', v_principal::text, true);
  perform public.m08_archive_pet(v_pet_arch, '036-test');
  begin
    perform public.m08_update_pet_profile(
      v_pet_arch, 'X', 'DOG', null, 'M', 'M', '', 0, 0, null, null
    );
    perform pg_temp.m08_vr('profile_update_archived_blocked', 'FAIL', 'archived update allowed');
  exception when others then
    perform pg_temp.m08_pf('profile_update_archived_blocked', sqlerrm ilike '%PET_NOT_ACTIVE%', sqlerrm);
  end;

  perform public.m08_mark_pet_deceased(v_pet_dec, '036-test');
  begin
    perform public.m08_update_pet_profile(
      v_pet_dec, 'X', 'DOG', null, 'M', 'M', '', 0, 0, null, null
    );
    perform pg_temp.m08_vr('profile_update_deceased_blocked', 'FAIL', 'deceased update allowed');
  exception when others then
    perform pg_temp.m08_pf('profile_update_deceased_blocked', sqlerrm ilike '%PET_NOT_ACTIVE%', sqlerrm);
  end;

  -- 19) Microchip ACTIVE conflict
  begin
    perform public.m08_update_pet_profile(
      v_pet_chip, 'Val036Chip', 'DOG', null, 'M', 'L', '', 0, 0, null, 'CHIP-036-UNIQUE'
    );
    perform pg_temp.m08_vr('microchip_active_conflict', 'FAIL', 'duplicate ACTIVE chip allowed');
  exception when others then
    perform pg_temp.m08_pf(
      'microchip_active_conflict',
      sqlerrm ilike '%PET_MICROCHIP_ACTIVE_CONFLICT%',
      sqlerrm
    );
  end;

  -- 20) Health: manage_health PASS; without FORBIDDEN; manage_health alone no profile
  perform public.m08_grant_pet_authorization(v_pet, v_auth_health, array['pet.manage_health']::text[], null);
  perform set_config('request.jwt.claim.sub', v_auth_health::text, true);
  begin
    perform public.m08_update_pet_health(
      v_pet,
      '[{"v":"ok"}]'::jsonb,
      '[]'::jsonb,
      '2026-01-01', 'prod', null, null, 'NO', null, 'health-ok', 9.5
    );
    perform pg_temp.m08_pf('health_update_with_manage_health', true, '');
  exception when others then
    perform pg_temp.m08_pf('health_update_with_manage_health', false, sqlerrm);
  end;

  begin
    perform public.m08_update_pet_profile(
      v_pet, 'ShouldFail', 'DOG', null, 'M', 'M', '', 0, 0, null, null
    );
    perform pg_temp.m08_vr('manage_health_alone_no_profile', 'FAIL', 'profile update allowed with only manage_health');
  exception when others then
    perform pg_temp.m08_pf('manage_health_alone_no_profile', sqlerrm ilike '%FORBIDDEN%', sqlerrm);
  end;

  perform set_config('request.jwt.claim.sub', v_stranger::text, true);
  begin
    perform public.m08_update_pet_health(
      v_pet, '[]'::jsonb, '[]'::jsonb, null, null, null, null, null, null, 'x', null
    );
    perform pg_temp.m08_vr('health_update_without_capability_forbidden', 'FAIL', 'stranger health allowed');
  exception when others then
    perform pg_temp.m08_pf('health_update_without_capability_forbidden', sqlerrm ilike '%FORBIDDEN%', sqlerrm);
  end;

  -- 21) Relations + list fixtures
  perform set_config('request.jwt.claim.sub', v_principal::text, true);
  perform public.m08_assign_pet_responsibility(v_pet, 'CO_RESPONSIBLE', v_co, null, null, null);
  perform public.m08_grant_pet_authorization(v_pet, v_auth_read, array['pet.read']::text[], null);

  -- PRINCIPAL
  select * into v_ctx from public.m08_get_pet_access_context(v_pet);
  perform pg_temp.m08_pf(
    'context_relation_principal',
    v_ctx.relation_code = 'PRINCIPAL'
      and v_ctx.principal_person_id = v_principal
      and v_ctx.can_read
      and v_ctx.can_update,
    format('rel=%s', v_ctx.relation_code)
  );
  v_caps := v_ctx.capabilities;
  perform pg_temp.m08_pf(
    'capabilities_no_duplicates',
    coalesce(cardinality(v_caps), 0) = (
      select count(distinct x) from unnest(coalesce(v_caps, array[]::text[])) x
    ),
    format('caps=%s', coalesce(cardinality(v_caps), 0))
  );

  -- CO_RESPONSIBLE
  perform set_config('request.jwt.claim.sub', v_co::text, true);
  select * into v_ctx from public.m08_get_pet_access_context(v_pet);
  perform pg_temp.m08_pf(
    'context_relation_co_responsible',
    v_ctx.relation_code = 'CO_RESPONSIBLE' and v_ctx.can_read and v_ctx.can_update,
    format('rel=%s', v_ctx.relation_code)
  );

  -- AUTHORIZED (read grant)
  perform set_config('request.jwt.claim.sub', v_auth_read::text, true);
  select * into v_ctx from public.m08_get_pet_access_context(v_pet);
  perform pg_temp.m08_pf(
    'context_relation_authorized',
    v_ctx.relation_code = 'AUTHORIZED' and v_ctx.can_read and not v_ctx.can_update,
    format('rel=%s', v_ctx.relation_code)
  );

  -- STAFF
  perform set_config('request.jwt.claim.sub', v_staff::text, true);
  select * into v_ctx from public.m08_get_pet_access_context(v_pet);
  perform pg_temp.m08_pf(
    'context_relation_staff',
    v_ctx.relation_code = 'STAFF' and v_ctx.can_read,
    format('rel=%s', v_ctx.relation_code)
  );

  -- NONE stranger — no principal leak
  perform set_config('request.jwt.claim.sub', v_stranger::text, true);
  select * into v_ctx from public.m08_get_pet_access_context(v_pet);
  perform pg_temp.m08_pf(
    'context_relation_none_no_leak',
    v_ctx.relation_code = 'NONE'
      and v_ctx.principal_person_id is null
      and v_ctx.principal_organization_id is null
      and coalesce(cardinality(v_ctx.capabilities), 0) = 0
      and not v_ctx.can_read,
    format('rel=%s caps=%s', v_ctx.relation_code, coalesce(cardinality(v_ctx.capabilities), 0))
  );

  -- List: principal / co / authorized-read include; stranger exclude
  perform set_config('request.jwt.claim.sub', v_principal::text, true);
  select coalesce(array_agg(id), array[]::uuid[]) into v_list_ids
  from public.m08_list_accessible_pets(null);
  perform pg_temp.m08_pf('list_includes_principal', v_pet = any (v_list_ids), '');

  perform set_config('request.jwt.claim.sub', v_co::text, true);
  select coalesce(array_agg(id), array[]::uuid[]) into v_list_ids
  from public.m08_list_accessible_pets(null);
  perform pg_temp.m08_pf('list_includes_co_responsible', v_pet = any (v_list_ids), '');

  perform set_config('request.jwt.claim.sub', v_auth_read::text, true);
  select coalesce(array_agg(id), array[]::uuid[]) into v_list_ids
  from public.m08_list_accessible_pets(null);
  perform pg_temp.m08_pf('list_includes_authorized_read', v_pet = any (v_list_ids), '');

  perform set_config('request.jwt.claim.sub', v_stranger::text, true);
  select coalesce(array_agg(id), array[]::uuid[]) into v_list_ids
  from public.m08_list_accessible_pets(null);
  perform pg_temp.m08_pf('list_excludes_stranger', not (v_pet = any (v_list_ids)), '');

  -- Org pet: list + context with owner_id null
  perform set_config('request.jwt.claim.sub', v_principal::text, true);
  select * into v_ctx from public.m08_get_pet_access_context(v_pet_org);
  select coalesce(array_agg(id), array[]::uuid[]) into v_list_ids
  from public.m08_list_accessible_pets('ACTIVE');
  perform pg_temp.m08_pf(
    'org_list_context_ok',
    v_ctx.relation_code = 'PRINCIPAL'
      and v_ctx.principal_organization_id = v_org
      and v_ctx.principal_person_id is null
      and v_pet_org = any (v_list_ids)
      and exists (select 1 from public.pets where id = v_pet_org and owner_id is null),
    format('rel=%s org=%s', v_ctx.relation_code, v_ctx.principal_organization_id)
  );

  -- Direct CRUD under role authenticated (runtime deny)
  begin
    execute 'set local role authenticated';
    insert into public.pets (owner_id, name, species, sex, size, description, age_years, age_months)
    values (v_principal, 'HackIns', 'DOG', 'M', 'M', '', 0, 0);
    perform pg_temp.m08_vr('pets_direct_insert_runtime_denied', 'FAIL', 'insert succeeded');
  exception when others then
    perform pg_temp.m08_pf(
      'pets_direct_insert_runtime_denied',
      sqlerrm ilike '%permission denied%',
      sqlerrm
    );
  end;
  begin execute 'reset role'; exception when others then null; end;

  perform set_config('request.jwt.claim.sub', v_principal::text, true);
  begin
    execute 'set local role authenticated';
    update public.pets set name = 'HackUpd' where id = v_pet;
    perform pg_temp.m08_vr('pets_direct_update_runtime_denied', 'FAIL', 'update succeeded');
  exception when others then
    perform pg_temp.m08_pf(
      'pets_direct_update_runtime_denied',
      sqlerrm ilike '%permission denied%',
      sqlerrm
    );
  end;
  begin execute 'reset role'; exception when others then null; end;

  perform set_config('request.jwt.claim.sub', v_principal::text, true);
  begin
    execute 'set local role authenticated';
    delete from public.pets where id = v_pet;
    perform pg_temp.m08_vr('pets_direct_delete_runtime_denied', 'FAIL', 'delete succeeded');
  exception when others then
    perform pg_temp.m08_pf(
      'pets_direct_delete_runtime_denied',
      sqlerrm ilike '%permission denied%',
      sqlerrm
    );
  end;
  begin execute 'reset role'; exception when others then null; end;

  -- Cleanup fixtures (FK order)
  delete from public.pet_status_history
  where pet_id in (v_pet, v_pet_arch, v_pet_dec, v_pet_chip, v_pet_org);
  delete from public.pet_transfers
  where pet_id in (v_pet, v_pet_arch, v_pet_dec, v_pet_chip, v_pet_org);
  delete from public.pet_authorizations
  where pet_id in (v_pet, v_pet_arch, v_pet_dec, v_pet_chip, v_pet_org);
  delete from public.pet_responsibilities
  where pet_id in (v_pet, v_pet_arch, v_pet_dec, v_pet_chip, v_pet_org);
  delete from public.pets
  where id in (v_pet, v_pet_arch, v_pet_dec, v_pet_chip, v_pet_org);

  delete from public.organization_memberships where organization_id = v_org;
  delete from public.organization_audit_log where organization_id = v_org;
  delete from public.organizations where id = v_org;

  delete from public.user_role_assignments where user_id = any (v_all);
  delete from public.users where id = any (v_all);
  delete from auth.users where id = any (v_all);

  perform pg_temp.m08_vr('mutating_suite_cleaned', 'PASS', 'fixtures deleted');
exception
  when others then
    perform pg_temp.m08_vr('mutating_suite_error', 'FAIL', sqlerrm);
end;
$$;

-- Emisión runtime m08.pet.updated diferida en 036 (deuda M07) — no inventar PASS.
select pg_temp.m08_vr(
  'm07_pet_updated_runtime_emission',
  'BACKLOG',
  'Emisión runtime m08.pet.updated diferida en 036; catálogo 035 presente'
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
