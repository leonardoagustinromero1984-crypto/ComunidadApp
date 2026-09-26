-- SEC-04C geography/lost-found counts only. No notes/PII.
select 'lost_found_alerts' as t, count(*)::int as n from public.lost_found_alerts
union all select 'lost_found_sightings', count(*)::int from public.lost_found_sightings
union all select 'service_providers', count(*)::int from public.service_providers;
