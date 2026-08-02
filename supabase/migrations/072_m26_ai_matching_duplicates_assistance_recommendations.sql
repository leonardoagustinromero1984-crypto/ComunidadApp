-- =============================================================================
-- LeoVer M26 — migración 072: inteligencia asistida (Bloque 2).
-- Forward-only sobre 001–071. LOCAL ONLY: no aplicar a staging sin autorización.
-- Sin pagos ni integración M24. Asistencia stub — no reemplaza moderación M04.
-- =============================================================================

begin;

create table if not exists public.m26_visual_match_suggestions (
  id uuid primary key default gen_random_uuid(),
  requester_user_id uuid not null references public.users(id) on delete restrict,
  source_label text not null,
  target_label text not null,
  score numeric(5,4) not null,
  confidence_band text not null,
  status text not null default 'PENDING',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m26_match_status_chk check (status = any (array['PENDING','ACCEPTED','REJECTED','EXPIRED']::text[])),
  constraint m26_match_band_chk check (confidence_band = any (array['LOW','MEDIUM','HIGH']::text[])),
  constraint m26_match_score_chk check (score >= 0 and score <= 1),
  constraint m26_match_label_chk check (
    char_length(trim(source_label)) between 2 and 120
    and char_length(trim(target_label)) between 2 and 120
    and lower(trim(source_label)) <> lower(trim(target_label))
  )
);

create table if not exists public.m26_duplicate_candidates (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references public.users(id) on delete restrict,
  primary_label text not null,
  duplicate_label text not null,
  similarity_score numeric(5,4) not null,
  status text not null default 'OPEN',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m26_dup_status_chk check (status = any (array['OPEN','CONFIRMED','DISMISSED']::text[])),
  constraint m26_dup_score_chk check (similarity_score >= 0 and similarity_score <= 1),
  constraint m26_dup_label_chk check (
    char_length(trim(primary_label)) between 2 and 120
    and char_length(trim(duplicate_label)) between 2 and 120
  )
);

create table if not exists public.m26_assistance_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete restrict,
  topic text not null,
  status text not null default 'ACTIVE',
  summary text not null,
  created_at timestamptz not null default timezone('utc', now()),
  closed_at timestamptz,
  constraint m26_assist_topic_chk check (topic = any (array['GENERAL','ADOPTION','LOST_PET','MARKETPLACE','OTHER']::text[])),
  constraint m26_assist_status_chk check (status = any (array['ACTIVE','CLOSED','EXPIRED']::text[])),
  constraint m26_assist_summary_chk check (char_length(trim(summary)) between 5 and 1000)
);

create table if not exists public.m26_evaluated_recommendations (
  id uuid primary key default gen_random_uuid(),
  subject_user_id uuid not null references public.users(id) on delete restrict,
  kind text not null,
  title text not null,
  rationale text not null,
  human_reviewed boolean not null default false,
  reviewer_note text,
  status text not null default 'PENDING_REVIEW',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m26_rec_kind_chk check (kind = any (array['CONTENT','PROVIDER','PRODUCT','EVENT','OTHER']::text[])),
  constraint m26_rec_status_chk check (status = any (array['DRAFT','PENDING_REVIEW','APPROVED','REJECTED','EXPIRED']::text[])),
  constraint m26_rec_title_chk check (char_length(trim(title)) between 3 and 160),
  constraint m26_rec_rationale_chk check (char_length(trim(rationale)) between 10 and 2000)
);

create index if not exists m26_match_requester_idx on public.m26_visual_match_suggestions(requester_user_id, status);
create index if not exists m26_dup_owner_idx on public.m26_duplicate_candidates(owner_user_id, status);
create index if not exists m26_assist_user_idx on public.m26_assistance_sessions(user_id, status);
create index if not exists m26_rec_status_idx on public.m26_evaluated_recommendations(status, human_reviewed);

alter table public.m26_visual_match_suggestions enable row level security;
alter table public.m26_duplicate_candidates enable row level security;
alter table public.m26_assistance_sessions enable row level security;
alter table public.m26_evaluated_recommendations enable row level security;

create policy m26_match_authenticated_deny on public.m26_visual_match_suggestions for all to authenticated using (false) with check (false);
create policy m26_dup_authenticated_deny on public.m26_duplicate_candidates for all to authenticated using (false) with check (false);
create policy m26_assist_authenticated_deny on public.m26_assistance_sessions for all to authenticated using (false) with check (false);
create policy m26_rec_authenticated_deny on public.m26_evaluated_recommendations for all to authenticated using (false) with check (false);

