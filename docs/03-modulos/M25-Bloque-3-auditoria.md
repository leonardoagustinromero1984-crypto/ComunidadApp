# M25 Bloque 3 — Auditoría operaciones marketplace

**LeoVer** · sin pagos (M24 pospuesto).

## Matriz de cobertura

| # | Función | Kotlin | Mock | 070 | Supabase | UI | Brecha |
|---|---------|--------|------|-----|----------|-----|--------|
| 1 | Comercios | ✅ | ✅ | parcial | stub | ✅ | 071 transiciones |
| 2 | Catálogo | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 3 | Categorías | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 4 | Productos | ✅ | ✅ | ✅ | stub | ✅ | 071 upsert |
| 5 | Variantes | — | — | — | — | — | fuera alcance D01 |
| 6 | Stock reservado | ✅ | ✅ | ❌ | stub | ✅ | **071** |
| 7 | Promociones | ✅ | ✅ | ✅ | stub | parcial | 071 cálculo servidor |
| 8 | Carrito | ✅ | ✅ | parcial | parcial | ✅ | 071 update/remove |
| 9 | Precio informativo | ✅ | ✅ | ✅ | ✅ | ✅ | sin pago |
| 10 | Pedido | ✅ | ✅ | parcial | stub | ✅ | **071 submit transaccional** |
| 11 | Líneas snapshot | ✅ | ✅ | parcial | stub | ✅ | 071 discount/subtotal |
| 12 | Preparación | ✅ | ✅ | ❌ | stub | ✅ | 071 RPC |
| 13 | Despacho | ✅ | ✅ | ❌ | stub | ✅ | 071 RPC |
| 14 | Entrega | ✅ | ✅ | ❌ | stub | ✅ | 071 commit stock |
| 15 | Cancelación | ✅ | ✅ | ❌ | stub | ✅ | 071 release |
| 16 | Devolución | ✅ | ✅ | parcial | stub | parcial | 071 líneas |
| 17 | Historial | ✅ | ✅ | ❌ | stub | parcial | **071 tabla** |
| 18 | Idempotencia | ✅ | ✅ | parcial | stub | — | 071 client_request |
| 19 | Privacidad | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 20 | Moderación | stub M04 | — | ❌ | ❌ | — | Bloque 4 |

## Exclusiones confirmadas

- Sin checkout, PSP, estados PAID/REFUNDED.
- Sin variantes complejas (no en D01 Bloque 3).
- M06 hook stub; allowlist no ampliada.

## Migración 070

**NO aplicada** — operaciones centrales requieren **071**.

## Bloque 4

Pendiente: paridad remota, aplicación staging, validación 120/120, smoke 25/25.
