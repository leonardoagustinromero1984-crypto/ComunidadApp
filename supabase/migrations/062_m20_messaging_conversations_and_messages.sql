-- =============================================================================
-- LeoVer M20 — migración 062: conversaciones, mensajes, bloqueos de usuario,
-- RLS y RPCs SECURITY DEFINER con JSON público sanitizado.
-- Forward-only sobre 001–061. Sin upload de adjuntos ni retención avanzada.
-- LOCAL ONLY — no aplicar hasta entorno no productivo autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------------
create table if not exists public.m20_conversations (
  id uuid primary key default gen_random_uuid(),
  participant_low uuid not null references public.users (id) on delete restrict,
  participant_high uuid not null references public.users (id) on delete restrict,
  conversation_status text not null default 'ACTIVE',
  context_type text,
  context_target_id text,
  context_display_label text,
  context_is_public boolean not null default true,
  last_message_preview text,
  last_message_at timestamptz,
  blocked_by_user_id uuid references public.users (id) on delete set null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m20_conv_participants_ordered check (participant_low < participant_high),
  constraint m20_conv_status_chk check (
    conversation_status = any (array['ACTIVE','ARCHIVED','BLOCKED']::text[])
  ),
  constraint m20_conv_context_type_chk check (
    context_type is null
    or context_type = any (array['PET','ORGANIZATION','EVENT']::text[])
  ),
  constraint m20_conv_context_label_len check (
    context_display_label is null or char_length(trim(context_display_label)) <= 120
  ),
  constraint m20_conv_preview_len check (
    last_message_preview is null or char_length(last_message_preview) <= 4000
  )
);

create unique index if not exists m20_conv_participants_uniq
  on public.m20_conversations (participant_low, participant_high);

create index if not exists m20_conv_low_idx
  on public.m20_conversations (participant_low, conversation_status, last_message_at desc nulls last);

create index if not exists m20_conv_high_idx
  on public.m20_conversations (participant_high, conversation_status, last_message_at desc nulls last);

create table if not exists public.m20_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.m20_conversations (id) on delete cascade,
  sender_user_id uuid not null references public.users (id) on delete restrict,
  sender_display_name text not null default 'Participante',
  content text not null,
  message_status text not null default 'SENT',
  attachment_ref text,
  sent_at timestamptz not null default timezone('utc', now()),
  constraint m20_msg_status_chk check (
    message_status = any (array['SENT','DELIVERED','READ']::text[])
  ),
  constraint m20_msg_content_len check (char_length(trim(content)) between 1 and 4000),
  constraint m20_msg_sender_len check (char_length(trim(sender_display_name)) <= 80),
  constraint m20_msg_attachment_len check (
    attachment_ref is null or char_length(attachment_ref) <= 512
  )
);

create index if not exists m20_messages_conv_sent_idx
  on public.m20_messages (conversation_id, sent_at desc, id desc);

create index if not exists m20_messages_sender_idx
  on public.m20_messages (sender_user_id, sent_at desc);

