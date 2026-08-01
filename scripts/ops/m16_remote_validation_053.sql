-- M16 migración 053 — validación remota staging (casos 01–50)
-- Ejecutar: supabase db query --linked -f scripts/ops/m16_remote_validation_053.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m16_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m16_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m16_val_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m16_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

-- UUIDs fijos de prueba M16
do $setup$
declare
  v_mgr uuid := 'a0000000-0000-4000-8000-000000000001';
  v_out uuid := 'a0000000-0000-4000-8000-000000000002';
  v_mod uuid := 'a0000000-0000-4000-8000-000000000003';
  v_org uuid := 'b0000000-0000-4000-8000-000000000001';
  v_org2 uuid := 'b0000000-0000-4000-8000-000000000003';
  v_org3 uuid := 'b0000000-0000-4000-8000-000000000004';
  v_bad_org uuid := 'b0000000-0000-4000-8000-000000000002';
  v_s_pub uuid := 'c0000000-0000-4000-8000-000000000001';
  v_s_unpub uuid := 'c0000000-0000-4000-8000-000000000002';
  v_s_closed uuid := 'c0000000-0000-4000-8000-000000000003';
  v_s_work uuid := 'c0000000-0000-4000-8000-000000000004';
  v_owner_role uuid;
  v_admin_platform uuid;
  v_req_id uuid;
  v_json jsonb;
  v_cnt int;
  v_err text;
