select
  (select count(*) from information_schema.columns
    where table_schema = 'public'
      and table_name = 'pets'
      and column_name in (
        'current_custodian_kind',
        'current_custodian_person_id',
        'current_custodian_organization_id'
      )) as custodian_columns,
  (select count(*) from pg_tables
    where schemaname = 'public'
      and tablename in ('pet_care_transfers', 'pet_care_stages')) as care_tables,
  (select count(*) from public.permission_codes
    where code = 'org.pets.transfer') as org_transfer_perm,
  (select count(*) from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public'
     and p.proname in (
       'canon_initiate_care_transfer',
       'canon_accept_care_transfer',
       'canon_reject_care_transfer',
       'canon_cancel_care_transfer',
       'canon_list_care_transfers',
       'canon_list_incoming_care_transfers',
       'canon_get_pet_care_context',
       'canon_search_care_transfer_targets',
       '_acl_is_current_custodian_operator'
     )) as care_rpcs,
  (select count(*) from public.pets p
    where p.current_custodian_kind is null) as pets_missing_custodian;
