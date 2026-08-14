# KMP-IOS — Arquitectura compartida (post KMP-23/24/25)

```text
iosApp SwiftUI
  └─ PocIosViewController()
       └─ SharedRemoteRuntime (1 SupabaseClient)
            → Pets profile + health (m08_update_pet_health)
            → L/F owner edit + media replace (PostgREST + M05)
            → Notification prefs (m06_get/update_preference)
            → Push install register/revoke + permission UX
            → Public content get_public_*
            → Apple Sign In APP_SIDE (backend config required)
```

## Principios

1. Un solo SupabaseClient.
2. Health nunca en PublicContent.
3. KMP-24 ≠ M24 Pagos (pagos POSPUESTO).
4. Gateways/DTOs internal; commonMain sin UIKit/android.
5. KT-86501: native cache disabled iosSimulatorArm64 framework + test.
6. Preferences: categorías del RPC; email write = false.
