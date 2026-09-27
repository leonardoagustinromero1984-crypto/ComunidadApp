-- Restore SEC-03 test mutations if the directed suite aborted.
delete from public.social_posts where body like 'SEC03-FIXTURE-%';

update public.security_rate_limit_policies
   set limit_count = case operation_key
     when 'social.post.create' then 10
     when 'signed_url.request' then 60
     when 'media.upload.bytes.daily' then 209715200
     else limit_count
   end,
       window_seconds = case operation_key
     when 'social.post.create' then 3600
     when 'signed_url.request' then 600
     when 'media.upload.bytes.daily' then 86400
     else window_seconds
   end
 where operation_key in (
   'social.post.create',
   'signed_url.request',
   'media.upload.bytes.daily'
 );

update public.security_feature_flags set enabled = true
 where flag_key in ('imports.enabled', 'reels.create.enabled', 'media.video.upload.enabled');

select operation_key, limit_count, window_seconds
  from public.security_rate_limit_policies
 where operation_key in (
   'social.post.create',
   'signed_url.request',
   'media.upload.bytes.daily'
 )
 order by 1;

select count(*) as leftover_sec03_posts
  from public.social_posts
 where body like 'SEC03-FIXTURE-%';
