# KMP-IOS — Bloque 7 validación

**HEAD base:** `296ec88238f3c37907336fc7d98362b850e2689e`

**Commit KMP-7:** (ver SHA post-push)

## Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | PASS — **153** tests (116 base + 37 nuevos) |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL |
| commonMain isolation | PASS |
| SQL / schema / APK / lint / JaCoCo | NO |

## Data modes iOS host

| Capa | Modo |
| ---- | ---- |
| SESSION | REAL_REMOTE |
| PROFILE | REAL_REMOTE |
| PETS | REAL_REMOTE |
| LOST_FOUND | REAL_REMOTE |
| ADOPTIONS | REAL_REMOTE |

FakeSession / FakeProfile / FakePets / FakeLostFound / FakeAdoption **no** en `PocIosViewController`.

Sin config: Unconfigured* siguen etiquetados REAL_REMOTE → Unavailable.

## ObjC

`SharedRemoteRuntime`, gateways, DTOs, remote repos = `internal`.
Entry Swift: `PocIosViewController()`.

## Gate siguiente

Manual: KMP iOS Validation → `main` (READY_TO_RUN_IOS_CLOUD_GATE).
