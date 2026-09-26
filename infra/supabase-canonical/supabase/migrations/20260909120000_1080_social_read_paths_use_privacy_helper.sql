-- 1080: Wire social READ/interact paths to _canon_social_post_visible (1079).
-- Does not edit 1066/1046/1068/1079 files.
-- Root cause: canon_list_social_feed (1066) used visibility='PUBLIC' without author privacy.

-- ---------------------------------------------------------------------------
-- Feed: must call helper (existing posts + new posts)
-- ---------------------------------------------------------------------------
create or replace function public.canon_list_social_feed(
  p_limit integer,
  p_cursor_created_at timestamptz,
  p_cursor_id uuid
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 30), 1), 50);
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(q.obj order by q.created_at desc, q.id desc)
    from (
      select
        s.created_at,
        s.id,
        public._canon_social_feed_item(s) as obj
      from public.social_posts s
      where s.hidden_at is null
        and s.sponsored = false
        and s.content_kind in ('POST', 'REEL')
        and public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility)
        and (
          p_cursor_created_at is null
          or (s.created_at, s.id) < (p_cursor_created_at, p_cursor_id)
        )
      order by s.created_at desc, s.id desc
      limit v_limit
    ) q
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_social_feed()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return public.canon_list_social_feed(30, null::timestamptz, null::uuid);
end;
$$;

