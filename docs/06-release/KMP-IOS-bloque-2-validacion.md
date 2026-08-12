# KMP-IOS — Bloque 2 validación

## Arquitectura iOS elegida

**Compose Multiplatform embebido en SwiftUI host** (reutiliza POC existente).

Motivo: ya había `PocIosViewController` + `iosApp` Xcode; evita UI Swift duplicada.

## Pantallas iOS (shared Compose)

1. **Home compartido** — lista fake `PetAggregate` (Loading/Empty/Content/Error)
2. **POC launcher** — M22 / M08
3. Sesión stub (`SharedSessionStub`) — no Auth productivo

## Framework

- Nombre: `LeoVerShared` (static)
- Targets: `iosArm64`, `iosSimulatorArm64`
- Host: `iosApp/iosApp.xcodeproj` scheme `LeoVerKmpPoc`

## Validación Windows (pre-push)

| Check | Resultado |
| ----- | --------- |
| `:shared:allTests` | PASS — **32/32** |
| `compileLocalDebugKotlin` | PASS |
| iOS compile local | SKIPPED_WINDOWS |

## Cloud gate

Workflow: `.github/workflows/kmp-ios-validation.yml`  
Dispatch manual → Actions → KMP iOS Validation → main.

Resultado cloud: **PENDING_MANUAL_DISPATCH** hasta ejecución macOS verde.