revoke all on table public.m26_visual_match_suggestions from public, anon, authenticated;
revoke all on table public.m26_duplicate_candidates from public, anon, authenticated;
revoke all on table public.m26_assistance_sessions from public, anon, authenticated;
revoke all on table public.m26_evaluated_recommendations from public, anon, authenticated;
grant all on table public.m26_visual_match_suggestions, public.m26_duplicate_candidates,
  public.m26_assistance_sessions, public.m26_evaluated_recommendations to service_role;

create or replace function public._m26_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := auth.uid();
begin
  if v_actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v_actor;
end;
$$;

create or replace function public._m26_confidence_band(p_score numeric)
returns text language sql immutable as $$
  select case
    when p_score >= 0.85 then 'HIGH'
    when p_score >= 0.60 then 'MEDIUM'
    else 'LOW'
  end;
$$;

create or replace function public._m26_public_match_json(m public.m26_visual_match_suggestions)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'source_label', m.source_label, 'target_label', m.target_label,
    'score', m.score, 'confidence_band', m.confidence_band, 'status', m.status
  );
$$;

create or replace function public._m26_public_duplicate_json(d public.m26_duplicate_candidates)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'primary_label', d.primary_label, 'duplicate_label', d.duplicate_label,
    'similarity_score', d.similarity_score, 'status', d.status
  );
$$;

create or replace function public._m26_public_assistance_json(s public.m26_assistance_sessions)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('topic', s.topic, 'status', s.status, 'summary', s.summary);
$$;

create or replace function public._m26_public_recommendation_json(r public.m26_evaluated_recommendations)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'title', r.title, 'rationale', r.rationale, 'kind', r.kind,
    'human_reviewed', r.human_reviewed,
    'approved_for_display', r.human_reviewed and r.status = 'APPROVED'
  );
$$;

create or replace function public._m26_match_json(m public.m26_visual_match_suggestions)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', m.id, 'requester_user_id', m.requester_user_id,
    'source_label', m.source_label, 'target_label', m.target_label,
    'score', m.score, 'confidence_band', m.confidence_band, 'status', m.status,
    'created_at', m.created_at, 'updated_at', m.updated_at
  );
$$;

create or replace function public.m26_list_visual_matches()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m26_public_match_json(m)
  from public.m26_visual_match_suggestions m
  where m.requester_user_id = public._m26_require_authenticated()
    and m.status <> 'EXPIRED'
  order by m.score desc, m.created_at desc;
$$;

create or replace function public.m26_list_duplicate_candidates()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m26_public_duplicate_json(d)
  from public.m26_duplicate_candidates d
  where d.owner_user_id = public._m26_require_authenticated()
    and d.status = 'OPEN'
  order by d.similarity_score desc, d.created_at desc;
$$;

create or replace function public.m26_list_assistance_sessions()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m26_public_assistance_json(s)
  from public.m26_assistance_sessions s
  where s.user_id = public._m26_require_authenticated()
  order by s.created_at desc;
$$;

create or replace function public.m26_list_eligible_recommendations()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m26_public_recommendation_json(r)
  from public.m26_evaluated_recommendations r
  where r.human_reviewed = true and r.status = 'APPROVED'
  order by r.updated_at desc;
$$;

