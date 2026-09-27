-- 1088: FOUND/LOST hardening. Does not edit 1087.
-- Scheduler: pg_cron every 5 minutes → canon_tick_lost_found_waves (no auth.uid).
-- Matching: species hard; breed/sex/size/age/geo/time scoring only.
-- Notifications to LOST owners and current custodian. Owner/custodian reject. IN_CARE.

alter table public.lost_found_alerts
  add column if not exists next_wave_at timestamptz null,
  add column if not exists fanout_exhausted_at timestamptz null,
  add column if not exists resolved_reason text null,
  add column if not exists conversation_id uuid null references public.conversations(id);

alter table public.lost_found_alerts
  drop constraint if exists lost_found_alerts_status_check;
alter table public.lost_found_alerts
  add constraint lost_found_alerts_status_check
  check (status in ('OPEN', 'CLAIMED', 'IN_CARE', 'RESOLVED', 'CANCELLED', 'HIDDEN'));

alter table public.lost_found_alerts
  drop constraint if exists lost_found_alerts_resolved_reason_check;
alter table public.lost_found_alerts
  add constraint lost_found_alerts_resolved_reason_check
  check (resolved_reason is null or resolved_reason in (
    'SAFE_IN_CARE', 'REUNITED_WITH_OWNER', 'MOVED_TO_ADOPTION'
  ));

alter table public.lost_found_alert_recipients
  drop constraint if exists lost_found_alert_recipients_status_check;
alter table public.lost_found_alert_recipients
  add constraint lost_found_alert_recipients_status_check
  check (status in (
    'NOTIFIED', 'VIEWED', 'CLAIMED', 'SKIPPED', 'CANCELLED', 'CANCELLED_CASE_CLAIMED'
  ));

alter table public.lost_found_match_candidates
  drop constraint if exists lost_found_match_candidates_status_check;
alter table public.lost_found_match_candidates
  add constraint lost_found_match_candidates_status_check
  check (status in (
    'PENDING', 'ACCEPTED', 'REJECTED', 'REJECTED_BY_OWNER', 'REJECTED_BY_CUSTODIAN'
  ));

alter table public.lost_found_match_candidates
  add column if not exists conversation_id uuid null references public.conversations(id),
  add column if not exists rejected_by uuid null references public.persons(user_id),
  add column if not exists rejected_at timestamptz null,
  add column if not exists reject_note text null;

create table if not exists public.lost_found_match_channels (
  candidate_id uuid primary key references public.lost_found_match_candidates(id) on delete cascade,
  alert_id uuid not null references public.lost_found_alerts(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id),
  created_at timestamptz not null default timezone('utc', now())
);

alter table public.lost_found_match_channels enable row level security;
revoke all on table public.lost_found_match_channels from anon, authenticated;

create or replace function public._canon_pet_age_months(p public.pets)
returns integer
language plpgsql
stable
as $$
declare
  v_months integer;
begin
  if p.birth_precision = 'ESTIMATED' and p.estimated_age_months is not null then
    return p.estimated_age_months
      + greatest(0, (current_date - coalesce(p.estimated_as_of, current_date)) / 30);
  end if;
  if p.birth_precision = 'EXACT_DATE' and p.birth_date is not null then
    return greatest(0, (current_date - p.birth_date) / 30);
  end if;
  if p.birth_precision = 'MONTH_PRECISION' and p.birth_year is not null and p.birth_month is not null then
    return greatest(0, (extract(year from current_date)::int - p.birth_year) * 12
      + (extract(month from current_date)::int - p.birth_month));
  end if;
  if p.birth_precision = 'YEAR_PRECISION' and p.birth_year is not null then
    return greatest(0, (extract(year from current_date)::int - p.birth_year) * 12);
  end if;
  return null;
end;
$$;

