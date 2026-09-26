select id, public, file_size_limit from storage.buckets order by 1;

select policyname, cmd, roles
  from pg_policies
 where schemaname = 'storage' and tablename = 'objects'
 order by policyname;
