-- 1096: FOUND↔LOST match must resolve PostGIS under search_path=public.
-- STAGING Physical Round 3: _canon_match_found_to_lost threw (ST_Distance),
-- swallowed by 1095 create isolation → no candidates, no notifications.
-- M06 emit is absent; always persist public.notifications + outbox.
-- Verification request is idempotent for PENDING.
-- Does not edit 1095 or earlier.

alter table public.lost_found_alerts
  add column if not exists location_label text null;

create or replace function public._canon_lf_human_location(p_locality text, p_label text, p_note text)
returns text
language sql
immutable
as $$
  select coalesce(
    nullif(btrim(coalesce(p_label, '')), ''),
    case
      when nullif(btrim(coalesce(p_locality, '')), '') is null then null
      when btrim(p_locality) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then null
      when btrim(p_locality) like 'loc-%' then null
      when lower(btrim(p_locality)) in ('zona marcada en el mapa', 'zona marcada') then null
      else btrim(p_locality)
    end,
    case
      when p_note is null then null
      when split_part(p_note, ' · ', 1) ~* 'zona marcada' then null
      else nullif(btrim(split_part(p_note, ' · ', 1)), '')
    end
  );
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
declare
  v_id uuid;
begin
  if p_user_id is null or p_dedup is null then
    return;
  end if;
  if to_regprocedure(
    'public.m06_emit_domain_notification(uuid,text,text,text,text,text,text,text,text,text,text,uuid,text,text,text,text,jsonb,text,text,timestamptz,boolean)'
  ) is not null then
    begin
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
        p_payload := jsonb_build_object('alert_id', p_alert_id, 'cta', p_cta, 'dedup', p_dedup),
        p_deduplication_key := p_dedup,
        p_idempotency_key := p_dedup,
        p_expires_at := timezone('utc', now()) + interval '14 days',
        p_is_internal := false
      );
    exception when others then
      null;
    end;
  end if;

  select n.id into v_id
    from public.notifications n
   where n.user_id = p_user_id
     and n.kind = p_event_key
     and coalesce(n.payload->>'dedup', '') = p_dedup
   limit 1;
  if v_id is null then
    insert into public.notifications (user_id, kind, payload)
    values (
      p_user_id,
      p_event_key,
      jsonb_build_object(
        'alert_id', p_alert_id,
        'title', p_title,
        'body', p_body,
        'cta', p_cta,
        'dedup', p_dedup,
        'deep_link_type', 'LOST_FOUND_CASE',
        'deep_link_resource_type', 'LOST_FOUND_CASE',
        'deep_link_resource_id', p_alert_id
      )
    )
    returning id into v_id;
    insert into public.notification_outbox (notification_id, channel, status)
    values (v_id, 'PUSH', 'PENDING');
  end if;
end;
$$;

create or replace function public._canon_match_found_to_lost(p_found_id uuid)
returns integer
language plpgsql
security definer
set search_path = public, extensions
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
        case
          when v_found.precise_location is null or a.precise_location is null then null
          else extensions.ST_Distance(v_found.precise_location, a.precise_location)
        end as meters,
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
               and extensions.ST_Distance(v_found.precise_location, a.precise_location) <= 25000
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
  p_breed_id uuid default null,
  p_location_label text default null
)
returns uuid
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_id uuid;
  v_kind text := upper(btrim(coalesce(p_kind, '')));
  v_pet uuid := p_pet_id;
  v_loc extensions.geography(Point, 4326);
  v_species text := nullif(btrim(coalesce(p_species, '')), '');
  v_label text := public._canon_lf_human_location(p_locality_id, p_location_label, p_note);
