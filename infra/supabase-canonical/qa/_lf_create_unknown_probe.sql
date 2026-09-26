-- Read-only probe: capture the real FOUND create exception, then roll back.
-- Not a migration. Do not apply as schema.

begin;

create temporary table qa_lf_probe (
  stage text,
  sqlstate text,
  message text,
  detail text,
  hint text,
  context text,
  result_id text
) on commit drop;

do $$
declare
  v_uid uuid;
  v_detail text;
  v_hint text;
  v_ctx text;
  v_pet uuid;
  v_alert uuid;
begin
  select p.user_id into v_uid
    from public.persons p
   where p.lifecycle_status = 'ACTIVE'
   order by p.updated_at desc nulls last
   limit 1;

  insert into qa_lf_probe(stage, result_id)
  values ('AUTH_UID', v_uid::text);

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_uid::text, 'role', 'authenticated')::text,
    true
  );

  insert into qa_lf_probe(stage, result_id)
  values ('AUTH_UID_AFTER', coalesce(auth.uid()::text, 'NULL'));

  begin
    v_pet := public._canon_create_found_identity(
      'QA-ROOT', 'DOG', 'UNKNOWN', 'UNKNOWN', null, null, null
    );
    insert into qa_lf_probe(stage, result_id)
    values ('IDENTITY_OK', v_pet::text);
  exception when others then
    get stacked diagnostics
      v_detail = pg_exception_detail,
      v_hint = pg_exception_hint,
      v_ctx = pg_exception_context;
    insert into qa_lf_probe(stage, sqlstate, message, detail, hint, context)
    values ('IDENTITY_FAIL', SQLSTATE, SQLERRM, v_detail, v_hint, v_ctx);
  end;

  begin
    v_alert := public.canon_create_lost_found(
      'FOUND',
      null,
      null,
      'DOG',
      'QA root cause',
      -34.6037,
      -58.3816,
      null,
      null,
      'QA-ROOT',
      'UNKNOWN',
      'UNKNOWN',
      null,
      null
    );
    insert into qa_lf_probe(stage, result_id)
    values ('CREATE_OK', v_alert::text);
  exception when others then
    get stacked diagnostics
      v_detail = pg_exception_detail,
      v_hint = pg_exception_hint,
      v_ctx = pg_exception_context;
    insert into qa_lf_probe(stage, sqlstate, message, detail, hint, context)
    values ('CREATE_FAIL', SQLSTATE, SQLERRM, v_detail, v_hint, v_ctx);
  end;
end $$;

select stage, sqlstate, message, detail, hint, left(context, 800) as context, result_id
  from qa_lf_probe
 order by stage;

-- Keep the probe transactional: never persist QA rows.
rollback;
