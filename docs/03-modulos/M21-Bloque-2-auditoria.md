# M21 Bloque 2 — Auditoría

**Migración:** `064_m21_reputation_reviews_and_verifications.sql`

## Tablas

- `m21_reviews` — reseñas transaccionales, unique reviewer+target
- `m21_verification_requests` — identidad / matrícula
- `m21_appeals` — apelaciones abiertas por reseña

## RPCs

- `m21_get_my_reputation_summary`
- `m21_list_my_reviews` / `m21_list_reviews_for_target`
- `m21_submit_review`
- `m21_list_my_verifications` / `m21_submit_verification`
- `m21_submit_appeal`

## Paridad mock / 064

| Función | Mock | 064 |
|---------|------|-----|
| Resumen | Sí | Sí |
| Submit review | Sí | Sí |
| Duplicate guard | Sí | Sí |
| Verifications | Sí | Sí |
| Appeals | Sí | Sí |
| Badges en summary remoto | Sí (mock) | Parcial (score only) |

## Estado

**064 NO APLICADA** al cierre Bloque 2.
