-- LeoVer STAGING QA batch — public social post resolver for HTTPS /p/{id}
-- Apply on STAGING only before physical QA.

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
  left join public.media_assets ma on ma.id = s.media_asset_id
  where s.id = p_post_id
    and s.hidden_at is null
    and s.visibility = 'PUBLIC';

  if not found then
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

revoke all on function public.canon_get_public_social_post(uuid) from public;
grant execute on function public.canon_get_public_social_post(uuid) to anon, authenticated;
