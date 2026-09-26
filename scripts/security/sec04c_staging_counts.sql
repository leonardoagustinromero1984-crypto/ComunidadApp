select 'persons' as t, count(*)::int as n from public.persons
union all select 'pets', count(*)::int from public.pets
union all select 'vitacora_profiles', count(*)::int from public.vitacora_profiles
union all select 'social_posts', count(*)::int from public.social_posts
union all select 'messages', count(*)::int from public.messages
union all select 'organizations', count(*)::int from public.organizations
union all select 'platform_admin_identities', count(*)::int from public.platform_admin_identities
union all select 'species', count(*)::int from public.species
union all select 'security_rate_limit_policies', count(*)::int from public.security_rate_limit_policies
union all select 'security_feature_flags', count(*)::int from public.security_feature_flags;
