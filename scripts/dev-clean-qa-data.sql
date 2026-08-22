-- =============================================================================
-- LeoVer / ComunidadApp
-- DESTRUCTIVE QA DATA ONLY
--
-- DO NOT USE FOR FULL RESET
-- DO NOT RUN ON UNREVIEWED PROJECT
--
-- Limpieza selectiva de seed QA en el backend compartido.
-- NO es migración. NO aplica 082. NO ejecuta scripts/dev-reset-data.sql.
-- NO borra security_events.
-- administrative_audit_log: solo filas cuyo actor es QA (necesario para
-- borrar esos auth.users; hoy las 11 filas son 100% QA). No se reescribe
-- historial de actores reales.
-- NO toca schema_migrations ni catálogos canónicos.
-- REAL_ADMIN_USER_ID (GUC) está en el set inmutable: no se borra ni altera.
--
-- Project ref esperado: wystsapjfpdtoprlmizz
--
-- Identificación QA (determinista, combinada):
--   auth.users.email ILIKE '%@test.local'
--   OR auth.users.id::text LIKE 'f0000000%'
--   orgs: id a0000000-* OR slug m27* OR created_by QA
--   pets: owner_id IN qa set
--
-- Procedimiento (misma sesión SQL):
--   1) Correr scripts/dev-clean-qa-preview.sql
--   2) Confirmar REAL_DATA_TARGETED_BY_MISTAKE = 0
--   3) set_config project_ref + confirm + execute
--   4) Para borrar ADMIN QA / auth QA: set_config real_admin_user_id
--
-- select set_config('leover.qa_clean.project_ref', 'wystsapjfpdtoprlmizz', false);
-- select set_config('leover.qa_clean.confirm', 'LEOVER-QA-CLEAN-CONFIRM', false);
-- select set_config('leover.qa_clean.execute', 'YES', false);
-- select set_config('leover.qa_clean.scope', 'DOMAIN', false);
-- -- FULL requiere administrador real (UUID, no versionar):
-- -- select set_config('leover.qa_clean.real_admin_user_id', '<UUID>', false);
-- -- select set_config('leover.qa_clean.scope', 'FULL', false);
-- =============================================================================

do $$
declare
  expected_ref constant text := 'wystsapjfpdtoprlmizz';
  given_ref text := current_setting('leover.qa_clean.project_ref', true);
  confirm text := current_setting('leover.qa_clean.confirm', true);
  execute_flag text := current_setting('leover.qa_clean.execute', true);
  scope text := upper(coalesce(nullif(current_setting('leover.qa_clean.scope', true), ''), 'DOMAIN'));
  real_admin uuid := nullif(current_setting('leover.qa_clean.real_admin_user_id', true), '')::uuid;
begin
  if given_ref is distinct from expected_ref then
    raise exception using
      message = 'LEOVER QA CLEAN BLOCKED',
      detail = 'project_ref ausente o distinto del esperado. '
               || 'Este script solo puede correr contra el backend confirmado.',
      hint = 'set_config leover.qa_clean.project_ref = wystsapjfpdtoprlmizz. NO se borró nada.';
  end if;

  if confirm is distinct from 'LEOVER-QA-CLEAN-CONFIRM'
     or execute_flag is distinct from 'YES' then
    raise exception using
      message = 'LEOVER QA CLEAN BLOCKED',
      detail = 'Falta confirmación explícita. Primero scripts/dev-clean-qa-preview.sql.',
      hint = 'NO se borró nada.';
  end if;

  if scope not in ('DOMAIN', 'FULL') then
    raise exception 'leover.qa_clean.scope debe ser DOMAIN o FULL';
  end if;

  if scope = 'FULL' and real_admin is null then
    raise exception using
      message = 'ADMIN_BOOTSTRAP = BLOCKED',
      detail = 'FULL requiere leover.qa_clean.real_admin_user_id. QA_ADMIN_DELETE = NO.',
      hint = 'Usá scope=DOMAIN para orgs/pet/M27/reports/storage, o proveé el UUID real.';
  end if;
end $$;

begin;

create temporary table _qa_users (
  id uuid primary key
) on commit drop;

insert into _qa_users (id)
select a.id
from auth.users a
where a.email ilike '%@test.local'
   or a.id::text like 'f0000000%';

create temporary table _real_users (
  id uuid primary key
) on commit drop;

insert into _real_users (id)
select a.id
from auth.users a
where not exists (select 1 from _qa_users q where q.id = a.id);

-- Inmutable: la cuenta SUPERADMIN real nunca entra al set QA.
delete from _qa_users q
where q.id = nullif(current_setting('leover.qa_clean.real_admin_user_id', true), '')::uuid
   or q.id in (select id from _real_users);

