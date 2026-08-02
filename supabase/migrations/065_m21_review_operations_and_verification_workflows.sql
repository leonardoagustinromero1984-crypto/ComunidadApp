-- =============================================================================
-- LeoVer M21 — migración 065: operaciones Bloque 3–4 (reseñas, respuestas,
-- disputas, elegibilidad, agregados, verificaciones extendidas).
-- Forward-only sobre 001–064. NO modifica 064. LOCAL ONLY hasta staging autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Estados extendidos en m21_reviews
-- ---------------------------------------------------------------------------
alter table public.m21_reviews
  drop constraint if exists m21_review_status_chk;

alter table public.m21_reviews
  add constraint m21_review_status_chk check (
    review_status = any (array[
      'DRAFT','PENDING','PUBLISHED','EDITED','HIDDEN','ARCHIVED',
      'DISPUTED','REMOVED','REMOVED_BY_MODERATION','APPEALED'
    ]::text[])
  );

drop index if exists public.m21_reviews_reviewer_target_uniq;

create unique index if not exists m21_reviews_reviewer_target_uniq
  on public.m21_reviews (reviewer_user_id, target_type, target_id)
  where review_status not in ('REMOVED', 'REMOVED_BY_MODERATION', 'ARCHIVED');

-- ---------------------------------------------------------------------------
-- 1. Columnas contexto / edición en m21_reviews
-- ---------------------------------------------------------------------------
alter table public.m21_reviews
  add column if not exists title text,
  add column if not exists context_type text,
  add column if not exists context_id text,
  add column if not exists context_public_label text,
  add column if not exists edit_count integer not null default 0;

alter table public.m21_reviews
  drop constraint if exists m21_review_title_len;

alter table public.m21_reviews
  add constraint m21_review_title_len check (
    title is null or char_length(trim(title)) between 1 and 120
  );

alter table public.m21_reviews
  drop constraint if exists m21_review_context_type_chk;

alter table public.m21_reviews
  add constraint m21_review_context_type_chk check (
    context_type is null
    or context_type = any (array[
      'ADOPTION_COMPLETED','FOSTER_COMPLETED','SERVICE_COMPLETED',
      'DONATION_COMPLETED','EVENT_ATTENDED','SHELTER_INTERACTION','SUPPORT_CONVERSATION'
    ]::text[])
  );

alter table public.m21_reviews
  drop constraint if exists m21_review_edit_count_chk;

alter table public.m21_reviews
  add constraint m21_review_edit_count_chk check (edit_count >= 0);

create unique index if not exists m21_reviews_reviewer_context_uniq
  on public.m21_reviews (reviewer_user_id, context_id)
  where context_id is not null
    and review_status not in ('REMOVED', 'REMOVED_BY_MODERATION', 'ARCHIVED');

create index if not exists m21_reviews_context_idx
  on public.m21_reviews (context_id)
  where context_id is not null;

-- ---------------------------------------------------------------------------
-- 2. Tablas operativas Bloque 3
-- ---------------------------------------------------------------------------
create table if not exists public.m21_review_responses (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.m21_reviews (id) on delete cascade,
  responder_user_id uuid not null references public.users (id) on delete restrict,
  content text not null,
  response_status text not null default 'PUBLISHED',
  edit_count integer not null default 0,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m21_resp_status_chk check (
    response_status = any (array[
      'PUBLISHED','EDITED','HIDDEN','REMOVED_BY_MODERATION'
    ]::text[])
  ),
  constraint m21_resp_content_len check (char_length(trim(content)) between 5 and 2000),
  constraint m21_resp_edit_count_chk check (edit_count >= 0)
);

create unique index if not exists m21_review_responses_active_uniq
  on public.m21_review_responses (review_id)
  where response_status in ('PUBLISHED', 'EDITED');

create index if not exists m21_review_responses_review_idx
  on public.m21_review_responses (review_id, response_status, updated_at desc);

create table if not exists public.m21_review_disputes (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.m21_reviews (id) on delete cascade,
  claimant_user_id uuid not null references public.users (id) on delete restrict,
  reason text not null,
  details text not null,
  dispute_status text not null default 'OPEN',
  evidence_ref text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m21_dispute_reason_chk check (
    reason = any (array[
      'FACTUAL_ERROR','CONFLICT_OF_INTEREST','HARASSMENT',
      'SPAM','PERSONAL_DATA','OTHER'
    ]::text[])
  ),
  constraint m21_dispute_status_chk check (
    dispute_status = any (array['OPEN','UNDER_REVIEW','RESOLVED','DISMISSED']::text[])
  ),
  constraint m21_dispute_details_len check (char_length(trim(details)) between 10 and 2000)
);

create unique index if not exists m21_review_disputes_open_uniq
  on public.m21_review_disputes (review_id)
  where dispute_status = 'OPEN';

create index if not exists m21_review_disputes_claimant_idx
  on public.m21_review_disputes (claimant_user_id, dispute_status, created_at desc);

