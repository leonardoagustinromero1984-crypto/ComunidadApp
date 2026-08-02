-- =============================================================================
-- LeoVer M17 — migración 057: corrección listado público voluntariado
-- Bug: m17_list_public_volunteer_opportunities referenciaba alias o fuera de scope.
-- Forward-only; no modifica 054/055/056 aplicadas.
-- =============================================================================

begin;

create or replace function public.m17_list_public_volunteer_opportunities(
  p_query text default null,
  p_type text default null,
  p_organization_id uuid default null
) returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_row public.m17_volunteer_opportunities;
declare v_q text := nullif(lower(trim(coalesce(p_query, ''))), '');
begin
  for v_row in
    select o.* from public.m17_volunteer_opportunities o
    where public._m17_vol_opp_is_public(o)
      and o.status = 'PUBLISHED'
      and (p_type is null or o.opportunity_type = upper(trim(p_type)))
      and (p_organization_id is null or o.organization_id = p_organization_id)
      and (v_q is null or o.title ilike '%' || v_q || '%' or o.description ilike '%' || v_q || '%')
    order by o.updated_at desc
  loop
    return next public._m17_public_volunteer_opp_json(v_row.id);
  end loop;
end;
$$;

revoke all on function public.m17_list_public_volunteer_opportunities from public;
grant execute on function public.m17_list_public_volunteer_opportunities to anon, authenticated;

commit;
