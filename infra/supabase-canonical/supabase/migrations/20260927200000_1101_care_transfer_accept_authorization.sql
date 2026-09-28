-- LeoVer Canonical Baseline
-- Logical migration: 1101
-- Authorize the care-transfer recipient before any terminal accept result.
-- Does not edit 1000–1100. Does not grant table SELECT. Client execute stays authenticated only.
--
-- canon_accept_care_transfer (1071) returned _canon_care_transfer_json as soon as
-- status was ACCEPTED, and only then checked _acl_can_operate_care_actor.
-- An authenticated caller who was not the recipient could read the transfer
-- after acceptance. The early return did not change custody.
--
-- Recipient model is unchanged. The target may be PERSON or ORGANIZATION.
-- _acl_can_operate_care_actor authorizes the target person, or an organization
-- operator with org.pets.transfer. The initiator is not an accept actor unless
-- that same check already includes them as the target.
--
-- Terminal statuses already supported by pet_care_transfers:
--   ACCEPTED  — authorized recipient receives the existing row and nothing is written
--   REJECTED  — authorized recipient still receives PET_TRANSFER_NOT_PENDING
--   CANCELLED — authorized recipient still receives PET_TRANSFER_NOT_PENDING
-- Unauthorized callers receive FORBIDDEN for every terminal status. Transfer JSON
-- is not returned before authorization.
--
-- The ACCEPTED retry does not update status, so the existing AFTER UPDATE trigger
-- pet_care_transfers_adoption_close does not call _canon_complete_adoptions_for_pet.
-- Publication closure remains the 1100 completion function, and it still runs only
-- when a transfer first becomes ACCEPTED.

create or replace function public.canon_accept_care_transfer(p_transfer_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_tr public.pet_care_transfers%rowtype;
  v_pet public.pets%rowtype;
  v_link uuid;
  v_open public.pet_care_stages%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_transfer_id is null then raise exception 'VALIDATION'; end if;

  select * into v_tr
  from public.pet_care_transfers
  where id = p_transfer_id
  for update;
  if not found then raise exception 'NOT_FOUND'; end if;

  if not public._acl_can_operate_care_actor(
    auth.uid(), v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id, 'org.pets.transfer'
  ) then
    raise exception 'FORBIDDEN';
  end if;

  if v_tr.status = 'ACCEPTED' then
    return public._canon_care_transfer_json(v_tr.id);
  end if;
  if v_tr.status <> 'PENDING' then
    raise exception 'PET_TRANSFER_NOT_PENDING';
  end if;

  select * into v_pet
  from public.pets
  where id = v_tr.pet_id
  for update;
  if not found then raise exception 'NOT_FOUND'; end if;

  if v_pet.lifecycle_status <> 'ACTIVE' then
    raise exception 'PET_NOT_ACTIVE';
  end if;
  if v_pet.current_custodian_kind is distinct from v_tr.source_kind
     or v_pet.current_custodian_person_id is distinct from v_tr.source_person_id
     or v_pet.current_custodian_organization_id is distinct from v_tr.source_organization_id then
    raise exception 'PET_TRANSFER_SOURCE_STALE';
  end if;
  if (
    v_tr.target_kind = v_pet.current_custodian_kind
    and v_tr.target_person_id is not distinct from v_pet.current_custodian_person_id
    and v_tr.target_organization_id is not distinct from v_pet.current_custodian_organization_id
  ) then
    raise exception 'PET_TRANSFER_SAME_ACTOR';
  end if;
  if v_tr.target_kind = 'PERSON'
     and not exists (select 1 from public.persons where user_id = v_tr.target_person_id) then
    raise exception 'PET_TRANSFER_TARGET_INVALID';
  end if;
  if v_tr.target_kind = 'ORGANIZATION'
     and not exists (
       select 1 from public.organizations
       where id = v_tr.target_organization_id and lifecycle_status = 'ACTIVE'
     ) then
    raise exception 'PET_TRANSFER_TARGET_INVALID';
  end if;

  select * into v_open
  from public.pet_care_stages
  where pet_id = v_pet.id and closed_at is null
  for update;

  if found then
    update public.pet_care_stages
    set closed_at = timezone('utc', now()),
        share_personal_media = v_tr.share_personal_media,
        closed_by_user_id = auth.uid(),
        transfer_id = v_tr.id
    where id = v_open.id;
  else
    insert into public.pet_care_stages (
      pet_id, actor_kind, actor_person_id, actor_organization_id,
      opened_at, closed_at, share_personal_media,
      opened_by_user_id, closed_by_user_id, transfer_id
    ) values (
      v_pet.id, v_tr.source_kind, v_tr.source_person_id, v_tr.source_organization_id,
      v_pet.created_at, timezone('utc', now()), v_tr.share_personal_media,
      v_tr.initiated_by_user_id, auth.uid(), v_tr.id
    );
  end if;

  perform public._canon_revoke_current_care_holders(v_pet.id);
  perform public._canon_apply_current_custodian(
    v_pet.id, v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id
  );
  v_link := public._canon_grant_custodian_control(
    v_pet.id, v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id, auth.uid()
  );
  perform public._canon_open_care_stage(
    v_pet.id, v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id, auth.uid(), v_tr.id
  );

  update public.pet_care_transfers
  set status = 'ACCEPTED',
      decided_by_user_id = auth.uid(),
      decided_at = timezone('utc', now())
  where id = v_tr.id
    and status = 'PENDING';

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type, metadata)
  values (
    v_pet.id,
    v_link,
    auth.uid(),
    'CARE_TRANSFERRED',
    jsonb_build_object(
      'transfer_id', v_tr.id,
      'source_kind', v_tr.source_kind,
      'target_kind', v_tr.target_kind,
      'share_personal_media', v_tr.share_personal_media,
      'performed_by', auth.uid()
    )
  );

  return public._canon_care_transfer_json(v_tr.id);
end;
$$;

revoke all on function public.canon_accept_care_transfer(uuid) from public, anon;
grant execute on function public.canon_accept_care_transfer(uuid) to authenticated;

comment on function public.canon_accept_care_transfer(uuid) is
  'Recipient authorization (PERSON or ORGANIZATION target) happens before any terminal result. An already ACCEPTED transfer returns without custody, adoption, or VitaCora writes.';

notify pgrst, 'reload schema';
