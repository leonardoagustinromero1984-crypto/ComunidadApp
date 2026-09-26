select
  (select count(*) from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'canon_list_incoming_care_transfers') as incoming_rpc,
  (select pg_get_functiondef(p.oid) like '%_canon_personal_media_withheld_from%'
     from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'canon_list_vitacora_moments'
    limit 1) as list_redacts_withheld,
  (select count(*) from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = '_canon_media_origin_readable') as origin_readable,
  (
    select count(*)
    from public.pet_care_transfers
    where id = 'ba8378f9-e970-4f13-855a-e2be2d77aac9'
      and status = 'PENDING'
      and share_personal_media is false
      and target_kind = 'PERSON'
  ) as qa_pending_share_no;
