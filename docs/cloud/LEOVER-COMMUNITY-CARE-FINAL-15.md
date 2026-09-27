# LeoVer Community Care final consolidation 15

Audit and documentation only. No new product feature, no new migration, no STAGING mutation, no PROD, no `main`, no merge, no emulator, no Maestro.

Source branch: `cursor/community-care-end-transit-14c` at `3dd0c06c88e2633c3e5cdca3a10d22820f52bfb3`.

Integration base: `cloud-integration/leover-20260927` at `c387a44238ffd8452c684494f26dc8698e03c285`.

STAGING project: `tobqbddfcyitwgbkthhy`.

A module is **COVERED** only when a live STAGING read or an already completed live fixture confirms the behavior. File existence is not a pass. JVM coverage is not a device pass. Maestro was not executed.

## 1. Git chain

PASS.

`cloud-integration/leover-20260927` is the merge base. The source branch is 18 commits ahead and 0 behind. There are no merge commits in the range, no duplicate commit subjects, and no duplicate patch ids. No commit subject in the range already exists on the integration base. History was not rewritten.

| Commit | Work |
| --- | --- |
| `f0de616` | Community Care audit 08 |
| `ecffb0a` | Audit 08 correction after the code pass |
| `6fd77b0` | Canonical inbox and lost/found list wiring |
| `d925cff` | QA lost/found media fixture helper |
| `5353028` | Live lost/found pair probe |
| `c7bfe06` | One-way owner reunification validation |
| `a87ed75` | Responder fanout PostGIS, migration 1099 |
| `e2c1910` | Live responder care probe |
| `b2bd1f1` | Pin the responder FOUND so later runs only read |
| `192835c` | Canonical adoption application contract, migration 1100 |
| `b9c1dc2` | Live Luna adoption probe |
| `4ec1d10` | Care-transfer accepted-retry authorization, migration 1101 |
| `c262cb4` | Foster transit read contract, migration 1102 |
| `f35bdd6` | Executable SQL lock for the 1102 custody assertions |
| `5c49937` | Android wiring to the live 1102 contract |
| `dfc5aaf` | Selected foster application recovery and repository types |
| `ec11347` | End-transit action, migration 1103 |
| `3dd0c06` | End-transit migration path from the Gradle working directory |

The diff against the integration base is 48 files. Every change is Community Care audit, Android wiring, migrations 1099–1103, live probes, or their contracts. No unrelated product area was added. Migrations in the range were added once. No migration file was modified after its add commit.

## 2. Migration audit

PASS.

Canonical directory: `infra/supabase-canonical/supabase/migrations`.

| Check | Result |
| --- | --- |
| Files | 104 |
| Version range | 1000–1103, no gaps, no duplicate versions |
| Filename order | Same as version order |
| Timestamp order | Strictly increasing |
| Local max | `20260927220000` / 1103 `foster_transit_completion` |
| STAGING max | `20260927220000` |
| STAGING row count | 104 |
| Local vs STAGING versions | Same sequence |

| Version | File | STAGING |
| --- | --- | --- |
| 1099 | `20260927180000_1099_responder_fanout_postgis.sql` | Applied |
| 1100 | `20260927190000_1100_adoption_canonical_contract.sql` | Applied |
| 1101 | `20260927200000_1101_care_transfer_accept_authorization.sql` | Applied |
| 1102 | `20260927210000_1102_foster_transit_read_contract.sql` | Applied |
| 1103 | `20260927220000_1103_foster_transit_completion.sql` | Applied |

Remote history was read with `select version from supabase_migrations.schema_migrations`. No migration was applied from this block. PROD was not contacted.

## 3. Core module matrix

