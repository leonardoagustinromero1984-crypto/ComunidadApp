# KMP-IOS — Bloque 20 auditoría (Public deep link content)

Fuente autoritativa: `supabase/migrations/081_web_public_shareable_pages.sql` + `web/lib/public/*`.

## RPCs (exactas)

| Vertical | RPC | Input |
| -------- | --- | ----- |
| Pet / passport | `get_public_pet` | `p_public_code text` |
| Adoption | `get_public_adoption` | `p_public_code text` |
| Lost | `get_public_lost_case` | `p_public_code text` |
| Found | `get_public_found_case` | `p_public_code text` |

Grants: `anon`, `authenticated`. Error uniforme: `NOT_PUBLIC` / `P0001` → NotFound.

## OUTPUT SAFE FIELDS

- **Pet:** `public_code`, `display_name`, `species`, `breed_text`, `sex`, `birth_date`, `primary_color`, `distinctive_marks`, `microchip_masked`, `status`, `photo_url` (often null/`storage:`), `updated_at`
- **Adoption:** `public_code`, `title`, `name`, `description`, `requirements`, `species`, `sex`, ages, `size`, `status`, `location_text`, `photo_url`, `publisher_display_name`, timestamps
- **Lost/Found:** `public_code`, `case_type`, `pet_name`, `species`, `description`, `zone_text`, `status`, `photo_url`, timestamps

## REDACTION

Omitidos: email, phone, `author_id`, `publisher_id`, `pet_id`, coords, `contact_info`, microchip completo.

## MEDIA

`MediaRefParser.fromPhotoField` — HTTPS/UUID; `storage:` → null (sin nuevo stack).

## PUBLIC CODE FORMAT

`PUB-` + hex (generado en SQL `_web_generate_public_code` / M14).

## SHARED

`PublicContentRepository` + `SharedPublicContentScreen`; deep links públicos sin login forzado.
