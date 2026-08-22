-- Read-only AUTH-02 staging inspection. Do not mutate users.
select
  has_table_privilege('authenticated', 'public.persons', 'select') as persons_select_granted,
  has_table_privilege('anon', 'public.persons', 'select') as persons_anon_select_should_be_false,
  has_table_privilege('anon', 'public.location_nodes', 'select') as location_nodes_anon_select,
  has_table_privilege('authenticated', 'public.location_nodes', 'select') as location_nodes_auth_select;

select kind, count(*)::int as n
from public.location_nodes
where active
group by kind
order by kind;

select
  count(*) filter (where id = 'loc-ar' and kind = 'COUNTRY')::int as argentina_country,
  count(*) filter (where kind = 'PROVINCE' and parent_id = 'loc-ar')::int as argentina_provinces,
  count(*) filter (where kind = 'LOCALITY')::int as localities
from public.location_nodes
where active;

-- PERSON usernames that look like the current QA fixture. No email printed.
select
  p.username,
  (p.home_locality_id is not null) as has_locality,
  (u.email_confirmed_at is not null) as email_confirmed,
  (u.confirmed_at is not null) as confirmed_at_set
from public.persons p
join auth.users u on u.id = p.user_id
where lower(p.username) like '%leo%'
   or lower(coalesce(p.display_name, '')) like '%leonardo%'
order by p.created_at desc
limit 10;
