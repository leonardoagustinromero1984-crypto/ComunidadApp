# KMP-IOS — Arquitectura compartida (post KMP-10)

```text
iosApp SwiftUI
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (internal)
            Auth + Postgrest + Storage + Keychain
            → Session / Profile / Pets / LostFound / Adoption REAL_REMOTE
            → LostFound publish + M05 media WRITE REAL_REMOTE
            → MediaResolver (M05 READ) REAL_REMOTE
            + SharedRemoteImage (bytes → ImageBitmap)
            + IosImagePicker (PHPicker) → FileRef durable temp
```

## Principios

1. Un solo SupabaseClient.
2. Gateways/DTOs/runtime/Storage/`SupabaseM05MediaReadGateway` `internal`.
3. M05 contratos existentes (sin SQL/schema).
4. Media fail no borra alerta publicada ni bloquea detalle.
5. Signed URL solo temporal en memoria; clear en logout.
6. Fakes solo tests.
7. KT-86501: native cache disabled en iosSimulatorArm64 framework + test.
