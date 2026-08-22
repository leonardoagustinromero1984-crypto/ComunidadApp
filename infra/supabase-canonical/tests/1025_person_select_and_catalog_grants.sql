-- AUTH-02 / 1025 focused smoke (run against Staging after apply).
-- Expect: persons SELECT granted to authenticated; self username available;
-- location_nodes still DIRECT_CATALOG_SELECT.

select
  has_table_privilege('authenticated', 'public.persons', 'select') as persons_select_granted,
  has_table_privilege('anon', 'public.location_nodes', 'select') as location_nodes_anon_select,
  has_table_privilege('authenticated', 'public.location_nodes', 'select') as location_nodes_auth_select;
