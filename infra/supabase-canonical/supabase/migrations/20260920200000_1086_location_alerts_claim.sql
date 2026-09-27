-- 1086: Location foundation + Lost/Found nearest-10 + atomic FOUND claim.
-- Forward-only. Does not edit 1013/1024/1085.
-- Exact coordinates stay behind security-definer RPCs. No public SELECT of precise_location.

-- ---------------------------------------------------------------------------
-- Legal: versioned location treatment (reuses legal_documents + consent events)
-- ---------------------------------------------------------------------------
insert into public.legal_documents (type, version, locale, content_hash, status, consent_code)
select 'CONTEXTUAL', '1', 'es-AR', 'location-treatment-v1', 'DRAFT', 'LOCATION_TREATMENT'
where not exists (
  select 1 from public.legal_documents d
  where d.type = 'CONTEXTUAL'
    and d.version = '1'
    and d.locale = 'es-AR'
    and coalesce(d.consent_code, '') = 'LOCATION_TREATMENT'
);

-- ---------------------------------------------------------------------------
-- Responder base location (internal). Public profiles keep locality only.
-- ---------------------------------------------------------------------------
alter table public.persons
  add column if not exists base_location extensions.geography(Point, 4326) null,
  add column if not exists receive_nearby_cases boolean not null default false;

alter table public.organizations
  add column if not exists base_location extensions.geography(Point, 4326) null,
  add column if not exists receive_nearby_cases boolean not null default false;

create index if not exists persons_base_location_gix
  on public.persons using gist (base_location);
create index if not exists organizations_base_location_gix
  on public.organizations using gist (base_location);

-- ---------------------------------------------------------------------------
-- Alert case columns (reuse lost_found_alerts)
-- ---------------------------------------------------------------------------
alter table public.lost_found_alerts
  add column if not exists claimed_by uuid null references public.persons(user_id),
  add column if not exists claimed_at timestamptz null,
  add column if not exists photo_asset_id uuid null references public.media_assets(id),
  add column if not exists incident_at timestamptz null,
  add column if not exists updated_at timestamptz not null default timezone('utc', now());

alter table public.lost_found_alerts
  drop constraint if exists lost_found_alerts_status_check;
alter table public.lost_found_alerts
  add constraint lost_found_alerts_status_check
  check (status in ('OPEN', 'CLAIMED', 'RESOLVED', 'CANCELLED', 'HIDDEN'));

alter table public.pets
  add column if not exists origin_kind text not null default 'STANDARD';
alter table public.pets
  drop constraint if exists pets_origin_kind_check;
alter table public.pets
  add constraint pets_origin_kind_check
  check (origin_kind in ('STANDARD', 'FOUND_CASE', 'IMPORT'));

-- ---------------------------------------------------------------------------
-- Recipients: unique case + responder (person or organization)
-- ---------------------------------------------------------------------------
create table if not exists public.lost_found_alert_recipients (
  id uuid primary key default gen_random_uuid(),
  alert_id uuid not null references public.lost_found_alerts(id) on delete cascade,
  responder_person_id uuid null references public.persons(user_id),
  responder_organization_id uuid null references public.organizations(id),
  rank integer not null check (rank between 1 and 10),
  distance_meters numeric(12,2) not null check (distance_meters >= 0),
  notified_at timestamptz null,
  viewed_at timestamptz null,
  status text not null default 'NOTIFIED'
    check (status in ('NOTIFIED', 'VIEWED', 'CLAIMED', 'SKIPPED')),
  created_at timestamptz not null default timezone('utc', now()),
  constraint lost_found_alert_recipients_holder_xor check (
    (responder_person_id is not null and responder_organization_id is null)
    or (responder_person_id is null and responder_organization_id is not null)
  )
);

create unique index if not exists lost_found_alert_recipients_person_uidx
  on public.lost_found_alert_recipients (alert_id, responder_person_id)
  where responder_person_id is not null;
