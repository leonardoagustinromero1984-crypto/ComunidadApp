-- =============================================================================
-- LeoVer M19 — migración 061: visibilidad, media, referencias, feed paginado,
-- reacción LOVE, comentarios editables/archivables y moderación de posts.
-- Forward-only sobre 001–060. No modifica 060 retroactivamente.
-- LOCAL ONLY — no aplicar hasta entorno no productivo autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Columnas y restricciones extendidas
-- ---------------------------------------------------------------------------
alter table public.m19_social_posts
  add column if not exists visibility text not null default 'PUBLIC',
  add column if not exists content_references jsonb not null default '[]'::jsonb,
  add column if not exists media_attachments jsonb not null default '[]'::jsonb;

alter table public.m19_social_posts
  drop constraint if exists m19_post_status_chk;

alter table public.m19_social_posts
  add constraint m19_post_status_chk check (post_status = any (array[
    'DRAFT','PUBLISHED','HIDDEN','ARCHIVED','REMOVED','REMOVED_BY_MODERATION'
  ]::text[]));

alter table public.m19_social_posts
  drop constraint if exists m19_post_visibility_chk;

alter table public.m19_social_posts
  add constraint m19_post_visibility_chk check (
    visibility = any (array['PUBLIC','ORGANIZATION']::text[])
  );

alter table public.m19_social_posts
  drop constraint if exists m19_post_content_refs_arr;

alter table public.m19_social_posts
  add constraint m19_post_content_refs_arr check (jsonb_typeof(content_references) = 'array');

alter table public.m19_social_posts
  drop constraint if exists m19_post_media_arr;

alter table public.m19_social_posts
  add constraint m19_post_media_arr check (jsonb_typeof(media_attachments) = 'array');

alter table public.m19_post_reactions
  drop constraint if exists m19_reaction_type_chk;

alter table public.m19_post_reactions
  add constraint m19_reaction_type_chk check (reaction_type = any (array[
    'LIKE','LOVE','SUPPORT','CELEBRATE'
  ]::text[]));

alter table public.m19_post_comments
  add column if not exists archived boolean not null default false;

alter table public.m19_post_comments
  add column if not exists updated_at timestamptz;

update public.m19_post_comments
set updated_at = created_at
where updated_at is null;

alter table public.m19_post_comments
  alter column updated_at set default timezone('utc', now()),
  alter column updated_at set not null;

create index if not exists m19_posts_feed_cursor_idx
  on public.m19_social_posts (
    coalesce(published_at, created_at) desc,
    id desc
  )
  where post_status = 'PUBLISHED'
    and visibility = 'PUBLIC';

create index if not exists m19_comments_post_visible_idx
  on public.m19_post_comments (post_id, created_at)
  where not hidden and not archived;

-- ---------------------------------------------------------------------------
-- 2. Helpers — visibilidad pública, media/referencias sanitizadas, filtros
-- ---------------------------------------------------------------------------
create or replace function public._m19_post_is_public(p_row public.m19_social_posts)
returns boolean language sql stable as $$
  select p_row.post_status = 'PUBLISHED'
    and coalesce(p_row.visibility, 'PUBLIC') = 'PUBLIC'
    and coalesce(p_row.moderation_status, 'APPROVED') in ('APPROVED')
$$;

create or replace function public._m19_route_hint(p_type text, p_target_id text)
returns text language sql immutable as $$
  select case upper(trim(coalesce(p_type, '')))
    when 'PET' then 'm08/pets/' || coalesce(p_target_id, '')
    when 'ORGANIZATION' then 'm03/orgs/' || coalesce(p_target_id, '')
    when 'SHELTER' then 'm16/shelters/' || coalesce(p_target_id, '')
    when 'CAMPAIGN' then 'm17/campaigns/' || coalesce(p_target_id, '')
    when 'EVENT' then 'm18/events/' || coalesce(p_target_id, '')
    else 'm19/unknown'
  end;
$$;

create or replace function public._m19_public_media_json(p_media jsonb)
returns jsonb language sql stable as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'ref', elem->>'ref',
    'is_public', true,
    'mime_hint', elem->>'mime_hint'
  )), '[]'::jsonb)
  from jsonb_array_elements(coalesce(p_media, '[]'::jsonb)) elem
  where coalesce((elem->>'is_public')::boolean, true)
    and coalesce(trim(elem->>'ref'), '') <> '';
$$;

