# IOS-PILOT-2 — Estrategia dispositivo real (sin Mac local obligatorio)

**Base SHA:** `33cf1c14aec66abf168244b4514a57c11be29419`  
**CI actual:** `.github/workflows/kmp-ios-validation.yml` — `xcodebuild` **iphonesimulator**, `CODE_SIGNING_ALLOWED=NO`. No exporta IPA firmado. No instala en iPhone.

## Separación clara

| Categoría | Estado actual |
| --------- | ------------- |
| CLOUD_COMPILATION | **YES** — shared + iosApp unsigned simulator GREEN |
| CLOUD_SIGNING_POSSIBLE_IF_CREDENTIALS_CONFIGURED | **POSIBLE en principio** — requiere Apple certs/profiles en secrets CI + job de archive/export; **no implementado hoy** |
| REAL_IPHONE_INSTALLATION | **NO desde CI actual** — no hay artefacto firmado instalable |
| LOCAL_MAC_REQUIRED_OR_NOT | **No obligatorio comprar Mac** si se usa firma cloud + TestFlight; Mac/Xcode sigue siendo el camino más simple para el primer cable |
| APP_STORE_CONNECT_REQUIRED_OR_NOT | **Requerido** para TestFlight (opción C); no requerido para cable USB con Mac (opción A) |

**No afirmar** que un artifact de GHA unsigned se puede instalar en iPhone.

## Opciones (ninguna ejecutada en esta fase)

### A) Mac propio + Xcode + iPhone

| | |
| - | - |
| Requisitos | Apple Developer Program, Team ID, cable/Wi‑Fi device, Xcode |
| Costo | Mac hardware + membresía anual |
| Complejidad | Baja operativa |
| Recomendación | Mejor para **primer smoke** firmado local |

### B) GitHub Actions macOS + signing seguro + artifact

| | |
| - | - |
| Requisitos | Membership, certs/profiles en GitHub Secrets / ASC API, workflow archive+export |
| Costo | Sin Mac propio; CI minutes + membresía |
| Complejidad | Media–alta (secrets, match/fastlane o manual export) |
| Instalación iPhone | El IPA/artifact **aún** necesita TestFlight, MDM o Mac para install — no “tap to install” mágico desde Actions |
| Recomendación | Viable como pipeline; **no** alcanza solo con el workflow actual |

### C) TestFlight / App Store Connect

| | |
| - | - |
| Requisitos | Membership, App Store Connect app record, build firmado (desde Mac o CI), testers |
| Costo | Membresía; sin Mac diario para testers |
| Complejidad | Media |
| Recomendación | **Preferida para piloto multi-tester sin comprar Mac** |

## Recomendación LeoVer

1. **No comprar Mac solo por esta etapa** si ya hay Apple Developer + se puede firmar vía CI o Mac prestado una vez.
2. Camino mínimo seguro: **Apple Developer Program → firmar build (A o B) → TestFlight (C)** para validación real.
3. Mantener GHA unsigned como **CLOUD_COMPILATION gate**; no confundir con device readiness.
4. External capabilities (Apple provider, AASA, APNs sender) siguen fuera hasta fase de activación.

```text
RECOMMENDED_PATH = Apple Developer + signed build + TestFlight
AVOID_BUYING_MAC_THIS_STAGE = YES (si hay alternativa de firma)
CI_TODAY_INSTALLS_ON_IPHONE = NO
```
