# KMP-IOS — Arquitectura compartida (post KMP-8)

```text
iosApp SwiftUI
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (internal)
            Auth + Postgrest + Keychain
            → Session / Profile / Pets / LostFound / Adoption REAL_REMOTE
            → LostFound publish REAL_REMOTE (media PARTIAL)
            + IosImagePicker (PHPicker) opcional
```

## Principios

1. Un solo SupabaseClient.
2. Gateways/DTOs/runtime `internal`.
3. Backend/RLS autoriza writes (`author_id = auth.uid()`).
4. UI SAFE: sin coords/PII; `contact_info` no vuelve a modelos de lectura.
5. Fakes solo tests; host sin fallback fake.
6. Media M05 no fingida si no está en shared.
