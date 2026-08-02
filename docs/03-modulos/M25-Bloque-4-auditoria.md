# M25 Bloque 4 — Auditoría paridad remota

## Decisión 071

**REQUERIDA** — 070 no cubría stock reservado, submit transaccional, historial, devoluciones con líneas ni RPCs operativas.

## Estrategia stock concurrente

Reserva atómica en `_m25_reserve_stock`:

```sql
UPDATE m25_products SET stock_reserved = stock_reserved + qty
WHERE id = ... AND stock_quantity - stock_reserved >= qty
RETURNING ...
```

Idempotencia vía `reservation_key` único en `m25_stock_movements`.

## Matriz resumida

| Área | Mock Bloque 3 | 070 | 071 | Brecha post-071 |
|------|---------------|-----|-----|-----------------|
| Submit pedido | ✅ | ❌ | ✅ | — |
| Stock reservado | ✅ | ❌ | ✅ | — |
| Historial | ✅ | ❌ | ✅ | — |
| Devoluciones | ✅ | parcial | ✅ | — |
| Pagos M24 | excluido | excluido | excluido | — |

## Staging

Proyecto: `wystsapjfpdtoprlmizz` (no producción).

Migraciones a aplicar: **070**, **071** (orden estricto).