create unique index if not exists lost_found_alert_recipients_org_uidx
  on public.lost_found_alert_recipients (alert_id, responder_organization_id)
  where responder_organization_id is not null;
create index if not exists lost_found_alert_recipients_alert_rank_idx
  on public.lost_found_alert_recipients (alert_id, rank);

alter table public.lost_found_alert_recipients enable row level security;
revoke all on table public.lost_found_alert_recipients from anon, authenticated;

-- ---------------------------------------------------------------------------
-- Eligibility: verified independent RESCUER or verified SHELTER/NGO with base geo
-- ---------------------------------------------------------------------------
create or replace function public._canon_alert_responder_eligible(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(p_user_id, '00000000-0000-0000-0000-000000000000'::uuid) is not null
    and (
      exists (
        select 1
          from public.person_capabilities c
          join public.persons p on p.user_id = c.user_id
         where c.user_id = p_user_id
           and c.capability = 'RESCUER'
           and c.active
           and c.verification_status = 'VERIFIED'
           and coalesce(p.receive_nearby_cases, false)
           and p.base_location is not null
           and p.lifecycle_status = 'ACTIVE'
      )
      or exists (
        select 1
          from public.organization_memberships m
          join public.organizations o on o.id = m.organization_id
          join public.organization_capabilities oc on oc.organization_id = o.id
         where m.person_id = p_user_id
           and m.status = 'ACTIVE'
           and o.lifecycle_status = 'ACTIVE'
           and o.verification_status = 'VERIFIED'
           and oc.capability in ('SHELTER', 'NGO')
           and coalesce(o.receive_nearby_cases, false)
           and o.base_location is not null
      )
    );
$$;

create or replace function public._canon_alert_can_see_exact(p_user_id uuid, p_alert public.lost_found_alerts)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_user_id is not null and (
    p_alert.created_by = p_user_id
    or p_alert.claimed_by = p_user_id
    or public._acl_is_admin(p_user_id)
    or public._acl_is_staff(p_user_id)
  );
$$;

create or replace function public._canon_emit_lost_found_notice(
  p_user_id uuid,
  p_alert_id uuid,
  p_kind text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_title text;
  v_body text;
  v_cta text;
begin
  if p_user_id is null then
    return;
  end if;
  if p_kind = 'FOUND' then
    v_title := 'Animal encontrado cerca de tu zona';
    v_body := 'Hay un animal encontrado cerca de tu zona de actividad.';
    v_cta := 'Ver caso';
  else
    v_title := 'Mascota perdida cerca de tu zona';
    v_body := 'Hay una mascota perdida cerca de tu zona de actividad.';
    v_cta := 'Ver alerta';
  end if;
  if to_regprocedure(
    'public.m06_emit_domain_notification(uuid,text,text,text,text,text,text,text,text,text,text,uuid,text,text,text,text,jsonb,text,text,timestamptz,boolean)'
  ) is not null then
    perform public.m06_emit_domain_notification(
      p_recipient_user_id := p_user_id,
      p_event_key := 'lost_found.nearby.' || lower(p_kind),
      p_origin_module := 'LOST_FOUND',
      p_origin_type := 'ALERT',
      p_category := 'LOST_FOUND',
      p_priority := 'HIGH',
      p_sensitivity := 'PRIVATE',
      p_title := v_title,
      p_body := v_body,
      p_resource_type := 'LOST_FOUND_CASE',
      p_resource_id := p_alert_id::text,
      p_organization_id := null,
      p_deep_link_type := 'LOST_FOUND_CASE',
      p_deep_link_resource_type := 'LOST_FOUND_CASE',
      p_deep_link_resource_id := p_alert_id::text,
      p_deep_link_required_permission := null,
      p_payload := jsonb_build_object(
        'alert_id', p_alert_id,
        'kind', p_kind,
        'cta', v_cta
      ),
      p_deduplication_key := 'lost_found:' || p_alert_id::text || ':' || p_user_id::text,
      p_idempotency_key := 'lost_found:' || p_alert_id::text || ':' || p_user_id::text,
      p_expires_at := timezone('utc', now()) + interval '14 days',
      p_is_internal := false
    );
  else
    insert into public.notifications (user_id, kind, payload)
    select p_user_id, 'LOST_FOUND_NEARBY', jsonb_build_object(
      'alert_id', p_alert_id, 'kind', p_kind, 'title', v_title, 'cta', v_cta
    )
    where not exists (
      select 1 from public.notifications n
       where n.user_id = p_user_id
         and n.kind = 'LOST_FOUND_NEARBY'
         and n.payload->>'alert_id' = p_alert_id::text
    );
  end if;
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
  r record;
begin
  select * into v_alert from public.lost_found_alerts where id = p_alert_id;
  if not found or v_alert.precise_location is null then
    return 0;
  end if;

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
      select
        'ORGANIZATION',
        null,
        o.id,
        o.base_location
      from public.organizations o
      join public.organization_capabilities oc on oc.organization_id = o.id
      where oc.capability in ('SHELTER', 'NGO')
        and o.lifecycle_status = 'ACTIVE'
        and o.verification_status = 'VERIFIED'
        and coalesce(o.receive_nearby_cases, false)
        and o.base_location is not null
    )
    select
      holder_kind,
      person_id,
      organization_id,
      ST_Distance(loc, v_alert.precise_location) as meters
    from candidates
    order by meters
    limit 10
  loop
    v_count := v_count + 1;
    insert into public.lost_found_alert_recipients (
      alert_id, responder_person_id, responder_organization_id, rank, distance_meters, notified_at, status
    ) values (
      p_alert_id,
      r.person_id,
      r.organization_id,
      v_count,
      r.meters,
      timezone('utc', now()),
      'NOTIFIED'
    )
    on conflict do nothing;

    if r.person_id is not null then
      perform public._canon_emit_lost_found_notice(r.person_id, p_alert_id, v_alert.kind);
    else
      insert into public.lost_found_alert_recipients (
        alert_id, responder_person_id, responder_organization_id, rank, distance_meters, notified_at, status
      )
      select p_alert_id, m.person_id, null, v_count, r.meters, timezone('utc', now()), 'NOTIFIED'
        from public.organization_memberships m
       where m.organization_id = r.organization_id
         and m.status = 'ACTIVE'
         and m.person_id is distinct from v_alert.created_by
      on conflict do nothing;
      perform public._canon_emit_lost_found_notice(m.person_id, p_alert_id, v_alert.kind)
        from public.organization_memberships m
       where m.organization_id = r.organization_id
         and m.status = 'ACTIVE'
         and m.person_id is distinct from v_alert.created_by;
    end if;
  end loop;
  return v_count;
end;
$$;

-- ---------------------------------------------------------------------------
-- Create / list / get / claim
-- ---------------------------------------------------------------------------
drop function if exists public.canon_create_lost_found(text, uuid, text, text, text);

create or replace function public.canon_create_lost_found(
  p_kind text,
  p_pet_id uuid default null,
  p_locality_id text default null,
  p_species text default null,
  p_note text default null,
  p_lat double precision default null,
  p_lng double precision default null,
  p_incident_at timestamptz default null,
  p_photo_asset_id uuid default null
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
  if v_kind = 'FOUND' then
    v_pet := null;
  end if;
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
  insert into public.lost_found_alerts (
    kind, pet_id, created_by, locality_id, species_code, note,
    precise_location, photo_asset_id, incident_at, status
  ) values (
    v_kind, v_pet, auth.uid(), p_locality_id, p_species, p_note,
    v_loc, p_photo_asset_id, coalesce(p_incident_at, timezone('utc', now())), 'OPEN'
  ) returning id into v_id;
  perform public.canon_audit(
    'lost_found.create',
    'lost_found_alerts',
    v_id,
    jsonb_build_object('kind', v_kind, 'has_precise', v_loc is not null, 'pet_bound', v_pet is not null)
  );
  perform public._canon_fanout_lost_found_recipients(v_id);
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
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', a.id,
      'kind', a.kind,
      'status', a.status,
      'public_code', a.public_code,
      'pet_id', a.pet_id,
      'pet_name', case
        when a.kind = 'FOUND' and a.status = 'OPEN' then null
        else p.name
      end,
      'species', coalesce(a.species_code, p.species_code),
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
             and (
               r.responder_person_id = auth.uid()
               or r.responder_organization_id in (
                 select m.organization_id from public.organization_memberships m
                  where m.person_id = auth.uid() and m.status = 'ACTIVE'
               )
             )
        ),
      'created_at', a.created_at
    ) order by a.created_at desc)
    from public.lost_found_alerts a
    left join public.pets p on p.id = a.pet_id
    where a.status in ('OPEN', 'CLAIMED')
      and (p_kind is null or a.kind = p_kind)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_lost_found_exact(p_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row public.lost_found_alerts%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_row from public.lost_found_alerts where id = p_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if not public._canon_alert_can_see_exact(auth.uid(), v_row) then
    raise exception 'FORBIDDEN';
  end if;
  return jsonb_build_object(
    'id', v_row.id,
    'lat', ST_Y(v_row.precise_location::extensions.geometry),
    'lng', ST_X(v_row.precise_location::extensions.geometry)
  );
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
  v_name text := 'Sin nombre';
  v_species text;
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
  if not exists (
    select 1 from public.lost_found_alert_recipients r
     where r.alert_id = p_id
       and (
         r.responder_person_id = auth.uid()
         or r.responder_organization_id in (
           select m.organization_id from public.organization_memberships m
            where m.person_id = auth.uid() and m.status = 'ACTIVE'
         )
       )
  ) then
    raise exception 'FORBIDDEN';
  end if;

  v_species := coalesce(nullif(btrim(v_row.species_code), ''), 'DOG');
  if not exists (select 1 from public.species s where s.code = v_species) then
    v_species := 'DOG';
  end if;

  insert into public.pets (
    created_by_user_id, name, species_code, origin_kind, birth_precision,
    management_context_kind, management_context_id,
    current_custodian_kind, current_custodian_person_id
  ) values (
    auth.uid(), v_name, v_species, 'FOUND_CASE', 'UNKNOWN',
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

  update public.lost_found_alerts
     set status = 'CLAIMED',
         claimed_by = auth.uid(),
         claimed_at = timezone('utc', now()),
         pet_id = v_pet,
         updated_at = timezone('utc', now())
   where id = p_id
     and status = 'OPEN';
  if not found then
    raise exception 'ALERT_ALREADY_CLAIMED';
  end if;

  update public.lost_found_alert_recipients
     set status = case when responder_person_id = auth.uid() then 'CLAIMED' else 'SKIPPED' end
   where alert_id = p_id;

  perform public.canon_audit(
    'lost_found.claim',
    'lost_found_alerts',
    p_id,
    jsonb_build_object('pet_id', v_pet, 'claimed_by', auth.uid())
  );

  return jsonb_build_object(
    'id', p_id,
    'status', 'CLAIMED',
    'pet_id', v_pet,
    'claimed_by', auth.uid()
  );
end;
$$;

create or replace function public.canon_upsert_responder_base_location(
  p_lat double precision,
  p_lng double precision,
  p_organization_id uuid default null,
  p_receive boolean default true
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_loc extensions.geography(Point, 4326);
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_lat is null or p_lng is null or p_lat < -90 or p_lat > 90 or p_lng < -180 or p_lng > 180 then
    raise exception 'LOCATION_INVALID';
  end if;
  v_loc := ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::extensions.geography;
  if p_organization_id is null then
    update public.persons
       set base_location = v_loc,
           receive_nearby_cases = coalesce(p_receive, true),
           updated_at = timezone('utc', now())
     where user_id = auth.uid();
  else
    if not exists (
      select 1 from public.organization_memberships m
       where m.organization_id = p_organization_id
         and m.person_id = auth.uid()
         and m.status = 'ACTIVE'
    ) then
      raise exception 'FORBIDDEN';
    end if;
    update public.organizations
       set base_location = v_loc,
           receive_nearby_cases = coalesce(p_receive, true),
           updated_at = timezone('utc', now())
     where id = p_organization_id;
  end if;
  return true;
end;
$$;

create or replace function public.canon_get_my_responder_base()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_person jsonb;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select jsonb_build_object(
    'eligible', public._canon_alert_responder_eligible(auth.uid()),
    'has_base_location', p.base_location is not null,
    'receive_nearby_cases', coalesce(p.receive_nearby_cases, false),
    'home_locality_id', p.home_locality_id
  )
    into v_person
    from public.persons p
   where p.user_id = auth.uid();
  return coalesce(v_person, jsonb_build_object('eligible', false));
end;
$$;

create or replace function public.canon_record_location_consent()
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_doc uuid;
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select id into v_doc
    from public.legal_documents
   where type = 'CONTEXTUAL'
     and consent_code = 'LOCATION_TREATMENT'
     and locale = 'es-AR'
   order by version desc
   limit 1;
  if v_doc is null then raise exception 'DOCUMENT_NOT_FOUND'; end if;
  insert into public.legal_consent_events (
    subject_user_id, actor_user_id, document_id, event_type, source, metadata
  ) values (
    auth.uid(), auth.uid(), v_doc, 'CONTEXTUAL_GRANT', 'APP',
    jsonb_build_object('consent_code', 'LOCATION_TREATMENT', 'version', '1')
  ) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.canon_list_lost_found_recipients(p_alert_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (
    select 1 from public.lost_found_alerts a
     where a.id = p_alert_id
       and (a.created_by = auth.uid() or public._acl_is_admin(auth.uid()) or public._acl_is_staff(auth.uid()))
  ) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'rank', r.rank,
      'distance_meters', r.distance_meters,
      'status', r.status,
      'notified_at', r.notified_at
    ) order by r.rank)
    from public.lost_found_alert_recipients r
    where r.alert_id = p_alert_id
  ), '[]'::jsonb);
