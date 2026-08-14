# KMP-IOS — Device / external capabilities readiness

Auditoría solo desde repo. Sin secrets / sin tocar web / sin habilitar providers.
Actualizado en IOS-PILOT-1: seams de configuración sin reescritura de arquitectura.

## 1. Sign in with Apple

| Requisito | Clasificación | Evidencia |
| --------- | ------------- | --------- |
| Native ASAuthorization + IDToken exchange | APP_SIDE_DONE | KMP-18 `AppleSignInIos` aislado |
| Supabase `[auth.external.apple] enabled` | EXTERNAL_CONFIG_REQUIRED | `config.toml` enabled=false |
| Sign in with Apple entitlement | APPLE_DEVELOPER_REQUIRED | sin `.entitlements` (punto claro: agregar capability Xcode) |
| Provisioning / Team | APPLE_DEVELOPER_REQUIRED | `DEVELOPMENT_TEAM=""` |

**Seam:** adapter Apple → `signInWithAppleIdToken` → misma sesión. No secrets en app.

## 2. Universal Links

| Requisito | Clasificación | Evidencia |
| --------- | ------------- | --------- |
| Custom scheme `leover://` | APP_SIDE_DONE | Info.plist |
| HTTPS DeepLinkParser | APP_SIDE_DONE | commonMain |
| Associated Domains | APPLE_DEVELOPER_REQUIRED | punto claro: entitlements `applinks:` |
| AASA | WEB_REQUIRED | no en repo |

**Seam:** Swift `onOpenURL` → `offerDeepLinkUrl` → parser shared. Associated Domains no requiere rewrite de routing.

## 3. APNs

| Requisito | Clasificación | Evidencia |
| --------- | ------------- | --------- |
| Permission + fingerprint + M06 register/revoke | APP_SIDE_DONE | stable install id `leover-ios-default-install` |
| `aps-environment` | APPLE_DEVELOPER_REQUIRED | punto claro: Push Notifications capability |
| Server → APNs | SERVER_REQUIRED | no .p8 / sender en app |

**Seam:** AppDelegate → hex → fingerprint → M06. Delivery server-side only.

## Resumen

```text
APPLE_SIGN_IN     = APP_SIDE_DONE + EXTERNAL_CONFIG + APPLE_DEVELOPER
UNIVERSAL_LINKS   = WEB_REQUIRED + APPLE_DEVELOPER (scheme APP_SIDE_DONE)
APNS_PROD         = APPLE_DEVELOPER + SERVER_REQUIRED (register APP_SIDE_DONE)
ARCHITECTURE_REWRITE_FOR_EXTERNAL = NO
```
