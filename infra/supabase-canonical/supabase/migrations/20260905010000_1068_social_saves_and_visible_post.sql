-- 1068 FIX-F02: canonical social saves + authenticated visible post fetch.
-- Does not edit 1053/1063/1066/1067. Visibility rules match canon_list_social_feed.

create table if not exists public.social_post_saves (
  user_id uuid not null references public.persons(user_id) on delete cascade,
  post_id uuid not null references public.social_posts(id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, post_id)
);

create index if not exists social_post_saves_user_created_idx
  on public.social_post_saves (user_id, created_at desc, post_id desc);

alter table public.social_post_saves enable row level security;

drop policy if exists social_post_saves_own on public.social_post_saves;
create policy social_post_saves_own on public.social_post_saves
for all
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

revoke all on table public.social_post_saves from public, anon;
grant select, insert, delete on table public.social_post_saves to authenticated;

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
    p_visibility = 'PUBLIC'
    or (
      p_visibility in ('FOLLOWERS', 'PRIVATE')
      and (
        p_author = p_viewer
        or exists (
          select 1 from public.friendships f
          where f.status = 'ACCEPTED'
            and (
              (f.requester_id = p_viewer and f.addressee_id = p_author)
              or (f.addressee_id = p_viewer and f.requester_id = p_author)
            )
        )
      )
    );
$$;

revoke all on function public._canon_social_post_visible(uuid, uuid, text) from public, anon, authenticated;

create or replace function public._canon_social_feed_item(s public.social_posts)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', s.id,
    'author_user_id', s.author_user_id,
    'author_name', per.display_name,
    'author_username', per.username,
    'body', s.body,
    'visibility', s.visibility,
    'content_kind', s.content_kind,
    'sponsored', s.sponsored,
    'media_asset_id', s.media_asset_id,
    'media_bucket', ma.bucket,
    'media_path', ma.object_path,
    'media_mime', ma.mime_type,
    'pet_id', s.pet_id,
    'locality_id', s.locality_id,
    'composition', coalesce(s.composition, '{}'::jsonb),
    'extra_media', coalesce((
      select jsonb_agg(
        jsonb_build_object('bucket', extra.bucket, 'path', extra.object_path)
        order by extra.ord
      )
      from (
        select ma2.bucket, ma2.object_path, t.ord
        from jsonb_array_elements_text(
          coalesce(s.composition->'extra_media_asset_ids', '[]'::jsonb)
        ) with ordinality as t(asset_id, ord)
        join public.media_assets ma2 on ma2.id::text = t.asset_id
      ) extra
    ), '[]'::jsonb),
    'like_count', (select count(*) from public.social_reactions r where r.post_id = s.id),
    'comment_count', (select count(*) from public.social_comments c where c.post_id = s.id and c.hidden_at is null),
    'created_at', s.created_at
  )
  from public.persons per
  left join public.media_assets ma on ma.id = s.media_asset_id
  where per.user_id = s.author_user_id;
$$;

revoke all on function public._canon_social_feed_item(public.social_posts) from public, anon, authenticated;

create or replace function public.canon_toggle_save_social_post(p_post_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  s public.social_posts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_post_id is null then raise exception 'VALIDATION'; end if;
  select * into s from public.social_posts where id = p_post_id;
  if not found or s.hidden_at is not null then
    raise exception 'NOT_FOUND';
  end if;
  if exists (
    select 1 from public.social_post_saves sv
    where sv.user_id = auth.uid() and sv.post_id = p_post_id
  ) then
    delete from public.social_post_saves
     where user_id = auth.uid() and post_id = p_post_id;
    return false;
  end if;
  if not public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.social_post_saves (user_id, post_id) values (auth.uid(), p_post_id);
  return true;
end;
$$;

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
    select jsonb_agg(sv.post_id order by sv.created_at desc, sv.post_id desc)
    from public.social_post_saves sv
    where sv.user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_saved_social_posts(
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
    select jsonb_agg(q.obj order by q.saved_at desc, q.id desc)
    from (
      select
        sv.created_at as saved_at,
        s.id,
        public._canon_social_feed_item(s) as obj
      from public.social_post_saves sv
      join public.social_posts s on s.id = sv.post_id
      where sv.user_id = auth.uid()
        and s.hidden_at is null
        and s.sponsored = false
        and s.content_kind in ('POST', 'REEL')
        and public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility)
        and (
          p_cursor_created_at is null
          or (sv.created_at, s.id) < (p_cursor_created_at, p_cursor_id)
        )
      order by sv.created_at desc, s.id desc
      limit v_limit
    ) q
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_visible_social_post(p_post_id uuid)
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
  if p_post_id is null then raise exception 'VALIDATION'; end if;
  select * into s from public.social_posts where id = p_post_id;
  if not found or s.hidden_at is not null then
    raise exception 'NOT_FOUND';
  end if;
  if not public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility) then
    raise exception 'FORBIDDEN';
  end if;
  return public._canon_social_feed_item(s);
end;
$$;

revoke all on function public.canon_toggle_save_social_post(uuid) from public, anon;
revoke all on function public.canon_list_saved_social_post_ids() from public, anon;
revoke all on function public.canon_list_saved_social_posts(integer, timestamptz, uuid) from public, anon;
revoke all on function public.canon_get_visible_social_post(uuid) from public, anon;
grant execute on function public.canon_toggle_save_social_post(uuid) to authenticated;
grant execute on function public.canon_list_saved_social_post_ids() to authenticated;
grant execute on function public.canon_list_saved_social_posts(integer, timestamptz, uuid) to authenticated;
grant execute on function public.canon_get_visible_social_post(uuid) to authenticated;
