-- =============================================================================
-- LeoVer M25 — migración 070: marketplace, carrito y pedidos (Bloque 2).
-- Forward-only sobre 001–069. LOCAL ONLY: no aplicar a staging sin autorización.
-- Sin pagos, PSP, checkout ni campos financieros (M24 pospuesto).
-- =============================================================================

begin;

insert into public.organization_permissions (code, description) values
  ('marketplace.shop.read', 'Ver tiendas marketplace de la organización'),
  ('marketplace.shop.manage', 'Gestionar tiendas y productos marketplace'),
  ('marketplace.order.manage', 'Gestionar pedidos de tiendas marketplace')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_id)
select r.id, p.id from public.organization_roles r
cross join public.organization_permissions p
where r.code in ('OWNER', 'ADMIN', 'MANAGER')
  and p.code in ('marketplace.shop.read', 'marketplace.shop.manage', 'marketplace.order.manage')
on conflict do nothing;

create table if not exists public.m25_shops (
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
  constraint m25_shop_category_chk check (category = any (array['PET_FOOD','ACCESSORIES','HEALTH','GROOMING','OTHER']::text[])),
  constraint m25_shop_status_chk check (status = any (array['DRAFT','ACTIVE','SUSPENDED','ARCHIVED']::text[])),
  constraint m25_shop_name_chk check (char_length(trim(display_name)) between 2 and 160),
  constraint m25_shop_description_chk check (char_length(trim(description)) between 10 and 2000),
  constraint m25_shop_city_chk check (char_length(trim(city)) between 2 and 120)
);

create table if not exists public.m25_products (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.m25_shops(id) on delete cascade,
  sku text not null,
  name text not null,
  description text not null,
  list_price_cents bigint not null,
  currency text not null default 'ARS',
  stock_quantity integer not null default 0,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m25_product_status_chk check (status = any (array['ACTIVE','INACTIVE','ARCHIVED']::text[])),
  constraint m25_product_price_chk check (list_price_cents > 0),
  constraint m25_product_stock_chk check (stock_quantity >= 0),
  constraint m25_product_sku_shop_uq unique (shop_id, sku)
);

create table if not exists public.m25_promotions (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.m25_shops(id) on delete cascade,
  code text not null,
  promo_type text not null,
  promo_value bigint not null,
  status text not null default 'DRAFT',
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  constraint m25_promo_type_chk check (promo_type = any (array['PERCENTAGE','FIXED_AMOUNT']::text[])),
  constraint m25_promo_status_chk check (status = any (array['DRAFT','ACTIVE','EXPIRED','ARCHIVED']::text[])),
  constraint m25_promo_code_shop_uq unique (shop_id, code)
);

create table if not exists public.m25_cart_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade,
  shop_id uuid not null references public.m25_shops(id) on delete cascade,
  product_id uuid not null references public.m25_products(id) on delete restrict,
  quantity integer not null,
  client_line_id text,
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m25_cart_qty_chk check (quantity between 1 and 99),
  constraint m25_cart_client_line_uq unique (user_id, client_line_id)
);

create table if not exists public.m25_orders (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.m25_shops(id) on delete restrict,
  customer_user_id uuid not null references public.users(id) on delete restrict,
  status text not null default 'SUBMITTED',
  subtotal_cents bigint not null,
  discount_cents bigint not null default 0,
  currency text not null default 'ARS',
  shipping_mode text not null,
  shipping_city text not null,
  shipping_notes text,
  promotion_code text,
  client_request_id text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m25_order_status_chk check (status = any (array[
    'DRAFT','SUBMITTED','ACCEPTED','PREPARING','SHIPPED','DELIVERED','CANCELLED','RETURN_REQUESTED','RETURNED'
  ]::text[])),
  constraint m25_order_shipping_mode_chk check (shipping_mode = any (array['PICKUP','DELIVERY']::text[])),
  constraint m25_order_subtotal_chk check (subtotal_cents >= 0),
  constraint m25_order_discount_chk check (discount_cents >= 0),
  constraint m25_order_client_request_uq unique (customer_user_id, client_request_id)
);

create table if not exists public.m25_order_lines (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.m25_orders(id) on delete cascade,
  product_id uuid not null references public.m25_products(id) on delete restrict,
  product_name text not null,
  quantity integer not null,
  unit_price_cents bigint not null,
  currency text not null default 'ARS',
  constraint m25_order_line_qty_chk check (quantity between 1 and 99),
  constraint m25_order_line_price_chk check (unit_price_cents > 0)
);

