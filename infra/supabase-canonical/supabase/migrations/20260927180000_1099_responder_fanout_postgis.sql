-- 1099: responder fanout must resolve PostGIS the same way matching does.
-- Live definition was 1088: search_path = public and unqualified ST_Distance.
-- 1095 proved unqualified PostGIS under search_path=public raises 42883
-- (function does not exist). 1096 fixed _canon_match_found_to_lost with
-- search_path = public, extensions and extensions.ST_Distance.
-- Fanout was not replaced by 1095–1098, so FOUND create still called the
-- 1088 body. Eligibility, wave size, ordering, and the 15 minute cadence
-- are unchanged.
-- Direct callee _canon_emit_lost_found_notice (1086) uses no PostGIS.
-- canon_create_lost_found (1096) still swallows fanout errors on purpose,
-- the same isolation used for match and audit. This migration does not add
-- logging. A swallowed failure still writes no recipient row, so can_claim
-- stays false.
-- Does not edit 1098 or earlier. Execute privileges stay revoked.

create or replace function public._canon_fanout_lost_found_recipients(p_alert_id uuid)
returns integer
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_alert public.lost_found_alerts%rowtype;
  v_count integer := 0;
  v_wave integer;
  r record;
begin
  select * into v_alert from public.lost_found_alerts where id = p_alert_id;
  if not found or v_alert.precise_location is null then
    return 0;
  end if;
  if v_alert.fanout_stopped_at is not null or v_alert.status <> 'OPEN' then
    return 0;
  end if;
  v_wave := coalesce(v_alert.wave_count, 0) + 1;

  for r in
    with candidates as (
      select
        'PERSON'::text as holder_kind,
        p.user_id as person_id,
        null::uuid as organization_id,
        p.base_location as loc
      from public.persons p
      join public.person_capabilities c on c.user_id = p.user_id
      where c.capability = 'RESCUER'
        and c.active
        and c.verification_status = 'VERIFIED'
        and coalesce(p.receive_nearby_cases, false)
        and p.base_location is not null
        and p.lifecycle_status = 'ACTIVE'
        and p.user_id is distinct from v_alert.created_by
        and not exists (
          select 1 from public.person_capabilities x
           where x.user_id = p.user_id and x.capability = 'RESCUER'
             and x.verification_status = 'SUSPENDED'
        )
      union all
      select 'ORGANIZATION', null, o.id, o.base_location
      from public.organizations o
      join public.organization_capabilities oc on oc.organization_id = o.id
      where oc.capability in ('SHELTER', 'NGO')
        and o.lifecycle_status = 'ACTIVE'
        and o.verification_status = 'VERIFIED'
        and coalesce(o.receive_nearby_cases, false)
        and o.base_location is not null
    )
    select holder_kind, person_id, organization_id,
           extensions.ST_Distance(loc, v_alert.precise_location) as meters
      from candidates c
     where not exists (
       select 1 from public.lost_found_alert_recipients rec
        where rec.alert_id = p_alert_id
          and (
            (c.person_id is not null and rec.responder_person_id = c.person_id)
            or (c.organization_id is not null and rec.responder_organization_id = c.organization_id)
          )
     )
     order by meters
     limit 10
  loop
    v_count := v_count + 1;
    insert into public.lost_found_alert_recipients (
      alert_id, responder_person_id, responder_organization_id, rank, wave, distance_meters, notified_at, status
    ) values (
      p_alert_id, r.person_id, r.organization_id, v_count, v_wave, r.meters, timezone('utc', now()), 'NOTIFIED'
    )
    on conflict do nothing;
    if r.person_id is not null then
      perform public._canon_emit_lost_found_notice(r.person_id, p_alert_id, v_alert.kind);
    else
      perform public._canon_emit_lost_found_notice(m.person_id, p_alert_id, v_alert.kind)
        from public.organization_memberships m
       where m.organization_id = r.organization_id
         and m.status = 'ACTIVE'
         and m.person_id is distinct from v_alert.created_by;
    end if;
  end loop;

  if v_count > 0 then
    update public.lost_found_alerts
       set wave_count = v_wave,
           last_wave_at = timezone('utc', now()),
           next_wave_at = timezone('utc', now()) + interval '15 minutes',
           updated_at = timezone('utc', now())
     where id = p_alert_id;
  else
    update public.lost_found_alerts
       set fanout_exhausted_at = coalesce(fanout_exhausted_at, timezone('utc', now())),
           next_wave_at = null,
           updated_at = timezone('utc', now())
     where id = p_alert_id;
  end if;
  return v_count;
end;
$$;

revoke all on function public._canon_fanout_lost_found_recipients(uuid) from public, anon, authenticated;

comment on function public._canon_fanout_lost_found_recipients(uuid) is
  'Nearest responder wave. PostGIS ST_Distance is extensions-qualified. Eligibility unchanged from 1088.';
