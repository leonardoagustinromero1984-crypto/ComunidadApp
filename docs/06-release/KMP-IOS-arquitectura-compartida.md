# KMP-IOS — Arquitectura compartida (post KMP-4)

```text
app (Android)
  └─ implementation(project(":shared"))
       ├─ commonMain
       │    ├─ domain pets / onboarding / LF+adoption rules
       │    ├─ session / profile / pets presentation
       │    ├─ lostfound / adoption presentation (SAFE UI)
       │    ├─ LeoVerSharedApp (CMP vertical)
       │    └─ POC M08/M22 (dev)
       ├─ androidMain ← prefs, clock, ImagePicker, AndroidSessionMapper
       └─ iosMain     ← ComposeUIViewController, NSUserDefaults, PHPicker

iosApp (SwiftUI shell)
  └─ LeoVerShared.framework
       └─ PocIosViewController() → LeoVerSharedApp
            SESSION_STUB + SHARED_FAKE (perfil/pets/LF/adopciones)
```

## Principios

1. Reglas canónicas KMP-1 reutilizadas; no duplicar.
2. Modelos públicos SAFE separados de posts Android / M09.
3. Sin Android/UIKit types en commonMain.
4. Auth productivo en `:app`; iOS stub/fake etiquetado.
5. Sin SQL / schema / RPC nuevos en este bloque.

## Navegación compartida

Home → Perfil | Mascotas | Alertas (Perdidos/Encontrados) | Adopciones → detalles.
