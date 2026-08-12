# KMP/iOS — Auditoría actual

**Fecha:** 2026-08-12  
**HEAD auditado:** `ddea2b2` (`ci(kmp): add macOS iOS validation gate`)  
**Nota Git:** el commit RC1.1 `5986a01` está en el historial; HEAD actual ya incluye POC KMP + gate iOS.  
**Working tree al auditar:** dirty con WIP M09/public_code/decoding/M29 (preservado, fuera de scope KMP).

## Tabla de estado

| Área                  | Estado        | Evidencia |
| --------------------- | ------------- | --------- |
| Gradle KMP            | LISTO         | `:shared` en `settings.gradle.kts`; plugins KMP + AGP multiplatform library |
| Targets iOS           | PARCIAL       | `iosArm64`, `iosSimulatorArm64` (no `iosX64`) — adecuado a Apple Silicon CI |
| commonMain            | PARCIAL       | POC m08/m22 + dominio productivo M08/onboarding/m23 (post KMP-1) |
| commonTest            | PARCIAL       | POC tests + `SharedDomainFoundationTest` |
| androidMain           | PARCIAL       | ImagePicker Android, PlatformClock, PocPlatform |
| iosMain               | PARCIAL       | PocIosEntry, IosImagePicker PHPicker, PlatformClock |
| Compose Multiplatform | PARCIAL       | POC UI M08/M22 + launcher; no UI productiva |
| iosApp                | PARCIAL       | Host SwiftUI mínimo `LeoVerKmpPoc` + scheme compartido |
| Framework             | LISTO         | `LeoVerShared` static via `binaries.framework` |
| Networking            | PARCIAL       | Ktor + Supabase KMP en common (POC M22 read); app productiva Android-only |
| Auth                  | ANDROID_ONLY  | Auth productiva en app; iOS stub/sesión simulada |
| Serialización         | LISTO         | kotlinx.serialization en shared |
| Coroutines            | LISTO         | kotlinx-coroutines-core |
| ViewModels            | PARCIAL       | Solo POC VMs en common |
| Navegación            | PARCIAL       | Navigation Compose MP solo POC M08 |
| Persistencia local    | ANDROID_ONLY  | DataStore/Room en app |
| Archivos              | PARCIAL       | `FileRef` + picker adapters POC |
| Cámara                | ANDROID_ONLY  | — |
| Ubicación             | ANDROID_ONLY  | — |
| Notificaciones        | ANDROID_ONLY  | FCM |
| CI macOS              | PARCIAL       | `.github/workflows/kmp-ios-validation.yml` (workflow_dispatch) |

## Source sets encontrados

```text
shared/src/commonMain
shared/src/commonTest
shared/src/androidMain
shared/src/iosMain
```

No hay `iosTest` / `iosX64Main` dedicados.

## POCs previos reutilizados

- POC1 M22 catálogo (models/domain/repo/VM/UI/tests)
- POC2 M08 nav + FileRef + picker
- `PocLauncherApp` + `PocIosViewController`
- Workflow `kmp-ios-validation.yml`

## Bloqueadores

- WIP M09/decoding en working tree (no mezclar con KMP).
- Sin `gh` CLI local para auto-dispatch.
- Hardware local Windows: iOS solo vía GitHub Actions macOS.
