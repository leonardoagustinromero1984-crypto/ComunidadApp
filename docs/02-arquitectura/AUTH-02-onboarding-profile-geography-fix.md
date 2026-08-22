# AUTH-02 / ONBOARDING-01 — Signup state, PERSON reuse, geography

Canonical Staging only: `tobqbddfcyitwgbkthhy`  
Never touch legacy `wystsapjfpdtoprlmizz` or production.

AUTH-01 (email OTP + Android callback `com.comunidapp.app://login-callback`) is **not** undone here. Hosted Dashboard Site URL / Redirect URLs / email templates remain a separate pending task.

## Root causes

| QA finding | Cause |
| --- | --- |
| A. Auth user exists after a failed-looking confirmation redirect | GoTrue confirms on `ConfirmationURL` **before** `redirect_to`. A `null`/`anull` browser destination can happen after `email_confirmed_at` is already set. That is not bypassing confirmation. |
| B. Onboarding asks username + display name again; same username fails | Signup already stored username on `auth.users.raw_user_meta_data` and `handle_new_user()` created **one** PERSON. Android `getUser()` SELECT on `persons` had **RLS without GRANT** (1020 revoked ALL, 1023 granted pets/media only). JWT fallback had no username. `canon_is_username_available` treated the current PERSON as a conflict. |
| C. Provincia dropdown empty | Catalog SELECT already existed (`location_nodes`). Android never called `refresh()`, searched `PROVINCE` with `parentId = null` (canonical provinces parent `loc-ar`), and `searchLocalitiesInProvince` only walked MUNICIPALITY parents (canonical localities parent the province). Completeness was hardcoded `COMPLETED` whenever PERSON existed, and `canon_update_my_person` was not given `p_home_locality_id`. |

## Auth states (derived, not DB enums)

| State | Meaning | App access |
| --- | --- | --- |
| ACCOUNT_CREATED_EMAIL_PENDING | `auth.users` row exists; `email_confirmed_at` null | No. Show **Verificá tu correo**. |
| EMAIL_CONFIRMED | `email_confirmed_at` set | Not enough by itself. |
| AUTHENTICATED | Valid session + confirmed email | Session gate only. |
| PERSON_PROVISIONED | `handle_new_user()` PERSON (`user_id` PK) | May precede confirmation. Idempotent. |
| ONBOARDING_INCOMPLETE | PERSON username present, `home_locality_id` missing | Profile setup. Ask **only missing** fields. |
| ONBOARDING_COMPLETE | username + `home_locality_id` | Home. Second login skips onboarding. |

`auth.users` existence is never treated as “logged into LeoVer”.

## Username

Canonical source: `persons.username` (unique). Signup metadata + `handle_new_user()` persist it once.

Onboarding: if PERSON/JWT already has username → read-only `@usuario`. No availability check against an unchanged self username.

`canon_is_username_available` (1025): same normalized username owned by `auth.uid()` → available. Unique index unchanged. Other PERSON → unavailable.

## Geography

| Item | Value |
| --- | --- |
| Tables | `public.location_nodes` |
| Kinds | COUNTRY / PROVINCE / LOCALITY |
| Argentina | `loc-ar` (seed 1021) |
| Provinces | 24, parent `loc-ar` |
| Seed localities | 1021 baseline = 14; 1026 GEOREF_AR completes Argentina |
| Province read | `DIRECT_CATALOG_SELECT` (`location_nodes` kind=PROVINCE) |
| Locality read | `DIRECT_CATALOG_SELECT` (kind=LOCALITY, parent=province id) |
| Profile location | `persons.home_locality_id` (RLS self-select; not a public catalog leak) |

No hardcoded Argentina province list as authority. No free-text province/locality. No legacy backend.

## Migration 1025

`20260816140000_1025_person_self_select_and_username_self.sql`

- `GRANT SELECT ON public.persons TO authenticated` (policy `persons_self_select` already existed)
- Replace `canon_is_username_available` with self-exclusion
- **No** geography redesign. Catalog GRANT/RLS already in 1020.

## Android