| Module | Status | Evidence |
| --- | --- | --- |
| PERSON username login | COVERED | Migration 1097 returns email only after password verification. Every probe in this block logged in with usernames. |
| Verification | PARTIAL | Read-only list today: QA07 `SHELTER:VERIFIED`, QA08 `SHELTER:PENDING`, QA09 `SHELTER:PENDING`. No new request was sent. Admin review is still not `canon_review_leover_verification`. |
| LOST | COVERED | Mora LOST `232c473b-cbf3-485f-8f89-cb27132c8f89` is absent from the active list. The original pet remains the case pet. Camera and GPS pin stay physical. |
| FOUND | COVERED | Reunified provisional FOUND is archived. Responder FOUND `3e238403-174b-4ce8-82fd-58daa4faa636` is `IN_CARE`. Photo capture stays physical. |
| Automatic matching | COVERED | The pinned automatic candidate is `REJECTED` after owner confirmation. Score and cap were proven by the earlier live pair. The separate M13 screen is still a mock. |
| Owner assertion | COVERED | Owner candidate `97b5ae80-eaa0-45e2-9457-39c43c15de7f` is `ACCEPTED`. `asserted_by` is QA01. |
| Finder / custodian validation | COVERED | Custodian confirmation already resolved the pair. This block only read it. Confirm still requires the provisional pet's current custodian. |
| Reunification | COVERED | Both alerts are out of the active list. One active Mora remains. |
| Responder fanout | COVERED | QA03 has the case notification. QA06 has none. QA06 `eligible=false`. Migration 1099 is applied. |
| Responder claim | COVERED | Claimed by QA03. QA03 is not OWNER. This block used `--read-only` and did not claim again. |
| IN_CARE | COVERED | Status `IN_CARE`. Custodian is QA03. Pet stays the same provisional FOUND pet and VitaCora. |
| Notifications | PARTIAL | The visible list is `canon_list_my_notifications`. Mark-read has no canonical RPC and fails closed. OS shade is physical. |
| Community nearby | PARTIAL | Read-only nearby at the QA01 pin returned 18 rows. `hours_json` was non-null on 0 rows. |
| Adoption | COVERED | Luna publication `CLOSED`. QA14 application `COMPLETED`. QA15 application `CLOSED`. Direct finalize was not called. |
| Care transfer | COVERED | Luna transfer `ACCEPTED`. QA14 is PERSON OWNER. Unrelated accept calls returned `FORBIDDEN` before any fixture change. QA14 retry was idempotent. |
| Professional private history | COVERED | QA11 reads the private history. QA16 and the owner do not. Direct table select is 403. |
| VitaCora | PARTIAL | Reunification, adoption, and transit keep the same pet identity. Transit start and end still do not write VitaCora moments. |
| Foster / transit request | COVERED | Bruno request `8c293651-83f6-43bc-b677-f34bef9e021f` is `COMPLETED`. This block did not create another request. |
| Foster application | COVERED | QA06 application `b0537915-f73b-45b8-addd-9246bd9fd542` remains `SELECTED` on the completed request. |
| Foster selection | COVERED | Placement `07d69d92-7d42-492d-a78d-48d020e3de59` is `CLOSED`. |
| Active transit | COVERED | `canon_get_active_foster_transit` is `NONE` for the responsible org and for QA06. |
| End transit | COVERED | QA06 is `AUTHORIZED` / `ENDED`. The organization is `RESPONSIBLE` / `ACTIVE`. QA06 is not OWNER. The completion RPC was not called again. |

## 4. VitaCora reunification invariant

COVERED.

`canon_confirm_found_owner_match` in migration 1087 is still the only definition of confirm. Migrations 1099–1103 do not replace it.

On confirm, the current behavior is:

- The LOST pet stays the survivor (`pet_id` in the result).
- Hallazgo `vitacora_moments` are updated to that pet. They are not deleted.
- The FOUND alert is relinked to the LOST pet and both alerts become `RESOLVED`.
- The provisional FOUND pet is set to `ARCHIVED`.
- Confirm does not insert another pet or another `vitacora_profiles` row.
- Only the provisional pet's current custodian can confirm.

`ReunificationVitaCoraInvariantTest` locks that SQL. `REG-LF-006` records it in the regression catalog and matrix.

Live read of the Mora pair in this block: provisional pet `5f487c6f-f7b5-4b68-9419-d018b89195f8` is `ARCHIVED` / `FOUND_CASE`. Mora `f58305a1-0b83-40ed-bbc3-fdcdc0120fb5` is `ACTIVE`. QA01 sees one pet named `QA - Mora`, and that row is the original pet. The hallazgo photo is on Mora and is not left on the provisional moment list. Read checks passed with no failure.

Residue, not a second active identity: confirm does not delete the archived pet's `vitacora_profiles` row. `canon_list_vitacora_moments` still synthesizes a `CARE_CREATED` line from any pet row, including that archived pet. The live probe expects that synthetic line. It is recorded as P2. It is not a second active pet and not a reason for a new migration.

## 5. Live fixtures

READ ONLY. No fixture was created or updated by a successful product write.

### Reunification

