-- LeoVer Canonical Baseline
-- Logical migration: 1102
-- Foster transit read contract, one non-terminal request per pet, and
-- eligible-foster discovery. Does not edit 1000–1101.
-- Does not grant table SELECT. Client execute stays authenticated only.
-- Does not apply custody changes. Does not implement end-of-transit.
--
-- Status audit (check constraints already in the schema; no new names):
--
-- foster_care_requests.status
--   NON-TERMINAL: REQUESTED, MATCHED, ACTIVE
--   TERMINAL:     COMPLETED, CANCELLED
--   1093 select writes ACTIVE. MATCHED remains a legal pre-completion status
--   because canon_select_foster_applicant still accepts it.
--
-- foster_care_applications.status
--   PENDING:       non-terminal, waiting for the responsible to choose
--   SELECTED:      in force for the current transit (not a terminal row)
--   NOT_SELECTED:  terminal
--   WITHDRAWN:     terminal
--
-- foster_placements.status
--   NON-TERMINAL: OPEN
--   TERMINAL:     CLOSED, CANCELLED
--
-- pet_responsibility_links
--   role:   PERSON OWNER | PERSON AUTHORIZED | ORGANIZATION RESPONSIBLE
--   status: PENDING and ACTIVE are non-terminal; ENDED is terminal
--
-- Authorization already used by the 1093 transit RPCs:
--   auth.uid()
--   _acl_pet_holder(user, pet) — active person holder or active org member
--   foster eligibility inside canon_apply_to_foster_request:
--     active foster_profiles row
--     active person_capabilities FOSTER with verification_status VERIFIED
--     _canon_person_has_base_location
--   species_pref, age_pref, accepts_treatment, other_animals_ok, and capacity
--   are stored and are not read by that eligibility check.
--
-- Discovery decision (locked by this migration):
--   canon_list_open_foster_requests was executable by every authenticated
--   user and returned every REQUESTED row. That is not the product contract.
--   Open requests are foster opportunities. The caller must pass the same
--   eligibility gate as canon_apply_to_foster_request.
--   Preferences stay advisory. Capacity stays a stored profile field.
--   Neither becomes a hard filter here, because apply does not enforce them
--   and no existing function computes remaining places.
--   An unrelated PERSONAL account receives FOSTER_NOT_ELIGIBLE and no rows.
--   Management of a request stays on canon_list_my_foster_requests.
--
-- Temporary custody (unchanged, not rewritten here):
--   canon_select_foster_applicant still inserts PERSON / AUTHORIZED for the
--   selected foster. It does not insert OWNER, does not end the organization
--   RESPONSIBLE link, does not update pets.current_custodian_*, and does not
--   write a VitaCora moment. 1102 does not switch this to care_transfer.
--
-- TRANSIT_START_VITACORA_EVENT: MISSING
--   vitacora_moments.kind is ARRIVAL, BIRTHDAY, MEMORY, PHOTO, TRIP,
--   MILESTONE, NOTE, SOCIAL. No transit-start kind exists. Selection writes
--   neither vitacora_moments nor pet_responsibility_events. This block does
--   not invent a kind. Core transit state is the request, application,
--   placement, and AUTHORIZED link.
--
-- END TRANSIT — next contract, not implemented in 1102:
--   An authorized actor ends the active placement.
--   The foster PERSON / AUTHORIZED link becomes ENDED.
--   The organization RESPONSIBLE link stays ACTIVE.
--   The foster never becomes OWNER.
--   The request and the placement move to terminal statuses already allowed
--   above (request COMPLETED or CANCELLED, placement CLOSED or CANCELLED).
--   Pet id and VitaCora profile stay the same row.
--   Optional history uses an existing VitaCora event contract only.
--   pets.current_custodian_* stays untouched unless a later domain decision
--   says the custodian columns are part of temporary care.

-- ---------------------------------------------------------------------------
-- One non-terminal request per pet. Terminal history stays.
-- ---------------------------------------------------------------------------

