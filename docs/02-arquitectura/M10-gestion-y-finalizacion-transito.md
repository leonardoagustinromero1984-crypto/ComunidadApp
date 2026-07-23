# M10 — Gestión y finalización del tránsito

## Alcance (bloque 2)

Gastos, evolución, pedidos de ayuda / aportes (sin pagos), cancelación de reservas e finalización de alojamientos activos.

## Modelos

- `FosterExpense` + `FosterExpenseCategory`
- `FosterEvolutionEntry` + `FosterHealthStatus` + `FosterEvolutionVisibility`
- `FosterHelpRequest` / `FosterHelpContribution` + estados / tipos
- `FosterPlacementEndReason`; columnas `endNotes` / `endedBy` en `FosterPlacement`

## Gastos

- Solo alojamientos `ACTIVE`; registra el hogar temporal.
- `amountMinor > 0` (sin `Double`); moneda validada.
- Comprobantes: solo `m05://` o `file_asset:` (sin bucket público `leover`).
- No públicos; sin cobros ni reintegros.

## Evolución

- Separada del historial clínico M08.
- `PUBLIC` trunca descripción sensible; `CRITICAL` preparado para alerta futura (sin push).
- Medios con mismas reglas de referencia segura.

## Ayuda y aportes

- Tipos incluyen `MONEY` como **necesidad**, no pasarela.
- Rechazo de CBU / alias bancario / tarjeta en texto.
- Aportes `PLEDGED` / `RECEIVED` / `CANCELLED`; progreso y auto-`FULFILLED` por objetivo.
- Pedidos cerrados no editables (idempotencia / error controlado).

## Finalización / cancelación

| RPC | Uso |
|-----|-----|
| `m10_complete_foster_placement` | `ACTIVE` → `COMPLETED` |
| `m10_cancel_foster_placement` | solo `RESERVED` |

Al completar:

1. Motivo + notas + `ended_by` / `ended_at`
2. Libera ocupación y recalcula disponibilidad
3. Revoca `TEMPORARY_CUSTODIAN`
4. Conserva `PRINCIPAL`
5. Cancela pedidos de ayuda abiertos
6. Conserva gastos / evolución como historial
7. Idempotente si ya `COMPLETED`
8. `ADOPTED` **no** finaliza M09

## Migración

`041_m10_foster_care_management.sql` (forward-only; **no** modifica 040).

Tablas: `foster_expenses`, `foster_evolution_entries`, `foster_help_requests`, `foster_help_contributions`.

RLS + RPC listados en el módulo M10. **040 y 041 pendientes de apply remoto.**

## Repositorios / UI

Contratos mock + Supabase; ViewModels con `StateFlow` y anti doble envío.

Rutas: panel `foster_placement_management`, gastos, evolución, ayuda, complete, `foster_history`.

## Tests / build

- `M10FosterCareManagementTest` (+ núcleo M10 / M08 / M09 focalizados)
- `compileLocalDebugKotlin`

## Limitaciones / pendientes

Sin pagos, reputación, chat, push, IA, galería nueva, apply remoto, APK.
