-- LeoVer M17 — own goods pledges and volunteer interests.
-- Forward-only. Reuses m17_in_kind_pledges and m17_volunteer_applications.
-- Does not add tables and does not return identifiers.

begin;

create or replace function public.m17_list_my_in_kind_pledges()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m17_require_authenticated();
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'need_title', n.title,
      'organization_name', coalesce(o.display_name, ''),
      'quantity', p.quantity,
      'unit', coalesce(n.quantity_unit, ''),
      'status', p.status,
      'created_at', p.created_at
    ) order by p.created_at desc)
    from public.m17_in_kind_pledges p
    join public.m17_in_kind_needs n on n.id = p.need_id
    left join public.organizations o on o.id = n.organization_id
    where p.contributor_user_id = v_actor
  ), '[]'::jsonb);
end;
$$;

create or replace function public.m17_list_my_volunteer_applications()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m17_require_authenticated();
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'opportunity_title', opp.title,
      'organization_name', coalesce(o.display_name, ''),
      'status', a.status,
      'created_at', a.created_at
    ) order by a.created_at desc)
    from public.m17_volunteer_applications a
    join public.m17_volunteer_opportunities opp on opp.id = a.opportunity_id
    left join public.organizations o on o.id = opp.organization_id
    where a.applicant_user_id = v_actor
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.m17_list_my_in_kind_pledges() from public;
grant execute on function public.m17_list_my_in_kind_pledges() to authenticated;
revoke all on function public.m17_list_my_volunteer_applications() from public;
grant execute on function public.m17_list_my_volunteer_applications() to authenticated;

commit;
