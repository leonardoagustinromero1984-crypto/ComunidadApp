# Location and Lost / Found alerts

Brand: **LeoVer**. Android block ANDROID-LOCATION-ALERTS-01. Web is not implemented here.

## Tutorial persistence

Canonical source of truth is `tutorial_progress` key `onb02_flow` version `1` via `canon_list_tutorial_progress` / `canon_upsert_tutorial_progress`.

A complete PERSON plus empty local DataStore is **not** enough to skip Tutorial LeoVer. That heuristic skipped new Google users and clean STAGING installs.

- New user: Completar perfil → Tutorial → selector → Home
- Existing completed FLOW: login / reinstall → Home
- Local prefs are a cache only

## Location

Foreground only: `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`. No background location.

Consent is versioned in `legal_documents` (`CONTEXTUAL` / `LOCATION_TREATMENT` / `1`) and `legal_consent_events`. Términos acceptance is not an Android permission.

Approximate Android grants are enough for nearby UX. Exact incident pins are chosen on the map.

“Usar mi ubicación” pide COARSE/FINE (nunca background), lee Fused (`HIGH_ACCURACY` si hay precisa, si no `BALANCED`), centra el mapa y guarda lat/lng. Si el permiso se niega o el dispositivo no entrega punto, el pin manual sigue disponible. Create stages 1094/1095: `LF-CREATE-AUTH/KIND/FORBIDDEN/LOCATION/IDENTITY/ALERT`. Location uses `_canon_geo_point` (`extensions.ST_MakePoint`). Match y fanout no abortan la persistencia atómica de pet + VitaCora + alert. Base location canónica: `persons.base_location` / `organizations.base_location` (misma coordenada que el domicilio).

## Responder base location

Stored as `geography(Point,4326)` on `persons.base_location` and `organizations.base_location`.

Eligible nearest responders:

- Independent: `person_capabilities.RESCUER` + `active` + `verification_status = VERIFIED` + `receive_nearby_cases` + valid base point
- Organization: capability `SHELTER` or `NGO` + org `ACTIVE` + `VERIFIED` + `receive_nearby_cases` + valid base point

Exact base coordinates are not public. Profiles keep locality / zone.

## Alert model

Reuses `lost_found_alerts`.

| Field | Notes |
|---|---|
| `kind` | `LOST` / `FOUND` |
| `status` | `OPEN` / `CLAIMED` / `RESOLVED` / `CANCELLED` (`HIDDEN` retained) |
| `pet_id` | required for LOST when known; set on FOUND at publish (provisional pet) |
| `precise_location` | exact incident point, server-only |
| `claimed_by` / `claimed_at` | FOUND claim |

Public list RPCs return locality / zone, never lat/lng.

`canon_get_lost_found_exact` is limited to reporter, assigned responder, or staff.

## LOST

Prefers an existing LeoVer pet. If missing, create a minimal pet + VitaCora first, then publish with `pet_id`. Do not create a second VitaCora.

## FOUND

Publishing FOUND is atomic and idempotent. It creates, in one transaction:

1. One `lost_found_alerts` row (`kind = FOUND`)
2. One provisional pet (`origin_kind = FOUND_CASE`)
3. One `vitacora_profiles` row
4. `alert.pet_id` linked to that pet
5. Matching V1 against active LOST (species hard; breed/sex/size/age/geo/time scoring). No color. No visual IA.
6. First wave of nearest-10 responder alerts. Later waves: **pg_cron** every 5 minutes via `canon_tick_lost_found_waves`. Not `canon_list_lost_found`.

Required to publish: animal photo + existing basic Pet/VitaCora fields (species, sex, size, breed_id if known, optional name, optional estimated age). Unknown uses `UNKNOWN` / `ESTIMATED`. No invented attributes.

The photo is the provisional pet avatar, the FOUND image, the image sent to responders, and the future visual-matching input. One media asset is reused.

The finder is **not** OWNER. `current_custodian_*` is the finder. They receive `AUTHORIZED` care so they can manage the case while they have the animal.

A possible owner may tap “Podría ser mi mascota” before any responder claims. That does not change ownership, merge VitaCoras, close the case, or cancel other candidates.

Who validates: the person who physically has the animal (`current_custodian`). If still the finder, the finder confirms. After a responder claim, the responder confirms.

If the finder confirms the owner before a responder claim: stop all responder waves, cancel pending alerts, confirm the match, start reunification, and relink the provisional FOUND pet/VitaCora onto the existing LOST pet (archive the provisional pet, move moments/photos, keep audit). One active pet / VitaCora remains.

