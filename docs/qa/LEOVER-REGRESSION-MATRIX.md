# LeoVer regression matrix

Honest coverage. **PARTIAL** means some layer exists; it is not FULL proof.
Local default is Fast (no emulator). Maestro is on-demand.

| Module | Rule | Unit/contract | Backend/SQL | Compose/device-free | Maestro | Manual | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH | REG-AUTH-001 username PERSON first | Yes | Contract on 1097 | — | not executed | — | COVERED |
| AUTH | REG-AUTH-002 email login | Yes | — | — | — | — | PARTIAL |
| AUTH | REG-AUTH-003 logout | — | — | — | Subflow | — | PARTIAL |
| AUTH | REG-AUTH-004 Google chooser | — | — | — | — | Yes | MANUAL_ONLY |
| AUTH | REG-AUTH-005 staff/MFA isolation | Yes | sec02 SQL | — | — | — | PARTIAL |
| PERSON | REG-PERSON-001 privacy/profile | Yes | — | — | — | — | PARTIAL |
| PERSON | REG-PERSON-002 profile switch | — | — | — | 05b | — | PARTIAL |
| PET | REG-PET-001 create + photo | Yes (shared) | — | — | — | Photo HW | PARTIAL |
| PET | REG-PET-002 update/shared | Yes | — | — | — | — | PARTIAL |
| PET | REG-PET-003 QR display | — | — | — | not executed | Scan HW | PARTIAL |
| PET | QR direct route | Contract | — | — | not executed | — | COVERED |
| VITACORA | REG-VITA-001 history filter | Yes | — | — | — | — | PARTIAL |
| VITACORA | REG-VITA-002 proposals/ACL | Guard | — | — | 09b | — | PARTIAL |
| SOCIAL | REG-SOC-001 feed/privacy | Yes | — | — | — | Feed HW | PARTIAL |
| LOST_FOUND | REG-LF-001 LOST publish | Shared vertical | — | — | 01 | — | PARTIAL |
| LOST_FOUND | REG-LF-002 FOUND + provisional | Contract | — | — | 02 | — | PARTIAL |
| LOST_FOUND | REG-LF-003 match notify | — | 1096 SQL probe | — | 03 | Push shade | PARTIAL |
| LOST_FOUND | REG-LF-004 ST_MakePoint search_path | Contract | Migration static | — | — | — | COVERED |
| LOST_FOUND | REG-BUG-003 matching 1096 search_path | Contract | Migration static | — | — | — | COVERED |
| LOST_FOUND | REG-LF-005 claim + exclusions | — | — | — | 04 / 04b | — | PARTIAL |
| LOST_FOUND | REG-LF-006 reunification VitaCora invariant | Contract | Live read of resolved Mora pair | — | not executed | — | COVERED |
| VERIFICATION | REG-VER-001 states | Rules unit | — | — | not executed | — | PARTIAL |
| VERIFICATION | REG-BUG-005 immediate PENDING refresh | ViewModel | — | — | not executed | — | COVERED |
| TRANSIT | REG-TRANSIT-001 request/select | ViewModel | — | — | 08 | — | PARTIAL |
| ADOPTION | REG-ADOPT-001 multi-applicant | ViewModel | — | — | not executed | — | PARTIAL |
| ADOPTION | REG-BUG-011 direct finalize refused | ViewModel | Migration 1093 | — | not executed | — | COVERED |
| COMMUNITY | REG-COM-001 nearby/filters | Filter unit | — | — | 06 | Exact GPS | PARTIAL |
| COMMUNITY | REG-BUG-010 form+map state | ViewModel | — | — | — | — | COVERED |
| PROFESSIONAL | REG-PRO-001 hub/ACL | Vet ViewModel | — | — | not executed | — | PARTIAL |
| PROFESSIONAL | REG-BUG-012 private history 1098 | Contract | RPC 1098 | — | not executed | — | COVERED |
| PROFESSIONAL | REG-PRO-002 email invite | — | — | — | 09c tagged manual | Inbox | MANUAL_ONLY |
| MEDIA | REG-MEDIA-001 upload/visibility | Yes | — | — | — | Camera | PARTIAL |
| SECURITY | REG-SEC-001 RLS/admin isolation | Authz unit | SQL probes | — | — | — | PARTIAL |
| UI | REG-BUG-008/009 * / labels | UiRegressionGate | — | Device-free | — | — | PARTIAL |
| HARDWARE | REG-HW-001 camera/GPS/push/QR/email | — | — | — | — | Yes | MANUAL_ONLY |

## Suite levels

