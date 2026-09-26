-- 1087: FOUND publish creates provisional pet + VitaCora.
-- Claim only transfers custody. Waves of 10 / 15 min. Owner-assert + confirm relink.
-- Does not edit 1086.

alter table public.lost_found_alerts
  add column if not exists fanout_stopped_at timestamptz null,
  add column if not exists last_wave_at timestamptz null,
  add column if not exists wave_count integer not null default 0;

alter table public.lost_found_alert_recipients
  add column if not exists wave integer not null default 1;

alter table public.lost_found_alert_recipients
  drop constraint if exists lost_found_alert_recipients_status_check;
alter table public.lost_found_alert_recipients
  add constraint lost_found_alert_recipients_status_check
  check (status in ('NOTIFIED', 'VIEWED', 'CLAIMED', 'SKIPPED', 'CANCELLED'));

alter table public.lost_found_match_candidates
  add column if not exists lost_alert_id uuid null references public.lost_found_alerts(id),
  add column if not exists asserted_by uuid null references public.persons(user_id),
  add column if not exists match_reason text null;

create unique index if not exists lost_found_match_owner_assert_uidx
  on public.lost_found_match_candidates (alert_id, lost_alert_id, asserted_by)
  where lost_alert_id is not null and asserted_by is not null;

create unique index if not exists lost_found_match_auto_uidx
  on public.lost_found_match_candidates (alert_id, lost_alert_id)
  where lost_alert_id is not null and asserted_by is null;

create table if not exists public.lost_found_claim_attempts (
  id uuid primary key default gen_random_uuid(),
  alert_id uuid not null references public.lost_found_alerts(id) on delete cascade,
  responder_person_id uuid not null references public.persons(user_id),
  distance_meters numeric(12,2) not null,
  created_at timestamptz not null default timezone('utc', now())
);

create unique index if not exists lost_found_claim_attempts_person_uidx
  on public.lost_found_claim_attempts (alert_id, responder_person_id);

create index if not exists lost_found_claim_attempts_alert_idx
  on public.lost_found_claim_attempts (alert_id, created_at desc);

alter table public.lost_found_claim_attempts enable row level security;
revoke all on table public.lost_found_claim_attempts from anon, authenticated;

