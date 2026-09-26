-- 1074: PERSON origin library ("Mis recuerdos").
-- Does not edit 1071/1072/1073. No asset delete. PET ACCESS ≠ MEDIA ACCESS.
-- Organization owner_kind remains valid; ORG UI PENDING QA / FUTURE SURFACE.

create or replace function public.canon_list_my_personal_memories(
  p_limit integer default 50
)
returns table (
  asset_id uuid,
  mime_type text,
  created_at timestamptz,
  pet_id uuid,
  pet_name text,
  caption text,
  shared_with_vitacora boolean,
  can_open_pet boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 100);
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;

  return query
  select
    a.id,
    a.mime_type,
    a.created_at,
    linked.pet_id,
    p.name,
    coalesce(
      (
        select nullif(trim(m.title), '')
        from public.vitacora_moments m
        where m.asset_id = a.id
        order by m.created_at desc
        limit 1
      ),
      (
        select nullif(trim(m.body), '')
        from public.vitacora_moments m
        where m.asset_id = a.id
        order by m.created_at desc
        limit 1
      ),
      (
        select nullif(trim(sp.body), '')
        from public.media_asset_links l
        join public.social_posts sp on sp.id = l.owner_id
        where l.asset_id = a.id
          and l.owner_table = 'social_posts'
        limit 1
      )
    ) as caption,
    (
      linked.pet_id is not null
      and not exists (
        select 1
        from public.pet_care_stages s
        where s.pet_id = linked.pet_id
          and s.closed_at is not null
          and s.share_personal_media is false
          and a.created_at >= s.opened_at
          and a.created_at <= s.closed_at
      )
    ) as shared_with_vitacora,
    (
      linked.pet_id is not null
      and (
        public._acl_pet_holder(v_uid, linked.pet_id)
        or public._acl_pet_permission(v_uid, linked.pet_id, 'vitacora.view')
      )
    ) as can_open_pet
  from public.media_assets a
  left join lateral (
    select public._canon_media_linked_pet_id(a) as pet_id
  ) linked on true
  left join public.pets p on p.id = linked.pet_id
  where a.owner_kind = 'PERSON'
    and a.owner_person_id = v_uid
    and a.lifecycle_status in ('READY', 'UPLOADING')
    and public._canon_media_origin_readable(v_uid, a)
    and public._canon_media_is_personal_social(a)
    and not public._canon_media_is_health_or_professional(a)
  order by a.created_at desc
  limit v_limit;
end;
$$;

revoke all on function public.canon_list_my_personal_memories(integer) from public, anon;
grant execute on function public.canon_list_my_personal_memories(integer) to authenticated;

notify pgrst, 'reload schema';
