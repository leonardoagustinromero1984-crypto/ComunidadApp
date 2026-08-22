-- Staging live hardening checks for VitaCora numbering / RLS / idempotency.
-- Does not reset sequence. QA pets are deleted at the end.

drop table if exists public._qa_hardening_result;
create table public._qa_hardening_result (
  n1 bigint, n_forced bigint, n3 bigint, after_roll bigint,
  outsider text, pending text, noperm text, unver text, ok text, tamper text,
  admin_r text, mod_r text, created int, idem_err text
);

select public._import_parse_age_months('2 años') as age_2_years,
       public._import_parse_age_months('1 año') as age_1_year,
       public._import_parse_age_months('6 meses') as age_6_months,
       public._import_parse_age_months('1 año 6 meses') as age_18;

do $$
declare
  v_person uuid := 'caa259b6-1b4a-4494-9417-8998ad2ad5ba';
  v_other uuid := 'd9059eba-510c-43d3-807a-a07e202f4ef1';
  v_org uuid := '2e5b94ef-6049-4947-8f07-b749542a1c68';
  v_pet1 uuid; v_pet2 uuid; v_pet3 uuid; v_pet_roll uuid;
  v_n1 bigint; v_n2 bigint; v_n3 bigint; v_n_forced bigint; v_after_roll bigint;
  v_uuid uuid;
  v_job uuid;
  v_created int := 0;
  v_idem_err text := null;
  v_outsider text; v_pending text; v_noperm text; v_ok text; v_unver text; v_ver text;
  v_admin text; v_mod text; v_tamper text;
  v_role uuid; v_member_role uuid;
  v_loc text;