| Item | Observed |
| --- | --- |
| Mora pet | `f58305a1-0b83-40ed-bbc3-fdcdc0120fb5`, `ACTIVE` |
| Pets named `QA - Mora` visible to QA01 | 1, the original pet |
| LOST / FOUND | Not in the active list |
| Owner candidate | `ACCEPTED` |
| Automatic candidate | `REJECTED` |
| Provisional pet | `ARCHIVED` |

### Responder

| Item | Observed |
| --- | --- |
| FOUND | `3e238403-174b-4ce8-82fd-58daa4faa636` |
| Status | `IN_CARE` |
| Claimed by | QA03 |
| QA03 owner | NO |
| QA02 owner | NO |
| QA06 notifications for the case | 0 |
| Mode | `READ` |

### Adoption

| Item | Observed |
| --- | --- |
| Luna pet | `73e9997e-27fa-4265-946f-36001f27f815` |
| Publication | `CLOSED` |
| QA14 application | `COMPLETED` |
| QA15 application | `CLOSED` |
| Transfer | `7dcae39a-b8a2-4d10-9fc0-ea16d287a830`, `ACCEPTED` |
| Holder | PERSON OWNER, QA14 |
| Writes | none |

### Transit

| Item | Observed |
| --- | --- |
| Bruno pet | `9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830` |
| Request | `COMPLETED` |
| Application | `SELECTED` |
| Placement | `CLOSED` |
| QA06 | `AUTHORIZED` / `ENDED`, not OWNER |
| Organization | `RESPONSIBLE` / `ACTIVE` |
| Active transit | `NONE` |
| Pet / VitaCora | preserved |
| Mode | `READ_ONLY` |
| End transit RPC | `NOT_RUN` |

## 6. Read-only live probes

Each probe used QA user sessions on `tobqbddfcyitwgbkthhy`. None used `service_role`.

| Probe | Mode | Result |
| --- | --- | --- |
| `probe-professional-private-history.py` | Read | PASS. QA11 private history yes. QA16 denied. Owner leak no. Direct select 403. |
| `probe-community-care-live-lost-found.py` | Confirmed-pair read only. `main()` was not entered, so the create path did not run. | PASS. `READ CHECKS: PASS`. |
| `probe-community-care-live-responder-path.py --read-only` | `READ` because the pin is already `IN_CARE` | PASS. |
| `probe-community-care-live-adoption.py --read-only` | `MODE READ`. Direct finalize not called. | PASS. |
| `probe-community-care-care-transfer-security.py` | Terminal idempotent accept. Unauthorized calls denied. Snapshot unchanged. | PASS. |
| `probe-community-care-live-transit.py` | Completed fixture. `--end` was not set. `END_TRANSIT NOT_RUN`. QA06 transfer attempt denied. | PASS. |
| Community nearby | Extra read of `canon_list_community_nearby` | 18 rows. `hours_json` non-null: 0. |
| Verification list | Extra read of `canon_list_my_leover_verifications` | QA07 VERIFIED, QA08 PENDING, QA09 PENDING. No submit. |

The care-transfer probe calls `canon_accept_care_transfer` for the recipient. Migration 1101 authorizes the recipient before the accepted row is returned, and the probe compared the fixture snapshot before and after. The snapshot did not change. The transit probe's only write attempt is QA06 `canon_initiate_care_transfer`, which returned denied and did not create a transfer.

## 7. Security

PASS.

| Control | Result |
| --- | --- |
| Product probes | User sessions only. `service_role` refused by the probe guards. |
| Direct sensitive select | Adoption and care-transfer probes: denied. Professional table: 403. Foster tables: denied before the transit read continues. |
| Professional isolation | QA16 and the owner do not receive the private clinical text. |
| Adoption applications | Unrelated listing stays behind the manager RPC. The completed read did not use table SELECT. |
| Care-transfer terminal return | Authorization runs before the `ACCEPTED` return in 1101. QA15, QA01, and QA07 received `FORBIDDEN`. QA14 retry did not change `decided_at`. |
| Foster management | QA01 cannot read the Bruno transit. QA06 cannot read the responsible care context after completion. |
| FOSTER fanout | QA06 eligible false. No QA06 notification on the responder FOUND. 1099 fanout body does not add `FOSTER`. |
| Responder claim eligibility | Claim requires a recipient row. The completed case stays with QA03. |
| Finder / custodian confirm | Confirm checks `current_custodian_person_id`. |
| Username login | `canon_begin_username_login` returns `{email}` only after `crypt` matches. Failure is `INVALID_CREDENTIALS`. |
| Secrets in git | QA password, access token, and publishable key are not in the tree. Private-key text hits are the example placeholder and a PEM-stripping helper. |