begin
  -- Permisos shelter.* (prerrequisito 042 no registrado en historial remoto)
  insert into public.organization_permissions (code, description) values
    ('shelter.view', 'Ver operación de refugio'),
    ('shelter.manage', 'Gestionar perfil y capacidad del refugio')
  on conflict (code) do nothing;

  insert into public.organization_role_permissions (role_id, permission_id)
  select r.id, p.id
  from public.organization_roles r
  cross join public.organization_permissions p
  where r.code in ('OWNER', 'ADMIN', 'MANAGER')
    and p.code in ('shelter.view', 'shelter.manage')
  on conflict do nothing;

  -- Usuarios auth + public
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_mgr, 'authenticated', 'authenticated',
     'm16-mgr@test.local', crypt('m16-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm16-out@test.local', crypt('m16-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_mod, 'authenticated', 'authenticated',
     'm16-mod@test.local', crypt('m16-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, account_type, email_verified, account_status)
  values
    (v_mgr, 'm16-mgr@test.local', 'M16 Manager', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm16-out@test.local', 'M16 Outsider', 'PERSON', true, 'ACTIVE'),
    (v_mod, 'm16-mod@test.local', 'M16 Moderator', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  select id into v_admin_platform from public.platform_roles where code = 'ADMIN' limit 1;
  if v_admin_platform is not null then
    insert into public.user_role_assignments (user_id, role_id, assigned_by)
    values (v_mod, v_admin_platform, v_mod)
    on conflict do nothing;
  end if;

  insert into public.organizations (id, slug, display_name, type, status, created_by)
  values
    (v_org, 'm16-val-shelter', 'M16 Val Shelter Org', 'SHELTER', 'ACTIVE', v_mgr),
    (v_org2, 'm16-val-unpub', 'M16 Val Unpub Org', 'SHELTER', 'ACTIVE', v_mgr),
    (v_org3, 'm16-val-closed', 'M16 Val Closed Org', 'SHELTER', 'ACTIVE', v_mgr),
    (v_bad_org, 'm16-val-vet', 'M16 Val Vet Org', 'VETERINARY_CLINIC', 'ACTIVE', v_out)
  on conflict (id) do nothing;

  insert into public.organization_memberships (organization_id, user_id, role_code, status, joined_at)
  values
    (v_org, v_mgr, 'OWNER', 'ACTIVE', now()),
    (v_org2, v_mgr, 'OWNER', 'ACTIVE', now()),
    (v_org3, v_mgr, 'OWNER', 'ACTIVE', now())
  on conflict do nothing;

  -- Perfiles semilla para pruebas públicas (1:1 org)
  insert into public.m16_shelter_profiles (
    id, organization_id, display_name, public_zone_text, total_capacity,
    operational_status, publication_status, verification_status, created_by, updated_by
  ) values
    (v_s_pub, v_org, 'Refugio Publicado Activo', 'Zona Norte', 20,
     'ACTIVE', 'PUBLISHED', 'UNVERIFIED', v_mgr, v_mgr),
    (v_s_unpub, v_org2, 'Refugio No Publicado', 'Zona Sur', 10,
     'ACTIVE', 'UNPUBLISHED', 'UNVERIFIED', v_mgr, v_mgr),
    (v_s_closed, v_org3, 'Refugio Cerrado', 'Zona Oeste', 5,
     'PERMANENTLY_CLOSED', 'PUBLISHED', 'UNVERIFIED', v_mgr, v_mgr)
  on conflict (id) do nothing;

  insert into public.m16_shelter_public_contacts (shelter_profile_id, channel_type, value, is_public)
  select v_s_pub, 'WEBSITE', 'https://example.org', true
  where not exists (
    select 1 from public.m16_shelter_public_contacts
    where shelter_profile_id = v_s_pub and channel_type = 'WEBSITE'
  );
  insert into public.m16_shelter_public_contacts (shelter_profile_id, channel_type, value, is_public)
  select v_s_pub, 'INSTITUTIONAL_PHONE', '+5491100000000', false
  where not exists (
    select 1 from public.m16_shelter_public_contacts
    where shelter_profile_id = v_s_pub and channel_type = 'INSTITUTIONAL_PHONE'
  );

  -- ESTRUCTURA 01–08
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema='public' and table_name like 'm16_shelter%';
  perform pg_temp.m16_val(1, 'Cinco tablas M16', v_cnt = 5);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema='public' and table_name='m16_shelter_profiles'
    and constraint_type='FOREIGN KEY' and constraint_name like '%organization%';
  perform pg_temp.m16_val(2, 'FK organization_id', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname='public' and tablename='m16_shelter_profiles'
    and indexdef ilike '%unique%organization_id%';
  perform pg_temp.m16_val(3, 'UNIQUE organization_id', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name=tc.constraint_name
  where tc.table_schema='public' and tc.table_name='m16_shelter_opening_periods'
    and tc.constraint_type='FOREIGN KEY' and kcu.column_name='shelter_profile_id';
  perform pg_temp.m16_val(4, 'FKs hijas al perfil', v_cnt >= 1);

  begin
    insert into public.m16_shelter_profiles (
      organization_id, display_name, public_zone_text, total_capacity,
      current_occupancy, reserved_count
    ) values (v_bad_org, 'X', 'Z', 5, 3, 3);
    perform pg_temp.m16_val(5, 'CHECK capacidad', false, 'debió fallar');
  exception when check_violation then
    perform pg_temp.m16_val(5, 'CHECK capacidad', true);
  end;

  begin
    insert into public.m16_shelter_opening_periods (shelter_profile_id, day_of_week, closed, open_time, close_time)
    values (v_s_pub, 1, false, '18:00', '09:00');
    perform pg_temp.m16_val(6, 'CHECK horarios', false);
  exception when check_violation then
    perform pg_temp.m16_val(6, 'CHECK horarios', true);
  end;

  select count(*)::int into v_cnt from pg_indexes
  where schemaname='public' and tablename like 'm16_%';
  perform pg_temp.m16_val(7, 'Índices M16', v_cnt >= 5);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid=c.relnamespace
  where n.nspname='public' and c.relname like 'm16_shelter%' and c.relrowsecurity;
  perform pg_temp.m16_val(8, 'RLS habilitado', v_cnt = 5);

  -- ANON 09–18
  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema='public' and table_name='m16_shelter_profiles' and grantee='anon';
  perform pg_temp.m16_val(9, 'Anon sin tablas internas', v_cnt = 0);

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    select count(*)::int into v_cnt from public.m16_list_public_shelters();
    perform pg_temp.m16_val(10, 'Anon list_public', v_cnt >= 1);
  exception when others then
    perform pg_temp.m16_val(10, 'Anon list_public', false, SQLERRM);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    v_json := public.m16_get_public_shelter(v_s_pub);
    perform pg_temp.m16_val(11, 'Anon get_public', v_json is not null);
  exception when others then
    perform pg_temp.m16_val(11, 'Anon get_public', false, SQLERRM);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    select count(*)::int into v_cnt from public.m16_list_public_shelters() j
    where (j->>'id')::uuid = v_s_unpub;
    perform pg_temp.m16_val(12, 'UNPUBLISHED oculto', v_cnt = 0);
  exception when others then
    perform pg_temp.m16_val(12, 'UNPUBLISHED oculto', false, SQLERRM);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    select count(*)::int into v_cnt from public.m16_list_public_shelters() j
    where j->>'operational_status' = 'ACTIVE';
    perform pg_temp.m16_val(13, 'ACTIVE publicado visible', v_cnt >= 1);
  exception when others then
    perform pg_temp.m16_val(13, 'ACTIVE publicado visible', false, SQLERRM);
  end;

  -- PAUSED publicado
  update public.m16_shelter_profiles set operational_status='PAUSED'
  where id=v_s_pub and publication_status='PUBLISHED';
  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    select count(*)::int into v_cnt from public.m16_list_public_shelters() j
    where (j->>'id')::uuid = v_s_pub;
    perform pg_temp.m16_val(14, 'PAUSED publicado visible', v_cnt = 1);
  exception when others then
    perform pg_temp.m16_val(14, 'PAUSED publicado visible', false, SQLERRM);
  end;
  update public.m16_shelter_profiles set operational_status='ACTIVE' where id=v_s_pub;

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    select count(*)::int into v_cnt from public.m16_list_public_shelters() j
    where (j->>'id')::uuid = v_s_closed;
    perform pg_temp.m16_val(15, 'PERMANENTLY_CLOSED oculto default', v_cnt = 0);
  exception when others then
    perform pg_temp.m16_val(15, 'PERMANENTLY_CLOSED oculto default', false, SQLERRM);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    select count(*)::int into v_cnt
    from public.m16_list_public_shelters(null,null,null,'PERMANENTLY_CLOSED',false,false);
    perform pg_temp.m16_val(16, 'PERMANENTLY_CLOSED con filtro', v_cnt >= 1);
  exception when others then
    perform pg_temp.m16_val(16, 'PERMANENTLY_CLOSED con filtro', false, SQLERRM);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    v_json := public.m16_get_public_shelter(v_s_pub);
    perform pg_temp.m16_val(17, 'Contactos privados excluidos',
      not exists (
        select 1 from jsonb_array_elements(v_json->'public_contacts') c
        where c->>'value' = '+5491100000000'
      ));
  exception when others then
    perform pg_temp.m16_val(17, 'Contactos privados excluidos', false, SQLERRM);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    v_json := public.m16_get_public_shelter(v_s_pub);
    perform pg_temp.m16_val(18, 'Sin organization_id ni PII',
      (v_json->>'organization_id') is null
      and (v_json->>'internal_notes') is null);
  exception when others then
    perform pg_temp.m16_val(18, 'Sin organization_id ni PII', false, SQLERRM);
  end;

  -- AUTORIZADO 19–39
  perform pg_temp.m16_act_as(v_mgr);
  begin
    v_json := public.m16_get_shelter_profile(v_s_pub);
    perform pg_temp.m16_val(19, 'shelter.view lectura interna', v_json is not null);
  exception when others then
    perform pg_temp.m16_val(19, 'shelter.view lectura interna', false, SQLERRM);
  end;

  begin
    v_json := public.m16_create_shelter_profile(
      v_org, 'Segundo', 'Zona', 15, null, '{}', '{}', false);
    perform pg_temp.m16_val(20, 'Crear perfil', v_json is not null);
    v_s_work := (v_json->>'id')::uuid;
  exception when others then
    perform pg_temp.m16_val(20, 'Crear perfil', false, SQLERRM);
  end;

  begin
    v_json := public.m16_create_shelter_profile(
      v_org, 'Duplicado', 'Zona', 15, null, '{}', '{}', false);
    perform pg_temp.m16_val(21, 'Segunda creación idempotente',
      (v_json->>'id')::uuid = coalesce(v_s_work, v_s_pub));
  exception when others then
    perform pg_temp.m16_val(21, 'Segunda creación idempotente', false, SQLERRM);
  end;

  if v_s_work is null then v_s_work := v_s_pub; end if;

  begin
    v_json := public.m16_update_shelter_public_data(
      v_s_work, 'Refugio Editado', 'Zona Edit', 'Desc', '{}', '{DOG}', null);
    perform pg_temp.m16_val(22, 'Actualizar datos públicos',
      v_json->>'display_name' = 'Refugio Editado');
  exception when others then
    perform pg_temp.m16_val(22, 'Actualizar datos públicos', false, SQLERRM);
  end;

  begin
    v_json := public.m16_update_capacity(v_s_work, 30, 5, 2);
    perform pg_temp.m16_val(23, 'Capacidad válida', (v_json->'capacity'->>'total_capacity')::int = 30);
  exception when others then
    perform pg_temp.m16_val(23, 'Capacidad válida', false, SQLERRM);
  end;

  begin
    perform public.m16_update_capacity(v_s_work, -1, 0, 0);
    perform pg_temp.m16_val(24, 'Capacidad inválida rechazada', false);
  exception when others then
    perform pg_temp.m16_val(24, 'Capacidad inválida rechazada', true);
  end;

  begin
    perform public.m16_update_capacity(v_s_work, 10, 8, 5);
    perform pg_temp.m16_val(25, 'Ocupación > capacidad rechazada', false);
  exception when others then
    perform pg_temp.m16_val(25, 'Ocupación > capacidad rechazada', true);
  end;

  begin
    v_json := public.m16_update_opening_hours(
      v_s_work, 'America/Argentina/Buenos_Aires',
      '[{"day_of_week":1,"closed":false,"open_time":"09:00","close_time":"17:00"}]'::jsonb);
    perform pg_temp.m16_val(26, 'Horarios válidos', jsonb_array_length(v_json->'opening_hours'->'periods') >= 1);
  exception when others then
    perform pg_temp.m16_val(26, 'Horarios válidos', false, SQLERRM);
  end;

  begin
    perform public.m16_update_opening_hours(v_s_work, null,
      '[{"day_of_week":2,"closed":false,"open_time":"18:00","close_time":"09:00"}]'::jsonb);
    perform pg_temp.m16_val(27, 'Horarios inválidos rechazados', false);
  exception when others then
    perform pg_temp.m16_val(27, 'Horarios inválidos rechazados', true);
  end;

  begin
    v_json := public.m16_update_public_contacts(v_s_work,
      '[{"type":"WEBSITE","value":"https://refugio.test","label":"Web"}]'::jsonb);
    perform pg_temp.m16_val(28, 'Contactos públicos válidos',
      jsonb_array_length(v_json->'public_contacts') >= 1);
  exception when others then
    perform pg_temp.m16_val(28, 'Contactos públicos válidos', false, SQLERRM);
  end;

  begin
    perform public.m16_update_public_contacts(v_s_work, '[{"type":"BAD","value":""}]'::jsonb);
    perform pg_temp.m16_val(29, 'Contactos inválidos rechazados', false);
  exception when others then
    perform pg_temp.m16_val(29, 'Contactos inválidos rechazados', true);
  end;

  begin
    v_json := public.m16_update_publication_status(v_s_work, 'PUBLISHED');
    perform pg_temp.m16_val(30, 'Publicar', v_json->>'publication_status' = 'PUBLISHED');
  exception when others then
    perform pg_temp.m16_val(30, 'Publicar', false, SQLERRM);
  end;

  begin
    v_json := public.m16_update_publication_status(v_s_work, 'PUBLISHED');
    perform pg_temp.m16_val(31, 'Repetir publicación idempotente',
      v_json->>'publication_status' = 'PUBLISHED');
  exception when others then
    perform pg_temp.m16_val(31, 'Repetir publicación idempotente', false, SQLERRM);
  end;

  begin
    v_json := public.m16_update_publication_status(v_s_work, 'UNPUBLISHED');
    perform pg_temp.m16_val(32, 'Pausar publicación', v_json->>'publication_status' = 'UNPUBLISHED');
  exception when others then
    perform pg_temp.m16_val(32, 'Pausar publicación', false, SQLERRM);
  end;

  begin
    v_json := public.m16_update_publication_status(v_s_work, 'UNPUBLISHED');
    perform pg_temp.m16_val(33, 'Repetir pausa idempotente',
      v_json->>'publication_status' = 'UNPUBLISHED');
  exception when others then
    perform pg_temp.m16_val(33, 'Repetir pausa idempotente', false, SQLERRM);
  end;

  update public.m16_shelter_profiles set publication_status='PUBLISHED' where id=v_s_work;

  begin
    v_json := public.m16_request_verification(v_s_work);
    perform pg_temp.m16_val(34, 'Solicitar verificación', true);
    perform pg_temp.m16_val(35, 'Solicitud PENDING',
      v_json->>'verification_status' = 'PENDING');
  exception when others then
    perform pg_temp.m16_val(34, 'Solicitar verificación', false, SQLERRM);
    perform pg_temp.m16_val(35, 'Solicitud PENDING', false, SQLERRM);
  end;

  begin
    perform public.m16_decide_shelter_verification(gen_random_uuid(), 'VERIFIED');
    perform pg_temp.m16_val(36, 'Manager no auto VERIFIED / no decide', false);
  exception when others then
    perform pg_temp.m16_val(36, 'Manager no auto VERIFIED / no decide', true);
  end;

  begin
    v_json := public.m16_update_operational_status(v_s_work, 'PERMANENTLY_CLOSED');
    perform pg_temp.m16_val(37, 'Cierre permanente', v_json->>'operational_status' = 'PERMANENTLY_CLOSED');
  exception when others then
    perform pg_temp.m16_val(37, 'Cierre permanente', false, SQLERRM);
  end;

  begin
    v_json := public.m16_update_operational_status(v_s_work, 'PERMANENTLY_CLOSED');
    perform pg_temp.m16_val(38, 'Repetir cierre idempotente',
      v_json->>'operational_status' = 'PERMANENTLY_CLOSED');
  exception when others then
    perform pg_temp.m16_val(38, 'Repetir cierre idempotente', false, SQLERRM);
  end;

  begin
    perform public.m16_update_publication_status(v_s_work, 'PUBLISHED');
    perform pg_temp.m16_val(39, 'No reactivar cerrado', false);
  exception when others then
    perform pg_temp.m16_val(39, 'No reactivar cerrado', SQLERRM like '%M16_STATE_ALREADY_FINAL%');
  end;


  -- NO AUTORIZADO 40–44
  perform pg_temp.m16_act_as(v_out);
  begin
    perform public.m16_get_shelter_profile(v_s_pub);
    perform pg_temp.m16_val(40, 'Ajeno no lee interno', false);
  exception when others then
    perform pg_temp.m16_val(40, 'Ajeno no lee interno', true);
  end;
  begin
    perform public.m16_create_shelter_profile(v_org, 'Hack', 'Z', 10);
    perform pg_temp.m16_val(41, 'Ajeno no crea', false);
  exception when others then
    perform pg_temp.m16_val(41, 'Ajeno no crea', true);
  end;
  begin
    perform public.m16_update_shelter_public_data(v_s_pub, 'X', 'Z');
    perform pg_temp.m16_val(42, 'Ajeno no modifica', false);
  exception when others then
    perform pg_temp.m16_val(42, 'Ajeno no modifica', true);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    perform public.m16_create_shelter_profile(v_org, 'Anon', 'Z', 10);
    perform pg_temp.m16_val(43, 'Anon no muta', false);
  exception when others then
    perform pg_temp.m16_val(43, 'Anon no muta', true);
  end;

  perform pg_temp.m16_act_as(v_out);
  begin
    perform public.m16_create_shelter_profile(v_bad_org, 'Vet', 'Z', 10);
    perform pg_temp.m16_val(44, 'Org no elegible rechazada', false);
  exception when others then
    perform pg_temp.m16_val(44, 'Org no elegible rechazada', SQLERRM like '%NOT_ELIGIBLE%');
  end;

  -- M04 45–50 — perfiles en orgs dedicadas (1:1)
  insert into public.organizations (id, slug, display_name, type, status, created_by)
  values
    ('b0000000-0000-4000-8000-000000000005', 'm16-val-mod', 'M16 Mod Org', 'SHELTER', 'ACTIVE', v_mgr),
    ('b0000000-0000-4000-8000-000000000006', 'm16-val-rej', 'M16 Reject Org', 'SHELTER', 'ACTIVE', v_mgr)
  on conflict (id) do nothing;
  insert into public.organization_memberships (organization_id, user_id, role_code, status, joined_at)
  values
    ('b0000000-0000-4000-8000-000000000005', v_mgr, 'OWNER', 'ACTIVE', now()),
    ('b0000000-0000-4000-8000-000000000006', v_mgr, 'OWNER', 'ACTIVE', now())
  on conflict do nothing;

  v_s_work := 'c0000000-0000-4000-8000-000000000005';
  insert into public.m16_shelter_profiles (
    id, organization_id, display_name, public_zone_text, total_capacity,
    operational_status, publication_status, verification_status, created_by, updated_by
  ) values (v_s_work, 'b0000000-0000-4000-8000-000000000005', 'Refugio Mod Test', 'Zona', 12,
    'ACTIVE', 'PUBLISHED', 'UNVERIFIED', v_mgr, v_mgr)
  on conflict (id) do nothing;

  perform pg_temp.m16_act_as(v_mgr);
  perform public.m16_request_verification(v_s_work);
  select id into v_req_id from public.m16_shelter_verification_requests
  where shelter_profile_id=v_s_work and status='PENDING' order by requested_at desc limit 1;

  perform pg_temp.m16_act_as(v_mod);
  begin
    select count(*)::int into v_cnt from public.m16_shelter_verification_requests where status='PENDING';
    perform pg_temp.m16_val(45, 'Moderador ve PENDING', v_cnt >= 1);
  exception when others then
    perform pg_temp.m16_val(45, 'Moderador ve PENDING', false, SQLERRM);
  end;

  if v_req_id is not null then
    begin
      v_json := public.m16_decide_shelter_verification(v_req_id, 'VERIFIED');
      perform pg_temp.m16_val(46, 'Moderador aprueba', true);
      perform pg_temp.m16_val(47, 'Perfil VERIFIED', v_json->>'verification_status' = 'VERIFIED');
    exception when others then
      perform pg_temp.m16_val(46, 'Moderador aprueba', false, SQLERRM);
      perform pg_temp.m16_val(47, 'Perfil VERIFIED', false, SQLERRM);
    end;

    begin
      v_json := public.m16_decide_shelter_verification(v_req_id, 'VERIFIED');
      perform pg_temp.m16_val(48, 'Decisión terminal idempotente',
        v_json->>'verification_status' = 'VERIFIED');
    exception when others then
      perform pg_temp.m16_val(48, 'Decisión terminal idempotente', false, SQLERRM);
    end;
  end if;

  -- Rechazo en otra solicitud
  v_s_work := 'c0000000-0000-4000-8000-000000000006';
  insert into public.m16_shelter_profiles (
    id, organization_id, display_name, public_zone_text, total_capacity,
    operational_status, publication_status, verification_status, created_by, updated_by
  ) values (v_s_work, 'b0000000-0000-4000-8000-000000000006', 'Refugio Reject Test', 'Zona', 8,
    'ACTIVE', 'PUBLISHED', 'UNVERIFIED', v_mgr, v_mgr)
  on conflict (id) do nothing;
  perform pg_temp.m16_act_as(v_mgr);
  perform public.m16_request_verification(v_s_work);
  select id into v_req_id from public.m16_shelter_verification_requests
  where shelter_profile_id=v_s_work and status='PENDING' order by requested_at desc limit 1;
  perform pg_temp.m16_act_as(v_mod);
  if v_req_id is not null then
    begin
      v_json := public.m16_decide_shelter_verification(v_req_id, 'REJECTED', 'motivo test');
      perform pg_temp.m16_val(49, 'Moderador rechaza', v_json->>'verification_status' = 'REJECTED');
    exception when others then
      perform pg_temp.m16_val(49, 'Moderador rechaza', false, SQLERRM);
    end;
  end if;

  perform pg_temp.m16_act_as(v_mgr);
  if v_req_id is not null then
    begin
      perform public.m16_decide_shelter_verification(v_req_id, 'VERIFIED');
      perform pg_temp.m16_val(50, 'No moderador no decide', false);
    exception when others then
      perform pg_temp.m16_val(50, 'No moderador no decide', true);
    end;
  end if;

  -- Limpieza datos prueba
  delete from public.m16_shelter_verification_requests
  where shelter_profile_id in (
    select id from public.m16_shelter_profiles
    where organization_id in (
      v_org, v_org2, v_org3, v_bad_org,
      'b0000000-0000-4000-8000-000000000005',
      'b0000000-0000-4000-8000-000000000006'
    )
  );
  delete from public.m16_shelter_public_contacts
  where shelter_profile_id in (
    select id from public.m16_shelter_profiles
    where organization_id in (v_org, v_org2, v_org3, v_bad_org,
      'b0000000-0000-4000-8000-000000000005',
      'b0000000-0000-4000-8000-000000000006')
  );
  delete from public.m16_shelter_opening_periods
  where shelter_profile_id in (
    select id from public.m16_shelter_profiles
    where organization_id in (v_org, v_org2, v_org3, v_bad_org,
      'b0000000-0000-4000-8000-000000000005',
      'b0000000-0000-4000-8000-000000000006')
  );
  delete from public.m16_shelter_needs
  where shelter_profile_id in (
    select id from public.m16_shelter_profiles
    where organization_id in (v_org, v_org2, v_org3, v_bad_org,
      'b0000000-0000-4000-8000-000000000005',
      'b0000000-0000-4000-8000-000000000006')
  );
  delete from public.m16_shelter_profiles
  where organization_id in (
    v_org, v_org2, v_org3, v_bad_org,
    'b0000000-0000-4000-8000-000000000005',
    'b0000000-0000-4000-8000-000000000006'
  );
  delete from public.organization_memberships
  where organization_id in (v_org, v_org2, v_org3,
    'b0000000-0000-4000-8000-000000000005',
    'b0000000-0000-4000-8000-000000000006');
  delete from public.organizations where id in (
    v_org, v_org2, v_org3, v_bad_org,
    'b0000000-0000-4000-8000-000000000005',
    'b0000000-0000-4000-8000-000000000006'
  );
  delete from public.user_role_assignments where user_id in (v_mgr, v_out, v_mod);
  delete from public.users where id in (v_mgr, v_out, v_mod);
  delete from auth.users where id in (v_mgr, v_out, v_mod);
end;
$setup$;

insert into supabase_migrations.schema_migrations (version, name, statements)
values ('053', '053_m16_shelter_profiles_and_public_access', '{}')
on conflict (version) do nothing;

select case_id, label, result, detail
from m16_val_results
order by case_id;

select count(*) filter (where result='PASS') as pass_count,
       count(*) filter (where result='FAIL') as fail_count,
       count(*) as total
from m16_val_results;

commit;
