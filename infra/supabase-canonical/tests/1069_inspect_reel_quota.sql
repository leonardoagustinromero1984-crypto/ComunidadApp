-- Read-only inspect: reel/media usage windows + recent quota audit.
-- Does not mutate. Does not change policies.

select
  w.operation_key,
  w.scope_id,
  w.window_started_at,
  w.used_count,
  w.used_units,
  p.limit_count,
  p.window_seconds,
  p.exceed_code,
  left(per.display_name, 40) as display_name,
  per.username
from public.security_usage_windows w
join public.security_rate_limit_policies p on p.operation_key = w.operation_key
left join public.persons per on per.user_id::text = w.scope_id
where w.operation_key in (
  'media.register',
  'media.upload.bytes.daily',
  'media.video.count.daily',
  'social.reel.create'
)
order by w.used_units desc, w.window_started_at desc
limit 80;

select
  e.occurred_at,
  e.action,
  e.actor_user_id,
  e.metadata
from public.security_audit_events e
where e.action in ('UPLOAD_QUOTA_EXCEEDED', 'RATE_LIMIT_EXCEEDED')
  and e.occurred_at > timezone('utc', now()) - interval '7 days'
order by e.occurred_at desc
limit 40;
