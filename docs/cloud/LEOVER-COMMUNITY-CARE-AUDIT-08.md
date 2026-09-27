# LeoVer Community Care Audit 08

Audit only. No new migration, no product fix, no emulator, no Maestro, no PROD, no `main`, no merge.

Source branch: `cloud-integration/leover-20260927` at `c387a44238ffd8452c684494f26dc8698e03c285`.

A module is **PARTIAL** when some layer exists and another required layer is unproven or unwired. File existence is not PASS. JVM **COVERED** is not a device pass and not a live STAGING pass.

## How this audit was checked

- Repository migrations through `20260927120000_1098_professional_private_care_read.sql`.
- Android wiring in `DataProvider` for the canonical staging URL (`useLegacyRemoteModules` is false when the URL is the staging project).
- Last recorded full JVM run in `docs/cloud/LEOVER-CLOUD-BASELINE-REPAIR-02.md`: app **3037/3037**, shared **390/390**. Five `@Test` methods were added after that run. This audit did not execute Gradle.
- Read-only STAGING session on project `tobqbddfcyitwgbkthhy` with QA users. No inserts, no verification submit, no claim, no apply.

## LOST

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_create_lost_found` requires an existing pet for LOST. List and create live in migrations 1094–1096. |
| Android | Staging uses `CanonicalLostFoundRepository`. `LostPetSelector` drops `FOUND_CASE`. Publish screen is on the lost/found route. |
| Automated | Shared `LostFoundPublishVerticalTest` plus selector contracts. Maestro `01_lost_owner.yaml` is not an executed result. |
| Live STAGING | `canon_list_lost_found` for QA01 returned **0** rows in `OPEN` / `CLAIMED` / `IN_CARE`. Publish was not called. |
| Maestro / physical | Flow 01 still required. Camera and GPS pin are physical. |
| Known bug | None proven on an empty alert set. |
| Missing contract | No test asserts the staging client sends `canon_create_lost_found` with a real pet id and a required photo. |

## FOUND

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | FOUND creates a provisional pet. Geo uses `extensions.ST_MakePoint` with `search_path = public, extensions` (1095). `location_label` and `_canon_lf_human_location` are in 1096. |
| Android | Create path sends `p_location_label`. Detail shows the human label, or the nearby fallback. |
| Automated | `REG-LF-004` / `REG-BUG-002` are source scans of the migration text. |
| Live STAGING | No FOUND row to read. |
| Maestro / physical | Flow 02 still required. Photo capture and the map pin are physical. |
| Known bug | None proven live. |
| Missing contract | No test publishes a FOUND and reads `location_label` back. |

## MATCHING

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `_canon_match_found_to_lost` (1096) sets `search_path = public, extensions`, calls `extensions.ST_Distance`, keeps score `>= 0.55`, cap 15, and emits `lost_found.match.candidate`. |
| Android | The detail screen loads candidates only for the custodian, then shows confirm/reject only when `assertedBy != null`. Automatic rows (`asserted_by` null, reason `BASIC_GEO_TIME`) stay hidden. |
| Automated | `CloudBaselineRepair02ContractTest.migration1096_matchFunctionSetsExtensionsSearchPath` scans the SQL file. It does not execute PostGIS. |
| Live STAGING | Zero active alerts and zero QA01 notifications. `_r3_after_1096.sql` was not run. |
| Maestro / physical | Flow 03 still required. OS push shade is physical. |
| Known bug | Automatic candidates are stored for the custodian and are not rendered. |
| Missing contract | No test asserts score, cap, or that an automatic candidate is visible to the owner. |

## OWNER MATCH

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | Match insert plus `_canon_emit_lf_event` to the LOST owner. Owner assert is a separate RPC, `canon_assert_found_might_be_mine`. |
| Android | "Podría ser mi mascota" is on the FOUND detail when the viewer has their own `ACTIVE` LOST. There is no owner list of automatic candidates. |
| Automated | No behavior test. Maestro `03_match_owner_notification.yaml` was not executed. |
| Live STAGING | `canon_list_my_notifications` for QA01 returned **0**. |
| Maestro / physical | Flow 03 and the notification shade. |
| Known bug | The owner path depends on the inbox. On canonical staging that inbox is the in-memory M06 mock (see NOTIFICATIONS). |
| Missing contract | No test that an owner session receives `lost_found.match.candidate`. |

## FINDER VALIDATION

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_assert_found_might_be_mine`, `canon_confirm_found_owner_match`, `canon_reject_found_might_be_mine`, `canon_reject_found_owner_match` (1087/1088). |
| Android | `LostFoundDetailScreen` calls those methods on `CanonicalLostFoundRepository`. The interface defaults used by the local mock fail assert/confirm and no-op reject and in-care. |
| Automated | No repository test of confirm/reject. Maestro `03b_owner_assert_finder.yaml` was not executed. |
| Live STAGING | No candidate row. RPCs were not called. |
| Maestro / physical | Flow 03b. |
| Known bug | Custodian actions ignore automatic candidates. |
| Missing contract | No test that confirm unifies the provisional FOUND pet with the LOST pet. |

