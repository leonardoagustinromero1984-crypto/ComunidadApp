# IOS-PILOT — Readiness report

**Base code:** `043a72c` + IOS-PILOT-1 hardening commit
**Gate #19:** PASS (`82a33f3`) — KMP-23/24/25 CLOSED GREEN
**Gate #20:** PENDING (`043a72c`) hasta evidencia cloud

## Verdict layers

| Layer | Status |
| ----- | ------ |
| APP CODE READY | **YES** (KMP-1…27 + pilot hardening) |
| CLOUD GATE READY | Gate #20 PENDING / run after this commit |
| REAL DEVICE VALIDATION | **PENDING** (matrix NOT_RUN) |
| EXTERNAL CONFIG | **PENDING** |

## APP_CODE_READY = YES

Shared/iOS covers auth, profile, pets (CRUD/health/lifecycle), lost/found (publish/edit/resolve), adoptions (publish/apply/review), media M05, public deep-link content, push prefs + quiet hours, logout/privacy hardening.

## EXTERNAL_CONFIG_REQUIRED (exact)

| Blocker | Class |
| ------- | ----- |
| Supabase Apple provider enabled=false | EXTERNAL_CONFIG_REQUIRED |
| Sign in with Apple entitlement / Team | APPLE_DEVELOPER_REQUIRED |
| AASA + Associated Domains | WEB_REQUIRED + APPLE_DEVELOPER_REQUIRED |
| aps-environment + provisioning | APPLE_DEVELOPER_REQUIRED |
| Server → APNs delivery | SERVER_REQUIRED |

No secrets / .p8 / web enablement in this package.

## REAL_DEVICE_VALIDATION = PENDING

Execute `IOS-PILOT-MANUAL-TESTS.md` on physical iPhone before pilot claim.
