-- Leover Sprints A–D: social, adopciones, comunidad, reputación

-- Sprint A: likes y comentarios
create table if not exists public.post_likes (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.users (id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    primary key (post_id, user_id)
);

create table if not exists public.post_comments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.posts (id) on delete cascade,
    author_id uuid not null references public.users (id) on delete cascade,
    author_name text not null,
    content text not null,
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists post_comments_post_id_idx on public.post_comments (post_id, created_at);

-- Sprint B: solicitudes de adopción
create table if not exists public.adoption_requests (
    id uuid primary key default gen_random_uuid(),
    adoption_id uuid not null references public.adoptions (id) on delete cascade,
    applicant_id uuid not null references public.users (id) on delete cascade,
    applicant_name text not null,
    message text not null,
    phone text,
    status text not null default 'PENDING',
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists adoption_requests_adoption_idx on public.adoption_requests (adoption_id);

-- Sprint C: coordenadas en perdidos
alter table public.lost_found_posts
    add column if not exists latitude double precision,
    add column if not exists longitude double precision;

-- Sprint D: comunidad Fase 2
create table if not exists public.shelters (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.users (id) on delete cascade,
    name text not null,
    location text not null,
    description text not null,
    photo_url text,
    contact_phone text,
    contact_email text,
    adoption_pet_ids text[] not null default '{}',
    needs jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.foster_homes (
    id uuid primary key default gen_random_uuid(),
    host_id uuid not null references public.users (id) on delete cascade,
    host_name text not null,
    location text not null,
    capacity integer not null default 1,
    accepted_species text[] not null default '{}',
    notes text not null default '',
    available boolean not null default true,
    contact_info text not null,
    photo_url text,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.community_events (
    id uuid primary key default gen_random_uuid(),
    organizer_id uuid not null references public.users (id) on delete cascade,
    title text not null,
    location text not null,
    event_date text not null,
    organizer_name text not null,
    description text not null,
    contact_info text not null,
    photo_url text,
    event_type text not null default 'ADOPTION_FAIR',
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.donation_campaigns (
    id uuid primary key default gen_random_uuid(),
    organizer_id uuid not null references public.users (id) on delete cascade,
    title text not null,
    description text not null,
    location text not null,
    goal_amount numeric,
    raised_amount numeric not null default 0,
    donation_type text not null default 'MONEY',
    photo_url text,
    active boolean not null default true,
    created_at timestamptz not null default timezone('utc', now())
);

-- Reputación e insignias
alter table public.users
    add column if not exists reputation_score integer not null default 0;

create table if not exists public.user_badges (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users (id) on delete cascade,
    badge_type text not null,
    earned_at timestamptz not null default timezone('utc', now()),
    unique (user_id, badge_type)
);

-- RLS
alter table public.post_likes enable row level security;
alter table public.post_comments enable row level security;
alter table public.adoption_requests enable row level security;
alter table public.shelters enable row level security;
alter table public.foster_homes enable row level security;
alter table public.community_events enable row level security;
alter table public.donation_campaigns enable row level security;
alter table public.user_badges enable row level security;

create policy post_likes_select on public.post_likes for select to authenticated using (true);
create policy post_likes_insert on public.post_likes for insert to authenticated with check (auth.uid() = user_id);
create policy post_likes_delete on public.post_likes for delete to authenticated using (auth.uid() = user_id);

create policy post_comments_select on public.post_comments for select to authenticated using (true);
create policy post_comments_insert on public.post_comments for insert to authenticated with check (auth.uid() = author_id);

create policy adoption_requests_select on public.adoption_requests for select to authenticated using (true);
create policy adoption_requests_insert on public.adoption_requests for insert to authenticated with check (auth.uid() = applicant_id);
create policy adoption_requests_update on public.adoption_requests for update to authenticated
    using (auth.uid() = applicant_id or exists (
        select 1 from public.adoptions a where a.id = adoption_id and a.publisher_id = auth.uid()
    ));

create policy shelters_select on public.shelters for select to authenticated using (true);
create policy shelters_write on public.shelters for all to authenticated using (auth.uid() = owner_id);

create policy foster_homes_select on public.foster_homes for select to authenticated using (true);
create policy foster_homes_write on public.foster_homes for all to authenticated using (auth.uid() = host_id);

create policy events_select on public.community_events for select to authenticated using (true);
create policy events_write on public.community_events for all to authenticated using (auth.uid() = organizer_id);

create policy donations_select on public.donation_campaigns for select to authenticated using (true);
create policy donations_write on public.donation_campaigns for all to authenticated using (auth.uid() = organizer_id);

create policy badges_select on public.user_badges for select to authenticated using (true);
create policy badges_insert on public.user_badges for insert to authenticated with check (auth.uid() = user_id);

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'post_comments'
    ) then
        alter publication supabase_realtime add table public.post_comments;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'adoption_requests'
    ) then
        alter publication supabase_realtime add table public.adoption_requests;
    end if;
end $$;