do $$
declare
  real_admin uuid := nullif(current_setting('leover.qa_clean.real_admin_user_id', true), '')::uuid;
begin
  if real_admin is not null then
    if not exists (select 1 from _real_users r where r.id = real_admin) then
      raise exception 'REAL_ADMIN_USER_ID no está en el set real. ABORT.';
    end if;
    if exists (select 1 from _qa_users q where q.id = real_admin) then
      raise exception 'REAL_ADMIN_USER_ID cayó en el set QA. ABORT.';
    end if;
  end if;
end $$;

create temporary table _qa_orgs (
  id uuid primary key
) on commit drop;

insert into _qa_orgs (id)
select o.id
from public.organizations o
where o.id::text like 'a0000000%'
   or o.slug::text ilike 'm27%'
   or o.created_by in (select id from _qa_users);

create temporary table _qa_pets (
  id uuid primary key
) on commit drop;

insert into _qa_pets (id)
select p.id
from public.pets p
where p.owner_id in (select id from _qa_users);

create temporary table _real_pets (
  id uuid primary key
) on commit drop;

insert into _real_pets (id)
select p.id
from public.pets p
where p.owner_id in (select id from _real_users);

create temporary table _qa_reports (
  id uuid primary key
) on commit drop;

insert into _qa_reports (id)
select cr.id
from public.content_reports cr
where cr.reporter_id in (select id from _qa_users)
  and (cr.reviewed_by is null or cr.reviewed_by in (select id from _qa_users))
  and not exists (
    select 1 from _real_users r
    where r.id = cr.reporter_id
       or r.id = cr.reviewed_by
       or r.id::text = cr.target_id
  );

create temporary table _orphan_objects (
  bucket_id text not null,
  name text not null,
  primary key (bucket_id, name)
) on commit drop;

insert into _orphan_objects (bucket_id, name)
select o.bucket_id, o.name
from storage.objects o
where o.bucket_id = 'leover'
  and not exists (
    select 1 from auth.users a
    where a.id::text = split_part(o.name, '/', 2)
  )
  and not exists (
    select 1 from public.pets p
    where coalesce(p.photo_url, '') ilike '%' || o.name || '%'
       or coalesce(p.photo_url, '') ilike '%' || split_part(o.name, '/', 2) || '%'
  )
  and not exists (
    select 1 from public.users u
    where coalesce(u.avatar_path, '') ilike '%' || o.name || '%'
       or coalesce(u.avatar_path, '') ilike '%' || split_part(o.name, '/', 2) || '%'
  )
  and not exists (
    select 1 from public.organizations org
    where coalesce(org.logo_path, '') ilike '%' || o.name || '%'
       or coalesce(org.cover_path, '') ilike '%' || o.name || '%'
  )
  and not exists (
    select 1 from public.posts p
    where p.id::text = split_part(o.name, '/', 2)
       or coalesce(p.image_url, '') ilike '%' || o.name || '%'
  )
  and not exists (
    select 1 from public.lost_found_posts lf
    where coalesce(lf.photo_url, '') ilike '%' || o.name || '%'
  )
  and not exists (
    select 1 from public.adoptions ad
    where coalesce(ad.photo_url, '') ilike '%' || o.name || '%'
  )
  and not exists (
    select 1 from public.file_asset_versions v
    where coalesce(v.storage_path, '') ilike '%' || o.name || '%'
  )
  and not exists (
    select 1 from public.m19_social_posts s
    where coalesce(s.cover_image_ref, '') ilike '%' || o.name || '%'
  );

do $$
declare
  mistake int;
  real_in_qa int;
begin
  select count(*) into real_in_qa
  from _qa_users q
  join _real_users r on r.id = q.id;

  select
    (select count(*) from _qa_pets p join _real_pets r on r.id = p.id)
    + (select count(*) from public.content_reports cr
        where cr.id in (select id from _qa_reports)
          and (
            cr.reporter_id in (select id from _real_users)
            or cr.reviewed_by in (select id from _real_users)
            or exists (select 1 from _real_users r where r.id::text = cr.target_id)
          ))
    + (select count(*) from public.shelter_pet_placements spp
        where spp.pet_id in (select id from _real_pets)
          and spp.source_organization_id in (select id from _qa_orgs))
  into mistake;

  if real_in_qa > 0 or mistake > 0 then
    raise exception using
      message = 'LEOVER QA CLEAN ABORT',
      detail = format('REAL_DATA_TARGETED_BY_MISTAKE=%s real_in_qa_set=%s', mistake, real_in_qa),
      hint = 'NO se borró nada.';
  end if;

  if exists (
    select 1 from public.pet_passports pp
    where pp.pet_id in (select id from _real_pets)
      and pp.pet_id in (select id from _qa_pets)
  ) then
    raise exception 'LEOVER QA CLEAN ABORT: passport real en set QA';
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- DOMAIN: orgs QA, pet QA, leftovers M27, reports QA, storage huérfano
-- ---------------------------------------------------------------------------

