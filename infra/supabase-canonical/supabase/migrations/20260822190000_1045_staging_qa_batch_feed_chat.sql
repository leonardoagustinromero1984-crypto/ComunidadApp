-- 1045: STAGING QA batch — chat bootstrap + social feed visibility for connections

create or replace function public.canon_start_conversation(
  p_kind text, p_other_person uuid, p_org uuid, p_body text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_allow boolean;
begin
  if p_kind = 'PERSON' then
    select c.allow_unknown_dms into v_allow
    from public.person_contact_controls c where c.user_id = p_other_person;
    if v_allow is false and not exists (
      select 1 from public.friendships f
      where f.status = 'ACCEPTED'
        and ((f.requester_id = auth.uid() and f.addressee_id = p_other_person)
          or (f.addressee_id = auth.uid() and f.requester_id = p_other_person))
    ) then
      raise exception 'UNKNOWN_DM_RESTRICTED';
    end if;
  end if;
  insert into public.conversations (subject_kind, created_by)
  values (p_kind, auth.uid()) returning id into v_id;
  insert into public.conversation_participants (conversation_id, participant_kind, person_id)
  values (v_id, 'PERSON', auth.uid());
  if p_kind = 'PERSON' then
    insert into public.conversation_participants (conversation_id, participant_kind, person_id)
    values (v_id, 'PERSON', p_other_person);
  else
    insert into public.conversation_participants (conversation_id, participant_kind, organization_id)
    values (v_id, 'ORGANIZATION', p_org);
  end if;
  if p_body is not null and btrim(p_body) <> '' then
    insert into public.messages (conversation_id, actor_user_id, body)
    values (v_id, auth.uid(), p_body);
  end if;
  return v_id;
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
          s.visibility = 'FOLLOWERS'
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

grant execute on function public.canon_start_conversation(text, uuid, uuid, text) to authenticated;
grant execute on function public.canon_list_social_feed() to authenticated;