begin
  if auth.uid() is null then
    raise exception 'LF-CREATE-AUTH';
  end if;
  if v_kind not in ('LOST', 'FOUND') then
    raise exception 'LF-CREATE-KIND';
  end if;
  if v_kind = 'LOST' and v_pet is not null
     and not public._acl_pet_holder(auth.uid(), v_pet) then
    raise exception 'LF-CREATE-FORBIDDEN';
  end if;
  if p_lat is not null and p_lng is not null then
    if p_lat < -90 or p_lat > 90 or p_lng < -180 or p_lng > 180 then
      raise exception 'LF-CREATE-LOCATION'
        using detail = 'lat/lng out of range', hint = '22023';
    end if;
    begin
      v_loc := public._canon_geo_point(p_lng, p_lat);
    exception when others then
      raise exception 'LF-CREATE-LOCATION'
        using detail = sqlerrm, hint = sqlstate;
    end;
  end if;
  if v_species is not null and not exists (select 1 from public.species s where s.code = v_species) then
    v_species := 'OTHER';
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
    begin
      v_pet := public._canon_create_found_identity(
        p_name, p_species, p_sex, p_size, p_photo_asset_id, p_estimated_age_months, p_breed_id
      );
    exception when others then
      raise exception 'LF-CREATE-IDENTITY'
        using detail = sqlerrm, hint = sqlstate;
    end;
    if v_pet is null then
      raise exception 'LF-CREATE-IDENTITY';
    end if;
  end if;

  begin
    insert into public.lost_found_alerts (
      kind, pet_id, created_by, locality_id, location_label, species_code, note,
      precise_location, photo_asset_id, incident_at, status, next_wave_at
    ) values (
      v_kind, v_pet, auth.uid(), p_locality_id, v_label, v_species, p_note,
      v_loc, p_photo_asset_id, coalesce(p_incident_at, timezone('utc', now())), 'OPEN',
      timezone('utc', now()) + interval '15 minutes'
    ) returning id into v_id;
  exception when others then
    raise exception 'LF-CREATE-ALERT'
      using detail = sqlerrm, hint = sqlstate;
  end;

  if v_kind = 'FOUND' then
    begin
      perform public._canon_match_found_to_lost(v_id);
    exception when others then
      null;
    end;
  end if;
  begin
    perform public._canon_fanout_lost_found_recipients(v_id);
  exception when others then
    null;
  end;
  begin
    perform public.canon_audit(
      'lost_found.create',
      'lost_found_alerts',
      v_id,
      jsonb_build_object('kind', v_kind, 'pet_id', v_pet)
    );
  exception when others then
    null;
  end;
  return v_id;
end;
$$;

grant execute on function public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid, text, text, text, integer, uuid, text) to authenticated;

