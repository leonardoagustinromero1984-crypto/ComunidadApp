-- 1081: Enrich the existing accepted-Manada pet profile projection with age.
-- The authorization contract is unchanged from 1048:
-- self or an ACCEPTED PERSON friendship with the profile owner.

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
      'public_code', pet.public_code,
      'birth_precision', pet.birth_precision,
      'birth_date', pet.birth_date,
      'birth_year', pet.birth_year,
      'birth_month', pet.birth_month,
      'estimated_age_months', pet.estimated_age_months,
      'estimated_as_of', pet.estimated_as_of
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

revoke all on function public.canon_list_pets_for_person_profile(uuid) from public, anon;
grant execute on function public.canon_list_pets_for_person_profile(uuid) to authenticated;

notify pgrst, 'reload schema';
