# M09 — Persistencia de publicaciones de adopción

## Migración

`supabase/migrations/037_m09_adoption_publications.sql`

Forward-only sobre 001–036. **No reescribe** migraciones anteriores. **No aplicada** en remoto en el bloque 1.

## Evolución de `public.adoptions`

Columnas agregadas:

| Columna | Uso |
|--------|-----|
| `pet_id` | FK a `pets` (publicación ligada a mascota) |
| `title` | Título de la publicación |
| `requirements` | Requisitos de adopción |
| `location_text` | Ubicación textual |
| `published_at` | Momento de publicación |
| `publisher_organization_id` | Reserva org (uso futuro) |

Backfill de estados legacy:

- `AVAILABLE` → `PUBLISHED`
- `IN_PROCESS` → `PAUSED`

## Índices y unicidad

- `adoptions_pet_id_idx`
- `adoptions_status_idx`
- `adoptions_published_at_idx`
- `adoptions_one_open_per_pet_uidx` — unique parcial sobre `pet_id` donde status ∈ {DRAFT, PUBLISHED, PAUSED}

## Seguridad

- RLS select: `PUBLISHED` o publisher/gestor de la mascota.
- Escrituras directas bloqueadas; mutaciones vía RPC `m09_*` (`SECURITY DEFINER`).
- Autorización reutiliza helpers M08 (`_m09_actor_can_manage_pet`).

## Consistencia al adoptar

`m09_mark_adoption_adopted`:

1. Marca publicación `ADOPTED` (idempotente si ya lo está).
2. Si hay `pet_id` y la mascota está `ACTIVE`, pasa a `ARCHIVED` e inserta `pet_status_history` con `reason = 'ADOPTED'`.

No se agrega estado `ADOPTED` al ciclo de vida M08 de mascotas (se usa `ARCHIVED` + historial).
