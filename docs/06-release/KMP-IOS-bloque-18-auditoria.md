# KMP-IOS — Bloque 18 auditoría (Apple Sign In app-side)

Fuente: `AuthSessionGateway.signInWithAppleIdToken` + `AppleSignInIosController`.

## ESTADO

```text
APPLE_SIGN_IN = APP_SIDE_READY_BACKEND_CONFIG_REQUIRED
```

Motivo: `supabase/config.toml` → `[auth.external.apple] enabled = false`.

## FLUJO APP

1. `isAppleSignInAvailable()` → true solo iosMain.
2. ASAuthorization → `identityToken` + raw nonce.
3. `signInWith(IDToken) { provider = Apple; nonce = rawNonce }`.
4. Fallos tipados: `Cancelled`, `ConfigurationRequired`, `InvalidCredentials`.

## CAPABILITY

```text
DEVICE_CAPABILITY_REQUIRED
```

No se agregó `.entitlements` Sign in with Apple (rompe unsigned simulator). Habilitar en device/provisioning real.

## Swift

`AppleAuthBridge.swift` opcional (fallback). Kotlin/Native AuthenticationServices es el path primario.
