-- LeoVer Canonical
-- Logical migration: 1111
-- Own profile can store a phone and choose whether location and phone are public.
-- Saving a phone does not publish it. Do not apply this file to production from Desktop.

alter table public.persons
  add column if not exists show_location boolean not null default true,
  add column if not exists phone_public boolean not null default false;

drop function if exists public.canon_update_my_person(text, text);

create function public.canon_update_my_person(
  p_display_name text default null,
  p_home_locality_id text default null,
  p_e164_phone text default null,
  p_show_location boolean default null,
  p_phone_public boolean default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  update public.persons
    set display_name = coalesce(nullif(btrim(p_display_name), ''), display_name),
        home_locality_id = coalesce(p_home_locality_id, home_locality_id),
        e164_phone = case
          when p_e164_phone is null then e164_phone
          else nullif(btrim(p_e164_phone), '')
        end,
        show_location = coalesce(p_show_location, show_location),
        phone_public = coalesce(p_phone_public, phone_public)
    where user_id = auth.uid();
  if not found then raise exception 'NOT_FOUND'; end if;
  return auth.uid();
end;
$$;

create or replace function public.canon_get_public_person(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row public.persons%rowtype;
  v_self boolean;
  v_show_location boolean;
  v_show_phone boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.persons where user_id = p_user_id;
  if not found then return null; end if;
  v_self := v_row.user_id = auth.uid();
  if not v_self
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
  v_show_location := v_self or coalesce(v_row.show_location, true);
  v_show_phone := v_self or coalesce(v_row.phone_public, false);
  return jsonb_build_object(
    'id', v_row.user_id,
    'username', v_row.username,
    'display_name', v_row.display_name,
    'avatar_path', v_row.avatar_asset_id,
    'avatar_asset_id', v_row.avatar_asset_id,
    'home_locality_id', case when v_show_location then v_row.home_locality_id else null end,
    'privacy_state', v_row.privacy_state,
    'phone', case when v_show_phone then v_row.e164_phone else null end,
    'show_location', case when v_self then v_row.show_location else null end,
    'show_phone', case when v_self then v_row.phone_public else null end
  );
end;
$$;

revoke all on function public.canon_update_my_person(text, text, text, boolean, boolean) from public, anon;
grant execute on function public.canon_update_my_person(text, text, text, boolean, boolean) to authenticated;
revoke all on function public.canon_get_public_person(uuid) from public, anon;
grant execute on function public.canon_get_public_person(uuid) to authenticated;

notify pgrst, 'reload schema';
