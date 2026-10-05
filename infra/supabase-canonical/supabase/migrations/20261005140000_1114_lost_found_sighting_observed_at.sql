-- 1114: contribute information with a real observed_at.
-- Not executed by this change. Does not edit 1013.
--
-- lost_found_sightings already stores the case (alert_id), the contributor
-- and the note. observed_at was missing, so the client timestamp had nowhere
-- to live. The canonical write below replaces that missing legacy call.
-- The new write is canon_contribute_lost_found_info. observed_at is timestamptz.

alter table public.lost_found_sightings
  add column if not exists observed_at timestamptz,
  add column if not exists zone_text text,
  add column if not exists species_code text,
  add column if not exists primary_color text,
  add column if not exists media_ref text;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'lost_found_sightings_media_ref_len'
  ) then
    alter table public.lost_found_sightings
      add constraint lost_found_sightings_media_ref_len
      check (media_ref is null or char_length(media_ref) between 1 and 200);
  end if;
end $$;

create index if not exists lost_found_sightings_alert_observed_idx
  on public.lost_found_sightings (alert_id, observed_at desc);

alter table public.lost_found_sightings enable row level security;
revoke all on table public.lost_found_sightings from public, anon, authenticated;

create or replace function public._canon_sighting_can_read(
  p_reporter uuid,
  p_alert_owner uuid,
  p_pet_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.uid() is not null and (
    auth.uid() = p_reporter
    or auth.uid() = p_alert_owner
    or exists (
      select 1
        from public.pet_responsibility_links l
       where l.pet_id = p_pet_id
         and l.status = 'ACTIVE'
         and (
           l.holder_person_id = auth.uid()
           or (
             l.holder_organization_id is not null
             and public._acl_org_permission(auth.uid(), l.holder_organization_id, 'org.edit')
           )
         )
    )
  );
$$;

revoke all on function public._canon_sighting_can_read(uuid, uuid, uuid) from public, anon, authenticated;

create or replace function public._canon_sighting_json(p_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', s.id,
    'alert_id', s.alert_id,
    'lost_found_case_id', s.alert_id,
    'reporter_user_id', s.reporter_user_id,
    'observed_at', s.observed_at,
    'note', s.note,
    'description', s.note,
    'zone_text', s.zone_text,
    'species', s.species_code,
    'primary_color', s.primary_color,
    'media_ref', s.media_ref,
    'created_at', s.created_at,
    'status', 'ACTIVE'
  )
  from public.lost_found_sightings s
  where s.id = p_id;
$$;

revoke all on function public._canon_sighting_json(uuid) from public, anon, authenticated;

create or replace function public.canon_contribute_lost_found_info(
  p_alert_id uuid,
  p_observed_at timestamptz,
  p_note text,
  p_zone_text text,
  p_species_code text default null,
  p_primary_color text default null,
  p_lat double precision default null,
  p_lng double precision default null,
  p_media_ref text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_status text;
  v_id uuid;
  v_loc extensions.geography;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if p_observed_at is null then
    raise exception 'OBSERVED_AT_REQUIRED';
  end if;
  if p_alert_id is null then
    raise exception 'CASE_NOT_FOUND';
  end if;
  select a.status into v_status
    from public.lost_found_alerts a
   where a.id = p_alert_id;
  if v_status is null or v_status not in ('OPEN', 'CLAIMED', 'IN_CARE') then
    raise exception 'CASE_NOT_FOUND';
  end if;
  if p_lat is not null and p_lng is not null then
    v_loc := extensions.ST_SetSRID(extensions.ST_MakePoint(p_lng, p_lat), 4326)::extensions.geography;
  end if;
  insert into public.lost_found_sightings (
    alert_id,
    reporter_user_id,
    precise_location,
    note,
    observed_at,
    zone_text,
    species_code,
    primary_color,
    media_ref
  ) values (
    p_alert_id,
    auth.uid(),
    v_loc,
    nullif(btrim(p_note), ''),
    p_observed_at,
    nullif(btrim(p_zone_text), ''),
    nullif(btrim(p_species_code), ''),
    nullif(btrim(p_primary_color), ''),
    nullif(btrim(p_media_ref), '')
  )
  returning id into v_id;
  return public._canon_sighting_json(v_id);
end;
$$;

create or replace function public.canon_get_lost_found_sighting(p_sighting_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_reporter uuid;
  v_owner uuid;
  v_pet uuid;
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  select s.reporter_user_id, a.created_by, a.pet_id
    into v_reporter, v_owner, v_pet
    from public.lost_found_sightings s
    left join public.lost_found_alerts a on a.id = s.alert_id
   where s.id = p_sighting_id;
  if v_reporter is null then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;
  if not public._canon_sighting_can_read(v_reporter, v_owner, v_pet) then
    raise exception 'FORBIDDEN';
  end if;
  return public._canon_sighting_json(p_sighting_id);
end;
$$;

revoke all on function public.canon_contribute_lost_found_info(uuid, timestamptz, text, text, text, text, double precision, double precision, text) from public, anon;
revoke all on function public.canon_get_lost_found_sighting(uuid) from public, anon;
grant execute on function public.canon_contribute_lost_found_info(uuid, timestamptz, text, text, text, text, double precision, double precision, text) to authenticated;
grant execute on function public.canon_get_lost_found_sighting(uuid) to authenticated;

notify pgrst, 'reload schema';