create table if not exists public.m21_eligibility_records (
  id uuid primary key default gen_random_uuid(),
  reviewer_user_id uuid not null references public.users (id) on delete cascade,
  target_type text not null,
  target_id text not null,
  context_type text not null,
  context_id text not null,
  context_public_label text not null,
  completed_at timestamptz not null,
  expires_at timestamptz,
  cancelled boolean not null default false,
  rejected boolean not null default false,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m21_elig_target_type_chk check (
    target_type = any (array['ADOPTION','SERVICE','DONATION','ORGANIZATION','USER']::text[])
  ),
  constraint m21_elig_context_type_chk check (
    context_type = any (array[
      'ADOPTION_COMPLETED','FOSTER_COMPLETED','SERVICE_COMPLETED',
      'DONATION_COMPLETED','EVENT_ATTENDED','SHELTER_INTERACTION','SUPPORT_CONVERSATION'
    ]::text[])
  ),
  constraint m21_elig_context_label_len check (char_length(trim(context_public_label)) between 1 and 200),
  constraint m21_elig_cancel_reject_chk check (not (cancelled and rejected))
);

create index if not exists m21_eligibility_lookup_idx
  on public.m21_eligibility_records (
    reviewer_user_id, target_type, target_id, context_id, completed_at desc
  );

create unique index if not exists m21_eligibility_context_uniq
  on public.m21_eligibility_records (reviewer_user_id, context_id);

-- ---------------------------------------------------------------------------
-- 3. Verificaciones extendidas (Bloque 4)
-- ---------------------------------------------------------------------------
alter table public.m21_verification_requests
  add column if not exists evidence_ref text;

alter table public.m21_verification_requests
  drop constraint if exists m21_ver_status_chk;

alter table public.m21_verification_requests
  add constraint m21_ver_status_chk check (
    verification_status = any (array[
      'NOT_REQUESTED','NOT_SUBMITTED','PENDING','UNDER_REVIEW',
      'APPROVED','REJECTED','EXPIRED','REVOKED'
    ]::text[])
  );

alter table public.m21_verification_requests
  drop constraint if exists m21_ver_evidence_ref_chk;

alter table public.m21_verification_requests
  add constraint m21_ver_evidence_ref_chk check (
    evidence_ref is null or char_length(trim(evidence_ref)) between 1 and 512
  );

-- ---------------------------------------------------------------------------
-- 4. RLS — deny direct mutation (patrón 064)
-- ---------------------------------------------------------------------------
alter table public.m21_review_responses enable row level security;
alter table public.m21_review_disputes enable row level security;
alter table public.m21_eligibility_records enable row level security;

create policy m21_resp_select on public.m21_review_responses for select to authenticated using (true);
create policy m21_resp_mut on public.m21_review_responses for all to authenticated using (false);

create policy m21_dispute_select on public.m21_review_disputes for select to authenticated
  using (claimant_user_id = auth.uid());
create policy m21_dispute_mut on public.m21_review_disputes for all to authenticated using (false);

create policy m21_elig_select on public.m21_eligibility_records for select to authenticated
  using (reviewer_user_id = auth.uid());
create policy m21_elig_mut on public.m21_eligibility_records for all to authenticated using (false);

revoke all on table public.m21_review_responses from public, anon;
revoke all on table public.m21_review_disputes from public, anon;
revoke all on table public.m21_eligibility_records from public, anon;

grant select on table public.m21_review_responses to authenticated;
grant select on table public.m21_review_disputes to authenticated;
grant select on table public.m21_eligibility_records to authenticated;

grant all on table public.m21_review_responses to service_role;
grant all on table public.m21_review_disputes to service_role;
grant all on table public.m21_eligibility_records to service_role;

-- ---------------------------------------------------------------------------
-- 5. Helpers internos
-- ---------------------------------------------------------------------------
create or replace function public._m21_terminal_review_statuses()
returns text[] language sql immutable parallel safe as $$
  select array['REMOVED','REMOVED_BY_MODERATION','ARCHIVED']::text[];
$$;

create or replace function public._m21_public_review_statuses()
returns text[] language sql immutable parallel safe as $$
  select array['PUBLISHED','EDITED','DISPUTED','APPEALED']::text[];
$$;

create or replace function public._m21_countable_review_statuses()
returns text[] language sql immutable parallel safe as $$
  select array['PUBLISHED','EDITED','DISPUTED','APPEALED']::text[];
$$;

create or replace function public._m21_is_public_review_status(p_status text)
returns boolean language sql immutable as $$
  select upper(coalesce(p_status, '')) = any (public._m21_public_review_statuses());
$$;

create or replace function public._m21_is_countable_review(p_review public.m21_reviews)
returns boolean language sql stable as $$
  select p_review.review_status = any (public._m21_countable_review_statuses())
    and p_review.context_id is not null;
$$;

create or replace function public._m21_scrub_public_text(p_text text)
returns text language sql immutable as $$
  select trim(regexp_replace(
    regexp_replace(
      regexp_replace(coalesce(p_text, ''), '(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}', '[redactado]', 'g'),
      '(?i)(dni|cuil|cuit)\s*[:#]?\s*\d+', '[redactado]', 'g'
    ),
    '(?i)<script|javascript:|on\w+\s*=|<iframe', '[redactado]', 'g'
  ));
$$;

