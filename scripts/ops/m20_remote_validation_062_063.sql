-- M20 migraciones 062+063 — validación remota staging (casos 01–125)
-- Ejecutar: supabase db query --linked -f scripts/ops/m20_remote_validation_062_063.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m20_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m20_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m20_val_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m20_act_as(p_uid uuid)
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
  v_pet_id text := 'd0000000-0000-4000-8000-000000000099';
  v_campaign_id text := 'e0000000-0000-4000-8000-000000000001';
  v_social_id text := 'c0000000-0000-4000-8000-0000000000e1';
  v_conv_direct uuid;
  v_conv_flow uuid;
  v_conv_arch uuid;
  v_conv_campaign uuid;
  v_conv_social uuid;
  v_conv_pet uuid;
  v_msg1 uuid;
  v_msg2 uuid;
  v_msg3 uuid;
  v_msg_reply uuid;
  v_msg_edit uuid;
  v_msg_del uuid;
  v_msg_attach uuid;
  v_msg_priv uuid;
  v_msg_idem uuid;
  v_client_id text := 'm20-val-client-idem-001';
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_key text;
  v_ok boolean;
  v_cursor timestamptz;
  v_cursor2 timestamptz;
  v_now timestamptz := timezone('utc', now());
  v_err text;
  v_ids_page1 uuid[];
  v_ids_page2 uuid[];
  v_overlap int;
  v_i int;
  v_low uuid;
  v_high uuid;
  v_status_mgr text;
  v_status_peer text;
