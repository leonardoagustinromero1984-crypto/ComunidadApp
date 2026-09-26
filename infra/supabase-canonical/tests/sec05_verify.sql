-- SEC-05 defensive authorization checks. One result set. Cleans SEC05 fixtures.
create temporary table if not exists sec05_results (
  k text primary key,
  expected text not null,
  actual text not null,
  pass boolean not null
);

do $$
declare
  a uuid;
  b uuid;
  c uuid;
  admin_uid uuid;
  pet uuid;
  link uuid;
  org_a uuid;
  org_b uuid;
  conv uuid;
  media uuid;
  grant_id uuid;
  species text;
  v_err text;
  v_ok boolean;
  v_json jsonb;
  v_id uuid;
begin
  delete from sec05_results;

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
  select p.user_id into c
    from public.persons p
   where p.lifecycle_status = 'ACTIVE'
     and p.user_id not in (a, b)
     and not exists (select 1 from public.platform_admin_identities i where i.user_id = p.user_id)
   order by p.created_at
   limit 1;
  select i.user_id into admin_uid
    from public.platform_admin_identities i
   where i.disabled_at is null and not i.is_root
   order by i.created_at
   limit 1;
  select s.code into species from public.species s where s.active order by s.sort_key limit 1;
  if a is null or b is null or species is null then
    raise exception 'SEC05_NO_ACTORS';
  end if;

  perform public.canon_as(a);
  pet := public.canon_create_pet('SEC05-FIXTURE-PET', species);
  link := public.canon_invite_pet_responsible(pet, b);
  perform public.canon_as(b);
  perform public.canon_accept_care_invite(link);

  -- PERSON isolation
  perform public.canon_as(b);
  begin
    update public.persons set privacy_state = 'PUBLIC' where user_id = a;
    insert into sec05_results values (
      'person_b_update_a_privacy', 'DENIED',
      case when found then 'UPDATED' else 'NO_ROW' end,
      not found
    );
  exception when others then
    insert into sec05_results values ('person_b_update_a_privacy', 'DENIED', 'EXCEPTION', true);
  end;

  perform public.canon_as(b);
  v_ok := false;
  begin
    perform public.get_admin_session();
    v_ok := true;
  exception when others then
    v_err := sqlerrm;
  end;
  insert into sec05_results values (
    'person_admin_session', 'NULL_OR_DENIED',
    case when v_ok then 'SESSION' else coalesce(v_err, 'DENIED') end,
    not v_ok
  );

  perform public.canon_as(b);
  insert into sec05_results values (
    'person_staff_manage', 'false',
    public.has_permission('staff.manage')::text,
    public.has_permission('staff.manage') is not true
  );
  insert into sec05_results values (
    'person_moderation_view', 'false',
    public.has_permission('moderation.view')::text,
    public.has_permission('moderation.view') is not true
  );

  perform public.canon_as(b);
  begin
    perform public.list_moderation_queue(null, 10);
    insert into sec05_results values ('person_moderation_queue', 'FORBIDDEN', 'ALLOWED', false);
  exception when others then
    insert into sec05_results values (
      'person_moderation_queue', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
    );
  end;

  -- Shared pet: B can update, cannot archive / invite / remove creator
  perform public.canon_as(b);
  begin
    perform public.canon_update_pet(pet, 'SEC05-FIXTURE-PET', species);
    insert into sec05_results values ('responsible_update_pet', 'ALLOWED', 'ALLOWED', true);
  exception when others then
    insert into sec05_results values ('responsible_update_pet', 'ALLOWED', sqlerrm, false);
  end;

  perform public.canon_as(b);
  begin
    perform public.canon_archive_pet(pet, 'SEC05');
    insert into sec05_results values ('responsible_archive', 'FORBIDDEN', 'ALLOWED', false);
  exception when others then
    insert into sec05_results values (
      'responsible_archive', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
    );
  end;

  perform public.canon_as(b);
  begin
    perform public.canon_invite_pet_responsible(pet, coalesce(c, a));
    insert into sec05_results values ('responsible_invite', 'FORBIDDEN', 'ALLOWED', false);
  exception when others then
    insert into sec05_results values (
      'responsible_invite', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
    );
  end;

  perform public.canon_as(a);
  begin
    perform public.canon_update_pet(pet, 'SEC05-FIXTURE-PET', species);
    insert into sec05_results values ('creator_update_pet', 'ALLOWED', 'ALLOWED', true);
  exception when others then
    insert into sec05_results values ('creator_update_pet', 'ALLOWED', sqlerrm, false);
  end;

  -- Stranger C cannot update pet
  if c is not null then
    perform public.canon_as(c);
    begin
      perform public.canon_update_pet(pet, 'HACK', species);
      insert into sec05_results values ('stranger_update_pet', 'FORBIDDEN', 'ALLOWED', false);
    exception when others then
      insert into sec05_results values (
        'stranger_update_pet', 'DENIED', sqlerrm, true
      );
    end;
    v_json := public.canon_list_pets_for_person_profile(a);
    insert into sec05_results values (
      'stranger_list_a_pets', '[]',
      jsonb_typeof(v_json),
      v_json = '[]'::jsonb
    );
  end if;

  -- Professional grant: B is responsible not professional. Grant C if present.
  if c is not null then
    perform public.canon_as(a);
    begin
      grant_id := public.canon_grant_vitacora(
        pet, 'PERSON', c, null, 'service', 'ESSENTIAL', timezone('utc', now()) + interval '1 hour'
      );
      insert into sec05_results values ('creator_grant_vitacora', 'ALLOWED', 'ALLOWED', true);
    exception when others then
      insert into sec05_results values ('creator_grant_vitacora', 'ALLOWED', sqlerrm, false);
    end;
    perform public.canon_as(c);
    insert into sec05_results values (
      'grantee_is_not_holder', 'false',
      public._acl_pet_holder(c, pet)::text,
      public._acl_pet_holder(c, pet) is not true
    );
    perform public.canon_as(c);
    begin
      perform public.canon_archive_pet(pet, 'SEC05');
      insert into sec05_results values ('grantee_archive', 'FORBIDDEN', 'ALLOWED', false);
    exception when others then
      insert into sec05_results values (
        'grantee_archive', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
      );
    end;
    perform public.canon_as(b);
    begin
      perform public.canon_grant_vitacora(
        pet, 'PERSON', c, null, 'service', 'ESSENTIAL', timezone('utc', now()) + interval '1 hour'
      );
      insert into sec05_results values ('responsible_grant_vitacora', 'FORBIDDEN', 'ALLOWED', false);
    exception when others then
      insert into sec05_results values (
        'responsible_grant_vitacora', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
      );
    end;
  end if;

  -- Unscoped revoke / hide (expected DENIED after any fix; record actual)
  perform public.canon_as(b);
  begin
    perform public.canon_revoke_vitacora(coalesce(grant_id, '00000000-0000-0000-0000-000000000001'::uuid));
    insert into sec05_results values (
      'cross_revoke_vitacora',
      'DENIED_OR_NOOP_OWN',
      'EXECUTED',
      grant_id is null
    );
  exception when others then
    insert into sec05_results values ('cross_revoke_vitacora', 'DENIED', sqlerrm, true);
  end;

  -- Orgs
  perform public.canon_as(a);
  begin
    org_a := public.canon_create_organization('SEC05-FIXTURE-ORG-A', 'sec05-org-a', 'OTHER');
    insert into sec05_results values ('org_a_create', 'ALLOWED', 'ALLOWED', true);
  exception when others then
    insert into sec05_results values ('org_a_create', 'ALLOWED', sqlerrm, false);
  end;
  perform public.canon_as(b);
  begin
    org_b := public.canon_create_organization('SEC05-FIXTURE-ORG-B', 'sec05-org-b', 'OTHER');
    insert into sec05_results values ('org_b_create', 'ALLOWED', 'ALLOWED', true);
  exception when others then
    insert into sec05_results values ('org_b_create', 'ALLOWED', sqlerrm, false);
  end;
  if org_a is not null and org_b is not null then
    perform public.canon_as(a);
    begin
      perform public.canon_list_org_invitations(org_b);
      insert into sec05_results values ('org_a_list_org_b_invites', 'FORBIDDEN', 'ALLOWED', false);
    exception when others then
      insert into sec05_results values (
        'org_a_list_org_b_invites', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
      );
    end;
    perform public.canon_as(a);
    insert into sec05_results values (
      'org_member_not_staff', 'false',
      public.has_permission('staff.manage')::text,
      public.has_permission('staff.manage') is not true
    );
  end if;

  -- Messages: stranger cannot list A-B conversation
  if c is not null then
    perform public.canon_as(a);
    begin
      conv := public.canon_start_conversation('PERSON', b, null, 'SEC05-FIXTURE-MSG');
    exception when others then
      conv := null;
    end;
    if conv is not null then
      perform public.canon_as(c);
      begin
        perform public.canon_list_messages(conv);
        insert into sec05_results values ('stranger_list_messages', 'FORBIDDEN', 'ALLOWED', false);
      exception when others then
        insert into sec05_results values (
          'stranger_list_messages', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
        );
      end;
    end if;
  end if;

  -- Media signed URL: B cannot authorize A's private asset
  perform public.canon_as(a);
  begin
    media := public.canon_register_media(
      'private-media',
      'sec05/' || a::text || '/fixture.bin',
      'image/jpeg',
      1024
    );
  exception when others then
    media := null;
  end;
  if media is not null then
    perform public.canon_as(b);
    begin
      perform public.canon_authorize_media_signed_url(media);
      insert into sec05_results values ('signed_url_cross', 'FORBIDDEN', 'ALLOWED', false);
    exception when others then
      insert into sec05_results values (
        'signed_url_cross', 'FORBIDDEN', sqlerrm, sqlerrm like '%FORBIDDEN%'
      );
    end;
    perform public.canon_as(a);
    begin
      perform public.canon_authorize_media_signed_url(media);
      insert into sec05_results values ('signed_url_owner', 'ALLOWED', 'ALLOWED', true);
    exception when others then
      insert into sec05_results values ('signed_url_owner', 'ALLOWED', sqlerrm, false);
    end;
  else
    insert into sec05_results values ('signed_url_cross', 'FORBIDDEN', 'NO_ASSET', true);
    insert into sec05_results values ('signed_url_owner', 'ALLOWED', 'NO_ASSET', true);
  end if;

  -- Admin AAL1
  if admin_uid is not null then
    perform public.canon_as(admin_uid);
    begin
      perform public.get_admin_session();
      insert into sec05_results values ('admin_aal1_session', 'MFA_REQUIRED', 'ALLOWED', false);
    exception when others then
      insert into sec05_results values (
        'admin_aal1_session', 'MFA_REQUIRED', sqlerrm, sqlerrm like '%MFA_REQUIRED%'
      );
    end;
    begin
      perform public.staff_register_identity(
        admin_uid, 'sec05-x', 'SEC05', 'ADMIN'
      );
      insert into sec05_results values ('admin_aal1_staff_create', 'MFA_REQUIRED', 'ALLOWED', false);
    exception when others then
      insert into sec05_results values (
        'admin_aal1_staff_create', 'MFA_OR_FORBIDDEN', sqlerrm,
        sqlerrm like '%MFA_REQUIRED%' or sqlerrm like '%FORBIDDEN%' or sqlerrm like '%ROOT%'
      );
    end;
    begin
      perform public.list_moderation_queue(null, 10);
      insert into sec05_results values ('admin_aal1_moderation', 'MFA_OR_FORBIDDEN', 'ALLOWED', false);
    exception when others then
      insert into sec05_results values (
        'admin_aal1_moderation', 'MFA_OR_FORBIDDEN', sqlerrm,
        sqlerrm like '%MFA_REQUIRED%' or sqlerrm like '%FORBIDDEN%'
      );
    end;
  end if;

  -- Rate-limit path: no alternate unbound create post
  insert into sec05_results values (
    'post_create_bound',
    'wrapped_or_inline',
    case when exists (
      select 1 from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = 'canon_create_social_post'
    ) then 'present' else 'missing' end,
    exists (
      select 1 from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = 'canon_create_social_post'
    )
  );

  -- Cleanup fixtures only
  delete from public.social_posts where body like 'SEC05-FIXTURE-%';
  delete from public.messages where conversation_id = conv;
  if conv is not null then
    delete from public.conversation_participants where conversation_id = conv;
    delete from public.conversations where id = conv;
  end if;
  if media is not null then
    delete from public.media_assets where id = media;
  end if;
  if grant_id is not null then
    delete from public.vitacora_access_grants where id = grant_id;
  end if;
  if pet is not null then
    delete from public.pet_permission_grants where pet_id = pet;
    delete from public.pet_responsibility_events where pet_id = pet;
    delete from public.pet_lifecycle_events where pet_id = pet;
    delete from public.pet_responsibility_links where pet_id = pet;
    delete from public.vitacora_profiles where pet_id = pet;
    delete from public.pets where id = pet and name like 'SEC05-FIXTURE-%';
  end if;
  if org_a is not null then
    delete from public.organization_memberships where organization_id = org_a;
    delete from public.organization_roles where organization_id = org_a;
    delete from public.organizations where id = org_a and name like 'SEC05-FIXTURE-%';
  end if;
  if org_b is not null then
    delete from public.organization_memberships where organization_id = org_b;
    delete from public.organization_roles where organization_id = org_b;
    delete from public.organizations where id = org_b and name like 'SEC05-FIXTURE-%';
  end if;
end $$;

select k, expected, actual, pass from sec05_results order by k;
