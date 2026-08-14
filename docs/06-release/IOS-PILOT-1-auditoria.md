# IOS-PILOT-1 — Auditoría de flows

Base funcional: `043a72c` (KMP-1…27). Catálogo módulos oficiales M00–M27. No KMP-28.

| Flow | Estado |
| ---- | ------ |
| AUTH email/password | REAL_REMOTE |
| APPLE AUTH | APP_SIDE_ONLY + EXTERNAL_CONFIG_REQUIRED |
| PROFILE READ/EDIT + avatar | REAL_REMOTE |
| PETS READ | REAL_REMOTE |
| PET CREATE / EDIT | REAL_REMOTE |
| PET HEALTH | REAL_REMOTE |
| PET LIFECYCLE archive/restore/deceased | REAL_REMOTE |
| LOST / FOUND READ + CREATE | REAL_REMOTE |
| LOST/FOUND EDIT + RESOLVE | REAL_REMOTE |
| ADOPTION READ / PUBLISH / APPLY | REAL_REMOTE |
| MY APPLICATIONS / SHELTER REVIEW | REAL_REMOTE |
| MEDIA READ / WRITE (M05) | REAL_REMOTE |
| DEEP LINKS parser + custom scheme | REAL_REMOTE / APP_SIDE |
| PUBLIC CONTENT get_public_* | REAL_REMOTE |
| UNIVERSAL LINKS | APP_SIDE_ONLY + WEB_REQUIRED |
| PUSH PREFS + QUIET HOURS (+ days) | REAL_REMOTE |
| APNs permission + register/revoke | APP_SIDE_ONLY + APPLE_DEVELOPER + SERVER_REQUIRED |
| ADOPTION MEDIA WRITE | NOT_APPLICABLE |

## Hardening applied (this package)

- Logout: clear pending deep links + Home route + media cache + push revoke (stable install id)
- Home SessionViewModel wired with push repo
- ProfileEdit Error (no infinite spinner)
- Public RemoteUrl media without auth
- Quiet hours days UI
- Image pick errors sanitized
- Activar notificaciones busy flag
- Multi-user isolation tests
