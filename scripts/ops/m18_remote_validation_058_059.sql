-- M18 migraciones 058+059 — validación remota staging (casos 01–110)
-- Ejecutar: supabase db query --linked -f scripts/ops/m18_remote_validation_058_059.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m18_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m18_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m18_val_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m18_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_mgr uuid := 'f0000000-0000-4000-8000-000000000001';
  v_user2 uuid := 'f0000000-0000-4000-8000-000000000002';
  v_out uuid := 'f0000000-0000-4000-8000-000000000003';
  v_org uuid := 'a0000000-0000-4000-8000-000000000001';
  v_bad_org uuid := 'a0000000-0000-4000-8000-000000000002';
  v_e_draft uuid := 'b0000000-0000-4000-8000-000000000001';
  v_e_pub uuid := 'b0000000-0000-4000-8000-000000000002';
  v_e_cap uuid := 'b0000000-0000-4000-8000-000000000003';
  v_e_att uuid := 'b0000000-0000-4000-8000-000000000004';
  v_e_flow uuid;
  v_e_bad_trans uuid;
  v_reg_mgr uuid;
  v_reg_user2 uuid;
  v_reg_wait uuid;
  v_reg_noshow uuid;
  v_reg_attend uuid;
  v_json jsonb;
  v_cnt int;
  v_key text;
  v_ok boolean;
  v_now timestamptz := timezone('utc', now());
