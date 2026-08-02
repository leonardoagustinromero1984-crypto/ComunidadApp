# M25 — Arquitectura marketplace (Bloque 1)

## Capas

```
ui/screens/m25          → ViewModels → Repositories (mock)
domain/m25              → Validators, lifecycle, privacy, order ops
data/model              → M25MarketplaceModels.kt
data/repository         → Interfaces + Mock + MemoryStore
navigation/M25NavGraph  → rutas m25/*
```

## Rutas

- `m25/hub` — hub marketplace
- `m25/shops` — catálogo tiendas
- `m25/shops/{shopId}` — detalle
- `m25/cart` — carrito
- `m25/orders` — mis pedidos
- `m25/shops/manage` — gestión comerciante

## Estados pedido (sin pago)

`DRAFT → SUBMITTED → ACCEPTED → PREPARING → SHIPPED → DELIVERED → RETURN_REQUESTED → RETURNED`

Terminales: `CANCELLED`, `RETURNED`.

## Autoridades

- M01: actor autenticado
- M03: `organizationId` opcional en tienda
- M24: **no integrado** (pospuesto)

## Bloque 2 (pendiente aplicación remota)

Migración `070_m25_marketplace_catalog_cart_and_orders.sql` — tablas `m25_*`, RLS deny + RPC SECURITY DEFINER. **No aplicada.** Validación remota pendiente.
