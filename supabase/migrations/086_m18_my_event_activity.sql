-- LeoVer M18 — own event registrations for personal activity.
-- Forward-only. Reuses m18_event_registrations and m18_community_events.
-- Does not add tables. event_id is returned only so the app can open the event.

begin;

create or replace function public.m18_list_my_event_activity()
returns setof jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m18_require_authenticated();
begin
  return query
  select jsonb_build_object(
    'event_id', e.id,
    'title', e.title,
    'organization_name', coalesce(o.display_name, ''),
    'starts_at', e.starts_at,
    'ends_at', e.ends_at,
    'event_status', e.event_status,
    'registration_status', r.status,
    'venue_name', e.venue_name,
    'location_text', e.public_location_text
  )
  from public.m18_event_registrations r
  join public.m18_community_events e on e.id = r.event_id
  left join public.organizations o on o.id = e.organization_id
  where r.user_id = v_actor
  order by e.starts_at desc;
end;
$$;

revoke all on function public.m18_list_my_event_activity() from public;
grant execute on function public.m18_list_my_event_activity() to authenticated;

commit;
