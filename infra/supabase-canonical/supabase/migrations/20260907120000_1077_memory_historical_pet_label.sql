-- 1077: historical memory pet label + content date + safer pet linkage.
-- Does not edit 1071–1076. Does not grant pet access. Does not invent ownership.

alter table public.media_assets
  add column if not exists memory_pet_id uuid null;

alter table public.media_assets
  add column if not exists memory_pet_label text null;

create index if not exists media_assets_memory_pet_idx
  on public.media_assets (owner_person_id, memory_pet_id)
  where memory_pet_id is not null;

-- Prefer persisted historical label / social_post_pets / unambiguous care stage.
create or replace function public._canon_media_linked_pet_id(p_asset public.media_assets)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    p_asset.memory_pet_id,
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
      select sp.pet_id
      from public.social_posts s
      join public.social_post_pets sp on sp.post_id = s.id
      where s.media_asset_id = p_asset.id
      order by s.created_at desc, sp.pet_id
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
    ),
    (
      -- Unambiguous care-stage inference only (exactly one pet in window).
      select st.pet_id
      from public.pet_care_stages st
      where st.actor_person_id = p_asset.owner_person_id
        and p_asset.created_at >= st.opened_at
        and (st.closed_at is null or p_asset.created_at <= st.closed_at)
      group by st.pet_id
      having (
        select count(distinct st2.pet_id)
        from public.pet_care_stages st2
        where st2.actor_person_id = p_asset.owner_person_id
          and p_asset.created_at >= st2.opened_at
          and (st2.closed_at is null or p_asset.created_at <= st2.closed_at)
      ) = 1
      limit 1
    )
  );
$$;

create or replace function public._canon_media_memory_pet_label(p_asset public.media_assets)
returns text
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    nullif(trim(p_asset.memory_pet_label), ''),
    (
      select nullif(trim(p.name), '')
      from public.pets p
      where p.id = public._canon_media_linked_pet_id(p_asset)
      limit 1
    )
  );
$$;

create or replace function public._canon_media_memory_occurred_at(p_asset public.media_assets)
returns timestamptz
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (
      select (m.occurred_on::timestamptz)
      from public.vitacora_moments m
      where m.asset_id = p_asset.id
        and m.occurred_on is not null
      order by m.created_at desc
      limit 1
    ),
    (
      select m.created_at
      from public.vitacora_moments m
      where m.asset_id = p_asset.id
      order by m.created_at desc
      limit 1
    ),
    (
      select s.created_at
      from public.social_posts s
      where s.media_asset_id = p_asset.id
      order by s.created_at desc
      limit 1
    ),
    p_asset.created_at
  );
$$;

