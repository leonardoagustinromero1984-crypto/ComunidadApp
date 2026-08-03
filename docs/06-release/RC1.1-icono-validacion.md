# RC1.1 — Validación de ícono launcher

**Fecha:** 2026-08-02  
**Build asociado:** RC1.1 localDebug (posterior a generación APK)

## Controles previos al APK

| # | Control | Resultado |
|---|---------|-----------|
| 1 | XML adaptativos válidos | PASS — `ic_launcher.xml`, `ic_launcher_round.xml` |
| 2 | Sin referencias rotas | PASS — foreground/background/monochrome resuelven |
| 3 | Manifest apunta a `@mipmap/ic_launcher` | PASS — sin cambios |
| 4 | `ic_launcher` ≡ `ic_launcher_round` (layers) | PASS — mismos 3 drawables |
| 5 | Recurso monocromático presente | PASS — `ic_launcher_monochrome.xml` → silueta blanca |
| 6 | Sin imágenes innecesarias gigantes | PASS — isotipo ~700×700 px derivado del logo 1254² |
| 7 | Sin información sensible en recursos | PASS |
| 8 | Sin texto pequeño en ícono | PASS — solo isotipo, sin wordmark |
| 9 | Zona segura 18% inset | PASS — margen ~19,4 dp en lienzo 108 dp |
| 10 | Isotipo no incluye wordmark | PASS — recorte inferior 56% del logo |

## Archivos modificados

| Archivo | Acción |
|---------|--------|
| `drawable/logo_leover_isotype.png` | Creado (derivado oficial) |
| `drawable/logo_leover_isotype_monochrome.png` | Creado |
| `drawable/ic_launcher_foreground.xml` | Actualizado (inset isotipo) |
| `drawable/ic_launcher_background.xml` | Actualizado (blanco plano) |
| `drawable/ic_launcher_monochrome.xml` | Creado |
| `mipmap-anydpi/ic_launcher.xml` | Corregido |
| `mipmap-anydpi/ic_launcher_round.xml` | Corregido |
| `app/build.gradle.kts` | versionCode 2, versionName 1.1 |

## Legacy PNG

**No requeridos:** `minSdk = 26` (introducción de adaptive icons). Recursos en `mipmap-anydpi-v21` empaquetados en APK.

## Compilación previa

`compileLocalDebugKotlin` — **BUILD SUCCESSFUL**

Tests onboarding 16/16 — evidencia vigente del commit `646862c` (sin cambios Kotlin funcionales).

## Veredicto

Ícono adaptativo corregido — listo para empaquetar RC1.1.
