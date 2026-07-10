-- LeoVer — Cierre Fase 1–3 + base Fase 4
-- Social (guardar/reportar/bloquear), adopciones (entrevistas), perdidos (avistamientos),
-- notificaciones, reseñas, catálogo, pagos, admin, matching stub

-- 1) Social
create table if not exists public.post_saves (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.users (id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    primary key (post_id, user_id)
);

create table if not exists public.user_blocks (
    blocker_id uuid not null references public.users (id) on delete cascade,
    blocked_id uuid not null references public.users (id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    primary key (blocker_id, blocked_id),
    constraint user_blocks_no_self check (blocker_id <> blocked_id)
);

create table if not exists public.content_reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.users (id) on delete cascade,
    target_type text not null, -- POST | USER | COMMENT
    target_id text not null,
    reason text not null,
    details text,
    status text not null default 'OPEN', -- OPEN | REVIEWED | DISMISSED | ACTIONED
    created_at timestamptz not null default timezone('utc', now()),
    reviewed_at timestamptz,
    reviewed_by uuid references public.users (id)
);

create index if not exists content_reports_status_idx on public.content_reports (status, created_at desc);

-- 2) Adopciones: entrevistas
alter table public.adoption_requests
    add column if not exists interview_at timestamptz,
    add column if not exists interview_notes text,
    add column if not exists interview_status text; -- NONE | SCHEDULED | DONE | NO_SHOW

-- 3) Perdidos: avistamientos
create table if not exists public.lost_found_sightings (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.lost_found_posts (id) on delete cascade,
    reporter_id uuid not null references public.users (id) on delete cascade,
    reporter_name text not null,
    note text not null,
    location_text text,
    latitude double precision,
    longitude double precision,
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists lost_found_sightings_post_idx
    on public.lost_found_sightings (post_id, created_at desc);

-- 4) Notificaciones in-app
create table if not exists public.notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users (id) on delete cascade,
    type text not null,
    title text not null,
    body text not null,
    related_id text,
    related_type text,
    read_at timestamptz,
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists notifications_user_idx
    on public.notifications (user_id, created_at desc);

-- 5) Servicios: reseñas + catálogo + pagos
create table if not exists public.service_reviews (
    id uuid primary key default gen_random_uuid(),
    service_id uuid not null references public.service_profiles (id) on delete cascade,
    author_id uuid not null references public.users (id) on delete cascade,
    author_name text not null,
    rating integer not null check (rating between 1 and 5),
    comment text not null default '',
    created_at timestamptz not null default timezone('utc', now()),
    unique (service_id, author_id)
);

