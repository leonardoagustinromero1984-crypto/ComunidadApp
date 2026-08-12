# KMP-IOS — Arquitectura compartida (post KMP-1)

```text
app (Android)
  └─ implementation(project(":shared"))
       ├─ commonMain  ← dominio puro + POC Compose/Nav/Supabase read
       ├─ androidMain ← pickers, clock, host helpers
       └─ iosMain     ← ComposeUIViewController, PHPicker, clock

iosApp (SwiftUI shell)
  └─ LeoVerShared.framework (static)
       └─ PocIosViewController() → PocLauncherApp
```

## Principios

1. Dominio puro en commonMain; Android importa (mismo paquete cuando es migración).
2. No Android types en commonMain.
3. Adapters de plataforma por interfaz / actual mínimo.
4. UI Compose Multiplatform solo POC hasta migración incremental.
5. Backend: sin migraciones SQL; Supabase productivo Android; POC M22 read-only KMP.

## iOS aún no “listo”

Gate cloud macOS pendiente de ejecución verde post KMP-2.
