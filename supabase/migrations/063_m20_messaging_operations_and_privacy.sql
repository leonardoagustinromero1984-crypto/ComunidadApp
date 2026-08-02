-- =============================================================================
-- LeoVer M20 — migración 063: operaciones Bloque 3 (edit/delete/reply, archivo
-- per-participante, markRead cursor, idempotencia client_message_id, tipos).
-- Forward-only sobre 001–062. No modifica 062 retroactivamente.
-- LOCAL ONLY — no aplicar hasta entorno no productivo autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Ajustes índice único (múltiples hilos por par + contexto) y estados msg
-- ---------------------------------------------------------------------------
drop index if exists public.m20_conv_participants_uniq;

create unique index if not exists m20_conv_participants_ctx_uniq
  on public.m20_conversations (
    participant_low,
    participant_high,
    conversation_type,
    coalesce(context_type, ''),
    coalesce(context_target_id, '')
  );

alter table public.m20_messages
  drop constraint if exists m20_msg_status_chk;

alter table public.m20_messages
  add constraint m20_msg_status_chk check (
    message_status = any (array[
      'SENT','DELIVERED','READ','EDITED','DELETED'
    ]::text[])
  );

-- ---------------------------------------------------------------------------
-- 1. Columnas extendidas
-- ---------------------------------------------------------------------------
alter table public.m20_conversations
  add column if not exists conversation_type text not null default 'DIRECT';

alter table public.m20_conversations
  drop constraint if exists m20_conv_type_chk;

alter table public.m20_conversations
  add constraint m20_conv_type_chk check (
    conversation_type = any (array['DIRECT','ORGANIZATION','SUPPORT','CONTEXTUAL']::text[])
  );

alter table public.m20_conversations
  drop constraint if exists m20_conv_context_type_chk;

alter table public.m20_conversations
  add constraint m20_conv_context_type_chk check (
    context_type is null
    or context_type = any (array[
      'PET','ORGANIZATION','EVENT','CAMPAIGN','SOCIAL_POST'
    ]::text[])
  );

alter table public.m20_messages
  add column if not exists client_message_id text,
  add column if not exists message_type text not null default 'TEXT',
  add column if not exists reply_to_message_id uuid references public.m20_messages (id) on delete set null,
  add column if not exists edited_at timestamptz,
  add column if not exists deleted_at timestamptz;

alter table public.m20_messages
  drop constraint if exists m20_msg_type_chk;

alter table public.m20_messages
  add constraint m20_msg_type_chk check (
    message_type = any (array[
      'TEXT','IMAGE_REFERENCE','FILE_REFERENCE','SYSTEM_CONTEXT'
    ]::text[])
  );

alter table public.m20_messages
  drop constraint if exists m20_msg_content_len;

alter table public.m20_messages
  add constraint m20_msg_content_len check (
    char_length(content) <= 4000
    and (
      char_length(trim(content)) between 1 and 4000
      or attachment_ref is not null
      or deleted_at is not null
    )
  );

create unique index if not exists m20_msg_client_id_uniq
  on public.m20_messages (conversation_id, sender_user_id, client_message_id)
  where client_message_id is not null;

create index if not exists m20_messages_reply_idx
  on public.m20_messages (reply_to_message_id)
  where reply_to_message_id is not null;

-- ---------------------------------------------------------------------------
-- 2. Estado per-participante (archivo + cursor lectura)
-- ---------------------------------------------------------------------------
create table if not exists public.m20_participant_state (
  conversation_id uuid not null references public.m20_conversations (id) on delete cascade,
  user_id uuid not null references public.users (id) on delete cascade,
  archived boolean not null default false,
  last_read_message_id uuid references public.m20_messages (id) on delete set null,
  last_read_at timestamptz,
  updated_at timestamptz not null default timezone('utc', now()),
  primary key (conversation_id, user_id)
);

create index if not exists m20_participant_state_user_idx
  on public.m20_participant_state (user_id, archived);

alter table public.m20_participant_state enable row level security;

create policy m20_part_state_select on public.m20_participant_state for select to authenticated
  using (
    exists (
      select 1 from public.m20_conversations c
      where c.id = conversation_id
        and auth.uid() in (c.participant_low, c.participant_high)
    )
  );

create policy m20_part_state_mut on public.m20_participant_state for all to authenticated
  using (false);

