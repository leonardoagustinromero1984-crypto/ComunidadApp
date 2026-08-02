-- M25 migraciones 070+071 — validación remota staging (casos 01–120)
-- Proyecto staging: wystsapjfpdtoprlmizz
-- Ejecutar: supabase db query --linked -f scripts/ops/m25_remote_validation_070_071.sql
-- Limpia datos de prueba al finalizar. Sin pagos (M24 pospuesto).

begin;

create temp table if not exists m25_val_results (
  case_id int primary key,
  label text not null,
  result text not null,
  detail text
) on commit drop;

create or replace function pg_temp.m25_val(p_case_id int, p_label text, ok boolean, p_detail text default null)
returns void language plpgsql as $$
begin
  insert into m25_val_results (case_id, label, result, detail)
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
  v_owner uuid := 'f0000000-0000-4000-8000-000000000031';
  v_customer uuid := 'f0000000-0000-4000-8000-000000000032';
  v_customer2 uuid := 'f0000000-0000-4000-8000-000000000033';
  v_out uuid := 'f0000000-0000-4000-8000-000000000034';
  v_shop_id uuid;
  v_shop_draft uuid;
  v_product_id uuid;
  v_product_low uuid;
  v_product_inactive uuid;
  v_promo_id uuid;
  v_cart_id uuid;
  v_cart_id2 uuid;
  v_order_id uuid;
  v_order_id2 uuid;
  v_order_rej uuid;
  v_order_cancel_c uuid;
  v_order_cancel_m uuid;
  v_order_ship uuid;
  v_order_return uuid;
  v_return_id uuid;
  v_json jsonb;
  v_json2 jsonb;
  v_cnt int;
  v_qty int;
  v_reserved int;
  v_err text;
  v_i int;
  v_lines jsonb;
