-- =============================================================================
-- LeoVer M25 — migración 071: operaciones, inventario reservado y devoluciones.
-- Forward-only sobre 070. LOCAL ONLY: no aplicar a staging sin autorización.
-- Sin pagos, PSP, checkout ni campos financieros (M24 pospuesto).
-- =============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Inventario reservado en productos
-- ---------------------------------------------------------------------------

alter table public.m25_products
  add column if not exists stock_reserved integer not null default 0;

alter table public.m25_products drop constraint if exists m25_product_stock_reserved_chk;
alter table public.m25_products add constraint m25_product_stock_reserved_chk
  check (stock_reserved >= 0 and stock_reserved <= stock_quantity);

-- ---------------------------------------------------------------------------
-- 2. Ampliación de estados (shop / product / order)
-- ---------------------------------------------------------------------------

alter table public.m25_shops drop constraint if exists m25_shop_status_chk;
alter table public.m25_shops add constraint m25_shop_status_chk check (status = any (array[
  'DRAFT','ACTIVE','PAUSED','SUSPENDED','CLOSED','ARCHIVED'
]::text[]));

alter table public.m25_products drop constraint if exists m25_product_status_chk;
alter table public.m25_products add constraint m25_product_status_chk check (status = any (array[
  'DRAFT','ACTIVE','INACTIVE','OUT_OF_STOCK','PAUSED','ARCHIVED','REMOVED_BY_MODERATION'
]::text[]));

alter table public.m25_orders drop constraint if exists m25_order_status_chk;
alter table public.m25_orders add constraint m25_order_status_chk check (status = any (array[
  'DRAFT','SUBMITTED','ACCEPTED','PREPARING','READY_FOR_DISPATCH','SHIPPED','DELIVERED',
  'REJECTED','CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT',
  'RETURN_REQUESTED','RETURNED','CLOSED'
]::text[]));

-- ---------------------------------------------------------------------------
-- 3–5. Tablas append-only e historial de devoluciones
-- ---------------------------------------------------------------------------

create table if not exists public.m25_order_history (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.m25_orders(id) on delete cascade,
  actor_user_id uuid references public.users(id) on delete set null,
  from_status text,
  to_status text not null,
  public_reason text,
  actor_role text not null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m25_order_history_role_chk check (actor_role = any (array['CUSTOMER','MERCHANT','SYSTEM']::text[])),
  constraint m25_order_history_to_status_chk check (to_status = any (array[
    'DRAFT','SUBMITTED','ACCEPTED','PREPARING','READY_FOR_DISPATCH','SHIPPED','DELIVERED',
    'REJECTED','CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT',
    'RETURN_REQUESTED','RETURNED','CLOSED'
  ]::text[]))
);

create table if not exists public.m25_stock_movements (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.m25_products(id) on delete restrict,
  movement_type text not null,
  quantity integer not null,
  reservation_key text,
  reason text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint m25_stock_movement_type_chk check (movement_type = any (array[
    'RESERVE','RELEASE','COMMIT','REPLENISH','ADJUST'
  ]::text[])),
  constraint m25_stock_movement_qty_chk check (quantity > 0)
);

create unique index if not exists m25_stock_reserve_key_uq
  on public.m25_stock_movements(product_id, reservation_key)
  where reservation_key is not null and movement_type = 'RESERVE';

create table if not exists public.m25_return_lines (
  id uuid primary key default gen_random_uuid(),
  return_id uuid not null references public.m25_returns(id) on delete cascade,
  product_id uuid not null references public.m25_products(id) on delete restrict,
  quantity integer not null,
  constraint m25_return_line_qty_chk check (quantity between 1 and 99)
);

-- ---------------------------------------------------------------------------
-- 6–7. Columnas snapshot en líneas y tracking en pedidos
-- ---------------------------------------------------------------------------

alter table public.m25_order_lines
  add column if not exists discount_cents bigint not null default 0,
  add column if not exists subtotal_cents bigint;

update public.m25_order_lines
set subtotal_cents = unit_price_cents * quantity
where subtotal_cents is null;

alter table public.m25_order_lines alter column subtotal_cents set not null;

alter table public.m25_order_lines drop constraint if exists m25_order_line_discount_chk;
alter table public.m25_order_lines add constraint m25_order_line_discount_chk check (discount_cents >= 0);
alter table public.m25_order_lines drop constraint if exists m25_order_line_subtotal_chk;
alter table public.m25_order_lines add constraint m25_order_line_subtotal_chk check (subtotal_cents >= 0);

alter table public.m25_orders
  add column if not exists tracking_code text,
  add column if not exists carrier_text text,
  add column if not exists dispatched_at timestamptz,
  add column if not exists delivered_at timestamptz;

alter table public.m25_returns
  add column if not exists client_request_id text;

create unique index if not exists m25_return_client_request_uq
  on public.m25_returns(customer_user_id, client_request_id)
  where client_request_id is not null;

create index if not exists m25_order_history_order_idx on public.m25_order_history(order_id, created_at);
create index if not exists m25_stock_movement_product_idx on public.m25_stock_movements(product_id, created_at);
create index if not exists m25_return_line_return_idx on public.m25_return_lines(return_id);

alter table public.m25_order_history enable row level security;
alter table public.m25_stock_movements enable row level security;
alter table public.m25_return_lines enable row level security;

create policy m25_order_history_deny on public.m25_order_history for all to authenticated using (false) with check (false);
create policy m25_stock_movement_deny on public.m25_stock_movements for all to authenticated using (false) with check (false);
create policy m25_return_line_deny on public.m25_return_lines for all to authenticated using (false) with check (false);

revoke all on table public.m25_order_history, public.m25_stock_movements, public.m25_return_lines
  from public, anon, authenticated;
grant all on table public.m25_order_history, public.m25_stock_movements, public.m25_return_lines to service_role;

-- Append-only: bloquear UPDATE/DELETE para clientes
create or replace function public._m25_append_only_guard()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if current_user in ('authenticated', 'anon') then
    raise exception 'M25_IMMUTABLE';
  end if;
  if tg_op = 'DELETE' then return old; end if;
  return new;
end;
$$;

drop trigger if exists trg_m25_order_history_immutable on public.m25_order_history;
create trigger trg_m25_order_history_immutable
  before update or delete on public.m25_order_history
  for each row execute function public._m25_append_only_guard();