| Level | What | When |
| --- | --- | --- |
| FAST (default) | JVM unit/contract/ViewModel/static SQL guards | Every change |
| AFFECTED | FAST filtered by `change-impact-map.yaml` | After implementation |
| CORE | FAST + Maestro P0 Community Care | Block close / explicit `-SmokeE2E` then CommunityCare |
| FULL E2E | All Maestro flows on LeoVer-QA | Release / `-FullE2E` only |
| MANUAL | Hardware / OS / external inbox | Cannot automate reliably |

## Inventory snapshot (this machine)

| Kind | Where | Count (approx) | Device? |
| --- | --- | --- | --- |
| App JVM unit/contract/ViewModel | `app/src/test` | ~367 classes | No |
| Shared commonTest | `shared/src/commonTest` | 29 classes | No |
| Android instrumented | `app/src/androidTest` | 1 example | Emulator/device |
| Maestro flows | `.maestro/flows` | 19 YAML | LeoVer-QA on demand |
| SQL/QA probes | `infra/supabase-canonical/qa` | 15 | STAGING CLI |
| QA scripts | `scripts/qa` | runners + seed/reset | Optional emulator |

Compose UI tests that need a device are **not** the daily path. `UiRegressionGateTest` is static and device-free.

## Cloud chain coverage (2026-09-27)

JVM and contract coverage on this branch. **COVERED** here is not a Maestro pass and not a physical QA pass.

| Item | Evidence | Status |
| --- | --- | --- |
| VERIFICATION REFRESH | `LeoverVerificationRequestViewModelTest` | COVERED |
| PROFESSIONAL PRIVATE HISTORY | `ProfessionalPrivateHistoryContractTest`, migration 1098 | COVERED |
| ADOPTION DIRECT FINALIZE | `M09AdoptionCompletionTest` refuses `ADOPTION_USE_CANONICAL_TRANSFER` | COVERED |
| PERSON USERNAME LOGIN | `PersonUsernameLoginContractTest`, migration 1097 | COVERED |
| 1096 MATCHING SEARCH_PATH | `CloudBaselineRepair02ContractTest` | COVERED |
| COMMUNITY DIRTY STATE | `CommunityFormDirtyStateTest` | COVERED |
| QR DIRECT ROUTE | `CloudBaselineRepair02ContractTest` opens `m14/pets/{petId}/share` | COVERED |
| REUNIFICATION VITACORA | `ReunificationVitaCoraInvariantTest` and `VitaCoraReunificationRetirement1104Test`. Migration 1104 retires the provisional VitaCora. The Mora read is the post-apply check and was not run in this block. | COVERED |

Final consolidation JVM, recorded from `:app:testLocalDebugUnitTest` and `:shared:testAndroidHostTest` on this revision: app **3120/3120**, shared **390/390**, 0 FAIL. The two tests above the previous app baseline of 3118 are `ReunificationVitaCoraInvariantTest`.

Maestro flows and the emulator were not executed in the final Community Care consolidation. Physical QR scan, real GPS, camera capture, and the OS push shade stay manual. REG-LF-006 COVERED is the contract plus the read-only STAGING fixture. It is not a device pass.

## QA09 fixture state

During P0 validation, STAGING organization `qa-cc-shelter-noreq` (QA09) moved from `NOT_REQUESTED` to `PENDING`. That is the current canonical QA fixture state. Do not restore `NOT_REQUESTED` by hand and do not open another verification request. The committed seed still describes the original insert; this consolidation does not re-seed STAGING.

## P0 gaps

No open P0 correctness or security defect was found in the final Community Care consolidation through migration 1103. Maestro flows 01–08 were not executed. That is physical or flow-update work, not an unfixed backend P0.

## P1 gaps

- Notification mark-read and a canonical unread-count RPC. The visible list reads `canon_list_my_notifications`. Mark-read fails closed.
- Community nearby `hours_json` is still null on every row.
- M13 "Coincidencias" still opens the in-memory match repository from the lost/found list.
- Foster-initiated early termination is explicitly later than migration 1103.
- Transit start and end do not write VitaCora moments.
- Adoption interviews, documents, and agreements stay in memory on canonical staging.
- Verification admin review still is not `canon_review_leover_verification`.
- Google Sign-In remains a separate hardware flow.
- Stories and reels remain a separate historical QA track.

## P2 gaps

- Support tickets
- Agenda edge cases beyond 09d
- Rate-limit live tests
- Migration 1104 retires a reunited provisional VitaCora and stops synthetic `CARE_CREATED`. It is not applied. STAGING still shows that residue until 1104 is applied.
