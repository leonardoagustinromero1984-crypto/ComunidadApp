-- Leover initial schema for Supabase (Postgres)
-- Run in Supabase Dashboard → SQL Editor

create extension if not exists "pgcrypto";

create table if not exists public.users (
    id uuid primary key references auth.users (id) on delete cascade,
    email text not null,
    name text not null,
    account_type text not null default 'PERSON',
    profile_image_url text,
    bio text,
    location_text text,
    phone text,
    phone_public boolean not null default false,
    email_verified boolean not null default false,
    foster_home_active boolean not null default false,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.pets (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.users (id) on delete cascade,
    name text not null,
    photo_url text,
    species text not null,
    sex text not null,
    age_years integer not null default 0,
    age_months integer not null default 0,
    size text not null,
    description text not null,
    vaccinations jsonb not null default '[]'::jsonb,
    last_deworming text,
    last_flea_treatment text,
    reminders jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.posts (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references public.users (id) on delete cascade,
    author_name text not null,
    author_image_url text,
    type text not null,
    title text not null,
    content text not null,
    image_url text,
    location_text text,
    like_count integer not null default 0,
    comment_count integer not null default 0,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create index if not exists pets_owner_id_created_at_idx
    on public.pets (owner_id, created_at desc);

create index if not exists posts_created_at_idx
    on public.posts (created_at desc);

alter table public.users enable row level security;
alter table public.pets enable row level security;
alter table public.posts enable row level security;

drop policy if exists users_select_authenticated on public.users;
create policy users_select_authenticated
    on public.users for select to authenticated
    using (true);

drop policy if exists users_insert_own on public.users;
create policy users_insert_own
    on public.users for insert to authenticated
    with check (auth.uid() = id);

drop policy if exists users_update_own on public.users;
create policy users_update_own
    on public.users for update to authenticated
    using (auth.uid() = id);

drop policy if exists pets_select_authenticated on public.pets;
create policy pets_select_authenticated
    on public.pets for select to authenticated
    using (true);

drop policy if exists pets_insert_own on public.pets;
create policy pets_insert_own
    on public.pets for insert to authenticated
    with check (auth.uid() = owner_id);

drop policy if exists pets_update_own on public.pets;
create policy pets_update_own
    on public.pets for update to authenticated
    using (auth.uid() = owner_id);

drop policy if exists pets_delete_own on public.pets;
create policy pets_delete_own
    on public.pets for delete to authenticated
    using (auth.uid() = owner_id);

drop policy if exists posts_select_authenticated on public.posts;
create policy posts_select_authenticated
    on public.posts for select to authenticated
    using (true);

drop policy if exists posts_insert_own on public.posts;
create policy posts_insert_own
    on public.posts for insert to authenticated
    with check (auth.uid() = author_id);

drop policy if exists posts_update_own on public.posts;
create policy posts_update_own
    on public.posts for update to authenticated
    using (auth.uid() = author_id);

drop policy if exists posts_delete_own on public.posts;
create policy posts_delete_own
    on public.posts for delete to authenticated
    using (auth.uid() = author_id);

alter publication supabase_realtime add table public.users;
alter publication supabase_realtime add table public.pets;
alter publication supabase_realtime add table public.posts;
