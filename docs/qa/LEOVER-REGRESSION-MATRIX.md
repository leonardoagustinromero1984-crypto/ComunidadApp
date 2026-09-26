# LeoVer regression matrix

Honest coverage. **PARTIAL** means some layer exists; it is not FULL proof.
Local default is Fast (no emulator). Maestro is on-demand.

| Module | Rule | Unit/contract | Backend/SQL | Compose/device-free | Maestro | Manual | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH | REG-AUTH-001 username PERSON first | Yes | Contract on 1097 | — | Smoke QA01 | — | PARTIAL |
| AUTH | REG-AUTH-002 email login | Yes | — | — | — | — | PARTIAL |
| AUTH | REG-AUTH-003 logout | — | — | — | Subflow | — | PARTIAL |
| AUTH | REG-AUTH-004 Google chooser | — | — | — | — | Yes | MANUAL_ONLY |
| AUTH | REG-AUTH-005 staff/MFA isolation | Yes | sec02 SQL | — | — | — | PARTIAL |
| PERSON | REG-PERSON-001 privacy/profile | Yes | — | — | — | — | PARTIAL |
| PERSON | REG-PERSON-002 profile switch | — | — | — | 05b | — | PARTIAL |
| PET | REG-PET-001 create + photo | Yes (shared) | — | — | — | Photo HW | PARTIAL |
| PET | REG-PET-002 update/shared | Yes | — | — | — | — | PARTIAL |
| PET | REG-PET-003 QR display | — | — | — | 05c | Scan HW | PARTIAL |
| VITACORA | REG-VITA-001 history filter | Yes | — | — | — | — | PARTIAL |
| VITACORA | REG-VITA-002 proposals/ACL | Guard | — | — | 09b | — | PARTIAL |
| SOCIAL | REG-SOC-001 feed/privacy | Yes | — | — | — | Feed HW | PARTIAL |
| LOST_FOUND | REG-LF-001 LOST publish | Shared vertical | — | — | 01 | — | PARTIAL |
| LOST_FOUND | REG-LF-002 FOUND + provisional | Contract | — | — | 02 | — | PARTIAL |
| LOST_FOUND | REG-LF-003 match notify | — | 1096 SQL probe | — | 03 | Push shade | PARTIAL |
| LOST_FOUND | REG-LF-004 ST_MakePoint search_path | Contract | Migration static | — | — | — | COVERED |
| LOST_FOUND | REG-LF-005 claim + exclusions | — | — | — | 04 / 04b | — | PARTIAL |
| VERIFICATION | REG-VER-001 states + PENDING refresh | Rules unit | — | — | 05 | — | PARTIAL |
| TRANSIT | REG-TRANSIT-001 request/select | ViewModel | — | — | 08 | — | PARTIAL |
| ADOPTION | REG-ADOPT-001 multi-applicant; direct finalize refused | ViewModel | — | — | 07 | — | PARTIAL |
| COMMUNITY | REG-COM-001 nearby/filters | Filter unit | — | — | 06 | Exact GPS | PARTIAL |
| COMMUNITY | REG-BUG-010 form+map state | — | — | — | — | — | MISSING |
| PROFESSIONAL | REG-PRO-001 hub/ACL | Vet ViewModel | — | — | 09 | — | PARTIAL |
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

## P0 gaps

- REG-BUG-010 Community form loses data when opening map — no test
- REG-LF-003 / REG-VER-001 / REG-PET-001 cheap JVM coverage still incomplete (happy-path often Maestro-only)
- Live RLS suite is probes, not a gated `SECURITY_REGRESSION` Gradle task

## P1 gaps

- Google login E2E
- QR scan, real GPS, OS push shade
- Stories/reels/comments Maestro
- Signed URL access-control live tests

## P2 gaps

- Support tickets
- Agenda edge cases beyond 09d
- Rate-limit live tests
