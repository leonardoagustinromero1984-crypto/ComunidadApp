# KMP-IOS — Arquitectura compartida (post KMP-7)

```text
iosApp SwiftUI
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (internal)
            Auth + Postgrest + Keychain
            → Session / Profile / Pets / LostFound / Adoption REAL_REMOTE
```

## Principios

1. Un solo SupabaseClient para auth + lecturas.
2. Repos/DTOs/gateways `internal` — no export ObjC.
3. Backend/RLS autoriza; cliente no finge ownership.
4. Fakes solo tests; host no hace fallback fake.
5. Sin SQL/schema en este bloque.
6. Modelos UI SAFE: sin coords exactas ni PII de contacto.
