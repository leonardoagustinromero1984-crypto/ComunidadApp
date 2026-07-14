# LEOVER — M00 Auditoría inicial

**Módulo:** M00 — Fundación Técnica  
**Etapa:** 1 — Auditoría y plan (M00-001 a M00-004)  
**Fecha:** 2026-07-14  
**Repositorio:** `ComunidadApp` (nombre Gradle: `Comunidapp`)  
**Commit base auditado:** `98d287c` (+ cambios locales no commiteados: GPS/mapa, stub pagos, docs D01/M00)  
**Autores de la auditoría:** Cursor (arquitecto/desarrollador senior)  

**Documentos de entrada:**

- [`docs/01-producto/D01-Modulos-y-Orden.md`](../01-producto/D01-Modulos-y-Orden.md)
- [`docs/03-modulos/M00-Fundacion-Tecnica.md`](../03-modulos/M00-Fundacion-Tecnica.md)

---

## 1. Resumen ejecutivo

El proyecto es un **monolito Android** (`:app`) con Kotlin, Jetpack Compose, Material 3, MVVM y Navigation Compose. El backend operativo actual es **Supabase** (Auth + Postgres + Storage + Edge Functions + migraciones SQL). Existe modo **mock** local vía `DataProvider` cuando no hay credenciales.

**Compilación debug:** OK. **Tests unitarios:** OK (7). **Lint debug:** FALLA (53 errores, 35 warnings) — bloquea `lintDebug` y, por tanto, un criterio duro de M00-H hasta baseline o remediación.

Respecto a M00: la app **cumple varias bases Android** (stack, navegación, tema claro/oscuro, mock vs remoto), pero **faltan casi todos los pilares de plataforma** exigidos por M00: NestJS, Docker Compose + PostGIS, CI, ADRs, contrato de errores API, feature flags formales, Network Security Config, `.env.example`, `CONTRIBUTING`, estructura `docs/02`/`adr` (esta auditoría inicia `02-arquitectura/`).

**No se recreó la app ni se refactorizó en esta etapa.**

---

## 2. Estructura actual del repositorio

```text
ComunidadApp/
├── app/                          # único módulo Gradle Android
│   ├── src/main/java/com/comunidapp/app/
│   │   ├── data/                 # model, mock, provider, remote, repository
│   │   ├── domain/               # permisos/módulos
│   │   ├── navigation/
│   │   ├── notifications/        # FCM
│   │   ├── payments/             # stub MP (local, no comiteado)
│   │   ├── ui/                   # screens, components, theme
│   │   ├── viewmodel/
│   │   ├── LeoverApplication.kt
│   │   └── MainActivity.kt
│   ├── google-services.json      # versionado (repo privado)
│   └── build.gradle.kts
├── docs/
│   ├── 01-producto/              # D01 (nuevo)
│   ├── 03-modulos/               # M00 (nuevo)
│   └── *.md legacy (roadmap, supabase, firebase, funcional)
├── supabase/
│   ├── migrations/               # 001–013
│   ├── functions/push/
│   └── email-templates/
├── apk/                          # artefactos locales (gitignore *.apk)
├── gradle/, gradlew*, build.gradle.kts, settings.gradle.kts
├── local.properties              # gitignored (secretos Supabase)
├── local.properties.example
├── README.md
├── firebase.json, firestore.rules, storage.rules, .firebaserc  # legado Firebase
└── .claude/                      # prompts auxiliares (no producto)
```

**Ausente vs estructura objetivo M00:**

| Objetivo M00 | Estado |
|--------------|--------|
| `backend/` NestJS | Ausente |
| `core/` / `feature/` módulos Gradle | Ausente (todo en `:app`) |
| `web/` reservado | Ausente (aceptable en M00) |
| `infra/docker`, `docker-compose.yml` | Ausente |
| `.github/workflows/` | Ausente |
| `.env.example` | Ausente (`local.properties.example` sí existe) |
| `CONTRIBUTING.md` | Ausente |
| `docs/00-maestro/`, `docs/adr/` | Ausente |
| `docs/02-arquitectura/` | Creado en esta etapa (este informe) |

---

## 3. Stack detectado

### 3.1 Android (cumplido parcialmente vs §3.1 M00)

