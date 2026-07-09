-- Leover Fase 3 — Chat entre usuarios

create table if not exists public.conversations (
    id uuid primary key default gen_random_uuid(),
    last_message_text text,
    last_message_at timestamptz,
    context_type text,
    context_id uuid,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.conversation_participants (
    conversation_id uuid not null references public.conversations (id) on delete cascade,
    user_id uuid not null references public.users (id) on delete cascade,
    peer_user_id uuid not null references public.users (id) on delete cascade,
    peer_name text not null,
    created_at timestamptz not null default timezone('utc', now()),
    primary key (conversation_id, user_id)
);

create index if not exists conversation_participants_user_idx
    on public.conversation_participants (user_id, peer_user_id);

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations (id) on delete cascade,
    sender_id uuid not null references public.users (id) on delete cascade,
    sender_name text not null,
    content text not null,
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists messages_conversation_created_idx
    on public.messages (conversation_id, created_at);

alter table public.conversations enable row level security;
alter table public.conversation_participants enable row level security;
alter table public.messages enable row level security;

drop policy if exists conversations_select_participant on public.conversations;
create policy conversations_select_participant
    on public.conversations for select to authenticated
    using (
        exists (
            select 1 from public.conversation_participants cp
            where cp.conversation_id = id and cp.user_id = auth.uid()
        )
    );

drop policy if exists conversations_insert on public.conversations;
create policy conversations_insert
    on public.conversations for insert to authenticated
    with check (true);

drop policy if exists conversations_update_participant on public.conversations;
create policy conversations_update_participant
    on public.conversations for update to authenticated
    using (
        exists (
            select 1 from public.conversation_participants cp
            where cp.conversation_id = id and cp.user_id = auth.uid()
        )
    );

drop policy if exists participants_select_own on public.conversation_participants;
create policy participants_select_own
    on public.conversation_participants for select to authenticated
    using (user_id = auth.uid());

drop policy if exists participants_insert_own on public.conversation_participants;
create policy participants_insert_own
    on public.conversation_participants for insert to authenticated
    with check (user_id = auth.uid());

drop policy if exists messages_select_participant on public.messages;
create policy messages_select_participant
    on public.messages for select to authenticated
    using (
        exists (
            select 1 from public.conversation_participants cp
            where cp.conversation_id = conversation_id and cp.user_id = auth.uid()
        )
    );

drop policy if exists messages_insert_participant on public.messages;
create policy messages_insert_participant
    on public.messages for insert to authenticated
    with check (
        sender_id = auth.uid()
        and exists (
            select 1 from public.conversation_participants cp
            where cp.conversation_id = conversation_id and cp.user_id = auth.uid()
        )
    );

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'messages'
    ) then
        alter publication supabase_realtime add table public.messages;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'conversations'
    ) then
        alter publication supabase_realtime add table public.conversations;
    end if;
end $$;

-- Crear conversación 1:1 (inserta ambos participantes con privilegios elevados)
create or replace function public.create_direct_conversation(
    peer_user_id uuid,
    peer_name text,
    context_type text default null,
    context_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    new_id uuid;
    me uuid := auth.uid();
    my_name text;
    in_peer_user_id uuid := peer_user_id;
    in_peer_name text := peer_name;
    in_context_type text := context_type;
    in_context_id uuid := context_id;
begin
    if me is null then
        raise exception 'Not authenticated';
    end if;
    if me = in_peer_user_id then
        raise exception 'Cannot chat with yourself';
    end if;

    select cp.conversation_id into new_id
    from public.conversation_participants cp
    where cp.user_id = me and cp.peer_user_id = in_peer_user_id
    limit 1;

    if new_id is not null then
        return new_id;
    end if;

    select u.name into my_name from public.users u where u.id = me;

    insert into public.conversations (context_type, context_id)
    values (in_context_type, in_context_id)
    returning id into new_id;

    insert into public.conversation_participants (conversation_id, user_id, peer_user_id, peer_name)
    values
        (new_id, me, in_peer_user_id, in_peer_name),
        (new_id, in_peer_user_id, me, coalesce(my_name, 'Usuario'));

    return new_id;
end;
$$;

grant execute on function public.create_direct_conversation(uuid, text, text, uuid) to authenticated;
