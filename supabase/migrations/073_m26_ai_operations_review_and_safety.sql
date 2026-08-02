-- =============================================================================
-- LeoVer M26 — migración 073: operaciones, jobs, revisión humana (Bloque 4).
-- Forward-only sobre 001–072. LOCAL ONLY: no aplicar a staging sin autorización.
-- Sin pagos ni integración M24. Motor stub síncrono — no reemplaza moderación M04.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Tablas operativas Bloque 4
-- ---------------------------------------------------------------------------

create table if not exists public.m26_ai_jobs (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references public.users(id) on delete restrict,
  job_type text not null,
  status text not null default 'QUEUED',
  client_request_id text,
  model_name text not null,
  model_version text not null,
  error_code text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  completed_at timestamptz,
  constraint m26_job_type_chk check (
    job_type = any (array['VISUAL_MATCH','DUPLICATE_SCAN','ASSISTANCE','RECOMMENDATION']::text[])
  ),
  constraint m26_job_status_chk check (
    status = any (array['QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED','EXPIRED']::text[])
  ),
  constraint m26_job_model_name_chk check (char_length(trim(model_name)) between 2 and 80),
  constraint m26_job_model_version_chk check (char_length(trim(model_version)) between 1 and 40),
  constraint m26_job_client_request_len_chk check (
    client_request_id is null or char_length(trim(client_request_id)) between 1 and 120
  )
);

create unique index if not exists m26_ai_jobs_client_request_uq
  on public.m26_ai_jobs (owner_user_id, client_request_id)
  where client_request_id is not null;

create index if not exists m26_ai_jobs_owner_status_idx
  on public.m26_ai_jobs (owner_user_id, status, updated_at desc);

create index if not exists m26_ai_jobs_status_idx
  on public.m26_ai_jobs (status, created_at desc);

create table if not exists public.m26_ai_results (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.m26_ai_jobs(id) on delete restrict,
  owner_user_id uuid not null references public.users(id) on delete restrict,
  result_type text not null,
  status text not null default 'DRAFT',
  summary text not null,
  reason_codes jsonb not null default '[]'::jsonb,
  model_name text not null,
  model_version text not null,
  source_module text not null default 'M26',
  provenance_job_id uuid not null references public.m26_ai_jobs(id) on delete restrict,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m26_result_type_chk check (
    result_type = any (array['VISUAL_MATCH','DUPLICATE_SCAN','ASSISTANCE','RECOMMENDATION']::text[])
  ),
  constraint m26_result_status_chk check (
    status = any (array['DRAFT','PENDING_REVIEW','APPROVED','REJECTED','ARCHIVED']::text[])
  ),
  constraint m26_result_summary_chk check (char_length(trim(summary)) between 5 and 2000),
  constraint m26_result_model_name_chk check (char_length(trim(model_name)) between 2 and 80),
  constraint m26_result_model_version_chk check (char_length(trim(model_version)) between 1 and 40),
  constraint m26_result_source_module_chk check (char_length(trim(source_module)) between 2 and 32),
  constraint m26_result_reason_codes_chk check (jsonb_typeof(reason_codes) = 'array')
);

create unique index if not exists m26_ai_results_job_uq
  on public.m26_ai_results (job_id);

create index if not exists m26_ai_results_owner_status_idx
  on public.m26_ai_results (owner_user_id, status, updated_at desc);

create index if not exists m26_ai_results_pending_review_idx
  on public.m26_ai_results (status, created_at desc)
  where status = 'PENDING_REVIEW';

create table if not exists public.m26_human_reviews (
  id uuid primary key default gen_random_uuid(),
  result_id uuid not null references public.m26_ai_results(id) on delete restrict,
  reviewer_user_id uuid not null references public.users(id) on delete restrict,
  decision text not null,
  public_reason text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m26_human_review_decision_chk check (
    decision = any (array['APPROVED','REJECTED','ARCHIVE']::text[])
  ),
  constraint m26_human_review_reason_len_chk check (
    public_reason is null or char_length(trim(public_reason)) between 1 and 500
  )
);

