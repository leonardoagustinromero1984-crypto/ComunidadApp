select provider, count(*)::int as n
  from auth.identities
 group by 1
 order by 1;
