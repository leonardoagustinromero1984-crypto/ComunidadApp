# M25 Bloque 4 — Validación remota

## Staging

- Proyecto: `wystsapjfpdtoprlmizz` (no producción)
- Previo M25: **069**
- Aplicadas: **070**, **071** (manual SQL, no `db push` global)

## schema_migrations

```text
070 — M25 catálogo, carrito y pedidos base
071 — M25 operaciones, inventario reservado y devoluciones
```

## Validación SQL 120/120

Script: `scripts/ops/m25_remote_validation_070_071.sql`

```powershell
supabase db query --linked -f scripts/ops/m25_remote_validation_070_071.sql
```

Resultado verificado: **120 PASS / 0 FAIL** (2026-08-02, staging `wystsapjfpdtoprlmizz`)

## Smoke remoto 25/25

Script: `scripts/ops/m25_remote_smoke_25.sql`

```powershell
supabase db query --linked -f scripts/ops/m25_remote_smoke_25.sql
```

Resultado verificado: **25 PASS / 0 FAIL** (2026-08-02, staging `wystsapjfpdtoprlmizz`)

## Tests Kotlin M25

Ejecutados en Bloque 3: foundation + operations + remote mapper — **PASS**

## Compilación

```text
compileLocalDebugKotlin — PASS
```

## Migración 071

**Requerida** — stock reservado, submit transaccional, historial, devoluciones.

## Producción

No afectada.

## M24

**Pospuesto.** Sin campos ni estados de pago.