create index if not exists m26_human_reviews_result_idx
  on public.m26_human_reviews (result_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 2. Par canónico en duplicados (072)
-- ---------------------------------------------------------------------------

create or replace function public.m26_canonical_duplicate_key(p_label_a text, p_label_b text)
returns text language sql immutable as $$
  select least(lower(trim(coalesce(p_label_a, ''))), lower(trim(coalesce(p_label_b, ''))))
    || '|' ||
         greatest(lower(trim(coalesce(p_label_a, ''))), lower(trim(coalesce(p_label_b, ''))));
$$;

alter table public.m26_duplicate_candidates
  add column if not exists canonical_pair_key text;

update public.m26_duplicate_candidates
set canonical_pair_key = public.m26_canonical_duplicate_key(primary_label, duplicate_label)
where canonical_pair_key is null;

alter table public.m26_duplicate_candidates
  alter column canonical_pair_key set not null;

create unique index if not exists m26_dup_canonical_open_uq
  on public.m26_duplicate_candidates (owner_user_id, canonical_pair_key)
  where status = 'OPEN';

create or replace function public._m26_dup_set_canonical_key()
returns trigger language plpgsql as $$
begin
  new.canonical_pair_key := public.m26_canonical_duplicate_key(new.primary_label, new.duplicate_label);
  return new;
end;
$$;

drop trigger if exists trg_m26_dup_canonical_key on public.m26_duplicate_candidates;
create trigger trg_m26_dup_canonical_key
  before insert or update of primary_label, duplicate_label on public.m26_duplicate_candidates
  for each row execute function public._m26_dup_set_canonical_key();

-- ---------------------------------------------------------------------------
-- 3. RLS deny-all y permisos tablas nuevas
-- ---------------------------------------------------------------------------

alter table public.m26_ai_jobs enable row level security;
alter table public.m26_ai_results enable row level security;
alter table public.m26_human_reviews enable row level security;

create policy m26_jobs_authenticated_deny on public.m26_ai_jobs for all to authenticated using (false) with check (false);
create policy m26_results_authenticated_deny on public.m26_ai_results for all to authenticated using (false) with check (false);
create policy m26_reviews_authenticated_deny on public.m26_human_reviews for all to authenticated using (false) with check (false);

revoke all on table public.m26_ai_jobs from public, anon, authenticated;
revoke all on table public.m26_ai_results from public, anon, authenticated;
revoke all on table public.m26_human_reviews from public, anon, authenticated;

grant all on table public.m26_ai_jobs, public.m26_ai_results, public.m26_human_reviews to service_role;

-- Append-only: revisiones humanas inmutables para clientes
create or replace function public._m26_append_only_guard()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if current_user in ('authenticated', 'anon') then
    raise exception 'M26_IMMUTABLE';
  end if;
  if tg_op = 'DELETE' then return old; end if;
  return new;
end;
$$;

drop trigger if exists trg_m26_human_reviews_immutable on public.m26_human_reviews;
create trigger trg_m26_human_reviews_immutable
  before update or delete on public.m26_human_reviews
  for each row execute function public._m26_append_only_guard();

-- ---------------------------------------------------------------------------
-- 4. Helpers internos
-- ---------------------------------------------------------------------------

create or replace function public._m26_is_reviewer(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_user is not null and (
    public.user_has_active_role(p_user, 'MODERATOR')
    or public.user_has_active_role(p_user, 'ADMIN')
    or public.user_has_active_role(p_user, 'SUPERADMIN')
  );
$$;

create or replace function public._m26_scrub_public_text(p_text text)
returns text language sql immutable as $$
  select trim(
    regexp_replace(
      regexp_replace(
        regexp_replace(
          regexp_replace(
            regexp_replace(
              coalesce(p_text, ''),
              '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}', '[redactado]', 'gi'
            ),
            '(\+?\d[\d\s().-]{6,}\d)', '[redactado]', 'gi'
          ),
          '(calle|av\.?|avenida|pasaje)\s+[\w\s\d]+', '[redactado]', 'gi'
        ),
        '(dni|cuil|cuit|documento)\s*[:#]?\s*[\w\d-]+', '[redactado]', 'gi'
      ),
      '(user[_-]?id|owner[_-]?id|requester)\s*=\s*\S+', '[redactado]', 'gi'
    )
  );
$$;

create or replace function public._m26_stub_model()
returns table(model_name text, model_version text) language sql immutable as $$
  select 'leover-stub'::text, '1.0.0'::text;
$$;

create or replace function public._m26_default_reason_codes(p_job_type text)
returns jsonb language sql immutable as $$
  select case upper(coalesce(p_job_type, ''))
    when 'VISUAL_MATCH' then '[
      {"code":"SIMILAR_COLOR_PATTERN","public_explanation":"Patrón de color similar"},
      {"code":"SIMILAR_BODY_SHAPE","public_explanation":"Forma corporal aproximada"}
    ]'::jsonb
    when 'DUPLICATE_SCAN' then '[
      {"code":"SHARED_PUBLIC_ATTRIBUTES","public_explanation":"Atributos públicos compartidos"}
    ]'::jsonb
    when 'ASSISTANCE' then '[
      {"code":"RECENT_RELEVANCE","public_explanation":"Contexto reciente del usuario"}
    ]'::jsonb
    when 'RECOMMENDATION' then '[
      {"code":"USER_SELECTED_PREFERENCE","public_explanation":"Preferencias declaradas"}
    ]'::jsonb
    else '[]'::jsonb
  end;