create table if not exists public.m20_user_blocks (
  id uuid primary key default gen_random_uuid(),
  blocker_user_id uuid not null references public.users (id) on delete cascade,
  blocked_user_id uuid not null references public.users (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m20_block_not_self check (blocker_user_id <> blocked_user_id)
);

create unique index if not exists m20_user_blocks_uniq
  on public.m20_user_blocks (blocker_user_id, blocked_user_id);

create index if not exists m20_user_blocks_blocked_idx
  on public.m20_user_blocks (blocked_user_id);

-- ---------------------------------------------------------------------------
-- 2. Helpers
-- ---------------------------------------------------------------------------
create or replace function public._m20_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v;
end;
$$;

create or replace function public._m20_user_display_name(p_user uuid)
returns text language sql stable security definer set search_path = public as $$
  select coalesce(nullif(trim(u.display_name), ''), nullif(trim(u.name), ''), 'Participante')
  from public.users u where u.id = p_user;
$$;

create or replace function public._m20_is_participant(p_conv public.m20_conversations, p_user uuid)
returns boolean language sql stable as $$
  select p_user in (p_conv.participant_low, p_conv.participant_high);
$$;

create or replace function public._m20_peer_user_id(p_conv public.m20_conversations, p_actor uuid)
returns uuid language sql stable as $$
  select case
    when p_actor = p_conv.participant_low then p_conv.participant_high
    when p_actor = p_conv.participant_high then p_conv.participant_low
    else null::uuid
  end;
$$;

create or replace function public._m20_users_blocked(p_a uuid, p_b uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.m20_user_blocks b
    where (b.blocker_user_id = p_a and b.blocked_user_id = p_b)
       or (b.blocker_user_id = p_b and b.blocked_user_id = p_a)
  );
$$;

create or replace function public._m20_route_hint(p_type text, p_target_id text)
returns text language sql immutable as $$
  select case upper(coalesce(p_type, ''))
    when 'PET' then 'm08/pets/' || coalesce(p_target_id, '')
    when 'ORGANIZATION' then 'm03/orgs/' || coalesce(p_target_id, '')
    when 'EVENT' then 'm18/events/' || coalesce(p_target_id, '')
    else ''
  end;
$$;

create or replace function public._m20_public_context_hint_json(p_conv public.m20_conversations)
returns jsonb language sql stable as $$
  select case
    when p_conv.context_type is null
      or not coalesce(p_conv.context_is_public, true)
      or coalesce(p_conv.context_display_label, '') = '' then null
    else jsonb_build_object(
      'type', p_conv.context_type,
      'display_label', trim(p_conv.context_display_label),
      'route_hint', public._m20_route_hint(p_conv.context_type, p_conv.context_target_id)
    )
  end;
$$;

create or replace function public._m20_unread_count(p_conv_id uuid, p_actor uuid)
returns integer language sql stable security definer set search_path = public as $$
  select count(*)::integer
  from public.m20_messages m
  where m.conversation_id = p_conv_id
    and m.sender_user_id <> p_actor
    and m.message_status <> 'READ';
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
    'status', v.conversation_status,
    'context_hint', public._m20_public_context_hint_json(v),
    'last_message_preview', v.last_message_preview,
    'last_message_at', v.last_message_at,
    'unread_count', public._m20_unread_count(v.id, p_actor)
  );
end;
$$;

