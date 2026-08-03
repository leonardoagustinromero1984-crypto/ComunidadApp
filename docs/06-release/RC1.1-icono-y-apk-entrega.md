# RC1.1 — Entrega ícono corregido y APK localDebug

**Proyecto:** LeoVer / ComunidadApp  
**Fecha de generación:** 2026-08-02 23:45 (hora local del build)  
**Propósito:** APK RC1.1 con ícono launcher corregido + onboarding de primer ingreso.

## Problema original

El adaptive icon usaba `logo_leover.jpg` completo (isotipo + wordmark) con inset 10%, provocando recorte del texto, halos blancos del JPG y monocromático inválido para Android 13+.

## Solución

Recorte del **isotipo oficial** desde `logo_leover.jpg`, transparencia real, inset 18% de zona segura, fondo blanco plano, silueta monocromática blanca derivada del isotipo.

## Origen Git

| Campo | Valor |
|-------|-------|
| SHA fuente (pre-commit icono) | `646862c` |
| Commit previo | `feat(onboarding): add action-oriented first-run experience` |
| Rama | `main` |

## Build Gradle

| Campo | Valor |
|-------|-------|
| Comando compile | `.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain` |
| Resultado compile | **BUILD SUCCESSFUL** |
| Comando APK | `.\gradlew.bat assembleLocalDebug --no-configuration-cache --max-workers=1 --console=plain` |
| Resultado APK | **BUILD SUCCESSFUL** (14m 26s) |
| Variante | `localDebug` |

## APK

| Campo | Valor |
|-------|-------|
| Nombre entrega | `LeoVer-RC1.1-local-debug.apk` |
| Ruta build (Gradle) | `app\build\outputs\apk\local\debug\app-local-debug.apk` |
| Ruta copia local | `artifacts\rc1.1\LeoVer-RC1.1-local-debug.apk` |
| Tamaño | 32 255 503 bytes (~30,8 MiB) |
| SHA-256 | `CDAB94DB2C733D40B4D2FD9EAD6D2EA68DC2756A83C1DD9316CB8C2B83AE6266` |
| En Git | **NO** (`/artifacts/` ignorado) |

## Identidad de paquete (aapt dump badging)

| Campo | Valor |
|-------|-------|
| applicationId | `com.comunidapp.app.local` |
| versionName | `1.1-local` |
| versionCode | `2` |
| minSdk | `26` |
| targetSdk | `36` |
| Label visible | **LeoVer Local** |
| debuggable | **sí** |
| icon (manifest) | `res/mipmap-anydpi-v21/ic_launcher.xml` |

## Recursos de ícono en APK

| Recurso | Presente |
|---------|----------|
| Adaptive icon (`ic_launcher.xml`) | Sí |
| Round icon (`ic_launcher_round.xml`) | Sí |
| Foreground (`ic_launcher_foreground.xml` + isotipo PNG) | Sí |
| Background (`ic_launcher_background.xml`) | Sí |
| Monochrome (`ic_launcher_monochrome.xml` + silueta PNG) | Sí |

## Permisos (sin cambios respecto RC1)

`INTERNET`, `POST_NOTIFICATIONS`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, FCM, receiver interno.

## Contenido funcional

- Onboarding RC1.1 incluido
- M00–M27 consolidados
- M24 pospuesto · M28 inexistente
- Sin SQL · staging no modificado · producción no afectada

## Referencia RC1 anterior

APK RC1 permanece en `artifacts\rc1\LeoVer-RC1-local-debug.apk` (versionCode 1). RC1.1 es actualizable sobre RC1 (mismo applicationId, firma debug).

Ver también: [`RC1-apk-entrega.md`](RC1-apk-entrega.md).

## Prueba física

**PENDIENTE** — ver [`RC1.1-plan-prueba-manual.md`](RC1.1-plan-prueba-manual.md).
