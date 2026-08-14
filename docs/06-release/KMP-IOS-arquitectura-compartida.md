# KMP-IOS — Arquitectura compartida (post IOS-PILOT-1)

```text
SharedRemoteRuntime (1 SupabaseClient)
  → Auth / Profile / Pets (CRUD+health+lifecycle)
  → LostFound / Adoptions
  → Public content + DeepLinkParser
  → Push prefs + quiet hours (+ days)
  → Logout: revoke push + clear pending deep links + media cache
```

## Pilot hardening

1. Stable install id `leover-ios-default-install`
2. Public `RemoteUrl` media without auth; private assets require session
3. ErrorSanitizer on image pick failures
4. Busy guards on write / Activar notificaciones
5. Catálogo M00–M27 · no KMP-28 · M24 Pagos POSPUESTO
6. KT-86501 workaround preservado
