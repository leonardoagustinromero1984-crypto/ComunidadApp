select c.relname,
       has_table_privilege('anon', c.oid, 'INSERT') as anon_ins,
       has_table_privilege('authenticated', c.oid, 'INSERT') as auth_ins,
       has_table_privilege('authenticated', c.oid, 'SELECT') as auth_sel
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