## RESPONDER CLAIM

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_claim_lost_found` waits up to 2 seconds (`pg_sleep`) and picks the nearest attempt. Eligibility is a verified `RESCUER` with base location and `receive_nearby_cases`, or a verified `SHELTER` / `NGO` member. `FOSTER` is not in `_canon_alert_responder_eligible`. |
| Android | "Tomar caso" renders only when `can_claim` is true. |
| Automated | Claim copy is a source scan. Maestro `04_found_claim.yaml` and `04b_responder_exclusions.yaml` were not executed. |
| Live STAGING | Read-only: QA03 `eligible=true`, `has_base=true`, `receive=true`. QA06 foster `eligible=false`. QA01 `eligible=false`. No claim was submitted. |
| Maestro / physical | Flows 04 and 04b. Two-device nearest-wins remains physical. |
| Known bug | None in the eligibility read. |
| Missing contract | No automated test of the 2-second nearest-wins outcome. |
| Fixture drift | Seed text marks QA06 availability TRUE. Live `receive_nearby_cases` for that person is false. Eligibility would stay false because the capability is `FOSTER`. |

## VERIFICATION

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_request_leover_verification` is idempotent for `PENDING` (1096 unique index). States include `NOT_REQUESTED`, `PENDING`, `REQUIRES_CORRECTION`, `VERIFIED`, `REJECTED`, `SUSPENDED`. |
| Android | `LeoverVerificationRequestScreen` calls the request and list RPCs. `LeoverVerificationRequestViewModel.submitSuccess_setsPending_andRefreshKeepsBackendPending` keeps backend `PENDING` after refresh, using a fake repository. |
| Automated | That ViewModel test is the refresh contract. `OrganizationVerificationRulesTest` covers state labels. Maestro `05_verification.yaml` was not executed. |
| Live STAGING | Read-only list: QA08 `SHELTER:PENDING` (1 row). QA09 `SHELTER:PENDING` (1 row). This matches the consolidation note. No new request was sent, so the refresh round trip was not re-proven live. |
| Maestro / physical | Flow 05. Admin review (`OrganizationVerificationQueueScreen`) is not a Community user entry. |
| Known bug | None in the ViewModel fake. |
| Missing contract | No live test that a `NOT_REQUESTED` org becomes `PENDING` and stays `PENDING` after the screen is reopened. |

