-- 1079: content-grouped VitaCora/memories + private-profile feed gate + PERSON→PERSON Manada.
-- Does not edit 1071–1078 files.

-- ---------------------------------------------------------------------------
-- A) Moment media helpers (single + array payload)
-- ---------------------------------------------------------------------------
create or replace function public._canon_moment_media_asset_ids(p_moment public.vitacora_moments)
returns uuid[]
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_ids uuid[] := '{}';
  v_one text;
  v_elem text;
  v_uuid uuid;
begin
  if p_moment.asset_id is not null then
    v_ids := array_append(v_ids, p_moment.asset_id);
  end if;
  if p_moment.body is null or btrim(p_moment.body) !~ '^\s*\{' then
    return v_ids;
  end if;
  begin
    for v_elem in
      select jsonb_array_elements_text(coalesce(p_moment.body::jsonb -> 'mediaAssetIds', '[]'::jsonb))
    loop
      begin
        v_uuid := nullif(btrim(v_elem), '')::uuid;
        if v_uuid is not null and not (v_uuid = any (v_ids)) then
          v_ids := array_append(v_ids, v_uuid);
        end if;
      exception when others then
        null;
      end;
    end loop;
  exception when others then
    null;
  end;
  v_one := nullif(btrim(p_moment.body::json ->> 'mediaAssetId'), '');
  if v_one is null then
    v_one := nullif(btrim(p_moment.body::json ->> 'mediaUrl'), '');
    if v_one is not null and v_one ~* '^https?://' then
      v_one := null;
    end if;
  end if;
  if v_one is not null then
    begin
      v_uuid := v_one::uuid;
      if not (v_uuid = any (v_ids)) then
        v_ids := array_append(v_ids, v_uuid);
      end if;
    exception when others then
      null;
    end;
  end if;
  return v_ids;
end;
$$;

create or replace function public._canon_moment_linked_asset_id(p_moment public.vitacora_moments)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select (public._canon_moment_media_asset_ids(p_moment))[1];
$$;

create or replace function public._canon_moment_source_social_post_id(p_moment public.vitacora_moments)
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
  if p_moment.body is null or btrim(p_moment.body) !~ '^\s*\{' then
    return null;
  end if;
  v_raw := nullif(btrim(p_moment.body::json ->> 'contentId'), '');
  if v_raw is null then
    return null;
  end if;
  begin
    v_id := v_raw::uuid;
  exception when others then
    return null;
  end;
  return v_id;
end;
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
    );
$$;

create or replace function public._canon_media_memory_content_key(p_asset public.media_assets)
returns text
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (
      select public._canon_moment_source_social_post_id(m)::text
      from public.vitacora_moments m
      where p_asset.id = any (public._canon_moment_media_asset_ids(m))
        and public._canon_moment_source_social_post_id(m) is not null
      order by m.created_at desc
      limit 1
    ),
    (
      select m.id::text
      from public.vitacora_moments m
      where p_asset.id = any (public._canon_moment_media_asset_ids(m))
      order by m.created_at desc
      limit 1
    ),
    p_asset.id::text
  );
$$;

-- Idempotent SOCIAL save: at most one SOCIAL moment per (pet, source_social_post_id).
create or replace function public.canon_save_social_vitacora_moment(
  p_pet_id uuid,
  p_title text,
  p_body text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_source uuid;
  v_tmp public.vitacora_moments%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.manage') then
    raise exception 'FORBIDDEN';
  end if;
  v_tmp.body := p_body;
  v_source := public._canon_moment_source_social_post_id(v_tmp);
  if v_source is not null then
    select m.id into v_id
    from public.vitacora_moments m
    where m.pet_id = p_pet_id
      and m.kind = 'SOCIAL'
      and m.hidden_at is null
      and public._canon_moment_source_social_post_id(m) = v_source
    order by m.created_at desc
    limit 1;
    if v_id is not null then
      update public.vitacora_moments
      set title = coalesce(nullif(btrim(p_title), ''), title),
          body = p_body
      where id = v_id;
      return v_id;
    end if;
  end if;
  insert into public.vitacora_moments (pet_id, kind, title, body, created_by)
  values (p_pet_id, 'SOCIAL', p_title, p_body, auth.uid())
  returning id into v_id;
  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- B) Memories: content groups (not per-asset rows)
