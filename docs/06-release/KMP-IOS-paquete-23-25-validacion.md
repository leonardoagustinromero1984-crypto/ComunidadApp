# KMP-IOS — Paquete 23–25 validación

**HEAD base (KMP-20/21/22):** `05a3a2a2bb904053b6c9a13903ad5ff69fac713a`
**HEAD cierre (KMP-23/24/25):** `1ca09b1fb1228bb8bc9d3959dab449ec4a5b3b19`
**Gate #17:** PASS (`d1d04cb2dfbb002c4960e6001196ba4e370b63a9`) — KMP-17/18/19 CLOSED GREEN
**Gate #18:** PASS (`05a3a2a2bb904053b6c9a13903ad5ff69fac713a`) — KMP-20/21/22 CLOSED GREEN
**Gate #19:** PASS (`82a33f31627846634bd32e3c7ebaf68bef1d7c80`) — docs Gate #18 package CLOSED
**Gate #20:** PASS (`043a72c693c54885e017e63fda24203ee5759ab2`)
**KMP-20/21/22:** CLOSED GREEN

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
