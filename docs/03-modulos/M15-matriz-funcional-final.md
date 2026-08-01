# M15 — Matriz funcional final

## Principio

```text
M10 = persistencia autoritativa (040/041)
M08 = responsabilidad principal (custodia TEMPORARY_CUSTODIAN)
M15 = experiencia funcional y operación
NO DUPLICAR TABLAS · NO DUPLICAR DATOS
```

## Dominios y persistencia

| Dominio M15 | Modelo | Persistencia | Repo M15 | Validación |
|-------------|--------|--------------|----------|------------|
| Hogar | `M15FosterHome` | `foster_home_profiles` | `m15FosterHomeRepository` | PASS |
| Solicitud | `M15FosterRequest` | `foster_care_requests` | `m15FosterRequestRepository` | PASS |
| Placement | `M15FosterPlacement` | `foster_placements` | `m15FosterPlacementRepository` | PASS |
| Evolución | `M15PlacementEvolution` | `foster_evolution_entries` | `m15EvolutionRepository` | PASS |
| Egreso | `M15DischargeInput` | RPC `m10_complete_foster_placement` | `m15DischargeRepository` | PASS |
| Gastos | `M15PlacementExpense` | `foster_expenses` | `m15ExpenseRepository` | PASS |
| Ayuda | `M15PlacementHelpRequest` | `foster_help_requests` | `m15HelpRepository` | PASS |
| Métricas | `M15OperationalMetrics` | Composición local (sin SQL) | `m15OperationsRepository` | PASS |

## Criterios funcionales — todos PASS

| Criterio | Estado |
|----------|--------|
| Hub y navegación `m15/*` | PASS |
| Operaciones y métricas agregadas | PASS |
| Privacidad sin PII en dashboard y proyecciones | PASS |
| Estados terminales e idempotencia | PASS |
| Capacidad y conflictos | PASS |
| Fallback M06 honesto | PASS |
| Smoke remoto M15/M10 integrado | PASS |
| Sin duplicación M10/M15 | PASS |
| Migraciones 001–052; sin 053 | PASS |

## Estados terminales

| Entidad | Terminales | Reapertura |
|---------|------------|------------|
| Solicitud | REJECTED, CANCELLED, EXPIRED | No |
| Placement | COMPLETED, CANCELLED | No |
| Ayuda | RESOLVED, CANCELLED, EXPIRED | No |
| Gasto terminal | APPROVED, REJECTED, CANCELLED | Solo transición válida |

## Errores Bloque 4

```text
M15_METRICS_INVALID_RANGE
M15_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE
M15_PUBLIC_PROJECTION_UNAVAILABLE
M15_PRIVACY_VIOLATION
M15_STATE_ALREADY_FINAL
M15_IDEMPOTENT_REPLAY
M15_CAPACITY_CONFLICT
M15_CONFLICT
M15_REMOTE_VALIDATION_PENDING
```

## Rutas UI finales

```text
m15/hub
m15/homes, m15/my_home, m15/requests/*
m15/placements/*
m15/operations (+ métricas/privacidad/smoke en tabs)
```

## Hooks M06 preparados

```text
M15_FOSTER_HOME_CREATED, M15_FOSTER_HOME_ACTIVATED
M15_FOSTER_REQUEST_SUBMITTED, M15_FOSTER_REQUEST_ACCEPTED
M15_PLACEMENT_RESERVED, M15_FOSTER_PLACEMENT_STARTED
M15_EVOLUTION_ADDED, M15_PLACEMENT_COMPLETED, M15_PLACEMENT_INTERRUPTED
M15_EXPENSE_RECORDED, M15_HELP_REQUEST_OPENED, M15_HELP_REQUEST_RESOLVED
M15_NOTIFICATION_INFRASTRUCTURE (fallback)
```

## Pendientes

| Tipo | Estado |
|------|--------|
| Pendientes funcionales M15 | **Ninguno** |
| Pendientes externos bloqueantes M15 | **Ninguno** |
| Pruebas automáticas | No ejecutadas (no bloqueante cierre oficial) |

## Estado del módulo

```text
M15 CIERRE OFICIAL COMPLETADO
M15 VALIDACIÓN FUNCIONAL MANUAL PASS
M15 SMOKE FUNCIONAL REMOTO PASS
M15 SIN DEFECTOS CRÍTICOS ABIERTOS
M10/M08 SON LA BASE AUTORITATIVA
SIN MIGRACIÓN 053
M16 NO INICIADO
```

**Fecha cierre oficial:** 1 de agosto de 2026 · `America/Argentina/Buenos_Aires` (UTC-3).