create table if not exists public.m25_returns (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.m25_orders(id) on delete restrict,
  customer_user_id uuid not null references public.users(id) on delete restrict,
  reason text not null,
  status text not null default 'REQUESTED',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint m25_return_status_chk check (status = any (array['REQUESTED','APPROVED','REJECTED','RECEIVED','CLOSED']::text[])),
  constraint m25_return_reason_chk check (char_length(trim(reason)) between 10 and 500)
);

create index if not exists m25_shop_category_status_idx on public.m25_shops(category, status);
create index if not exists m25_product_shop_idx on public.m25_products(shop_id);
create index if not exists m25_cart_user_idx on public.m25_cart_items(user_id);
create index if not exists m25_order_customer_idx on public.m25_orders(customer_user_id);
create index if not exists m25_order_shop_idx on public.m25_orders(shop_id);

alter table public.m25_shops enable row level security;
alter table public.m25_products enable row level security;
alter table public.m25_promotions enable row level security;
alter table public.m25_cart_items enable row level security;
alter table public.m25_orders enable row level security;
alter table public.m25_order_lines enable row level security;
alter table public.m25_returns enable row level security;

create policy m25_shop_deny on public.m25_shops for all to authenticated using (false) with check (false);
create policy m25_product_deny on public.m25_products for all to authenticated using (false) with check (false);
create policy m25_promo_deny on public.m25_promotions for all to authenticated using (false) with check (false);
create policy m25_cart_deny on public.m25_cart_items for all to authenticated using (false) with check (false);
create policy m25_order_deny on public.m25_orders for all to authenticated using (false) with check (false);
create policy m25_order_line_deny on public.m25_order_lines for all to authenticated using (false) with check (false);
create policy m25_return_deny on public.m25_returns for all to authenticated using (false) with check (false);

revoke all on table public.m25_shops, public.m25_products, public.m25_promotions,
  public.m25_cart_items, public.m25_orders, public.m25_order_lines, public.m25_returns
  from public, anon, authenticated;
grant all on table public.m25_shops, public.m25_products, public.m25_promotions,
  public.m25_cart_items, public.m25_orders, public.m25_order_lines, public.m25_returns to service_role;

create or replace function public._m25_require_authenticated()
returns uuid language plpgsql stable security definer set search_path = public as $$
declare v_actor uuid := auth.uid();
begin
  if v_actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return v_actor;
end;
$$;

create or replace function public._m25_can_manage_shop(p_shop public.m25_shops, p_actor uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_shop.owner_user_id = p_actor
    or (p_shop.organization_id is not null and public.has_org_permission(p_shop.organization_id, 'marketplace.shop.manage'));
$$;

create or replace function public._m25_public_shop_json(p public.m25_shops)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'display_name', p.display_name, 'category', p.category, 'description', p.description, 'city', p.city,
    'product_count', (select count(*) from public.m25_products pr where pr.shop_id = p.id and pr.status = 'ACTIVE'),
    'price_summary', (
      select case when min(pr.list_price_cents) is null then null
        else 'ARS ' || min(pr.list_price_cents)::text end
      from public.m25_products pr where pr.shop_id = p.id and pr.status = 'ACTIVE'
    )
  );
$$;

create or replace function public.m25_list_catalog(p_category text default null, p_city text default null)
returns setof jsonb language sql stable security definer set search_path = public as $$
  select public._m25_public_shop_json(s)
  from public.m25_shops s
  where s.status = 'ACTIVE'
    and (p_category is null or s.category = upper(trim(p_category)))
    and (p_city is null or lower(s.city) = lower(trim(p_city)))
  order by s.display_name;
$$;

create or replace function public.m25_get_shop_detail(p_shop_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare s public.m25_shops;
begin
  select * into s from public.m25_shops where id = p_shop_id and status = 'ACTIVE';
  if not found then raise exception 'M25_SHOP_NOT_PUBLIC'; end if;
  return jsonb_build_object(
    'display_name', s.display_name, 'category', s.category, 'description', s.description, 'city', s.city,
    'products', coalesce((
      select jsonb_agg(jsonb_build_object(
        'name', pr.name, 'description', pr.description,
        'list_price_cents', pr.list_price_cents, 'currency', pr.currency,
        'in_stock', pr.stock_quantity > 0
      ) order by pr.name)
      from public.m25_products pr where pr.shop_id = s.id and pr.status = 'ACTIVE'
    ), '[]'::jsonb)
  );
end;
$$;

create or replace function public.m25_list_my_shops()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', s.id, 'owner_user_id', s.owner_user_id, 'organization_id', s.organization_id,
    'display_name', s.display_name, 'category', s.category, 'description', s.description,
    'city', s.city, 'status', s.status,
    'created_at', s.created_at, 'updated_at', s.updated_at
  )
  from public.m25_shops s
  where s.owner_user_id = public._m25_require_authenticated()
     or (s.organization_id is not null and public.has_org_permission(s.organization_id, 'marketplace.shop.read'))
  order by s.updated_at desc;
