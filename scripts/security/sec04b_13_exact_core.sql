select 'users' as table_name, count(*)::int as n from public.users
union all select 'user_privacy_settings', count(*)::int from public.user_privacy_settings
union all select 'user_role_assignments', count(*)::int from public.user_role_assignments
union all select 'administrative_assignments', count(*)::int from public.administrative_assignments
union all select 'pets', count(*)::int from public.pets
union all select 'pet_responsibilities', count(*)::int from public.pet_responsibilities
union all select 'organizations', count(*)::int from public.organizations
union all select 'organization_memberships', count(*)::int from public.organization_memberships
union all select 'posts', count(*)::int from public.posts
union all select 'm19_social_posts', count(*)::int from public.m19_social_posts
union all select 'messages', count(*)::int from public.messages
union all select 'm20_messages', count(*)::int from public.m20_messages
union all select 'conversations', count(*)::int from public.conversations
union all select 'content_reports', count(*)::int from public.content_reports
union all select 'pet_clinical_records', count(*)::int from public.pet_clinical_records
union all select 'pet_passports', count(*)::int from public.pet_passports
union all select 'lost_found_posts', count(*)::int from public.lost_found_posts
order by 1;