drop trigger if exists trg_m25_stock_movements_immutable on public.m25_stock_movements;
create trigger trg_m25_stock_movements_immutable
  before update or delete on public.m25_stock_movements
  for each row execute function public._m25_append_only_guard();

-- ---------------------------------------------------------------------------
-- 8. Helpers internos (SECURITY DEFINER, search_path=public)
-- ---------------------------------------------------------------------------

create or replace function public._m25_reserve_stock(
  p_product_id uuid, p_qty integer, p_reservation_key text default null
) returns void language plpgsql security definer set search_path = public as $$
declare v_updated integer;
begin
  if p_qty <= 0 then raise exception 'M25_INVALID_QUANTITY'; end if;
  if p_reservation_key is not null and exists (
    select 1 from public.m25_stock_movements
    where product_id = p_product_id and reservation_key = p_reservation_key and movement_type = 'RESERVE'
  ) then
    return;
  end if;
  update public.m25_products
  set stock_reserved = stock_reserved + p_qty, updated_at = timezone('utc', now())
  where id = p_product_id and (stock_quantity - stock_reserved) >= p_qty;
  get diagnostics v_updated = row_count;
  if v_updated = 0 then raise exception 'M25_OUT_OF_STOCK'; end if;
  insert into public.m25_stock_movements(product_id, movement_type, quantity, reservation_key)
  values (p_product_id, 'RESERVE', p_qty, p_reservation_key);
end;
$$;

create or replace function public._m25_release_stock(
  p_product_id uuid, p_qty integer, p_reason text default null
) returns void language plpgsql security definer set search_path = public as $$
declare v_release integer;
begin
  if p_qty <= 0 then return; end if;
  select least(p_qty, stock_reserved) into v_release from public.m25_products where id = p_product_id for update;
  if not found then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
  if v_release <= 0 then return; end if;
  update public.m25_products
  set stock_reserved = stock_reserved - v_release, updated_at = timezone('utc', now())
  where id = p_product_id;
  insert into public.m25_stock_movements(product_id, movement_type, quantity, reason)
  values (p_product_id, 'RELEASE', v_release, p_reason);
end;
$$;

create or replace function public._m25_commit_stock(
  p_product_id uuid, p_qty integer, p_reason text default null
) returns void language plpgsql security definer set search_path = public as $$
declare v_commit integer;
begin
  if p_qty <= 0 then return; end if;
  select least(p_qty, stock_reserved) into v_commit from public.m25_products where id = p_product_id for update;
  if not found then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
  if v_commit <= 0 then return; end if;
  update public.m25_products
  set stock_quantity = stock_quantity - v_commit,
      stock_reserved = stock_reserved - v_commit,
      updated_at = timezone('utc', now())
  where id = p_product_id;
  insert into public.m25_stock_movements(product_id, movement_type, quantity, reason)
  values (p_product_id, 'COMMIT', v_commit, p_reason);
end;
$$;

create or replace function public._m25_calculate_promotion(
  p_shop_id uuid, p_promotion_code text, p_subtotal_cents bigint
) returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_now timestamptz := timezone('utc', now());
  v_promo public.m25_promotions;
  v_discount bigint := 0;
  v_code text := nullif(upper(trim(p_promotion_code)), '');
begin
  if p_subtotal_cents <= 0 then
    return jsonb_build_object('promotion_id', null, 'code', null, 'discount_cents', 0);
  end if;
  if v_code is not null then
    select * into v_promo from public.m25_promotions
    where shop_id = p_shop_id and upper(code) = v_code and status = 'ACTIVE'
      and starts_at <= v_now and ends_at >= v_now
    limit 1;
  else
    select p.* into v_promo from public.m25_promotions p
    where p.shop_id = p_shop_id and p.status = 'ACTIVE'
      and p.starts_at <= v_now and p.ends_at >= v_now
    order by case p.promo_type
      when 'PERCENTAGE' then (p_subtotal_cents * p.promo_value / 100)
      when 'FIXED_AMOUNT' then least(p_subtotal_cents, p.promo_value)
      else 0 end desc
    limit 1;
  end if;
  if not found then
    return jsonb_build_object('promotion_id', null, 'code', null, 'discount_cents', 0);
  end if;
  v_discount := case v_promo.promo_type
    when 'PERCENTAGE' then p_subtotal_cents * v_promo.promo_value / 100
    when 'FIXED_AMOUNT' then least(p_subtotal_cents, v_promo.promo_value)
    else 0 end;
  v_discount := greatest(0, least(p_subtotal_cents, v_discount));
  return jsonb_build_object(
    'promotion_id', v_promo.id, 'code', v_promo.code, 'discount_cents', v_discount
  );
end;
$$;

