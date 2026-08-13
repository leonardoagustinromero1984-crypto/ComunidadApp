# KMP-IOS — Arquitectura compartida (post KMP-9)

```text
iosApp SwiftUI
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (internal)
            Auth + Postgrest + Storage + Keychain
            → Session / Profile / Pets / LostFound / Adoption REAL_REMOTE
            → LostFound publish + M05 media WRITE REAL_REMOTE
            + IosImagePicker (PHPicker) → FileRef durable temp
```

## Principios

1. Un solo SupabaseClient.
2. Gateways/DTOs/runtime/Storage `internal`.
3. M05 contratos existentes (sin SQL/schema).
4. Media fail no borra alerta publicada.
5. Fakes solo tests.
