-- Leover Fase 2/3: completar comunidad + servicios profesionales + agenda/reservas

-- Solicitudes de tránsito (Fase 2)
create table if not exists public.foster_requests (
    id uuid primary key default gen_random_uuid(),
    foster_home_id uuid not null references public.foster_homes (id) on delete cascade,
    applicant_id uuid not null references public.users (id) on delete cascade,
    applicant_name text not null,
    message text not null,
    phone text,
    status text not null default 'PENDING',
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists foster_requests_home_idx on public.foster_requests (foster_home_id);

-- Interés en eventos
create table if not exists public.event_interests (
    event_id uuid not null references public.community_events (id) on delete cascade,
    user_id uuid not null references public.users (id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    primary key (event_id, user_id)
);

-- Perfiles de servicio (Fase 3: vet, educador, paseador, tienda)
create table if not exists public.service_profiles (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.users (id) on delete cascade,
    category text not null,
    name text not null,
    location text not null,
    description text not null default '',
    contact_info text,
    photo_url text,
    tags text[] not null default '{}',
    schedule_text text,
    price_from numeric,
    accepts_bookings boolean not null default true,
    active boolean not null default true,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint service_profiles_owner_category unique (owner_id, category)
);

create index if not exists service_profiles_category_idx
    on public.service_profiles (category) where active = true;

-- Reservas / agenda (sin pasarela de pago; estado de cobro manual)
create table if not exists public.service_bookings (
    id uuid primary key default gen_random_uuid(),
    service_id uuid not null references public.service_profiles (id) on delete cascade,
    provider_id uuid not null references public.users (id) on delete cascade,
    client_id uuid not null references public.users (id) on delete cascade,
    client_name text not null,
    scheduled_at timestamptz not null,
    notes text not null default '',
    status text not null default 'PENDING',
    payment_status text not null default 'UNPAID',
    payment_method text,
    amount numeric,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create index if not exists service_bookings_provider_idx
    on public.service_bookings (provider_id, scheduled_at);
create index if not exists service_bookings_client_idx
    on public.service_bookings (client_id, scheduled_at);

-- RLS
alter table public.foster_requests enable row level security;
alter table public.event_interests enable row level security;
alter table public.service_profiles enable row level security;
alter table public.service_bookings enable row level security;

drop policy if exists foster_requests_select on public.foster_requests;
create policy foster_requests_select on public.foster_requests for select to authenticated
    using (
        auth.uid() = applicant_id
        or exists (
            select 1 from public.foster_homes f
            where f.id = foster_home_id and f.host_id = auth.uid()
        )
    );

drop policy if exists foster_requests_insert on public.foster_requests;
create policy foster_requests_insert on public.foster_requests for insert to authenticated
    with check (auth.uid() = applicant_id);

drop policy if exists foster_requests_update on public.foster_requests;
create policy foster_requests_update on public.foster_requests for update to authenticated
    using (
        auth.uid() = applicant_id
        or exists (
            select 1 from public.foster_homes f
            where f.id = foster_home_id and f.host_id = auth.uid()
        )
    );

drop policy if exists event_interests_select on public.event_interests;
create policy event_interests_select on public.event_interests for select to authenticated using (true);

drop policy if exists event_interests_write on public.event_interests;
create policy event_interests_write on public.event_interests for all to authenticated
    using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists service_profiles_select on public.service_profiles;
create policy service_profiles_select on public.service_profiles for select to authenticated
    using (active = true or auth.uid() = owner_id);

drop policy if exists service_profiles_write on public.service_profiles;
create policy service_profiles_write on public.service_profiles for all to authenticated
    using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

drop policy if exists service_bookings_select on public.service_bookings;
create policy service_bookings_select on public.service_bookings for select to authenticated
    using (auth.uid() = provider_id or auth.uid() = client_id);

drop policy if exists service_bookings_insert on public.service_bookings;
create policy service_bookings_insert on public.service_bookings for insert to authenticated
    with check (auth.uid() = client_id);

drop policy if exists service_bookings_update on public.service_bookings;
create policy service_bookings_update on public.service_bookings for update to authenticated
    using (auth.uid() = provider_id or auth.uid() = client_id);

-- Reputación: sumar puntos y otorgar insignia básica
create or replace function public.add_reputation_points(
    target_user_id uuid,
    points integer,
    badge_type text default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.users
    set reputation_score = coalesce(reputation_score, 0) + points
    where id = target_user_id;

    if badge_type is not null then
        insert into public.user_badges (user_id, badge_type)
        values (target_user_id, badge_type)
        on conflict (user_id, badge_type) do nothing;
    end if;
end;
$$;

grant execute on function public.add_reputation_points(uuid, integer, text) to authenticated;
