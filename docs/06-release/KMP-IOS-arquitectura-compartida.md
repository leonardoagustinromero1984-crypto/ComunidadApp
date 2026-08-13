# KMP-IOS — Arquitectura compartida (post KMP-3)

```text
app (Android)
  └─ implementation(project(":shared"))
       ├─ commonMain
       │    ├─ dominio pets / onboarding / rules
       │    ├─ session / profile / pets presentation
       │    ├─ LeoVerSharedApp (CMP vertical)
       │    └─ POC M08/M22 (dev escape hatch)
       ├─ androidMain ← prefs, clock, ImagePicker, AndroidSessionMapper
       └─ iosMain     ← ComposeUIViewController, NSUserDefaults, PHPicker

iosApp (SwiftUI shell)
  └─ LeoVerShared.framework (static)
       └─ PocIosViewController() → LeoVerSharedApp (SESSION_STUB + SHARED_FAKE)
```

## Principios

1. Dominio puro en commonMain; no duplicar `PetAggregate`.
2. Sin Android types en commonMain.
3. Auth productivo permanece en `:app`; shared solo proyección / stub.
4. iOS demuestra vertical real de UI→state→domain→repository **con datos fake/stub etiquetados**.
5. Backend: sin SQL / sin cambios de schema; Supabase productivo Android intacto.

## Modos de datos

| Modo | Uso |
| ---- | --- |
| `REAL_REMOTE` | Reservado / Android productivo |
| `SESSION_STUB` | Sesión iOS / demos |
| `SHARED_FAKE` | Perfil y mascotas iOS / tests |

## Estado iOS

Shell + vertical Home/Perfil/Mascotas/Detalle funcional contra shared.
Gate cloud: ejecutar manualmente tras push KMP-3.