begin
  select id into v_loc from public.location_nodes where kind = 'LOCALITY' and active limit 1;

  insert into public.pets (created_by_user_id, name, species_code, lifecycle_status)
  values (v_person, 'QA_HARDENING_A', 'DOG', 'ACTIVE') returning id into v_pet1;
  insert into public.vitacora_profiles (pet_id) values (v_pet1);
  select pet_id, public_vitacora_number into v_uuid, v_n1 from public.vitacora_profiles where pet_id = v_pet1;
  if v_n1 is null or v_n1 < 1 then raise exception 'NUMBER_NOT_ASSIGNED'; end if;
  if v_uuid is distinct from v_pet1 then raise exception 'UUID_CHANGED'; end if;

  insert into public.pets (created_by_user_id, name, species_code, lifecycle_status)
  values (v_person, 'QA_HARDENING_B', 'DOG', 'ACTIVE') returning id into v_pet2;
  insert into public.vitacora_profiles (pet_id, public_vitacora_number) values (v_pet2, 999999);
  select public_vitacora_number into v_n_forced from public.vitacora_profiles where pet_id = v_pet2;
  if v_n_forced = 999999 then raise exception 'CLIENT_OVERRIDE_ACCEPTED'; end if;
  if v_n_forced = v_n1 then raise exception 'NUMBERS_COLLIDED'; end if;

  insert into public.pets (created_by_user_id, name, species_code, lifecycle_status)
  values (v_person, 'QA_HARDENING_C', 'DOG', 'ACTIVE') returning id into v_pet3;
  insert into public.vitacora_profiles (pet_id) values (v_pet3);
  select public_vitacora_number into v_n3 from public.vitacora_profiles where pet_id = v_pet3;
  if v_n3 in (v_n1, v_n_forced) then raise exception 'NOT_UNIQUE'; end if;

  begin
    alter table public.vitacora_profiles disable trigger vitacora_profiles_assign_public_number;
    insert into public.pets (created_by_user_id, name, species_code, lifecycle_status)
    values (v_person, 'QA_HARDENING_DUP', 'DOG', 'ACTIVE') returning id into v_pet_roll;
    begin
      insert into public.vitacora_profiles (pet_id, public_vitacora_number) values (v_pet_roll, v_n1);
      alter table public.vitacora_profiles enable trigger vitacora_profiles_assign_public_number;
      raise exception 'UNIQUE_NOT_ENFORCED';
    exception
      when unique_violation then
        alter table public.vitacora_profiles enable trigger vitacora_profiles_assign_public_number;
        delete from public.pets where id = v_pet_roll;
      when others then
        alter table public.vitacora_profiles enable trigger vitacora_profiles_assign_public_number;
        delete from public.pets where id = v_pet_roll;
        if sqlerrm like '%UNIQUE_NOT_ENFORCED%' then raise; end if;
        if sqlstate <> '23505' then raise; end if;
    end;
  end;

  begin
    insert into public.pets (created_by_user_id, name, species_code, lifecycle_status)
    values (v_person, 'QA_HARDENING_ROLL', 'DOG', 'ACTIVE') returning id into v_pet_roll;
    insert into public.vitacora_profiles (pet_id) values (v_pet_roll);
    raise exception 'QA_ROLLBACK';
  exception
    when others then
      if sqlerrm not like '%QA_ROLLBACK%' then raise; end if;
  end;
  if exists (select 1 from public.pets where id = v_pet_roll) then
    raise exception 'ROLLBACK_LEFT_PET';
  end if;

  insert into public.pets (created_by_user_id, name, species_code, lifecycle_status)
  values (v_person, 'QA_HARDENING_AFTER_ROLL', 'DOG', 'ACTIVE') returning id into v_pet_roll;
  insert into public.vitacora_profiles (pet_id) values (v_pet_roll);
  select public_vitacora_number into v_after_roll from public.vitacora_profiles where pet_id = v_pet_roll;
  if v_after_roll <= v_n3 then raise exception 'SEQUENCE_REUSED'; end if;

  perform set_config('request.jwt.claim.sub', v_other::text, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_other, 'role', 'authenticated')::text, true);
  begin
    perform public.canon_import_create_job(v_org, 'SELF_SERVICE', v_org::text || '/qa.xlsx', null, 'outsider');
    v_outsider := 'ALLOWED';
  exception when others then v_outsider := 'DENIED';
  end;

  select id into v_role from public.organization_roles where organization_id = v_org and code = 'OWNER' limit 1;
  insert into public.organization_roles (organization_id, code, name, is_system)
  values (v_org, 'QA_NO_IMPORT', 'QA no import', false)
  on conflict do nothing;
  select id into v_member_role from public.organization_roles where organization_id = v_org and code = 'QA_NO_IMPORT';

  delete from public.organization_memberships
   where organization_id = v_org and person_id = v_other;
  insert into public.organization_memberships (organization_id, person_id, role_id, status)
  values (v_org, v_other, v_member_role, 'INVITED');

  begin
    perform public.canon_import_create_job(v_org, 'SELF_SERVICE', v_org::text || '/qa.xlsx', null, 'pending');
    v_pending := 'ALLOWED';
  exception when others then v_pending := 'DENIED';
  end;

  update public.organization_memberships set status = 'ACTIVE' where organization_id = v_org and person_id = v_other;
  update public.organizations set verification_status = 'VERIFIED' where id = v_org;
  begin
    perform public.canon_import_create_job(v_org, 'SELF_SERVICE', v_org::text || '/qa.xlsx', null, 'noperm');
    v_noperm := 'ALLOWED';
  exception when others then v_noperm := 'DENIED';
  end;

  perform set_config('request.jwt.claim.sub', v_person::text, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_person, 'role', 'authenticated')::text, true);
  update public.organizations set verification_status = 'NOT_REQUESTED' where id = v_org;
  begin
    perform public.canon_import_create_job(v_org, 'SELF_SERVICE', v_org::text || '/qa.xlsx', null, 'unver');
    v_unver := 'ALLOWED';
  exception when others then v_unver := 'DENIED';
  end;

  update public.organizations set verification_status = 'VERIFIED' where id = v_org;
  begin
    perform public.canon_import_create_job(v_org, 'SELF_SERVICE', v_org::text || '/qa-ok.xlsx', null, 'ok');
    v_ok := 'ALLOWED';
  exception when others then v_ok := 'DENIED';
  end;

  begin
    perform public.canon_import_create_job('00000000-0000-0000-0000-000000000099', 'SELF_SERVICE', '00000000-0000-0000-0000-000000000099/qa.xlsx', null, 'tamper');
    v_tamper := 'ALLOWED';
  exception when others then v_tamper := 'DENIED';
  end;

  insert into public.user_platform_role_assignments (user_id, role_code, granted_by)
  values (v_person, 'ADMIN', v_person)
  on conflict (user_id, role_code) do update set revoked_at = null;
  begin
    perform public.canon_import_create_job(v_org, 'ADMIN', v_org::text || '/qa-admin.xlsx', null, 'admin', 'ORGANIZATION', null);
    v_admin := 'ALLOWED';
  exception when others then v_admin := sqlerrm;
  end;

  update public.user_platform_role_assignments set revoked_at = timezone('utc', now())
    where user_id = v_person and role_code = 'ADMIN';
  insert into public.user_platform_role_assignments (user_id, role_code, granted_by)
  values (v_person, 'MODERATOR', v_person)
  on conflict (user_id, role_code) do update set revoked_at = null;
  begin
    perform public.canon_import_create_job(v_org, 'ADMIN', v_org::text || '/qa-mod.xlsx', null, 'mod', 'ORGANIZATION', null);
    v_mod := 'ALLOWED';
  exception when others then v_mod := sqlerrm;
  end;
  update public.user_platform_role_assignments set revoked_at = timezone('utc', now())
    where user_id = v_person and role_code = 'MODERATOR';

  -- Idempotent confirm: insert a READY job with one LISTA row already created path
  insert into public.vitacora_import_jobs (
    organization_id, import_scope, mode, status, initiated_by, executed_by, storage_path, valid_rows, total_rows
  ) values (
    v_org, 'ORGANIZATION', 'SELF_SERVICE', 'READY', v_person, v_person, v_org::text || '/qa-idemp.xlsx', 1, 1
  ) returning id into v_job;
  insert into public.vitacora_import_rows (
    import_id, row_number, external_pet_id, external_pet_id_normalized, pet_name, status, payload
  ) values (
    v_job, 2, 'QA-IDEM-001', 'qa-idem-001', 'QA_IDEM', 'LISTA',
    jsonb_build_object('species','Perro','sex','Hembra','status','Activo','locality_id', v_loc, 'lifecycle_status','ACTIVE')
  );
  begin
    perform public.canon_import_confirm(v_job);
    perform public.canon_import_confirm(v_job);
    select count(*) into v_created from public.pets where created_from_import_id = v_job;
  exception when others then
    v_idem_err := sqlerrm;
    v_created := -1;
  end;

  insert into public._qa_hardening_result(n1, n_forced, n3, after_roll, outsider, pending, noperm, unver, ok, tamper, admin_r, mod_r, created, idem_err)
  values (v_n1, v_n_forced, v_n3, v_after_roll, v_outsider, v_pending, v_noperm, v_unver, v_ok, v_tamper, v_admin, v_mod, v_created, v_idem_err);
