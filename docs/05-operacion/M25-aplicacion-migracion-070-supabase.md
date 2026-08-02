# Operación — migración 070 (M25 marketplace)

**LeoVer** · staging · **no aplicar sin autorización explícita**.

## Estado

```text
070_m25_marketplace_catalog_cart_and_orders.sql — CREADA EN REPO, NO APLICADA
```

Última migración aplicada en staging (M23): **069**.

## Procedimiento (cuando se autorice)

1. Confirmar proyecto staging no productivo.
2. Ejecutar **070** completo una sola vez.
3. Verificar 7 tablas `m25_*` + RLS activo.
4. Verificar RPCs `m25_list_catalog`, `m25_add_to_cart`, `m25_list_my_orders`.
5. Registrar versión `070` en `schema_migrations`.
6. Ejecutar script de validación remota (pendiente creación Bloque 3).

## Límites

- No `supabase db push` global.
- No aplicar 039–052.
- No producción.
- **No pagos M24** — migración sin campos financieros.

## M24

Módulo **pospuesto**. Esta migración no habilita cobros.
