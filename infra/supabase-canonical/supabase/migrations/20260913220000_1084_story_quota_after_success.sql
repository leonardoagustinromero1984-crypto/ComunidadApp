-- 1084: Story quota is 20 published stories / person / day.
-- Consume only after a successful insert (failed/cancelled/retry of a failed create
-- does not burn the story counter). Does not edit 1063/1069.
-- Does not change Reel, Post, signed_url, chat, or import limits.

update public.security_rate_limit_policies
   set window_seconds = 86400,
       limit_count = 20,
       notes = '20 published stories / person / day'
 where operation_key = 'social.story.create';

create or replace function public.canon_create_story(
  p_asset_id uuid,
  p_caption text default null,
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
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_asset_id is null then
    raise exception 'MEDIA_REQUIRED';
  end if;
  insert into public.social_stories (
    author_user_id, asset_id, expires_at, pet_id, locality_id, composition, caption
  ) values (
    auth.uid(), p_asset_id, timezone('utc', now()) + interval '24 hours',
    p_pet_id, p_locality_id, coalesce(p_composition, '{}'::jsonb), p_caption
  )
  returning id into v_id;
  perform public._canon_consume_rate_limit('social.story.create', 1);
  return v_id;
end;
$$;

revoke all on function public.canon_create_story(uuid, text, uuid, text, jsonb)
  from public, anon;
grant execute on function public.canon_create_story(uuid, text, uuid, text, jsonb)
  to authenticated;

notify pgrst, 'reload schema';
