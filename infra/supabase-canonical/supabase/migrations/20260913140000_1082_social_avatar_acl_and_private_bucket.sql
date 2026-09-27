-- 1082: avatar-only social ACL + move PRIVATE person/pet avatars off public-media.
-- Does not edit 1081 or earlier. Does not widen general private media SELECT.
-- Does not change signed-url TTL, MFA, Health, or responsibility/grants.

create or replace function public._acl_accepted_connection(p_left uuid, p_right uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    p_left is not null
    and p_right is not null
    and p_left <> p_right
    and exists (
      select 1
      from public.friendships f
      where f.status = 'ACCEPTED'
        and (
          (f.requester_id = p_left and f.addressee_id = p_right)
          or (f.addressee_id = p_left and f.requester_id = p_right)
        )
    );
$$;

create or replace function public._acl_social_avatar_readable(
  p_viewer uuid,
  p_asset public.media_assets
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_viewer is null or p_asset.id is null then
    return false;
  end if;

  if exists (
    select 1
    from public.persons per
    where per.avatar_asset_id = p_asset.id
      and (
        per.user_id = p_viewer
        or public._acl_accepted_connection(p_viewer, per.user_id)
        or public._acl_is_admin(p_viewer)
      )
  ) then
    return true;
  end if;

  if exists (
    select 1
    from public.pets pet
    join public.pet_responsibility_links l
      on l.pet_id = pet.id
    where pet.avatar_asset_id = p_asset.id
      and pet.lifecycle_status = 'ACTIVE'
      and l.holder_kind = 'PERSON'
      and l.status = 'ACTIVE'
      and l.role in ('OWNER', 'PRINCIPAL')
      and (
        l.holder_person_id = p_viewer
        or public._acl_accepted_connection(p_viewer, l.holder_person_id)
        or public._acl_is_admin(p_viewer)
      )
  ) then
    return true;
  end if;

  return false;
end;
$$;

create or replace function public._acl_media_readable(p_user_id uuid, p_asset public.media_assets)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_pet uuid;
begin
  if p_asset.visibility = 'PUBLIC' then
    return true;
  end if;
  if p_user_id is null then
    return false;
  end if;
  if public._canon_media_origin_readable(p_user_id, p_asset) then
    return true;
  end if;
  if public._acl_is_admin(p_user_id) then
    return true;
  end if;
  if public._acl_social_avatar_readable(p_user_id, p_asset) then
    return true;
  end if;
  if exists (
    select 1 from public.pets p
    where p.avatar_asset_id = p_asset.id
      and public._acl_pet_holder(p_user_id, p.id)
  ) then
    return true;
  end if;

  v_pet := public._canon_media_linked_pet_id(p_asset);
  if v_pet is null then
    return false;
  end if;
  if public._canon_personal_media_withheld_from(p_user_id, p_asset) then
    return false;
  end if;
  if public._acl_pet_holder(p_user_id, v_pet)
     or public._acl_pet_permission(p_user_id, v_pet, 'vitacora.view')
     or public._acl_pet_permission(p_user_id, v_pet, 'health.manage_declared') then
    return true;
  end if;
  return false;
end;
$$;

create or replace function public._canon_relocate_private_avatar_object(p_asset_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public, storage
as $$
declare
  v_asset public.media_assets%rowtype;
  v_src storage.objects%rowtype;
  v_dst storage.objects%rowtype;
  v_src_size bigint;
  v_dst_size bigint;
begin
  select * into v_asset from public.media_assets where id = p_asset_id;
  if not found then
    raise exception 'AVATAR_MIGRATE_NOT_FOUND';
  end if;
  if v_asset.visibility <> 'PRIVATE' or v_asset.bucket <> 'public-media' then
    return false;
  end if;
  if not exists (
    select 1 from public.persons per where per.avatar_asset_id = v_asset.id
    union all
    select 1 from public.pets pet where pet.avatar_asset_id = v_asset.id
  ) then
    return false;
  end if;

  select * into v_src
  from storage.objects
  where bucket_id = 'public-media'
    and name = v_asset.object_path;
  if not found then
    raise exception 'AVATAR_MIGRATE_SOURCE_MISSING';
  end if;

  v_src_size := coalesce((v_src.metadata ->> 'size')::bigint, v_asset.byte_size);

  select * into v_dst
  from storage.objects
  where bucket_id = 'private-media'
    and name = v_asset.object_path;
  if not found then
    raise exception 'AVATAR_MIGRATE_COPY_MISSING';
  end if;

  v_dst_size := coalesce((v_dst.metadata ->> 'size')::bigint, 0);
  if v_src_size is null or v_dst_size is null or v_src_size <> v_dst_size or v_dst_size <= 0 then
    raise exception 'AVATAR_MIGRATE_SIZE_MISMATCH';
  end if;

  update public.media_assets
     set bucket = 'private-media'
   where id = v_asset.id
     and bucket = 'public-media'
     and object_path = v_asset.object_path;

  delete from storage.objects
  where bucket_id = 'public-media'
    and name = v_asset.object_path
    and exists (
      select 1 from public.media_assets m
      where m.id = v_asset.id
        and m.bucket = 'private-media'
        and m.object_path = v_asset.object_path
    );
  return true;
end;
$$;

-- Relocation of bytes is NOT executed here. Inserting storage.objects does not
-- copy S3 payloads. A later ops step copies bytes, verifies size, then calls
-- _canon_relocate_private_avatar_object. Never delete the public object first.

revoke all on function public._acl_accepted_connection(uuid, uuid) from public, anon;
revoke all on function public._acl_social_avatar_readable(uuid, public.media_assets) from public, anon;
revoke all on function public._canon_relocate_private_avatar_object(uuid) from public, anon, authenticated;

notify pgrst, 'reload schema';
