select c.relname,
       c.relrowsecurity as rls,
       c.relforcerowsecurity as rls_force
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

select schemaname, tablename, policyname, cmd, roles
  from pg_policies
 where schemaname = 'public'
   and tablename in (
     'commercial_offer_snapshots',
     'country_markets',
     'pet_friendly_venue_subtypes',
     'pet_org_external_ids',
     'pet_rescuer_external_ids',
     'provider_weekly_hours',
     'vitacora_import_jobs',
     'vitacora_import_rows'
   )
 order by 2, 3;
