-- Hotfix post-065: breakdown ambiguous columns + unicidad por contexto (no por target)
begin;

drop index if exists public.m21_reviews_reviewer_target_uniq;

create or replace function public._m21_rating_distribution(p_target_type text, p_target_id text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'one_star', count(*) filter (where rating = 1),
    'two_stars', count(*) filter (where rating = 2),
    'three_stars', count(*) filter (where rating = 3),
    'four_stars', count(*) filter (where rating = 4),
    'five_stars', count(*) filter (where rating = 5)
  )
  from public.m21_reviews
  where target_type = upper(p_target_type)
    and target_id = p_target_id
    and review_status = any (public._m21_countable_review_statuses())
    and context_id is not null;
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
  select count(*)::int, avg(m21_reviews.rating)::numeric, max(m21_reviews.created_at)
  into v_count, v_avg, v_last
  from public.m21_reviews
  where m21_reviews.target_type = v_type and m21_reviews.target_id = v_target
    and m21_reviews.review_status = any (public._m21_countable_review_statuses())
    and m21_reviews.context_id is not null;

  select count(*)::int into v_resp_count
  from public.m21_reviews rev
  where rev.target_type = v_type and rev.target_id = v_target
    and rev.review_status = any (public._m21_countable_review_statuses())
    and rev.context_id is not null
    and exists (
      select 1 from public.m21_review_responses resp
      where resp.review_id = rev.id
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

commit;

-- Fix eligibility lookup when context omitted
begin;

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
    and e.context_id = v_ctx_id
  order by e.completed_at desc
  limit 1;

  if not found then
    return jsonb_build_object(
      'eligible', false,
      'reason', 'NOT_ELIGIBLE',
      'subject', v_subject,
      'context_reference', jsonb_build_object(
        'context_type', v_ctx_type, 'context_id', v_ctx_id, 'public_label', v_ctx_label
      )
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

commit;
