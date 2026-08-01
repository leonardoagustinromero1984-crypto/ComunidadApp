# M15 — Matriz Bloque 3 (M10 / M08)

## Decisión

```text
CASO A — M10 041 cubre evolución, egreso, gastos y ayuda
M08 conserva responsabilidad principal
SIN MIGRACIÓN 053
```

## Capacidades por capa

| Capacidad M15 B3 | M10 (040/041) | M08 | Decisión |
|------------------|---------------|-----|----------|
| Evolución append-only | `foster_evolution_entries`, RPC add/list | — | REUTILIZAR M10 |
| Egreso completo | `m10_complete_foster_placement`, estados terminal | — | REUTILIZAR M10 |
| Cancelar reserva | RPC cancel placement | — | REUTILIZAR M10 |
| Gastos registro | `foster_expenses` | — | REUTILIZAR M10 |
| Ayuda coordinada | `foster_help_requests`, contributions | — | REUTILIZAR M10 |
| Custodia temporal | flag/revocación en flujo placement (040) | `TEMPORARY_CUSTODIAN` | COORDINAR M08 |
| Responsabilidad principal | no transferir en complete | titular M08 intacto | PRESERVAR M08 |
| Pagos / reembolsos | — | — | EXCLUIDO |
| Chat ayuda | — | — | EXCLUIDO |

## RPC M10 Bloque 3

| Operación M15 | RPC / repo M10 |
|---------------|----------------|
| Agregar evolución | `FosterEvolutionRepository.addEvolution` → `m10_add_foster_evolution` |
| Listar evolución | `observeEvolution` |
| Completar placement | `m10_complete_foster_placement` |
| Cancelar reserva | cancel reserved RPC |
| Registrar gasto | `FosterExpenseRepository.addExpense` |
| Crear ayuda | `FosterHelpRepository.createHelpRequest` |
| Resolver ayuda | `changeHelpRequestStatus` → FULFILLED |

## Mapeo egreso

| M15DischargeOutcome | Acción M10 |
|---------------------|------------|
| COMPLETED | `completePlacement` → status COMPLETED |
| INTERRUPTED | `completePlacement` → status COMPLETED + reason emergencia |
| CANCELLED | `cancelReservedPlacement` |

| M15DischargeReason | FosterEndReason M10 |
|--------------------|---------------------|
| RETURNED_TO_RESPONSIBLE | RETURNED |
| EMERGENCY | EMERGENCY |
| OTHER | OTHER |

## Custodia M08

| Evento | Custodia temporal | Responsable principal |
|--------|-------------------|----------------------|
| `startPlacement` | Grant TEMPORARY_CUSTODIAN al cuidador | Sin cambio |
| `discharge` COMPLETED/INTERRUPTED | Revoke custodia temporal | Sin cambio |
| Egreso | — | M08 titular permanece |

## Brechas / 053

Ninguna brecha estructural detectada. Tablas 041 existentes cubren el alcance. **053 no creada.**

## Implementación cliente

| Capa | Archivo |
|------|---------|
| Modelos lifecycle | `M15LifecycleModels.kt` |
| Mappers | `M15LifecycleMappers.kt` |
| Adaptadores Supabase | `SupabaseM15LifecycleRepositories.kt` |
| Mocks locales | `MockM15LifecycleRepositories.kt` |
| ViewModels | `M15LifecycleViewModels.kt` |
| UI | `M15LifecycleScreens.kt` |
| Navegación | `M15NavGraph.kt`, `NavRoutes.kt` |
| Switching | `DataProvider` |