-- ---------------------------------------------------------------------------
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
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

  return query
  with eligible as (
    select
      a.id as asset_id,
      a.mime_type,
      public._canon_media_memory_occurred_at(a) as occurred_at,
      public._canon_media_linked_pet_id(a) as linked_pet_id,
      public._canon_media_memory_pet_label(a) as linked_pet_label,
      public._canon_media_memory_content_key(a) as content_key
    from public.media_assets a
    where a.owner_kind = 'PERSON'
      and a.owner_person_id = v_uid
      and a.lifecycle_status in ('READY', 'UPLOADING')
      and public._canon_media_origin_readable(v_uid, a)
      and public._canon_media_is_vitacora_personal_memory(a)
      and not public._canon_media_is_health_or_professional(a)
  ),
  contents as (
    select
      e.linked_pet_id,
      e.content_key,
      max(e.linked_pet_label) as linked_pet_label,
      max(e.occurred_at) as occurred_at,
      bool_or(coalesce(e.mime_type, '') ilike 'video/%') as has_video,
      (array_agg(e.asset_id order by e.occurred_at desc, e.asset_id desc))[1] as cover_asset_id,
      (array_agg(e.mime_type order by e.occurred_at desc, e.asset_id desc))[1] as cover_mime_type
    from eligible e
    group by e.linked_pet_id, e.content_key
  ),
  grouped as (
    select
      c.linked_pet_id as g_pet_id,
      max(c.linked_pet_label) as g_label,
      count(*)::integer as g_memory_count,
      count(*) filter (where not c.has_video)::integer as g_photo_count,
      count(*) filter (where c.has_video)::integer as g_video_count,
      max(c.occurred_at) as g_last_memory_at
    from contents c
    group by c.linked_pet_id
  ),
  covers as (
    select distinct on (c.linked_pet_id)
      c.linked_pet_id as c_pet_id,
      c.cover_asset_id as c_cover_asset_id,
      c.cover_mime_type as c_cover_mime_type
    from contents c
    order by c.linked_pet_id, c.occurred_at desc, c.content_key desc
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
    )
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
  memory_id uuid,
  source_social_post_id uuid,
  occurred_at timestamptz,
  created_at timestamptz,
  pet_id uuid,
  pet_name text,
  caption text,
  shared_with_vitacora boolean,
  can_open_pet boolean,
  media_asset_ids uuid[],
  media_mime_types text[],
  cover_asset_id uuid,
  cover_mime_type text
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
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

  return query
  with eligible as (
    select
      a.id as asset_id,
      a.mime_type,
      a.created_at as asset_created_at,
      public._canon_media_memory_occurred_at(a) as occurred_at,
      public._canon_media_linked_pet_id(a) as linked_pet_id,
      public._canon_media_memory_content_key(a) as content_key
    from public.media_assets a
    where a.owner_kind = 'PERSON'
      and a.owner_person_id = v_uid
      and a.lifecycle_status in ('READY', 'UPLOADING')
      and public._canon_media_origin_readable(v_uid, a)
      and public._canon_media_is_vitacora_personal_memory(a)
      and not public._canon_media_is_health_or_professional(a)
      and public._canon_media_linked_pet_id(a) is not distinct from p_pet_id
  ),
  contents as (
    select
      case
        when e.content_key ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          then e.content_key::uuid
        else (array_agg(e.asset_id order by e.occurred_at desc, e.asset_id desc))[1]
      end as memory_id,
      case
        when e.content_key ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          then e.content_key::uuid
        else null
      end as source_social_post_id,
      max(e.occurred_at) as occurred_at,
      max(e.asset_created_at) as created_at,
      e.linked_pet_id as pet_id,
      array_agg(e.asset_id order by e.occurred_at desc, e.asset_id desc) as media_asset_ids,
      array_agg(e.mime_type order by e.occurred_at desc, e.asset_id desc) as media_mime_types
    from eligible e
    group by e.content_key, e.linked_pet_id
  )
  select
    c.memory_id,
    c.source_social_post_id,
    c.occurred_at,
    c.created_at,
    c.pet_id,
    coalesce(
      nullif(trim(p.name), ''),
      case when c.pet_id is null then 'Otros recuerdos' else 'Mascota' end
    ),
    coalesce(
      (
        select nullif(trim(m.title), '')
        from public.vitacora_moments m
        where c.media_asset_ids[1] = any (public._canon_moment_media_asset_ids(m))
        order by m.created_at desc
        limit 1
      ),
      (
        select nullif(trim(sp.body), '')
        from public.social_posts sp
        where sp.id = c.source_social_post_id
        limit 1
      )
    ),
    (
      c.pet_id is not null
      and not exists (
        select 1
        from public.pet_care_stages s
        where s.pet_id = c.pet_id
          and s.closed_at is not null
          and s.share_personal_media is false
          and c.created_at >= s.opened_at
          and c.created_at <= s.closed_at
      )
    ),
    (
      c.pet_id is not null
      and (
        public._acl_pet_holder(v_uid, c.pet_id)
        or public._acl_pet_permission(v_uid, c.pet_id, 'vitacora.view')
      )
    ),
    c.media_asset_ids,
    c.media_mime_types,
    c.media_asset_ids[1],
    c.media_mime_types[1]
  from contents c
  left join public.pets p on p.id = c.pet_id
  where (
    p_cursor_created_at is null
    or p_cursor_id is null
    or (c.occurred_at, c.memory_id) < (p_cursor_created_at, p_cursor_id)
  )
  order by c.occurred_at desc, c.memory_id desc
  limit v_limit;
