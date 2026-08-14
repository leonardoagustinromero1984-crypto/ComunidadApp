# KMP-IOS — Bloque 19 auditoría (APNs foundation)

Fuente: `shared/.../push/` + `IosPushBridge` + Swift `AppDelegate`.

## CONTRATO M06

| RPC | Uso |
| --- | --- |
| `m06_register_installation` | `p_platform=IOS`, `p_token_fingerprint` |
| `m06_revoke_current_installation` | logout / revoke |

## FINGERPRINT

- SHA-256 hex lowercase del device token (bytes / hex APNs).
- Modelo público: `PushTokenFingerprint(hexSha256)` — **nunca raw token**.

## PERMISO

- Solo botón explícito **"Activar notificaciones"** (Home) — no al launch.
- Tap notificación → `deep_link_type` + `resource_id` → `NotificationIntentParser`.

## LIMITACIONES

- Push delivery end-to-end / prod certs: fuera de este bloque (foundation only).
- Unsigned sim: registro APNs puede fallar → `MissingToken` / `Unavailable`.