$$;

create or replace function public._m26_initial_result_status(p_job_type text)
returns text language sql immutable as $$
  select case upper(coalesce(p_job_type, ''))
    when 'ASSISTANCE' then 'DRAFT'
    else 'PENDING_REVIEW'
  end;
$$;

create or replace function public._m26_build_result_summary(p_job_type text, p_payload text)
returns text language sql immutable as $$
  select case upper(coalesce(p_job_type, ''))
    when 'VISUAL_MATCH' then 'Posible coincidencia visual (estimación): ' || replace(trim(p_payload), '|', ' ↔ ')
    when 'DUPLICATE_SCAN' then 'Candidato de duplicado detectado: ' || replace(trim(p_payload), '|', ' / ')
    when 'ASSISTANCE' then 'Asistencia no autoritativa generada.'
    when 'RECOMMENDATION' then 'Recomendación sugerida: ' || left(trim(p_payload), 120)
    else left(trim(p_payload), 200)
  end;
$$;

create or replace function public._m26_validate_job_payload(p_payload text, p_job_type text)
returns void language plpgsql immutable as $$
declare
  v_payload text := trim(coalesce(p_payload, ''));
  v_type text := upper(trim(coalesce(p_job_type, '')));
begin
  if v_type not in ('VISUAL_MATCH','DUPLICATE_SCAN','ASSISTANCE','RECOMMENDATION') then
    raise exception 'M26_INVALID_JOB';
  end if;
  if char_length(v_payload) not between 3 and 500 then
    raise exception 'M26_INVALID_JOB';
  end if;
  if v_payload ~* '(<script|javascript:|on\w+\s*=|<iframe)' then
    raise exception 'M26_INVALID_JOB';
  end if;
  if v_type = 'ASSISTANCE'
    and v_payload ~* '(diagnóstico|diagnostico|prescri|dosis|medicament|urgencia clínica|eutanasia automática)' then
    raise exception 'M26_ASSISTANCE_NOT_AUTHORITATIVE';
  end if;
  if v_type = 'VISUAL_MATCH' and position('|' in v_payload) = 0 then
    raise exception 'M26_INVALID_MATCH';
  end if;
  if v_type = 'DUPLICATE_SCAN' and position('|' in v_payload) = 0 then
    raise exception 'M26_INVALID_DUPLICATE';
  end if;
end;
$$;

create or replace function public._m26_job_json(j public.m26_ai_jobs)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', j.id,
    'owner_user_id', j.owner_user_id,
    'job_type', j.job_type,
    'status', j.status,
    'client_request_id', j.client_request_id,
    'model_name', j.model_name,
    'model_version', j.model_version,
    'error_code', j.error_code,
    'created_at', j.created_at,
    'updated_at', j.updated_at,
    'completed_at', j.completed_at
  );
