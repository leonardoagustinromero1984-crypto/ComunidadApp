-- LeoVer Canonical
-- Logical migration: 1039
-- Admin master catalogs: species/breeds/location writes + health products.
-- Idempotent seeds. Does not overwrite Admin edits (ON CONFLICT DO NOTHING).

-- ---------------------------------------------------------------------------
-- Extra species so pet create FK matches the Android catalog
-- ---------------------------------------------------------------------------
insert into public.species (code, name, sort_key) values
  ('DOG', 'Perro', 1),
  ('CAT', 'Gato', 2),
  ('HORSE', 'Caballo', 10),
  ('DONKEY', 'Asno', 11),
  ('COW', 'Vaca', 12),
  ('SHEEP', 'Oveja', 13),
  ('GOAT', 'Cabra', 14),
  ('PIG', 'Cerdo', 15),
  ('RABBIT', 'Conejo', 20),
  ('HAMSTER', 'Hámster', 21),
  ('GUINEA_PIG', 'Cobayo', 22),
  ('BIRD', 'Ave', 30),
  ('CHICKEN', 'Gallina', 31),
  ('DUCK', 'Pato', 32),
  ('FISH', 'Pez', 40),
  ('REPTILE', 'Reptil', 41),
  ('OTHER', 'Otra', 99)
on conflict (code) do nothing;

insert into public.breeds (species_code, name, sort_key) values
  ('DOG', 'Mestizo', 1),
  ('DOG', 'Labrador', 2),
  ('DOG', 'Golden Retriever', 3),
  ('DOG', 'Caniche', 4),
  ('CAT', 'Mestizo', 1),
  ('CAT', 'Siamés', 2),
  ('CAT', 'Persa', 3)
on conflict (species_code, name) do nothing;

-- ---------------------------------------------------------------------------
-- Health product master data
-- ---------------------------------------------------------------------------
create table if not exists public.pet_health_products (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('VACCINE', 'FLEA', 'DEWORMER')),
  code text not null,
  display_name text not null,
  species_code text null references public.species(code),
  active boolean not null default true,
  sort_order integer not null default 0,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  unique (kind, code)
);

create index if not exists pet_health_products_kind_active_idx
  on public.pet_health_products (kind, active, sort_order);

alter table public.pet_health_products enable row level security;
revoke all on table public.pet_health_products from anon, authenticated;

drop trigger if exists pet_health_products_set_updated_at on public.pet_health_products;
create trigger pet_health_products_set_updated_at
  before update on public.pet_health_products
  for each row execute function public.set_updated_at();

insert into public.pet_health_products (kind, code, display_name, species_code, sort_order) values
  ('VACCINE', 'RABIES', 'Rabia', null, 1),
  ('VACCINE', 'DHPP_LEPTO', 'Séxtuple (DHPP + Lepto)', 'DOG', 2),
  ('VACCINE', 'OCTUPLE', 'Octuple', 'DOG', 3),
  ('VACCINE', 'BORDETELLA', 'Bordetella (tos de las perreras)', 'DOG', 4),
  ('VACCINE', 'LEPTO', 'Leptospirosis', 'DOG', 5),
  ('VACCINE', 'GIARDIA', 'Giardia', 'DOG', 6),
  ('VACCINE', 'FELINE_RCP', 'Triple felina (RCP)', 'CAT', 2),
  ('VACCINE', 'FELINE_QUAD', 'Cuádruple felina', 'CAT', 3),
  ('VACCINE', 'FELV', 'Leucemia felina (FeLV)', 'CAT', 4),
  ('VACCINE', 'ANNUAL_BOOSTER', 'Refuerzo anual', null, 80),
  ('VACCINE', 'OTHER', 'Otra', null, 99),
  ('FLEA', 'FIPRONIL', 'Fipronil (pipeta)', null, 1),
  ('FLEA', 'FLURALANER', 'Fluralaner (comprimido)', null, 2),
  ('FLEA', 'AMITRAZ', 'Amitraz (collar)', null, 3),
  ('FLEA', 'PERMETHRIN', 'Permetrina (spray)', null, 4),
  ('FLEA', 'SELAMECTIN', 'Selamectina', null, 5),
  ('FLEA', 'COLLAR', 'Collar antipulgas', null, 6),
  ('FLEA', 'OTHER', 'Otro producto', null, 99),
  ('DEWORMER', 'IVERMECTIN', 'Ivermectina', null, 1),
  ('DEWORMER', 'ALBENDAZOLE', 'Albendazol', null, 2),
  ('DEWORMER', 'FENBENDAZOLE', 'Febendazol', null, 3),
  ('DEWORMER', 'PRAZIQUANTEL', 'Praziquantel', null, 4),
  ('DEWORMER', 'MOXIDECTIN', 'Moxidectina', null, 5),
  ('DEWORMER', 'PIPERAZINE', 'Piperazina', null, 6),
  ('DEWORMER', 'SPOT_ON', 'Spot-on desparasitante', null, 7),
  ('DEWORMER', 'OTHER', 'Otro producto', null, 99)