create or replace function public.canon_list_lost_found(p_kind text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
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
      'location_label', public._canon_lf_human_location(a.locality_id, a.location_label, a.note),
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

create unique index if not exists leover_verification_pending_uidx
  on public.leover_verification_requests (
    person_id,
    function_code,
    (coalesce(organization_id, '00000000-0000-0000-0000-000000000000'::uuid))
  )
  where status = 'PENDING';

create or replace function public.canon_request_leover_verification(
  p_function_code text,
  p_evidence jsonb default '{}'::jsonb,
  p_organization_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_fn text := upper(btrim(coalesce(p_function_code, '')));
  v_terms boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_fn not in ('RESCUER', 'SHELTER', 'NGO', 'FOSTER', 'PROFESSIONAL', 'VETERINARY', 'BUSINESS') then
    raise exception 'FUNCTION_INVALID';
  end if;
  if not public._canon_person_profile_complete(auth.uid()) then
    raise exception 'PROFILE_INCOMPLETE';
  end if;
  if not public._canon_person_has_contact(auth.uid()) then
    raise exception 'CONTACT_REQUIRED';
  end if;
  v_terms := coalesce((p_evidence ->> 'terms_accepted') in ('true', 'TRUE', '1'), false);
  if not v_terms then
    raise exception 'TERMS_REQUIRED';
  end if;
  if v_fn in ('RESCUER', 'FOSTER') and not public._canon_person_has_base_location(auth.uid()) then
    raise exception 'BASE_LOCATION_REQUIRED';
  end if;
  if v_fn in ('SHELTER', 'NGO', 'VETERINARY', 'BUSINESS') then
    if p_organization_id is null then raise exception 'ORGANIZATION_REQUIRED'; end if;
    if not exists (
      select 1 from public.organizations o
      join public.organization_memberships m on m.organization_id = o.id
     where o.id = p_organization_id
       and m.person_id = auth.uid()
       and m.status = 'ACTIVE'
       and nullif(btrim(o.name), '') is not null
    ) then
      raise exception 'ORGANIZATION_INCOMPLETE';
    end if;
    if not exists (
      select 1 from public.organizations o
       where o.id = p_organization_id and o.base_location is not null
    ) then
      raise exception 'BASE_LOCATION_REQUIRED';
    end if;
  end if;
  if v_fn = 'PROFESSIONAL' then
    insert into public.professional_profiles (person_id)
    values (auth.uid())
    on conflict (person_id) do nothing;
  end if;

  select r.id into v_id
    from public.leover_verification_requests r
   where r.person_id = auth.uid()
     and r.function_code = v_fn
     and r.organization_id is not distinct from p_organization_id
     and r.status = 'PENDING'
   order by r.created_at desc
   limit 1;
  if v_id is not null then
    update public.leover_verification_requests
       set evidence = coalesce(p_evidence, evidence),
           updated_at = timezone('utc', now())
     where id = v_id;
    return v_id;
  end if;

  insert into public.leover_verification_requests (
    subject_kind, person_id, organization_id, function_code, status, evidence
  ) values (
    case when p_organization_id is null then 'PERSON' else 'ORGANIZATION' end,
    auth.uid(), p_organization_id, v_fn, 'PENDING', coalesce(p_evidence, '{}'::jsonb)
  ) returning id into v_id;

  if p_organization_id is null and v_fn in ('RESCUER', 'FOSTER', 'PROFESSIONAL') then
    insert into public.person_capabilities (user_id, capability, active, verification_status, updated_at)
    values (auth.uid(), v_fn, true, 'PENDING', timezone('utc', now()))
    on conflict (user_id, capability) do update
      set verification_status = 'PENDING', updated_at = timezone('utc', now());
  end if;
  if p_organization_id is not null then
    update public.organizations
       set verification_status = case
             when verification_status in ('VERIFIED', 'SUSPENDED') then verification_status
             else 'PENDING'
           end,
           updated_at = timezone('utc', now())
     where id = p_organization_id
       and verification_status = 'NOT_REQUESTED';
  end if;
  begin
    perform public.canon_audit(
      'verification.request',
      'leover_verification_requests',
      v_id,
      jsonb_build_object('function_code', v_fn, 'organization_id', p_organization_id)
    );
  exception when others then
    null;
  end;
  return v_id;
end;
$$;

create or replace function public.canon_list_my_notifications(p_limit integer default 100)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', n.id,
      'user_id', n.user_id,
      'event_id', n.id,
      'category', 'LOST_FOUND',
      'priority', 'HIGH',
      'sensitivity', 'PRIVATE',
      'state', case when n.read_at is null then 'UNREAD' else 'READ' end,
      'title', coalesce(n.payload->>'title', n.kind),
      'body', coalesce(n.payload->>'body', ''),
      'deduplication_key', coalesce(n.payload->>'dedup', 'legacy:' || n.id::text),
      'deep_link_type', coalesce(n.payload->>'deep_link_type', 'LOST_FOUND_CASE'),
      'deep_link_resource_type', coalesce(n.payload->>'deep_link_resource_type', 'LOST_FOUND_CASE'),
      'deep_link_resource_id', coalesce(n.payload->>'deep_link_resource_id', n.payload->>'alert_id'),
      'related_type', n.payload->>'deep_link_resource_type',
      'related_id', coalesce(n.payload->>'deep_link_resource_id', n.payload->>'alert_id'),
      'created_at', n.created_at,
      'updated_at', n.created_at,
      'read_at', n.read_at,
      'is_internal', false
    ) order by n.created_at desc)
    from (
      select * from public.notifications
       where user_id = auth.uid()
       order by created_at desc
       limit least(greatest(coalesce(p_limit, 100), 1), 200)
    ) n
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_list_my_notifications(integer) to authenticated;

