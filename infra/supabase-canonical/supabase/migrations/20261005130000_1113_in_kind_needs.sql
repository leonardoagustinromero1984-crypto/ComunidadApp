-- 1113: organization material needs.
-- Not executed by this change.
--
-- in_kind_offers remains a person offer ("I can contribute X").
-- It is not a shelter need and this migration does not read it.
--
-- in_kind_needs is the organization publication.
-- in_kind_pledges is the commitment of a person against one need.
-- The pledge stores need_id, so the contribution cannot drift to another row.

create table if not exists public.in_kind_needs (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  created_by uuid not null references public.persons(user_id),
  title text not null check (char_length(title) between 3 and 120),
  description text not null check (char_length(description) between 1 and 2000),
  category text not null default 'OTHER'
    check (category in (
      'FOOD', 'MEDICATION', 'HYGIENE', 'BEDDING',
      'TRANSPORT_SUPPLIES', 'CONSTRUCTION_MATERIALS', 'OTHER'
    )),
  quantity_needed integer not null check (quantity_needed > 0),
  status text not null default 'PUBLISHED'
    check (status in ('PUBLISHED', 'CLOSED')),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);

create index if not exists in_kind_needs_org_status_idx
  on public.in_kind_needs (organization_id, status, created_at desc);

create table if not exists public.in_kind_pledges (
  id uuid primary key default gen_random_uuid(),
  need_id uuid not null references public.in_kind_needs(id) on delete cascade,
  pledged_by uuid not null references public.persons(user_id),
  quantity integer not null check (quantity > 0),
  message text null check (message is null or char_length(message) <= 1000),
  status text not null default 'PLEDGED'
    check (status in ('PLEDGED', 'DELIVERED', 'CANCELLED')),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  unique (need_id, pledged_by)
);

create index if not exists in_kind_pledges_need_idx
  on public.in_kind_pledges (need_id, status);

alter table public.in_kind_needs enable row level security;
alter table public.in_kind_pledges enable row level security;
revoke all on table public.in_kind_needs from public, anon, authenticated;
revoke all on table public.in_kind_pledges from public, anon, authenticated;

create or replace function public._canon_in_kind_need_is_manager(p_need_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
      from public.in_kind_needs n
     where n.id = p_need_id
       and public._acl_org_permission(auth.uid(), n.organization_id, 'org.edit')
  );
$$;

revoke all on function public._canon_in_kind_need_is_manager(uuid) from public, anon, authenticated;

create or replace function public._canon_in_kind_need_json(p_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', n.id,
    'organization_id', n.organization_id,
    'organization_display_name', org.name,
    'title', n.title,
    'description', n.description,
    'category', n.category,
    'status', n.status,
    'quantity_requested', n.quantity_needed,
    'quantity_pledged', coalesce((
      select sum(p.quantity)::int
        from public.in_kind_pledges p
       where p.need_id = n.id
         and p.status in ('PLEDGED', 'DELIVERED')
    ), 0),
    'quantity_delivered', coalesce((
      select sum(p.quantity)::int
        from public.in_kind_pledges p
       where p.need_id = n.id
         and p.status = 'DELIVERED'
    ), 0),
    'quantity_unit', 'unidades',
    'coverage_percent', least(100, coalesce((
      select ((sum(p.quantity) * 100) / n.quantity_needed)::int
        from public.in_kind_pledges p
       where p.need_id = n.id
         and p.status in ('PLEDGED', 'DELIVERED')
    ), 0)),
    'created_at', n.created_at
  )
  from public.in_kind_needs n
  join public.organizations org on org.id = n.organization_id
  where n.id = p_id;
$$;

revoke all on function public._canon_in_kind_need_json(uuid) from public, anon, authenticated;