end;
$$;

revoke all on function public._canon_alert_responder_eligible(uuid) from public, anon;
revoke all on function public._canon_alert_can_see_exact(uuid, public.lost_found_alerts) from public, anon;
revoke all on function public._canon_emit_lost_found_notice(uuid, uuid, text) from public, anon, authenticated;
revoke all on function public._canon_fanout_lost_found_recipients(uuid) from public, anon, authenticated;

grant execute on function public._canon_alert_responder_eligible(uuid) to authenticated;
grant execute on function public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid) to authenticated;
grant execute on function public.canon_list_lost_found(text) to authenticated;
grant execute on function public.canon_get_lost_found_exact(uuid) to authenticated;
grant execute on function public.canon_claim_lost_found(uuid) to authenticated;
grant execute on function public.canon_upsert_responder_base_location(double precision, double precision, uuid, boolean) to authenticated;
grant execute on function public.canon_get_my_responder_base() to authenticated;
grant execute on function public.canon_record_location_consent() to authenticated;
grant execute on function public.canon_list_lost_found_recipients(uuid) to authenticated;

insert into public.security_rate_limit_rpc_bindings (proname, operation_key)
values
  ('canon_claim_lost_found', 'social.lost_found.create'),
  ('canon_upsert_responder_base_location', 'social.lost_found.create')
on conflict (proname) do nothing;
