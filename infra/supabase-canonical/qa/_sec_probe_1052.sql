create or replace function pg_temp.sec_probe_1052()
returns jsonb
language plpgsql
as $$
declare
  v_a uuid;
  v_b uuid;
  v_pet uuid;
  v_link_a uuid;
  v_link_b uuid;
  v_end_b_on_a text := 'NOT_RUN';
  v_b_invite text := 'NOT_RUN';
  v_b_leave text := 'NOT_RUN';
  v_archive_fn boolean;
  v_canon_archive boolean;
  v_b_archive text := 'NO_RPC';
  v_a_archive text := 'NO_RPC';
  v_grants jsonb;
  v_b_acl jsonb;
  v_invite_after_accept jsonb;
  v_out jsonb;
begin
  select user_id into v_a from public.persons where username = 'velu';
  select user_id into v_b from public.persons where username = 'leo';
  if v_a is null or v_b is null then
    raise exception 'PROBE_MISSING_PERSONS';
  end if;

  perform public.canon_as(v_a);
  v_pet := public.canon_create_pet('SEC-PROBE-1052', 'DOG');

  select id into v_link_a
  from public.pet_responsibility_links
  where pet_id = v_pet and holder_person_id = v_a and role = 'OWNER' and status = 'ACTIVE';

  v_link_b := public.canon_invite_pet_responsible(v_pet, v_b);

  perform public.canon_as(v_b);
  perform public.canon_accept_care_invite(v_link_b);

  select coalesce(jsonb_agg(permission_code order by permission_code), '[]'::jsonb)
  into v_grants
  from public.pet_permission_grants
  where link_id = v_link_b and revoked_at is null;

  select jsonb_build_object('role', role, 'status', status)
  into v_invite_after_accept
  from public.pet_responsibility_links where id = v_link_b;

  v_b_acl := jsonb_build_object(
    'pet.view', public._acl_pet_permission(v_b, v_pet, 'pet.view'),
    'pet.edit', public._acl_pet_permission(v_b, v_pet, 'pet.edit'),
    'vitacora.view', public._acl_pet_permission(v_b, v_pet, 'vitacora.view'),
    'vitacora.manage', public._acl_pet_permission(v_b, v_pet, 'vitacora.manage'),
    'health.manage_declared', public._acl_pet_permission(v_b, v_pet, 'health.manage_declared'),
    'responsibility.manage', public._acl_pet_permission(v_b, v_pet, 'responsibility.manage'),
    'privacy.manage', public._acl_pet_permission(v_b, v_pet, 'privacy.manage'),
    'vitacora.share', public._acl_pet_permission(v_b, v_pet, 'vitacora.share'),
    'services.authorize', public._acl_pet_permission(v_b, v_pet, 'services.authorize')
  );

  begin
    perform public.canon_end_pet_responsibility(v_link_a);
    v_end_b_on_a := 'ALLOWED';
  exception when others then
    v_end_b_on_a := sqlerrm;
  end;

  begin
    perform public.canon_invite_pet_responsible(v_pet, '00000000-0000-0000-0000-000000000001');
    v_b_invite := 'ALLOWED';
  exception when others then
    v_b_invite := sqlerrm;
  end;

  select exists (
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'm08_archive_pet'
  ) into v_archive_fn;
  select exists (
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'canon_archive_pet'
  ) into v_canon_archive;

  if v_canon_archive then
    begin
      perform public.canon_archive_pet(v_pet, 'sec-probe-b');
      v_b_archive := 'ALLOWED';
    exception when others then
      v_b_archive := sqlerrm;
    end;
  end if;

  begin
    perform public.canon_leave_care_network(v_link_b);
    v_b_leave := 'ALLOWED';
  exception when others then
    v_b_leave := sqlerrm;
  end;

  perform public.canon_as(v_a);
  if v_canon_archive and v_b_archive is distinct from 'ALLOWED' then
    begin
      perform public.canon_archive_pet(v_pet, 'sec-probe-a');
      v_a_archive := 'ALLOWED';
    exception when others then
      v_a_archive := sqlerrm;
    end;
  end if;

  v_out := jsonb_build_object(
    'pet_id', v_pet,
    'created_by', v_a,
    'b', v_b,
    'invite_role_status', v_invite_after_accept,
    'b_acl', v_b_acl,
    'a_acl_responsibility_manage', public._acl_pet_permission(v_a, v_pet, 'responsibility.manage'),
    'b_grants_after_accept', v_grants,
    'b_end_creator_link', v_end_b_on_a,
    'b_invite_other', v_b_invite,
    'b_leave_own', v_b_leave,
    'm08_archive_exists', v_archive_fn,
    'canon_archive_exists', v_canon_archive,
    'b_archive', v_b_archive,
    'a_archive', v_a_archive,
    'creator_still_active_owner', exists (
      select 1 from public.pet_responsibility_links
      where id = v_link_a and status = 'ACTIVE' and holder_person_id = v_a
    )
  );

  delete from public.pet_permission_grants where pet_id = v_pet;
  delete from public.pet_responsibility_events where pet_id = v_pet;
  delete from public.pet_lifecycle_events where pet_id = v_pet;
  delete from public.pet_responsibility_links where pet_id = v_pet;
  delete from public.vitacora_profiles where pet_id = v_pet;
  delete from public.pets where id = v_pet;
  return v_out;
end;
$$;

select pg_temp.sec_probe_1052();
