# KMP-IOS — Bloque 24 auditoría (L/F owner edit + media)

**Nota:** KMP-24 = bloque KMP. M24 Pagos = POSPUESTO / NO TOCAR.

## EDITABLE_FIELDS

`description`, `location` via PostgREST (`updateOwnerContent`).

## IMMUTABLE

`type`, `author_id`, `public_code`, `id` — no writers shared.

## MEDIA_REPLACE_POLICY

M05 `LOST_FOUND_MEDIA` → `photo_url = assetId`.
Sin `photo_url = null` (no contrato de remove).
Sin borrado manual de asset viejo.

## OWNER_POLICY

`viewerCanManage` + RLS `author_id`. RESOLVED editable (RLS sin gate de status).

## HARD DELETE

NO UI.
