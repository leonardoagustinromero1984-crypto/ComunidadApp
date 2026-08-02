-- =============================================================================
-- LeoVer M22 — migración 066: prestadores y catálogo de servicios (Bloque 2).
-- Forward-only sobre 001–065. LOCAL ONLY: no aplicar a staging sin autorización.
-- No modifica service_profiles ni las tablas veterinarias M12.
-- =============================================================================

begin;

-- 0. Permisos M03 para gestión organizacional de prestadores.
insert into public.organization_permissions (code, description) values
  ('provider.profile.read', 'Ver perfiles de prestadores de la organización'),
  ('provider.profile.manage', 'Gestionar perfiles y sedes de prestadores'),
  ('provider.catalog.manage', 'Gestionar ofertas del catálogo de prestadores')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in ('provider.profile.read', 'provider.profile.manage', 'provider.catalog.manage')
on conflict do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id
from public.organization_roles r
cross join public.organization_permissions p
where r.code = 'MEMBER' and p.code = 'provider.profile.read'
on conflict do nothing;

-- 1. Tablas internas. Los importes se almacenan en centavos (minor units).
create table if not exists public.m22_service_providers (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid references public.organizations(id) on delete restrict,
  owner_user_id uuid not null references public.users(id) on delete restrict,
  display_name text not null,
  category text not null,
  description text not null,
  city text not null,
  status text not null default 'DRAFT',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m22_provider_category_chk check (category = any (array[
    'VET','GROOMING','TRAINING','WALKING','BOARDING','TRANSPORT','OTHER'
  ]::text[])),
  constraint m22_provider_status_chk check (status = any (array[
    'DRAFT','ACTIVE','SUSPENDED','ARCHIVED'
  ]::text[])),
  constraint m22_provider_name_chk check (char_length(trim(display_name)) between 2 and 160),
  constraint m22_provider_description_chk check (char_length(trim(description)) between 10 and 2000),
  constraint m22_provider_city_chk check (char_length(trim(city)) between 2 and 120)
);

create table if not exists public.m22_provider_branches (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.m22_service_providers(id) on delete cascade,
  organization_branch_id uuid references public.organization_branches(id) on delete set null,
  name text not null,
  city text not null,
  neighborhood text,
  coverage_type text not null,
  coverage_city text not null,
  coverage_neighborhood text,
  coverage_radius_km integer,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m22_branch_status_chk check (status = any (array['ACTIVE','INACTIVE','ARCHIVED']::text[])),
  constraint m22_branch_coverage_type_chk check (coverage_type = any (array['CITY','NEIGHBORHOOD','RADIUS']::text[])),
  constraint m22_branch_name_chk check (char_length(trim(name)) between 2 and 160),
  constraint m22_branch_city_chk check (char_length(trim(city)) between 2 and 120),
  constraint m22_branch_coverage_chk check (
    (coverage_type = 'CITY' and coverage_neighborhood is null and coverage_radius_km is null)
    or (coverage_type = 'NEIGHBORHOOD' and char_length(trim(coalesce(coverage_neighborhood, ''))) between 2 and 120 and coverage_radius_km is null)
    or (coverage_type = 'RADIUS' and coverage_neighborhood is null and coverage_radius_km between 1 and 200)
  )
);

create table if not exists public.m22_service_offerings (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.m22_service_providers(id) on delete cascade,
  branch_id uuid references public.m22_provider_branches(id) on delete set null,
  name text not null,
  description text not null,
  price_type text not null,
  price_amount_cents bigint,
  currency text not null default 'ARS',
  active boolean not null default true,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m22_offering_price_type_chk check (price_type = any (array['FIXED','FROM','QUOTE']::text[])),
  constraint m22_offering_name_chk check (char_length(trim(name)) between 2 and 160),
  constraint m22_offering_description_chk check (char_length(trim(description)) between 10 and 2000),
  constraint m22_offering_currency_chk check (currency ~ '^[A-Z]{3}$'),
  constraint m22_offering_price_chk check (
    (price_type in ('FIXED','FROM') and price_amount_cents is not null and price_amount_cents > 0)
    or (price_type = 'QUOTE' and price_amount_cents is null)
  )
);

