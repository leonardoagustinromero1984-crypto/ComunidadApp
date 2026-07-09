-- Fix: peer_user_id / context_type ambiguos en create_direct_conversation (PL/pgSQL)

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
