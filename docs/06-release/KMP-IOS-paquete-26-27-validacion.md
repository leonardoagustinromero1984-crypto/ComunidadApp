# KMP-IOS — Paquete 26–27 + IOS-READY validación

**HEAD base (docs Gate #18):** `82a33f31627846634bd32e3c7ebaf68bef1d7c80`
**Padre funcional KMP-23/24/25:** `1ca09b1fb1228bb8bc9d3959dab449ec4a5b3b19`
**Gate #18:** PASS (`05a3a2a`) — KMP-20/21/22 CLOSED GREEN
**Gate #19:** PENDING (sobre `82a33f3…`; no inventar PASS/FAIL)

## Alcance

| Bloque | Resultado |
| ------ | --------- |
| KMP-26 Pet archive/lifecycle | REAL_REMOTE |
| KMP-27 Quiet hours / prefs | REAL_REMOTE |
| IOS-READY external audit | DOCUMENTED (no external enable) |

## Validación Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | **386 PASS** (0 failures) |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `:shared:compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL |
| Nuevos tests (lifecycle + quiet hours) | **17 PASS** |
| Suite base previa | **369** (`1ca09b1`) |
| WIP M09/decoding/M29 | NO TOCADO |
| M24 Pagos | NO TOCADO |
| M28 módulo | NO (catálogo M00–M27) |

## Cloud

`READY_TO_RUN_GROUPED_IOS_CLOUD_GATE`
