-- LeoVer Canonical Baseline
-- Logical migration: 1103
-- Atomic end of an active foster transit.
-- Does not edit 1000–1102. Does not grant table SELECT.
-- Client execute stays authenticated only.
--
-- canon_end_pet_responsibility is not the transit operation.
-- It returns a terminal link id before checking responsibility.manage,
-- and it does not move the foster request and placement with the link.
-- Calling it would repeat the early-return leak fixed for care transfers
-- in 1101, and it would leave the transit half-closed.
-- This function authorizes the request manager first, then either returns
-- the existing COMPLETED/CLOSED result or closes the whole transit.
--
-- Authorized actor: the current responsible or request manager.
-- _canon_can_manage_foster_request already allows the requester, or an
-- active holder who is not a PENDING/SELECTED foster on that request.
-- The selected foster is not given a new completion privilege.
-- Foster-initiated early termination stays a later flow.
-- The foster never becomes OWNER. No care transfer. No adoption.
-- No custodian write. The organization RESPONSIBLE link stays ACTIVE.
--
-- APPLICATION STATUS DECISION:
-- foster_care_applications.status has no COMPLETED value.
-- SELECTED remains the historical application status after the request
-- becomes COMPLETED and the placement becomes CLOSED.
-- Active versus finished is request status plus placement status.
--
-- TRANSIT_END_MOMENT: NOT_CREATED_BY_CURRENT_DOMAIN_DESIGN
-- vitacora_moments.kind has no transit-end value. This migration does not
-- invent one, does not insert a moment, and does not replace the VitaCora.
-- The existing public number is only read so the caller can see that the
-- same profile row is still there.

