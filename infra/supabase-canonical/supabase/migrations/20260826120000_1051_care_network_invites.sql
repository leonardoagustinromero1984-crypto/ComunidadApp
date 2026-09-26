-- LeoVer STAGING — 1051 care network invites (PERSON↔PERSON scoped to pet/VitaCora)
-- Reuses pet_responsibility_links (OWNER / AUTHORIZED, PENDING / ACTIVE / ENDED).
-- Does not edit 1044–1050.

alter table public.pet_responsibility_links
  add column if not exists care_role text null
    check (care_role is null or care_role in ('FAMILY', 'CAREGIVER', 'TRUSTED', 'OTHER'));

create or replace function public.canon_list_pet_holders(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public._acl_pet_holder(auth.uid(), p_pet_id)
    or public._acl_is_admin(auth.uid())
    or exists (
      select 1 from public.pet_responsibility_links pending
      where pending.pet_id = p_pet_id
        and pending.holder_person_id = auth.uid()
        and pending.status = 'PENDING'
    )
  ) then
    raise exception 'FORBIDDEN';
  end if;

  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'link_id', l.id,
      'holder_kind', l.holder_kind,
      'role', l.role,
      'care_role', l.care_role,
      'status', l.status,
      'person_id', l.holder_person_id,
      'organization_id', l.holder_organization_id,
      'display_name', coalesce(per.display_name, org.name),
      'username', per.username,
      'avatar_asset_id', coalesce(per.avatar_asset_id, org.logo_asset_id)
    ) order by l.created_at)
    from public.pet_responsibility_links l
    left join public.persons per on per.user_id = l.holder_person_id
    left join public.organizations org on org.id = l.holder_organization_id
    where l.pet_id = p_pet_id
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_invite_care_person(
  p_pet_id uuid,
  p_person_id uuid,
  p_care_role text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link uuid;
  v_role text := upper(btrim(coalesce(p_care_role, '')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_person_id is null or p_person_id = auth.uid() then
    raise exception 'CARE_INVITE_INVALID_PERSON';
  end if;
  if v_role not in ('FAMILY', 'CAREGIVER', 'TRUSTED', 'OTHER') then
    raise exception 'CARE_ROLE_INVALID';
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
    raise exception 'CARE_INVITE_DUPLICATE';
  end if;

  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, role, status, care_role, granted_by_actor_user_id
  ) values (
    p_pet_id, 'PERSON', p_person_id, 'AUTHORIZED', 'PENDING', v_role, auth.uid()
  )
  returning id into v_link;

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type, metadata)
  values (p_pet_id, v_link, auth.uid(), 'CARE_INVITED', jsonb_build_object('care_role', v_role));
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
  if v_link.status <> 'PENDING' or v_link.role <> 'AUTHORIZED' then
    raise exception 'CARE_INVITE_NOT_PENDING';
  end if;

  update public.pet_responsibility_links
    set status = 'ACTIVE'
    where id = p_link_id;

  insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
  select v_link.pet_id, v_link.id, v_link.holder_person_id, code, v_link.granted_by_actor_user_id
  from public.permission_codes
  where scope in ('PET', 'VITACORA')
    and code in ('pet.view', 'vitacora.view')
    and not exists (
      select 1 from public.pet_permission_grants g
      where g.link_id = v_link.id
        and g.permission_code = code
        and g.revoked_at is null
    );

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_link.pet_id, p_link_id, auth.uid(), 'CARE_ACCEPTED');
  return p_link_id;
end;
$$;

create or replace function public.canon_reject_care_invite(p_link_id uuid)
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

  update public.pet_responsibility_links
    set status = 'ENDED', valid_until = timezone('utc', now())
    where id = p_link_id;

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_link.pet_id, p_link_id, auth.uid(), 'CARE_REJECTED');
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
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_link from public.pet_responsibility_links where id = p_link_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_link.holder_person_id is distinct from auth.uid() then raise exception 'FORBIDDEN'; end if;
  if v_link.role = 'OWNER' then raise exception 'CARE_OWNER_CANNOT_LEAVE'; end if;
  if v_link.status <> 'ACTIVE' then return p_link_id; end if;

  update public.pet_responsibility_links
    set status = 'ENDED', valid_until = timezone('utc', now())
    where id = p_link_id;
  update public.pet_permission_grants
    set revoked_at = timezone('utc', now())
    where link_id = p_link_id and revoked_at is null;

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_link.pet_id, p_link_id, auth.uid(), 'CARE_LEFT');
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
      and l.role = 'AUTHORIZED'
      and l.status = 'PENDING'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_care_pets()
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
    ) order by pet.name)
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
      and l.role = 'AUTHORIZED'
      and l.status = 'ACTIVE'
      and pet.lifecycle_status = 'ACTIVE'
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_invite_care_person(uuid, uuid, text) from public, anon;
revoke all on function public.canon_accept_care_invite(uuid) from public, anon;
revoke all on function public.canon_reject_care_invite(uuid) from public, anon;
revoke all on function public.canon_leave_care_network(uuid) from public, anon;
revoke all on function public.canon_list_my_care_invites() from public, anon;
revoke all on function public.canon_list_my_care_pets() from public, anon;
grant execute on function public.canon_invite_care_person(uuid, uuid, text) to authenticated;
grant execute on function public.canon_accept_care_invite(uuid) to authenticated;
grant execute on function public.canon_reject_care_invite(uuid) to authenticated;
grant execute on function public.canon_leave_care_network(uuid) to authenticated;
grant execute on function public.canon_list_my_care_invites() to authenticated;
grant execute on function public.canon_list_my_care_pets() to authenticated;
-- Owner remove / cancel: existing canon_end_pet_responsibility only ended ACTIVE.
-- PENDING invites must also be cancellable without deleting the pet.
create or replace function public.canon_end_pet_responsibility(p_link_id uuid)
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
  if v_link.status not in ('ACTIVE', 'PENDING') then return p_link_id; end if;
  if not public._acl_pet_permission(auth.uid(), v_link.pet_id, 'responsibility.manage') then
    raise exception 'FORBIDDEN';
  end if;
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
  values (v_link.pet_id, p_link_id, auth.uid(), 'ENDED');
  return p_link_id;
end;
$$;

grant execute on function public.canon_list_pet_holders(uuid) to authenticated;
grant execute on function public.canon_end_pet_responsibility(uuid) to authenticated;
