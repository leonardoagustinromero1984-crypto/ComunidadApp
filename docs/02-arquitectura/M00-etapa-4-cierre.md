# M00 — Cierre Etapa 4: Configuración, observabilidad y seguridad base

**Fecha:** 2026-07-14  
**Rama:** `m00/etapa-4-config-observabilidad`  
**Módulo:** M00 — Fundación Técnica  
**Estado de entrada:** Etapa 3 aprobada (`m00/etapa-3-calidad-ci` @ `f6d6b12`)  

---

## 1. Estado inicial de Git (Bloque 1)

| Ref | SHA / nota |
|-----|------------|
| `main` | `98d287c` |
| `m00/etapa-3-calidad-ci` | `f6d6b12` — base limpia (lint + CI, sin WIP) |
| `m00/etapa-4-config-observabilidad` | creada desde etapa-3; tip spec `f6049eb` |
| `wip/gps-mapas-pagos` | `68ceb82` — WIP preservado, **no** mezclado |
| `backup/pre-m00-etapa-3` | `830140d` — respaldo previo |

**Confirmación:** no se incorporó GPS, mapas ni pagos funcionales a esta rama. Supabase sigue siendo el único backend.

---

## 2. Archivos creados

| Archivo |
|---------|
| `app/src/main/java/com/comunidapp/app/core/config/AppConfig.kt` |
| `app/src/main/java/com/comunidapp/app/core/config/AppConfigProvider.kt` |
| `app/src/main/java/com/comunidapp/app/core/featureflags/FeatureFlags.kt` |
| `app/src/main/java/com/comunidapp/app/core/logging/AppLogger.kt` |
| `app/src/main/java/com/comunidapp/app/core/result/AppResult.kt` |
| `app/src/main/java/com/comunidapp/app/ui/components/state/FoundationStates.kt` |
| `app/src/main/res/xml/network_security_config.xml` |
| `app/src/test/java/com/comunidapp/app/core/config/AppConfigProviderTest.kt` |
| `app/src/test/java/com/comunidapp/app/core/logging/AppLoggerSanitizeTest.kt` |
| `app/src/test/java/com/comunidapp/app/core/result/AppErrorMapperTest.kt` |
| `docs/03-modulos/M00-Etapa-4-Configuracion-Observabilidad-y-Cierre.md` (spec, commit previo) |
| `docs/02-arquitectura/M00-etapa-4-cierre.md` (este) |
| `docs/02-arquitectura/M00-cierre-final.md` |

## 3. Archivos modificados

| Archivo | Motivo |
|---------|--------|
| `app/src/main/AndroidManifest.xml` | `networkSecurityConfig` |
| `app/src/main/java/.../LeoverApplication.kt` | log de startup vía `AppLog` (sin secretos/PII) |
| `app/src/main/java/.../MainActivity.kt` | deep link auth vía `AppConfigProvider` |
| `app/src/main/java/.../DataProvider.kt` | `useSupabase` vía flags |
| `app/src/main/java/.../AuthProvider.kt` | idem |
| `app/src/main/java/.../PushTokenRegistrar.kt` | flags + `AppLog` |
| `app/src/main/java/.../ui/components/LoadingState.kt` | delega a foundation |
| `local.properties.example` | AppConfig / staging futuro |
| `docs/adr/ADR-0005-...` | capa `AppConfigProvider` |
| `docs/02-arquitectura/arquitectura-inicial.md` | core + estados UI |
| `docs/04-calidad/M00-plan-de-calidad.md` | 20 tests / Etapa 4 |
| `docs/README.md`, `README.md` | enlaces y notas CI/config |

## 4. Archivos eliminados

Ninguno.

---

## 5. Decisiones tomadas

1. **AppConfig** tipado como única fuente para UI/proveedores; `BuildConfig` queda como inyección Gradle.
2. **Anon key** no viaja en `AppConfig` ni en logs; solo URL host opcional.
3. **Defaults:** `enablePaymentsStub` / `enableMapsExperimental` = `false`; mock si faltan credenciales.
4. **Ambientes:** solo `DEBUG` / `RELEASE`; staging documentado como futuro (ADR-0005).
5. **AppLogger:** sanitiza JWT, bearer, password/token assignments, email, teléfono, coordenadas; release reduce debug/info.
6. **AppResult/AppError:** modelo para código nuevo; sin migración masiva de repositorios.
7. **Network Security:** cleartext deshabilitado; sin excepciones inventadas.
8. **Sin** Hilt, modularización Gradle, renombre de paquete, Crashlytics/Sentry, NestJS/Docker/ORM.

---

## 6. Pruebas agregadas

| Suite | Tests |
|-------|------:|
| `AppConfigProviderTest` | 5 |
| `AppLoggerSanitizeTest` | 3 |
| `AppErrorMapperTest` | 5 |
| Previos (`ExampleUnitTest` + `SupabaseMappersTest`) | 7 |
| **Total** | **20** |

Los 7 tests existentes se conservaron y pasan.

---

## 7. Comandos y resultados (validación local)

```bash
./gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon
```

| Comando | Resultado |
|---------|-----------|
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **SUCCESS** — 20 tests, 0 failures |
| `lintDebug` | **SUCCESS** — **0 errors**, 38 warnings, 1 hint |

---

## 8. Revisión de seguridad

| Control | Estado |
|---------|--------|
| Cleartext traffic | OFF (`network_security_config.xml`) |
| Secretos en repo | `local.properties` gitignored; ejemplo ficticio |
| Logs | Sin tokens/claves/email/coords exactas (sanitize + startup sin URL key) |
| CI | No inyecta `SUPABASE_*` reales; modo mock |
| GPS/mapas/pagos en rama | Ausentes (WIP en `wip/gps-mapas-pagos`) |

---

## 9. Estado de CI

- Workflow vigente: `.github/workflows/android-ci.yml` (Etapa 3).
- Revisión Etapa 4: assemble + unit tests + lint; `if: always()` para artefactos; sin secretos.
- **Validación remota:** no ejecutada/observada en esta sesión (sin afirmar éxito remoto). La corrida en PR/push a `main` sigue siendo la verificación en GitHub Actions.
- **Validación local:** OK (sección 7).

---

## 10. Riesgos y deuda aceptada

- Warnings de lint (~38): target API, KTX, resources, etc.; sin baseline.
- Clientes Supabase aún leen `BuildConfig` internamente (aceptable); UI/proveedores ya usan `AppConfigProvider`.
- Sin crash reporting externo (M07).
- Instrumentados aún pendientes.
- Paquete `com.comunidapp.app` sin renombrar (ADR-0006).

---

## 11. Checklist de aceptación

- [x] No se perdió WIP del usuario  
- [x] La rama no contiene GPS/mapas/pagos funcionales  
- [x] Supabase único backend  
- [x] AppConfig central tipado  
- [x] Defaults seguros debug/release  
- [x] FeatureFlags tipadas + pruebas  
- [x] AppLogger sin datos sensibles  
- [x] Modelo común de errores  
- [x] Loading/Empty/Error/Retry  
- [x] Network Security Config asociado  
- [x] Sin renombre de paquete / refactor masivo  
- [x] Tests previos + nuevos en verde  
- [x] assemble / test / lint OK  
- [x] CI configurado sin secretos  
- [x] Documentación alineada  
- [x] Informe final M00 creado  

---

## 12. Siguiente paso

Tras **revisión explícita** de este cierre: el siguiente módulo habilitado es **M01**. No iniciado en esta etapa.