- Sign out any stale session before signup; session user id must match PERSON being onboarded.
- Signup with user but no session → verification screen, not Home.
- Unconfirmed login → sign out leftover session, **Verificá tu correo** / resend.
- Already-registered email → Login / Forgot Password copy; no user enumeration; no auto OTP.
- `CanonicalLocationCatalogRepository.refresh()` from the picker; LOADING / LOADED / EMPTY / ERROR.
- Onboarding save: `canon_update_my_person(p_display_name, p_home_locality_id)`; username untouched; reload PERSON.
- Social visibility in onboarding is **PUBLIC / PRIVATE** only. “Solo amigos” / FRIENDS is not selectable. Canonical `privacy_state` was already `PRIVATE | PUBLIC_LIMITED` (1002); no 1026. See `docs/01-producto/D-perfil-visibilidad-public-private.md`.

## Staging smoke / tests / APK

| Check | Result |
| --- | --- |
| Argentina / provinces / localities on Staging | YES / 24 / 14 (`location_nodes` DIRECT_CATALOG_SELECT, anon+authenticated) |
| `persons` SELECT grant (authenticated) | YES after 1025; anon remains false |
| Current QA PERSON | username `velu`; `email_confirmed_at` populated; `home_locality_id` null (onboarding incomplete fixture). Not deleted. |
| Profile update via user JWT | not executed here (preserve QA fixture for physical onboarding) |
| Dry-run 1025 | only `20260816140000_1025_person_self_select_and_username_self.sql` |
| Apply | Staging `tobqbddfcyitwgbkthhy` only |
| Focused Android tests | PASS (onboarding, session policy, catalog hierarchy, username self) |
| `compileLocalDebugKotlin` | PASS |
| `assembleLocalDebug` | PASS |
| QA APK | `apk/LeoVer-CANONICAL-STAGING-FULL-QA.apk` SHA256 `408A41E597B23E95A730702E7A2086EA268EAEA127493952F5FCC6C891F6AF3F` |

Physical onboarding QA remains pending. Do not delete the current Staging QA user; it is the confirmed-PERSON fixture (`velu`, locality still missing).

No commit. No push. No emulator. No ADB.

## AUTH-03 geography consistency (does not rewrite AUTH-02 results)

Staging `location_nodes` seed 1021 is the approved catalog: Argentina `loc-ar`, **24** provinces, **14** localities. There is no unused complete-Argentina locality list in the repo. Maestro v1.2 / D01 v1.3 set market piloto as San Vicente + Almirante Brown and ámbito inicial Argentina; REBASE-03B says baseline seed ≠ admin-managed catalog after launch. Governance does **not** decide whether onboarding must list every Argentine locality or only piloto partidos.

| Group | Provinces |
| --- | --- |
| **With localities (4)** | Ciudad Autónoma de Buenos Aires (2), Buenos Aires (10), Córdoba (1), Santa Fe (1) |
| **Without localities (20)** | Catamarca, Chaco, Chubut, Corrientes, Entre Ríos, Formosa, Jujuy, La Pampa, La Rioja, Mendoza, Misiones, Neuquén, Río Negro, Salta, San Juan, San Luis, Santa Cruz, Santiago del Estero, Tierra del Fuego, Tucumán |

Onboarding/consumer picker (`showFullHierarchy = false`) lists only provinces that have at least one locality. Empty provinces remain in the catalog; they are not selectable. No 1026 geography migration: no unapplied approved catalog data exists.

`GEOGRAPHY_SCOPE_PRODUCT_DECISION_REQUIRED` was closed after AUTH-03:

- `GEOGRAPHY_SCOPE = ARGENTINA_COMPLETE`
- `PILOT_SCOPE = San Vicente + Almirante Brown` (launch / density / metrics only)
- `PILOT_IS_REGISTRATION_RESTRICTION = NO`

Canonical catalog is the official Georef Argentina v2.1 complete download (`infra/supabase-canonical/seeds/georef-ar/`), applied by forward migration **1026**. Onboarding lists all 24 jurisdictions. Runtime reads LeoVer `location_nodes`, not Georef.

AUTH-03 inspect (read-only, fixture not mutated): username `velu` still present; `home_locality_id` is now set; `privacy_state` = `PUBLIC_LIMITED`; no `FRIENDS_ONLY` rows. Disposable `canon_update_my_person` smoke persisted display name + locality on a separate `@leover.invalid` PERSON and deleted it.
