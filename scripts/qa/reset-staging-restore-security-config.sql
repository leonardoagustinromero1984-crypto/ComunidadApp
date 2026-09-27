-- QA recovery only. Not a product migration.
-- Re-seeds SEC-03/SEC-P1 config rows wiped because those tables
-- were missing from the reset preserve list. Values match 1063 + 1067 + 1069.

insert into public.security_feature_flags (flag_key, enabled, notes) values
  ('media.video.upload.enabled', true, 'Kill switch for video uploads'),
  ('reels.create.enabled', true, 'Kill switch for reel create'),
  ('imports.enabled', true, 'Kill switch for VitaCora import analyze/execute')
on conflict (flag_key) do nothing;

insert into public.security_rate_limit_policies
  (operation_key, window_seconds, limit_count, scope_kind, enabled, fail_closed, exceed_code, notes)
values
  ('social.post.create', 3600, 10, 'USER', true, false, 'RATE_LIMITED', 'Human posting; stop bursts'),
  ('social.reel.create', 3600, 6, 'USER', true, false, 'RATE_LIMITED', 'Video cost companion'),
  ('social.story.create', 3600, 12, 'USER', true, false, 'RATE_LIMITED', 'Story bursts'),
  ('social.comment.create', 600, 30, 'USER', true, false, 'RATE_LIMITED', 'Comment spam'),
  ('social.reaction', 600, 60, 'USER', true, false, 'RATE_LIMITED', 'Reaction storms'),
  ('social.connection_request', 3600, 20, 'USER', true, false, 'RATE_LIMITED', 'Account farming'),
  ('social.connection_cancel', 3600, 20, 'USER', true, false, 'RATE_LIMITED', 'Request churn'),
  ('chat.message.send', 60, 40, 'USER', true, false, 'RATE_LIMITED', 'Per-minute chat'),
  ('chat.conversation.create', 3600, 15, 'USER', true, false, 'RATE_LIMITED', 'Conversation farming'),
  ('report.create', 3600, 15, 'USER', true, false, 'RATE_LIMITED', 'Automated reports'),
  ('social.lost_found.create', 3600, 8, 'USER', true, false, 'RATE_LIMITED', 'Lost/found spam'),
  ('social.org_invite', 3600, 20, 'USER', true, false, 'RATE_LIMITED', 'Invite + notification spam'),
  ('media.register', 3600, 30, 'USER', true, true, 'RATE_LIMITED', 'Metadata + storage insert'),
  ('media.upload.bytes.daily', 86400, 209715200, 'USER', true, true, 'QUOTA_EXCEEDED', '200 MiB/day declared size'),
  ('media.video.count.daily', 86400, 8, 'USER', true, true, 'QUOTA_EXCEEDED', 'Daily video count'),
  ('signed_url.request', 600, 60, 'USER', true, true, 'RATE_LIMITED', 'Private signed URL gate'),
  ('vitacora.import.analyze', 3600, 6, 'USER', true, true, 'RATE_LIMITED', 'XLSX analyze'),
  ('vitacora.import.execute', 3600, 4, 'USER', true, true, 'RATE_LIMITED', 'Import confirm'),
  ('admin.staff.create', 600, 10, 'ADMIN', true, true, 'RATE_LIMITED', 'Staff create'),
  ('admin.password_reset', 600, 10, 'ADMIN', true, true, 'RATE_LIMITED', 'Staff password reset'),
  ('admin.role_change', 3600, 20, 'ADMIN', true, true, 'RATE_LIMITED', 'Role mutations')
on conflict (operation_key) do nothing;

update public.security_rate_limit_policies
   set limit_count = 60,
       window_seconds = 600,
       fail_closed = true,
       updated_at = timezone('utc', now())
 where operation_key = 'signed_url.request';

insert into public.security_rate_limit_rpc_bindings (proname, operation_key) values
  ('canon_create_story', 'social.story.create'),
  ('canon_comment_social_post', 'social.comment.create'),
  ('canon_comment_story', 'social.comment.create'),
  ('canon_react_social_post', 'social.reaction'),
  ('canon_react_story', 'social.reaction'),
  ('canon_send_friend_request', 'social.connection_request'),
  ('canon_cancel_friend_request', 'social.connection_cancel'),
  ('canon_start_conversation', 'chat.conversation.create'),
  ('canon_send_message', 'chat.message.send'),
  ('create_content_report', 'report.create'),
  ('canon_create_lost_found', 'social.lost_found.create'),
  ('canon_invite_organization_member', 'social.org_invite'),
  ('canon_import_analyze', 'vitacora.import.analyze'),
  ('canon_import_confirm', 'vitacora.import.execute'),
  ('staff_register_identity', 'admin.staff.create'),
  ('staff_force_password_change', 'admin.password_reset'),
  ('staff_on_password_reset', 'admin.password_reset'),
  ('staff_set_role', 'admin.role_change'),
  ('assign_platform_role', 'admin.role_change'),
  ('revoke_platform_role', 'admin.role_change'),
  ('canon_remove_friendship', 'social.connection_cancel')
on conflict (proname) do nothing;

select
  (select count(*) from public.security_feature_flags) as feature_flags,
  (select count(*) from public.security_rate_limit_policies) as rate_policies,
  (select count(*) from public.security_rate_limit_rpc_bindings) as rpc_bindings,
  (select limit_count from public.security_rate_limit_policies
    where operation_key = 'signed_url.request') as signed_url_limit,
  (select window_seconds from public.security_rate_limit_policies
    where operation_key = 'signed_url.request') as signed_url_window,
  (select fail_closed from public.security_rate_limit_policies
    where operation_key = 'signed_url.request') as signed_url_fail_closed;