$$;

create or replace function public._m26_result_json(r public.m26_ai_results)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', r.id,
    'job_id', r.job_id,
    'owner_user_id', r.owner_user_id,
    'result_type', r.result_type,
    'status', r.status,
    'summary', r.summary,
    'reason_codes', r.reason_codes,
    'model_name', r.model_name,
    'model_version', r.model_version,
    'source_module', r.source_module,
    'provenance_job_id', r.provenance_job_id,
    'created_at', r.created_at,
    'updated_at', r.updated_at
  );
$$;

create or replace function public._m26_public_ai_result_json(r public.m26_ai_results)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'summary', public._m26_scrub_public_text(r.summary),
    'result_type', r.result_type,
    'status', r.status,
    'reason_codes', coalesce((
      select jsonb_agg(elem ->> 'public_explanation' order by ordinality)
      from jsonb_array_elements(r.reason_codes) with ordinality as t(elem, ordinality)
    ), '[]'::jsonb),
    'model_name', r.model_name,
    'model_version', r.model_version,
    'is_estimate', r.status <> 'APPROVED'
  );
$$;

create or replace function public._m26_review_queue_json(r public.m26_ai_results)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'result_id', r.id,
    'summary', public._m26_scrub_public_text(r.summary),
    'result_type', r.result_type,
    'status', r.status,
    'model_version', r.model_version
  );
$$;

create or replace function public._m26_stub_create_artifacts(
  p_job public.m26_ai_jobs,
  p_payload text
) returns void language plpgsql security definer set search_path = public as $$
declare
  v_parts text[];
  v_source text;
  v_target text;
  v_score numeric := 0.75;
  v_dup_score numeric := 0.82;
  v_canonical text;
  v_title text;
begin
  v_parts := string_to_array(trim(p_payload), '|');
  case upper(p_job.job_type)
    when 'VISUAL_MATCH' then
      if array_length(v_parts, 1) >= 2 then
        v_source := trim(v_parts[1]);
        v_target := trim(v_parts[2]);
        if char_length(v_source) between 2 and 120
          and char_length(v_target) between 2 and 120
          and lower(v_source) <> lower(v_target) then
          insert into public.m26_visual_match_suggestions (
            requester_user_id, source_label, target_label, score, confidence_band
          ) values (
            p_job.owner_user_id, v_source, v_target, v_score, public._m26_confidence_band(v_score)
          );
        end if;
      end if;
    when 'DUPLICATE_SCAN' then
      if array_length(v_parts, 1) >= 2 then
        v_source := trim(v_parts[1]);
        v_target := trim(v_parts[2]);
        v_canonical := public.m26_canonical_duplicate_key(v_source, v_target);
        if char_length(v_source) between 2 and 120
          and char_length(v_target) between 2 and 120
          and not exists (
            select 1 from public.m26_duplicate_candidates d
            where d.owner_user_id = p_job.owner_user_id
              and d.canonical_pair_key = v_canonical
              and d.status = 'OPEN'
          ) then
          begin
            insert into public.m26_duplicate_candidates (
              owner_user_id, primary_label, duplicate_label, similarity_score, canonical_pair_key
            ) values (
              p_job.owner_user_id, v_source, v_target, v_dup_score, v_canonical
            );
          exception
            when unique_violation then
              null;
          end;
        end if;
      end if;
    when 'ASSISTANCE' then
      insert into public.m26_assistance_sessions (user_id, topic, summary)
      values (
        p_job.owner_user_id,
        'GENERAL',
        'Sesión stub: ' || left(trim(p_payload), 200)
      );
    when 'RECOMMENDATION' then
      v_title := left(trim(p_payload), 80);
      if char_length(v_title) < 3 then
        v_title := 'Sugerencia generada';
      end if;
      insert into public.m26_evaluated_recommendations (
        subject_user_id, kind, title, rationale, status
      ) values (
        p_job.owner_user_id,
        'CONTENT',
        v_title,
        'Sugerencia automática pendiente de revisión humana.',
        'PENDING_REVIEW'
      );
    else
      null;
  end case;