revoke all on table public.m20_participant_state from public, anon;
grant select on table public.m20_participant_state to authenticated;
grant all on table public.m20_participant_state to service_role;

-- ---------------------------------------------------------------------------
-- 3. Helpers actualizados
-- ---------------------------------------------------------------------------
create or replace function public._m20_route_hint(p_type text, p_target_id text)
returns text language sql immutable as $$
  select case upper(coalesce(p_type, ''))
    when 'PET' then 'm08/pets/' || coalesce(p_target_id, '')
    when 'ORGANIZATION' then 'm03/orgs/' || coalesce(p_target_id, '')
    when 'EVENT' then 'm18/events/' || coalesce(p_target_id, '')
    when 'CAMPAIGN' then 'm17/campaigns/' || coalesce(p_target_id, '')
    when 'SOCIAL_POST' then 'm19/posts/' || coalesce(p_target_id, '')
    else ''
  end;
$$;

create or replace function public._m20_participant_state_row(
  p_conv_id uuid,
  p_user uuid
) returns public.m20_participant_state language sql stable security definer set search_path = public as $$
  select * from public.m20_participant_state
  where conversation_id = p_conv_id and user_id = p_user;
$$;

create or replace function public._m20_effective_status(
  p_conv public.m20_conversations,
  p_actor uuid
) returns text language sql stable security definer set search_path = public as $$
  select case
    when p_conv.conversation_status = 'BLOCKED' then 'BLOCKED'
    when coalesce((
      select ps.archived from public.m20_participant_state ps
      where ps.conversation_id = p_conv.id and ps.user_id = p_actor
    ), false) then 'ARCHIVED'
    else p_conv.conversation_status
  end;
$$;

create or replace function public._m20_unread_count(p_conv_id uuid, p_actor uuid)
returns integer language sql stable security definer set search_path = public as $$
  select count(*)::integer
  from public.m20_messages m
  where m.conversation_id = p_conv_id
    and m.sender_user_id <> p_actor
    and m.deleted_at is null
    and m.message_status <> 'READ'
    and not exists (
      select 1 from public.m20_participant_state ps
      where ps.conversation_id = p_conv_id
        and ps.user_id = p_actor
        and ps.last_read_message_id = m.id
    )
    and not exists (
      select 1 from public.m20_participant_state ps
      join public.m20_messages lr on lr.id = ps.last_read_message_id
      where ps.conversation_id = p_conv_id
        and ps.user_id = p_actor
        and ps.last_read_at is not null
        and (
          m.sent_at < lr.sent_at
          or (m.sent_at = lr.sent_at and m.id <= lr.id)
        )
    );
$$;

create or replace function public._m20_reply_reference_json(
  p_reply_id uuid,
  p_actor uuid
) returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  m public.m20_messages;
  v public.m20_conversations;
begin
  if p_reply_id is null then return null; end if;
  select * into m from public.m20_messages where id = p_reply_id;
  if not found then return null; end if;
  select * into v from public.m20_conversations where id = m.conversation_id;
  if not found or not public._m20_is_participant(v, p_actor) then return null; end if;
  return jsonb_build_object(
    'message_id', m.id,
    'preview', case
      when m.deleted_at is not null then '[mensaje eliminado]'
      else left(trim(m.content), 120)
    end,
    'sender_display_name', m.sender_display_name
  );
end;
$$;