create or replace function public._m25_validate_order_transition(p_from text, p_to text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  if p_from = p_to then return; end if;
  if p_from = any (array[
    'CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT','REJECTED','RETURNED','CLOSED'
  ]::text[]) then raise exception 'M25_ORDER_TERMINAL'; end if;
  if p_from = 'DRAFT' and p_to in ('SUBMITTED','CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT') then return; end if;
  if p_from = 'SUBMITTED' and p_to in ('ACCEPTED','REJECTED','CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT') then return; end if;
  if p_from = 'ACCEPTED' and p_to in ('PREPARING','CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT') then return; end if;
  if p_from = 'PREPARING' and p_to in ('READY_FOR_DISPATCH','SHIPPED','CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT') then return; end if;
  if p_from = 'READY_FOR_DISPATCH' and p_to in ('SHIPPED','CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT') then return; end if;
  if p_from = 'SHIPPED' and p_to = 'DELIVERED' then return; end if;
  if p_from = 'DELIVERED' and p_to in ('RETURN_REQUESTED','CLOSED') then return; end if;
  if p_from = 'RETURN_REQUESTED' and p_to = 'RETURNED' then return; end if;
  raise exception 'M25_INVALID_ORDER_TRANSITION';
end;
$$;

create or replace function public._m25_validate_return_transition(p_from text, p_to text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  if p_from = p_to then return; end if;
  if p_from = 'CLOSED' then raise exception 'M25_RETURN_TERMINAL'; end if;
  if p_from = 'REQUESTED' and p_to in ('APPROVED','REJECTED') then return; end if;
  if p_from = 'APPROVED' and p_to = 'RECEIVED' then return; end if;
  if p_from = 'RECEIVED' and p_to = 'CLOSED' then return; end if;
  raise exception 'M25_INVALID_RETURN_TRANSITION';
end;
$$;

create or replace function public._m25_validate_shop_transition(p_from text, p_to text, p_has_active_product boolean)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  if p_from = p_to then return; end if;
  if p_to in ('ARCHIVED','CLOSED') then return; end if;
  if p_from in ('ARCHIVED','CLOSED') then raise exception 'M25_ARCHIVED_SHOP'; end if;
  if p_from = 'DRAFT' and p_to = 'ACTIVE' then
    if not p_has_active_product then raise exception 'M25_SHOP_NOT_READY_TO_PUBLISH'; end if;
    return;
  end if;
  if p_from = 'ACTIVE' and p_to in ('PAUSED','SUSPENDED','CLOSED') then return; end if;
  if p_from = 'PAUSED' and p_to = 'ACTIVE' then return; end if;
  if p_from = 'SUSPENDED' and p_to = 'ACTIVE' then return; end if;
  raise exception 'M25_INVALID_STATUS_TRANSITION';
end;
$$;

create or replace function public._m25_append_order_history(
  p_order_id uuid, p_actor uuid, p_from text, p_to text, p_role text, p_reason text default null
) returns void language sql security definer set search_path = public as $$
  insert into public.m25_order_history(order_id, actor_user_id, from_status, to_status, public_reason, actor_role)
  values (p_order_id, p_actor, p_from, p_to, nullif(trim(p_reason), ''), p_role);
$$;

create or replace function public._m25_release_order_lines(p_order_id uuid, p_reason text default null)
returns void language plpgsql security definer set search_path = public as $$
declare v_line record;
begin
  for v_line in select product_id, quantity from public.m25_order_lines where order_id = p_order_id loop
    perform public._m25_release_stock(v_line.product_id, v_line.quantity, p_reason);
  end loop;
end;
$$;

create or replace function public._m25_commit_order_lines(p_order_id uuid, p_reason text default null)
returns void language plpgsql security definer set search_path = public as $$
declare v_line record;
begin
  for v_line in select product_id, quantity from public.m25_order_lines where order_id = p_order_id loop
    perform public._m25_commit_stock(v_line.product_id, v_line.quantity, p_reason);
  end loop;
end;
$$;

create or replace function public._m25_order_line_json(p public.m25_order_lines)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'product_id', p.product_id, 'product_name', p.product_name, 'quantity', p.quantity,
    'unit_price_cents', p.unit_price_cents, 'currency', p.currency,
    'discount_cents', p.discount_cents, 'subtotal_cents', p.subtotal_cents
  );
$$;

create or replace function public._m25_order_json(p_order public.m25_orders, p_include_private boolean default true)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v_lines jsonb;
begin
  select coalesce(jsonb_agg(public._m25_order_line_json(ol) order by ol.product_name), '[]'::jsonb)
  into v_lines from public.m25_order_lines ol where ol.order_id = p_order.id;
  return jsonb_build_object(
    'id', p_order.id,
    'shop_id', p_order.shop_id,
    'customer_user_id', case when p_include_private then p_order.customer_user_id else null end,
    'status', p_order.status,
    'lines', v_lines,
    'subtotal_cents', p_order.subtotal_cents,
    'discount_cents', p_order.discount_cents,
    'currency', p_order.currency,
    'shipping_mode', p_order.shipping_mode,
    'shipping_city', case when p_include_private then p_order.shipping_city else null end,
    'shipping_notes', case when p_include_private then p_order.shipping_notes else null end,
    'promotion_code', p_order.promotion_code,
    'client_request_id', case when p_include_private then p_order.client_request_id else null end,
    'tracking_code', p_order.tracking_code,
    'carrier_text', p_order.carrier_text,
    'dispatched_at', p_order.dispatched_at,
    'delivered_at', p_order.delivered_at,
    'created_at', p_order.created_at,
    'updated_at', p_order.updated_at
  );
end;
$$;

create or replace function public._m25_return_json(p_ret public.m25_returns)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v_lines jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object(
    'product_id', rl.product_id, 'quantity', rl.quantity
  ) order by rl.product_id), '[]'::jsonb)
  into v_lines from public.m25_return_lines rl where rl.return_id = p_ret.id;
  return jsonb_build_object(
    'id', p_ret.id, 'order_id', p_ret.order_id, 'customer_user_id', p_ret.customer_user_id,
    'reason', p_ret.reason, 'status', p_ret.status, 'lines', v_lines,
    'created_at', p_ret.created_at, 'updated_at', p_ret.updated_at
  );
end;
$$;

create or replace function public._m25_transition_order(
  p_order_id uuid, p_target text, p_actor uuid, p_role text, p_public_reason text default null
) returns public.m25_orders language plpgsql security definer set search_path = public as $$
declare
  v_order public.m25_orders;
  v_previous text;
begin
  select * into v_order from public.m25_orders where id = p_order_id for update;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  if v_order.status = p_target then return v_order; end if;
  perform public._m25_validate_order_transition(v_order.status, p_target);
  v_previous := v_order.status;
  if p_target in ('REJECTED','CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT')
     and v_previous in ('SUBMITTED','ACCEPTED','PREPARING','READY_FOR_DISPATCH') then
    perform public._m25_release_order_lines(p_order_id, p_target);
  end if;
  if p_target = 'DELIVERED' and v_previous = 'SHIPPED' then
    perform public._m25_commit_order_lines(p_order_id, 'DELIVERED');
  end if;
  update public.m25_orders
  set status = p_target,
      delivered_at = case when p_target = 'DELIVERED' then timezone('utc', now()) else delivered_at end,
      updated_at = timezone('utc', now())
  where id = p_order_id
  returning * into v_order;
  perform public._m25_append_order_history(p_order_id, p_actor, v_previous, p_target, p_role, p_public_reason);
  return v_order;
end;
$$;

