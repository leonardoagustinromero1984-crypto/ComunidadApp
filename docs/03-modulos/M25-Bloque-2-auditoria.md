# M25 Bloque 2 — Auditoría

Migración `070_m25_marketplace_catalog_cart_and_orders.sql` creada; **no aplicada**.

## Tablas

| Tabla | Propósito |
|---|---|
| `m25_shops` | Tiendas marketplace |
| `m25_products` | Catálogo + stock |
| `m25_promotions` | Descuentos (sin cobro) |
| `m25_cart_items` | Carrito por usuario |
| `m25_orders` | Pedidos sin pago |
| `m25_order_lines` | Líneas de pedido |
| `m25_returns` | Devoluciones |

## RLS

Deny-all para `authenticated` en tablas; operaciones vía RPC SECURITY DEFINER.

## RPC Bloque 2 (subset inicial)

- `m25_list_catalog`, `m25_get_shop_detail` — público
- `m25_list_my_shops`, `m25_create_shop`
- `m25_list_cart`, `m25_add_to_cart`
- `m25_list_my_orders`

Operaciones merchant/submit order: **Bloque 3** (no iniciado).

## M24

Sin tablas ni campos de pago. M24 **pospuesto**.

## Kotlin

- `SupabaseM25RemoteDataSource`
- `SupabaseM25MarketplaceRepository`, `SupabaseM25CartRepository`, `SupabaseM25OrderRepository`
- DataProvider conmuta mock/Supabase

## Validación remota

**Pendiente** — migración no aplicada.
