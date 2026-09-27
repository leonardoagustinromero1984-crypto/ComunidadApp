select 'AUTH_USERS' as k, count(*)::bigint as n from auth.users
union all select 'PERSONS', count(*) from public.persons
union all select 'PETS', count(*) from public.pets
union all select 'VITACORA_PROFILES', count(*) from public.vitacora_profiles
union all select 'ORGANIZATIONS', count(*) from public.organizations
union all select 'ORG_MEMBERSHIPS', count(*) from public.organization_memberships
union all select 'SOCIAL_POSTS', count(*) from public.social_posts
union all select 'SOCIAL_STORIES', count(*) from public.social_stories
union all select 'SOCIAL_COMMENTS', count(*) from public.social_comments
union all select 'CONVERSATIONS', count(*) from public.conversations
union all select 'MESSAGES', count(*) from public.messages
union all select 'ADOPTION_PUBLICATIONS', count(*) from public.adoption_publications
union all select 'VITACORA_IMPORT_JOBS', count(*) from public.vitacora_import_jobs
union all select 'COUNTRIES', count(*) from public.location_nodes where kind = 'COUNTRY'
union all select 'ADMINISTRATIVE_AREAS', count(*) from public.location_nodes where kind = 'PROVINCE'
union all select 'LOCALITIES', count(*) from public.location_nodes where kind = 'LOCALITY'
union all select 'SPECIES', count(*) from public.species
union all select 'BREEDS', count(*) from public.breeds
union all select 'COUNTRY_MARKETS', count(*) from public.country_markets
union all select 'LEGAL_DOCUMENTS', count(*) from public.legal_documents
union all select 'PERMISSION_CODES', count(*) from public.permission_codes
union all select 'PLATFORM_ROLES', count(*) from public.platform_roles
union all select 'SERVICE_CATEGORIES', count(*) from public.service_categories
union all select 'STORAGE_OBJECTS', count(*) from storage.objects
union all select 'STORAGE_BUCKETS', count(*) from storage.buckets
union all select 'MEDIA_ASSETS', count(*) from public.media_assets
union all select 'PLATFORM_ADMIN_IDENTITIES', count(*) from public.platform_admin_identities
union all select 'STAFF_ROLE_ASSIGNMENTS', count(*) from public.user_platform_role_assignments
  where revoked_at is null
    and role_code in ('ADMIN', 'SUPERADMIN', 'MODERATOR', 'SUPPORT')
union all select 'SUPERADMIN_ASSIGNMENTS', count(*) from public.user_platform_role_assignments
  where revoked_at is null and role_code = 'SUPERADMIN'
union all select 'MFA_FACTORS', count(*) from auth.mfa_factors;
