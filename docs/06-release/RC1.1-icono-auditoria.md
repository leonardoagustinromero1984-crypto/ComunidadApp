# RC1.1 — Auditoría del ícono launcher

**Fecha:** 2026-08-02  
**HEAD base:** `646862c` — onboarding RC1.1  
**Fuente visual oficial:** `app/src/main/res/drawable/logo_leover.jpg` (1254×1254, isotipo + wordmark LeoVer).

## Recursos previos

| Recurso | Estado anterior |
|---------|-----------------|
| `mipmap-anydpi/ic_launcher.xml` | Adaptive icon con `logo_leover.jpg` completo (isotipo + texto) al 10% inset |
| `mipmap-anydpi/ic_launcher_round.xml` | Idéntico al cuadrado |
| `drawable/ic_launcher_foreground.xml` | Vector genérico de huella **no referenciado** por el adaptive icon |
| `drawable/ic_launcher_background.xml` | Color `@color/leover_background` (#FFFBFE) |
| Legacy PNG (`mipmap-mdpi` …) | **Ausentes** (minSdk 26 → adaptive icon suficiente) |
| Flavor `local` | Sin override de ícono |

## Problemas detectados

1. **Foreground con logotipo completo:** incluía el wordmark “LeoVer”, ilegible en tamaños pequeños y recortado por máscaras circulares.
2. **Contenido fuera de zona segura:** isotipo + texto ocupaban casi todo el lienzo 108dp; Android recortaba bordes.
3. **Fondo blanco embebido en JPG:** el foreground rasterizado arrastraba blanco opaco, generando halos en launchers de color.
4. **Monocromático inválido:** reutilizaba el JPG a color; no apto para themed icons Android 13+.
5. **Inconsistencia:** existía un foreground vector alternativo (huella) distinto del adaptive icon real.

## Estrategia de corrección

1. Recortar **solo el isotipo** desde `logo_leover.jpg` → `logo_leover_isotype.png` (transparencia real, sin wordmark).
2. Aplicar **inset 18%** en foreground y monochrome para zona segura (66dp central).
3. Fondo plano blanco coherente con la marca (`ic_launcher_background.xml`).
4. Monocromático: silueta blanca sobre transparente derivada del isotipo (`logo_leover_isotype_monochrome.png`).
5. Unificar `ic_launcher` e `ic_launcher_round` con los mismos layers.
6. **versionCode 2**, **versionName 1.1-local** (suffix del flavor `local`).

## Archivos modificados

- `drawable/logo_leover_isotype.png` (nuevo, derivado del logo oficial)
- `drawable/logo_leover_isotype_monochrome.png` (nuevo)
- `drawable/ic_launcher_foreground.xml`
- `drawable/ic_launcher_background.xml`
- `drawable/ic_launcher_monochrome.xml` (nuevo)
- `mipmap-anydpi/ic_launcher.xml`
- `mipmap-anydpi/ic_launcher_round.xml`
- `app/build.gradle.kts` (versión localDebug)

**Sin cambios:** Manifest, onboarding, navegación, módulos, SQL, staging, producción.
