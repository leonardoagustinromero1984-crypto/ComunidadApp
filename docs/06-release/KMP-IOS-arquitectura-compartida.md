# KMP-IOS — Arquitectura compartida (post KMP-14/15/16)

```text
iosApp SwiftUI
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (internal)
            Auth + Postgrest + Storage + Keychain
            → Session / Profile / Pets / LostFound / Adoption READ REAL_REMOTE
            → LostFound publish + M05 media WRITE REAL_REMOTE
            → MediaResolver REAL_REMOTE
            → Adoption publish REAL_REMOTE (foto = pet snapshot)
            → Adoption applications candidate + shelter review REAL_REMOTE
            → Profile update + avatar legacy REAL_REMOTE
            → Pet create + PET_AVATAR M05 REAL_REMOTE
            + SharedRemoteImage + IosImagePicker
```

## Principios

1. Un solo SupabaseClient.
2. Un solo `SupabaseM05MediaUploadGateway` (Lost/Found + Pet avatar).
3. Gateways/DTOs/runtime `internal`.
4. Contratos existentes (sin SQL/schema).
5. Adoption media write = NOT_APPLICABLE (pet snapshot).
6. KT-86501: native cache disabled en iosSimulatorArm64 framework + test.