create or replace function public._m20_public_conversation_json(p_conv_id uuid, p_actor uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m20_conversations;
  v_peer uuid;
begin
  select * into v from public.m20_conversations where id = p_conv_id;
  if not found then return null; end if;
  if not public._m20_is_participant(v, p_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  v_peer := public._m20_peer_user_id(v, p_actor);

  return jsonb_build_object(
    'id', v.id,
    'peer_display_name', coalesce(public._m20_user_display_name(v_peer), 'Participante'),
    'status', public._m20_effective_status(v, p_actor),
    'conversation_type', v.conversation_type,
    'context_hint', public._m20_public_context_hint_json(v),
    'last_message_preview', v.last_message_preview,
    'last_message_at', v.last_message_at,
    'unread_count', public._m20_unread_count(v.id, p_actor)
  );
end;
$$;

create or replace function public._m20_public_message_json(p_msg_id uuid, p_actor uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  m public.m20_messages;
  v public.m20_conversations;
  v_deleted boolean;
  v_content text;
begin
  select * into m from public.m20_messages where id = p_msg_id;
  if not found then return null; end if;
  select * into v from public.m20_conversations where id = m.conversation_id;
  if not found or not public._m20_is_participant(v, p_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  v_deleted := m.deleted_at is not null;
  v_content := case when v_deleted then '[mensaje eliminado]' else m.content end;

  return jsonb_build_object(
    'id', m.id,
    'conversation_id', m.conversation_id,
    'sender_display_name', m.sender_display_name,
    'content', v_content,
    'status', case when v_deleted then 'DELETED' else m.message_status end,
    'message_type', m.message_type,
    'attachment_ref', case
      when v_deleted then null
      when m.attachment_ref is null or m.attachment_ref like 'private://%' then null
      else m.attachment_ref
    end,
    'reply_reference', public._m20_reply_reference_json(m.reply_to_message_id, p_actor),
    'edited_at', m.edited_at,
    'is_deleted', v_deleted,
    'sent_at', m.sent_at,
    'is_own_message', m.sender_user_id = p_actor
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RPC — crear conversación directa idempotente
-- ---------------------------------------------------------------------------
create or replace function public.m20_create_direct_conversation(
  p_peer_user_id uuid,
  p_context_type text default null,
  p_context_target_id text default null,
  p_context_display_label text default null,
  p_context_is_public boolean default true,
  p_conversation_type text default 'DIRECT'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_low uuid;
  v_high uuid;
  v_conv_id uuid;
  v_conv public.m20_conversations;
  v_ctx_type text := nullif(upper(trim(coalesce(p_context_type, ''))), '');
begin
  if p_peer_user_id is null or p_peer_user_id = v_actor then
    raise exception 'M20_INVALID_MESSAGE';
  end if;
  if public._m20_users_blocked(v_actor, p_peer_user_id) then
    raise exception 'M20_USER_BLOCKED';
  end if;

  v_low := least(v_actor, p_peer_user_id);
  v_high := greatest(v_actor, p_peer_user_id);

  select id into v_conv_id
  from public.m20_conversations c
  where c.participant_low = v_low
    and c.participant_high = v_high
    and c.conversation_type = coalesce(nullif(trim(p_conversation_type), ''), 'DIRECT')
    and coalesce(c.context_type, '') = coalesce(v_ctx_type, '')
    and coalesce(c.context_target_id, '') = coalesce(nullif(trim(p_context_target_id), ''), '')
  limit 1;

  if v_conv_id is null then
    insert into public.m20_conversations (
      participant_low, participant_high, conversation_type,
      context_type, context_target_id, context_display_label, context_is_public
    ) values (
      v_low, v_high, coalesce(nullif(trim(p_conversation_type), ''), 'DIRECT'),
      v_ctx_type, nullif(trim(p_context_target_id), ''), nullif(trim(p_context_display_label), ''),
      coalesce(p_context_is_public, true)
    ) returning id into v_conv_id;
  end if;

  return public._m20_public_conversation_json(v_conv_id, v_actor);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. RPC — enviar mensaje extendido
-- ---------------------------------------------------------------------------
drop function if exists public.m20_send_message(uuid, text, text);

create or replace function public.m20_send_message(
  p_conversation_id uuid,
  p_content text default '',
  p_attachment_ref text default null,
  p_client_message_id text default null,
  p_reply_to_message_id uuid default null,
  p_message_type text default 'TEXT'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_conv public.m20_conversations;
  v_peer uuid;
  v_id uuid;
  v_content text := trim(coalesce(p_content, ''));
  v_attachment text := nullif(trim(coalesce(p_attachment_ref, '')), '');
  v_client_id text := nullif(trim(coalesce(p_client_message_id, '')), '');
  v_sender_name text;
  v_msg_type text := coalesce(nullif(trim(p_message_type), ''), 'TEXT');
  v_reply public.m20_messages;
  v_existing uuid;
begin
  if v_client_id is not null then
    select id into v_existing
    from public.m20_messages
    where conversation_id = p_conversation_id
      and sender_user_id = v_actor
      and client_message_id = v_client_id
    limit 1;
    if v_existing is not null then
      return public._m20_public_message_json(v_existing, v_actor);
    end if;
  end if;

  select * into v_conv from public.m20_conversations where id = p_conversation_id;
  if not found then raise exception 'M20_CONVERSATION_NOT_FOUND'; end if;
  if not public._m20_is_participant(v_conv, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;
  if v_conv.conversation_status = 'BLOCKED' then
    raise exception 'M20_CONVERSATION_BLOCKED';
  end if;
  if public._m20_effective_status(v_conv, v_actor) = 'ARCHIVED' then
    raise exception 'M20_CONVERSATION_ARCHIVED';
  end if;

  v_peer := public._m20_peer_user_id(v_conv, v_actor);
  if public._m20_users_blocked(v_actor, v_peer) then
    raise exception 'M20_CONVERSATION_BLOCKED';
  end if;

  if char_length(v_content) > 4000 then raise exception 'M20_INVALID_MESSAGE'; end if;
  if char_length(v_content) < 1 and v_attachment is null then
    raise exception 'M20_INVALID_MESSAGE';
  end if;
  if v_content ~* '<script|javascript:|on\w+\s*=|<iframe' then
    raise exception 'M20_INVALID_MESSAGE';
  end if;
  if v_attachment is not null then
    if v_attachment like 'private://%' then raise exception 'M20_ATTACHMENT_NOT_ALLOWED'; end if;
    if char_length(v_attachment) > 512 then raise exception 'M20_INVALID_ATTACHMENT_REF'; end if;
  end if;

  if p_reply_to_message_id is not null then
    select * into v_reply from public.m20_messages where id = p_reply_to_message_id;
    if not found or v_reply.conversation_id <> p_conversation_id or v_reply.deleted_at is not null then
      raise exception 'M20_REPLY_NOT_FOUND';
    end if;
  end if;

  v_sender_name := coalesce(public._m20_user_display_name(v_actor), 'Participante');

  insert into public.m20_messages (
    conversation_id, sender_user_id, sender_display_name,
    content, message_status, attachment_ref,
    client_message_id, message_type, reply_to_message_id
  ) values (
    p_conversation_id, v_actor, v_sender_name,
    coalesce(nullif(v_content, ''), '📎 Adjunto'), 'SENT', v_attachment,
    v_client_id, v_msg_type, p_reply_to_message_id
  ) returning id into v_id;

  update public.m20_conversations set
    last_message_preview = coalesce(nullif(v_content, ''), '📎 Adjunto'),
    last_message_at = timezone('utc', now()),
    updated_at = timezone('utc', now())
  where id = p_conversation_id;

  return public._m20_public_message_json(v_id, v_actor);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPC — editar / eliminar / marcar leído
-- ---------------------------------------------------------------------------
create or replace function public.m20_edit_message(
  p_message_id uuid,
  p_content text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  m public.m20_messages;
  v public.m20_conversations;
  v_content text := trim(coalesce(p_content, ''));
begin
  select * into m from public.m20_messages where id = p_message_id;
  if not found then raise exception 'M20_MESSAGE_NOT_FOUND'; end if;
  select * into v from public.m20_conversations where id = m.conversation_id;
  if not found or not public._m20_is_participant(v, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;
  if m.sender_user_id <> v_actor then raise exception 'M20_PERMISSION_DENIED'; end if;
  if m.deleted_at is not null then raise exception 'M20_MESSAGE_NOT_FOUND'; end if;
  if char_length(v_content) < 1 or char_length(v_content) > 4000 then
    raise exception 'M20_INVALID_MESSAGE';
  end if;

  update public.m20_messages set
    content = v_content,
    message_status = 'EDITED',
    edited_at = timezone('utc', now())
  where id = p_message_id;

  return public._m20_public_message_json(p_message_id, v_actor);
end;
$$;

create or replace function public.m20_delete_message(p_message_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  m public.m20_messages;
  v public.m20_conversations;
begin
  select * into m from public.m20_messages where id = p_message_id;
  if not found then raise exception 'M20_MESSAGE_NOT_FOUND'; end if;
  select * into v from public.m20_conversations where id = m.conversation_id;
  if not found or not public._m20_is_participant(v, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;
  if m.sender_user_id <> v_actor then raise exception 'M20_PERMISSION_DENIED'; end if;

  if m.deleted_at is null then
    update public.m20_messages set
      content = '[mensaje eliminado]',
      message_status = 'DELETED',
      deleted_at = timezone('utc', now()),
      attachment_ref = null
    where id = p_message_id;
  end if;

  return jsonb_build_object('ok', true, 'message_id', p_message_id);
end;
$$;

create or replace function public.m20_mark_conversation_read(
  p_conversation_id uuid,
  p_last_read_message_id uuid default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_conv public.m20_conversations;
  v_target public.m20_messages;
  v_last_id uuid;
begin
  select * into v_conv from public.m20_conversations where id = p_conversation_id;
  if not found then raise exception 'M20_CONVERSATION_NOT_FOUND'; end if;
  if not public._m20_is_participant(v_conv, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  if p_last_read_message_id is not null then
    select * into v_target from public.m20_messages where id = p_last_read_message_id;
    if not found or v_target.conversation_id <> p_conversation_id then
      raise exception 'M20_MESSAGE_NOT_FOUND';
    end if;
    v_last_id := v_target.id;
  else
    select id into v_last_id
    from public.m20_messages
    where conversation_id = p_conversation_id
      and sender_user_id <> v_actor
      and deleted_at is null
    order by sent_at desc, id desc
    limit 1;
    if v_last_id is null then
      return jsonb_build_object('ok', true, 'conversation_id', p_conversation_id);
    end if;
    select * into v_target from public.m20_messages where id = v_last_id;
  end if;

  insert into public.m20_participant_state (
    conversation_id, user_id, last_read_message_id, last_read_at, updated_at
  ) values (
    p_conversation_id, v_actor, v_last_id, v_target.sent_at, timezone('utc', now())
  )
  on conflict (conversation_id, user_id) do update set
    last_read_message_id = excluded.last_read_message_id,
    last_read_at = excluded.last_read_at,
    updated_at = excluded.updated_at
  where coalesce(excluded.last_read_at, '-infinity'::timestamptz)
    >= coalesce(public.m20_participant_state.last_read_at, '-infinity'::timestamptz);

  update public.m20_messages m set message_status = 'READ'
  where m.conversation_id = p_conversation_id
    and m.sender_user_id <> v_actor
    and m.deleted_at is null
    and (
      m.sent_at < v_target.sent_at
      or (m.sent_at = v_target.sent_at and m.id <= v_target.id)
    )
    and m.message_status <> 'READ';

  return jsonb_build_object('ok', true, 'conversation_id', p_conversation_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. RPC — archivo per-participante
-- ---------------------------------------------------------------------------
create or replace function public.m20_archive_conversation(p_conversation_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_conv public.m20_conversations;
begin
  select * into v_conv from public.m20_conversations where id = p_conversation_id;
  if not found then raise exception 'M20_CONVERSATION_NOT_FOUND'; end if;
  if not public._m20_is_participant(v_conv, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;
  if v_conv.conversation_status = 'BLOCKED' then
    raise exception 'M20_CONVERSATION_BLOCKED';
  end if;

  insert into public.m20_participant_state (conversation_id, user_id, archived, updated_at)
  values (p_conversation_id, v_actor, true, timezone('utc', now()))
  on conflict (conversation_id, user_id) do update set
    archived = true,
    updated_at = excluded.updated_at;

  return jsonb_build_object('ok', true, 'conversation_id', p_conversation_id);
end;
$$;

-- Grants RPC nuevas/actualizadas
revoke all on function public.m20_create_direct_conversation(uuid, text, text, text, boolean, text) from public, anon;
revoke all on function public.m20_send_message(uuid, text, text, text, uuid, text) from public, anon;
revoke all on function public.m20_edit_message(uuid, text) from public, anon;
revoke all on function public.m20_delete_message(uuid) from public, anon;
revoke all on function public.m20_mark_conversation_read(uuid, uuid) from public, anon;

grant execute on function public.m20_create_direct_conversation(uuid, text, text, text, boolean, text) to authenticated;
grant execute on function public.m20_send_message(uuid, text, text, text, uuid, text) to authenticated;
grant execute on function public.m20_edit_message(uuid, text) to authenticated;
grant execute on function public.m20_delete_message(uuid) to authenticated;
grant execute on function public.m20_mark_conversation_read(uuid, uuid) to authenticated;

commit;
