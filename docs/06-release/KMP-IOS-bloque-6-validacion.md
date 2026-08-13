# KMP-IOS — Bloque 6 validación

**HEAD base:** `005def3`
**Commit KMP-6:** (ver SHA post-push)

## Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | PASS — **116** tests (92 + 24) |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `compileKotlinIosSimulatorArm64` | PASS (sesión previa / misma base) |
| commonMain isolation | PASS |
| SQL / schema / APK | NO |

## Data modes iOS host

| Capa | Modo |
| ---- | ---- |
| SESSION | REAL_REMOTE |
| PROFILE | REAL_REMOTE |
| PETS | REAL_REMOTE |
| LOST_FOUND | SHARED_FAKE |
| ADOPTIONS | SHARED_FAKE |

FakeSession / FakeProfile / FakePets **no** usados en `PocIosViewController`.

Sin config: Unconfigured* repos siguen etiquetados REAL_REMOTE → Unavailable.

## ObjC

`SharedRemoteRuntime`, gateways, DTOs, remote repos = `internal`.
Entry Swift: `PocIosViewController()`.

## Gate

Manual: KMP iOS Validation → `main`.