create or replace function public._canon_emit_lf_event(
  p_user_id uuid,
  p_alert_id uuid,
  p_event_key text,
  p_title text,
  p_body text,
  p_cta text,
  p_dedup text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_user_id is null or p_dedup is null then
    return;
  end if;
  if to_regprocedure(
    'public.m06_emit_domain_notification(uuid,text,text,text,text,text,text,text,text,text,text,uuid,text,text,text,text,jsonb,text,text,timestamptz,boolean)'
  ) is not null then
    perform public.m06_emit_domain_notification(
      p_recipient_user_id := p_user_id,
      p_event_key := p_event_key,
      p_origin_module := 'LOST_FOUND',
      p_origin_type := 'ALERT',
      p_category := 'LOST_FOUND',
      p_priority := 'HIGH',
      p_sensitivity := 'PRIVATE',
      p_title := p_title,
      p_body := p_body,
      p_resource_type := 'LOST_FOUND_CASE',
      p_resource_id := p_alert_id::text,
      p_organization_id := null,
      p_deep_link_type := 'LOST_FOUND_CASE',
      p_deep_link_resource_type := 'LOST_FOUND_CASE',
      p_deep_link_resource_id := p_alert_id::text,
      p_deep_link_required_permission := null,
      p_payload := jsonb_build_object('alert_id', p_alert_id, 'cta', p_cta),
      p_deduplication_key := p_dedup,
      p_idempotency_key := p_dedup,
      p_expires_at := timezone('utc', now()) + interval '14 days',
      p_is_internal := false
    );
  else
    insert into public.notifications (user_id, kind, payload)
    select p_user_id, p_event_key, jsonb_build_object(
      'alert_id', p_alert_id, 'title', p_title, 'cta', p_cta
    )
    where not exists (
      select 1 from public.notifications n
       where n.user_id = p_user_id
         and n.kind = p_event_key
         and n.payload->>'alert_id' = p_alert_id::text
         and coalesce(n.payload->>'dedup', '') = p_dedup
    );
  end if;
end;
$$;

create or replace function public._canon_stop_lost_found_fanout(p_alert_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.lost_found_alerts
     set fanout_stopped_at = coalesce(fanout_stopped_at, timezone('utc', now())),
         next_wave_at = null,
         updated_at = timezone('utc', now())
   where id = p_alert_id;
  update public.lost_found_alert_recipients
     set status = 'CANCELLED_CASE_CLAIMED'
   where alert_id = p_alert_id
     and status in ('NOTIFIED', 'VIEWED');
end;
$$;

drop function if exists public._canon_create_found_identity(text, text, text, text, uuid, integer);

create or replace function public._canon_create_found_identity(
  p_name text,
  p_species text,
  p_sex text,
  p_size text,
  p_photo_asset_id uuid,
  p_estimated_age_months integer,
  p_breed_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pet uuid;
  v_link uuid;
  v_name text := nullif(btrim(coalesce(p_name, '')), '');
  v_species text := upper(btrim(coalesce(p_species, 'DOG')));
  v_sex text := upper(btrim(coalesce(p_sex, 'UNKNOWN')));
  v_size text := upper(btrim(coalesce(p_size, 'UNKNOWN')));
  v_precision text := 'UNKNOWN';
  v_est integer := p_estimated_age_months;
  v_breed uuid := p_breed_id;
begin
  if v_name is null then v_name := 'Sin nombre'; end if;
  if not exists (select 1 from public.species s where s.code = v_species) then
    v_species := 'DOG';
  end if;
  if v_sex not in ('FEMALE', 'MALE', 'UNKNOWN') then v_sex := 'UNKNOWN'; end if;
  if v_size not in ('SMALL', 'MEDIUM', 'LARGE', 'UNKNOWN') then v_size := 'UNKNOWN'; end if;
  if v_breed is not null and not exists (select 1 from public.breeds b where b.id = v_breed) then
    v_breed := null;
  end if;
  if v_est is not null and v_est >= 0 then
    v_precision := 'ESTIMATED';
  else
    v_est := null;
  end if;
  insert into public.pets (
    created_by_user_id, name, species_code, breed_id, sex, size, origin_kind, birth_precision,
    estimated_age_months, estimated_as_of, avatar_asset_id,
    management_context_kind, management_context_id,
    current_custodian_kind, current_custodian_person_id
  ) values (
    auth.uid(), v_name, v_species, v_breed, v_sex, v_size, 'FOUND_CASE', v_precision,
    v_est, case when v_precision = 'ESTIMATED' then current_date else null end, p_photo_asset_id,
    'PERSON', auth.uid()::text,
    'PERSON', auth.uid()
  ) returning id into v_pet;
  insert into public.vitacora_profiles (pet_id) values (v_pet);
  insert into public.pet_responsibility_links (
    pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
  ) values (v_pet, 'PERSON', auth.uid(), 'AUTHORIZED', auth.uid())
  returning id into v_link;
  insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
  select v_pet, v_link, auth.uid(), code, auth.uid()
    from public.permission_codes where scope in ('PET', 'VITACORA');
  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_pet, v_link, auth.uid(), 'FOUND_CASE_CARE');
  insert into public.pet_lifecycle_events (pet_id, to_status, actor_user_id, note)
  values (v_pet, 'ACTIVE', auth.uid(), 'FOUND_CASE');
  if p_photo_asset_id is not null then
    insert into public.vitacora_moments (pet_id, kind, title, asset_id, created_by, occurred_on)
    values (v_pet, 'PHOTO', 'Foto del hallazgo', p_photo_asset_id, auth.uid(), current_date);
  end if;
  return v_pet;
end;
$$;

create or replace function public._canon_match_found_to_lost(p_found_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_found public.lost_found_alerts%rowtype;
  v_found_pet public.pets%rowtype;
  r record;
  v_count integer := 0;
  v_score numeric;
  v_found_age integer;
  v_lost_age integer;
  v_age_diff integer;
  v_lost_pet public.pets%rowtype;
begin
  select * into v_found from public.lost_found_alerts where id = p_found_id and kind = 'FOUND';
  if not found or v_found.pet_id is null then
    return 0;
  end if;
  select * into v_found_pet from public.pets where id = v_found.pet_id;
  v_found_age := public._canon_pet_age_months(v_found_pet);

  for r in
    select a.id as lost_id, a.created_by as lost_owner, a.precise_location, a.incident_at,
           a.created_at, p.id as lost_pet_id
      from public.lost_found_alerts a
      join public.pets p on p.id = a.pet_id
     where a.kind = 'LOST'
       and a.status in ('OPEN', 'CLAIMED', 'IN_CARE')
       and a.id is distinct from p_found_id
  loop
    select * into v_lost_pet from public.pets where id = r.lost_pet_id;
    if v_lost_pet.species_code is distinct from v_found_pet.species_code then
      continue;
    end if;
    v_score := 0.40;
    if v_found_pet.breed_id is not null and v_lost_pet.breed_id is not null
       and v_found_pet.breed_id = v_lost_pet.breed_id then
      v_score := v_score + 0.12;
    end if;
    if v_found_pet.sex is not null and v_found_pet.sex <> 'UNKNOWN'
       and v_lost_pet.sex is not null and v_lost_pet.sex <> 'UNKNOWN'
       and v_lost_pet.sex = v_found_pet.sex then
      v_score := v_score + 0.20;
    end if;
    if v_found_pet.size is not null and v_found_pet.size <> 'UNKNOWN'
       and v_lost_pet.size is not null and v_lost_pet.size <> 'UNKNOWN'
       and v_lost_pet.size = v_found_pet.size then
      v_score := v_score + 0.15;
    end if;
    v_lost_age := public._canon_pet_age_months(v_lost_pet);
    if v_found_age is not null and v_lost_age is not null then
      v_age_diff := abs(v_found_age - v_lost_age);
      if v_age_diff <= 6 then
        v_score := v_score + 0.12;
      elsif v_age_diff <= 18 then
        v_score := v_score + 0.06;
      end if;
    end if;
    if v_found.precise_location is not null and r.precise_location is not null then
      if ST_Distance(v_found.precise_location, r.precise_location) <= 25000 then
        v_score := v_score + 0.15;
      end if;
    end if;
    if coalesce(v_found.incident_at, v_found.created_at) is not null
       and coalesce(r.incident_at, r.created_at) is not null
       and abs(extract(epoch from (
         coalesce(v_found.incident_at, v_found.created_at) - coalesce(r.incident_at, r.created_at)
       ))) <= 14 * 24 * 3600 then
      v_score := v_score + 0.10;
    end if;
    insert into public.lost_found_match_candidates (alert_id, lost_alert_id, score, status, match_reason)
    values (p_found_id, r.lost_id, least(v_score, 0.9999), 'PENDING', 'BASIC_GEO_TIME')
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

create or replace function public._canon_fanout_lost_found_recipients(p_alert_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
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
           ST_Distance(loc, v_alert.precise_location) as meters
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

-- Scheduler tick: NO auth.uid(). Invoked by pg_cron.
create or replace function public.canon_tick_lost_found_waves()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_total integer := 0;
  r record;
begin
  for r in
    select a.id
      from public.lost_found_alerts a
     where a.status = 'OPEN'
       and a.fanout_stopped_at is null
       and a.fanout_exhausted_at is null
       and a.precise_location is not null
       and a.next_wave_at is not null
       and a.next_wave_at <= timezone('utc', now())
  loop
    v_total := v_total + public._canon_fanout_lost_found_recipients(r.id);
  end loop;
  return v_total;
end;
$$;

create or replace function public.canon_advance_lost_found_waves(p_alert_id uuid default null)
returns integer
language plpgsql
security definer
set search_path = public
as $$
begin
  -- Kept for admin/service diagnostics. Not used by list/read paths.
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return public.canon_tick_lost_found_waves();
end;
$$;

drop function if exists public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid, text, text, text, integer);

create or replace function public.canon_create_lost_found(
  p_kind text,
  p_pet_id uuid default null,
  p_locality_id text default null,
  p_species text default null,
  p_note text default null,
  p_lat double precision default null,
  p_lng double precision default null,
  p_incident_at timestamptz default null,
  p_photo_asset_id uuid default null,
  p_name text default null,
  p_sex text default 'UNKNOWN',
  p_size text default 'UNKNOWN',
  p_estimated_age_months integer default null,
  p_breed_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_kind text := upper(btrim(coalesce(p_kind, '')));
  v_pet uuid := p_pet_id;
  v_loc extensions.geography(Point, 4326);
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_kind not in ('LOST', 'FOUND') then raise exception 'KIND_INVALID'; end if;
  if v_kind = 'LOST' and v_pet is not null
     and not public._acl_pet_holder(auth.uid(), v_pet) then
    raise exception 'FORBIDDEN';
  end if;
  if p_lat is not null and p_lng is not null then
    if p_lat < -90 or p_lat > 90 or p_lng < -180 or p_lng > 180 then
      raise exception 'LOCATION_INVALID';
    end if;
    v_loc := ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::extensions.geography;
  end if;

  if v_kind = 'FOUND' then
    if exists (
      select 1 from public.lost_found_alerts a
       where a.created_by = auth.uid()
         and a.kind = 'FOUND'
         and a.status = 'OPEN'
         and a.created_at > timezone('utc', now()) - interval '15 seconds'
         and coalesce(a.note, '') = coalesce(p_note, '')
    ) then
      select id into v_id
        from public.lost_found_alerts
       where created_by = auth.uid() and kind = 'FOUND' and status = 'OPEN'
       order by created_at desc
       limit 1;
      return v_id;
    end if;
    v_pet := public._canon_create_found_identity(
      p_name, p_species, p_sex, p_size, p_photo_asset_id, p_estimated_age_months, p_breed_id
    );
  end if;

  insert into public.lost_found_alerts (
    kind, pet_id, created_by, locality_id, species_code, note,
    precise_location, photo_asset_id, incident_at, status, next_wave_at
  ) values (
    v_kind, v_pet, auth.uid(), p_locality_id, p_species, p_note,
    v_loc, p_photo_asset_id, coalesce(p_incident_at, timezone('utc', now())), 'OPEN',
    timezone('utc', now()) + interval '15 minutes'
  ) returning id into v_id;

  if v_kind = 'FOUND' then
    perform public._canon_match_found_to_lost(v_id);
  end if;
  perform public._canon_fanout_lost_found_recipients(v_id);
  perform public.canon_audit(
    'lost_found.create',
    'lost_found_alerts',
    v_id,
    jsonb_build_object('kind', v_kind, 'pet_id', v_pet)
  );
  return v_id;
end;
$$;

create or replace function public.canon_list_lost_found(p_kind text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  -- No wave scheduling here. Waves are pg_cron only.
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', a.id,
      'kind', a.kind,
      'status', a.status,
      'public_code', a.public_code,
      'pet_id', a.pet_id,
      'pet_name', p.name,
      'species', coalesce(a.species_code, p.species_code),
      'sex', p.sex,
      'size', p.size,
      'breed_id', p.breed_id,
      'locality_id', a.locality_id,
      'note', a.note,
      'created_by', a.created_by,
      'claimed_by', a.claimed_by,
      'claimed_at', a.claimed_at,
      'incident_at', a.incident_at,
      'photo_asset_id', a.photo_asset_id,
      'resolved_reason', a.resolved_reason,
      'can_claim', a.kind = 'FOUND'
        and a.status = 'OPEN'
        and public._canon_alert_responder_eligible(auth.uid())
        and exists (
          select 1 from public.lost_found_alert_recipients r
           where r.alert_id = a.id
             and r.status in ('NOTIFIED', 'VIEWED')
             and (
               r.responder_person_id = auth.uid()
               or r.responder_organization_id in (
                 select m.organization_id from public.organization_memberships m
                  where m.person_id = auth.uid() and m.status = 'ACTIVE'
               )
             )
        ),
      'is_custodian', p.current_custodian_person_id = auth.uid(),
      'created_at', a.created_at
    ) order by a.created_at desc)
    from public.lost_found_alerts a
    left join public.pets p on p.id = a.pet_id
    where a.status in ('OPEN', 'CLAIMED', 'IN_CARE')
      and (p_kind is null or a.kind = p_kind)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_assert_found_might_be_mine(p_found_id uuid, p_lost_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_found public.lost_found_alerts%rowtype;
  v_lost public.lost_found_alerts%rowtype;
  v_id uuid;
  v_conv uuid;
  v_custodian uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_found from public.lost_found_alerts where id = p_found_id;
  select * into v_lost from public.lost_found_alerts where id = p_lost_id;
  if v_found.id is null or v_lost.id is null then raise exception 'NOT_FOUND'; end if;
  if v_found.kind <> 'FOUND' or v_lost.kind <> 'LOST' then raise exception 'KIND_INVALID'; end if;
  if v_found.status not in ('OPEN', 'CLAIMED', 'IN_CARE') then raise exception 'FORBIDDEN'; end if;
  if v_lost.created_by <> auth.uid()
     and (v_lost.pet_id is null or not public._acl_pet_holder(auth.uid(), v_lost.pet_id)) then
    raise exception 'FORBIDDEN';
  end if;
  insert into public.lost_found_match_candidates (
    alert_id, lost_alert_id, asserted_by, status, match_reason, score
  ) values (
    p_found_id, p_lost_id, auth.uid(), 'PENDING', 'OWNER_ASSERT', 0.5000
  )
  on conflict (alert_id, lost_alert_id, asserted_by) where lost_alert_id is not null and asserted_by is not null
  do update set status = 'PENDING'
  returning id into v_id;

  select current_custodian_person_id into v_custodian from public.pets where id = v_found.pet_id;
  if v_custodian is not null and v_custodian is distinct from auth.uid() then
    if not exists (select 1 from public.lost_found_match_channels c where c.candidate_id = v_id) then
      v_conv := public.canon_start_conversation(
        'PERSON', v_custodian, null,
        'Hola, creo que el animal encontrado podría ser mi mascota.'
      );
      insert into public.lost_found_match_channels (candidate_id, alert_id, conversation_id)
      values (v_id, p_found_id, v_conv)
      on conflict (candidate_id) do nothing;
      update public.lost_found_match_candidates set conversation_id = v_conv where id = v_id;
    end if;
    perform public._canon_emit_lf_event(
      v_custodian,
      p_found_id,
      'lost_found.owner_assert',
      'Posible dueño',
      'Una persona cree que el animal encontrado podría ser su mascota.',
      'Revisar',
      'lost_found_assert:' || v_id::text || ':' || v_custodian::text
    );
  end if;
  perform public.canon_audit('lost_found.owner_assert', 'lost_found_match_candidates', v_id, '{}'::jsonb);
  return v_id;
end;
$$;

create or replace function public.canon_reject_found_might_be_mine(p_found_id uuid, p_lost_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  update public.lost_found_match_candidates
     set status = 'REJECTED_BY_OWNER',
         rejected_by = auth.uid(),
         rejected_at = timezone('utc', now())
   where alert_id = p_found_id
     and lost_alert_id = p_lost_id
     and (asserted_by = auth.uid() or asserted_by is null);
  if not found then
    insert into public.lost_found_match_candidates (
      alert_id, lost_alert_id, asserted_by, status, match_reason, score, rejected_by, rejected_at
    ) values (
      p_found_id, p_lost_id, auth.uid(), 'REJECTED_BY_OWNER', 'OWNER_REJECT', 0.1000, auth.uid(), timezone('utc', now())
    );
  end if;
  perform public.canon_audit('lost_found.owner_reject', 'lost_found_alerts', p_found_id, jsonb_build_object('lost_id', p_lost_id));
  return true;
end;
$$;

create or replace function public.canon_reject_found_owner_match(p_candidate_id uuid, p_note text default null)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_cand public.lost_found_match_candidates%rowtype;
  v_found public.lost_found_alerts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_cand from public.lost_found_match_candidates where id = p_candidate_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  select * into v_found from public.lost_found_alerts where id = v_cand.alert_id;
  if not exists (
    select 1 from public.pets p
     where p.id = v_found.pet_id and p.current_custodian_person_id = auth.uid()
  ) then
    raise exception 'FORBIDDEN';
  end if;
  update public.lost_found_match_candidates
     set status = 'REJECTED_BY_CUSTODIAN',
         rejected_by = auth.uid(),
         rejected_at = timezone('utc', now()),
         reject_note = nullif(btrim(coalesce(p_note, '')), '')
   where id = p_candidate_id;
  perform public.canon_audit('lost_found.custodian_reject', 'lost_found_match_candidates', p_candidate_id, '{}'::jsonb);
  return true;
end;
$$;

create or replace function public.canon_mark_lost_found_in_care(p_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_alerts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.lost_found_alerts where id = p_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.status <> 'CLAIMED' then raise exception 'FORBIDDEN'; end if;
  if v_row.claimed_by is distinct from auth.uid()
     and not exists (
       select 1 from public.pets p where p.id = v_row.pet_id and p.current_custodian_person_id = auth.uid()
     ) then
    raise exception 'FORBIDDEN';
  end if;
  update public.lost_found_alerts
     set status = 'IN_CARE',
         updated_at = timezone('utc', now())
   where id = p_id;
  perform public.canon_audit('lost_found.in_care', 'lost_found_alerts', p_id, '{}'::jsonb);
  return jsonb_build_object('id', p_id, 'status', 'IN_CARE');
end;
$$;

create or replace function public.canon_resolve_lost_found_reason(p_id uuid, p_reason text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_reason text := upper(btrim(coalesce(p_reason, '')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_reason not in ('SAFE_IN_CARE', 'REUNITED_WITH_OWNER', 'MOVED_TO_ADOPTION') then
    raise exception 'REASON_INVALID';
  end if;
  if not exists (
    select 1 from public.lost_found_alerts a
    left join public.pets p on p.id = a.pet_id
     where a.id = p_id
       and (
         p.current_custodian_person_id = auth.uid()
         or a.created_by = auth.uid()
         or a.claimed_by = auth.uid()
       )
  ) then
    raise exception 'FORBIDDEN';
  end if;
  update public.lost_found_alerts
     set status = 'RESOLVED',
         resolved_at = timezone('utc', now()),
         resolved_reason = v_reason,
         updated_at = timezone('utc', now())
   where id = p_id;
  perform public._canon_stop_lost_found_fanout(p_id);
  return true;
end;
$$;

-- Claim: keep nearest-wins among registered attempts in 8s window (server-side).
-- Recipients cancelled as CANCELLED_CASE_CLAIMED. No new pet.

create or replace function public.canon_set_receive_nearby_cases(p_receive boolean)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  update public.persons
     set receive_nearby_cases = coalesce(p_receive, false),
         updated_at = timezone('utc', now())
   where user_id = auth.uid();
  return coalesce(p_receive, false);
end;
$$;

revoke all on function public.canon_tick_lost_found_waves() from public, anon, authenticated;
grant execute on function public.canon_tick_lost_found_waves() to service_role;
grant execute on function public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid, text, text, text, integer, uuid) to authenticated;
grant execute on function public.canon_list_lost_found(text) to authenticated;
grant execute on function public.canon_assert_found_might_be_mine(uuid, uuid) to authenticated;
grant execute on function public.canon_reject_found_might_be_mine(uuid, uuid) to authenticated;
grant execute on function public.canon_reject_found_owner_match(uuid, text) to authenticated;
grant execute on function public.canon_mark_lost_found_in_care(uuid) to authenticated;
grant execute on function public.canon_resolve_lost_found_reason(uuid, text) to authenticated;
grant execute on function public.canon_set_receive_nearby_cases(boolean) to authenticated;
revoke all on function public._canon_create_found_identity(text, text, text, text, uuid, integer, uuid) from public, anon, authenticated;
revoke all on function public._canon_emit_lf_event(uuid, uuid, text, text, text, text, text) from public, anon, authenticated;

-- pg_cron: every 5 minutes. Independent of Android.
do $cron$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron')
     or exists (select 1 from pg_namespace where nspname = 'cron') then
    begin
      perform cron.unschedule(jobid)
        from cron.job
       where jobname = 'leover-lost-found-waves';
    exception when others then
      null;
    end;
    begin
      perform cron.schedule(
        'leover-lost-found-waves',
        '*/5 * * * *',
        $job$select public.canon_tick_lost_found_waves();$job$
      );
    exception when others then
      raise notice 'pg_cron schedule skipped: %', sqlerrm;
    end;
  else
    begin
      create extension if not exists pg_cron;
      perform cron.schedule(
        'leover-lost-found-waves',
        '*/5 * * * *',
        $job$select public.canon_tick_lost_found_waves();$job$
      );
    exception when others then
      raise notice 'pg_cron unavailable: %', sqlerrm;
    end;
  end if;
end;
$cron$;
