-- =============================================================================
-- LeoVer M13 — migración 049: revisión humana remota de coincidencias
-- Forward-only sobre 001–048. Reutiliza tablas/helpers de 048.
-- Sin autoconfirmación. Sin cierre automático de lost_found_posts.
-- LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Una decisión final por candidato (estructura 048 + índice único)
-- ---------------------------------------------------------------------------
create unique index if not exists lost_found_match_decisions_candidate_uniq
  on public.lost_found_match_decisions (candidate_id);

-- ---------------------------------------------------------------------------
-- 1. Helpers internos de autoridad / decisión / JSON
-- ---------------------------------------------------------------------------
create or replace function public._m13_require_auth()
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return v_uid;
end;
$$;

create or replace function public._m13_is_terminal_match_status(p_status text)
returns boolean
language sql
immutable
as $$
  select p_status = any (array[
    'CONFIRMED','REJECTED','INCONCLUSIVE','WITHDRAWN','EXPIRED'
  ]::text[]);
$$;

-- Apertura / retiro / expiración / inconclusa (review) + dueño / moderate.
create or replace function public._m13_can_review_match(p_case_id uuid, p_actor uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_actor is null or p_case_id is null then
    return false;
  end if;
  if p_actor <> auth.uid() then
    return false;
  end if;
  if public._m13_is_case_owner(p_case_id, p_actor) then
    return true;
  end if;
  if public.has_permission('lostfound.match.review')
     or public.has_permission('lostfound.sighting.moderate') then
    return true;
  end if;
  return false;
end;
$$;

-- Confirm / reject (decisión final) + dueño / confirm / moderate.
create or replace function public._m13_can_confirm_match(p_case_id uuid, p_actor uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_actor is null or p_case_id is null then
    return false;
  end if;
  if p_actor <> auth.uid() then
    return false;
  end if;
  if public._m13_is_case_owner(p_case_id, p_actor) then
    return true;
  end if;
  if public.has_permission('lostfound.match.confirm')
     or public.has_permission('lostfound.sighting.moderate') then
    return true;
  end if;
  return false;
end;
$$;

create or replace function public._m13_resolve_match_actor_authority(
  p_case_id uuid,
  p_actor uuid
) returns text
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if public._m13_is_case_owner(p_case_id, p_actor) then
    return 'CASE_OWNER';
  end if;
  if public.has_permission('lostfound.sighting.moderate') then
    return 'MODERATOR';
  end if;
  if public.has_permission('lostfound.match.confirm')
     or public.has_permission('lostfound.match.review') then
    return 'ORG_MANAGER';
  end if;
  return 'REPORTER';
end;
$$;

create or replace function public._m13_decision_json(
  p_d public.lost_found_match_decisions
) returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', p_d.id,
    'candidate_id', p_d.candidate_id,
    'decision', p_d.decision,
    'actor_user_id', p_d.actor_user_id,
    'actor_authority', p_d.actor_authority,
    'reason_code', p_d.reason_code,
    -- note_private solo vía RPC autorizada; no se omite para gestores del caso.
    'note_private', p_d.note_private,
    'created_at', p_d.created_at
  );
$$;

create or replace function public._m13_status_history_json(
  p_h public.lost_found_match_status_history
) returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', p_h.id,
    'candidate_id', p_h.candidate_id,
    'from_status', p_h.from_status,
    'to_status', p_h.to_status,
    'changed_by', p_h.changed_by,
    'reason', p_h.reason,
    'created_at', p_h.created_at
  );
$$;

-- Escribe decisión final idempotente; lanza CONFLICT / DECISION_ALREADY_EXISTS si choca.
create or replace function public._m13_write_final_decision(
  p_candidate_id uuid,
  p_decision text,
  p_actor uuid,
  p_authority text,
  p_reason_code text,
  p_note_private text
) returns public.lost_found_match_decisions
language plpgsql
security definer
set search_path = public
as $$
declare
  v_existing public.lost_found_match_decisions;
  v_row public.lost_found_match_decisions;
  v_reason text := nullif(trim(coalesce(p_reason_code, '')), '');
  v_note text := nullif(trim(coalesce(p_note_private, '')), '');
