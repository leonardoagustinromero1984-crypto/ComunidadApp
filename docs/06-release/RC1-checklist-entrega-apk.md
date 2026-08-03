# RC1 — Checklist entrega APK

**Fecha:** 2026-08-02  
**SHA Git origen:** `702c914`

| # | Verificación | Estado |
|---|--------------|--------|
| 1 | Build `assembleLocalDebug` exitoso | ✓ BUILD SUCCESSFUL |
| 2 | APK generada en outputs Gradle | ✓ `app-local-debug.apk` |
| 3 | Copia en `artifacts/rc1/` | ✓ `LeoVer-RC1-local-debug.apk` |
| 4 | APK no versionada en Git | ✓ (`*.apk` + `/artifacts/`) |
| 5 | SHA-256 registrado | ✓ ver `RC1-apk-entrega.md` |
| 6 | Package verificado (aapt) | ✓ `com.comunidapp.app.local` |
| 7 | Versión verificada | ✓ `1.0-local` / code `1` |
| 8 | Permisos revisados | ✓ 6 permisos; sin cámara/ubicación |
| 9 | Secretos revisados | ✓ sin service_role/DB password |
| 10 | Staging no modificado | ✓ |
| 11 | Producción no modificada | ✓ |
| 12 | SQL no aplicado | ✓ |
| 13 | M24 ausente | ✓ |
| 14 | M28 inexistente | ✓ |
| 15 | Plan manual preparado | ✓ `RC1-ejecucion-prueba-manual.md` |
| 16 | Instalación documentada | ✓ `RC1-instalacion-android.md` |
| 17 | Entrega documentada | ✓ `RC1-apk-entrega.md` |
| 18 | Prueba física | **PENDIENTE** |
| 19 | `.gitignore` incluye `/artifacts/` | ✓ |
| 20 | Working tree limpio post-commit | pendiente verificación final |

## Veredicto

**RC1 APK LOCALDEBUG GENERADA** — lista para transferencia manual al dispositivo.
