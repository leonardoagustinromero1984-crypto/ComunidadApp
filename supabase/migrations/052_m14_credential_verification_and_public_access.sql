-- LeoVer M14 Bloque 3 — emisión, verificación humana y acceso público seguro.
-- Forward-only over 050/051. Does not modify 001–051 files.
-- Tables reused: pet_passports, pet_passport_credentials,
--   pet_passport_verification_requests, pet_passport_verification_decisions,
--   pet_passport_status_history.
-- No historia clínica / medical records. No remote apply from Cursor.

begin;

-- ---------------------------------------------------------------------------
-- 1. Status adjustment: UNDER_REVIEW on verification requests
-- ---------------------------------------------------------------------------
alter table public.pet_passport_verification_requests
  drop constraint if exists pet_passport_verification_requests_status_chk;

alter table public.pet_passport_verification_requests
  add constraint pet_passport_verification_requests_status_chk
  check (status in (
    'PENDING',
    'UNDER_REVIEW',
    'APPROVED',
    'REJECTED',
    'CANCELLED',
    'EXPIRED'
  ));

-- One open (non-final) request per credential
drop index if exists pet_passport_verification_requests_one_pending;
create unique index if not exists pet_passport_verification_requests_one_open
  on public.pet_passport_verification_requests(credential_id)
  where status in ('PENDING', 'UNDER_REVIEW');

create index if not exists pet_passport_verification_requests_queue_idx
  on public.pet_passport_verification_requests(status, requested_at);

create index if not exists pet_passport_credentials_issuer_lookup_idx
  on public.pet_passport_credentials(issuer_organization_id, issuer_professional_id, status);

-- request_id already UNIQUE on decisions (050) — keep as one-final-decision guard