begin
  if v_reason is null then
    v_reason := p_decision;
  end if;
  if v_note is not null and char_length(v_note) > 2000 then
    raise exception 'SIGHTING_INVALID';
  end if;

  select * into v_existing
  from public.lost_found_match_decisions
  where candidate_id = p_candidate_id;

  if found then
    if v_existing.decision = p_decision then
      return v_existing; -- idempotente
    end if;
    raise exception 'DECISION_ALREADY_EXISTS';
  end if;

  begin
    insert into public.lost_found_match_decisions (
      candidate_id, decision, actor_user_id, actor_authority, reason_code, note_private
    ) values (
      p_candidate_id, p_decision, p_actor, p_authority, v_reason, v_note
    )
    returning * into v_row;
  exception
    when unique_violation then
      select * into v_existing
      from public.lost_found_match_decisions
      where candidate_id = p_candidate_id;
      if found and v_existing.decision = p_decision then
        return v_existing;
      end if;
      raise exception 'CONFLICT';
  end;

  return v_row;
end;
$$;

create or replace function public._m13_mark_sighting_confirmed_for_match(
  p_sighting_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.lost_found_sighting_details
  set status = 'CONFIRMED',
      updated_at = timezone('utc', now())
  where sighting_id = p_sighting_id
    and status = 'ACTIVE';
  -- No tocar lost_found_posts.status (sin cierre automático del caso).
end;
$$;

create or replace function public._m13_audit_match_decision(
  p_event_key text,
  p_candidate_id uuid,
  p_decision text,
  p_case_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_corr text := replace(gen_random_uuid()::text, '-', '');
begin
  perform public.m07_best_effort_audit(
    p_event_key,
    'MATCH_DECISION',
    'SUCCESS',
    v_corr,
    'lost_found_match_candidate',
    p_candidate_id::text,
    jsonb_build_object(
      'result', 'SUCCESS',
      'module', 'M13',
      'decision', p_decision,
      'case_id', p_case_id
    )
  );
exception when others then
  null;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2. RPC cliente — open / confirm / reject / inconclusive
-- ---------------------------------------------------------------------------
create or replace function public.m13_open_match_review(p_candidate_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
begin
  select * into v_c
  from public.lost_found_match_candidates
  where id = p_candidate_id
  for update;

  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  if not public._m13_can_review_match(v_c.case_id, v_actor) then
    raise exception 'MATCH_REVIEW_NOT_ALLOWED';
  end if;

  if public._m13_is_terminal_match_status(v_c.status) then
    raise exception 'MATCH_ALREADY_FINAL';
  end if;

  if v_c.status = 'UNDER_REVIEW' then
    return public._m13_candidate_json(v_c); -- idempotente
  end if;

  if v_c.status <> 'PROPOSED' then
    raise exception 'INVALID_TRANSITION';
  end if;

  update public.lost_found_match_candidates
  set status = 'UNDER_REVIEW',
      updated_at = timezone('utc', now())
  where id = v_c.id
  returning * into v_c;

  perform public._m13_append_candidate_history(
    v_c.id, 'PROPOSED', 'UNDER_REVIEW', v_actor, 'OPEN_REVIEW'
  );

  return public._m13_candidate_json(v_c);
end;
$$;

create or replace function public.m13_confirm_match_candidate(
  p_candidate_id uuid,
  p_reason_code text,
  p_note_private text
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
  v_auth text;
  v_from text;
begin
  select * into v_c
  from public.lost_found_match_candidates
  where id = p_candidate_id
  for update;

  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  if not public._m13_can_confirm_match(v_c.case_id, v_actor) then
    raise exception 'UNAUTHORIZED';
  end if;

  if v_c.status = 'CONFIRMED' then
    -- Idempotente: misma decisión final.
    perform public._m13_write_final_decision(
      v_c.id, 'CONFIRMED', v_actor,
      public._m13_resolve_match_actor_authority(v_c.case_id, v_actor),
      coalesce(p_reason_code, 'HUMAN_CONFIRM'),
      p_note_private
    );
    return public._m13_candidate_json(v_c);
  end if;

  if public._m13_is_terminal_match_status(v_c.status) then
    raise exception 'MATCH_ALREADY_FINAL';
  end if;

  if v_c.status <> 'UNDER_REVIEW' then
    raise exception 'INVALID_TRANSITION';
  end if;

  v_from := v_c.status;
  v_auth := public._m13_resolve_match_actor_authority(v_c.case_id, v_actor);

  perform public._m13_write_final_decision(
    v_c.id, 'CONFIRMED', v_actor, v_auth,
    coalesce(p_reason_code, 'HUMAN_CONFIRM'),
    p_note_private
  );

  update public.lost_found_match_candidates
  set status = 'CONFIRMED',
      updated_at = timezone('utc', now())
  where id = v_c.id
  returning * into v_c;

  perform public._m13_append_candidate_history(
    v_c.id, v_from, 'CONFIRMED', v_actor, coalesce(nullif(trim(p_reason_code), ''), 'HUMAN_CONFIRM')
  );

  perform public._m13_mark_sighting_confirmed_for_match(v_c.sighting_id);

  perform public._m13_audit_match_decision(
    'm13.match.confirmed', v_c.id, 'CONFIRMED', v_c.case_id
  );

  -- Explicitamente NO actualizar lost_found_posts.
  return public._m13_candidate_json(v_c);
end;
$$;

create or replace function public.m13_reject_match_candidate(
  p_candidate_id uuid,
  p_reason_code text,
  p_note_private text
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
  v_auth text;
  v_from text;
begin
  select * into v_c
  from public.lost_found_match_candidates
  where id = p_candidate_id
  for update;

  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  if not public._m13_can_confirm_match(v_c.case_id, v_actor) then
    raise exception 'UNAUTHORIZED';
  end if;

  if v_c.status = 'REJECTED' then
    perform public._m13_write_final_decision(
      v_c.id, 'REJECTED', v_actor,
      public._m13_resolve_match_actor_authority(v_c.case_id, v_actor),
      coalesce(p_reason_code, 'HUMAN_REJECT'),
      p_note_private
    );
    return public._m13_candidate_json(v_c);
  end if;

  if public._m13_is_terminal_match_status(v_c.status) then
    raise exception 'MATCH_ALREADY_FINAL';
  end if;

  if v_c.status <> 'UNDER_REVIEW' then
    raise exception 'INVALID_TRANSITION';
  end if;

  v_from := v_c.status;
  v_auth := public._m13_resolve_match_actor_authority(v_c.case_id, v_actor);

  perform public._m13_write_final_decision(
    v_c.id, 'REJECTED', v_actor, v_auth,
    coalesce(p_reason_code, 'HUMAN_REJECT'),
    p_note_private
  );

  update public.lost_found_match_candidates
  set status = 'REJECTED',
      updated_at = timezone('utc', now())
  where id = v_c.id
  returning * into v_c;

  perform public._m13_append_candidate_history(
    v_c.id, v_from, 'REJECTED', v_actor, coalesce(nullif(trim(p_reason_code), ''), 'HUMAN_REJECT')
  );

  perform public._m13_audit_match_decision(
    'm13.match.rejected', v_c.id, 'REJECTED', v_c.case_id
  );

  return public._m13_candidate_json(v_c);
end;
$$;

create or replace function public.m13_mark_match_inconclusive(
  p_candidate_id uuid,
  p_reason_code text,
  p_note_private text
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
  v_auth text;
  v_from text;
begin
  select * into v_c
  from public.lost_found_match_candidates
  where id = p_candidate_id
  for update;

  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  -- Inconclusa: review (contrato prompt) o confirm/owner/moderate.
  if not (
    public._m13_can_review_match(v_c.case_id, v_actor)
    or public._m13_can_confirm_match(v_c.case_id, v_actor)
  ) then
    raise exception 'UNAUTHORIZED';
  end if;

  if v_c.status = 'INCONCLUSIVE' then
    perform public._m13_write_final_decision(
      v_c.id, 'INCONCLUSIVE', v_actor,
      public._m13_resolve_match_actor_authority(v_c.case_id, v_actor),
      coalesce(p_reason_code, 'HUMAN_INCONCLUSIVE'),
      p_note_private
    );
    return public._m13_candidate_json(v_c);
  end if;

  if public._m13_is_terminal_match_status(v_c.status) then
    raise exception 'MATCH_ALREADY_FINAL';
  end if;

  if v_c.status <> 'UNDER_REVIEW' then
    raise exception 'INVALID_TRANSITION';
  end if;

  v_from := v_c.status;
  v_auth := public._m13_resolve_match_actor_authority(v_c.case_id, v_actor);

  perform public._m13_write_final_decision(
    v_c.id, 'INCONCLUSIVE', v_actor, v_auth,
    coalesce(p_reason_code, 'HUMAN_INCONCLUSIVE'),
    p_note_private
  );

  update public.lost_found_match_candidates
  set status = 'INCONCLUSIVE',
      updated_at = timezone('utc', now())
  where id = v_c.id
  returning * into v_c;

  perform public._m13_append_candidate_history(
    v_c.id, v_from, 'INCONCLUSIVE', v_actor,
    coalesce(nullif(trim(p_reason_code), ''), 'HUMAN_INCONCLUSIVE')
  );

  perform public._m13_audit_match_decision(
    'm13.match.inconclusive', v_c.id, 'INCONCLUSIVE', v_c.case_id
  );

  return public._m13_candidate_json(v_c);
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. RPC cliente — withdraw / expire
-- ---------------------------------------------------------------------------
create or replace function public.m13_withdraw_match_candidate(
  p_candidate_id uuid,
  p_reason_code text default 'HUMAN_WITHDRAW'
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
  v_from text;
  v_reason text := coalesce(nullif(trim(p_reason_code), ''), 'HUMAN_WITHDRAW');
begin
  select * into v_c
  from public.lost_found_match_candidates
  where id = p_candidate_id
  for update;

  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  if not public._m13_can_review_match(v_c.case_id, v_actor) then
    raise exception 'MATCH_REVIEW_NOT_ALLOWED';
  end if;

  if v_c.status = 'WITHDRAWN' then
    return public._m13_candidate_json(v_c);
  end if;

  if public._m13_is_terminal_match_status(v_c.status) then
    raise exception 'MATCH_ALREADY_FINAL';
  end if;

  if v_c.status not in ('PROPOSED', 'UNDER_REVIEW') then
    raise exception 'INVALID_TRANSITION';
  end if;

  v_from := v_c.status;

  update public.lost_found_match_candidates
  set status = 'WITHDRAWN',
      updated_at = timezone('utc', now())
  where id = v_c.id
  returning * into v_c;

  perform public._m13_append_candidate_history(
    v_c.id, v_from, 'WITHDRAWN', v_actor, v_reason
  );

  return public._m13_candidate_json(v_c);
end;
$$;

create or replace function public.m13_expire_match_candidate(
  p_candidate_id uuid,
  p_reason_code text default 'EXPIRED'
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
  v_from text;
  v_reason text := coalesce(nullif(trim(p_reason_code), ''), 'EXPIRED');
begin
  select * into v_c
  from public.lost_found_match_candidates
  where id = p_candidate_id
  for update;

  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  if not public._m13_can_review_match(v_c.case_id, v_actor) then
    raise exception 'MATCH_REVIEW_NOT_ALLOWED';
  end if;

  if v_c.status = 'EXPIRED' then
    return public._m13_candidate_json(v_c);
  end if;

  if public._m13_is_terminal_match_status(v_c.status) then
    raise exception 'MATCH_ALREADY_FINAL';
  end if;

  if v_c.status not in ('PROPOSED', 'UNDER_REVIEW') then
    raise exception 'INVALID_TRANSITION';
  end if;

  v_from := v_c.status;

  update public.lost_found_match_candidates
  set status = 'EXPIRED',
      updated_at = timezone('utc', now())
  where id = v_c.id
  returning * into v_c;

  perform public._m13_append_candidate_history(
    v_c.id, v_from, 'EXPIRED', v_actor, v_reason
  );

  return public._m13_candidate_json(v_c);
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RPC cliente — listados
-- ---------------------------------------------------------------------------
create or replace function public.m13_list_match_decisions(p_candidate_id uuid)
returns setof jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
begin
  select * into v_c from public.lost_found_match_candidates where id = p_candidate_id;
  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  if not public._m13_can_manage_case(v_c.case_id, v_actor) then
    raise exception 'MATCH_FORBIDDEN';
  end if;

  return query
  select public._m13_decision_json(d)
  from public.lost_found_match_decisions d
  where d.candidate_id = p_candidate_id
  order by d.created_at desc
  limit 50;
end;
$$;

create or replace function public.m13_list_match_status_history(p_candidate_id uuid)
returns setof jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
begin
  select * into v_c from public.lost_found_match_candidates where id = p_candidate_id;
  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  if not public._m13_can_manage_case(v_c.case_id, v_actor) then
    raise exception 'MATCH_FORBIDDEN';
  end if;

  return query
  select public._m13_status_history_json(h)
  from public.lost_found_match_status_history h
  where h.candidate_id = p_candidate_id
  order by h.created_at desc
  limit 100;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. Grants / revokes
-- ---------------------------------------------------------------------------
revoke all on function public._m13_is_terminal_match_status(text) from public, anon, authenticated;
revoke all on function public._m13_can_review_match(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m13_can_confirm_match(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m13_resolve_match_actor_authority(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m13_decision_json(public.lost_found_match_decisions) from public, anon, authenticated;
revoke all on function public._m13_status_history_json(public.lost_found_match_status_history) from public, anon, authenticated;
revoke all on function public._m13_write_final_decision(uuid, text, uuid, text, text, text) from public, anon, authenticated;
revoke all on function public._m13_mark_sighting_confirmed_for_match(uuid) from public, anon, authenticated;
revoke all on function public._m13_audit_match_decision(text, uuid, text, uuid) from public, anon, authenticated;

revoke all on function public.m13_open_match_review(uuid) from public;
revoke all on function public.m13_open_match_review(uuid) from anon;
grant execute on function public.m13_open_match_review(uuid) to authenticated;

revoke all on function public.m13_confirm_match_candidate(uuid, text, text) from public;
revoke all on function public.m13_confirm_match_candidate(uuid, text, text) from anon;
grant execute on function public.m13_confirm_match_candidate(uuid, text, text) to authenticated;

revoke all on function public.m13_reject_match_candidate(uuid, text, text) from public;
revoke all on function public.m13_reject_match_candidate(uuid, text, text) from anon;
grant execute on function public.m13_reject_match_candidate(uuid, text, text) to authenticated;

revoke all on function public.m13_mark_match_inconclusive(uuid, text, text) from public;
revoke all on function public.m13_mark_match_inconclusive(uuid, text, text) from anon;
grant execute on function public.m13_mark_match_inconclusive(uuid, text, text) to authenticated;

revoke all on function public.m13_withdraw_match_candidate(uuid, text) from public;
revoke all on function public.m13_withdraw_match_candidate(uuid, text) from anon;
grant execute on function public.m13_withdraw_match_candidate(uuid, text) to authenticated;

revoke all on function public.m13_expire_match_candidate(uuid, text) from public;
revoke all on function public.m13_expire_match_candidate(uuid, text) from anon;
grant execute on function public.m13_expire_match_candidate(uuid, text) to authenticated;

revoke all on function public.m13_list_match_decisions(uuid) from public;
revoke all on function public.m13_list_match_decisions(uuid) from anon;
grant execute on function public.m13_list_match_decisions(uuid) to authenticated;

revoke all on function public.m13_list_match_status_history(uuid) from public;
revoke all on function public.m13_list_match_status_history(uuid) from anon;
grant execute on function public.m13_list_match_status_history(uuid) to authenticated;

-- Tablas: sin DML cliente (ya en 048); refuerzo.
revoke insert, update, delete on table public.lost_found_match_decisions from authenticated, anon, public;
revoke insert, update, delete on table public.lost_found_match_status_history from authenticated, anon, public;
revoke insert, update, delete on table public.lost_found_match_candidates from authenticated, anon, public;

comment on function public.m13_open_match_review(uuid) is
  'M13 049: PROPOSED→UNDER_REVIEW; FOR UPDATE; sin autoconfirm.';
comment on function public.m13_confirm_match_candidate(uuid, text, text) is
  'M13 049: UNDER_REVIEW→CONFIRMED; una decisión; marca sighting; no cierra caso.';
comment on function public.m13_reject_match_candidate(uuid, text, text) is
  'M13 049: UNDER_REVIEW→REJECTED; una decisión final.';
comment on function public.m13_mark_match_inconclusive(uuid, text, text) is
  'M13 049: UNDER_REVIEW→INCONCLUSIVE.';
comment on function public.m13_withdraw_match_candidate(uuid, text) is
  'M13 049: PROPOSED|UNDER_REVIEW→WITHDRAWN.';
comment on function public.m13_expire_match_candidate(uuid, text) is
  'M13 049: PROPOSED|UNDER_REVIEW→EXPIRED.';

commit;
