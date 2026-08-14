# KMP-IOS — Device / external capabilities readiness

Auditoría solo desde repo. Sin secrets / sin tocar web / sin habilitar providers.

## 1. Sign in with Apple

| Requisito | Clasificación | Evidencia |
| --------- | ------------- | --------- |
| Native ASAuthorization + IDToken exchange | APP_SIDE_DONE | KMP-18 `AppleSignInIos` |
| Supabase `[auth.external.apple] enabled` | EXTERNAL_CONFIG_REQUIRED | `config.toml` enabled=false |
| Sign in with Apple entitlement | APPLE_DEVELOPER_REQUIRED | sin `.entitlements` |
| Provisioning / Team | APPLE_DEVELOPER_REQUIRED | `DEVELOPMENT_TEAM=""` |

**Composite:** APP_SIDE_DONE + EXTERNAL_CONFIG_REQUIRED + APPLE_DEVELOPER_REQUIRED

## 2. Universal Links

| Requisito | Clasificación | Evidencia |
| --------- | ------------- | --------- |
| Custom scheme `leover://` | APP_SIDE_DONE | Info.plist CFBundleURLTypes |
| HTTPS DeepLinkParser allowlist | APP_SIDE_DONE | KMP-17 |
| Associated Domains entitlement | APPLE_DEVELOPER_REQUIRED | ausente |
| `apple-app-site-association` | WEB_REQUIRED | no en repo/web |

**Composite:** custom scheme APP_SIDE_DONE; Universal Links = WEB_REQUIRED + APPLE_DEVELOPER_REQUIRED

## 3. APNs

| Requisito | Clasificación | Evidencia |
| --------- | ------------- | --------- |
| Permission UX + register token fingerprint | APP_SIDE_DONE | KMP-19/25 + M06 |
| `m06_register_installation` / revoke | APP_SIDE_DONE | shared push |
| `aps-environment` entitlement | APPLE_DEVELOPER_REQUIRED | ausente |
| Server → APNs delivery | SERVER_REQUIRED | NOT_VALIDATED_WITHOUT_APPLE_SERVER_CONFIG |

**Composite:** foundation APP_SIDE_DONE; prod push = APPLE_DEVELOPER_REQUIRED + SERVER_REQUIRED

## Resumen

```text
APPLE_SIGN_IN     = APP_SIDE_DONE + EXTERNAL_CONFIG + APPLE_DEVELOPER
UNIVERSAL_LINKS   = WEB_REQUIRED + APPLE_DEVELOPER (scheme APP_SIDE_DONE)
APNS_PROD         = APPLE_DEVELOPER + SERVER_REQUIRED (register APP_SIDE_DONE)
```

NO secrets · NO .p8 · NO web changes · NO remote Supabase config en este paquete.
