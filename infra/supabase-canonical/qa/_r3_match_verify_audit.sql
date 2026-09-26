-- Read-only STAGING audit for Physical Round 3. Not a migration.

select 'alerts' as section, a.id::text, a.kind, a.status, a.species_code,
       a.pet_id::text, a.created_by::text, a.note,
       a.precise_location is not null as has_geo,
       a.created_at::text
  from public.lost_found_alerts a
 order by a.created_at desc
 limit 20;
