# D08-06 — Auditoría de reemplazo de logo (v1.0)

**Fecha:** 2026-08-05 · **Rama:** main · **HEAD base:** `5986a01`

## Resumen de referencias (antes → después)

| Archivo | Uso | Antes | Ahora |
|---------|-----|-------|-------|
| `BrandLogo.kt` | Login, register, onboarding, session loading | `logo_leover` | `leover_logo_official` (+ variantes) |
| `LoginScreen.kt` | Presentación | vía BrandLogo | vía BrandLogo |
| `RegisterScreen.kt` | Presentación | vía BrandLogo | vía BrandLogo |
| `FirstRunOnboardingScreen.kt` | Welcome | vía BrandLogo | vía BrandLogo |
| `SessionLoadingScreen.kt` | Carga sesión | vía BrandLogo | vía BrandLogo |
| `themes.xml` | Splash API | `logo_leover` | fondo `brand_cream` + `leover_isotype_official` |
| `ic_launcher_foreground.xml` | Adaptive FG | `logo_leover_isotype` | `leover_launcher_foreground` |
| `ic_launcher_monochrome.xml` | Themed | `logo_leover_isotype_monochrome` | `leover_isotype_monochrome` |
| `ic_launcher.xml` / round | Manifest | sin cambio de id | `@mipmap/ic_launcher` |
| `LeoverNotificationHelper.kt` | Small icon | `@mipmap/ic_launcher` | sin cambio |

## Método de fondo

Flood-fill BFS desde bordes sobre píxeles casi blancos (umbral 248), fringe anti-alias; blancos internos (ojos, bigotes, huella, venas) preservados por no conectividad al exterior.

## Confirmaciones

- Sin referencias productivas a `logo_leover*` en `app/`.
- Sin `ComunidadApp` visible en UI (`app/src/main` kt/xml).
- Recursos antiguos en `docs/08-marca/assets/historicos/identidad-anterior/`.
- applicationId / package / minSdk / firma: no modificados.
