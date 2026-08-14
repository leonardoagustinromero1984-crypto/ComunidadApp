# IOS-PILOT — Readiness report

**Cloud / CI GREEN base:** `33cf1c14aec66abf168244b4514a57c11be29419`

**Gate #19:** PASS (`82a33f3`) — KMP-23/24/25 CLOSED GREEN

**Gate #20:** PASS (`043a72c693c54885e017e63fda24203ee5759ab2`) — KMP-26/27 CLOSED GREEN

**IOS PILOT HARDENING SHA:** `f7ce081df3e60adba044ce460de8b99bef4a236f`

**IOS-PILOT-2 (prep):** docs only — identity / UL / APNs / device strategy / external matrix

## Verdict layers

| Layer | Status |
| ----- | ------ |
| APP CODE READY | **YES** (KMP-1…27 + pilot hardening + CI closure) |
| IOS PILOT CLOUD | **GREEN** (`33cf1c1`) |
| EXTERNAL CAPABILITY PLAN | **COMPLETE** (prep docs; nothing activated) |
| REAL DEVICE VALIDATION | **PENDING** (P-* and RD-* = NOT_RUN) |
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

No secrets / .p8 / web enablement in this package. See `IOS-PILOT-2-external-readiness.md`.

## REAL_DEVICE_VALIDATION = PENDING

Execute `IOS-PILOT-MANUAL-TESTS.md` (incl. RD-01…RD-25) on physical iPhone before pilot claim.