end;
$$;

update public.pets set created_from_import_id = null
 where name like 'QA_HARDENING_%' or name = 'QA_IDEM';
delete from public.vitacora_import_rows
 where import_id in (
   select id from public.vitacora_import_jobs
   where coalesce(comment,'') in ('ok','admin','mod','outsider','pending','noperm','unver','tamper')
      or storage_path like '%qa-idemp%'
      or storage_path like '%/qa-ok%'
      or storage_path like '%/qa-admin%'
      or storage_path like '%/qa-mod%'
      or storage_path like '%/qa.xlsx'
 );
delete from public.vitacora_import_jobs
 where coalesce(comment,'') in ('ok','admin','mod','outsider','pending','noperm','unver','tamper')
    or storage_path like '%qa-idemp%'
    or storage_path like '%/qa-ok%'
    or storage_path like '%/qa-admin%'
    or storage_path like '%/qa-mod%'
    or storage_path like '%/qa.xlsx';
delete from public.pet_lifecycle_events
 where pet_id in (select id from public.pets where name like 'QA_HARDENING_%' or name = 'QA_IDEM');
delete from public.pet_permission_grants
 where pet_id in (select id from public.pets where name like 'QA_HARDENING_%' or name = 'QA_IDEM');
delete from public.pet_responsibility_links
 where pet_id in (select id from public.pets where name like 'QA_HARDENING_%' or name = 'QA_IDEM');
delete from public.pet_org_external_ids
 where pet_id in (select id from public.pets where name like 'QA_HARDENING_%' or name = 'QA_IDEM');
delete from public.pet_rescuer_external_ids
 where pet_id in (select id from public.pets where name like 'QA_HARDENING_%' or name = 'QA_IDEM');
delete from public.pet_declared_health
 where pet_id in (select id from public.pets where name like 'QA_HARDENING_%' or name = 'QA_IDEM');
delete from public.vitacora_profiles
 where pet_id in (select id from public.pets where name like 'QA_HARDENING_%' or name = 'QA_IDEM');
delete from public.pets where name like 'QA_HARDENING_%' or name = 'QA_IDEM';
delete from public.organization_memberships
 where organization_id = '2e5b94ef-6049-4947-8f07-b749542a1c68'
   and person_id = 'd9059eba-510c-43d3-807a-a07e202f4ef1';
delete from public.organization_roles
 where organization_id = '2e5b94ef-6049-4947-8f07-b749542a1c68' and code = 'QA_NO_IMPORT';
update public.organizations set verification_status = 'NOT_REQUESTED'
 where id = '2e5b94ef-6049-4947-8f07-b749542a1c68';
update public.user_platform_role_assignments set revoked_at = timezone('utc', now())
 where user_id = 'caa259b6-1b4a-4494-9417-8998ad2ad5ba' and role_code in ('ADMIN','MODERATOR');

select r.*,
       s.last_value as seq_last,
       s.is_called as seq_called,
       public._import_parse_age_months('2 años') as age_2_years,
       public._import_parse_age_months('1 año') as age_1_year,
       public._import_parse_age_months('6 meses') as age_6_months,
       public._import_parse_age_months('1 año 6 meses') as age_18
from public._qa_hardening_result r
cross join public.vitacora_public_number_seq s;
drop table if exists public._qa_hardening_result;
