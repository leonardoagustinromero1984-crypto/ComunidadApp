-- Independent rescuer ACL + cleanup. Does not reset sequence.
drop table if exists public._qa_rescuer_result;
create table public._qa_rescuer_result (
  unverified text, verified text, spoof text, leftover_jobs int
);

do $$
declare
  v_person uuid := 'caa259b6-1b4a-4494-9417-8998ad2ad5ba';
  v_other uuid := 'd9059eba-510c-43d3-807a-a07e202f4ef1';
  v_unverified text; v_verified text; v_spoof text;
begin
  perform set_config('request.jwt.claim.sub', v_other::text, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_other, 'role', 'authenticated')::text, true);

  delete from public.person_capabilities where user_id = v_other and capability = 'RESCUER';
  begin
    perform public.canon_import_create_job(
      null, 'SELF_SERVICE', 'rescuer/' || v_other::text || '/qa-rescuer.xlsx', null, 'rescuer-unverified',
      'INDEPENDENT_RESCUER', null
    );
    v_unverified := 'ALLOWED';
  exception when others then v_unverified := 'DENIED';
  end;

  insert into public.person_capabilities (user_id, capability, active, verification_status, verified_at)
  values (v_other, 'RESCUER', true, 'VERIFIED', timezone('utc', now()));
  begin
    perform public.canon_import_create_job(
      null, 'SELF_SERVICE', 'rescuer/' || v_other::text || '/qa-rescuer-ok.xlsx', null, 'rescuer-ok',
      'INDEPENDENT_RESCUER', null
    );
    v_verified := 'ALLOWED';
  exception when others then v_verified := sqlerrm;
  end;

  begin
    perform public.canon_import_create_job(
      null, 'SELF_SERVICE', 'rescuer/' || v_other::text || '/qa-rescuer-spoof.xlsx', null, 'rescuer-spoof',
      'INDEPENDENT_RESCUER', v_person
    );
    v_spoof := 'ALLOWED';
  exception when others then v_spoof := 'DENIED';
  end;

  delete from public.vitacora_import_jobs
   where coalesce(comment,'') in ('rescuer-unverified','rescuer-ok','rescuer-spoof')
      or storage_path like '%qa-rescuer%';
  delete from public.person_capabilities where user_id = v_other and capability = 'RESCUER';

  insert into public._qa_rescuer_result values (
    v_unverified, v_verified, v_spoof,
    (select count(*) from public.vitacora_import_jobs where storage_path like '%qa-rescuer%')
  );
end;
$$;

select * from public._qa_rescuer_result;
drop table if exists public._qa_rescuer_result;