do $$
begin
  if exists (
    select 1
      from public.foster_care_requests
     where status in ('REQUESTED', 'MATCHED', 'ACTIVE')
     group by pet_id
     having count(*) > 1
  ) then
    raise exception 'FOSTER_REQUEST_DUPLICATES_PRESENT';
  end if;
end;
$$;

create unique index if not exists foster_care_requests_one_nonterminal_pet_uidx
  on public.foster_care_requests (pet_id)
  where status in ('REQUESTED', 'MATCHED', 'ACTIVE');

-- ---------------------------------------------------------------------------
-- Shared helpers. Not granted to clients.
-- ---------------------------------------------------------------------------

create or replace function public._canon_assert_foster_eligible()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if not exists (
    select 1
      from public.foster_profiles fp
      join public.person_capabilities c
        on c.user_id = fp.user_id
       and c.capability = 'FOSTER'
     where fp.user_id = auth.uid()
       and fp.active
       and c.verification_status = 'VERIFIED'
       and c.active
  ) then
    raise exception 'FOSTER_NOT_ELIGIBLE';
  end if;
  if not public._canon_person_has_base_location(auth.uid()) then
    raise exception 'BASE_LOCATION_REQUIRED';
  end if;
end;
$$;

create or replace function public._canon_can_manage_foster_request(
  p_user_id uuid,
  p_request_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
      from public.foster_care_requests r
     where r.id = p_request_id
       and (
         r.requested_by = p_user_id
         or (
           public._acl_pet_holder(p_user_id, r.pet_id)
           and not exists (
             select 1
               from public.foster_care_applications a
              where a.request_id = r.id
                and a.foster_user_id = p_user_id
                and a.status in ('PENDING', 'SELECTED')
           )
         )
       )
  );
$$;

