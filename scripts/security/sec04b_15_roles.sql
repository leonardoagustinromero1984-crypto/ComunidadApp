select r.code, count(*)::int as n
  from public.user_role_assignments a
  join public.platform_roles r on r.id = a.role_id
 group by 1
 order by 1;