create table if not exists public.shop_products (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.users (id) on delete cascade,
    service_id uuid references public.service_profiles (id) on delete set null,
    name text not null,
    description text not null default '',
    price numeric not null default 0,
    stock integer not null default 0,
    photo_url text,
    active boolean not null default true,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.payment_intents (
    id uuid primary key default gen_random_uuid(),
    booking_id uuid references public.service_bookings (id) on delete set null,
    payer_id uuid not null references public.users (id) on delete cascade,
    provider_id uuid not null references public.users (id) on delete cascade,
    amount numeric not null,
    currency text not null default 'ARS',
    status text not null default 'CREATED', -- CREATED | PENDING | PAID | FAILED | CANCELLED
    provider text not null default 'MANUAL', -- MANUAL | MERCADOPAGO_STUB
    external_ref text,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

-- 6) Fase 4 base: matching + historial clínico
create table if not exists public.adoption_matches (
    id uuid primary key default gen_random_uuid(),
    adoption_id uuid not null references public.adoptions (id) on delete cascade,
    user_id uuid not null references public.users (id) on delete cascade,
    score numeric not null default 0,
    reasons text[] not null default '{}',
    created_at timestamptz not null default timezone('utc', now()),
    unique (adoption_id, user_id)
);

create table if not exists public.pet_clinical_records (
    id uuid primary key default gen_random_uuid(),
    pet_id uuid not null references public.pets (id) on delete cascade,
    author_id uuid not null references public.users (id) on delete cascade,
    author_name text not null,
    record_type text not null default 'NOTE', -- NOTE | VACCINE | SURGERY | LAB | VISIT
    title text not null,
    notes text not null default '',
    recorded_at timestamptz not null default timezone('utc', now()),
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists pet_clinical_records_pet_idx
    on public.pet_clinical_records (pet_id, recorded_at desc);

-- RLS
alter table public.post_saves enable row level security;
alter table public.user_blocks enable row level security;
alter table public.content_reports enable row level security;
alter table public.lost_found_sightings enable row level security;
alter table public.notifications enable row level security;
alter table public.service_reviews enable row level security;
alter table public.shop_products enable row level security;
alter table public.payment_intents enable row level security;
alter table public.adoption_matches enable row level security;
alter table public.pet_clinical_records enable row level security;

drop policy if exists post_saves_all on public.post_saves;
create policy post_saves_all on public.post_saves for all to authenticated
    using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists user_blocks_all on public.user_blocks;
create policy user_blocks_all on public.user_blocks for all to authenticated
    using (auth.uid() = blocker_id) with check (auth.uid() = blocker_id);

drop policy if exists content_reports_select on public.content_reports;
create policy content_reports_select on public.content_reports for select to authenticated using (true);
drop policy if exists content_reports_insert on public.content_reports;
create policy content_reports_insert on public.content_reports for insert to authenticated
    with check (auth.uid() = reporter_id);
drop policy if exists content_reports_update on public.content_reports;
create policy content_reports_update on public.content_reports for update to authenticated using (true);

drop policy if exists sightings_select on public.lost_found_sightings;
create policy sightings_select on public.lost_found_sightings for select to authenticated using (true);
drop policy if exists sightings_insert on public.lost_found_sightings;
create policy sightings_insert on public.lost_found_sightings for insert to authenticated
    with check (auth.uid() = reporter_id);

drop policy if exists notifications_select on public.notifications;
create policy notifications_select on public.notifications for select to authenticated
    using (auth.uid() = user_id);
drop policy if exists notifications_update on public.notifications;
create policy notifications_update on public.notifications for update to authenticated
    using (auth.uid() = user_id);
drop policy if exists notifications_insert on public.notifications;
create policy notifications_insert on public.notifications for insert to authenticated with check (true);

drop policy if exists service_reviews_select on public.service_reviews;
create policy service_reviews_select on public.service_reviews for select to authenticated using (true);
drop policy if exists service_reviews_write on public.service_reviews;
create policy service_reviews_write on public.service_reviews for all to authenticated
    using (auth.uid() = author_id) with check (auth.uid() = author_id);

drop policy if exists shop_products_select on public.shop_products;
create policy shop_products_select on public.shop_products for select to authenticated
    using (active = true or auth.uid() = owner_id);
drop policy if exists shop_products_write on public.shop_products;
create policy shop_products_write on public.shop_products for all to authenticated
    using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

drop policy if exists payment_intents_select on public.payment_intents;
create policy payment_intents_select on public.payment_intents for select to authenticated
    using (auth.uid() = payer_id or auth.uid() = provider_id);
drop policy if exists payment_intents_insert on public.payment_intents;
create policy payment_intents_insert on public.payment_intents for insert to authenticated
    with check (auth.uid() = payer_id);
drop policy if exists payment_intents_update on public.payment_intents;
create policy payment_intents_update on public.payment_intents for update to authenticated
    using (auth.uid() = payer_id or auth.uid() = provider_id);

drop policy if exists adoption_matches_select on public.adoption_matches;
create policy adoption_matches_select on public.adoption_matches for select to authenticated using (true);

drop policy if exists clinical_select on public.pet_clinical_records;
create policy clinical_select on public.pet_clinical_records for select to authenticated using (true);
drop policy if exists clinical_insert on public.pet_clinical_records;
create policy clinical_insert on public.pet_clinical_records for insert to authenticated
    with check (auth.uid() = author_id);

-- Helper: crear notificación
create or replace function public.create_notification(
    target_user_id uuid,
    notif_type text,
    notif_title text,
    notif_body text,
    related_id text default null,
    related_type text default null
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    new_id uuid;
begin
    insert into public.notifications (user_id, type, title, body, related_id, related_type)
    values (target_user_id, notif_type, notif_title, notif_body, related_id, related_type)
    returning id into new_id;
    return new_id;
end;
$$;

grant execute on function public.create_notification(uuid, text, text, text, text, text) to authenticated;