end;
$$;

create or replace function public._m26_sync_recommendation_approval(p_result public.m26_ai_results)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_title text := left(trim(replace(p_result.summary, 'Recomendación sugerida: ', '')), 80);
begin
  update public.m26_evaluated_recommendations r set
    human_reviewed = true,
    status = 'APPROVED',
    updated_at = timezone('utc', now())
  where r.subject_user_id = p_result.owner_user_id
    and r.title = v_title
    and r.status in ('DRAFT', 'PENDING_REVIEW');
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. RPCs operativos Bloque 4
-- ---------------------------------------------------------------------------

create or replace function public.m26_request_ai_job(
  p_job_type text,
  p_payload_summary text,
  p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m26_require_authenticated();
  v_payload text := trim(coalesce(p_payload_summary, ''));
  v_type text := upper(trim(coalesce(p_job_type, '')));
  v_client_request_id text := nullif(trim(coalesce(p_client_request_id, '')), '');
  v_model_name text;
  v_model_version text;
  v_job public.m26_ai_jobs;
  v_result public.m26_ai_results;
  v_now timestamptz := timezone('utc', now());
begin
  perform public._m26_validate_job_payload(v_payload, v_type);

  if v_client_request_id is not null then
    select * into v_job
    from public.m26_ai_jobs
    where owner_user_id = v_actor and client_request_id = v_client_request_id;
    if found then
      return public._m26_job_json(v_job);
    end if;
  end if;

  select s.model_name, s.model_version into v_model_name, v_model_version
  from public._m26_stub_model() s;

  insert into public.m26_ai_jobs (
    owner_user_id, job_type, status, client_request_id, model_name, model_version
  ) values (
    v_actor, v_type, 'QUEUED', v_client_request_id, v_model_name, v_model_version
  ) returning * into v_job;

  update public.m26_ai_jobs set
    status = 'RUNNING',
    updated_at = v_now
  where id = v_job.id
  returning * into v_job;

  insert into public.m26_ai_results (
    job_id, owner_user_id, result_type, status, summary, reason_codes,
    model_name, model_version, source_module, provenance_job_id
  ) values (
    v_job.id,
    v_actor,
    v_type,
    public._m26_initial_result_status(v_type),
    public._m26_build_result_summary(v_type, v_payload),
    public._m26_default_reason_codes(v_type),
    v_model_name,
    v_model_version,
    'M26',
    v_job.id
  ) returning * into v_result;

  perform public._m26_stub_create_artifacts(v_job, v_payload);

  update public.m26_ai_jobs set
    status = 'COMPLETED',
    updated_at = timezone('utc', now()),
    completed_at = timezone('utc', now())
  where id = v_job.id
  returning * into v_job;

  return public._m26_job_json(v_job);
exception
  when unique_violation then
    if v_client_request_id is not null then
      select * into v_job
      from public.m26_ai_jobs
      where owner_user_id = v_actor and client_request_id = v_client_request_id;
      if found then
        return public._m26_job_json(v_job);
      end if;
    end if;
    raise;
end;
$$;

create or replace function public.m26_cancel_ai_job(p_job_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m26_require_authenticated();
  v_job public.m26_ai_jobs;
begin
  select * into v_job from public.m26_ai_jobs where id = p_job_id for update;
  if not found then raise exception 'M26_JOB_NOT_FOUND'; end if;
  if v_job.owner_user_id <> v_actor then raise exception 'M26_PERMISSION_DENIED'; end if;
  if v_job.status = 'CANCELLED' then return public._m26_job_json(v_job); end if;
  if v_job.status in ('COMPLETED', 'FAILED', 'EXPIRED') then raise exception 'M26_JOB_TERMINAL'; end if;
  if v_job.status not in ('QUEUED', 'RUNNING') then raise exception 'M26_INVALID_JOB_TRANSITION'; end if;

  update public.m26_ai_jobs set
    status = 'CANCELLED',
    updated_at = timezone('utc', now())
  where id = p_job_id
  returning * into v_job;

  return public._m26_job_json(v_job);
end;
$$;

create or replace function public.m26_list_my_jobs()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m26_job_json(j)
  from public.m26_ai_jobs j
  where j.owner_user_id = public._m26_require_authenticated()
  order by j.created_at desc;
$$;

create or replace function public.m26_list_my_results()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m26_public_ai_result_json(r)
  from public.m26_ai_results r
  where r.owner_user_id = public._m26_require_authenticated()
  order by r.created_at desc;
$$;

create or replace function public.m26_list_review_queue()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m26_review_queue_json(r)
  from public.m26_ai_results r
  where r.status = 'PENDING_REVIEW'
    and public._m26_is_reviewer(public._m26_require_authenticated())
  order by r.created_at asc;
$$;

create or replace function public.m26_submit_result_for_review(p_result_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m26_require_authenticated();
  v_result public.m26_ai_results;
begin
  select * into v_result from public.m26_ai_results where id = p_result_id for update;
  if not found then raise exception 'M26_RESULT_NOT_FOUND'; end if;
  if v_result.owner_user_id <> v_actor then raise exception 'M26_PERMISSION_DENIED'; end if;
  if v_result.status = 'PENDING_REVIEW' then return public._m26_result_json(v_result); end if;
  if v_result.status <> 'DRAFT' then raise exception 'M26_INVALID_RESULT_TRANSITION'; end if;

  update public.m26_ai_results set
    status = 'PENDING_REVIEW',
    updated_at = timezone('utc', now())
  where id = p_result_id and status = 'DRAFT'
  returning * into v_result;

  if not found then raise exception 'M26_INVALID_RESULT_TRANSITION'; end if;
  return public._m26_result_json(v_result);
end;
$$;

create or replace function public.m26_review_ai_result(
  p_result_id uuid,
  p_decision text,
  p_public_reason text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m26_require_authenticated();
  v_decision text := upper(trim(coalesce(p_decision, '')));
  v_target_status text;
  v_result public.m26_ai_results;
  v_existing public.m26_ai_results;
  v_public_reason text := nullif(trim(coalesce(p_public_reason, '')), '');
begin
  if not public._m26_is_reviewer(v_actor) then raise exception 'M26_PERMISSION_DENIED'; end if;
  if v_decision not in ('APPROVED', 'REJECTED', 'ARCHIVE') then raise exception 'M26_INVALID_REVIEW_DECISION'; end if;

  v_target_status := case v_decision
    when 'APPROVED' then 'APPROVED'
    when 'REJECTED' then 'REJECTED'
    when 'ARCHIVE' then 'ARCHIVED'
  end;

  select * into v_result from public.m26_ai_results where id = p_result_id for update;
  if not found then raise exception 'M26_RESULT_NOT_FOUND'; end if;

  if v_result.status = v_target_status then
    return public._m26_result_json(v_result);
  end if;

  if v_result.status = 'APPROVED' and v_decision = 'APPROVED' then
    return public._m26_result_json(v_result);
  end if;
  if v_result.status = 'REJECTED' and v_decision = 'REJECTED' then
    return public._m26_result_json(v_result);
  end if;

  if v_result.status = 'PENDING_REVIEW' then
    update public.m26_ai_results set
      status = v_target_status,
      updated_at = timezone('utc', now())
    where id = p_result_id and status = 'PENDING_REVIEW'
    returning * into v_existing;

    if not found then
      select * into v_result from public.m26_ai_results where id = p_result_id;
      if v_result.status = v_target_status then
        return public._m26_result_json(v_result);
      end if;
      raise exception 'M26_INVALID_RESULT_TRANSITION';
    end if;

    insert into public.m26_human_reviews (result_id, reviewer_user_id, decision, public_reason)
    values (p_result_id, v_actor, v_decision, v_public_reason);

    if v_existing.result_type = 'RECOMMENDATION' and v_target_status = 'APPROVED' then
      perform public._m26_sync_recommendation_approval(v_existing);
    end if;

    return public._m26_result_json(v_existing);
  end if;

  if v_result.status in ('APPROVED', 'REJECTED') and v_target_status = 'ARCHIVED' then
    update public.m26_ai_results set
      status = 'ARCHIVED',
      updated_at = timezone('utc', now())
    where id = p_result_id and status in ('APPROVED', 'REJECTED')
    returning * into v_existing;

    if not found then raise exception 'M26_INVALID_RESULT_TRANSITION'; end if;

    insert into public.m26_human_reviews (result_id, reviewer_user_id, decision, public_reason)
    values (p_result_id, v_actor, v_decision, v_public_reason);

    return public._m26_result_json(v_existing);
  end if;

  raise exception 'M26_INVALID_RESULT_TRANSITION';
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Endurecer revisión de recomendaciones 072
-- ---------------------------------------------------------------------------

create or replace function public.m26_review_recommendation(
  p_recommendation_id uuid, p_approved boolean, p_reviewer_note text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m26_require_authenticated(); v_rec public.m26_evaluated_recommendations;
begin
  if not public._m26_is_reviewer(v_actor) then raise exception 'M26_PERMISSION_DENIED'; end if;
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

-- ---------------------------------------------------------------------------
-- 7. Permisos funciones
-- ---------------------------------------------------------------------------

revoke all on function public.m26_canonical_duplicate_key(text, text) from public, anon, authenticated;
revoke all on function public._m26_dup_set_canonical_key() from public, anon, authenticated;
revoke all on function public._m26_append_only_guard() from public, anon, authenticated;
revoke all on function public._m26_is_reviewer(uuid) from public, anon, authenticated;
revoke all on function public._m26_scrub_public_text(text) from public, anon, authenticated;
revoke all on function public._m26_stub_model() from public, anon, authenticated;
revoke all on function public._m26_default_reason_codes(text) from public, anon, authenticated;
revoke all on function public._m26_initial_result_status(text) from public, anon, authenticated;
revoke all on function public._m26_build_result_summary(text, text) from public, anon, authenticated;
revoke all on function public._m26_validate_job_payload(text, text) from public, anon, authenticated;
revoke all on function public._m26_job_json(public.m26_ai_jobs) from public, anon, authenticated;
revoke all on function public._m26_result_json(public.m26_ai_results) from public, anon, authenticated;
revoke all on function public._m26_public_ai_result_json(public.m26_ai_results) from public, anon, authenticated;
revoke all on function public._m26_review_queue_json(public.m26_ai_results) from public, anon, authenticated;
revoke all on function public._m26_stub_create_artifacts(public.m26_ai_jobs, text) from public, anon, authenticated;
revoke all on function public._m26_sync_recommendation_approval(public.m26_ai_results) from public, anon, authenticated;

revoke all on function public.m26_request_ai_job(text, text, text) from public, anon;
revoke all on function public.m26_cancel_ai_job(uuid) from public, anon;
revoke all on function public.m26_list_my_jobs() from public, anon;
revoke all on function public.m26_list_my_results() from public, anon;
revoke all on function public.m26_list_review_queue() from public, anon;
revoke all on function public.m26_submit_result_for_review(uuid) from public, anon;
revoke all on function public.m26_review_ai_result(uuid, text, text) from public, anon;

grant execute on function public.m26_request_ai_job(text, text, text) to authenticated;
grant execute on function public.m26_cancel_ai_job(uuid) to authenticated;
grant execute on function public.m26_list_my_jobs() to authenticated;
grant execute on function public.m26_list_my_results() to authenticated;
grant execute on function public.m26_list_review_queue() to authenticated;
grant execute on function public.m26_submit_result_for_review(uuid) to authenticated;
grant execute on function public.m26_review_ai_result(uuid, text, text) to authenticated;

comment on table public.m26_ai_jobs is 'M26 Bloque 4: orquestación stub de jobs IA; acceso vía RPC SECURITY DEFINER.';
comment on table public.m26_ai_results is 'M26 Bloque 4: resultados IA con reason codes; proyecciones públicas sin PII.';
comment on table public.m26_human_reviews is 'M26 Bloque 4: historial append-only de revisiones humanas M26 (distinto de M04).';

commit;
