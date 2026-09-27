-- 1075: Mis recuerdos eligibility for social/reel paths + social_posts linkage.
-- Does not edit 1071/1072/1073/1074 files.
-- Does not invent ownership. Owner backfill only when created_by is unambiguous PERSON.

-- Path regex was '/(gallery|posts|stories|reels)/' which misses object_path
-- values like 'reels/<id>/...' (no leading slash segment).
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
      p_asset.object_path ~* '(^|/)(gallery|posts|stories|reels)(/|$)'
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
      or exists (
        select 1
        from public.social_posts s
        where s.media_asset_id = p_asset.id
          and s.content_kind in ('POST', 'REEL')
      )
    );
$$;

-- Link pet via social_posts.media_asset_id when links/moments are missing.
create or replace function public._canon_media_linked_pet_id(p_asset public.media_assets)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (
      select p.id
      from public.pets p
      where p.avatar_asset_id = p_asset.id
      limit 1
    ),
    (
      select m.pet_id
      from public.vitacora_moments m
      where m.asset_id = p_asset.id
      limit 1
    ),
    (
      select s.pet_id
      from public.social_posts s
      where s.media_asset_id = p_asset.id
        and s.pet_id is not null
      order by s.created_at desc
      limit 1
    ),
    (
      select case
        when l.owner_table = 'pets' then l.owner_id
        when l.owner_table = 'vitacora_moments' then (
          select vm.pet_id from public.vitacora_moments vm where vm.id = l.owner_id
        )
        else null
      end
      from public.media_asset_links l
      where l.asset_id = p_asset.id
      order by l.created_at
      limit 1
    )
  );
$$;

-- Safe ownership backfill: only when owner_person_id is null and created_by
-- matches a PERSON with no conflicting organization owner evidence.
update public.media_assets a
set owner_kind = 'PERSON',
    owner_person_id = a.created_by,
    owner_organization_id = null
where a.owner_person_id is null
  and a.owner_organization_id is null
  and a.created_by is not null
  and exists (select 1 from public.persons pe where pe.user_id = a.created_by)
  and public._canon_media_is_personal_social(a)
  and not public._canon_media_is_health_or_professional(a);

create or replace function public.canon_list_my_personal_memories(
  p_limit integer default 50
)
returns table (
  asset_id uuid,
  mime_type text,
  created_at timestamptz,
  pet_id uuid,
  pet_name text,
  caption text,
  shared_with_vitacora boolean,
  can_open_pet boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 100);
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;

  return query
  select
    a.id,
    a.mime_type,
    a.created_at,
    linked.pet_id,
    p.name,
    coalesce(
      (
        select nullif(trim(m.title), '')
        from public.vitacora_moments m
        where m.asset_id = a.id
        order by m.created_at desc
        limit 1
      ),
      (
        select nullif(trim(m.body), '')
        from public.vitacora_moments m
        where m.asset_id = a.id
        order by m.created_at desc
        limit 1
      ),
      (
        select nullif(trim(sp.body), '')
        from public.social_posts sp
        where sp.media_asset_id = a.id
        order by sp.created_at desc
        limit 1
      ),
      (
        select nullif(trim(sp.body), '')
        from public.media_asset_links l
        join public.social_posts sp on sp.id = l.owner_id
        where l.asset_id = a.id
          and l.owner_table = 'social_posts'
        limit 1
      )
    ) as caption,
    (
      linked.pet_id is not null
      and not exists (
        select 1
        from public.pet_care_stages s
        where s.pet_id = linked.pet_id
          and s.closed_at is not null
          and s.share_personal_media is false
          and a.created_at >= s.opened_at
          and a.created_at <= s.closed_at
      )
    ) as shared_with_vitacora,
    (
      linked.pet_id is not null
      and (
        public._acl_pet_holder(v_uid, linked.pet_id)
        or public._acl_pet_permission(v_uid, linked.pet_id, 'vitacora.view')
      )
    ) as can_open_pet
  from public.media_assets a
  left join lateral (
    select public._canon_media_linked_pet_id(a) as pet_id
  ) linked on true
  left join public.pets p on p.id = linked.pet_id
  where a.owner_kind = 'PERSON'
    and a.owner_person_id = v_uid
    and a.lifecycle_status in ('READY', 'UPLOADING')
    and public._canon_media_origin_readable(v_uid, a)
    and public._canon_media_is_personal_social(a)
    and not public._canon_media_is_health_or_professional(a)
  order by a.created_at desc
  limit v_limit;
end;
$$;

revoke all on function public._canon_media_is_personal_social(public.media_assets) from public, anon;
revoke all on function public._canon_media_linked_pet_id(public.media_assets) from public, anon;
revoke all on function public.canon_list_my_personal_memories(integer) from public, anon;
grant execute on function public.canon_list_my_personal_memories(integer) to authenticated;

notify pgrst, 'reload schema';
