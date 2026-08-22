-- LeoVer Canonical
-- Logical migration: 1027
-- Consumer contract only: canon_list_providers returns coverage geography.
-- No QA/demo DML.

create or replace function public.canon_list_providers()
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
      'id', p.id,
      'holder_kind', p.holder_kind,
      'holder_person_id', p.holder_person_id,
      'holder_organization_id', p.holder_organization_id,
      'display_name', p.display_name,
      'categories', coalesce((
        select jsonb_agg(o.category_code)
        from public.service_offerings o
        where o.provider_id = p.id and o.active
      ), '[]'::jsonb),
      'locality_ids', coalesce((
        select jsonb_agg(c.locality_id)
        from public.provider_coverage_areas c
        where c.provider_id = p.id
      ), '[]'::jsonb),
      'province_ids', coalesce((
        select jsonb_agg(distinct loc.parent_id)
        from public.provider_coverage_areas c
        join public.location_nodes loc on loc.id = c.locality_id
        where c.provider_id = p.id
      ), '[]'::jsonb)
    ) order by p.display_name)
    from public.service_providers p
    where p.lifecycle_status = 'ACTIVE'
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_list_providers() from public, anon;
grant execute on function public.canon_list_providers() to authenticated;
