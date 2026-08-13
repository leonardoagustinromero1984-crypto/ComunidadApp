# KMP-IOS — Bloque 8 validación

**HEAD base:** `a65d05b088330da59e5e1a390ca367b1ee4fb4f3`

**Commit KMP-8:** (ver SHA post-push)

## Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | PASS — **180** tests (153 base + 27 nuevos) |
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
| LOST/FOUND PUBLISH | REAL_REMOTE |
| MEDIA WRITE | PARTIAL |

Fake* host = NO. Un solo SupabaseClient.

## ObjC

Write gateways / DTOs / runtime = `internal`. Entry: `PocIosViewController()`.

## Gate siguiente

READY_TO_RUN_IOS_CLOUD_GATE