delete from public.organization_memberships
where organization_id in (select id from _qa_orgs);

delete from public.organization_invitations
where organization_id in (select id from _qa_orgs);

delete from public.organization_branches
where organization_id in (select id from _qa_orgs);

delete from public.organization_resource_links
where organization_id in (select id from _qa_orgs);

delete from public.organizations
where id in (select id from _qa_orgs);

delete from public.pet_responsibilities
where pet_id in (select id from _qa_pets);

delete from public.pet_status_history
where pet_id in (select id from _qa_pets);

delete from public.pet_clinical_records
where pet_id in (select id from _qa_pets);

delete from public.pets
where id in (select id from _qa_pets)
  and owner_id in (select id from _qa_users)
  and id not in (select id from _real_pets);

delete from public.content_reports
where id in (select id from _qa_reports);

delete from public._m27_val_run;
delete from public._m27_smoke_run;
delete from public._m27_debug_last_err;
delete from public._m27_val_last_failures;
delete from public._m27_smoke_last_failures;

-- Storage: no DELETE en storage.objects (trigger protect_delete).
-- Los huérfanos se listan en _orphan_objects y se borran con
-- `supabase storage rm ss:///leover/<name>` después de este script.

-- ---------------------------------------------------------------------------
-- FULL: bootstrap admin real, después auth QA (excepto audit FK + hasta verificar)
-- ---------------------------------------------------------------------------

do $$
declare
  scope text := upper(coalesce(nullif(current_setting('leover.qa_clean.scope', true), ''), 'DOMAIN'));
  real_admin uuid := nullif(current_setting('leover.qa_clean.real_admin_user_id', true), '')::uuid;
  has_role boolean;
  has_perm boolean;
  real_admins int;
begin
  if scope <> 'FULL' then
    raise notice 'ADMIN_BOOTSTRAP = BLOCKED; QA_ADMIN_DELETE = NO; scope=DOMAIN';
    return;
  end if;

  if real_admin is null or not exists (select 1 from _real_users r where r.id = real_admin) then
    raise exception using
      message = 'ADMIN_BOOTSTRAP = BLOCKED',
      detail = 'REAL_ADMIN_USER_ID ausente o no es una de las 4 cuentas reales.',
      hint = 'QA_ADMIN_DELETE = NO. Rollback.';
  end if;

  perform public.ensure_default_user_role(real_admin);

  insert into public.user_role_assignments (user_id, role_id, assigned_by)
  select real_admin, r.id, real_admin
  from public.platform_roles r
  where r.code = 'SUPERADMIN'
    and not exists (
      select 1 from public.user_role_assignments a
      where a.user_id = real_admin
        and a.role_id = r.id
        and a.revoked_at is null
    );

  insert into public.role_assignment_history (
    user_id, role_code, action, previous_state, new_state,
    reason_code, changed_by
  )
  select
    real_admin,
    'SUPERADMIN',
    'ASSIGN',
    null,
    'ACTIVE',
    'manual_admin',
    real_admin
  where not exists (
    select 1 from public.role_assignment_history h
    where h.user_id = real_admin
      and h.role_code = 'SUPERADMIN'
      and h.action = 'ASSIGN'
      and h.reason_code = 'manual_admin'
  );

  if exists (
    select 1 from public.users u
    where u.id = real_admin and coalesce(u.account_type, 'PERSON') <> 'PERSON'
  ) then
    raise exception 'REAL_ADMIN_ACCOUNT_TYPE must remain PERSON. ABORT.';
  end if;

  select public.user_has_active_role(real_admin, 'SUPERADMIN') into has_role;

  select (
    select count(distinct p.code)
    from public.user_role_assignments a
    join public.role_permissions rp on rp.role_id = a.role_id
    join public.permissions p on p.id = rp.permission_id
    where a.user_id = real_admin
      and a.revoked_at is null
      and p.code in (
        'roles.assign',
        'roles.revoke',
        'users.change_status',
        'audit.view',
        'moderation.manage_reports'
      )
  ) = 5 into has_perm;

  select count(distinct ura.user_id) into real_admins
  from public.user_role_assignments ura
  join public.platform_roles r on r.id = ura.role_id
  where ura.revoked_at is null
    and r.code in ('ADMIN', 'SUPERADMIN')
    and ura.user_id in (select id from _real_users);

  if not has_role or not has_perm or real_admins < 1 then
    raise exception using
      message = 'ADMIN_BOOTSTRAP VERIFY FAILED',
      detail = format('user_has_active_role=%s required_perms=%s real_admins=%s',
                      has_role, has_perm, real_admins),
      hint = 'QA_ADMIN_DELETE = NO. Rollback.';
  end if;