end;
$$;

-- Flat overload: first page of content groups across pets.
create or replace function public.canon_list_my_personal_memories(
  p_limit integer default 50
)
returns table (
  memory_id uuid,
  source_social_post_id uuid,
  occurred_at timestamptz,
  created_at timestamptz,
  pet_id uuid,
  pet_name text,
  caption text,
  shared_with_vitacora boolean,
  can_open_pet boolean,
  media_asset_ids uuid[],
  media_mime_types text[],
  cover_asset_id uuid,
  cover_mime_type text
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
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

  return query
  with eligible as (
    select
      a.id as asset_id,
      a.mime_type,
      a.created_at as asset_created_at,
      public._canon_media_memory_occurred_at(a) as occurred_at,
      public._canon_media_linked_pet_id(a) as linked_pet_id,
      public._canon_media_memory_content_key(a) as content_key
    from public.media_assets a
    where a.owner_kind = 'PERSON'
      and a.owner_person_id = v_uid
      and a.lifecycle_status in ('READY', 'UPLOADING')
      and public._canon_media_origin_readable(v_uid, a)
      and public._canon_media_is_vitacora_personal_memory(a)
      and not public._canon_media_is_health_or_professional(a)
  ),
  contents as (
    select
      case
        when e.content_key ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          then e.content_key::uuid
        else (array_agg(e.asset_id order by e.occurred_at desc, e.asset_id desc))[1]
      end as memory_id,
      case
        when e.content_key ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          then e.content_key::uuid
        else null
      end as source_social_post_id,
      max(e.occurred_at) as occurred_at,
      max(e.asset_created_at) as created_at,
      e.linked_pet_id as pet_id,
      array_agg(e.asset_id order by e.occurred_at desc, e.asset_id desc) as media_asset_ids,
      array_agg(e.mime_type order by e.occurred_at desc, e.asset_id desc) as media_mime_types
    from eligible e
    group by e.content_key, e.linked_pet_id
  )
  select
    c.memory_id,
    c.source_social_post_id,
    c.occurred_at,
    c.created_at,
    c.pet_id,
    coalesce(
      nullif(trim(p.name), ''),
      case when c.pet_id is null then 'Otros recuerdos' else 'Mascota' end
    ),
    (
      select nullif(trim(m.title), '')
      from public.vitacora_moments m
      where c.media_asset_ids[1] = any (public._canon_moment_media_asset_ids(m))
      order by m.created_at desc
      limit 1
    ),
    (
      c.pet_id is not null
      and not exists (
        select 1
        from public.pet_care_stages s
        where s.pet_id = c.pet_id
          and s.closed_at is not null
          and s.share_personal_media is false
          and c.created_at >= s.opened_at
          and c.created_at <= s.closed_at
      )
    ),
    (
      c.pet_id is not null
      and (
        public._acl_pet_holder(v_uid, c.pet_id)
        or public._acl_pet_permission(v_uid, c.pet_id, 'vitacora.view')
      )
    ),
    c.media_asset_ids,
    c.media_mime_types,
    c.media_asset_ids[1],
    c.media_mime_types[1]
  from contents c
  left join public.pets p on p.id = c.pet_id
  order by c.occurred_at desc, c.memory_id desc
  limit v_limit;
end;
$$;

-- ---------------------------------------------------------------------------
-- C) Social privacy: PRIVATE profile content only for self + ACCEPTED Manada
-- ---------------------------------------------------------------------------
create or replace function public._canon_social_post_visible(
  p_viewer uuid,
  p_author uuid,
  p_visibility text
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    p_author = p_viewer
    or exists (
      select 1 from public.friendships f
      where f.status = 'ACCEPTED'
        and (
          (f.requester_id = p_viewer and f.addressee_id = p_author)
          or (f.addressee_id = p_viewer and f.requester_id = p_author)
        )
    )
    or (
      p_visibility = 'PUBLIC'
      and exists (
        select 1 from public.persons pe
        where pe.user_id = p_author
          and pe.privacy_state = 'PUBLIC_LIMITED'
      )
    );
$$;

-- ---------------------------------------------------------------------------
-- D) PERSON → PERSON transfer requires accepted Manada friendship
-- ---------------------------------------------------------------------------
create or replace function public._canon_accepted_manada(p_a uuid, p_b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_a is not null and p_b is not null and p_a <> p_b and exists (
    select 1 from public.friendships f
    where f.status = 'ACCEPTED'
      and (
        (f.requester_id = p_a and f.addressee_id = p_b)
        or (f.addressee_id = p_a and f.requester_id = p_b)
      )
  );
$$;

create or replace function public.canon_initiate_care_transfer(
  p_pet_id uuid,
  p_target_kind text,
  p_target_person_id uuid default null,
  p_target_organization_id uuid default null,
  p_share_personal_media boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pet public.pets%rowtype;
  v_kind text := upper(btrim(coalesce(p_target_kind, '')));
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_age_allows(auth.uid(), 'pet.transfer_responsibility') then
    raise exception 'AGE_CAPABILITY_DENIED';
  end if;
  select * into v_pet from public.pets where id = p_pet_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_pet.lifecycle_status <> 'ACTIVE' then raise exception 'PET_NOT_ACTIVE'; end if;
  if not public._acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.transfer') then
    raise exception 'FORBIDDEN';
  end if;
  if v_kind not in ('PERSON', 'ORGANIZATION') then
    raise exception 'VALIDATION';
  end if;
  if not public.holder_xor_ok(v_kind, p_target_person_id, p_target_organization_id) then
    raise exception 'PET_TRANSFER_DEST_XOR_REQUIRED';
  end if;
  if v_kind = 'PERSON' and not exists (select 1 from public.persons where user_id = p_target_person_id) then
    raise exception 'PET_TRANSFER_TARGET_INVALID';
  end if;
  if v_kind = 'ORGANIZATION' and not exists (
    select 1 from public.organizations
    where id = p_target_organization_id and lifecycle_status = 'ACTIVE'
  ) then
    raise exception 'PET_TRANSFER_TARGET_INVALID';
  end if;
  -- PERSON → PERSON: accepted Mi manada required (orgs unchanged).
  if v_kind = 'PERSON'
     and v_pet.current_custodian_kind = 'PERSON'
     and not public._canon_accepted_manada(auth.uid(), p_target_person_id) then
    raise exception 'RELATIONSHIP_REQUIRED';
  end if;
  if (
    v_pet.current_custodian_kind = v_kind
    and v_pet.current_custodian_person_id is not distinct from p_target_person_id
    and v_pet.current_custodian_organization_id is not distinct from p_target_organization_id
  ) then
    raise exception 'PET_TRANSFER_SAME_ACTOR';
  end if;
  if exists (
    select 1 from public.pet_care_transfers
    where pet_id = p_pet_id and status = 'PENDING'
  ) then
    raise exception 'PET_TRANSFER_PENDING_EXISTS';
  end if;

  insert into public.pet_care_transfers (
    pet_id, status,
    source_kind, source_person_id, source_organization_id,
    target_kind, target_person_id, target_organization_id,
    initiated_by_user_id, share_personal_media
  ) values (
    p_pet_id, 'PENDING',
    v_pet.current_custodian_kind, v_pet.current_custodian_person_id, v_pet.current_custodian_organization_id,
    v_kind, p_target_person_id, p_target_organization_id,
    auth.uid(), coalesce(p_share_personal_media, false)
  ) returning id into v_id;

  insert into public.pet_responsibility_events (pet_id, actor_user_id, event_type, metadata)
  values (
    p_pet_id,
    auth.uid(),
    'CARE_TRANSFER_INITIATED',
    jsonb_build_object('transfer_id', v_id)
  );

  return public._canon_care_transfer_json(v_id);
end;
$$;

create or replace function public.canon_search_care_transfer_targets(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_q text := btrim(coalesce(p_query, ''));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if char_length(v_q) < 2 then return '[]'::jsonb; end if;
  return coalesce((
    select jsonb_agg(row_data)
    from (
      select jsonb_build_object(
        'kind', 'PERSON',
        'id', pe.user_id,
        'display_name', pe.display_name,
        'subtitle', coalesce('@' || pe.username, '')
      ) as row_data
      from public.persons pe
      where pe.user_id <> auth.uid()
        and public._canon_accepted_manada(auth.uid(), pe.user_id)
        and (
          pe.display_name ilike ('%' || v_q || '%')
          or coalesce(pe.username, '') ilike ('%' || v_q || '%')
        )
      order by pe.display_name
      limit 10
    ) people
  ), '[]'::jsonb) || coalesce((
    select jsonb_agg(row_data)
    from (
      select jsonb_build_object(
        'kind', 'ORGANIZATION',
        'id', o.id,
        'display_name', o.name,
        'subtitle', coalesce(o.primary_label, '')
      ) as row_data
      from public.organizations o
      where o.lifecycle_status = 'ACTIVE'
        and (
          o.name ilike ('%' || v_q || '%')
          or coalesce(o.slug, '') ilike ('%' || v_q || '%')
        )
      order by o.name
      limit 10
    ) orgs
  ), '[]'::jsonb);
end;
$$;

revoke all on function public._canon_moment_media_asset_ids(public.vitacora_moments) from public, anon;
revoke all on function public._canon_moment_linked_asset_id(public.vitacora_moments) from public, anon;
revoke all on function public._canon_moment_source_social_post_id(public.vitacora_moments) from public, anon;
revoke all on function public._canon_media_is_vitacora_personal_memory(public.media_assets) from public, anon;
revoke all on function public._canon_media_memory_content_key(public.media_assets) from public, anon;
revoke all on function public._canon_accepted_manada(uuid, uuid) from public, anon;
revoke all on function public._canon_social_post_visible(uuid, uuid, text) from public, anon, authenticated;
revoke all on function public.canon_save_social_vitacora_moment(uuid, text, text) from public, anon;
revoke all on function public.canon_list_my_memory_pets() from public, anon;
revoke all on function public.canon_list_my_personal_memories(integer) from public, anon;
revoke all on function public.canon_list_my_personal_memories(uuid, integer, timestamptz, uuid) from public, anon;
revoke all on function public.canon_initiate_care_transfer(uuid, text, uuid, uuid, boolean) from public, anon;
revoke all on function public.canon_search_care_transfer_targets(text) from public, anon;

grant execute on function public.canon_save_social_vitacora_moment(uuid, text, text) to authenticated;
grant execute on function public.canon_list_my_memory_pets() to authenticated;
grant execute on function public.canon_list_my_personal_memories(integer) to authenticated;
grant execute on function public.canon_list_my_personal_memories(uuid, integer, timestamptz, uuid) to authenticated;
grant execute on function public.canon_initiate_care_transfer(uuid, text, uuid, uuid, boolean) to authenticated;
grant execute on function public.canon_search_care_transfer_targets(text) to authenticated;

notify pgrst, 'reload schema';