| Decisión M00 | Estado actual | Nota |
|---------------|---------------|------|
| Kotlin | Cumplido | 2.2.10 |
| Jetpack Compose | Cumplido | BOM 2026.02.01 |
| Material Design 3 | Cumplido | `material3` + tema propio |
| Gradle Kotlin DSL | Cumplido | `*.gradle.kts` + version catalog |
| Capas / MVVM + Flow | Cumplido | `data` / `domain` / `ui` / `viewmodel` |
| Navigation Compose | Cumplido | `ComunidappNavGraph.kt` |
| Hilt | **No** | Alternativa: service locator `DataProvider` / `AuthProvider` |
| HTTP: Retrofit **o** Ktor (uno) | Parcial | Ktor vía cliente Supabase; no hay capa REST NestJS |
| Serialization | Cumplido | Kotlinx Serialization |

### 3.2 Backend (objetivo M00 vs realidad)

| Decisión M00 | Estado actual |
|---------------|---------------|
| NestJS + `/api/v1` | **Faltante** |
| PostgreSQL + PostGIS local (Compose) | **Faltante** (Postgres remoto vía Supabase) |
| ORM Prisma/TypeORM | **Faltante** |
| OpenAPI, health, correlation ID | **Faltante** |
| Supabase / Firebase “por si acaso” | **Ya existe y está en uso** — contradicción a resolver por ADR (no borrar en M00) |

### 3.3 Dependencias principales Android

- AndroidX: Core, Activity Compose, Lifecycle, Navigation, SplashScreen  
- Compose Material3 + icons extended  
- Coil  
- Supabase-kt BOM 3.0.3 (postgrest, auth, storage, realtime)  
- Ktor client Android 3.0.3  
- Firebase Messaging (push)  
- Play Services Location  
- Tests: JUnit4, Espresso, Compose UI Test  

**No hay:** Hilt, Retrofit, Crashlytics/Sentry, Timber, feature-flag SDK, Network Security Config XML dedicado.

---

## 4. Configuración y secretos

| Ítem | Estado |
|------|--------|
| `local.properties` gitignored | Cumplido |
| `local.properties.example` | Cumplido (SDK + Supabase) |
| `.env.example` | Faltante |
| BuildConfig `SUPABASE_*` desde local.properties | Cumplido |
| Ambientes `dev` / `staging` / `production` | Faltante (solo default + release sin minify) |
| Product flavors | Faltante |
| `google-services.json` en Git | Riesgo si el repo se vuelve público (documentado en `.gitignore`) |
| Secretos en logs | No auditado exhaustivamente; no hay logger estructurado |

---

## 5. Arquitectura Android observada

- **Navegación:** centralizada en `navigation/ComunidappNavGraph.kt` + `NavRoutes.kt`.  
- **Tema:** claro y oscuro (`Theme.kt`, `isSystemInDarkTheme`).  
- **UI estados:** `LoadingState` existe; **no** hay componentes unificados de vacío / error / reintento.  
- **Datos:** interfaces de repositorio + `Mock*` / `Supabase*` seleccionados por `DataProvider.useSupabase`.  
- **DI:** sin Hilt; constructores default + objetos singleton.  
- **Errores:** `Result` Kotlin ad-hoc; sin modelo API común ni mapping de red unificado.  
- **Dispatchers:** hardcodeados (`Dispatchers.IO` / `Main.immediate`); sin `DispatcherProvider` testeable.  
- **Negocio:** módulos funcionales R1–R6 ya implementados en app (fuera del alcance de *implementación* de M00, pero presentes en el código).

---

## 6. Compilación y pruebas ejecutadas

