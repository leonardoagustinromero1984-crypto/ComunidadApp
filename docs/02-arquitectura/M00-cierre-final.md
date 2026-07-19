# M00 — Cierre final: Fundación Técnica

**Fecha:** 2026-07-14  
**Módulo:** M00 — Fundación Técnica  
**Estado:** Completado a nivel de implementación y documentación; pendiente revisión/aprobación humana de Etapa 4  

Este documento cierra M00. **No autoriza por sí solo iniciar M01** hasta la revisión explícita del cierre de Etapa 4.

---

## 1. Fundación lista

| Área | Entrega |
|------|---------|
| Gobierno | ADRs 0001–0006, `docs/` estructurado, CONTRIBUTING, plantilla PR, inventario legacy |
| Calidad | Lint 0 errors; plan de calidad; evidencia antes/después |
| CI | `.github/workflows/android-ci.yml` (assemble + unit tests + lint, mock, sin secretos) |
| Configuración | `AppConfig` / `AppConfigProvider` |
| Feature flags | Tipadas locales (`useSupabase`, pagos/mapas OFF, verbose) |
| Logging | `AppLogger` / `AppLog` con sanitización |
| Errores | `AppResult` / `AppError` / `AppErrorMapper` para código nuevo |
| UI estados | `LoadingState` / `EmptyState` / `ErrorState` (+ retry) |
| Red | Network Security Config (cleartext OFF) |
| Backend | **Solo Supabase** (ADR-0001) |

---

## 2. ADR vigentes

| ADR | Decisión |
|-----|----------|
| 0001 | Supabase como backend principal |
| 0002 | Android monolito modular (un módulo `:app`) |
| 0003 | Proveedores / service locator (sin Hilt por ahora) |
| 0004 | Acceso a datos vía cliente Supabase Kotlin |
| 0005 | Ambientes, secretos, `local.properties` + `AppConfigProvider` |
| 0006 | Migración de paquete a Leover: diferida / documentada |

---

## 3. Stack definitivo (M00)

| Capa | Tecnología |
|------|------------|
| App | Kotlin, Jetpack Compose, Material 3, Navigation Compose, MVVM |
| Datos | Repositorios + mock **o** Supabase |
| Backend | Supabase (Auth, Postgres, Storage, Edge Functions) |
| Push | Firebase Cloud Messaging únicamente |
| Build | Gradle KTS, AGP 9.x, JDK 17 |
| Calidad | JUnit unitarios; lint; GitHub Actions |

**Fuera del stack oficial:** NestJS, Docker Compose DB, Prisma, TypeORM, Hilt, módulos Gradle adicionales, segunda base de datos.

---

## 4. Controles de calidad

| Control | Resultado al cierre |
|---------|---------------------|
| `assembleDebug` | SUCCESS |
| `testDebugUnitTest` | SUCCESS (20 tests) |
| `lintDebug` | SUCCESS (0 errors) |
| CI workflow | Presente; ejecución remota = verificación en PR/`main` |
| Secretos | No en Git; CI sin claves reales |

Evidencia por etapa: `M00-auditoria-inicial.md`, `M00-etapa-2-cierre.md`, `M00-etapa-3-cierre.md`, `M00-etapa-4-cierre.md`.

---

## 5. Deuda aceptada

- Warnings de lint (~38).
- `google-services.json` versionado (repo privado).
- Paquete `com.comunidapp.app` vs marca Leover.
- Docs Firebase legacy en raíz / `99-legacy`.
- Sin pruebas instrumentadas en CI.
- Sin Crashlytics/Sentry (M07).
- Clientes Supabase internos aún usan `BuildConfig` (capa AppConfig ya en proveedores/UI nuevos).
- WIP GPS/mapas/pagos en rama aparte; no en fundación limpia.

---

## 6. Fuera de alcance de M00 (explícito)

- M01 y cualquier funcionalidad de negocio nueva  
- GPS / mapas / Mercado Pago  
- Renombre de `applicationId` / namespace  
- Hilt, modularización Gradle, upgrade masivo de dependencias  
- NestJS / Docker / ORM / segunda DB  
- Eliminación masiva de Firebase legacy sin aprobación  

---

## 7. Siguiente módulo habilitado

**M01** — según [D01 — Módulos y Orden](../01-producto/D01-Modulos-y-Orden.md).

### Condiciones de entrada a M01

1. Revisión explícita y aprobación del cierre de Etapa 4 / este cierre final.  
2. Trabajar sobre rama limpia (sin mezclar `wip/gps-mapas-pagos` salvo plan aparte).  
3. Respetar ADR: Supabase único backend; AppConfig/flags/logger para código nuevo.  
4. Mantener verde: assemble + unit tests + lint.  
5. No reabrir NestJS/Docker ni renombre de paquete salvo ADR nuevo.

---

## 8. Ramas de referencia

| Rama | Rol |
|------|-----|
| `m00/etapa-4-config-observabilidad` | Cierre M00 Etapa 4 |
| `m00/etapa-3-calidad-ci` | Lint + CI |
| `wip/gps-mapas-pagos` | WIP funcional preservado |
| `main` | Producto estable sin WIP M00 completo hasta merge |

---

**Fin de M00 (fundación técnica).** Esperar aprobación antes de comenzar M01.