create or replace function public._canon_caller_may_read_foster_transit(
  p_user_id uuid,
  p_pet_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public._acl_pet_holder(p_user_id, p_pet_id)
      or exists (
        select 1
          from public.foster_care_requests r
         where r.pet_id = p_pet_id
           and r.requested_by = p_user_id
      )
      or exists (
        select 1
          from public.foster_care_applications a
          join public.foster_care_requests r on r.id = a.request_id
         where r.pet_id = p_pet_id
           and a.foster_user_id = p_user_id
           and a.status = 'SELECTED'
      );
$$;

-- ---------------------------------------------------------------------------
-- Idempotent request creation. First non-terminal row wins.
-- ---------------------------------------------------------------------------

create or replace function public.canon_request_foster_for_pet(
  p_pet_id uuid,
  p_needs text default null,
  p_notes text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_pet_id is null then raise exception 'VALIDATION'; end if;
  if not public._acl_pet_holder(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended('leover-foster-request:' || p_pet_id::text, 1102)
  );

  select r.id
    into v_id
    from public.foster_care_requests r
   where r.pet_id = p_pet_id
     and r.status in ('REQUESTED', 'MATCHED', 'ACTIVE')
   order by r.created_at
   limit 1
   for update;
  if v_id is not null then
    return v_id;
  end if;

  begin
    insert into public.foster_care_requests (pet_id, requested_by, needs, notes)
    values (p_pet_id, auth.uid(), p_needs, p_notes)
    returning id into v_id;
    return v_id;
  exception
    when unique_violation then
      select r.id
        into v_id
        from public.foster_care_requests r
       where r.pet_id = p_pet_id
         and r.status in ('REQUESTED', 'MATCHED', 'ACTIVE')
       order by r.created_at
       limit 1;
      if v_id is null then
        raise;
      end if;
      return v_id;
  end;
end;
$$;

-- Apply keeps 1093 behavior. Eligibility is the shared helper so discovery
-- cannot drift from the write gate. Preferences and capacity stay unused.
create or replace function public.canon_apply_to_foster_request(p_request_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_req public.foster_care_requests%rowtype;
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_req from public.foster_care_requests where id = p_request_id for update;
  if not found or v_req.status <> 'REQUESTED' then raise exception 'NOT_FOUND'; end if;
  perform public._canon_assert_foster_eligible();
  insert into public.foster_care_applications (request_id, foster_user_id)
  values (p_request_id, auth.uid())
  on conflict (request_id, foster_user_id) do update set status = 'PENDING'
  returning id into v_id;
  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Reads
-- ---------------------------------------------------------------------------

create or replace function public.canon_list_open_foster_requests()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public._canon_assert_foster_eligible();
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', r.id,
      'pet_id', r.pet_id,
      'pet_name', p.name,
      'species', p.species_code,
      'sex', p.sex,
      'needs', r.needs,
      'notes', r.notes,
      'status', r.status,
      'created_at', r.created_at
    ) order by r.created_at desc)
    from public.foster_care_requests r
    join public.pets p on p.id = r.pet_id
   where r.status = 'REQUESTED'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_foster_requests()
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
      'id', r.id,
      'pet_id', r.pet_id,
      'pet_name', p.name,
      'species', p.species_code,
      'sex', p.sex,
      'status', r.status,
      'needs', r.needs,
      'notes', r.notes,
      'selected_application_id', r.selected_application_id,
      'placement_id', pl.id,
      'placement_status', pl.status,
      'created_at', r.created_at,
      'updated_at', r.updated_at
    ) order by r.updated_at desc)
    from public.foster_care_requests r
    join public.pets p on p.id = r.pet_id
    left join lateral (
      select fp.id, fp.status
        from public.foster_placements fp
        join public.foster_care_applications a on a.id = r.selected_application_id
       where fp.pet_id = r.pet_id
         and fp.foster_user_id = a.foster_user_id
       order by case when fp.status = 'OPEN' then 0 else 1 end, fp.starts_at desc
       limit 1
    ) pl on true
   where public._canon_can_manage_foster_request(auth.uid(), r.id)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_foster_applications()
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
      'id', a.id,
      'request_id', a.request_id,
      'status', a.status,
      'request_status', r.status,
      'pet_id', r.pet_id,
      'pet_name', p.name,
      'species', p.species_code,
      'sex', p.sex,
      'needs', r.needs,
      'notes', r.notes,
      'selected_application_id', r.selected_application_id,
      'placement_id', pl.id,
      'placement_status', pl.status,
      'created_at', a.created_at
    ) order by a.created_at desc)
    from public.foster_care_applications a
    join public.foster_care_requests r on r.id = a.request_id
    join public.pets p on p.id = r.pet_id
    left join lateral (
      select fp.id, fp.status
        from public.foster_placements fp
       where fp.pet_id = r.pet_id
         and fp.foster_user_id = a.foster_user_id
         and a.status = 'SELECTED'
       order by case when fp.status = 'OPEN' then 0 else 1 end, fp.starts_at desc
       limit 1
    ) pl on true
   where a.foster_user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_active_foster_transit(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_req public.foster_care_requests%rowtype;
  v_application_status text;
  v_foster_user_id uuid;
  v_placement_id uuid;
  v_placement_status text;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_pet_id is null then raise exception 'VALIDATION'; end if;
  if not public._canon_caller_may_read_foster_transit(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;

  select *
    into v_req
    from public.foster_care_requests r
   where r.pet_id = p_pet_id
     and r.status in ('MATCHED', 'ACTIVE')
   order by r.updated_at desc
   limit 1;
  if not found then
    raise exception 'NOT_FOUND';
  end if;

  select a.status, a.foster_user_id
    into v_application_status, v_foster_user_id
    from public.foster_care_applications a
   where a.id = v_req.selected_application_id;

  select fp.id, fp.status
    into v_placement_id, v_placement_status
    from public.foster_placements fp
   where v_foster_user_id is not null
     and fp.pet_id = v_req.pet_id
     and fp.foster_user_id = v_foster_user_id
   order by case when fp.status = 'OPEN' then 0 else 1 end, fp.starts_at desc
   limit 1;

  return jsonb_build_object(
    'request_id', v_req.id,
    'request_status', v_req.status,
    'selected_application_id', v_req.selected_application_id,
    'application_status', v_application_status,
    'placement_id', v_placement_id,
    'placement_status', v_placement_status,
    'pet_id', v_req.pet_id,
    'pet_name', (select pet.name from public.pets pet where pet.id = v_req.pet_id),
    'species', (select pet.species_code from public.pets pet where pet.id = v_req.pet_id),
    'foster_user_id', v_foster_user_id,
    'temporary_holder_kind', 'PERSON',
    'temporary_holder_role', 'AUTHORIZED',
    'temporary_link_status', (
      select l.status
        from public.pet_responsibility_links l
       where l.pet_id = v_req.pet_id
         and l.holder_kind = 'PERSON'
         and l.holder_person_id = v_foster_user_id
         and l.role = 'AUTHORIZED'
         and l.status = 'ACTIVE'
       limit 1
    ),
    'requested_by', v_req.requested_by,
    'responsible_organization_id', (
      select l.holder_organization_id
        from public.pet_responsibility_links l
       where l.pet_id = v_req.pet_id
         and l.holder_kind = 'ORGANIZATION'
         and l.role = 'RESPONSIBLE'
         and l.status = 'ACTIVE'
       limit 1
    ),
    'responsible_role', 'RESPONSIBLE',
    'needs', v_req.needs,
    'notes', v_req.notes
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Privileges. Direct table SELECT stays revoked.
-- ---------------------------------------------------------------------------

revoke all on table public.foster_care_requests from public, anon, authenticated;
revoke all on table public.foster_care_applications from public, anon, authenticated;
revoke all on table public.foster_placements from public, anon, authenticated;
revoke all on table public.foster_profiles from public, anon, authenticated;
revoke all on table public.pet_responsibility_links from public, anon, authenticated;
revoke all on table public.vitacora_profiles from public, anon, authenticated;

revoke all on function public._canon_assert_foster_eligible() from public, anon, authenticated;
revoke all on function public._canon_can_manage_foster_request(uuid, uuid) from public, anon, authenticated;
revoke all on function public._canon_caller_may_read_foster_transit(uuid, uuid) from public, anon, authenticated;

revoke all on function public.canon_request_foster_for_pet(uuid, text, text) from public, anon;
grant execute on function public.canon_request_foster_for_pet(uuid, text, text) to authenticated;

revoke all on function public.canon_apply_to_foster_request(uuid) from public, anon;
grant execute on function public.canon_apply_to_foster_request(uuid) to authenticated;

revoke all on function public.canon_list_open_foster_requests() from public, anon;
grant execute on function public.canon_list_open_foster_requests() to authenticated;

revoke all on function public.canon_list_my_foster_requests() from public, anon;
grant execute on function public.canon_list_my_foster_requests() to authenticated;

revoke all on function public.canon_list_my_foster_applications() from public, anon;
grant execute on function public.canon_list_my_foster_applications() to authenticated;

revoke all on function public.canon_get_active_foster_transit(uuid) from public, anon;
grant execute on function public.canon_get_active_foster_transit(uuid) to authenticated;

comment on function public.canon_list_open_foster_requests() is
  'Eligible foster discovery only. Active verified FOSTER profile and base location are required. species_pref, age_pref, accepts_treatment, other_animals_ok, and capacity are advisory and are not filters.';

comment on function public.canon_list_my_foster_requests() is
  'Requests the caller created or holds, excluding a foster applicant of that same request. Includes REQUESTED, MATCHED, ACTIVE, and terminal history for that manager only.';

comment on function public.canon_list_my_foster_applications() is
  'Applications whose foster_user_id is the caller. No argument can target another foster.';

comment on function public.canon_get_active_foster_transit(uuid) is
  'Read-only MATCHED/ACTIVE transit for the pet holder, the requester, or the SELECTED foster. Does not change custody, ownership, or VitaCora.';

comment on function public.canon_request_foster_for_pet(uuid, text, text) is
  'Returns the existing non-terminal request id for the pet. A second insert is rejected by foster_care_requests_one_nonterminal_pet_uidx. COMPLETED and CANCELLED history is preserved.';

notify pgrst, 'reload schema';
