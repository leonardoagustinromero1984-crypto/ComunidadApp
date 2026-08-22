# UX-07 + MAP-01 — product architecture notes

| Flag | Value |
|---|---|
| `UI_CONTRACT_ENFORCEMENT` | `MANDATORY` |
| `APP_BACKGROUND` | `#FAFBF8` |
| `PRIMARY_UI_GREEN` | `#49B749` |
| `MAP_COST_POLICY` | `FREE_ONLY` |
| `PRODUCTION_MAP_POLICY` | `FREE_ONLY` |
| `PAID_MAP_APIS_V1` | `FORBIDDEN` |
| `PRODUCTION_REQUIRES_PAID_MAP_API` | `NO` |
| `MAP_SEARCH_AUTHORITY` | `LEOVER_CANONICAL_DATA` |
| `NEARBY_CALCULATION` | `SUPABASE_POSTGIS` |
| `ADDRESS_GEOCODING_DEPENDENCY` | `NONE` |
| `MAP_PROVIDER_REPLACEABLE` | `YES` |
| `COMMUNITY_VIEW` | `LIST + MAP` |
| `COMMUNITY_FLOW` | `SERVICE → FILTERS / CERCA MÍO → BUSCAR → RESULTS → LISTA / MAPA` |
| `PERSON_PUBLISH` | `SOCIAL_ONLY_BY_DEFAULT` |
| `FOSTER_STAY` | `CANONICAL_PLACEMENT_LINKED_TO_PET` |
| `SERVICE_HOURS` | `STRUCTURED` |
| `FIXED_PROVIDER_LOCATION` | `USER_CONFIRMED_MAP_PIN` |
| `MOBILE_PROVIDER_LOCATION` | `PRIVACY_SAFE_SERVICE_AREA` |

See also: [MAP-01-free-only-maps-android.md](MAP-01-free-only-maps-android.md).

## Visual contract

Canonical tokens:

- background `#FAFBF8`
- surface `#FFFFFF`
- primary UI green `#49B749`
- text `#263238` / `#667085`
- soft border `#E5EAE4`

Orange is scarce brand/accent only. Red/coral is semantic only. Sage/Teal V2 must not return. Logo artwork colors stay unchanged.

`checkLeoVerUiContract` is the static gate. Migrated screens must not re-import legacy production UI packages.

## Publicar

ActiveContext is UX context only — never security authority. Backend RPCs remain authorization.

Person (perfil personal): Publicación, Reel, Historia. No campaign/donation by default. Lost/Found/Adoption/Foster stay on Sumate.

## Foster

Visible name: **Tránsitos**. Copy: “Alojamientos temporales de mascotas que están a tu cuidado.”

Direct placement creates `foster_placements` against an existing canonical pet. VitaCora is not copied. Access status is derived from `vitacora_access_grants`.

## Daycare

`DAYCARE_RESERVATIONS` ≠ `DAYCARE_GUESTS`. Guests are `daycare_stays.status = IN_STAY` only.
