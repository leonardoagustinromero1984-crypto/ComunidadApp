# M25 — Auditoría inicial

Bloque 1 implementa marketplace local sin cobros reales.

- Persistencia: memoria determinista; sin SQL ni wiring Supabase.
- Integración M03: `organizationId` opcional en tiendas; sin duplicar organizaciones.
- Integración M10: ciudad de envío como texto validado; sin duplicar geoservicios.
- Integración M24: **explícitamente excluida** — M24 pospuesto; sin campos de pago, PSP ni checkout.
- Privacidad: proyecciones públicas sin IDs internos, propietario ni organización.
- M06: hook stub `M25NotificationHookState`; sin ampliar allowlist.

## Alcance Bloque 1

| Incluido | Excluido |
|---|---|
| Tiendas, productos, promociones mock | Pagos, señas, reembolsos |
| Carrito e idempotencia `clientLineId` | Migración SQL |
| Pedidos sin cobro (SUBMITTED→DELIVERED) | PSP, webhooks financieros |
| Devoluciones solicitadas | Conciliación, facturación |
| UI Compose hub/catálogo/carrito/pedidos | Bloque 3 operaciones merchant remoto |

## Actores

- Comprador (cliente autenticado)
- Comerciante (owner tienda / M03 org)
- Público anónimo (catálogo futuro vía RPC Bloque 2)

## Dependencias

- M01 identidad
- M03 organizaciones (opcional)
- M06 notificaciones (stub)
- M10 ubicación (texto envío)