create index if not exists m22_provider_category_status_idx on public.m22_service_providers(category, status);
create index if not exists m22_provider_organization_idx on public.m22_service_providers(organization_id);
create index if not exists m22_branch_provider_idx on public.m22_provider_branches(provider_id);
create index if not exists m22_branch_city_idx on public.m22_provider_branches(city);
create index if not exists m22_offering_provider_idx on public.m22_service_offerings(provider_id);

-- 2. RLS: no acceso directo para anon; las operaciones se exponen por RPC.
alter table public.m22_service_providers enable row level security;
alter table public.m22_provider_branches enable row level security;
alter table public.m22_service_offerings enable row level security;

create policy m22_provider_authenticated_deny on public.m22_service_providers for all to authenticated using (false) with check (false);
create policy m22_branch_authenticated_deny on public.m22_provider_branches for all to authenticated using (false) with check (false);
create policy m22_offering_authenticated_deny on public.m22_service_offerings for all to authenticated using (false) with check (false);

revoke all on table public.m22_service_providers from public, anon, authenticated;
revoke all on table public.m22_provider_branches from public, anon, authenticated;
revoke all on table public.m22_service_offerings from public, anon, authenticated;
grant all on table public.m22_service_providers, public.m22_provider_branches, public.m22_service_offerings to service_role;

-- 3. Helpers internos para autorización, validación y proyección pública.
create or replace function public._m22_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := auth.uid();
begin
  if v_actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v_actor;
end;
$$;

create or replace function public._m22_can_manage(p_provider public.m22_service_providers, p_actor uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_provider.owner_user_id = p_actor
    or (p_provider.organization_id is not null
      and public.has_org_permission(p_provider.organization_id, 'provider.profile.manage'));
$$;

create or replace function public._m22_can_manage_catalog(p_provider public.m22_service_providers, p_actor uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_provider.owner_user_id = p_actor
    or (p_provider.organization_id is not null
      and public.has_org_permission(p_provider.organization_id, 'provider.catalog.manage'));
$$;

create or replace function public._m22_validate_provider(
  p_name text, p_category text, p_description text, p_city text, p_status text
) returns void language plpgsql security definer set search_path = public as $$
begin
  if char_length(trim(coalesce(p_name, ''))) not between 2 and 160
    or char_length(trim(coalesce(p_description, ''))) not between 10 and 2000
    or char_length(trim(coalesce(p_city, ''))) not between 2 and 120
    or upper(coalesce(p_category, '')) not in ('VET','GROOMING','TRAINING','WALKING','BOARDING','TRANSPORT','OTHER')
    or upper(coalesce(p_status, '')) not in ('DRAFT','ACTIVE','SUSPENDED','ARCHIVED') then
    raise exception 'M22_INVALID_PROVIDER';
  end if;
end;
$$;

create or replace function public._m22_provider_json(p public.m22_service_providers)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', p.id, 'owner_user_id', p.owner_user_id, 'organization_id', p.organization_id,
    'display_name', p.display_name, 'category', p.category, 'description', p.description,
    'city', p.city, 'status', p.status, 'created_at', p.created_at, 'updated_at', p.updated_at
  );
$$;

create or replace function public._m22_branch_json(b public.m22_provider_branches)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', b.id, 'provider_id', b.provider_id, 'name', b.name, 'city', b.city,
    'neighborhood', b.neighborhood, 'coverage_type', b.coverage_type, 'coverage_city', b.coverage_city,
    'coverage_neighborhood', b.coverage_neighborhood, 'coverage_radius_km', b.coverage_radius_km,
    'status', b.status
  );
$$;

create or replace function public._m22_offering_json(o public.m22_service_offerings)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', o.id, 'provider_id', o.provider_id, 'branch_id', o.branch_id, 'name', o.name,
    'description', o.description, 'price_type', o.price_type, 'price_amount_cents', o.price_amount_cents,
    'currency', o.currency, 'active', o.active
  );
$$;

create or replace function public._m22_public_listing_json(p public.m22_service_providers)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'display_name', p.display_name, 'category', p.category, 'description', p.description, 'city', p.city,
    'branch_count', (select count(*) from public.m22_provider_branches b where b.provider_id = p.id and b.status = 'ACTIVE'),
    'price_summary', (
      select case
        when count(*) filter (where o.price_type = 'FIXED') > 0 then 'Precio fijo'
        when count(*) filter (where o.price_type = 'FROM') > 0 then 'Desde'
        when count(*) > 0 then 'A cotizar' else null end
      from public.m22_service_offerings o where o.provider_id = p.id and o.active
    )
  );
