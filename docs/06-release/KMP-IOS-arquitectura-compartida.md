# KMP-IOS — Arquitectura compartida (post KMP-26/27)

```text
SharedRemoteRuntime (1 SupabaseClient)
  → Pets: create / edit / health / archive / restore / deceased
  → L/F owner resolve + edit + media
  → Notification prefs + quiet hours + marketing
  → Push install register/revoke
  → Public content get_public_*
  → Apple Sign In APP_SIDE (external config required)
```

## Principios

1. Un solo SupabaseClient.
2. Lifecycle vía RPCs M08 — sin hard delete.
3. Quiet hours REAL_REMOTE (M06).
4. External SIWA / Universal Links / APNs prod = device-readiness doc.
5. Catálogo módulos oficiales M00–M27 (no M28).
6. KT-86501 workaround preservado.
7. M24 Pagos POSPUESTO.