## 8. Remaining inventory

### P0

None. No new migration.

### P1

- Notification mark-read and canonical unread count. List is live. Mark-read returns `CANONICAL_MARK_READ_UNAVAILABLE` and is not stored in memory.
- Community `hours_json` is null on all 18 nearby rows read in this block. Weekly hours still live on the provider profile.
- M13 "Coincidencias" from the lost/found card still opens `MockM13MatchRepository` on canonical staging. The custodian candidate list on the alert detail uses `canon_list_found_match_candidates`.
- Foster-initiated early termination. Migration 1103 says it stays a later flow.
- Transit VitaCora start and end moments. The completed Bruno read reports both as not created by the current domain design.
- Adoption interviews, documents, and agreements. Canonical staging keeps them in memory. They are not the 1100 completion gate.
- Verification admin-review UX. The user request screen is canonical. Staff review is still the organization queue, not `canon_review_leover_verification`. QA09 remains `PENDING`, so the old "not requested" Maestro step does not match the fixture.
- Google Sign-In. Separate historical hardware QA. Username login does not replace it.
- Stories and reels. Separate historical QA. Not part of the 1099–1103 contracts.

Not P1 anymore: responder PostGIS fanout, adoption application contract, care-transfer accepted-retry authorization, foster read contract, and responsible-org end transit. Legacy M12 veterinary contract tests still guard `supabase/migrations` 046/047. They are legacy guards, not a stale failure of the 1098 private-history path.

### P2

- Support tickets.
- Agenda edge cases beyond Maestro 09d.
- Rate-limit live tests.
- Archived provisional FOUND pet keeps a `vitacora_profiles` row and a synthetic `CARE_CREATED` line.

### Product bugs

No P0 product bug. The remaining mock and contract gaps above are P1 or P2. Core persisted Community Care state is not silently stored in memory.

## 9. Physical QA matrix

JVM tests and these STAGING reads are not a device pass.

| Requirement | Automated / live STAGING | Real device |
| --- | --- | --- |
| Camera | Not covered | REQUIRED |
| Photo capture and upload | Staging fixtures reused an already uploaded asset. This block did not capture or upload. | REQUIRED |
| Real GPS | Nearby and lost/found pins in probes are the synthetic QA coordinates. | REQUIRED |
| Map pin | Contract and list refresh exist. A device pin was not placed. | REQUIRED |
| OS push notification shade | In-app notification rows were read. The shade was not opened. | REQUIRED |
| QR physical scan | QR route contract exists. No camera scan. | REQUIRED |
| External email / invite | 09c only asserts the form field in YAML. No inbox was read. | REQUIRED |
| Two-device responder race | Single-session read of an already claimed case. The 2-second nearest-wins race was not run. | REQUIRED |
| Google OAuth hardware flow | Not run. | REQUIRED |

## 10. Maestro 00–09d

Not executed.

| Flow | Classification | Why |
| --- | --- | --- |
| `00_smoke_login` | READY_TO_RUN | Login still says "Correo o usuario". |
| `00_smoke_qa02` | READY_TO_RUN | Same login shell. |
| `00_smoke_qa03` | READY_TO_RUN | Same login shell. |
| `01_lost_owner` | NEEDS_UPDATE | Labels still match, but Mora is already reunified. A run would publish another LOST. Photo picker is also physical. |
| `02_found_finder` | NEEDS_UPDATE | Would publish another FOUND. Photo picker is physical. |
| `03_match_owner_notification` | NEEDS_UPDATE | The pinned pair is already confirmed. "Podría ser mi mascota" is the pre-confirm action. Push shade is physical. |
| `03b_owner_assert_finder` | NEEDS_UPDATE | Custodian confirm already happened and is one-way. |
| `04_found_claim` | NEEDS_UPDATE | The pinned FOUND is already `IN_CARE`. The flow taps "Tomar caso" again. |
| `04b_responder_exclusions` | NEEDS_UPDATE | Screenshots only. It does not assert the 1099 FOSTER exclusion. |
| `05_verification` | STALE | QA09 is `PENDING`. The flow still expects "Solicitar verificación". |
| `05b_profile_switch` | READY_TO_RUN | "Usar LeoVer como" is still on the profile. Not executed. |
| `05c_qr_mora` | PHYSICAL_ONLY | Display path exists. A physical scan is a different check and was not run. |
| `06_community` | READY_TO_RUN | Live nearby still contains "QA - Veterinaria Centro" and a Pet Shop name. The flow does not assert hours. Not executed. |
| `07_adoption` | NEEDS_UPDATE | Luna is `CLOSED`. The flow applies and taps "Aceptar". It does not cover the 1100/1101 care-transfer completion. |
| `08_transit` | NEEDS_UPDATE | Bruno is `COMPLETED`. The flow publishes again, taps "Elegir", and has no "Finalizar tránsito" step from 1103. |
| `09_professional` | READY_TO_RUN | Private-history RPC passed live. The device flow was not run. |
| `09b_vitacora_proposals` | READY_TO_RUN | Not changed by 1099–1103. Not executed. |
| `09c_vet_patient_email` | PHYSICAL_ONLY | External inbox delivery cannot be passed from the form YAML. |
| `09d_agenda` | READY_TO_RUN | Outside the 1103 transit contract. Not executed. |

