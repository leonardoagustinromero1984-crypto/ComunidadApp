-- =============================================================================
-- LeoVer M13 — migración 048: avistamientos (detalles) y candidatos de match
-- Bloque 2. Forward-only sobre 001–047. NO modifica lost_found_posts / columnas
-- legacy de lost_found_sightings (tabla lateral 1:1). Sin confirm/reject RPC.
-- Sin service_role. Sin autoconfirmación. LOCAL ONLY hasta apply remoto autorizado.
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 0. Permisos M03 lostfound.* (org catalog) + plataforma (has_permission)
-- ---------------------------------------------------------------------------
insert into public.organization_permissions (code, description) values
  ('lostfound.sighting.read', 'Ver avistamientos Lost/Found (M13)'),
  ('lostfound.sighting.create', 'Crear avistamientos Lost/Found (M13)'),
  ('lostfound.sighting.manage_own', 'Gestionar avistamientos propios Lost/Found (M13)'),
  ('lostfound.sighting.moderate', 'Moderar avistamientos Lost/Found (M13)'),
  ('lostfound.match.read', 'Ver candidatos de coincidencia Lost/Found (M13)'),
  ('lostfound.match.review', 'Revisar candidatos de coincidencia Lost/Found (M13)'),
  ('lostfound.match.confirm', 'Confirmar/rechazar coincidencias Lost/Found (M13; Bloque 3)')
on conflict (code) do nothing;

-- OWNER/ADMIN/MANAGER: lectura, gestión, revisión y confirmación.
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in (
    'lostfound.sighting.read',
    'lostfound.sighting.create',
    'lostfound.sighting.manage_own',
    'lostfound.sighting.moderate',
    'lostfound.match.read',
    'lostfound.match.review',
    'lostfound.match.confirm'
  )
on conflict do nothing;

-- MEMBER: solo lectura.
insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER'
  and p.code in (
    'lostfound.sighting.read',
    'lostfound.match.read'
  )
on conflict do nothing;

-- Plataforma M04: códigos en public.permissions para has_permission().
insert into public.permissions (code, description) values
  ('lostfound.sighting.read', 'Ver avistamientos Lost/Found (M13)'),
  ('lostfound.sighting.create', 'Crear avistamientos Lost/Found (M13)'),
  ('lostfound.sighting.manage_own', 'Gestionar avistamientos propios Lost/Found (M13)'),
  ('lostfound.sighting.moderate', 'Moderar avistamientos Lost/Found (M13)'),
  ('lostfound.match.read', 'Ver candidatos de coincidencia Lost/Found (M13)'),
  ('lostfound.match.review', 'Revisar candidatos de coincidencia Lost/Found (M13)'),
  ('lostfound.match.confirm', 'Confirmar/rechazar coincidencias Lost/Found (M13; Bloque 3)')
on conflict (code) do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.platform_roles r
cross join public.permissions p
where r.code in ('MODERATOR', 'ADMIN', 'SUPERADMIN')
  and p.code in (
    'lostfound.sighting.read',
    'lostfound.sighting.moderate',
    'lostfound.match.read',
    'lostfound.match.review',
    'lostfound.match.confirm'
  )
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- 1. Helpers mínimos previos a constraints de media
-- ---------------------------------------------------------------------------
create or replace function public._m13_require_auth()
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v uuid := auth.uid();
begin
  if v is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  return v;
end;
$$;

create or replace function public._m13_is_safe_media_ref(p_ref text)
returns boolean
language sql
immutable
parallel safe
as $$
  select p_ref is not null
    and length(trim(p_ref)) > 0
    and p_ref ~* '^(m05://|file_asset:)'
    and p_ref !~* '^https?://'
    and p_ref !~* 'object/public/leover';
$$;

create or replace function public._m13_validate_media_refs(p_refs text[])
returns void
language plpgsql
immutable
as $$
declare
  v_ref text;
begin
  if p_refs is null then
    raise exception 'MEDIA_REF_INVALID';
  end if;
  foreach v_ref in array p_refs loop
    if not public._m13_is_safe_media_ref(v_ref) then
      raise exception 'MEDIA_REF_INVALID';
    end if;
  end loop;
end;
$$;

create or replace function public._m13_media_refs_ok(p_refs text[])
returns boolean
language sql
immutable
parallel safe
as $$
  select p_refs is not null
    and not exists (
      select 1
      from unnest(p_refs) as t(ref)
      where not public._m13_is_safe_media_ref(t.ref)
    );
$$;

create or replace function public._m13_haversine_km(
  p_lat1 double precision,
  p_lon1 double precision,
  p_lat2 double precision,
  p_lon2 double precision
) returns numeric
language sql
immutable
parallel safe
as $$
  select (
    6371.0 * 2.0 * atan2(
      sqrt(
        power(sin(radians(p_lat2 - p_lat1) / 2.0), 2)
        + cos(radians(p_lat1)) * cos(radians(p_lat2))
          * power(sin(radians(p_lon2 - p_lon1) / 2.0), 2)
      ),
      sqrt(
        1.0 - (
          power(sin(radians(p_lat2 - p_lat1) / 2.0), 2)
          + cos(radians(p_lat1)) * cos(radians(p_lat2))
            * power(sin(radians(p_lon2 - p_lon1) / 2.0), 2)
        )
      )
    )
  )::numeric;
$$;

create or replace function public._m13_level_from_score(p_score int)
returns text
language sql
immutable
parallel safe
as $$
  select case
    when p_score >= 70 then 'HIGH'
    when p_score >= 40 then 'MEDIUM'
    else 'LOW'
  end;
$$;

