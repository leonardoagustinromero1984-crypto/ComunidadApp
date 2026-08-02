# M25 Bloque 2 — Validación

## Migración

```text
070_m25_marketplace_catalog_cart_and_orders.sql — CREADA, NO APLICADA
```

## Tests Kotlin

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m25.*" --no-configuration-cache --max-workers=1 --console=plain
```

Incluye `M25MarketplaceRemoteMapperTest` (fixtures JSON, sin red).

## Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

## Staging

**No modificado** en Bloque 2.

## Bloque 3

**No iniciado.**

## M24

**Pospuesto.** Sin integración de pagos.
