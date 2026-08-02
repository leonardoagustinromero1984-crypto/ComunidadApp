select jsonb_build_object(
  'schema_migrations', (
    select coalesce(jsonb_agg(jsonb_build_object('version', version, 'name', name) order by version), '[]'::jsonb)
    from supabase_migrations.schema_migrations
    where version in ('064', '065', '066')
  ),
  'tables_065', jsonb_build_object(
    'm21_eligibility_records', exists (select 1 from information_schema.tables where table_schema='public' and table_name='m21_eligibility_records'),
    'm21_review_responses', exists (select 1 from information_schema.tables where table_schema='public' and table_name='m21_review_responses'),
    'm21_review_disputes', exists (select 1 from information_schema.tables where table_schema='public' and table_name='m21_review_disputes')
  ),
  'rpc_065_count', (
    select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname in (
      'm21_check_eligibility','m21_edit_review','m21_archive_review',
      'm21_submit_review_response','m21_submit_dispute','m21_get_subject_breakdown','m21_get_review_detail'
    )
  ),
  'helpers_065_count', (
    select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname in (
      '_m21_countable_review_statuses','_m21_is_countable_review','_m21_evaluate_eligibility'
    )
  ),
  'tables_066', jsonb_build_object(
    'm22_service_providers', exists (select 1 from information_schema.tables where table_schema='public' and table_name='m22_service_providers'),
    'm22_provider_branches', exists (select 1 from information_schema.tables where table_schema='public' and table_name='m22_provider_branches'),
    'm22_service_offerings', exists (select 1 from information_schema.tables where table_schema='public' and table_name='m22_service_offerings')
  )
) as verification;
