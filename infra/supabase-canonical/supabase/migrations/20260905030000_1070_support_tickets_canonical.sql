-- 1070 SUPPORT: real ticket queue/detail/actions on existing support_tickets.
-- Additive columns + messages. Permission-driven. No mock. No moderation grants.

alter table public.support_tickets
  add column if not exists category text not null default 'OTHER',
  add column if not exists description text not null default '',
  add column if not exists priority text not null default 'NORMAL',
  add column if not exists assigned_to uuid,
  add column if not exists updated_at timestamptz not null default timezone('utc', now()),
  add column if not exists resolved_at timestamptz,
  add column if not exists closed_at timestamptz,
  add column if not exists close_reason_code text,
  add column if not exists linked_moderation_case_id uuid;

alter table public.support_tickets drop constraint if exists support_tickets_status_check;
alter table public.support_tickets
  add constraint support_tickets_status_check
  check (status in (
    'OPEN', 'IN_PROGRESS', 'WAITING_USER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED'
  ));

alter table public.support_tickets drop constraint if exists support_tickets_category_check;
alter table public.support_tickets
  add constraint support_tickets_category_check
  check (category in (
    'ACCOUNT_ACCESS', 'PROFILE', 'ORGANIZATION', 'TECHNICAL',
    'PRIVACY', 'SAFETY', 'CONTENT', 'OTHER'
  ));

create table if not exists public.support_ticket_messages (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null references public.support_tickets(id) on delete cascade,
  author_user_id uuid not null,
  visibility text not null check (visibility in ('REQUESTER_VISIBLE', 'INTERNAL')),
  body text not null,
  created_at timestamptz not null default timezone('utc', now())
);

create index if not exists support_ticket_messages_ticket_idx
  on public.support_ticket_messages (ticket_id, created_at);

alter table public.support_tickets enable row level security;
alter table public.support_ticket_messages enable row level security;

drop policy if exists support_tickets_requester_select on public.support_tickets;
create policy support_tickets_requester_select on public.support_tickets
for select to authenticated
using (opened_by = auth.uid());

drop policy if exists support_ticket_messages_requester_select on public.support_ticket_messages;
create policy support_ticket_messages_requester_select on public.support_ticket_messages
for select to authenticated
using (
  visibility = 'REQUESTER_VISIBLE'
  and exists (
    select 1 from public.support_tickets t
    where t.id = ticket_id and t.opened_by = auth.uid()
  )
);

revoke all on table public.support_tickets from public, anon;
revoke all on table public.support_ticket_messages from public, anon;
grant select on table public.support_tickets to authenticated;
grant select on table public.support_ticket_messages to authenticated;

create or replace function public._canon_support_ticket_json(t public.support_tickets)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', t.id,
    'requester_user_id', t.opened_by,
    'category', t.category,
    'subject', t.subject,
    'description', t.description,
    'priority', t.priority,
    'status', t.status,
    'assigned_to_user_id', t.assigned_to,
    'created_at', t.created_at,
    'updated_at', t.updated_at,
    'resolved_at', t.resolved_at,
    'closed_at', t.closed_at,
    'close_reason_code', t.close_reason_code,
    'linked_moderation_case_id', t.linked_moderation_case_id
  );
$$;

revoke all on function public._canon_support_ticket_json(public.support_tickets)
  from public, anon, authenticated;

create or replace function public._canon_support_messages_json(p_ticket uuid, p_include_internal boolean)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', m.id,
    'ticket_id', m.ticket_id,
    'author_user_id', m.author_user_id,
    'visibility', m.visibility,
    'body', m.body,
    'created_at', m.created_at
  ) order by m.created_at), '[]'::jsonb)
  from public.support_ticket_messages m
  where m.ticket_id = p_ticket
    and (p_include_internal or m.visibility = 'REQUESTER_VISIBLE');
$$;

revoke all on function public._canon_support_messages_json(uuid, boolean)
  from public, anon, authenticated;