create or replace function public.m26_request_visual_match(p_source_label text, p_target_label text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_score numeric := 0.75;
  v_match public.m26_visual_match_suggestions;
begin
  if char_length(trim(coalesce(p_source_label, ''))) not between 2 and 120
    or char_length(trim(coalesce(p_target_label, ''))) not between 2 and 120
    or lower(trim(p_source_label)) = lower(trim(p_target_label)) then
    raise exception 'M26_INVALID_MATCH';
  end if;
  insert into public.m26_visual_match_suggestions (
    requester_user_id, source_label, target_label, score, confidence_band
  ) values (
    v_actor, trim(p_source_label), trim(p_target_label), v_score, public._m26_confidence_band(v_score)
  ) returning * into v_match;
  return public._m26_match_json(v_match);
end;
$$;

create or replace function public.m26_dismiss_visual_match(p_match_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_match public.m26_visual_match_suggestions;
begin
  select * into v_match from public.m26_visual_match_suggestions where id = p_match_id;
  if not found then raise exception 'M26_MATCH_NOT_FOUND'; end if;
  if v_match.requester_user_id <> v_actor then raise exception 'M26_PERMISSION_DENIED'; end if;
  if v_match.status = 'EXPIRED' then raise exception 'M26_PERMISSION_DENIED'; end if;
  update public.m26_visual_match_suggestions set status = 'REJECTED', updated_at = timezone('utc', now())
  where id = p_match_id returning * into v_match;
  return public._m26_match_json(v_match);
end;
$$;

create or replace function public.m26_confirm_duplicate(p_candidate_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_dup public.m26_duplicate_candidates;
begin
  select * into v_dup from public.m26_duplicate_candidates where id = p_candidate_id;
  if not found then raise exception 'M26_DUPLICATE_NOT_FOUND'; end if;
  if v_dup.owner_user_id <> v_actor then raise exception 'M26_PERMISSION_DENIED'; end if;
  if v_dup.status <> 'OPEN' then raise exception 'M26_PERMISSION_DENIED'; end if;
  update public.m26_duplicate_candidates set status = 'CONFIRMED', updated_at = timezone('utc', now())
  where id = p_candidate_id returning * into v_dup;
  return public._m26_public_duplicate_json(v_dup);
end;
$$;

create or replace function public.m26_dismiss_duplicate(p_candidate_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_dup public.m26_duplicate_candidates;
begin
  select * into v_dup from public.m26_duplicate_candidates where id = p_candidate_id;
  if not found then raise exception 'M26_DUPLICATE_NOT_FOUND'; end if;
  if v_dup.owner_user_id <> v_actor then raise exception 'M26_PERMISSION_DENIED'; end if;
  if v_dup.status <> 'OPEN' then raise exception 'M26_PERMISSION_DENIED'; end if;
  update public.m26_duplicate_candidates set status = 'DISMISSED', updated_at = timezone('utc', now())
  where id = p_candidate_id returning * into v_dup;
  return public._m26_public_duplicate_json(v_dup);
end;
$$;

create or replace function public.m26_start_assistance_session(p_topic text, p_initial_prompt text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_session public.m26_assistance_sessions;
begin
  if char_length(trim(coalesce(p_initial_prompt, ''))) not between 5 and 1000
    or upper(coalesce(p_topic, '')) not in ('GENERAL','ADOPTION','LOST_PET','MARKETPLACE','OTHER') then
    raise exception 'M26_INVALID_ASSISTANCE';
  end if;
  insert into public.m26_assistance_sessions (user_id, topic, summary)
  values (v_actor, upper(trim(p_topic)), 'Sesión stub: ' || trim(p_initial_prompt))
  returning * into v_session;
  return jsonb_build_object(
    'id', v_session.id, 'user_id', v_session.user_id, 'topic', v_session.topic,
    'status', v_session.status, 'summary', v_session.summary,
    'created_at', v_session.created_at, 'closed_at', v_session.closed_at
  );
end;
$$;

create or replace function public.m26_close_assistance_session(p_session_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_session public.m26_assistance_sessions;
begin
  select * into v_session from public.m26_assistance_sessions where id = p_session_id;
  if not found then raise exception 'M26_SESSION_NOT_FOUND'; end if;
  if v_session.user_id <> v_actor then raise exception 'M26_PERMISSION_DENIED'; end if;
  if v_session.status <> 'ACTIVE' then raise exception 'M26_SESSION_ALREADY_CLOSED'; end if;
  update public.m26_assistance_sessions set status = 'CLOSED', closed_at = timezone('utc', now())
  where id = p_session_id returning * into v_session;
  return jsonb_build_object(
    'id', v_session.id, 'user_id', v_session.user_id, 'topic', v_session.topic,
    'status', v_session.status, 'summary', v_session.summary,
    'created_at', v_session.created_at, 'closed_at', v_session.closed_at
  );
end;
$$;

create or replace function public.m26_submit_recommendation(p_kind text, p_title text, p_rationale text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_rec public.m26_evaluated_recommendations;
begin
  if char_length(trim(coalesce(p_title, ''))) not between 3 and 160
    or char_length(trim(coalesce(p_rationale, ''))) not between 10 and 2000
    or upper(coalesce(p_kind, '')) not in ('CONTENT','PROVIDER','PRODUCT','EVENT','OTHER') then
    raise exception 'M26_INVALID_RECOMMENDATION';
  end if;
  insert into public.m26_evaluated_recommendations (subject_user_id, kind, title, rationale, status)
  values (v_actor, upper(trim(p_kind)), trim(p_title), trim(p_rationale), 'PENDING_REVIEW')
  returning * into v_rec;
  return jsonb_build_object(
    'id', v_rec.id, 'subject_user_id', v_rec.subject_user_id, 'kind', v_rec.kind,
    'title', v_rec.title, 'rationale', v_rec.rationale, 'human_reviewed', v_rec.human_reviewed,
    'reviewer_note', v_rec.reviewer_note, 'status', v_rec.status,
    'created_at', v_rec.created_at, 'updated_at', v_rec.updated_at
  );
end;
$$;

create or replace function public.m26_review_recommendation(
  p_recommendation_id uuid, p_approved boolean, p_reviewer_note text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_rec public.m26_evaluated_recommendations;
begin
  select * into v_rec from public.m26_evaluated_recommendations where id = p_recommendation_id;
  if not found then raise exception 'M26_RECOMMENDATION_NOT_FOUND'; end if;
  if v_rec.status not in ('DRAFT','PENDING_REVIEW') then raise exception 'M26_PERMISSION_DENIED'; end if;
  update public.m26_evaluated_recommendations set
    human_reviewed = true,
    reviewer_note = nullif(trim(coalesce(p_reviewer_note, '')), ''),
    status = case when coalesce(p_approved, false) then 'APPROVED' else 'REJECTED' end,
    updated_at = timezone('utc', now())
  where id = p_recommendation_id returning * into v_rec;
  return jsonb_build_object(
    'id', v_rec.id, 'subject_user_id', v_rec.subject_user_id, 'kind', v_rec.kind,
    'title', v_rec.title, 'rationale', v_rec.rationale, 'human_reviewed', v_rec.human_reviewed,
    'reviewer_note', v_rec.reviewer_note, 'status', v_rec.status,
    'created_at', v_rec.created_at, 'updated_at', v_rec.updated_at
  );
end;
$$;

revoke all on function public._m26_require_authenticated() from public, anon, authenticated;
revoke all on function public._m26_confidence_band(numeric) from public, anon, authenticated;
revoke all on function public._m26_public_match_json(public.m26_visual_match_suggestions) from public, anon, authenticated;
revoke all on function public._m26_public_duplicate_json(public.m26_duplicate_candidates) from public, anon, authenticated;
revoke all on function public._m26_public_assistance_json(public.m26_assistance_sessions) from public, anon, authenticated;
revoke all on function public._m26_public_recommendation_json(public.m26_evaluated_recommendations) from public, anon, authenticated;
revoke all on function public._m26_match_json(public.m26_visual_match_suggestions) from public, anon, authenticated;

revoke all on function public.m26_list_visual_matches() from public, anon;
revoke all on function public.m26_list_duplicate_candidates() from public, anon;
revoke all on function public.m26_list_assistance_sessions() from public, anon;
revoke all on function public.m26_list_eligible_recommendations() from public, anon;
revoke all on function public.m26_request_visual_match(text, text) from public, anon;
revoke all on function public.m26_dismiss_visual_match(uuid) from public, anon;
revoke all on function public.m26_confirm_duplicate(uuid) from public, anon;
revoke all on function public.m26_dismiss_duplicate(uuid) from public, anon;
revoke all on function public.m26_start_assistance_session(text, text) from public, anon;
revoke all on function public.m26_close_assistance_session(uuid) from public, anon;
revoke all on function public.m26_submit_recommendation(text, text, text) from public, anon;
revoke all on function public.m26_review_recommendation(uuid, boolean, text) from public, anon;

grant execute on function public.m26_list_visual_matches() to authenticated;
grant execute on function public.m26_list_duplicate_candidates() to authenticated;
grant execute on function public.m26_list_assistance_sessions() to authenticated;
grant execute on function public.m26_list_eligible_recommendations() to authenticated;
grant execute on function public.m26_request_visual_match(text, text) to authenticated;
grant execute on function public.m26_dismiss_visual_match(uuid) to authenticated;
grant execute on function public.m26_confirm_duplicate(uuid) to authenticated;
grant execute on function public.m26_dismiss_duplicate(uuid) to authenticated;
grant execute on function public.m26_start_assistance_session(text, text) to authenticated;
grant execute on function public.m26_close_assistance_session(uuid) to authenticated;
grant execute on function public.m26_submit_recommendation(text, text, text) to authenticated;
grant execute on function public.m26_review_recommendation(uuid, boolean, text) to authenticated;

commit;
