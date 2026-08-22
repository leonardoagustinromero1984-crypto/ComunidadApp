# MAP-01 — Free-only maps (Android)

**Product decision (permanent for V1 Production):** LeoVer launches without paid map/search/geocoding/navigation APIs.

| Flag | Value |
|---|---|
| `MAP_COST_POLICY` | `FREE_ONLY` |
| `PRODUCTION_MAP_POLICY` | `FREE_ONLY` |
| `PRODUCTION_REQUIRES_PAID_MAP_API` | `NO` |
| `PAID_MAP_APIS_V1` | `FORBIDDEN` |
| `MAP_SEARCH_AUTHORITY` | `LEOVER_CANONICAL_DATA` |
| `NEARBY_CALCULATION` | `SUPABASE_POSTGIS` |
| `ADDRESS_GEOCODING_DEPENDENCY` | `NONE` |
| `MAP_PROVIDER_REPLACEABLE` | `YES` |
| `NO_AUTOMATIC_PAID_FALLBACK` | `YES` |

## What free-only allows

The Android client may use a base map SDK **only** for:

- interactive map rendering
- pan / zoom
- markers
- current **foreground** device location
- map pin positioning / camera control
- displaying **LeoVer-owned** geographic data

LeoVer remains source of truth for providers, organizations, pets, cases, locations, service areas, and directory data.

## Forbidden (do not enable or integrate)

Do **not** enable:

- Google Places API / Places SDK search
- Google Autocomplete
- Google Geocoding / Reverse Geocoding
- Google Routes / Directions
- Distance Matrix
- Navigation SDK
- Street View
- Address Validation
- any other separately billable Google Maps Platform service
- any paid external business-directory dependency

`GOOGLE_PLACES_ENABLED = NO`  
`GOOGLE_AUTOCOMPLETE_ENABLED = NO`  
`GOOGLE_GEOCODING_ENABLED = NO`  
`GOOGLE_ROUTES_ENABLED = NO`  
`GOOGLE_NAVIGATION_ENABLED = NO`  
`GOOGLE_STREET_VIEW_ENABLED = NO`

## Current Android base map

Preferred V1 implementation:

**Google Maps SDK for Android + Maps Compose** — rendering only.

Domain / ViewModels / Community / Foster / Lost-Found must depend on `LeoVerMap` types (`LeoVerGeoPoint`, `LeoVerMapMarker`, `LeoVerMapCameraState`, `LeoVerMapBounds`), never Google Maps classes.

If Google later starts charging for this base-map capability:

1. migrate `LeoVerMap` to a free/open provider (MapLibre / OpenStreetMap or another approved free alternative), **or**
2. temporarily disable the optional map view while preserving list/location functionality

until explicit product approval exists. **Do not automatically start paying.**

## Manual Android setup (API key)

The SDK requires a Maps SDK for Android key from Google Cloud. This is **not** Places/Geocoding.

1. Google Cloud Console → enable **Maps SDK for Android** only.
2. Create an API key. Restrict it:
   - Application restriction: Android apps
   - Package names (all flavors):
     - `com.comunidapp.app`
     - `com.comunidapp.app.local`
     - `com.comunidapp.app.staging`
   - SHA-1 / SHA-256 of the signing certificate(s) used for QA and release
   - API restriction: **Maps SDK for Android** only — never Places, Geocoding, Routes, Navigation, Street View
3. Put the key in `local.properties` (gitignored):

```
MAPS_API_KEY=YOUR_KEY_HERE
```

Never commit, log, document, or test the real key.

Gradle copies it into:

- `BuildConfig.MAPS_API_KEY`
- AndroidManifest `com.google.android.geo.API_KEY` via manifest placeholder

If the key is missing, the APK still compiles. The map view shows a configuration placeholder. That is **not** map success. Physical QA with a restricted key is required.

## Nearby / distance

Distance is calculated with **device foreground lat/lng + provider canonical coordinates + Supabase/PostGIS** (`ST_Distance` / `ST_DWithin`).

Never Google Distance Matrix or Routes.

Show `1.2 km` only when a real geographic distance was calculated. Do not invent distance from province, locality, address text, or list order.

## Address without geocoding

Fixed premises:

1. Provincia
2. Localidad
3. interactive map pin
4. Confirmar ubicación

Confirmed pin latitude/longitude = geographic authority.  
Optional address text = display/contact only. Never reverse-geocode or geocode via an external API.

Mobile person providers (paseador, cuidador, educador): **no public home pin**. Use provincia / localidad / service area.

Lost/Found: the map abstraction can host later flows. Exact protected coordinates must never become a public raw marker.

## Community flow

`SERVICE → FILTERS / CERCA MÍO → BUSCAR → RESULTS → LISTA | MAPA`

Markers are the current LeoVer result set. Camera movement does not search. No Google business search.
