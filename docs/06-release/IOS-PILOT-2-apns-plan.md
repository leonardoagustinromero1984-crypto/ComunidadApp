# IOS-PILOT-2 — APNs plan (sin sender / sin .p8)

**Base SHA:** `33cf1c14aec66abf168244b4514a57c11be29419`  
**Regla:** token APNs raw no se documenta ni se loguea en docs; app usa fingerprint.

## APP_SIDE_READY

| Capacidad | Evidencia |
| --------- | --------- |
| Pedir permiso notificaciones | shared push + UNUserNotificationCenter (AppDelegate delegate) |
| `registerForRemoteNotifications` | `IosPushBridge` handler → `UIApplication.shared.registerForRemoteNotifications()` |
| Device token → hex bridge | `AppDelegate.didRegister…` → `onIosDeviceTokenHex` (solo hex interno) |
| Fingerprint / M06 register | `m06_register_installation` + `p_token_fingerprint` |
| Revoke | `m06_revoke_current_installation` en logout path (`VerticalViewModels` / push repo) |
| Installation id estable | `leover-ios-default-install` |
| Notification → deep link | `deep_link_type` + `resource_id` → `NotificationIntentParser` / `offerIosNotificationExtras` |
| Quiet hours / prefs | KMP-27 app-side |

MissingToken tratado sin fingir ACTIVE.

## APPLE_DEVELOPER_REQUIRED

- Push Notifications capability en App ID / Xcode
- Entitlement `aps-environment` (`development` sandbox piloto; `production` para TestFlight/App Store)
- Team + provisioning que incluya push
- Certificado o key APNs (**fuera del repo**; nunca commit `.p8`)

## SERVER_REQUIRED

- Credencial APNs en servidor / secret store (no Git)
- Sender que entregue a APNs (sandbox vs production alineado con `aps-environment`)
- Payload con extras allowlisted (`deep_link_type`, `resource_id`) compatibles con parser
- **No** implementar sender en esta fase

## REAL_DEVICE_REQUIRED

- iPhone físico (simulador no valida APNs real de producción)
- Permiso usuario
- Register success o MissingToken honesto
- Tap notificación → deep link tipado
- Logout → revoke instalación

## Separación explícita

```text
APP_SIDE_READY              = YES (register/revoke/fingerprint/deeplink)
APPLE_DEVELOPER_REQUIRED    = YES (capability + aps-environment + Team)
SERVER_REQUIRED             = YES (APNs credentials + sender)
REAL_DEVICE_REQUIRED        = YES (NOT_VALIDATED)
```

No crear `.p8`. No agregar secrets a GitHub en esta fase.
