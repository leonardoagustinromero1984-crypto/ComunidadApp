# IOS-PILOT-2 — Identidad y signing (auditoría repo)

**Base SHA:** `33cf1c14aec66abf168244b4514a57c11be29419`  
**Fuente:** solo archivos del repositorio. Sin modificar signing / Team / capabilities.

## Identidad actual

| Ítem | Valor en repo | Clasificación |
| ---- | ------------- | ------------- |
| Target / product | `LeoVerKmpPoc` (`LeoVerKmpPoc.app`) | READY |
| Scheme | `LeoVerKmpPoc` | READY |
| Bundle identifier | `com.comunidapp.leover.kmppoc` | READY |
| Display name | `LeoVer KMP POC` | READY |
| Deployment target | iOS **16.0** | READY |
| Device family | iPhone + iPad (`1,2`) | READY |
| `DEVELOPMENT_TEAM` | `""` (vacío Debug y Release) | APPLE_DEVELOPER_REQUIRED |
| Signing Style | `Automatic` | READY (estilo); Team vacío → no firma device |
| `CODE_SIGN_ENTITLEMENTS` | no referenciado | MISSING |
| Archivo `.entitlements` | **ausente** en `iosApp/` | MISSING |
| Info.plist | `iosApp/iosApp/Info.plist` | READY |
| URL scheme `leover` | `CFBundleURLSchemes` = `leover` | READY |
| Custom scheme uso | `leover://…` (parser shared) | READY |
| Associated Domains | omitido a propósito (comentario en Info.plist) | APPLE_DEVELOPER_REQUIRED + EXTERNAL_CONFIG_REQUIRED (web AASA) |
| Sign in with Apple entitlement | no presente | APPLE_DEVELOPER_REQUIRED |
| `aps-environment` | no presente | APPLE_DEVELOPER_REQUIRED |
| Push Notifications capability | no presente en proyecto | APPLE_DEVELOPER_REQUIRED |
| Cloud CI signing | `CODE_SIGNING_ALLOWED=NO` (simulator) | NOT_APPLICABLE (gate compile-only) |

## Evidencia clave

- `iosApp/iosApp.xcodeproj/project.pbxproj` — `PRODUCT_BUNDLE_IDENTIFIER`, `DEVELOPMENT_TEAM=""`, `CODE_SIGN_STYLE = Automatic`
- `iosApp/iosApp/Info.plist` — scheme `leover`; sin Associated Domains
- `.github/workflows/kmp-ios-validation.yml` — build unsigned simulator

## SIGN_IN_WITH_APPLE

| Capa | Estado |
| ---- | ------ |
| APP_SIDE | **READY** (parcial hacia device: falta entitlement) — AuthenticationServices K/N, nonce + SHA-256, ID token → `signInWith(IDToken)` + `provider = Apple`, sesión Supabase, Keychain iOS, logout + revoke instalación |
| APPLE_DEVELOPER | **REQUIRED** — App ID con Sign in with Apple; Team; provisioning; entitlement en target |
| SUPABASE | **EXTERNAL_CONFIG_REQUIRED** — `[auth.external.apple] enabled = false` en `supabase/config.toml`; secret vía env (no en repo) |
| REAL_DEVICE | **NOT_VALIDATED** |

### Services ID

Flujo actual del repo: **nativo only** — `ASAuthorizationAppleIDProvider` → `identityToken` + raw nonce → `AuthSessionGateway.signInWithAppleIdToken` → Supabase `signInWith(IDToken) { provider = Apple }`.

**No se requiere Services ID** para este flujo nativo de ID token.  
Services ID sería necesario si se agregara OAuth web / redirect browser; **no está en el path actual**.

Client ID típico a configurar en Supabase (fuera del repo, cuando se active): **Bundle ID** `com.comunidapp.leover.kmppoc` (App ID), no inventar otro ID aquí.

## Qué NO hacer en esta fase

No setear Team, no crear `.entitlements`, no habilitar provider remoto, no agregar secrets.
