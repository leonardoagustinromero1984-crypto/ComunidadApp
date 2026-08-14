# KMP-IOS — Paquete 23–25 validación

**HEAD base (KMP-20/21/22):** `05a3a2a2bb904053b6c9a13903ad5ff69fac713a`
**Gate #17:** PASS (`d1d04cb2dfbb002c4960e6001196ba4e370b63a9`) — KMP-17/18/19 CLOSED GREEN
**Gate #18:** PENDING (sobre `05a3a2a…`; no inventar PASS/FAIL)

## Alcance

| Bloque | Resultado |
| ------ | --------- |
| KMP-23 Pet health | REAL_REMOTE |
| KMP-24 L/F owner edit + media | REAL_REMOTE (≠ M24 Pagos) |
| KMP-25 Notification prefs + push UX | REAL_REMOTE + REAL_NATIVE |

## Validación Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | **369 PASS** (0 failures) |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `:shared:compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL |
| Nuevos tests (health + LF edit + prefs) | **21 PASS** |
| Suite base previa | **348** (`05a3a2a`) |
| WIP M09/decoding/M29 | NO TOCADO |
| M24 Pagos | NO TOCADO |

## Invariantes

- SUPABASE CLIENT COUNT HOST = 1
- Fake fallback = NO
- SQL / migrations / schema / web / APK = NO
- KT-86501 workaround preserved

## Cloud

`READY_TO_RUN_GROUPED_IOS_CLOUD_GATE`
