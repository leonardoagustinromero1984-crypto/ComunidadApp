# KMP-IOS — Bloque 14 auditoría (Shelter application review)

Fuente: shared KMP — sin tocar WIP M09 dirty en `app/`.

## REVIEW CONTRACT

| Operación | RPC |
| --------- | --- |
| Recibidas | `m09_list_received_applications` (`p_status` nullable / JsonNull) |
| Detalle | `m09_get_application` (`p_application_id`) |
| En revisión | `m09_mark_application_under_review` |
| Aceptar | `m09_accept_application` |
| Rechazar | `m09_reject_application` (`p_rejection_reason` nullable) |

## MODELOS

- `AdoptionApplicationReviewSummary` — incluye `applicantDisplayName`; **sin** `applicantUserId`.
- `AdoptionApplicationReviewDetail` — campos privados manager (phone, housing, experience, rejection).
- Candidate summary (`AdoptionApplicationSummary`) sin nombre/teléfono del postulante.

## ERRORES

| Código | Mensaje UI |
| ------ | ---------- |
| `APPLICATION_FORBIDDEN` | No tenés permiso para esta acción. |
| `APPLICATION_INVALID_TRANSITION` | Esa transición de estado no está permitida. |
| `APPLICATION_NOT_FOUND` | No encontramos ese contenido. |

## TRANSICIONES UI

- Mark under review: `SUBMITTED`
- Accept / Reject: `SUBMITTED` \| `UNDER_REVIEW`
