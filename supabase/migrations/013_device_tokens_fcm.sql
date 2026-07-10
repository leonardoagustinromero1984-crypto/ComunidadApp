-- LeoVer — Push / retención: tokens FCM por dispositivo

create table if not exists public.device_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users (id) on delete cascade,
    token text not null,
    platform text not null default 'android',
    updated_at timestamptz not null default timezone('utc', now()),
    created_at timestamptz not null default timezone('utc', now()),
    unique (user_id, token)
);

create index if not exists device_tokens_user_idx on public.device_tokens (user_id);

alter table public.device_tokens enable row level security;

drop policy if exists device_tokens_select on public.device_tokens;
create policy device_tokens_select on public.device_tokens for select to authenticated
    using (auth.uid() = user_id);

drop policy if exists device_tokens_write on public.device_tokens;
create policy device_tokens_write on public.device_tokens for all to authenticated
    using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Service role / edge function needs to read tokens for any user
-- (service role bypasses RLS)