## COMMUNITY

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_list_community_nearby` uses radius 25000 m, limit 80, `ORDER BY meters`, and `LOCATION_INVALID` without coordinates. `hours_json` is selected as null. Weekly hours are stored by `canon_set_provider_weekly_hours`. |
| Android | `ComunidadViewModel` calls `CanonicalCommunityNearbyRepository` when near-me has coordinates. `CanonicalServiceRepository` writes weekly hours on the business form. `CommunityFormDirtyStateTest` keeps form fields across the map update. |
| Automated | Filter unit test plus the dirty-state ViewModel test. Maestro `06_community.yaml` was not executed. |
| Live STAGING | QA01 nearby at the Obelisco pin returned **18** rows (`VETERINARY` 6, `SHELTER` 5, `RESCUER` 3, `PROFESSIONAL` 3, `FOSTER` 1). `hours_json` was non-null on **0** rows. |
| Maestro / physical | Flow 06. A real device GPS fix is physical. The synthetic pin is not that proof. |
| Known bug | Nearby cards cannot show hours. The column is hard-coded null. |
| Missing contract | No test that a saved weekly schedule appears on a nearby card. |

## TRANSIT

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_request_foster_for_pet`, `canon_apply_to_foster_request`, `canon_select_foster_applicant`, `canon_list_open_foster_requests` (1093). |
| Android | `FosterCareRequestScreens` call those RPCs directly and are registered on `FOSTER_CARE_REQUEST`. `FosterRequestRepository` on canonical staging is still `MockFosterRequestRepository`. Home profile and placements use the canonical repositories. |
| Automated | `M10FosterCareManagementTest` exercises the in-memory repository. Maestro `08_transit.yaml` was not executed. |
| Live STAGING | `canon_list_open_foster_requests` for QA06 returned **0**. |
| Maestro / physical | Flow 08. |
| Known bug | The repository seam and the screen seam disagree. The screen is the live path. |
| Missing contract | No test that select-applicant calls `canon_select_foster_applicant` and keeps the same pet id. |

## ADOPTION

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_create_adoption` / `canon_list_adoptions` / `canon_set_adoption_status` publish listings. `canon_apply_adoption` and `canon_accept_adoption_application` pause the other applications. `m09_finalize_adoption` is replaced with `ADOPTION_USE_CANONICAL_TRANSFER` only when that function already exists. |
| Android | Staging `adoptionRepository` is `CanonicalAdoptionRepository` (list/create/status). `adoptionApplicationRepository`, interviews, documents, agreements, completion, and follow-up are **in-memory mocks** on the canonical URL. `RPC_APPLY_ADOPTION` and `RPC_ACCEPT_ADOPTION_APPLICATION` have no call site. The completion button calls `finalizeAdoption`, which the mock rejects locally. |
| Automated | `M09AdoptionCompletionTest.finalizeAdoption_readyProcess_refusesCanonicalTransfer` expects the mock error and no owner change. `CloudBaselineRepair02ContractTest` scans the 1093 source text. Maestro `07_adoption.yaml` was not executed. |
| Live STAGING | `canon_list_adoptions` returned **1** row, status `OPEN`, for QA01 and QA07. `canon_accept_adoption_application` with a nil id returned HTTP 400 `P0001` (function present, row absent). `m09_finalize_adoption` returned HTTP 404 `PGRST202` (function not exposed). Accept and apply were not called with a real id. |
| Maestro / physical | Flow 07. |
| Known bug | Publications are canonical and applications are a device-local store. Accepting an applicant on staging does not pause other applicants in the database. Finalize does not start `canon_initiate_care_transfer`. |
| Missing contract | No test fails when `DataProvider` selects `MockAdoptionApplicationRepository` for the canonical staging URL. |

Direct-finalize **COVERED** means the in-memory mock refuses. It does not mean staging executed `ADOPTION_USE_CANONICAL_TRANSFER`.

## TRANSFER

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_initiate_care_transfer`, `canon_accept_care_transfer`, `canon_reject_care_transfer`, `canon_list_care_transfers(uuid)`, `canon_list_incoming_care_transfers` (1071). |
| Android | Staging `petTransferRepository` is `CanonicalCareTransferRepository`. Incoming cards are on profile, my pets, and notifications. |
| Automated | Transfer copy and repository tests exist for the client mapping. No Maestro flow. |
| Live STAGING | `canon_list_incoming_care_transfers` for QA01 returned **0**. A call without `p_pet_id` returned `PGRST202`, which matches the required argument. No transfer was initiated. |
| Maestro / physical | Accept/reject on a device, with two accounts. |
| Known bug | The adoption completion screen does not open this flow after the local finalize refusal. |
| Missing contract | No test that a successful care-transfer accept closes the adoption publication. |

## PROFESSIONAL

**PARTIAL** for the hub. Private-history isolation is a **live read-only PASS**.

