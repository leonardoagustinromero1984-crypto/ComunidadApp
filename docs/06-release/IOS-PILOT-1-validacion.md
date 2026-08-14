# IOS-PILOT-1 — Validación

**HEAD base:** `043a72c693c54885e017e63fda24203ee5759ab2`

**IOS PILOT HARDENING SHA:** `f7ce081df3e60adba044ce460de8b99bef4a236f`

**Gate #19:** PASS (`82a33f3`) — KMP-23/24/25 CLOSED GREEN

**Gate #20:** PASS (`043a72c693c54885e017e63fda24203ee5759ab2`) — KMP-26/27 CLOSED GREEN

## Alcance

Hardening only — no new product verticals. Quiet hours days = REAL_REMOTE.

## Validación Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | **390 PASS** (0 failures) |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `:shared:compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL |
| Suite base previa | **386** (`043a72c`) |
| WIP M09/M29 | NO TOCADO |
| M24 / KMP-28 | NO |

## IOS-PILOT-1.1 — CI migration guard

| Check | Resultado |
| ----- | --------- |
| Causa CI Android | Guard stale `highest == 052` vs repo `081` |
| Fix | Option A — derive highest; keep dupes/gaps/format |
| SQL / apply / repair | NO |
| Device tests | NOT_RUN (sigue PENDING) |

## Cloud

`READY_TO_RUN_IOS_PILOT_CLOUD_GATE`
