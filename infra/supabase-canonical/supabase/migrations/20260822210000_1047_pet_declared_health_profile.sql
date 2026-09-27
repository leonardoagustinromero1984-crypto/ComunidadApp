-- LeoVer STAGING — declared health profile fields (sterilized, last vet visit, notes)
-- Extends read/write for fields the Android pet form already collects.

alter table public.pet_declared_health
  add column if not exists sterilized_status text null
    check (sterilized_status is null or sterilized_status in ('YES', 'NO', 'UNKNOWN')),
  add column if not exists last_vet_visit date null;

create or replace function public.canon_upsert_pet_declared_health_profile(
  p_pet_id uuid,
  p_notes text default null,
  p_sterilized text default null,
  p_last_vet_visit date default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_can_manage_declared_health(auth.uid(), p_pet_id) then
    raise exception 'FORBIDDEN';
  end if;
  if p_sterilized is not null and p_sterilized not in ('YES', 'NO', 'UNKNOWN') then
    raise exception 'PET_STERILIZED_INVALID';
  end if;
  insert into public.pet_declared_health (
    pet_id, notes, sterilized_status, last_vet_visit, updated_by
  ) values (
    p_pet_id,
    nullif(btrim(coalesce(p_notes, '')), ''),
    nullif(btrim(coalesce(p_sterilized, '')), ''),
    p_last_vet_visit,
    auth.uid()
  )
  on conflict (pet_id) do update
    set notes = coalesce(excluded.notes, public.pet_declared_health.notes),
        sterilized_status = coalesce(excluded.sterilized_status, public.pet_declared_health.sterilized_status),
        last_vet_visit = coalesce(excluded.last_vet_visit, public.pet_declared_health.last_vet_visit),
        updated_by = auth.uid(),
        updated_at = timezone('utc', now());
  return p_pet_id;
end;
$$;

create or replace function public.canon_get_pet_health(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_declared record;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not (
    public._acl_pet_holder(auth.uid(), p_pet_id)
    or public._acl_is_admin(auth.uid())
  ) then
    raise exception 'FORBIDDEN';
  end if;

  select h.notes, h.sterilized_status, h.last_vet_visit
    into v_declared
  from public.pet_declared_health h
  where h.pet_id = p_pet_id;

  return jsonb_build_object(
    'pet_id', p_pet_id,
    'sterilized_status', v_declared.sterilized_status,
    'last_vet_visit', v_declared.last_vet_visit,
    'allergies', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', a.id, 'name', a.name, 'source', a.source, 'status', a.status,
        'created_at', a.created_at
      ) order by a.created_at desc)
      from public.pet_allergies a
      where a.pet_id = p_pet_id and a.status = 'ACTIVE'
    ), '[]'::jsonb),
    'medications', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', m.id, 'name', m.name, 'instructions', m.instructions,
        'source', m.source, 'status', m.status, 'created_at', m.created_at
      ) order by m.created_at desc)
      from public.pet_medications m
      where m.pet_id = p_pet_id and m.status = 'ACTIVE'
    ), '[]'::jsonb),
    'vaccinations', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', v.id, 'vaccine_name', v.vaccine_name,
        'administered_on', v.administered_on, 'source', v.source,
        'created_at', v.created_at
      ) order by v.created_at desc)
      from public.pet_declared_vaccinations v
      where v.pet_id = p_pet_id
    ), '[]'::jsonb),
    'parasite_treatments', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', t.id, 'kind', t.kind, 'product_name', t.product_name,
        'treated_on', t.treated_on, 'source', t.source, 'created_at', t.created_at
      ) order by t.treated_on desc, t.created_at desc)
      from public.pet_parasite_treatments t
      where t.pet_id = p_pet_id
    ), '[]'::jsonb),
    'conditions', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', c.id, 'name', c.name, 'source', c.source, 'status', c.status,
        'created_at', c.created_at
      ) order by c.created_at desc)
      from public.pet_conditions c
      where c.pet_id = p_pet_id and c.status = 'ACTIVE'
    ), '[]'::jsonb),
    'weights', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', w.id, 'kilograms', w.kilograms, 'measured_on', w.measured_on,
        'source', w.source, 'created_at', w.created_at
      ) order by w.measured_on desc, w.created_at desc)
      from public.pet_weights w
      where w.pet_id = p_pet_id
    ), '[]'::jsonb),
    'care_instructions', (
      select jsonb_build_object(
        'feeding', i.feeding, 'medication', i.medication,
        'specials', i.specials, 'updated_at', i.updated_at
      )
      from public.pet_care_instructions i
      where i.pet_id = p_pet_id
    ),
    'declared_notes', v_declared.notes
  );
end;
$$;

revoke all on function public.canon_upsert_pet_declared_health_profile(uuid, text, text, date) from public;
grant execute on function public.canon_upsert_pet_declared_health_profile(uuid, text, text, date) to authenticated;