create or replace function public._m19_public_references_json(p_refs jsonb)
returns jsonb language sql stable as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'type', upper(elem->>'type'),
    'display_label', elem->>'display_label',
    'route_hint', public._m19_route_hint(elem->>'type', elem->>'target_id')
  )), '[]'::jsonb)
  from jsonb_array_elements(coalesce(p_refs, '[]'::jsonb)) elem
  where coalesce((elem->>'is_public')::boolean, true)
    and coalesce(trim(elem->>'display_label'), '') <> '';
$$;

create or replace function public._m19_post_matches_kind(
  p_row public.m19_social_posts,
  p_kind text
) returns boolean language plpgsql stable as $$
declare v_kind text := upper(trim(coalesce(p_kind, 'ALL')));
begin
  if v_kind in ('', 'ALL') then return true; end if;

  if v_kind = 'ORGANIZATIONS' then
    return exists (
      select 1 from jsonb_array_elements(coalesce(p_row.content_references, '[]'::jsonb)) e
      where upper(e->>'type') = 'ORGANIZATION'
    ) or coalesce(trim(p_row.author_display_name), '') <> '';
  end if;

  if v_kind = 'PETS' then
    return exists (
      select 1 from jsonb_array_elements(coalesce(p_row.content_references, '[]'::jsonb)) e
      where upper(e->>'type') = 'PET'
    );
  end if;

  if v_kind = 'SHELTERS' then
    return exists (
      select 1 from jsonb_array_elements(coalesce(p_row.content_references, '[]'::jsonb)) e
      where upper(e->>'type') = 'SHELTER'
    );
  end if;

  if v_kind = 'CAMPAIGNS' then
    return exists (
      select 1 from jsonb_array_elements(coalesce(p_row.content_references, '[]'::jsonb)) e
      where upper(e->>'type') = 'CAMPAIGN'
    );
  end if;

  if v_kind = 'EVENTS' then
    return exists (
      select 1 from jsonb_array_elements(coalesce(p_row.content_references, '[]'::jsonb)) e
      where upper(e->>'type') = 'EVENT'
    );
  end if;

  if v_kind = 'MEDIA' then
    return p_row.cover_image_ref is not null
      or jsonb_array_length(coalesce(p_row.media_attachments, '[]'::jsonb)) > 0;
  end if;

  if v_kind = 'TEXT' then
    return p_row.cover_image_ref is null
      and jsonb_array_length(coalesce(p_row.media_attachments, '[]'::jsonb)) = 0;
  end if;

  return true;
end;
$$;

create or replace function public._m19_engagement_summary(p_post_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'like_count', count(*) filter (where r.reaction_type = 'LIKE'),
    'love_count', count(*) filter (where r.reaction_type = 'LOVE'),
    'support_count', count(*) filter (where r.reaction_type = 'SUPPORT'),
    'celebrate_count', count(*) filter (where r.reaction_type = 'CELEBRATE'),
    'comment_count', (
      select count(*)::integer from public.m19_post_comments c
      where c.post_id = p_post_id and not c.hidden and not c.archived
    )
  )
  from public.m19_post_reactions r
  where r.post_id = p_post_id;
$$;

create or replace function public._m19_public_post_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  v_org_name text;
  v_eng jsonb;
  v_public_cover text;
begin
  select * into v from public.m19_social_posts where id = p_id;
  if not found or not public._m19_post_is_public(v) then return null; end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = v.organization_id;

  v_eng := public._m19_engagement_summary(p_id);

  v_public_cover := case
    when v.cover_image_ref is null then null
    when exists (
      select 1 from jsonb_array_elements(coalesce(v.media_attachments, '[]'::jsonb)) m
      where m->>'ref' = v.cover_image_ref
        and not coalesce((m->>'is_public')::boolean, true)
    ) then null
    else v.cover_image_ref
  end;

  return jsonb_build_object(
    'id', v.id,
    'title', v.title,
    'content', v.content,
    'organization_display_name', v_org_name,
    'author_display_name', v.author_display_name,
    'status', v.post_status,
    'visibility', v.visibility,
    'cover_image_ref', v_public_cover,
    'media_attachments', public._m19_public_media_json(v.media_attachments),
    'content_references', public._m19_public_references_json(v.content_references),
    'like_count', coalesce((v_eng->>'like_count')::integer, 0),
    'love_count', coalesce((v_eng->>'love_count')::integer, 0),
    'support_count', coalesce((v_eng->>'support_count')::integer, 0),
    'celebrate_count', coalesce((v_eng->>'celebrate_count')::integer, 0),
    'comment_count', coalesce((v_eng->>'comment_count')::integer, 0),
    'published_at', v.published_at,
    'created_at', v.created_at
  );
