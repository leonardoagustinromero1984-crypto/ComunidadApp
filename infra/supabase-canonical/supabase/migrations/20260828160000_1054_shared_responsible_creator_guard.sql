-- 1054: shared OWNER invite stays OWNER (same pet / same VitaCora).
-- Creator-only actions are NOT implied by an invited holder with role=OWNER.
-- Does not edit 1052/1053 files.

-- Leftover 1052 accepts copied every PET/VITACORA grant onto B.
update public.pet_permission_grants g
set revoked_at = timezone('utc', now())
from public.pets p
where g.pet_id = p.id
  and g.revoked_at is null
  and g.subject_person_id is not null
  and g.subject_person_id is distinct from p.created_by_user_id
  and g.permission_code in (
    'responsibility.manage',
    'privacy.manage',
    'services.authorize',
    'vitacora.share'
  );

create or replace function public._acl_pet_permission(p_user_id uuid, p_pet_id uuid, p_code text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public._acl_pet_holder(p_user_id, p_pet_id)
     and (
       (
         p_code in (
           'responsibility.manage',
           'privacy.manage',
           'services.authorize',
           'vitacora.share'
         )
         and (
           exists (
             select 1 from public.pets p
             where p.id = p_pet_id
               and p.created_by_user_id = p_user_id
           )
           or exists (
             select 1 from public.pet_permission_grants g
             where g.pet_id = p_pet_id
               and g.revoked_at is null
               and g.permission_code = p_code
               and g.subject_organization_id is not null
               and public._acl_org_member(p_user_id, g.subject_organization_id)
           )
         )
       )
       or (
         p_code not in (
           'responsibility.manage',
           'privacy.manage',
           'services.authorize',
           'vitacora.share'
         )
         and (
           exists (
             select 1 from public.pet_permission_grants g
             where g.pet_id = p_pet_id
               and g.revoked_at is null
               and g.permission_code = p_code
               and (g.subject_person_id = p_user_id or (
                 g.subject_organization_id is not null
                 and public._acl_org_member(p_user_id, g.subject_organization_id)
               ))
           )
           or exists (
             select 1 from public.pet_responsibility_links l
             where l.pet_id = p_pet_id
               and l.status = 'ACTIVE'
               and l.holder_person_id = p_user_id
               and l.role = 'OWNER'
               and p_code in (
                 'pet.view',
                 'pet.edit',
                 'vitacora.view',
                 'vitacora.manage',
                 'health.manage_declared'
               )
           )
         )
       )
     );
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
      (
        v_link.role = 'OWNER'
        and code in (
          'pet.view',
          'pet.edit',
          'vitacora.view',
          'vitacora.manage',
          'health.manage_declared'
        )
      )
      or (
        v_link.role <> 'OWNER'
        and code in ('pet.view', 'vitacora.view')
      )
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

create or replace function public.canon_end_pet_responsibility(p_link_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.pet_responsibility_links%rowtype;
  v_creator uuid;
  v_owners integer;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_link from public.pet_responsibility_links where id = p_link_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_link.status not in ('ACTIVE', 'PENDING') then return p_link_id; end if;
  if not public._acl_pet_permission(auth.uid(), v_link.pet_id, 'responsibility.manage') then
    raise exception 'FORBIDDEN';
  end if;

  select created_by_user_id into v_creator from public.pets where id = v_link.pet_id;
  if v_link.holder_person_id is not distinct from v_creator
     and auth.uid() is distinct from v_creator then
    raise exception 'FORBIDDEN';
  end if;

  if v_link.role = 'OWNER' and v_link.status = 'ACTIVE' then
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

create or replace function public.canon_archive_pet(p_pet_id uuid, p_reason text default null)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_creator uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select created_by_user_id into v_creator from public.pets where id = p_pet_id;
  if v_creator is null then raise exception 'NOT_FOUND'; end if;
  if auth.uid() is distinct from v_creator then
    raise exception 'FORBIDDEN';
  end if;

  update public.pets
    set lifecycle_status = 'ARCHIVED',
        archived_at = timezone('utc', now())
    where id = p_pet_id
      and lifecycle_status = 'ACTIVE';
  if not found then raise exception 'PET_NOT_ACTIVE'; end if;

  insert into public.pet_lifecycle_events (pet_id, from_status, to_status, actor_user_id, note)
  values (
    p_pet_id,
    'ACTIVE',
    'ARCHIVED',
    auth.uid(),
    nullif(btrim(coalesce(p_reason, '')), '')
  );
  return p_pet_id;
end;
$$;

revoke all on function public.canon_archive_pet(uuid, text) from public, anon;
grant execute on function public.canon_archive_pet(uuid, text) to authenticated;
grant execute on function public.canon_accept_care_invite(uuid) to authenticated;
grant execute on function public.canon_end_pet_responsibility(uuid) to authenticated;

notify pgrst, 'reload schema';
