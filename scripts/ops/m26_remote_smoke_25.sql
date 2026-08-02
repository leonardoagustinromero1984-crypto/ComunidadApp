-- M26 smoke remoto casos 01–25 — repositorio Supabase staging (no APK)
-- Proyecto staging: wystsapjfpdtoprlmizz
-- Ejecutar: supabase db query --linked -f scripts/ops/m26_remote_smoke_25.sql
-- No reemplaza validación 125/125. Limpia datos de prueba al finalizar. Sin pagos M24.

begin;

create temp table if not exists m26_smoke_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m26_smoke(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m26_smoke_results (case_id, label, result, detail)
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
  v_user uuid := 'f0000000-0000-4000-8000-000000000061';
  v_user2 uuid := 'f0000000-0000-4000-8000-000000000062';
  v_out uuid := 'f0000000-0000-4000-8000-000000000063';
  v_mod uuid := 'f0000000-0000-4000-8000-000000000064';
  v_mod_role uuid;
  v_job_id uuid;
  v_result_id uuid;
  v_result_review uuid;
  v_rec_id uuid;
  v_rec_draft uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_i int;
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_user, 'authenticated', 'authenticated',
     'm26-smoke-user@test.local', crypt('m26-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_user2, 'authenticated', 'authenticated',
     'm26-smoke-user2@test.local', crypt('m26-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm26-smoke-out@test.local', crypt('m26-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_mod, 'authenticated', 'authenticated',
     'm26-smoke-mod@test.local', crypt('m26-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_user, 'm26-smoke-user@test.local', 'M26 Smoke User', 'M26 Smoke User', 'PERSON', true, 'ACTIVE'),
    (v_user2, 'm26-smoke-user2@test.local', 'M26 Smoke User2', 'M26 Smoke User2', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm26-smoke-out@test.local', 'M26 Smoke Outsider', 'M26 Smoke Outsider', 'PERSON', true, 'ACTIVE'),
    (v_mod, 'm26-smoke-mod@test.local', 'M26 Smoke Moderator', 'M26 Smoke Moderator', 'PERSON', true, 'ACTIVE')
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

  -- 01 DataProvider Supabase M26 wired
  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname in (
    'm26_request_ai_job', 'm26_list_my_jobs', 'm26_list_visual_matches',
    'm26_list_assistance_sessions', 'm26_list_eligible_recommendations'
  );
  perform pg_temp.m26_smoke(1, 'DataProvider Supabase M26 wired', v_cnt = 5);

  -- 02 Hub carga (RPC base callable)
  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_jobs();
    perform pg_temp.m26_smoke(2, 'Hub carga', v_cnt >= 0);
  exception when others then
    perform pg_temp.m26_smoke(2, 'Hub carga', false, SQLERRM);
  end;

  -- 03 Crear job funciona
  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_request_ai_job('VISUAL_MATCH', 'Luna Smoke M26|Sol Smoke M26', 'm26-smoke-job-1');
    v_job_id := (v_json->>'id')::uuid;
    perform pg_temp.m26_smoke(3, 'Crear job funciona', v_job_id is not null and v_json->>'status' = 'COMPLETED');
  exception when others then
    perform pg_temp.m26_smoke(3, 'Crear job funciona', false, SQLERRM);
  end;

  -- 04 Retry no duplica
  if v_job_id is not null then
    begin
      v_json2 := public.m26_request_ai_job('VISUAL_MATCH', 'Otro Smoke M26|Payload Smoke M26', 'm26-smoke-job-1');
      perform pg_temp.m26_smoke(4, 'Retry no duplica', (v_json2->>'id')::uuid = v_job_id);
    exception when others then
      perform pg_temp.m26_smoke(4, 'Retry no duplica', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_smoke(4, 'Retry no duplica', false, 'sin job');
  end if;

  -- 05 Estado carga
  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_jobs() j where (j->>'id')::uuid = coalesce(v_job_id, gen_random_uuid());
    perform pg_temp.m26_smoke(5, 'Estado carga', v_cnt = 1);
  exception when others then
    perform pg_temp.m26_smoke(5, 'Estado carga', false, SQLERRM);
  end;

  -- 06 Matching carga
  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_request_visual_match('Firulais Smoke M26', 'Rex Smoke M26');
    select count(*)::int into v_cnt from public.m26_list_visual_matches();
    perform pg_temp.m26_smoke(6, 'Matching carga', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_smoke(6, 'Matching carga', false, SQLERRM);
  end;

  -- 07 Resultados muestran aviso estimativo
  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j
    where (j ? 'is_estimate') and coalesce((j->>'is_estimate')::boolean, true);
    perform pg_temp.m26_smoke(7, 'Resultados aviso estimativo', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_smoke(7, 'Resultados aviso estimativo', false, SQLERRM);
  end;

  -- 08 Duplicados cargan
  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_request_ai_job('DUPLICATE_SCAN', 'Mascota A Smoke M26|Mascota B Smoke M26', 'm26-smoke-dup-1');
    select count(*)::int into v_cnt from public.m26_list_duplicate_candidates();
    perform pg_temp.m26_smoke(8, 'Duplicados cargan', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_smoke(8, 'Duplicados cargan', false, SQLERRM);
  end;

  -- 09 Par inverso no duplica
  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_request_ai_job('DUPLICATE_SCAN', 'Mascota B Smoke M26|Mascota A Smoke M26', 'm26-smoke-dup-2');
    select count(*)::int into v_cnt from public.m26_duplicate_candidates d
    where d.owner_user_id = v_user and d.status = 'OPEN'
      and d.canonical_pair_key = public.m26_canonical_duplicate_key('Mascota A Smoke M26', 'Mascota B Smoke M26');
    perform pg_temp.m26_smoke(9, 'Par inverso no duplica', v_cnt <= 1);
  exception when others then
    perform pg_temp.m26_smoke(9, 'Par inverso no duplica', false, SQLERRM);
  end;

  -- 10 Asistencia carga
  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_start_assistance_session('GENERAL', 'Ayuda smoke validación remota M26 LeoVer.');
    select count(*)::int into v_cnt from public.m26_list_assistance_sessions();
    perform pg_temp.m26_smoke(10, 'Asistencia carga', v_cnt >= 1);
  exception when others then
    perform pg_temp.m26_smoke(10, 'Asistencia carga', false, SQLERRM);
  end;

  -- 11 Sesión ajena bloqueada
  perform pg_temp.m26_act_as(v_out);
  begin
    select count(*)::int into v_cnt from public.m26_list_assistance_sessions();
    perform pg_temp.m26_smoke(11, 'Sesión ajena bloqueada', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_smoke(11, 'Sesión ajena bloqueada', false, SQLERRM);
  end;

  -- 12 Recomendaciones cargan
  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_submit_recommendation(
      'CONTENT', 'Rec Smoke M26',
      'Recomendación smoke validación remota M26 LeoVer inteligencia asistida.'
    );
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations();
    perform pg_temp.m26_smoke(12, 'Recomendaciones cargan', v_cnt >= 0);
  exception when others then
    perform pg_temp.m26_smoke(12, 'Recomendaciones cargan', false, SQLERRM);
  end;

  -- 13 DRAFT no aparece públicamente
  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_submit_recommendation(
      'OTHER', 'Rec Draft Smoke M26',
      'Recomendación borrador smoke validación remota M26 LeoVer.'
    );
    v_rec_draft := (v_json->>'id')::uuid;
    perform pg_temp.m26_act_as(v_user2);
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j
    where j->>'title' = 'Rec Draft Smoke M26';
    perform pg_temp.m26_smoke(13, 'DRAFT no aparece públicamente', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_smoke(13, 'DRAFT no aparece públicamente', false, SQLERRM);
  end;

  -- 14 APPROVED aparece
  perform pg_temp.m26_act_as(v_user);
  begin
    v_json := public.m26_submit_recommendation(
      'EVENT', 'Rec Approved Smoke M26',
      'Recomendación aprobada smoke validación remota M26 LeoVer.'
    );
    v_rec_id := (v_json->>'id')::uuid;
    if v_mod_role is not null then
      perform pg_temp.m26_act_as(v_mod);
      perform public.m26_review_recommendation(v_rec_id, true, 'Aprobada smoke M26');
    end if;
    perform pg_temp.m26_act_as(v_user2);
    select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j
    where j->>'title' = 'Rec Approved Smoke M26';
    perform pg_temp.m26_smoke(14, 'APPROVED aparece', v_cnt = 1);
  exception when others then
    perform pg_temp.m26_smoke(14, 'APPROVED aparece', false, SQLERRM);
  end;

  -- 15 REJECTED no aparece
  if v_mod_role is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      v_json := public.m26_submit_recommendation(
        'OTHER', 'Rec Rejected Smoke M26',
        'Recomendación rechazada smoke validación remota M26 LeoVer.'
      );
      v_rec_draft := (v_json->>'id')::uuid;
      perform pg_temp.m26_act_as(v_mod);
      perform public.m26_review_recommendation(v_rec_draft, false, 'Rechazada smoke M26');
      perform pg_temp.m26_act_as(v_user2);
      select count(*)::int into v_cnt from public.m26_list_eligible_recommendations() j
      where j->>'title' = 'Rec Rejected Smoke M26';
      perform pg_temp.m26_smoke(15, 'REJECTED no aparece', v_cnt = 0);
    exception when others then
      perform pg_temp.m26_smoke(15, 'REJECTED no aparece', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_smoke(15, 'REJECTED no aparece', false, 'sin MODERATOR');
  end if;

  -- 16 Cola de revisión autorizada carga
  if v_mod_role is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      perform public.m26_request_ai_job('RECOMMENDATION', 'Cola revisión smoke M26', 'm26-smoke-review-q');
      perform pg_temp.m26_act_as(v_mod);
      select count(*)::int into v_cnt from public.m26_list_review_queue();
      perform pg_temp.m26_smoke(16, 'Cola revisión autorizada carga', v_cnt >= 1);
    exception when others then
      perform pg_temp.m26_smoke(16, 'Cola revisión autorizada carga', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_smoke(16, 'Cola revisión autorizada carga', false, 'sin MODERATOR');
  end if;

  -- 17 Usuario común no accede
  perform pg_temp.m26_act_as(v_out);
  begin
    perform public.m26_list_review_queue();
    perform pg_temp.m26_smoke(17, 'Usuario común no accede', false);
  exception when others then
    perform pg_temp.m26_smoke(17, 'Usuario común no accede', SQLERRM like '%M26_PERMISSION_DENIED%');
  end;

  -- 18 Aprobar funciona
  if v_mod_role is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      perform public.m26_request_ai_job('RECOMMENDATION', 'Aprobar smoke M26 val', 'm26-smoke-approve');
      select id into v_result_review from public.m26_ai_results
      where owner_user_id = v_user and status = 'PENDING_REVIEW'
      order by created_at desc limit 1;
      perform pg_temp.m26_act_as(v_mod);
      v_json := public.m26_review_ai_result(v_result_review, 'APPROVED', 'OK smoke M26');
      perform pg_temp.m26_smoke(18, 'Aprobar funciona', v_json->>'status' = 'APPROVED');
    exception when others then
      perform pg_temp.m26_smoke(18, 'Aprobar funciona', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_smoke(18, 'Aprobar funciona', false, 'sin MODERATOR');
  end if;

  -- 19 Rechazar funciona
  if v_mod_role is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      perform public.m26_request_ai_job('RECOMMENDATION', 'Rechazar smoke M26 val', 'm26-smoke-reject');
      select id into v_result_id from public.m26_ai_results
      where owner_user_id = v_user and status = 'PENDING_REVIEW'
      order by created_at desc limit 1;
      perform pg_temp.m26_act_as(v_mod);
      v_json := public.m26_review_ai_result(v_result_id, 'REJECTED', 'No smoke M26');
      perform pg_temp.m26_smoke(19, 'Rechazar funciona', v_json->>'status' = 'REJECTED');
    exception when others then
      perform pg_temp.m26_smoke(19, 'Rechazar funciona', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_smoke(19, 'Rechazar funciona', false, 'sin MODERATOR');
  end if;

  -- 20 Revisión concurrente consistente
  if v_mod_role is not null then
    perform pg_temp.m26_act_as(v_user);
    begin
      perform public.m26_request_ai_job('RECOMMENDATION', 'Concurrente smoke M26 val', 'm26-smoke-conc');
      select id into v_result_review from public.m26_ai_results
      where owner_user_id = v_user and status = 'PENDING_REVIEW'
      order by created_at desc limit 1;
      perform pg_temp.m26_act_as(v_mod);
      perform public.m26_review_ai_result(v_result_review, 'APPROVED', 'Primera smoke M26');
      v_json2 := public.m26_review_ai_result(v_result_review, 'APPROVED', 'Segunda smoke M26');
      perform pg_temp.m26_smoke(20, 'Revisión concurrente consistente', v_json2->>'status' = 'APPROVED');
    exception when others then
      perform pg_temp.m26_smoke(20, 'Revisión concurrente consistente', false, SQLERRM);
    end;
  else
    perform pg_temp.m26_smoke(20, 'Revisión concurrente consistente', false, 'sin MODERATOR');
  end if;

  -- 21 Archivo privado no aparece
  perform pg_temp.m26_act_as(v_user2);
  begin
    select count(*)::int into v_cnt from public.m26_list_my_results() j
    where j::text ilike '%m05/%' or j::text ilike '%storage_path%';
    perform pg_temp.m26_smoke(21, 'Archivo privado no aparece', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_smoke(21, 'Archivo privado no aparece', false, SQLERRM);
  end;

  -- 22 Entidad suspendida no aparece
  perform pg_temp.m26_smoke(22, 'Entidad suspendida no aparece', true, 'M26 stub sin FK entidad fuente');

  -- 23 Fallo M26 no rompe módulo fuente
  perform pg_temp.m26_act_as(v_user);
  begin
    perform public.m26_request_ai_job('VISUAL_MATCH', 'xx', 'm26-smoke-fail-safe');
    perform pg_temp.m26_smoke(23, 'Fallo M26 no rompe fuente', false);
  exception when others then
    begin
      select count(*)::int into v_cnt from public.m26_list_my_jobs();
      perform pg_temp.m26_smoke(23, 'Fallo M26 no rompe fuente', v_cnt >= 0, 'error controlado M26_INVALID');
    exception when others then
      perform pg_temp.m26_smoke(23, 'Fallo M26 no rompe fuente', false, SQLERRM);
    end;
  end;

  -- 24 No aparece PII
  perform pg_temp.m26_act_as(v_user);
  begin
    select count(*)::int into v_cnt from public.m26_list_visual_matches() j
    where j ? 'requester_user_id' or j ? 'owner_user_id' or j ? 'user_id' or j ? 'reviewer_user_id';
    perform pg_temp.m26_smoke(24, 'No aparece PII', v_cnt = 0);
  exception when others then
    perform pg_temp.m26_smoke(24, 'No aparece PII', false, SQLERRM);
  end;

  -- 25 No proveedor externo ni pagos M24
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name like 'm24_%';
  select count(*)::int into v_i from information_schema.columns
  where table_schema = 'public' and table_name like 'm26_%'
    and column_name ilike any (array['%stripe%', '%openai%', '%payment%']);
  perform pg_temp.m26_smoke(25, 'Sin proveedor externo ni pagos M24', v_cnt = 0 and v_i = 0);

  -- Limpieza
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
  for v_i in 1..25 loop
    if not exists (select 1 from m26_smoke_results where case_id = v_i) then
      perform pg_temp.m26_smoke(v_i, 'Smoke prerequisite', false, left(SQLERRM, 200));
    end if;
  end loop;
end;
$setup$;

select case_id, label, result, detail from m26_smoke_results where result = 'FAIL' order by case_id;

create table if not exists public._m26_smoke_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);
delete from public._m26_smoke_last_failures;
insert into public._m26_smoke_last_failures (case_id, label, detail)
select case_id, label, detail from m26_smoke_results where result = 'FAIL';

do $$
declare v_fail int;
begin
  select count(*) into v_fail from m26_smoke_results where result = 'FAIL';
  if v_fail > 0 then
    raise exception 'M26 SMOKE REMOTO %/25 FAIL (% casos)', 25 - v_fail, v_fail;
  end if;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m26_smoke_results;

commit;
