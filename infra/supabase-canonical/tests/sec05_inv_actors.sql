-- Counts only. No emails, usernames, or user ids in this query.
select
  (select count(*) from public.persons where lifecycle_status = 'ACTIVE') as active_persons,
  (select count(*) from public.organizations) as orgs,
  (select count(*) from public.pets where lifecycle_status = 'ACTIVE') as active_pets,
  (select count(*) from public.platform_admin_identities) as admin_identities,
  (select count(*) from public.platform_admin_identities where is_root) as roots,
  (select count(*) from public.pet_holders where status = 'ACTIVE') as active_holders,
  (select count(*) from public.vitacora_grants where revoked_at is null) as active_grants;
