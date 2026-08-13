# KMP-IOS — Bloque 6 validación

**HEAD base:** `005def3`

**KMP-6 SHA:** `296ec88238f3c37907336fc7d98362b850e2689e`

**GitHub Actions Gate #8:** PASS (macOS real)

**KMP-6 = CLOSED GREEN**

## Windows

| Check | Resultado |
| ----- | --------- |
| `:shared:testAndroidHostTest` | PASS — **116** tests (92 + 24) |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |
| `compileKotlinIosSimulatorArm64` | PASS (sesión previa / misma base) |
| commonMain isolation | PASS |
| SQL / schema / APK | NO |

## Data modes iOS host (al cierre KMP-6)

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

GitHub Actions KMP iOS Validation **#8** → PASS REAL macOS.

## Sucesor

KMP-7 conecta LOST_FOUND + ADOPTIONS a REAL_REMOTE (ver `KMP-IOS-bloque-7-validacion.md`).
