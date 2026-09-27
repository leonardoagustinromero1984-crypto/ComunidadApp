-- 1058: resolve catalog breed by id including inactive (historical pet.breed_id).
-- Does not edit 1057. No DELETE. No denormalized breed text on pets.

create or replace function public.canon_get_breed(p_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_id is null then return null; end if;
  return (
    select jsonb_build_object(
      'id', b.id,
      'species_code', b.species_code,
      'name', b.name,
      'sort_key', b.sort_key,
      'active', b.active
    )
    from public.breeds b
    where b.id = p_id
  );
end;
$$;

revoke all on function public.canon_get_breed(uuid) from public;
grant execute on function public.canon_get_breed(uuid) to authenticated, anon;

notify pgrst, 'reload schema';
