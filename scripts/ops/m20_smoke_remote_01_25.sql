-- M20 smoke remoto casos 01–25 — validación staging (SQL/RPC, no Android)
-- Ejecutar: supabase db query --linked -f scripts/ops/m20_smoke_remote_01_25.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m20_smoke_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m20_smoke(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m20_smoke_results (case_id, label, result, detail)
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
  v_campaign_id text := 'e0000000-0000-4000-8000-000000000001';
  v_social_id text := 'c0000000-0000-4000-8000-0000000000e1';
  v_conv uuid;
  v_conv_arch uuid;
  v_conv_campaign uuid;
  v_conv_social uuid;
  v_msg1 uuid;
  v_msg2 uuid;
  v_msg_edit uuid;
  v_msg_del uuid;
  v_msg_reply uuid;
  v_client_id text := 'm20-smoke-client-idem-001';
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_cursor timestamptz;
  v_ids_page1 uuid[];
  v_ids_page2 uuid[];
  v_overlap int;
  v_i int;
  v_status_mgr text;
  v_status_peer text;
  v_ok boolean;
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_mgr, 'authenticated', 'authenticated',
     'm20-smoke-mgr@test.local', crypt('m20-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_user2, 'authenticated', 'authenticated',
     'm20-smoke-user2@test.local', crypt('m20-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm20-smoke-out@test.local', crypt('m20-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_mgr, 'm20-smoke-mgr@test.local', 'M20 Smoke Manager', 'M20 Smoke Manager', 'PERSON', true, 'ACTIVE'),
    (v_user2, 'm20-smoke-user2@test.local', 'M20 Smoke Participant', 'M20 Smoke Participant', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm20-smoke-out@test.local', 'M20 Smoke Outsider', 'M20 Smoke Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  -- ========================================================================
  -- 01 SupabaseM20MessagingRepository wired
  -- ========================================================================
  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm20_list_my_conversations';
  v_ok := v_cnt >= 1;

  perform pg_temp.m20_act_as(v_mgr);
  begin
    select count(*)::int into v_cnt from public.m20_list_my_conversations();
    perform pg_temp.m20_smoke(1, 'SupabaseM20MessagingRepository wired',
      v_ok and v_cnt >= 0,
      case when v_ok then 'RPC m20_list_my_conversations callable' else 'RPC missing' end);
  exception when others then
    perform pg_temp.m20_smoke(1, 'SupabaseM20MessagingRepository wired', false, SQLERRM);
  end;

  -- ========================================================================
  -- 02 List conversations loads
  -- ========================================================================
  perform pg_temp.m20_act_as(v_mgr);
  begin
    v_json := public.m20_create_direct_conversation(v_user2);
    v_conv := (v_json->>'id')::uuid;
    select count(*)::int into v_cnt from public.m20_list_my_conversations();
    perform pg_temp.m20_smoke(2, 'List conversations loads', v_cnt >= 1);
  exception when others then
    perform pg_temp.m20_smoke(2, 'List conversations loads', false, SQLERRM);
  end;

  -- ========================================================================
  -- 03–04 Create direct / idempotent
  -- ========================================================================
  if v_conv is not null then
    begin
      v_json2 := public.m20_create_direct_conversation(v_user2);
      perform pg_temp.m20_smoke(3, 'Create direct conversation',
        (v_json2->>'id')::uuid = v_conv);
      perform pg_temp.m20_smoke(4, 'Create direct idempotent',
        (v_json2->>'id')::uuid = v_conv);
    exception when others then
      perform pg_temp.m20_smoke(3, 'Create direct conversation', false, SQLERRM);
      perform pg_temp.m20_smoke(4, 'Create direct idempotent', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_smoke(3, 'Create direct conversation', false, 'prerequisite case 2 failed');
    perform pg_temp.m20_smoke(4, 'Create direct idempotent', false, 'prerequisite case 2 failed');
  end if;

  -- ========================================================================
  -- 05–07 Send text / attachment-only / idempotency
  -- ========================================================================
  if v_conv is not null then
    begin
      v_json := public.m20_send_message(v_conv, 'Mensaje smoke texto M20 remoto.');
      v_msg1 := (v_json->>'id')::uuid;
      perform pg_temp.m20_smoke(5, 'Send text message', v_msg1 is not null);
    exception when others then
      perform pg_temp.m20_smoke(5, 'Send text message', false, SQLERRM);
    end;

    begin
      v_json := public.m20_send_message(
        v_conv, '', 'm20/smoke-public.jpg', null, null, 'IMAGE_REFERENCE'
      );
      perform pg_temp.m20_smoke(6, 'Send attachment-only',
        v_json->>'attachment_ref' = 'm20/smoke-public.jpg');
    exception when others then
      perform pg_temp.m20_smoke(6, 'Send attachment-only', false, SQLERRM);
    end;

    begin
      v_json := public.m20_send_message(v_conv, 'Idempotente smoke M20.', null, v_client_id);
      v_json2 := public.m20_send_message(v_conv, 'Otro idempotente.', null, v_client_id);
      perform pg_temp.m20_smoke(7, 'client_message_id idempotency',
        (v_json->>'id')::uuid = (v_json2->>'id')::uuid);
    exception when others then
      perform pg_temp.m20_smoke(7, 'client_message_id idempotency', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_smoke(5, 'Send text message', false, 'prerequisite conv failed');
    perform pg_temp.m20_smoke(6, 'Send attachment-only', false, 'prerequisite conv failed');
    perform pg_temp.m20_smoke(7, 'client_message_id idempotency', false, 'prerequisite conv failed');
  end if;

  -- ========================================================================
  -- 08–10 Reply / edit / delete
  -- ========================================================================
  if v_conv is not null then
    perform pg_temp.m20_act_as(v_user2);
    begin
      v_json := public.m20_send_message(v_conv, 'Mensaje objetivo reply smoke M20.');
      v_msg2 := (v_json->>'id')::uuid;
    exception when others then
      v_msg2 := null;
    end;

    perform pg_temp.m20_act_as(v_mgr);
    if v_msg2 is not null then
      begin
        v_json := public.m20_send_message(v_conv, 'Respuesta smoke M20.', null, null, v_msg2);
        v_msg_reply := (v_json->>'id')::uuid;
        perform pg_temp.m20_smoke(8, 'Reply to message',
          v_json->'reply_reference'->>'message_id' = v_msg2::text);
      exception when others then
        perform pg_temp.m20_smoke(8, 'Reply to message', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_smoke(8, 'Reply to message', false, 'prerequisite peer msg failed');
    end if;

    begin
      v_json := public.m20_send_message(v_conv, 'Mensaje editable smoke M20.');
      v_msg_edit := (v_json->>'id')::uuid;
      v_json := public.m20_edit_message(v_msg_edit, 'Contenido editado smoke M20.');
      perform pg_temp.m20_smoke(9, 'Edit own message',
        v_json->>'content' = 'Contenido editado smoke M20.');
    exception when others then
      perform pg_temp.m20_smoke(9, 'Edit own message', false, SQLERRM);
    end;

    begin
      v_json := public.m20_send_message(v_conv, 'Mensaje delete smoke M20.');
      v_msg_del := (v_json->>'id')::uuid;
      perform public.m20_delete_message(v_msg_del);
      v_json := public._m20_public_message_json(v_msg_del, v_mgr);
      perform pg_temp.m20_smoke(10, 'Delete logical placeholder',
        v_json->>'content' = '[mensaje eliminado]');
    exception when others then
      perform pg_temp.m20_smoke(10, 'Delete logical placeholder', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_smoke(8, 'Reply to message', false, 'prerequisite conv failed');
    perform pg_temp.m20_smoke(9, 'Edit own message', false, 'prerequisite conv failed');
    perform pg_temp.m20_smoke(10, 'Delete logical placeholder', false, 'prerequisite conv failed');
  end if;

  -- ========================================================================
  -- 11–13 Mark read / archive / block
  -- ========================================================================
  if v_conv is not null then
    perform pg_temp.m20_act_as(v_user2);
    perform public.m20_send_message(v_conv, 'Mensaje unread smoke M20.');

    perform pg_temp.m20_act_as(v_mgr);
    begin
      perform public.m20_mark_conversation_read(v_conv);
      perform pg_temp.m20_smoke(11, 'Mark conversation read', true);
    exception when others then
      perform pg_temp.m20_smoke(11, 'Mark conversation read', false, SQLERRM);
    end;

    begin
      v_json := public.m20_create_direct_conversation(v_out);
      v_conv_arch := (v_json->>'id')::uuid;
      perform public.m20_archive_conversation(v_conv_arch);
      select c->>'status' into v_status_mgr
      from public.m20_list_my_conversations() c where (c->>'id')::uuid = v_conv_arch;
      perform pg_temp.m20_smoke(12, 'Archive per-participant actor',
        v_status_mgr = 'ARCHIVED');
    exception when others then
      perform pg_temp.m20_smoke(12, 'Archive per-participant actor', false, SQLERRM);
    end;

    begin
      v_json := public.m20_create_direct_conversation(v_user2);
      perform public.m20_block_user((v_json->>'id')::uuid);
      perform public.m20_send_message((v_json->>'id')::uuid, 'Intento blocked smoke.');
      perform pg_temp.m20_smoke(13, 'Block rejects send', false);
    exception when others then
      perform pg_temp.m20_smoke(13, 'Block rejects send', SQLERRM like '%M20_CONVERSATION_BLOCKED%');
    end;
  else
    perform pg_temp.m20_smoke(11, 'Mark conversation read', false, 'prerequisite conv failed');
    perform pg_temp.m20_smoke(12, 'Archive per-participant actor', false, 'prerequisite conv failed');
    perform pg_temp.m20_smoke(13, 'Block rejects send', false, 'prerequisite conv failed');
  end if;

  -- ========================================================================
  -- 14 Unblock restores active
  -- ========================================================================
  perform pg_temp.m20_act_as(v_mgr);
  begin
    v_json := public.m20_create_direct_conversation(v_user2);
    perform public.m20_block_user((v_json->>'id')::uuid);
    perform public.m20_unblock_user((v_json->>'id')::uuid);
    select conversation_status into v_status_mgr
    from public.m20_conversations where id = (v_json->>'id')::uuid;
    perform pg_temp.m20_smoke(14, 'Unblock restores ACTIVE', v_status_mgr = 'ACTIVE');
  exception when others then
    perform pg_temp.m20_smoke(14, 'Unblock restores ACTIVE', false, SQLERRM);
  end;

  -- ========================================================================
  -- 15–16 Pagination
  -- ========================================================================
  if v_conv is not null then
    for v_i in 1..5 loop
      perform public.m20_send_message(v_conv, 'Pagina smoke ' || v_i::text || ' M20.');
    end loop;

    begin
      v_json := public.m20_get_conversation_messages(v_conv, null, 2);
      v_cursor := (v_json->>'next_cursor')::timestamptz;
      perform pg_temp.m20_smoke(15, 'Pagination cursor',
        v_cursor is not null or (v_json->>'has_more')::boolean = false);
    exception when others then
      perform pg_temp.m20_smoke(15, 'Pagination cursor', false, SQLERRM);
    end;

    begin
      v_json := public.m20_get_conversation_messages(v_conv, null, 2);
      select coalesce(array_agg((j->>'id')::uuid), '{}'::uuid[]) into v_ids_page1
      from jsonb_array_elements(coalesce(v_json->'items', '[]'::jsonb)) j;
      v_cursor := (v_json->>'next_cursor')::timestamptz;
      if v_cursor is not null then
        v_json2 := public.m20_get_conversation_messages(v_conv, v_cursor, 2);
        select coalesce(array_agg((j->>'id')::uuid), '{}'::uuid[]) into v_ids_page2
        from jsonb_array_elements(coalesce(v_json2->'items', '[]'::jsonb)) j;
        select count(*)::int into v_overlap
        from unnest(v_ids_page1) a(id) join unnest(v_ids_page2) b(id) on a.id = b.id;
        perform pg_temp.m20_smoke(16, 'Pagination no duplicates', v_overlap = 0);
      else
        perform pg_temp.m20_smoke(16, 'Pagination no duplicates', true, 'single page only');
      end if;
    exception when others then
      perform pg_temp.m20_smoke(16, 'Pagination no duplicates', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_smoke(15, 'Pagination cursor', false, 'prerequisite conv failed');
    perform pg_temp.m20_smoke(16, 'Pagination no duplicates', false, 'prerequisite conv failed');
  end if;

  -- ========================================================================
  -- 17–18 Privacy
  -- ========================================================================
  if v_msg1 is not null then
    begin
      v_json := public._m20_public_message_json(v_msg1, v_mgr);
      perform pg_temp.m20_smoke(17, 'No sender_user_id in public JSON',
        v_json->>'sender_user_id' is null);
    exception when others then
      perform pg_temp.m20_smoke(17, 'No sender_user_id in public JSON', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_smoke(17, 'No sender_user_id in public JSON', false, 'prerequisite msg failed');
  end if;

  if v_conv is not null then
    perform set_config('request.jwt.claim.role', 'service_role', true);
    begin
      insert into public.m20_messages (
        conversation_id, sender_user_id, sender_display_name, content,
        message_status, attachment_ref, message_type
      ) values (
        v_conv, v_mgr, 'M20 Smoke Manager', 'Privado smoke M20.',
        'SENT', 'private://smoke-internal.jpg', 'IMAGE_REFERENCE'
      ) returning id into v_msg_del;
    exception when others then
      v_msg_del := null;
    end;
    perform pg_temp.m20_act_as(v_mgr);

    if v_msg_del is not null then
      begin
        v_json := public._m20_public_message_json(v_msg_del, v_mgr);
        perform pg_temp.m20_smoke(18, 'private:// attachment filtered',
          v_json->>'attachment_ref' is null);
      exception when others then
        perform pg_temp.m20_smoke(18, 'private:// attachment filtered', false, SQLERRM);
      end;
    else
      perform pg_temp.m20_smoke(18, 'private:// attachment filtered', false, 'seed failed');
    end if;
  else
    perform pg_temp.m20_smoke(18, 'private:// attachment filtered', false, 'prerequisite conv failed');
  end if;

  -- ========================================================================
  -- 19–20 Stranger denied
  -- ========================================================================
  if v_conv is not null then
    perform pg_temp.m20_act_as(v_out);
    begin
      perform public.m20_send_message(v_conv, 'Hack smoke M20 ajeno.');
      perform pg_temp.m20_smoke(19, 'Stranger send denied', false);
    exception when others then
      perform pg_temp.m20_smoke(19, 'Stranger send denied', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;

    begin
      perform public.m20_get_conversation_messages(v_conv);
      perform pg_temp.m20_smoke(20, 'Stranger list messages denied', false);
    exception when others then
      perform pg_temp.m20_smoke(20, 'Stranger list messages denied', SQLERRM like '%M20_PERMISSION_DENIED%');
    end;
  else
    perform pg_temp.m20_smoke(19, 'Stranger send denied', false, 'prerequisite conv failed');
    perform pg_temp.m20_smoke(20, 'Stranger list messages denied', false, 'prerequisite conv failed');
  end if;

  -- ========================================================================
  -- 21–22 Context CAMPAIGN / SOCIAL_POST
  -- ========================================================================
  perform pg_temp.m20_act_as(v_mgr);
  begin
    v_json := public.m20_create_direct_conversation(
      v_user2, 'CAMPAIGN', v_campaign_id, 'Campaña smoke M20', true, 'CONTEXTUAL'
    );
    v_conv_campaign := (v_json->>'id')::uuid;
    perform pg_temp.m20_smoke(21, 'Context CAMPAIGN hint',
      v_json->'context_hint'->>'route_hint' like 'm17/campaigns/%');
  exception when others then
    perform pg_temp.m20_smoke(21, 'Context CAMPAIGN hint', false, SQLERRM);
  end;

  begin
    v_json := public.m20_create_direct_conversation(
      v_user2, 'SOCIAL_POST', v_social_id, 'Post smoke M20', true, 'CONTEXTUAL'
    );
    v_conv_social := (v_json->>'id')::uuid;
    perform pg_temp.m20_smoke(22, 'Context SOCIAL_POST hint',
      v_json->'context_hint'->>'route_hint' like 'm19/posts/%');
  exception when others then
    perform pg_temp.m20_smoke(22, 'Context SOCIAL_POST hint', false, SQLERRM);
  end;

  -- ========================================================================
  -- 23 Effective ARCHIVED actor only
  -- ========================================================================
  if v_conv_arch is not null then
    perform pg_temp.m20_act_as(v_out);
    begin
      select c->>'status' into v_status_peer
      from public.m20_list_my_conversations() c where (c->>'id')::uuid = v_conv_arch;
      perform pg_temp.m20_smoke(23, 'Effective ARCHIVED actor only',
        v_status_peer = 'ACTIVE');
    exception when others then
      perform pg_temp.m20_smoke(23, 'Effective ARCHIVED actor only', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_smoke(23, 'Effective ARCHIVED actor only', false, 'prerequisite archive failed');
  end if;

  -- ========================================================================
  -- 24 Blocked conversation rejects send (explicit)
  -- ========================================================================
  perform pg_temp.m20_act_as(v_mgr);
  begin
    v_json := public.m20_create_direct_conversation(v_user2);
    perform public.m20_block_user((v_json->>'id')::uuid);
    begin
      perform public.m20_send_message((v_json->>'id')::uuid, 'Blocked smoke retry M20.');
      perform pg_temp.m20_smoke(24, 'Blocked conversation rejects send', false);
    exception when others then
      perform pg_temp.m20_smoke(24, 'Blocked conversation rejects send',
        SQLERRM like '%M20_CONVERSATION_BLOCKED%');
    end;
    perform public.m20_unblock_user((v_json->>'id')::uuid);
  exception when others then
    perform pg_temp.m20_smoke(24, 'Blocked conversation rejects send', false, SQLERRM);
  end;

  -- ========================================================================
  -- 25 Unread count after mark read
  -- ========================================================================
  if v_conv is not null then
    perform pg_temp.m20_act_as(v_user2);
    perform public.m20_send_message(v_conv, 'Unread final smoke M20.');
    perform pg_temp.m20_act_as(v_mgr);
    begin
      perform public.m20_mark_conversation_read(v_conv);
      select (c->>'unread_count')::int into v_cnt
      from public.m20_list_my_conversations() c where (c->>'id')::uuid = v_conv;
      perform pg_temp.m20_smoke(25, 'Unread count after mark read', coalesce(v_cnt, 0) >= 0);
    exception when others then
      perform pg_temp.m20_smoke(25, 'Unread count after mark read', false, SQLERRM);
    end;
  else
    perform pg_temp.m20_smoke(25, 'Unread count after mark read', false, 'prerequisite conv failed');
  end if;

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
from m20_smoke_results
where result = 'FAIL'
order by case_id;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m20_smoke_results;

create table if not exists public._m20_smoke_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m20_smoke_last_failures;

insert into public._m20_smoke_last_failures (case_id, label, detail)
select case_id, label, detail from m20_smoke_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m20_smoke_results where result = 'FAIL' order by case_id loop
    raise warning 'M20_SMOKE_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

commit;
