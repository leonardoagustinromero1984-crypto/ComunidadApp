-- LeoVer Canonical Baseline
-- Logical migration: 1011
-- M19 social. Moments are not posts. Sponsored marker support only.

create table public.social_posts (
  id uuid primary key default gen_random_uuid(),
  author_user_id uuid not null references public.persons(user_id),
  body text null,
  visibility text not null default 'PUBLIC'
    check (visibility in ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
  sponsored boolean not null default false,
  hidden_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.social_comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.social_posts(id) on delete cascade,
  author_user_id uuid not null references public.persons(user_id),
  body text not null,
  hidden_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.social_reactions (
  post_id uuid not null references public.social_posts(id) on delete cascade,
  user_id uuid not null references public.persons(user_id),
  kind text not null default 'LIKE',
  created_at timestamptz not null default timezone('utc', now()),
  primary key (post_id, user_id)
);

create table public.social_stories (
  id uuid primary key default gen_random_uuid(),
  author_user_id uuid not null references public.persons(user_id),
  asset_id uuid null references public.media_assets(id),
  expires_at timestamptz not null,
  hidden_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now())
);
