-- 1044: VitaCora grants/proposals listing for pet holders (STAGING)

create or replace function public.canon_list_vitacora_grants(p_pet_id uuid)
returns setof public.vitacora_access_grants
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.view') then
    raise exception 'FORBIDDEN';
  end if;
  return query
    select *
    from public.vitacora_access_grants g
    where g.pet_id = p_pet_id
    order by g.granted_at desc;
end;
$$;

create or replace function public.canon_list_vitacora_proposals(p_pet_id uuid)
returns setof public.vitacora_update_proposals
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'vitacora.view') then
    raise exception 'FORBIDDEN';
  end if;
  return query
    select *
    from public.vitacora_update_proposals p
    where p.pet_id = p_pet_id
    order by p.created_at desc;
end;
$$;

grant execute on function public.canon_list_vitacora_grants(uuid) to authenticated;
grant execute on function public.canon_list_vitacora_proposals(uuid) to authenticated;
