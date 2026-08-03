# RC1.1 — Checklist de entrega

**Fecha:** 2026-08-02

## Ícono

- [x] Isotipo oficial recortado desde `logo_leover.jpg`
- [x] Adaptive icon foreground con zona segura 18%
- [x] Background plano blanco
- [x] Monochrome (Android 13+ themed icon)
- [x] Round icon coherente con square adaptive
- [x] Sin wordmark en launcher
- [x] Manifest sin cambios (`@mipmap/ic_launcher`)

## Versión

- [x] versionCode = 2
- [x] versionName = 1.1-local (suffix flavor)
- [x] applicationId = com.comunidapp.app.local

## Build

- [x] compileLocalDebugKotlin PASS
- [x] assembleLocalDebug PASS (una sola ejecución)
- [x] APK copiada a `artifacts\rc1.1\LeoVer-RC1.1-local-debug.apk`
- [x] SHA-256 registrado
- [x] aapt badging verificado

## Git / seguridad

- [x] APK **no** versionada
- [x] `/artifacts/` ignorado
- [x] Sin SQL / migraciones
- [x] Staging no modificado
- [x] Producción no afectada
- [x] M24 pospuesto · M28 inexistente

## Documentación

- [x] RC1.1-icono-auditoria.md
- [x] RC1.1-icono-validacion.md
- [x] RC1.1-icono-y-apk-entrega.md
- [x] RC1.1-plan-prueba-manual.md
- [x] RC1.1-checklist-entrega.md (este archivo)
- [x] RC1-apk-entrega.md actualizado (referencia RC1.1)
- [x] RC1-backlog-hallazgos.md actualizado

## Pendiente

- [ ] Prueba física IC-01–IC-08
- [ ] Prueba física OB-01–OB-17
- [ ] Prueba física RC1-01–RC1-17
