select
  (select count(*) from auth.users) as auth_users,
  (select count(*) from public.persons) as persons,
  (select count(*) from public.pets) as pets,
  (select count(*) from public.social_posts) as social_posts,
  (select count(*) from storage.objects) as storage_objects,
  (select count(*) from public.platform_admin_identities) as platform_admin_identities,
  (select count(*) from public.user_platform_role_assignments
    where revoked_at is null
      and role_code in ('ADMIN', 'SUPERADMIN', 'MODERATOR', 'SUPPORT')) as staff_role_assignments,
  (select count(*) from public.user_platform_role_assignments
    where revoked_at is null and role_code = 'SUPERADMIN') as superadmin_assignments,
  (select count(*) from auth.mfa_factors) as mfa_factors,
  (select last_value from public.vitacora_public_number_seq) as vitacora_seq_last,
  (select is_called from public.vitacora_public_number_seq) as vitacora_seq_called,
  (select count(*) from supabase_migrations.schema_migrations) as schema_migrations,
  (select max(version) from supabase_migrations.schema_migrations) as migration_max,
  (select count(*) from public.species) as species,
  (select count(*) from public.security_rate_limit_policies
    where operation_key = 'signed_url.request') as signed_url_policy;
