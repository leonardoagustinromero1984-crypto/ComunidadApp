create temporary table if not exists sec05_retest (
  k text primary key,
  expected text not null,
  actual text not null,
  pass boolean not null
);

do $$
declare
  a uuid;
  b uuid;
  admin_uid uuid;
  pet uuid;
  grant_id uuid;
  species text;
begin
  delete from sec05_retest;

  select p.user_id into a
    from public.persons p
   where p.lifecycle_status = 'ACTIVE'
     and not exists (select 1 from public.platform_admin_identities i where i.user_id = p.user_id)
   order by p.created_at
   limit 1;
  select p.user_id into b
    from public.persons p
   where p.lifecycle_status = 'ACTIVE'
     and p.user_id <> a
     and not exists (select 1 from public.platform_admin_identities i where i.user_id = p.user_id)
   order by p.created_at
   limit 1;
  select i.user_id into admin_uid
    from public.platform_admin_identities i
   where i.disabled_at is null and not i.is_root
   order by i.created_at
   limit 1;
  select s.code into species from public.species s where s.active order by s.sort_key limit 1;

  perform public.canon_as(a);
  pet := public.canon_create_pet('SEC05-FIXTURE-PET2', species);
  grant_id := public.canon_grant_vitacora(
    pet, 'PERSON', b, null, 'service', 'ESSENTIAL', timezone('utc', now()) + interval '1 hour'
  );

  perform public.canon_as(b);
  begin
    perform public.canon_revoke_vitacora(grant_id);
    insert into sec05_retest values ('responsible_revoke_grant', 'FORBIDDEN', 'ALLOWED', false);
  exception when others then
    insert into sec05_retest values (
      'responsible_revoke_grant', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
    );
  end;

  perform public.canon_as(b);
  begin
    perform public.canon_create_proposal(pet, 'OTHER', '{"note":"SEC05"}'::jsonb);
    insert into sec05_retest values ('grantee_create_proposal', 'ALLOWED', 'ALLOWED', true);
  exception when others then
    insert into sec05_retest values ('grantee_create_proposal', 'ALLOWED', sqlerrm, false);
  end;

  perform public.canon_as(admin_uid);
  begin
    perform public.list_moderation_queue(null, 10);
    insert into sec05_retest values ('admin_aal1_moderation', 'MFA_REQUIRED', 'ALLOWED', false);
  exception when others then
    insert into sec05_retest values (
      'admin_aal1_moderation', 'MFA_REQUIRED', sqlerrm, sqlerrm like '%MFA_REQUIRED%'
    );
  end;
  begin
    perform public.admin_search_users('sec05', 5);
    insert into sec05_retest values ('admin_aal1_search', 'MFA_REQUIRED', 'ALLOWED', false);
  exception when others then
    insert into sec05_retest values (
      'admin_aal1_search', 'MFA_REQUIRED', sqlerrm, sqlerrm like '%MFA_REQUIRED%'
    );
  end;
  begin
    perform public.get_admin_session();
    insert into sec05_retest values ('admin_aal1_session', 'MFA_REQUIRED', 'ALLOWED', false);
  exception when others then
    insert into sec05_retest values (
      'admin_aal1_session', 'MFA_REQUIRED', sqlerrm, sqlerrm like '%MFA_REQUIRED%'
    );
  end;

  delete from public.vitacora_update_proposals where pet_id = pet;
  delete from public.vitacora_access_grants where pet_id = pet;
  delete from public.pet_permission_grants where pet_id = pet;
  delete from public.pet_responsibility_events where pet_id = pet;
  delete from public.pet_lifecycle_events where pet_id = pet;
  delete from public.pet_responsibility_links where pet_id = pet;
  delete from public.vitacora_profiles where pet_id = pet;
  delete from public.pets where id = pet and name like 'SEC05-FIXTURE-%';
end $$;

select k, expected, actual, pass from sec05_retest order by 1;
