# IOS-PILOT — Readiness report

**Base code:** `043a72c` + IOS-PILOT-1 hardening `f7ce081`

**Gate #19:** PASS (`82a33f3`) — KMP-23/24/25 CLOSED GREEN

**Gate #20:** PASS (`043a72c693c54885e017e63fda24203ee5759ab2`) — KMP-26/27 CLOSED GREEN

**IOS PILOT HARDENING SHA:** `f7ce081df3e60adba044ce460de8b99bef4a236f`

## Verdict layers

| Layer | Status |
| ----- | ------ |
| APP CODE READY | **YES** (KMP-1…27 + pilot hardening) |
| CLOUD GATE READY | Gate #20 PASS; migration-guard CI fixed (no fixed ceiling 052) |
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