## Nearest 10 and waves

`_canon_fanout_lost_found_recipients` ranks eligible responders with PostGIS `ST_Distance`, `LIMIT 10`, persists `lost_found_alert_recipients` (unique case + person / org), then notifies via existing M06 (`m06_emit_domain_notification`) when present.

Scheduler: `pg_cron` job `leover-lost-found-waves` (`* * * * *`, every 1 minute since 1090) calls `canon_tick_lost_found_waves()` as `security definer` **without** `auth.uid()`. Eligible cases: `status = OPEN`, `fanout_stopped_at` null, `fanout_exhausted_at` null, `next_wave_at <= now()`. A tick with `next_wave_at > now()` does nothing. Each successful wave adds the next 10 never-notified eligible responders (RESCUER / SHELTER / NGO, never FOSTER), emits M06, sets `next_wave_at + 15 minutes`. Max extra delay after the 15-minute guard is ~1 minute (worst ~16). If zero new recipients, `fanout_exhausted_at` is set (no infinite loop).

`canon_list_lost_found` is read-only. It does **not** advance waves.

When claimed or owner confirmed: `fanout_stopped_at`, recipients `NOTIFIED`/`VIEWED` → `CANCELLED_CASE_CLAIMED`. A late cron tick sees stopped/non-OPEN and emits nothing.

Claim is `CLAIMED` (accepted to attend), not physical care. `canon_mark_lost_found_in_care` → `IN_CARE`. Resolution reasons: `SAFE_IN_CARE`, `REUNITED_WITH_OWNER`, `MOVED_TO_ADOPTION`.

Matching V1 (species required; others scoring only). Notify only if score ≥ 0.55; persist/notify at most 15, ordered score DESC, distance ASC NULLS LAST, incident_at DESC. Same-species alone (0.40) does not notify.

Claim arbitration: `canon_register_lost_found_claim_attempt` starts a 2-second window without assigning custody. `canon_claim_lost_found` waits the remaining time (`pg_sleep`, holds the session ~2s), then locks and picks nearest (`distance ASC, created_at ASC, person_id ASC`). Only then CLAIMED / stop fanout. Loser: `ALERT_CLAIM_NOT_NEAREST`.

Matching V1 (species required; others scoring only):

| Signal | Rule |
|---|---|
| Species | Hard match |
| Breed (`pets.breed_id`) | +0.12 if both known and equal; unknown = neutral |
| Sex | +0.20 if both known and equal; mismatch/unknown = neutral (not excluded) |
| Size | +0.15 if both known and equal; unknown = neutral |
| Age (`PetBirth` / `_canon_pet_age_months`) | +0.12 if \|Δ\| ≤ 6 months; +0.06 if ≤ 18; else / unknown = neutral |
| Geo | +0.15 if `ST_Distance` ≤ 25000 m |
| Time | +0.10 if \|Δ incident\| ≤ 14 days |

LOST owners get inbox `lost_found.match.candidate`. Owner assert notifies current custodian and opens a PERSON conversation reused across custody changes. Owner can reject (`REJECTED_BY_OWNER`). Custodian can reject (`REJECTED_BY_CUSTODIAN`) without closing cases.

## Claim

`canon_claim_lost_found` locks the row. If status is not `OPEN`, raises `ALERT_ALREADY_CLAIMED` (human copy: “Este caso ya fue tomado por otro colaborador.”).

Claim does **not** create a pet or a VitaCora. Those already exist from publish. Claim only:

1. `OPEN` → `CLAIMED`
2. Transfer operational custody (`current_custodian_*` → responder)
3. Grant least-privilege `AUTHORIZED` (not `OWNER`) to the responder
4. Cancel every pending responder alert and stop future waves

Effectively simultaneous acceptances: `lost_found_claim_attempts` plus nearest-distance tie-break. The closest registered responder wins. One custodian. No second claim.

Technical `pets.created_by_user_id` remains the finder (historical creator). Neither finder nor claimer becomes OWNER.

## Notifications

Reuse M06 inbox + push planning. Dedup key `lost_found:{alert_id}:{user_id}`.

- FOUND: “Animal encontrado cerca de tu zona” / Ver caso
- LOST: “Mascota perdida cerca de tu zona” / Ver alerta

## Web contracts (not implemented)

Same RPCs, same ACL, same DS2 tokens. Web must not query `precise_location` broadly.
