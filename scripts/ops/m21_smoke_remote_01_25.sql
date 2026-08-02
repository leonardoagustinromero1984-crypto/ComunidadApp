-- M21 smoke remoto casos 01–25 — validación staging (SQL/RPC, no Android)
-- Ejecutar: supabase db query --linked -f scripts/ops/m21_smoke_remote_01_25.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m21_smoke_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m21_smoke(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m21_smoke_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m21_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_reviewer uuid := 'f0000000-0000-4000-8000-000000000001';
  v_subject uuid := 'f0000000-0000-4000-8000-000000000002';
  v_out uuid := 'f0000000-0000-4000-8000-000000000003';
  v_ctx text := 'm21-smoke-ctx-001';
  v_ctx2 text := 'm21-smoke-ctx-002';
  v_review_id uuid;
  v_review2_id uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_err text;
  v_ok boolean;
  v_now timestamptz := timezone('utc', now());
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_reviewer, 'authenticated', 'authenticated',
     'm21-smoke-reviewer@test.local', crypt('m21-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_subject, 'authenticated', 'authenticated',
     'm21-smoke-subject@test.local', crypt('m21-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm21-smoke-out@test.local', crypt('m21-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status, reputation_score)
  values
    (v_reviewer, 'm21-smoke-reviewer@test.local', 'M21 Smoke Reviewer', 'M21 Smoke Reviewer', 'PERSON', true, 'ACTIVE', 0),
    (v_subject, 'm21-smoke-subject@test.local', 'M21 Smoke Subject', 'M21 Smoke Subject', 'PERSON', true, 'ACTIVE', 0),
    (v_out, 'm21-smoke-out@test.local', 'M21 Smoke Outsider', 'M21 Smoke Outsider', 'PERSON', true, 'ACTIVE', 0)
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m21_review_disputes
  where review_id in (select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out));
  delete from public.m21_appeals
  where review_id in (select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out));
  delete from public.m21_review_responses
  where review_id in (select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out));
  delete from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out);
  delete from public.m21_verification_requests where user_id in (v_reviewer, v_subject, v_out);
  delete from public.m21_eligibility_records where context_id like 'm21-smoke-%';

  insert into public.m21_eligibility_records (
    reviewer_user_id, target_type, target_id, context_type, context_id,
    context_public_label, completed_at
  ) values
    (v_reviewer, 'USER', v_subject::text, 'SUPPORT_CONVERSATION', v_ctx,
     'Conversación smoke M21', v_now - interval '1 day'),
    (v_reviewer, 'USER', v_subject::text, 'DONATION_COMPLETED', v_ctx2,
     'Donación smoke M21', v_now - interval '2 days');
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ========================================================================
  -- 01 SupabaseM21ReputationRepository wired
  -- ========================================================================
  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm21_get_my_reputation_summary';
  v_ok := v_cnt >= 1;

  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_get_my_reputation_summary();
    perform pg_temp.m21_smoke(1, 'SupabaseM21ReputationRepository wired',
      v_ok and v_json ? 'reputation_score',
      case when v_ok then 'RPC m21_get_my_reputation_summary callable' else 'RPC missing' end);
  exception when others then
    perform pg_temp.m21_smoke(1, 'SupabaseM21ReputationRepository wired', false, SQLERRM);
  end;

  -- ========================================================================
  -- 02 Check eligibility
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_check_eligibility(
      'USER', v_subject::text, 'M21 Smoke Subject',
      'SUPPORT_CONVERSATION', v_ctx, 'Conversación smoke M21'
    );
    perform pg_temp.m21_smoke(2, 'Check eligibility eligible',
      (v_json->>'eligible')::boolean = true);
  exception when others then
    perform pg_temp.m21_smoke(2, 'Check eligibility eligible', false, SQLERRM);
  end;

  -- ========================================================================
  -- 03 Submit review
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_submit_review(
      'USER', v_subject::text, 'M21 Smoke Subject', 5,
      'Excelente experiencia de apoyo en la comunidad LeoVer smoke M21.',
      'Título smoke M21', 'SUPPORT_CONVERSATION', v_ctx, 'Conversación smoke M21'
    );
    v_review_id := (v_json->>'id')::uuid;
    perform pg_temp.m21_smoke(3, 'Submit review with context', v_review_id is not null);
  exception when others then
    perform pg_temp.m21_smoke(3, 'Submit review with context', false, SQLERRM);
  end;

  -- ========================================================================
  -- 04 List my reviews
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    select count(*)::int into v_cnt from public.m21_list_my_reviews();
    perform pg_temp.m21_smoke(4, 'List my reviews', v_cnt >= 1);
  exception when others then
    perform pg_temp.m21_smoke(4, 'List my reviews', false, SQLERRM);
  end;

  -- ========================================================================
  -- 05 List reviews for target
  -- ========================================================================
  perform pg_temp.m21_act_as(v_out);
  begin
    select count(*)::int into v_cnt
    from public.m21_list_reviews_for_target('USER', v_subject::text);
    perform pg_temp.m21_smoke(5, 'List reviews for target', v_cnt >= 1);
  exception when others then
    perform pg_temp.m21_smoke(5, 'List reviews for target', false, SQLERRM);
  end;

  -- ========================================================================
  -- 06 Edit own review
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_reviewer);
    begin
      v_json := public.m21_edit_review(
        v_review_id, 4, 'Contenido editado smoke M21 remoto.', 'Título editado smoke'
      );
      perform pg_temp.m21_smoke(6, 'Edit own review',
        v_json->>'status' = 'EDITED' and v_json->>'content' like 'Contenido editado%');
    exception when others then
      perform pg_temp.m21_smoke(6, 'Edit own review', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(6, 'Edit own review', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 07 Subject response
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_subject);
    begin
      v_json := public.m21_submit_review_response(
        v_review_id, 'Respuesta oficial del sujeto evaluado smoke M21 remoto.'
      );
      perform pg_temp.m21_smoke(7, 'Subject review response', v_json->>'id' is not null);
    exception when others then
      perform pg_temp.m21_smoke(7, 'Subject review response', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(7, 'Subject review response', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 08 Subject dispute
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_subject);
    begin
      v_json := public.m21_submit_dispute(
        v_review_id, 'FACTUAL_ERROR',
        'Detalle disputa smoke M21 remoto con descripción suficiente para moderación.'
      );
      perform pg_temp.m21_smoke(8, 'Subject dispute opens DISPUTED',
        coalesce(v_json->>'ok', 'false') = 'true');
    exception when others then
      perform pg_temp.m21_smoke(8, 'Subject dispute opens DISPUTED', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(8, 'Subject dispute opens DISPUTED', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 09 Subject appeal (065 flow)
  -- ========================================================================
  if v_review_id is not null then
    perform set_config('request.jwt.claim.role', 'service_role', true);
    delete from public.m21_review_disputes where review_id = v_review_id;
    update public.m21_reviews set review_status = 'PUBLISHED', updated_at = v_now where id = v_review_id;
    perform set_config('request.jwt.claim.role', 'postgres', true);

    perform pg_temp.m21_act_as(v_subject);
    begin
      v_json := public.m21_submit_appeal(
        v_review_id, 'Apelación smoke M21 remoto del sujeto evaluado con motivo suficiente.'
      );
      perform pg_temp.m21_smoke(9, 'Subject appeal (065)', coalesce(v_json->>'ok', 'false') = 'true');
    exception when others then
      perform pg_temp.m21_smoke(9, 'Subject appeal (065)', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(9, 'Subject appeal (065)', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 10 Archive own review (second context)
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_submit_review(
      'USER', v_subject::text, 'M21 Smoke Subject', 5,
      'Segunda reseña smoke M21 para prueba archive.',
      null, 'DONATION_COMPLETED', v_ctx2, 'Donación smoke M21'
    );
    v_review2_id := (v_json->>'id')::uuid;
    perform public.m21_archive_review(v_review2_id);
    perform pg_temp.m21_smoke(10, 'Archive own review', true);
  exception when others then
    perform pg_temp.m21_smoke(10, 'Archive own review', false, SQLERRM);
  end;

  -- ========================================================================
  -- 11 Submit verification identity
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_submit_verification(
      'IDENTITY', 'Verificación identidad smoke M21', null, null, null, 'm21/smoke-evidence.jpg'
    );
    perform pg_temp.m21_smoke(11, 'Submit identity verification', v_json->>'verification_type' = 'IDENTITY');
  exception when others then
    perform pg_temp.m21_smoke(11, 'Submit identity verification', false, SQLERRM);
  end;

  -- ========================================================================
  -- 12 List my verifications
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    select count(*)::int into v_cnt from public.m21_list_my_verifications();
    perform pg_temp.m21_smoke(12, 'List my verifications', v_cnt >= 1);
  exception when others then
    perform pg_temp.m21_smoke(12, 'List my verifications', false, SQLERRM);
  end;

  -- ========================================================================
  -- 13 Subject breakdown aggregates
  -- ========================================================================
  perform pg_temp.m21_act_as(v_out);
  begin
    v_json := public.m21_get_subject_breakdown('USER', v_subject::text);
    perform pg_temp.m21_smoke(13, 'Subject breakdown aggregates',
      v_json ? 'average_rating' and v_json ? 'rating_distribution');
  exception when others then
    perform pg_temp.m21_smoke(13, 'Subject breakdown aggregates', false, SQLERRM);
  end;

  -- ========================================================================
  -- 14 Review detail
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_reviewer);
    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_smoke(14, 'Review detail', v_json->>'id' = v_review_id::text);
    exception when others then
      perform pg_temp.m21_smoke(14, 'Review detail', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(14, 'Review detail', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 15 Report review via M04
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_out);
    begin
      v_json := public.m21_report_review(v_review_id, 'spam', 'Reporte smoke M21 remoto');
      perform pg_temp.m21_smoke(15, 'Report review via M04', coalesce(v_json->>'ok', 'false') = 'true');
    exception when others then
      perform pg_temp.m21_smoke(15, 'Report review via M04', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(15, 'Report review via M04', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 16 Self review denied
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    perform public.m21_submit_review(
      'USER', v_reviewer::text, 'Yo mismo', 5,
      'Auto reseña smoke M21 no permitida.',
      null, 'SUPPORT_CONVERSATION', 'm21-smoke-self', 'Auto contexto smoke'
    );
    perform pg_temp.m21_smoke(16, 'Self review denied', false);
  exception when others then
    perform pg_temp.m21_smoke(16, 'Self review denied', SQLERRM like '%M21_SELF_REVIEW%');
  end;

  -- ========================================================================
  -- 17 Not eligible denied
  -- ========================================================================
  perform pg_temp.m21_act_as(v_out);
  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Smoke Subject', 5,
      'Reseña smoke sin elegibilidad M21 remoto.',
      null, 'SUPPORT_CONVERSATION', v_ctx, 'Conversación smoke M21'
    );
    perform pg_temp.m21_smoke(17, 'Not eligible denied', false);
  exception when others then
    perform pg_temp.m21_smoke(17, 'Not eligible denied',
      SQLERRM like '%M21_NOT_ELIGIBLE%' or SQLERRM like '%M21_REVIEW_ELIGIBILITY_UNAVAILABLE%');
  end;

  -- ========================================================================
  -- 18 Stranger edit denied
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_out);
    begin
      perform public.m21_edit_review(v_review_id, 1, 'Hack edit smoke M21 ajeno.');
      perform pg_temp.m21_smoke(18, 'Stranger edit denied', false);
    exception when others then
      perform pg_temp.m21_smoke(18, 'Stranger edit denied', SQLERRM like '%M21_PERMISSION_DENIED%');
    end;
  else
    perform pg_temp.m21_smoke(18, 'Stranger edit denied', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 19 No reviewer_user_id in public JSON
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_reviewer);
    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_smoke(19, 'No reviewer_user_id in public JSON',
        v_json->>'reviewer_user_id' is null);
    exception when others then
      perform pg_temp.m21_smoke(19, 'No reviewer_user_id in public JSON', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(19, 'No reviewer_user_id in public JSON', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 20 No target_id in review JSON
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_out);
    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_smoke(20, 'No target_id in review JSON', v_json->>'target_id' is null);
    exception when others then
      perform pg_temp.m21_smoke(20, 'No target_id in review JSON', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(20, 'No target_id in review JSON', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- 21 Duplicate context rejected
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Smoke Subject', 5,
      'Duplicado contexto smoke M21 remoto.',
      null, 'SUPPORT_CONVERSATION', v_ctx, 'Conversación smoke M21'
    );
    perform pg_temp.m21_smoke(21, 'Duplicate context rejected', false);
  exception when others then
    perform pg_temp.m21_smoke(21, 'Duplicate context rejected',
      SQLERRM like '%M21_DUPLICATE_REVIEW%' or SQLERRM like '%M21_NOT_ELIGIBLE%');
  end;

  -- ========================================================================
  -- 22 Invalid rating rejected
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Smoke Subject', 6,
      'Rating inválido smoke M21 remoto.',
      null, 'DONATION_COMPLETED', 'm21-smoke-invalid', 'Ctx inválido smoke'
    );
    perform pg_temp.m21_smoke(22, 'Invalid rating rejected', false);
  exception when others then
    perform pg_temp.m21_smoke(22, 'Invalid rating rejected', SQLERRM like '%M21_INVALID_RATING%');
  end;

  -- ========================================================================
  -- 23 Invalid content rejected
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Smoke Subject', 5, '',
      null, 'DONATION_COMPLETED', 'm21-smoke-empty', 'Ctx vacío smoke'
    );
    perform pg_temp.m21_smoke(23, 'Invalid content rejected', false);
  exception when others then
    perform pg_temp.m21_smoke(23, 'Invalid content rejected', SQLERRM like '%M21_INVALID_REVIEW%');
  end;

  -- ========================================================================
  -- 24 Reputation summary after submit
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_get_my_reputation_summary();
    perform pg_temp.m21_smoke(24, 'Reputation summary after submit',
      (v_json->>'published_review_count')::int >= 1);
  exception when others then
    perform pg_temp.m21_smoke(24, 'Reputation summary after submit', false, SQLERRM);
  end;

  -- ========================================================================
  -- 25 Verified experience badge in public JSON
  -- ========================================================================
  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_reviewer);
    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_smoke(25, 'Verified experience badge',
        v_json->>'eligible_experience_badge' = 'Experiencia verificada');
    exception when others then
      perform pg_temp.m21_smoke(25, 'Verified experience badge', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_smoke(25, 'Verified experience badge', false, 'prerequisite case 3 failed');
  end if;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'service_role', true);

  delete from public.m21_review_disputes
  where review_id in (
    select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out)
  );

  delete from public.m21_appeals
  where review_id in (
    select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out)
  );

  delete from public.m21_review_responses
  where review_id in (
    select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out)
  );

  delete from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out);

  delete from public.m21_verification_requests where user_id in (v_reviewer, v_subject, v_out);

  delete from public.m21_eligibility_records where context_id like 'm21-smoke-%';

  -- No eliminar usuarios si M04 audit los referencia (FK)
  perform set_config('request.jwt.claim.role', 'postgres', true);
end;
$setup$;

select case_id, label, result, detail
from m21_smoke_results
where result = 'FAIL'
order by case_id;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m21_smoke_results;

create table if not exists public._m21_smoke_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m21_smoke_last_failures;

insert into public._m21_smoke_last_failures (case_id, label, detail)
select case_id, label, detail from m21_smoke_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m21_smoke_results where result = 'FAIL' order by case_id loop
    raise warning 'M21_SMOKE_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

commit;