create or replace function public.canon_search_professional_patients(p_query text default '')
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_q text := lower(btrim(coalesce(p_query, '')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(row_to_json(x))
    from (
      select
        p.id,
        p.name,
        p.species_code as species,
        p.public_code,
        p.avatar_asset_id,
        holder.display_name as responsible_name,
        p.updated_at
      from public.pets p
      left join lateral (
        select coalesce(per.display_name, per.username) as display_name
          from public.pet_responsibility_links l
          left join public.persons per on per.user_id = l.holder_person_id
         where l.pet_id = p.id and l.status = 'ACTIVE'
         order by case l.role when 'OWNER' then 0 when 'RESPONSIBLE' then 1 else 2 end
         limit 1
      ) holder on true
      where p.lifecycle_status = 'ACTIVE'
        and p.archived_at is null
        and coalesce(p.origin_kind, 'STANDARD') <> 'FOUND_CASE'
        and (
          public._acl_pet_holder(auth.uid(), p.id)
          or p.created_by_user_id = auth.uid()
          or exists (
            select 1
              from public.pet_responsibility_links l
              join public.organization_memberships m
                on m.organization_id = l.holder_organization_id
               and m.person_id = auth.uid()
               and m.status = 'ACTIVE'
             where l.pet_id = p.id
               and l.status = 'ACTIVE'
               and l.holder_kind = 'ORGANIZATION'
          )
          or exists (
            select 1 from public.vitacora_access_grants g
             where g.pet_id = p.id
               and g.grantee_person_id = auth.uid()
               and g.revoked_at is null
               and (g.expires_at is null or g.expires_at > timezone('utc', now()))
          )
        )
        and (
          v_q = ''
          or lower(p.name) like '%' || v_q || '%'
          or lower(coalesce(p.public_code, '')) like '%' || v_q || '%'
        )
      order by p.updated_at desc nulls last
      limit 40
    ) x
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_search_professional_patients(text) to authenticated;

do $$
declare
  r record;
begin
  for r in
    select id from public.lost_found_alerts
     where kind = 'FOUND' and status = 'OPEN'
  loop
    begin
      perform public._canon_match_found_to_lost(r.id);
    exception when others then
      null;
    end;
  end loop;
end;
$$;

create or replace function public.canon_list_community_nearby(
  p_lat double precision,
  p_lng double precision,
  p_filter text default 'ALL',
  p_radius_m integer default 25000
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
declare
  v_loc extensions.geography(Point, 4326);
  v_filter text := upper(btrim(coalesce(p_filter, 'ALL')));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_lat is null or p_lng is null then raise exception 'LOCATION_INVALID'; end if;
  v_loc := public._canon_geo_point(p_lng, p_lat);
  return coalesce((
    select jsonb_agg(row_to_json(x))
    from (
      select * from (
        select 'RESCUER'::text as kind,
               p.user_id::text as id,
               coalesce(p.display_name, p.username, 'Rescatista') as name,
               p.home_locality_id as locality_id,
               c.verification_status,
               extensions.ST_Distance(p.base_location, v_loc) as meters,
               null::text as hours_json,
               false as public_address
          from public.persons p
          join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'RESCUER' and c.active
         where p.base_location is not null
           and extensions.ST_DWithin(p.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'RESCUER')
        union all
        select 'FOSTER', p.user_id::text, coalesce(p.display_name, 'Tránsito'),
               p.home_locality_id, c.verification_status,
               extensions.ST_Distance(p.base_location, v_loc), null, false
          from public.persons p
          join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'FOSTER' and c.active
         where p.base_location is not null
           and extensions.ST_DWithin(p.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'FOSTER')
        union all
        select case when oc.capability = 'SHELTER' then 'SHELTER' else oc.capability end,
               o.id::text, o.name, o.home_locality_id, o.verification_status,
               extensions.ST_Distance(o.base_location, v_loc), null, false
          from public.organizations o
          join public.organization_capabilities oc on oc.organization_id = o.id
         where o.base_location is not null
           and o.lifecycle_status = 'ACTIVE'
           and extensions.ST_DWithin(o.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'SHELTER')
           and oc.capability in ('SHELTER', 'NGO')
        union all
        select 'VETERINARY', o.id::text, o.name, o.home_locality_id, o.verification_status,
               extensions.ST_Distance(o.base_location, v_loc), null, true
          from public.organizations o
          join public.organization_capabilities oc on oc.organization_id = o.id
         where o.base_location is not null
           and o.lifecycle_status = 'ACTIVE'
           and extensions.ST_DWithin(o.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'VETERINARY', 'SERVICES')
           and oc.capability in ('VETERINARY', 'CLINIC', 'VETERINARY_CLINIC', 'BUSINESS', 'PROVIDER')
        union all
        select 'PROFESSIONAL', pr.person_id::text, coalesce(p.display_name, 'Profesional'),
               p.home_locality_id, c.verification_status,
               extensions.ST_Distance(p.base_location, v_loc), null, false
          from public.professional_profiles pr
          join public.persons p on p.user_id = pr.person_id
          left join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'PROFESSIONAL'
         where pr.active
           and p.base_location is not null
           and extensions.ST_DWithin(p.base_location, v_loc, coalesce(p_radius_m, 25000))
           and v_filter in ('ALL', 'NEAR', 'PROFESSIONAL', 'SERVICES')
      ) q
      order by meters
      limit 80
    ) x
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_list_community_nearby(double precision, double precision, text, integer) to authenticated;

comment on function public._canon_match_found_to_lost(uuid) is
  '1096: PostGIS via extensions.ST_Distance. Notify score>=0.55, cap 15.';
comment on function public.canon_request_leover_verification(text, jsonb, uuid) is
  'Idempotent PENDING per person+function+org. Audits verification.request.';
comment on function public.canon_list_community_nearby(double precision, double precision, text, integer) is
  'Unchanged product radius: 25000m, limit 80, ORDER BY meters. Includes VETERINARY_CLINIC/PROVIDER.';
