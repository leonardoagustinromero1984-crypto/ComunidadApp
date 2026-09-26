-- SEC-04C storage inventory. Names/sizes only.
select id, public, file_size_limit,
       (select count(*)::int from storage.objects o where o.bucket_id = b.id) as object_rows
  from storage.buckets b
 order by id;
