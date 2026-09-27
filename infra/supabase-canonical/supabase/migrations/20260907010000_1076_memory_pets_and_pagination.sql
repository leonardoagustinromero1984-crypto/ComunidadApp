-- 1076: Mis recuerdos grouped by pet + cursor pagination for pet detail.
-- Does not edit 1074/1075 files. Does not invent ownership. Does not grant pet access.

-- Lightweight first screen: one row per historical pet (or null pet bucket).
create or replace function public.canon_list_my_memory_pets()
returns table (
  pet_id uuid,
  pet_name text,
  memory_count integer,
  photo_count integer,
  video_count integer,
  cover_asset_id uuid,
  cover_mime_type text,
  last_memory_at timestamptz,
  can_open_pet boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;

  return query
  with eligible as (
    select
      a.id as asset_id,
      a.mime_type,
      a.created_at,
      public._canon_media_linked_pet_id(a) as linked_pet_id
    from public.media_assets a
    where a.owner_kind = 'PERSON'
      and a.owner_person_id = v_uid
      and a.lifecycle_status in ('READY', 'UPLOADING')
      and public._canon_media_origin_readable(v_uid, a)
      and public._canon_media_is_personal_social(a)
      and not public._canon_media_is_health_or_professional(a)
  ),
  grouped as (
    select
      e.linked_pet_id as g_pet_id,
      count(*)::integer as g_memory_count,
      count(*) filter (
        where coalesce(e.mime_type, '') not ilike 'video/%'
      )::integer as g_photo_count,
      count(*) filter (
        where coalesce(e.mime_type, '') ilike 'video/%'
      )::integer as g_video_count,
      max(e.created_at) as g_last_memory_at
    from eligible e
    group by e.linked_pet_id
  ),
  covers as (
    select distinct on (e.linked_pet_id)
      e.linked_pet_id as c_pet_id,
      e.asset_id as c_cover_asset_id,
      e.mime_type as c_cover_mime_type
    from eligible e
    order by e.linked_pet_id, e.created_at desc, e.asset_id desc
  )
  select
    g.g_pet_id,
    coalesce(
      nullif(trim(p.name), ''),
      case when g.g_pet_id is null then 'Otros recuerdos' else 'Mascota' end
    ),
    g.g_memory_count,
    g.g_photo_count,
    g.g_video_count,
    c.c_cover_asset_id,
    c.c_cover_mime_type,
    g.g_last_memory_at,
    (
      g.g_pet_id is not null
      and (
        public._acl_pet_holder(v_uid, g.g_pet_id)
        or public._acl_pet_permission(v_uid, g.g_pet_id, 'vitacora.view')
      )
    ) as can_open_pet
  from grouped g
  left join covers c on c.c_pet_id is not distinct from g.g_pet_id
  left join public.pets p on p.id = g.g_pet_id
  order by g.g_last_memory_at desc nulls last, g.g_pet_id nulls last;
end;
$$;

-- Paginated assets for one pet bucket.
-- p_pet_id null => unassigned memories only (linked pet is null).
-- Cursor: (created_at, id) descending. No signed URLs.
create or replace function public.canon_list_my_personal_memories(
  p_pet_id uuid,
  p_limit integer default 24,
  p_cursor_created_at timestamptz default null,
  p_cursor_id uuid default null
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
  v_limit integer := least(greatest(coalesce(p_limit, 24), 1), 40);
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
    coalesce(
      nullif(trim(p.name), ''),
      case when linked.pet_id is null then 'Otros recuerdos' else 'Mascota' end
    ),
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
    and linked.pet_id is not distinct from p_pet_id
    and (
      p_cursor_created_at is null
      or p_cursor_id is null
      or (a.created_at, a.id) < (p_cursor_created_at, p_cursor_id)
    )
  order by a.created_at desc, a.id desc
  limit v_limit;
end;
$$;

-- Legacy flat overload (bounded). Keeps prior callers working without pet filter.
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
  v_limit integer := least(greatest(coalesce(p_limit, 24), 1), 40);
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
    coalesce(
      nullif(trim(p.name), ''),
      case when linked.pet_id is null then 'Otros recuerdos' else 'Mascota' end
    ),
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
  order by a.created_at desc, a.id desc
  limit v_limit;
end;
$$;

revoke all on function public.canon_list_my_memory_pets() from public, anon;
revoke all on function public.canon_list_my_personal_memories(integer) from public, anon;
revoke all on function public.canon_list_my_personal_memories(uuid, integer, timestamptz, uuid) from public, anon;
grant execute on function public.canon_list_my_memory_pets() to authenticated;
grant execute on function public.canon_list_my_personal_memories(integer) to authenticated;
grant execute on function public.canon_list_my_personal_memories(uuid, integer, timestamptz, uuid) to authenticated;

notify pgrst, 'reload schema';