$$;

-- 4. Catálogo público sanitizado: no incluye owner_user_id, organization_id ni rutas internas.
create or replace function public.m22_list_catalog(p_category text default null, p_city text default null)
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m22_public_listing_json(p)
  from public.m22_service_providers p
  where p.status = 'ACTIVE'
    and (p_category is null or p.category = upper(trim(p_category)))
    and (p_city is null or lower(p.city) = lower(trim(p_city)))
  order by p.display_name;
$$;

create or replace function public.m22_get_provider_detail(p_provider_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare p public.m22_service_providers;
begin
  select * into p from public.m22_service_providers where id = p_provider_id and status = 'ACTIVE';
  if not found then raise exception 'M22_PROVIDER_NOT_PUBLIC'; end if;
  return jsonb_build_object(
    'display_name', p.display_name, 'category', p.category, 'description', p.description, 'city', p.city,
    'branches', coalesce((select jsonb_agg(jsonb_build_object(
      'name', b.name, 'city', b.city, 'neighborhood', b.neighborhood,
      'coverage', case b.coverage_type when 'CITY' then b.coverage_city
        when 'NEIGHBORHOOD' then b.coverage_city || ' · ' || b.coverage_neighborhood
        else b.coverage_city || ' · ' || b.coverage_radius_km || ' km' end
    ) order by b.name) from public.m22_provider_branches b where b.provider_id = p.id and b.status = 'ACTIVE'), '[]'::jsonb),
    'offerings', coalesce((select jsonb_agg(jsonb_build_object(
      'name', o.name, 'description', o.description, 'price_type', o.price_type,
      'price_amount_cents', o.price_amount_cents, 'currency', o.currency
    ) order by o.name) from public.m22_service_offerings o where o.provider_id = p.id and o.active), '[]'::jsonb)
  );
end;
$$;

create or replace function public.m22_list_my_providers()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m22_provider_json(p)
  from public.m22_service_providers p
  where p.owner_user_id = public._m22_require_authenticated()
     or (p.organization_id is not null and public.has_org_permission(p.organization_id, 'provider.profile.read'))
  order by p.updated_at desc;
$$;

create or replace function public.m22_create_provider(
  p_display_name text, p_category text, p_description text, p_city text, p_organization_id uuid default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m22_require_authenticated(); v_provider public.m22_service_providers;
begin
  perform public._m22_validate_provider(p_display_name, p_category, p_description, p_city, 'DRAFT');
  if p_organization_id is not null and not public.has_org_permission(p_organization_id, 'provider.profile.manage') then
    raise exception 'M22_PERMISSION_DENIED';
  end if;
  insert into public.m22_service_providers (organization_id, owner_user_id, display_name, category, description, city)
  values (p_organization_id, v_actor, trim(p_display_name), upper(trim(p_category)), trim(p_description), trim(p_city))
  returning * into v_provider;
  return public._m22_provider_json(v_provider);
end;
$$;

create or replace function public.m22_update_provider(
  p_provider_id uuid, p_display_name text default null, p_description text default null,
  p_city text default null, p_status text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m22_require_authenticated(); v_provider public.m22_service_providers;
begin
  select * into v_provider from public.m22_service_providers where id = p_provider_id;
  if not found then raise exception 'M22_PROVIDER_NOT_FOUND'; end if;
  if not public._m22_can_manage(v_provider, v_actor) then raise exception 'M22_PERMISSION_DENIED'; end if;
  perform public._m22_validate_provider(coalesce(p_display_name, v_provider.display_name), v_provider.category,
    coalesce(p_description, v_provider.description), coalesce(p_city, v_provider.city), coalesce(p_status, v_provider.status));
  update public.m22_service_providers set display_name = coalesce(nullif(trim(p_display_name), ''), display_name),
    description = coalesce(nullif(trim(p_description), ''), description), city = coalesce(nullif(trim(p_city), ''), city),
    status = coalesce(upper(nullif(trim(p_status), '')), status), updated_at = timezone('utc', now())
  where id = p_provider_id returning * into v_provider;
  return public._m22_provider_json(v_provider);
end;
$$;

create or replace function public.m22_upsert_branch(
  p_provider_id uuid, p_branch_id uuid default null, p_name text default null, p_city text default null,
  p_neighborhood text default null, p_coverage_type text default null, p_coverage_city text default null,
  p_coverage_neighborhood text default null, p_coverage_radius_km integer default null, p_status text default 'ACTIVE'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m22_require_authenticated(); v_provider public.m22_service_providers; v_branch public.m22_provider_branches;
begin
  select * into v_provider from public.m22_service_providers where id = p_provider_id;
  if not found then raise exception 'M22_PROVIDER_NOT_FOUND'; end if;
  if not public._m22_can_manage(v_provider, v_actor) then raise exception 'M22_PERMISSION_DENIED'; end if;
  if char_length(trim(coalesce(p_name, ''))) not between 2 and 160 or char_length(trim(coalesce(p_city, ''))) not between 2 and 120
    or upper(coalesce(p_coverage_type, '')) not in ('CITY','NEIGHBORHOOD','RADIUS')
    or upper(coalesce(p_status, '')) not in ('ACTIVE','INACTIVE','ARCHIVED') then raise exception 'M22_INVALID_BRANCH'; end if;
  if upper(p_coverage_type) = 'RADIUS' and p_coverage_radius_km not between 1 and 200 then raise exception 'M22_INVALID_BRANCH'; end if;
  if upper(p_coverage_type) = 'NEIGHBORHOOD' and char_length(trim(coalesce(p_coverage_neighborhood, ''))) < 2 then raise exception 'M22_INVALID_BRANCH'; end if;
  if p_branch_id is null then
    insert into public.m22_provider_branches (provider_id, name, city, neighborhood, coverage_type, coverage_city, coverage_neighborhood, coverage_radius_km, status)
    values (p_provider_id, trim(p_name), trim(p_city), nullif(trim(p_neighborhood), ''), upper(p_coverage_type), trim(p_coverage_city),
      case when upper(p_coverage_type) = 'NEIGHBORHOOD' then trim(p_coverage_neighborhood) else null end,
      case when upper(p_coverage_type) = 'RADIUS' then p_coverage_radius_km else null end, upper(p_status))
    returning * into v_branch;
  else
    update public.m22_provider_branches set name = trim(p_name), city = trim(p_city), neighborhood = nullif(trim(p_neighborhood), ''),
      coverage_type = upper(p_coverage_type), coverage_city = trim(p_coverage_city),
      coverage_neighborhood = case when upper(p_coverage_type) = 'NEIGHBORHOOD' then trim(p_coverage_neighborhood) else null end,
      coverage_radius_km = case when upper(p_coverage_type) = 'RADIUS' then p_coverage_radius_km else null end,
      status = upper(p_status), updated_at = timezone('utc', now())
    where id = p_branch_id and provider_id = p_provider_id returning * into v_branch;
    if not found then raise exception 'M22_BRANCH_NOT_FOUND'; end if;
  end if;
  return public._m22_branch_json(v_branch);
end;
$$;

create or replace function public.m22_upsert_offering(
  p_provider_id uuid, p_offering_id uuid default null, p_branch_id uuid default null, p_name text default null,
  p_description text default null, p_price_type text default null, p_price_amount_cents bigint default null,
  p_currency text default 'ARS', p_active boolean default true
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m22_require_authenticated(); v_provider public.m22_service_providers; v_offering public.m22_service_offerings;
begin
  select * into v_provider from public.m22_service_providers where id = p_provider_id;
  if not found then raise exception 'M22_PROVIDER_NOT_FOUND'; end if;
  if not public._m22_can_manage_catalog(v_provider, v_actor) then raise exception 'M22_PERMISSION_DENIED'; end if;
  if p_branch_id is not null and not exists (select 1 from public.m22_provider_branches where id = p_branch_id and provider_id = p_provider_id) then raise exception 'M22_BRANCH_NOT_FOUND'; end if;
  if char_length(trim(coalesce(p_name, ''))) not between 2 and 160 or char_length(trim(coalesce(p_description, ''))) not between 10 and 2000
    or upper(coalesce(p_price_type, '')) not in ('FIXED','FROM','QUOTE')
    or (upper(p_price_type) in ('FIXED','FROM') and coalesce(p_price_amount_cents, 0) <= 0)
    or (upper(p_price_type) = 'QUOTE' and p_price_amount_cents is not null) then raise exception 'M22_INVALID_OFFERING'; end if;
  if p_offering_id is null then
    insert into public.m22_service_offerings (provider_id, branch_id, name, description, price_type, price_amount_cents, currency, active)
    values (p_provider_id, p_branch_id, trim(p_name), trim(p_description), upper(p_price_type), p_price_amount_cents, upper(trim(p_currency)), coalesce(p_active, true))
    returning * into v_offering;
  else
    update public.m22_service_offerings set branch_id = p_branch_id, name = trim(p_name), description = trim(p_description),
      price_type = upper(p_price_type), price_amount_cents = p_price_amount_cents, currency = upper(trim(p_currency)),
      active = coalesce(p_active, active), updated_at = timezone('utc', now())
    where id = p_offering_id and provider_id = p_provider_id returning * into v_offering;
    if not found then raise exception 'M22_OFFERING_NOT_FOUND'; end if;
  end if;
  return public._m22_offering_json(v_offering);
end;
$$;

create or replace function public.m22_archive_provider(p_provider_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m22_require_authenticated(); v_provider public.m22_service_providers;
begin
  select * into v_provider from public.m22_service_providers where id = p_provider_id;
  if not found then raise exception 'M22_PROVIDER_NOT_FOUND'; end if;
  if not public._m22_can_manage(v_provider, v_actor) then raise exception 'M22_PERMISSION_DENIED'; end if;
  update public.m22_service_providers set status = 'ARCHIVED', updated_at = timezone('utc', now())
  where id = p_provider_id returning * into v_provider;
  return public._m22_provider_json(v_provider);
end;
$$;

-- 5. Ejecutables mínimos; anon solo puede consumir las proyecciones públicas.
revoke all on function public._m22_require_authenticated() from public, anon, authenticated;
revoke all on function public._m22_can_manage(public.m22_service_providers, uuid) from public, anon, authenticated;
revoke all on function public._m22_can_manage_catalog(public.m22_service_providers, uuid) from public, anon, authenticated;
revoke all on function public._m22_validate_provider(text, text, text, text, text) from public, anon, authenticated;
revoke all on function public._m22_provider_json(public.m22_service_providers) from public, anon, authenticated;
revoke all on function public._m22_branch_json(public.m22_provider_branches) from public, anon, authenticated;
revoke all on function public._m22_offering_json(public.m22_service_offerings) from public, anon, authenticated;
revoke all on function public._m22_public_listing_json(public.m22_service_providers) from public, anon, authenticated;
revoke all on function public.m22_list_catalog(text, text) from public;
revoke all on function public.m22_get_provider_detail(uuid) from public;
revoke all on function public.m22_list_my_providers() from public, anon;
revoke all on function public.m22_create_provider(text, text, text, text, uuid) from public, anon;
revoke all on function public.m22_update_provider(uuid, text, text, text, text) from public, anon;
revoke all on function public.m22_upsert_branch(uuid, uuid, text, text, text, text, text, text, integer, text) from public, anon;
revoke all on function public.m22_upsert_offering(uuid, uuid, uuid, text, text, text, bigint, text, boolean) from public, anon;
revoke all on function public.m22_archive_provider(uuid) from public, anon;
grant execute on function public.m22_list_catalog(text, text) to anon, authenticated;
grant execute on function public.m22_get_provider_detail(uuid) to anon, authenticated;
grant execute on function public.m22_list_my_providers() to authenticated;
grant execute on function public.m22_create_provider(text, text, text, text, uuid) to authenticated;
grant execute on function public.m22_update_provider(uuid, text, text, text, text) to authenticated;
grant execute on function public.m22_upsert_branch(uuid, uuid, text, text, text, text, text, text, integer, text) to authenticated;
grant execute on function public.m22_upsert_offering(uuid, uuid, uuid, text, text, text, bigint, text, boolean) to authenticated;
grant execute on function public.m22_archive_provider(uuid) to authenticated;

commit;
