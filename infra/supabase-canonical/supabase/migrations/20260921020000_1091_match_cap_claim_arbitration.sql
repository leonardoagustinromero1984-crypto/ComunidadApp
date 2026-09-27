-- 1091: FOUND↔LOST matching threshold/cap + real 2s nearest-wins claim.
-- Does not edit 1090 or earlier.

alter table public.lost_found_alerts
  add column if not exists arbitration_started_at timestamptz null;

alter table public.lost_found_claim_attempts
  add column if not exists outcome text not null default 'PENDING';

alter table public.lost_found_claim_attempts
  drop constraint if exists lost_found_claim_attempts_outcome_check;
alter table public.lost_found_claim_attempts
  add constraint lost_found_claim_attempts_outcome_check
  check (outcome in ('PENDING', 'WON', 'NOT_SELECTED'));

comment on function public._canon_match_found_to_lost(uuid) is
  'V1 scoring: species hard. breed +0.12, sex +0.20, size +0.15, age<=6m +0.12 / <=18m +0.06, geo<=25km +0.15, time<=14d +0.10. Base 0.40. Notify only score>=0.55, cap 15, order score desc, distance asc nulls last, incident_at desc.';

create or replace function public._canon_match_found_to_lost(p_found_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_found public.lost_found_alerts%rowtype;
  v_found_pet public.pets%rowtype;
  v_found_age integer;
  v_count integer := 0;
  r record;
begin
  select * into v_found from public.lost_found_alerts where id = p_found_id and kind = 'FOUND';
  if not found or v_found.pet_id is null then
    return 0;
  end if;
  select * into v_found_pet from public.pets where id = v_found.pet_id;
  v_found_age := public._canon_pet_age_months(v_found_pet);

  for r in
    with scored as (
      select
        a.id as lost_id,
        a.created_by as lost_owner,
        a.incident_at,
        a.created_at,
        ST_Distance(v_found.precise_location, a.precise_location) as meters,
        (
          0.40
          + case
              when v_found_pet.breed_id is not null
               and p.breed_id is not null
               and v_found_pet.breed_id = p.breed_id
              then 0.12 else 0
            end
          + case
              when v_found_pet.sex is not null and v_found_pet.sex <> 'UNKNOWN'
               and p.sex is not null and p.sex <> 'UNKNOWN'
               and p.sex = v_found_pet.sex
              then 0.20 else 0
            end
          + case
              when v_found_pet.size is not null and v_found_pet.size <> 'UNKNOWN'
               and p.size is not null and p.size <> 'UNKNOWN'
               and p.size = v_found_pet.size
              then 0.15 else 0
            end
          + case
              when v_found_age is not null
               and public._canon_pet_age_months(p) is not null
               and abs(v_found_age - public._canon_pet_age_months(p)) <= 6
              then 0.12
              when v_found_age is not null
               and public._canon_pet_age_months(p) is not null
               and abs(v_found_age - public._canon_pet_age_months(p)) <= 18
              then 0.06
              else 0
            end
          + case
              when v_found.precise_location is not null
               and a.precise_location is not null
               and ST_Distance(v_found.precise_location, a.precise_location) <= 25000
              then 0.15 else 0
            end
          + case
              when coalesce(v_found.incident_at, v_found.created_at) is not null
               and coalesce(a.incident_at, a.created_at) is not null
               and abs(extract(epoch from (
                 coalesce(v_found.incident_at, v_found.created_at)
                 - coalesce(a.incident_at, a.created_at)
               ))) <= 14 * 24 * 3600
              then 0.10 else 0
            end
        ) as score
      from public.lost_found_alerts a
      join public.pets p on p.id = a.pet_id
     where a.kind = 'LOST'
       and a.status in ('OPEN', 'CLAIMED', 'IN_CARE')
       and a.id is distinct from p_found_id
       and p.species_code is not distinct from v_found_pet.species_code
    )
    select *
      from scored
     where score >= 0.55
     order by score desc, meters asc nulls last, coalesce(incident_at, created_at) desc
     limit 15
  loop
    insert into public.lost_found_match_candidates (alert_id, lost_alert_id, score, status, match_reason)
    values (p_found_id, r.lost_id, least(r.score, 0.9999), 'PENDING', 'BASIC_GEO_TIME')
    on conflict (alert_id, lost_alert_id) where lost_alert_id is not null and asserted_by is null
    do nothing;
    if found then
      v_count := v_count + 1;
      perform public._canon_emit_lf_event(
        r.lost_owner,
        p_found_id,
        'lost_found.match.candidate',
        'Posible coincidencia',
        'Encontraron un animal que podría coincidir con tu mascota.',
        'Ver coincidencia',
        'lost_found_match:' || p_found_id::text || ':' || r.lost_id::text || ':' || r.lost_owner::text
      );
    end if;
  end loop;
  return v_count;
end;
$$;

create or replace function public._canon_apply_lost_found_claim(p_id uuid, p_winner uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_alerts%rowtype;
  v_pet uuid;
  v_link uuid;
begin
  select * into v_row from public.lost_found_alerts where id = p_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.status = 'CLAIMED' and v_row.claimed_by = p_winner then
    return jsonb_build_object('id', p_id, 'status', 'CLAIMED', 'pet_id', v_row.pet_id, 'claimed_by', p_winner);
  end if;
  if v_row.status <> 'OPEN' then
    raise exception 'ALERT_ALREADY_CLAIMED';
  end if;
  v_pet := v_row.pet_id;
  if v_pet is null then raise exception 'NOT_FOUND'; end if;

  update public.pets
     set current_custodian_kind = 'PERSON',
         current_custodian_person_id = p_winner,
         current_custodian_organization_id = null,
         updated_at = timezone('utc', now())
   where id = v_pet;

  if not exists (
    select 1 from public.pet_responsibility_links l
     where l.pet_id = v_pet and l.holder_person_id = p_winner and l.status = 'ACTIVE'
  ) then
    insert into public.pet_responsibility_links (
      pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
    ) values (v_pet, 'PERSON', p_winner, 'AUTHORIZED', p_winner)
    returning id into v_link;
    insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
    select v_pet, v_link, p_winner, code, p_winner
      from public.permission_codes where scope in ('PET', 'VITACORA');
  end if;

  update public.lost_found_alerts
     set status = 'CLAIMED',
         claimed_by = p_winner,
         claimed_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = p_id
     and status = 'OPEN';
  if not found then
    raise exception 'ALERT_ALREADY_CLAIMED';
  end if;

  perform public._canon_stop_lost_found_fanout(p_id);
  update public.lost_found_alert_recipients
     set status = case
       when responder_person_id = p_winner then 'CLAIMED'
       when status in ('NOTIFIED', 'VIEWED') then 'CANCELLED_CASE_CLAIMED'
       else status
     end
   where alert_id = p_id;

  update public.lost_found_claim_attempts
     set outcome = case when responder_person_id = p_winner then 'WON' else 'NOT_SELECTED' end
   where alert_id = p_id
     and outcome = 'PENDING';

  perform public.canon_audit(
    'lost_found.claim',
    'lost_found_alerts',
    p_id,
    jsonb_build_object('pet_id', v_pet, 'claimed_by', p_winner, 'arbitration', '2s')
  );

  return jsonb_build_object(
    'id', p_id,
    'status', 'CLAIMED',
    'pet_id', v_pet,
    'claimed_by', p_winner
  );
end;
$$;

create or replace function public.canon_register_lost_found_claim_attempt(p_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_dist numeric;
  v_id uuid;
  v_row public.lost_found_alerts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._canon_alert_responder_eligible(auth.uid()) then
    raise exception 'FORBIDDEN';
  end if;
  select * into v_row from public.lost_found_alerts where id = p_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.kind <> 'FOUND' then raise exception 'CLAIM_FOUND_ONLY'; end if;
  if v_row.status <> 'OPEN' then raise exception 'ALERT_ALREADY_CLAIMED'; end if;

  select min(r.distance_meters) into v_dist
    from public.lost_found_alert_recipients r
   where r.alert_id = p_id
     and r.status in ('NOTIFIED', 'VIEWED', 'CLAIMED')
     and (
       r.responder_person_id = auth.uid()
       or r.responder_organization_id in (
         select m.organization_id from public.organization_memberships m
          where m.person_id = auth.uid() and m.status = 'ACTIVE'
       )
     );
  if v_dist is null then raise exception 'FORBIDDEN'; end if;

  insert into public.lost_found_claim_attempts (alert_id, responder_person_id, distance_meters, outcome)
  values (p_id, auth.uid(), v_dist, 'PENDING')
  on conflict (alert_id, responder_person_id)
  do update set distance_meters = excluded.distance_meters
  returning id into v_id;

  update public.lost_found_alerts
     set arbitration_started_at = coalesce(arbitration_started_at, timezone('utc', now())),
         updated_at = timezone('utc', now())
   where id = p_id
     and status = 'OPEN';

  return v_id;
end;
$$;

-- Synchronous 2s remaining wait, then nearest-wins. Holds the DB session ~2s.
-- Documented: statement timeout must be > 3s. No client-side winner.
create or replace function public.canon_claim_lost_found(p_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_alerts%rowtype;
  v_winner uuid;
  v_close timestamptz;
  v_wait numeric;
begin
  perform public.canon_register_lost_found_claim_attempt(p_id);

  select * into v_row from public.lost_found_alerts where id = p_id;
  if v_row.status = 'CLAIMED' then
    if v_row.claimed_by = auth.uid() then
      return jsonb_build_object('id', p_id, 'status', 'CLAIMED', 'pet_id', v_row.pet_id, 'claimed_by', auth.uid());
    end if;
    raise exception 'ALERT_CLAIM_NOT_NEAREST';
  end if;

  v_close := coalesce(v_row.arbitration_started_at, timezone('utc', now())) + interval '2 seconds';
  v_wait := extract(epoch from (v_close - timezone('utc', now())));
  if v_wait > 0 and v_wait <= 2.5 then
    perform pg_sleep(v_wait);
  end if;

  select * into v_row from public.lost_found_alerts where id = p_id for update;
  if v_row.status = 'CLAIMED' then
    if v_row.claimed_by = auth.uid() then
      return jsonb_build_object('id', p_id, 'status', 'CLAIMED', 'pet_id', v_row.pet_id, 'claimed_by', auth.uid());
    end if;
    raise exception 'ALERT_CLAIM_NOT_NEAREST';
  end if;
  if v_row.status <> 'OPEN' then
    raise exception 'ALERT_ALREADY_CLAIMED';
  end if;

  select a.responder_person_id into v_winner
    from public.lost_found_claim_attempts a
   where a.alert_id = p_id
     and a.created_at <= coalesce(v_row.arbitration_started_at, timezone('utc', now())) + interval '2 seconds'
   order by a.distance_meters asc, a.created_at asc, a.responder_person_id asc
   limit 1;

  if v_winner is distinct from auth.uid() then
    update public.lost_found_claim_attempts
       set outcome = 'NOT_SELECTED'
     where alert_id = p_id and responder_person_id = auth.uid() and outcome = 'PENDING';
    raise exception 'ALERT_CLAIM_NOT_NEAREST';
  end if;

  return public._canon_apply_lost_found_claim(p_id, v_winner);
end;
$$;

revoke all on function public._canon_apply_lost_found_claim(uuid, uuid) from public, anon, authenticated;
grant execute on function public.canon_register_lost_found_claim_attempt(uuid) to authenticated;
grant execute on function public.canon_claim_lost_found(uuid) to authenticated;
