-- 1094: FOUND/LOST create stages + isolate match/fanout so they cannot abort identity+alert.
-- Does not edit 1093 or earlier.

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
      raise exception 'LF-CREATE-LOCATION';
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
      kind, pet_id, created_by, locality_id, species_code, note,
      precise_location, photo_asset_id, incident_at, status, next_wave_at
    ) values (
      v_kind, v_pet, auth.uid(), p_locality_id, p_species, p_note,
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

grant execute on function public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid, text, text, text, integer, uuid) to authenticated;

comment on function public.canon_create_lost_found(text, uuid, text, text, text, double precision, double precision, timestamptz, uuid, text, text, text, integer, uuid) is
  'Atomic FOUND/LOST create. Stages: LF-CREATE-AUTH/KIND/FORBIDDEN/LOCATION/IDENTITY/ALERT. Match+fanout isolated after persist.';
