-- LeoVer Canonical Baseline
-- Logical migration: 1003
-- Platform roles: USER / MODERATOR / ADMIN / SUPERADMIN. Not org/pet/teen.

create table public.platform_roles (
  code text primary key,
  description text not null
);

create table public.platform_permissions (
  code text primary key,
  description text not null
);

create table public.platform_role_permissions (
  role_code text not null references public.platform_roles(code) on delete cascade,
  permission_code text not null references public.platform_permissions(code) on delete cascade,
  primary key (role_code, permission_code)
);

create table public.user_platform_role_assignments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.persons(user_id) on delete cascade,
  role_code text not null references public.platform_roles(code),
  granted_by uuid null references public.persons(user_id),
  granted_at timestamptz not null default timezone('utc', now()),
  revoked_at timestamptz null,
  unique (user_id, role_code)
);

create index user_platform_role_assignments_user_idx
  on public.user_platform_role_assignments (user_id)
  where revoked_at is null;
