select bucket_id,
       count(*)::int as objects,
       coalesce(sum((metadata->>'size')::bigint), 0) as bytes
  from storage.objects
 group by 1
 order by 1;