$$;

create or replace function public.m25_create_shop(
  p_display_name text, p_category text, p_description text, p_city text, p_organization_id uuid default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m25_require_authenticated(); v_shop public.m25_shops;
begin
  insert into public.m25_shops (owner_user_id, organization_id, display_name, category, description, city, status)
  values (v_actor, p_organization_id, trim(p_display_name), upper(trim(p_category)), trim(p_description), trim(p_city), 'DRAFT')
  returning * into v_shop;
  return jsonb_build_object(
    'id', v_shop.id, 'owner_user_id', v_shop.owner_user_id, 'organization_id', v_shop.organization_id,
    'display_name', v_shop.display_name, 'category', v_shop.category, 'description', v_shop.description,
    'city', v_shop.city, 'status', v_shop.status,
    'created_at', v_shop.created_at, 'updated_at', v_shop.updated_at
  );
end;
$$;

create or replace function public.m25_list_cart()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', c.id, 'user_id', c.user_id, 'product_id', c.product_id, 'shop_id', c.shop_id,
    'quantity', c.quantity, 'client_line_id', c.client_line_id, 'updated_at', c.updated_at
  )
  from public.m25_cart_items c
  where c.user_id = public._m25_require_authenticated()
  order by c.updated_at desc;
$$;

create or replace function public.m25_add_to_cart(
  p_product_id uuid, p_quantity integer, p_client_line_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m25_require_authenticated();
  v_product public.m25_products; v_item public.m25_cart_items;
begin
  if p_client_line_id is not null then
    select * into v_item from public.m25_cart_items where user_id = v_actor and client_line_id = p_client_line_id;
    if found then
      return jsonb_build_object('id', v_item.id, 'user_id', v_item.user_id, 'product_id', v_item.product_id,
        'shop_id', v_item.shop_id, 'quantity', v_item.quantity, 'client_line_id', v_item.client_line_id, 'updated_at', v_item.updated_at);
    end if;
  end if;
  select * into v_product from public.m25_products where id = p_product_id and status = 'ACTIVE';
  if not found then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
  if v_product.stock_quantity < p_quantity then raise exception 'M25_OUT_OF_STOCK'; end if;
  insert into public.m25_cart_items (user_id, shop_id, product_id, quantity, client_line_id)
  values (v_actor, v_product.shop_id, p_product_id, p_quantity, p_client_line_id)
  returning * into v_item;
  return jsonb_build_object('id', v_item.id, 'user_id', v_item.user_id, 'product_id', v_item.product_id,
    'shop_id', v_item.shop_id, 'quantity', v_item.quantity, 'client_line_id', v_item.client_line_id, 'updated_at', v_item.updated_at);
end;
$$;

create or replace function public.m25_list_my_orders()
returns setof jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', o.id, 'shop_name', s.display_name, 'status', o.status,
    'line_count', (select count(*) from public.m25_order_lines ol where ol.order_id = o.id),
    'subtotal_cents', o.subtotal_cents, 'currency', o.currency, 'created_at', o.created_at
  )
  from public.m25_orders o
  join public.m25_shops s on s.id = o.shop_id
  where o.customer_user_id = public._m25_require_authenticated()
  order by o.created_at desc;
$$;

revoke all on function public._m25_require_authenticated(), public._m25_can_manage_shop(public.m25_shops, uuid),
  public._m25_public_shop_json(public.m25_shops) from public, anon, authenticated;
revoke all on function public.m25_list_catalog(text, text) from public;
revoke all on function public.m25_get_shop_detail(uuid) from public;
grant execute on function public.m25_list_catalog(text, text), public.m25_get_shop_detail(uuid) to anon, authenticated;
grant execute on function public.m25_list_my_shops(), public.m25_create_shop(text, text, text, text, uuid),
  public.m25_list_cart(), public.m25_add_to_cart(uuid, integer, text), public.m25_list_my_orders() to authenticated;

commit;