end $$;

create temporary table _qa_auth_deletable (
  id uuid primary key
) on commit drop;

insert into _qa_auth_deletable (id)
select q.id
from _qa_users q
where q.id not in (select id from _real_users)
  and q.id is distinct from nullif(current_setting('leover.qa_clean.real_admin_user_id', true), '')::uuid
  and (
    upper(coalesce(nullif(current_setting('leover.qa_clean.scope', true), ''), 'DOMAIN')) = 'FULL'
    or not exists (
      select 1
      from public.user_role_assignments ura
      join public.platform_roles r on r.id = ura.role_id
      where ura.user_id = q.id
        and ura.revoked_at is null
        and r.code in ('ADMIN', 'SUPERADMIN')
    )
  );

-- DOMAIN nunca borra auth. FULL borra los 28 QA, nunca la cuenta real.
delete from _qa_auth_deletable d
where upper(coalesce(nullif(current_setting('leover.qa_clean.scope', true), ''), 'DOMAIN')) <> 'FULL';

do $$
declare
  real_admin uuid := nullif(current_setting('leover.qa_clean.real_admin_user_id', true), '')::uuid;
begin
  if exists (
    select 1 from _qa_auth_deletable d
    where d.id = real_admin
       or d.id in (select id from _real_users)
  ) then
    raise exception 'LEOVER QA CLEAN ABORT: set deletable incluye cuenta real.';
  end if;
end $$;

-- Audit QA-only: las filas actuales tienen actor QA. security_events no se toca.
delete from public.administrative_audit_log
where actor_user_id in (select id from _qa_auth_deletable)
  and actor_user_id not in (select id from _real_users);

delete from public.user_status_history
where user_id in (select id from _qa_auth_deletable)
   or changed_by in (select id from _qa_auth_deletable);

update public.user_role_assignments
set assigned_by = null
where assigned_by in (select id from _qa_auth_deletable);

update public.user_role_assignments
set revoked_by = null
where revoked_by in (select id from _qa_auth_deletable);

delete from public.role_assignment_history
where user_id in (select id from _qa_auth_deletable)
   or changed_by in (select id from _qa_auth_deletable);

delete from public.user_role_assignments
where user_id in (select id from _qa_auth_deletable);

delete from public.user_privacy_settings
where user_id in (select id from _qa_auth_deletable);

delete from auth.users
where id in (select id from _qa_auth_deletable)
  and id not in (select id from _real_users)
  and id is distinct from nullif(current_setting('leover.qa_clean.real_admin_user_id', true), '')::uuid;

do $$
declare
  auth_orphan int;
  public_orphan int;
begin
  select count(*) into auth_orphan
  from auth.users a
  where not exists (select 1 from public.users u where u.id = a.id);

  select count(*) into public_orphan
  from public.users u
  where not exists (select 1 from auth.users a where a.id = u.id);

  if auth_orphan > 0 or public_orphan > 0 then
    raise exception using
      message = 'LEOVER QA CLEAN INTEGRITY FAIL',
      detail = format('AUTH_WITHOUT_PUBLIC_PROFILE=%s PUBLIC_WITHOUT_AUTH=%s',
                      auth_orphan, public_orphan),
      hint = 'Rollback. No se dejó el commit inconsistente.';
  end if;
end $$;

commit;

select
  (select count(*) from auth.users) as auth_users_remaining,
  (select count(*) from public.users) as public_users_remaining,
  (select count(*) from public.pets) as pets_remaining,
  (select count(*) from public.pet_passports) as passports_remaining,
  (select count(*) from public.organizations) as organizations_remaining,
  (select count(*) from public.content_reports) as content_reports_remaining,
  (select count(*) from public._m27_val_run) as m27_val_run_remaining,
  (select count(*) from public._m27_smoke_run) as m27_smoke_run_remaining,
  (select count(*) from public.m27_api_contracts) as m27_contracts_remaining,
  (select count(*) from storage.objects) as storage_objects_remaining,
  (select count(*) from public.security_events) as security_events_remaining,
  (select count(*) from public.administrative_audit_log) as audit_log_remaining,
  (
    select count(*) from auth.users a
    where not exists (select 1 from public.users u where u.id = a.id)
  ) as auth_without_public_profile,
  (
    select count(*) from public.users u
    where not exists (select 1 from auth.users a where a.id = u.id)
  ) as public_without_auth;
