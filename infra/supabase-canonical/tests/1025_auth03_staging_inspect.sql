-- AUTH-03 read-only inspect. Single JSON. No email printed.
select json_build_object(
  'argentina_country', (
    select count(*)::int from public.location_nodes
    where active and id = 'loc-ar' and kind = 'COUNTRY'
  ),
  'argentina_provinces', (
    select count(*)::int from public.location_nodes
    where active and kind = 'PROVINCE' and parent_id = 'loc-ar'
  ),
  'argentina_localities', (
    select count(*)::int from public.location_nodes
    where active and kind = 'LOCALITY'
  ),
  'provinces_with_counts', (
    select json_agg(row_to_json(t) order by t.locality_count desc, t.province)
    from (
      select p.name as province, count(l.id)::int as locality_count
      from public.location_nodes p
      left join public.location_nodes l
        on l.parent_id = p.id and l.kind = 'LOCALITY' and l.active
      where p.kind = 'PROVINCE' and p.parent_id = 'loc-ar' and p.active
      group by p.name
    ) t
  ),
  'privacy_state_counts', (
    select coalesce(json_object_agg(privacy_state, n), '{}'::json)
    from (
      select privacy_state, count(*)::int as n
      from public.persons
      group by privacy_state
    ) s
  ),
  'friends_only_rows', (
    select count(*)::int from public.persons
    where privacy_state in ('FRIENDS_ONLY', 'FRIENDS', 'ONLY_FRIENDS', 'SOLO_AMIGOS')
  ),
  'velu', (
    select json_build_object(
      'username', username,
      'has_locality', home_locality_id is not null,
      'display_name_len', length(coalesce(display_name, '')),
      'privacy_state', privacy_state
    )
    from public.persons
    where lower(username) = 'velu'
  ),
  'migration_head', (
    select version from supabase_migrations.schema_migrations
    order by version desc
    limit 1
  )
);
