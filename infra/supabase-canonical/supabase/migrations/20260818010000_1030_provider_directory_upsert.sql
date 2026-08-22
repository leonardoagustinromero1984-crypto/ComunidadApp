-- LeoVer Canonical
-- Logical migration: 1030
-- UX-06.1: directory-only provider upsert. PERSON or ORGANIZATION holder.
-- Marketplace/checkout fields are not required.
-- Forward-only. Do not edit 1000-1029.

insert into public.service_categories (code, name, sort_key) values
  ('GROOMING', 'Peluquería', 7),
  ('SHOP', 'Tienda', 8)
on conflict (code) do nothing;

create or replace function public.canon_upsert_provider(
  p_kind text,
  p_person uuid,
  p_org uuid,
  p_name text,
  p_category text,
  p_locality_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_kind text;
  v_person uuid;
  v_org uuid;
  v_name text;
  v_category text;
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;

  v_kind := upper(btrim(coalesce(p_kind, '')));
  v_name := btrim(coalesce(p_name, ''));
  v_category := upper(btrim(coalesce(p_category, '')));

  if v_name = '' then raise exception 'PROVIDER_NAME_REQUIRED'; end if;

  if v_category in ('DAYCARE', 'GUARDERIA', 'GUARDERÍA') then
    v_category := 'BOARDING';
  elsif v_category in ('WALKER') then
    v_category := 'WALKING';
  elsif v_category in ('TRAINER', 'EDUCADOR') then
    v_category := 'TRAINING';
  elsif v_category in ('CAREGIVER', 'CUIDADOR') then
    v_category := 'CARE';
  elsif v_category in ('VET') then
    v_category := 'VETERINARY';
  end if;

  if not exists (
    select 1 from public.service_categories c
    where c.code = v_category and c.active
  ) then
    raise exception 'PROVIDER_CATEGORY_UNSUPPORTED';
  end if;

  if v_kind = 'PERSON' then
    v_person := coalesce(p_person, auth.uid());
    v_org := null;
    if v_person is distinct from auth.uid() then
      raise exception 'PROVIDER_HOLDER_FORBIDDEN';
    end if;
  elsif v_kind = 'ORGANIZATION' then
    v_org := p_org;
    v_person := null;
    if v_org is null then raise exception 'PROVIDER_HOLDER_REQUIRED'; end if;
    if not public._acl_org_member(auth.uid(), v_org) then
      raise exception 'PROVIDER_HOLDER_FORBIDDEN';
    end if;
  else
    raise exception 'PROVIDER_HOLDER_REQUIRED';
  end if;

  if not public.holder_xor_ok(v_kind, v_person, v_org) then
    raise exception 'PROVIDER_HOLDER_REQUIRED';
  end if;

  select p.id into v_id
  from public.service_providers p
  join public.service_offerings o on o.provider_id = p.id
  where p.lifecycle_status = 'ACTIVE'
    and o.category_code = v_category
    and o.active
    and p.holder_kind = v_kind
    and (
      (v_kind = 'PERSON' and p.holder_person_id = v_person)
      or (v_kind = 'ORGANIZATION' and p.holder_organization_id = v_org)
    )
  order by p.created_at
  limit 1;

  if v_id is null then
    insert into public.service_providers (
      holder_kind, holder_person_id, holder_organization_id, display_name
    ) values (v_kind, v_person, v_org, v_name)
    returning id into v_id;

    insert into public.service_offerings (provider_id, category_code, name)
    values (v_id, v_category, v_name);
  else
    update public.service_providers
      set display_name = v_name
      where id = v_id;
    update public.service_offerings
      set name = v_name, active = true
      where provider_id = v_id and category_code = v_category;
  end if;

  if p_locality_id is not null and btrim(p_locality_id) <> '' then
    insert into public.provider_coverage_areas (provider_id, locality_id)
    values (v_id, btrim(p_locality_id))
    on conflict (provider_id, locality_id) do nothing;
  end if;

  return v_id;
end;
$$;

revoke all on function public.canon_upsert_provider(text, uuid, uuid, text, text, text)
  from public, anon;
grant execute on function public.canon_upsert_provider(text, uuid, uuid, text, text, text)
  to authenticated;
