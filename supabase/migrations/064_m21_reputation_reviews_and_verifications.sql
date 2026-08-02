-- =============================================================================
-- LeoVer M21 — migración 064: reseñas transaccionales, verificaciones y apelaciones.
-- Forward-only sobre 001–063. LOCAL ONLY — no aplicar hasta staging autorizado.
-- =============================================================================

begin;

create table if not exists public.m21_reviews (
  id uuid primary key default gen_random_uuid(),
  target_type text not null,
  target_id text not null,
  target_display_label text not null,
  reviewer_user_id uuid not null references public.users (id) on delete restrict,
  reviewer_display_name text not null default 'Participante',
  rating integer not null,
  content text not null,
  review_status text not null default 'PUBLISHED',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m21_review_type_chk check (
    target_type = any (array['ADOPTION','SERVICE','DONATION','ORGANIZATION','USER']::text[])
  ),
  constraint m21_review_status_chk check (
    review_status = any (array['PENDING','PUBLISHED','HIDDEN','REMOVED','APPEALED']::text[])
  ),
  constraint m21_review_rating_chk check (rating between 1 and 5),
  constraint m21_review_content_len check (char_length(trim(content)) between 1 and 2000)
);

create unique index if not exists m21_reviews_reviewer_target_uniq
  on public.m21_reviews (reviewer_user_id, target_type, target_id)
  where review_status not in ('REMOVED');

create index if not exists m21_reviews_target_idx
  on public.m21_reviews (target_type, target_id, review_status, created_at desc);

create table if not exists public.m21_verification_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users (id) on delete cascade,
  verification_type text not null,
  verification_status text not null default 'PENDING',
  display_label text not null,
  license_number text,
  issuing_authority text,
  jurisdiction text,
  submitted_at timestamptz not null default timezone('utc', now()),
  reviewed_at timestamptz,
  rejection_reason text,
  constraint m21_ver_type_chk check (
    verification_type = any (array['IDENTITY','PROFESSIONAL_LICENSE']::text[])
  ),
  constraint m21_ver_status_chk check (
    verification_status = any (array[
      'NOT_SUBMITTED','PENDING','APPROVED','REJECTED','EXPIRED'
    ]::text[])
  )
);

create index if not exists m21_ver_user_idx
  on public.m21_verification_requests (user_id, verification_status);

create table if not exists public.m21_appeals (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.m21_reviews (id) on delete cascade,
  appellant_user_id uuid not null references public.users (id) on delete restrict,
  reason text not null,
  appeal_status text not null default 'OPEN',
  created_at timestamptz not null default timezone('utc', now()),
  constraint m21_appeal_status_chk check (
    appeal_status = any (array['OPEN','UNDER_REVIEW','RESOLVED','DISMISSED']::text[])
  ),
  constraint m21_appeal_reason_len check (char_length(trim(reason)) between 10 and 1000)
);

create unique index if not exists m21_appeals_open_uniq
  on public.m21_appeals (review_id)
  where appeal_status = 'OPEN';

-- RLS deny direct mutation
alter table public.m21_reviews enable row level security;
alter table public.m21_verification_requests enable row level security;
alter table public.m21_appeals enable row level security;

create policy m21_reviews_select on public.m21_reviews for select to authenticated using (true);
create policy m21_reviews_mut on public.m21_reviews for all to authenticated using (false);
create policy m21_ver_select on public.m21_verification_requests for select to authenticated
  using (user_id = auth.uid());
create policy m21_ver_mut on public.m21_verification_requests for all to authenticated using (false);
create policy m21_appeals_select on public.m21_appeals for select to authenticated
  using (appellant_user_id = auth.uid());
create policy m21_appeals_mut on public.m21_appeals for all to authenticated using (false);

revoke all on table public.m21_reviews from public, anon;
revoke all on table public.m21_verification_requests from public, anon;
revoke all on table public.m21_appeals from public, anon;
grant select on table public.m21_reviews to authenticated;
grant select on table public.m21_verification_requests to authenticated;
grant select on table public.m21_appeals to authenticated;
grant all on table public.m21_reviews to service_role;
grant all on table public.m21_verification_requests to service_role;
grant all on table public.m21_appeals to service_role;

create or replace function public._m21_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v;
end;
$$;

create or replace function public._m21_public_review_json(p_review public.m21_reviews, p_actor uuid)
returns jsonb language sql stable as $$
  select jsonb_build_object(
    'id', p_review.id,
    'target_type', p_review.target_type,
    'target_display_label', p_review.target_display_label,
    'reviewer_display_name', p_review.reviewer_display_name,
    'rating', p_review.rating,
    'content', p_review.content,
    'status', p_review.review_status,
    'created_at', p_review.created_at,
    'is_own_review', p_review.reviewer_user_id = p_actor
  );
$$;

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
  where r.reviewer_user_id = v_actor and r.review_status = 'PUBLISHED';
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

create or replace function public.m21_list_my_reviews()
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m21_require_authenticated(); r public.m21_reviews;
begin
  for r in select * from public.m21_reviews where reviewer_user_id = v_actor order by created_at desc loop
    return next public._m21_public_review_json(r, v_actor);
  end loop;
end;
$$;