create or replace function public._canon_stop_lost_found_fanout(p_alert_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.lost_found_alerts
     set fanout_stopped_at = coalesce(fanout_stopped_at, timezone('utc', now())),
         updated_at = timezone('utc', now())
   where id = p_alert_id;
  update public.lost_found_alert_recipients
     set status = 'CANCELLED'
   where alert_id = p_alert_id
     and status in ('NOTIFIED', 'VIEWED');
end;
$$;

create or replace function public._canon_create_found_identity(
  p_name text,
  p_species text,
  p_sex text,
  p_size text,
  p_photo_asset_id uuid,
  p_estimated_age_months integer
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
begin
  if v_name is null then v_name := 'Sin nombre'; end if;
  if not exists (select 1 from public.species s where s.code = v_species) then
    v_species := 'DOG';
  end if;
  if v_sex not in ('FEMALE', 'MALE', 'UNKNOWN') then v_sex := 'UNKNOWN'; end if;
  if v_size not in ('SMALL', 'MEDIUM', 'LARGE', 'UNKNOWN') then v_size := 'UNKNOWN'; end if;
  if v_est is not null and v_est >= 0 then
    v_precision := 'ESTIMATED';
  else
    v_est := null;
  end if;

  insert into public.pets (
    created_by_user_id, name, species_code, sex, size, origin_kind, birth_precision,
    estimated_age_months, estimated_as_of, avatar_asset_id,
    management_context_kind, management_context_id,
    current_custodian_kind, current_custodian_person_id
  ) values (
    auth.uid(), v_name, v_species, v_sex, v_size, 'FOUND_CASE', v_precision,
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
begin
  select * into v_found from public.lost_found_alerts where id = p_found_id and kind = 'FOUND';
  if not found or v_found.pet_id is null then
    return 0;
  end if;
  select * into v_found_pet from public.pets where id = v_found.pet_id;

  for r in
    select a.id as lost_id, p.id as lost_pet_id, p.species_code, p.sex, p.size,
           a.precise_location, a.incident_at, a.created_at
      from public.lost_found_alerts a
      join public.pets p on p.id = a.pet_id
     where a.kind = 'LOST'
       and a.status = 'OPEN'
       and a.id is distinct from p_found_id
  loop
    if r.species_code is distinct from v_found_pet.species_code then
      continue;
    end if;
    v_score := 0.40;
    if v_found_pet.sex is not null and v_found_pet.sex <> 'UNKNOWN'
       and r.sex is not null and r.sex <> 'UNKNOWN' then
      if r.sex = v_found_pet.sex then v_score := v_score + 0.20; else continue; end if;
    end if;
    if v_found_pet.size is not null and v_found_pet.size <> 'UNKNOWN'
       and r.size is not null and r.size <> 'UNKNOWN' then
      if r.size = v_found_pet.size then v_score := v_score + 0.15; end if;
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
    if v_score < 0.40 then
      continue;
    end if;
    insert into public.lost_found_match_candidates (alert_id, lost_alert_id, score, status, match_reason)
    values (p_found_id, r.lost_id, least(v_score, 0.9999), 'PENDING', 'BASIC_GEO_TIME')
    on conflict (alert_id, lost_alert_id) where lost_alert_id is not null and asserted_by is null
    do nothing;
    v_count := v_count + 1;
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
           updated_at = timezone('utc', now())
     where id = p_alert_id;
  end if;
  return v_count;
end;
$$;

create or replace function public.canon_advance_lost_found_waves(p_alert_id uuid default null)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_total integer := 0;
  r record;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  for r in
    select a.id
      from public.lost_found_alerts a
     where a.status = 'OPEN'
       and a.fanout_stopped_at is null
       and a.precise_location is not null
       and (p_alert_id is null or a.id = p_alert_id)
       and (
         a.last_wave_at is null
         or a.last_wave_at <= timezone('utc', now()) - interval '15 minutes'
       )
  loop
    v_total := v_total + public._canon_fanout_lost_found_recipients(r.id);
  end loop;
  return v_total;
end;
$$;

drop function if exists public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid);

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
  p_estimated_age_months integer default null
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
      select id, pet_id into v_id, v_pet
        from public.lost_found_alerts
       where created_by = auth.uid() and kind = 'FOUND' and status = 'OPEN'
       order by created_at desc
       limit 1;
      return v_id;
    end if;
    v_pet := public._canon_create_found_identity(
      p_name, p_species, p_sex, p_size, p_photo_asset_id, p_estimated_age_months
    );
  end if;

  insert into public.lost_found_alerts (
    kind, pet_id, created_by, locality_id, species_code, note,
    precise_location, photo_asset_id, incident_at, status
  ) values (
    v_kind, v_pet, auth.uid(), p_locality_id, p_species, p_note,
    v_loc, p_photo_asset_id, coalesce(p_incident_at, timezone('utc', now())), 'OPEN'
  ) returning id into v_id;

  if v_kind = 'FOUND' then
    perform public._canon_match_found_to_lost(v_id);
  end if;
  perform public._canon_fanout_lost_found_recipients(v_id);
  perform public.canon_audit(
    'lost_found.create',
    'lost_found_alerts',
    v_id,
    jsonb_build_object('kind', v_kind, 'pet_id', v_pet, 'origin', 'FOUND_CASE')
  );
  return v_id;
end;
$$;

create or replace function public.canon_list_lost_found(p_kind text default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  perform public.canon_advance_lost_found_waves(null);
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
      'locality_id', a.locality_id,
      'note', a.note,
      'created_by', a.created_by,
      'claimed_by', a.claimed_by,
      'claimed_at', a.claimed_at,
      'incident_at', a.incident_at,
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
    where a.status in ('OPEN', 'CLAIMED')
      and (p_kind is null or a.kind = p_kind)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_claim_lost_found(p_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_alerts%rowtype;
  v_pet uuid;
  v_link uuid;
  v_dist numeric;
  v_winner uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._canon_alert_responder_eligible(auth.uid()) then
    raise exception 'FORBIDDEN';
  end if;

  select * into v_row from public.lost_found_alerts where id = p_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.kind <> 'FOUND' then raise exception 'CLAIM_FOUND_ONLY'; end if;
  if v_row.status <> 'OPEN' then
    raise exception 'ALERT_ALREADY_CLAIMED';
  end if;
  if v_row.pet_id is null then
    v_pet := public._canon_create_found_identity(
      null, v_row.species_code, 'UNKNOWN', 'UNKNOWN', v_row.photo_asset_id, null
    );
    update public.lost_found_alerts
       set pet_id = v_pet, updated_at = timezone('utc', now())
     where id = p_id;
  else
    v_pet := v_row.pet_id;
  end if;

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

  insert into public.lost_found_claim_attempts (alert_id, responder_person_id, distance_meters)
  values (p_id, auth.uid(), v_dist)
  on conflict (alert_id, responder_person_id)
  do update set distance_meters = excluded.distance_meters;

  select a.responder_person_id into v_winner
    from public.lost_found_claim_attempts a
   where a.alert_id = p_id
     and a.created_at >= timezone('utc', now()) - interval '8 seconds'
   order by a.distance_meters asc, a.created_at asc, a.responder_person_id asc
   limit 1;
  if v_winner is distinct from auth.uid() then
    raise exception 'ALERT_CLAIM_NOT_NEAREST';
  end if;

  update public.pets
     set current_custodian_kind = 'PERSON',
         current_custodian_person_id = auth.uid(),
         current_custodian_organization_id = null,
         updated_at = timezone('utc', now())
   where id = v_pet;

  if not exists (
    select 1 from public.pet_responsibility_links l
     where l.pet_id = v_pet and l.holder_person_id = auth.uid() and l.status = 'ACTIVE'
  ) then
    insert into public.pet_responsibility_links (
      pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
    ) values (v_pet, 'PERSON', auth.uid(), 'AUTHORIZED', auth.uid())
    returning id into v_link;
    insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
    select v_pet, v_link, auth.uid(), code, auth.uid()
      from public.permission_codes where scope in ('PET', 'VITACORA');
  end if;

  update public.lost_found_alerts
     set status = 'CLAIMED',
         claimed_by = auth.uid(),
         claimed_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = p_id
     and status = 'OPEN';
  if not found then
    raise exception 'ALERT_ALREADY_CLAIMED';
  end if;

  perform public._canon_stop_lost_found_fanout(p_id);
  update public.lost_found_alert_recipients
     set status = 'CLAIMED'
   where alert_id = p_id
     and responder_person_id = auth.uid();

  perform public.canon_audit(
    'lost_found.claim',
    'lost_found_alerts',
    p_id,
    jsonb_build_object('pet_id', v_pet, 'claimed_by', auth.uid(), 'new_pet', false)
  );

  return jsonb_build_object(
    'id', p_id,
    'status', 'CLAIMED',
    'pet_id', v_pet,
    'claimed_by', auth.uid()
  );
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
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_found from public.lost_found_alerts where id = p_found_id;
  select * into v_lost from public.lost_found_alerts where id = p_lost_id;
  if v_found.id is null or v_lost.id is null then raise exception 'NOT_FOUND'; end if;
  if v_found.kind <> 'FOUND' or v_lost.kind <> 'LOST' then raise exception 'KIND_INVALID'; end if;
  if v_found.status not in ('OPEN', 'CLAIMED') then raise exception 'FORBIDDEN'; end if;
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
  perform public.canon_audit('lost_found.owner_assert', 'lost_found_match_candidates', v_id, '{}'::jsonb);
  return v_id;
end;
$$;

create or replace function public.canon_confirm_found_owner_match(p_candidate_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_cand public.lost_found_match_candidates%rowtype;
  v_found public.lost_found_alerts%rowtype;
  v_lost public.lost_found_alerts%rowtype;
  v_found_pet uuid;
  v_lost_pet uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_cand from public.lost_found_match_candidates where id = p_candidate_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  select * into v_found from public.lost_found_alerts where id = v_cand.alert_id for update;
  select * into v_lost from public.lost_found_alerts where id = v_cand.lost_alert_id for update;
  if v_found.pet_id is null or v_lost.pet_id is null then raise exception 'NOT_FOUND'; end if;
  v_found_pet := v_found.pet_id;
  v_lost_pet := v_lost.pet_id;
  if not exists (
    select 1 from public.pets p
     where p.id = v_found_pet
       and p.current_custodian_person_id = auth.uid()
  ) then
    raise exception 'FORBIDDEN';
  end if;

  update public.pets lost
     set avatar_asset_id = coalesce(lost.avatar_asset_id, found.avatar_asset_id),
         updated_at = timezone('utc', now())
    from public.pets found
   where lost.id = v_lost_pet
     and found.id = v_found_pet;
  update public.vitacora_moments set pet_id = v_lost_pet where pet_id = v_found_pet;
  update public.lost_found_alerts
     set pet_id = v_lost_pet,
         status = 'RESOLVED',
         resolved_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = v_found.id;
  update public.lost_found_alerts
     set status = 'RESOLVED',
         resolved_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = v_lost.id;
  update public.pets
     set lifecycle_status = 'ARCHIVED',
         archived_at = timezone('utc', now()),
         updated_at = timezone('utc', now())
   where id = v_found_pet;
  update public.lost_found_match_candidates
     set status = 'ACCEPTED'
   where id = p_candidate_id;
  update public.lost_found_match_candidates
     set status = 'REJECTED'
   where alert_id = v_found.id
     and id is distinct from p_candidate_id
     and status = 'PENDING';
  perform public._canon_stop_lost_found_fanout(v_found.id);
  insert into public.lost_found_match_reviews (candidate_id, reviewer_user_id, decision)
  values (p_candidate_id, auth.uid(), 'ACCEPTED');
  perform public.canon_audit(
    'lost_found.reunify',
    'lost_found_alerts',
    v_found.id,
    jsonb_build_object('survivor_pet_id', v_lost_pet, 'archived_pet_id', v_found_pet)
  );
  return jsonb_build_object(
    'found_id', v_found.id,
    'lost_id', v_lost.id,
    'pet_id', v_lost_pet,
    'archived_pet_id', v_found_pet
  );
end;
$$;

create or replace function public.canon_list_found_match_candidates(p_found_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_found public.lost_found_alerts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_found from public.lost_found_alerts where id = p_found_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_found.created_by <> auth.uid()
     and v_found.claimed_by is distinct from auth.uid()
     and not exists (
       select 1 from public.pets p
        where p.id = v_found.pet_id and p.current_custodian_person_id = auth.uid()
     ) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', c.id,
      'lost_alert_id', c.lost_alert_id,
      'asserted_by', c.asserted_by,
      'status', c.status,
      'score', c.score,
      'match_reason', c.match_reason
    ) order by c.created_at desc)
    from public.lost_found_match_candidates c
    where c.alert_id = p_found_id
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_attach_lost_found_photo(p_id uuid, p_photo_asset_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_alerts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_photo_asset_id is null then raise exception 'PHOTO_REQUIRED'; end if;
  select * into v_row from public.lost_found_alerts where id = p_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_row.created_by <> auth.uid()
     and not exists (
       select 1 from public.pets p
        where p.id = v_row.pet_id and p.current_custodian_person_id = auth.uid()
     ) then
    raise exception 'FORBIDDEN';
  end if;
  update public.lost_found_alerts
     set photo_asset_id = p_photo_asset_id,
         updated_at = timezone('utc', now())
   where id = p_id;
  if v_row.pet_id is not null then
    update public.pets
       set avatar_asset_id = coalesce(avatar_asset_id, p_photo_asset_id),
           updated_at = timezone('utc', now())
     where id = v_row.pet_id;
    if not exists (
      select 1 from public.vitacora_moments m
       where m.pet_id = v_row.pet_id and m.asset_id = p_photo_asset_id
    ) then
      insert into public.vitacora_moments (pet_id, kind, title, asset_id, created_by, occurred_on)
      values (v_row.pet_id, 'PHOTO', 'Foto del hallazgo', p_photo_asset_id, auth.uid(), current_date);
    end if;
  end if;
  return true;
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
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._canon_alert_responder_eligible(auth.uid()) then
    raise exception 'FORBIDDEN';
  end if;
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
  insert into public.lost_found_claim_attempts (alert_id, responder_person_id, distance_meters)
  values (p_id, auth.uid(), v_dist)
  on conflict (alert_id, responder_person_id)
  do update set distance_meters = excluded.distance_meters
  returning id into v_id;
  return v_id;
end;
$$;

revoke all on function public._canon_stop_lost_found_fanout(uuid) from public, anon, authenticated;
revoke all on function public._canon_create_found_identity(text, text, text, text, uuid, integer) from public, anon, authenticated;
revoke all on function public._canon_match_found_to_lost(uuid) from public, anon, authenticated;

grant execute on function public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid, text, text, text, integer) to authenticated;
grant execute on function public.canon_list_lost_found(text) to authenticated;
grant execute on function public.canon_claim_lost_found(uuid) to authenticated;
grant execute on function public.canon_advance_lost_found_waves(uuid) to authenticated;
grant execute on function public.canon_assert_found_might_be_mine(uuid, uuid) to authenticated;
grant execute on function public.canon_confirm_found_owner_match(uuid) to authenticated;
grant execute on function public.canon_list_found_match_candidates(uuid) to authenticated;
grant execute on function public.canon_attach_lost_found_photo(uuid, uuid) to authenticated;
grant execute on function public.canon_register_lost_found_claim_attempt(uuid) to authenticated;