create or replace function public.canon_list_in_kind_needs(p_organization_id uuid default null)
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
    select jsonb_agg(public._canon_in_kind_need_json(n.id) order by n.created_at desc)
      from public.in_kind_needs n
     where n.status = 'PUBLISHED'
       and (p_organization_id is null or n.organization_id = p_organization_id)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_in_kind_need(p_need_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_status text;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  select n.status into v_status
    from public.in_kind_needs n
   where n.id = p_need_id;
  if v_status is null then
    raise exception 'NEED_NOT_FOUND';
  end if;
  if v_status <> 'PUBLISHED' and not public._canon_in_kind_need_is_manager(p_need_id) then
    raise exception 'NEED_NOT_FOUND';
  end if;
  return public._canon_in_kind_need_json(p_need_id) || jsonb_build_object(
    'can_manage', public._canon_in_kind_need_is_manager(p_need_id)
  );
end;
$$;

create or replace function public.canon_pledge_in_kind_need(
  p_need_id uuid,
  p_quantity integer,
  p_message text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_status text;
  v_quantity integer;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_quantity is null or p_quantity < 1 then
    raise exception 'INVALID_QUANTITY';
  end if;
  if not exists (
    select 1 from public.in_kind_needs n
     where n.id = p_need_id and n.status = 'PUBLISHED'
  ) then
    raise exception 'NEED_NOT_FOUND';
  end if;
  insert into public.in_kind_pledges (
    need_id, pledged_by, quantity, message, status
  ) values (
    p_need_id, auth.uid(), p_quantity, nullif(btrim(p_message), ''), 'PLEDGED'
  )
  on conflict (need_id, pledged_by) do update
    set quantity = excluded.quantity,
        message = excluded.message,
        status = 'PLEDGED',
        updated_at = timezone('utc', now())
    where public.in_kind_pledges.status = 'CANCELLED'
  returning id, status, quantity into v_id, v_status, v_quantity;
  if v_id is null then
    select p.id, p.status, p.quantity into v_id, v_status, v_quantity
      from public.in_kind_pledges p
     where p.need_id = p_need_id
       and p.pledged_by = auth.uid();
  end if;
  return jsonb_build_object(
    'id', v_id,
    'need_id', p_need_id,
    'pledged_by', auth.uid(),
    'quantity', v_quantity,
    'status', v_status,
    'message', nullif(btrim(p_message), '')
  );
end;
$$;

create or replace function public.canon_list_in_kind_pledges(p_need_id uuid)
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
  if not public._canon_in_kind_need_is_manager(p_need_id) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', p.id,
      'need_id', p.need_id,
      'pledged_by', p.pledged_by,
      'quantity', p.quantity,
      'message', p.message,
      'status', p.status,
      'created_at', p.created_at
    ) order by p.created_at)
      from public.in_kind_pledges p
     where p.need_id = p_need_id
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_in_kind_pledges()
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
      'id', p.id,
      'need_id', p.need_id,
      'need_title', n.title,
      'organization_name', org.name,
      'quantity', p.quantity,
      'unit', 'unidades',
      'status', p.status,
      'created_at', p.created_at
    ) order by p.created_at desc)
      from public.in_kind_pledges p
      join public.in_kind_needs n on n.id = p.need_id
      join public.organizations org on org.id = n.organization_id
     where p.pledged_by = auth.uid()
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_mark_in_kind_pledge_delivered(p_pledge_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v public.in_kind_pledges%rowtype;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  select * into v from public.in_kind_pledges where id = p_pledge_id;
  if v.id is null then
    raise exception 'PLEDGE_NOT_FOUND';
  end if;
  if not public._canon_in_kind_need_is_manager(v.need_id) then
    raise exception 'FORBIDDEN';
  end if;
  if v.pledged_by = auth.uid() then
    raise exception 'FORBIDDEN';
  end if;
  if v.status = 'PLEDGED' then
    update public.in_kind_pledges
       set status = 'DELIVERED',
           updated_at = timezone('utc', now())
     where id = v.id
       and status = 'PLEDGED';
    v.status := 'DELIVERED';
  end if;
  return jsonb_build_object(
    'id', v.id,
    'need_id', v.need_id,
    'pledged_by', v.pledged_by,
    'quantity', v.quantity,
    'status', v.status
  );
end;
$$;

revoke all on function public.canon_list_in_kind_needs(uuid) from public, anon;
revoke all on function public.canon_get_in_kind_need(uuid) from public, anon;
revoke all on function public.canon_pledge_in_kind_need(uuid, integer, text) from public, anon;
revoke all on function public.canon_list_in_kind_pledges(uuid) from public, anon;
revoke all on function public.canon_list_my_in_kind_pledges() from public, anon;
revoke all on function public.canon_mark_in_kind_pledge_delivered(uuid) from public, anon;

grant execute on function public.canon_list_in_kind_needs(uuid) to authenticated;
grant execute on function public.canon_get_in_kind_need(uuid) to authenticated;
grant execute on function public.canon_pledge_in_kind_need(uuid, integer, text) to authenticated;
grant execute on function public.canon_list_in_kind_pledges(uuid) to authenticated;
grant execute on function public.canon_list_my_in_kind_pledges() to authenticated;
grant execute on function public.canon_mark_in_kind_pledge_delivered(uuid) to authenticated;

notify pgrst, 'reload schema';