-- ---------------------------------------------------------------------------
-- 2. Tablas M13 (lateral + candidatos + decisiones + historial)
-- ---------------------------------------------------------------------------
create table if not exists public.lost_found_sighting_details (
  sighting_id uuid primary key
    references public.lost_found_sightings (id) on delete cascade,
  species text not null,
  breed_text text null,
  primary_color text not null,
  secondary_color text null,
  sex text null,
  size text null,
  observed_at timestamptz not null,
  zone_text text not null,
  latitude_approx double precision null,
  longitude_approx double precision null,
  accuracy_meters double precision null,
  description text not null,
  media_refs text[] not null default '{}'::text[],
  status text not null default 'ACTIVE',
  updated_at timestamptz not null default timezone('utc', now()),
  constraint lost_found_sighting_details_sex_chk
    check (sex is null or sex = any (array['MALE','FEMALE','UNKNOWN']::text[])),
  constraint lost_found_sighting_details_size_chk
    check (size is null or size = any (array['SMALL','MEDIUM','LARGE']::text[])),
  constraint lost_found_sighting_details_status_chk
    check (status = any (array['ACTIVE','CONFIRMED','DISMISSED','WITHDRAWN','EXPIRED']::text[])),
  constraint lost_found_sighting_details_accuracy_chk
    check (accuracy_meters is null or accuracy_meters >= 0),
  constraint lost_found_sighting_details_coords_chk
    check (
      (latitude_approx is null and longitude_approx is null)
      or (latitude_approx is not null and longitude_approx is not null)
    ),
  constraint lost_found_sighting_details_species_chk
    check (char_length(trim(species)) > 0),
  constraint lost_found_sighting_details_primary_color_chk
    check (char_length(trim(primary_color)) > 0),
  constraint lost_found_sighting_details_zone_chk
    check (char_length(trim(zone_text)) > 0),
  constraint lost_found_sighting_details_description_chk
    check (char_length(trim(description)) > 0),
  constraint lost_found_sighting_details_media_refs_chk
    check (public._m13_media_refs_ok(media_refs))
);

create index if not exists lost_found_sighting_details_status_idx
  on public.lost_found_sighting_details (status);
create index if not exists lost_found_sighting_details_observed_at_idx
  on public.lost_found_sighting_details (observed_at desc);

create table if not exists public.lost_found_match_candidates (
  id uuid primary key default gen_random_uuid(),
  case_id uuid not null references public.lost_found_posts (id) on delete cascade,
  sighting_id uuid not null references public.lost_found_sightings (id) on delete cascade,
  score int not null,
  level text not null,
  reasons text[] not null default '{}'::text[],
  status text not null default 'PROPOSED',
  algorithm_version text not null default 'm13-b1-local-v1',
  created_by uuid null references public.users (id) on delete set null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint lost_found_match_candidates_case_sighting_uniq unique (case_id, sighting_id),
  constraint lost_found_match_candidates_score_chk check (score between 0 and 100),
  constraint lost_found_match_candidates_level_chk
    check (level = any (array['LOW','MEDIUM','HIGH']::text[])),
  constraint lost_found_match_candidates_status_chk
    check (status = any (array[
      'PROPOSED','UNDER_REVIEW','CONFIRMED','REJECTED','INCONCLUSIVE','WITHDRAWN','EXPIRED'
    ]::text[])),
  constraint lost_found_match_candidates_score_level_chk check (
    (level = 'LOW' and score between 0 and 39)
    or (level = 'MEDIUM' and score between 40 and 69)
    or (level = 'HIGH' and score between 70 and 100)
  )
);

create index if not exists lost_found_match_candidates_case_status_idx
  on public.lost_found_match_candidates (case_id, status);
create index if not exists lost_found_match_candidates_sighting_idx
  on public.lost_found_match_candidates (sighting_id);
create index if not exists lost_found_match_candidates_created_at_idx
  on public.lost_found_match_candidates (created_at desc);

create table if not exists public.lost_found_match_decisions (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null
    references public.lost_found_match_candidates (id) on delete cascade,
  decision text not null,
  actor_user_id uuid not null references public.users (id) on delete restrict,
  actor_authority text not null,
  reason_code text not null,
  note_private text null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint lost_found_match_decisions_decision_chk
    check (decision = any (array['CONFIRMED','REJECTED','INCONCLUSIVE']::text[])),
  constraint lost_found_match_decisions_authority_chk
    check (char_length(trim(actor_authority)) > 0),
  constraint lost_found_match_decisions_reason_chk
    check (char_length(trim(reason_code)) > 0),
  constraint lost_found_match_decisions_note_chk
    check (note_private is null or char_length(note_private) <= 2000)
);

create index if not exists lost_found_match_decisions_candidate_idx
  on public.lost_found_match_decisions (candidate_id, created_at desc);

create table if not exists public.lost_found_match_status_history (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null
    references public.lost_found_match_candidates (id) on delete cascade,
  from_status text null,
  to_status text not null,
  changed_by uuid null references public.users (id) on delete set null,
  reason text null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint lost_found_match_status_history_to_chk
    check (char_length(trim(to_status)) > 0)
);

create index if not exists lost_found_match_status_history_candidate_idx
  on public.lost_found_match_status_history (candidate_id);
create index if not exists lost_found_match_status_history_created_at_idx
  on public.lost_found_match_status_history (created_at desc);

-- ---------------------------------------------------------------------------
-- 3. Helpers de autoridad, JSON y matching
-- ---------------------------------------------------------------------------
create or replace function public._m13_case_is_active(p_case_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.lost_found_posts p
    where p.id = p_case_id
      and p.status = 'ACTIVE'
  );
$$;

create or replace function public._m13_is_case_owner(p_case_id uuid, p_actor uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.lost_found_posts p
    where p.id = p_case_id
      and p.author_id = p_actor
  );
$$;

create or replace function public._m13_can_moderate(p_actor uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    public.has_permission('lostfound.sighting.moderate')
    or public.has_permission('lostfound.match.review'),
    false
  )
  and p_actor is not null
  and p_actor = auth.uid();
$$;