**Comandos:**

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue
```

| Tarea | Resultado | Evidencia |
|-------|-----------|-----------|
| `:app:assembleDebug` | **OK** | Task completed; APK copiado vía `copyDebugApk` |
| `:app:testDebugUnitTest` | **OK** | 7 tests, 0 failures (`ExampleUnitTest` 1 + `SupabaseMappersTest` 6) |
| `:app:lintDebug` | **FALLA** | 53 errors, 35 warnings, 1 hint |

**Lint — IDs más frecuentes (reporte debug):**

| Count | Issue |
|------:|-------|
| 51 | `InvalidFragmentVersionForActivityResult` |
| 13 | `GradleDependency` (warnings de versiones) |
| 8 | `UnusedResources` |
| 2 | `AppLinkUrlError` (deep link OAuth `autoVerify` sin https/host web) |
| 1+ | `OldTargetApi`, `Unused…`, etc. |

**Interpretación M00-H:** la base compila y tiene pruebas mínimas, pero **calidad automática lint no pasa**. M00 exige lint sin cientos de errores históricos: aquí hay 53; se recomienda **baseline gradual** + fixes críticos (App Links), no freeze del pipeline.

Instrumented tests (`androidTest`) no se ejecutaron en esta etapa (requieren emulador/dispositivo).

---

## 7. Duplicaciones y riesgos

1. **Doble backend conceptual:** Supabase (hecho) vs NestJS (obligatorio M00). Riesgo de trabajo paralelo o reescritura si no hay ADR de coexistencia.  
2. **Legado Firebase** (rules/json) + FCM actual: documentación y artefactos legacy confunden onboarding.  
3. **Docs duales:** roadmap viejo en raíz vs D01/M00 nuevos; requiere consolidación en Etapa 2.  
4. **Service locator** escala mal para tests; Hilt es la vía M00 preferida, pero el locator es *válido y consistente* hoy.  
5. **Lint App Links:** `autoVerify` sobre scheme custom — falso positivo de “App Link” o mal configurado; afecta CI si se exige lint estricto.  
6. **Working tree sucio:** cambios de mapa GPS / pagos stub no son M00; no deben mezclarse con PRs de fundación.  
7. **applicationId / namespace** sigue `com.comunidapp.app` mientras marca producto es LeoVer — deuda de branding, no bloqueante M00.

---

## 8. Matriz de requisitos M00

Leyenda: **C** = cumplido · **P** = parcial · **F** = faltante

### M00-A Auditoría

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| A1 | Informe escrito | **C** | Este archivo |

### M00-B Gobierno

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| B1 | README ejecución local | **P** | `README.md` (Supabase/mock); faltan NestJS/Docker |
| B2 | CONTRIBUTING | **F** | — |
| B3 | Convenciones ramas/commits | **F** | — |
| B4 | Plantilla PR | **F** | — |
| B5 | ADRs 0001–0005 | **F** | — |
| B6 | `arquitectura-inicial.md` | **F** | — |

### M00-C Ambientes

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| C1 | Ambientes local/staging/prod | **F** | Solo debug/release |
| C2 | Sin secretos en Git | **P** | `local.properties` OK; `google-services.json` versionado |
| C3 | `.env.example` | **F** | Existe `local.properties.example` |
| C4 | Flags/URLs configurables | **P** | Solo `SUPABASE_*` |
| C5 | Validación config inválida con mensaje claro | **P** | Fallback a mock silencioso |

### M00-D Backend NestJS

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| D1–D12 | NestJS, health, version, filtros, OpenAPI, CORS, etc. | **F** | No existe `backend/` |

### M00-E Base de datos local

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| E1 | Compose Postgres/PostGIS | **F** | — |
| E2 | Migración infra vacía ORM | **F** | Hay SQL Supabase de negocio (fuera de alcance M00) |
| E3 | Scripts documentados up/migrate | **F** | — |

### M00-F Contrato errores

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| F1 | JSON error común API | **F** | — |
| F2 | Mapping Android de errores red | **F** | `Result` suelto |

### M00-G Fundación Android

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| G1 | Navegación centralizada | **C** | `ComunidappNavGraph` |
| G2 | Tema claro/oscuro | **C** | `Theme.kt` |
| G3 | Loading / empty / error / retry | **P** | Solo `LoadingState` |
| G4 | Abstracción configuración | **P** | BuildConfig + local.properties |
| G5 | Abstracción cliente API | **P** | Repos mock/remote; no cliente NestJS |
| G6 | Manejo común errores | **F** | — |
| G7 | DispatcherProvider | **F** | — |
| G8 | Result models consistentes | **P** | `Result` / UiState por screen |
| G9 | Previews componentes base | **P** | Tooling preview dep; previews no sistemáticos |
| G10 | Mock vs remoto sin cambiar UI | **C** | `DataProvider` |

### M00-H Calidad

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| H1 | Compilación debug | **C** | assembleDebug OK |
| H2 | Unit tests | **P** | 7 tests; cobertura fundación baja |
| H3 | Android Lint limpio/plan | **P** | Ejecutado; **falla** 53 errores |
| H4 | Backend TS/ESLint/tests | **F** | — |

### M00-I CI

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| I1 | GitHub Actions Android+backend+secretos | **F** | Sin `.github/` |

### M00-J Observabilidad

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| J1 | Logs estructurados backend | **F** | — |
| J2 | Correlation ID | **F** | — |
| J3 | Logging abstraction Android | **F** | — |
| J4 | Evento start app | **F** | — |
| J5 | Doc puntos crash/métricas | **F** | — |

### M00-K Feature flags

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| K1 | Interfaz flags local | **F** | Solo `SUPABASE_ENABLED` como toggle BaaS |

### M00-L Seguridad base

| ID | Requisito | Estado | Evidencia |
|----|-----------|--------|-----------|
| L1 | Secretos fuera repo | **P** | Ver C2 |
| L2 | HTTPS fuera local | **P** | Supabase HTTPS; sin política explícita |
| L3 | Network Security Config | **F** | Sin XML; cleartext no forzado globalmente (default OK) |
| L4 | Headers seguridad backend | **F** | — |
| L5 | Body size limits | **F** | — |

### Criterios de aceptación M00 §7 (estado preliminar Etapa 1)

| # | Criterio | Estado Etapa 1 |
|---|----------|----------------|
| 1 | Android sigue compilando | **C** (debug) |
| 2 | Auditoría escrita | **C** (este doc) |
| 3–7 | Backend/Docker/health/OpenAPI/errores | **F** |
| 8 | Capa red Android + errores comunes | **F** |
| 9 | Configs por ambiente | **F** |
| 10 | Sin secretos reales versionados | **P** |
| 11 | Validaciones automáticas pasan | **P** (tests OK, lint FAIL) |
| 12 | CI | **F** |
| 13 | ADRs | **F** |
| 14 | README entorno 0→1 | **P** |
| 15 | Sin módulos futuros *nuevos* en M00 | **C** en esta etapa (no se implementó negocio) |

---

## 9. Preguntas / contradicciones a registrar (ADR pendientes)

1. **¿Coexistencia Supabase + NestJS?** M00 exige NestJS/PostGIS local y dice no añadir BaaS “por si acaso”, pero el producto ya depende de Supabase Auth/Storage/SQL.  
   **Propuesta de auditoría (no implementada):** en M00 levantar NestJS health + Compose DB **sin migrar** Auth/Storage; ADR-0001 documenta monolito API + fase de corte con Supabase post-M00.

2. **¿Hilt ahora o mantener DataProvider en M00?** Spec permite alternativa válida.  
   **Propuesta:** ADR: mantener locator en M00; Hilt en etapa posterior si CI/tests lo justifican (evita refactor masivo).

3. **¿Cliente HTTP para NestJS?** Hoy Ktor vía Supabase.  
   **Propuesta ADR-0004:** Ktor Client puro para `/api/v1` (una sola familia), o Retrofit si el equipo prefiere — elegir uno en Etapa 2/4.

4. **Lint baseline vs fail-the-build:** 53 errores históricos.  
   **Propuesta:** baseline en Etapa 5 + fix App Links; no bloquear Etapas 2–4.

---

## 10. Plan de implementación propuesto (Etapas 2–6)

Orden obligatorio según M00 §8. **Sin módulos futuros de negocio.**

### Etapa 2 — Documentación y gobierno (siguiente)

| Tarea | Archivos a crear/modificar |
|-------|----------------------------|
| M00-005 Estructura docs | `docs/00-maestro/` (índice), `docs/adr/`, completar `02-arquitectura/` |
| M00-006 README + CONTRIBUTING | `README.md`, `CONTRIBUTING.md` |
| M00-007 ADR-0001…0005 | `docs/adr/ADR-0001-…md` … `0005` |
| M00-008 ignores + env example | `.gitignore`, `.env.example`, actualizar `local.properties.example` |
| Extra mínimo | `.github/PULL_REQUEST_TEMPLATE.md` |

**No tocar:** lógica de negocio app, migraciones Supabase de producto.

### Etapa 3 — Backend y datos locales

| Tarea | Archivos |
|-------|----------|
| M00-009 NestJS scaffold | `backend/` (package.json, src/) |
| M00-010 Validación, errores, logs | `backend/src/common/` |
| M00-011 Compose PostGIS | `docker-compose.yml`, `infra/docker/` |
| M00-012 ORM (Prisma **recomendado** en ADR tras Etapa 2) | `backend/prisma/` |
| M00-013 health/ready/version | `backend/src/health/` |
| M00-014 tests | `backend/test/` e2e health |

### Etapa 4 — Fundación Android (incremental, sin romper UI)

| Tarea | Archivos |
|-------|----------|
| M00-015 Nota arquitectura app | `docs/02-arquitectura/arquitectura-android.md` |
| M00-016 Ambientes | `app/build.gradle.kts` (buildTypes/flavors livianos), `AppConfig` |
| M00-017 Cliente API NestJS | `app/.../data/remote/api/` (Ktor), interfaces |
| M00-018 Result/Error comunes | `app/.../core/` o `domain/error/` |
| M00-019 Empty/Error/Retry UI | `ui/components/` |
| M00-020 Tests fundación | `app/src/test/...` |

**Preservar** `DataProvider` mock/Supabase. Nueva capa API NestJS en paralelo (health only en M00).

### Etapa 5 — CI y seguridad

| Tarea | Archivos |
|-------|----------|
| M00-021 Workflows | `.github/workflows/android.yml`, `backend.yml` |
| M00-022 Lint baseline + unit tests | `app/lint-baseline.xml`, CI |
| M00-023 Network security + secrets scan | `res/xml/network_security_config.xml`, `.gitignore` |
| M00-024 Doc observabilidad + FeatureFlags | `AppLogger`, `FeatureFlags`, doc en `02-arquitectura/` |

### Etapa 6 — Cierre

Verificación completa §7, actualizar esta auditoría con estado final, deuda legítima, reporte DoD.

---

## 11. Prioridad de cambios (vista rápida)

| Prioridad | Qué | Por qué |
|-----------|-----|---------|
| P0 | ADRs + README/CONTRIBUTING + `.env.example` | Desbloquea decisiones NestJS/ORM/HTTP |
| P0 | `backend/` health + Compose PostGIS | Criterios 3–6 |
| P1 | Contrato errores + OpenAPI | Criterios 6–7 |
| P1 | AppConfig + ApiClient + UI empty/error | Criterios 8–9 |
| P2 | CI + lint baseline | Criterios 11–12 |
| P2 | FeatureFlags + AppLogger | M00-J/K |
| P3 | Network Security Config, PR template | M00-L / gobierno |
| Fuera M00 | Migrar Auth/storage a NestJS, web, crash SDK | D01 M07 / post-M00 |

---

## 12. Archivos creados / modificados en Etapa 1

### Creados

- `docs/02-arquitectura/M00-auditoria-inicial.md` (este informe)

### Modificados

- Ninguno (solo documentación de auditoría).

### Comandos ejecutados

```text
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue
```

### Resultado

- assembleDebug: **SUCCESS**  
- testDebugUnitTest: **SUCCESS** (7/7)  
- lintDebug: **FAILED** (53 errors)

---

## 13. Decisiones tomadas en Etapa 1

1. **No refactorizar** módulos Gradle ni mover paquetes.  
2. **No crear** `backend/` todavía (corresponde Etapa 3).  
3. Documentar **coexistencia** Supabase existente + NestJS requerido como riesgo/ADR, sin inventar corte de producto.  
4. Tratar cambios locales de mapa/pagos como **fuera de M00** (no mezclar en PRs de fundación).

---

## 14. Riesgos / deuda pendiente

- Lint en rojo (sobre todo Fragment version + App Links).  
- Backend NestJS y PostGIS local 100% pendientes.  
- Documentación legacy vs D01 aún no unificada.  
- Pruebas instrumentadas no ejecutadas.  
- `google-services.json` en el remoto.  
- Nombre/ID de paquete `comunidapp` vs marca LeoVer.

---

## 15. Criterios de aceptación cumplidos (solo Etapa 1)

- [x] Auditoría escrita de la base actual (criterio §7.2).  
- [x] Proyecto Android existente sigue compilando en debug (criterio §7.1 parcial; lint no limpio).  
- [x] Sin implementación de módulos futuros en esta etapa.  
- [ ] Resto de criterios §7 → Etapas 2–6.

---

## 16. Próxima etapa propuesta

**Etapa 2 — Documentación y gobierno (M00-005 … M00-008)**  

Salida esperada: ADRs 0001–0005 (incluyendo decisión Supabase+NestJS y ORM), README ampliado, CONTRIBUTING, `.env.example`, plantilla PR, `arquitectura-inicial.md`.

**No avanzar a Etapa 3** hasta indicación explícita tras revisión de esta auditoría y de los ADR.
