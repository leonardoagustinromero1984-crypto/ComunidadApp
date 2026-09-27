-- 1069 FIX master track A/B/C: reel file-size signal, multi-pet posts, remove ACCEPTED friendship.
-- Does not edit 1063/1068. Does not raise rate-limit policies.

-- ---------------------------------------------------------------------------
-- A: per-file size is FILE_TOO_LARGE, not daily QUOTA_EXCEEDED.
-- ---------------------------------------------------------------------------
create or replace function public.canon_register_media(
  p_bucket text, p_path text, p_mime text, p_size bigint
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_bucket text := btrim(coalesce(p_bucket, ''));
  v_path text := btrim(coalesce(p_path, ''));
  v_mime text := lower(btrim(coalesce(p_mime, '')));
  v_size bigint := coalesce(p_size, 0);
  v_max bigint;
  v_video boolean := false;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if v_bucket not in ('public-media', 'private-media', 'documents', 'vitacora-import') then
    raise exception 'VALIDATION';
  end if;
  if v_path = '' or v_path like '%..%' or v_path like '/%' then
    raise exception 'VALIDATION';
  end if;
  v_max := case when v_bucket = 'vitacora-import' then 5242880 else 52428800 end;
  if v_size < 1 then
    raise exception 'VALIDATION';
  end if;
  if v_size > v_max then
    raise exception 'FILE_TOO_LARGE';
  end if;
  v_video := v_mime like 'video/%';
  if v_video then
    perform public._canon_require_feature('media.video.upload.enabled');
    if v_mime not in ('video/mp4', 'video/quicktime', 'video/webm', 'video/3gpp') then
      raise exception 'VALIDATION';
    end if;
  elsif v_mime like 'image/%' then
    if v_mime not in ('image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif', 'image/gif') then
      raise exception 'VALIDATION';
    end if;
  elsif v_bucket in ('documents', 'vitacora-import') then
    if v_mime not in (
      'application/pdf',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.ms-excel',
      'text/csv',
      'application/octet-stream'
    ) then
      raise exception 'VALIDATION';
    end if;
  else
    raise exception 'VALIDATION';
  end if;

  perform public._canon_consume_rate_limit('media.register', 1);
  perform public._canon_consume_rate_limit('media.upload.bytes.daily', v_size);
  if v_video then
    perform public._canon_consume_rate_limit('media.video.count.daily', 1);
  end if;

  insert into public.media_assets (
    bucket, object_path, mime_type, byte_size, owner_kind, owner_person_id, created_by, lifecycle_status
  ) values (
    v_bucket, v_path, v_mime, v_size, 'PERSON', auth.uid(), auth.uid(), 'READY'
  ) returning id into v_id;
  return v_id;
end;
$$;

revoke all on function public.canon_register_media(text, text, text, bigint) from public, anon;
grant execute on function public.canon_register_media(text, text, text, bigint) to authenticated;

-- ---------------------------------------------------------------------------
-- B: additive 1:N pets on social posts. Legacy social_posts.pet_id stays.
-- ---------------------------------------------------------------------------
create table if not exists public.social_post_pets (
  post_id uuid not null references public.social_posts(id) on delete cascade,
  pet_id uuid not null references public.pets(id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (post_id, pet_id)
);

create index if not exists social_post_pets_pet_idx on public.social_post_pets (pet_id);

alter table public.social_post_pets enable row level security;

drop policy if exists social_post_pets_select_visible on public.social_post_pets;
create policy social_post_pets_select_visible on public.social_post_pets
for select
to authenticated
using (
  exists (
    select 1 from public.social_posts s
    where s.id = post_id
      and s.hidden_at is null
      and public._canon_social_post_visible(auth.uid(), s.author_user_id, s.visibility)
  )
);

revoke all on table public.social_post_pets from public, anon;
grant select on table public.social_post_pets to authenticated;

create or replace function public._canon_can_tag_pet(p_pet_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_pet_id is not null
    and public._acl_pet_holder(auth.uid(), p_pet_id);
$$;

revoke all on function public._canon_can_tag_pet(uuid) from public, anon, authenticated;

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
end;
$$;

revoke all on function public._canon_attach_social_post_pets(uuid, uuid, jsonb)
  from public, anon, authenticated;

create or replace function public.canon_create_social_post(
  p_body text,
  p_visibility text default 'PUBLIC',
  p_content_kind text default 'POST',
  p_media_asset_id uuid default null,
  p_pet_id uuid default null,
  p_locality_id text default null,
  p_composition jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_kind text := upper(btrim(coalesce(p_content_kind, 'POST')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_visibility not in ('PUBLIC', 'FOLLOWERS', 'PRIVATE') then
    raise exception 'VISIBILITY_INVALID';
  end if;
  if v_kind not in ('POST', 'REEL') then raise exception 'CONTENT_KIND_INVALID'; end if;
  if public.person_is_under_13((select birth_date from public.persons where user_id = auth.uid())) then
    raise exception 'UNDER_13_DENIED';
  end if;
  if v_kind = 'REEL' and p_media_asset_id is null then
    raise exception 'MEDIA_REQUIRED';
  end if;
  if v_kind = 'REEL' then
    perform public._canon_require_feature('reels.create.enabled');
    perform public._canon_consume_rate_limit('social.reel.create', 1);
  else
    perform public._canon_consume_rate_limit('social.post.create', 1);
  end if;
  insert into public.social_posts (
    author_user_id, body, visibility, content_kind, media_asset_id, pet_id, locality_id, composition
  ) values (
    auth.uid(), p_body, p_visibility, v_kind, p_media_asset_id, p_pet_id, p_locality_id,
    coalesce(p_composition, '{}'::jsonb)
  )
  returning id into v_id;
  perform public._canon_attach_social_post_pets(v_id, p_pet_id, coalesce(p_composition, '{}'::jsonb));
  return v_id;
end;
$$;

revoke all on function public.canon_create_social_post(text, text, text, uuid, uuid, text, jsonb)
  from public, anon;
grant execute on function public.canon_create_social_post(text, text, text, uuid, uuid, text, jsonb)
  to authenticated;

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
    'pet_ids', coalesce((
      select jsonb_agg(sp.pet_id order by sp.created_at, sp.pet_id)
      from public.social_post_pets sp
      where sp.post_id = s.id
    ), case when s.pet_id is null then '[]'::jsonb else jsonb_build_array(s.pet_id) end),
    'pet_names', coalesce((
      select jsonb_agg(pt.name order by sp.created_at, sp.pet_id)
      from public.social_post_pets sp
      join public.pets pt on pt.id = sp.pet_id
      where sp.post_id = s.id
    ), case
      when s.pet_id is null then '[]'::jsonb
      else coalesce(
        (select jsonb_build_array(pt.name) from public.pets pt where pt.id = s.pet_id),
        '[]'::jsonb
      )
    end),
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

-- ---------------------------------------------------------------------------
-- C: terminate ACCEPTED connection. Not cancel-pending. Not block.
-- ---------------------------------------------------------------------------
create or replace function public.canon_remove_friendship(p_connection_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_connection_id is null then raise exception 'VALIDATION'; end if;
  delete from public.friendships
  where id = p_connection_id
    and status = 'ACCEPTED'
    and (requester_id = auth.uid() or addressee_id = auth.uid());
  if not found then raise exception 'FRIENDSHIP_NOT_FOUND'; end if;
end;
$$;

revoke all on function public.canon_remove_friendship(uuid) from public, anon;
grant execute on function public.canon_remove_friendship(uuid) to authenticated;

insert into public.security_rate_limit_rpc_bindings (proname, operation_key) values
  ('canon_remove_friendship', 'social.connection_cancel')
on conflict (proname) do nothing;
