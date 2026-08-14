# KMP-IOS — Backlog (post KMP-20/21/22)

## Terminado

- KMP-1…19
- **KMP-20:** Public deep link content REAL_REMOTE (`get_public_*`)
- **KMP-21:** Pet edit REAL_REMOTE (`m08_update_pet_profile` + avatar)
- **KMP-22:** Lost/Found owner resolve REAL_REMOTE (ACTIVE→RESOLVED)

## iOS ahora

```text
SESSION / PROFILE / PETS / LOST_FOUND / ADOPTIONS = REAL_REMOTE
DEEP_LINKS = SHARED + PUBLIC CONTENT REAL_REMOTE
PET EDIT = REAL_REMOTE (profile; health deferred)
L/F OWNER = REAL_REMOTE (resolve; no hard delete UI)
APPLE_SIGN_IN = APP_SIDE_READY_BACKEND_CONFIG_REQUIRED
APNs = FOUNDATION (M06 install register/revoke)
UNIVERSAL_LINKS = APP_SIDE_READY / WEB ASSOCIATION BLOCKED
```

## Pendiente

- Enable Supabase Apple provider + device SIWA entitlement
- Pet health / archive lifecycle
- L/F owner field-edit UI + media replace UX
- Universal Links AASA (web)
- APNs prod delivery / prefs UI

## No iniciar

M24, SQL/schema, web, APK en este paquete