-- Mejorar add_to_cart (070) para considerar stock reservado
create or replace function public.m25_add_to_cart(
  p_product_id uuid, p_quantity integer, p_client_line_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_product public.m25_products;
  v_item public.m25_cart_items;
  v_available integer;
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
  v_available := v_product.stock_quantity - v_product.stock_reserved;
  if v_available < p_quantity then raise exception 'M25_OUT_OF_STOCK'; end if;
  insert into public.m25_cart_items (user_id, shop_id, product_id, quantity, client_line_id)
  values (v_actor, v_product.shop_id, p_product_id, p_quantity, p_client_line_id)
  returning * into v_item;
  return jsonb_build_object('id', v_item.id, 'user_id', v_item.user_id, 'product_id', v_item.product_id,
    'shop_id', v_item.shop_id, 'quantity', v_item.quantity, 'client_line_id', v_item.client_line_id, 'updated_at', v_item.updated_at);
end;
$$;

-- ---------------------------------------------------------------------------
-- 9. RPCs merchant / carrito / pedidos / devoluciones
-- ---------------------------------------------------------------------------

create or replace function public.m25_update_shop(
  p_shop_id uuid, p_display_name text default null, p_description text default null,
  p_city text default null, p_status text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_shop public.m25_shops;
  v_has_active boolean;
begin
  select * into v_shop from public.m25_shops where id = p_shop_id for update;
  if not found then raise exception 'M25_SHOP_NOT_FOUND'; end if;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  v_has_active := exists (
    select 1 from public.m25_products pr
    where pr.shop_id = p_shop_id and pr.status = 'ACTIVE' and pr.stock_quantity > pr.stock_reserved
  );
  if p_status is not null then
    perform public._m25_validate_shop_transition(v_shop.status, upper(trim(p_status)), v_has_active);
  end if;
  update public.m25_shops set
    display_name = coalesce(nullif(trim(p_display_name), ''), display_name),
    description = coalesce(nullif(trim(p_description), ''), description),
    city = coalesce(nullif(trim(p_city), ''), city),
    status = coalesce(upper(trim(p_status)), status),
    updated_at = timezone('utc', now())
  where id = p_shop_id
  returning * into v_shop;
  return jsonb_build_object(
    'id', v_shop.id, 'owner_user_id', v_shop.owner_user_id, 'organization_id', v_shop.organization_id,
    'display_name', v_shop.display_name, 'category', v_shop.category, 'description', v_shop.description,
    'city', v_shop.city, 'status', v_shop.status,
    'created_at', v_shop.created_at, 'updated_at', v_shop.updated_at
  );
end;
$$;

create or replace function public.m25_transition_shop(p_shop_id uuid, p_status text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  return public.m25_update_shop(p_shop_id, null, null, null, p_status);
end;
$$;

create or replace function public.m25_upsert_product(
  p_shop_id uuid, p_sku text, p_name text, p_description text, p_list_price_cents bigint,
  p_stock_quantity integer, p_product_id uuid default null, p_currency text default 'ARS', p_status text default 'ACTIVE'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_shop public.m25_shops;
  v_product public.m25_products;
begin
  select * into v_shop from public.m25_shops where id = p_shop_id;
  if not found then raise exception 'M25_SHOP_NOT_FOUND'; end if;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if p_list_price_cents <= 0 then raise exception 'M25_INVALID_PRICE'; end if;
  if p_stock_quantity < 0 then raise exception 'M25_INVALID_STOCK'; end if;
  if char_length(trim(p_name)) not between 2 and 120 then raise exception 'M25_INVALID_PRODUCT'; end if;
  if char_length(trim(p_description)) not between 10 and 2000 then raise exception 'M25_INVALID_PRODUCT'; end if;
  if p_product_id is not null then
    select * into v_product from public.m25_products where id = p_product_id and shop_id = p_shop_id for update;
    if not found then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
    if p_stock_quantity < v_product.stock_reserved then raise exception 'M25_INVALID_STOCK'; end if;
    update public.m25_products set
      sku = trim(p_sku), name = trim(p_name), description = trim(p_description),
      list_price_cents = p_list_price_cents, currency = coalesce(nullif(trim(p_currency), ''), 'ARS'),
      stock_quantity = p_stock_quantity, status = upper(trim(p_status)), updated_at = timezone('utc', now())
    where id = p_product_id returning * into v_product;
  else
    insert into public.m25_products(shop_id, sku, name, description, list_price_cents, currency, stock_quantity, status)
    values (p_shop_id, trim(p_sku), trim(p_name), trim(p_description), p_list_price_cents,
      coalesce(nullif(trim(p_currency), ''), 'ARS'), p_stock_quantity, upper(trim(p_status)))
    returning * into v_product;
  end if;
  return jsonb_build_object(
    'id', v_product.id, 'shop_id', v_product.shop_id, 'sku', v_product.sku, 'name', v_product.name,
    'description', v_product.description, 'list_price_cents', v_product.list_price_cents,
    'currency', v_product.currency, 'stock_quantity', v_product.stock_quantity,
    'stock_reserved', v_product.stock_reserved, 'status', v_product.status
  );
end;
$$;

create or replace function public.m25_upsert_promotion(
  p_shop_id uuid, p_code text, p_promo_type text, p_promo_value bigint,
  p_starts_at timestamptz, p_ends_at timestamptz,
  p_promotion_id uuid default null, p_status text default 'DRAFT'
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_shop public.m25_shops;
  v_promo public.m25_promotions;
  v_code text := upper(trim(p_code));
begin
  select * into v_shop from public.m25_shops where id = p_shop_id;
  if not found then raise exception 'M25_SHOP_NOT_FOUND'; end if;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if char_length(v_code) not between 3 and 32 then raise exception 'M25_INVALID_PROMOTION'; end if;
  if p_ends_at <= p_starts_at then raise exception 'M25_INVALID_PROMOTION'; end if;
  if upper(trim(p_promo_type)) = 'PERCENTAGE' and p_promo_value not between 1 and 100 then
    raise exception 'M25_INVALID_PROMOTION';
  end if;
  if upper(trim(p_promo_type)) = 'FIXED_AMOUNT' and p_promo_value <= 0 then
    raise exception 'M25_INVALID_PROMOTION';
  end if;
  if p_promotion_id is not null then
    select * into v_promo from public.m25_promotions where id = p_promotion_id and shop_id = p_shop_id;
    if not found then raise exception 'M25_PROMOTION_INVALID'; end if;
    update public.m25_promotions set
      code = v_code, promo_type = upper(trim(p_promo_type)), promo_value = p_promo_value,
      starts_at = p_starts_at, ends_at = p_ends_at, status = upper(trim(p_status))
    where id = p_promotion_id returning * into v_promo;
  else
    insert into public.m25_promotions(shop_id, code, promo_type, promo_value, starts_at, ends_at, status)
    values (p_shop_id, v_code, upper(trim(p_promo_type)), p_promo_value, p_starts_at, p_ends_at, upper(trim(p_status)))
    returning * into v_promo;
  end if;
  return jsonb_build_object(
    'id', v_promo.id, 'shop_id', v_promo.shop_id, 'code', v_promo.code,
    'promo_type', v_promo.promo_type, 'promo_value', v_promo.promo_value,
    'status', v_promo.status, 'starts_at', v_promo.starts_at, 'ends_at', v_promo.ends_at
  );
end;
$$;

create or replace function public.m25_update_cart_item(p_cart_item_id uuid, p_quantity integer)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_item public.m25_cart_items;
  v_product public.m25_products;
  v_available integer;
begin
  if p_quantity not between 1 and 99 then raise exception 'M25_INVALID_QUANTITY'; end if;
  select * into v_item from public.m25_cart_items where id = p_cart_item_id and user_id = v_actor for update;
  if not found then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
  select * into v_product from public.m25_products where id = v_item.product_id and status = 'ACTIVE';
  if not found then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
  v_available := v_product.stock_quantity - v_product.stock_reserved;
  if v_available < p_quantity then raise exception 'M25_OUT_OF_STOCK'; end if;
  update public.m25_cart_items set quantity = p_quantity, updated_at = timezone('utc', now())
  where id = p_cart_item_id returning * into v_item;
  return jsonb_build_object('id', v_item.id, 'user_id', v_item.user_id, 'product_id', v_item.product_id,
    'shop_id', v_item.shop_id, 'quantity', v_item.quantity, 'client_line_id', v_item.client_line_id, 'updated_at', v_item.updated_at);
end;
$$;

create or replace function public.m25_remove_cart_item(p_cart_item_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m25_require_authenticated(); v_deleted uuid;
begin
  delete from public.m25_cart_items where id = p_cart_item_id and user_id = v_actor returning id into v_deleted;
  if v_deleted is null then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
  return jsonb_build_object('removed', true);
end;
$$;

create or replace function public.m25_clear_cart()
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_actor uuid := public._m25_require_authenticated();
begin
  delete from public.m25_cart_items where user_id = v_actor;
  return jsonb_build_object('cleared', true);
end;
$$;

create or replace function public.m25_submit_order(
  p_shop_id uuid, p_shipping_mode text, p_shipping_city text,
  p_shipping_notes text default null, p_promotion_code text default null, p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_shop public.m25_shops;
  v_existing public.m25_orders;
  v_cart record;
  v_product public.m25_products;
  v_order public.m25_orders;
  v_subtotal bigint := 0;
  v_discount bigint := 0;
  v_promo jsonb;
  v_res_key text;
  v_line_subtotal bigint;
begin
  if char_length(trim(p_shipping_city)) not between 2 and 120 then raise exception 'M25_INVALID_SHIPPING'; end if;
  select * into v_shop from public.m25_shops where id = p_shop_id;
  if not found then raise exception 'M25_SHOP_NOT_FOUND'; end if;
  if v_shop.status <> 'ACTIVE' then raise exception 'M25_SHOP_NOT_PUBLIC'; end if;
  if nullif(trim(p_client_request_id), '') is not null then
    select * into v_existing from public.m25_orders
    where customer_user_id = v_actor and client_request_id = trim(p_client_request_id);
    if found then return public._m25_order_json(v_existing, true); end if;
  end if;
  if not exists (
    select 1 from public.m25_cart_items c where c.user_id = v_actor and c.shop_id = p_shop_id
  ) then raise exception 'M25_CART_EMPTY'; end if;
  for v_cart in
    select c.* from public.m25_cart_items c
    where c.user_id = v_actor and c.shop_id = p_shop_id
    order by c.updated_at
  loop
    select * into v_product from public.m25_products where id = v_cart.product_id and status = 'ACTIVE';
    if not found then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
    if (v_product.stock_quantity - v_product.stock_reserved) < v_cart.quantity then
      raise exception 'M25_OUT_OF_STOCK';
    end if;
    v_line_subtotal := v_product.list_price_cents * v_cart.quantity;
    v_subtotal := v_subtotal + v_line_subtotal;
  end loop;
  v_promo := public._m25_calculate_promotion(p_shop_id, p_promotion_code, v_subtotal);
  v_discount := coalesce((v_promo ->> 'discount_cents')::bigint, 0);
  insert into public.m25_orders(
    shop_id, customer_user_id, status, subtotal_cents, discount_cents, currency,
    shipping_mode, shipping_city, shipping_notes, promotion_code, client_request_id
  ) values (
    p_shop_id, v_actor, 'SUBMITTED', v_subtotal, v_discount, 'ARS',
    upper(trim(p_shipping_mode)), trim(p_shipping_city), nullif(trim(p_shipping_notes), ''),
    nullif(upper(trim(coalesce(p_promotion_code, v_promo ->> 'code'))), ''), nullif(trim(p_client_request_id), '')
  ) returning * into v_order;
  for v_cart in
    select c.* from public.m25_cart_items c
    where c.user_id = v_actor and c.shop_id = p_shop_id
    order by c.updated_at
  loop
    select * into v_product from public.m25_products where id = v_cart.product_id;
    v_res_key := coalesce(nullif(trim(p_client_request_id), ''), v_order.id::text) || ':' || v_cart.product_id::text;
    perform public._m25_reserve_stock(v_product.id, v_cart.quantity, v_res_key);
    v_line_subtotal := v_product.list_price_cents * v_cart.quantity;
    insert into public.m25_order_lines(
      order_id, product_id, product_name, quantity, unit_price_cents, currency, discount_cents, subtotal_cents
    ) values (
      v_order.id, v_product.id, v_product.name, v_cart.quantity, v_product.list_price_cents,
      v_product.currency, 0, v_line_subtotal
    );
  end loop;
  delete from public.m25_cart_items where user_id = v_actor and shop_id = p_shop_id;
  perform public._m25_append_order_history(v_order.id, v_actor, null, 'SUBMITTED', 'CUSTOMER');
  select * into v_order from public.m25_orders where id = v_order.id;
  return public._m25_order_json(v_order, true);
end;
$$;

create or replace function public.m25_list_shop_orders(p_shop_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_shop public.m25_shops;
  v_order public.m25_orders;
begin
  select * into v_shop from public.m25_shops where id = p_shop_id;
  if not found then raise exception 'M25_SHOP_NOT_FOUND'; end if;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  for v_order in
    select * from public.m25_orders where shop_id = p_shop_id and status <> 'DRAFT' order by created_at desc
  loop
    return next public._m25_order_json(v_order, true);
  end loop;
end;
$$;

create or replace function public.m25_get_order(p_order_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_order public.m25_orders;
  v_shop public.m25_shops;
begin
  select * into v_order from public.m25_orders where id = p_order_id;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if v_order.customer_user_id = v_actor then
    return public._m25_order_json(v_order, true);
  end if;
  if public._m25_can_manage_shop(v_shop, v_actor) then
    return public._m25_order_json(v_order, true);
  end if;
  raise exception 'M25_PERMISSION_DENIED';
end;
$$;

create or replace function public.m25_list_order_history(p_order_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_order public.m25_orders;
  v_shop public.m25_shops;
begin
  select * into v_order from public.m25_orders where id = p_order_id;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if v_order.customer_user_id <> v_actor and not public._m25_can_manage_shop(v_shop, v_actor) then
    raise exception 'M25_PERMISSION_DENIED';
  end if;
  return query
  select jsonb_build_object(
    'id', h.id, 'order_id', h.order_id, 'from_status', h.from_status, 'to_status', h.to_status,
    'public_reason', h.public_reason, 'actor_role', h.actor_role, 'created_at', h.created_at
  )
  from public.m25_order_history h
  where h.order_id = p_order_id
  order by h.created_at;
end;
$$;

create or replace function public.m25_transition_order(p_order_id uuid, p_status text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_order public.m25_orders;
  v_shop public.m25_shops;
  v_target text := upper(trim(p_status));
  v_updated public.m25_orders;
begin
  select * into v_order from public.m25_orders where id = p_order_id;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  v_updated := public._m25_transition_order(p_order_id, v_target, v_actor, 'MERCHANT');
  return public._m25_order_json(v_updated, true);
end;
$$;

create or replace function public.m25_reject_order(p_order_id uuid, p_public_reason text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_order public.m25_orders;
  v_shop public.m25_shops;
  v_updated public.m25_orders;
begin
  select * into v_order from public.m25_orders where id = p_order_id;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if v_order.status = 'REJECTED' then return public._m25_order_json(v_order, true); end if;
  v_updated := public._m25_transition_order(p_order_id, 'REJECTED', v_actor, 'MERCHANT', p_public_reason);
  return public._m25_order_json(v_updated, true);
end;
$$;

create or replace function public.m25_cancel_order_customer(p_order_id uuid, p_reason text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_order public.m25_orders;
  v_updated public.m25_orders;
begin
  select * into v_order from public.m25_orders where id = p_order_id and customer_user_id = v_actor;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  if v_order.status not in ('SUBMITTED','ACCEPTED','PREPARING') then raise exception 'M25_INVALID_ORDER_TRANSITION'; end if;
  v_updated := public._m25_transition_order(p_order_id, 'CANCELLED_BY_CUSTOMER', v_actor, 'CUSTOMER', p_reason);
  return public._m25_order_json(v_updated, true);
end;
$$;

create or replace function public.m25_cancel_order_merchant(p_order_id uuid, p_public_reason text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_order public.m25_orders;
  v_shop public.m25_shops;
  v_updated public.m25_orders;
begin
  select * into v_order from public.m25_orders where id = p_order_id;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if v_order.status not in ('SUBMITTED','ACCEPTED','PREPARING','READY_FOR_DISPATCH') then
    raise exception 'M25_INVALID_ORDER_TRANSITION';
  end if;
  v_updated := public._m25_transition_order(p_order_id, 'CANCELLED_BY_MERCHANT', v_actor, 'MERCHANT', p_public_reason);
  return public._m25_order_json(v_updated, true);
end;
$$;

create or replace function public.m25_ship_order(
  p_order_id uuid, p_tracking_code text default null, p_carrier_text text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_order public.m25_orders;
  v_shop public.m25_shops;
  v_previous text;
begin
  select * into v_order from public.m25_orders where id = p_order_id for update;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if v_order.status = 'SHIPPED' then return public._m25_order_json(v_order, true); end if;
  perform public._m25_validate_order_transition(v_order.status, 'SHIPPED');
  v_previous := v_order.status;
  update public.m25_orders set
    status = 'SHIPPED',
    tracking_code = nullif(trim(p_tracking_code), ''),
    carrier_text = nullif(trim(p_carrier_text), ''),
    dispatched_at = timezone('utc', now()),
    updated_at = timezone('utc', now())
  where id = p_order_id returning * into v_order;
  perform public._m25_append_order_history(p_order_id, v_actor, v_previous, 'SHIPPED', 'MERCHANT');
  return public._m25_order_json(v_order, true);
end;
$$;

create or replace function public.m25_request_return(
  p_order_id uuid, p_reason text, p_lines jsonb default '[]'::jsonb, p_client_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_order public.m25_orders;
  v_existing public.m25_returns;
  v_ret public.m25_returns;
  v_line jsonb;
  v_product_id uuid;
  v_qty integer;
  v_order_line public.m25_order_lines;
begin
  if char_length(trim(p_reason)) not between 10 and 500 then raise exception 'M25_INVALID_RETURN'; end if;
  if nullif(trim(p_client_request_id), '') is not null then
    select * into v_existing from public.m25_returns
    where customer_user_id = v_actor and client_request_id = trim(p_client_request_id);
    if found then return public._m25_return_json(v_existing); end if;
  end if;
  select * into v_order from public.m25_orders where id = p_order_id and customer_user_id = v_actor;
  if not found then raise exception 'M25_ORDER_NOT_FOUND'; end if;
  if v_order.status <> 'DELIVERED' then raise exception 'M25_INVALID_RETURN_TRANSITION'; end if;
  if jsonb_array_length(p_lines) = 0 then raise exception 'M25_INVALID_RETURN'; end if;
  for v_line in select * from jsonb_array_elements(p_lines) loop
    v_product_id := (v_line ->> 'product_id')::uuid;
    v_qty := (v_line ->> 'quantity')::integer;
    if v_qty is null or v_qty < 1 then raise exception 'M25_INVALID_RETURN'; end if;
    select * into v_order_line from public.m25_order_lines
    where order_id = p_order_id and product_id = v_product_id;
    if not found or v_qty > v_order_line.quantity then raise exception 'M25_INVALID_RETURN'; end if;
  end loop;
  insert into public.m25_returns(order_id, customer_user_id, reason, status, client_request_id)
  values (p_order_id, v_actor, trim(p_reason), 'REQUESTED', nullif(trim(p_client_request_id), ''))
  returning * into v_ret;
  for v_line in select * from jsonb_array_elements(p_lines) loop
    insert into public.m25_return_lines(return_id, product_id, quantity)
    values (v_ret.id, (v_line ->> 'product_id')::uuid, (v_line ->> 'quantity')::integer);
  end loop;
  perform public._m25_transition_order(p_order_id, 'RETURN_REQUESTED', v_actor, 'CUSTOMER', p_reason);
  select * into v_ret from public.m25_returns where id = v_ret.id;
  return public._m25_return_json(v_ret);
end;
$$;

create or replace function public.m25_approve_return(p_return_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_ret public.m25_returns;
  v_order public.m25_orders;
  v_shop public.m25_shops;
begin
  select * into v_ret from public.m25_returns where id = p_return_id for update;
  if not found then raise exception 'M25_INVALID_RETURN'; end if;
  select * into v_order from public.m25_orders where id = v_ret.order_id;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if v_ret.status = 'APPROVED' then return public._m25_return_json(v_ret); end if;
  perform public._m25_validate_return_transition(v_ret.status, 'APPROVED');
  update public.m25_returns set status = 'APPROVED', updated_at = timezone('utc', now())
  where id = p_return_id returning * into v_ret;
  return public._m25_return_json(v_ret);
end;
$$;

create or replace function public.m25_reject_return(p_return_id uuid, p_public_reason text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_ret public.m25_returns;
  v_order public.m25_orders;
  v_shop public.m25_shops;
begin
  select * into v_ret from public.m25_returns where id = p_return_id for update;
  if not found then raise exception 'M25_INVALID_RETURN'; end if;
  select * into v_order from public.m25_orders where id = v_ret.order_id;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if v_ret.status = 'REJECTED' then return public._m25_return_json(v_ret); end if;
  perform public._m25_validate_return_transition(v_ret.status, 'REJECTED');
  update public.m25_returns set status = 'REJECTED', updated_at = timezone('utc', now())
  where id = p_return_id returning * into v_ret;
  return public._m25_return_json(v_ret);
end;
$$;

create or replace function public.m25_receive_return(p_return_id uuid, p_replenish_stock boolean default false)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_ret public.m25_returns;
  v_order public.m25_orders;
  v_shop public.m25_shops;
  v_line record;
begin
  select * into v_ret from public.m25_returns where id = p_return_id for update;
  if not found then raise exception 'M25_INVALID_RETURN'; end if;
  select * into v_order from public.m25_orders where id = v_ret.order_id;
  select * into v_shop from public.m25_shops where id = v_order.shop_id;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if v_ret.status = 'RECEIVED' then return public._m25_return_json(v_ret); end if;
  perform public._m25_validate_return_transition(v_ret.status, 'RECEIVED');
  if p_replenish_stock then
    for v_line in select * from public.m25_return_lines where return_id = p_return_id loop
      update public.m25_products
      set stock_quantity = stock_quantity + v_line.quantity, updated_at = timezone('utc', now())
      where id = v_line.product_id;
      insert into public.m25_stock_movements(product_id, movement_type, quantity, reason)
      values (v_line.product_id, 'REPLENISH', v_line.quantity, 'RETURN');
    end loop;
  end if;
  update public.m25_returns set status = 'RECEIVED', updated_at = timezone('utc', now())
  where id = p_return_id returning * into v_ret;
  perform public._m25_transition_order(v_ret.order_id, 'RETURNED', v_actor, 'MERCHANT');
  return public._m25_return_json(v_ret);
end;
$$;

create or replace function public.m25_adjust_stock(p_product_id uuid, p_new_total integer, p_reason text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_product public.m25_products;
  v_shop public.m25_shops;
  v_delta integer;
begin
  if char_length(trim(p_reason)) not between 5 and 200 then raise exception 'M25_INVALID_STOCK'; end if;
  select * into v_product from public.m25_products where id = p_product_id for update;
  if not found then raise exception 'M25_PRODUCT_NOT_FOUND'; end if;
  select * into v_shop from public.m25_shops where id = v_product.shop_id;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  if p_new_total < v_product.stock_reserved then raise exception 'M25_INVALID_STOCK'; end if;
  v_delta := p_new_total - v_product.stock_quantity;
  update public.m25_products set stock_quantity = p_new_total, updated_at = timezone('utc', now())
  where id = p_product_id returning * into v_product;
  if v_delta <> 0 then
    insert into public.m25_stock_movements(product_id, movement_type, quantity, reason)
    values (p_product_id, 'ADJUST', abs(v_delta), trim(p_reason));
  end if;
  return jsonb_build_object(
    'id', v_product.id, 'shop_id', v_product.shop_id, 'sku', v_product.sku, 'name', v_product.name,
    'description', v_product.description, 'list_price_cents', v_product.list_price_cents,
    'currency', v_product.currency, 'stock_quantity', v_product.stock_quantity,
    'stock_reserved', v_product.stock_reserved, 'status', v_product.status
  );
end;
$$;

create or replace function public.m25_merchant_metrics(p_shop_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_shop public.m25_shops;
begin
  select * into v_shop from public.m25_shops where id = p_shop_id;
  if not found then raise exception 'M25_SHOP_NOT_FOUND'; end if;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  return jsonb_build_object(
    'created', (select count(*) from public.m25_orders o where o.shop_id = p_shop_id and o.status = 'SUBMITTED'),
    'accepted', (select count(*) from public.m25_orders o where o.shop_id = p_shop_id and o.status = 'ACCEPTED'),
    'preparing', (select count(*) from public.m25_orders o where o.shop_id = p_shop_id and o.status = 'PREPARING'),
    'dispatched', (select count(*) from public.m25_orders o where o.shop_id = p_shop_id and o.status = 'SHIPPED'),
    'delivered', (select count(*) from public.m25_orders o where o.shop_id = p_shop_id and o.status = 'DELIVERED'),
    'cancelled', (select count(*) from public.m25_orders o where o.shop_id = p_shop_id and o.status in (
      'CANCELLED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_MERCHANT','REJECTED'
    )),
    'returns', (select count(*) from public.m25_returns r
      join public.m25_orders o on o.id = r.order_id where o.shop_id = p_shop_id),
    'units_sold', (select coalesce(sum(ol.quantity), 0) from public.m25_order_lines ol
      join public.m25_orders o on o.id = ol.order_id
      where o.shop_id = p_shop_id and o.status = 'DELIVERED'),
    'low_stock_products', (select count(*) from public.m25_products pr
      where pr.shop_id = p_shop_id and pr.status = 'ACTIVE' and (pr.stock_quantity - pr.stock_reserved) <= 3)
  );
end;
$$;

create or replace function public.m25_list_shop_products(p_shop_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_shop public.m25_shops;
  v_product public.m25_products;
begin
  select * into v_shop from public.m25_shops where id = p_shop_id;
  if not found then raise exception 'M25_SHOP_NOT_FOUND'; end if;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  for v_product in select * from public.m25_products where shop_id = p_shop_id order by name loop
    return next jsonb_build_object(
      'id', v_product.id, 'shop_id', v_product.shop_id, 'sku', v_product.sku, 'name', v_product.name,
      'description', v_product.description, 'list_price_cents', v_product.list_price_cents,
      'currency', v_product.currency, 'stock_quantity', v_product.stock_quantity,
      'stock_reserved', v_product.stock_reserved, 'status', v_product.status
    );
  end loop;
end;
$$;

create or replace function public.m25_list_shop_promotions(p_shop_id uuid)
returns setof jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_actor uuid := public._m25_require_authenticated();
  v_shop public.m25_shops;
  v_promo public.m25_promotions;
begin
  select * into v_shop from public.m25_shops where id = p_shop_id;
  if not found then raise exception 'M25_SHOP_NOT_FOUND'; end if;
  if not public._m25_can_manage_shop(v_shop, v_actor) then raise exception 'M25_PERMISSION_DENIED'; end if;
  for v_promo in select * from public.m25_promotions where shop_id = p_shop_id order by code loop
    return next jsonb_build_object(
      'id', v_promo.id, 'shop_id', v_promo.shop_id, 'code', v_promo.code,
      'promo_type', v_promo.promo_type, 'promo_value', v_promo.promo_value,
      'status', v_promo.status, 'starts_at', v_promo.starts_at, 'ends_at', v_promo.ends_at
    );
  end loop;
end;
$$;

-- Actualizar proyecciones públicas de catálogo (stock disponible, sin datos privados)
create or replace function public._m25_public_shop_json(p public.m25_shops)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'display_name', p.display_name, 'category', p.category, 'description', p.description, 'city', p.city,
    'product_count', (
      select count(*) from public.m25_products pr
      where pr.shop_id = p.id and pr.status = 'ACTIVE' and (pr.stock_quantity - pr.stock_reserved) > 0
    ),
    'price_summary', (
      select case when min(pr.list_price_cents) is null then null
        else 'ARS ' || min(pr.list_price_cents)::text end
      from public.m25_products pr
      where pr.shop_id = p.id and pr.status = 'ACTIVE' and (pr.stock_quantity - pr.stock_reserved) > 0
    )
  );
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
        'in_stock', (pr.stock_quantity - pr.stock_reserved) > 0
      ) order by pr.name)
      from public.m25_products pr where pr.shop_id = s.id and pr.status = 'ACTIVE'
    ), '[]'::jsonb)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 11–12. Grants / revokes
-- ---------------------------------------------------------------------------

revoke all on function public._m25_append_only_guard() from public, anon, authenticated;
revoke all on function public._m25_reserve_stock(uuid, integer, text),
  public._m25_release_stock(uuid, integer, text),
  public._m25_commit_stock(uuid, integer, text),
  public._m25_calculate_promotion(uuid, text, bigint),
  public._m25_validate_order_transition(text, text),
  public._m25_validate_return_transition(text, text),
  public._m25_validate_shop_transition(text, text, boolean),
  public._m25_append_order_history(uuid, uuid, text, text, text, text),
  public._m25_release_order_lines(uuid, text),
  public._m25_commit_order_lines(uuid, text),
  public._m25_order_line_json(public.m25_order_lines),
  public._m25_order_json(public.m25_orders, boolean),
  public._m25_return_json(public.m25_returns),
  public._m25_transition_order(uuid, text, uuid, text, text)
  from public, anon, authenticated;

grant execute on function public.m25_update_shop(uuid, text, text, text, text),
  public.m25_transition_shop(uuid, text),
  public.m25_upsert_product(uuid, text, text, text, bigint, integer, uuid, text, text),
  public.m25_upsert_promotion(uuid, text, text, bigint, timestamptz, timestamptz, uuid, text),
  public.m25_update_cart_item(uuid, integer),
  public.m25_remove_cart_item(uuid),
  public.m25_clear_cart(),
  public.m25_submit_order(uuid, text, text, text, text, text),
  public.m25_list_shop_orders(uuid),
  public.m25_get_order(uuid),
  public.m25_list_order_history(uuid),
  public.m25_transition_order(uuid, text),
  public.m25_reject_order(uuid, text),
  public.m25_cancel_order_customer(uuid, text),
  public.m25_cancel_order_merchant(uuid, text),
  public.m25_ship_order(uuid, text, text),
  public.m25_request_return(uuid, text, jsonb, text),
  public.m25_approve_return(uuid),
  public.m25_reject_return(uuid, text),
  public.m25_receive_return(uuid, boolean),
  public.m25_adjust_stock(uuid, integer, text),
  public.m25_merchant_metrics(uuid),
  public.m25_list_shop_products(uuid),
  public.m25_list_shop_promotions(uuid)
  to authenticated;

commit;
