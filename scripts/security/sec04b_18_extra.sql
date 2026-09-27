select 'device_tokens' as t, count(*)::int as n from public.device_tokens
union all select 'friendships', count(*)::int from public.friendships
union all select 'file_assets', count(*)::int from public.file_assets
union all select 'pet_authorizations', count(*)::int from public.pet_authorizations
union all select 'administrative_audit_log', count(*)::int from public.administrative_audit_log
order by 1;
