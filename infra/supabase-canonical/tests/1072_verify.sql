select
  (select count(*) from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'canon_list_vitacora_moments') as list_moments,
  (select pg_get_functiondef(p.oid) like '%CARE_TRANSFER%'
     from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'canon_list_vitacora_moments'
    limit 1) as lists_care_transfer,
  (select pg_get_functiondef(p.oid) like '%CARE_CREATED%'
     from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'canon_list_vitacora_moments'
    limit 1) as lists_care_created;
