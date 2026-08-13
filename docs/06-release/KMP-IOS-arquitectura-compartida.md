# KMP-IOS — Arquitectura compartida (post KMP-11/12/13)

```text
iosApp SwiftUI
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (internal)
            Auth + Postgrest + Storage + Keychain
            → Session / Profile / Pets / LostFound / Adoption READ REAL_REMOTE
            → LostFound publish + M05 media WRITE REAL_REMOTE
            → MediaResolver (M05 READ + profile avatar path) REAL_REMOTE
            → Adoption publish (m09_create_…) REAL_REMOTE
            → Adoption applications (submit/withdraw/list mine) REAL_REMOTE
            → Profile update + avatar legacy write REAL_REMOTE
            + SharedRemoteImage (bytes → ImageBitmap)
            + IosImagePicker (PHPicker) → FileRef durable temp
```

## Principios

1. Un solo SupabaseClient.
2. Gateways/DTOs/runtime/Storage/`SupabaseM05Media*Gateway` / avatar upload `internal`.
3. Contratos existentes (sin SQL/schema).
4. Adoption media write = PARTIAL (foto vía pet; sin inventar M05 adoption write).
5. Signed URL solo temporal en memoria; clear/invalidate en logout/avatar change.
6. Fakes solo tests.
7. KT-86501: native cache disabled en iosSimulatorArm64 framework + test.
