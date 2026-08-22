-- AUTH-03 / 1026 geography smoke. Read-only. No user mutation.
select json_build_object(
  'argentina_country', (
    select count(*)::int from public.location_nodes
    where active and id = 'loc-ar' and kind = 'COUNTRY'
  ),
  'province_count', (
    select count(*)::int from public.location_nodes
    where active and kind = 'PROVINCE' and parent_id = 'loc-ar'
  ),
  'locality_count', (
    select count(*)::int from public.location_nodes
    where active and kind = 'LOCALITY'
  ),
  'provinces_without_localities', (
    select count(*)::int
    from public.location_nodes p
    where p.kind = 'PROVINCE' and p.parent_id = 'loc-ar' and p.active
      and not exists (
        select 1 from public.location_nodes l
        where l.kind = 'LOCALITY' and l.active and l.parent_id = p.id
      )
  ),
  'required_province_locality_counts', (
    select json_object_agg(s.name, s.c)
    from (
      select p.name, count(l.id)::int as c
      from public.location_nodes p
      left join public.location_nodes l
        on l.parent_id = p.id and l.kind = 'LOCALITY' and l.active
      where p.id in (
        'loc-ar-prov-buenos-aires',
        'loc-ar-prov-caba',
        'loc-ar-prov-cordoba',
        'loc-ar-prov-santa-fe',
        'loc-ar-prov-mendoza',
        'loc-ar-prov-misiones',
        'loc-ar-prov-salta',
        'loc-ar-prov-neuquen',
        'loc-ar-prov-tierra-del-fuego'
      )
      group by p.name
    ) s
  ),
  'san_vicente_ba', exists (
    select 1 from public.location_nodes
    where id = 'loc-ar-loc-san-vicente'
      and parent_id = 'loc-ar-prov-buenos-aires'
      and name = 'San Vicente'
      and kind = 'LOCALITY'
      and active
  ),
  'almirante_brown_relevant', (
    select json_build_object(
      'almirante_brown', exists (
        select 1 from public.location_nodes
        where parent_id = 'loc-ar-prov-buenos-aires'
          and kind = 'LOCALITY' and active
          and name = 'Almirante Brown'
      ),
      'adrogue', exists (
        select 1 from public.location_nodes
        where parent_id = 'loc-ar-prov-buenos-aires'
          and kind = 'LOCALITY' and active
          and name = 'Adrogué'
      ),
      'burzaco', exists (
        select 1 from public.location_nodes
        where parent_id = 'loc-ar-prov-buenos-aires'
          and kind = 'LOCALITY' and active
          and name = 'Burzaco'
      ),
      'glew', exists (
        select 1 from public.location_nodes
        where parent_id = 'loc-ar-prov-buenos-aires'
          and kind = 'LOCALITY' and active
          and name = 'Glew'
      )
    )
  ),
  'baseline_ids_preserved', (
    select count(*)::int from public.location_nodes
    where id in (
      'loc-ar-loc-san-vicente',
      'loc-ar-loc-almirante-brown',
      'loc-ar-loc-la-plata',
      'loc-ar-loc-lomas',
      'loc-ar-loc-quilmes',
      'loc-ar-loc-avellaneda',
      'loc-ar-loc-adrogué',
      'loc-ar-loc-burzaco',
      'loc-ar-loc-glew',
      'loc-ar-loc-alejandro-korn',
      'loc-ar-loc-caba',
      'loc-ar-loc-palermo',
      'loc-ar-loc-cordoba-cap',
      'loc-ar-loc-rosario'
    )
  ),
  'catalog_rpc_count', jsonb_array_length(public.canon_list_location_catalog()),
  'velu_home_locality_still_valid', (
    select (p.home_locality_id is null)
        or exists (
          select 1 from public.location_nodes n
          where n.id = p.home_locality_id and n.kind = 'LOCALITY' and n.active
        )
    from public.persons p
    where lower(p.username) = 'velu'
  ),
  'migration_head', (
    select version from supabase_migrations.schema_migrations
    order by version desc
    limit 1
  )
);
