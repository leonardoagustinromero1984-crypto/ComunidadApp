# KMP-IOS — Paquete 17–19 validación

**HEAD base (KMP-14/15/16):** `22115b1`
**HEAD cierre (KMP-17/18/19):** `d1d04cb`
**Gate #16:** PASS (`22115b1284c6834da17f168a3826d5559465f68d`) — KMP-14/15/16 CLOSED GREEN
**Gate #17:** PENDING
**KMP-14/15/16:** CLOSED GREEN (suite base **294** tests)

## Alcance

| Bloque | Resultado |
| ------ | --------- |
| KMP-17 Deep links | REAL SHARED + iOS URL scheme |
| KMP-18 Apple Sign In | APP_SIDE_READY_BACKEND_CONFIG_REQUIRED |
| KMP-19 APNs foundation | FOUNDATION WIRED (register/revoke + permiso explícito) |

## Validación Windows

| Check | Resultado |
| ----- | --------- |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `:shared:compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL |
| `:shared:testAndroidHostTest` | **323 PASS** (0 failures) |
| Nuevos tests (deeplink + apple + push) | **29 PASS** |
| Suite base previa | **294** (HEAD `22115b1`) |
| WIP `app/` M09/decoding / M29 | NO TOCADO |
| Gate #16 (commit `22115b1`) | PASS |
| Gate #17 (commit `d1d04cb`) | PENDING |

## Invariantes

- SUPABASE CLIENT COUNT HOST = 1
- Fake fallback host = NO
- Token APNs raw nunca en modelos / mensajes UI
- Deep link reasons sin URL cruda
- Associated Domains = NO
- Sign in with Apple entitlements = DEVICE_CAPABILITY_REQUIRED
- SQL / migrations / schema = NO

## Cloud

`READY_TO_RUN_GROUPED_IOS_CLOUD_GATE` (Gate #17 PENDING hasta evidencia)
