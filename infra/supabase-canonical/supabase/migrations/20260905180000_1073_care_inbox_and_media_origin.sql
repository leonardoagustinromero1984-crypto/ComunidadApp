-- 1073: incoming care inbox stays PERSON|ORGANIZATION.
-- Personal media origin vs custody-chain, without rewriting accept (1071).
-- Does not edit 1071/1072 files. Does not delete assets.

-- share_personal_media = false → PRIVATE_TO_ORIGIN (later custodians do not get the file)
-- share_personal_media = true  → CUSTODY_CHAIN (file travels with VitaCora)

create or replace function public._canon_media_origin_readable(
  p_user_id uuid,
  p_asset public.media_assets
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    p_user_id is not null
    and (
      p_asset.owner_person_id = p_user_id
      or (
        p_asset.owner_kind = 'ORGANIZATION'
        and p_asset.owner_organization_id is not null
        and public._acl_org_permission(
          p_user_id,
          p_asset.owner_organization_id,
          'org.pets.manage'
        )
      )
    );
$$;

create or replace function public._canon_media_is_personal_social(p_asset public.media_assets)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    not public._canon_media_is_health_or_professional(p_asset)
    and (
      p_asset.object_path ~* '/(gallery|posts|stories|reels)/'
      or exists (
        select 1
        from public.media_asset_links l
        where l.asset_id = p_asset.id
          and l.purpose in (
            'PET_GALLERY', 'POST_MEDIA', 'STORY_MEDIA', 'REEL_MEDIA',
            'SOCIAL', 'GALLERY', 'MEMORY'
          )
      )
      or exists (
        select 1
        from public.vitacora_moments m
        where m.asset_id = p_asset.id
          and m.kind in ('PHOTO', 'MEMORY', 'TRIP', 'MILESTONE', 'SOCIAL')
      )
    );
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

create or replace function public._canon_moment_linked_asset_id(p_moment public.vitacora_moments)
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_raw text;
  v_id uuid;
begin
  if p_moment.asset_id is not null then
    return p_moment.asset_id;
  end if;
  if p_moment.body is null or btrim(p_moment.body) !~ '^\s*\{' then
    return null;
  end if;
  v_raw := nullif(btrim(p_moment.body::json ->> 'mediaAssetId'), '');
  if v_raw is null then
    return null;
  end if;
  begin
    v_id := v_raw::uuid;
  exception
    when others then
      return null;
  end;
  return v_id;
end;
$$;

create or replace function public.canon_list_vitacora_moments(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.view')
    or public._acl_is_admin(auth.uid())
  ) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(row_data order by (row_data->>'created_at') desc)
    from (
      select jsonb_build_object(
        'id', m.id,
        'kind', m.kind,
        'title', m.title,
        'body', case
          when a.id is not null
           and public._canon_personal_media_withheld_from(auth.uid(), a)
          then null
          else m.body
        end,
        'created_by', m.created_by,
        'created_at', m.created_at
      ) as row_data
      from public.vitacora_moments m
      left join public.media_assets a
        on a.id = public._canon_moment_linked_asset_id(m)
      where m.pet_id = p_pet_id
        and m.hidden_at is null

      union all

      select jsonb_build_object(
        'id', p.id,
        'kind', 'CARE_CREATED',
        'title', 'Se creó la VitaCora de ' || p.name || '.',
        'body', null,
        'created_by', p.created_by_user_id,
        'created_at', p.created_at
      )
      from public.pets p
      where p.id = p_pet_id

      union all

      select jsonb_build_object(
        'id', t.id,
        'kind', 'CARE_TRANSFER',
        'title',
          p.name || ' pasó a estar bajo el cuidado de ' ||
          coalesce(
            public._canon_actor_display_name(
              t.target_kind, t.target_person_id, t.target_organization_id
            ),
            'un nuevo cuidador'
          ) || '.',
        'body', null,
        'created_by', t.decided_by_user_id,
        'created_at', t.decided_at
      )
      from public.pet_care_transfers t
      join public.pets p on p.id = t.pet_id
      where t.pet_id = p_pet_id
        and t.status = 'ACCEPTED'
        and t.decided_at is not null
    ) listed
  ), '[]'::jsonb);
end;
$$;

revoke all on function public._canon_media_origin_readable(uuid, public.media_assets) from public, anon;
revoke all on function public._canon_moment_linked_asset_id(public.vitacora_moments) from public, anon;
revoke all on function public.canon_list_vitacora_moments(uuid) from public, anon;
grant execute on function public.canon_list_vitacora_moments(uuid) to authenticated;

notify pgrst, 'reload schema';
