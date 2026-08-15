-- LeoVer Canonical Baseline
-- Logical migration: 1019
-- Minimal M29 support. No second social network. UNDER_18 sponsored distribution = NO.

create table public.brand_campaigns (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  title text not null,
  status text not null default 'DRAFT' check (status in ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.brand_placements (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.brand_campaigns(id) on delete cascade,
  post_id uuid not null references public.social_posts(id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now())
);