-- ---------------------------------------------------------------------------
-- Deep-link /p/{id}: PUBLIC post only when author profile is PUBLIC_LIMITED
-- ---------------------------------------------------------------------------
create or replace function public.canon_get_public_social_post(p_post_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row record;
begin
  select
    s.id,
    s.body,
    s.content_kind,
    s.visibility,
    s.author_user_id,
    s.created_at,
    ma.bucket as media_bucket,
    ma.object_path as media_path,
    ma.mime_type as media_mime
  into v_row
  from public.social_posts s
  join public.persons pe on pe.user_id = s.author_user_id
  left join public.media_assets ma on ma.id = s.media_asset_id
  where s.id = p_post_id
    and s.hidden_at is null
    and s.visibility = 'PUBLIC'
    and pe.privacy_state = 'PUBLIC_LIMITED';

  if not found then
    return null;
  end if;

  -- Authenticated callers still must pass the helper (covers friendship edge cases).
  if auth.uid() is not null
     and not public._canon_social_post_visible(auth.uid(), v_row.author_user_id, v_row.visibility) then
    return null;
  end if;

  return jsonb_build_object(
    'id', v_row.id,
    'body', v_row.body,
    'content_kind', coalesce(v_row.content_kind, 'POST'),
    'author_name', (
      select coalesce(nullif(trim(p.display_name), ''), nullif(trim(p.username), ''), 'LeoVer')
      from public.persons p
      where p.user_id = v_row.author_user_id
    ),
    'created_at', v_row.created_at,
    'media_bucket', v_row.media_bucket,
    'media_path', v_row.media_path,
    'media_mime', v_row.media_mime
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Comments list: gate with helper; keep 1034 payload shape
-- ---------------------------------------------------------------------------
create or replace function public.canon_list_social_comments(p_post_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  s public.social_posts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into s from public.social_posts where id = p_post_id;
  if not found or s.hidden_at is not null then
    raise exception 'NOT_FOUND';
  end if;
  if not public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id,
      'post_id', c.post_id,
      'author_user_id', c.author_user_id,
      'author_name', per.display_name,
      'author_avatar_asset_id', per.avatar_asset_id,
      'body', c.body,
      'parent_id', c.parent_id,
      'created_at', c.created_at
    ) order by c.created_at asc)
    from public.social_comments c
    join public.persons per on per.user_id = c.author_user_id
    where c.post_id = p_post_id and c.hidden_at is null
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- React toggle: preserve (uuid) → boolean signature used by Android
-- ---------------------------------------------------------------------------
create or replace function public.canon_react_social_post(p_post_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  s public.social_posts%rowtype;
  v_exists boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into s from public.social_posts where id = p_post_id;
  if not found or s.hidden_at is not null then
    raise exception 'NOT_FOUND';
  end if;
  if not public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility) then
    raise exception 'FORBIDDEN';
  end if;
  select exists (
    select 1 from public.social_reactions
    where post_id = p_post_id and user_id = auth.uid()
  ) into v_exists;
  if v_exists then
    delete from public.social_reactions where post_id = p_post_id and user_id = auth.uid();
    return false;
  end if;
  insert into public.social_reactions (post_id, user_id) values (p_post_id, auth.uid());
  return true;
end;
$$;

-- ---------------------------------------------------------------------------
-- Comment write: gate with helper
-- ---------------------------------------------------------------------------
create or replace function public.canon_comment_social_post(p_post_id uuid, p_body text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  s public.social_posts%rowtype;
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into s from public.social_posts where id = p_post_id;
  if not found or s.hidden_at is not null then
    raise exception 'NOT_FOUND';
  end if;
  if not public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility) then
    raise exception 'FORBIDDEN';
  end if;
  if nullif(btrim(coalesce(p_body, '')), '') is null then
    raise exception 'VALIDATION';
  end if;
  insert into public.social_comments (post_id, author_user_id, body)
  values (p_post_id, auth.uid(), btrim(p_body))
  returning id into v_id;
  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Saved IDs: drop revoked access (helper already on list_saved_posts in 1068)
-- ---------------------------------------------------------------------------
create or replace function public.canon_list_saved_social_post_ids()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(sv.post_id order by sv.created_at desc)
    from public.social_post_saves sv
    join public.social_posts s on s.id = sv.post_id
    where sv.user_id = auth.uid()
      and s.hidden_at is null
      and public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility)
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Active stories: author privacy (stories treated as PUBLIC content kind)
-- ---------------------------------------------------------------------------
create or replace function public.canon_list_active_stories()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', s.id,
      'author_user_id', s.author_user_id,
      'author_name', per.display_name,
      'author_username', per.username,
      'author_avatar_asset_id', per.avatar_asset_id,
      'asset_id', s.asset_id,
      'media_bucket', ma.bucket,
      'media_path', ma.object_path,
      'media_mime', ma.mime_type,
      'caption', s.caption,
      'pet_id', s.pet_id,
      'locality_id', s.locality_id,
      'composition', s.composition,
      'created_at', s.created_at,
      'expires_at', s.expires_at
    ) order by s.created_at desc)
    from public.social_stories s
    join public.persons per on per.user_id = s.author_user_id
    left join public.media_assets ma on ma.id = s.asset_id
    where s.hidden_at is null
      and s.expires_at > timezone('utc', now())
      and public._canon_social_post_visible(auth.uid(), s.author_user_id, 'PUBLIC')
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_list_social_feed() from public, anon;
revoke all on function public.canon_list_social_feed(integer, timestamptz, uuid) from public, anon;
revoke all on function public.canon_get_public_social_post(uuid) from public;
revoke all on function public.canon_list_social_comments(uuid) from public, anon;
revoke all on function public.canon_react_social_post(uuid) from public, anon;
revoke all on function public.canon_comment_social_post(uuid, text) from public, anon;
revoke all on function public.canon_list_saved_social_post_ids() from public, anon;
revoke all on function public.canon_list_active_stories() from public, anon;

grant execute on function public.canon_list_social_feed() to authenticated;
grant execute on function public.canon_list_social_feed(integer, timestamptz, uuid) to authenticated;
grant execute on function public.canon_get_public_social_post(uuid) to anon, authenticated;
grant execute on function public.canon_list_social_comments(uuid) to authenticated;
grant execute on function public.canon_react_social_post(uuid) to authenticated;
grant execute on function public.canon_comment_social_post(uuid, text) to authenticated;
grant execute on function public.canon_list_saved_social_post_ids() to authenticated;
grant execute on function public.canon_list_active_stories() to authenticated;

notify pgrst, 'reload schema';
