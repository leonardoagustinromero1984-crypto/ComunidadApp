# KMP-IOS — Arquitectura compartida (post KMP-20/21/22)

```text
iosApp SwiftUI
  ├─ onOpenURL → offerDeepLinkUrl
  ├─ AppDelegate APNs token → onIosDeviceTokenHex
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (internal)
            Auth + Postgrest + Storage + Keychain
            → Session / Profile / Pets / LostFound / Adoption REAL_REMOTE
            → PublicContent get_public_* REAL_REMOTE
            → Pet edit m08_update_pet_profile + avatar
            → L/F owner markResolved (PostgREST)
            → Apple Sign In (IDToken) APP_SIDE (backend apple disabled)
            → Push installation register/revoke M06
            → DeepLinkParser → SharedPublicContentScreen
            + SharedRemoteImage + IosImagePicker + AppleSignInIos
```

## Principios

1. Un solo SupabaseClient.
2. Un solo `SupabaseM05MediaUploadGateway` (Lost/Found + Pet avatar).
3. Gateways/DTOs/runtime `internal`.
4. Contratos existentes (sin SQL/schema).
5. Adoption media write = NOT_APPLICABLE (pet snapshot).
6. KT-86501: native cache disabled en iosSimulatorArm64 framework + test.
7. Deep links tipados — Universal Links web association blocked.
8. Public content: RPCs sanitize; no PII/coords; sin fallback a query privada.
9. Push: fingerprint SHA-256 only; permiso solo con CTA explícito.
