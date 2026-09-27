select operation_key, window_seconds, limit_count, exceed_code, enabled, fail_closed
from public.security_rate_limit_policies
where operation_key in (
  'media.register',
  'media.upload.bytes.daily',
  'media.video.count.daily',
  'social.reel.create'
)
order by operation_key;

select to_regclass('public.pet_transfers') as pet_transfers,
       to_regclass('public.social_post_pets') as social_post_pets,
       to_regclass('public.support_tickets') as support_tickets;

select routine_name
from information_schema.routines
where routine_schema = 'public'
  and routine_name in (
    'canon_remove_friendship',
    'm08_list_pet_transfers',
    'm08_initiate_pet_transfer',
    'canon_list_support_tickets'
  )
order by routine_name;
