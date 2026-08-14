# KMP-IOS — Bloque 25 auditoría (Notification prefs + push UX)

Fuente: `026` + Android `SupabaseNotificationPreferenceRepository`.

## PREFERENCES

| RPC | Params |
| --- | ------ |
| `m06_get_preferences` | (JWT actor) |
| `m06_update_preference` | category, in_app, push, email=false, marketing, quiet*, timezone |

Categories: returned by RPC (SQL allowlist). No invented toggles.

## PLATFORM UX

Permission: NotDetermined / Denied / Authorized / Provisional.
CTA Activar solo cuando corresponde. Denied → mensaje Ajustes sistema.
Register: permission → APNs → fingerprint → `m06_register_installation`.
Revoke: `m06_revoke_current_installation` on logout / disable when wired.

## SERVER_TO_APNS_DELIVERY

```text
NOT_VALIDATED_WITHOUT_APPLE_SERVER_CONFIG
```