-- ---------------------------------------------------------------------------
-- 2. Private helpers (B3)
-- ---------------------------------------------------------------------------
create or replace function public._m14_is_moderator(p_actor uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select p_actor is not null and public.has_permission('passport.moderate');
$$;

create or replace function public._m14_request_pet_id(p_request_id uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select p.pet_id
  from public.pet_passport_verification_requests r
  join public.pet_passport_credentials c on c.id = r.credential_id
  join public.pet_passports p on p.id = c.passport_id
  where r.id = p_request_id;
$$;

create or replace function public._m14_actor_is_pet_responsible(p_pet_id uuid, p_actor uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select p_actor is not null
    and p_pet_id is not null
    and public.m08_actor_has_active_responsibility(p_pet_id, p_actor);
$$;

create or replace function public._m14_active_professional_id(p_actor uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select vp.id
  from public.veterinary_professionals vp
  where vp.user_id = p_actor
    and vp.status = 'ACTIVE'
  order by vp.updated_at desc nulls last
  limit 1;
$$;

-- Review/decide authority: target issuer OR moderator. Never pet responsible / requester (unless moderator).
create or replace function public._m14_can_decide_request(p_request_id uuid, p_actor uuid) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  r public.pet_passport_verification_requests;
  v_pet uuid;
  v_ok boolean := false;
begin
  if p_actor is null then
    return false;
  end if;
  select * into r from public.pet_passport_verification_requests where id = p_request_id;
  if not found then
    return false;
  end if;
  v_pet := public._m14_request_pet_id(p_request_id);

  if public._m14_is_moderator(p_actor) then
    return true;
  end if;

  -- Anti-self-verification: requester and M08 responsibles cannot decide.
  if r.requested_by = p_actor then
    return false;
  end if;
  if public._m14_actor_is_pet_responsible(v_pet, p_actor) then
    return false;
  end if;

  if r.target_organization_id is not null
     and (
       public.has_org_permission(r.target_organization_id, 'passport.credential.verify')
       or public.has_org_permission(r.target_organization_id, 'passport.verify')
     ) then
    v_ok := true;
  end if;

  if r.target_professional_id is not null
     and exists (
       select 1 from public.veterinary_professionals vp
       where vp.id = r.target_professional_id
         and vp.user_id = p_actor
         and vp.status = 'ACTIVE'
     ) then
    v_ok := true;
  end if;

  -- Global passport.verify only when actor is not related to the pet as responsible.
  if public.has_permission('passport.verify') and not public._m14_actor_is_pet_responsible(v_pet, p_actor) then
    -- Still require belonging to target when a target is set.
    if r.target_organization_id is null and r.target_professional_id is null then
      v_ok := true;
    end if;
  end if;

  return v_ok;
end;
$$;

create or replace function public._m14_can_issue_verified(
  p_actor uuid,
  p_pet_id uuid,
  p_issuer_organization_id uuid,
  p_issuer_professional_id uuid
) returns boolean
language plpgsql stable security definer set search_path = public as $$
begin
  if p_actor is null or p_pet_id is null then
    return false;
  end if;
  if public._m14_is_moderator(p_actor) then
    return (p_issuer_organization_id is not null or p_issuer_professional_id is not null);
  end if;
  if public._m14_actor_is_pet_responsible(p_pet_id, p_actor) then
    return false;
  end if;
  if p_issuer_organization_id is not null
     and public.has_org_permission(p_issuer_organization_id, 'passport.credential.issue') then
    return true;
  end if;
  if p_issuer_professional_id is not null
     and exists (
       select 1 from public.veterinary_professionals vp
       where vp.id = p_issuer_professional_id
         and vp.user_id = p_actor
         and vp.status = 'ACTIVE'
     ) then
    return true;
  end if;
  return false;
end;
$$;

create or replace function public._m14_can_revoke_credential(
  p_credential_id uuid,
  p_actor uuid
) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  c public.pet_passport_credentials;
begin
  if p_actor is null then
    return false;
  end if;
  select * into c from public.pet_passport_credentials where id = p_credential_id;
  if not found then
    return false;
  end if;
  if public._m14_is_moderator(p_actor) then
    return true;
  end if;
  if c.issuer_organization_id is not null
     and (
       public.has_org_permission(c.issuer_organization_id, 'passport.credential.verify')
       or public.has_org_permission(c.issuer_organization_id, 'passport.credential.issue')
       or public.has_org_permission(c.issuer_organization_id, 'passport.verify')
     ) then
    return true;
  end if;
  if c.issuer_professional_id is not null
     and exists (
       select 1 from public.veterinary_professionals vp
       where vp.id = c.issuer_professional_id
         and vp.user_id = p_actor
         and vp.status = 'ACTIVE'
     ) then
    return true;
  end if;
  return false;
end;
$$;

create or replace function public._m14_decision_json(
  p_d public.pet_passport_verification_decisions
) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', p_d.id,
    'request_id', p_d.request_id,
    'decision', p_d.decision,
    'actor_user_id', p_d.actor_user_id,
    'actor_authority', p_d.actor_authority,
    'reason_code', p_d.reason_code,
    'note_private', p_d.note_private,
    'created_at', p_d.created_at
  );
$$;

create or replace function public._m14_request_json(
  p_r public.pet_passport_verification_requests
) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', p_r.id,
    'credential_id', p_r.credential_id,
    'requested_by', p_r.requested_by,
    'target_organization_id', p_r.target_organization_id,
    'target_professional_id', p_r.target_professional_id,
    'status', p_r.status,
    'requested_at', p_r.requested_at,
    'resolved_at', p_r.resolved_at,
    'resolution_reason', p_r.resolution_reason
  );
$$;

create or replace function public._m14_history_json(
  p_h public.pet_passport_status_history
) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', p_h.id,
    'passport_id', p_h.passport_id,
    'from_status', p_h.from_status,
    'to_status', p_h.to_status,
    'actor_user_id', p_h.actor_user_id,
    'reason', p_h.reason,
    'created_at', p_h.created_at,
    'metadata', p_h.metadata
  );
$$;

-- Idempotent final decision writer (APPROVED/REJECTED only).
create or replace function public._m14_write_final_decision(
  p_request_id uuid,
  p_decision text,
  p_actor uuid,
  p_authority text,
  p_reason_code text,
  p_note_private text
) returns public.pet_passport_verification_decisions
language plpgsql security definer set search_path = public as $$
declare
  v_existing public.pet_passport_verification_decisions;
  v_row public.pet_passport_verification_decisions;
  v_reason text := nullif(trim(coalesce(p_reason_code, '')), '');
  v_note text := nullif(trim(coalesce(p_note_private, '')), '');
begin
  if p_decision not in ('APPROVED', 'REJECTED') then
    raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION';
  end if;
  if v_reason is null then
    v_reason := p_decision;
  end if;
  if v_note is not null and char_length(v_note) > 2000 then
    raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION';
  end if;

  select * into v_existing
  from public.pet_passport_verification_decisions
  where request_id = p_request_id;

  if found then
    if v_existing.decision = p_decision then
      return v_existing;
    end if;
    raise exception using errcode = 'P0001', message = 'DECISION_ALREADY_EXISTS';
  end if;

  begin
    insert into public.pet_passport_verification_decisions (
      request_id, decision, actor_user_id, actor_authority, reason_code, note_private, created_at
    ) values (
      p_request_id, p_decision, p_actor, p_authority, v_reason, v_note, timezone('utc', now())
    )
    returning * into v_row;
  exception
    when unique_violation then
      select * into v_existing
      from public.pet_passport_verification_decisions
      where request_id = p_request_id;
      if found and v_existing.decision = p_decision then
        return v_existing;
      end if;
      raise exception using errcode = 'P0001', message = 'CONFLICT';
  end;

  return v_row;
end;
$$;

create or replace function public._m14_append_credential_event(
  p_passport_id uuid,
  p_actor uuid,
  p_reason text,
  p_event text,
  p_credential_id uuid default null,
  p_request_id uuid default null
) returns void
language plpgsql security definer set search_path = public as $$
declare
  v_status text;
begin
  select status into v_status from public.pet_passports where id = p_passport_id;
  if not found then
    return;
  end if;
  -- Keep passport status unchanged; record typed metadata only.
  perform public._m14_append_passport_history(
    p_passport_id,
    v_status,
    v_status,
    p_actor,
    p_reason,
    jsonb_strip_nulls(jsonb_build_object(
      'event', p_event,
      'credential_id', p_credential_id,
      'request_id', p_request_id
    ))
  );
end;
$$;

create or replace function public._m14_decide_authority_label(
  p_actor uuid,
  p_request public.pet_passport_verification_requests
) returns text
language plpgsql stable security definer set search_path = public as $$
begin
  if public._m14_is_moderator(p_actor) then
    return 'MODERATOR';
  end if;
  if p_request.target_organization_id is not null
     and public.has_org_permission(p_request.target_organization_id, 'passport.credential.verify') then
    return 'ORG_CREDENTIAL_VERIFY';
  end if;
  if p_request.target_organization_id is not null
     and public.has_org_permission(p_request.target_organization_id, 'passport.verify') then
    return 'ORG_VERIFY';
  end if;
  if p_request.target_professional_id is not null then
    return 'M12_PROFESSIONAL';
  end if;
  if public.has_permission('passport.verify') then
    return 'PASSPORT_VERIFY';
  end if;
  return 'UNKNOWN';
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. Client RPCs (10)
-- ---------------------------------------------------------------------------

-- PENDING -> UNDER_REVIEW
create or replace function public.m14_open_verification_review(p_request_id uuid)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  r public.pet_passport_verification_requests;
  c public.pet_passport_credentials;
  p public.pet_passports;
begin
  select * into r from public.pet_passport_verification_requests where id = p_request_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_NOT_FOUND';
  end if;
  if not public._m14_can_decide_request(r.id, actor) then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_REVIEW_NOT_ALLOWED';
  end if;
  if r.status = 'UNDER_REVIEW' then
    return public._m14_request_json(r); -- idempotent
  end if;
  if r.status in ('APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED') then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_ALREADY_FINAL';
  end if;
  if r.status <> 'PENDING' then
    raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION';
  end if;

  update public.pet_passport_verification_requests
    set status = 'UNDER_REVIEW', updated_at = timezone('utc', now())
  where id = r.id
  returning * into r;

  select * into c from public.pet_passport_credentials where id = r.credential_id;
  select * into p from public.pet_passports where id = c.passport_id;
  perform public._m14_append_credential_event(
    p.id, actor, 'REVIEW_OPENED', 'VERIFICATION_REVIEW_OPENED', c.id, r.id
  );
  perform public._m14_best_effort_audit('m14.verification.review_opened', 'UPDATE', p.id);
  return public._m14_request_json(r);
end;
$$;

create or replace function public.m14_approve_verification_request(
  p_request_id uuid,
  p_reason_code text default null,
  p_note_private text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  r public.pet_passport_verification_requests;
  c public.pet_passport_credentials;
  p public.pet_passports;
  d public.pet_passport_verification_decisions;
  v_auth text;
begin
  select * into r from public.pet_passport_verification_requests where id = p_request_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_NOT_FOUND';
  end if;
  if not public._m14_can_decide_request(r.id, actor) then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_REVIEW_NOT_ALLOWED';
  end if;

  -- Block direct approve from PENDING
  if r.status = 'PENDING' then
    raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION';
  end if;

  select * into c from public.pet_passport_credentials where id = r.credential_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'CREDENTIAL_NOT_FOUND';
  end if;
  select * into p from public.pet_passports where id = c.passport_id;

  if r.status in ('APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED') then
    select * into d from public.pet_passport_verification_decisions where request_id = r.id;
    if found and d.decision = 'APPROVED' then
      return jsonb_build_object('request', public._m14_request_json(r), 'decision', public._m14_decision_json(d), 'credential', public._m14_credential_json(c, true));
    end if;
    raise exception using errcode = 'P0001', message = 'VERIFICATION_ALREADY_FINAL';
  end if;
  if r.status <> 'UNDER_REVIEW' then
    raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION';
  end if;

  v_auth := public._m14_decide_authority_label(actor, r);
  d := public._m14_write_final_decision(r.id, 'APPROVED', actor, v_auth, p_reason_code, p_note_private);

  update public.pet_passport_verification_requests
    set status = 'APPROVED',
        resolved_at = timezone('utc', now()),
        resolution_reason = coalesce(nullif(trim(p_reason_code), ''), 'APPROVED'),
        updated_at = timezone('utc', now())
  where id = r.id
  returning * into r;

  if c.status = 'PENDING_VERIFICATION' then
    update public.pet_passport_credentials
      set status = 'VERIFIED',
          issuer_organization_id = coalesce(c.issuer_organization_id, r.target_organization_id),
          issuer_professional_id = coalesce(c.issuer_professional_id, r.target_professional_id),
          updated_at = timezone('utc', now())
    where id = c.id
    returning * into c;
  elsif c.status = 'VERIFIED' then
    null; -- idempotent
  else
    raise exception using errcode = 'P0001', message = 'CREDENTIAL_ALREADY_FINAL';
  end if;

  perform public._m14_append_credential_event(
    p.id, actor, coalesce(nullif(trim(p_reason_code), ''), 'APPROVED'), 'VERIFICATION_APPROVED', c.id, r.id
  );
  perform public._m14_best_effort_audit('m14.verification.approved', 'UPDATE', p.id);
  return jsonb_build_object(
    'request', public._m14_request_json(r),
    'decision', public._m14_decision_json(d),
    'credential', public._m14_credential_json(c, true)
  );
end;
$$;

create or replace function public.m14_reject_verification_request(
  p_request_id uuid,
  p_reason_code text default null,
  p_note_private text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  r public.pet_passport_verification_requests;
  c public.pet_passport_credentials;
  p public.pet_passports;
  d public.pet_passport_verification_decisions;
  v_auth text;
begin
  select * into r from public.pet_passport_verification_requests where id = p_request_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_NOT_FOUND';
  end if;
  if not public._m14_can_decide_request(r.id, actor) then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_REVIEW_NOT_ALLOWED';
  end if;
  if r.status = 'PENDING' then
    raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION';
  end if;

  select * into c from public.pet_passport_credentials where id = r.credential_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'CREDENTIAL_NOT_FOUND';
  end if;
  select * into p from public.pet_passports where id = c.passport_id;

  if r.status in ('APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED') then
    select * into d from public.pet_passport_verification_decisions where request_id = r.id;
    if found and d.decision = 'REJECTED' then
      return jsonb_build_object('request', public._m14_request_json(r), 'decision', public._m14_decision_json(d), 'credential', public._m14_credential_json(c, true));
    end if;
    raise exception using errcode = 'P0001', message = 'VERIFICATION_ALREADY_FINAL';
  end if;
  if r.status <> 'UNDER_REVIEW' then
    raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION';
  end if;

  v_auth := public._m14_decide_authority_label(actor, r);
  d := public._m14_write_final_decision(r.id, 'REJECTED', actor, v_auth, p_reason_code, p_note_private);

  update public.pet_passport_verification_requests
    set status = 'REJECTED',
        resolved_at = timezone('utc', now()),
        resolution_reason = coalesce(nullif(trim(p_reason_code), ''), 'REJECTED'),
        updated_at = timezone('utc', now())
  where id = r.id
  returning * into r;

  if c.status = 'PENDING_VERIFICATION' then
    update public.pet_passport_credentials
      set status = 'REJECTED', updated_at = timezone('utc', now())
    where id = c.id
    returning * into c;
  end if;

  perform public._m14_append_credential_event(
    p.id, actor, coalesce(nullif(trim(p_reason_code), ''), 'REJECTED'), 'VERIFICATION_REJECTED', c.id, r.id
  );
  perform public._m14_best_effort_audit('m14.verification.rejected', 'UPDATE', p.id);
  return jsonb_build_object(
    'request', public._m14_request_json(r),
    'decision', public._m14_decision_json(d),
    'credential', public._m14_credential_json(c, true)
  );
end;
$$;

create or replace function public.m14_expire_verification_request(p_request_id uuid)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  r public.pet_passport_verification_requests;
  c public.pet_passport_credentials;
  p public.pet_passports;
begin
  select * into r from public.pet_passport_verification_requests where id = p_request_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_NOT_FOUND';
  end if;
  -- Expire: decide authority OR requester OR moderator
  if not (
    public._m14_can_decide_request(r.id, actor)
    or r.requested_by = actor
    or public._m14_is_moderator(actor)
  ) then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_REVIEW_NOT_ALLOWED';
  end if;

  select * into c from public.pet_passport_credentials where id = r.credential_id for update;
  select * into p from public.pet_passports where id = c.passport_id;

  if r.status = 'EXPIRED' then
    return public._m14_request_json(r);
  end if;
  if r.status in ('APPROVED', 'REJECTED', 'CANCELLED') then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_ALREADY_FINAL';
  end if;
  if r.status not in ('PENDING', 'UNDER_REVIEW') then
    raise exception using errcode = 'P0001', message = 'INVALID_TRANSITION';
  end if;

  -- No decision row for expire (no approve/reject)
  update public.pet_passport_verification_requests
    set status = 'EXPIRED',
        resolved_at = timezone('utc', now()),
        resolution_reason = 'EXPIRED',
        updated_at = timezone('utc', now())
  where id = r.id
  returning * into r;

  if c.status = 'PENDING_VERIFICATION' then
    if c.expires_at is not null and c.expires_at <= timezone('utc', now()) then
      update public.pet_passport_credentials
        set status = 'EXPIRED', updated_at = timezone('utc', now())
      where id = c.id;
    else
      update public.pet_passport_credentials
        set status = 'DRAFT', updated_at = timezone('utc', now())
      where id = c.id;
    end if;
  end if;

  perform public._m14_append_credential_event(
    p.id, actor, 'EXPIRED', 'VERIFICATION_EXPIRED', c.id, r.id
  );
  perform public._m14_best_effort_audit('m14.verification.expired', 'UPDATE', p.id);
  return public._m14_request_json(r);
end;
$$;

create or replace function public.m14_get_verification_decision(p_request_id uuid)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  r public.pet_passport_verification_requests;
  d public.pet_passport_verification_decisions;
begin
  select * into r from public.pet_passport_verification_requests where id = p_request_id;
  if not found then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_NOT_FOUND';
  end if;
  if r.requested_by <> actor
     and not public._m14_can_manage_request(r.id, actor)
     and not public._m14_can_decide_request(r.id, actor) then
    raise exception using errcode = 'P0001', message = 'UNAUTHORIZED';
  end if;
  select * into d from public.pet_passport_verification_decisions where request_id = p_request_id;
  if not found then
    raise exception using errcode = 'P0001', message = 'DECISION_NOT_FOUND';
  end if;
  return public._m14_decision_json(d);
end;
$$;

create or replace function public.m14_list_verification_decisions(p_request_id uuid)
returns setof jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  r public.pet_passport_verification_requests;
begin
  select * into r from public.pet_passport_verification_requests where id = p_request_id;
  if not found then
    raise exception using errcode = 'P0001', message = 'VERIFICATION_NOT_FOUND';
  end if;
  if r.requested_by <> actor
     and not public._m14_can_manage_request(r.id, actor)
     and not public._m14_can_decide_request(r.id, actor) then
    raise exception using errcode = 'P0001', message = 'UNAUTHORIZED';
  end if;
  return query
    select public._m14_decision_json(d)
    from public.pet_passport_verification_decisions d
    where d.request_id = p_request_id
    order by d.created_at desc;
end;
$$;

create or replace function public.m14_issue_verified_credential(
  p_passport_id uuid,
  p_type text,
  p_title text,
  p_issuer_organization_id uuid default null,
  p_issuer_professional_id uuid default null,
  p_issued_at timestamptz default null,
  p_expires_at timestamptz default null,
  p_visibility text default 'PRIVATE',
  p_media_refs text[] default '{}',
  p_external_reference_masked text default null,
  p_note_private text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  p public.pet_passports;
  c public.pet_passport_credentials;
  v_org uuid := p_issuer_organization_id;
  v_prof uuid := p_issuer_professional_id;
begin
  select * into p from public.pet_passports where id = p_passport_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'PASSPORT_NOT_FOUND';
  end if;
  if p.status not in ('DRAFT', 'ACTIVE') then
    raise exception using errcode = 'P0001', message = 'INVALID_PASSPORT_STATUS';
  end if;
  if v_org is null and v_prof is null then
    v_prof := public._m14_active_professional_id(actor);
  end if;
  if v_org is null and v_prof is null then
    raise exception using errcode = 'P0001', message = 'ISSUER_NOT_AUTHORIZED';
  end if;
  if not public._m14_can_issue_verified(actor, p.pet_id, v_org, v_prof) then
    raise exception using errcode = 'P0001', message = 'SELF_VERIFICATION_NOT_ALLOWED';
  end if;
  if p_expires_at is not null and p_issued_at is not null and p_expires_at <= p_issued_at then
    raise exception using errcode = 'P0001', message = 'INVALID_CREDENTIAL_DATES';
  end if;
  perform public._m14_assert_media_refs(p_media_refs);

  insert into public.pet_passport_credentials(
    passport_id, type, title,
    issuer_organization_id, issuer_professional_id,
    issued_at, expires_at, status, visibility,
    media_refs, external_reference_masked, note_private,
    created_by, created_at, updated_at
  ) values (
    p.id,
    upper(trim(p_type)),
    trim(p_title),
    v_org,
    v_prof,
    coalesce(p_issued_at, timezone('utc', now())),
    p_expires_at,
    'VERIFIED',
    coalesce(p_visibility, 'PRIVATE'),
    coalesce(p_media_refs, '{}'::text[]),
    nullif(trim(p_external_reference_masked), ''),
    nullif(trim(p_note_private), ''),
    actor,
    timezone('utc', now()),
    timezone('utc', now())
  ) returning * into c;

  perform public._m14_append_credential_event(
    p.id, actor, 'ISSUED', 'CREDENTIAL_ISSUED', c.id, null
  );
  perform public._m14_best_effort_audit('m14.credential.issued', 'CREATE', p.id);
  return public._m14_credential_json(c, true);
end;
$$;

create or replace function public.m14_revoke_verified_credential(
  p_credential_id uuid,
  p_reason_code text default null,
  p_note_private text default null
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  c public.pet_passport_credentials;
  p public.pet_passports;
begin
  select * into c from public.pet_passport_credentials where id = p_credential_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'CREDENTIAL_NOT_FOUND';
  end if;
  select * into p from public.pet_passports where id = c.passport_id;

  if c.status = 'REVOKED' then
    return public._m14_credential_json(c, true); -- idempotent
  end if;
  if c.status <> 'VERIFIED' then
    raise exception using errcode = 'P0001', message = 'CREDENTIAL_REVOCATION_NOT_ALLOWED';
  end if;
  if not public._m14_can_revoke_credential(c.id, actor) then
    raise exception using errcode = 'P0001', message = 'CREDENTIAL_REVOCATION_NOT_ALLOWED';
  end if;

  update public.pet_passport_credentials
    set status = 'REVOKED',
        note_private = coalesce(nullif(trim(p_note_private), ''), c.note_private),
        updated_at = timezone('utc', now())
  where id = c.id
  returning * into c;

  perform public._m14_append_credential_event(
    p.id, actor, coalesce(nullif(trim(p_reason_code), ''), 'REVOKED'), 'CREDENTIAL_REVOKED', c.id, null
  );
  perform public._m14_best_effort_audit('m14.credential.revoked', 'UPDATE', p.id);
  return public._m14_credential_json(c, true);
end;
$$;

create or replace function public.m14_rotate_public_code(p_passport_id uuid)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
  p public.pet_passports;
  v_old text;
  v_new text;
begin
  select * into p from public.pet_passports where id = p_passport_id for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'PASSPORT_NOT_FOUND';
  end if;
  if not (
    public._m14_can_manage_pet(p.pet_id, actor)
    or public._m14_is_moderator(actor)
  ) then
    raise exception using errcode = 'P0001', message = 'PUBLIC_CODE_ROTATION_NOT_ALLOWED';
  end if;
  if p.status in ('REVOKED', 'ARCHIVED') then
    raise exception using errcode = 'P0001', message = 'PUBLIC_CODE_ROTATION_NOT_ALLOWED';
  end if;

  v_old := p.public_code;
  v_new := public._m14_generate_public_code();

  update public.pet_passports
    set public_code = v_new,
        updated_at = timezone('utc', now())
  where id = p.id
  returning * into p;

  perform public._m14_append_passport_history(
    p.id, p.status, p.status, actor, 'PUBLIC_CODE_ROTATED',
    jsonb_build_object('event', 'PUBLIC_CODE_ROTATED', 'previous_code_rotated', true)
  );
  perform public._m14_best_effort_audit('m14.passport.public_code_rotated', 'UPDATE', p.id);

  return jsonb_build_object(
    'id', p.id,
    'passport_number', p.passport_number,
    'public_code', p.public_code,
    'status', p.status,
    'updated_at', p.updated_at
  );
end;
$$;

create or replace function public.m14_list_passport_status_history(p_passport_id uuid)
returns setof jsonb
language plpgsql security definer set search_path = public as $$
declare
  actor uuid := public._m14_require_auth();
begin
  if not public._m14_can_manage_passport(p_passport_id, actor)
     and not public._m14_is_moderator(actor) then
    raise exception using errcode = 'P0001', message = 'UNAUTHORIZED';
  end if;
  return query
    select public._m14_history_json(h)
    from public.pet_passport_status_history h
    where h.passport_id = p_passport_id
    order by h.created_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. Grants — authenticated EXECUTE only; anon/PUBLIC revoked; helpers locked
-- ---------------------------------------------------------------------------
do $grants$
declare
  f text;
begin
  foreach f in array array[
    '_m14_is_moderator(uuid)',
    '_m14_request_pet_id(uuid)',
    '_m14_actor_is_pet_responsible(uuid,uuid)',
    '_m14_active_professional_id(uuid)',
    '_m14_can_decide_request(uuid,uuid)',
    '_m14_can_issue_verified(uuid,uuid,uuid,uuid)',
    '_m14_can_revoke_credential(uuid,uuid)',
    '_m14_decision_json(pet_passport_verification_decisions)',
    '_m14_request_json(pet_passport_verification_requests)',
    '_m14_history_json(pet_passport_status_history)',
    '_m14_write_final_decision(uuid,text,uuid,text,text,text)',
    '_m14_append_credential_event(uuid,uuid,text,text,uuid,uuid)',
    '_m14_decide_authority_label(uuid,pet_passport_verification_requests)'
  ] loop
    execute format('revoke all on function public.%s from public, anon, authenticated', f);
  end loop;

  foreach f in array array[
    'm14_open_verification_review(uuid)',
    'm14_approve_verification_request(uuid,text,text)',
    'm14_reject_verification_request(uuid,text,text)',
    'm14_expire_verification_request(uuid)',
    'm14_get_verification_decision(uuid)',
    'm14_list_verification_decisions(uuid)',
    'm14_issue_verified_credential(uuid,text,text,uuid,uuid,timestamptz,timestamptz,text,text[],text,text)',
    'm14_revoke_verified_credential(uuid,text,text)',
    'm14_rotate_public_code(uuid)',
    'm14_list_passport_status_history(uuid)'
  ] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end;
$grants$;

-- Preserve 051 table privilege posture (SELECT authenticated only; no DML)
revoke all privileges on table public.pet_passports from authenticated, anon;
revoke all privileges on table public.pet_passport_credentials from authenticated, anon;
revoke all privileges on table public.pet_passport_verification_requests from authenticated, anon;
revoke all privileges on table public.pet_passport_verification_decisions from authenticated, anon;
revoke all privileges on table public.pet_passport_status_history from authenticated, anon;

grant select on table public.pet_passports to authenticated;
grant select on table public.pet_passport_credentials to authenticated;
grant select on table public.pet_passport_verification_requests to authenticated;
grant select on table public.pet_passport_verification_decisions to authenticated;
grant select on table public.pet_passport_status_history to authenticated;

commit;
