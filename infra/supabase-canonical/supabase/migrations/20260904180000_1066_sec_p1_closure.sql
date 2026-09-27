-- 1066 SEC-P1-CLOSURE
-- Signed-URL private SELECT restriction, feed/messages cursor pagination,
-- and Storage MIME allowlists matching canon_register_media.
-- Does not edit 1053 visibility or composition/extra_media shape.
-- Does not change STAGING commercial rate-limit numbers.

-- ---------------------------------------------------------------------------
-- Storage: private objects are no longer SELECT-able by the user JWT.
-- Public-media keeps SELECT (public URLs stay the read path).
-- vitacora-import keeps its own owner SELECT policy (1037).
-- Client createSignedUrl on private buckets therefore fails closed.
-- Minting is Edge + service_role after canon_authorize_media_signed_url.
-- ---------------------------------------------------------------------------
drop policy if exists canon_storage_select on storage.objects;

create policy canon_storage_select_public_media on storage.objects
for select
to anon, authenticated
using (
  bucket_id = 'public-media'
  and exists (
    select 1 from public.media_assets m
    where m.bucket = storage.objects.bucket_id
      and m.object_path = storage.objects.name
      and public._acl_media_readable(auth.uid(), m)
  )
);

-- ---------------------------------------------------------------------------
-- Bucket MIME allowlists = same types canon_register_media already accepts.
-- moderation-evidence is not a register target; left null on purpose.
-- ---------------------------------------------------------------------------
update storage.buckets
   set allowed_mime_types = array[
     'image/jpeg',
     'image/png',
     'image/webp',
     'image/heic',
     'image/heif',
     'image/gif',
     'video/mp4',
     'video/quicktime',
     'video/webm',
     'video/3gpp'
   ]
 where id in ('public-media', 'private-media');

update storage.buckets
   set allowed_mime_types = array[
     'application/pdf',
     'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
     'application/vnd.ms-excel',
     'text/csv',
     'application/octet-stream'
   ]
 where id in ('documents', 'vitacora-import');

-- ---------------------------------------------------------------------------
-- Feed: bounded page + stable (created_at, id) cursor. Visibility unchanged.
-- 0-arg wrapper = first page so old callers cannot dump the whole feed.
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
        jsonb_build_object(
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
        ) as obj
      from public.social_posts s
      join public.persons per on per.user_id = s.author_user_id
      left join public.media_assets ma on ma.id = s.media_asset_id
      where s.hidden_at is null
        and s.sponsored = false
        and s.content_kind in ('POST', 'REEL')
        and (
          s.visibility = 'PUBLIC'
          or (
            s.visibility in ('FOLLOWERS', 'PRIVATE')
            and (
              s.author_user_id = auth.uid()
              or exists (
                select 1 from public.friendships f
                where f.status = 'ACCEPTED'
                  and (
                    (f.requester_id = auth.uid() and f.addressee_id = s.author_user_id)
                    or (f.addressee_id = auth.uid() and f.requester_id = s.author_user_id)
                  )
              )
            )
          )
        )
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

revoke all on function public.canon_list_social_feed() from public, anon;
revoke all on function public.canon_list_social_feed(integer, timestamptz, uuid) from public, anon;
grant execute on function public.canon_list_social_feed() to authenticated;
grant execute on function public.canon_list_social_feed(integer, timestamptz, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Messages: recent page by default; older pages via (created_at, id) cursor.
-- Payload stays ASC within the page (chat display order).
-- ---------------------------------------------------------------------------
create or replace function public.canon_list_messages(
  p_conversation_id uuid,
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
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 50);
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (
    select 1 from public.conversation_participants p
    where p.conversation_id = p_conversation_id
      and p.left_at is null
      and p.person_id = auth.uid()
  ) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(q.obj order by q.created_at asc, q.id asc)
    from (
      select
        m.created_at,
        m.id,
        jsonb_build_object(
          'id', m.id,
          'actor_user_id', m.actor_user_id,
          'body', m.body,
          'payload', m.payload,
          'created_at', m.created_at
        ) as obj
      from public.messages m
      where m.conversation_id = p_conversation_id
        and m.hidden_at is null
        and (
          p_cursor_created_at is null
          or (m.created_at, m.id) < (p_cursor_created_at, p_cursor_id)
        )
      order by m.created_at desc, m.id desc
      limit v_limit
    ) q
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_messages(p_conversation_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return public.canon_list_messages(p_conversation_id, 50, null::timestamptz, null::uuid);
end;
$$;

revoke all on function public.canon_list_messages(uuid) from public, anon;
revoke all on function public.canon_list_messages(uuid, integer, timestamptz, uuid) from public, anon;
grant execute on function public.canon_list_messages(uuid) to authenticated;
grant execute on function public.canon_list_messages(uuid, integer, timestamptz, uuid) to authenticated;
