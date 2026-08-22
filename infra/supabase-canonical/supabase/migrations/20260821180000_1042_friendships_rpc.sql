-- LeoVer Canonical — 1042
-- Friendships still failed after 1041 because Android SELECT/INSERT on the table
-- depends on GRANT that may not be present. Security-definer RPCs work via
-- EXECUTE (granted here). Also reaffirms table GRANT + participant RLS (idempotent).
-- Apply on STAGING (tobqbddfcyitwgbkthhy) before QA. Do not apply to production.

grant select, insert, update, delete on table public.friendships to authenticated;

drop policy if exists friendships_participant_select on public.friendships;
create policy friendships_participant_select on public.friendships
  for select to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid());

drop policy if exists friendships_self_insert on public.friendships;
create policy friendships_self_insert on public.friendships
  for insert to authenticated
  with check (
    requester_id = auth.uid()
    and addressee_id <> auth.uid()
    and status = 'PENDING'
  );

drop policy if exists friendships_addressee_update on public.friendships;
create policy friendships_addressee_update on public.friendships
  for update to authenticated
  using (addressee_id = auth.uid() or requester_id = auth.uid())
  with check (addressee_id = auth.uid() or requester_id = auth.uid());

drop policy if exists friendships_requester_delete_pending on public.friendships;
create policy friendships_requester_delete_pending on public.friendships
  for delete to authenticated
  using (requester_id = auth.uid() and status = 'PENDING');

create or replace function public.canon_list_my_friendships()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', f.id,
      'requester_id', f.requester_id,
      'addressee_id', f.addressee_id,
      'status', f.status,
      'created_at', f.created_at
    ) order by f.created_at desc)
    from public.friendships f
    where f.requester_id = auth.uid() or f.addressee_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_send_friend_request(p_addressee_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_existing public.friendships%rowtype;
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_addressee_id is null or p_addressee_id = auth.uid() then
    raise exception 'FRIEND_SELF';
  end if;
  if not exists (select 1 from public.persons p where p.user_id = auth.uid()) then
    raise exception 'PERSON_REQUIRED';
  end if;
  if not exists (select 1 from public.persons p where p.user_id = p_addressee_id) then
    raise exception 'PERSON_NOT_FOUND';
  end if;

  select * into v_existing
  from public.friendships f
  where (f.requester_id = auth.uid() and f.addressee_id = p_addressee_id)
     or (f.requester_id = p_addressee_id and f.addressee_id = auth.uid())
  order by f.created_at desc
  limit 1;

  if found then
    if v_existing.status = 'ACCEPTED' then
      raise exception 'ALREADY_FRIENDS';
    end if;
    if v_existing.status = 'PENDING' then
      raise exception 'REQUEST_PENDING';
    end if;
    update public.friendships
      set status = 'PENDING',
          requester_id = auth.uid(),
          addressee_id = p_addressee_id,
          responded_at = null
      where id = v_existing.id;
    return v_existing.id;
  end if;

  insert into public.friendships (requester_id, addressee_id, status)
  values (auth.uid(), p_addressee_id, 'PENDING')
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_respond_friend_request(p_connection_id uuid, p_accept boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  update public.friendships
    set status = case when p_accept then 'ACCEPTED' else 'DECLINED' end,
        responded_at = timezone('utc', now())
    where id = p_connection_id
      and addressee_id = auth.uid()
      and status = 'PENDING';
  if not found then raise exception 'FRIEND_REQUEST_NOT_FOUND'; end if;
end;
$$;

create or replace function public.canon_cancel_friend_request(p_connection_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  delete from public.friendships
  where id = p_connection_id
    and requester_id = auth.uid()
    and status = 'PENDING';
  if not found then raise exception 'FRIEND_REQUEST_NOT_FOUND'; end if;
end;
$$;

grant execute on function public.canon_list_my_friendships() to authenticated;
grant execute on function public.canon_send_friend_request(uuid) to authenticated;
grant execute on function public.canon_respond_friend_request(uuid, boolean) to authenticated;
grant execute on function public.canon_cancel_friend_request(uuid) to authenticated;
