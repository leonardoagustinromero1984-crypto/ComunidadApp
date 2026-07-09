-- Leover Fase 1 — Módulos comunitarios (adopciones, perdidos/encontrados)
-- Ejecutar después de 001–004

-- Módulos activos por usuario (Documento Funcional §5)
alter table public.users
    add column if not exists active_modules text[] not null default '{}';

-- Perfil de mascota ampliado (Documento Funcional §7)
alter table public.pets
    add column if not exists weight_kg real,
    add column if not exists color text,
    add column if not exists breed text,
    add column if not exists personality text,
    add column if not exists location_text text;

create table if not exists public.adoptions (
    id uuid primary key default gen_random_uuid(),
    publisher_id uuid not null references public.users (id) on delete cascade,
    publisher_name text not null,
    shelter_id uuid references public.users (id) on delete set null,
    name text not null,
    photo_url text,
    species text not null,
    sex text not null,
    age_years integer not null default 0,
    age_months integer not null default 0,
    size text not null,
    location text not null,
    description text not null,
    status text not null default 'AVAILABLE',
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.lost_found_posts (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references public.users (id) on delete cascade,
    author_name text not null,
    type text not null,
    pet_name text,
    species text not null,
    photo_url text,
    location text not null,
    description text not null,
    contact_info text not null,
    status text not null default 'ACTIVE',
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create index if not exists adoptions_status_created_at_idx
    on public.adoptions (status, created_at desc);

create index if not exists adoptions_publisher_id_idx
    on public.adoptions (publisher_id);

create index if not exists lost_found_status_created_at_idx
    on public.lost_found_posts (status, created_at desc);

create index if not exists lost_found_type_idx
    on public.lost_found_posts (type);

alter table public.adoptions enable row level security;
alter table public.lost_found_posts enable row level security;

drop policy if exists adoptions_select_authenticated on public.adoptions;
create policy adoptions_select_authenticated
    on public.adoptions for select to authenticated
    using (true);

drop policy if exists adoptions_insert_own on public.adoptions;
create policy adoptions_insert_own
    on public.adoptions for insert to authenticated
    with check (auth.uid() = publisher_id);

drop policy if exists adoptions_update_own on public.adoptions;
create policy adoptions_update_own
    on public.adoptions for update to authenticated
    using (auth.uid() = publisher_id);

drop policy if exists adoptions_delete_own on public.adoptions;
create policy adoptions_delete_own
    on public.adoptions for delete to authenticated
    using (auth.uid() = publisher_id);

drop policy if exists lost_found_select_authenticated on public.lost_found_posts;
create policy lost_found_select_authenticated
    on public.lost_found_posts for select to authenticated
    using (true);

drop policy if exists lost_found_insert_own on public.lost_found_posts;
create policy lost_found_insert_own
    on public.lost_found_posts for insert to authenticated
    with check (auth.uid() = author_id);

drop policy if exists lost_found_update_own on public.lost_found_posts;
create policy lost_found_update_own
    on public.lost_found_posts for update to authenticated
    using (auth.uid() = author_id);

drop policy if exists lost_found_delete_own on public.lost_found_posts;
create policy lost_found_delete_own
    on public.lost_found_posts for delete to authenticated
    using (auth.uid() = author_id);

-- Realtime (idempotente: no falla si la tabla ya está en la publicación)
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'adoptions'
    ) then
        alter publication supabase_realtime add table public.adoptions;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'lost_found_posts'
    ) then
        alter publication supabase_realtime add table public.lost_found_posts;
    end if;
end $$;
