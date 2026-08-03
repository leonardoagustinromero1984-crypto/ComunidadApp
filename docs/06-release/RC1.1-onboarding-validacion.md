# RC1.1 — Validación de onboarding

**Fecha:** 2026-08-02  
**Prueba física:** no ejecutada (PENDIENTE)

## Tests unitarios focalizados

Archivo: `app/src/test/java/com/comunidapp/app/viewmodel/FirstRunOnboardingViewModelTest.kt`

Cobertura principal:
- Auto-show para usuario nuevo; no auto-show para `COMPLETED` / `SKIPPED`
- Reanudación `IN_PROGRESS`
- Comenzar, explorar primero, omitir
- Indicadores 1/2/3 de 3
- Selección de intención y rutas NavRoutes
- Completar marca `COMPLETED` y navegación única
- Reinicio visual de tutorial
- Ayudas contextuales vistas una vez y reinicio
- Fallo de persistencia no bloquea skip
- Zona aproximada sin coordenadas
- Copy “LeoVer”; sin M24 en rutas

## Compilación

Comando: `.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain`

**Resultado:** PASS (2026-08-02)

## Tests ejecutados

Comando: `.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.viewmodel.FirstRunOnboardingViewModelTest" ...`

**Resultado:** 16/16 PASS

## Veredicto técnico

ONBOARDING DE PRIMER INGRESO IMPLEMENTADO — tests focalizados PASS, compilación Kotlin PASS. Prueba física **PENDIENTE**.

| Ítem | Estado |
|------|--------|
| SQL creado | NO |
| Migraciones | NO |
| Staging modificado | NO |
| Producción modificada | NO |
| M24 iniciado | NO |
| M28 creado | NO |
| APK generada | NO |
| Permisos nuevos en Manifest | NO |
| Roles automáticos | NO |

## Veredicto técnico

Pendiente de resultado de tests y compilación en entrega final.
