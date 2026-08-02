# M25 Bloque 3 — Validación

## Tests Kotlin

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m25.*" --no-configuration-cache --max-workers=1 --console=plain
```

Incluye `M25MarketplaceOperationsTest` (40 casos operativos) + foundation + remote mapper.

## Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

## Migraciones

- **070** — creada, **no aplicada**
- **071** — pendiente Bloque 4

## Staging

**No modificado** en Bloque 3.

## M24

**Pospuesto.** Sin integración de pagos.

## Bloque 4

Pendiente — paridad Supabase, aplicación 070+071, cierre oficial.