-- Snapshot historical pet label when social posts attach pets.
create or replace function public._canon_attach_social_post_pets(
  p_post_id uuid,
  p_pet_id uuid,
  p_composition jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_ids uuid[] := '{}';
  v_id uuid;
  v_media uuid;
  v_label text;
begin
  if p_pet_id is not null then
    v_ids := array_append(v_ids, p_pet_id);
  end if;
  for v_id in
    select distinct x::uuid
    from jsonb_array_elements_text(coalesce(p_composition->'pet_ids', '[]'::jsonb)) as t(x)
    where x ~* '^[0-9a-f-]{36}$'
  loop
    if not (v_id = any (v_ids)) then
      v_ids := array_append(v_ids, v_id);
    end if;
  end loop;

  if coalesce(array_length(v_ids, 1), 0) > 10 then
    raise exception 'VALIDATION';
  end if;

  foreach v_id in array v_ids loop
    if not public._canon_can_tag_pet(v_id) then
      raise exception 'FORBIDDEN';
    end if;
    insert into public.social_post_pets (post_id, pet_id)
    values (p_post_id, v_id)
    on conflict do nothing;
  end loop;

  if p_pet_id is null and coalesce(array_length(v_ids, 1), 0) > 0 then
    update public.social_posts
       set pet_id = v_ids[1]
     where id = p_post_id
       and pet_id is null;
  end if;

  select s.media_asset_id into v_media
  from public.social_posts s
  where s.id = p_post_id;

  if v_media is not null and coalesce(array_length(v_ids, 1), 0) > 0 then
    select nullif(trim(p.name), '') into v_label
    from public.pets p
    where p.id = v_ids[1];

    update public.media_assets a
       set memory_pet_id = coalesce(a.memory_pet_id, v_ids[1]),
           memory_pet_label = coalesce(nullif(trim(a.memory_pet_label), ''), v_label)
     where a.id = v_media
       and (a.memory_pet_id is null or nullif(trim(a.memory_pet_label), '') is null);
  end if;
end;
$$;

-- Safe backfill from existing social / moment links.
with candidates as (
  select
    a.id as asset_id,
    public._canon_media_linked_pet_id(a) as pet_id,
    public._canon_media_memory_pet_label(a) as label
  from public.media_assets a
  where a.owner_kind = 'PERSON'
    and a.lifecycle_status in ('READY', 'UPLOADING')
    and public._canon_media_is_personal_social(a)
    and not public._canon_media_is_health_or_professional(a)
    and (
      a.memory_pet_id is null
      or nullif(trim(a.memory_pet_label), '') is null
    )
)
update public.media_assets a
set memory_pet_id = coalesce(a.memory_pet_id, c.pet_id),
    memory_pet_label = coalesce(nullif(trim(a.memory_pet_label), ''), c.label)
from candidates c
where a.id = c.asset_id
  and c.pet_id is not null
  and c.label is not null;

-- Recreate list RPCs with occurred_at + historical labels.
drop function if exists public.canon_list_my_personal_memories(uuid, integer, timestamptz, uuid);
drop function if exists public.canon_list_my_personal_memories(integer);

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
      public._canon_media_memory_occurred_at(a) as occurred_at,
      public._canon_media_linked_pet_id(a) as linked_pet_id,
      public._canon_media_memory_pet_label(a) as linked_pet_label
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
      max(e.linked_pet_label) as g_label,
      count(*)::integer as g_memory_count,
      count(*) filter (
        where coalesce(e.mime_type, '') not ilike 'video/%'
      )::integer as g_photo_count,
      count(*) filter (
        where coalesce(e.mime_type, '') ilike 'video/%'
      )::integer as g_video_count,
      max(e.occurred_at) as g_last_memory_at
    from eligible e
    group by e.linked_pet_id
  ),
  covers as (
    select distinct on (e.linked_pet_id)
      e.linked_pet_id as c_pet_id,
      e.asset_id as c_cover_asset_id,
      e.mime_type as c_cover_mime_type
    from eligible e
    order by e.linked_pet_id, e.occurred_at desc, e.asset_id desc
  )
  select
    g.g_pet_id,
    coalesce(
      nullif(trim(g.g_label), ''),
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
  occurred_at timestamptz,
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
    public._canon_media_memory_occurred_at(a) as occurred_at,
    linked.pet_id,
    coalesce(
      nullif(trim(public._canon_media_memory_pet_label(a)), ''),
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
      or (public._canon_media_memory_occurred_at(a), a.id) < (p_cursor_created_at, p_cursor_id)
    )
  order by public._canon_media_memory_occurred_at(a) desc, a.id desc
  limit v_limit;
end;
$$;

-- Keep flat overload signature; include occurred_at.
create or replace function public.canon_list_my_personal_memories(
  p_limit integer default 50
)
returns table (
  asset_id uuid,
  mime_type text,
  created_at timestamptz,
  occurred_at timestamptz,
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
    public._canon_media_memory_occurred_at(a),
    linked.pet_id,
    coalesce(
      nullif(trim(public._canon_media_memory_pet_label(a)), ''),
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
        select nullif(trim(sp.body), '')
        from public.social_posts sp
        where sp.media_asset_id = a.id
        order by sp.created_at desc
        limit 1
      )
    ),
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
    ),
    (
      linked.pet_id is not null
      and (
        public._acl_pet_holder(v_uid, linked.pet_id)
        or public._acl_pet_permission(v_uid, linked.pet_id, 'vitacora.view')
      )
    )
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
  order by public._canon_media_memory_occurred_at(a) desc, a.id desc
  limit v_limit;
end;
$$;

revoke all on function public._canon_media_memory_pet_label(public.media_assets) from public, anon;
revoke all on function public._canon_media_memory_occurred_at(public.media_assets) from public, anon;
revoke all on function public._canon_media_linked_pet_id(public.media_assets) from public, anon;
revoke all on function public._canon_attach_social_post_pets(uuid, uuid, jsonb) from public, anon, authenticated;
revoke all on function public.canon_list_my_memory_pets() from public, anon;
revoke all on function public.canon_list_my_personal_memories(integer) from public, anon;
revoke all on function public.canon_list_my_personal_memories(uuid, integer, timestamptz, uuid) from public, anon;
grant execute on function public.canon_list_my_memory_pets() to authenticated;
grant execute on function public.canon_list_my_personal_memories(integer) to authenticated;
grant execute on function public.canon_list_my_personal_memories(uuid, integer, timestamptz, uuid) to authenticated;

notify pgrst, 'reload schema';
