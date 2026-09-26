select c.relname,
       c.relrowsecurity as rls,
       c.relforcerowsecurity as rls_force,
       (select count(*) from pg_policy pol where pol.polrelid = c.oid) as policy_count,
       (select count(*) from pg_policy pol where pol.polrelid = c.oid and pol.polcmd = 'a') as insert_policies
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relname in (
     'commercial_offer_snapshots',
     'country_markets',
     'pet_friendly_venue_subtypes',
     'pet_org_external_ids',
     'pet_rescuer_external_ids',
     'provider_weekly_hours',
     'vitacora_import_jobs',
     'vitacora_import_rows'
   )
 order by 1;
