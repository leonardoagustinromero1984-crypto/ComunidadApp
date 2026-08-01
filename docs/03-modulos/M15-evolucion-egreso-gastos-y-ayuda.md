# M15 — Evolución, egreso, gastos y ayuda (Bloque 3)

## Fuente autoritativa

```text
M10 migraciones 040/041
M08 responsabilidad principal de mascota
M15 adaptadores SupabaseM15Placement* / MockM15Placement*
SIN MIGRACIÓN 053
```

## Evolución (append-only)

- Modelo: `M15PlacementEvolution`, `AddM15EvolutionInput`.
- Persistencia M10: `foster_evolution_entries` vía `FosterEvolutionRepository`.
- Reglas: solo placements `RESERVED` o `ACTIVE`; entradas inmutables; `eventType` codificado en título `[M15:TYPE]`.
- UI: `m15/placements/{id}/evolution`, `/evolution/new`.

## Egreso

- Modelo: `M15DischargeReason`, `M15DischargeOutcome`, `M15DischargeInput`.
- RPC M10: `m10_complete_foster_placement`, cancelación reserva vía `cancelReservedPlacement`.
- Motivos tipificados: retorno al responsable, emergencia, otro/cancelación.
- Libera capacidad del hogar; idempotencia vía estado terminal del placement.

## Custodia temporal M08

- Al iniciar placement (`startPlacement`): grant `TEMPORARY_CUSTODIAN` al cuidador de tránsito.
- Al egreso: revocación de custodia temporal; **no** transfiere responsabilidad principal M08.
- Mock local: `M15TemporaryCustodyGrant` en `M15MemoryStore`.

## Gastos (sin pagos)

- Modelo: `M15PlacementExpense`, `AddM15ExpenseInput`.
- Persistencia M10: `foster_expenses`; registro contable únicamente.
- Excluido: pagos, reembolsos, wallets, comprobantes obligatorios.

## Solicitudes de ayuda (sin chat)

- Modelo: `M15PlacementHelpRequest`, `AddM15HelpRequestInput`.
- Persistencia M10: `foster_help_requests`, `foster_help_contributions`.
- Excluido: chat integrado, mensajería M04.

## DataProvider

| Repo M15 | Supabase | Local |
|----------|----------|-------|
| `m15EvolutionRepository` | `SupabaseM15PlacementEvolutionRepository` → `fosterEvolutionRepository` | `MockM15PlacementEvolutionRepository` |
| `m15DischargeRepository` | `SupabaseM15PlacementDischargeRepository` → `fosterPlacementRepository` | `MockM15PlacementDischargeRepository` |
| `m15ExpenseRepository` | `SupabaseM15PlacementExpenseRepository` → `fosterExpenseRepository` | `MockM15PlacementExpenseRepository` |
| `m15HelpRepository` | `SupabaseM15PlacementHelpRepository` → `fosterHelpRepository` | `MockM15PlacementHelpRepository` |

## Rutas UI

```text
m15/placements
m15/placements/{placementId}
m15/placements/{placementId}/evolution
m15/placements/{placementId}/evolution/new
m15/placements/{placementId}/discharge
m15/placements/{placementId}/expenses
m15/placements/{placementId}/expenses/new
m15/placements/{placementId}/help
m15/placements/{placementId}/help/new
```

## Estado Bloque 3

```text
M15 BLOQUE 3 CERRADO LOCALMENTE
M10/M08 SON LA BASE AUTORITATIVA
SIN MIGRACIÓN 053
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

## Propuesta Bloque 4

Métricas agregadas (`M15OperationalMetrics`), hooks M06 push reales, privacidad final en proyecciones públicas, smoke remoto integrado M15/M10, cierre técnico local.
