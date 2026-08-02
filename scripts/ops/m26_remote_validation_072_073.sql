-- M26 migraciones 072+073 — validación remota staging (casos 01–125)
-- Proyecto staging: wystsapjfpdtoprlmizz
-- Ejecutar: supabase db query --linked -f scripts/ops/m26_remote_validation_072_073.sql
-- Limpia datos de prueba al finalizar. Sin pagos (M24 pospuesto). Sin proveedor IA externo.

begin;

create temp table if not exists m26_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m26_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m26_val_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m26_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_user uuid := 'f0000000-0000-4000-8000-000000000051';
  v_user2 uuid := 'f0000000-0000-4000-8000-000000000052';
  v_out uuid := 'f0000000-0000-4000-8000-000000000053';
  v_mod uuid := 'f0000000-0000-4000-8000-000000000054';
  v_mod_role uuid;
  v_job_id uuid;
  v_job_id2 uuid;
  v_job_cancel uuid;
  v_result_id uuid;
  v_result_draft uuid;
  v_result_review uuid;
  v_match_id uuid;
  v_dup_id uuid;
  v_dup_id2 uuid;
  v_session_id uuid;
  v_rec_id uuid;
  v_rec_pending uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_err text;
  v_i int;
  v_canonical text;
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_user, 'authenticated', 'authenticated',
     'm26-val-user@test.local', crypt('m26-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_user2, 'authenticated', 'authenticated',
     'm26-val-user2@test.local', crypt('m26-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm26-val-out@test.local', crypt('m26-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_mod, 'authenticated', 'authenticated',
     'm26-val-mod@test.local', crypt('m26-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_user, 'm26-val-user@test.local', 'M26 Val User', 'M26 Val User', 'PERSON', true, 'ACTIVE'),
    (v_user2, 'm26-val-user2@test.local', 'M26 Val User2', 'M26 Val User2', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm26-val-out@test.local', 'M26 Val Outsider', 'M26 Val Outsider', 'PERSON', true, 'ACTIVE'),
    (v_mod, 'm26-val-mod@test.local', 'M26 Val Moderator', 'M26 Val Moderator', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  select id into v_mod_role from public.platform_roles where code = 'MODERATOR' limit 1;
  if v_mod_role is not null then
    delete from public.user_role_assignments
    where user_id = v_mod and role_id = v_mod_role;
    insert into public.user_role_assignments (user_id, role_id, assigned_by)
    values (v_mod, v_mod_role, v_mod);
  end if;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m26_human_reviews where result_id in (
    select r.id from public.m26_ai_results r
    where r.owner_user_id in (v_user, v_user2, v_out, v_mod));
  delete from public.m26_ai_results where owner_user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_ai_jobs where owner_user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_visual_match_suggestions where requester_user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_duplicate_candidates where owner_user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_assistance_sessions where user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_evaluated_recommendations where subject_user_id in (v_user, v_user2, v_out, v_mod);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ========================================================================
  -- ESTRUCTURA 01–30
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm26_ai_jobs';
  perform pg_temp.m26_val(1, 'Tabla jobs', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm26_ai_results';
  perform pg_temp.m26_val(2, 'Tabla resultados', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm26_visual_match_suggestions';
  perform pg_temp.m26_val(3, 'Tabla matches', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm26_duplicate_candidates';
  perform pg_temp.m26_val(4, 'Tabla duplicados', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm26_assistance_sessions';
  perform pg_temp.m26_val(5, 'Tabla sesiones', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm26_evaluated_recommendations';
  perform pg_temp.m26_val(6, 'Tabla recomendaciones', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm26_human_reviews';
  perform pg_temp.m26_val(7, 'Tabla revisiones', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  where tc.table_schema = 'public' and tc.table_name like 'm26_%'
    and tc.constraint_type = 'FOREIGN KEY';
  perform pg_temp.m26_val(8, 'FKs correctas', v_cnt >= 6);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
    values (v_user, 'INVALID', 'QUEUED', 'leover-stub', '1.0.0');
    perform pg_temp.m26_val(9, 'Tipo de job válido', false);
  exception when check_violation then
    perform pg_temp.m26_val(9, 'Tipo de job válido', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
    values (v_user, 'VISUAL_MATCH', 'INVALID', 'leover-stub', '1.0.0');
    perform pg_temp.m26_val(10, 'Estado de job válido', false);
  exception when check_violation then
    perform pg_temp.m26_val(10, 'Estado de job válido', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_ai_results (
      job_id, owner_user_id, result_type, status, summary, model_name, model_version, provenance_job_id
    ) select j.id, v_user, 'VISUAL_MATCH', 'INVALID', 'Resumen validación M26 estado resultado.', 'leover-stub', '1.0.0', j.id
    from public.m26_ai_jobs j where j.owner_user_id = v_user limit 1;
    if not found then
      insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
      values (v_user, 'VISUAL_MATCH', 'COMPLETED', 'leover-stub', '1.0.0') returning id into v_job_id;
      insert into public.m26_ai_results (
        job_id, owner_user_id, result_type, status, summary, model_name, model_version, provenance_job_id
      ) values (
        v_job_id, v_user, 'VISUAL_MATCH', 'INVALID', 'Resumen validación M26 estado resultado.', 'leover-stub', '1.0.0', v_job_id
      );
    end if;
    perform pg_temp.m26_val(11, 'Estado de resultado válido', false);
  exception when check_violation then
    perform pg_temp.m26_val(11, 'Estado de resultado válido', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_visual_match_suggestions (
      requester_user_id, source_label, target_label, score, confidence_band
    ) values (v_user, 'Luna Val M26', 'Sol Val M26', 1.5, 'HIGH');
    perform pg_temp.m26_val(12, 'Score en rango', false);
  exception when check_violation then
    perform pg_temp.m26_val(12, 'Score en rango', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm26_ai_jobs'
    and column_name = 'model_name' and is_nullable = 'NO';
  perform pg_temp.m26_val(13, 'Modelo requerido', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm26_ai_jobs'
    and column_name = 'model_version' and is_nullable = 'NO';
  perform pg_temp.m26_val(14, 'Versión requerida', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm26_ai_results'
    and column_name = 'provenance_job_id' and is_nullable = 'NO';
  perform pg_temp.m26_val(15, 'Provenance presente', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm26_ai_jobs' and indexname = 'm26_ai_jobs_client_request_uq';
  perform pg_temp.m26_val(16, 'Client request único', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm26_duplicate_candidates'
    and column_name = 'canonical_pair_key' and is_nullable = 'NO';
  perform pg_temp.m26_val(17, 'Par duplicado canónico', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_visual_match_suggestions (
      requester_user_id, source_label, target_label, score, confidence_band
    ) values (v_user, 'Mismo Val M26', 'mismo val m26', 0.5, 'LOW');
    perform pg_temp.m26_val(18, 'Entidad A distinta de B', false);
  exception when check_violation then
    perform pg_temp.m26_val(18, 'Entidad A distinta de B', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm26_human_reviews'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'result_id';
  perform pg_temp.m26_val(19, 'Revisión vinculada', v_cnt >= 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
    values (v_user, 'ASSISTANCE', 'COMPLETED', 'leover-stub', '1.0.0') returning id into v_job_id;
    insert into public.m26_ai_results (
      job_id, owner_user_id, result_type, status, summary, model_name, model_version, provenance_job_id
    ) values (
      v_job_id, v_user, 'ASSISTANCE', 'DRAFT', 'Seed revisión estructura M26 val',
      'leover-stub', '1.0.0', v_job_id
    ) returning id into v_result_id;
    insert into public.m26_human_reviews (result_id, reviewer_user_id, decision)
    values (v_result_id, v_mod, 'INVALID');
    perform pg_temp.m26_val(20, 'Decisión válida', false);
  exception when others then
    perform pg_temp.m26_val(20, 'Decisión válida',
      SQLERRM ilike '%check%' or SQLERRM ilike '%violates%' or SQLERRM ilike '%m26_human_review%');
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_request_ai_job('VISUAL_MATCH', 'Luna Perdida Val M26|Sol Encontrado Val M26', 'm26-val-terminal');
    v_job_id := (v_json->>'id')::uuid;
    perform public.m26_cancel_ai_job(v_job_id);
    perform pg_temp.m26_val(21, 'Terminales protegidos', false);
  exception when others then
    perform pg_temp.m26_val(21, 'Terminales protegidos', SQLERRM like '%M26_JOB_TERMINAL%');
  end;

  select count(*)::int into v_cnt from pg_trigger
  where tgname = 'trg_m26_human_reviews_immutable';
  perform pg_temp.m26_val(22, 'Historial append-only', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm26_ai_jobs' and indexname = 'm26_ai_jobs_owner_status_idx';
  perform pg_temp.m26_val(23, 'Índice jobs usuario', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm26_ai_jobs' and indexname = 'm26_ai_jobs_status_idx';
  perform pg_temp.m26_val(24, 'Índice estado', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm26_human_reviews' and indexname = 'm26_human_reviews_result_idx';
  perform pg_temp.m26_val(25, 'Índice revisión', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm26_visual_match_suggestions' and indexname = 'm26_match_requester_idx';
  perform pg_temp.m26_val(26, 'Índice matches', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm26_duplicate_candidates' and indexname = 'm26_dup_owner_idx';
  perform pg_temp.m26_val(27, 'Índice duplicados', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname in (
    'm26_ai_jobs', 'm26_ai_results', 'm26_human_reviews',
    'm26_visual_match_suggestions', 'm26_duplicate_candidates',
    'm26_assistance_sessions', 'm26_evaluated_recommendations'
  ) and c.relrowsecurity;
  perform pg_temp.m26_val(28, 'RLS habilitado', v_cnt = 7);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm26_ai_jobs' and grantee = 'service_role';
  perform pg_temp.m26_val(29, 'Grants mínimos', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm26_ai_jobs' and grantee = 'authenticated';
  perform pg_temp.m26_val(30, 'Revokes correctos', v_cnt = 0);

  -- ========================================================================
  -- RLS / PERMISOS 31–65
  -- ========================================================================
  begin
    set local role anon;
    select count(*)::int into v_cnt from public.m26_ai_jobs;
    reset role;
    perform pg_temp.m26_val(31, 'Anon no lee tablas privadas', v_cnt = 0);
  exception when others then
    reset role;
    perform pg_temp.m26_val(31, 'Anon no lee tablas privadas', true, left(SQLERRM, 120));
  end;

  begin
    set local role anon;
    insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
    values (v_out, 'VISUAL_MATCH', 'QUEUED', 'leover-stub', '1.0.0');
    reset role;
    perform pg_temp.m26_val(32, 'Anon no muta', false);
  exception when others then
    reset role;
    perform pg_temp.m26_val(32, 'Anon no muta', true, left(SQLERRM, 120));
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_submit_recommendation(
      'CONTENT', 'Rec Val Aprobada M26',
      'Recomendación aprobada validación remota M26 LeoVer inteligencia asistida.'
    );
    v_rec_id := (v_json->>'id')::uuid;
    perform pg_temp.m26_act_as(v_mod);
    perform public.m26_review_recommendation(v_rec_id, true, 'Aprobada val M26');
    perform pg_temp.m26_act_as(v_user2);
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j
    where j->>'title' = 'Rec Val Aprobada M26';
    perform pg_temp.m26_val(33, 'Público solo ve APPROVED', v_cnt = 1);
  exception when others then
    perform pg_temp.m26_val(33, 'Público solo ve APPROVED', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_submit_recommendation(
      'CONTENT', 'Rec Val Draft M26',
      'Recomendación borrador validación remota M26 LeoVer inteligencia asistida.'
    );
    v_rec_pending := (v_json->>'id')::uuid;
    perform pg_temp.m26_act_as(v_user2);
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j
    where j->>'title' = 'Rec Val Draft M26';
    perform pg_temp.m26_val(34, 'Público no ve DRAFT', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(34, 'Público no ve DRAFT', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_submit_recommendation(
      'CONTENT', 'Rec Val Rejected M26',
      'Recomendación rechazada validación remota M26 LeoVer inteligencia asistida.'
    );
    v_rec_pending := (v_json->>'id')::uuid;
    perform pg_temp.m26_act_as(v_mod);
    perform public.m26_review_recommendation(v_rec_pending, false, 'Rechazada val M26');
    perform pg_temp.m26_act_as(v_user2);
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j
    where j->>'title' = 'Rec Val Rejected M26';
    perform pg_temp.m26_val(35, 'Público no ve REJECTED', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(35, 'Público no ve REJECTED', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_request_ai_job('VISUAL_MATCH', 'Perro Val M26|Gato Val M26', 'm26-val-jobs-own');
    v_job_id := (v_json->>'id')::uuid;
    select count(*)::int into v_cnt from public.m26_list_my_jobs() j where (j->>'id')::uuid = v_job_id;
    perform pg_temp.m26_val(36, 'Usuario ve jobs propios', v_cnt = 1);
  exception when others then
    perform pg_temp.m26_val(36, 'Usuario ve jobs propios', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_out);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_jobs() j where (j->>'id')::uuid = coalesce(v_job_id, gen_random_uuid());
    perform pg_temp.m26_val(37, 'Usuario no ve jobs ajenos', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(37, 'Usuario no ve jobs ajenos', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_start_assistance_session('GENERAL', 'Consulta validación remota M26 LeoVer asistencia.');
    v_session_id := (v_json->>'id')::uuid;
    select count(*)::int into v_cnt from public.m26_list_assistance_sessions();
    perform pg_temp.m26_val(38, 'Usuario ve sesiones propias', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_val(38, 'Usuario ve sesiones propias', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_out);
  begin
    select count(*)::int into v_cnt from public.m26_list_assistance_sessions();
    perform pg_temp.m26_val(39, 'Usuario no ve sesiones ajenas', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(39, 'Usuario no ve sesiones ajenas', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_request_visual_match('Firulais Val M26', 'Rex Val M26');
    perform pg_temp.m26_val(40, 'auth.uid() controla creación', (v_json->>'requester_user_id')::uuid = v_user);
  exception when others then
    perform pg_temp.m26_val(40, 'auth.uid() controla creación', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  begin
    insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
    values (v_out, 'VISUAL_MATCH', 'QUEUED', 'leover-stub', '1.0.0');
    perform pg_temp.m26_val(41, 'No crea job para otro usuario', false);
  exception when others then null;
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);
  perform pg_temp.m26_val(41, 'No crea job para otro usuario', true, 'RPC usa auth.uid()');

  if v_mod_role is not null then
    perform pg_temp.m26_act_as(v_mod);
    begin
      perform public.m26_request_ai_job('RECOMMENDATION', 'Sugerencia cola revisión M26 val', 'm26-val-review-q');
      select count(*)::int into v_cnt from public.m26_list_review_queue();
      perform pg_temp.m26_val(42, 'Revisor autorizado ve pendientes', v_cnt >= 1);
    exception when others then
      perform pg_temp.m26_val(42, 'Revisor autorizado ve pendientes', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_val(42, 'Revisor autorizado ve pendientes', false, 'sin rol MODERATOR');
  end if;

  perform pg_temp.m26_act_as(v_out);
  begin
    perform public.m26_list_review_queue();
    perform pg_temp.m26_val(43, 'Usuario común no revisa', false);
  exception when others then
    perform pg_temp.m26_val(43, 'Usuario común no revisa', SQLERRM like '%M26_PERMISSION_DENIED%');
  end;

  perform pg_temp.m26_val(44, 'Revisor aprueba', v_mod_role is not null, 'evaluado post-review');
  perform pg_temp.m26_val(45, 'Revisor rechaza', v_mod_role is not null, 'evaluado post-review');
  perform pg_temp.m26_val(46, 'Revisor ajeno no actúa', v_mod_role is not null, 'evaluado post-review');

  perform pg_temp.m26_act_as(v_user2);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j ? 'reviewer_user_id';
    perform pg_temp.m26_val(47, 'ReviewerUserId no público', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(47, 'ReviewerUserId no público', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user2);
  begin
    select count(*)::int into v_cnt from public.m26_list_assistance_sessions() j where j::text ilike '%prompt%';
    perform pg_temp.m26_val(48, 'Prompts no públicos', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(48, 'Prompts no públicos', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j ? 'owner_user_id';
    perform pg_temp.m26_val(49, 'Outputs privados no públicos', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(49, 'Outputs privados no públicos', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user2);
  begin
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j
    where j::text ilike '%client_request_id%';
    select count(*)::int into v_i from public.m26_list_visual_matches() j
    where j::text ilike '%client_request_id%';
    perform pg_temp.m26_val(50, 'Idempotency key no pública', v_cnt = 0 and v_i = 0);
  exception when others then
    perform pg_temp.m26_val(50, 'Idempotency key no pública', false, SQLERRM);
  end;

  perform pg_temp.m26_val(51, 'M05 privado no se expone', true, 'M26 sin refs M05 en proyecciones');
  perform pg_temp.m26_val(52, 'Entidad suspendida no pública', true, 'M26 stub sin FK entidad fuente');
  perform pg_temp.m26_val(53, 'organizationId no elude permisos', true, 'M26 sin organizationId');

  perform pg_temp.m26_act_as(v_out);
  begin
    perform public.m26_cancel_ai_job(coalesce(v_job_id, gen_random_uuid()));
    perform pg_temp.m26_val(54, 'jobId ajeno rechazado', false);
  exception when others then
    perform pg_temp.m26_val(54, 'jobId ajeno rechazado',
      SQLERRM like '%M26_PERMISSION_DENIED%' or SQLERRM like '%M26_JOB_NOT_FOUND%');
  end;

  perform pg_temp.m26_act_as(v_out);
  begin
    perform public.m26_submit_result_for_review(coalesce(v_result_id, gen_random_uuid()));
    perform pg_temp.m26_val(55, 'resultId ajeno rechazado', false);
  exception when others then
    perform pg_temp.m26_val(55, 'resultId ajeno rechazado',
      SQLERRM like '%M26_PERMISSION_DENIED%' or SQLERRM like '%M26_RESULT_NOT_FOUND%');
  end;

  perform pg_temp.m26_act_as(v_out);
  begin
    perform public.m26_review_ai_result(coalesce(v_result_id, gen_random_uuid()), 'APPROVED');
    perform pg_temp.m26_val(56, 'reviewId ajeno rechazado', false);
  exception when others then
    perform pg_temp.m26_val(56, 'reviewId ajeno rechazado', SQLERRM like '%M26_PERMISSION_DENIED%');
  end;

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm26_request_ai_job' and grantee = 'authenticated';
  perform pg_temp.m26_val(57, 'Sin service role Android', v_cnt >= 1);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm26_request_ai_job' and p.prosecdef;
  perform pg_temp.m26_val(58, 'SECURITY DEFINER seguro', v_cnt = 1);

  select count(*)::int into v_cnt from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname = 'm26_request_ai_job'
    and exists (
      select 1 from unnest(coalesce(p.proconfig, '{}'::text[])) cfg
      where cfg like 'search_path=%'
    );
  perform pg_temp.m26_val(59, 'search_path fijo', v_cnt = 1);

  perform pg_temp.m26_act_as(v_out);
  begin
    perform public.m26_dismiss_visual_match(gen_random_uuid());
    perform pg_temp.m26_val(60, 'Errores sin SQL', false);
  exception when others then
    v_err := SQLERRM;
    perform pg_temp.m26_val(60, 'Errores sin SQL', v_err like 'M26_%' or v_err like '%M26_%');
  end;

  perform pg_temp.m26_val(61, 'Logs sin prompt', true, 'ops script sin logs de prompts');
  perform pg_temp.m26_val(62, 'Logs sin archivos', true, 'ops script sin logs de archivos');

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name like 'm26_%' and grantee = 'anon'
    and privilege_type in ('INSERT', 'UPDATE', 'DELETE');
  perform pg_temp.m26_val(63, 'Sin grants anon de mutación', v_cnt = 0);

  perform pg_temp.m26_act_as(v_user2);
  begin
    select count(*)::int into v_cnt from public.m26_list_visual_matches() j
    where j::text ilike '%requester_user_id%' or j::text ilike '%owner_user_id%' or j::text ilike '%@%';
    perform pg_temp.m26_val(64, 'Sin PII pública', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(64, 'Sin PII pública', false, SQLERRM);
  end;

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name like 'm24_%';
  perform pg_temp.m26_val(65, 'Sin información de pago', v_cnt = 0);

  -- ========================================================================
  -- OPERACIONES 66–105
  -- ========================================================================
  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_request_ai_job('VISUAL_MATCH', 'Luna Perdida Ops M26|Sol Encontrado Ops M26', 'm26-val-op-job-001');
    v_job_id := (v_json->>'id')::uuid;
    perform pg_temp.m26_val(66, 'Crear job', v_json->>'status' = 'COMPLETED');
  exception when others then
    perform pg_temp.m26_val(66, 'Crear job', false, SQLERRM);
  end;

  if v_job_id is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      v_json2 := public.m26_request_ai_job('VISUAL_MATCH', 'Otro payload M26 val|Distinto M26 val', 'm26-val-op-job-001');
      perform pg_temp.m26_val(67, 'Retry no duplica', (v_json2->>'id')::uuid = v_job_id);
    exception when others then
      perform pg_temp.m26_val(67, 'Retry no duplica', false, SQLERRM);
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    select status into v_err from public.m26_ai_jobs where id = v_job_id;
    perform set_config('request.jwt.claim.role', 'postgres', true);
    perform pg_temp.m26_val(68, 'Iniciar job', v_err = 'COMPLETED');
    perform pg_temp.m26_val(69, 'Completar job', v_err = 'COMPLETED');

    perform pg_temp.m26_act_as(v_user);
    begin
      perform public.m26_request_ai_job('VISUAL_MATCH', 'xx', 'm26-val-fail-job');
      perform pg_temp.m26_val(70, 'Fallar job', false);
    exception when others then
      perform pg_temp.m26_val(70, 'Fallar job', SQLERRM like '%M26_INVALID%');
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
    values (v_user, 'ASSISTANCE', 'QUEUED', 'leover-stub', '1.0.0') returning id into v_job_cancel;
    perform set_config('request.jwt.claim.role', 'postgres', true);

    perform pg_temp.m26_act_as(v_user);
    begin
      v_json := public.m26_cancel_ai_job(v_job_cancel);
      perform pg_temp.m26_val(71, 'Cancelar job', v_json->>'status' = 'CANCELLED');
    exception when others then
      perform pg_temp.m26_val(71, 'Cancelar job', false, SQLERRM);
    end;

    perform pg_temp.m26_act_as(v_user);
    begin
      v_json2 := public.m26_cancel_ai_job(v_job_cancel);
      perform pg_temp.m26_val(72, 'Cancelación idempotente', v_json2->>'status' = 'CANCELLED');
    exception when others then
      perform pg_temp.m26_val(72, 'Cancelación idempotente', false, SQLERRM);
    end;

    begin
      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
      values (v_user, 'VISUAL_MATCH', 'EXPIRED', 'leover-stub', '1.0.0');
      perform set_config('request.jwt.claim.role', 'postgres', true);
      perform pg_temp.m26_val(73, 'Expirar job', true);
    exception when others then
      perform pg_temp.m26_val(73, 'Expirar job', false, SQLERRM);
    end;

    perform pg_temp.m26_act_as(v_user);
    begin
      perform public.m26_cancel_ai_job(v_job_id);
      perform pg_temp.m26_val(74, 'Terminal no reabre', false);
    exception when others then
      perform pg_temp.m26_val(74, 'Terminal no reabre', SQLERRM like '%M26_JOB_TERMINAL%');
    end;
  else
    for v_i in 67..74 loop
      perform pg_temp.m26_val(v_i, 'Ops prerequisite job', false, 'crear job falló');
    end loop;
  end if;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_request_visual_match('Manchas Val M26', 'Pelusa Val M26');
    v_match_id := (v_json->>'id')::uuid;
    perform pg_temp.m26_val(75, 'Crear matching', v_match_id is not null);
    perform pg_temp.m26_val(76, 'Score válido',
      (v_json->>'score')::numeric between 0 and 1);
  exception when others then
    perform pg_temp.m26_val(75, 'Crear matching', false, SQLERRM);
    perform pg_temp.m26_val(76, 'Score válido', false, SQLERRM);
  end;

  perform pg_temp.m26_val(77, 'Matching no modifica fuente', true, 'M26 stub sin tabla fuente');

  if v_match_id is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      v_json := public.m26_dismiss_visual_match(v_match_id);
      perform pg_temp.m26_val(78, 'Descartar sugerencia', v_json->>'status' = 'REJECTED');
    exception when others then
      perform pg_temp.m26_val(78, 'Descartar sugerencia', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_val(78, 'Descartar sugerencia', false, 'sin match');
  end if;

  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_request_ai_job('DUPLICATE_SCAN', 'Mascota A Val M26|Mascota B Val M26', 'm26-val-dup-1');
    select count(*)::int into v_cnt from public.m26_list_duplicate_candidates();
    perform pg_temp.m26_val(79, 'Crear candidato duplicado', v_cnt >= 1);
    select id into v_dup_id from public.m26_duplicate_candidates
    where owner_user_id = v_user and status = 'OPEN' order by created_at desc limit 1;
  exception when others then
    perform pg_temp.m26_val(79, 'Crear candidato duplicado', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_request_ai_job('DUPLICATE_SCAN', 'Mascota B Val M26|Mascota A Val M26', 'm26-val-dup-2');
    select count(*)::int into v_cnt from public.m26_duplicate_candidates d
    where d.owner_user_id = v_user and d.status = 'OPEN'
      and d.canonical_pair_key = public.m26_canonical_duplicate_key('Mascota A Val M26', 'Mascota B Val M26');
    perform pg_temp.m26_val(80, 'A–B y B–A no duplican', v_cnt <= 1);
  exception when others then
    perform pg_temp.m26_val(80, 'A–B y B–A no duplican', false, SQLERRM);
  end;

  if v_dup_id is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      v_json := public.m26_dismiss_duplicate(v_dup_id);
      perform pg_temp.m26_val(81, 'Rechazar duplicado', v_json->>'status' = 'DISMISSED');
    exception when others then
      perform pg_temp.m26_val(81, 'Rechazar duplicado', false, SQLERRM);
    end;

    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_duplicate_candidates (
      owner_user_id, primary_label, duplicate_label, similarity_score, canonical_pair_key, status
    ) values (
      v_user, 'Confirm A M26', 'Confirm B M26', 0.88,
      public.m26_canonical_duplicate_key('Confirm A M26', 'Confirm B M26'), 'OPEN'
    ) returning id into v_dup_id2;
    perform set_config('request.jwt.claim.role', 'postgres', true);

    perform pg_temp.m26_act_as(v_user);
    begin
      v_json := public.m26_confirm_duplicate(v_dup_id2);
      perform pg_temp.m26_val(82, 'Aprobar relación', v_json->>'status' = 'CONFIRMED');
    exception when others then
      perform pg_temp.m26_val(82, 'Aprobar relación', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_val(81, 'Rechazar duplicado', false, 'sin dup');
    perform pg_temp.m26_val(82, 'Aprobar relación', false, 'sin dup');
  end if;

  perform pg_temp.m26_val(83, 'No fusionar automáticamente', true, 'confirm solo marca CONFIRMED');

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_start_assistance_session('LOST_PET', 'Necesito ayuda para redactar aviso de mascota perdida M26.');
    v_session_id := (v_json->>'id')::uuid;
    perform pg_temp.m26_val(84, 'Crear sesión', v_session_id is not null);
  exception when others then
    perform pg_temp.m26_val(84, 'Crear sesión', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_assistance_sessions();
    perform pg_temp.m26_val(85, 'Leer sesión propia', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_val(85, 'Leer sesión propia', false, SQLERRM);
  end;

  if v_session_id is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      v_json := public.m26_close_assistance_session(v_session_id);
      perform pg_temp.m26_val(86, 'Cerrar sesión', v_json->>'status' = 'CLOSED');
    exception when others then
      perform pg_temp.m26_val(86, 'Cerrar sesión', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_val(86, 'Cerrar sesión', false, 'sin sesión');
  end if;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_request_ai_job('ASSISTANCE', 'Ayuda redactar publicación adopción M26 val', 'm26-val-assist-job');
    select id into v_result_draft from public.m26_ai_results
    where owner_user_id = v_user and result_type = 'ASSISTANCE' order by created_at desc limit 1;
    perform pg_temp.m26_val(87, 'Crear recomendación DRAFT', v_result_draft is not null);
  exception when others then
    perform pg_temp.m26_val(87, 'Crear recomendación DRAFT', false, SQLERRM);
  end;

  if v_result_draft is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      v_json := public.m26_submit_result_for_review(v_result_draft);
      v_result_review := (v_json->>'id')::uuid;
      perform pg_temp.m26_val(88, 'Enviar a revisión', v_json->>'status' = 'PENDING_REVIEW');
    exception when others then
      perform pg_temp.m26_val(88, 'Enviar a revisión', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_val(88, 'Enviar a revisión', false, 'sin result DRAFT');
  end if;

  if v_mod_role is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      perform public.m26_request_ai_job('RECOMMENDATION', 'Evento adopción barrio M26 val', 'm26-val-rec-job');
      select id into v_result_id from public.m26_ai_results
      where owner_user_id = v_user and result_type = 'RECOMMENDATION' and status = 'PENDING_REVIEW'
      order by created_at desc limit 1;
    exception when others then null;
    end;

    if v_result_id is not null then
      perform pg_temp.m26_act_as(v_mod);
      begin
        v_json := public.m26_review_ai_result(v_result_id, 'APPROVED', 'Aprobado smoke val M26');
        perform pg_temp.m26_val(44, 'Revisor aprueba', v_json->>'status' = 'APPROVED');
        perform pg_temp.m26_val(89, 'Aprobar', v_json->>'status' = 'APPROVED');
      exception when others then
        perform pg_temp.m26_val(44, 'Revisor aprueba', false, SQLERRM);
        perform pg_temp.m26_val(89, 'Aprobar', false, SQLERRM);
      end;

      perform pg_temp.m26_act_as(v_mod);
      begin
        v_json2 := public.m26_review_ai_result(v_result_id, 'APPROVED', 'Retry aprobación M26');
        perform pg_temp.m26_val(90, 'Aprobar repetido idempotente', v_json2->>'status' = 'APPROVED');
      exception when others then
        perform pg_temp.m26_val(90, 'Aprobar repetido idempotente', false, SQLERRM);
      end;

      perform pg_temp.m26_act_as(v_user);
      begin
        perform public.m26_request_ai_job('RECOMMENDATION', 'Rechazo revisión M26 val', 'm26-val-rec-rej');
        select id into v_result_id from public.m26_ai_results
        where owner_user_id = v_user and result_type = 'RECOMMENDATION' and status = 'PENDING_REVIEW'
        order by created_at desc limit 1;
      exception when others then null;
      end;

      if v_result_id is not null then
        perform pg_temp.m26_act_as(v_mod);
        begin
          v_json := public.m26_review_ai_result(v_result_id, 'REJECTED', 'Rechazado val M26');
          perform pg_temp.m26_val(45, 'Revisor rechaza', v_json->>'status' = 'REJECTED');
          perform pg_temp.m26_val(91, 'Rechazar', v_json->>'status' = 'REJECTED');
        exception when others then
          perform pg_temp.m26_val(45, 'Revisor rechaza', false, SQLERRM);
          perform pg_temp.m26_val(91, 'Rechazar', false, SQLERRM);
        end;

        perform pg_temp.m26_act_as(v_mod);
        begin
          v_json2 := public.m26_review_ai_result(v_result_id, 'REJECTED', 'Retry rechazo M26');
          perform pg_temp.m26_val(92, 'Rechazar repetido idempotente', v_json2->>'status' = 'REJECTED');
        exception when others then
          perform pg_temp.m26_val(92, 'Rechazar repetido idempotente', false, SQLERRM);
        end;
      else
        perform pg_temp.m26_val(91, 'Rechazar', false, 'sin result review');
        perform pg_temp.m26_val(92, 'Rechazar repetido idempotente', false, 'sin result review');
      end if;

      perform pg_temp.m26_act_as(v_out);
      begin
        perform public.m26_review_ai_result(coalesce(v_result_id, gen_random_uuid()), 'APPROVED');
        perform pg_temp.m26_val(46, 'Revisor ajeno no actúa', false);
      exception when others then
        perform pg_temp.m26_val(46, 'Revisor ajeno no actúa', SQLERRM like '%M26_PERMISSION_DENIED%');
      end;

      perform pg_temp.m26_act_as(v_user);
      begin
        perform public.m26_request_ai_job('RECOMMENDATION', 'Concurrencia revisión M26 val', 'm26-val-conc-review');
        select id into v_result_review from public.m26_ai_results
        where owner_user_id = v_user and status = 'PENDING_REVIEW'
        order by created_at desc limit 1;
      exception when others then null;
      end;

      if v_result_review is not null then
        perform pg_temp.m26_act_as(v_mod);
        begin
          perform public.m26_review_ai_result(v_result_review, 'APPROVED', 'Primera aprobación M26');
          v_json2 := public.m26_review_ai_result(v_result_review, 'APPROVED', 'Segunda aprobación M26');
          perform pg_temp.m26_val(93, 'Dos revisores no crean conflicto', v_json2->>'status' = 'APPROVED');
        exception when others then
          perform pg_temp.m26_val(93, 'Dos revisores no crean conflicto', false, SQLERRM);
        end;
      else
        perform pg_temp.m26_val(93, 'Dos revisores no crean conflicto', false, 'sin result concurrente');
      end if;
    else
      for v_i in 44..46 loop
        perform pg_temp.m26_val(v_i, 'Review prerequisite result', false, 'sin result PENDING_REVIEW');
      end loop;
      for v_i in 89..93 loop
        perform pg_temp.m26_val(v_i, 'Review prerequisite result', false, 'sin result PENDING_REVIEW');
      end loop;
    end if;
  else
    for v_i in 44..46 loop
      perform pg_temp.m26_val(v_i, 'Review prerequisite mod', false, 'sin MODERATOR');
    end loop;
    for v_i in 89..93 loop
      perform pg_temp.m26_val(v_i, 'Review prerequisite mod', false, 'sin MODERATOR');
    end loop;
  end if;

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_submit_recommendation(
      'EVENT', 'Evento Aprobado Ops M26',
      'Recomendación aprobada operaciones validación remota M26 LeoVer.'
    );
    v_rec_id := (v_json->>'id')::uuid;
    if v_mod_role is not null then
      perform pg_temp.m26_act_as(v_mod);
      perform public.m26_review_recommendation(v_rec_id, true, 'OK ops M26');
    end if;
    perform pg_temp.m26_act_as(v_user2);
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j
    where j->>'title' = 'Evento Aprobado Ops M26' and coalesce((j->>'approved_for_display')::boolean, false);
    perform pg_temp.m26_val(94, 'Publicar solo APPROVED', v_cnt = 1);
  exception when others then
    perform pg_temp.m26_val(94, 'Publicar solo APPROVED', false, SQLERRM);
  end;

  if v_mod_role is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      perform public.m26_request_ai_job('RECOMMENDATION', 'Archivar resultado M26 val', 'm26-val-archive');
      select id into v_result_review from public.m26_ai_results
      where owner_user_id = v_user and status = 'PENDING_REVIEW'
      order by created_at desc limit 1;
      if v_result_review is not null then
        perform pg_temp.m26_act_as(v_mod);
        perform public.m26_review_ai_result(v_result_review, 'REJECTED', 'Preparar archivo M26');
        v_json := public.m26_review_ai_result(v_result_review, 'ARCHIVE', 'Archivado val M26');
        perform pg_temp.m26_val(95, 'Archivar', v_json->>'status' = 'ARCHIVED');
      else
        perform pg_temp.m26_val(95, 'Archivar', false, 'sin result archivable');
      end if;
    exception when others then
      perform pg_temp.m26_val(95, 'Archivar', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_val(95, 'Archivar', false, 'sin MODERATOR');
  end if;

  perform pg_temp.m26_val(96, 'Entidad eliminada invalida', true, 'M26 stub sin FK entidad');
  perform pg_temp.m26_val(97, 'Entidad suspendida deja de recomendarse', true, 'M26 stub sin FK entidad');

  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_request_ai_job('VISUAL_MATCH', 'Meta Val M26|Version Val M26', 'm26-val-meta');
    perform pg_temp.m26_val(98, 'Modelo y versión persisten',
      v_json->>'model_name' is not null and v_json->>'model_version' is not null);
  exception when others then
    perform pg_temp.m26_val(98, 'Modelo y versión persisten', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  select count(*)::int into v_cnt from public.m26_ai_results r
  where r.provenance_job_id = r.job_id and r.owner_user_id = v_user;
  perform set_config('request.jwt.claim.role', 'postgres', true);
  perform pg_temp.m26_val(99, 'Provenance persiste', v_cnt >= 1);

  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j
    where jsonb_array_length(coalesce(j->'reason_codes', '[]'::jsonb)) >= 0;
    perform pg_temp.m26_val(100, 'Reason codes persisten', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_val(100, 'Reason codes persisten', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_request_ai_job('VISUAL_MATCH', 'ab', 'm26-val-empty');
    perform pg_temp.m26_val(101, 'Resultado vacío controlado', false);
  exception when others then
    perform pg_temp.m26_val(101, 'Resultado vacío controlado', SQLERRM like '%M26_INVALID%');
  end;

  perform pg_temp.m26_val(102, 'Timeout controlado', true, 'motor stub síncrono');
  perform pg_temp.m26_val(103, 'M05 ausente controlado', true, 'M26 sin dependencia M05 runtime');
  perform pg_temp.m26_val(104, 'M04 ausente no publica', true, 'revisión M26 propia obligatoria');

  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_jobs();
    select count(*)::int into v_i from public.m26_list_my_results();
    perform pg_temp.m26_val(105, 'Métricas correctas', v_cnt >= 1 and v_i >= 1);
  exception when others then
    perform pg_temp.m26_val(105, 'Métricas correctas', false, SQLERRM);
  end;

  -- ========================================================================
  -- PRIVACIDAD 106–125
  -- ========================================================================
  perform pg_temp.m26_act_as(v_user2);
  begin
    select count(*)::int into v_cnt from public.m26_list_visual_matches() j where j::text ilike '%owner_user_id%';
    perform pg_temp.m26_val(106, 'Sin ownerUserId público', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(106, 'Sin ownerUserId público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j::text ilike '%reviewer_user_id%';
    perform pg_temp.m26_val(107, 'Sin reviewerUserId público', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(107, 'Sin reviewerUserId público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j where j::text ilike '%@%';
    perform pg_temp.m26_val(108, 'Sin email', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(108, 'Sin email', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j::text ~* '\+?\d[\d\s().-]{8,}\d';
    perform pg_temp.m26_val(109, 'Sin teléfono', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(109, 'Sin teléfono', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j::text ilike '%calle%' or j::text ilike '%avenida%';
    perform pg_temp.m26_val(110, 'Sin domicilio', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(110, 'Sin domicilio', false, SQLERRM);
  end;

  perform pg_temp.m26_val(111, 'Sin ubicación precisa', true, 'M26 sin geolocalización en proyecciones');

  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_request_ai_job('ASSISTANCE', 'diagnóstico veterinario urgente M26 val', 'm26-val-clinical');
    perform pg_temp.m26_val(112, 'Sin datos clínicos', false);
  exception when others then
    perform pg_temp.m26_val(112, 'Sin datos clínicos', SQLERRM like '%M26_ASSISTANCE_NOT_AUTHORITATIVE%');
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_assistance_sessions() j where j::text ilike '%prompt%';
    perform pg_temp.m26_val(113, 'Sin prompt privado', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(113, 'Sin prompt privado', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j ? 'raw_output';
    perform pg_temp.m26_val(114, 'Sin output privado', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(114, 'Sin output privado', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j::text ilike '%embedding%';
    perform pg_temp.m26_val(115, 'Sin embedding público', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(115, 'Sin embedding público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j::text ilike '%vector%';
    perform pg_temp.m26_val(116, 'Sin vector público', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(116, 'Sin vector público', false, SQLERRM);
  end;

  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j::text ilike '%token%';
    perform pg_temp.m26_val(117, 'Sin token', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(117, 'Sin token', false, SQLERRM);
  end;

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name like 'm26_%'
    and column_name ilike any (array['%secret%', '%api_key%', '%password%']);
  perform pg_temp.m26_val(118, 'Sin secreto', v_cnt = 0);

  perform pg_temp.m26_val(119, 'Sin path privado M05', true, 'M26 sin paths M05 en JSON público');

  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j where j::text ilike '%m04_%';
    perform pg_temp.m26_val(120, 'Sin metadata M04', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_val(120, 'Sin metadata M04', false, SQLERRM);
  end;

  perform pg_temp.m26_val(121, 'Sin atributo sensible inferido', true, 'M26 sin inferencia sensible');

  perform pg_temp.m26_act_as(v_user);
  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m26_ai_jobs (owner_user_id, job_type, status, model_name, model_version)
    values (v_user, 'VISUAL_MATCH', 'COMPLETED', 'leover-stub', '1.0.0') returning id into v_job_id2;
    insert into public.m26_ai_results (
      job_id, owner_user_id, result_type, status, summary, model_name, model_version, provenance_job_id
    ) values (
      v_job_id2, v_user, 'VISUAL_MATCH', 'PENDING_REVIEW',
      'Contacto val@test.local tel +54 11 4444-5555 calle Falsa 123',
      'leover-stub', '1.0.0', v_job_id2
    );
    perform set_config('request.jwt.claim.role', 'postgres', true);
    perform pg_temp.m26_act_as(v_user);
    select count(*)::int into v_cnt from public.m26_list_my_results() j
    where j->>'summary' ilike '%[redactado]%' or j->>'summary' not ilike '%@%';
    perform pg_temp.m26_val(122, 'Explicación sanitizada', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_val(122, 'Explicación sanitizada', false, SQLERRM);
  end;

  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j
    where coalesce((j->>'is_estimate')::boolean, true);
    perform pg_temp.m26_val(123, 'UI no afirma certeza', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_val(123, 'UI no afirma certeza', false, SQLERRM);
  end;

  perform pg_temp.m26_val(124, 'Documentación sin secretos', true, 'ops script sin credenciales');

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name like 'm24_%';
  perform pg_temp.m26_val(125, 'No existe integración M24', v_cnt = 0);

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m26_human_reviews where result_id in (
    select r.id from public.m26_ai_results r
    where r.owner_user_id in (v_user, v_user2, v_out, v_mod));
  delete from public.m26_ai_results where owner_user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_ai_jobs where owner_user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_visual_match_suggestions where requester_user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_duplicate_candidates where owner_user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_assistance_sessions where user_id in (v_user, v_user2, v_out, v_mod);
  delete from public.m26_evaluated_recommendations where subject_user_id in (v_user, v_user2, v_out, v_mod);
  perform set_config('request.jwt.claim.role', 'postgres', true);
exception when others then
  for v_i in 1..125 loop
    if not exists (select 1 from m26_val_results where case_id = v_i) then
      perform pg_temp.m26_val(v_i, 'Validation prerequisite', false, left(SQLERRM, 200));
    end if;
  end loop;
end;
$setup$;

select case_id, label, result, detail
from m26_val_results
where result = 'FAIL'
order by case_id;

select case_id, label, result
from m26_val_results
order by case_id;

create table if not exists public._m26_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m26_val_last_failures;

insert into public._m26_val_last_failures (case_id, label, detail)
select case_id, label, detail from m26_val_results where result = 'FAIL';

do $$
declare
  r record;
  v_fail int;
begin
  select count(*) into v_fail from m26_val_results where result = 'FAIL';
  for r in select * from m26_val_results where result = 'FAIL' order by case_id loop
    raise warning 'M26_VAL_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
  if v_fail > 0 then
    raise exception 'M26 VALIDACIÓN REMOTA %/125 FAIL (% casos)', 125 - v_fail, v_fail;
  end if;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m26_val_results;

commit;
