# KMP-IOS — Arquitectura compartida (post KMP-17/18/19)

```text
iosApp SwiftUI
  ├─ onOpenURL → offerDeepLinkUrl
  ├─ AppDelegate APNs token → onIosDeviceTokenHex
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (internal)
            Auth + Postgrest + Storage + Keychain
            → Session / Profile / Pets / LostFound / Adoption REAL_REMOTE
            → Apple Sign In (IDToken) APP_SIDE (backend apple disabled)
            → Push installation register/revoke M06
            → DeepLinkParser + landing (no public-by-code RPC)
            + SharedRemoteImage + IosImagePicker + AppleSignInIos
```

## Principios

1. Un solo SupabaseClient.
2. Un solo `SupabaseM05MediaUploadGateway` (Lost/Found + Pet avatar).
3. Gateways/DTOs/runtime `internal`.
4. Contratos existentes (sin SQL/schema).
5. Adoption media write = NOT_APPLICABLE (pet snapshot).
6. KT-86501: native cache disabled en iosSimulatorArm64 framework + test.
7. Deep links tipados — sin Associated Domains en este paquete.
8. Push: fingerprint SHA-256 only; permiso solo con CTA explícito.
