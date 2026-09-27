-- 1053: expose social_posts.composition + extra image assets on the feed list.
-- Does not edit 1050 visibility rules. Extra photos live in composition.extra_media_asset_ids
-- (social_posts.media_asset_id remains the primary image; there is no post_media table).

create or replace function public.canon_list_social_feed()
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
    ) order by s.created_at desc)
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
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_list_social_feed() to authenticated;
