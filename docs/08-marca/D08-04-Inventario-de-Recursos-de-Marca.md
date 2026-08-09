# D08-04 — Inventario de recursos de marca LeoVer

**Versión:** 1.1 · **Fecha:** 2026-08-05  
**Fuente de procesamiento:** `app/src/main/res/drawable/logo_leover.jpg` (histórico)  
**Método:** flood-fill de blanco exterior (C# / System.Drawing LockBits), crop, normalización suave a paleta oficial, sin IA.

---

## 1. Oficial (guía)

| Nombre | Ruta | Dims / tamaño | Formato | Fondo | SHA-256 | Uso | Estado |
|--------|------|---------------|---------|-------|---------|-----|--------|
| LeoVer-Brand-Board-Oficial-v1.0.png | `assets/oficial/` | 1142267 B | PNG | sólido | `46A92D75EA79B2BBFD8EA16E1B80F7D3BEFA951D5D6CF3F562E2970F51A16204` | Guía | Oficial — no usar in-app |

---

## 2. Productivos documentales (v1)

| Nombre | Ruta | Dimensiones | Formato | Fondo | Uso | Estado | Reemplaza |
|--------|------|-------------|---------|-------|-----|--------|-----------|
| leover-logo-vertical-v1.png | `assets/productivos/logo-principal/` | 1493×1600 | PNG | transparente | Presentación vertical | Productivo | logo_leover.jpg |
| leover-logo-horizontal-v1.png | idem | 2771×836 | PNG | transparente | Encabezados | Productivo | — |
| leover-wordmark-v1.png | idem | 1200×289 | PNG | transparente | Wordmark solo | Productivo | — |
| leover-isotipo-v1.png | `assets/productivos/isotipo/` | 1024×1024 | PNG | transparente | Isotipo | Productivo | logo_leover_isotype.png |
| leover-isotipo-transparente-v1.png | idem | 1024×1024 | PNG | transparente | Copia canónica | Productivo | idem |
| leover-launcher-foreground-v1.png | `assets/productivos/launcher/` | 1024×1024 | PNG | transparente | Adaptive FG (safe zone) | Productivo | logo_leover_isotype en inset |
| leover-launcher-preview-v1.png | idem | 1024×1024 | PNG | crema | Verificación | Preview | — |
| leover-launcher-round-preview-v1.png | idem | 1024×1024 | PNG | crema/máscara | Verificación | Preview | — |
| leover-isotipo-monochrome-v1.png | `assets/productivos/monocromatico/` | 1024×1024 | PNG | transparente | Themed icon | Productivo | logo_leover_isotype_monochrome.png |
| leover-splash-v1.png | `assets/productivos/splash/` | 1600×1600 | PNG | crema `#FFF6EA` | Splash documental / Compose | Productivo | — |

Hashes SHA-256 (generados post-proceso): ver tabla §5.

---

## 3. Android (runtime)

| Nombre | Ruta | Uso | Estado |
|--------|------|-----|--------|
| leover_logo_official.png | `drawable-nodpi/` | BrandLogo vertical, login, onboarding | Productivo |
| leover_logo_horizontal.png | `drawable-nodpi/` | BrandLogo horizontal | Productivo |
| leover_isotype_official.png | `drawable-nodpi/` | Isotipo + splash animated icon | Productivo |
| leover_splash_logo.png | `drawable-nodpi/` | Splash full (disponible) | Productivo |
| leover_launcher_foreground.png | `drawable/` | Referenciado por `ic_launcher_foreground.xml` | Productivo |
| leover_isotype_monochrome.png | `drawable/` | Referenciado por `ic_launcher_monochrome.xml` | Productivo |
| ic_launcher*.xml | `mipmap-anydpi/` + `drawable/` | Adaptive / round / mono | Productivo |

**Eliminados de `app/src/main/res/drawable/` (ya sin referencias):**  
`logo_leover.jpg`, `logo_leover_isotype.png`, `logo_leover_isotype_monochrome.png`.

---

## 4. Históricos

| Nombre | Ruta | Estado |
|--------|------|--------|
| logo_leover.jpg (+ isotipos previos) | `assets/historicos/identidad-anterior/` | **IDENTIDAD ANTERIOR — REEMPLAZADA POR IDENTIDAD VISUAL OFICIAL V1.0** |

---

## 5. SHA-256 (recursos clave)

| Archivo | SHA-256 |
|---------|---------|
| leover-logo-vertical-v1.png | `1E153A49342906D1CBC24C327B61DDFE3CDC22C6A6B86E07AEC9EE8B544AA8B2` |
| leover-logo-horizontal-v1.png | `403332A170D7D44ED584FCC91E63CA68D7091798B4FC28B40629048894D7BB46` |
| leover-wordmark-v1.png | `252447CE2F11FF93D10630D5A4212287E2294E4CD21066DD057747DEE005738D` |
| leover-isotipo-v1.png | `8781D9CBBC4A4F73E0EEDE24FA4DDE5D31B5AD4D233FF91B12DCDE61DD384644` |
| leover-launcher-foreground-v1.png | `62038DDD009131D1AF72E0960226358618223796E642021D1DEAD3F2B4878A69` |
| leover-isotipo-monochrome-v1.png | `8870253DBBEFD4A766F0B898E2605E340E2959DC96437B2B86D86241104FA6EA` |
| leover-splash-v1.png | `1DC6BE6E7E76C408F66225F59F30BC2CEC2613F3A4765B5A5918216CDE2031B4` |
| Brand Board oficial | `46A92D75EA79B2BBFD8EA16E1B80F7D3BEFA951D5D6CF3F562E2970F51A16204` |

---

## 6. Referencias de código actualizadas

| Archivo | Antes | Ahora |
|---------|-------|-------|
| `BrandLogo.kt` | `R.drawable.logo_leover` | `leover_logo_official` / horizontal / isotype |
| `themes.xml` Splash | `@drawable/logo_leover` | fondo crema + `@drawable/leover_isotype_official` |
| `ic_launcher_foreground.xml` | `@drawable/logo_leover_isotype` | `@drawable/leover_launcher_foreground` |
| `ic_launcher_monochrome.xml` | `@drawable/logo_leover_isotype_monochrome` | `@drawable/leover_isotype_monochrome` |

Login / Register / Onboarding / SessionLoading usan `BrandLogo` (propagación automática).
