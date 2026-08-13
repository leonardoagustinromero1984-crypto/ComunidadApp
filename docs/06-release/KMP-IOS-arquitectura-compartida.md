# KMP-IOS — Arquitectura compartida (post KMP-5)

```text
iosApp (SwiftUI)
  └─ LeoVerShared.framework
       └─ PocIosViewController
            ├─ AuthRepository REAL_REMOTE (supabase-kt + Keychain)
            ├─ Profile/Pets/LF/Adoption SHARED_FAKE
            └─ LeoVerSharedApp → Login | Home → …

app (Android)
  └─ Auth productivo :app (GoTrue) — sin cambios fat
  └─ shared (session contracts, AndroidSessionMapper, secure storage adapter)
```

## Principios

1. Auth email/password compartido vía gateway + supabase-kt.
2. Tokens fuera de UI models; Keychain en iOS.
3. FakeSessionRepository solo tests/determinismo — no host iOS principal.
4. Perfil/contenido vertical aún fake (cierre auth primero).
5. Sin SQL / schema / service_role.
