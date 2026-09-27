-- Read-only storage inventory by bucket. No object payloads.
select bucket_id, count(*)::bigint as n
from storage.objects
group by bucket_id
order by bucket_id;
