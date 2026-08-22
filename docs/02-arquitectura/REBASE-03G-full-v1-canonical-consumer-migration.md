# REBASE-03G — Full V1 canonical consumer migration

Pre-QA closure. No commit. No production. Continues REBASE-03F / 03F.1 WIP.

## Starting state

- HEAD (committed): `679fd3d63197b7318e17c0b48351e0719563c212`
- Staging: `tobqbddfcyitwgbkthhy`
- Legacy: `wystsapjfpdtoprlmizz` (untouched)
- Migration head: **1023**
- 03F.1: AccountType/AppMode authority neutralized; Health read = NONE / schema blocked

## Work performed

1. Forward migration **1024** (`20260816023000_1024_consumer_read_write.sql`)
2. Android Health read via `canon_get_pet_health`
3. Holder end via `canon_end_pet_responsibility`
4. Person self-update / public person via canonical RPCs (no `update_my_profile` / `users` table)
5. Canonical consumer repos: Lost/Found, Adoption, Feed, Chat, Organizations, Location catalog
6. Marketplace cart/orders removed from V1 hub (OUT_OF_V1)
7. Staging dry-run + apply of 1024 only
8. Staging smoke `canon_03g_smoke_suite` (write/read Health, deny stranger, LF + adoption create)

## Schema migrations added

| Logical | File | Purpose |
| --- | --- | --- |
| 1024 | `infra/supabase-canonical/supabase/migrations/20260816023000_1024_consumer_read_write.sql` | Health read, holder end, person update/public, VitaCora moments list, LF/adoption/social/messaging/org/provider/booking/event/donation/foster consumer RPCs |

1000–1023 not edited.

## Consumer mappings

| Feature | Canonical path |
| --- | --- |
| Health read | `canon_get_pet_health` |
| Health write | 1023 `canon_record_*` |
| Holder revoke | `canon_end_pet_responsibility` |
| Profile update | `canon_update_my_person` |
| Lost/Found | `canon_create_lost_found` / `canon_list_lost_found` / `canon_resolve_lost_found` |
| Adoption | `canon_create_adoption` / `canon_list_adoptions` / `canon_set_adoption_status` |
| Social feed | `canon_create_social_post` / `canon_list_social_feed` |
| Messaging | `canon_start_conversation` / `canon_list_*` / `canon_send_message` |
| Organizations | `canon_create_organization` / `canon_list_my_organizations` |
| Location | SELECT `location_nodes` (already granted) |
| VitaCora | existing 1020 RPCs + `canon_list_vitacora_moments` |
| Public web | `canon_public_pet` / `canon_public_lost_found` / `canon_public_adoption` |

## Legacy / mock removals

- `AppMode` already removed in 03F.1
- AccountType permission overloads ignore type (03F.1)
- Profile no longer calls `update_my_profile` / `complete_profile_onboarding` / `users`
- Unauthorized Health table SELECT stays gone
- M25 cart/orders buttons removed from hub

## Remaining operational UIs

Full M11/M15/M16 shelter-foster ops, M12/M28 veterinary portal, M17/M18/M21/M22/M23 rich screens, M26/M27 still use in-memory module stores on Staging. RPCs for list/create exist where the canonical table exists; complete UI mapping is **NOT_IMPLEMENTED_PRODUCT** for those portals. Do not treat their demo rows as Staging data.

## Staging results

- Dry-run: only `20260816023000_1024_consumer_read_write.sql`
- Apply: PASS (Docker catalog cache warning only)
- Smoke: Health write → read allergy visible; stranger FORBIDDEN; LF + adoption create. Cleanup stopped on audit FK (QA persons may remain).

## Tests

- Android focused: PASS
- `compileLocalDebugKotlin`: PASS
- Web / KMP: see gate report

## V1 module matrix (D01 v1.3)

