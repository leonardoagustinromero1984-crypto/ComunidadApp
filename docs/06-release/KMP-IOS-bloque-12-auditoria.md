# KMP-IOS — Bloque 12 auditoría (Adoption Application)

Fuente: `HEAD` comprometido, no WIP M09 dirty.

## APPLY / INTEREST CONTRACT

| Operación | RPC |
| --------- | --- |
| Submit | `m09_submit_application` |
| Withdraw | `m09_withdraw_application` |
| Mis postulaciones | `m09_list_my_applications` |

Params submit (Android `SubmitApplicationParams`):

- `p_adoption_id`
- `p_message`
- `p_housing_type` (nullable)
- `p_has_other_pets` (nullable)
- `p_previous_experience` (nullable)
- `p_contact_phone` (nullable)

## DUPLICATE

- Backend: `APPLICATION_ALREADY_EXISTS` → UI: “Ya tenés una solicitud activa para esta adopción.”

## STATUS (reales)

`SUBMITTED` | `UNDER_REVIEW` | `ACCEPTED` | `REJECTED` | `WITHDRAWN`

Withdraw UI solo si `SUBMITTED` / `UNDER_REVIEW` (misma regla Android).

## PRIVACY

- `AdoptionApplicationSummary` separado de `AdoptionSummary` / `AdoptionDetail`.
- No applicant phone/email/userId en modelos públicos de adopción.
- `contact_phone` solo en payload submit (opcional).

## MY APPLICATIONS

- Portado: listado mínimo vía `m09_list_my_applications`.

## WITHDRAW

- Portado: `m09_withdraw_application`.

## OWNER / SHELTER SIDE

- RPCs de revisión existen (`m09_list_received_applications`, accept/reject/… ) → **DEFERRED** (fuera de KMP-12 candidate-side).