end;
$$;

create or replace function public._m19_internal_post_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v public.m19_social_posts;
declare v_org_name text;
begin
  select * into v from public.m19_social_posts where id = p_id;
  if not found then return null; end if;

  select coalesce(o.display_name, o.legal_name, 'Organización')
  into v_org_name from public.organizations o where o.id = v.organization_id;

  return jsonb_build_object(
    'id', v.id,
    'organization_id', v.organization_id,
    'organization_display_name', v_org_name,
    'author_user_id', v.author_user_id,
    'author_display_name', v.author_display_name,
    'title', v.title,
    'content', v.content,
    'status', v.post_status,
    'post_status', v.post_status,
    'visibility', v.visibility,
    'cover_image_ref', v.cover_image_ref,
    'media_attachments', coalesce(v.media_attachments, '[]'::jsonb),
    'content_references', coalesce(v.content_references, '[]'::jsonb),
    'moderation_status', v.moderation_status,
    'published_at', v.published_at,
    'created_by', v.created_by,
    'created_at', v.created_at,
    'updated_at', v.updated_at
  );
end;
$$;

create or replace function public._m19_public_comment_json(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare c public.m19_post_comments;
begin
  select * into c from public.m19_post_comments where id = p_id;
  if not found or c.hidden or c.archived then return null; end if;
  return jsonb_build_object(
    'id', c.id,
    'post_id', c.post_id,
    'author_display_name', c.author_display_name,
    'content', c.content,
    'created_at', c.created_at,
    'updated_at', c.updated_at
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. RPCs públicos — feed paginado y lectura ampliada
-- ---------------------------------------------------------------------------
create or replace function public.m19_list_public_feed_page(
  p_query text default null,
  p_organization_id uuid default null,
  p_cursor text default null,
  p_page_size integer default 10,
  p_kind text default 'ALL'
) returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_q text := nullif(lower(trim(coalesce(p_query, ''))), '');
  v_limit integer := greatest(1, least(coalesce(p_page_size, 10), 50));
  v_cursor_at timestamptz;
  v_cursor_id uuid;
  v_parts text[];
  v_cursor_ms bigint;
  v_items jsonb := '[]'::jsonb;
  v_row public.m19_social_posts;
  v_count integer := 0;
  v_last public.m19_social_posts;
  v_has_more boolean := false;
  v_next_cursor text;
  v_last_ms bigint;
  v_json jsonb;
begin
  if p_cursor is not null and trim(p_cursor) <> '' then
    v_parts := string_to_array(p_cursor, '|');
    if array_length(v_parts, 1) = 2 then
      begin
        v_cursor_ms := v_parts[1]::bigint;
        v_cursor_id := v_parts[2]::uuid;
        v_cursor_at := to_timestamp(v_cursor_ms / 1000.0) at time zone 'utc';
      exception when others then
        v_cursor_at := null;
        v_cursor_id := null;
      end;
    end if;
  end if;

  for v_row in
    select p.* from public.m19_social_posts p
    where public._m19_post_is_public(p)
      and (p_organization_id is null or p.organization_id = p_organization_id)
      and public._m19_post_matches_kind(p, p_kind)
      and (
        v_q is null
        or p.title ilike '%' || v_q || '%'
        or p.content ilike '%' || v_q || '%'
      )
      and (
        v_cursor_at is null
        or coalesce(p.published_at, p.created_at) < v_cursor_at
        or (
          coalesce(p.published_at, p.created_at) = v_cursor_at
          and p.id < v_cursor_id
        )
      )
    order by coalesce(p.published_at, p.created_at) desc, p.id desc
    limit v_limit + 1
  loop
    v_count := v_count + 1;
    if v_count > v_limit then
      v_has_more := true;
      exit;
    end if;
    v_last := v_row;
    v_json := public._m19_public_post_json(v_row.id);
    if v_json is not null then
      v_items := v_items || jsonb_build_array(v_json);
    end if;
  end loop;

  if v_has_more and v_last.id is not null then
    v_last_ms := (extract(epoch from coalesce(v_last.published_at, v_last.created_at)) * 1000)::bigint;
    v_next_cursor := v_last_ms::text || '|' || v_last.id::text;
  end if;

  return jsonb_build_object(
    'items', v_items,
    'next_cursor', v_next_cursor,
    'has_more', v_has_more
  );
end;
$$;

create or replace function public.m19_get_public_post(p_post_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  v_json jsonb;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if v.post_status in ('REMOVED', 'REMOVED_BY_MODERATION') then raise exception 'M19_POST_REMOVED'; end if;
  if v.post_status <> 'PUBLISHED' then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  v_json := public._m19_public_post_json(p_post_id);
  if v_json is null then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  return v_json;
end;
$$;

create or replace function public.m19_list_public_comments(p_post_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  c public.m19_post_comments;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if not public._m19_post_is_public(v) then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  for c in
    select * from public.m19_post_comments
    where post_id = p_post_id and not hidden and not archived
    order by created_at asc
  loop
    return next public._m19_public_comment_json(c.id);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RPCs — transiciones, archivo, comentarios y moderación
-- ---------------------------------------------------------------------------
create or replace function public.m19_transition_post(
  p_post_id uuid,
  p_target_status text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m19_social_posts;
declare v_target text := upper(trim(p_target_status));
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  perform public._m19_require_org_perm(v.organization_id, 'social.manage');

  if v.post_status = v_target then
    return public._m19_internal_post_json(p_post_id);
  end if;
  if v.post_status = 'REMOVED_BY_MODERATION' then raise exception 'M19_STATE_ALREADY_FINAL'; end if;
  if v.post_status = 'REMOVED' and v_target <> 'REMOVED' then raise exception 'M19_STATE_ALREADY_FINAL'; end if;
  if v_target = 'REMOVED_BY_MODERATION' then raise exception 'M19_PERMISSION_DENIED'; end if;

  if v_target = 'PUBLISHED' and v.post_status not in ('DRAFT', 'HIDDEN', 'ARCHIVED') then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  elsif v_target = 'HIDDEN' and v.post_status <> 'PUBLISHED' then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  elsif v_target = 'ARCHIVED' and v.post_status not in ('DRAFT', 'PUBLISHED', 'HIDDEN') then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  elsif v_target not in ('DRAFT','PUBLISHED','HIDDEN','ARCHIVED','REMOVED') then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  end if;

  update public.m19_social_posts set
    post_status = v_target,
    published_at = case
      when v_target = 'PUBLISHED' and published_at is null then timezone('utc', now())
      else published_at
    end,
    updated_at = timezone('utc', now())
  where id = p_post_id;

  return public._m19_internal_post_json(p_post_id);
end;
$$;

create or replace function public.m19_archive_post(p_post_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m19_social_posts;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  perform public._m19_require_org_perm(v.organization_id, 'social.manage');
  if v.post_status in ('REMOVED', 'REMOVED_BY_MODERATION') then
    raise exception 'M19_STATE_ALREADY_FINAL';
  end if;
  if v.post_status = 'ARCHIVED' then
    return public._m19_internal_post_json(p_post_id);
  end if;
  if v.post_status not in ('DRAFT', 'PUBLISHED', 'HIDDEN') then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  end if;

  update public.m19_social_posts set
    post_status = 'ARCHIVED',
    updated_at = timezone('utc', now())
  where id = p_post_id;

  return public._m19_internal_post_json(p_post_id);
end;
$$;

create or replace function public.m19_moderate_post(p_post_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  v_actor uuid := public._m19_require_authenticated();
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if not public._m19_is_moderator(v_actor) then
    raise exception 'M19_PERMISSION_DENIED';
  end if;
  if v.post_status = 'REMOVED_BY_MODERATION' then
    return public._m19_internal_post_json(p_post_id);
  end if;

  update public.m19_social_posts set
    post_status = 'REMOVED_BY_MODERATION',
    moderation_status = coalesce(moderation_status, 'BLOCKED'),
    updated_at = timezone('utc', now())
  where id = p_post_id;

  return public._m19_internal_post_json(p_post_id);
end;
$$;

create or replace function public.m19_edit_comment(
  p_comment_id uuid,
  p_content text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  c public.m19_post_comments;
  v_actor uuid := public._m19_require_authenticated();
begin
  select * into c from public.m19_post_comments where id = p_comment_id;
  if not found or c.archived then raise exception 'M19_COMMENT_NOT_FOUND'; end if;
  if c.user_id <> v_actor then raise exception 'M19_PERMISSION_DENIED'; end if;
  if char_length(trim(coalesce(p_content, ''))) < 1
    or char_length(coalesce(p_content, '')) > 1000 then
    raise exception 'M19_INVALID_COMMENT';
  end if;

  update public.m19_post_comments set
    content = trim(p_content),
    updated_at = timezone('utc', now())
  where id = p_comment_id;

  return public._m19_public_comment_json(p_comment_id);
end;
$$;

create or replace function public.m19_archive_comment(p_comment_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  c public.m19_post_comments;
  v_actor uuid := public._m19_require_authenticated();
begin
  select * into c from public.m19_post_comments where id = p_comment_id;
  if not found then raise exception 'M19_COMMENT_NOT_FOUND'; end if;
  if c.user_id <> v_actor then raise exception 'M19_PERMISSION_DENIED'; end if;
  if c.archived then
    return jsonb_build_object('ok', true);
  end if;

  update public.m19_post_comments set
    archived = true,
    updated_at = timezone('utc', now())
  where id = p_comment_id;

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.m19_add_reaction(
  p_post_id uuid,
  p_reaction_type text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.m19_social_posts;
  v_actor uuid := public._m19_require_authenticated();
  v_type text := upper(trim(p_reaction_type));
  v_id uuid;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if not public._m19_post_is_public(v) then raise exception 'M19_POST_NOT_PUBLIC'; end if;
  if v_type not in ('LIKE','LOVE','SUPPORT','CELEBRATE') then
    raise exception 'M19_INVALID_STATE_TRANSITION';
  end if;

  delete from public.m19_post_reactions
  where post_id = p_post_id and user_id = v_actor;

  insert into public.m19_post_reactions (post_id, user_id, reaction_type)
  values (p_post_id, v_actor, v_type)
  returning id into v_id;

  return jsonb_build_object(
    'id', v_id,
    'post_id', p_post_id,
    'user_id', v_actor,
    'reaction_type', v_type,
    'created_at', timezone('utc', now())
  );
end;
$$;

create or replace function public.m19_update_post(
  p_post_id uuid,
  p_title text,
  p_content text,
  p_cover_image_ref text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v public.m19_social_posts;
begin
  select * into v from public.m19_social_posts where id = p_post_id;
  if not found then raise exception 'M19_POST_NOT_FOUND'; end if;
  if v.post_status in ('REMOVED', 'REMOVED_BY_MODERATION') then
    raise exception 'M19_STATE_ALREADY_FINAL';
  end if;
  perform public._m19_require_org_perm(v.organization_id, 'social.manage');
  if char_length(trim(coalesce(p_title, ''))) < 1
    or char_length(trim(p_title)) > 120 then
    raise exception 'M19_INVALID_TITLE';
  end if;
  if char_length(trim(coalesce(p_content, ''))) < 5
    or char_length(coalesce(p_content, '')) > 5000 then
    raise exception 'M19_INVALID_CONTENT';
  end if;

  update public.m19_social_posts set
    title = trim(p_title),
    content = trim(p_content),
    cover_image_ref = coalesce(p_cover_image_ref, cover_image_ref),
    updated_at = timezone('utc', now())
  where id = p_post_id;

  return public._m19_internal_post_json(p_post_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. Grants — revoke PUBLIC, acceso mínimo vía RPC
-- ---------------------------------------------------------------------------
revoke all on function public._m19_route_hint(text, text) from public;
revoke all on function public._m19_public_media_json(jsonb) from public;
revoke all on function public._m19_public_references_json(jsonb) from public;
revoke all on function public._m19_post_matches_kind(public.m19_social_posts, text) from public;

grant execute on function public.m19_list_public_feed_page(text, uuid, text, integer, text) to anon, authenticated;
grant execute on function public.m19_archive_post(uuid) to authenticated;
grant execute on function public.m19_moderate_post(uuid) to authenticated;
grant execute on function public.m19_edit_comment(uuid, text) to authenticated;
grant execute on function public.m19_archive_comment(uuid) to authenticated;

revoke all on function public.m19_list_public_feed_page(text, uuid, text, integer, text) from public;
revoke all on function public.m19_archive_post(uuid) from public;
revoke all on function public.m19_moderate_post(uuid) from public;
revoke all on function public.m19_edit_comment(uuid, text) from public;
revoke all on function public.m19_archive_comment(uuid) from public;

commit;