| MODULE | CURRENT_FEATURE | CANONICAL_SCHEMA | ANDROID | WEB | KMP | REAL_STAGING | LEGACY_RUNTIME | MOCK_RUNTIME | STATUS | NOTES |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M00 | Foundation | YES | YES | YES | YES | YES | NO | NO | MIGRATED | |
| M01 | Auth / PERSON | YES | YES | public only | shared config | YES | NO | NO | MIGRATED | |
| M02 | Profile / age | YES | YES | N/A | models | YES | NO | NO | MIGRATED | AccountType not authority |
| M03 | Organizations | YES | list/create | N/A | OrganizationId | YES | NO | NO | MIGRATED | Branches/invites NOT_IMPLEMENTED_PRODUCT |
| M04 | Moderation | YES foundations | screens exist | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | Full queue not wired |
| M05 | Media | YES | pet/person avatar | N/A | N/A | YES | NO | NO | MIGRATED | Old asset DELETE deferred |
| M06 | Notifications | YES foundations | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | |
| M07 | Observability | YES foundations | admin extras | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | |
| M08 | Pets / holders | YES | YES | public pet | Pet models | YES | NO | NO | MIGRATED | |
| M09 | Adoption | YES | list/create/close | public | N/A | YES | NO | NO | MIGRATED | Interviews/follow-up NOT_IMPLEMENTED_PRODUCT |
| M10 | Foster SQL | YES | upsert/list RPC | N/A | N/A | PARTIAL RPC | NO | UI store | NOT_IMPLEMENTED_PRODUCT | Full M10 UI mapping remaining |
| M11 | Shelter ops | tables/legacy | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | |
| M12 | Vet directory | 1016 | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | |
| M13 | Sightings | tables | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | |
| M14 | VitaCora | YES | projection + RPCs | N/A | N/A | YES | NO | NO | MIGRATED | Credentials/verification NOT_IMPLEMENTED_PRODUCT |
| M15 | Foster ops UI | YES | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | |
| M16 | Shelter public | org profile | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | |
| M17 | Donations | YES + list/create RPC | screens | N/A | N/A | RPC only | NO | YES UI | NOT_IMPLEMENTED_PRODUCT | 0% commission unchanged |
| M18 | Events | YES + list/create RPC | screens | N/A | N/A | RPC only | NO | YES UI | NOT_IMPLEMENTED_PRODUCT | |
| M19 | Social | YES | feed wired | N/A | N/A | YES | NO | NO | MIGRATED | Stories/reels if unused = N/A |
| M20 | Messaging | YES | chat wired | N/A | N/A | YES | NO | NO | MIGRATED | |
| M21 | Reputation | tables | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | |
| M22 | Providers | YES + list RPC | screens | N/A | N/A | RPC only | NO | YES UI | NOT_IMPLEMENTED_PRODUCT | |
| M23 | Bookings | YES + list RPC | screens | N/A | N/A | RPC only | NO | YES UI | NOT_IMPLEMENTED_PRODUCT | |
| M24 | Payments | N/A | N/A | N/A | N/A | N/A | NO | NO | OUT_OF_V1 | |
| M25 | Catalog | N/A transactional | catalog UI; cart hidden | N/A | N/A | NO | NO | catalog mock | OUT_OF_V1 | Checkout/cart/orders out of V1 |
| M26 | AI | N/A | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | Advanced later |
| M27 | Integrations | N/A | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | Outside pilot |
| M28 | Vet portal | 1016 + record RPC | screens | N/A | N/A | NO | NO | YES | NOT_IMPLEMENTED_PRODUCT | Pilot-minimum not fully wired |
| M29 | Brand Studio | 1019 support | none | N/A | N/A | N/A | NO | NO | NOT_IMPLEMENTED_PRODUCT | |
| Lost/Found | Alerts | YES | YES | public | N/A | YES | NO | NO | MIGRATED | Precise geo protected |

## APK

- Path: `apk/LeoVer-CANONICAL-STAGING-FULL-QA.apk`
- Size: `40369796`
- SHA256: `59BA81839004230E7632EBF3E5F820EBFC5126E7936CE6F11C7B5D62DA36D428`
- Variant: `localDebug`
- Environment: STAGING

## Physical QA

Checklist: `REBASE-CANONICAL-FULL-PHYSICAL-QA-CHECKLIST.md`  
`PHYSICAL_QA_RESULT = PENDING_USER`

## AUTH BLOCKER FOUND DURING PHYSICAL QA

Physical QA of the 03G APK discovered a blocking Auth issue before the rest of the checklist:

- Signup confirmation email arrived without a visible 6-digit code.
- Tapping the confirmation link redirected to a null/anull destination.
- A real user could not reliably complete account creation.

This does **not** change the 03G schema/RPC verdict above. 03G consumer migration results stay as recorded.

**RESOLVED BY AUTH-01** (Android/Web consumer + Auth redirect hardening + repo email templates). Hosted Staging Auth templates / Site URL / Redirect URL allowlist still require the dashboard paste documented in `AUTH-01-canonical-email-verification-and-deeplink.md`. Confirm Email remains enabled.

**AUTH-02 / ONBOARDING-01 (follow-up, does not undo AUTH-01):** Physical QA after AUTH-01 also found (A) auth user can exist even when the email-link redirect visually failed, (B) onboarding asked username/display name again although PERSON already owned the signup username, (C) Province selector was empty. App/onboarding/geography read+save is documented in `AUTH-02-onboarding-profile-geography-fix.md`. Hosted Dashboard URL/template paste remains a separate AUTH-01 task.
