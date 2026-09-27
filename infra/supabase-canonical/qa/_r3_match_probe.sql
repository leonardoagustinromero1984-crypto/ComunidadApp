begin;

create temporary table qa_match_probe (
  stage text,
  sqlstate text,
  message text,
  detail text,
  hint text,
  extra text
) on commit drop;

do $$
declare
  v_found uuid := '411e5c79-0f9c-4068-923f-996c78b5cb16';
  v_lost uuid := '2b69711e-4b92-44a9-bfa2-13b07d329bb4';
  v_score numeric;
  v_meters double precision;
  v_count integer;
  v_detail text;
  v_hint text;
  v_fp public.pets%rowtype;
  v_lp public.pets%rowtype;
  v_m06 text;
begin
  select p.* into v_fp from public.pets p
    join public.lost_found_alerts a on a.pet_id = p.id where a.id = v_found;
  select p.* into v_lp from public.pets p
    join public.lost_found_alerts a on a.pet_id = p.id where a.id = v_lost;

  insert into qa_match_probe(stage, extra)
  values (
    'PETS',
    format('found_pet=%s name=%s species=%s sex=%s size=%s origin=%s | lost_pet=%s name=%s species=%s sex=%s size=%s origin=%s',
      v_fp.id, v_fp.name, v_fp.species_code, v_fp.sex, v_fp.size, v_fp.origin_kind,
      v_lp.id, v_lp.name, v_lp.species_code, v_lp.sex, v_lp.size, v_lp.origin_kind)
  );

  begin
    select ST_Distance(f.precise_location, l.precise_location)
      into v_meters
      from public.lost_found_alerts f, public.lost_found_alerts l
     where f.id = v_found and l.id = v_lost;
    insert into qa_match_probe(stage, extra) values ('ST_DISTANCE_PUBLIC', coalesce(v_meters::text, 'NULL'));
  exception when others then
    insert into qa_match_probe(stage, sqlstate, message, hint)
    values ('ST_DISTANCE_PUBLIC_FAIL', SQLSTATE, SQLERRM, 'search_path default');
  end;

  begin
    select extensions.ST_Distance(f.precise_location, l.precise_location)
      into v_meters
      from public.lost_found_alerts f, public.lost_found_alerts l
     where f.id = v_found and l.id = v_lost;
    insert into qa_match_probe(stage, extra) values ('ST_DISTANCE_EXT', coalesce(v_meters::text, 'NULL'));
  exception when others then
    insert into qa_match_probe(stage, sqlstate, message)
    values ('ST_DISTANCE_EXT_FAIL', SQLSTATE, SQLERRM);
  end;

  select to_regprocedure(
    'public.m06_emit_domain_notification(uuid,text,text,text,text,text,text,text,text,text,text,uuid,text,text,text,text,jsonb,text,text,timestamptz,boolean)'
  )::text into v_m06;
  insert into qa_match_probe(stage, extra) values ('M06_FN', coalesce(v_m06, 'MISSING'));

  begin
    v_count := public._canon_match_found_to_lost(v_found);
    insert into qa_match_probe(stage, extra) values ('MATCH_OK', v_count::text);
  exception when others then
    get stacked diagnostics v_detail = pg_exception_detail, v_hint = pg_exception_hint;
    insert into qa_match_probe(stage, sqlstate, message, detail, hint)
    values ('MATCH_FAIL', SQLSTATE, SQLERRM, v_detail, v_hint);
  end;
end $$;

select * from qa_match_probe;
select count(*) as candidates_after_probe from public.lost_found_match_candidates;
select count(*) as notifications_after_probe from public.notifications;

rollback;
