-- 1050: FOLLOWERS-equivalent PRIVATE posts + professional search browse
-- Do not edit 1044–1049. Empty query must still discover veterinary taxonomy.

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

create or replace function public.canon_search_vitacora_access_targets(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_q text := btrim(coalesce(p_query, ''));
  v_ql text := lower(btrim(coalesce(p_query, '')));
  v_vet_term boolean;
  v_browse boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  v_browse := char_length(v_q) < 2;
  v_vet_term := v_browse or v_ql ~ '(vet|veterinar|clinic|cl[ií]nic)';

  return coalesce((
    select jsonb_agg(row_data order by (row_data->>'display_name'))
    from (
      select jsonb_build_object(
        'target_kind', 'ORGANIZATION',
        'target_id', sp.holder_organization_id::text,
        'display_name', sp.display_name,
        'subtitle', coalesce(org.primary_label, 'Entidad veterinaria'),
        'avatar_asset_id', org.logo_asset_id,
        'verified', org.verification_status = 'VERIFIED'
      ) as row_data
      from public.service_providers sp
      join public.organizations org on org.id = sp.holder_organization_id
      where sp.lifecycle_status = 'ACTIVE'
        and sp.holder_kind = 'ORGANIZATION'
        and (
          exists (
            select 1 from public.organization_capabilities oc
            where oc.organization_id = org.id
              and oc.capability in ('VETERINARY_CLINIC', 'PROVIDER', 'NGO', 'SHELTER')
          )
          or org.primary_label ilike 'VETERINARY%'
          or exists (
            select 1 from public.service_offerings o
            where o.provider_id = sp.id
              and o.active
              and o.category_code in ('VETERINARY', 'BOARDING', 'GROOMING')
          )
        )
        and (
          v_vet_term
          or sp.display_name ilike ('%' || v_q || '%')
          or coalesce(org.primary_label, '') ilike ('%' || v_q || '%')
        )
      union all
      select jsonb_build_object(
        'target_kind', 'PERSON',
        'target_id', sp.holder_person_id::text,
        'display_name', sp.display_name,
        'subtitle', 'Profesional independiente',
        'avatar_asset_id', per.avatar_asset_id,
        'verified', false
      ) as row_data
      from public.service_providers sp
      join public.persons per on per.user_id = sp.holder_person_id
      where sp.lifecycle_status = 'ACTIVE'
        and sp.holder_kind = 'PERSON'
        and exists (
          select 1 from public.service_offerings o
          where o.provider_id = sp.id
            and o.active
            and o.category_code in ('VETERINARY', 'BOARDING', 'GROOMING')
        )
        and (
          v_vet_term
          or sp.display_name ilike ('%' || v_q || '%')
          or coalesce(per.display_name, '') ilike ('%' || v_q || '%')
        )
    ) listed
    limit 25
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_list_social_feed() to authenticated;
grant execute on function public.canon_search_vitacora_access_targets(text) to authenticated;