Flows that must be updated before the next device run because Community Care changed through 1103: `01`, `02`, `03`, `03b`, `04`, `04b`, `05`, `07`, `08`.

## 11. JVM regression

Command:

```bash
bash scripts/cloud/bootstrap-cloud.sh
bash scripts/qa/run-regression.sh
```

Bootstrap completed with JDK 21 and Android platforms 36 and 36.1. No emulator and no system image.

This run, on the working tree that adds `ReunificationVitaCoraInvariantTest` (2 tests) to `3dd0c06`:

| Suite | PASS | FAIL | TOTAL |
| --- | --- | --- | --- |
| APP `:app:testLocalDebugUnitTest` | 3120 | 0 | 3120 |
| SHARED `:shared:testAndroidHostTest` | 390 | 0 | 390 |

The previous baseline was app 3118 and shared 390. The two new tests are the reunification invariant. `ReunificationVitaCoraInvariantTest` completed 2 tests, 0 failures. Report: `artifacts/qa/regression/20260927-211451/report.md`.

Emulator: not run. Maestro: not run.

## 12. Canonical staging mocks

`useLegacyRemoteModules` is false on the canonical staging URL.

| Surface | Class | Notes |
| --- | --- | --- |
| Lost/found case list and refresh | Canonical RPC | `refreshAlerts()` on list open and on the map. |
| Lost/found card sightings | OPTIONAL_P1 | `CanonicalPlatformRepository` still delegates sightings to the platform mock. |
| M13 Coincidencias / sightings | OPTIONAL_P1 | In-memory on canonical staging. Reachable from the list card. |
| Notification list | Canonical RPC | Not the M06 memory inbox. |
| Notification mark-read | Not a mock | Fails closed. Missing RPC is P1. |
| Notification install / delivery / preferences | LEGACY_ONLY | Not the visible Community Care list. |
| Adoption applications, publications, completion | Canonical RPC | 1100 path. |
| Adoption interviews, documents, agreements, follow-up | OPTIONAL_P1 | Memory on purpose until a later contract. Not the completion gate. |
| Legacy adoption request / match list | LEGACY_ONLY | `MockAdoptionRequestRepository`. The 1100 screens use the application repository. |
| Care transfer | Canonical RPC | |
| Foster transit request, application, selection, end | Canonical RPC | `RpcCanonicalFosterTransitRepository`. |
| Old foster request repository | LEGITIMATE_LOCAL_ONLY | Fails with `CANONICAL_TRANSIT_USES_RPC` instead of storing a request. |
| Foster expense, evolution, help | LEGACY_ONLY | Not the Bruno request/completion path. |
| Community nearby | Canonical RPC | `CanonicalCommunityNearbyRepository`. |
| Legacy `communityRepository` | LEGACY_ONLY | Nearby does not use it. |
| Professional private history | Canonical RPC | Screen calls `canon_list_professional_pet_cares`. |
| Verification request | Canonical RPC | `CanonicalVerificationRepository`. |
| VitaCora moments | Canonical RPC | |

## 13. Boundaries

| Boundary | State |
| --- | --- |
| STAGING | READ ONLY |
| PROD | NO |
| service_role | NO |
| main | NOT MODIFIED |
| merge | NO |
| New migration | NO |
| Emulator | NO |
| Maestro | NO |
