select 'candidates' as kind, count(*)::text as n from public.lost_found_match_candidates;
select 'notifications' as kind, count(*)::text as n from public.notifications;
select 'outbox' as kind, count(*)::text as n from public.notification_outbox;
select 'qa_orgs' as kind, count(*)::text as n from public.organizations where slug like 'qa-cc02-%';

select c.alert_id::text as found_id, c.lost_alert_id::text as lost_id, c.score, c.status
  from public.lost_found_match_candidates c
 order by c.created_at desc
 limit 5;

select n.user_id::text, n.kind, n.payload->>'dedup' as dedup, n.read_at is null as unread
  from public.notifications n
 order by n.created_at desc
 limit 5;
