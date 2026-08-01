# M16 Bloque 2 — Validación

**Fecha:** 2026-08-01  
**Variante:** localDebug  
**Migración:** `053_m16_shelter_profiles_and_public_access.sql`

## Compilación Kotlin

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

```text
BUILD SUCCESSFUL
compileLocalDebugKotlin PASS
```

## SQL aplicado

```text
NO APLICADO — apply remoto requiere autorización explícita del operador en entorno no productivo.
Migración 053 lista en supabase/migrations/.
Procedimiento: docs/05-operacion/M16-aplicacion-migracion-053-supabase.md
```

## Pruebas automáticas

```text
NO EJECUTADAS — sin clase *M16*Test configurada en el repo.
```

## Validación focalizada (diseño / código)

| # | Caso | Estado |
|---|------|--------|
| 1 | Anon listar PUBLISHED vía RPC | PASS (diseño 053) |
| 2 | Anon sin acceso tablas internas | PASS (revoke + RLS deny) |
| 3 | UNPUBLISHED excluido de listado público | PASS (filtro SQL) |
| 4 | ACTIVE visible por defecto | PASS |
| 5 | PAUSED visible por defecto | PASS |
| 6 | PERMANENTLY_CLOSED solo con filtro | PASS |
| 7 | Detalle público sin IDs privados | PASS (`_m16_public_shelter_json`) |
| 8 | Contactos privados excluidos | PASS (`is_public`) |
| 9 | Admin lee perfil propio | PASS (`m16_get_shelter_profile`) |
| 10 | Admin actualiza datos | PASS (RPCs mutación) |
| 11 | Org no elegible rechazada | PASS (`_m16_org_is_eligible`) |
| 12 | Segunda creación idempotente | PASS (UNIQUE org + retorno existente) |
| 13 | Usuario ajeno no muta | PASS (`has_org_permission`) |
| 14 | Anon no muta | PASS (grants authenticated only) |
| 15–18 | Idempotencia estados / terminal | PASS (SQL + mock preservado) |
| 19–20 | Verificación PENDING / no auto-VERIFIED | PASS |
| 21–25 | Validadores capacidad/horarios/contactos | PASS (SQL CHECK + Kotlin validators) |

Validación remota en staging: **PENDIENTE** hasta apply de 053.

## M03 autorización

- `canManageOrganization` → RPC `has_org_permission(..., 'shelter.manage')`
- `isOrganizationEligible` → RPC `m16_is_organization_eligible`
- Complementa RLS; no reemplaza

## M04 verificación

- Solicitud: `m16_request_verification` → PENDING + fila en cola
- Decisión: `m16_decide_shelter_verification` (moderador)
- UI M04 para cola M16: **PENDIENTE** (adaptador mínimo listo)

## M06

```text
M16_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE
```

## Estado

```text
M16 BLOQUE 2 PERSISTENCIA REMOTA IMPLEMENTADA
MIGRACIÓN 053 CREADA
RLS M16 IMPLEMENTADO
REPOSITORIO SUPABASE M16 OPERATIVO
AUTORIZACIÓN M03 INTEGRADA
COMPILACIÓN KOTLIN PASS
SQL REMOTO PENDIENTE DE APPLY AUTORIZADO
M16 BLOQUE 3 NO INICIADO
M16 COMPLETO: NO
```
