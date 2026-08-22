-- =============================================================================
-- LeoVer / ComunidadApp
-- READ ONLY — preview de limpieza selectiva QA
--
-- DO NOT USE FOR FULL RESET
-- DO NOT RUN ON UNREVIEWED PROJECT
--
-- No borra nada. No es migración. No aplicar 082 desde acá.
-- Project ref esperado (fuera de SQL): wystsapjfpdtoprlmizz
-- =============================================================================

with qa as (
  select a.id
  from auth.users a
  where a.email ilike '%@test.local'
     or a.id::text like 'f0000000%'
),
real_u as (
  select a.id
  from auth.users a
  where not exists (select 1 from qa q where q.id = a.id)
),
qa_orgs as (
  select o.id
  from public.organizations o
  where o.id::text like 'a0000000%'
     or o.slug::text ilike 'm27%'
     or o.created_by in (select id from qa)
),
qa_pets as (
  select p.id
  from public.pets p
  where p.owner_id in (select id from qa)
),
real_pets as (
  select p.id
  from public.pets p
  where p.owner_id in (select id from real_u)
),
reports_qa as (
  select cr.id
  from public.content_reports cr
  where cr.reporter_id in (select id from qa)
    and (cr.reviewed_by is null or cr.reviewed_by in (select id from qa))
    and not exists (
      select 1 from real_u r
      where r.id = cr.reporter_id
         or r.id = cr.reviewed_by
         or r.id::text = cr.target_id
    )
),
reports_real as (
  select cr.id
  from public.content_reports cr
  where cr.reporter_id in (select id from real_u)
     or cr.reviewed_by in (select id from real_u)
     or exists (select 1 from real_u r where r.id::text = cr.target_id)
),
audit_blocked as (
  select distinct l.actor_user_id as id
  from public.administrative_audit_log l
  where l.actor_user_id in (select id from qa)
),
qa_admin as (
  select distinct ura.user_id as id
  from public.user_role_assignments ura
  join public.platform_roles r on r.id = ura.role_id
  where ura.revoked_at is null
    and r.code in ('ADMIN', 'SUPERADMIN')
    and ura.user_id in (select id from qa)
)
select
  (select count(*) from real_u) as real_users_preserved,
  (select count(*) from qa) as qa_users_targeted,
  (select count(*) from real_pets) as real_pets_preserved,
  (select count(*) from qa_pets) as qa_pets_targeted,
  (select count(*) from public.pet_passports pp
    where pp.pet_id in (select id from real_pets)) as passports_preserved,
  (select count(*) from public.pet_passports pp
    where pp.pet_id in (select id from qa_pets)) as qa_passports_targeted,
  (select count(*) from public.friend_connections fc
    where fc.requester_id in (select id from real_u)
       or fc.addressee_id in (select id from real_u)) as real_friend_links,
  (select count(*) from qa_orgs) as qa_orgs_targeted,
  (select count(*) from reports_qa) as qa_content_reports_targeted,
  (select count(*) from reports_real) as content_reports_touching_real,
  (select count(*) from public._m27_val_run)
    + (select count(*) from public._m27_smoke_run)
    + (select count(*) from public._m27_debug_last_err) as qa_m27_rows_targeted,
  (select count(*) from public.m27_api_contracts) as m27_contracts_preserved,
  (select count(*) from public.m27_rate_limit_quotas) as m27_quotas_preserved,
  (select count(*) from qa_admin) as qa_admin_users,
  (select count(*) from audit_blocked) as qa_auth_blocked_by_audit_fk,
  (select count(*) from public.user_role_assignments ura
    join public.platform_roles r on r.id = ura.role_id
    where ura.revoked_at is null
      and r.code in ('ADMIN', 'SUPERADMIN')
      and ura.user_id in (select id from real_u)) as real_admin_assignments,
  (
    (select count(*) from qa_pets p
      where exists (select 1 from real_pets r where r.id = p.id))
    + (select count(*) from reports_real)
    + (select count(*) from public.shelter_pet_placements spp
        where spp.pet_id in (select id from real_pets))
  ) as real_data_targeted_by_mistake;
