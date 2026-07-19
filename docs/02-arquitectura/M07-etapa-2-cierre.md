# M07 — Etapa 2 cierre: Contratos, catálogo, correlation IDs, sanitización y mocks

**Fecha:** 2026-07-17  
**Producto:** LeoVer  
**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 2 — Contratos Kotlin puros y base local  
**Rama:** `m07/etapa-2-contratos-catalogo-correlation-sanitizacion`  
**Commit base:** `a4f2f681c241d4f0dd0960685dab591d039227f3`  
**Estado:** implementación local lista para revisión; **sin commit** en este cierre documental.

---

## 1. Resumen de implementación

Se construyó la base tipada de observabilidad **sin persistencia SQL**, **sin proveedores externos** y **sin instrumentar call sites M00–M06**:

- Contratos de dominio (`AuditEvent`, `SecurityEvent`, `ApplicationError`, etc.).
- Catálogo central local con **108** event keys únicos (`m00`–`m07`).
- Metadata allowlist deny-by-default.
- Correlation IDs + provider (logout / cambio de cuenta limpian sesión).
- Sanitización de strings y `Throwable` → `SanitizedThrowable`.
- Retención, sampling, autorización local deny-by-default.
- Interfaces de repositorio + mocks deterministas + wiring en `DataProvider`.
- `AppLogger` ampliado (Throwable sanitizado, correlation opcional; sin remoto).

**No iniciado:** Etapa 3, M08, SQL, dashboards, alertas reales, Crashlytics/Sentry/OTel/Analytics.

---

## 2. Estado Git

| Check | Resultado |
|---|---|
| Commit base | `a4f2f681c241d4f0dd0960685dab591d039227f3` |
| Rama | `m07/etapa-2-contratos-catalogo-correlation-sanitizacion` |
| SQL / migraciones | ninguna creada ni modificada |
| Auth / username | intactos (`AuthRepository`, `domain/auth`, `UsernameValidators`) |
| Merge / staging / producción | no ejecutados |

---

## 3. Contratos creados

| Contrato | Ubicación |
|---|---|
| `AuditEvent` | `domain/observability/ObservabilityContracts.kt` |
| `SecurityEvent` | idem |
| `ApplicationError` | idem |
| `PerformanceMetric` | idem |
| `HealthCheck` / `HealthStatus` | idem |
| `AnalyticsEvent` | idem (producto ≠ marketing) |
| `AlertRule` / `AlertIncident` | idem |
| `ObservabilityExport` | idem |
| `RetentionPolicy` / `RetentionPolicyKey` | `domain/observability/retention/` |
| `CorrelationContext` / `CorrelationId` | `domain/observability/correlation/` |
| Enums | categoría, severidad, sensibilidad, resultado, actor, errores |

Categorías, severidades, sensibilidades, resultados y actores: según spec Etapa 2 (exactas).

---

## 4. Catálogo

- **Cantidad de eventos:** **108**
- Convención: `m0X.dominio.accion`
- Keys únicos; módulo alineado al prefijo
- Metadata requerida validada; extra rechazada
- Evento desconocido → `OBS_EVENT_UNKNOWN` o `UnknownLocalOnly` explícito
- Incluye inventario M00–M06 de la auditoría + eventos M07 propios
- **Sin instrumentación** de call sites M00–M06

---

## 5. Correlation IDs

| Pieza | Estado |
|---|---|
| `CorrelationId` | formato allowlisted, sin PII |
| `CorrelationContext` | root/child, request/session/job/event ids opacos |
| `CorrelationIdGenerator` | UUID + `Sequential` inyectable |
| `CorrelationContextProvider` | startRoot/child, logout, account change |
| `CorrelationPropagationPolicy` | CHILD / REUSE / REJECT |
| Headers / RPC | **no** modificados |

---

## 6. Sanitización y Throwable