create or replace function public.m21_list_reviews_for_target(p_target_type text, p_target_id text)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m21_require_authenticated(); r public.m21_reviews;
begin
  for r in
    select * from public.m21_reviews
    where target_type = upper(p_target_type) and target_id = p_target_id and review_status = 'PUBLISHED'
    order by created_at desc
  loop
    return next public._m21_public_review_json(r, v_actor);
  end loop;
end;
$$;

create or replace function public.m21_submit_review(
  p_target_type text, p_target_id text, p_target_display_label text, p_rating int, p_content text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m21_require_authenticated();
  v_id uuid;
  v_name text;
begin
  if p_rating < 1 or p_rating > 5 then raise exception 'M21_INVALID_RATING'; end if;
  if char_length(trim(coalesce(p_content, ''))) < 1 then raise exception 'M21_INVALID_REVIEW'; end if;
  if exists (
    select 1 from public.m21_reviews
    where reviewer_user_id = v_actor and target_type = upper(p_target_type)
      and target_id = p_target_id and review_status <> 'REMOVED'
  ) then raise exception 'M21_DUPLICATE_REVIEW'; end if;
  v_name := coalesce((select nullif(trim(name), '') from public.users where id = v_actor), 'Participante');
  insert into public.m21_reviews (
    target_type, target_id, target_display_label, reviewer_user_id, reviewer_display_name,
    rating, content, review_status
  ) values (
    upper(p_target_type), p_target_id, trim(p_target_display_label), v_actor, v_name,
    p_rating, trim(p_content), 'PUBLISHED'
  ) returning id into v_id;
  update public.users set reputation_score = coalesce(reputation_score, 0) + 5 where id = v_actor;
  return public._m21_public_review_json((select r from public.m21_reviews r where r.id = v_id), v_actor);
end;
$$;

create or replace function public.m21_list_my_verifications()
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := public._m21_require_authenticated(); v public.m21_verification_requests;
begin
  for v in select * from public.m21_verification_requests where user_id = v_actor order by submitted_at desc loop
    return next jsonb_build_object(
      'id', v.id,
      'verification_type', v.verification_type,
      'status', v.verification_status,
      'display_label', v.display_label,
      'license_summary', case when v.license_number is not null
        then coalesce(v.issuing_authority, '') || ' · ' || coalesce(v.jurisdiction, '') else null end,
      'submitted_at', v.submitted_at,
      'is_own_request', true
    );
  end loop;
end;
$$;

create or replace function public.m21_submit_verification(
  p_verification_type text, p_display_label text,
  p_license_number text default null, p_issuing_authority text default null, p_jurisdiction text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m21_require_authenticated(); v_id uuid;
begin
  if upper(p_verification_type) = 'PROFESSIONAL_LICENSE' and coalesce(trim(p_license_number), '') = '' then
    raise exception 'M21_LICENSE_REQUIRED';
  end if;
  insert into public.m21_verification_requests (
    user_id, verification_type, verification_status, display_label,
    license_number, issuing_authority, jurisdiction
  ) values (
    v_actor, upper(p_verification_type), 'PENDING', trim(p_display_label),
    nullif(trim(p_license_number), ''), nullif(trim(p_issuing_authority), ''), nullif(trim(p_jurisdiction), '')
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

create or replace function public.m21_submit_appeal(p_review_id uuid, p_reason text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m21_require_authenticated(); r public.m21_reviews;
begin
  select * into r from public.m21_reviews where id = p_review_id;
  if not found then raise exception 'M21_REVIEW_NOT_FOUND'; end if;
  if r.reviewer_user_id <> v_actor then raise exception 'M21_PERMISSION_DENIED'; end if;
  if char_length(trim(coalesce(p_reason, ''))) < 10 then raise exception 'M21_INVALID_APPEAL'; end if;
  if exists (select 1 from public.m21_appeals where review_id = p_review_id and appeal_status = 'OPEN') then
    raise exception 'M21_APPEAL_EXISTS';
  end if;
  insert into public.m21_appeals (review_id, appellant_user_id, reason) values (p_review_id, v_actor, trim(p_reason));
  update public.m21_reviews set review_status = 'APPEALED', updated_at = timezone('utc', now()) where id = p_review_id;
  return jsonb_build_object('ok', true);
end;
$$;

revoke all on function public.m21_get_my_reputation_summary() from public, anon;
revoke all on function public.m21_list_my_reviews() from public, anon;
revoke all on function public.m21_list_reviews_for_target(text, text) from public, anon;
revoke all on function public.m21_submit_review(text, text, text, int, text) from public, anon;
revoke all on function public.m21_list_my_verifications() from public, anon;
revoke all on function public.m21_submit_verification(text, text, text, text, text) from public, anon;
revoke all on function public.m21_submit_appeal(uuid, text) from public, anon;

grant execute on function public.m21_get_my_reputation_summary() to authenticated;
grant execute on function public.m21_list_my_reviews() to authenticated;
grant execute on function public.m21_list_reviews_for_target(text, text) to authenticated;
grant execute on function public.m21_submit_review(text, text, text, int, text) to authenticated;
grant execute on function public.m21_list_my_verifications() to authenticated;
grant execute on function public.m21_submit_verification(text, text, text, text, text) to authenticated;
grant execute on function public.m21_submit_appeal(uuid, text) to authenticated;

commit;
