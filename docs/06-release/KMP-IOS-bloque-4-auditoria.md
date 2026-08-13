# KMP-IOS — Bloque 4 auditoría

**HEAD base:** `b84a8db`
**WIP M09/decoding/M29:** preservado (fuera de scope)

| Área | Clasificación | Notas |
| ---- | ------------- | ----- |
| `LostFoundCaseType` / `LostFoundCaseStatus` / `LostFoundStatusRules` | SHARED | KMP-1 — no recrear |
| `AdoptionListingStatus` / `AdoptionStatusRules` | SHARED | KMP-1 — no recrear |
| Session / profile / pets vertical KMP-3 | SHARED | Extender shell |
| `ErrorSanitizer` / `VerticalLoadState` | SHARED | Reutilizar |
| `PetAggregate` | SHARED | No duplicar |
| Presentación LF/Adoption (summary/detail/repo) | READY_TO_MOVE → este bloque | commonMain + fake |
| `LostFoundPost` / `AdoptionPost` Android | ANDROID_ONLY | WIP public_code |
| Repos Supabase / DataProvider | ANDROID_ONLY | ADAPTER_REQUIRED futuro |
| M09 RPC / decoding | ANDROID_ONLY + DEFERRED | No tocar |
| Alert map / GPS / `PublicAlertLocation` | ANDROID_ONLY + DEFERRED | Sin geo compleja |
| Applications / completion adopción | ANDROID_ONLY + DEFERRED | Sin formulario remoto |
| Auth iOS / Keychain / REAL_REMOTE | DEFERRED | iOS = SHARED_FAKE |
| Enums Android (`PAUSED`, sin `CLOSED` LF) | DUPLICATED (parcial) | Shared = canónico KMP |

## Decisión Bloque 4

- Contratos + mappers seguros + fakes deterministas en commonMain.
- iOS: **SHARED_FAKE** (alertas/perdidos/encontrados/adopciones).
- Android productivo intacto; sin SQL/RPC/schema.
- Separación estricta DOMAIN INTERNAL vs PUBLIC/SAFE UI.
