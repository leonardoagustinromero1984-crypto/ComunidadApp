-- LeoVer Canonical Baseline
-- Logical migration: 1018
-- Moderation, support, notifications, security audit.

create table public.content_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_user_id uuid not null references public.persons(user_id),
  target_table text not null,
  target_id uuid not null,
  reason_code text not null references public.moderation_reason_codes(code),
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.moderation_cases (
  id uuid primary key default gen_random_uuid(),
  report_id uuid null references public.content_reports(id),
  assigned_to uuid null references public.persons(user_id),
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.support_tickets (
  id uuid primary key default gen_random_uuid(),
  opened_by uuid not null references public.persons(user_id),
  subject text not null,
  status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.persons(user_id),
  kind text not null,
  payload jsonb not null default '{}'::jsonb,
  read_at timestamptz null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.notification_outbox (
  id uuid primary key default gen_random_uuid(),
  notification_id uuid not null references public.notifications(id) on delete cascade,
  channel text not null check (channel in ('PUSH', 'EMAIL')),
  status text not null default 'PENDING' check (status in ('PENDING', 'SENT', 'FAILED')),
  created_at timestamptz not null default timezone('utc', now())
);

create table public.security_audit_events (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid null references public.persons(user_id),
  action text not null,
  entity_table text not null,
  entity_id uuid null,
  occurred_at timestamptz not null default timezone('utc', now()),
  metadata jsonb not null default '{}'::jsonb
);

create index security_audit_events_occurred_idx on public.security_audit_events (occurred_at);
create index security_audit_events_actor_idx on public.security_audit_events (actor_user_id);