on conflict (kind, code) do nothing;

-- ---------------------------------------------------------------------------
-- Public list (active only)
-- ---------------------------------------------------------------------------
create or replace function public.canon_list_species()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'code', s.code, 'name', s.name, 'sort_key', s.sort_key, 'active', s.active
    ) order by s.sort_key, s.name)
    from public.species s
    where s.active
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_breeds(p_species_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', b.id, 'species_code', b.species_code, 'name', b.name,
      'sort_key', b.sort_key, 'active', b.active
    ) order by b.sort_key, b.name)
    from public.breeds b
    where b.active and b.species_code = p_species_code
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_pet_health_products(p_kind text, p_species_code text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', p.id, 'kind', p.kind, 'code', p.code, 'display_name', p.display_name,
      'species_code', p.species_code, 'active', p.active, 'sort_order', p.sort_order
    ) order by p.sort_order, p.display_name)
    from public.pet_health_products p
    where p.active
      and p.kind = p_kind
      and (p.species_code is null or p_species_code is null or p.species_code = p_species_code)
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Admin writes — server-side _acl_is_admin. ActiveContext is never authority.
-- ---------------------------------------------------------------------------
create or replace function public.canon_admin_list_species()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public._acl_is_admin(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'code', s.code, 'name', s.name, 'sort_key', s.sort_key, 'active', s.active
    ) order by s.sort_key, s.name)
    from public.species s
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_upsert_species(
  p_code text, p_name text, p_sort_key integer, p_active boolean
)
returns text
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public._acl_is_admin(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if p_code is null or length(trim(p_code)) = 0 then raise exception 'VALIDATION'; end if;
  insert into public.species (code, name, sort_key, active)
  values (upper(trim(p_code)), trim(p_name), coalesce(p_sort_key, 0), coalesce(p_active, true))
  on conflict (code) do update
    set name = excluded.name,
        sort_key = excluded.sort_key,
        active = excluded.active;
  return upper(trim(p_code));
end;
$$;

create or replace function public.canon_admin_list_breeds(p_species_code text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public._acl_is_admin(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', b.id, 'species_code', b.species_code, 'name', b.name,
      'sort_key', b.sort_key, 'active', b.active
    ) order by b.species_code, b.sort_key, b.name)
    from public.breeds b
    where p_species_code is null or b.species_code = p_species_code
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_upsert_breed(
  p_id uuid, p_species_code text, p_name text, p_sort_key integer, p_active boolean
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if not public._acl_is_admin(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if p_species_code is null or length(trim(p_name)) = 0 then raise exception 'VALIDATION'; end if;
  if p_id is null then
    insert into public.breeds (species_code, name, sort_key, active)
    values (p_species_code, trim(p_name), coalesce(p_sort_key, 0), coalesce(p_active, true))
    on conflict (species_code, name) do update
      set sort_key = excluded.sort_key,
          active = excluded.active
    returning id into v_id;
  else
    update public.breeds
      set species_code = p_species_code,
          name = trim(p_name),
          sort_key = coalesce(p_sort_key, sort_key),
          active = coalesce(p_active, active)
    where id = p_id
    returning id into v_id;
  end if;
  return v_id;
end;
$$;

create or replace function public.canon_admin_list_pet_health_products(p_kind text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public._acl_is_admin(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', p.id, 'kind', p.kind, 'code', p.code, 'display_name', p.display_name,
      'species_code', p.species_code, 'active', p.active, 'sort_order', p.sort_order
    ) order by p.kind, p.sort_order, p.display_name)
    from public.pet_health_products p
    where p_kind is null or p.kind = p_kind
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_upsert_pet_health_product(
  p_id uuid, p_kind text, p_code text, p_display_name text,
  p_species_code text, p_sort_order integer, p_active boolean
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare v_id uuid;
begin
  if not public._acl_is_admin(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if p_kind not in ('VACCINE', 'FLEA', 'DEWORMER') then raise exception 'VALIDATION'; end if;
  if length(trim(p_code)) = 0 or length(trim(p_display_name)) = 0 then raise exception 'VALIDATION'; end if;
  if p_id is null then
    insert into public.pet_health_products (kind, code, display_name, species_code, sort_order, active)
    values (p_kind, upper(trim(p_code)), trim(p_display_name), p_species_code, coalesce(p_sort_order, 0), coalesce(p_active, true))
    on conflict (kind, code) do update
      set display_name = excluded.display_name,
          species_code = excluded.species_code,
          sort_order = excluded.sort_order,
          active = excluded.active
    returning id into v_id;
  else
    update public.pet_health_products
      set kind = p_kind,
          code = upper(trim(p_code)),
          display_name = trim(p_display_name),
          species_code = p_species_code,
          sort_order = coalesce(p_sort_order, sort_order),
          active = coalesce(p_active, active)
    where id = p_id
    returning id into v_id;
  end if;
  return v_id;
end;
$$;

create or replace function public.canon_admin_upsert_location_node(
  p_id text, p_kind text, p_parent_id text, p_name text, p_iso_code text,
  p_sort_key integer, p_active boolean
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare v_id text;
begin
  if not public._acl_is_admin(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if p_kind not in ('COUNTRY', 'PROVINCE', 'LOCALITY') then raise exception 'VALIDATION'; end if;
  if length(trim(p_name)) = 0 then raise exception 'VALIDATION'; end if;
  v_id := coalesce(nullif(trim(p_id), ''), 'loc_' || replace(gen_random_uuid()::text, '-', ''));
  insert into public.location_nodes (id, kind, parent_id, name, iso_code, sort_key, active)
  values (v_id, p_kind, p_parent_id, trim(p_name), p_iso_code, coalesce(p_sort_key, 0), coalesce(p_active, true))
  on conflict (id) do update
    set kind = excluded.kind,
        parent_id = excluded.parent_id,
        name = excluded.name,
        iso_code = excluded.iso_code,
        sort_key = excluded.sort_key,
        active = excluded.active;
  return v_id;
end;
$$;

create or replace function public.canon_admin_list_location_catalog()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public._acl_is_admin(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', n.id, 'kind', n.kind, 'parent_id', n.parent_id, 'name', n.name,
      'iso_code', n.iso_code, 'sort_key', n.sort_key, 'active', n.active
    ) order by n.kind, n.sort_key, n.name)
    from public.location_nodes n
  ), '[]'::jsonb);
end;
$$;

grant execute on function public.canon_list_species() to authenticated, anon;
grant execute on function public.canon_list_breeds(text) to authenticated, anon;
grant execute on function public.canon_list_pet_health_products(text, text) to authenticated, anon;
grant execute on function public.canon_admin_list_species() to authenticated;
grant execute on function public.canon_admin_upsert_species(text, text, integer, boolean) to authenticated;
grant execute on function public.canon_admin_list_breeds(text) to authenticated;
grant execute on function public.canon_admin_upsert_breed(uuid, text, text, integer, boolean) to authenticated;
grant execute on function public.canon_admin_list_pet_health_products(text) to authenticated;
grant execute on function public.canon_admin_upsert_pet_health_product(uuid, text, text, text, text, integer, boolean) to authenticated;
grant execute on function public.canon_admin_upsert_location_node(text, text, text, text, text, integer, boolean) to authenticated;
grant execute on function public.canon_admin_list_location_catalog() to authenticated;
