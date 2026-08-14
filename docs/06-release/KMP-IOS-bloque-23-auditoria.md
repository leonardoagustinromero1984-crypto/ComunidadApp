# KMP-IOS — Bloque 23 auditoría (Pet health)

Fuente: `036` `m08_update_pet_health` + SELECT `pets` (sin `m08_get_pet_health`).

## CONTRACTS

| Capacidad | Contrato | Clase |
| --------- | -------- | ----- |
| HEALTH_READ | SELECT `pets` / list RPC columns | REAL_REMOTE_SUPPORTED / OWNER_READ |
| HEALTH_WRITE | `m08_update_pet_health` + `pet.manage_health` | OWNER_WRITE |
| VACCINE R/W | jsonb `vaccinations` `{name,date,next_due_date}` | OWNER_WRITE |
| REMINDER R/W | jsonb `reminders` `{id,title,date,type}` | OWNER_WRITE |

## PRIVACY

`PetHealthSummary` solo en detalle autenticado.
`get_public_pet` / `PublicContent.Pet` **sin** health.

## NOT IN SCOPE

Archive/deceased (KMP-26).