create or replace function public.canon_complete_foster_transit(p_request_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_req public.foster_care_requests%rowtype;
  v_app public.foster_care_applications%rowtype;
  v_place public.foster_placements%rowtype;
  v_link public.pet_responsibility_links%rowtype;
  v_open_count bigint;
  v_link_count bigint;
  v_org uuid;
  v_vita_pet uuid;
  v_vita_number bigint;
  v_idempotent boolean := false;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_request_id is null then
    raise exception 'VALIDATION';
  end if;

  select *
    into v_req
    from public.foster_care_requests
   where id = p_request_id
   for update;
  if not found then
    raise exception 'NOT_FOUND';
  end if;

  if not public._canon_can_manage_foster_request(auth.uid(), v_req.id) then
    raise exception 'FORBIDDEN';
  end if;

  if v_req.selected_application_id is null then
    raise exception 'STATUS_INVALID';
  end if;

  select *
    into v_app
    from public.foster_care_applications
   where id = v_req.selected_application_id
   for update;
  if not found or v_app.request_id is distinct from v_req.id or v_app.status <> 'SELECTED' then
    raise exception 'STATUS_INVALID';
  end if;

  if v_req.status = 'COMPLETED' then
    select *
      into v_place
      from public.foster_placements
     where pet_id = v_req.pet_id
       and foster_user_id = v_app.foster_user_id
       and status = 'CLOSED'
     order by ends_at desc nulls last, starts_at desc
     limit 1;
    if not found then
      raise exception 'STATUS_INVALID';
    end if;

    select *
      into v_link
      from public.pet_responsibility_links
     where pet_id = v_req.pet_id
       and holder_kind = 'PERSON'
       and holder_person_id = v_app.foster_user_id
       and role = 'AUTHORIZED'
       and status = 'ENDED'
     order by valid_until desc nulls last, created_at desc
     limit 1;
    if not found then
      raise exception 'STATUS_INVALID';
    end if;
    v_idempotent := true;
  elsif v_req.status <> 'ACTIVE' then
    raise exception 'STATUS_INVALID';
  else
    select count(*)
      into v_open_count
      from public.foster_placements
     where pet_id = v_req.pet_id
       and foster_user_id = v_app.foster_user_id
       and status = 'OPEN';
    if v_open_count <> 1 then
      raise exception 'STATUS_INVALID';
    end if;

    select *
      into v_place
      from public.foster_placements
     where pet_id = v_req.pet_id
       and foster_user_id = v_app.foster_user_id
       and status = 'OPEN'
     for update;

    select count(*)
      into v_link_count
      from public.pet_responsibility_links
     where pet_id = v_req.pet_id
       and holder_kind = 'PERSON'
       and holder_person_id = v_app.foster_user_id
       and role = 'AUTHORIZED'
       and status = 'ACTIVE';
    if v_link_count <> 1 then
      raise exception 'STATUS_INVALID';
    end if;

    select *
      into v_link
      from public.pet_responsibility_links
     where pet_id = v_req.pet_id
       and holder_kind = 'PERSON'
       and holder_person_id = v_app.foster_user_id
       and role = 'AUTHORIZED'
       and status = 'ACTIVE'
     for update;

    if not exists (
      select 1
        from public.pet_responsibility_links l
       where l.pet_id = v_req.pet_id
         and l.holder_kind = 'ORGANIZATION'
         and l.role = 'RESPONSIBLE'
         and l.status = 'ACTIVE'
    ) then
      raise exception 'STATUS_INVALID';
    end if;

    update public.foster_care_requests
       set status = 'COMPLETED',
           updated_at = timezone('utc', now())
     where id = v_req.id
       and status = 'ACTIVE';
    if not found then
      raise exception 'STATUS_INVALID';
    end if;

    update public.foster_placements
       set status = 'CLOSED',
           ends_at = timezone('utc', now())
     where id = v_place.id
       and status = 'OPEN';
    if not found then
      raise exception 'STATUS_INVALID';
    end if;

    update public.pet_responsibility_links
       set status = 'ENDED',
           valid_until = timezone('utc', now())
     where id = v_link.id
       and holder_kind = 'PERSON'
       and holder_person_id = v_app.foster_user_id
       and role = 'AUTHORIZED'
       and status = 'ACTIVE';
    if not found then
      raise exception 'STATUS_INVALID';
    end if;

    update public.pet_permission_grants
       set revoked_at = timezone('utc', now())
     where link_id = v_link.id
       and revoked_at is null;

    insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type, metadata)
    values (
      v_req.pet_id,
      v_link.id,
      auth.uid(),
      'ENDED',
      jsonb_build_object(
        'request_id', v_req.id,
        'placement_id', v_place.id,
        'source', 'canon_complete_foster_transit'
      )
    );

    select *
      into v_req
      from public.foster_care_requests
     where id = v_req.id;
    select *
      into v_place
      from public.foster_placements
     where id = v_place.id;
    select *
      into v_link
      from public.pet_responsibility_links
     where id = v_link.id;
  end if;

  select l.holder_organization_id
    into v_org
    from public.pet_responsibility_links l
   where l.pet_id = v_req.pet_id
     and l.holder_kind = 'ORGANIZATION'
     and l.role = 'RESPONSIBLE'
     and l.status = 'ACTIVE'
   limit 1;

  select v.pet_id, v.public_vitacora_number
    into v_vita_pet, v_vita_number
    from public.vitacora_profiles v
   where v.pet_id = v_req.pet_id;

  return jsonb_build_object(
    'request_id', v_req.id,
    'request_status', v_req.status,
    'request_updated_at', v_req.updated_at,
    'application_id', v_app.id,
    'application_status', v_app.status,
    'placement_id', v_place.id,
    'placement_status', v_place.status,
    'placement_ends_at', v_place.ends_at,
    'pet_id', v_req.pet_id,
    'foster_user_id', v_app.foster_user_id,
    'temporary_link_id', v_link.id,
    'temporary_holder_kind', v_link.holder_kind,
    'temporary_holder_role', v_link.role,
    'temporary_link_status', v_link.status,
    'temporary_link_valid_until', v_link.valid_until,
    'responsible_organization_id', v_org,
    'vitacora_pet_id', v_vita_pet,
    'public_vitacora_number', v_vita_number,
    'idempotent', v_idempotent
  );
end;
$$;

revoke all on function public.canon_complete_foster_transit(uuid) from public, anon;
grant execute on function public.canon_complete_foster_transit(uuid) to authenticated;

comment on function public.canon_complete_foster_transit(uuid) is
  'Request manager ends one ACTIVE/OPEN transit atomically. Authorization runs before any COMPLETED result. A completed retry writes nothing. The selected foster is not a completion actor. SELECTED stays historical. Pet, VitaCora, organization RESPONSIBLE, and ownership stay in place.';

notify pgrst, 'reload schema';
