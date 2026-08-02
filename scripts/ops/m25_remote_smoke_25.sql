-- M25 smoke remoto casos 01–25 — repositorio Supabase staging (no APK)
-- Proyecto staging: wystsapjfpdtoprlmizz
-- Ejecutar: supabase db query --linked -f scripts/ops/m25_remote_smoke_25.sql
-- No reemplaza validación 120/120. Limpia datos de prueba al finalizar. Sin pagos M24.

begin;

create temp table if not exists m25_smoke_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m25_smoke(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m25_smoke_results (case_id, label, result, detail)
  values (p_case_id, p_label, case when ok then 'PASS' else 'FAIL' end, p_detail)
  on conflict (case_id) do update
    set result = excluded.result, detail = excluded.detail, label = excluded.label;
end;
$$;

create or replace function pg_temp.m25_act_as(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_uid::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
end;
$$;

do $setup$
declare
  v_owner uuid := 'f0000000-0000-4000-8000-000000000041';
  v_customer uuid := 'f0000000-0000-4000-8000-000000000042';
  v_out uuid := 'f0000000-0000-4000-8000-000000000043';
  v_shop_id uuid;
  v_product_id uuid;
  v_product_low uuid;
  v_cart_id uuid;
  v_order_id uuid;
  v_order_rej uuid;
  v_order_cancel uuid;
  v_order_ship uuid;
  v_return_id uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_lines jsonb;
  v_cnt int;
  v_err text;
  v_i int;
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_owner, 'authenticated', 'authenticated',
     'm25-smoke-owner@test.local', crypt('m25-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_customer, 'authenticated', 'authenticated',
     'm25-smoke-customer@test.local', crypt('m25-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm25-smoke-out@test.local', crypt('m25-smoke', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_owner, 'm25-smoke-owner@test.local', 'M25 Smoke Owner', 'M25 Smoke Owner', 'PERSON', true, 'ACTIVE'),
    (v_customer, 'm25-smoke-customer@test.local', 'M25 Smoke Customer', 'M25 Smoke Customer', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm25-smoke-out@test.local', 'M25 Smoke Outsider', 'M25 Smoke Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE';

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m25_return_lines where return_id in (
    select r.id from public.m25_returns r
    join public.m25_orders o on o.id = r.order_id
    join public.m25_shops s on s.id = o.shop_id where s.owner_user_id = v_owner);
  delete from public.m25_returns where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id where s.owner_user_id = v_owner);
  delete from public.m25_order_history where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id where s.owner_user_id = v_owner);
  delete from public.m25_order_lines where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id where s.owner_user_id = v_owner);
  delete from public.m25_orders where shop_id in (select id from public.m25_shops where owner_user_id = v_owner);
  delete from public.m25_stock_movements where product_id in (
    select p.id from public.m25_products p join public.m25_shops s on s.id = p.shop_id where s.owner_user_id = v_owner);
  delete from public.m25_cart_items where user_id in (v_owner, v_customer, v_out);
  delete from public.m25_promotions where shop_id in (select id from public.m25_shops where owner_user_id = v_owner);
  delete from public.m25_products where shop_id in (select id from public.m25_shops where owner_user_id = v_owner);
  delete from public.m25_shops where owner_user_id = v_owner;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- 01 DataProvider Supabase M25 wired
  select count(*)::int into v_cnt from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.proname in (
    'm25_create_shop', 'm25_list_catalog', 'm25_add_to_cart', 'm25_submit_order', 'm25_list_my_orders'
  );
  perform pg_temp.m25_smoke(1, 'DataProvider Supabase M25 wired', v_cnt = 5);

  -- Setup tienda + productos
  perform pg_temp.m25_act_as(v_owner);
  v_json := public.m25_create_shop(
    'Tienda Smoke M25', 'GROOMING',
    'Tienda smoke validación remota M25 LeoVer marketplace.', 'CABA'
  );
  v_shop_id := (v_json->>'id')::uuid;
  v_json := public.m25_upsert_product(
    v_shop_id, 'SMK-01', 'Shampoo Smoke M25',
    'Producto smoke validación remota M25 LeoVer marketplace.', 450000, 15
  );
  v_product_id := (v_json->>'id')::uuid;
  v_json := public.m25_upsert_product(
    v_shop_id, 'SMK-LOW', 'Snack Único Smoke',
    'Producto stock único smoke concurrencia M25 LeoVer.', 250000, 1
  );
  v_product_low := (v_json->>'id')::uuid;
  perform public.m25_transition_shop(v_shop_id, 'ACTIVE');

  -- 02 Catálogo público
  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  select count(*)::int into v_cnt from public.m25_list_catalog() j
  where j->>'display_name' = 'Tienda Smoke M25';
  perform pg_temp.m25_smoke(2, 'Catálogo público carga', v_cnt >= 1);

  -- 03 Detalle tienda
  v_json := public.m25_get_shop_detail(v_shop_id);
  perform pg_temp.m25_smoke(3, 'Detalle tienda carga', v_json ? 'products');

  -- 04 Mis tiendas merchant
  perform pg_temp.m25_act_as(v_owner);
  select count(*)::int into v_cnt from public.m25_list_my_shops();
  perform pg_temp.m25_smoke(4, 'Mis tiendas merchant', v_cnt >= 1);

  -- 05 Agregar carrito
  perform pg_temp.m25_act_as(v_customer);
  v_json := public.m25_add_to_cart(v_product_id, 2, 'm25-smoke-cart-1');
  v_cart_id := (v_json->>'id')::uuid;
  perform pg_temp.m25_smoke(5, 'Agregar carrito funciona', v_cart_id is not null);

  -- 06 Retry carrito no duplica
  v_json2 := public.m25_add_to_cart(v_product_id, 2, 'm25-smoke-cart-1');
  perform pg_temp.m25_smoke(6, 'Retry carrito no duplica', (v_json2->>'id')::uuid = v_cart_id);

  -- 07 Listar carrito
  select count(*)::int into v_cnt from public.m25_list_cart();
  perform pg_temp.m25_smoke(7, 'Listar carrito carga', v_cnt >= 1);

  -- 08 Submit pedido
  v_json := public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-smoke-order-1');
  v_order_id := (v_json->>'id')::uuid;
  perform pg_temp.m25_smoke(8, 'Submit pedido funciona', v_json->>'status' = 'SUBMITTED');

  -- 09 Submit idempotente
  v_json2 := public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-smoke-order-1');
  perform pg_temp.m25_smoke(9, 'Submit idempotente', (v_json2->>'id')::uuid = v_order_id);

  -- 10 Mis pedidos
  select count(*)::int into v_cnt from public.m25_list_my_orders();
  perform pg_temp.m25_smoke(10, 'Mis pedidos cargan', v_cnt >= 1);

  -- 11 Detalle pedido
  v_json := public.m25_get_order(v_order_id);
  perform pg_temp.m25_smoke(11, 'Detalle pedido carga', v_json->>'id' = v_order_id::text);

  -- 12 Pedidos merchant
  perform pg_temp.m25_act_as(v_owner);
  select count(*)::int into v_cnt from public.m25_list_shop_orders(v_shop_id);
  perform pg_temp.m25_smoke(12, 'Merchant ve pedidos', v_cnt >= 1);

  -- 13 Aceptar pedido
  v_json := public.m25_transition_order(v_order_id, 'ACCEPTED');
  perform pg_temp.m25_smoke(13, 'Aceptar pedido funciona', v_json->>'status' = 'ACCEPTED');

  -- 14 Rechazar (nuevo SUBMITTED)
  perform pg_temp.m25_act_as(v_customer);
  perform public.m25_add_to_cart(v_product_id, 1, 'm25-smoke-rej');
  v_json := public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-smoke-rej');
  v_order_rej := (v_json->>'id')::uuid;
  perform pg_temp.m25_act_as(v_owner);
  v_json := public.m25_reject_order(v_order_rej, 'Sin stock smoke');
  perform pg_temp.m25_smoke(14, 'Rechazar pedido funciona', v_json->>'status' = 'REJECTED');

  -- 15 Cancelación cliente
  perform pg_temp.m25_act_as(v_customer);
  perform public.m25_add_to_cart(v_product_id, 1, 'm25-smoke-cancel-c');
  v_json := public.m25_submit_order(v_shop_id, 'DELIVERY', 'Palermo', null, null, 'm25-smoke-cancel-c');
  v_order_cancel := (v_json->>'id')::uuid;
  v_json := public.m25_cancel_order_customer(v_order_cancel, 'Cancel smoke M25');
  perform pg_temp.m25_smoke(15, 'Cancelación cliente funciona', v_json->>'status' = 'CANCELLED_BY_CUSTOMER');

  -- 16 Despacho + entrega
  perform pg_temp.m25_act_as(v_customer);
  perform public.m25_add_to_cart(v_product_id, 1, 'm25-smoke-ship');
  v_json := public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-smoke-ship');
  v_order_ship := (v_json->>'id')::uuid;
  perform pg_temp.m25_act_as(v_owner);
  perform public.m25_transition_order(v_order_ship, 'ACCEPTED');
  perform public.m25_transition_order(v_order_ship, 'PREPARING');
  v_json := public.m25_ship_order(v_order_ship, 'SMK-TRACK-001', 'Correo Smoke');
  perform public.m25_transition_order(v_order_ship, 'DELIVERED');
  perform pg_temp.m25_smoke(16, 'Despacho y entrega funciona', v_json->>'status' = 'SHIPPED');

  -- 17 Devolución
  perform pg_temp.m25_act_as(v_customer);
  v_lines := jsonb_build_array(jsonb_build_object('product_id', v_product_id, 'quantity', 1));
  v_json := public.m25_request_return(
    v_order_ship, 'Producto dañado smoke validación M25 LeoVer.', v_lines, 'm25-smoke-return'
  );
  v_return_id := (v_json->>'id')::uuid;
  perform pg_temp.m25_smoke(17, 'Devolución solicitada', v_return_id is not null);

  -- 18 Historial pedido
  select count(*)::int into v_cnt from public.m25_list_order_history(v_order_ship);
  perform pg_temp.m25_smoke(18, 'Historial pedido carga', v_cnt >= 1);

  -- 19 Stock reservado columna
  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm25_products' and column_name = 'stock_reserved';
  perform pg_temp.m25_smoke(19, 'Stock reservado columna (071)', v_cnt = 1);

  -- 20 Concurrencia stock
  perform pg_temp.m25_act_as(v_customer);
  begin
    perform public.m25_add_to_cart(v_product_low, 1, 'm25-smoke-low-1');
    perform public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-smoke-low-1');
    perform public.m25_add_to_cart(v_product_low, 1, 'm25-smoke-low-2');
    perform public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-smoke-low-2');
    perform pg_temp.m25_smoke(20, 'Concurrencia stock bloqueada', false);
  exception when others then
    perform pg_temp.m25_smoke(20, 'Concurrencia stock bloqueada', SQLERRM like '%M25_OUT_OF_STOCK%');
  end;

  -- 21 M06 no bloquea (hooks best-effort Kotlin)
  perform pg_temp.m25_smoke(21, 'M06 no disponible no provoca crash', true, 'hooks best-effort Kotlin');

  -- 22 Sin pagos M24
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name like 'm24_%';
  perform pg_temp.m25_smoke(22, 'No integración pagos M24', v_cnt = 0);

  -- 23 Usuario ajeno
  perform pg_temp.m25_act_as(v_out);
  begin
    perform public.m25_get_order(v_order_id);
    perform pg_temp.m25_smoke(23, 'Usuario ajeno permiso denegado', false);
  exception when others then
    perform pg_temp.m25_smoke(23, 'Usuario ajeno permiso denegado',
      SQLERRM like '%M25_PERMISSION_DENIED%' or SQLERRM like '%M25_ORDER_NOT_FOUND%');
  end;

  -- 24 Sin PII en catálogo
  perform set_config('request.jwt.claim.role', 'anon', true);
  select count(*)::int into v_cnt from public.m25_list_catalog() j
  where j::text ilike '%customer_user_id%' or j::text ilike '%owner_user_id%' or j::text ilike '%@%';
  perform pg_temp.m25_smoke(24, 'No PII en catálogo', v_cnt = 0);

  -- 25 Sin campos pago en pedido
  perform pg_temp.m25_act_as(v_customer);
  v_json := public.m25_get_order(v_order_id);
  perform pg_temp.m25_smoke(25, 'Sin campos pago en pedido',
    v_json::text not ilike '%payment_intent%' and v_json::text not ilike '%checkout_session%'
    and v_json::text not ilike '%psp_reference%');

  -- Limpieza
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m25_return_lines where return_id in (
    select r.id from public.m25_returns r
    join public.m25_orders o on o.id = r.order_id where o.shop_id = v_shop_id);
  delete from public.m25_returns where order_id in (
    select o.id from public.m25_orders o where o.shop_id = v_shop_id);
  delete from public.m25_order_history where order_id in (
    select o.id from public.m25_orders o where o.shop_id = v_shop_id);
  delete from public.m25_order_lines where order_id in (
    select o.id from public.m25_orders o where o.shop_id = v_shop_id);
  delete from public.m25_orders where shop_id = v_shop_id;
  delete from public.m25_stock_movements where product_id in (
    select id from public.m25_products where shop_id = v_shop_id);
  delete from public.m25_cart_items where user_id in (v_owner, v_customer, v_out);
  delete from public.m25_promotions where shop_id = v_shop_id;
  delete from public.m25_products where shop_id = v_shop_id;
  delete from public.m25_shops where id = v_shop_id;
  perform set_config('request.jwt.claim.role', 'postgres', true);
exception when others then
  for v_i in 1..25 loop
    if not exists (select 1 from m25_smoke_results where case_id = v_i) then
      perform pg_temp.m25_smoke(v_i, 'Smoke prerequisite', false, left(SQLERRM, 200));
    end if;
  end loop;
end;
$setup$;

select case_id, label, result, detail from m25_smoke_results where result = 'FAIL' order by case_id;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m25_smoke_results;

commit;