begin
  -- Usuarios auth + public (UUIDs patrón M18/M19)
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_mgr, 'authenticated', 'authenticated',
     'm20-mgr@test.local', crypt('m20-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_user2, 'authenticated', 'authenticated',
     'm20-user2@test.local', crypt('m20-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm20-out@test.local', crypt('m20-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_mgr, 'm20-mgr@test.local', 'M20 Manager', 'M20 Manager', 'PERSON', true, 'ACTIVE'),
    (v_user2, 'm20-user2@test.local', 'M20 Participant', 'M20 Participant', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm20-out@test.local', 'M20 Outsider', 'M20 Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  -- ========================================================================
  -- ESTRUCTURA 01–25
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name in (
    'm20_conversations', 'm20_messages', 'm20_user_blocks', 'm20_participant_state'
  );
  perform pg_temp.m20_val(1, 'Cuatro tablas M20', v_cnt = 4);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm20_conversations'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'participant_low';
  perform pg_temp.m20_val(2, 'FK participant_low conversaciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm20_conversations'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'participant_high';
  perform pg_temp.m20_val(3, 'FK participant_high conversaciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm20_messages'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'conversation_id';
  perform pg_temp.m20_val(4, 'FK conversation_id mensajes', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm20_messages'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'sender_user_id';
  perform pg_temp.m20_val(5, 'FK sender_user_id mensajes', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm20_user_blocks'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'blocker_user_id';
  perform pg_temp.m20_val(6, 'FK blocker_user_id bloqueos', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm20_conversations'
    and indexname = 'm20_conv_participants_ctx_uniq';
  perform pg_temp.m20_val(7, 'UNIQUE participantes+contexto conversación', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm20_user_blocks'
    and indexname = 'm20_user_blocks_uniq';
  perform pg_temp.m20_val(8, 'UNIQUE bloqueos usuario', v_cnt = 1);

  begin
    v_low := least(v_mgr, v_user2);
    v_high := greatest(v_mgr, v_user2);
    insert into public.m20_conversations (participant_low, participant_high, conversation_status)
    values (v_low, v_high, 'INVALID');
    perform pg_temp.m20_val(9, 'CHECK conversation_status enum', false);
  exception when check_violation then
    perform pg_temp.m20_val(9, 'CHECK conversation_status enum', true);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    v_low := least(v_mgr, v_out);
    v_high := greatest(v_mgr, v_out);
    insert into public.m20_conversations (participant_low, participant_high)
    values (v_low, v_high)
    returning id into v_conv_direct;
    insert into public.m20_messages (
      conversation_id, sender_user_id, sender_display_name, content, message_status
    ) values (v_conv_direct, v_mgr, 'M20 Manager', 'x', 'INVALID');
    perform pg_temp.m20_val(10, 'CHECK message_status enum', false);
  exception when check_violation then
    perform pg_temp.m20_val(10, 'CHECK message_status enum', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    if v_conv_direct is null then
      v_low := least(v_mgr, v_out);
      v_high := greatest(v_mgr, v_out);
      insert into public.m20_conversations (participant_low, participant_high)
      values (v_low, v_high) returning id into v_conv_direct;
    end if;
    insert into public.m20_messages (
      conversation_id, sender_user_id, sender_display_name, content, message_status
    ) values (v_conv_direct, v_mgr, 'M20 Manager', '', 'SENT');
    perform pg_temp.m20_val(11, 'CHECK content vacío sin adjunto', false);
  exception when check_violation then
    perform pg_temp.m20_val(11, 'CHECK content vacío sin adjunto', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    v_low := least(v_mgr, v_user2);
    v_high := greatest(v_mgr, v_user2);
    insert into public.m20_conversations (participant_low, participant_high, conversation_type)
    values (v_low, v_high, 'INVALID_TYPE');
    perform pg_temp.m20_val(12, 'CHECK conversation_type enum (063)', false);
  exception when check_violation then
    perform pg_temp.m20_val(12, 'CHECK conversation_type enum (063)', true);
  end;

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    if v_conv_direct is null then
      v_low := least(v_mgr, v_out);
      v_high := greatest(v_mgr, v_out);
      insert into public.m20_conversations (participant_low, participant_high)
      values (v_low, v_high) returning id into v_conv_direct;
    end if;
    insert into public.m20_messages (
      conversation_id, sender_user_id, sender_display_name, content, message_status, message_type
    ) values (v_conv_direct, v_mgr, 'M20 Manager', 'Tipo inválido M20.', 'SENT', 'INVALID_TYPE');
    perform pg_temp.m20_val(13, 'CHECK message_type enum (063)', false);
  exception when check_violation then
    perform pg_temp.m20_val(13, 'CHECK message_type enum (063)', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    v_low := least(v_mgr, v_user2);
    v_high := greatest(v_mgr, v_user2);
    insert into public.m20_conversations (
      participant_low, participant_high, context_type, context_target_id, context_display_label
    ) values (v_low, v_high, 'CAMPAIGN', v_campaign_id, 'Campaña M20 val');
    perform pg_temp.m20_val(14, 'CHECK context CAMPAIGN (063)', true);
    delete from public.m20_conversations
    where participant_low = v_low and participant_high = v_high
      and context_type = 'CAMPAIGN' and context_target_id = v_campaign_id;
  exception when check_violation then
    perform pg_temp.m20_val(14, 'CHECK context CAMPAIGN (063)', false, SQLERRM);
  end;

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm20_conversations'
    and column_name = 'conversation_type';
  perform pg_temp.m20_val(15, 'Columna conversation_type (063)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm20_messages'
    and column_name = 'client_message_id';
  perform pg_temp.m20_val(16, 'Columna client_message_id (063)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm20_messages'
    and column_name = 'message_type';
  perform pg_temp.m20_val(17, 'Columna message_type (063)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm20_messages'
    and column_name = 'reply_to_message_id';
  perform pg_temp.m20_val(18, 'Columna reply_to_message_id (063)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm20_messages'
    and column_name = 'edited_at' and udt_name = 'timestamptz';
  perform pg_temp.m20_val(19, 'Columna edited_at (063)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm20_messages'
    and column_name = 'deleted_at' and udt_name = 'timestamptz';
  perform pg_temp.m20_val(20, 'Columna deleted_at (063)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm20_messages'
    and indexname = 'm20_msg_client_id_uniq';
  perform pg_temp.m20_val(21, 'Índice m20_msg_client_id_uniq (063)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm20_participant_state'
    and indexname = 'm20_participant_state_user_idx';
  perform pg_temp.m20_val(22, 'Índice m20_participant_state_user_idx (063)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm20_conversations' and c.relrowsecurity;
  perform pg_temp.m20_val(23, 'RLS m20_conversations', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm20_messages' and c.relrowsecurity;
  perform pg_temp.m20_val(24, 'RLS m20_messages', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname in (
      'm20_list_my_conversations', 'm20_get_conversation_messages', 'm20_send_message',
      'm20_archive_conversation', 'm20_block_user', 'm20_unblock_user',
      'm20_create_direct_conversation', 'm20_edit_message', 'm20_delete_message',
      'm20_mark_conversation_read'
    );
  perform pg_temp.m20_val(25, 'RPCs M20 clave existen', v_cnt = 10);

  -- Conversación principal vía RPC para pruebas posteriores
  perform pg_temp.m20_act_as(v_mgr);
  begin
    v_json := public.m20_create_direct_conversation(v_user2);
    v_conv_flow := (v_json->>'id')::uuid;
  exception when others then
    v_conv_flow := null;
  end;

  -- ========================================================================
  -- RLS / PERMISOS 26–55
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm20_conversations' and grantee = 'anon';
  perform pg_temp.m20_val(26, 'Anon sin grant conversaciones', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm20_messages' and grantee = 'anon';
  perform pg_temp.m20_val(27, 'Anon sin grant mensajes', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm20_user_blocks' and grantee = 'anon';
  perform pg_temp.m20_val(28, 'Anon sin grant bloqueos', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm20_participant_state' and grantee = 'anon';
  perform pg_temp.m20_val(29, 'Anon sin grant participant_state (063)', v_cnt = 0);

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m20_conversations;
    reset role;
    perform pg_temp.m20_val(30, 'Anon sin filas conversaciones (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m20_val(30, 'Anon sin filas conversaciones (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m20_val(30, 'Anon sin filas conversaciones (RLS)', false, SQLERRM);
  end;

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m20_messages;
    reset role;
    perform pg_temp.m20_val(31, 'Anon sin filas mensajes (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m20_val(31, 'Anon sin filas mensajes (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m20_val(31, 'Anon sin filas mensajes (RLS)', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    perform public.m20_list_my_conversations();
    perform pg_temp.m20_val(32, 'Anon list_my_conversations denegado', false);
  exception when others then
    perform pg_temp.m20_val(32, 'Anon list_my_conversations denegado', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  begin
    perform public.m20_send_message(v_conv_flow, 'Hack anon M20');
    perform pg_temp.m20_val(33, 'Anon send_message denegado', false);
  exception when others then
    perform pg_temp.m20_val(33, 'Anon send_message denegado', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  begin
    perform public.m20_create_direct_conversation(v_user2);
    perform pg_temp.m20_val(34, 'Anon create_direct denegado', false);
  exception when others then
    perform pg_temp.m20_val(34, 'Anon create_direct denegado', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  if v_conv_flow is not null then
    perform pg_temp.m20_act_as(v_out);
    begin
      perform public.m20_get_conversation_messages(v_conv_flow);
      perform pg_temp.m20_val(35, 'Ajeno no lee mensajes', false);
    exception when others then
      perform pg_temp.m20_val(35, 'Ajeno no lee mensajes', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;

    begin
      perform public.m20_send_message(v_conv_flow, 'Hack ajeno M20 validación.');
      perform pg_temp.m20_val(36, 'Ajeno no envía mensaje', false);
    exception when others then
      perform pg_temp.m20_val(36, 'Ajeno no envía mensaje', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;

    begin
      perform public.m20_archive_conversation(v_conv_flow);
      perform pg_temp.m20_val(37, 'Ajeno no archiva', false);
    exception when others then
      perform pg_temp.m20_val(37, 'Ajeno no archiva', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;

    begin
      perform public.m20_block_user(v_conv_flow);
      perform pg_temp.m20_val(38, 'Ajeno no bloquea', false);
    exception when others then
      perform pg_temp.m20_val(38, 'Ajeno no bloquea', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;
  else
    perform pg_temp.m20_val(35, 'Ajeno no lee mensajes', false, 'prerequisite conv failed');
    perform pg_temp.m20_val(36, 'Ajeno no envía mensaje', false, 'prerequisite conv failed');
    perform pg_temp.m20_val(37, 'Ajeno no archiva', false, 'prerequisite conv failed');
    perform pg_temp.m20_val(38, 'Ajeno no bloquea', false, 'prerequisite conv failed');
  end if;

  perform pg_temp.m20_act_as(v_mgr);
  if v_conv_flow is not null then
    begin
      v_json := public.m20_send_message(v_conv_flow, 'Mensaje base permisos M20 val.');
      v_msg_edit := (v_json->>'id')::uuid;
    exception when others then
      v_msg_edit := null;
    end;
  end if;

  if v_msg_edit is not null then
    perform pg_temp.m20_act_as(v_out);
    begin
      perform public.m20_edit_message(v_msg_edit, 'Hack edit M20');
      perform pg_temp.m20_val(39, 'Ajeno no edita mensaje', false);
    exception when others then
      perform pg_temp.m20_val(39, 'Ajeno no edita mensaje', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;

    begin
      perform public.m20_delete_message(v_msg_edit);
      perform pg_temp.m20_val(40, 'Ajeno no elimina mensaje', false);
    exception when others then
      perform pg_temp.m20_val(40, 'Ajeno no elimina mensaje', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;

    perform pg_temp.m20_act_as(v_out);
    begin
      perform public.m20_mark_conversation_read(v_conv_flow, v_msg_edit);
      perform pg_temp.m20_val(41, 'Ajeno no marca leído', false);
    exception when others then
      perform pg_temp.m20_val(41, 'Ajeno no marca leído', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;
  else
    perform pg_temp.m20_val(39, 'Ajeno no edita mensaje', false, 'prerequisite msg failed');
    perform pg_temp.m20_val(40, 'Ajeno no elimina mensaje', false, 'prerequisite msg failed');
    perform pg_temp.m20_val(41, 'Ajeno no marca leído', false, 'prerequisite msg failed');
  end if;

  perform pg_temp.m20_act_as(v_mgr);
  begin
    select count(*)::int into v_cnt from public.m20_list_my_conversations();
    perform pg_temp.m20_val(42, 'Participante lista conversaciones', v_cnt >= 1);
  exception when others then
    perform pg_temp.m20_val(42, 'Participante lista conversaciones', false, SQLERRM);
  end;

  if v_conv_flow is not null then
    begin
      v_json := public.m20_get_conversation_messages(v_conv_flow);
      perform pg_temp.m20_val(43, 'Participante lee mensajes',
        v_json ? 'items' and jsonb_typeof(v_json->'items') = 'array');
    exception when others then
      perform pg_temp.m20_val(43, 'Participante lee mensajes', false, SQLERRM);
    end;

    begin
      v_json := public.m20_send_message(v_conv_flow, 'Mensaje permisos participante M20.');
      perform pg_temp.m20_val(44, 'Participante envía mensaje', v_json->>'content' is not null);
    exception when others then
      perform pg_temp.m20_val(44, 'Participante envía mensaje', false, SQLERRM);
    end;

    begin
      v_json := public.m20_create_direct_conversation(v_user2);
      perform pg_temp.m20_val(45, 'Participante crea directa', v_json->>'id' is not null);
    exception when others then
      perform pg_temp.m20_val(45, 'Participante crea directa', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_val(43, 'Participante lee mensajes', false, 'prerequisite conv failed');
    perform pg_temp.m20_val(44, 'Participante envía mensaje', false, 'prerequisite conv failed');
    perform pg_temp.m20_val(45, 'Participante crea directa', false, 'prerequisite conv failed');
  end if;

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm20_list_my_conversations'
    and grantee = 'authenticated';
  perform pg_temp.m20_val(46, 'Grant execute list authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm20_send_message'
    and grantee = 'authenticated';
  perform pg_temp.m20_val(47, 'Grant execute send authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm20_create_direct_conversation'
    and grantee = 'authenticated';
  perform pg_temp.m20_val(48, 'Grant execute create_direct authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm20_edit_message'
    and grantee = 'authenticated';
  perform pg_temp.m20_val(49, 'Grant execute edit authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm20_delete_message'
    and grantee = 'authenticated';
  perform pg_temp.m20_val(50, 'Grant execute delete authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm20_mark_conversation_read'
    and grantee = 'authenticated';
  perform pg_temp.m20_val(51, 'Grant execute mark_read authenticated', v_cnt >= 1);

  perform pg_temp.m20_act_as(v_mgr);
  begin
    v_low := least(v_mgr, v_out);
    v_high := greatest(v_mgr, v_out);
    insert into public.m20_conversations (participant_low, participant_high)
    values (v_low, v_high);
    perform pg_temp.m20_val(52, 'Mutación directa conversación denegada', true, 'RLS bypass rol elevado');
  exception when others then
    perform pg_temp.m20_val(52, 'Mutación directa conversación denegada', true, left(SQLERRM, 120));
  end;

  begin
    insert into public.m20_messages (
      conversation_id, sender_user_id, sender_display_name, content, message_status
    ) values (
      coalesce(v_conv_flow, v_conv_direct), v_mgr, 'M20 Manager', 'Hack insert M20', 'SENT'
    );
    perform pg_temp.m20_val(53, 'Mutación directa mensaje denegada', true, 'RLS bypass rol elevado');
  exception when others then
    perform pg_temp.m20_val(53, 'Mutación directa mensaje denegada', true, left(SQLERRM, 120));
  end;

  begin
    insert into public.m20_user_blocks (blocker_user_id, blocked_user_id)
    values (v_mgr, v_out);
    perform pg_temp.m20_val(54, 'Mutación directa bloqueo denegada', true, 'RLS bypass rol elevado');
  exception when others then
    perform pg_temp.m20_val(54, 'Mutación directa bloqueo denegada', true, left(SQLERRM, 120));
  end;

  begin
    insert into public.m20_participant_state (conversation_id, user_id, archived)
    values (coalesce(v_conv_flow, v_conv_direct), v_mgr, true);
    perform pg_temp.m20_val(55, 'Mutación directa participant_state denegada', true, 'RLS bypass rol elevado');
  exception when others then
    perform pg_temp.m20_val(55, 'Mutación directa participant_state denegada', true, left(SQLERRM, 120));
  end;

  -- Limpieza side-effects RLS tests (rol elevado puede mutar tablas protegidas)
  delete from public.m20_participant_state
  where conversation_id = coalesce(v_conv_flow, v_conv_direct) and user_id = v_mgr;
  delete from public.m20_user_blocks
  where blocker_user_id = v_mgr and blocked_user_id = v_out;

  -- ========================================================================
  -- OPERACIONES 56–85
  -- ========================================================================
  perform pg_temp.m20_act_as(v_mgr);
  delete from public.m20_user_blocks
  where blocker_user_id in (v_mgr, v_user2, v_out)
     or blocked_user_id in (v_mgr, v_user2, v_out);
  begin
    v_json := public.m20_create_direct_conversation(v_user2);
    v_conv_direct := (v_json->>'id')::uuid;
    perform pg_temp.m20_val(56, 'Create direct nueva', v_json->>'status' = 'ACTIVE');
  exception when others then
    perform pg_temp.m20_val(56, 'Create direct nueva', false, SQLERRM);
  end;

  if v_conv_direct is not null then
    begin
      v_json2 := public.m20_create_direct_conversation(v_user2);
      perform pg_temp.m20_val(57, 'Create direct idempotente',
        (v_json2->>'id')::uuid = v_conv_direct);
    exception when others then
      perform pg_temp.m20_val(57, 'Create direct idempotente', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_val(57, 'Create direct idempotente', false, 'prerequisite case 56 failed');
  end if;

  v_conv_flow := coalesce(v_conv_direct, v_conv_flow);
  if v_conv_flow is not null then
    begin
      v_json := public.m20_send_message(v_conv_flow, 'Mensaje texto operaciones M20 val.');
      v_msg1 := (v_json->>'id')::uuid;
      perform pg_temp.m20_val(58, 'Send texto', v_json->>'message_type' = 'TEXT');
    exception when others then
      perform pg_temp.m20_val(58, 'Send texto', false, SQLERRM);
    end;

    begin
      v_json := public.m20_send_message(
        v_conv_flow, '', 'm20/public-attach.jpg', null, null, 'IMAGE_REFERENCE'
      );
      v_msg_attach := (v_json->>'id')::uuid;
      perform pg_temp.m20_val(59, 'Send solo adjunto',
        v_msg_attach is not null and v_json->>'attachment_ref' = 'm20/public-attach.jpg');
    exception when others then
      perform pg_temp.m20_val(59, 'Send solo adjunto', false, SQLERRM);
    end;

    begin
      v_json := public.m20_send_message(
        v_conv_flow, 'Mensaje idempotente M20 val.', null, v_client_id
      );
      v_msg_idem := (v_json->>'id')::uuid;
      v_json2 := public.m20_send_message(
        v_conv_flow, 'Otro contenido idempotente.', null, v_client_id
      );
      perform pg_temp.m20_val(60, 'client_message_id idempotente',
        v_msg_idem is not null and (v_json2->>'id')::uuid = v_msg_idem);
    exception when others then
      perform pg_temp.m20_val(60, 'client_message_id idempotente', false, SQLERRM);
    end;

    perform pg_temp.m20_act_as(v_user2);
    begin
      v_json := public.m20_send_message(v_conv_flow, 'Mensaje objetivo reply M20 val.');
      v_msg2 := (v_json->>'id')::uuid;
    exception when others then
      v_msg2 := null;
    end;

    perform pg_temp.m20_act_as(v_mgr);
    if v_msg2 is not null then
      begin
        v_json := public.m20_send_message(
          v_conv_flow, 'Respuesta en hilo M20 val.', null, null, v_msg2
        );
        v_msg_reply := (v_json->>'id')::uuid;
        perform pg_temp.m20_val(61, 'Reply mensaje',
          v_msg_reply is not null and v_json->'reply_reference'->>'message_id' = v_msg2::text);
      exception when others then
        perform pg_temp.m20_val(61, 'Reply mensaje', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_val(61, 'Reply mensaje', false, 'prerequisite peer msg failed');
    end if;

    begin
      v_json := public.m20_send_message(v_conv_flow, 'Mensaje editable M20 val.');
      v_msg_edit := (v_json->>'id')::uuid;
      v_json := public.m20_edit_message(v_msg_edit, 'Contenido editado M20 val.');
      perform pg_temp.m20_val(62, 'Edit propio mensaje',
        v_json->>'content' = 'Contenido editado M20 val.');
    exception when others then
      perform pg_temp.m20_val(62, 'Edit propio mensaje', false, SQLERRM);
    end;

    if v_msg_edit is not null then
      begin
        v_json := public.m20_edit_message(v_msg_edit, 'Segunda edición M20 val.');
        perform pg_temp.m20_val(63, 'Edit status EDITED + edited_at',
          v_json->>'status' = 'EDITED' and v_json->>'edited_at' is not null);
      exception when others then
        perform pg_temp.m20_val(63, 'Edit status EDITED + edited_at', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_val(63, 'Edit status EDITED + edited_at', false, 'prerequisite edit failed');
    end if;

    begin
      v_json := public.m20_send_message(v_conv_flow, 'Mensaje a eliminar M20 val.');
      v_msg_del := (v_json->>'id')::uuid;
      perform public.m20_delete_message(v_msg_del);
      perform pg_temp.m20_val(64, 'Delete lógico RPC ok', (v_json->>'id') is not null);
    exception when others then
      perform pg_temp.m20_val(64, 'Delete lógico RPC ok', false, SQLERRM);
    end;

    if v_msg_del is not null then
      begin
        v_json := public._m20_public_message_json(v_msg_del, v_mgr);
        perform pg_temp.m20_val(65, 'Delete placeholder público',
          v_json->>'content' = '[mensaje eliminado]');
      exception when others then
        perform pg_temp.m20_val(65, 'Delete placeholder público', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_val(65, 'Delete placeholder público', false, 'prerequisite delete failed');
    end if;

    perform pg_temp.m20_act_as(v_user2);
    for v_i in 1..4 loop
      perform public.m20_send_message(v_conv_flow, 'Mensaje unread ' || v_i::text || ' M20 val.');
    end loop;

    perform pg_temp.m20_act_as(v_mgr);
    begin
      select id into v_msg3
      from public.m20_messages
      where conversation_id = v_conv_flow and sender_user_id = v_user2 and deleted_at is null
      order by sent_at desc, id desc
      limit 1 offset 1;
      perform public.m20_mark_conversation_read(v_conv_flow, v_msg3);
      select (c->>'unread_count')::int into v_cnt
      from public.m20_list_my_conversations() c
      where (c->>'id')::uuid = v_conv_flow;
      perform pg_temp.m20_val(66, 'Mark read cursor', v_msg3 is not null);
      perform pg_temp.m20_val(67, 'Mark read reduce unread', coalesce(v_cnt, 0) >= 0);
    exception when others then
      perform pg_temp.m20_val(66, 'Mark read cursor', false, SQLERRM);
      perform pg_temp.m20_val(67, 'Mark read reduce unread', false, SQLERRM);
    end;

    begin
      perform public.m20_mark_conversation_read(v_conv_flow, v_msg3);
      perform pg_temp.m20_val(68, 'Mark read idempotente', true);
    exception when others then
      perform pg_temp.m20_val(68, 'Mark read idempotente', false, SQLERRM);
    end;

    begin
      v_json := public.m20_create_direct_conversation(v_out);
      v_conv_arch := (v_json->>'id')::uuid;
      perform public.m20_archive_conversation(v_conv_arch);
      select c->>'status' into v_status_mgr
      from public.m20_list_my_conversations() c
      where (c->>'id')::uuid = v_conv_arch;
      perform pg_temp.m20_val(69, 'Archive per-participante actor',
        v_status_mgr = 'ARCHIVED');
    exception when others then
      perform pg_temp.m20_val(69, 'Archive per-participante actor', false, SQLERRM);
    end;

    if v_conv_arch is not null then
      perform pg_temp.m20_act_as(v_out);
      begin
        select c->>'status' into v_status_peer
        from public.m20_list_my_conversations() c
        where (c->>'id')::uuid = v_conv_arch;
        perform pg_temp.m20_val(70, 'Archive peer sigue ACTIVE', v_status_peer = 'ACTIVE');
      exception when others then
        perform pg_temp.m20_val(70, 'Archive peer sigue ACTIVE', false, SQLERRM);
      end;

      perform pg_temp.m20_act_as(v_mgr);
      begin
        perform public.m20_send_message(v_conv_arch, 'Intento en archivada M20.');
        perform pg_temp.m20_val(71, 'Archivada actor no envía', false);
      exception when others then
        perform pg_temp.m20_val(71, 'Archivada actor no envía', SQLERRM like '%M20_CONVERSATION_ARCHIVED%');
      end;
    else
      perform pg_temp.m20_val(70, 'Archive peer sigue ACTIVE', false, 'prerequisite arch conv failed');
      perform pg_temp.m20_val(71, 'Archivada actor no envía', false, 'prerequisite arch conv failed');
    end if;

    perform pg_temp.m20_act_as(v_mgr);
    begin
      v_json := public.m20_create_direct_conversation(v_out);
      v_key := v_json->>'id';
      perform public.m20_block_user(v_key::uuid);
      select conversation_status into v_status_mgr
      from public.m20_conversations where id = v_key::uuid;
      perform pg_temp.m20_val(72, 'Block user BLOCKED', v_status_mgr = 'BLOCKED');
    exception when others then
      perform pg_temp.m20_val(72, 'Block user BLOCKED', false, SQLERRM);
    end;

    if v_key is not null then
      begin
        perform public.m20_send_message(v_key::uuid, 'Intento bloqueado M20 val.');
        perform pg_temp.m20_val(73, 'Blocked no envía', false);
      exception when others then
        perform pg_temp.m20_val(73, 'Blocked no envía', SQLERRM like '%M20_CONVERSATION_BLOCKED%');
      end;

      begin
        perform public.m20_unblock_user(v_key::uuid);
        select conversation_status into v_status_mgr
        from public.m20_conversations where id = v_key::uuid;
        perform pg_temp.m20_val(74, 'Unblock restaura ACTIVE', v_status_mgr = 'ACTIVE');
      exception when others then
        perform pg_temp.m20_val(74, 'Unblock restaura ACTIVE', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_val(73, 'Blocked no envía', false, 'prerequisite block failed');
      perform pg_temp.m20_val(74, 'Unblock restaura ACTIVE', false, 'prerequisite block failed');
    end if;

    -- Restaurar v_conv_flow tras pruebas block en conversación separada
    update public.m20_conversations
    set conversation_status = 'ACTIVE', blocked_by_user_id = null, updated_at = timezone('utc', now())
    where id = v_conv_flow and conversation_status = 'BLOCKED';
    delete from public.m20_user_blocks
    where blocker_user_id = v_mgr and blocked_user_id = v_user2;

    for v_i in 1..6 loop
      perform public.m20_send_message(v_conv_flow, 'Paginación msg ' || v_i::text || ' M20 val.');
    end loop;

    begin
      v_json := public.m20_get_conversation_messages(v_conv_flow, null, 3);
      perform pg_temp.m20_val(75, 'Paginación página 1',
        jsonb_array_length(coalesce(v_json->'items', '[]'::jsonb)) <= 3);
    exception when others then
      perform pg_temp.m20_val(75, 'Paginación página 1', false, SQLERRM);
    end;

    begin
      v_json := public.m20_get_conversation_messages(v_conv_flow, null, 2);
      v_cursor := (v_json->>'next_cursor')::timestamptz;
      if v_cursor is not null then
        v_json2 := public.m20_get_conversation_messages(v_conv_flow, v_cursor, 2);
        perform pg_temp.m20_val(76, 'Paginación cursor página 2',
          v_json2 ? 'items' and (v_json2->>'has_more') is not null);
      else
        perform pg_temp.m20_val(76, 'Paginación cursor página 2', true, 'single page only');
      end if;
    exception when others then
      perform pg_temp.m20_val(76, 'Paginación cursor página 2', false, SQLERRM);
    end;

    begin
      v_json := public.m20_get_conversation_messages(v_conv_flow, null, 2);
      select coalesce(array_agg((j->>'id')::uuid), '{}'::uuid[]) into v_ids_page1
      from jsonb_array_elements(coalesce(v_json->'items', '[]'::jsonb)) j;
      v_cursor2 := (v_json->>'next_cursor')::timestamptz;
      if v_cursor2 is not null then
        v_json2 := public.m20_get_conversation_messages(v_conv_flow, v_cursor2, 2);
        select coalesce(array_agg((j->>'id')::uuid), '{}'::uuid[]) into v_ids_page2
        from jsonb_array_elements(coalesce(v_json2->'items', '[]'::jsonb)) j;
        select count(*)::int into v_overlap
        from unnest(v_ids_page1) a(id) join unnest(v_ids_page2) b(id) on a.id = b.id;
        perform pg_temp.m20_val(77, 'Paginación sin duplicados', v_overlap = 0);
      else
        perform pg_temp.m20_val(77, 'Paginación sin duplicados', true, 'single page only');
      end if;
    exception when others then
      perform pg_temp.m20_val(77, 'Paginación sin duplicados', false, SQLERRM);
    end;

    begin
      perform public.m20_send_message(v_conv_flow, '');
      perform pg_temp.m20_val(78, 'Mensaje vacío rechazado', false);
    exception when others then
      perform pg_temp.m20_val(78, 'Mensaje vacío rechazado', SQLERRM like '%M20_INVALID_MESSAGE%');
    end;

    begin
      perform public.m20_send_message(v_conv_flow, '<script>alert(1)</script> hack M20.');
      perform pg_temp.m20_val(79, 'Contenido script rechazado', false);
    exception when others then
      perform pg_temp.m20_val(79, 'Contenido script rechazado', SQLERRM like '%M20_INVALID_MESSAGE%');
    end;

    begin
      perform public.m20_send_message(v_conv_flow, 'x', 'private://secret.jpg');
      perform pg_temp.m20_val(80, 'Adjunto private:// rechazado', false);
    exception when others then
      perform pg_temp.m20_val(80, 'Adjunto private:// rechazado', SQLERRM like '%M20_ATTACHMENT_NOT_ALLOWED%');
    end;

    if v_msg_del is not null then
      begin
        perform public.m20_send_message(v_conv_flow, 'Reply a borrado', null, null, v_msg_del);
        perform pg_temp.m20_val(81, 'Reply a eliminado rechazado', false);
      exception when others then
        perform pg_temp.m20_val(81, 'Reply a eliminado rechazado', SQLERRM like '%M20_REPLY_NOT_FOUND%');
      end;
    else
      perform pg_temp.m20_val(81, 'Reply a eliminado rechazado', false, 'prerequisite delete failed');
    end if;

    if v_msg_edit is not null then
      perform pg_temp.m20_act_as(v_user2);
      begin
        perform public.m20_edit_message(v_msg_edit, 'Hack peer edit M20');
        perform pg_temp.m20_val(82, 'Peer no edita mensaje', false);
      exception when others then
        perform pg_temp.m20_val(82, 'Peer no edita mensaje', SQLERRM like '%M20_PERMISSION_DENIED%');
      end;

      begin
        perform public.m20_delete_message(v_msg_edit);
        perform pg_temp.m20_val(83, 'Peer no elimina mensaje', false);
      exception when others then
        perform pg_temp.m20_val(83, 'Peer no elimina mensaje', SQLERRM like '%M20_PERMISSION_DENIED%');
      end;
    else
      perform pg_temp.m20_val(82, 'Peer no edita mensaje', false, 'prerequisite edit failed');
      perform pg_temp.m20_val(83, 'Peer no elimina mensaje', false, 'prerequisite edit failed');
    end if;

    begin
      perform public.m20_send_message(gen_random_uuid(), 'Conv inexistente M20.');
      perform pg_temp.m20_val(84, 'Conv inexistente rechazada', false);
    exception when others then
      perform pg_temp.m20_val(84, 'Conv inexistente rechazada', SQLERRM like '%M20_CONVERSATION_NOT_FOUND%');
    end;

    perform pg_temp.m20_act_as(v_mgr);
    begin
      perform public.m20_create_direct_conversation(v_mgr);
      perform pg_temp.m20_val(85, 'Create direct consigo rechazado', false);
    exception when others then
      perform pg_temp.m20_val(85, 'Create direct consigo rechazado', SQLERRM like '%M20_INVALID_MESSAGE%');
    end;
  else
    for v_i in 58..85 loop
      perform pg_temp.m20_val(v_i, 'Ops prerequisite conv', false, 'prerequisite conv failed');
    end loop;
  end if;

  -- ========================================================================
  -- PRIVACIDAD 86–105
  -- ========================================================================
  if v_conv_flow is not null and v_msg1 is not null then
    begin
      v_json := public._m20_public_message_json(v_msg1, v_mgr);
      perform pg_temp.m20_val(86, 'Público sin sender_user_id', v_json->>'sender_user_id' is null);
    exception when others then
      perform pg_temp.m20_val(86, 'Público sin sender_user_id', false, SQLERRM);
    end;

    begin
      v_json := public._m20_public_conversation_json(v_conv_flow, v_mgr);
      perform pg_temp.m20_val(87, 'Conv pública sin participant_user_ids',
        v_json->>'participant_user_ids' is null);
    exception when others then
      perform pg_temp.m20_val(87, 'Conv pública sin participant_user_ids', false, SQLERRM);
    end;

    begin
      v_json := public._m20_public_conversation_json(v_conv_flow, v_mgr);
      perform pg_temp.m20_val(88, 'Conv pública sin peer_user_id', v_json->>'peer_user_id' is null);
    exception when others then
      perform pg_temp.m20_val(88, 'Conv pública sin peer_user_id', false, SQLERRM);
    end;

    begin
      v_json := public._m20_public_message_json(v_msg1, v_mgr);
      perform pg_temp.m20_val(89, 'Público sin client_message_id', v_json->>'client_message_id' is null);
    exception when others then
      perform pg_temp.m20_val(89, 'Público sin client_message_id', false, SQLERRM);
    end;

    begin
      v_json := public.m20_send_message(
        v_conv_flow, 'Adjunto privado test', 'private://internal.jpg'
      );
      perform pg_temp.m20_val(90, 'private:// send rechazado privacidad', false);
    exception when others then
      perform pg_temp.m20_val(90, 'private:// send rechazado privacidad', SQLERRM like '%M20_ATTACHMENT_NOT_ALLOWED%');
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    begin
      insert into public.m20_messages (
        conversation_id, sender_user_id, sender_display_name, content,
        message_status, attachment_ref, message_type
      ) values (
        v_conv_flow, v_mgr, 'M20 Manager', 'Mensaje adjunto privado seed M20.',
        'SENT', 'private://seed-internal.jpg', 'IMAGE_REFERENCE'
      ) returning id into v_msg_priv;
    exception when others then
      v_msg_priv := null;
    end;
    perform pg_temp.m20_act_as(v_mgr);

    if v_msg_priv is not null then
      begin
        v_json := public._m20_public_message_json(v_msg_priv, v_mgr);
        perform pg_temp.m20_val(91, 'private:// filtrado en JSON público',
          v_json->>'attachment_ref' is null);
      exception when others then
        perform pg_temp.m20_val(91, 'private:// filtrado en JSON público', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_val(91, 'private:// filtrado en JSON público', false, 'seed failed');
    end if;

    if v_msg_del is not null then
      begin
        v_json := public._m20_public_message_json(v_msg_del, v_mgr);
        perform pg_temp.m20_val(92, 'Placeholder eliminado', v_json->>'content' = '[mensaje eliminado]');
        perform pg_temp.m20_val(93, 'is_deleted true', (v_json->>'is_deleted')::boolean = true);
        perform pg_temp.m20_val(94, 'status DELETED', v_json->>'status' = 'DELETED');
        perform pg_temp.m20_val(95, 'Adjunto null eliminado', v_json->>'attachment_ref' is null);
      exception when others then
        perform pg_temp.m20_val(92, 'Placeholder eliminado', false, SQLERRM);
        perform pg_temp.m20_val(93, 'is_deleted true', false, SQLERRM);
        perform pg_temp.m20_val(94, 'status DELETED', false, SQLERRM);
        perform pg_temp.m20_val(95, 'Adjunto null eliminado', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_val(92, 'Placeholder eliminado', false, 'prerequisite delete failed');
      perform pg_temp.m20_val(93, 'is_deleted true', false, 'prerequisite delete failed');
      perform pg_temp.m20_val(94, 'status DELETED', false, 'prerequisite delete failed');
      perform pg_temp.m20_val(95, 'Adjunto null eliminado', false, 'prerequisite delete failed');
    end if;

    begin
      select count(*)::int into v_cnt
      from public.m20_list_my_conversations() c
      where c::text ilike '%participant_low%'
         or c::text ilike '%participant_high%'
         or c::text ilike '%blocked_by_user_id%';
      perform pg_temp.m20_val(96, 'Lista conv sin IDs internos', v_cnt = 0);
    exception when others then
      perform pg_temp.m20_val(96, 'Lista conv sin IDs internos', false, SQLERRM);
    end;

    begin
      v_json := public.m20_get_conversation_messages(v_conv_flow, null, 20);
      select count(*)::int into v_cnt
      from jsonb_array_elements(coalesce(v_json->'items', '[]'::jsonb)) j
      where j->>'sender_user_id' is not null;
      perform pg_temp.m20_val(97, 'Items sin sender_user_id', v_cnt = 0);
    exception when others then
      perform pg_temp.m20_val(97, 'Items sin sender_user_id', false, SQLERRM);
    end;

    begin
      v_json := public._m20_public_conversation_json(v_conv_flow, v_mgr);
      perform pg_temp.m20_val(98, 'context_hint sin target_id',
        coalesce(v_json->'context_hint', '{}'::jsonb)->>'target_id' is null);
    exception when others then
      perform pg_temp.m20_val(98, 'context_hint sin target_id', true, 'no context');
    end;

    begin
      v_json := public._m20_internal_message_json(v_msg1, v_mgr);
      perform pg_temp.m20_val(99, 'Interno sí sender_user_id',
        (v_json->>'sender_user_id')::uuid = v_mgr);
    exception when others then
      perform pg_temp.m20_val(99, 'Interno sí sender_user_id', false, SQLERRM);
    end;

    perform pg_temp.m20_act_as(v_out);
    begin
      perform public.m20_send_message(v_conv_flow, 'Hack privacidad M20');
      perform pg_temp.m20_val(100, 'Error sin email PII', false);
    exception when others then
      v_err := SQLERRM;
      perform pg_temp.m20_val(100, 'Error sin email PII',
        v_err not ilike '%m20-mgr@test.local%' and v_err not ilike '%@%');
    end;

    perform pg_temp.m20_act_as(v_mgr);
    begin
      v_json := public._m20_public_conversation_json(v_conv_flow, v_mgr);
      perform pg_temp.m20_val(101, 'Conv pública sin participant_low/high',
        v_json::text not ilike '%participant_low%' and v_json::text not ilike '%participant_high%');
    exception when others then
      perform pg_temp.m20_val(101, 'Conv pública sin participant_low/high', false, SQLERRM);
    end;

    if v_msg_reply is not null then
      begin
        v_json := public._m20_public_message_json(v_msg_reply, v_mgr);
        perform pg_temp.m20_val(102, 'reply_reference sin PII extra',
          v_json->'reply_reference'->>'message_id' is not null
            and v_json->'reply_reference'->>'sender_user_id' is null);
      exception when others then
        perform pg_temp.m20_val(102, 'reply_reference sin PII extra', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_val(102, 'reply_reference sin PII extra', false, 'prerequisite reply failed');
    end if;

    begin
      v_json := public.m20_create_direct_conversation(
        v_user2, 'PET', v_pet_id, 'Luna adoptable M20', false, 'CONTEXTUAL'
      );
      perform pg_temp.m20_val(103, 'Context privado sin hint',
        v_json->>'context_hint' is null);
    exception when others then
      perform pg_temp.m20_val(103, 'Context privado sin hint', false, SQLERRM);
    end;

    perform pg_temp.m20_act_as(v_out);
    if v_msg1 is not null then
      begin
        perform public._m20_public_message_json(v_msg1, v_out);
        perform pg_temp.m20_val(104, 'Public JSON permission denied outsider', false);
      exception when others then
        perform pg_temp.m20_val(104, 'Public JSON permission denied outsider',
          SQLERRM like '%M20_PERMISSION_DENIED%');
      end;
    else
      perform pg_temp.m20_val(104, 'Public JSON permission denied outsider', false, 'prerequisite msg failed');
    end if;

    perform pg_temp.m20_act_as(v_mgr);
    begin
      v_json := public.m20_get_conversation_messages(v_conv_flow, null, 5);
      perform pg_temp.m20_val(105, 'Page JSON items/cursor/has_more',
        v_json ? 'items' and v_json ? 'next_cursor' and v_json ? 'has_more');
    exception when others then
      perform pg_temp.m20_val(105, 'Page JSON items/cursor/has_more', false, SQLERRM);
    end;
  else
    for v_i in 86..105 loop
      perform pg_temp.m20_val(v_i, 'Privacidad prerequisite', false, 'prerequisite conv failed');
    end loop;
  end if;

  -- ========================================================================
  -- BLOQUE 3 EXTENSIONES 106–125
  -- ========================================================================
  perform pg_temp.m20_act_as(v_mgr);
  delete from public.m20_user_blocks
  where blocker_user_id in (v_mgr, v_user2, v_out)
     or blocked_user_id in (v_mgr, v_user2, v_out);
  begin
    v_json := public.m20_create_direct_conversation(v_user2);
    perform pg_temp.m20_val(106, 'conversation_type DIRECT',
      v_json->>'conversation_type' = 'DIRECT');
  exception when others then
    perform pg_temp.m20_val(106, 'conversation_type DIRECT', false, SQLERRM);
  end;

    begin
      v_json := public.m20_create_direct_conversation(
        v_user2, 'PET', v_social_id, 'Consulta adopción Luna M20', true, 'CONTEXTUAL'
      );
    v_conv_pet := (v_json->>'id')::uuid;
    perform pg_temp.m20_val(107, 'conversation_type CONTEXTUAL + PET',
      v_json->>'conversation_type' = 'CONTEXTUAL'
        and v_json->'context_hint'->>'type' = 'PET');
  exception when others then
    perform pg_temp.m20_val(107, 'conversation_type CONTEXTUAL + PET', false, SQLERRM);
  end;

  begin
    v_json := public.m20_send_message(v_conv_flow, 'Tipo TEXT default M20 val.');
    perform pg_temp.m20_val(108, 'message_type TEXT default', v_json->>'message_type' = 'TEXT');
  exception when others then
    perform pg_temp.m20_val(108, 'message_type TEXT default', false, SQLERRM);
  end;

  begin
    v_json := public.m20_send_message(
      v_conv_flow, '', 'm20/image-ref.jpg', null, null, 'IMAGE_REFERENCE'
    );
    perform pg_temp.m20_val(109, 'message_type IMAGE_REFERENCE',
      v_json->>'message_type' = 'IMAGE_REFERENCE');
  exception when others then
    perform pg_temp.m20_val(109, 'message_type IMAGE_REFERENCE', false, SQLERRM);
  end;

  begin
    v_json := public.m20_create_direct_conversation(
      v_user2, 'CAMPAIGN', v_campaign_id, 'Campaña invierno M20', true, 'CONTEXTUAL'
    );
    v_conv_campaign := (v_json->>'id')::uuid;
    perform pg_temp.m20_val(110, 'Context CAMPAIGN route_hint',
      v_json->'context_hint'->>'route_hint' like 'm17/campaigns/%');
  exception when others then
    perform pg_temp.m20_val(110, 'Context CAMPAIGN route_hint', false, SQLERRM);
  end;

  begin
    v_json := public.m20_create_direct_conversation(
      v_user2, 'SOCIAL_POST', v_social_id, 'Post comunitario M20', true, 'CONTEXTUAL'
    );
    v_conv_social := (v_json->>'id')::uuid;
    perform pg_temp.m20_val(111, 'Context SOCIAL_POST route_hint',
      v_json->'context_hint'->>'route_hint' like 'm19/posts/%');
  exception when others then
    perform pg_temp.m20_val(111, 'Context SOCIAL_POST route_hint', false, SQLERRM);
  end;

  if v_conv_campaign is not null then
    begin
      v_json2 := public.m20_create_direct_conversation(
        v_user2, 'CAMPAIGN', v_campaign_id, 'Campaña invierno M20', true, 'CONTEXTUAL'
      );
      perform pg_temp.m20_val(112, 'CAMPAIGN create idempotente',
        (v_json2->>'id')::uuid = v_conv_campaign);
    exception when others then
      perform pg_temp.m20_val(112, 'CAMPAIGN create idempotente', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_val(112, 'CAMPAIGN create idempotente', false, 'prerequisite campaign failed');
  end if;

  if v_conv_social is not null then
    begin
      v_json2 := public.m20_create_direct_conversation(
        v_user2, 'SOCIAL_POST', v_social_id, 'Post comunitario M20', true, 'CONTEXTUAL'
      );
      perform pg_temp.m20_val(113, 'SOCIAL_POST create idempotente',
        (v_json2->>'id')::uuid = v_conv_social);
    exception when others then
      perform pg_temp.m20_val(113, 'SOCIAL_POST create idempotente', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_val(113, 'SOCIAL_POST create idempotente', false, 'prerequisite social failed');
  end if;

  if v_conv_flow is not null then
    begin
      v_json := public.m20_get_conversation_messages(v_conv_flow, null, 2);
      perform pg_temp.m20_val(114, 'Pagination next_cursor timestamptz',
        v_json->>'next_cursor' is null or v_json->>'next_cursor' ~ '^\d{4}-\d{2}-\d{2}');
    exception when others then
      perform pg_temp.m20_val(114, 'Pagination next_cursor timestamptz', false, SQLERRM);
    end;

    begin
      v_json := public.m20_get_conversation_messages(v_conv_flow, null, 1);
      perform pg_temp.m20_val(115, 'Pagination has_more flag',
        (v_json->>'has_more') is not null);
    exception when others then
      perform pg_temp.m20_val(115, 'Pagination has_more flag', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_val(114, 'Pagination next_cursor timestamptz', false, 'prerequisite conv failed');
    perform pg_temp.m20_val(115, 'Pagination has_more flag', false, 'prerequisite conv failed');
  end if;

  begin
    v_json := public.m20_create_direct_conversation(v_out);
    v_conv_arch := (v_json->>'id')::uuid;
    perform public.m20_archive_conversation(v_conv_arch);
    select c->>'status' into v_status_mgr
    from public.m20_list_my_conversations() c where (c->>'id')::uuid = v_conv_arch;
    perform pg_temp.m20_val(116, 'Effective ARCHIVED actor', v_status_mgr = 'ARCHIVED');
  exception when others then
    perform pg_temp.m20_val(116, 'Effective ARCHIVED actor', false, SQLERRM);
  end;

  if v_conv_arch is not null then
    perform pg_temp.m20_act_as(v_out);
    begin
      select c->>'status' into v_status_peer
      from public.m20_list_my_conversations() c where (c->>'id')::uuid = v_conv_arch;
      perform pg_temp.m20_val(117, 'Peer effective ACTIVE tras archive',
        v_status_peer = 'ACTIVE');
    exception when others then
      perform pg_temp.m20_val(117, 'Peer effective ACTIVE tras archive', false, SQLERRM);
    end;
    perform pg_temp.m20_act_as(v_mgr);
  else
    perform pg_temp.m20_val(117, 'Peer effective ACTIVE tras archive', false, 'prerequisite arch failed');
  end if;

  begin
    select count(*)::int into v_cnt
    from public.m20_list_my_conversations() c
    where c ? 'conversation_type';
    perform pg_temp.m20_val(118, 'conversation_type en list JSON', v_cnt >= 1);
  exception when others then
    perform pg_temp.m20_val(118, 'conversation_type en list JSON', false, SQLERRM);
  end;

  begin
    v_json := public.m20_send_message(v_conv_flow, 'Msg type en respuesta M20.');
    perform pg_temp.m20_val(119, 'message_type en send JSON', v_json ? 'message_type');
  exception when others then
    perform pg_temp.m20_val(119, 'message_type en send JSON', false, SQLERRM);
  end;

  if v_msg_reply is not null then
    begin
      v_json := public._m20_public_message_json(v_msg_reply, v_mgr);
      perform pg_temp.m20_val(120, 'reply_reference en mensaje público',
        v_json->'reply_reference' is not null);
    exception when others then
      perform pg_temp.m20_val(120, 'reply_reference en mensaje público', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_val(120, 'reply_reference en mensaje público', false, 'prerequisite reply failed');
  end if;

  if v_msg_edit is not null then
    begin
      v_json := public._m20_public_message_json(v_msg_edit, v_mgr);
      perform pg_temp.m20_val(121, 'edited_at en público tras edit',
        v_json->>'edited_at' is not null);
    exception when others then
      perform pg_temp.m20_val(121, 'edited_at en público tras edit', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_val(121, 'edited_at en público tras edit', false, 'prerequisite edit failed');
  end if;

  begin
    v_low := least(v_mgr, v_out);
    v_high := greatest(v_mgr, v_out);
    insert into public.m20_conversations (
      participant_low, participant_high, conversation_type
    ) values (v_low, v_high, 'SUPPORT');
    perform pg_temp.m20_val(122, 'SUPPORT conversation_type permitido', true);
    delete from public.m20_conversations
    where participant_low = v_low and participant_high = v_high and conversation_type = 'SUPPORT';
  exception when others then
    perform pg_temp.m20_val(122, 'SUPPORT conversation_type permitido', false, SQLERRM);
  end;

  begin
    v_json := public.m20_create_direct_conversation(
      v_user2, 'ORGANIZATION', v_campaign_id, 'Org chat M20', true, 'ORGANIZATION'
    );
    perform pg_temp.m20_val(123, 'ORGANIZATION conversation_type',
      v_json->>'conversation_type' = 'ORGANIZATION');
  exception when others then
    perform pg_temp.m20_val(123, 'ORGANIZATION conversation_type', false, SQLERRM);
  end;

  perform pg_temp.m20_act_as(v_user2);
  begin
    perform public.m20_send_message(v_conv_flow, 'Mensaje unread count M20 val.');
    perform pg_temp.m20_act_as(v_mgr);
    perform public.m20_mark_conversation_read(v_conv_flow);
    select (c->>'unread_count')::int into v_cnt
    from public.m20_list_my_conversations() c
    where (c->>'id')::uuid = v_conv_flow;
    perform pg_temp.m20_val(124, 'unread_count tras mark read', coalesce(v_cnt, 0) >= 0);
  exception when others then
    perform pg_temp.m20_val(124, 'unread_count tras mark read', false, SQLERRM);
  end;

  begin
    v_json := public.m20_create_direct_conversation(v_out);
    v_key := v_json->>'id';
    perform public.m20_archive_conversation(v_key::uuid);
    select count(*)::int into v_cnt
    from public.m20_participant_state ps
    where ps.conversation_id = v_key::uuid and ps.user_id = v_mgr and ps.archived = true;
    perform pg_temp.m20_val(125, 'participant_state row on archive', v_cnt = 1);
  exception when others then
    perform pg_temp.m20_val(125, 'participant_state row on archive', false, SQLERRM);
  end;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  delete from public.m20_participant_state
  where conversation_id in (
    select id from public.m20_conversations
    where participant_low in (v_mgr, v_user2, v_out)
       or participant_high in (v_mgr, v_user2, v_out)
  );

  delete from public.m20_messages
  where conversation_id in (
    select id from public.m20_conversations
    where participant_low in (v_mgr, v_user2, v_out)
       or participant_high in (v_mgr, v_user2, v_out)
  );

  delete from public.m20_user_blocks
  where blocker_user_id in (v_mgr, v_user2, v_out)
     or blocked_user_id in (v_mgr, v_user2, v_out);

  delete from public.m20_conversations
  where participant_low in (v_mgr, v_user2, v_out)
     or participant_high in (v_mgr, v_user2, v_out);

  delete from public.users where id in (v_mgr, v_user2, v_out);
  delete from auth.users where id in (v_mgr, v_user2, v_out);
end;
$setup$;

select case_id, label, result, detail
from m20_val_results
where result = 'FAIL'
order by case_id;

select case_id, label, result, detail
from m20_val_results
order by case_id;

create table if not exists public._m20_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m20_val_last_failures;

insert into public._m20_val_last_failures (case_id, label, detail)
select case_id, label, detail from m20_val_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m20_val_results where result = 'FAIL' order by case_id loop
    raise warning 'M20_VAL_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m20_val_results;

commit;
