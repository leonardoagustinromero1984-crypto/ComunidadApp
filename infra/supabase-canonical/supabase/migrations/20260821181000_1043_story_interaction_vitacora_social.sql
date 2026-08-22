-- LeoVer Canonical — 1043
-- Story viewers, reactions, author-visible replies; VitaCora SOCIAL kind.
-- Apply on STAGING before QA. Do not apply to production.

alter table public.vitacora_moments drop constraint if exists vitacora_moments_kind_check;
alter table public.vitacora_moments
  add constraint vitacora_moments_kind_check
  check (kind in (
    'ARRIVAL', 'BIRTHDAY', 'MEMORY', 'PHOTO', 'TRIP', 'MILESTONE', 'NOTE', 'SOCIAL'
  ));

create table if not exists public.social_story_views (
  story_id uuid not null references public.social_stories(id) on delete cascade,
  viewer_user_id uuid not null references public.persons(user_id),
  seen_at timestamptz not null default timezone('utc', now()),
  primary key (story_id, viewer_user_id)
);

create table if not exists public.social_story_reactions (
  story_id uuid not null references public.social_stories(id) on delete cascade,
  user_id uuid not null references public.persons(user_id),
  kind text not null default 'HEART' check (kind in ('HEART')),
  created_at timestamptz not null default timezone('utc', now()),
  primary key (story_id, user_id)
);

create table if not exists public.social_story_comments (
  id uuid primary key default gen_random_uuid(),
  story_id uuid not null references public.social_stories(id) on delete cascade,
  author_user_id uuid not null references public.persons(user_id),
  body text not null,
  created_at timestamptz not null default timezone('utc', now())
);

alter table public.social_story_views enable row level security;
alter table public.social_story_reactions enable row level security;
alter table public.social_story_comments enable row level security;

revoke all on table public.social_story_views from anon, authenticated;
revoke all on table public.social_story_reactions from anon, authenticated;
revoke all on table public.social_story_comments from anon, authenticated;

create or replace function public.canon_record_story_view(p_story_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (select 1 from public.social_stories s where s.id = p_story_id and s.hidden_at is null) then
    raise exception 'STORY_NOT_FOUND';
  end if;
  insert into public.social_story_views (story_id, viewer_user_id, seen_at)
  values (p_story_id, auth.uid(), timezone('utc', now()))
  on conflict (story_id, viewer_user_id)
  do update set seen_at = excluded.seen_at;
end;
$$;

create or replace function public.canon_list_story_viewers(p_story_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (
    select 1 from public.social_stories s
    where s.id = p_story_id and s.author_user_id = auth.uid()
  ) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'user_id', p.user_id,
      'display_name', p.display_name,
      'username', p.username,
      'avatar_path', p.avatar_asset_id,
      'seen_at', v.seen_at
    ) order by v.seen_at desc)
    from public.social_story_views v
    join public.persons p on p.user_id = v.viewer_user_id
    where v.story_id = p_story_id
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_react_story(p_story_id uuid, p_kind text default 'HEART')
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare v_exists boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if coalesce(p_kind, 'HEART') <> 'HEART' then raise exception 'REACTION_INVALID'; end if;
  if not exists (select 1 from public.social_stories s where s.id = p_story_id and s.hidden_at is null) then
    raise exception 'STORY_NOT_FOUND';
  end if;
  select exists (
    select 1 from public.social_story_reactions r
    where r.story_id = p_story_id and r.user_id = auth.uid()
  ) into v_exists;
  if v_exists then
    delete from public.social_story_reactions
    where story_id = p_story_id and user_id = auth.uid();
    return false;
  end if;
  insert into public.social_story_reactions (story_id, user_id, kind)
  values (p_story_id, auth.uid(), 'HEART');
  return true;
end;
$$;

create or replace function public.canon_comment_story(p_story_id uuid, p_body text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_body is null or btrim(p_body) = '' then raise exception 'COMMENT_REQUIRED'; end if;
  if not exists (select 1 from public.social_stories s where s.id = p_story_id and s.hidden_at is null) then
    raise exception 'STORY_NOT_FOUND';
  end if;
  insert into public.social_story_comments (story_id, author_user_id, body)
  values (p_story_id, auth.uid(), btrim(p_body))
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_list_story_comments(p_story_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (
    select 1 from public.social_stories s
    where s.id = p_story_id
      and s.hidden_at is null
      and (s.author_user_id = auth.uid() or exists (
        select 1 from public.social_story_comments c
        where c.story_id = p_story_id and c.author_user_id = auth.uid()
      ))
  ) then
    -- Author always; commenters can reread their thread via author list.
    if not exists (
      select 1 from public.social_stories s where s.id = p_story_id and s.author_user_id = auth.uid()
    ) then
      return coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', c.id,
          'author_user_id', c.author_user_id,
          'display_name', p.display_name,
          'username', p.username,
          'body', c.body,
          'created_at', c.created_at
        ) order by c.created_at asc)
        from public.social_story_comments c
        join public.persons p on p.user_id = c.author_user_id
        where c.story_id = p_story_id
          and c.author_user_id = auth.uid()
      ), '[]'::jsonb);
    end if;
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id,
      'author_user_id', c.author_user_id,
      'display_name', p.display_name,
      'username', p.username,
      'body', c.body,
      'created_at', c.created_at
    ) order by c.created_at asc)
    from public.social_story_comments c
    join public.persons p on p.user_id = c.author_user_id
    where c.story_id = p_story_id
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_record_story_view(uuid) to authenticated;
grant execute on function public.canon_list_story_viewers(uuid) to authenticated;
grant execute on function public.canon_react_story(uuid, text) to authenticated;
grant execute on function public.canon_comment_story(uuid, text) to authenticated;
grant execute on function public.canon_list_story_comments(uuid) to authenticated;
