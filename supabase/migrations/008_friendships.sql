-- Leover — Amistades y privacidad de perfiles (tipo IG)

create table if not exists public.friend_connections (
    id uuid primary key default gen_random_uuid(),
    requester_id uuid not null references public.users (id) on delete cascade,
    addressee_id uuid not null references public.users (id) on delete cascade,
    status text not null default 'PENDING',
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint friend_connections_no_self check (requester_id <> addressee_id),
    constraint friend_connections_unique_pair unique (requester_id, addressee_id)
);

create index if not exists friend_connections_addressee_idx
    on public.friend_connections (addressee_id, status);

create index if not exists friend_connections_requester_idx
    on public.friend_connections (requester_id, status);

alter table public.friend_connections enable row level security;

drop policy if exists friend_connections_select on public.friend_connections;
create policy friend_connections_select
    on public.friend_connections for select to authenticated
    using (auth.uid() = requester_id or auth.uid() = addressee_id);

drop policy if exists friend_connections_insert on public.friend_connections;
create policy friend_connections_insert
    on public.friend_connections for insert to authenticated
    with check (auth.uid() = requester_id and status = 'PENDING');

drop policy if exists friend_connections_update on public.friend_connections;
create policy friend_connections_update
    on public.friend_connections for update to authenticated
    using (auth.uid() = requester_id or auth.uid() = addressee_id);
