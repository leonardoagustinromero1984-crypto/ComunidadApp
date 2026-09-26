-- 1083: Mis recuerdos gated on completed care transfer + narrow social-post media ACL.
-- Does not edit 1082 or earlier. Does not widen general private-media SELECT.
-- Does not change MFA, AAL2, rate limits, Health, or responsibility/grants.

-- ---------------------------------------------------------------------------
-- A) Transfer gate for Mis recuerdos
-- Completed PERSON care transfer (ACCEPTED) is the only eligibility event.
-- Closed pet_care_stages with transfer_id corroborate the same event.
-- ---------------------------------------------------------------------------
create or replace function public._canon_viewer_completed_care_transfer(
  p_viewer uuid,
  p_pet_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    p_viewer is not null
    and p_pet_id is not null
    and (
      exists (
        select 1
        from public.pet_care_transfers t
        where t.pet_id = p_pet_id
          and t.status = 'ACCEPTED'
          and t.source_kind = 'PERSON'
          and t.source_person_id = p_viewer
      )
      or exists (
        select 1
        from public.pet_care_stages s
        join public.pet_care_transfers t on t.id = s.transfer_id
        where s.pet_id = p_pet_id
          and s.closed_at is not null
          and s.actor_kind = 'PERSON'
          and s.actor_person_id = p_viewer
          and t.status = 'ACCEPTED'
      )
    );
$$;

create or replace function public._canon_media_is_vitacora_personal_memory(
  p_asset public.media_assets
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    (
      exists (
        select 1
        from public.vitacora_moments m
        where p_asset.id = any (public._canon_moment_media_asset_ids(m))
      )
      or exists (
        select 1
        from public.media_asset_links l
        where l.asset_id = p_asset.id
          and (
            l.owner_table = 'vitacora_moments'
            or l.purpose in ('MEMORY', 'PET_GALLERY', 'VITACORA_MEDIA')
          )
      )
    )
    and public._canon_viewer_completed_care_transfer(
      auth.uid(),
      public._canon_media_linked_pet_id(p_asset)
    );
$$;

-- ---------------------------------------------------------------------------
-- B) Social post media: viewer who can see the post can resolve THAT asset only.
-- ---------------------------------------------------------------------------
create or replace function public._acl_social_post_media_readable(
  p_viewer uuid,
  p_asset public.media_assets
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    p_viewer is not null
    and p_asset.id is not null
    and exists (
      select 1
      from public.social_posts s
      where s.hidden_at is null
        and (
          s.media_asset_id = p_asset.id
          or exists (
            select 1
            from jsonb_array_elements_text(
              coalesce(s.composition->'extra_media_asset_ids', '[]'::jsonb)
            ) extra(asset_id)
            where extra.asset_id = p_asset.id::text
          )
        )
        and public._canon_social_post_visible(
          p_viewer,
          s.author_user_id,
          s.visibility
        )
    );
$$;

-- ---------------------------------------------------------------------------
-- C) Pet avatar: current pet of the profile PERSON + ACCEPTED (or self/admin).
-- Does not grant responsibility.
-- ---------------------------------------------------------------------------
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
    where pet.avatar_asset_id = p_asset.id
      and pet.lifecycle_status = 'ACTIVE'
      and exists (
        select 1
        from (
          select l.holder_person_id as person_id
          from public.pet_responsibility_links l
          where l.pet_id = pet.id
            and l.holder_kind = 'PERSON'
            and l.status = 'ACTIVE'
          union
          select pet.current_custodian_person_id
          where pet.current_custodian_kind = 'PERSON'
            and pet.current_custodian_person_id is not null
        ) holders
        where holders.person_id is not null
          and (
            holders.person_id = p_viewer
            or public._acl_accepted_connection(p_viewer, holders.person_id)
            or public._acl_is_admin(p_viewer)
          )
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
  if public._acl_social_post_media_readable(p_user_id, p_asset) then
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

-- Project avatar_asset_id explicitly so clients do not depend on avatar_path alias.
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
    'avatar_asset_id', v_row.avatar_asset_id,
    'home_locality_id', v_row.home_locality_id,
    'privacy_state', v_row.privacy_state
  );
end;
$$;

revoke all on function public._canon_viewer_completed_care_transfer(uuid, uuid) from public, anon;
revoke all on function public._acl_social_post_media_readable(uuid, public.media_assets) from public, anon;
revoke all on function public._acl_social_avatar_readable(uuid, public.media_assets) from public, anon;
revoke all on function public._canon_media_is_vitacora_personal_memory(public.media_assets) from public, anon;
revoke all on function public._acl_media_readable(uuid, public.media_assets) from public, anon;
revoke all on function public.canon_get_public_person(uuid) from public, anon;

notify pgrst, 'reload schema';
