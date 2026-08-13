# KMP-IOS — Bloque 7 validación

**HEAD base:** `296ec88238f3c37907336fc7d98362b850e2689e`

**KMP-7 SHA:** `a65d05b088330da59e5e1a390ca367b1ee4fb4f3`

**GitHub Actions Gate #9:** PASS (macOS real)

**KMP-7 = CLOSED GREEN**

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

## Sucesor

KMP-8: publish Lost/Found REAL_REMOTE (media PARTIAL).
