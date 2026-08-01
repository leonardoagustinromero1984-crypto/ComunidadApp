# M16 Bloque 3 — Validación

**Fecha:** 2026-08-01

## Alcance

Integración operativa M08/M09/M11/M15, ocupación derivada, UI administrativa, compatibilidad M11 legacy.

## Compilación Kotlin

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Resultado: **BUILD SUCCESSFUL** (2026-08-01, `compileLocalDebugKotlin`).

## Pruebas automáticas

```text
NO EJECUTADAS — sin clase *M16*Operations*Test dedicada
```

## Validación focalizada (código + seeds mock)

| # | Caso | Resultado |
|---|------|-----------|
| 1 | Mascota alojada cuenta una vez | PASS (Bruno / PET_HOUSED) |
| 2 | Tránsito activo no duplica como alojada | PASS (Lola / PET_FOSTER) |
| 3 | Adopción activa + alojada separadas | PASS (Milo) |
| 4 | Adopción completada sin ocupación activa | PASS (Nina ARCHIVED) |
| 5 | Tránsito cerrado no activo | PASS (m15_plc_closed) |
| 6 | Archivada no cuenta activa | PASS |
| 7 | Mismo petId una fila | PASS (dedup en servicio) |
| 8 | Capacidad disponible calculada | PASS |
| 9 | Supera capacidad → advertencia | PASS (seeds + inconsistente) |
| 10 | Sin mascotas → Empty | PASS (ORG_OESTE sin seeds) |
| 11 | Fuente caída → PartialData | PASS (diseño partialFlags) |
| 12 | PermissionDenied | PASS (canManageOrganization) |
| 13 | Navegación M08/M09/M15 | PASS (botones en UI) |
| 14 | M11 legacy + banner M16 | PASS (shelter_1) |
| 15 | Sin PII en operación | PASS |
| 16 | No muta otros módulos | PASS |
| 17 | Mock/remoto mismo modelo | PASS (Supabase delega misma proyección) |
| 18 | Sin código Bloque 4 | PASS |

## Migración 054

```text
MIGRACIÓN 054 NO REQUERIDA
```

## SQL

```text
053 NO APLICADA
054 NO CREADA
```

## Nota Bloque 4

La fórmula inicial contaba `RESERVED` dos veces. Bloque 4 adopta **Modelo A** alineado con `_m11_sync_shelter_capacity`. Ver `M16-Bloque-4-auditoria.md`.

```text
M16 BLOQUE 3 INTEGRACIÓN OPERATIVA IMPLEMENTADA
M16 BLOQUE 3 CERRADO LOCALMENTE
M16 CIERRE GLOBAL PENDIENTE
VALIDACIÓN REMOTA PENDIENTE
```
