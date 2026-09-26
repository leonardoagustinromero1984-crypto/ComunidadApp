-- 1048: Connection profile pets + VitaCora access target search (STAGING QA)

create or replace function public.canon_list_pets_for_person_profile(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_user_id is null then return '[]'::jsonb; end if;
  if auth.uid() <> p_user_id and not exists (
    select 1 from public.friendships f
    where f.status = 'ACCEPTED'
      and (
        (f.requester_id = auth.uid() and f.addressee_id = p_user_id)
        or (f.addressee_id = auth.uid() and f.requester_id = p_user_id)
      )
  ) then
    return '[]'::jsonb;
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', pet.id,
      'name', pet.name,
      'species_code', pet.species_code,
      'sex', pet.sex,
      'avatar_asset_id', pet.avatar_asset_id,
      'public_code', pet.public_code
    ) order by pet.name)
    from public.pet_responsibility_links l
    join public.pets pet on pet.id = l.pet_id
    where l.holder_person_id = p_user_id
      and l.holder_kind = 'PERSON'
      and l.status = 'ACTIVE'
      and l.role in ('OWNER', 'PRINCIPAL')
      and pet.lifecycle_status = 'ACTIVE'
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
declare v_q text := btrim(coalesce(p_query, ''));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if char_length(v_q) < 2 then return '[]'::jsonb; end if;

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
      join public.organization_capabilities oc on oc.organization_id = org.id
      where sp.lifecycle_status = 'ACTIVE'
        and sp.holder_kind = 'ORGANIZATION'
        and oc.capability in ('VETERINARY_CLINIC', 'PROVIDER', 'NGO', 'SHELTER')
        and exists (
          select 1 from public.service_offerings o
          where o.provider_id = sp.id
            and o.active
            and o.category_code in ('VETERINARY', 'BOARDING', 'GROOMING')
        )
        and sp.display_name ilike ('%' || v_q || '%')
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
            and o.category_code = 'VETERINARY'
        )
        and sp.display_name ilike ('%' || v_q || '%')
    ) listed
    limit 25
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_list_pets_for_person_profile(uuid) to authenticated;
grant execute on function public.canon_search_vitacora_access_targets(text) to authenticated;