create or replace function public._m21_validate_review_content(p_content text, p_rating int)
returns void language plpgsql as $$
begin
  if p_rating < 1 or p_rating > 5 then raise exception 'M21_INVALID_RATING'; end if;
  if char_length(trim(coalesce(p_content, ''))) < 1 then raise exception 'M21_INVALID_REVIEW'; end if;
  if char_length(trim(coalesce(p_content, ''))) > 2000 then raise exception 'M21_INVALID_REVIEW'; end if;
  if p_content ~* '(?i)<script|javascript:|on\w+\s*=|<iframe' then raise exception 'M21_INVALID_REVIEW'; end if;
  if p_content ~* '(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}' then raise exception 'M21_INVALID_REVIEW'; end if;
  if p_content ~* '(?i)(dni|cuil|cuit)\s*[:#]?\s*\d' then raise exception 'M21_INVALID_REVIEW'; end if;
end;
$$;

create or replace function public._m21_validate_review_title(p_title text)
returns void language plpgsql as $$
begin
  if p_title is null then return; end if;
  if char_length(trim(p_title)) < 1 or char_length(trim(p_title)) > 120 then
    raise exception 'M21_INVALID_REVIEW';
  end if;
  if p_title ~* '(?i)<script|javascript:|on\w+\s*=|<iframe' then raise exception 'M21_INVALID_REVIEW'; end if;
end;
$$;

create or replace function public._m21_validate_response_content(p_content text)
returns void language plpgsql as $$
begin
  if char_length(trim(coalesce(p_content, ''))) < 5 then raise exception 'M21_INVALID_RESPONSE'; end if;
  if char_length(trim(coalesce(p_content, ''))) > 2000 then raise exception 'M21_INVALID_RESPONSE'; end if;
  if p_content ~* '(?i)<script|javascript:|on\w+\s*=|<iframe' then raise exception 'M21_INVALID_RESPONSE'; end if;
  if p_content ~* '(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}' then raise exception 'M21_INVALID_RESPONSE'; end if;
  if p_content ~* '(?i)(dni|cuil|cuit)\s*[:#]?\s*\d' then raise exception 'M21_INVALID_RESPONSE'; end if;
end;
$$;

create or replace function public._m21_is_self_review(
  p_actor uuid,
  p_target_type text,
  p_target_id text
) returns boolean language sql immutable as $$
  select upper(coalesce(p_target_type, '')) = 'USER'
    and trim(coalesce(p_target_id, '')) = coalesce(p_actor::text, '');
$$;

create or replace function public._m21_is_subject_owner(
  p_target_type text,
  p_target_id text,
  p_actor uuid
) returns boolean language plpgsql stable security definer set search_path = public as $$
declare
  v_type text := upper(trim(coalesce(p_target_type, '')));
  v_id text := trim(coalesce(p_target_id, ''));
  v_org uuid;
  v_publisher uuid;
begin
  if p_actor is null or v_id = '' then return false; end if;

  if v_type = 'USER' then
    return v_id = p_actor::text;
  end if;

  if v_type = 'ORGANIZATION' then
    begin v_org := v_id::uuid; exception when others then return false; end;
    return public.has_org_permission(v_org, 'organization.update')
        or public.has_org_permission(v_org, 'donation.manage');
  end if;

  if v_type = 'DONATION' then
    select c.organization_id into v_org
    from public.m17_donation_campaigns c
    where c.id::text = v_id
    limit 1;
    if v_org is not null then
      return public.has_org_permission(v_org, 'donation.manage');
    end if;
  end if;

  if v_type = 'SERVICE' then
    select cp.organization_id into v_org
    from public.veterinary_services vs
    join public.veterinary_clinic_profiles cp on cp.id = vs.clinic_id
    where vs.id::text = v_id
    limit 1;
    if v_org is not null then
      return public.has_org_permission(v_org, 'organization.update');
    end if;
  end if;

  if v_type = 'ADOPTION' then
    select a.publisher_id into v_publisher
    from public.adoptions a
    where a.id::text = v_id
    limit 1;
    if v_publisher = p_actor then return true; end if;
    select a.shelter_id into v_publisher
    from public.adoptions a
    where a.id::text = v_id and a.shelter_id is not null
    limit 1;
    if v_publisher = p_actor then return true; end if;
  end if;

  return false;
end;
$$;

create or replace function public._m21_active_response(p_review_id uuid)
returns public.m21_review_responses language sql stable security definer set search_path = public as $$
  select r.*
  from public.m21_review_responses r
  where r.review_id = p_review_id
    and r.response_status in ('PUBLISHED', 'EDITED')
  order by r.updated_at desc
  limit 1;
$$;

create or replace function public._m21_public_response_json(p_resp public.m21_review_responses)
returns jsonb language sql stable as $$
  select case
    when p_resp.response_status in ('HIDDEN', 'REMOVED_BY_MODERATION') then null
    else jsonb_build_object(
      'id', p_resp.id,
      'content', public._m21_scrub_public_text(p_resp.content),
      'status', p_resp.response_status,
      'created_at', p_resp.created_at,
      'updated_at', p_resp.updated_at
    )
  end;
$$;

