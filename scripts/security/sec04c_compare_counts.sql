-- SEC-04C counts. Read-only. No PII.
select 'persons' as t, count(*)::int as n from public.persons
union all select 'pets', count(*)::int from public.pets
union all select 'pet_responsibility_links', count(*)::int from public.pet_responsibility_links
union all select 'vitacora_profiles', count(*)::int from public.vitacora_profiles
union all select 'vitacora_moments', count(*)::int from public.vitacora_moments
union all select 'social_posts', count(*)::int from public.social_posts
union all select 'messages', count(*)::int from public.messages
union all select 'conversations', count(*)::int from public.conversations
union all select 'organizations', count(*)::int from public.organizations
union all select 'platform_admin_identities', count(*)::int from public.platform_admin_identities
union all select 'user_platform_role_assignments', count(*)::int from public.user_platform_role_assignments
union all select 'species', count(*)::int from public.species
union all select 'breeds', count(*)::int from public.breeds
union all select 'pet_health_products', count(*)::int from public.pet_health_products
union all select 'security_rate_limit_policies', count(*)::int from public.security_rate_limit_policies
union all select 'security_feature_flags', count(*)::int from public.security_feature_flags
union all select 'media_assets', count(*)::int from public.media_assets
union all select 'auth.users', count(*)::int from auth.users
union all select 'auth.identities', count(*)::int from auth.identities
union all select 'auth.mfa_factors', count(*)::int from auth.mfa_factors
union all select 'storage.buckets', count(*)::int from storage.buckets
union all select 'storage.objects', count(*)::int from storage.objects;
