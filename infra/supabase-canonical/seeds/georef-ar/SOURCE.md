# GEOREF_AR — LeoVer Argentina catalog

- `source` = `GEOREF_AR`
- `source_version` = Georef API v2.1
- `source_date` = 2026-08-16
- `source_external_id` = Georef `localidades.id` (stored in `location_nodes.iso_code` for LOCALITY rows)

Official complete downloads (no private lists):

- https://apis.datos.gob.ar/georef/api/v2.1/provincias.json
- https://apis.datos.gob.ar/georef/api/v2.1/localidades.json

LeoVer runtime reads `public.location_nodes` / `canon_list_location_catalog()`. It does not call Georef.

To regenerate 1026 after a new official download:

1. Replace `provincias.json` and `localidades.json`
2. Run `generate-1026.ps1`
3. Apply the new forward migration on a clean environment, or keep 1026 as the frozen import of this date

Existing LeoVer IDs (`loc-ar`, 24 provinces, 14 baseline localities) are preserved. New rows use `loc-ar-loc-georef-{id}`.
