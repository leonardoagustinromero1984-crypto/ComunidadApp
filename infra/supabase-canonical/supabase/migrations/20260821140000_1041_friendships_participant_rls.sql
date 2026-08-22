-- LeoVer Canonical — 1041
-- Friendships table already exists (1002). Migration 1020 enabled RLS on every
-- public table and revoked ALL from authenticated, without participant policies
-- on friendships. Android staging therefore used the in-memory mock.
--
-- Apply on STAGING (tobqbddfcyitwgbkthhy) before QA. Do not apply to production
-- automatically.

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

-- Friends must be able to resolve display_name/username of people they already
-- connected with, even if privacy_state is PRIVATE.
create or replace function public.canon_get_public_person(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare v_row public.persons%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.persons where user_id = p_user_id;
  if not found then return null; end if;
  if v_row.user_id <> auth.uid()
     and v_row.privacy_state <> 'PUBLIC_LIMITED'
     and not public._acl_is_admin(auth.uid())
     and not exists (
       select 1 from public.friendships f
       where (f.requester_id = auth.uid() and f.addressee_id = p_user_id)
          or (f.addressee_id = auth.uid() and f.requester_id = p_user_id)
     )
  then
    return null;
  end if;
  return jsonb_build_object(
    'id', v_row.user_id,
    'username', v_row.username,
    'display_name', v_row.display_name,
    'avatar_path', v_row.avatar_asset_id,
    'home_locality_id', v_row.home_locality_id,
    'privacy_state', v_row.privacy_state
  );
end;
$$;