| Layer | State |
| --- | --- |
| Backend | `canon_list_professional_pet_cares` (1098) reads `veterinary_care_records` only with an active `HEALTH` grant for that clinic or that professional. Pet holders are not granted here. Table select stays revoked. |
| Android | `ProfessionalPatientsScreen` calls `RPC_LIST_PROFESSIONAL_PET_CARES` and `RPC_SEARCH_PROFESSIONAL_PATIENTS`. Hub route is `professional_hub`. `RPC_CREATE_VET_PATIENT` is called from the hub. |
| Automated | `ProfessionalPrivateHistoryContractTest` scans 1098 and the screen. It does not open a session. |
| Live STAGING | `scripts/qa/probe-professional-private-history.py` in this audit: QA11 read PASS (private marker present), QA16 denied (`P0001`, no leak), QA01 professional RPC denied, owner VitaCora moments/proposals no leak, direct `veterinary_care_records` select HTTP 403, patient search no leak. Probe result **PASS**. |
| Maestro / physical | Flows 09, 09c, 09d were not executed. Vet email invite remains an external inbox. |
| Known bug | `CloudBaselineRepair02ContractTest` still scans legacy `m28_list_pet_cares` and keeps `LIVE_STAGING_REQUIRED_PROFESSIONAL_ISOLATION = true`. The live rule is 1098. |
| Missing contract | Agenda and the email invite have no JVM proof. |

## VITACORA

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `canon_list_vitacora_moments`, `canon_list_vitacora_proposals`, `canon_decide_proposal`. Care history is migration 1072. Private veterinary rows are not part of the moments query (1098 contract). |
| Android | Pet detail "Compartir QR" navigates to `m14/pets/{petId}/share` (`M14PassportShareScreen`). Proposals go through `CanonicalVitacoraAccessRepository.decideProposal`. |
| Automated | `VitaCoraUserHistoryFilterTest` hides internal events. `CloudBaselineRepair02ContractTest.directQrRouteOpensPetShareScreen` checks the route string. Maestro `09b_vitacora_proposals.yaml` and `05c_qr_mora.yaml` were not executed. |
| Live STAGING | For the pet QA11 can read, QA01 moments and proposals returned HTTP 200 and did not contain the private-care markers. |
| Maestro / physical | Proposal accept/reject on device. QR **scan** is physical. Generating the share screen is not a scan pass. |
| Known bug | None proven on the share route. |
| Missing contract | No test that owner accept writes an accepted proposal and still hides `veterinary_care_records.summary`. |

## NOTIFICATIONS

**PARTIAL**

| Layer | State |
| --- | --- |
| Backend | `_canon_emit_lf_event` writes `public.notifications` and `notification_outbox` (`PUSH` / `PENDING`). It also tries `m06_emit_domain_notification` and swallows failure. `canon_list_my_notifications` lists the canonical rows. |
| Android | `SupabaseNotificationInboxRepository` falls back to `canon_list_my_notifications` when `m06_get_inbox` is empty or fails. `DataProvider.notificationInboxRepository` uses that class only for the legacy project. Canonical staging uses `m06Stage2ContractMocks.inbox`. Unread count and mark-read stay on `m06_*` even in the legacy class. |
| Automated | No test mentions `canon_list_my_notifications` or the staging inbox binding. |
| Live STAGING | QA01 list returned HTTP 200 and **0** rows. The RPC works. The staging app does not call it. |
| Maestro / physical | In-app list could be automated after the binding is fixed. The OS shade stays physical. |
| Known bug | Match notifications persisted by 1096 are invisible in the canonical staging app. |
| Missing contract | A test that the canonical staging URL selects a repository whose list method calls `canon_list_my_notifications`. |

## AUTOMATED COVERAGE

Last full Gradle result on record, before the five later tests:

| Suite | Recorded result | This audit |
| --- | --- | --- |
| `:app:testLocalDebugUnitTest` | 3037 / 3037 PASS | Not re-run. Method inventory is 3042 (`+5` `@Test`, none removed). |
| `:shared:testAndroidHostTest` | 390 / 390 PASS | Not re-run. `shared/` is unchanged since that run. |

JVM contracts that exist, and what they do not prove:

| Item | What passed earlier | What this audit adds |
| --- | --- | --- |
| Username login | `PersonUsernameLoginContractTest` on migration 1097 | Not called. Probe login used email/password. |
| Verification refresh | ViewModel fake stays `PENDING` | QA09 is already `PENDING`. No new submit. |
| Adoption direct finalize | In-memory mock returns `ADOPTION_USE_CANONICAL_TRANSFER` | Staging does not expose `m09_finalize_adoption`. |
| Professional isolation | Source scan of 1098 | Live read-only probe **PASS** (QA11 / QA16 / QA01 / table select 403). |
| Matching `search_path` | Source scan of 1096 | No live match. Alert set is empty. |
| Community dirty form | ViewModel test | Nearby list itself returned 18 live rows. |
| QR route | Route string opens `M14PassportShareScreen` | No scan. |

Maestro Community Care flows were not executed. `connectedAndroidTest` was not run.

## PHYSICAL QA REQUIRED

Do not mark these PASS:

- Camera capture for LOST, FOUND, pet photo, and profile photo
- Real GPS fix and the Community map pin
- OS push notification shade
- Physical QR scan
- External vet-invite email inbox
- Two-responder claim arbitration on devices
- Maestro flows `00` through `09d` on LeoVer-QA

## P0 GAPS

1. Canonical staging inbox is the M06 mock, so 1096 match notifications cannot appear in the app.
2. Adoption applications, interviews, documents, agreements, and completion are in-memory on canonical staging. `canon_apply_adoption` and `canon_accept_adoption_application` are not called.
3. Finalize refuses in the mock and the server function is absent. The screen does not start canonical care transfer.
4. Automatic match candidates are hidden (`assertedBy != null`), and the owner notification path is the mock inbox.
5. Live LOST/FOUND set is empty, so match, claim, finder validation, and `IN_CARE` have no staging row. Maestro was not run.

## P1 GAPS

- Nearby `hours_json` is always null while weekly hours exist on the provider profile.
- Verification review is an admin screen, not a Community user path.
- `FosterRequestRepository` remains a mock beside screens that call the RPCs directly.
- Mark-read and unread count have no canonical fallback.
- QA06 live `receive_nearby_cases` is false while the seed text says available.
- `CloudBaselineRepair02ContractTest` still treats professional isolation as live-required against the legacy M28 function.
- Google sign-in, stories/reels, and signed-URL access are outside this live pass.

## P2 GAPS

- Support tickets, agenda edges, and live rate limits.
- Full app suite not re-executed after the five tests added past 3037.
- Lint debt recorded in the baseline repair (Media3 opt-in and `values-es-rAR`) was not re-run.

## PRODUCT BUGS

1. `DataProvider.notificationInboxRepository` selects the in-memory inbox whenever the URL is canonical staging.
2. Adoption publication reads and writes are canonical, and adoption applications on that same URL are a memory store.
3. `LostFoundDetailScreen` drops match candidates whose `assertedBy` is null, which is every automatic match.
4. `canon_list_community_nearby` returns `hours_json` null for every kind.
5. Staging has no `m09_finalize_adoption`. The client mock and the server contract are not the same object.

## NEXT DEVELOPMENT BLOCK

Wire the canonical staging client to the RPCs that already exist:

1. Inbox list (and then read state) through `canon_list_my_notifications`.
2. Adoption apply and accept through `canon_apply_adoption` and `canon_accept_adoption_application`, including the pause of the other applicants.
3. Replace the local finalize refusal with navigation into `canon_initiate_care_transfer` for the accepted applicant.
4. Show the owner the automatic match candidate, and show the custodian that same row.

No new migration is required for those four bindings. Nearby hours can be a later change. After the bindings, seed one LOST and one FOUND on STAGING and run Maestro flows 01–04 before any UI Foundation work.

## APP TESTS

3042 test methods are on the branch. The last full execution on record is **3037/3037 PASS**. The later five tests were not run as a suite in this audit. This is not a 3042/3042 PASS.

## SHARED

**390/390** last recorded `:shared:testAndroidHostTest`. Sources unchanged since that run. Not re-executed here.

## MIGRATION MAX

**1098** in `infra/supabase-canonical/supabase/migrations`. Live evidence that 1098 is applied: `canon_list_professional_pet_cares` enforces the clinic grant and direct table select returns 403. `schema_migrations` was not queried.

## STAGING

READ ONLY. Professional isolation probe PASS. Verification, nearby, adoption list, responder eligibility, foster open-request list, incoming transfers, and notification list were reads. No writes.

## PROD

NO

## MAIN

NOT MODIFIED

## MERGE

NO