-- Nota: has_permission usa auth.uid(); p_actor debe coincidir con el actor de sesión.
create or replace function public._m13_can_manage_case(p_case_id uuid, p_actor uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_actor is null or p_case_id is null then
    return false;
  end if;
  if p_actor <> auth.uid() then
    return false;
  end if;
  if public._m13_is_case_owner(p_case_id, p_actor) then
    return true;
  end if;
  if public.has_permission('lostfound.match.review')
     or public.has_permission('lostfound.sighting.moderate')
     or public.has_permission('lostfound.match.read') then
    return true;
  end if;
  return false;
end;
$$;

create or replace function public._m13_can_write_case(p_case_id uuid, p_actor uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if p_actor is null or p_case_id is null then
    return false;
  end if;
  if p_actor <> auth.uid() then
    return false;
  end if;
  if public._m13_is_case_owner(p_case_id, p_actor) then
    return true;
  end if;
  if public.has_permission('lostfound.match.review')
     or public.has_permission('lostfound.sighting.moderate') then
    return true;
  end if;
  return false;
end;
$$;

create or replace function public._m13_zone_text_overlap(p_a text, p_b text)
returns boolean
language plpgsql
immutable
as $$
declare
  na text;
  nb text;
  tok text;
begin
  na := lower(trim(regexp_replace(coalesce(p_a, ''), '\s+', ' ', 'g')));
  nb := lower(trim(regexp_replace(coalesce(p_b, ''), '\s+', ' ', 'g')));
  if na = '' or nb = '' then
    return false;
  end if;
  if position(nb in na) > 0 or position(na in nb) > 0 then
    return true;
  end if;
  foreach tok in array regexp_split_to_array(na, ' ') loop
    if char_length(tok) >= 3 and position(tok in nb) > 0 then
      return true;
    end if;
  end loop;
  return false;
end;
$$;

create or replace function public._m13_public_sighting_json(
  p_sighting public.lost_found_sightings,
  p_details public.lost_found_sighting_details
) returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_day timestamptz;
  v_preview text;
begin
  -- Approx day (UTC date boundary), sin hora exacta ni coords exactas ni reporter.
  v_day := date_trunc('day', p_details.observed_at at time zone 'utc') at time zone 'utc';
  v_preview := trim(p_details.description);
  if char_length(v_preview) > 120 then
    v_preview := left(v_preview, 119) || '…';
  end if;

  return jsonb_build_object(
    'id', p_sighting.id,
    'lost_found_case_id', p_sighting.post_id,
    'species', p_details.species,
    'breed_text', p_details.breed_text,
    'primary_color', p_details.primary_color,
    'secondary_color', p_details.secondary_color,
    'sex', p_details.sex,
    'size', p_details.size,
    'observed_at_approx_day', v_day,
    'zone_text', p_details.zone_text,
    'description_preview', v_preview,
    'media_refs', to_jsonb(coalesce(p_details.media_refs, '{}'::text[])),
    'status', p_details.status,
    'has_approximate_location',
      (p_details.latitude_approx is not null and p_details.longitude_approx is not null),
    'created_at', p_sighting.created_at
  );
end;
$$;

create or replace function public._m13_managed_sighting_json(
  p_sighting public.lost_found_sightings,
  p_details public.lost_found_sighting_details
) returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return public._m13_public_sighting_json(p_sighting, p_details)
    || jsonb_build_object(
      'reporter_user_id', p_sighting.reporter_id,
      'reporter_name', p_sighting.reporter_name,
      'observed_at', p_details.observed_at,
      'latitude_approx', p_details.latitude_approx,
      'longitude_approx', p_details.longitude_approx,
      'accuracy_meters', p_details.accuracy_meters,
      'description', p_details.description,
      'updated_at', p_details.updated_at,
      'note', p_sighting.note,
      'location_text', p_sighting.location_text
    );
end;
$$;

create or replace function public._m13_candidate_json(
  p_c public.lost_found_match_candidates
) returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', p_c.id,
    'case_id', p_c.case_id,
    'sighting_id', p_c.sighting_id,
    'score', p_c.score,
    'level', p_c.level,
    'reasons', to_jsonb(coalesce(p_c.reasons, '{}'::text[])),
    'status', p_c.status,
    'algorithm_version', p_c.algorithm_version,
    'created_by', p_c.created_by,
    'created_at', p_c.created_at,
    'updated_at', p_c.updated_at
  );
$$;

-- Scoring alineado a Android Bloque 1 (M13MatchingEngine). Sin autoconfirmación.
create or replace function public._m13_score_pair(
  p_sighting_id uuid,
  p_case_id uuid
) returns table (
  score int,
  level text,
  reasons text[]
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_s public.lost_found_sightings;
  v_d public.lost_found_sighting_details;
  v_c public.lost_found_posts;
  v_score int := 0;
  v_reasons text[] := '{}'::text[];
  v_case_time timestamptz;
  v_delta_days numeric;
  v_geo_ok boolean := false;
  v_zone_ok boolean := false;
  v_breed text;
  v_color text;
  v_desc text;
  v_pet text;
begin
  select * into v_s from public.lost_found_sightings where id = p_sighting_id;
  if not found then
    return;
  end if;

  select * into v_d from public.lost_found_sighting_details where sighting_id = p_sighting_id;
  if not found then
    return;
  end if;

  if v_d.status <> 'ACTIVE' then
    return;
  end if;

  select * into v_c from public.lost_found_posts where id = p_case_id;
  if not found then
    return;
  end if;

  if v_c.status <> 'ACTIVE' then
    return;
  end if;

  -- Especie obligatoria (match exacto case-insensitive).
  if lower(trim(v_d.species)) <> lower(trim(v_c.species)) then
    return;
  end if;

  v_reasons := array_append(v_reasons, 'SPECIES_MATCH');
  v_score := v_score + 20;

  -- Ventana temporal 30 días.
  v_case_time := coalesce(v_c.created_at, timezone('utc', now()));
  v_delta_days := abs(extract(epoch from (v_d.observed_at - v_case_time))) / 86400.0;
  if v_delta_days > 30 then
    return;
  end if;
  v_reasons := array_append(v_reasons, 'TIME_PROXIMITY');
  v_score := v_score + 20;

  -- Zona: haversine <= 10 km si ambas coords; si no, overlap de zone_text.
  if v_d.latitude_approx is not null
     and v_d.longitude_approx is not null
     and v_c.latitude is not null
     and v_c.longitude is not null then
    if public._m13_haversine_km(
         v_d.latitude_approx, v_d.longitude_approx, v_c.latitude, v_c.longitude
       ) <= 10 then
      v_geo_ok := true;
    else
      -- Coords presentes pero fuera de radio: no candidato.
      return;
    end if;
  end if;

  v_zone_ok := public._m13_zone_text_overlap(v_d.zone_text, v_c.location);
  if v_geo_ok or v_zone_ok then
    v_reasons := array_append(v_reasons, 'ZONE_PROXIMITY');
    v_score := v_score + 25;
  end if;

  v_desc := lower(coalesce(v_c.description, ''));
  v_pet := lower(coalesce(v_c.pet_name, ''));

  v_breed := lower(trim(coalesce(v_d.breed_text, '')));
  if v_breed <> '' and position(v_breed in v_desc) > 0 then
    v_reasons := array_append(v_reasons, 'BREED_MATCH');
    v_score := v_score + 10;
  end if;

  v_color := lower(trim(v_d.primary_color));
  if v_color <> '' and (position(v_color in v_desc) > 0 or position(v_color in v_pet) > 0) then
    v_reasons := array_append(v_reasons, 'COLOR_MATCH');
    v_score := v_score + 15;
  end if;

  if v_d.sex is not null and position(lower(v_d.sex) in v_desc) > 0 then
    v_reasons := array_append(v_reasons, 'SEX_MATCH');
    v_score := v_score + 5;
  end if;

  if v_d.size is not null and position(lower(v_d.size) in v_desc) > 0 then
    v_reasons := array_append(v_reasons, 'SIZE_MATCH');
    v_score := v_score + 5;
  end if;

  -- Manual link si el avistamiento legacy apunta al mismo caso.
  if v_s.post_id = p_case_id then
    v_reasons := array_append(v_reasons, 'MANUAL_LINK');
    v_score := v_score + 20;
  end if;

  v_score := greatest(0, least(100, v_score));
  score := v_score;
  level := public._m13_level_from_score(v_score);
  reasons := (
    select array_agg(distinct x order by x)
    from unnest(v_reasons) as x
  );
  return next;
end;
$$;

create or replace function public._m13_append_candidate_history(
  p_candidate_id uuid,
  p_from_status text,
  p_to_status text,
  p_changed_by uuid,
  p_reason text
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.lost_found_match_status_history (
    candidate_id, from_status, to_status, changed_by, reason
  ) values (
    p_candidate_id, p_from_status, p_to_status, p_changed_by, p_reason
  );
end;
$$;

create or replace function public._m13_upsert_candidate(
  p_case_id uuid,
  p_sighting_id uuid,
  p_score int,
  p_level text,
  p_reasons text[],
  p_actor uuid
) returns public.lost_found_match_candidates
language plpgsql
security definer
set search_path = public
as $$
declare
  v_existing public.lost_found_match_candidates;
  v_row public.lost_found_match_candidates;
  v_terminal boolean;
begin
  select * into v_existing
  from public.lost_found_match_candidates
  where case_id = p_case_id and sighting_id = p_sighting_id;

  if not found then
    insert into public.lost_found_match_candidates (
      case_id, sighting_id, score, level, reasons, status, created_by
    ) values (
      p_case_id, p_sighting_id, p_score, p_level,
      coalesce(p_reasons, '{}'::text[]), 'PROPOSED', p_actor
    )
    returning * into v_row;

    perform public._m13_append_candidate_history(
      v_row.id, null, 'PROPOSED', p_actor, 'MATCH_PROPOSED'
    );
    return v_row;
  end if;

  v_terminal := v_existing.status = any (array[
    'CONFIRMED','REJECTED','INCONCLUSIVE','WITHDRAWN','EXPIRED'
  ]::text[]);

  if v_terminal then
    -- Idempotente: no sobrescribe estado terminal; actualiza score explicativo.
    update public.lost_found_match_candidates
    set score = p_score,
        level = p_level,
        reasons = coalesce(p_reasons, '{}'::text[]),
        updated_at = timezone('utc', now())
    where id = v_existing.id
    returning * into v_row;
    return v_row;
  end if;

  update public.lost_found_match_candidates
  set score = p_score,
      level = p_level,
      reasons = coalesce(p_reasons, '{}'::text[]),
      updated_at = timezone('utc', now())
  where id = v_existing.id
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public._m13_require_reporter_name(p_actor uuid)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_name text;
begin
  select u.name into v_name from public.users u where u.id = p_actor;
  if v_name is null or char_length(trim(v_name)) = 0 then
    return 'Usuario';
  end if;
  return trim(v_name);
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. RPC cliente — avistamientos
-- ---------------------------------------------------------------------------
create or replace function public.m13_create_sighting(
  p_case_id uuid,
  p_species text,
  p_breed_text text,
  p_primary_color text,
  p_secondary_color text,
  p_sex text,
  p_size text,
  p_observed_at timestamptz,
  p_zone_text text,
  p_latitude_approx double precision,
  p_longitude_approx double precision,
  p_accuracy_meters double precision,
  p_description text,
  p_media_refs text[]
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_case public.lost_found_posts;
  v_sighting public.lost_found_sightings;
  v_details public.lost_found_sighting_details;
  v_media text[] := coalesce(p_media_refs, '{}'::text[]);
  v_scored record;
begin
  if p_case_id is null then
    raise exception 'CASE_REQUIRED';
  end if;

  select * into v_case from public.lost_found_posts where id = p_case_id;
  if not found then
    raise exception 'CASE_NOT_FOUND';
  end if;

  if trim(coalesce(p_species, '')) = '' then
    raise exception 'SIGHTING_INVALID';
  end if;
  if trim(coalesce(p_primary_color, '')) = '' then
    raise exception 'SIGHTING_INVALID';
  end if;
  if trim(coalesce(p_zone_text, '')) = '' then
    raise exception 'SIGHTING_INVALID';
  end if;
  if trim(coalesce(p_description, '')) = '' then
    raise exception 'SIGHTING_INVALID';
  end if;
  if p_observed_at is null then
    raise exception 'SIGHTING_INVALID';
  end if;
  if p_sex is not null and p_sex not in ('MALE','FEMALE','UNKNOWN') then
    raise exception 'SIGHTING_INVALID';
  end if;
  if p_size is not null and p_size not in ('SMALL','MEDIUM','LARGE') then
    raise exception 'SIGHTING_INVALID';
  end if;
  if (p_latitude_approx is null) <> (p_longitude_approx is null) then
    raise exception 'SIGHTING_INVALID';
  end if;
  if p_accuracy_meters is not null and p_accuracy_meters < 0 then
    raise exception 'SIGHTING_INVALID';
  end if;

  perform public._m13_validate_media_refs(v_media);

  insert into public.lost_found_sightings (
    post_id, reporter_id, reporter_name, note, location_text, latitude, longitude
  ) values (
    p_case_id,
    v_actor,
    public._m13_require_reporter_name(v_actor),
    trim(p_description),
    trim(p_zone_text),
    p_latitude_approx,
    p_longitude_approx
  )
  returning * into v_sighting;

  insert into public.lost_found_sighting_details (
    sighting_id, species, breed_text, primary_color, secondary_color, sex, size,
    observed_at, zone_text, latitude_approx, longitude_approx, accuracy_meters,
    description, media_refs, status
  ) values (
    v_sighting.id,
    trim(p_species),
    nullif(trim(coalesce(p_breed_text, '')), ''),
    trim(p_primary_color),
    nullif(trim(coalesce(p_secondary_color, '')), ''),
    p_sex,
    p_size,
    p_observed_at,
    trim(p_zone_text),
    p_latitude_approx,
    p_longitude_approx,
    p_accuracy_meters,
    trim(p_description),
    v_media,
    'ACTIVE'
  )
  returning * into v_details;

  -- Generar candidato vs caso vinculado si el caso está ACTIVE (nunca auto-confirm).
  if public._m13_case_is_active(p_case_id) then
    for v_scored in
      select * from public._m13_score_pair(v_sighting.id, p_case_id)
    loop
      perform public._m13_upsert_candidate(
        p_case_id, v_sighting.id, v_scored.score, v_scored.level, v_scored.reasons, v_actor
      );
    end loop;
  end if;

  return public._m13_managed_sighting_json(v_sighting, v_details);
end;
$$;

create or replace function public.m13_update_my_sighting(
  p_sighting_id uuid,
  p_species text,
  p_breed_text text,
  p_primary_color text,
  p_secondary_color text,
  p_sex text,
  p_size text,
  p_observed_at timestamptz,
  p_zone_text text,
  p_latitude_approx double precision,
  p_longitude_approx double precision,
  p_accuracy_meters double precision,
  p_description text,
  p_media_refs text[]
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_sighting public.lost_found_sightings;
  v_details public.lost_found_sighting_details;
  v_media text[];
begin
  select * into v_sighting from public.lost_found_sightings where id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;
  if v_sighting.reporter_id <> v_actor then
    raise exception 'SIGHTING_FORBIDDEN';
  end if;

  select * into v_details from public.lost_found_sighting_details where sighting_id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;
  if v_details.status <> 'ACTIVE' then
    raise exception 'SIGHTING_NOT_ACTIVE';
  end if;

  if trim(coalesce(p_species, '')) = ''
     or trim(coalesce(p_primary_color, '')) = ''
     or trim(coalesce(p_zone_text, '')) = ''
     or trim(coalesce(p_description, '')) = ''
     or p_observed_at is null then
    raise exception 'SIGHTING_INVALID';
  end if;
  if p_sex is not null and p_sex not in ('MALE','FEMALE','UNKNOWN') then
    raise exception 'SIGHTING_INVALID';
  end if;
  if p_size is not null and p_size not in ('SMALL','MEDIUM','LARGE') then
    raise exception 'SIGHTING_INVALID';
  end if;
  if (p_latitude_approx is null) <> (p_longitude_approx is null) then
    raise exception 'SIGHTING_INVALID';
  end if;
  if p_accuracy_meters is not null and p_accuracy_meters < 0 then
    raise exception 'SIGHTING_INVALID';
  end if;

  v_media := coalesce(p_media_refs, '{}'::text[]);
  perform public._m13_validate_media_refs(v_media);

  update public.lost_found_sightings
  set note = trim(p_description),
      location_text = trim(p_zone_text),
      latitude = p_latitude_approx,
      longitude = p_longitude_approx
  where id = p_sighting_id
  returning * into v_sighting;

  update public.lost_found_sighting_details
  set species = trim(p_species),
      breed_text = nullif(trim(coalesce(p_breed_text, '')), ''),
      primary_color = trim(p_primary_color),
      secondary_color = nullif(trim(coalesce(p_secondary_color, '')), ''),
      sex = p_sex,
      size = p_size,
      observed_at = p_observed_at,
      zone_text = trim(p_zone_text),
      latitude_approx = p_latitude_approx,
      longitude_approx = p_longitude_approx,
      accuracy_meters = p_accuracy_meters,
      description = trim(p_description),
      media_refs = v_media,
      updated_at = timezone('utc', now())
  where sighting_id = p_sighting_id
  returning * into v_details;

  return public._m13_managed_sighting_json(v_sighting, v_details);
end;
$$;

create or replace function public.m13_withdraw_my_sighting(p_sighting_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_sighting public.lost_found_sightings;
  v_details public.lost_found_sighting_details;
begin
  select * into v_sighting from public.lost_found_sightings where id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;
  if v_sighting.reporter_id <> v_actor then
    raise exception 'SIGHTING_FORBIDDEN';
  end if;

  select * into v_details from public.lost_found_sighting_details where sighting_id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;
  if v_details.status = 'WITHDRAWN' then
    return public._m13_managed_sighting_json(v_sighting, v_details);
  end if;
  if v_details.status <> 'ACTIVE' then
    raise exception 'SIGHTING_NOT_ACTIVE';
  end if;

  update public.lost_found_sighting_details
  set status = 'WITHDRAWN',
      updated_at = timezone('utc', now())
  where sighting_id = p_sighting_id
  returning * into v_details;

  return public._m13_managed_sighting_json(v_sighting, v_details);
end;
$$;

create or replace function public.m13_get_sighting(p_sighting_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_sighting public.lost_found_sightings;
  v_details public.lost_found_sighting_details;
begin
  select * into v_sighting from public.lost_found_sightings where id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;
  select * into v_details from public.lost_found_sighting_details where sighting_id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;

  if v_sighting.reporter_id = v_actor
     or public._m13_can_write_case(v_sighting.post_id, v_actor)
     or public.has_permission('lostfound.sighting.read') then
    return public._m13_managed_sighting_json(v_sighting, v_details);
  end if;

  return public._m13_public_sighting_json(v_sighting, v_details);
end;
$$;

create or replace function public.m13_list_public_sightings(
  p_limit int default 50,
  p_offset int default 0
) returns setof jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_limit int := greatest(1, least(coalesce(p_limit, 50), 100));
  v_offset int := greatest(0, coalesce(p_offset, 0));
begin
  perform public._m13_require_auth();

  return query
  select public._m13_public_sighting_json(s, d)
  from public.lost_found_sighting_details d
  join public.lost_found_sightings s on s.id = d.sighting_id
  where d.status = 'ACTIVE'
  order by d.observed_at desc
  limit v_limit offset v_offset;
end;
$$;

create or replace function public.m13_list_my_sightings()
returns setof jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
begin
  return query
  select public._m13_managed_sighting_json(s, d)
  from public.lost_found_sighting_details d
  join public.lost_found_sightings s on s.id = d.sighting_id
  where s.reporter_id = v_actor
  order by d.observed_at desc;
end;
$$;

create or replace function public.m13_list_managed_sightings()
returns setof jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
begin
  return query
  select public._m13_managed_sighting_json(s, d)
  from public.lost_found_sighting_details d
  join public.lost_found_sightings s on s.id = d.sighting_id
  join public.lost_found_posts p on p.id = s.post_id
  where p.author_id = v_actor
     or public.has_permission('lostfound.sighting.moderate')
     or public.has_permission('lostfound.match.review')
  order by d.observed_at desc;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. RPC cliente — candidatos (sin confirm/reject)
-- ---------------------------------------------------------------------------
create or replace function public.m13_generate_match_candidates_for_sighting(
  p_sighting_id uuid
) returns setof jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_sighting public.lost_found_sightings;
  v_details public.lost_found_sighting_details;
  v_case record;
  v_scored record;
  v_row public.lost_found_match_candidates;
begin
  select * into v_sighting from public.lost_found_sightings where id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;
  select * into v_details from public.lost_found_sighting_details where sighting_id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;
  if v_details.status <> 'ACTIVE' then
    raise exception 'SIGHTING_NOT_ACTIVE';
  end if;

  if not (
    v_sighting.reporter_id = v_actor
    or public._m13_can_write_case(v_sighting.post_id, v_actor)
  ) then
    raise exception 'MATCH_GENERATION_NOT_ALLOWED';
  end if;

  for v_case in
    select p.id
    from public.lost_found_posts p
    where p.status = 'ACTIVE'
    order by
      case when p.id = v_sighting.post_id then 0 else 1 end,
      p.created_at desc
  loop
    for v_scored in
      select * from public._m13_score_pair(p_sighting_id, v_case.id)
    loop
      v_row := public._m13_upsert_candidate(
        v_case.id, p_sighting_id, v_scored.score, v_scored.level, v_scored.reasons, v_actor
      );
      return next public._m13_candidate_json(v_row);
    end loop;
  end loop;
end;
$$;

create or replace function public.m13_generate_match_candidates_for_case(
  p_case_id uuid
) returns setof jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_case public.lost_found_posts;
  v_sighting_id uuid;
  v_scored record;
  v_row public.lost_found_match_candidates;
begin
  select * into v_case from public.lost_found_posts where id = p_case_id;
  if not found then
    raise exception 'CASE_NOT_FOUND';
  end if;
  if v_case.status <> 'ACTIVE' then
    raise exception 'CASE_NOT_ACTIVE';
  end if;
  if not public._m13_can_write_case(p_case_id, v_actor) then
    raise exception 'MATCH_GENERATION_NOT_ALLOWED';
  end if;

  for v_sighting_id in
    select d.sighting_id
    from public.lost_found_sighting_details d
    where d.status = 'ACTIVE'
    order by d.observed_at desc
  loop
    for v_scored in
      select * from public._m13_score_pair(v_sighting_id, p_case_id)
    loop
      v_row := public._m13_upsert_candidate(
        p_case_id, v_sighting_id, v_scored.score, v_scored.level, v_scored.reasons, v_actor
      );
      return next public._m13_candidate_json(v_row);
    end loop;
  end loop;
end;
$$;

create or replace function public.m13_list_case_match_candidates(p_case_id uuid)
returns setof jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
begin
  if not exists (select 1 from public.lost_found_posts where id = p_case_id) then
    raise exception 'CASE_NOT_FOUND';
  end if;
  if not public._m13_can_manage_case(p_case_id, v_actor) then
    raise exception 'MATCH_FORBIDDEN';
  end if;

  return query
  select public._m13_candidate_json(c)
  from public.lost_found_match_candidates c
  where c.case_id = p_case_id
  order by c.score desc, c.created_at desc;
end;
$$;

create or replace function public.m13_list_sighting_match_candidates(p_sighting_id uuid)
returns setof jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_sighting public.lost_found_sightings;
begin
  select * into v_sighting from public.lost_found_sightings where id = p_sighting_id;
  if not found then
    raise exception 'SIGHTING_NOT_FOUND';
  end if;

  if not (
    v_sighting.reporter_id = v_actor
    or public._m13_can_manage_case(v_sighting.post_id, v_actor)
  ) then
    raise exception 'MATCH_FORBIDDEN';
  end if;

  return query
  select public._m13_candidate_json(c)
  from public.lost_found_match_candidates c
  where c.sighting_id = p_sighting_id
  order by c.score desc, c.created_at desc;
end;
$$;

create or replace function public.m13_get_match_candidate(p_candidate_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
  v_sighting public.lost_found_sightings;
begin
  select * into v_c from public.lost_found_match_candidates where id = p_candidate_id;
  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  select * into v_sighting from public.lost_found_sightings where id = v_c.sighting_id;

  if not (
    public._m13_can_manage_case(v_c.case_id, v_actor)
    or (v_sighting.reporter_id = v_actor)
  ) then
    raise exception 'MATCH_FORBIDDEN';
  end if;

  return public._m13_candidate_json(v_c);
end;
$$;

create or replace function public.m13_recalculate_match_candidate(p_candidate_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor uuid := public._m13_require_auth();
  v_c public.lost_found_match_candidates;
  v_scored record;
  v_found boolean := false;
begin
  select * into v_c from public.lost_found_match_candidates where id = p_candidate_id;
  if not found then
    raise exception 'MATCH_NOT_FOUND';
  end if;

  if not public._m13_can_write_case(v_c.case_id, v_actor) then
    raise exception 'MATCH_GENERATION_NOT_ALLOWED';
  end if;

  if v_c.status = any (array[
    'CONFIRMED','REJECTED','INCONCLUSIVE','WITHDRAWN','EXPIRED'
  ]::text[]) then
    raise exception 'MATCH_TERMINAL';
  end if;

  for v_scored in
    select * from public._m13_score_pair(v_c.sighting_id, v_c.case_id)
  loop
    v_found := true;
    v_c := public._m13_upsert_candidate(
      v_c.case_id, v_c.sighting_id, v_scored.score, v_scored.level, v_scored.reasons, v_actor
    );
  end loop;

  if not v_found then
    raise exception 'MATCH_DATA_INSUFFICIENT';
  end if;

  return public._m13_candidate_json(v_c);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RLS defensivo (sin DML directo autenticado)
-- ---------------------------------------------------------------------------
alter table public.lost_found_sighting_details enable row level security;
alter table public.lost_found_match_candidates enable row level security;
alter table public.lost_found_match_decisions enable row level security;
alter table public.lost_found_match_status_history enable row level security;

-- sighting_details SELECT
drop policy if exists m13_sighting_details_select on public.lost_found_sighting_details;
create policy m13_sighting_details_select
  on public.lost_found_sighting_details
  for select
  to authenticated
  using (
    exists (
      select 1 from public.lost_found_sightings s
      where s.id = sighting_id and s.reporter_id = auth.uid()
    )
    or exists (
      select 1
      from public.lost_found_sightings s
      join public.lost_found_posts p on p.id = s.post_id
      where s.id = sighting_id and p.author_id = auth.uid()
    )
    or public.has_permission('lostfound.sighting.read')
    or public.has_permission('lostfound.sighting.moderate')
    or public.has_permission('lostfound.match.review')
  );

drop policy if exists m13_sighting_details_ins on public.lost_found_sighting_details;
create policy m13_sighting_details_ins
  on public.lost_found_sighting_details for insert to authenticated with check (false);
drop policy if exists m13_sighting_details_upd on public.lost_found_sighting_details;
create policy m13_sighting_details_upd
  on public.lost_found_sighting_details for update to authenticated using (false);
drop policy if exists m13_sighting_details_del on public.lost_found_sighting_details;
create policy m13_sighting_details_del
  on public.lost_found_sighting_details for delete to authenticated using (false);

-- match_candidates SELECT (sin llamar helpers _m13_* — EXECUTE revocado a authenticated)
drop policy if exists m13_match_candidates_select on public.lost_found_match_candidates;
create policy m13_match_candidates_select
  on public.lost_found_match_candidates
  for select
  to authenticated
  using (
    exists (
      select 1 from public.lost_found_posts p
      where p.id = case_id and p.author_id = auth.uid()
    )
    or exists (
      select 1 from public.lost_found_sightings s
      where s.id = sighting_id and s.reporter_id = auth.uid()
    )
    or public.has_permission('lostfound.match.read')
    or public.has_permission('lostfound.match.review')
    or public.has_permission('lostfound.sighting.moderate')
  );

drop policy if exists m13_match_candidates_ins on public.lost_found_match_candidates;
create policy m13_match_candidates_ins
  on public.lost_found_match_candidates for insert to authenticated with check (false);
drop policy if exists m13_match_candidates_upd on public.lost_found_match_candidates;
create policy m13_match_candidates_upd
  on public.lost_found_match_candidates for update to authenticated using (false);
drop policy if exists m13_match_candidates_del on public.lost_found_match_candidates;
create policy m13_match_candidates_del
  on public.lost_found_match_candidates for delete to authenticated using (false);

-- match_decisions SELECT (Bloque 3 escribirá vía RPC; sin write cliente ahora)
drop policy if exists m13_match_decisions_select on public.lost_found_match_decisions;
create policy m13_match_decisions_select
  on public.lost_found_match_decisions
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.lost_found_match_candidates c
      join public.lost_found_posts p on p.id = c.case_id
      where c.id = candidate_id
        and (
          p.author_id = auth.uid()
          or public.has_permission('lostfound.match.review')
          or public.has_permission('lostfound.match.confirm')
          or public.has_permission('lostfound.sighting.moderate')
        )
    )
  );

drop policy if exists m13_match_decisions_ins on public.lost_found_match_decisions;
create policy m13_match_decisions_ins
  on public.lost_found_match_decisions for insert to authenticated with check (false);
drop policy if exists m13_match_decisions_upd on public.lost_found_match_decisions;
create policy m13_match_decisions_upd
  on public.lost_found_match_decisions for update to authenticated using (false);
drop policy if exists m13_match_decisions_del on public.lost_found_match_decisions;
create policy m13_match_decisions_del
  on public.lost_found_match_decisions for delete to authenticated using (false);

-- status history SELECT
drop policy if exists m13_match_status_history_select on public.lost_found_match_status_history;
create policy m13_match_status_history_select
  on public.lost_found_match_status_history
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.lost_found_match_candidates c
      join public.lost_found_posts p on p.id = c.case_id
      where c.id = candidate_id
        and (
          p.author_id = auth.uid()
          or public.has_permission('lostfound.match.read')
          or public.has_permission('lostfound.match.review')
          or public.has_permission('lostfound.sighting.moderate')
        )
    )
  );

drop policy if exists m13_match_status_history_ins on public.lost_found_match_status_history;
create policy m13_match_status_history_ins
  on public.lost_found_match_status_history for insert to authenticated with check (false);
drop policy if exists m13_match_status_history_upd on public.lost_found_match_status_history;
create policy m13_match_status_history_upd
  on public.lost_found_match_status_history for update to authenticated using (false);
drop policy if exists m13_match_status_history_del on public.lost_found_match_status_history;
create policy m13_match_status_history_del
  on public.lost_found_match_status_history for delete to authenticated using (false);

revoke all privileges on table public.lost_found_sighting_details from public;
revoke all privileges on table public.lost_found_sighting_details from anon;
revoke all privileges on table public.lost_found_sighting_details from authenticated;
revoke all privileges on table public.lost_found_match_candidates from public;
revoke all privileges on table public.lost_found_match_candidates from anon;
revoke all privileges on table public.lost_found_match_candidates from authenticated;
revoke all privileges on table public.lost_found_match_decisions from public;
revoke all privileges on table public.lost_found_match_decisions from anon;
revoke all privileges on table public.lost_found_match_decisions from authenticated;
revoke all privileges on table public.lost_found_match_status_history from public;
revoke all privileges on table public.lost_found_match_status_history from anon;
revoke all privileges on table public.lost_found_match_status_history from authenticated;

-- Lectura defensiva opcional vía RLS (SELECT grant; DML denegado por policy + revoke).
grant select on table public.lost_found_sighting_details to authenticated;
grant select on table public.lost_found_match_candidates to authenticated;
grant select on table public.lost_found_match_decisions to authenticated;
grant select on table public.lost_found_match_status_history to authenticated;

-- ---------------------------------------------------------------------------
-- 7. Grants — helpers _m13_* (sin EXECUTE para public/anon/authenticated)
-- ---------------------------------------------------------------------------
revoke all on function public._m13_require_auth() from public, anon, authenticated;
revoke all on function public._m13_is_safe_media_ref(text) from public, anon, authenticated;
revoke all on function public._m13_validate_media_refs(text[]) from public, anon, authenticated;
revoke all on function public._m13_media_refs_ok(text[]) from public, anon, authenticated;
revoke all on function public._m13_haversine_km(double precision, double precision, double precision, double precision) from public, anon, authenticated;
revoke all on function public._m13_level_from_score(int) from public, anon, authenticated;
revoke all on function public._m13_case_is_active(uuid) from public, anon, authenticated;
revoke all on function public._m13_is_case_owner(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m13_can_moderate(uuid) from public, anon, authenticated;
revoke all on function public._m13_can_manage_case(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m13_can_write_case(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m13_zone_text_overlap(text, text) from public, anon, authenticated;
revoke all on function public._m13_public_sighting_json(public.lost_found_sightings, public.lost_found_sighting_details) from public, anon, authenticated;
revoke all on function public._m13_managed_sighting_json(public.lost_found_sightings, public.lost_found_sighting_details) from public, anon, authenticated;
revoke all on function public._m13_candidate_json(public.lost_found_match_candidates) from public, anon, authenticated;
revoke all on function public._m13_score_pair(uuid, uuid) from public, anon, authenticated;
revoke all on function public._m13_append_candidate_history(uuid, text, text, uuid, text) from public, anon, authenticated;
revoke all on function public._m13_upsert_candidate(uuid, uuid, int, text, text[], uuid) from public, anon, authenticated;
revoke all on function public._m13_require_reporter_name(uuid) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 8. Grants — RPC cliente (revoke public/anon; grant execute authenticated)
-- ---------------------------------------------------------------------------
revoke all on function public.m13_create_sighting(uuid, text, text, text, text, text, text, timestamptz, text, double precision, double precision, double precision, text, text[]) from public;
revoke all on function public.m13_create_sighting(uuid, text, text, text, text, text, text, timestamptz, text, double precision, double precision, double precision, text, text[]) from anon;
grant execute on function public.m13_create_sighting(uuid, text, text, text, text, text, text, timestamptz, text, double precision, double precision, double precision, text, text[]) to authenticated;

revoke all on function public.m13_update_my_sighting(uuid, text, text, text, text, text, text, timestamptz, text, double precision, double precision, double precision, text, text[]) from public;
revoke all on function public.m13_update_my_sighting(uuid, text, text, text, text, text, text, timestamptz, text, double precision, double precision, double precision, text, text[]) from anon;
grant execute on function public.m13_update_my_sighting(uuid, text, text, text, text, text, text, timestamptz, text, double precision, double precision, double precision, text, text[]) to authenticated;

revoke all on function public.m13_withdraw_my_sighting(uuid) from public;
revoke all on function public.m13_withdraw_my_sighting(uuid) from anon;
grant execute on function public.m13_withdraw_my_sighting(uuid) to authenticated;

revoke all on function public.m13_get_sighting(uuid) from public;
revoke all on function public.m13_get_sighting(uuid) from anon;
grant execute on function public.m13_get_sighting(uuid) to authenticated;

revoke all on function public.m13_list_public_sightings(int, int) from public;
revoke all on function public.m13_list_public_sightings(int, int) from anon;
grant execute on function public.m13_list_public_sightings(int, int) to authenticated;

revoke all on function public.m13_list_my_sightings() from public;
revoke all on function public.m13_list_my_sightings() from anon;
grant execute on function public.m13_list_my_sightings() to authenticated;

revoke all on function public.m13_list_managed_sightings() from public;
revoke all on function public.m13_list_managed_sightings() from anon;
grant execute on function public.m13_list_managed_sightings() to authenticated;

revoke all on function public.m13_generate_match_candidates_for_sighting(uuid) from public;
revoke all on function public.m13_generate_match_candidates_for_sighting(uuid) from anon;
grant execute on function public.m13_generate_match_candidates_for_sighting(uuid) to authenticated;

revoke all on function public.m13_generate_match_candidates_for_case(uuid) from public;
revoke all on function public.m13_generate_match_candidates_for_case(uuid) from anon;
grant execute on function public.m13_generate_match_candidates_for_case(uuid) to authenticated;

revoke all on function public.m13_list_case_match_candidates(uuid) from public;
revoke all on function public.m13_list_case_match_candidates(uuid) from anon;
grant execute on function public.m13_list_case_match_candidates(uuid) to authenticated;

revoke all on function public.m13_list_sighting_match_candidates(uuid) from public;
revoke all on function public.m13_list_sighting_match_candidates(uuid) from anon;
grant execute on function public.m13_list_sighting_match_candidates(uuid) to authenticated;

revoke all on function public.m13_get_match_candidate(uuid) from public;
revoke all on function public.m13_get_match_candidate(uuid) from anon;
grant execute on function public.m13_get_match_candidate(uuid) to authenticated;

revoke all on function public.m13_recalculate_match_candidate(uuid) from public;
revoke all on function public.m13_recalculate_match_candidate(uuid) from anon;
grant execute on function public.m13_recalculate_match_candidate(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 9. Comentarios
-- ---------------------------------------------------------------------------
comment on table public.lost_found_sighting_details is
  'LeoVer M13 Bloque 2: detalles 1:1 de avistamiento; lateral a lost_found_sightings legacy.';
comment on table public.lost_found_match_candidates is
  'LeoVer M13 Bloque 2: candidatos de coincidencia explicables; sin autoconfirmación.';
comment on table public.lost_found_match_decisions is
  'LeoVer M13 Bloque 2: decisiones de match (estructura para Bloque 3; sin RPC de escritura aún).';
comment on table public.lost_found_match_status_history is
  'LeoVer M13 Bloque 2: historial de estados de candidatos de coincidencia.';

comment on function public.m13_create_sighting(uuid, text, text, text, text, text, text, timestamptz, text, double precision, double precision, double precision, text, text[]) is
  'M13: crea avistamiento legacy + detalles; genera candidato vs caso si ACTIVE; sin auto-confirm.';
comment on function public.m13_withdraw_my_sighting(uuid) is
  'M13: retira avistamiento propio (status WITHDRAWN).';
comment on function public.m13_generate_match_candidates_for_sighting(uuid) is
  'M13: genera candidatos vs casos ACTIVE; nunca confirma.';
comment on function public.m13_recalculate_match_candidate(uuid) is
  'M13: recalcula score de candidato no terminal; sin transición a CONFIRMED.';

commit;
