-- Read-only: equivalent of reset-staging Superadmin preserve selection.
select
  (select count(*) from public.platform_admin_identities where is_root and disabled_at is null) as root_identities,
  (select count(*)
     from public.user_platform_role_assignments a
     join public.platform_admin_identities i on i.user_id = a.user_id
    where a.role_code = 'SUPERADMIN' and a.revoked_at is null) as root_superadmin_assignments,
  (select count(*)
     from auth.users u
    where exists (
      select 1 from public.platform_admin_identities i where i.user_id = u.id
    )
    or exists (
      select 1 from public.user_platform_role_assignments a
      where a.user_id = u.id
        and a.role_code in ('ADMIN', 'SUPERADMIN')
        and a.revoked_at is null
    )) as users_reset_would_preserve,
  (select count(*)
     from public.persons p
     join public.platform_admin_identities i on i.user_id = p.user_id
    where i.is_root) as root_person_rows;