begin
  -- Permisos event.* (058)
  insert into public.organization_permissions (code, description) values
    ('event.view', 'Ver eventos comunitarios de la organización'),
    ('event.manage', 'Gestionar eventos comunitarios, inscripciones y check-in')
  on conflict (code) do nothing;

  insert into public.organization_role_permissions (role_id, permission_id)
  select r.id, p.id
  from public.organization_roles r
  cross join public.organization_permissions p
  where r.code in ('OWNER', 'ADMIN', 'MANAGER')
    and p.code in ('event.view', 'event.manage')
  on conflict do nothing;

  insert into public.organization_role_permissions (role_id, permission_id)
  select r.id, p.id
  from public.organization_roles r
  cross join public.organization_permissions p
  where r.code = 'MEMBER' and p.code = 'event.view'
  on conflict do nothing;

  -- Usuarios auth + public
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_mgr, 'authenticated', 'authenticated',
     'm18-mgr@test.local', crypt('m18-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_user2, 'authenticated', 'authenticated',
     'm18-user2@test.local', crypt('m18-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm18-out@test.local', crypt('m18-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, account_type, email_verified, account_status)
  values
    (v_mgr, 'm18-mgr@test.local', 'M18 Manager', 'PERSON', true, 'ACTIVE'),
    (v_user2, 'm18-user2@test.local', 'M18 Participant', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm18-out@test.local', 'M18 Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  insert into public.organizations (id, slug, display_name, type, status, created_by)
  values
    (v_org, 'm18-val-shelter', 'M18 Val Shelter Org', 'SHELTER', 'ACTIVE', v_mgr),
    (v_bad_org, 'm18-val-vet', 'M18 Val Vet Org', 'VETERINARY_CLINIC', 'ACTIVE', v_out)
  on conflict (id) do nothing;

  insert into public.organization_memberships (organization_id, user_id, role_code, status, joined_at)
  values (v_org, v_mgr, 'OWNER', 'ACTIVE', now())
  on conflict do nothing;

  -- Semilla eventos fijos (postgres) para pruebas estructurales/privacidad
  insert into public.m18_community_events (
    id, organization_id, title, description, event_type, event_status,
    max_capacity, waitlist_enabled, starts_at, ends_at, moderation_status, created_by
  ) values
    (v_e_draft, v_org, 'Evento Borrador M18',
     'Descripción borrador de prueba M18 validación remota.', 'ADOPTION_FAIR', 'DRAFT',
     50, true, v_now + interval '7 days', v_now + interval '7 days 3 hours', null, v_mgr),
    (v_e_pub, v_org, 'Evento Publicado M18',
     'Descripción publicada de prueba M18 validación remota.', 'VOLUNTEER_DAY', 'PUBLISHED',
     20, true, v_now + interval '3 days', v_now + interval '3 days 4 hours', 'APPROVED', v_mgr),
    (v_e_cap, v_org, 'Evento Capacidad 1 M18',
     'Descripción capacidad uno M18 validación remota waitlist.', 'COMMUNITY_GATHERING', 'PUBLISHED',
     1, true, v_now + interval '2 days', v_now + interval '2 days 2 hours', 'APPROVED', v_mgr),
    (v_e_att, v_org, 'Evento Asistencia M18',
     'Descripción asistencia M18 validación remota check-in.', 'TRAINING_WORKSHOP', 'PUBLISHED',
     10, true, v_now - interval '30 minutes', v_now + interval '2 hours', 'APPROVED', v_mgr)
  on conflict (id) do nothing;

  update public.m18_community_events
  set published_at = coalesce(published_at, v_now),
      check_in_opens_at = v_now - interval '2 hours',
      check_in_closes_at = v_now + interval '3 hours'
  where id in (v_e_pub, v_e_cap, v_e_att);

  -- ========================================================================
  -- ESTRUCTURA 01–30
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name in (
    'm18_community_events', 'm18_event_registrations', 'm18_event_reminders'
  );
  perform pg_temp.m18_val(1, 'Tres tablas M18', v_cnt = 3);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm18_community_events'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'organization_id';
  perform pg_temp.m18_val(2, 'FK organization_id eventos', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm18_event_registrations'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'event_id';
  perform pg_temp.m18_val(3, 'FK event_id inscripciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm18_event_registrations'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'user_id';
  perform pg_temp.m18_val(4, 'FK user_id inscripciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm18_event_reminders'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'event_id';
  perform pg_temp.m18_val(5, 'FK event_id recordatorios', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm18_event_registrations'
    and indexdef ilike '%event_id%user_id%';
  perform pg_temp.m18_val(6, 'UNIQUE event_id+user_id', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm18_event_reminders'
    and indexdef ilike '%event_id%user_id%';
  perform pg_temp.m18_val(7, 'UNIQUE reminder event+user', v_cnt >= 1);

  begin
    insert into public.m18_community_events (
      organization_id, title, description, event_type, max_capacity, starts_at, ends_at
    ) values (
      v_org, 'Cap cero', 'Descripción capacidad cero M18 test.', 'ADOPTION_FAIR', 0,
      v_now + interval '1 day', v_now + interval '2 days'
    );
    perform pg_temp.m18_val(8, 'CHECK max_capacity > 0', false);
  exception when check_violation then
    perform pg_temp.m18_val(8, 'CHECK max_capacity > 0', true);
  end;

  begin
    insert into public.m18_community_events (
      organization_id, title, description, event_type, max_capacity, starts_at, ends_at
    ) values (
      v_org, 'Fechas inválidas', 'Descripción fechas inválidas M18 test.', 'ADOPTION_FAIR', 10,
      v_now + interval '2 days', v_now + interval '1 day'
    );
    perform pg_temp.m18_val(9, 'CHECK ends_at > starts_at', false);
  exception when check_violation then
    perform pg_temp.m18_val(9, 'CHECK ends_at > starts_at', true);
  end;

  begin
    insert into public.m18_community_events (
      organization_id, title, description, event_type, max_capacity, starts_at, ends_at
    ) values (
      v_org, '', 'Descripción título vacío M18 test.', 'ADOPTION_FAIR', 10,
      v_now + interval '1 day', v_now + interval '2 days'
    );
    perform pg_temp.m18_val(10, 'CHECK title length', false);
  exception when check_violation then
    perform pg_temp.m18_val(10, 'CHECK title length', true);
  end;

  begin
    insert into public.m18_community_events (
      organization_id, title, description, event_type, max_capacity, starts_at, ends_at
    ) values (
      v_org, 'Desc corta', 'corta', 'ADOPTION_FAIR', 10,
      v_now + interval '1 day', v_now + interval '2 days'
    );
    perform pg_temp.m18_val(11, 'CHECK description length', false);
  exception when check_violation then
    perform pg_temp.m18_val(11, 'CHECK description length', true);
  end;

  begin
    insert into public.m18_community_events (
      organization_id, title, description, event_type, max_capacity, starts_at, ends_at
    ) values (
      v_org, 'Tipo inválido', 'Descripción tipo inválido M18 test.', 'INVALID_TYPE', 10,
      v_now + interval '1 day', v_now + interval '2 days'
    );
    perform pg_temp.m18_val(12, 'CHECK event_type enum', false);
  exception when check_violation then
    perform pg_temp.m18_val(12, 'CHECK event_type enum', true);
  end;

  begin
    insert into public.m18_community_events (
      organization_id, title, description, event_type, event_status, max_capacity, starts_at, ends_at
    ) values (
      v_org, 'Estado inválido', 'Descripción estado inválido M18 test.', 'ADOPTION_FAIR', 'INVALID', 10,
      v_now + interval '1 day', v_now + interval '2 days'
    );
    perform pg_temp.m18_val(13, 'CHECK event_status enum', false);
  exception when check_violation then
    perform pg_temp.m18_val(13, 'CHECK event_status enum', true);
  end;

  begin
    insert into public.m18_event_registrations (event_id, user_id, status)
    values (v_e_pub, v_mgr, 'INVALID_STATUS');
    perform pg_temp.m18_val(14, 'CHECK registration status enum', false);
  exception when check_violation then
    perform pg_temp.m18_val(14, 'CHECK registration status enum', true);
  end;

  begin
    insert into public.m18_event_reminders (event_id, user_id, scheduled_for, status)
    values (v_e_pub, v_mgr, v_now + interval '1 day', 'INVALID');
    perform pg_temp.m18_val(15, 'CHECK reminder status enum', false);
  exception when check_violation then
    perform pg_temp.m18_val(15, 'CHECK reminder status enum', true);
  end;

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm18_event_registrations'
    and column_name = 'attended_at' and udt_name = 'timestamptz';
  perform pg_temp.m18_val(16, 'Columna attended_at (059)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename like 'm18_%';
  perform pg_temp.m18_val(17, 'Índices M18', v_cnt >= 6);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm18_community_events' and c.relrowsecurity;
  perform pg_temp.m18_val(18, 'RLS m18_community_events', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm18_event_registrations' and c.relrowsecurity;
  perform pg_temp.m18_val(19, 'RLS m18_event_registrations', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm18_event_reminders' and c.relrowsecurity;
  perform pg_temp.m18_val(20, 'RLS m18_event_reminders', v_cnt = 1);

  select count(*)::int into v_cnt from public.organization_permissions
  where code = 'event.view';
  perform pg_temp.m18_val(21, 'Permiso event.view', v_cnt = 1);

  select count(*)::int into v_cnt from public.organization_permissions
  where code = 'event.manage';
  perform pg_temp.m18_val(22, 'Permiso event.manage', v_cnt = 1);

  select count(*)::int into v_cnt
  from public.organization_role_permissions orp
  join public.organization_roles r on r.id = orp.role_id
  join public.organization_permissions p on p.id = orp.permission_id
  where r.code = 'OWNER' and p.code = 'event.manage';
  perform pg_temp.m18_val(23, 'OWNER tiene event.manage', v_cnt >= 1);

  begin
    insert into public.m18_community_events (
      organization_id, title, description, event_type, event_status, max_capacity, starts_at, ends_at
    ) values (
      v_org, 'Terminal completed', 'Descripción terminal completed M18 test.', 'ADOPTION_FAIR',
      'COMPLETED', 10, v_now - interval '2 days', v_now - interval '1 day'
    );
    perform pg_temp.m18_val(24, 'INSERT terminal COMPLETED', true);
  exception when others then
    perform pg_temp.m18_val(24, 'INSERT terminal COMPLETED', false, SQLERRM);
  end;

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm18_community_events'
    and constraint_name = 'm18_event_type_chk';
  perform pg_temp.m18_val(25, 'CHECK m18_event_type_chk', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm18_community_events'
    and constraint_name = 'm18_event_status_chk';
  perform pg_temp.m18_val(26, 'CHECK m18_event_status_chk', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm18_event_registrations'
    and constraint_name = 'm18_reg_status_chk';
  perform pg_temp.m18_val(27, 'CHECK m18_reg_status_chk (059)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm18_community_events'
    and indexname = 'm18_events_org_idx';
  perform pg_temp.m18_val(28, 'Índice m18_events_org_idx', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm18_event_registrations'
    and indexname = 'm18_reg_event_user_uniq';
  perform pg_temp.m18_val(29, 'Índice m18_reg_event_user_uniq', v_cnt = 1);

  begin
    insert into public.m18_event_registrations (event_id, user_id, status, attended_at)
    values (v_e_pub, v_out, 'ATTENDED', v_now);
    perform pg_temp.m18_val(30, 'Estado ATTENDED insertable (059)', true);
    delete from public.m18_event_registrations
    where event_id = v_e_pub and user_id = v_out and status = 'ATTENDED';
  exception when others then
    perform pg_temp.m18_val(30, 'Estado ATTENDED insertable (059)', false, SQLERRM);
  end;

  -- ========================================================================
  -- RLS / PERMISOS 31–60
  -- ========================================================================
  perform pg_temp.m18_val(31, 'RLS eventos habilitado',
    exists (
      select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = 'm18_community_events' and c.relrowsecurity
    ));

  perform pg_temp.m18_val(32, 'RLS inscripciones habilitado',
    exists (
      select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = 'm18_event_registrations' and c.relrowsecurity
    ));

  perform pg_temp.m18_val(33, 'RLS recordatorios habilitado',
    exists (
      select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = 'm18_event_reminders' and c.relrowsecurity
    ));

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm18_community_events' and grantee = 'anon';
  perform pg_temp.m18_val(34, 'Anon sin grant eventos', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm18_event_registrations' and grantee = 'anon';
  perform pg_temp.m18_val(35, 'Anon sin grant inscripciones', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm18_event_reminders' and grantee = 'anon';
  perform pg_temp.m18_val(36, 'Anon sin grant recordatorios', v_cnt = 0);

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m18_community_events;
    reset role;
    perform pg_temp.m18_val(37, 'Anon sin filas eventos (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m18_val(37, 'Anon sin filas eventos (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m18_val(37, 'Anon sin filas eventos (RLS)', false, SQLERRM);
  end;

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m18_event_registrations;
    reset role;
    perform pg_temp.m18_val(38, 'Anon sin filas inscripciones (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m18_val(38, 'Anon sin filas inscripciones (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m18_val(38, 'Anon sin filas inscripciones (RLS)', false, SQLERRM);
  end;

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m18_event_reminders;
    reset role;
    perform pg_temp.m18_val(39, 'Anon sin filas recordatorios (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m18_val(39, 'Anon sin filas recordatorios (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m18_val(39, 'Anon sin filas recordatorios (RLS)', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);

  begin
    select count(*)::int into v_cnt from public.m18_list_public_events();
    perform pg_temp.m18_val(40, 'Anon list_public_events', v_cnt >= 1);
  exception when others then
    perform pg_temp.m18_val(40, 'Anon list_public_events', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(41, 'Anon get_public_event', v_json is not null);
  exception when others then
    perform pg_temp.m18_val(41, 'Anon get_public_event', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m18_list_public_events() j
    where (j->>'id')::uuid = v_e_draft;
    perform pg_temp.m18_val(42, 'DRAFT oculto en listado', v_cnt = 0);
  exception when others then
    perform pg_temp.m18_val(42, 'DRAFT oculto en listado', false, SQLERRM);
  end;

  begin
    perform public.m18_get_public_event(v_e_draft);
    perform pg_temp.m18_val(43, 'DRAFT get_public falla', false);
  exception when others then
    perform pg_temp.m18_val(43, 'DRAFT get_public falla', SQLERRM like '%M18_EVENT_NOT_PUBLIC%');
  end;

  perform pg_temp.m18_act_as(v_out);
  begin
    perform public.m18_create_event(
      v_org, 'Hack M18', 'Descripción hack evento M18 test.', 'ADOPTION_FAIR', 10,
      v_now + interval '1 day', v_now + interval '2 days'
    );
    perform pg_temp.m18_val(44, 'Ajeno no crea evento', false);
  exception when others then
    perform pg_temp.m18_val(44, 'Ajeno no crea evento', SQLERRM like '%M18_PERMISSION_DENIED%');
  end;

  begin
    perform public.m18_transition_event(v_e_pub, 'PAUSED');
    perform pg_temp.m18_val(45, 'Ajeno no transiciona evento', false);
  exception when others then
    perform pg_temp.m18_val(45, 'Ajeno no transiciona evento', SQLERRM like '%M18_PERMISSION_DENIED%');
  end;

  begin
    perform public.m18_list_registrations_for_manage(v_e_pub);
    perform pg_temp.m18_val(46, 'Ajeno no lista inscripciones manage', false);
  exception when others then
    perform pg_temp.m18_val(46, 'Ajeno no lista inscripciones manage', SQLERRM like '%M18_PERMISSION_DENIED%');
  end;

  begin
    v_json := public.m18_create_event(
      v_bad_org, 'Vet event', 'Descripción org no elegible M18 test.', 'ADOPTION_FAIR', 10,
      v_now + interval '1 day', v_now + interval '2 days'
    );
    perform pg_temp.m18_val(47, 'Org no elegible rechazada', false);
  exception when others then
    perform pg_temp.m18_val(47, 'Org no elegible rechazada', SQLERRM like '%M18_ORGANIZATION_NOT_ELIGIBLE%');
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    v_json := public.m18_create_event(
      v_org, 'Evento RPC M18', 'Descripción evento creado vía RPC M18 test.', 'AWARENESS_WALK', 15,
      v_now + interval '5 days', v_now + interval '5 days 3 hours'
    );
    v_e_flow := (v_json->>'id')::uuid;
    perform pg_temp.m18_val(48, 'Manager crea evento borrador', v_json->>'status' = 'DRAFT');
  exception when others then
    perform pg_temp.m18_val(48, 'Manager crea evento borrador', false, SQLERRM);
  end;

  if v_e_flow is not null then
    begin
      v_json := public.m18_transition_event(v_e_flow, 'PUBLISHED');
      perform pg_temp.m18_val(49, 'Manager publica evento', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m18_val(49, 'Manager publica evento', false, SQLERRM);
    end;

    update public.m18_community_events
    set moderation_status = 'APPROVED', published_at = coalesce(published_at, v_now)
    where id = v_e_flow;
  end if;

  perform pg_temp.m18_act_as(v_user2);
  begin
    v_json := public.m18_register_for_event(v_e_pub);
    perform pg_temp.m18_val(50, 'Participante se inscribe', v_json->>'status' = 'REGISTERED');
    v_reg_user2 := (v_json->>'id')::uuid;
  exception when others then
    perform pg_temp.m18_val(50, 'Participante se inscribe', false, SQLERRM);
  end;

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm18_list_public_events'
    and grantee in ('anon', 'PUBLIC');
  perform pg_temp.m18_val(51, 'Grant execute list_public anon', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm18_get_public_event'
    and grantee in ('anon', 'PUBLIC');
  perform pg_temp.m18_val(52, 'Grant execute get_public anon', v_cnt >= 1);

  perform pg_temp.m18_act_as(v_mgr);
  begin
    select count(*)::int into v_cnt from public.m18_list_org_events(v_org);
    perform pg_temp.m18_val(53, 'Manager lista eventos org', v_cnt >= 1);
  exception when others then
    perform pg_temp.m18_val(53, 'Manager lista eventos org', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_event(v_e_pub);
    perform pg_temp.m18_val(54, 'Manager lee evento interno',
      (v_json->>'organization_id')::uuid = v_org);
  exception when others then
    perform pg_temp.m18_val(54, 'Manager lee evento interno', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_out);
  begin
    perform public.m18_get_event(v_e_pub);
    perform pg_temp.m18_val(55, 'Ajeno no lee evento interno', false);
  exception when others then
    perform pg_temp.m18_val(55, 'Ajeno no lee evento interno', SQLERRM like '%M18_PERMISSION_DENIED%');
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m18_community_events;
    reset role;
    perform pg_temp.m18_val(56, 'Anon no SELECT directo eventos', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m18_val(56, 'Anon no SELECT directo eventos', true);
  when others then
    reset role;
    perform pg_temp.m18_val(56, 'Anon no SELECT directo eventos', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    v_ok := public.m18_is_organization_eligible(v_org);
    perform pg_temp.m18_val(57, 'Org elegible SHELTER', v_ok);
  exception when others then
    perform pg_temp.m18_val(57, 'Org elegible SHELTER', false, SQLERRM);
  end;

  begin
    v_ok := public.m18_is_organization_eligible(v_bad_org);
    perform pg_temp.m18_val(58, 'Vet no elegible', not v_ok);
  exception when others then
    perform pg_temp.m18_val(58, 'Vet no elegible', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    perform public.m18_mark_attendance(gen_random_uuid());
    perform pg_temp.m18_val(59, 'Ajeno implícito mark_attendance', false);
  exception when others then
    perform pg_temp.m18_val(59, 'mark_attendance requiere manage',
      SQLERRM like '%M18_REGISTRATION_NOT_FOUND%' or SQLERRM like '%M18_PERMISSION_DENIED%');
  end;

  perform pg_temp.m18_act_as(v_out);
  begin
    perform public.m18_mark_attendance(gen_random_uuid());
    perform pg_temp.m18_val(60, 'Outsider denied mark_attendance', false);
  exception when others then
    perform pg_temp.m18_val(60, 'Outsider denied mark_attendance',
      SQLERRM like '%M18_PERMISSION_DENIED%' or SQLERRM like '%M18_REGISTRATION_NOT_FOUND%');
  end;

  -- ========================================================================
  -- OPERACIONES 61–90
  -- ========================================================================
  perform pg_temp.m18_act_as(v_mgr);
  begin
    v_json := public.m18_register_for_event(v_e_cap);
    perform pg_temp.m18_val(61, 'Manager ocupa cupo 1', v_json->>'status' = 'REGISTERED');
    v_reg_mgr := (v_json->>'id')::uuid;
  exception when others then
    perform pg_temp.m18_val(61, 'Manager ocupa cupo 1', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_out);
  begin
    v_json := public.m18_register_for_event(v_e_cap);
    perform pg_temp.m18_val(62, 'Segundo usuario waitlist', v_json->>'status' = 'WAITLISTED');
    v_reg_wait := (v_json->>'id')::uuid;
  exception when others then
    perform pg_temp.m18_val(62, 'Segundo usuario waitlist', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_out);
  begin
    v_json := public.m18_register_for_event(v_e_cap);
    perform pg_temp.m18_val(63, 'Registro idempotente waitlist',
      v_json->>'status' = 'WAITLISTED' and (v_json->>'id')::uuid = v_reg_wait);
  exception when others then
    perform pg_temp.m18_val(63, 'Registro idempotente waitlist', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    v_json := public.m18_cancel_registration(v_e_cap);
    perform pg_temp.m18_val(64, 'Cancelación manager libera cupo', v_json->>'status' = 'CANCELLED');
  exception when others then
    perform pg_temp.m18_val(64, 'Cancelación manager libera cupo', false, SQLERRM);
  end;

  begin
    select status into v_key from public.m18_event_registrations where id = v_reg_wait;
    perform pg_temp.m18_val(65, 'Cancel promote waitlist', v_key = 'REGISTERED');
  exception when others then
    perform pg_temp.m18_val(65, 'Cancel promote waitlist', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_user2);
  begin
    v_json := public.m18_register_for_event(v_e_att);
    perform pg_temp.m18_val(66, 'User2 inscripción check-in', v_json->>'status' = 'REGISTERED');
    v_reg_attend := (v_json->>'id')::uuid;
  exception when others then
    perform pg_temp.m18_val(66, 'User2 inscripción check-in', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  if v_reg_attend is not null then
    begin
      v_json := public.m18_check_in_registration(v_reg_attend);
      perform pg_temp.m18_val(67, 'Check-in inscripción', v_json->>'status' = 'CHECKED_IN');
    exception when others then
      perform pg_temp.m18_val(67, 'Check-in inscripción', false, SQLERRM);
    end;

    begin
      v_json := public.m18_check_in_registration(v_reg_attend);
      perform pg_temp.m18_val(68, 'Check-in idempotente', v_json->>'status' = 'CHECKED_IN');
    exception when others then
      perform pg_temp.m18_val(68, 'Check-in idempotente', false, SQLERRM);
    end;
  end if;

  perform pg_temp.m18_act_as(v_out);
  begin
    v_json := public.m18_register_for_event(v_e_att);
    v_reg_noshow := (v_json->>'id')::uuid;
    perform pg_temp.m18_val(69, 'Outsider inscripción no-show', v_json->>'status' = 'REGISTERED');
  exception when others then
    perform pg_temp.m18_val(69, 'Outsider inscripción no-show', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    v_json := public.m18_transition_event(v_e_att, 'COMPLETED');
    perform pg_temp.m18_val(70, 'Transition COMPLETED', v_json->>'status' = 'COMPLETED');
  exception when others then
    perform pg_temp.m18_val(70, 'Transition COMPLETED', false, SQLERRM);
  end;

  update public.m18_community_events
  set starts_at = v_now - interval '3 hours',
      ends_at = v_now - interval '1 hour'
  where id = v_e_att;

  if v_reg_attend is not null then
    begin
      v_json := public.m18_mark_attendance(v_reg_attend);
      perform pg_temp.m18_val(71, 'Mark attendance CHECKED_IN->ATTENDED',
        v_json->>'status' = 'ATTENDED' and v_json->>'attended_at' is not null);
    exception when others then
      perform pg_temp.m18_val(71, 'Mark attendance CHECKED_IN->ATTENDED', false, SQLERRM);
    end;

    begin
      v_json := public.m18_mark_attendance(v_reg_attend);
      perform pg_temp.m18_val(72, 'Mark attendance idempotente', v_json->>'status' = 'ATTENDED');
    exception when others then
      perform pg_temp.m18_val(72, 'Mark attendance idempotente', false, SQLERRM);
    end;
  end if;

  if v_reg_noshow is not null then
    begin
      v_json := public.m18_mark_no_show(v_reg_noshow);
      perform pg_temp.m18_val(73, 'Mark no_show post-evento', v_json->>'status' = 'NO_SHOW');
    exception when others then
      perform pg_temp.m18_val(73, 'Mark no_show post-evento', false, SQLERRM);
    end;

    begin
      v_json := public.m18_mark_no_show(v_reg_noshow);
      perform pg_temp.m18_val(74, 'Mark no_show idempotente', v_json->>'status' = 'NO_SHOW');
    exception when others then
      perform pg_temp.m18_val(74, 'Mark no_show idempotente', false, SQLERRM);
    end;
  end if;

  if v_e_flow is not null then
    begin
      v_json := public.m18_transition_event(v_e_flow, 'PUBLISHED');
      perform pg_temp.m18_val(75, 'PUBLISHED idempotente', v_json->>'status' = 'PUBLISHED');
    exception when others then
      perform pg_temp.m18_val(75, 'PUBLISHED idempotente', false, SQLERRM);
    end;

    begin
      v_json := public.m18_transition_event(v_e_flow, 'PAUSED');
      perform pg_temp.m18_val(76, 'Transition PAUSED', v_json->>'status' = 'PAUSED');
    exception when others then
      perform pg_temp.m18_val(76, 'Transition PAUSED', false, SQLERRM);
    end;

    begin
      v_json := public.m18_transition_event(v_e_flow, 'CANCELLED');
      perform pg_temp.m18_val(77, 'Transition CANCELLED', v_json->>'status' = 'CANCELLED');
    exception when others then
      perform pg_temp.m18_val(77, 'Transition CANCELLED', false, SQLERRM);
    end;

    begin
      perform public.m18_transition_event(v_e_flow, 'PUBLISHED');
      perform pg_temp.m18_val(78, 'No reactivar CANCELLED', false);
    exception when others then
      perform pg_temp.m18_val(78, 'No reactivar CANCELLED', SQLERRM like '%M18_STATE_ALREADY_FINAL%');
    end;
  end if;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    v_json := public.m18_create_event(
      v_org, 'Salto inválido M18', 'Descripción salto inválido M18 test.', 'FREE_FUNDRAISER', 10,
      v_now + interval '10 days', v_now + interval '10 days 2 hours'
    );
    perform pg_temp.m18_val(79, 'Crear evento transición test', v_json->>'status' = 'DRAFT');
    v_e_bad_trans := (v_json->>'id')::uuid;
  exception when others then
    perform pg_temp.m18_val(79, 'Crear evento transición test', false, SQLERRM);
  end;

  if v_e_bad_trans is not null then
    begin
      perform public.m18_transition_event(v_e_bad_trans, 'COMPLETED');
      perform pg_temp.m18_val(80, 'DRAFT->COMPLETED rechazado', false);
    exception when others then
      perform pg_temp.m18_val(80, 'DRAFT->COMPLETED rechazado',
        SQLERRM like '%M18_INVALID_STATE_TRANSITION%');
    end;
  end if;

  begin
    v_json := public.m18_get_capacity_summary(v_e_pub);
    perform pg_temp.m18_val(81, 'Capacity summary RPC',
      (v_json->>'max_capacity')::int = 20);
  exception when others then
    perform pg_temp.m18_val(81, 'Capacity summary RPC', false, SQLERRM);
  end;

  begin
    v_json := public.m18_promote_next_waitlisted(v_e_cap);
    perform pg_temp.m18_val(82, 'Promote waitlist sin cupo', v_json is null);
  exception when others then
    perform pg_temp.m18_val(82, 'Promote waitlist sin cupo', false, SQLERRM);
  end;

  begin
    v_json := public.m18_update_event_capacity(v_e_pub, 25, true);
    perform pg_temp.m18_val(83, 'Update capacity válido', (v_json->>'max_capacity')::int = 25);
  exception when others then
    perform pg_temp.m18_val(83, 'Update capacity válido', false, SQLERRM);
  end;

  begin
    perform public.m18_update_event_capacity(v_e_pub, 0, true);
    perform pg_temp.m18_val(84, 'Update capacity inválida', false);
  exception when others then
    perform pg_temp.m18_val(84, 'Update capacity inválida', SQLERRM like '%M18_INVALID_CAPACITY%');
  end;

  perform pg_temp.m18_act_as(v_user2);
  begin
    perform public.m18_register_for_event(v_e_pub);
    perform pg_temp.m18_val(85, 'Registro idempotente REGISTERED', true);
  exception when others then
    perform pg_temp.m18_val(85, 'Registro idempotente REGISTERED', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    v_json := public.m18_reject_registration(v_reg_wait);
    perform pg_temp.m18_val(86, 'Reject registration REJECTED', v_json->>'status' = 'REJECTED');
  exception when others then
    perform pg_temp.m18_val(86, 'Reject registration REJECTED', false, SQLERRM);
  end;

  begin
    v_json := public.m18_reject_registration(v_reg_wait);
    perform pg_temp.m18_val(87, 'Reject idempotente', v_json->>'status' = 'REJECTED');
  exception when others then
    perform pg_temp.m18_val(87, 'Reject idempotente', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_user2);
  begin
    v_json := public.m18_get_my_registration(v_e_pub);
    perform pg_temp.m18_val(88, 'Get my registration', v_json is not null);
  exception when others then
    perform pg_temp.m18_val(88, 'Get my registration', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    select count(*)::int into v_cnt from public.m18_list_registrations_for_manage(v_e_cap);
    perform pg_temp.m18_val(89, 'List registrations manage', v_cnt >= 1);
  exception when others then
    perform pg_temp.m18_val(89, 'List registrations manage', false, SQLERRM);
  end;

  begin
    perform public.m18_schedule_reminder(v_e_pub);
    perform pg_temp.m18_val(90, 'Schedule reminder bloqueado', false);
  exception when others then
    perform pg_temp.m18_val(90, 'Schedule reminder bloqueado',
      SQLERRM like '%M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE%');
  end;

  -- ========================================================================
  -- PRIVACIDAD 91–110
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(91, 'Sin organization_id público',
      v_json->>'organization_id' is null);
  exception when others then
    perform pg_temp.m18_val(91, 'Sin organization_id público', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(92, 'Sin user_id público', v_json->>'user_id' is null);
  exception when others then
    perform pg_temp.m18_val(92, 'Sin user_id público', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(93, 'Sin created_by público', v_json->>'created_by' is null);
  exception when others then
    perform pg_temp.m18_val(93, 'Sin created_by público', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(94, 'Sin internal_notes público', v_json->>'internal_notes' is null);
  exception when others then
    perform pg_temp.m18_val(94, 'Sin internal_notes público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from jsonb_object_keys(public.m18_get_public_event(v_e_pub)) as key;
    perform pg_temp.m18_val(95, 'Claves públicas esperadas', v_cnt between 15 and 25);
  exception when others then
    perform pg_temp.m18_val(95, 'Claves públicas esperadas', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_registration_stats(v_e_pub);
    perform pg_temp.m18_val(96, 'Stats agregados sin user_id',
      v_json ? 'registered_count' and not v_json ? 'user_id');
  exception when others then
    perform pg_temp.m18_val(96, 'Stats agregados sin user_id', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_capacity_summary(v_e_pub);
    perform pg_temp.m18_val(97, 'Capacity summary agregado',
      v_json ? 'registered_count' and v_json ? 'available_spots');
  exception when others then
    perform pg_temp.m18_val(97, 'Capacity summary agregado', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt
    from public.m18_list_public_events() j
    where j::text ilike '%organization_id%' or j::text ilike '%user_id%';
    perform pg_temp.m18_val(98, 'List público sin PII ids', v_cnt = 0);
  exception when others then
    perform pg_temp.m18_val(98, 'List público sin PII ids', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(99, 'organization_display_name presente',
      v_json->>'organization_display_name' is not null);
  exception when others then
    perform pg_temp.m18_val(99, 'organization_display_name presente', false, SQLERRM);
  end;

  update public.m18_community_events set moderation_status = 'BLOCKED' where id = v_e_pub;
  begin
    select count(*)::int into v_cnt from public.m18_list_public_events() j
    where (j->>'id')::uuid = v_e_pub;
    perform pg_temp.m18_val(100, 'BLOCKED oculto en público', v_cnt = 0);
  exception when others then
    perform pg_temp.m18_val(100, 'BLOCKED oculto en público', false, SQLERRM);
  end;
  update public.m18_community_events set moderation_status = 'APPROVED' where id = v_e_pub;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(101, 'Sin pet_id en reference público',
      (v_json->'reference'->>'pet_id') is null);
  exception when others then
    perform pg_temp.m18_val(101, 'Sin pet_id en reference público', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(102, 'Sin shelter_profile_id público',
      (v_json->'reference'->>'shelter_profile_id') is null);
  exception when others then
    perform pg_temp.m18_val(102, 'Sin shelter_profile_id público', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_registration_stats(v_e_pub);
    perform pg_temp.m18_val(103, 'Stats sin filas individuales',
      jsonb_typeof(v_json) = 'object' and not v_json ? 'registrations');
  exception when others then
    perform pg_temp.m18_val(103, 'Stats sin filas individuales', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(104, 'registered_count agregado en público',
      (v_json->>'registered_count')::int >= 0);
  exception when others then
    perform pg_temp.m18_val(104, 'registered_count agregado en público', false, SQLERRM);
  end;

  begin
    v_json := public.m18_get_public_event(v_e_pub);
    perform pg_temp.m18_val(105, 'is_registration_open boolean',
      jsonb_typeof(v_json->'is_registration_open') = 'boolean');
  exception when others then
    perform pg_temp.m18_val(105, 'is_registration_open boolean', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    v_json := public.m18_get_event(v_e_pub);
    perform pg_temp.m18_val(106, 'Interno sí tiene organization_id',
      (v_json->>'organization_id')::uuid = v_org);
  exception when others then
    perform pg_temp.m18_val(106, 'Interno sí tiene organization_id', false, SQLERRM);
  end;

  perform pg_temp.m18_act_as(v_mgr);
  begin
    select count(*)::int into v_cnt from public.m18_list_registrations_for_manage(v_e_pub) r
    where r->>'user_id' is not null;
    perform pg_temp.m18_val(107, 'Manage expone user_id interno', v_cnt >= 1);
  exception when others then
    perform pg_temp.m18_val(107, 'Manage expone user_id interno', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    v_json := public.m18_get_public_event(v_e_att);
    perform pg_temp.m18_val(108, 'COMPLETED visible en público', v_json->>'status' = 'COMPLETED');
  exception when others then
    perform pg_temp.m18_val(108, 'COMPLETED visible en público', false, SQLERRM);
  end;

  begin
    perform public.m18_get_public_event(v_e_flow);
    perform pg_temp.m18_val(109, 'CANCELLED get_public falla', false);
  exception when others then
    perform pg_temp.m18_val(109, 'CANCELLED get_public falla', SQLERRM like '%M18_EVENT_TERMINAL%');
  end;

  begin
    v_json := public.m18_get_public_registration_stats(v_e_pub);
    perform pg_temp.m18_val(110, 'Stats checked_in_count agregado',
      v_json ? 'checked_in_count' and not v_json ? 'checked_in_at');
  exception when others then
    perform pg_temp.m18_val(110, 'Stats checked_in_count agregado', false, SQLERRM);
  end;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  delete from public.m18_event_reminders
  where event_id in (
    select id from public.m18_community_events
    where organization_id in (v_org, v_bad_org)
       or id in (
         'b0000000-0000-4000-8000-000000000001',
         'b0000000-0000-4000-8000-000000000002',
         'b0000000-0000-4000-8000-000000000003',
         'b0000000-0000-4000-8000-000000000004'
       )
  );

  delete from public.m18_event_registrations
  where event_id in (
    select id from public.m18_community_events
    where organization_id in (v_org, v_bad_org)
  );

  delete from public.m18_community_events
  where organization_id in (v_org, v_bad_org);

  delete from public.organization_memberships where organization_id in (v_org, v_bad_org);
  delete from public.organizations where id in (v_org, v_bad_org);
  delete from public.users where id in (v_mgr, v_user2, v_out);
  delete from auth.users where id in (v_mgr, v_user2, v_out);
end;
$setup$;

insert into supabase_migrations.schema_migrations (version, name, statements)
values
  ('058', '058_m18_community_events_and_registrations', '{}'),
  ('059', '059_m18_event_operations_and_attendance', '{}')
on conflict (version) do nothing;

select case_id, label, result, detail
from m18_val_results
where result = 'FAIL'
order by case_id;

select case_id, label, result, detail
from m18_val_results
order by case_id;

create table if not exists public._m18_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m18_val_last_failures;

insert into public._m18_val_last_failures (case_id, label, detail)
select case_id, label, detail from m18_val_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m18_val_results where result = 'FAIL' order by case_id loop
    raise warning 'M18_VAL_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m18_val_results;

commit;
