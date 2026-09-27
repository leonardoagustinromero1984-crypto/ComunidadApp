-- READ ONLY inventory of schema auth. No DML. No secrets.
select current_database() as db_name,
       (select last_value from public.vitacora_public_number_seq) as vitacora_sequence_last,
       (select is_called from public.vitacora_public_number_seq) as vitacora_sequence_is_called,
       (select count(*) from auth.users) as auth_users,
       (select count(*) from public.persons) as persons,
       (select count(*) from public.pets) as pets,
       (select count(*) from storage.objects) as storage_objects,
       (select count(*) from public.platform_admin_identities) as platform_admin_identities,
       (select count(*) from public.user_platform_role_assignments
         where revoked_at is null
           and role_code in ('ADMIN', 'SUPERADMIN', 'MODERATOR', 'SUPPORT')) as staff_role_assignments,
       (select count(*) from public.user_platform_role_assignments
         where revoked_at is null and role_code = 'SUPERADMIN') as superadmin_assignments,
       (select count(*) from auth.mfa_factors) as mfa_factors,
       (select count(*) from auth.users u
         where exists (
           select 1 from public.user_platform_role_assignments a
            where a.user_id = u.id
              and a.revoked_at is null
              and a.role_code in ('ADMIN', 'SUPERADMIN', 'MODERATOR', 'SUPPORT')
         ) or exists (
           select 1 from public.platform_admin_identities i where i.user_id = u.id
         )) as expected_auth_users_after;
