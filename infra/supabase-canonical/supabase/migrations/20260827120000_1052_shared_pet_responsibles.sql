-- LeoVer STAGING — 1052 shared PERSON responsables (same pet / same VitaCora)
-- Additive: reuses pet_responsibility_links OWNER + PENDING/ACTIVE/ENDED.
-- Does not drop care_role or existing AUTHORIZED care links.

create or replace function public.canon_invite_pet_responsible(
  p_pet_id uuid,
  p_person_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_person_id is null or p_person_id = auth.uid() then
    raise exception 'RESPONSIBLE_INVITE_INVALID_PERSON';
  end if;
  if not public._acl_pet_permission(auth.uid(), p_pet_id, 'responsibility.manage') then
    raise exception 'FORBIDDEN';
  end if;
  if not exists (select 1 from public.persons p where p.user_id = p_person_id) then
    raise exception 'PERSON_NOT_FOUND';
  end if;
  if exists (
    select 1 from public.pet_responsibility_links l
    where l.pet_id = p_pet_id
      and l.holder_person_id = p_person_id
      and l.status in ('PENDING', 'ACTIVE')
  ) then
    raise exception 'RESPONSIBLE_INVITE_DUPLICATE';
  end if;

  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, role, status, granted_by_actor_user_id
  ) values (
    p_pet_id, 'PERSON', p_person_id, 'OWNER', 'PENDING', auth.uid()
  )
  returning id into v_link;

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type, metadata)
  values (p_pet_id, v_link, auth.uid(), 'RESPONSIBLE_INVITED', '{}'::jsonb);
  return v_link;
end;
$$;

create or replace function public.canon_accept_care_invite(p_link_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.pet_responsibility_links%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_link from public.pet_responsibility_links where id = p_link_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_link.holder_person_id is distinct from auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_link.status <> 'PENDING' then raise exception 'CARE_INVITE_NOT_PENDING'; end if;
  if v_link.role not in ('OWNER', 'AUTHORIZED') then
    raise exception 'CARE_INVITE_NOT_PENDING';
  end if;

  update public.pet_responsibility_links
    set status = 'ACTIVE'
    where id = p_link_id;

  insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
  select v_link.pet_id, v_link.id, v_link.holder_person_id, code, v_link.granted_by_actor_user_id
  from public.permission_codes
  where scope in ('PET', 'VITACORA')
    and (
      v_link.role = 'OWNER'
      or code in ('pet.view', 'vitacora.view')
    )
    and not exists (
      select 1 from public.pet_permission_grants g
      where g.link_id = v_link.id
        and g.permission_code = code
        and g.revoked_at is null
    );

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (
    v_link.pet_id,
    p_link_id,
    auth.uid(),
    case when v_link.role = 'OWNER' then 'RESPONSIBLE_ACCEPTED' else 'CARE_ACCEPTED' end
  );
  return p_link_id;
end;
$$;

create or replace function public.canon_leave_care_network(p_link_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.pet_responsibility_links%rowtype;
  v_owners integer;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_link from public.pet_responsibility_links where id = p_link_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_link.holder_person_id is distinct from auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_link.status <> 'ACTIVE' then return p_link_id; end if;
  if v_link.role = 'OWNER' then
    select count(*) into v_owners
    from public.pet_responsibility_links
    where pet_id = v_link.pet_id and status = 'ACTIVE' and role = 'OWNER';
    if v_owners <= 1 then
      raise exception 'LAST_OWNER_REQUIRED';
    end if;
  end if;

  update public.pet_responsibility_links
    set status = 'ENDED', valid_until = timezone('utc', now())
    where id = p_link_id;
  update public.pet_permission_grants
    set revoked_at = timezone('utc', now())
    where link_id = p_link_id and revoked_at is null;

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_link.pet_id, p_link_id, auth.uid(), 'RESPONSIBLE_LEFT');
  return p_link_id;
end;
$$;

create or replace function public.canon_list_my_care_invites()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'link_id', l.id,
      'pet_id', l.pet_id,
      'pet_name', pet.name,
      'owner_name', coalesce(owner.display_name, ''),
      'care_role', l.care_role,
      'status', l.status
    ) order by l.created_at desc)
    from public.pet_responsibility_links l
    join public.pets pet on pet.id = l.pet_id
    left join public.pet_responsibility_links owners
      on owners.pet_id = l.pet_id
     and owners.role = 'OWNER'
     and owners.status = 'ACTIVE'
     and owners.holder_kind = 'PERSON'
    left join public.persons owner on owner.user_id = owners.holder_person_id
    where l.holder_person_id = auth.uid()
      and l.holder_kind = 'PERSON'
      and l.role in ('OWNER', 'AUTHORIZED')
      and l.status = 'PENDING'
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_invite_pet_responsible(uuid, uuid) from public, anon;
grant execute on function public.canon_invite_pet_responsible(uuid, uuid) to authenticated;
grant execute on function public.canon_accept_care_invite(uuid) to authenticated;
grant execute on function public.canon_leave_care_network(uuid) to authenticated;
grant execute on function public.canon_list_my_care_invites() to authenticated;
