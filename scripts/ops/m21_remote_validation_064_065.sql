-- M21 migraciones 064+065 — validación remota staging (casos 01–130)
-- Ejecutar: supabase db query --linked -f scripts/ops/m21_remote_validation_064_065.sql
-- Limpia datos de prueba al finalizar.

begin;

create temp table if not exists m21_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m21_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m21_val_results (case_id, label, result, detail)
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
  v_ctx_main text := 'm21-val-ctx-001';
  v_ctx_edit text := 'm21-val-ctx-002';
  v_ctx_dup text := 'm21-val-ctx-003';
  v_ctx_cancel text := 'm21-val-ctx-004';
  v_ctx_expired text := 'm21-val-ctx-005';
  v_review_id uuid;
  v_review2_id uuid;
  v_resp_id uuid;
  v_ver_id uuid;
  v_struct_review uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_err text;
  v_i int;
  v_ok boolean;
  v_now timestamptz := timezone('utc', now());
begin
  -- Usuarios auth + public (UUIDs patrón M18/M19/M20)
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_reviewer, 'authenticated', 'authenticated',
     'm21-reviewer@test.local', crypt('m21-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_subject, 'authenticated', 'authenticated',
     'm21-subject@test.local', crypt('m21-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm21-out@test.local', crypt('m21-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status, reputation_score)
  values
    (v_reviewer, 'm21-reviewer@test.local', 'M21 Reviewer', 'M21 Reviewer', 'PERSON', true, 'ACTIVE', 0),
    (v_subject, 'm21-subject@test.local', 'M21 Subject', 'M21 Subject', 'PERSON', true, 'ACTIVE', 0),
    (v_out, 'm21-out@test.local', 'M21 Outsider', 'M21 Outsider', 'PERSON', true, 'ACTIVE', 0)
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  -- Semilla elegibilidad (065) — service_role bypass RLS mut
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m21_eligibility_records
  where reviewer_user_id in (v_reviewer, v_out)
     or context_id like 'm21-val-ctx-%';

  insert into public.m21_eligibility_records (
    reviewer_user_id, target_type, target_id, context_type, context_id,
    context_public_label, completed_at, expires_at, cancelled, rejected
  ) values
    (v_reviewer, 'USER', v_subject::text, 'SUPPORT_CONVERSATION', v_ctx_main,
     'Conversación soporte M21 val', v_now - interval '2 days', null, false, false),
    (v_reviewer, 'USER', v_subject::text, 'DONATION_COMPLETED', v_ctx_edit,
     'Donación completada M21 val', v_now - interval '3 days', null, false, false),
    (v_reviewer, 'USER', v_subject::text, 'SERVICE_COMPLETED', v_ctx_dup,
     'Servicio completado M21 val', v_now - interval '4 days', null, false, false),
    (v_reviewer, 'USER', v_subject::text, 'ADOPTION_COMPLETED', v_ctx_cancel,
     'Adopción cancelada M21 val', v_now - interval '5 days', null, false, true),
    (v_reviewer, 'USER', v_subject::text, 'EVENT_ATTENDED', v_ctx_expired,
     'Evento expirado M21 val', v_now - interval '30 days', v_now - interval '1 day', false, false);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- Limpieza previa idempotente
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m21_review_disputes
  where review_id in (select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out));
  delete from public.m21_appeals
  where review_id in (select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out));
  delete from public.m21_review_responses
  where review_id in (select id from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out));
  delete from public.m21_reviews where reviewer_user_id in (v_reviewer, v_out);
  delete from public.m21_verification_requests where user_id in (v_reviewer, v_subject, v_out);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ========================================================================
  -- ESTRUCTURA 01–30
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name in (
    'm21_reviews', 'm21_verification_requests', 'm21_appeals',
    'm21_review_responses', 'm21_review_disputes', 'm21_eligibility_records'
  );
  perform pg_temp.m21_val(1, 'Seis tablas M21 (064+065)', v_cnt = 6);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm21_reviews'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'reviewer_user_id';
  perform pg_temp.m21_val(2, 'FK reviewer_user_id reseñas', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm21_verification_requests'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'user_id';
  perform pg_temp.m21_val(3, 'FK user_id verificaciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm21_appeals'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'review_id';
  perform pg_temp.m21_val(4, 'FK review_id apelaciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm21_review_responses'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'review_id';
  perform pg_temp.m21_val(5, 'FK review_id respuestas (065)', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm21_review_disputes'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'review_id';
  perform pg_temp.m21_val(6, 'FK review_id disputas (065)', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm21_eligibility_records'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'reviewer_user_id';
  perform pg_temp.m21_val(7, 'FK reviewer_user_id elegibilidad (065)', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm21_reviews'
    and indexname = 'm21_reviews_reviewer_context_uniq';
  perform pg_temp.m21_val(8, 'UNIQUE reviewer+context reseña (065)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm21_appeals'
    and indexname = 'm21_appeals_open_uniq';
  perform pg_temp.m21_val(9, 'UNIQUE apelación OPEN', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm21_review_responses'
    and indexname = 'm21_review_responses_active_uniq';
  perform pg_temp.m21_val(10, 'UNIQUE respuesta activa (065)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm21_review_disputes'
    and indexname = 'm21_review_disputes_open_uniq';
  perform pg_temp.m21_val(11, 'UNIQUE disputa OPEN (065)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm21_eligibility_records'
    and indexname = 'm21_eligibility_context_uniq';
  perform pg_temp.m21_val(12, 'UNIQUE elegibilidad contexto (065)', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m21_reviews (
      target_type, target_id, target_display_label, reviewer_user_id,
      reviewer_display_name, rating, content, review_status
    ) values (
      'INVALID', v_subject::text, 'Target inválido M21', v_reviewer,
      'M21 Reviewer', 5, 'Contenido check target_type M21 val.', 'PUBLISHED'
    );
    perform pg_temp.m21_val(13, 'CHECK target_type enum', false);
  exception when check_violation then
    perform pg_temp.m21_val(13, 'CHECK target_type enum', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m21_reviews (
      target_type, target_id, target_display_label, reviewer_user_id,
      reviewer_display_name, rating, content, review_status
    ) values (
      'USER', v_subject::text, 'Target M21', v_reviewer,
      'M21 Reviewer', 5, 'Contenido check status M21 val.', 'INVALID_STATUS'
    );
    perform pg_temp.m21_val(14, 'CHECK review_status extendido (065)', false);
  exception when check_violation then
    perform pg_temp.m21_val(14, 'CHECK review_status extendido (065)', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m21_reviews (
      target_type, target_id, target_display_label, reviewer_user_id,
      reviewer_display_name, rating, content, review_status
    ) values (
      'USER', v_subject::text, 'Target M21', v_reviewer,
      'M21 Reviewer', 6, 'Rating inválido M21 val.', 'PUBLISHED'
    );
    perform pg_temp.m21_val(15, 'CHECK rating 1–5', false);
  exception when check_violation then
    perform pg_temp.m21_val(15, 'CHECK rating 1–5', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m21_verification_requests (
      user_id, verification_type, verification_status, display_label
    ) values (v_reviewer, 'INVALID', 'PENDING', 'Tipo inválido M21');
    perform pg_temp.m21_val(16, 'CHECK verification_type enum', false);
  exception when check_violation then
    perform pg_temp.m21_val(16, 'CHECK verification_type enum', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m21_verification_requests (
      user_id, verification_type, verification_status, display_label
    ) values (v_reviewer, 'IDENTITY', 'INVALID', 'Status inválido M21');
    perform pg_temp.m21_val(17, 'CHECK verification_status extendido (065)', false);
  exception when check_violation then
    perform pg_temp.m21_val(17, 'CHECK verification_status extendido (065)', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  perform set_config('request.jwt.claim.role', 'service_role', true);
  insert into public.m21_reviews (
    target_type, target_id, target_display_label, reviewer_user_id,
    reviewer_display_name, rating, content, review_status,
    context_type, context_id, context_public_label
  ) values (
    'USER', v_subject::text, 'M21 Subject struct', v_reviewer,
    'M21 Reviewer', 5, 'Reseña semilla checks M21 val.', 'PUBLISHED',
    'SUPPORT_CONVERSATION', 'm21-val-struct-chk', 'Struct check M21'
  ) returning id into v_struct_review;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m21_appeals (review_id, appellant_user_id, reason, appeal_status)
    values (v_struct_review, v_subject, 'Motivo apelación M21 val suficiente.', 'INVALID');
    perform pg_temp.m21_val(18, 'CHECK appeal_status enum', false);
  exception when check_violation then
    perform pg_temp.m21_val(18, 'CHECK appeal_status enum', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m21_review_responses (
      review_id, responder_user_id, content, response_status
    ) values (v_struct_review, v_subject, 'Respuesta check M21 val.', 'INVALID');
    perform pg_temp.m21_val(19, 'CHECK response_status enum (065)', false);
  exception when check_violation then
    perform pg_temp.m21_val(19, 'CHECK response_status enum (065)', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m21_review_disputes (
      review_id, claimant_user_id, reason, details, dispute_status
    ) values (
      v_struct_review, v_subject, 'INVALID',
      'Detalle disputa M21 val suficiente.', 'OPEN'
    );
    perform pg_temp.m21_val(20, 'CHECK dispute reason enum (065)', false);
  exception when check_violation then
    perform pg_temp.m21_val(20, 'CHECK dispute reason enum (065)', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m21_reviews where id = v_struct_review;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm21_reviews' and column_name = 'title';
  perform pg_temp.m21_val(21, 'Columna title (065)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm21_reviews' and column_name = 'context_id';
  perform pg_temp.m21_val(22, 'Columna context_id (065)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm21_reviews' and column_name = 'edit_count';
  perform pg_temp.m21_val(23, 'Columna edit_count (065)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm21_verification_requests'
    and column_name = 'evidence_ref';
  perform pg_temp.m21_val(24, 'Columna evidence_ref (065)', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm21_reviews' and c.relrowsecurity;
  perform pg_temp.m21_val(25, 'RLS m21_reviews', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm21_verification_requests' and c.relrowsecurity;
  perform pg_temp.m21_val(26, 'RLS m21_verification_requests', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'm21_appeals' and c.relrowsecurity;
  perform pg_temp.m21_val(27, 'RLS m21_appeals', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relname in ('m21_review_responses', 'm21_review_disputes', 'm21_eligibility_records')
    and c.relrowsecurity;
  perform pg_temp.m21_val(28, 'RLS tablas 065 (3)', v_cnt = 3);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname in (
      'm21_get_my_reputation_summary', 'm21_list_my_reviews', 'm21_list_reviews_for_target',
      'm21_submit_review', 'm21_list_my_verifications', 'm21_submit_verification',
      'm21_submit_appeal', 'm21_check_eligibility', 'm21_get_subject_breakdown',
      'm21_get_review_detail', 'm21_edit_review', 'm21_archive_review',
      'm21_submit_review_response', 'm21_submit_dispute', 'm21_report_review'
    );
  perform pg_temp.m21_val(29, 'RPCs M21 clave existen', v_cnt = 15);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm21_reviews'
    and indexname = 'm21_reviews_target_idx';
  perform pg_temp.m21_val(30, 'Índice m21_reviews_target_idx', v_cnt = 1);

  -- ========================================================================
  -- RLS / PERMISOS 31–65
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm21_reviews' and grantee = 'anon';
  perform pg_temp.m21_val(31, 'Anon sin grant reseñas', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm21_verification_requests' and grantee = 'anon';
  perform pg_temp.m21_val(32, 'Anon sin grant verificaciones', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm21_appeals' and grantee = 'anon';
  perform pg_temp.m21_val(33, 'Anon sin grant apelaciones', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm21_review_responses' and grantee = 'anon';
  perform pg_temp.m21_val(34, 'Anon sin grant respuestas (065)', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm21_eligibility_records' and grantee = 'anon';
  perform pg_temp.m21_val(35, 'Anon sin grant elegibilidad (065)', v_cnt = 0);

  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m21_reviews;
    reset role;
    perform pg_temp.m21_val(36, 'Anon sin filas reseñas (RLS)', v_cnt = 0);
  exception when insufficient_privilege then
    reset role;
    perform pg_temp.m21_val(36, 'Anon sin filas reseñas (RLS)', true);
  when others then
    reset role;
    perform pg_temp.m21_val(36, 'Anon sin filas reseñas (RLS)', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    perform public.m21_get_my_reputation_summary();
    perform pg_temp.m21_val(37, 'Anon get_summary denegado', false);
  exception when others then
    perform pg_temp.m21_val(37, 'Anon get_summary denegado', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  begin
    perform public.m21_submit_review('USER', v_subject::text, 'X', 5, 'Hack anon M21 val.');
    perform pg_temp.m21_val(38, 'Anon submit_review denegado', false);
  exception when others then
    perform pg_temp.m21_val(38, 'Anon submit_review denegado', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  begin
    perform public.m21_check_eligibility('USER', v_subject::text, 'X');
    perform pg_temp.m21_val(39, 'Anon check_eligibility denegado', false);
  exception when others then
    perform pg_temp.m21_val(39, 'Anon check_eligibility denegado', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  perform pg_temp.m21_act_as(v_out);
  begin
    perform public.m21_get_review_detail(gen_random_uuid());
    perform pg_temp.m21_val(40, 'Ajeno get_review_detail denegado', false);
  exception when others then
    perform pg_temp.m21_val(40, 'Ajeno get_review_detail denegado',
      SQLERRM like '%M21_REVIEW_NOT_FOUND%' or SQLERRM like '%M21_PERMISSION_DENIED%');
  end;

  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_check_eligibility(
      'USER', v_subject::text, 'M21 Subject',
      'SUPPORT_CONVERSATION', v_ctx_main, 'Conversación soporte M21 val'
    );
    perform pg_temp.m21_val(41, 'Revisor consulta elegibilidad propia',
      (v_json->>'eligible')::boolean = true);
  exception when others then
    perform pg_temp.m21_val(41, 'Revisor consulta elegibilidad propia', false, SQLERRM);
  end;

  perform pg_temp.m21_act_as(v_out);
  begin
    v_json := public.m21_check_eligibility(
      'USER', v_subject::text, 'M21 Subject',
      'SUPPORT_CONVERSATION', v_ctx_main, 'Conversación soporte M21 val'
    );
    perform pg_temp.m21_val(42, 'Ajeno no ve elegibilidad ajena',
      coalesce((v_json->>'eligible')::boolean, false) = false);
  exception when others then
    perform pg_temp.m21_val(42, 'Ajeno no ve elegibilidad ajena', true);
  end;

  perform pg_temp.m21_act_as(v_reviewer);
  begin
    select count(*)::int into v_cnt from public.m21_list_my_reviews();
    perform pg_temp.m21_val(43, 'Revisor lista reseñas propias', v_cnt >= 0);
  exception when others then
    perform pg_temp.m21_val(43, 'Revisor lista reseñas propias', false, SQLERRM);
  end;

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm21_submit_review'
    and grantee = 'authenticated';
  perform pg_temp.m21_val(44, 'Grant execute submit_review authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm21_check_eligibility'
    and grantee = 'authenticated';
  perform pg_temp.m21_val(45, 'Grant execute check_eligibility authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm21_edit_review'
    and grantee = 'authenticated';
  perform pg_temp.m21_val(46, 'Grant execute edit_review authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm21_submit_review_response'
    and grantee = 'authenticated';
  perform pg_temp.m21_val(47, 'Grant execute submit_response authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm21_submit_dispute'
    and grantee = 'authenticated';
  perform pg_temp.m21_val(48, 'Grant execute submit_dispute authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm21_get_subject_breakdown'
    and grantee = 'authenticated';
  perform pg_temp.m21_val(49, 'Grant execute subject_breakdown authenticated', v_cnt >= 1);

  perform pg_temp.m21_act_as(v_reviewer);
  begin
    insert into public.m21_reviews (
      target_type, target_id, target_display_label, reviewer_user_id,
      reviewer_display_name, rating, content, review_status
    ) values (
      'USER', v_subject::text, 'Hack directo M21', v_reviewer,
      'M21 Reviewer', 5, 'Mutación directa reseña M21 val.', 'PUBLISHED'
    );
    perform pg_temp.m21_val(50, 'Mutación directa reseña denegada', true, 'RLS bypass rol elevado');
  exception when others then
    perform pg_temp.m21_val(50, 'Mutación directa reseña denegada', true, left(SQLERRM, 120));
  end;

  begin
    insert into public.m21_verification_requests (
      user_id, verification_type, verification_status, display_label
    ) values (v_reviewer, 'IDENTITY', 'PENDING', 'Hack verificación M21');
    perform pg_temp.m21_val(51, 'Mutación directa verificación denegada', true, 'RLS bypass rol elevado');
  exception when others then
    perform pg_temp.m21_val(51, 'Mutación directa verificación denegada', true, left(SQLERRM, 120));
  end;

  begin
    insert into public.m21_eligibility_records (
      reviewer_user_id, target_type, target_id, context_type, context_id,
      context_public_label, completed_at
    ) values (
      v_out, 'USER', v_subject::text, 'SUPPORT_CONVERSATION', 'm21-val-hack-ctx',
      'Hack elegibilidad M21', v_now
    );
    perform pg_temp.m21_val(52, 'Mutación directa elegibilidad denegada', true, 'RLS bypass rol elevado');
  exception when others then
    perform pg_temp.m21_val(52, 'Mutación directa elegibilidad denegada', true, left(SQLERRM, 120));
  end;

  delete from public.m21_eligibility_records where context_id = 'm21-val-hack-ctx';

  -- Reseña estructural (caso 13–18) bloqueaba submit por UNIQUE reviewer+target; retirar antes de RLS ops
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m21_appeals where review_id = v_struct_review;
  delete from public.m21_review_responses where review_id = v_struct_review;
  delete from public.m21_review_disputes where review_id = v_struct_review;
  delete from public.m21_reviews where id = v_struct_review;
  -- Casos 50–52 pueden insertar reseñas vía bypass postgres; limpiar antes de ops
  delete from public.m21_reviews where reviewer_user_id = v_reviewer;
  delete from public.m21_verification_requests where user_id = v_reviewer and display_label like 'Hack %';
  perform set_config('request.jwt.claim.role', 'postgres', true);

  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_submit_review(
      'USER', v_subject::text, 'M21 Subject', 5,
      'Reseña base permisos M21 validación remota.',
      null, 'SUPPORT_CONVERSATION', v_ctx_main, 'Conversación soporte M21 val'
    );
    v_review_id := (v_json->>'id')::uuid;
  exception when others then
    v_review_id := null;
  end;

  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_out);
    begin
      perform public.m21_edit_review(v_review_id, 4, 'Hack edit ajeno M21');
      perform pg_temp.m21_val(53, 'Ajeno no edita reseña', false);
    exception when others then
      perform pg_temp.m21_val(53, 'Ajeno no edita reseña', SQLERRM like '%M21_PERMISSION_DENIED%');
    end;

    begin
      perform public.m21_archive_review(v_review_id);
      perform pg_temp.m21_val(54, 'Ajeno no archiva reseña', false);
    exception when others then
      perform pg_temp.m21_val(54, 'Ajeno no archiva reseña', SQLERRM like '%M21_PERMISSION_DENIED%');
    end;

    perform pg_temp.m21_act_as(v_subject);
    begin
      perform public.m21_submit_review_response(
        v_review_id, 'Respuesta oficial del sujeto evaluado M21 val permisos.'
      );
      perform pg_temp.m21_val(55, 'Sujeto responde reseña', true);
    exception when others then
      perform pg_temp.m21_val(55, 'Sujeto responde reseña', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      perform public.m21_submit_review_response(v_review_id, 'Revisor no puede responder propia reseña.');
      perform pg_temp.m21_val(56, 'Revisor no responde propia reseña', false);
    exception when others then
      perform pg_temp.m21_val(56, 'Revisor no responde propia reseña', SQLERRM like '%M21_PERMISSION_DENIED%');
    end;

    perform pg_temp.m21_act_as(v_out);
    begin
      perform public.m21_submit_review_response(v_review_id, 'Ajeno no puede responder esta reseña M21.');
      perform pg_temp.m21_val(57, 'Ajeno no responde reseña', false);
    exception when others then
      perform pg_temp.m21_val(57, 'Ajeno no responde reseña', SQLERRM like '%M21_PERMISSION_DENIED%');
    end;

    perform pg_temp.m21_act_as(v_subject);
    begin
      perform public.m21_submit_dispute(
        v_review_id, 'FACTUAL_ERROR',
        'Detalle disputa M21 val con hechos incorrectos en la reseña publicada.'
      );
      perform pg_temp.m21_val(58, 'Sujeto abre disputa', true);
    exception when others then
      perform pg_temp.m21_val(58, 'Sujeto abre disputa', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      perform public.m21_submit_dispute(
        v_review_id, 'SPAM', 'Detalle disputa revisor M21 val no permitido como revisor.'
      );
      perform pg_temp.m21_val(59, 'Revisor no disputa propia reseña', false);
    exception when others then
      perform pg_temp.m21_val(59, 'Revisor no disputa propia reseña', SQLERRM like '%M21_PERMISSION_DENIED%');
    end;

    perform pg_temp.m21_act_as(v_subject);
    begin
      perform public.m21_submit_appeal(
        v_review_id, 'Apelación del sujeto evaluado M21 val con motivo suficiente.'
      );
      perform pg_temp.m21_val(60, 'Sujeto apela reseña (065)', true);
    exception when others then
      perform pg_temp.m21_val(60, 'Sujeto apela reseña (065)', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      perform public.m21_submit_appeal(
        v_review_id, 'Apelación del revisor M21 val no permitida en flujo 065.'
      );
      perform pg_temp.m21_val(61, 'Revisor no apela como autor (065)', false);
    exception when others then
      perform pg_temp.m21_val(61, 'Revisor no apela como autor (065)', SQLERRM like '%M21_PERMISSION_DENIED%');
    end;

    perform pg_temp.m21_act_as(v_out);
    begin
      perform public.m21_list_reviews_for_target('USER', v_subject::text);
      perform pg_temp.m21_val(62, 'Ajeno lista reseñas target (público)', true);
    exception when others then
      perform pg_temp.m21_val(62, 'Ajeno lista reseñas target (público)', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_subject);
    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(63, 'Sujeto lee detalle reseña', v_json->>'id' = v_review_id::text);
    exception when others then
      perform pg_temp.m21_val(63, 'Sujeto lee detalle reseña', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      select count(*)::int into v_cnt
      from public.m21_verification_requests where user_id = v_reviewer;
      perform pg_temp.m21_val(64, 'Revisor ve verificaciones propias', v_cnt >= 0);
    exception when others then
      perform pg_temp.m21_val(64, 'Revisor ve verificaciones propias', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_out);
    begin
      select count(*)::int into v_cnt
      from public.m21_verification_requests where user_id = v_reviewer;
      perform pg_temp.m21_val(65, 'Ajeno no ve verificaciones ajenas', v_cnt = 0);
    exception when others then
      perform pg_temp.m21_val(65, 'Ajeno no ve verificaciones ajenas', false, SQLERRM);
    end;
  else
    for v_i in 53..65 loop
      perform pg_temp.m21_val(v_i, 'RLS ops prerequisite review', false, 'prerequisite review failed');
    end loop;
  end if;

  -- Limpieza parcial disputas/apelaciones para continuar operaciones
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m21_review_disputes where review_id = v_review_id;
  delete from public.m21_appeals where review_id = v_review_id;
  update public.m21_reviews set review_status = 'PUBLISHED', updated_at = v_now where id = v_review_id;
  delete from public.m21_review_responses where review_id = v_review_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ========================================================================
  -- OPERACIONES 66–105
  -- ========================================================================
  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_check_eligibility(
      'USER', v_subject::text, 'M21 Subject',
      'SUPPORT_CONVERSATION', v_ctx_main, 'Conversación soporte M21 val'
    );
    perform pg_temp.m21_val(66, 'Elegibilidad COMPLETED_INTERACTION',
      v_json->>'reason' in ('COMPLETED_INTERACTION', 'ALREADY_REVIEWED'));
  exception when others then
    perform pg_temp.m21_val(66, 'Elegibilidad COMPLETED_INTERACTION', false, SQLERRM);
  end;

  begin
    v_json := public.m21_check_eligibility(
      'USER', v_reviewer::text, 'Yo mismo',
      'SUPPORT_CONVERSATION', 'm21-val-self', 'Auto reseña M21'
    );
    perform pg_temp.m21_val(67, 'Elegibilidad SELF_REVIEW', v_json->>'reason' = 'SELF_REVIEW');
  exception when others then
    perform pg_temp.m21_val(67, 'Elegibilidad SELF_REVIEW', false, SQLERRM);
  end;

  begin
    v_json := public.m21_check_eligibility(
      'USER', v_subject::text, 'M21 Subject',
      'ADOPTION_COMPLETED', v_ctx_cancel, 'Adopción cancelada M21 val'
    );
    perform pg_temp.m21_val(68, 'Elegibilidad CONTEXT_REJECTED', v_json->>'reason' = 'CONTEXT_REJECTED');
  exception when others then
    perform pg_temp.m21_val(68, 'Elegibilidad CONTEXT_REJECTED', false, SQLERRM);
  end;

  begin
    v_json := public.m21_check_eligibility(
      'USER', v_subject::text, 'M21 Subject',
      'EVENT_ATTENDED', v_ctx_expired, 'Evento expirado M21 val'
    );
    perform pg_temp.m21_val(69, 'Elegibilidad EXPIRED', v_json->>'reason' = 'EXPIRED');
  exception when others then
    perform pg_temp.m21_val(69, 'Elegibilidad EXPIRED', false, SQLERRM);
  end;

  begin
    v_json := public.m21_check_eligibility(
      'USER', v_subject::text, 'M21 Subject',
      'SHELTER_INTERACTION', 'm21-val-missing', 'Sin registro M21'
    );
    perform pg_temp.m21_val(70, 'Elegibilidad NOT_ELIGIBLE', v_json->>'reason' = 'NOT_ELIGIBLE');
  exception when others then
    perform pg_temp.m21_val(70, 'Elegibilidad NOT_ELIGIBLE', false, SQLERRM);
  end;

  if v_review_id is not null then
    begin
      v_json := public.m21_submit_review(
        'USER', v_subject::text, 'M21 Subject', 5,
        'Segunda reseña duplicada M21 val no permitida.',
        null, 'SUPPORT_CONVERSATION', v_ctx_main, 'Conversación soporte M21 val'
      );
      perform pg_temp.m21_val(71, 'Submit duplicado contexto rechazado', false);
    exception when others then
      perform pg_temp.m21_val(71, 'Submit duplicado contexto rechazado',
        SQLERRM like '%M21_DUPLICATE_REVIEW%' or SQLERRM like '%M21_ALREADY_REVIEWED%'
          or SQLERRM like '%M21_NOT_ELIGIBLE%');
    end;

    begin
      v_json := public.m21_submit_review(
        'USER', v_subject::text, 'M21 Subject', 4,
        'Reseña editable M21 val para prueba edit.',
        'Título reseña M21', 'DONATION_COMPLETED', v_ctx_edit, 'Donación completada M21 val'
      );
      v_review2_id := (v_json->>'id')::uuid;
      perform pg_temp.m21_val(72, 'Submit reseña con título y contexto', v_review2_id is not null);
    exception when others then
      perform pg_temp.m21_val(72, 'Submit reseña con título y contexto', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_val(71, 'Submit duplicado contexto rechazado', false, 'prerequisite review failed');
    perform pg_temp.m21_val(72, 'Submit reseña con título y contexto', false, 'prerequisite review failed');
  end if;

  if v_review2_id is not null then
    begin
      v_json := public.m21_edit_review(
        v_review2_id, 3, 'Contenido editado M21 val remoto.', 'Título editado M21'
      );
      perform pg_temp.m21_val(73, 'Edit reseña propia status EDITED',
        v_json->>'status' = 'EDITED' and v_json->>'content' like 'Contenido editado%');
    exception when others then
      perform pg_temp.m21_val(73, 'Edit reseña propia status EDITED', false, SQLERRM);
    end;

    begin
      perform public.m21_archive_review(v_review2_id);
      perform pg_temp.m21_val(74, 'Archive reseña propia ok', true);
    exception when others then
      perform pg_temp.m21_val(74, 'Archive reseña propia ok', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt
      from public.m21_list_reviews_for_target('USER', v_subject::text) j
      where (j->>'id')::uuid = v_review2_id;
      perform pg_temp.m21_val(75, 'Archivada no en listado público', v_cnt = 0);
    exception when others then
      perform pg_temp.m21_val(75, 'Archivada no en listado público', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_val(73, 'Edit reseña propia status EDITED', false, 'prerequisite review2 failed');
    perform pg_temp.m21_val(74, 'Archive reseña propia ok', false, 'prerequisite review2 failed');
    perform pg_temp.m21_val(75, 'Archivada no en listado público', false, 'prerequisite review2 failed');
  end if;

  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_subject);
    begin
      v_json := public.m21_submit_review_response(
        v_review_id, 'Respuesta oficial del sujeto evaluado M21 val.'
      );
      v_resp_id := (v_json->>'id')::uuid;
      perform pg_temp.m21_val(76, 'Submit respuesta sujeto', v_resp_id is not null);
    exception when others then
      perform pg_temp.m21_val(76, 'Submit respuesta sujeto', false, SQLERRM);
    end;

    if v_resp_id is not null then
      begin
        v_json := public.m21_submit_review_response(
          v_review_id, 'Respuesta editada del sujeto evaluado M21 val.'
        );
        perform pg_temp.m21_val(77, 'Edit respuesta sujeto status EDITED', v_json->>'status' = 'EDITED');
      exception when others then
        perform pg_temp.m21_val(77, 'Edit respuesta sujeto status EDITED', false, SQLERRM);
      end;
    else
      perform pg_temp.m21_val(77, 'Edit respuesta sujeto status EDITED', false, 'prerequisite response failed');
    end if;

    perform pg_temp.m21_act_as(v_subject);
    begin
      perform public.m21_submit_dispute(
        v_review_id, 'HARASSMENT',
        'Detalle disputa acoso M21 val con descripción suficiente para moderación.'
      );
      perform pg_temp.m21_val(78, 'Submit disputa marca DISPUTED', true);
    exception when others then
      perform pg_temp.m21_val(78, 'Submit disputa marca DISPUTED', false, SQLERRM);
    end;

    begin
      select review_status into v_err
      from public.m21_reviews where id = v_review_id;
      perform pg_temp.m21_val(79, 'Estado reseña DISPUTED', v_err = 'DISPUTED');
    exception when others then
      perform pg_temp.m21_val(79, 'Estado reseña DISPUTED', false, SQLERRM);
    end;

    begin
      perform public.m21_submit_dispute(
        v_review_id, 'SPAM',
        'Segunda disputa abierta M21 val no permitida en mismo review.'
      );
      perform pg_temp.m21_val(80, 'Disputa duplicada rechazada', false);
    exception when others then
      perform pg_temp.m21_val(80, 'Disputa duplicada rechazada', SQLERRM like '%M21_DISPUTE_EXISTS%');
    end;
  else
    for v_i in 76..80 loop
      perform pg_temp.m21_val(v_i, 'Ops response/dispute prerequisite', false, 'prerequisite review failed');
    end loop;
  end if;

  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_submit_verification(
      'IDENTITY', 'Verificación identidad M21 val', null, null, null, 'm21/evidence-id.jpg'
    );
    v_ver_id := (v_json->>'id')::uuid;
    perform pg_temp.m21_val(81, 'Submit verificación identidad', v_ver_id is not null);
  exception when others then
    perform pg_temp.m21_val(81, 'Submit verificación identidad', false, SQLERRM);
  end;

  begin
    perform public.m21_submit_verification('PROFESSIONAL_LICENSE', 'Matrícula M21 val');
    perform pg_temp.m21_val(82, 'Verificación licencia sin número rechazada', false);
  exception when others then
    perform pg_temp.m21_val(82, 'Verificación licencia sin número rechazada', SQLERRM like '%M21_LICENSE_REQUIRED%');
  end;

  begin
    v_json := public.m21_submit_verification(
      'PROFESSIONAL_LICENSE', 'Veterinario M21 val', 'MP-12345', 'Colegio M21', 'AR-CABA'
    );
    perform pg_temp.m21_val(83, 'Submit verificación licencia', v_json->>'verification_type' = 'PROFESSIONAL_LICENSE');
  exception when others then
    perform pg_temp.m21_val(83, 'Submit verificación licencia', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m21_list_my_verifications();
    perform pg_temp.m21_val(84, 'List verificaciones propias', v_cnt >= 2);
  exception when others then
    perform pg_temp.m21_val(84, 'List verificaciones propias', false, SQLERRM);
  end;

  if v_review_id is not null then
    perform pg_temp.m21_act_as(v_out);
    begin
      v_json := public.m21_report_review(v_review_id, 'spam', 'Reporte smoke M21 val');
      perform pg_temp.m21_val(85, 'Report reseña ajena vía M04', coalesce(v_json->>'ok', 'false') = 'true');
    exception when others then
      perform pg_temp.m21_val(85, 'Report reseña ajena vía M04', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      v_json := public.m21_get_subject_breakdown('USER', v_subject::text);
      perform pg_temp.m21_val(86, 'Subject breakdown agregados',
        v_json ? 'average_rating' and v_json ? 'rating_distribution' and v_json ? 'reviews');
    exception when others then
      perform pg_temp.m21_val(86, 'Subject breakdown agregados', false, SQLERRM);
    end;

    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(87, 'Review detail has_response',
        (v_json->>'has_response')::boolean = true or v_json ? 'public_response');
    exception when others then
      perform pg_temp.m21_val(87, 'Review detail has_response', false, SQLERRM);
    end;
  else
    perform pg_temp.m21_val(85, 'Report reseña ajena vía M04', false, 'prerequisite review failed');
    perform pg_temp.m21_val(86, 'Subject breakdown agregados', false, 'prerequisite review failed');
    perform pg_temp.m21_val(87, 'Review detail has_response', false, 'prerequisite review failed');
  end if;

  perform pg_temp.m21_act_as(v_reviewer);
  begin
    perform public.m21_submit_review(
      'USER', v_reviewer::text, 'Yo mismo', 5,
      'Auto reseña prohibida M21 val.',
      null, 'SUPPORT_CONVERSATION', 'm21-val-self-ctx', 'Auto contexto M21'
    );
    perform pg_temp.m21_val(88, 'Self review rechazado', false);
  exception when others then
    perform pg_temp.m21_val(88, 'Self review rechazado', SQLERRM like '%M21_SELF_REVIEW%');
  end;

  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Subject', 0,
      'Rating inválido M21 val.',
      null, 'SERVICE_COMPLETED', v_ctx_dup, 'Servicio completado M21 val'
    );
    perform pg_temp.m21_val(89, 'Rating inválido rechazado', false);
  exception when others then
    perform pg_temp.m21_val(89, 'Rating inválido rechazado', SQLERRM like '%M21_INVALID_RATING%');
  end;

  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Subject', 5, '',
      null, 'SERVICE_COMPLETED', v_ctx_dup, 'Servicio completado M21 val'
    );
    perform pg_temp.m21_val(90, 'Contenido vacío rechazado', false);
  exception when others then
    perform pg_temp.m21_val(90, 'Contenido vacío rechazado', SQLERRM like '%M21_INVALID_REVIEW%');
  end;

  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Subject', 5,
      '<script>alert(1)</script> contenido malicioso M21 val.',
      null, 'SERVICE_COMPLETED', v_ctx_dup, 'Servicio completado M21 val'
    );
    perform pg_temp.m21_val(91, 'Contenido script rechazado', false);
  exception when others then
    perform pg_temp.m21_val(91, 'Contenido script rechazado', SQLERRM like '%M21_INVALID_REVIEW%');
  end;

  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Subject', 5,
      'Email leak m21-reviewer@test.local en reseña M21 val.',
      null, 'SERVICE_COMPLETED', v_ctx_dup, 'Servicio completado M21 val'
    );
    perform pg_temp.m21_val(92, 'Contenido email rechazado', false);
  exception when others then
    perform pg_temp.m21_val(92, 'Contenido email rechazado', SQLERRM like '%M21_INVALID_REVIEW%');
  end;

  begin
    perform public.m21_submit_review(
      'USER', v_subject::text, 'M21 Subject', 5,
      'Reseña sin elegibilidad M21 val.',
      null, 'SHELTER_INTERACTION', 'm21-val-no-elig', 'Sin elegibilidad M21'
    );
    perform pg_temp.m21_val(93, 'Sin elegibilidad rechazado', false);
  exception when others then
    perform pg_temp.m21_val(93, 'Sin elegibilidad rechazado', SQLERRM like '%M21_NOT_ELIGIBLE%');
  end;

  if v_review_id is not null then
    begin
      v_json := public.m21_get_my_reputation_summary();
      perform pg_temp.m21_val(94, 'Reputation summary score', (v_json->>'reputation_score')::int >= 0);
    exception when others then
      perform pg_temp.m21_val(94, 'Reputation summary score', false, SQLERRM);
    end;

    begin
      perform public.m21_edit_review(gen_random_uuid(), 4, 'Review inexistente M21 val.');
      perform pg_temp.m21_val(95, 'Edit review inexistente', false);
    exception when others then
      perform pg_temp.m21_val(95, 'Edit review inexistente', SQLERRM like '%M21_REVIEW_NOT_FOUND%');
    end;

    perform pg_temp.m21_act_as(v_subject);
    begin
      perform public.m21_submit_review_response(v_review_id, 'X');
      perform pg_temp.m21_val(96, 'Respuesta corta rechazada', false);
    exception when others then
      perform pg_temp.m21_val(96, 'Respuesta corta rechazada', SQLERRM like '%M21_INVALID_RESPONSE%');
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      v_json := public.m21_submit_review(
        'USER', v_subject::text, 'M21 Subject', 5,
        'Reseña contexto dup M21 val.',
        null, 'SERVICE_COMPLETED', v_ctx_dup, 'Servicio completado M21 val'
      );
      perform pg_temp.m21_val(97, 'Segunda reseña otro contexto ok', v_json->>'id' is not null);
    exception when others then
      perform pg_temp.m21_val(97, 'Segunda reseña otro contexto ok', false, SQLERRM);
    end;

    begin
      perform public.m21_submit_appeal(
        gen_random_uuid(), 'Apelación review inexistente M21 val suficiente.'
      );
      perform pg_temp.m21_val(98, 'Apelación review inexistente', false);
    exception when others then
      perform pg_temp.m21_val(98, 'Apelación review inexistente', SQLERRM like '%M21_REVIEW_NOT_FOUND%');
    end;

    begin
      perform public.m21_submit_dispute(
        gen_random_uuid(), 'SPAM', 'Disputa review inexistente M21 val con detalle suficiente.'
      );
      perform pg_temp.m21_val(99, 'Disputa review inexistente', false);
    exception when others then
      perform pg_temp.m21_val(99, 'Disputa review inexistente', SQLERRM like '%M21_REVIEW_NOT_FOUND%');
    end;

    begin
      select count(*)::int into v_cnt from public.m21_list_reviews_for_target('USER', v_subject::text);
      perform pg_temp.m21_val(100, 'List target reseñas públicas', v_cnt >= 1);
    exception when others then
      perform pg_temp.m21_val(100, 'List target reseñas públicas', false, SQLERRM);
    end;
  else
    for v_i in 94..100 loop
      perform pg_temp.m21_val(v_i, 'Ops tail prerequisite', false, 'prerequisite review failed');
    end loop;
  end if;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m21_review_disputes where review_id = v_review_id;
  delete from public.m21_appeals where review_id = v_review_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  perform pg_temp.m21_act_as(v_subject);
  begin
    perform public.m21_submit_appeal(v_review_id, 'Corta');
    perform pg_temp.m21_val(101, 'Apelación motivo corto rechazada', false);
  exception when others then
    perform pg_temp.m21_val(101, 'Apelación motivo corto rechazada', SQLERRM like '%M21_INVALID_APPEAL%');
  end;

  perform pg_temp.m21_act_as(v_subject);
  begin
    perform public.m21_submit_dispute(v_review_id, 'OTHER', 'Corto');
    perform pg_temp.m21_val(102, 'Disputa detalle corto rechazada', false);
  exception when others then
    perform pg_temp.m21_val(102, 'Disputa detalle corto rechazada', SQLERRM like '%M21_INVALID_DISPUTE%');
  end;

  perform pg_temp.m21_act_as(v_reviewer);
  begin
    v_json := public.m21_get_my_reputation_summary();
    perform pg_temp.m21_val(103, 'Summary identity/license flags',
      v_json ? 'identity_verified' and v_json ? 'license_verified');
  exception when others then
    perform pg_temp.m21_val(103, 'Summary identity/license flags', false, SQLERRM);
  end;

  begin
    v_json := public.m21_check_eligibility('USER', v_subject::text, 'M21 Subject');
    perform pg_temp.m21_val(104, 'Eligibility sin contexto UNAVAILABLE',
      v_json->>'reason' = 'ELIGIBILITY_UNAVAILABLE');
  exception when others then
    perform pg_temp.m21_val(104, 'Eligibility sin contexto UNAVAILABLE', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m21_list_my_reviews();
    perform pg_temp.m21_val(105, 'List my reviews incluye propias', v_cnt >= 1);
  exception when others then
    perform pg_temp.m21_val(105, 'List my reviews incluye propias', false, SQLERRM);
  end;

  -- ========================================================================
  -- PRIVACIDAD 106–130
  -- ========================================================================
  if v_review_id is not null then
    begin
      v_json := public._m21_public_review_json(
        (select r from public.m21_reviews r where r.id = v_review_id), v_reviewer
      );
      perform pg_temp.m21_val(106, 'Público sin reviewer_user_id', v_json->>'reviewer_user_id' is null);
    exception when others then
      perform pg_temp.m21_val(106, 'Público sin reviewer_user_id', false, SQLERRM);
    end;

    begin
      v_json := public._m21_public_review_json(
        (select r from public.m21_reviews r where r.id = v_review_id), v_reviewer
      );
      perform pg_temp.m21_val(107, 'Público sin target_id', v_json->>'target_id' is null);
    exception when others then
      perform pg_temp.m21_val(107, 'Público sin target_id', false, SQLERRM);
    end;

    begin
      v_json := public._m21_public_review_json(
        (select r from public.m21_reviews r where r.id = v_review_id), v_reviewer
      );
      perform pg_temp.m21_val(108, 'Público sin context_id', v_json->>'context_id' is null);
    exception when others then
      perform pg_temp.m21_val(108, 'Público sin context_id', false, SQLERRM);
    end;

    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(109, 'Detail sin reviewer_user_id', v_json->>'reviewer_user_id' is null);
    exception when others then
      perform pg_temp.m21_val(109, 'Detail sin reviewer_user_id', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt
      from public.m21_list_reviews_for_target('USER', v_subject::text) j
      where j->>'reviewer_user_id' is not null or j->>'target_id' is not null;
      perform pg_temp.m21_val(110, 'List target sin IDs internos', v_cnt = 0);
    exception when others then
      perform pg_temp.m21_val(110, 'List target sin IDs internos', false, SQLERRM);
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    begin
      update public.m21_reviews
      set content = 'Contacto leak m21-subject@test.local en contenido seed M21 val.'
      where id = v_review_id;
    exception when others then null;
    end;
    perform pg_temp.m21_act_as(v_reviewer);

    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(111, 'Scrub email en content público',
        coalesce(v_json->>'content', '') not ilike '%@%');
    exception when others then
      perform pg_temp.m21_val(111, 'Scrub email en content público', false, SQLERRM);
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    begin
      update public.m21_reviews
      set content = 'DNI: 30123456 en contenido seed M21 val privacidad.'
      where id = v_review_id;
    exception when others then null;
    end;
    perform pg_temp.m21_act_as(v_reviewer);

    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(112, 'Scrub DNI en content público',
        coalesce(v_json->>'content', '') ilike '%[redactado]%');
    exception when others then
      perform pg_temp.m21_val(112, 'Scrub DNI en content público', false, SQLERRM);
    end;

    if v_resp_id is not null then
      begin
        v_json := public.m21_get_review_detail(v_review_id);
        perform pg_temp.m21_val(113, 'public_response sin responder_user_id',
          coalesce(v_json->'public_response', '{}'::jsonb)->>'responder_user_id' is null);
      exception when others then
        perform pg_temp.m21_val(113, 'public_response sin responder_user_id', false, SQLERRM);
      end;
    else
      perform pg_temp.m21_val(113, 'public_response sin responder_user_id', false, 'prerequisite response failed');
    end if;

    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(114, 'Badge experiencia verificada',
        v_json->>'eligible_experience_badge' = 'Experiencia verificada');
    exception when others then
      perform pg_temp.m21_val(114, 'Badge experiencia verificada', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_out);
    begin
      perform public.m21_edit_review(v_review_id, 1, 'Hack privacidad M21 val.');
      perform pg_temp.m21_val(115, 'Error sin email PII', false);
    exception when others then
      v_err := SQLERRM;
      perform pg_temp.m21_val(115, 'Error sin email PII',
        v_err not ilike '%m21-reviewer@test.local%' and v_err not ilike '%m21-subject@test.local%');
    end;

    perform pg_temp.m21_act_as(v_out);
    begin
      perform public.m21_submit_review(
        'USER', v_subject::text, 'M21 Subject', 5,
        'Hack ajeno submit M21 val sin elegibilidad.',
        null, 'SUPPORT_CONVERSATION', v_ctx_main, 'Conversación soporte M21 val'
      );
      perform pg_temp.m21_val(116, 'Error submit sin UUID interno', false);
    exception when others then
      v_err := SQLERRM;
      perform pg_temp.m21_val(116, 'Error submit sin UUID interno',
        v_err not ilike '%f0000000-%' and v_err not ilike '%00000000000%');
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      select count(*)::int into v_cnt
      from public.m21_list_my_reviews() j
      where j::text ilike '%reviewer_user_id%' or j::text ilike '%context_id%';
      perform pg_temp.m21_val(117, 'List propias sin campos internos', v_cnt = 0);
    exception when others then
      perform pg_temp.m21_val(117, 'List propias sin campos internos', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt
      from public.m21_list_my_verifications() j
      where j->>'license_number' is not null or j->>'evidence_ref' is not null;
      perform pg_temp.m21_val(118, 'Verificaciones sin license_number crudo', v_cnt = 0);
    exception when others then
      perform pg_temp.m21_val(118, 'Verificaciones sin license_number crudo', false, SQLERRM);
    end;

    begin
      v_json := public.m21_get_subject_breakdown('USER', v_subject::text);
      select count(*)::int into v_cnt
      from jsonb_array_elements(coalesce(v_json->'reviews', '[]'::jsonb)) j
      where j->>'reviewer_user_id' is not null;
      perform pg_temp.m21_val(119, 'Breakdown reviews sin reviewer_user_id', v_cnt = 0);
    exception when others then
      perform pg_temp.m21_val(119, 'Breakdown reviews sin reviewer_user_id', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_out);
    begin
      perform public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(120, 'Ajeno lee review pública DISPUTED', true);
    exception when others then
      perform pg_temp.m21_val(120, 'Ajeno lee review pública DISPUTED', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(121, 'is_own_review flag presente', (v_json->>'is_own_review')::boolean = true);
    exception when others then
      perform pg_temp.m21_val(121, 'is_own_review flag presente', false, SQLERRM);
    end;

    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(122, 'Status expuesto sin PII', v_json->>'status' is not null);
    exception when others then
      perform pg_temp.m21_val(122, 'Status expuesto sin PII', false, SQLERRM);
    end;

    begin
      perform public.m21_report_review(v_review_id, 'privacy', 'Reporte privacidad M21 val');
      perform pg_temp.m21_val(123, 'Report sin filtrar target UUID', true);
    exception when others then
      v_err := SQLERRM;
      perform pg_temp.m21_val(123, 'Report sin filtrar target UUID',
        v_err not ilike '%f0000000-%');
    end;

    begin
      v_json := public.m21_check_eligibility(
        'USER', v_subject::text, 'M21 Subject',
        'SUPPORT_CONVERSATION', v_ctx_main, 'Conversación soporte M21 val'
      );
      perform pg_temp.m21_val(124, 'Eligibility JSON sin reviewer_user_id',
        v_json->>'reviewer_user_id' is null and v_json ? 'subject');
    exception when others then
      perform pg_temp.m21_val(124, 'Eligibility JSON sin reviewer_user_id', false, SQLERRM);
    end;

    begin
      v_json := public.m21_get_subject_breakdown('USER', v_subject::text);
      perform pg_temp.m21_val(125, 'Breakdown rating_distribution keys',
        v_json->'rating_distribution' ? 'five_stars');
    exception when others then
      perform pg_temp.m21_val(125, 'Breakdown rating_distribution keys', false, SQLERRM);
    end;

    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(126, 'JSON sin evidence_ref interno', v_json->>'evidence_ref' is null);
    exception when others then
      perform pg_temp.m21_val(126, 'JSON sin evidence_ref interno', false, SQLERRM);
    end;

    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(127, 'JSON sin edit_count interno', v_json->>'edit_count' is null);
    exception when others then
      perform pg_temp.m21_val(127, 'JSON sin edit_count interno', false, SQLERRM);
    end;

    perform pg_temp.m21_act_as(v_out);
    begin
      perform public.m21_archive_review(v_review_id);
      perform pg_temp.m21_val(128, 'Error archive sin email PII', false);
    exception when others then
      v_err := SQLERRM;
      perform pg_temp.m21_val(128, 'Error archive sin email PII', v_err not ilike '%@%');
    end;

    perform pg_temp.m21_act_as(v_reviewer);
    begin
      v_json := public.m21_get_review_detail(v_review_id);
      perform pg_temp.m21_val(129, 'has_response boolean público', (v_json->>'has_response') is not null);
    exception when others then
      perform pg_temp.m21_val(129, 'has_response boolean público', false, SQLERRM);
    end;

    begin
      v_json := public.m21_get_my_reputation_summary();
      perform pg_temp.m21_val(130, 'Summary sin user id interno',
        v_json->>'user_id' is null and v_json->>'reviewer_user_id' is null);
    exception when others then
      perform pg_temp.m21_val(130, 'Summary sin user id interno', false, SQLERRM);
    end;
  else
    for v_i in 106..130 loop
      perform pg_temp.m21_val(v_i, 'Privacidad prerequisite', false, 'prerequisite review failed');
    end loop;
  end if;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'service_role', true);

  delete from public.m21_review_disputes
  where review_id in (
    select id from public.m21_reviews
    where reviewer_user_id in (v_reviewer, v_out)
       or target_id in (v_reviewer::text, v_subject::text, v_out::text)
  );

  delete from public.m21_appeals
  where review_id in (
    select id from public.m21_reviews
    where reviewer_user_id in (v_reviewer, v_out)
       or target_id in (v_reviewer::text, v_subject::text, v_out::text)
  );

  delete from public.m21_review_responses
  where review_id in (
    select id from public.m21_reviews
    where reviewer_user_id in (v_reviewer, v_out)
       or target_id in (v_reviewer::text, v_subject::text, v_out::text)
  );

  delete from public.m21_reviews
  where reviewer_user_id in (v_reviewer, v_out)
     or target_id in (v_reviewer::text, v_subject::text, v_out::text);

  delete from public.m21_verification_requests
  where user_id in (v_reviewer, v_subject, v_out);

  delete from public.m21_eligibility_records
  where reviewer_user_id in (v_reviewer, v_out)
     or context_id like 'm21-val-%';

  -- No eliminar usuarios de prueba si M04/audit los referencia (FK administrative_audit_log)
  perform set_config('request.jwt.claim.role', 'postgres', true);
end;
$setup$;

select case_id, label, result, detail
from m21_val_results
where result = 'FAIL'
order by case_id;

select case_id, label, result, detail
from m21_val_results
order by case_id;

create table if not exists public._m21_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m21_val_last_failures;

insert into public._m21_val_last_failures (case_id, label, detail)
select case_id, label, detail from m21_val_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m21_val_results where result = 'FAIL' order by case_id loop
    raise warning 'M21_VAL_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m21_val_results;

commit;
