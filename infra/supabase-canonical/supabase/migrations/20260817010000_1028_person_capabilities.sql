-- LeoVer Canonical
-- Logical migration: 1028
-- RESCUE-01: generic PERSON capabilities. Not AccountType. Not a rescue mega-table.
-- First capability code: RESCUER. Localized labels are not stored as authority.

create table public.person_capabilities (
  user_id uuid not null references public.persons(user_id) on delete cascade,
  capability text not null check (capability in ('RESCUER')),
  active boolean not null default true,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  deactivated_at timestamptz null,
  primary key (user_id, capability)
);

create index person_capabilities_user_active_idx
  on public.person_capabilities (user_id)
  where active;

comment on table public.person_capabilities is
  'PERSON + capability. UX/availability only. Not identity, membership, or RLS authority.';

alter table public.person_capabilities enable row level security;
revoke all on table public.person_capabilities from anon, authenticated;

create or replace function public.canon_set_person_capability(
  p_capability text,
  p_active boolean default true
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_capability is distinct from 'RESCUER' then
    raise exception 'PERSON_CAPABILITY_UNKNOWN';
  end if;
  if not exists (select 1 from public.persons where user_id = auth.uid()) then
    raise exception 'PERSON_NOT_FOUND';
  end if;

  insert into public.person_capabilities (user_id, capability, active, deactivated_at, updated_at)
  values (
    auth.uid(),
    p_capability,
    coalesce(p_active, true),
    case when coalesce(p_active, true) then null else timezone('utc', now()) end,
    timezone('utc', now())
  )
  on conflict (user_id, capability) do update
    set active = excluded.active,
        deactivated_at = excluded.deactivated_at,
        updated_at = timezone('utc', now());

  return coalesce(p_active, true);
end;
$$;

create or replace function public.canon_list_my_person_capabilities()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'capability', c.capability,
      'active', c.active,
      'updated_at', c.updated_at
    ) order by c.capability)
    from public.person_capabilities c
    where c.user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_set_person_capability(text, boolean) from public, anon;
revoke all on function public.canon_list_my_person_capabilities() from public, anon;
grant execute on function public.canon_set_person_capability(text, boolean) to authenticated;
grant execute on function public.canon_list_my_person_capabilities() to authenticated;