create or replace function public._m21_public_review_json(p_review public.m21_reviews, p_actor uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_resp public.m21_review_responses;
  v_resp_json jsonb;
begin
  v_resp := public._m21_active_response(p_review.id);
  v_resp_json := case when v_resp is not null then public._m21_public_response_json(v_resp) else null end;

  return jsonb_build_object(
    'id', p_review.id,
    'target_type', p_review.target_type,
    'target_display_label', public._m21_scrub_public_text(p_review.target_display_label),
    'reviewer_display_name', public._m21_scrub_public_text(p_review.reviewer_display_name),
    'rating', p_review.rating,
    'content', public._m21_scrub_public_text(p_review.content),
    'status', p_review.review_status,
    'created_at', p_review.created_at,
    'updated_at', p_review.updated_at,
    'is_own_review', p_review.reviewer_user_id = p_actor,
    'title', case when p_review.title is null then null else public._m21_scrub_public_text(p_review.title) end,
    'has_response', v_resp_json is not null,
    'public_response', v_resp_json,
    'eligible_experience_badge', case
      when p_review.context_id is not null
        and public._m21_is_public_review_status(p_review.review_status)
      then 'Experiencia verificada'
      else null
    end
  );
end;
$$;

create or replace function public._m21_evaluate_eligibility(
  p_actor uuid,
  p_target_type text,
  p_target_id text,
  p_target_display_label text,
  p_context_type text,
  p_context_id text,
  p_context_public_label text
) returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_type text := upper(trim(coalesce(p_target_type, '')));
  v_target text := trim(coalesce(p_target_id, ''));
  v_label text := trim(coalesce(p_target_display_label, ''));
  v_ctx_type text := nullif(upper(trim(coalesce(p_context_type, ''))), '');
  v_ctx_id text := nullif(trim(coalesce(p_context_id, '')), '');
  v_ctx_label text := nullif(trim(coalesce(p_context_public_label, '')), '');
  v_record public.m21_eligibility_records;
  v_subject jsonb;
  v_context jsonb;
begin
  v_subject := jsonb_build_object(
    'target_type', v_type,
    'target_id', v_target,
    'display_label', v_label
  );

  if public._m21_is_self_review(p_actor, v_type, v_target) then
    return jsonb_build_object(
      'eligible', false,
      'reason', 'SELF_REVIEW',
      'subject', v_subject,
      'context_reference', case when v_ctx_id is null then null else jsonb_build_object(
        'context_type', v_ctx_type, 'context_id', v_ctx_id, 'public_label', v_ctx_label
      ) end
    );
  end if;

  if v_ctx_id is not null and exists (
    select 1 from public.m21_reviews r
    where r.reviewer_user_id = p_actor
      and r.context_id = v_ctx_id
      and r.review_status <> all (public._m21_terminal_review_statuses())
  ) then
    return jsonb_build_object(
      'eligible', false,
      'reason', 'ALREADY_REVIEWED',
      'subject', v_subject,
      'context_reference', jsonb_build_object(
        'context_type', v_ctx_type, 'context_id', v_ctx_id, 'public_label', v_ctx_label
      )
    );
  end if;

  if v_ctx_id is null then
    return jsonb_build_object(
      'eligible', false,
      'reason', 'ELIGIBILITY_UNAVAILABLE',
      'subject', v_subject,
      'context_reference', null
    );
  end if;

  select e.* into v_record
  from public.m21_eligibility_records e
  where e.reviewer_user_id = p_actor
    and e.target_type = v_type
    and e.target_id = v_target
    and v_ctx_id is not null
    and e.context_id = v_ctx_id
  order by e.completed_at desc
  limit 1;

  if not found then
    return jsonb_build_object(
      'eligible', false,
      'reason', case when v_ctx_id is null then 'ELIGIBILITY_UNAVAILABLE' else 'NOT_ELIGIBLE' end,
      'subject', v_subject,
      'context_reference', case when v_ctx_id is null then null else jsonb_build_object(
        'context_type', v_ctx_type, 'context_id', v_ctx_id, 'public_label', v_ctx_label
      ) end
    );
  end if;

  v_context := jsonb_build_object(
    'context_type', v_record.context_type,
    'context_id', v_record.context_id,
    'public_label', v_record.context_public_label
  );

  if v_record.cancelled then
    return jsonb_build_object(
      'eligible', false, 'reason', 'CONTEXT_CANCELLED',
      'subject', v_subject, 'context_reference', v_context
    );
  end if;

  if v_record.rejected then
    return jsonb_build_object(
      'eligible', false, 'reason', 'CONTEXT_REJECTED',
      'subject', v_subject, 'context_reference', v_context
    );
  end if;

  if v_record.expires_at is not null and v_record.expires_at < timezone('utc', now()) then
    return jsonb_build_object(
      'eligible', false, 'reason', 'EXPIRED',
      'subject', v_subject, 'context_reference', v_context
    );
  end if;

  return jsonb_build_object(
    'eligible', true,
    'reason', 'COMPLETED_INTERACTION',
    'subject', v_subject,
    'context_reference', v_context
  );
end;
$$;

create or replace function public._m21_rating_distribution(p_target_type text, p_target_id text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'one_star', count(*) filter (where r.rating = 1),
    'two_stars', count(*) filter (where r.rating = 2),
    'three_stars', count(*) filter (where r.rating = 3),
    'four_stars', count(*) filter (where r.rating = 4),
    'five_stars', count(*) filter (where r.rating = 5)
  )
  from public.m21_reviews r
  where r.target_type = upper(p_target_type)
    and r.target_id = p_target_id
    and public._m21_is_countable_review(r);
$$;

-- ---------------------------------------------------------------------------
-- 6. RPC — elegibilidad y agregados
-- ---------------------------------------------------------------------------
create or replace function public.m21_check_eligibility(
  p_target_type text,
  p_target_id text,
  p_target_display_label text,
  p_context_type text default null,
  p_context_id text default null,
  p_context_public_label text default null
) returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m21_require_authenticated();
begin
  return public._m21_evaluate_eligibility(
    v_actor, p_target_type, p_target_id, p_target_display_label,
    p_context_type, p_context_id, p_context_public_label
  );
end;
$$;

create or replace function public.m21_get_subject_breakdown(p_target_type text, p_target_id text)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  v_type text := upper(trim(coalesce(p_target_type, '')));
  v_target text := trim(coalesce(p_target_id, ''));
  v_reviews jsonb := '[]'::jsonb;
  v_avg numeric;
  v_count int;
  v_resp_count int;
  v_last timestamptz;
  v_label text;
  r public.m21_reviews;
begin
  select count(*)::int, avg(rating)::numeric, max(created_at)
  into v_count, v_avg, v_last
  from public.m21_reviews r
  where r.target_type = v_type and r.target_id = v_target
    and public._m21_is_countable_review(r);

  select count(*)::int into v_resp_count
  from public.m21_reviews r
  where r.target_type = v_type and r.target_id = v_target
    and public._m21_is_countable_review(r)
    and exists (
      select 1 from public.m21_review_responses resp
      where resp.review_id = r.id
        and resp.response_status in ('PUBLISHED', 'EDITED')
    );

  for r in
    select * from public.m21_reviews
    where target_type = v_type and target_id = v_target
      and review_status = any (public._m21_public_review_statuses())
    order by created_at desc
  loop
    v_reviews := v_reviews || jsonb_build_array(public._m21_public_review_json(r, v_actor));
    if v_label is null then v_label := r.target_display_label; end if;
  end loop;

  return jsonb_build_object(
    'subject', jsonb_build_object(
      'target_type', v_type,
      'target_id', v_target,
      'display_label', coalesce(v_label, v_type)
    ),
    'average_rating', case when v_count > 0 then round(v_avg, 1) else null end,
    'published_review_count', coalesce(v_count, 0),
    'rating_distribution', public._m21_rating_distribution(v_type, v_target),
    'reviews_with_response_count', coalesce(v_resp_count, 0),
    'last_review_at', v_last,
    'reviews', v_reviews
  );
end;
$$;

create or replace function public.m21_get_review_detail(p_review_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  r public.m21_reviews;
begin
  select * into r from public.m21_reviews where id = p_review_id;
  if not found then raise exception 'M21_REVIEW_NOT_FOUND'; end if;

  if r.review_status = 'DRAFT' and r.reviewer_user_id <> v_actor then
    raise exception 'M21_PERMISSION_DENIED';
  end if;

  if not public._m21_is_public_review_status(r.review_status)
     and r.reviewer_user_id <> v_actor
     and not public._m21_is_subject_owner(r.target_type, r.target_id, v_actor) then
    raise exception 'M21_PERMISSION_DENIED';
  end if;

  return public._m21_public_review_json(r, v_actor);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. RPC — operaciones de reseña (actualizaciones)
-- ---------------------------------------------------------------------------
drop function if exists public.m21_submit_review(text, text, text, int, text);

create or replace function public.m21_submit_review(
  p_target_type text,
  p_target_id text,
  p_target_display_label text,
  p_rating int,
  p_content text,
  p_title text default null,
  p_context_type text default null,
  p_context_id text default null,
  p_context_public_label text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  v_elig jsonb;
  v_ctx jsonb;
  v_id uuid;
  v_name text;
  v_type text := upper(trim(coalesce(p_target_type, '')));
  v_target text := trim(coalesce(p_target_id, ''));
begin
  perform public._m21_validate_review_content(p_content, p_rating);
  perform public._m21_validate_review_title(p_title);

  if public._m21_is_self_review(v_actor, v_type, v_target) then
    raise exception 'M21_SELF_REVIEW';
  end if;

  v_elig := public._m21_evaluate_eligibility(
    v_actor, v_type, v_target, p_target_display_label,
    p_context_type, p_context_id, p_context_public_label
  );

  if not coalesce((v_elig->>'eligible')::boolean, false) then
    case v_elig->>'reason'
      when 'SELF_REVIEW' then raise exception 'M21_SELF_REVIEW';
      when 'ALREADY_REVIEWED' then raise exception 'M21_DUPLICATE_REVIEW';
      when 'ELIGIBILITY_UNAVAILABLE' then raise exception 'M21_REVIEW_ELIGIBILITY_UNAVAILABLE';
      else raise exception 'M21_NOT_ELIGIBLE';
    end case;
  end if;

  v_ctx := v_elig->'context_reference';
  if v_ctx is null then raise exception 'M21_NOT_ELIGIBLE'; end if;

  if exists (
    select 1 from public.m21_reviews
    where reviewer_user_id = v_actor and target_type = v_type
      and target_id = v_target
      and review_status <> all (public._m21_terminal_review_statuses())
  ) then raise exception 'M21_DUPLICATE_REVIEW'; end if;

  v_name := coalesce((select nullif(trim(name), '') from public.users where id = v_actor), 'Participante');

  insert into public.m21_reviews (
    target_type, target_id, target_display_label, reviewer_user_id, reviewer_display_name,
    rating, content, review_status, title,
    context_type, context_id, context_public_label
  ) values (
    v_type, v_target, trim(p_target_display_label), v_actor, v_name,
    p_rating, trim(p_content), 'PUBLISHED', nullif(trim(p_title), ''),
    v_ctx->>'context_type', v_ctx->>'context_id', v_ctx->>'public_label'
  ) returning id into v_id;

  update public.users set reputation_score = coalesce(reputation_score, 0) + 5 where id = v_actor;

  return public._m21_public_review_json((select r from public.m21_reviews r where r.id = v_id), v_actor);
end;
$$;

create or replace function public.m21_edit_review(
  p_review_id uuid,
  p_rating int default null,
  p_content text default null,
  p_title text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  r public.m21_reviews;
  v_rating int;
  v_content text;
  v_title text;
begin
  select * into r from public.m21_reviews where id = p_review_id;
  if not found then raise exception 'M21_REVIEW_NOT_FOUND'; end if;
  if r.reviewer_user_id <> v_actor then raise exception 'M21_PERMISSION_DENIED'; end if;

  v_rating := coalesce(p_rating, r.rating);
  v_content := coalesce(nullif(trim(p_content), ''), r.content);
  v_title := case when p_title is null then r.title else nullif(trim(p_title), '') end;

  perform public._m21_validate_review_content(v_content, v_rating);
  perform public._m21_validate_review_title(v_title);

  update public.m21_reviews set
    rating = v_rating,
    content = v_content,
    title = v_title,
    review_status = case when review_status = 'PUBLISHED' then 'EDITED' else review_status end,
    edit_count = edit_count + 1,
    updated_at = timezone('utc', now())
  where id = p_review_id;

  return public._m21_public_review_json((select x from public.m21_reviews x where x.id = p_review_id), v_actor);
end;
$$;

create or replace function public.m21_archive_review(p_review_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  r public.m21_reviews;
begin
  select * into r from public.m21_reviews where id = p_review_id;
  if not found then raise exception 'M21_REVIEW_NOT_FOUND'; end if;
  if r.reviewer_user_id <> v_actor then raise exception 'M21_PERMISSION_DENIED'; end if;

  if r.review_status <> 'ARCHIVED' then
    update public.m21_reviews set
      review_status = 'ARCHIVED',
      updated_at = timezone('utc', now())
    where id = p_review_id;
  end if;

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.m21_submit_review_response(
  p_review_id uuid,
  p_content text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  r public.m21_reviews;
  existing public.m21_review_responses;
  v_resp public.m21_review_responses;
begin
  perform public._m21_validate_response_content(p_content);

  select * into r from public.m21_reviews where id = p_review_id;
  if not found then raise exception 'M21_REVIEW_NOT_FOUND'; end if;
  if r.reviewer_user_id = v_actor then raise exception 'M21_PERMISSION_DENIED'; end if;
  if not public._m21_is_subject_owner(r.target_type, r.target_id, v_actor) then
    raise exception 'M21_PERMISSION_DENIED';
  end if;
  if not public._m21_is_public_review_status(r.review_status) then
    raise exception 'M21_PERMISSION_DENIED';
  end if;

  existing := public._m21_active_response(p_review_id);

  if existing is not null then
    update public.m21_review_responses set
      content = trim(p_content),
      response_status = 'EDITED',
      edit_count = edit_count + 1,
      updated_at = timezone('utc', now())
    where id = existing.id
    returning * into v_resp;
  else
    insert into public.m21_review_responses (
      review_id, responder_user_id, content, response_status
    ) values (
      p_review_id, v_actor, trim(p_content), 'PUBLISHED'
    ) returning * into v_resp;
  end if;

  update public.m21_reviews set updated_at = timezone('utc', now()) where id = p_review_id;

  return public._m21_public_response_json(v_resp);
end;
$$;

create or replace function public.m21_submit_dispute(
  p_review_id uuid,
  p_reason text,
  p_details text,
  p_evidence_ref text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  r public.m21_reviews;
  v_reason text := upper(trim(coalesce(p_reason, '')));
  v_details text := trim(coalesce(p_details, ''));
begin
  if char_length(v_details) < 10 or char_length(v_details) > 2000 then
    raise exception 'M21_INVALID_DISPUTE';
  end if;
  if v_reason not in (
    'FACTUAL_ERROR','CONFLICT_OF_INTEREST','HARASSMENT',
    'SPAM','PERSONAL_DATA','OTHER'
  ) then
    raise exception 'M21_INVALID_DISPUTE';
  end if;

  select * into r from public.m21_reviews where id = p_review_id;
  if not found then raise exception 'M21_REVIEW_NOT_FOUND'; end if;
  if r.reviewer_user_id = v_actor then raise exception 'M21_PERMISSION_DENIED'; end if;
  if not public._m21_is_subject_owner(r.target_type, r.target_id, v_actor) then
    raise exception 'M21_PERMISSION_DENIED';
  end if;
  if exists (
    select 1 from public.m21_review_disputes d
    where d.review_id = p_review_id and d.dispute_status = 'OPEN'
  ) then
    raise exception 'M21_DISPUTE_EXISTS';
  end if;

  insert into public.m21_review_disputes (
    review_id, claimant_user_id, reason, details, evidence_ref
  ) values (
    p_review_id, v_actor, v_reason, v_details, nullif(trim(p_evidence_ref), '')
  );

  if public._m21_is_public_review_status(r.review_status) then
    update public.m21_reviews set
      review_status = 'DISPUTED',
      updated_at = timezone('utc', now())
    where id = p_review_id;
  end if;

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.m21_report_review(
  p_review_id uuid,
  p_reason text,
  p_details text default null,
  p_report_response boolean default false
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  r public.m21_reviews;
  resp public.m21_review_responses;
  v_target_id text;
  v_reason text := lower(trim(coalesce(p_reason, 'other')));
  v_details text := nullif(trim(coalesce(p_details, '')), '');
  v_report jsonb;
begin
  perform public._m21_require_authenticated();

  if v_reason not in (
    'spam','harassment','hate','scam','impersonation',
    'inappropriate','violence','privacy','other'
  ) then
    v_reason := 'other';
  end if;

  select * into r from public.m21_reviews where id = p_review_id;
  if not found then raise exception 'M21_REVIEW_NOT_FOUND'; end if;

  if coalesce(p_report_response, false) then
    resp := public._m21_active_response(p_review_id);
    if resp is null then raise exception 'M21_REVIEW_NOT_FOUND'; end if;
    v_target_id := resp.id::text;
    v_details := coalesce(v_details, 'M21_REVIEW_RESPONSE');
  else
    v_target_id := p_review_id::text;
    v_details := coalesce(v_details, 'M21_REVIEW');
  end if;

  v_report := public.create_content_report('OTHER', v_target_id, v_reason, v_details);

  return jsonb_build_object('ok', true, 'report', v_report);
end;
$$;

-- Apelación: sujeto evaluado (no revisor). Distinta de disputa (m21_review_disputes).
create or replace function public.m21_submit_appeal(p_review_id uuid, p_reason text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  r public.m21_reviews;
begin
  select * into r from public.m21_reviews where id = p_review_id;
  if not found then raise exception 'M21_REVIEW_NOT_FOUND'; end if;
  if not public._m21_is_subject_owner(r.target_type, r.target_id, v_actor) then
    raise exception 'M21_PERMISSION_DENIED';
  end if;
  if char_length(trim(coalesce(p_reason, ''))) < 10 then raise exception 'M21_INVALID_APPEAL'; end if;
  if char_length(trim(p_reason)) > 1000 then raise exception 'M21_INVALID_APPEAL'; end if;
  if exists (select 1 from public.m21_appeals where review_id = p_review_id and appeal_status = 'OPEN') then
    raise exception 'M21_APPEAL_EXISTS';
  end if;

  insert into public.m21_appeals (review_id, appellant_user_id, reason)
  values (p_review_id, v_actor, trim(p_reason));

  if public._m21_is_public_review_status(r.review_status) then
    update public.m21_reviews set
      review_status = 'APPEALED',
      updated_at = timezone('utc', now())
    where id = p_review_id;
  end if;

  return jsonb_build_object('ok', true);
end;
$$;

-- Listados públicos enriquecidos (respuesta + estados extendidos)
create or replace function public.m21_list_reviews_for_target(p_target_type text, p_target_id text)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  r public.m21_reviews;
begin
  for r in
    select * from public.m21_reviews
    where target_type = upper(p_target_type)
      and target_id = p_target_id
      and review_status = any (public._m21_public_review_statuses())
    order by created_at desc
  loop
    return next public._m21_public_review_json(r, v_actor);
  end loop;
end;
$$;

drop function if exists public.m21_submit_verification(text, text, text, text, text);

create or replace function public.m21_submit_verification(
  p_verification_type text,
  p_display_label text,
  p_license_number text default null,
  p_issuing_authority text default null,
  p_jurisdiction text default null,
  p_evidence_ref text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  v_id uuid;
begin
  if upper(p_verification_type) = 'PROFESSIONAL_LICENSE'
     and coalesce(trim(p_license_number), '') = '' then
    raise exception 'M21_LICENSE_REQUIRED';
  end if;
  if char_length(trim(coalesce(p_display_label, ''))) < 1
     or char_length(trim(p_display_label)) > 120 then
    raise exception 'M21_INVALID_VERIFICATION';
  end if;

  insert into public.m21_verification_requests (
    user_id, verification_type, verification_status, display_label,
    license_number, issuing_authority, jurisdiction, evidence_ref
  ) values (
    v_actor, upper(p_verification_type), 'PENDING', trim(p_display_label),
    nullif(trim(p_license_number), ''), nullif(trim(p_issuing_authority), ''),
    nullif(trim(p_jurisdiction), ''), nullif(trim(p_evidence_ref), '')
  ) returning id into v_id;

  return (select jsonb_build_object(
    'id', v.id, 'verification_type', v.verification_type, 'status', v.verification_status,
    'display_label', v.display_label,
    'license_summary', case when v.license_number is not null
      then coalesce(v.issuing_authority, '') || ' · ' || coalesce(v.jurisdiction, '') else null end,
    'submitted_at', v.submitted_at, 'is_own_request', true
  ) from public.m21_verification_requests v where v.id = v_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7b. RPC — resumen propio (alinear estados contables post-edición)
-- ---------------------------------------------------------------------------
create or replace function public.m21_get_my_reputation_summary()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  v_score int;
  v_count int;
  v_avg numeric;
  v_identity boolean;
  v_license boolean;
begin
  select coalesce(u.reputation_score, 0) into v_score from public.users u where u.id = v_actor;
  select count(*)::int, avg(r.rating)::numeric
  into v_count, v_avg
  from public.m21_reviews r
  where r.reviewer_user_id = v_actor
    and public._m21_is_countable_review(r);
  select exists (
    select 1 from public.m21_verification_requests v
    where v.user_id = v_actor and v.verification_type = 'IDENTITY' and v.verification_status = 'APPROVED'
  ) into v_identity;
  select exists (
    select 1 from public.m21_verification_requests v
    where v.user_id = v_actor and v.verification_type = 'PROFESSIONAL_LICENSE' and v.verification_status = 'APPROVED'
  ) into v_license;
  return jsonb_build_object(
    'reputation_score', coalesce(v_score, 0),
    'published_review_count', coalesce(v_count, 0),
    'average_rating', v_avg,
    'identity_verified', coalesce(v_identity, false),
    'license_verified', coalesce(v_license, false)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Grants / revokes (patrón 064)
-- ---------------------------------------------------------------------------
revoke all on function public._m21_terminal_review_statuses() from public, anon, authenticated;
revoke all on function public._m21_public_review_statuses() from public, anon, authenticated;
revoke all on function public._m21_countable_review_statuses() from public, anon, authenticated;
revoke all on function public._m21_is_public_review_status(text) from public, anon, authenticated;
revoke all on function public._m21_is_countable_review(public.m21_reviews) from public, anon, authenticated;
revoke all on function public._m21_scrub_public_text(text) from public, anon, authenticated;
revoke all on function public._m21_validate_review_content(text, int) from public, anon, authenticated;
revoke all on function public._m21_validate_review_title(text) from public, anon, authenticated;
revoke all on function public._m21_validate_response_content(text) from public, anon, authenticated;
revoke all on function public._m21_is_self_review(uuid, text, text) from public, anon, authenticated;
revoke all on function public._m21_is_subject_owner(text, text, uuid) from public, anon, authenticated;
revoke all on function public._m21_active_response(uuid) from public, anon, authenticated;
revoke all on function public._m21_public_response_json(public.m21_review_responses) from public, anon, authenticated;
revoke all on function public._m21_evaluate_eligibility(uuid, text, text, text, text, text, text) from public, anon, authenticated;
revoke all on function public._m21_rating_distribution(text, text) from public, anon, authenticated;

revoke all on function public.m21_check_eligibility(text, text, text, text, text, text) from public, anon;
revoke all on function public.m21_get_subject_breakdown(text, text) from public, anon;
revoke all on function public.m21_get_review_detail(uuid) from public, anon;
revoke all on function public.m21_edit_review(uuid, int, text, text) from public, anon;
revoke all on function public.m21_archive_review(uuid) from public, anon;
revoke all on function public.m21_submit_review_response(uuid, text) from public, anon;
revoke all on function public.m21_submit_dispute(uuid, text, text, text) from public, anon;
revoke all on function public.m21_report_review(uuid, text, text, boolean) from public, anon;
revoke all on function public.m21_submit_review(text, text, text, int, text, text, text, text, text) from public, anon;
revoke all on function public.m21_submit_verification(text, text, text, text, text, text) from public, anon;

grant execute on function public.m21_check_eligibility(text, text, text, text, text, text) to authenticated;
grant execute on function public.m21_get_subject_breakdown(text, text) to authenticated;
grant execute on function public.m21_get_review_detail(uuid) to authenticated;
grant execute on function public.m21_edit_review(uuid, int, text, text) to authenticated;
grant execute on function public.m21_archive_review(uuid) to authenticated;
grant execute on function public.m21_submit_review_response(uuid, text) to authenticated;
grant execute on function public.m21_submit_dispute(uuid, text, text, text) to authenticated;
grant execute on function public.m21_report_review(uuid, text, text, boolean) to authenticated;
grant execute on function public.m21_submit_review(text, text, text, int, text, text, text, text, text) to authenticated;
grant execute on function public.m21_submit_verification(text, text, text, text, text, text) to authenticated;

commit;