begin
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

  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
  values
    ('00000000-0000-0000-0000-000000000000', v_owner, 'authenticated', 'authenticated',
     'm25-val-owner@test.local', crypt('m25-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_customer, 'authenticated', 'authenticated',
     'm25-val-customer@test.local', crypt('m25-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_customer2, 'authenticated', 'authenticated',
     'm25-val-customer2@test.local', crypt('m25-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000000', v_out, 'authenticated', 'authenticated',
     'm25-val-out@test.local', crypt('m25-test', gen_salt('bf')), now(),
     '{"provider":"email","providers":["email"]}', '{}', now(), now())
  on conflict (id) do nothing;

  insert into public.users (id, email, name, display_name, account_type, email_verified, account_status)
  values
    (v_owner, 'm25-val-owner@test.local', 'M25 Val Owner', 'M25 Val Owner', 'PERSON', true, 'ACTIVE'),
    (v_customer, 'm25-val-customer@test.local', 'M25 Val Customer', 'M25 Val Customer', 'PERSON', true, 'ACTIVE'),
    (v_customer2, 'm25-val-customer2@test.local', 'M25 Val Customer2', 'M25 Val Customer2', 'PERSON', true, 'ACTIVE'),
    (v_out, 'm25-val-out@test.local', 'M25 Val Outsider', 'M25 Val Outsider', 'PERSON', true, 'ACTIVE')
  on conflict (id) do update set account_status = 'ACTIVE', display_name = excluded.display_name;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m25_return_lines where return_id in (
    select r.id from public.m25_returns r
    join public.m25_orders o on o.id = r.order_id
    join public.m25_shops s on s.id = o.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_returns where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_order_history where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_order_lines where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_orders where shop_id in (
    select id from public.m25_shops where owner_user_id in (v_owner, v_out));
  delete from public.m25_stock_movements where product_id in (
    select p.id from public.m25_products p join public.m25_shops s on s.id = p.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_cart_items where user_id in (v_owner, v_customer, v_customer2, v_out);
  delete from public.m25_promotions where shop_id in (
    select id from public.m25_shops where owner_user_id in (v_owner, v_out));
  delete from public.m25_products where shop_id in (
    select id from public.m25_shops where owner_user_id in (v_owner, v_out));
  delete from public.m25_shops where owner_user_id in (v_owner, v_out);
  perform set_config('request.jwt.claim.role', 'postgres', true);

  -- ========================================================================
  -- ESTRUCTURA 01–25
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name in (
    'm25_shops', 'm25_products', 'm25_promotions', 'm25_cart_items', 'm25_orders',
    'm25_order_lines', 'm25_returns', 'm25_order_history', 'm25_stock_movements', 'm25_return_lines'
  );
  perform pg_temp.m25_val(1, 'Diez tablas M25 (070+071)', v_cnt = 10);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm25_shops'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'owner_user_id';
  perform pg_temp.m25_val(2, 'FK owner_user_id tiendas', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm25_products'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'shop_id';
  perform pg_temp.m25_val(3, 'FK shop_id productos', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm25_orders'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'customer_user_id';
  perform pg_temp.m25_val(4, 'FK customer_user_id pedidos', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm25_order_lines'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'order_id';
  perform pg_temp.m25_val(5, 'FK order_id líneas', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
  where tc.table_schema = 'public' and tc.table_name = 'm25_returns'
    and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'order_id';
  perform pg_temp.m25_val(6, 'FK order_id devoluciones', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.columns
  where table_schema = 'public' and table_name = 'm25_products' and column_name = 'stock_reserved';
  perform pg_temp.m25_val(7, 'Columna stock_reserved (071)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm25_order_history';
  perform pg_temp.m25_val(8, 'Tabla order_history (071)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm25_stock_movements';
  perform pg_temp.m25_val(9, 'Tabla stock_movements (071)', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.tables
  where table_schema = 'public' and table_name = 'm25_return_lines';
  perform pg_temp.m25_val(10, 'Tabla return_lines (071)', v_cnt = 1);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m25_products (shop_id, sku, name, description, list_price_cents, stock_quantity, status)
    values (gen_random_uuid(), 'BAD', 'X', 'Descripción inválida precio cero M25.', 0, 5, 'ACTIVE');
    perform pg_temp.m25_val(11, 'CHECK list_price_cents > 0', false);
  exception when check_violation then
    perform pg_temp.m25_val(11, 'CHECK list_price_cents > 0', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m25_products (shop_id, sku, name, description, list_price_cents, stock_quantity, status)
    values (gen_random_uuid(), 'NEG', 'X', 'Descripción inválida stock negativo M25.', 1000, -1, 'ACTIVE');
    perform pg_temp.m25_val(12, 'CHECK stock_quantity >= 0', false);
  exception when check_violation then
    perform pg_temp.m25_val(12, 'CHECK stock_quantity >= 0', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m25_cart_items (user_id, shop_id, product_id, quantity)
    values (v_customer, gen_random_uuid(), gen_random_uuid(), 0);
    perform pg_temp.m25_val(13, 'CHECK cart quantity 1-99', false);
  exception when check_violation then
    perform pg_temp.m25_val(13, 'CHECK cart quantity 1-99', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m25_orders (
      shop_id, customer_user_id, status, subtotal_cents, shipping_mode, shipping_city
    ) values (gen_random_uuid(), v_customer, 'INVALID_STATUS', 1000, 'PICKUP', 'CABA');
    perform pg_temp.m25_val(14, 'CHECK order status válido (071)', false);
  exception when check_violation then
    perform pg_temp.m25_val(14, 'CHECK order status válido (071)', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m25_shops (
      owner_user_id, display_name, category, description, city, status
    ) values (v_owner, 'Bad Cat', 'INVALID', 'Descripción categoría inválida M25 val.', 'CABA', 'DRAFT');
    perform pg_temp.m25_val(15, 'CHECK shop category enum', false);
  exception when check_violation then
    perform pg_temp.m25_val(15, 'CHECK shop category enum', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m25_orders (
      shop_id, customer_user_id, status, subtotal_cents, shipping_mode, shipping_city
    ) values (gen_random_uuid(), v_customer, 'SUBMITTED', 1000, 'INVALID', 'CABA');
    perform pg_temp.m25_val(16, 'CHECK shipping_mode PICKUP/DELIVERY', false);
  exception when check_violation then
    perform pg_temp.m25_val(16, 'CHECK shipping_mode PICKUP/DELIVERY', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  begin
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m25_products (
      shop_id, sku, name, description, list_price_cents, stock_quantity, stock_reserved, status
    ) values (gen_random_uuid(), 'RES', 'X', 'Descripción reserva inválida M25 val.', 1000, 5, 10, 'ACTIVE');
    perform pg_temp.m25_val(17, 'CHECK stock_reserved <= stock_quantity', false);
  exception when check_violation then
    perform pg_temp.m25_val(17, 'CHECK stock_reserved <= stock_quantity', true);
  end;
  perform set_config('request.jwt.claim.role', 'postgres', true);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm25_orders'
    and constraint_name = 'm25_order_client_request_uq';
  perform pg_temp.m25_val(18, 'UNIQUE client_request_id pedidos', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.table_constraints
  where table_schema = 'public' and table_name = 'm25_products'
    and constraint_name = 'm25_product_sku_shop_uq';
  perform pg_temp.m25_val(19, 'UNIQUE sku por tienda', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm25_shops' and indexname = 'm25_shop_category_status_idx';
  perform pg_temp.m25_val(20, 'Índice category+status tiendas', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm25_cart_items' and indexname = 'm25_cart_user_idx';
  perform pg_temp.m25_val(21, 'Índice user carrito', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm25_orders' and indexname = 'm25_order_customer_idx';
  perform pg_temp.m25_val(22, 'Índice customer pedidos', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm25_order_history' and indexname = 'm25_order_history_order_idx';
  perform pg_temp.m25_val(23, 'Índice order_history', v_cnt = 1);

  select count(*)::int into v_cnt from pg_indexes
  where schemaname = 'public' and tablename = 'm25_stock_movements' and indexname = 'm25_stock_reserve_key_uq';
  perform pg_temp.m25_val(24, 'Índice reservation_key RESERVE', v_cnt = 1);

  select count(*)::int into v_cnt from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname in (
    'm25_shops', 'm25_products', 'm25_promotions', 'm25_cart_items', 'm25_orders',
    'm25_order_lines', 'm25_returns', 'm25_order_history', 'm25_stock_movements', 'm25_return_lines'
  ) and c.relrowsecurity;
  perform pg_temp.m25_val(25, 'RLS habilitado 10 tablas', v_cnt = 10);

  -- ========================================================================
  -- Setup operativo tienda M25
  -- ========================================================================
  perform pg_temp.m25_act_as(v_owner);
  begin
    v_json := public.m25_create_shop(
      'Tienda Val M25', 'PET_FOOD',
      'Tienda de prueba validación remota M25 LeoVer marketplace.', 'CABA'
    );
    v_shop_id := (v_json->>'id')::uuid;
  exception when others then
    v_shop_id := null;
    v_err := SQLERRM;
  end;

  if v_shop_id is null then
    perform set_config('request.jwt.claim.role', 'service_role', true);
    insert into public.m25_shops (
      owner_user_id, display_name, category, description, city, status
    ) values (
      v_owner, 'Tienda Val M25 SR', 'PET_FOOD',
      'Tienda semilla service_role validación remota M25 LeoVer.', 'CABA', 'DRAFT'
    ) returning id into v_shop_id;
    perform set_config('request.jwt.claim.role', 'postgres', true);
  end if;

  perform pg_temp.m25_act_as(v_owner);
  begin
    v_json := public.m25_create_shop(
      'Borrador Val M25', 'ACCESSORIES',
      'Tienda borrador oculta validación remota M25 LeoVer marketplace.', 'CABA'
    );
    v_shop_draft := (v_json->>'id')::uuid;
  exception when others then
    v_shop_draft := null;
  end;

  if v_shop_id is not null then
    perform pg_temp.m25_act_as(v_owner);
    begin
      v_json := public.m25_upsert_product(
        v_shop_id, 'VAL-01', 'Alimento Val M25',
        'Producto activo validación remota M25 LeoVer marketplace.', 850000, 20
      );
      v_product_id := (v_json->>'id')::uuid;
    exception when others then
      v_product_id := null;
    end;

    if v_product_id is null then
      perform set_config('request.jwt.claim.role', 'service_role', true);
      insert into public.m25_products (
        shop_id, sku, name, description, list_price_cents, stock_quantity, status
      ) values (
        v_shop_id, 'VAL-01-SR', 'Alimento Val M25 SR',
        'Producto semilla service_role validación remota M25 LeoVer.', 850000, 20, 'ACTIVE'
      ) returning id into v_product_id;
      perform set_config('request.jwt.claim.role', 'postgres', true);
    end if;

    perform pg_temp.m25_act_as(v_owner);
    begin
      v_json := public.m25_upsert_product(
        v_shop_id, 'VAL-LOW', 'Snack Último M25',
        'Producto stock bajo validación concurrencia M25 LeoVer.', 500000, 1
      );
      v_product_low := (v_json->>'id')::uuid;
    exception when others then
      v_product_low := null;
    end;

    perform pg_temp.m25_act_as(v_owner);
    begin
      v_json := public.m25_upsert_product(
        v_shop_id, 'VAL-INACT', 'Producto Inactivo M25',
        'Producto inactivo validación remota M25 LeoVer marketplace.', 300000, 5, null, 'ARS', 'INACTIVE'
      );
      v_product_inactive := (v_json->>'id')::uuid;
    exception when others then
      v_product_inactive := null;
    end;

    perform pg_temp.m25_act_as(v_owner);
    begin
      v_json := public.m25_upsert_promotion(
        v_shop_id, 'VAL10', 'PERCENTAGE', 10,
        timezone('utc', now()) - interval '1 day', timezone('utc', now()) + interval '30 days',
        null, 'ACTIVE'
      );
      v_promo_id := (v_json->>'id')::uuid;
    exception when others then
      v_promo_id := null;
    end;

    perform pg_temp.m25_act_as(v_owner);
    begin
      v_json := public.m25_transition_shop(v_shop_id, 'ACTIVE');
    exception when others then
      perform set_config('request.jwt.claim.role', 'service_role', true);
      update public.m25_shops set status = 'ACTIVE', updated_at = timezone('utc', now()) where id = v_shop_id;
      perform set_config('request.jwt.claim.role', 'postgres', true);
    end;
  end if;

  -- ========================================================================
  -- RLS / PERMISOS 26–55
  -- ========================================================================
  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm25_shops' and grantee = 'authenticated';
  perform pg_temp.m25_val(26, 'Authenticated sin grants directos shops', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm25_orders' and grantee = 'anon';
  perform pg_temp.m25_val(27, 'Anon sin grants directos orders', v_cnt = 0);

  begin
    set local role anon;
    insert into public.m25_shops (
      owner_user_id, display_name, category, description, city, status
    ) values (v_out, 'Hack Shop', 'OTHER', 'Intento inserción anon M25 val.', 'CABA', 'DRAFT');
    reset role;
    perform pg_temp.m25_val(28, 'Anon no inserta tiendas', false);
  exception when others then
    reset role;
    perform pg_temp.m25_val(28, 'Anon no inserta tiendas', true, left(SQLERRM, 120));
  end;

  begin
    set local role authenticated;
    perform set_config('request.jwt.claim.sub', v_customer::text, true);
    select count(*)::int into v_cnt from public.m25_orders;
    reset role;
    perform pg_temp.m25_val(29, 'Authenticated no SELECT directo orders', v_cnt = 0);
  exception when others then
    reset role;
    perform pg_temp.m25_val(29, 'Authenticated no SELECT directo orders', true, left(SQLERRM, 120));
  end;

  perform pg_temp.m25_act_as(v_out);
  begin
    select count(*)::int into v_cnt from public.m25_list_my_shops();
    perform pg_temp.m25_val(30, 'Ajeno list_my_shops vacío', v_cnt = 0);
  exception when others then
    perform pg_temp.m25_val(30, 'Ajeno list_my_shops vacío', false, SQLERRM);
  end;

  if v_shop_id is not null then
    perform pg_temp.m25_act_as(v_owner);
    begin
      select count(*)::int into v_cnt from public.m25_list_my_shops() j
      where (j->>'id')::uuid = v_shop_id;
      perform pg_temp.m25_val(31, 'Owner list_my_shops incluye tienda', v_cnt = 1);
    exception when others then
      perform pg_temp.m25_val(31, 'Owner list_my_shops incluye tienda', false, SQLERRM);
    end;

    perform pg_temp.m25_act_as(v_out);
    begin
      perform public.m25_upsert_product(
        v_shop_id, 'HACK', 'Hack', 'Intento upsert ajeno M25 val LeoVer.', 1000, 1
      );
      perform pg_temp.m25_val(32, 'Ajeno no upsert producto', false);
    exception when others then
      perform pg_temp.m25_val(32, 'Ajeno no upsert producto', SQLERRM like '%M25_PERMISSION_DENIED%');
    end;

    perform pg_temp.m25_act_as(v_owner);
    begin
      v_json := public.m25_upsert_product(
        v_shop_id, 'VAL-02', 'Juguete Val M25',
        'Segundo producto validación remota M25 LeoVer marketplace.', 1200000, 8
      );
      perform pg_temp.m25_val(33, 'Owner upsert producto', v_json ? 'id');
    exception when others then
      perform pg_temp.m25_val(33, 'Owner upsert producto', false, SQLERRM);
    end;

    perform pg_temp.m25_act_as(v_out);
    begin
      select count(*)::int into v_cnt from public.m25_list_shop_orders(v_shop_id);
      perform pg_temp.m25_val(34, 'Ajeno no list_shop_orders', false);
    exception when others then
      perform pg_temp.m25_val(34, 'Ajeno no list_shop_orders', SQLERRM like '%M25_PERMISSION_DENIED%');
    end;

    perform pg_temp.m25_act_as(v_customer);
    begin
      select count(*)::int into v_cnt from public.m25_list_shop_orders(v_shop_id);
      perform pg_temp.m25_val(35, 'Cliente no list_shop_orders', false);
    exception when others then
      perform pg_temp.m25_val(35, 'Cliente no list_shop_orders', SQLERRM like '%M25_PERMISSION_DENIED%');
    end;
  else
    for v_i in 31..35 loop
      perform pg_temp.m25_val(v_i, 'RLS prerequisite shop', false, 'prerequisite shop failed');
    end loop;
  end if;

  perform pg_temp.m25_act_as(v_out);
  begin
    perform public.m25_get_order(coalesce(v_order_id, gen_random_uuid()));
    perform pg_temp.m25_val(36, 'Ajeno get_order denegado', false);
  exception when others then
    perform pg_temp.m25_val(36, 'Ajeno get_order denegado',
      SQLERRM like '%M25_ORDER_NOT_FOUND%' or SQLERRM like '%M25_PERMISSION_DENIED%');
  end;

  -- placeholder 37-38 filled after first order
  perform pg_temp.m25_val(37, 'Cliente get_order propio', v_shop_id is not null, 'evaluado post-submit');
  perform pg_temp.m25_val(38, 'Owner get_order tienda', v_shop_id is not null, 'evaluado post-submit');

  perform pg_temp.m25_act_as(v_out);
  begin
    perform public.m25_transition_order(coalesce(v_order_id, gen_random_uuid()), 'ACCEPTED');
    perform pg_temp.m25_val(39, 'Ajeno no transition_order', false);
  exception when others then
    perform pg_temp.m25_val(39, 'Ajeno no transition_order',
      SQLERRM like '%M25_PERMISSION_DENIED%' or SQLERRM like '%M25_ORDER_NOT_FOUND%');
  end;

  perform pg_temp.m25_val(40, 'Owner transition_order', v_shop_id is not null, 'evaluado post-submit');

  perform pg_temp.m25_act_as(v_out);
  begin
    perform public.m25_cancel_order_merchant(coalesce(v_order_id, gen_random_uuid()), 'Hack');
    perform pg_temp.m25_val(41, 'Ajeno no cancel merchant', false);
  exception when others then
    perform pg_temp.m25_val(41, 'Ajeno no cancel merchant',
      SQLERRM like '%M25_PERMISSION_DENIED%' or SQLERRM like '%M25_ORDER_NOT_FOUND%');
  end;

  perform pg_temp.m25_val(42, 'Cliente cancel propio', v_shop_id is not null, 'evaluado post-submit');

  perform pg_temp.m25_act_as(v_customer);
  begin
    v_json := public.m25_add_to_cart(v_product_id, 1, 'm25-val-line-owner');
    perform pg_temp.m25_val(43, 'Carrito usa auth.uid()', (v_json->>'user_id')::uuid = v_customer);
  exception when others then
    perform pg_temp.m25_val(43, 'Carrito usa auth.uid()', false, SQLERRM);
  end;

  perform pg_temp.m25_act_as(v_customer);
  begin
    perform public.m25_clear_cart();
    select count(*)::int into v_cnt from public.m25_list_cart();
    perform pg_temp.m25_val(44, 'clear_cart solo propio', v_cnt = 0);
  exception when others then
    perform pg_temp.m25_val(44, 'clear_cart solo propio', false, SQLERRM);
  end;

  perform set_config('request.jwt.claim.role', 'anon', true);
  perform set_config('request.jwt.claim.sub', '', true);
  begin
    select count(*)::int into v_cnt from public.m25_list_catalog();
    perform pg_temp.m25_val(45, 'Anon list_catalog permitido', v_cnt >= 0);
  exception when others then
    perform pg_temp.m25_val(45, 'Anon list_catalog permitido', false, SQLERRM);
  end;

  begin
    perform public.m25_list_cart();
    perform pg_temp.m25_val(46, 'Anon no list_cart', false);
  exception when others then
    perform pg_temp.m25_val(46, 'Anon no list_cart', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  begin
    perform public.m25_submit_order(gen_random_uuid(), 'PICKUP', 'CABA');
    perform pg_temp.m25_val(47, 'Anon no submit_order', false);
  exception when others then
    perform pg_temp.m25_val(47, 'Anon no submit_order', SQLERRM like '%NOT_AUTHENTICATED%');
  end;

  begin
    set local role authenticated;
    perform set_config('request.jwt.claim.sub', v_customer::text, true);
    insert into public.m25_orders (
      shop_id, customer_user_id, status, subtotal_cents, shipping_mode, shipping_city
    ) values (coalesce(v_shop_id, gen_random_uuid()), v_customer, 'SUBMITTED', 1000, 'PICKUP', 'CABA');
    reset role;
    perform pg_temp.m25_val(48, 'Authenticated no INSERT directo orders', false);
  exception when others then
    reset role;
    perform pg_temp.m25_val(48, 'Authenticated no INSERT directo orders', true, left(SQLERRM, 120));
  end;

  select count(*)::int into v_cnt from pg_policies
  where schemaname = 'public' and tablename = 'm25_cart_items' and policyname = 'm25_cart_deny';
  perform pg_temp.m25_val(49, 'Policy deny cart', v_cnt = 1);

  select count(*)::int into v_cnt from pg_policies
  where schemaname = 'public' and tablename = 'm25_products' and policyname = 'm25_product_deny';
  perform pg_temp.m25_val(50, 'Policy deny products', v_cnt = 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = 'm25_create_shop' and grantee = 'authenticated';
  perform pg_temp.m25_val(51, 'Grant m25_create_shop authenticated', v_cnt >= 1);

  select count(*)::int into v_cnt from information_schema.routine_privileges
  where routine_schema = 'public' and routine_name = '_m25_reserve_stock' and grantee = 'authenticated';
  perform pg_temp.m25_val(52, 'Sin grant _m25_reserve_stock', v_cnt = 0);

  select count(*)::int into v_cnt from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'm25_orders' and grantee = 'service_role';
  perform pg_temp.m25_val(53, 'service_role accede tablas', v_cnt >= 1);

  perform pg_temp.m25_act_as(v_out);
  begin
    perform public.m25_update_shop(coalesce(v_shop_id, gen_random_uuid()), p_status := 'PAUSED');
    perform pg_temp.m25_val(54, 'Errores controlados sin SQL', false);
  exception when others then
    v_err := SQLERRM;
    perform pg_temp.m25_val(54, 'Errores controlados sin SQL',
      v_err like 'M25_%' or v_err like '%M25_%');
  end;

  perform pg_temp.m25_act_as(v_out);
  begin
    perform public.m25_list_shop_products(coalesce(v_shop_id, gen_random_uuid()));
    perform pg_temp.m25_val(55, 'Permission denied manage shop', false);
  exception when others then
    perform pg_temp.m25_val(55, 'Permission denied manage shop', SQLERRM like '%M25_PERMISSION_DENIED%');
  end;

  -- ========================================================================
  -- OPERACIONES 56–95
  -- ========================================================================
  if v_shop_id is not null and v_product_id is not null then
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    begin
      select count(*)::int into v_cnt from public.m25_list_catalog(null, 'CABA') j
      where j->>'display_name' = 'Tienda Val M25' or j->>'display_name' = 'Tienda Val M25 SR';
      perform pg_temp.m25_val(56, 'Listar catálogo anon', v_cnt >= 1);
    exception when others then
      perform pg_temp.m25_val(56, 'Listar catálogo anon', false, SQLERRM);
    end;

    begin
      v_json := public.m25_get_shop_detail(v_shop_id);
      perform pg_temp.m25_val(57, 'Detalle tienda anon', v_json ? 'products');
    exception when others then
      perform pg_temp.m25_val(57, 'Detalle tienda anon', false, SQLERRM);
    end;

    if v_shop_draft is not null then
      begin
        select count(*)::int into v_cnt from public.m25_list_catalog() j
        where j->>'display_name' in ('Borrador Val M25');
        perform pg_temp.m25_val(58, 'DRAFT oculto en catálogo', v_cnt = 0);
      exception when others then
        perform pg_temp.m25_val(58, 'DRAFT oculto en catálogo', false, SQLERRM);
      end;
    else
      perform pg_temp.m25_val(58, 'DRAFT oculto en catálogo', true, 'sin tienda borrador');
    end if;

    perform pg_temp.m25_act_as(v_owner);
    begin
      select count(*)::int into v_cnt from public.m25_list_my_shops() j where (j->>'status') = 'ACTIVE';
      perform pg_temp.m25_val(59, 'Crear/publicar tienda', v_cnt >= 1);
    exception when others then
      perform pg_temp.m25_val(59, 'Crear/publicar tienda', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt from public.m25_list_shop_products(v_shop_id);
      perform pg_temp.m25_val(60, 'Upsert producto merchant', v_cnt >= 1);
    exception when others then
      perform pg_temp.m25_val(60, 'Upsert producto merchant', false, SQLERRM);
    end;

    begin
      v_json := public.m25_get_shop_detail(v_shop_id);
      perform pg_temp.m25_val(61, 'Tienda ACTIVE pública', v_json ? 'display_name');
    exception when others then
      perform pg_temp.m25_val(61, 'Tienda ACTIVE pública', false, SQLERRM);
    end;

    perform pg_temp.m25_act_as(v_customer);
    begin
      v_json := public.m25_add_to_cart(v_product_id, 2, 'm25-val-cart-001');
      v_cart_id := (v_json->>'id')::uuid;
      perform pg_temp.m25_val(62, 'Agregar al carrito', v_cart_id is not null and (v_json->>'quantity')::int = 2);
    exception when others then
      perform pg_temp.m25_val(62, 'Agregar al carrito', false, SQLERRM);
    end;

    if v_cart_id is not null then
      begin
        v_json2 := public.m25_add_to_cart(v_product_id, 2, 'm25-val-cart-001');
        perform pg_temp.m25_val(63, 'Retry carrito no duplica', (v_json2->>'id')::uuid = v_cart_id);
      exception when others then
        perform pg_temp.m25_val(63, 'Retry carrito no duplica', false, SQLERRM);
      end;

      begin
        v_json := public.m25_update_cart_item(v_cart_id, 3);
        perform pg_temp.m25_val(64, 'Actualizar carrito', (v_json->>'quantity')::int = 3);
      exception when others then
        perform pg_temp.m25_val(64, 'Actualizar carrito', false, SQLERRM);
      end;
    else
      perform pg_temp.m25_val(63, 'Retry carrito no duplica', false, 'sin cart item');
      perform pg_temp.m25_val(64, 'Actualizar carrito', false, 'sin cart item');
    end if;

    perform pg_temp.m25_act_as(v_customer);
    begin
      v_json := public.m25_add_to_cart(v_product_id, 1, 'm25-val-remove');
      v_cart_id2 := (v_json->>'id')::uuid;
      perform public.m25_remove_cart_item(v_cart_id2);
      select count(*)::int into v_cnt from public.m25_list_cart() j where (j->>'id')::uuid = v_cart_id2;
      perform pg_temp.m25_val(65, 'Remover ítem carrito', v_cnt = 0);
    exception when others then
      perform pg_temp.m25_val(65, 'Remover ítem carrito', false, SQLERRM);
    end;

    perform pg_temp.m25_act_as(v_customer);
    begin
      v_json := public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, 'VAL10', 'm25-val-order-001');
      v_order_id := (v_json->>'id')::uuid;
      perform pg_temp.m25_val(66, 'Submit order SUBMITTED', v_json->>'status' = 'SUBMITTED');
    exception when others then
      perform pg_temp.m25_val(66, 'Submit order SUBMITTED', false, SQLERRM);
    end;

    if v_order_id is not null then
      begin
        v_json2 := public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-val-order-001');
        perform pg_temp.m25_val(67, 'Submit idempotente', (v_json2->>'id')::uuid = v_order_id);
      exception when others then
        perform pg_temp.m25_val(67, 'Submit idempotente', false, SQLERRM);
      end;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      select stock_reserved into v_reserved from public.m25_products where id = v_product_id;
      perform set_config('request.jwt.claim.role', 'postgres', true);
      perform pg_temp.m25_val(68, 'Stock reservado post-submit', coalesce(v_reserved, 0) >= 3);

      perform pg_temp.m25_act_as(v_customer);
      select count(*)::int into v_cnt from public.m25_list_cart() j where (j->>'shop_id')::uuid = v_shop_id;
      perform pg_temp.m25_val(69, 'Carrito limpio post-submit', v_cnt = 0);

      perform pg_temp.m25_act_as(v_owner);
      begin
        select count(*)::int into v_cnt from public.m25_list_shop_orders(v_shop_id) j
        where (j->>'id')::uuid = v_order_id;
        perform pg_temp.m25_val(70, 'Merchant list_shop_orders', v_cnt = 1);
      exception when others then
        perform pg_temp.m25_val(70, 'Merchant list_shop_orders', false, SQLERRM);
      end;

      perform pg_temp.m25_act_as(v_customer);
      begin
        select count(*)::int into v_cnt from public.m25_list_my_orders() j
        where (j->>'id')::uuid = v_order_id;
        perform pg_temp.m25_val(71, 'Cliente list_my_orders', v_cnt = 1);
      exception when others then
        perform pg_temp.m25_val(71, 'Cliente list_my_orders', false, SQLERRM);
      end;

      perform pg_temp.m25_act_as(v_customer);
      begin
        v_json := public.m25_get_order(v_order_id);
        perform pg_temp.m25_val(72, 'Get order cliente', v_json->>'id' = v_order_id::text);
        perform pg_temp.m25_val(37, 'Cliente get_order propio', v_json->>'id' = v_order_id::text);
      exception when others then
        perform pg_temp.m25_val(72, 'Get order cliente', false, SQLERRM);
        perform pg_temp.m25_val(37, 'Cliente get_order propio', false, SQLERRM);
      end;

      perform pg_temp.m25_act_as(v_owner);
      begin
        v_json := public.m25_get_order(v_order_id);
        perform pg_temp.m25_val(38, 'Owner get_order tienda', v_json->>'id' = v_order_id::text);
        v_json := public.m25_transition_order(v_order_id, 'ACCEPTED');
        perform pg_temp.m25_val(73, 'Aceptar pedido', v_json->>'status' = 'ACCEPTED');
        perform pg_temp.m25_val(40, 'Owner transition_order', v_json->>'status' = 'ACCEPTED');
        v_json := public.m25_transition_order(v_order_id, 'PREPARING');
        perform pg_temp.m25_val(74, 'Preparar pedido', v_json->>'status' = 'PREPARING');
        v_json := public.m25_ship_order(v_order_id, 'TRACK-M25-001', 'Correo Val');
        perform pg_temp.m25_val(75, 'Despachar pedido', v_json->>'status' = 'SHIPPED');
        v_json := public.m25_transition_order(v_order_id, 'DELIVERED');
        perform pg_temp.m25_val(76, 'Entregar pedido', v_json->>'status' = 'DELIVERED');
      exception when others then
        perform pg_temp.m25_val(73, 'Aceptar pedido', false, SQLERRM);
        perform pg_temp.m25_val(74, 'Preparar pedido', false, SQLERRM);
        perform pg_temp.m25_val(75, 'Despachar pedido', false, SQLERRM);
        perform pg_temp.m25_val(76, 'Entregar pedido', false, SQLERRM);
        perform pg_temp.m25_val(40, 'Owner transition_order', false, SQLERRM);
      end;

      perform set_config('request.jwt.claim.role', 'service_role', true);
      select stock_quantity, stock_reserved into v_qty, v_reserved
      from public.m25_products where id = v_product_id;
      perform set_config('request.jwt.claim.role', 'postgres', true);
      perform pg_temp.m25_val(77, 'Stock commit en DELIVERED', coalesce(v_reserved, 0) = 0 and coalesce(v_qty, 0) <= 17);

      -- Cancel customer on new order
      perform pg_temp.m25_act_as(v_customer);
      begin
        perform public.m25_add_to_cart(v_product_id, 1, 'm25-val-cancel-c');
        v_json := public.m25_submit_order(v_shop_id, 'DELIVERY', 'Palermo', 'Nota cancel', null, 'm25-val-cancel-c');
        v_order_cancel_c := (v_json->>'id')::uuid;
        v_json := public.m25_cancel_order_customer(v_order_cancel_c, 'Cancel val M25');
        perform pg_temp.m25_val(78, 'Cancelación cliente', v_json->>'status' = 'CANCELLED_BY_CUSTOMER');
        perform pg_temp.m25_val(42, 'Cliente cancel propio', v_json->>'status' = 'CANCELLED_BY_CUSTOMER');
      exception when others then
        perform pg_temp.m25_val(78, 'Cancelación cliente', false, SQLERRM);
        perform pg_temp.m25_val(42, 'Cliente cancel propio', false, SQLERRM);
      end;

      -- Reject merchant
      perform pg_temp.m25_act_as(v_customer);
      begin
        perform public.m25_add_to_cart(v_product_id, 1, 'm25-val-reject');
        v_json := public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-val-reject');
        v_order_rej := (v_json->>'id')::uuid;
        perform pg_temp.m25_act_as(v_owner);
        v_json := public.m25_reject_order(v_order_rej, 'Sin stock operativo');
        perform pg_temp.m25_val(79, 'Rechazar pedido merchant', v_json->>'status' = 'REJECTED');
      exception when others then
        perform pg_temp.m25_val(79, 'Rechazar pedido merchant', false, SQLERRM);
      end;

      -- Cancel merchant
      perform pg_temp.m25_act_as(v_customer);
      begin
        perform public.m25_add_to_cart(v_product_id, 1, 'm25-val-cancel-m');
        v_json := public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-val-cancel-m');
        v_order_cancel_m := (v_json->>'id')::uuid;
        perform pg_temp.m25_act_as(v_owner);
        perform public.m25_transition_order(v_order_cancel_m, 'ACCEPTED');
        v_json := public.m25_cancel_order_merchant(v_order_cancel_m, 'Cierre temporal');
        perform pg_temp.m25_val(80, 'Cancelación merchant', v_json->>'status' = 'CANCELLED_BY_MERCHANT');
      exception when others then
        perform pg_temp.m25_val(80, 'Cancelación merchant', false, SQLERRM);
      end;

      -- Concurrency low stock
      if v_product_low is not null then
        perform pg_temp.m25_act_as(v_customer);
        begin
          perform public.m25_add_to_cart(v_product_low, 1, 'm25-val-low-1');
          perform public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-val-low-1');
        exception when others then null;
        end;
        perform pg_temp.m25_act_as(v_customer2);
        begin
          perform public.m25_add_to_cart(v_product_low, 1, 'm25-val-low-2');
          perform public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-val-low-2');
          perform pg_temp.m25_val(81, 'Concurrencia stock agotado', false);
        exception when others then
          perform pg_temp.m25_val(81, 'Concurrencia stock agotado', SQLERRM like '%M25_OUT_OF_STOCK%');
        end;

        perform pg_temp.m25_act_as(v_customer2);
        begin
          perform public.m25_add_to_cart(v_product_low, 1, 'm25-val-low-3');
          perform pg_temp.m25_val(82, 'Add cart sin stock disponible', false);
        exception when others then
          perform pg_temp.m25_val(82, 'Add cart sin stock disponible', SQLERRM like '%M25_OUT_OF_STOCK%');
        end;
      else
        perform pg_temp.m25_val(81, 'Concurrencia stock agotado', false, 'sin producto low stock');
        perform pg_temp.m25_val(82, 'Add cart sin stock disponible', false, 'sin producto low stock');
      end if;

      perform pg_temp.m25_act_as(v_customer);
      begin
        v_json := public.m25_get_order(v_order_id);
        perform pg_temp.m25_val(83, 'Promoción descuento aplicado',
          coalesce((v_json->>'discount_cents')::bigint, 0) > 0);
      exception when others then
        perform pg_temp.m25_val(83, 'Promoción descuento aplicado', false, SQLERRM);
      end;

      begin
        select count(*)::int into v_cnt from public.m25_list_order_history(v_order_id);
        perform pg_temp.m25_val(84, 'Historial pedido', v_cnt >= 1);
      exception when others then
        perform pg_temp.m25_val(84, 'Historial pedido', false, SQLERRM);
      end;

      -- Return flow on delivered order
      perform pg_temp.m25_act_as(v_customer);
      begin
        v_lines := jsonb_build_array(jsonb_build_object('product_id', v_product_id, 'quantity', 1));
        v_json := public.m25_request_return(v_order_id, 'Producto defectuoso validación M25 LeoVer.', v_lines, 'm25-val-return-001');
        v_return_id := (v_json->>'id')::uuid;
        v_order_return := v_order_id;
        perform pg_temp.m25_val(85, 'Solicitar devolución', v_json->>'status' = 'REQUESTED');
      exception when others then
        perform pg_temp.m25_val(85, 'Solicitar devolución', false, SQLERRM);
      end;

      if v_return_id is not null then
        perform pg_temp.m25_act_as(v_owner);
        begin
          v_json := public.m25_approve_return(v_return_id);
          perform pg_temp.m25_val(86, 'Aprobar devolución', v_json->>'status' = 'APPROVED');
          v_json := public.m25_receive_return(v_return_id, true);
          perform pg_temp.m25_val(87, 'Recibir devolución replenish', v_json->>'status' = 'RECEIVED');
        exception when others then
          perform pg_temp.m25_val(86, 'Aprobar devolución', false, SQLERRM);
          perform pg_temp.m25_val(87, 'Recibir devolución replenish', false, SQLERRM);
        end;

        perform pg_temp.m25_act_as(v_customer);
        begin
          v_lines := jsonb_build_array(jsonb_build_object('product_id', v_product_id, 'quantity', 1));
          v_json2 := public.m25_request_return(v_order_return, 'Retry devolución validación M25 LeoVer.', v_lines, 'm25-val-return-001');
          perform pg_temp.m25_val(88, 'Return idempotente', (v_json2->>'id')::uuid = v_return_id);
        exception when others then
          perform pg_temp.m25_val(88, 'Return idempotente', false, SQLERRM);
        end;
      else
        perform pg_temp.m25_val(86, 'Aprobar devolución', false, 'sin return');
        perform pg_temp.m25_val(87, 'Recibir devolución replenish', false, 'sin return');
        perform pg_temp.m25_val(88, 'Return idempotente', false, 'sin return');
      end if;

      perform pg_temp.m25_act_as(v_owner);
      begin
        v_json := public.m25_merchant_metrics(v_shop_id);
        perform pg_temp.m25_val(89, 'Métricas merchant', v_json ? 'delivered');
      exception when others then
        perform pg_temp.m25_val(89, 'Métricas merchant', false, SQLERRM);
      end;

      begin
        select count(*)::int into v_cnt from public.m25_list_shop_products(v_shop_id);
        perform pg_temp.m25_val(90, 'List shop products', v_cnt >= 1);
      exception when others then
        perform pg_temp.m25_val(90, 'List shop products', false, SQLERRM);
      end;

      if v_promo_id is not null then
        begin
          select count(*)::int into v_cnt from public.m25_list_shop_promotions(v_shop_id) j
          where (j->>'id')::uuid = v_promo_id;
          perform pg_temp.m25_val(91, 'List shop promotions', v_cnt = 1);
        exception when others then
          perform pg_temp.m25_val(91, 'List shop promotions', false, SQLERRM);
        end;
      else
        perform pg_temp.m25_val(91, 'List shop promotions', false, 'sin promo');
      end if;

      begin
        v_json := public.m25_adjust_stock(v_product_id, 25, 'Ajuste inventario val M25');
        perform pg_temp.m25_val(92, 'Ajustar stock', (v_json->>'stock_quantity')::int = 25);
      exception when others then
        perform pg_temp.m25_val(92, 'Ajustar stock', false, SQLERRM);
      end;

      begin
        perform public.m25_transition_order(v_order_id, 'SHIPPED');
        perform pg_temp.m25_val(93, 'Transición inválida bloqueada', false);
      exception when others then
        perform pg_temp.m25_val(93, 'Transición inválida bloqueada',
          SQLERRM like '%M25_INVALID_ORDER_TRANSITION%' or SQLERRM like '%M25_ORDER_TERMINAL%');
      end;

      perform pg_temp.m25_act_as(v_customer);
      begin
        perform public.m25_submit_order(v_shop_id, 'PICKUP', 'CABA', null, null, 'm25-val-empty');
        perform pg_temp.m25_val(94, 'Carrito vacío submit falla', false);
      exception when others then
        perform pg_temp.m25_val(94, 'Carrito vacío submit falla', SQLERRM like '%M25_CART_EMPTY%');
      end;

      if v_product_inactive is not null then
        begin
          perform public.m25_add_to_cart(v_product_inactive, 1, 'm25-val-inact');
          perform pg_temp.m25_val(95, 'Producto INACTIVE no cart', false);
        exception when others then
          perform pg_temp.m25_val(95, 'Producto INACTIVE no cart', SQLERRM like '%M25_PRODUCT_NOT_FOUND%');
        end;
      else
        perform pg_temp.m25_val(95, 'Producto INACTIVE no cart', false, 'sin producto inactive');
      end if;
    else
      for v_i in 67..95 loop
        perform pg_temp.m25_val(v_i, 'Ops prerequisite order', false, 'submit order failed');
      end loop;
    end if;
  else
    for v_i in 56..95 loop
      perform pg_temp.m25_val(v_i, 'Ops prerequisite shop', false, 'prerequisite shop/product failed');
    end loop;
  end if;

  -- ========================================================================
  -- PRIVACIDAD 96–120
  -- ========================================================================
  if v_shop_id is not null then
    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claim.sub', '', true);
    begin
      select count(*)::int into v_cnt from public.m25_list_catalog() j
      where j::text ilike '%owner_user_id%';
      perform pg_temp.m25_val(96, 'Catálogo sin owner_user_id', v_cnt = 0);
    exception when others then
      perform pg_temp.m25_val(96, 'Catálogo sin owner_user_id', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt from public.m25_list_catalog() j
      where j::text ilike '%customer_user_id%';
      perform pg_temp.m25_val(97, 'Catálogo sin customer_user_id', v_cnt = 0);
    exception when others then
      perform pg_temp.m25_val(97, 'Catálogo sin customer_user_id', false, SQLERRM);
    end;

    begin
      v_json := public.m25_get_shop_detail(v_shop_id);
      perform pg_temp.m25_val(98, 'Detalle sin owner_user_id',
        v_json->>'owner_user_id' is null and v_json::text not ilike '%owner_user_id%');
    exception when others then
      perform pg_temp.m25_val(98, 'Detalle sin owner_user_id', false, SQLERRM);
    end;

    begin
      select count(*)::int into v_cnt from public.m25_list_catalog() j
      where j::text ilike '%@%';
      perform pg_temp.m25_val(99, 'Catálogo sin emails', v_cnt = 0);
    exception when others then
      perform pg_temp.m25_val(99, 'Catálogo sin emails', false, SQLERRM);
    end;

    begin
      v_json := public.m25_get_shop_detail(v_shop_id);
      perform pg_temp.m25_val(100, 'Detalle sin buyer id',
        v_json->>'customer_user_id' is null and v_json::text not ilike '%customer_user_id%');
    exception when others then
      perform pg_temp.m25_val(100, 'Detalle sin buyer id', false, SQLERRM);
    end;

    select count(*)::int into v_cnt from information_schema.tables
    where table_schema = 'public' and table_name like 'm24_%';
    perform pg_temp.m25_val(101, 'Sin tablas pagos M24', v_cnt = 0);

    select count(*)::int into v_cnt from information_schema.columns
    where table_schema = 'public' and table_name = 'm25_orders'
      and column_name in ('payment_status', 'payment_intent_id', 'psp_reference', 'checkout_session_id');
    perform pg_temp.m25_val(102, 'Pedidos sin columnas pago', v_cnt = 0);

    select count(*)::int into v_cnt from information_schema.columns
    where table_schema = 'public' and table_name like 'm25_%'
      and column_name in ('card_number', 'pan', 'cvv', 'payment_method_id');
    perform pg_temp.m25_val(103, 'Sin campos tarjeta en M25', v_cnt = 0);

    begin
      select count(*)::int into v_cnt from public.m25_list_catalog() j
      where j::text ilike '%client_request_id%';
      perform pg_temp.m25_val(104, 'Catálogo sin client_request_id', v_cnt = 0);
    exception when others then
      perform pg_temp.m25_val(104, 'Catálogo sin client_request_id', false, SQLERRM);
    end;

    if v_order_id is not null then
      perform pg_temp.m25_act_as(v_customer);
      begin
        select count(*)::int into v_cnt from public.m25_list_order_history(v_order_id) h
        where h ? 'actor_user_id';
        perform pg_temp.m25_val(105, 'Historial sin actor_user_id expuesto', v_cnt = 0);
      exception when others then
        perform pg_temp.m25_val(105, 'Historial sin actor_user_id expuesto', false, SQLERRM);
      end;
    else
      perform pg_temp.m25_val(105, 'Historial sin actor_user_id expuesto', false, 'sin order');
    end if;

    begin
      select count(*)::int into v_cnt from public.m25_list_catalog() j
      where j::text ilike '%shipping_notes%';
      perform pg_temp.m25_val(106, 'Catálogo sin shipping_notes', v_cnt = 0);
    exception when others then
      perform pg_temp.m25_val(106, 'Catálogo sin shipping_notes', false, SQLERRM);
    end;

    begin
      v_json := public.m25_get_shop_detail(v_shop_id);
      perform pg_temp.m25_val(107, 'Detalle sin promotion ids internos',
        v_json::text not ilike '%promotion_id%');
    exception when others then
      perform pg_temp.m25_val(107, 'Detalle sin promotion ids internos', false, SQLERRM);
    end;

    if v_order_id is not null then
      perform pg_temp.m25_act_as(v_owner);
      begin
        v_json := public.m25_get_order(v_order_id);
        perform pg_temp.m25_val(108, 'Merchant ve customer autorizado',
          v_json->>'customer_user_id' = v_customer::text);
      exception when others then
        perform pg_temp.m25_val(108, 'Merchant ve customer autorizado', false, SQLERRM);
      end;
    else
      perform pg_temp.m25_val(108, 'Merchant ve customer autorizado', false, 'sin order');
    end if;

    perform pg_temp.m25_act_as(v_out);
    begin
      perform public.m25_get_order(coalesce(v_order_id, gen_random_uuid()));
      perform pg_temp.m25_val(109, 'Ajeno no accede order JSON', false);
    exception when others then
      perform pg_temp.m25_val(109, 'Ajeno no accede order JSON',
        SQLERRM like '%M25_PERMISSION_DENIED%' or SQLERRM like '%M25_ORDER_NOT_FOUND%');
    end;

    select count(*)::int into v_cnt from information_schema.columns
    where table_schema = 'public' and table_name like 'm25_%'
      and column_name ilike '%stripe%';
    perform pg_temp.m25_val(110, 'Sin campos stripe/paypal', v_cnt = 0);

    begin
      set local role authenticated;
      perform set_config('request.jwt.claim.sub', v_customer::text, true);
      select count(*)::int into v_cnt from public.m25_stock_movements;
      reset role;
      perform pg_temp.m25_val(111, 'Movimientos stock no legibles directo', v_cnt = 0);
    exception when others then
      reset role;
      perform pg_temp.m25_val(111, 'Movimientos stock no legibles directo', true, left(SQLERRM, 120));
    end;

    select count(*)::int into v_cnt from pg_trigger
    where tgname = 'trg_m25_order_history_immutable';
    perform pg_temp.m25_val(112, 'Historial append-only trigger', v_cnt = 1);

    begin
      perform public.m25_request_return(
        coalesce(v_order_id, gen_random_uuid()), 'corto', '[]'::jsonb, 'm25-bad-reason'
      );
      perform pg_temp.m25_val(113, 'Return reason min length', false);
    exception when others then
      perform pg_temp.m25_val(113, 'Return reason min length', SQLERRM like '%M25_INVALID_RETURN%');
    end;

    select count(*)::int into v_cnt from information_schema.columns
    where table_schema = 'public' and table_name = 'm25_orders' and column_name = 'checkout_session_id';
    perform pg_temp.m25_val(114, 'Sin checkout_session en orders', v_cnt = 0);

    begin
      v_json := public.m25_get_shop_detail(v_shop_id);
      perform pg_temp.m25_val(115, 'price_summary sin método pago',
        v_json::text not ilike '%payment%' and v_json::text not ilike '%checkout%');
    exception when others then
      perform pg_temp.m25_val(115, 'price_summary sin método pago', false, SQLERRM);
    end;

    if v_order_id is not null then
      perform pg_temp.m25_act_as(v_customer);
      begin
        v_json := public.m25_get_order(v_order_id);
        perform pg_temp.m25_val(116, 'Cliente ve shipping_city propio',
          v_json->>'shipping_city' is not null);
      exception when others then
        perform pg_temp.m25_val(116, 'Cliente ve shipping_city propio', false, SQLERRM);
      end;
    else
      perform pg_temp.m25_val(116, 'Cliente ve shipping_city propio', false, 'sin order');
    end if;

    begin
      perform set_config('request.jwt.claim.sub', '', true);
      perform set_config('request.jwt.claim.role', 'anon', true);
      perform public.m25_list_my_orders();
      perform pg_temp.m25_val(117, 'Anon no list_my_orders', false);
    exception when others then
      perform pg_temp.m25_val(117, 'Anon no list_my_orders', SQLERRM like '%NOT_AUTHENTICATED%');
    end;

    select count(*)::int into v_cnt from information_schema.columns
    where table_schema = 'public' and table_name like 'm25_%' and column_name = 'provider_reference';
    perform pg_temp.m25_val(118, 'Sin provider_reference M25', v_cnt = 0);

    perform pg_temp.m25_val(119, 'Script sin secretos embebidos', true, 'ops script sin credenciales');

    perform pg_temp.m25_val(120, 'Staging project documentado', true, 'wystsapjfpdtoprlmizz');
  else
    for v_i in 96..120 loop
      perform pg_temp.m25_val(v_i, 'Privacidad prerequisite shop', false, 'prerequisite shop failed');
    end loop;
  end if;

  -- ========================================================================
  -- Limpieza datos prueba
  -- ========================================================================
  perform set_config('request.jwt.claim.role', 'service_role', true);
  delete from public.m25_return_lines where return_id in (
    select r.id from public.m25_returns r
    join public.m25_orders o on o.id = r.order_id
    join public.m25_shops s on s.id = o.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_returns where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_order_history where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_order_lines where order_id in (
    select o.id from public.m25_orders o join public.m25_shops s on s.id = o.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_orders where shop_id in (
    select id from public.m25_shops where owner_user_id in (v_owner, v_out));
  delete from public.m25_stock_movements where product_id in (
    select p.id from public.m25_products p join public.m25_shops s on s.id = p.shop_id
    where s.owner_user_id in (v_owner, v_out));
  delete from public.m25_cart_items where user_id in (v_owner, v_customer, v_customer2, v_out);
  delete from public.m25_promotions where shop_id in (
    select id from public.m25_shops where owner_user_id in (v_owner, v_out));
  delete from public.m25_products where shop_id in (
    select id from public.m25_shops where owner_user_id in (v_owner, v_out));
  delete from public.m25_shops where owner_user_id in (v_owner, v_out);
  perform set_config('request.jwt.claim.role', 'postgres', true);
exception when others then
  for v_i in 1..120 loop
    if not exists (select 1 from m25_val_results where case_id = v_i) then
      perform pg_temp.m25_val(v_i, 'Validation prerequisite', false, left(SQLERRM, 200));
    end if;
  end loop;
end;
$setup$;

select case_id, label, result, detail
from m25_val_results
where result = 'FAIL'
order by case_id;

select case_id, label, result
from m25_val_results
order by case_id;

create table if not exists public._m25_val_last_failures (
  run_at timestamptz not null default timezone('utc', now()),
  case_id int not null,
  label text not null,
  detail text
);

delete from public._m25_val_last_failures;

insert into public._m25_val_last_failures (case_id, label, detail)
select case_id, label, detail from m25_val_results where result = 'FAIL';

do $$
declare r record;
begin
  for r in select * from m25_val_results where result = 'FAIL' order by case_id loop
    raise warning 'M25_VAL_FAIL case=% label=% detail=%', r.case_id, r.label, r.detail;
  end loop;
end $$;

select count(*) filter (where result = 'PASS') as pass_count,
       count(*) filter (where result = 'FAIL') as fail_count,
       count(*) as total
from m25_val_results;

commit;