create or replace function public._m20_internal_conversation_json(p_conv_id uuid, p_actor uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m20_conversations;
  v_peer uuid;
begin
  select * into v from public.m20_conversations where id = p_conv_id;
  if not found then raise exception 'M20_CONVERSATION_NOT_FOUND'; end if;
  if not public._m20_is_participant(v, p_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  v_peer := public._m20_peer_user_id(v, p_actor);

  return jsonb_build_object(
    'id', v.id,
    'participant_user_ids', jsonb_build_array(v.participant_low, v.participant_high),
    'peer_user_id', v_peer,
    'peer_display_name', coalesce(public._m20_user_display_name(v_peer), 'Participante'),
    'status', v.conversation_status,
    'context_snapshot', case
      when v.context_type is null then null
      else jsonb_build_object(
        'type', v.context_type,
        'target_id', v.context_target_id,
        'display_label', coalesce(v.context_display_label, ''),
        'is_public', coalesce(v.context_is_public, true)
      )
    end,
    'last_message_preview', v.last_message_preview,
    'last_message_at', v.last_message_at,
    'blocked_by_user_id', v.blocked_by_user_id,
    'created_at', v.created_at,
    'updated_at', v.updated_at,
    'unread_count', public._m20_unread_count(v.id, p_actor)
  );
end;
$$;

create or replace function public._m20_public_message_json(p_msg_id uuid, p_actor uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  m public.m20_messages;
  v public.m20_conversations;
begin
  select * into m from public.m20_messages where id = p_msg_id;
  if not found then return null; end if;
  select * into v from public.m20_conversations where id = m.conversation_id;
  if not found or not public._m20_is_participant(v, p_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  return jsonb_build_object(
    'id', m.id,
    'conversation_id', m.conversation_id,
    'sender_display_name', m.sender_display_name,
    'content', m.content,
    'status', m.message_status,
    'attachment_ref', case
      when m.attachment_ref is null or m.attachment_ref like 'private://%' then null
      else m.attachment_ref
    end,
    'sent_at', m.sent_at,
    'is_own_message', m.sender_user_id = p_actor
  );
end;
$$;

create or replace function public._m20_internal_message_json(p_msg_id uuid, p_actor uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  m public.m20_messages;
  v public.m20_conversations;
begin
  select * into m from public.m20_messages where id = p_msg_id;
  if not found then raise exception 'M20_MESSAGE_NOT_FOUND'; end if;
  select * into v from public.m20_conversations where id = m.conversation_id;
  if not found or not public._m20_is_participant(v, p_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  return jsonb_build_object(
    'id', m.id,
    'conversation_id', m.conversation_id,
    'sender_user_id', m.sender_user_id,
    'sender_display_name', m.sender_display_name,
    'content', m.content,
    'status', m.message_status,
    'attachment_ref', m.attachment_ref,
    'sent_at', m.sent_at
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. RLS — deny direct mutation; RPC SECURITY DEFINER
-- ---------------------------------------------------------------------------
alter table public.m20_conversations enable row level security;
alter table public.m20_messages enable row level security;
alter table public.m20_user_blocks enable row level security;

create policy m20_conv_select on public.m20_conversations for select to authenticated
  using (auth.uid() in (participant_low, participant_high));

create policy m20_conv_mut on public.m20_conversations for all to authenticated
  using (false);

create policy m20_msg_select on public.m20_messages for select to authenticated
  using (
    exists (
      select 1 from public.m20_conversations c
      where c.id = conversation_id
        and auth.uid() in (c.participant_low, c.participant_high)
    )
  );

create policy m20_msg_mut on public.m20_messages for all to authenticated
  using (false);

create policy m20_blocks_select on public.m20_user_blocks for select to authenticated
  using (blocker_user_id = auth.uid() or blocked_user_id = auth.uid());

create policy m20_blocks_mut on public.m20_user_blocks for all to authenticated
  using (false);

revoke all on table public.m20_conversations from public, anon;
revoke all on table public.m20_messages from public, anon;
revoke all on table public.m20_user_blocks from public, anon;
grant select on table public.m20_conversations to authenticated;
grant select on table public.m20_messages to authenticated;
grant select on table public.m20_user_blocks to authenticated;
grant all on table public.m20_conversations to service_role;
grant all on table public.m20_messages to service_role;
grant all on table public.m20_user_blocks to service_role;

-- ---------------------------------------------------------------------------
-- 4. RPCs — participante autenticado
-- ---------------------------------------------------------------------------
create or replace function public.m20_list_my_conversations()
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_row public.m20_conversations;
begin
  for v_row in
    select * from public.m20_conversations c
    where v_actor in (c.participant_low, c.participant_high)
    order by coalesce(c.last_message_at, c.updated_at) desc
  loop
    return next public._m20_public_conversation_json(v_row.id, v_actor);
  end loop;
end;
$$;

create or replace function public.m20_get_conversation_messages(
  p_conversation_id uuid,
  p_cursor timestamptz default null,
  p_page_size integer default 50
) returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_conv public.m20_conversations;
  v_limit integer := greatest(1, least(coalesce(p_page_size, 50), 100));
  v_msg public.m20_messages;
  v_items jsonb := '[]'::jsonb;
  v_count integer := 0;
  v_next_cursor timestamptz;
  v_has_more boolean := false;
begin
  select * into v_conv from public.m20_conversations where id = p_conversation_id;
  if not found then raise exception 'M20_CONVERSATION_NOT_FOUND'; end if;
  if not public._m20_is_participant(v_conv, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  for v_msg in
    select * from public.m20_messages m
    where m.conversation_id = p_conversation_id
      and (p_cursor is null or m.sent_at < p_cursor)
    order by m.sent_at desc, m.id desc
    limit v_limit + 1
  loop
    v_count := v_count + 1;
    if v_count > v_limit then
      v_has_more := true;
      exit;
    end if;
    v_items := v_items || public._m20_public_message_json(v_msg.id, v_actor);
    v_next_cursor := v_msg.sent_at;
  end loop;

  return jsonb_build_object(
    'items', coalesce((
      select jsonb_agg(elem order by (elem->>'sent_at') asc)
      from jsonb_array_elements(v_items) elem
    ), '[]'::jsonb),
    'next_cursor', case when v_has_more then v_next_cursor else null end,
    'has_more', v_has_more
  );
end;
$$;

create or replace function public.m20_send_message(
  p_conversation_id uuid,
  p_content text,
  p_attachment_ref text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_conv public.m20_conversations;
  v_peer uuid;
  v_id uuid;
  v_content text := trim(coalesce(p_content, ''));
  v_attachment text := nullif(trim(coalesce(p_attachment_ref, '')), '');
  v_sender_name text;
begin
  select * into v_conv from public.m20_conversations where id = p_conversation_id;
  if not found then raise exception 'M20_CONVERSATION_NOT_FOUND'; end if;
  if not public._m20_is_participant(v_conv, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;
  if v_conv.conversation_status = 'BLOCKED' then
    raise exception 'M20_CONVERSATION_BLOCKED';
  end if;
  if v_conv.conversation_status = 'ARCHIVED' then
    raise exception 'M20_CONVERSATION_ARCHIVED';
  end if;

  v_peer := public._m20_peer_user_id(v_conv, v_actor);
  if public._m20_users_blocked(v_actor, v_peer) then
    raise exception 'M20_CONVERSATION_BLOCKED';
  end if;

  if char_length(v_content) < 1 or char_length(v_content) > 4000 then
    raise exception 'M20_INVALID_MESSAGE';
  end if;
  if v_content ~* '<script|javascript:|on\w+\s*=|<iframe' then
    raise exception 'M20_INVALID_MESSAGE';
  end if;
  if v_attachment is not null then
    if v_attachment like 'private://%' then
      raise exception 'M20_ATTACHMENT_NOT_ALLOWED';
    end if;
    if char_length(v_attachment) > 512 then
      raise exception 'M20_INVALID_ATTACHMENT_REF';
    end if;
  end if;

  v_sender_name := coalesce(public._m20_user_display_name(v_actor), 'Participante');

  insert into public.m20_messages (
    conversation_id, sender_user_id, sender_display_name,
    content, message_status, attachment_ref
  ) values (
    p_conversation_id, v_actor, v_sender_name,
    v_content, 'SENT', v_attachment
  ) returning id into v_id;

  update public.m20_conversations set
    last_message_preview = v_content,
    last_message_at = timezone('utc', now()),
    updated_at = timezone('utc', now())
  where id = p_conversation_id;

  return public._m20_public_message_json(v_id, v_actor);
end;
$$;

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

  if v_conv.conversation_status <> 'ARCHIVED' then
    update public.m20_conversations set
      conversation_status = 'ARCHIVED',
      updated_at = timezone('utc', now())
    where id = p_conversation_id;
  end if;

  return jsonb_build_object('ok', true, 'conversation_id', p_conversation_id);
end;
$$;

create or replace function public.m20_block_user(p_conversation_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_conv public.m20_conversations;
  v_peer uuid;
begin
  select * into v_conv from public.m20_conversations where id = p_conversation_id;
  if not found then raise exception 'M20_CONVERSATION_NOT_FOUND'; end if;
  if not public._m20_is_participant(v_conv, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  v_peer := public._m20_peer_user_id(v_conv, v_actor);
  if v_peer is null then raise exception 'M20_PERMISSION_DENIED'; end if;

  insert into public.m20_user_blocks (blocker_user_id, blocked_user_id)
  values (v_actor, v_peer)
  on conflict do nothing;

  if v_conv.conversation_status <> 'BLOCKED' then
    update public.m20_conversations set
      conversation_status = 'BLOCKED',
      blocked_by_user_id = v_actor,
      updated_at = timezone('utc', now())
    where id = p_conversation_id;
  end if;

  return jsonb_build_object('ok', true, 'conversation_id', p_conversation_id);
end;
$$;

create or replace function public.m20_unblock_user(p_conversation_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m20_require_authenticated();
  v_conv public.m20_conversations;
  v_peer uuid;
begin
  select * into v_conv from public.m20_conversations where id = p_conversation_id;
  if not found then raise exception 'M20_CONVERSATION_NOT_FOUND'; end if;
  if not public._m20_is_participant(v_conv, v_actor) then
    raise exception 'M20_PERMISSION_DENIED';
  end if;

  v_peer := public._m20_peer_user_id(v_conv, v_actor);
  if v_peer is null then raise exception 'M20_PERMISSION_DENIED'; end if;

  delete from public.m20_user_blocks
  where blocker_user_id = v_actor and blocked_user_id = v_peer;

  if v_conv.conversation_status = 'BLOCKED' and v_conv.blocked_by_user_id = v_actor then
    update public.m20_conversations set
      conversation_status = 'ACTIVE',
      blocked_by_user_id = null,
      updated_at = timezone('utc', now())
    where id = p_conversation_id;
  end if;

  return jsonb_build_object('ok', true, 'conversation_id', p_conversation_id);
end;
$$;

-- Grants RPC — authenticated only; deny anon on internal tables and RPCs
revoke all on function public.m20_list_my_conversations() from public, anon;
revoke all on function public.m20_get_conversation_messages(uuid, timestamptz, integer) from public, anon;
revoke all on function public.m20_send_message(uuid, text, text) from public, anon;
revoke all on function public.m20_archive_conversation(uuid) from public, anon;
revoke all on function public.m20_block_user(uuid) from public, anon;
revoke all on function public.m20_unblock_user(uuid) from public, anon;

grant execute on function public.m20_list_my_conversations() to authenticated;
grant execute on function public.m20_get_conversation_messages(uuid, timestamptz, integer) to authenticated;
grant execute on function public.m20_send_message(uuid, text, text) to authenticated;
grant execute on function public.m20_archive_conversation(uuid) to authenticated;
grant execute on function public.m20_block_user(uuid) to authenticated;
grant execute on function public.m20_unblock_user(uuid) to authenticated;

commit;
