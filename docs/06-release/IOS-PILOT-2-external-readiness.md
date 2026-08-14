# IOS-PILOT-2 — External capability readiness

**Base SHA (cloud GREEN):** `33cf1c14aec66abf168244b4514a57c11be29419`

```text
IOS_PILOT_CLOUD = GREEN
APP_CODE_READY = YES
EXTERNAL_CAPABILITY_PLAN = COMPLETE
REAL_DEVICE_VALIDATION = PENDING
```

No marcar PASS de device/external sin evidencia.

## Matriz

| CAPABILITY | APP CODE | APPLE DEVELOPER | SUPABASE | WEB | SERVER | REAL DEVICE | STATUS |
| ---------- | -------- | --------------- | -------- | --- | ------ | ----------- | ------ |
| Sign in with Apple | READY (ID token path) | REQUIRED (entitlement/Team/App ID) | REQUIRED (`enabled=false`) | N/A (native) | N/A | NOT_VALIDATED | PENDING_EXTERNAL |
| Universal Links | READY (parser + onOpenURL) | REQUIRED (Associated Domains) | N/A | REQUIRED (AASA) | N/A | NOT_VALIDATED | PENDING_EXTERNAL |
| Custom scheme `leover://` | READY | N/A | N/A | N/A | N/A | NOT_VALIDATED | APP_READY_DEVICE_PENDING |
| APNs | READY (register/revoke/fingerprint) | REQUIRED (push + aps-environment) | N/A (M06 RPCs ready) | N/A | REQUIRED (sender + .p8 fuera repo) | NOT_VALIDATED | PENDING_EXTERNAL |
| Signing | Project Automatic; Team vacío | REQUIRED (Team + profiles) | N/A | N/A | N/A (o CI secrets) | NOT_VALIDATED | PENDING_EXTERNAL |
| TestFlight | N/A | REQUIRED (membership + ASC) | N/A | N/A | Build pipeline | NOT_VALIDATED | PENDING_EXTERNAL |

## Docs de esta fase

| Doc | Rol |
| --- | --- |
| `IOS-PILOT-2-identidad-y-signing.md` | Bundle / Team / entitlements |
| `IOS-PILOT-2-universal-links.md` + `IOS-PILOT-2-aasa-draft.json` | UL plan + draft placeholders |
| `IOS-PILOT-2-apns-plan.md` | APNs capas |
| `IOS-PILOT-2-device-strategy.md` | Mac / CI / TestFlight |
| `IOS-PILOT-MANUAL-TESTS.md` | Matrix RD-* NOT_RUN |

## Secrets scan (nombres de archivo)

`SECRETS_FOUND = NO` — sin `.p8` / `.p12` / `.mobileprovision` / AuthKey / service_role files en el árbol de trabajo auditado.

## Próximo paso (NO esta tarea)

Activación controlada externa + ejecución matrix RD en iPhone real.
