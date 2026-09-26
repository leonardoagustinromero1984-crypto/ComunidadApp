-- SEC-04D inventory. Paths/sizes only. No signed URLs.
select b.id as bucket,
       b.public as is_public,
       b.file_size_limit,
       o.name as object_path,
       coalesce((o.metadata->>'size')::bigint, 0) as size_bytes,
       o.metadata->>'mimetype' as content_type,
       o.metadata->>'eTag' as etag,
       o.updated_at
  from storage.buckets b
  left join storage.objects o on o.bucket_id = b.id
 order by b.id, o.name;