create or replace function public.canon_create_support_ticket(
  p_category text, p_subject text, p_description text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  t public.support_tickets%rowtype;
  v_cat text := upper(btrim(coalesce(p_category, 'OTHER')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (select 1 from public.persons p where p.user_id = auth.uid()) then
    raise exception 'PERSON_REQUIRED';
  end if;
  if v_cat not in (
    'ACCOUNT_ACCESS', 'PROFILE', 'ORGANIZATION', 'TECHNICAL',
    'PRIVACY', 'SAFETY', 'CONTENT', 'OTHER'
  ) then
    v_cat := 'OTHER';
  end if;
  if length(btrim(coalesce(p_subject, ''))) < 3 then raise exception 'VALIDATION'; end if;
  if length(btrim(coalesce(p_description, ''))) < 1 then raise exception 'VALIDATION'; end if;
  insert into public.support_tickets (opened_by, subject, description, category, status)
  values (auth.uid(), btrim(p_subject), btrim(p_description), v_cat, 'OPEN')
  returning * into t;
  return jsonb_build_object('ticket', public._canon_support_ticket_json(t));
end;
$$;

create or replace function public.canon_list_my_support_tickets(p_limit integer default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(public._canon_support_ticket_json(t) order by t.created_at desc)
    from (
      select * from public.support_tickets
      where opened_by = auth.uid()
      order by created_at desc
      limit least(greatest(coalesce(p_limit, 50), 1), 100)
    ) t
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_support_ticket_for_requester(p_ticket_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  t public.support_tickets%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into t from public.support_tickets where id = p_ticket_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if t.opened_by <> auth.uid() then raise exception 'FORBIDDEN'; end if;
  return jsonb_build_object(
    'ticket', public._canon_support_ticket_json(t),
    'messages', public._canon_support_messages_json(t.id, false)
  );
end;
$$;

create or replace function public.canon_list_support_queue(p_limit integer default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_sensitive boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_has_platform_permission(auth.uid(), 'support.view') then
    raise exception 'FORBIDDEN';
  end if;
  v_sensitive := public._acl_has_platform_permission(auth.uid(), 'support.view_sensitive');
  return coalesce((
    select jsonb_agg(public._canon_support_ticket_json(t) order by t.created_at desc)
    from (
      select * from public.support_tickets
      where v_sensitive or category not in ('PRIVACY', 'SAFETY')
      order by created_at desc
      limit least(greatest(coalesce(p_limit, 50), 1), 100)
    ) t
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_support_ticket_for_staff(p_ticket_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  t public.support_tickets%rowtype;
  v_sensitive boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_has_platform_permission(auth.uid(), 'support.view') then
    raise exception 'FORBIDDEN';
  end if;
  select * into t from public.support_tickets where id = p_ticket_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  v_sensitive := public._acl_has_platform_permission(auth.uid(), 'support.view_sensitive');
  if t.category in ('PRIVACY', 'SAFETY') and not v_sensitive then
    raise exception 'FORBIDDEN';
  end if;
  return jsonb_build_object(
    'ticket', public._canon_support_ticket_json(t),
    'messages', public._canon_support_messages_json(
      t.id,
      public._acl_has_platform_permission(auth.uid(), 'support.manage')
    )
  );
end;
$$;

create or replace function public.canon_assign_support_ticket(
  p_ticket_id uuid, p_assigned_to_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  t public.support_tickets%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_has_platform_permission(auth.uid(), 'support.manage') then
    raise exception 'FORBIDDEN';
  end if;
  update public.support_tickets
     set assigned_to = coalesce(p_assigned_to_user_id, auth.uid()),
         status = case when status = 'OPEN' then 'IN_PROGRESS' else status end,
         updated_at = timezone('utc', now())
   where id = p_ticket_id
  returning * into t;
  if not found then raise exception 'NOT_FOUND'; end if;
  return public._canon_support_ticket_json(t);
end;
$$;

create or replace function public.canon_change_support_ticket_status(
  p_ticket_id uuid, p_status text, p_close_reason_code text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  t public.support_tickets%rowtype;
  v_status text := upper(btrim(coalesce(p_status, '')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_has_platform_permission(auth.uid(), 'support.manage') then
    raise exception 'FORBIDDEN';
  end if;
  if v_status not in (
    'OPEN', 'IN_PROGRESS', 'WAITING_USER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED'
  ) then
    raise exception 'VALIDATION';
  end if;
  update public.support_tickets
     set status = v_status,
         close_reason_code = case when v_status = 'CLOSED' then p_close_reason_code else close_reason_code end,
         resolved_at = case when v_status = 'RESOLVED' then timezone('utc', now()) else resolved_at end,
         closed_at = case when v_status = 'CLOSED' then timezone('utc', now()) else closed_at end,
         updated_at = timezone('utc', now())
   where id = p_ticket_id
  returning * into t;
  if not found then raise exception 'NOT_FOUND'; end if;
  return public._canon_support_ticket_json(t);
end;
$$;

create or replace function public.canon_add_support_requester_message(p_ticket_id uuid, p_body text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  t public.support_tickets%rowtype;
  m public.support_ticket_messages%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if length(btrim(coalesce(p_body, ''))) < 1 then raise exception 'VALIDATION'; end if;
  select * into t from public.support_tickets where id = p_ticket_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if t.opened_by <> auth.uid()
     and not public._acl_has_platform_permission(auth.uid(), 'support.manage') then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.support_ticket_messages (ticket_id, author_user_id, visibility, body)
  values (p_ticket_id, auth.uid(), 'REQUESTER_VISIBLE', btrim(p_body))
  returning * into m;
  update public.support_tickets set updated_at = timezone('utc', now()) where id = p_ticket_id;
  return jsonb_build_object(
    'id', m.id,
    'ticket_id', m.ticket_id,
    'author_user_id', m.author_user_id,
    'visibility', m.visibility,
    'body', m.body,
    'created_at', m.created_at
  );
end;
$$;

create or replace function public.canon_add_support_internal_message(p_ticket_id uuid, p_body text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  m public.support_ticket_messages%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_has_platform_permission(auth.uid(), 'support.manage') then
    raise exception 'FORBIDDEN';
  end if;
  if length(btrim(coalesce(p_body, ''))) < 1 then raise exception 'VALIDATION'; end if;
  if not exists (select 1 from public.support_tickets where id = p_ticket_id) then
    raise exception 'NOT_FOUND';
  end if;
  insert into public.support_ticket_messages (ticket_id, author_user_id, visibility, body)
  values (p_ticket_id, auth.uid(), 'INTERNAL', btrim(p_body))
  returning * into m;
  update public.support_tickets set updated_at = timezone('utc', now()) where id = p_ticket_id;
  return jsonb_build_object(
    'id', m.id,
    'ticket_id', m.ticket_id,
    'author_user_id', m.author_user_id,
    'visibility', m.visibility,
    'body', m.body,
    'created_at', m.created_at
  );
end;
$$;

revoke all on function public.canon_create_support_ticket(text, text, text) from public, anon;
revoke all on function public.canon_list_my_support_tickets(integer) from public, anon;
revoke all on function public.canon_get_support_ticket_for_requester(uuid) from public, anon;
revoke all on function public.canon_list_support_queue(integer) from public, anon;
revoke all on function public.canon_get_support_ticket_for_staff(uuid) from public, anon;
revoke all on function public.canon_assign_support_ticket(uuid, uuid) from public, anon;
revoke all on function public.canon_change_support_ticket_status(uuid, text, text) from public, anon;
revoke all on function public.canon_add_support_requester_message(uuid, text) from public, anon;
revoke all on function public.canon_add_support_internal_message(uuid, text) from public, anon;

grant execute on function public.canon_create_support_ticket(text, text, text) to authenticated;
grant execute on function public.canon_list_my_support_tickets(integer) to authenticated;
grant execute on function public.canon_get_support_ticket_for_requester(uuid) to authenticated;
grant execute on function public.canon_list_support_queue(integer) to authenticated;
grant execute on function public.canon_get_support_ticket_for_staff(uuid) to authenticated;
grant execute on function public.canon_assign_support_ticket(uuid, uuid) to authenticated;
grant execute on function public.canon_change_support_ticket_status(uuid, text, text) to authenticated;
grant execute on function public.canon_add_support_requester_message(uuid, text) to authenticated;
grant execute on function public.canon_add_support_internal_message(uuid, text) to authenticated;