| Pieza | Estado |
|---|---|
| `SensitiveDataSanitizer` | JWT, Bearer, tokens, service role, signed URL, email, phone, coords, SQL, stack, paths, headers, base64, chat, INTERNAL |
| `ThrowableSanitizer` → `SanitizedThrowable` | errorClass, safeMessage, fingerprint, causeDepth, isRetryable |
| `MetadataSanitizer` / `StructuredLogSanitizer` / `ObservabilityErrorSanitizer` | implementados |
| Stack raw | no persistido |
| `AppLogger` | usa sanitizers; **no** pasa Throwable raw a `Log` |

---

## 7. Retención / sampling / autorización

**Retención:** `NO_REMOTE` … `LEGAL_REVIEW_REQUIRED`; DEBUG → no remoto por defecto; UNKNOWN → `NO_REMOTE`.

**Sampling:** `ALWAYS` / `NEVER` / `RATE` / `FIRST_PER_WINDOW` / `ERROR_ONLY`; críticos ALWAYS; denegaciones no se silencian del todo.

**Autorización:** deny-by-default (`ALLOWED` … `DENIED_UNKNOWN`); permisos conceptuales M07; AccountType/`active_modules` **no** otorgan autoridad; Android no se auto-declara staff.

---

## 8. Repositorios y mocks

Interfaces: `AuditEventRepository`, `SecurityEventRepository`, `ApplicationErrorRepository`, `PerformanceMetricRepository`, `HealthCheckRepository`, `AnalyticsEventRepository`, `AlertRepository`, `ObservabilityExportRepository`, `EventCatalogRepository`, `CorrelationContextRepository`.

Mocks: store compartido, IDs/correlation predecibles, clock/random inyectables, filtros, paginación, dedup, health, alerts, export simulado **sin archivos reales**.

`ClientDeniedAuditEventRepository` para modo Supabase hasta Etapa 3.

**Sin** implementaciones Supabase M07.

---

## 9. DataProvider

- `useSupabase=false`: mocks M07 no nulos.
- `useSupabase=true`: mocks locales + audit client-denied; **sin** backend M07.
- M00–M06 wiring preservado.

---

## 10. AppLogger

- No reemplazado.
- Sanitización de Throwable + correlation ID opcional.
- Sin migración masiva de call sites.
- Sin logs remotos, crash handler ni breadcrumbs.

---

## 11. Calidad

| Check | Resultado |
|---|---|
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **506 tests, 0 failures, 0 errors, 0 skipped** |
| `lintDebug` | **SUCCESS** |
| Tests previos | 489 conservadas (+17 nuevas M07) |

---

## 12. Confirmaciones de exclusiones

| Ítem | Confirmado |
|---|---|
| SQL / migraciones / tablas | ausentes |
| Crashlytics / Firebase Analytics / Sentry / OpenTelemetry | ausentes |
| Dashboards / alertas reales / Edge / jobs / cron / pantallas | ausentes |
| Instrumentación remota M00–M06 | no hecha |
| AuthRepository / domain/auth / UsernameValidators | intactos |
| Etapa 3 / M08 / merge main / staging / producción | no iniciados |

---

## 13. Staging 014–028

**PENDIENTE DE VALIDACIÓN REMOTA** (heredado de M06).  
Release bloqueado hasta evidencia remota. Esta etapa no aplica migraciones.

---

## 14. Archivos principales

```text
domain/observability/**
domain/observability/catalog/ObservabilityEventCatalog.kt
domain/observability/correlation/Correlation.kt
domain/observability/sanitization/Sanitization.kt
domain/observability/retention/RetentionPolicy.kt
domain/observability/authorization/ObservabilityAuthorization.kt
data/repository/ObservabilityRepositories.kt
data/provider/DataProvider.kt          (wiring M07)
core/logging/AppLogger.kt              (Throwable + cid)
test/.../M07Stage2ObservabilityFoundationTest.kt
test/.../M07DataProviderWiringTest.kt
test/.../M07Stage2AuthUsernameIntactTest.kt
docs/02-arquitectura/M07-etapa-2-cierre.md
```

---

## 15. Parada

Documento de cierre:

```text
/docs/02-arquitectura/M07-etapa-2-cierre.md
```

**No iniciar M07 Etapa 3.**  
**No iniciar M08.**  
**No hacer commit hasta revisión.**  
**No merge a `main`.**  
**No aplicar staging ni producción.**
