# M07 — Auditoría inicial: Auditoría, Analítica y Observabilidad

**Fecha:** 2026-07-17  
**Producto:** LeoVer  
**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 1 — Auditoría y diseño (sin instrumentación)  
**Rama:** `m07/auditoria-analitica-observabilidad-auditoria`  
**Commit base:** `b2066147e85366d543684ec8f41c68c0b82fe7e9`  
**Estado:** documento de auditoría local; **sin commit**; sin Etapa 2; sin M08.

---

## 1. Estado Git

| Check | Resultado |
|---|---|
| Commit base | `b2066147e85366d543684ec8f41c68c0b82fe7e9` confirmado |
| Rama | `m07/auditoria-analitica-observabilidad-auditoria` creada desde la base |
| Working tree al iniciar | limpio de cambios trackeados; existía `docs/03-modulos/M07-Auditoria-Analitica-y-Observabilidad.md` no trackeado |
| Working tree al cerrar Etapa 1 | sin cambios a código; untracked: este documento + spec M07 módulo |
| GPS / mapas / pagos | no mezclados en esta rama |
| Auth / username | sin modificaciones (`AuthRepository`, `domain/auth`, `UsernameValidators`) |
| Migraciones | ninguna creada ni editada en Etapa 1 |
| Proveedores nuevos | no agregados (Crashlytics, Firebase Analytics, Sentry, OpenTelemetry, métricas) |
| Merge / staging / producción | no ejecutados |

Esta etapa es **solo lectura + documentación**. No se modificó funcionalidad.

---

## 2. Separación de capas (obligatoria)

| Capa | Propósito | Estado actual |
|---|---|---|
| **Auditoría** | Quién hizo qué, sobre qué, con qué resultado | Parcial y siloed (M02 historiales, M04 `administrative_audit_log`, M05 `file_access_audit`, M06 schema de access audit) |
| **Observabilidad técnica** | Errores, latencia, salud, jobs, Edge, dead-letter | Parcial (AppLogger local, dead-letters M06, CI logs); sin sink remoto |
| **Analítica de producto** | Uso, funnels, activación (sin marketing) | Ausente (solo `AuthAnalytics` in-memory de pruebas) |
| **Seguridad** | Login fallido, denegaciones, tokens, abuso | Gap crítico: pocos security events propios |
| **Métricas operativas** | Contadores de deliveries, uploads, jobs, health | Parcial en tablas M05/M06; sin dashboards/alertas |

Regla: **analítica ≠ marketing**. Consentimiento comercial y campañas quedan fuera de M07 Etapa 1–N hasta especificación aparte.

---

## 3. Inventario Android

### 3.1 Logger y sanitización

| Ítem | Clasificación | Evidencia |
|---|---|---|
| `AppLogger` / `AppLog` | **IMPLEMENTADO** | `core/logging/AppLogger.kt` |
| `sanitizeLogMessage` | **IMPLEMENTADO** | JWT, Bearer, password/token/api_key, email, phone, coords |
| Gate de nivel (debug/release) | **IMPLEMENTADO** | `LoggingConfig` + `enableVerboseLogging` |
| `android.util.Log` directo fuera de AppLogger | **IMPLEMENTADO** (centralizado) | 0 usos dispersos |
| `println` / `System.out` / `printStackTrace` | **AUSENTE** | no encontrado |
| Sanitización de `Throwable` | **RIESGO** | message/stack del exception van a Logcat sin redactar |

### 3.2 Errores y UI

| Ítem | Clasificación |
|---|---|
| `AppError` / `AppErrorMapper` | **IMPLEMENTADO** |
| `AuthErrorMapper` | **IMPLEMENTADO** |
| `FileUiErrorMapper` | **IMPLEMENTADO** |
| `NotificationUiErrorMapper` (+ sanitizeTechnical) | **IMPLEMENTADO** |
| Mapper UI universal | **AUSENTE** |
| `catch (_: Exception)` silencioso (~50) | **RIESGO** / **PARCIAL** |
| Reporter de errores remoto | **AUSENTE** |

### 3.3 Correlation, crash, performance

| Ítem | Clasificación |
|---|---|
| Correlation / request ID end-to-end | **AUSENTE** |
| Idempotency keys de dominio (notif/delete) | **PARCIAL** (no logging correlation) |
| UncaughtExceptionHandler / Crashlytics / Sentry | **AUSENTE** |
| ANR / breadcrumbs | **AUSENTE** |
| Performance metrics / OTel | **AUSENTE** |
| Timber | **AUSENTE** |
| Firebase Analytics | **AUSENTE** |
| Firebase Messaging (FCM) | Presente solo para push M06 |

### 3.4 Red, sesión, notificaciones, archivos

| Ítem | Clasificación | Notas |
|---|---|---|
| Logging del cliente Supabase | **AUSENTE** | sin HttpClient debug |
| Retries | **PARCIAL** | notif/outbox, upload retry UX; sin connectivity monitor |
| Logout / cambio de cuenta | **PARCIAL** | SessionViewModel limpia bien; ProfileViewModel.logout incompleto vs pending nav |
| FCM / deep links hardening | **IMPLEMENTADO** | payload parser, allowlist, fail-closed |
| Signed URLs en logs de mensaje | Evitadas | riesgo residual vía `Throwable` |
| `AuthAnalytics` | **PARCIAL** / **FUTURO** | in-memory para tests; comentario “M07 pendiente” |
| Mocks vs prod | **PARCIAL** | mismo logger; mock token en UI forgot-password demo |

### 3.5 Dependencias de observabilidad

Confirmado en Gradle: **no** Crashlytics, **no** Firebase Analytics, **no** Sentry, **no** OpenTelemetry, **no** Timber. Solo Firebase Messaging para M06.

---

## 4. Inventario Supabase

### 4.1 Tablas de auditoría / observabilidad existentes

| Migración | Tabla | Rol |
|---|---|---|
| 014 | `user_consents` | consentimiento legal |
| 014 | `account_deletion_requests` | ciclo de eliminación |
| 018 | `user_status_history` | estados de usuario + `request_id` opcional |
| 018 | `role_assignment_history` | roles plataforma + `request_id` opcional |
| 019 | `organization_status_history` | estados de org + `request_id` |
| 019 | `organization_audit_log` | auditoría de dominio org + `request_id` |
| 022 | `administrative_audit_log` | auditoría M04 staff + `request_id` |
| 024 | `file_access_audit` | acceso/ops de archivos (sin `request_id`) |
| 026 | `notification_events` / `deliveries` / `outbox` | pipeline M06 |
| 026 | `notification_dead_letters` | fallos sanitizados |
| 026 | `notification_access_audit` | schema ALLOWED/DENIED (**sin writers**) |

**No existe** tabla global `audit_events`, `security_events`, `application_errors`, `performance_metrics`, `health_checks` ni `analytics_events`.

### 4.2 SECURITY DEFINER / search_path / grants

- Patrón dominante: `SECURITY DEFINER` + `SET search_path = public` (~170 funciones).
- Mitigación M06 Etapa 5 (028): revoke `PUBLIC`/`anon`/`authenticated` en enqueue/materialize/outbox/push; grant `service_role`.
- Riesgo residual: superficie DEFINER grande; cada RPC debe seguir validando actor/permiso.
- DDL idempotente con `EXCEPTION WHEN OTHERS THEN NULL` en 015/019/022 (swallow de migrate).

### 4.3 Edge Functions

| Función | Observabilidad | Sanitización |
|---|---|---|
| `push` | console.error; counts processed/delivered/failed | `sanitizeError` (Bearer/PEM); service-role only; ignore body arbitrario |
| `delete-account` | `correlation_id` (Idempotency-Key / UUID) | JWT actor; service role solo server-side |

Sin drain remoto de logs. Sin cron en repo (`config.toml` ausente). Push diseñado para worker externo.

### 4.4 Realtime / Storage / Auth / Jobs

| Área | Estado |
|---|---|
| Realtime notificaciones | **REALTIME PENDIENTE** (docs M06) |
| Publicaciones Realtime legacy | users/pets/posts/etc. en migraciones tempranas; sin métricas |
| Storage observability | parcial vía `file_access_audit` / uploads; signed URL issuance no auditada |
| Auth GoTrue logs | externos a la app; sin sink de dominio |
| Cron / backups / health checks | **AUSENTE** en repo |

### 4.5 Duplicaciones

- Historiales de status (user vs org) con shape similar.
- Audits siloed: org / administrative / file / notification_access.
- Helpers `m04_audit` / `m05_audit`; **no** hay writer de `notification_access_audit`.
- `user_status_history` escrito desde caminos 018 y 022.
- Correlation: `request_id` parcial vs `correlation_id` solo en Edge delete-account.

### 4.6 Errores silenciosos

| Sitio | Clasificación |
|---|---|
| M06 emit 027 | swallow total → **INCONSISTENTE** |
| M06 emit 028 | escribe dead-letter sanitizado; nested swallow si DL falla → **PARCIAL** |
| Android Supabase catch vacíos | **RIESGO** (ops blind) |
| DDL `WHEN OTHERS THEN NULL` | **RIESGO** migrate |

---

## 5. Inventario CI/CD

| Ítem | Estado |
|---|---|
| Workflow | `.github/workflows/android-ci.yml` único |
| Triggers | push/PR a `main` |
| Checks | `assembleDebug` + `testDebugUnitTest` + `lintDebug` (modo mock, sin secretos) |
| Artifacts | unit-test-results, lint-reports |
| Coverage | **AUSENTE** |
| Dependabot / vuln scan | **AUSENTE** |
| Secret scanning CI | **AUSENTE** (workflow evita inyectar secretos) |
| Validación SQL / migraciones | **AUSENTE** (solo asserts de substrings en unit tests M06) |
| SBOM | **AUSENTE** |
| Release / rollback / changelog automatizado | **AUSENTE** |
| Backups | documentados como requisito staging; **NO EJECUTADO** |

---

## 6. Catálogo de eventos M00–M06 (resumen clasificado)

Convención observada en M06: `m0X.dominio.accion` — **propuesta a reutilizar** en M07.

### 6.1 M00 Fundación

| Event key | Status | Destino actual |
|---|---|---|
| `m00.config.loaded` | PARCIAL | memoria / AppLog |
| `m00.config.missing` | RIESGO | UI/estado |
| `m00.feature_flag.evaluated` | AUSENTE | — |
| `m00.log.sanitized` | IMPLEMENTADO | logcat |
| `m00.build.debug_assemble` | IMPLEMENTADO | GitHub Actions |
| `m00.ci.unit_tests` / `m00.ci.lint` | IMPLEMENTADO | GitHub Actions |
| `m00.error.app_result_failure` | PARCIAL | UI / logcat |

### 6.2 M01 Auth

| Event key | Status | Destino |
|---|---|---|
| `m01.auth.login_success` | AUSENTE | GoTrue only |
| `m01.auth.login_failure` | RIESGO | logcat |
| `m01.auth.logout` | AUSENTE | — |
| `m01.auth.verify_email` | PARCIAL | account_status |
| `m01.auth.password_recovery` / `password_changed` | AUSENTE | GoTrue |
| `m01.consent.recorded` | IMPLEMENTADO | `user_consents` |
| `m01.consent.gate_unavailable` | INCONSISTENTE | AppLog fail-open |
| `m01.account.deletion_*` | IMPLEMENTADO | `account_deletion_requests` + Edge |

### 6.3 M02 Usuarios / roles

| Event key | Status | Destino |
|---|---|---|
| `m02.profile.*` / `privacy.settings_changed` | AUSENTE / PARCIAL | fila users |
| `m02.role.assigned/revoked` | IMPLEMENTADO | `role_assignment_history` |
| `m02.role.expired` | PARCIAL | acción permitida; sin job |
| `m02.status.changed` | IMPLEMENTADO / DUPLICADO path | `user_status_history` |
| `m02.permission.denied` | RIESGO | excepción SQL no persistida |
| `m02.admin.audit_read` | PARCIAL | lectura no auditada |

### 6.4 M03 Organizaciones

| Event key | Status | Destino |
|---|---|---|
| `m03.org.created` / `branch.changed` | AUSENTE / PARCIAL | filas dominio |
| `m03.invitation.*` | IMPLEMENTADO (notif) | `notification_events` |
| `m03.member.role_changed` / `removed` / `ownership.transferred` | IMPLEMENTADO (notif) | `notification_events` |
| Audit dedicado de invitaciones/roles | AUSENTE | gap vs notif |

### 6.5 M04 Moderación / soporte / verificación

| Familia | Status | Destino |
|---|---|---|
| report/case/action/appeal/verification/support CRUD staff | IMPLEMENTADO | `administrative_audit_log` vía `m04_audit` |
| Notificaciones M04→usuario (triggers 027) | IMPLEMENTADO | `notification_events` |
| Acceso sensible (quién vio reporter_id/docs) | RIESGO / PARCIAL | redacción sí; audit de acceso no |
| INTERNAL bodies | IMPLEMENTADO con cuidado | staff-only; verificar proyección |

### 6.6 M05 Archivos

| Event key | Status | Destino |
|---|---|---|
| `m05.upload.completed/failed/cancelled` | IMPLEMENTADO (notif) | `notification_events` |
| `m05.verification_document.ready` | IMPLEMENTADO | notif INTERNAL staff |
| `m05.signed_url.issued` / `download.performed` | RIESGO / AUSENTE | Storage sin audit app |
| `m05.file.deleted` / retention | PARCIAL / AUSENTE | Edge delete / sin job |
| `file_access_audit` writers | PARCIAL | `m05_audit` para ops definidas |

### 6.7 M06 Notificaciones

| Familia | Status | Destino |
|---|---|---|
| enqueue / materialize / plan / claim / mark | IMPLEMENTADO | tablas M06 |
| outbox / dead-letter | IMPLEMENTADO | tablas M06 (+ 028) |
| installations / preferences | IMPLEMENTADO | RPCs |
| deep link deny | RIESGO | deny local sin audit |
| `notification_access_audit` | PARCIAL / INCONSISTENTE | tabla sin writers |
| Edge push invoke | IMPLEMENTADO | console |
| legacy `create_notification` | INCONSISTENTE | self-only SYSTEM |

### 6.8 Campos transversales del catálogo

Para el diseño futuro, cada evento M07 debe declarar:

- módulo, event key, categoría, severidad, sensibilidad;
- actor (`auth.uid()` o actor técnico);
- recurso / organización;
- resultado;
- correlation ID;
- metadata **allowlisted**;
- retención;
- destino (audit / security / error / metric / analytics).

Hoy: correlation **AUSENTE** como primitivo unificado; metadata uneven; retención no tipada (legal 12m+ vs efímero logcat).

**Catálogo completo campo a campo:** ver [Anexo A](#anexo-a--catálogo-completo-de-eventos-m00m06).

---

## 7. Datos prohibidos — confirmación

| Dato | ¿Se registra hoy de forma segura? | Evidencia |
|---|---|---|
| Passwords | No en eventos app | sanitizer + no persistencia |
| Tokens / FCM raw | No en modelos públicos / logs de mensaje | fingerprint; Edge sanitize |
| Service role / FCM credentials | Solo secrets server-side | Edge; no Android |
| Signed URLs | Rechazadas en payloads M06 | `m06_validate_payload` / parser |
| SQL completo / stack crudo | Rechazado en payloads; riesgo en Throwable Log | PARCIAL |
| Chat bodies | No auditados | AUSENTE (correcto) |
| Bodies INTERNAL | No a requester; riesgo en audit note M04 | verificar proyección |
| Documentos / PII completa | No en notif; acceso file sin audit de download | RIESGO acceso |
| Coordenadas precisas | Redactadas en AppLogger | IMPLEMENTADO en sanitize |
| Provider message ID al cliente | No expuesto a Android | almacenado server-side en deliveries |

---

## 8. Privacidad, retención, permisos, exportaciones

### 8.1 Minimización / redacción / hashing

- Allowlists de payload M06 y sanitizers Android/Edge: **IMPLEMENTADO** parcial.
- Hashing de tokens (fingerprint): **IMPLEMENTADO** en instalaciones.
- Anonimización en deletion completed (`user_id=null`): **IMPLEMENTADO**.
- Retención tipada por categoría/sensibilidad: **AUSENTE** (policies futuras).
- Exportaciones controladas + audit de export: **AUSENTE**.
- “Auditar al auditor”: **AUSENTE**.

### 8.2 Permisos de lectura de observabilidad

- `audit.view` (018/022) para admin audit.
- Org audit: `organization.view_private`.
- File audit: `audit.view` / `moderation.view_sensitive`.
- AccountType / `active_modules`: **no** otorgan autoridad (confirmado en M02/M06).
- Roles M03: limitados a su organización.
- Propuesta: permisos M07 específicos (`observability.view`, `security_events.view`, `exports.create`) sin mezclar marketing.

---

## 9. Arquitectura objetivo (propuesta — **no implementada**)

### 9.1 Contratos de dominio futuros

```text
AuditEvent
SecurityEvent
ApplicationError
PerformanceMetric
HealthCheck
AnalyticsEvent          # producto, no marketing
AlertRule / AlertIncident
```

### 9.2 Persistencia propuesta (futura)

- Catálogo central `audit_events` (append-only, RLS deny client write).
- `security_events` (login fail, deny, token revoke).
- `application_errors` (fingerprint + sanitized_message).
- `performance_metrics` / `health_checks` (agregados).
- Vistas/proyecciones sobre silos existentes (M02/M04/M05/M06) sin duplicar hechos.
- Dead-letter genérico opcional; reutilizar `notification_dead_letters` para M06.

### 9.3 Metadata allowlist (propuesta)

Permitir: `event_key`, ids opacos, `module`, `result`, `reason_code`, `permission_code`, `channel`, `attempt_count`, `error_code` sanitizado, `app_version`, `platform`, hashes.

Prohibir: passwords, tokens, service role, signed URLs, SQL, stacks, chat, INTERNAL bodies, documentos, PII completa, coords precisas, provider IDs al cliente.

### 9.4 Correlation IDs

- Generar en borde Android/Edge (`X-Correlation-Id` / Idempotency-Key).
- Propagar a RPC `p_request_id` / columna unificada.
- Unificar `request_id` M02/M04 con `correlation_id` Edge.

### 9.5 Estrategias por superficie

| Superficie | Estrategia futura |
|---|---|
| Android | AppLogger → sink gobernado; sanitizar Throwable; correlation; no SDKs hasta Etapa aprobada |
| Supabase | writers DEFINER; RLS deny client; grants mínimos; no PUBLIC EXECUTE |
| Edge | structured logs + sanitize (patrón push); correlation obligatorio |
| CI/CD | coverage, migration lint, secret scan, evidence de release; sin secretos en logs |

### 9.6 Alertas / dashboards / health (propuesta)

Alertas: login_failure spike, permission_denied anomaly, dead-letter growth, Edge push fail rate, deletion failures, emit swallowed.

Dashboards: seguridad, moderación, archivos sensibles, notificaciones (outbox/deliveries), CI health.

Health checks: RPC ping, Edge push queue depth, storage reachable — **sin** implementar ahora.

### 9.7 Observabilidad específica M04 / M05 / M06

- **M04:** auditar accesos sensibles; redactar INTERNAL en lecturas; correlation en `m04_audit`.
- **M05:** auditar signed_url_issued / download (sin URL); retención/purga job.
- **M06:** poblar `notification_access_audit` o migrar a central; alertas DL; deep_link_denied.

---

## 10. Riesgos y gaps prioritarios

| ID | Riesgo | Severidad |
|---|---|---|
| R1 | `provider_message_id` server-side — no proyectar a cliente | Media |
| R2 | Denegaciones de autorización no auditadas | Alta |
| R3 | Login failure / recovery / password change sin security events | Alta |
| R4 | Signed URL / download sin audit de acceso | Alta |
| R5 | `notification_access_audit` sin writers | Media |
| R6 | Correlation ID no unificado | Alta |
| R7 | Lectura de auditoría no auditada | Media |
| R8 | Dual writers `user_status_history` | Media |
| R9 | Consent gate fail-open si falta tabla | Alta |
| R10 | Notas INTERNAL en audit log — proyección | Media |
| R11 | Sin capa central audit/security/error | Alta (núcleo M07) |
| R12 | Throwable no sanitizado en AppLog | Media |
| R13 | Silent catches Android Supabase | Media |
| R14 | Profile logout incompleto vs Session | Baja/Media |
| R15 | Staging 014–028 pendiente → release bloqueado | Alta (release) |

---

## 11. Decisiones que requieren aprobación

1. ¿Unificar silos existentes vía vistas + catálogo nuevo, o migrar hechos a `audit_events`?
2. ¿Cuándo autorizar el primer proveedor de crash/metrics (si alguno) vs sink propio en Supabase?
3. ¿Retenciones por sensibilidad (p.ej. SECURITY 24m, ERROR 12m, DEBUG 0 remoto)?
4. ¿Auditar denegaciones de permiso en caliente (volumen) o sampleado?
5. ¿Analítica de producto en misma DB o pipeline separado, siempre sin marketing?
6. ¿Permisos M07 nuevos vs reutilizar solo `audit.view`?
7. ¿Obligar correlation ID en todas las RPC mutadoras desde Etapa 2?

---

## 12. Archivos / artefactos futuros (no crear ahora)

```text
domain/observability/*Contracts.kt
data/... ObservabilityRepositories (Etapa posterior)
supabase/migrations/0xx_m07_observability_foundation.sql (solo tras aprobación)
docs/02-arquitectura/M07-etapa-2-*.md
docs/04-calidad/M07-pruebas-*.md
```

**Prohibido en Etapa 1 (cumplido):** migraciones, tablas, pantallas, repos, Edge nuevas, jobs, dashboards, alertas live, Crashlytics/Sentry/OTel/Analytics.

---

## 13. Plan por etapas (propuesta)

| Etapa | Alcance |
|---|---|
| **1** | Auditoría y diseño ← **esta etapa** |
| **2** | Contratos Kotlin + catálogo + allowlist + correlation + sanitización Throwable (sin proveedores) |
| **3** | Persistencia mínima `audit_events` / `security_events` / `application_errors` + writers seguros |
| **4** | Instrumentación selectiva M01/M02/M04/M05/M06 (denies, login fail, signed URL, DL alerts schema) |
| **5** | Health checks, métricas operativas, CI SQL lint, retención |
| **6** | Dashboards/alertas operativas + exportaciones auditadas + validación staging |

Cada etapa requiere aprobación explícita. **No iniciar Etapa 2** en este turno.

---

## 14. Calidad local (evidencia)

Comandos ejecutados (sin cambios funcionales):

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

| Check | Resultado |
|---|---|
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **489 tests, 0 failures, 0 errors, 0 skipped** |
| `lintDebug` | **SUCCESS** |

---

## 15. Staging 014–028

**PENDIENTE DE VALIDACIÓN REMOTA**

Sin acceso autorizado a staging en esta auditoría. No se aplicaron migraciones. No se usó producción. Release permanece bloqueado hasta evidencia remota (backup + historial + apply ordenado).

Secuencia pendiente:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022 → 023 → 024 → 025 → 026 → 027 → 028
```

---

## 16. Checklist Etapa 1

- [x] Commit base y rama correctos
- [x] Sin cambios funcionales / migraciones / proveedores
- [x] Inventario Android
- [x] Inventario Supabase
- [x] Inventario CI/CD
- [x] Catálogo M00–M06 clasificado
- [x] Datos prohibidos revisados
- [x] Arquitectura objetivo propuesta (no implementada)
- [x] Plan por etapas
- [x] Build/test/lint verdes
- [x] Staging declarado honestamente
- [x] Auth/username intactos
- [x] Sin Etapa 2 / M08 / merge / producción

---

## 17. Parada

Documento único de salida:

```text
/docs/02-arquitectura/M07-auditoria-inicial.md
```

**No iniciar M07 Etapa 2.**  
**No iniciar M08.**  
**No implementar email, crash SDKs, dashboards ni alertas.**  
**No hacer commit hasta revisión.**  
**No merge a `main`.**  
**No aplicar staging ni producción.**

---

## Anexo A — Catálogo completo de eventos M00–M06

Formato por fila:

`MODULE | EVENT_KEY | STATUS | CATEGORY | SEVERITY | SENSITIVITY | ACTOR | RESOURCE | ORG | RESULT | CORRELATION_ID | METADATA | RETENTION | CURRENT_DESTINATION | RISK | FUTURE_PROPOSAL`

STATUS = IMPLEMENTADO | PARCIAL | DUPLICADO | INCONSISTENTE | RIESGO | AUSENTE | FUTURO

Nota global: no existe aún capa central `audit_events` / `security_events` / `application_errors`. Los hechos viven en historiales, `administrative_audit_log`, cola M06 y logs efímeros.

### A.1 M00 — Fundación

| Event key | Status | Cat | Sev | Sens | Actor | Recurso | Org | Resultado | Corr | Metadata | Retención | Destino | Riesgo | Propuesta |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `m00.config.loaded` | PARCIAL | SYSTEM | INFO | INTERNAL | system(app) | AppConfig | NO | LOADED/MISSING | AUSENTE | env,isDebug,version,flags | no-remoto | memoria | detalle de setup si se loguea | SYSTEM config_snapshot sin keys |
| `m00.config.missing` | RIESGO | ERROR | ERROR | INTERNAL | system(app) | supabaseUrl | NO | MISSING | AUSENTE | missingConfigMessage | no-remoto | UI/estado | no loguear anon key | ERROR tipado CONFIG_MISSING |
| `m00.feature_flag.evaluated` | AUSENTE | SYSTEM | DEBUG | INTERNAL | system(app) | FeatureFlags | NO | ON/OFF | AUSENTE | flags tipadas | no-remoto | ninguno | flags pago/mapas sin traza | AUDIT config flag change |
| `m00.log.sanitized` | IMPLEMENTADO | SECURITY | DEBUG | INTERNAL | system(app) | log message | NO | REDACTED | AUSENTE | JWT,Bearer,email,phone,coords | efímero | logcat | best-effort regex | reusar en pipeline M07 |
| `m00.log.level_gate` | IMPLEMENTADO | SYSTEM | INFO | INTERNAL | system(app) | LoggingConfig | NO | LOGGED/SUPPRESSED | N.A. | enabled,verbose,isDebug | efímero | logcat | ops no persisten en release | sink remoto gobernado |
| `m00.build.debug_assemble` | IMPLEMENTADO | JOB | INFO | INTERNAL | ci | assembleDebug | NO | SUCCESS/FAIL | AUSENTE | gradle,commit | por-run | GitHub Actions | sin evidencia auditada | JOB/build evidence |
| `m00.ci.unit_tests` | IMPLEMENTADO | JOB | INFO | INTERNAL | ci | testDebugUnitTest | NO | PASS/FAIL | AUSENTE | resultados | por-run | GitHub Actions | sin coverage/SBOM | gates + coverage |
| `m00.ci.lint` | IMPLEMENTADO | JOB | WARNING | INTERNAL | ci | lintDebug | NO | PASS/FAIL | AUSENTE | findings | por-run | GitHub Actions | no correlacionado a release | migration-lint gate |
| `m00.error.app_result_failure` | PARCIAL | ERROR | ERROR | INTERNAL | system(app) | AppResult.Error | NO | FAILURE | AUSENTE | code/message | efímero | logcat/UI | detalle técnico | `application_errors` |

### A.2 M01 — Identidad y autenticación

| Event key | Status | Cat | Sev | Sens | Actor | Recurso | Org | Resultado | Corr | Metadata | Retención | Destino | Riesgo | Propuesta |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `m01.auth.login_success` | AUSENTE | SECURITY | INFO | SECURITY_SENSITIVE | user | session | NO | SUCCESS | AUSENTE | — | AUSENTE | GoTrue | sin evento dominio | security_events login_success |
| `m01.auth.login_failure` | RIESGO | SECURITY | WARNING | SECURITY_SENSITIVE | anon/user | credentials | NO | DENIED | AUSENTE | — | AUSENTE | logcat | brute-force no auditado | security_events + alertas |
| `m01.auth.logout` | AUSENTE | SECURITY | INFO | INTERNAL | user | session | NO | SUCCESS | AUSENTE | — | AUSENTE | signOut | no auditable | security_events logout |
| `m01.auth.verify_email` | PARCIAL | SECURITY | INFO | SECURITY_SENSITIVE | user | user | NO | VERIFIED/PENDING | AUSENTE | account_status | AUSENTE | GoTrue+status | sin evento dominio | email_verified |
| `m01.auth.password_recovery` | AUSENTE | SECURITY | WARNING | SECURITY_SENSITIVE | anon/user | user | NO | REQUESTED/DONE | AUSENTE | — | AUSENTE | GoTrue | sin audit | password_reset_* |
| `m01.auth.password_changed` | AUSENTE | SECURITY | NOTICE | SECURITY_SENSITIVE | user | credentials | NO | CHANGED | AUSENTE | — | AUSENTE | GoTrue | sin evento | credential_changed |
| `m01.consent.recorded` | IMPLEMENTADO | AUDIT | INFO | CONFIDENTIAL | user | user_consents | NO | RECORDED | AUSENTE | versions,locale,source | 12m+ legal | `user_consents` | fuera de catálogo unificado | enlazar audit_events |
| `m01.consent.gate_unavailable` | INCONSISTENTE | ERROR | WARNING | INTERNAL | system(app) | user_consents | NO | SKIPPED | AUSENTE | skip until migration | efímero | AppLog | fail-open | ERROR gobernado |
| `m01.account.deletion_requested` | IMPLEMENTADO | AUDIT | NOTICE | SECURITY_SENSITIVE | user | deletion_requests | NO | pending | PARCIAL | status,requested_at | 12m | tabla+Edge | corr solo Edge | persistir correlation_id |
| `m01.account.deletion_completed` | IMPLEMENTADO | AUDIT | NOTICE | SECURITY_SENSITIVE | user/edge | deletion_requests | NO | completed | PARCIAL | user_id=null | 12m | tabla | anonimiza bien | AUDIT + corr corto |
| `m01.account.deletion_failed` | IMPLEMENTADO | ERROR | ERROR | SECURITY_SENSITIVE | edge | deletion_requests | NO | failed | PARCIAL | failure_code | 12m | tabla | sin alerta tasa | alertar failures |

### A.3 M02 — Usuarios, roles y permisos

| Event key | Status | Cat | Sev | Sens | Actor | Recurso | Org | Resultado | Corr | Metadata | Retención | Destino | Riesgo | Propuesta |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `m02.profile.onboarding_completed` | PARCIAL | AUDIT | INFO | CONFIDENTIAL | user | users | NO | COMPLETED | AUSENTE | onboarding,username | AUSENTE | fila users | sin audit | AUDIT profile_changed |
| `m02.profile.updated` | AUSENTE | AUDIT | INFO | CONFIDENTIAL | user | users | NO | UPDATED | AUSENTE | campos | AUSENTE | update_my_profile | sin diff | AUDIT allowlisted |
| `m02.privacy.settings_changed` | AUSENTE | AUDIT | INFO | CONFIDENTIAL | user | privacy_settings | NO | UPDATED | AUSENTE | visibility flags | AUSENTE | upsert | sin traza | AUDIT privacy_changed |
| `m02.role.assigned` | IMPLEMENTADO | AUDIT | NOTICE | RESTRICTED | admin | role_assignment_history | NO | ASSIGN | PARCIAL | role,prev/new,reason,request_id | 12m | tabla 018 | request_id ≠ corr global | proyección audit_events |
| `m02.role.revoked` | IMPLEMENTADO | AUDIT | NOTICE | RESTRICTED | admin | role_assignment_history | NO | REVOKE | PARCIAL | ACTIVE→REVOKED | 12m | tabla 018 | sin corr Android | AUDIT correlacionado |
| `m02.role.expired` | PARCIAL | AUDIT | INFO | RESTRICTED | system | role_assignment_history | NO | EXPIRE | AUSENTE | action=EXPIRE | 12m | constraint | sin job EXPIRE | JOB role_expiry |
| `m02.status.changed` | IMPLEMENTADO / DUPLICADO | AUDIT | WARNING | RESTRICTED | admin | user_status_history | NO | status change | PARCIAL | prev/new,reason,request_id | 12m | 018+022 | dual writers | helper único |
| `m02.permission.denied` | RIESGO | AUTHORIZATION | WARNING | SECURITY_SENSITIVE | user/staff | has_permission | NO | DENIED | AUSENTE | permission_code | AUSENTE | excepción SQL | no persiste | authorization_denied |
| `m02.admin.audit_read` | PARCIAL | DATA_ACCESS | INFO | RESTRICTED | admin(audit.view) | admin_audit_log | NO | ALLOWED | AUSENTE | filtros | N.A. | list RPC | lectura no auditada | DATA_ACCESS audit_read |

### A.4 M03 — Organizaciones

| Event key | Status | Cat | Sev | Sens | Actor | Recurso | Org | Resultado | Corr | Metadata | Retención | Destino | Riesgo | Propuesta |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `m03.org.created` | PARCIAL | AUDIT | INFO | CONFIDENTIAL | owner | organizations | SI | CREATED | AUSENTE | org_id,type,name | AUSENTE | fila | sin audit | AUDIT org_lifecycle |
| `m03.invitation.created` | IMPLEMENTADO | INVITATION | INFO | CONFIDENTIAL | owner/admin | invitations | SI | PENDING | PARCIAL | dedup/idem | evento M06 | notification_events | notif≠audit | AUDIT invite_created |
| `m03.invitation.accepted` | IMPLEMENTADO | ORGANIZATION | INFO | CONFIDENTIAL | invited | invitations | SI | ACCEPTED | PARCIAL | idem | evento M06 | notification_events | sin audit | AUDIT status |
| `m03.invitation.declined` | IMPLEMENTADO | ORGANIZATION | INFO | CONFIDENTIAL | invited | invitations | SI | DECLINED | PARCIAL | idem | evento M06 | notification_events | sin audit | AUDIT status |
| `m03.invitation.expired` | IMPLEMENTADO | ORGANIZATION | INFO | CONFIDENTIAL | system | invitations | SI | EXPIRED | PARCIAL | idem | evento M06 | notification_events | sin audit | AUDIT status |
| `m03.member.role_changed` | IMPLEMENTADO | ORGANIZATION | NOTICE | CONFIDENTIAL | owner/admin | memberships | SI | ROLE_CHANGED | PARCIAL | dedup | evento M06 | notification_events | sin audit | AUDIT org_role |
| `m03.member.removed` | IMPLEMENTADO | ORGANIZATION | NOTICE | CONFIDENTIAL | owner/admin | memberships | SI | REMOVED | PARCIAL | dedup | evento M06 | notification_events | SAFE_HOME ok | AUDIT removed |
| `m03.ownership.transferred` | IMPLEMENTADO | ORGANIZATION | WARNING | CONFIDENTIAL | owner | memberships | SI | OWNERSHIP_TRANSFERRED | PARCIAL | dedup | evento M06 | notification_events | crítico sin audit | AUDIT RESTRICTED |
| `m03.branch.changed` | AUSENTE | AUDIT | INFO | CONFIDENTIAL | owner/admin | branches | SI | CREATED/UPDATED | AUSENTE | branch_id | AUSENTE | tabla 020 | sin evento | AUDIT branch |

### A.5 M04 — Moderación / soporte / verificación

| Event key | Status | Cat | Sev | Sens | Actor | Recurso | Org | Resultado | Corr | Metadata | Retención | Destino | Riesgo | Propuesta |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `m04.report.created` | IMPLEMENTADO | MODERATION | INFO | RESTRICTED | reporter | content_reports | NO | CREATE | PARCIAL | prev/new,reason,request_id | 12m | administrative_audit_log | reporter_id sensible | mapear audit_events |
| `m04.report.triaged` | IMPLEMENTADO | MODERATION | INFO | RESTRICTED | staff | content_reports | NO | TRIAGE | PARCIAL | status/priority | 12m | admin_audit | ok | idem |
| `m04.report.marked_duplicate` | IMPLEMENTADO | MODERATION | INFO | RESTRICTED | staff | content_reports | NO | DUPLICATE | PARCIAL | prev status | 12m | admin_audit | ok | idem |
| `m04.case.created` | IMPLEMENTADO | MODERATION | INFO | RESTRICTED | staff | moderation_cases | NO | CREATE | PARCIAL | new_value | 12m | admin_audit | ok | idem |
| `m04.case.report_attached` | IMPLEMENTADO | MODERATION | INFO | RESTRICTED | staff | moderation_cases | NO | ATTACH | PARCIAL | new_value | 12m | admin_audit | ok | idem |
| `m04.case.assigned` | IMPLEMENTADO | MODERATION | INFO | RESTRICTED | staff | moderation_cases | NO | ASSIGN | PARCIAL | assigned_to | 12m | admin_audit | assignee PII | idem |
| `m04.case.status_changed` | IMPLEMENTADO | MODERATION | NOTICE | RESTRICTED | staff | moderation_cases | NO | STATUS | PARCIAL | prev status | 12m | admin_audit | ok | idem |
| `m04.case.internal_note_added` | IMPLEMENTADO | MODERATION | INFO | SECURITY_SENSITIVE | staff | moderation_cases | NO | INTERNAL_NOTE | PARCIAL | note≤500 | 12m | admin_audit | INTERNAL en note | redactar lectura |
| `m04.action.applied` | IMPLEMENTADO | MODERATION | WARNING | RESTRICTED | staff | moderation_actions | NO | APPLY | PARCIAL | action | 12m | admin_audit+notif | doble efecto ok | measure_applied |
| `m04.moderation.action_applied` | IMPLEMENTADO | MODERATION | WARNING | SECURITY_SENSITIVE | trigger | moderation_actions | NO | MODERATION_ACTION | PARCIAL | dedup,body genérico | evento M06 | notification_events | body no revela medida | mantener |
| `m04.appeal.submitted` | IMPLEMENTADO | APPEAL | INFO | SECURITY_SENSITIVE | user | appeals | NO | SUBMIT | PARCIAL | new_value | 12m | admin_audit+notif | ok | idem |
| `m04.appeal.assigned` | IMPLEMENTADO | APPEAL | INFO | RESTRICTED | staff | appeals | NO | ASSIGN | PARCIAL | assigned_to | 12m | admin_audit | PII interna | idem |
| `m04.appeal.reviewed` | IMPLEMENTADO | APPEAL | NOTICE | SECURITY_SENSITIVE | staff | appeals | NO | REVIEW | PARCIAL | prev status | 12m | admin_audit | ok | idem |
| `m04.appeal.resolved` | IMPLEMENTADO | APPEAL | NOTICE | SECURITY_SENSITIVE | trigger | appeals | NO | RESOLVED | PARCIAL | dedup | evento M06 | notification_events | body genérico | ok |
| `m04.verification.assigned` | IMPLEMENTADO | VERIFICATION | INFO | RESTRICTED | staff | verification_reviews | SI | ASSIGN | PARCIAL | assigned_to | 12m | admin_audit | ok | idem |
| `m04.verification.decided` | IMPLEMENTADO | VERIFICATION | NOTICE | RESTRICTED | staff | verification_reviews | SI | DECISION | PARCIAL | decision | 12m | admin_audit+notif | review_note sensible | idem |
| `m04.verification.notify` | IMPLEMENTADO | VERIFICATION | NOTICE | SECURITY_SENSITIVE | trigger | verification_reviews | SI | STATUS | PARCIAL | dedup | evento M06 | notification_events | scope owners ok | ok |
| `m04.support.ticket_created` | IMPLEMENTADO | SUPPORT | INFO | CONFIDENTIAL | requester | support_tickets | NO | CREATE | PARCIAL | new_value | 12m | admin_audit+notif | ok | idem |
| `m04.support.assigned` | IMPLEMENTADO | SUPPORT | INFO | RESTRICTED | staff | support_tickets | NO | ASSIGN | PARCIAL | assigned_to | 12m | admin_audit | PII | idem |
| `m04.support.status_changed` | IMPLEMENTADO | SUPPORT | INFO | RESTRICTED | staff | support_tickets | NO | STATUS | PARCIAL | status/priority | 12m | admin_audit | ok | idem |
| `m04.support.internal_message` | IMPLEMENTADO | SUPPORT | INFO | SECURITY_SENSITIVE | staff | support_tickets | NO | INTERNAL | PARCIAL | note INTERNAL | 12m | admin_audit | nunca a requester | redactar |
| `m04.support.visible_reply` | IMPLEMENTADO | SUPPORT | INFO | CONFIDENTIAL | staff | ticket_messages | NO | VISIBLE_REPLY | PARCIAL | dedup | evento M06 | notification_events | REQUESTER_VISIBLE | ok |
| `m04.support.internal_update` | IMPLEMENTADO | SUPPORT | INFO | SECURITY_SENSITIVE | staff | ticket_messages | NO | INTERNAL | PARCIAL | is_internal+perm | evento M06 | notification_events | staff-only | ok |
| `m04.sensitive.access_projection` | PARCIAL | DATA_ACCESS | INFO | SECURITY_SENSITIVE | staff | reports/support/verif | SI/NO | ALLOWED/REDACTED | AUSENTE | view_sensitive | AUSENTE | RLS+RPC 023 | acceso no auditado | sensitive_view |
| `m04.audit.helper_write` | IMPLEMENTADO | AUDIT | INFO | RESTRICTED | DEFINER | admin_audit_log | NO | WRITTEN | PARCIAL | m04_audit fields | 12m | administrative_audit_log | base fuerte | unificar M07 |

### A.6 M05 — Archivos

| Event key | Status | Cat | Sev | Sens | Actor | Recurso | Org | Resultado | Corr | Metadata | Retención | Destino | Riesgo | Propuesta |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `m05.upload.session_started` | AUSENTE | FILE | INFO | CONFIDENTIAL | user | upload_sessions | SI/NO | PENDING | AUSENTE | asset_id | AUSENTE | fila | sin evento | upload_started |
| `m05.upload.completed` | IMPLEMENTADO | SYSTEM | INFO | CONFIDENTIAL | trigger | upload_sessions | SI/NO | COMPLETED | PARCIAL | dedup | evento M06 | notification_events | solo notif | + AUDIT state |
| `m05.upload.failed` | IMPLEMENTADO | ERROR | WARNING | CONFIDENTIAL | trigger | upload_sessions | SI/NO | FAILED | PARCIAL | dedup | evento M06 | notification_events | sin causa técnica | ERROR code |
| `m05.upload.cancelled` | IMPLEMENTADO | SYSTEM | INFO | CONFIDENTIAL | user/system | upload_sessions | SI/NO | CANCELLED | PARCIAL | dedup | evento M06 | notification_events | ok | idem |
| `m05.verification_document.ready` | IMPLEMENTADO | VERIFICATION | NOTICE | SECURITY_SENSITIVE | trigger | file_assets | SI | READY | PARCIAL | INTERNAL,no URL | evento M06 | notification_events | sin signed URL | mantener |
| `m05.signed_url.issued` | RIESGO | DATA_ACCESS | INFO | SECURITY_SENSITIVE | user/staff | storage | SI/NO | ISSUED | AUSENTE | NUNCA la URL | AUSENTE | Storage | no auditado | signed_url_issued |
| `m05.download.performed` | AUSENTE | DATA_ACCESS | INFO | CONFIDENTIAL | user/staff | storage | SI/NO | DOWNLOADED | AUSENTE | asset_id | AUSENTE | Storage logs | sin audit app | file_download |
| `m05.file.deleted` | PARCIAL | FILE | NOTICE | CONFIDENTIAL | user/edge | storage/assets | SI/NO | DELETED | PARCIAL | prefix users/uid | AUSENTE | Edge delete | sin evento por archivo | AUDIT deleted |
| `m05.retention.expiry` | AUSENTE | JOB | INFO | INTERNAL | system | file_assets | SI/NO | PURGED | AUSENTE | policy | AUSENTE | ninguno | sin job | retention_purge |
| `m05.storage.error` | PARCIAL | ERROR | ERROR | INTERNAL | edge | storage | SI/NO | FAILED | PARCIAL | failure_code | efímero | Edge+deletion | no centralizado | application_errors |

### A.7 M06 — Notificaciones

| Event key | Status | Cat | Sev | Sens | Actor | Recurso | Org | Resultado | Corr | Metadata | Retención | Destino | Riesgo | Propuesta |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `m06.event.enqueued` | IMPLEMENTADO | NOTIFICATION | INFO | CONFIDENTIAL | service_role | notification_events | SI/NO | ENQUEUED | PARCIAL | event_key,origin,dedup,idem | por-flujo | notification_events | no es audit_events | puente M07 |
| `m06.recipient.materialized` | IMPLEMENTADO | NOTIFICATION | INFO | CONFIDENTIAL | service_role | notifications | SI/NO | MATERIALIZED | PARCIAL | dedup,redaction | por-flujo | notifications | SENSITIVE body ok | ok |
| `m06.inbox.read` | IMPLEMENTADO | NOTIFICATION | DEBUG | CONFIDENTIAL | user | notifications | NO | READ | AUSENTE | id | N.A. | RPC mark read | esperable | N.A. |
| `m06.inbox.archived` | IMPLEMENTADO | NOTIFICATION | DEBUG | PRIVATE | user | notifications | NO | ARCHIVED | AUSENTE | id | N.A. | RPC | — | N.A. |
| `m06.inbox.deleted_logical` | IMPLEMENTADO | NOTIFICATION | DEBUG | PRIVATE | user | notifications | NO | DELETED | AUSENTE | id | N.A. | RPC soft-delete | — | N.A. |
| `m06.delivery.in_app` | IMPLEMENTADO | NOTIFICATION | INFO | CONFIDENTIAL | system | deliveries | NO | DELIVERED | PARCIAL | IN_APP | por-flujo | deliveries | ok | ok |
| `m06.delivery.push_planned` | IMPLEMENTADO | NOTIFICATION | INFO | CONFIDENTIAL | service_role | deliveries | NO | PENDING | PARCIAL | prefs/quiet | por-flujo | plan_push | SECURITY ignora quiet | ok |
| `m06.delivery.push_claimed` | IMPLEMENTADO | NOTIFICATION | INFO | CONFIDENTIAL | edge | deliveries | NO | PROCESSING | PARCIAL | skip locked | por-flujo | claim_push | token a Edge ok | ok |
| `m06.delivery.push_result` | IMPLEMENTADO | NOTIFICATION | INFO | CONFIDENTIAL | edge | deliveries | NO | DELIVERED/FAILED | PARCIAL | failure_code,provider_id | por-flujo | mark_result | provider_id server-only | no proyectar cliente |
| `m06.delivery.token_invalidated` | IMPLEMENTADO | SECURITY | NOTICE | SECURITY_SENSITIVE | edge | installations | NO | REVOKED | PARCIAL | INVALID_TOKEN | por-flujo | auto-disable | ok | installation_revoked |
| `m06.outbox.enqueued` | IMPLEMENTADO | JOB | INFO | INTERNAL | service_role | outbox | NO | PENDING | PARCIAL | unique event_id | por-flujo | outbox | ok | JOB obs |
| `m06.outbox.claimed` | IMPLEMENTADO | JOB | INFO | INTERNAL | service_role | outbox | NO | CLAIMED | PARCIAL | claimed_by,attempts | por-flujo | claim_outbox | ok | ok |
| `m06.outbox.processed` | IMPLEMENTADO | JOB | INFO | INTERNAL | system | outbox | NO | PROCESSED | AUSENTE | — | por-flujo | mark processed | ok | ok |
| `m06.outbox.failed` | IMPLEMENTADO | JOB | WARNING | INTERNAL | system | outbox | NO | RETRY/DEAD | PARCIAL | error_code,backoff | por-flujo | mark failed | ok | alerta crecimiento |
| `m06.dead_letter.recorded` | IMPLEMENTADO | ERROR | ERROR | INTERNAL | system | dead_letters | NO | DEAD_LETTER | PARCIAL | sanitized_context | hasta resolución | dead_letters | anti-fuga ok | alerta DL |
| `m06.emit.failed_swallowed` | INCONSISTENTE→PARCIAL | ERROR | WARNING | INTERNAL | system | dead_letters | NO | SWALLOWED→DL | PARCIAL | event_key,message | hasta resolución | 028 handler | 027 swallow; 028 mitiga | observar M06_EMIT_FAILED |
| `m06.installation.registered` | IMPLEMENTADO | SECURITY | INFO | SECURITY_SENSITIVE | user | installations | NO | REGISTERED | AUSENTE | fingerprint (no token) | por-dispositivo | register RPC | token no retornado | device_registered |
| `m06.installation.token_rotated` | IMPLEMENTADO | SECURITY | INFO | SECURITY_SENSITIVE | user | installations | NO | ROTATED | AUSENTE | fingerprint | por-dispositivo | rotate RPC | no filtra token | token_rotated |
| `m06.installation.revoked` | IMPLEMENTADO | SECURITY | NOTICE | SECURITY_SENSITIVE | user | installations | NO | REVOKED | AUSENTE | — | por-dispositivo | revoke RPC | ok | device_revoked |
| `m06.preference.updated` | IMPLEMENTADO | AUDIT | INFO | CONFIDENTIAL | user | preferences | NO | UPDATED | AUSENTE | category,channels,marketing_consent | N.A. | update RPC | marketing=consentimiento | AUDIT preference |
| `m06.deep_link.resolved` | IMPLEMENTADO | NOTIFICATION | DEBUG | CONFIDENTIAL | user | deep link | SI/NO | RESOLVED | AUSENTE | allowlist | N.A. | Android resolver | SAFE_HOME | N.A. |
| `m06.deep_link.permission_denied` | RIESGO | AUTHORIZATION | WARNING | SECURITY_SENSITIVE | user | deep link | SI/NO | DENIED | AUSENTE | permission | AUSENTE | deny local | no auditado | deeplink_denied |
| `m06.access_audit.decision` | PARCIAL | AUDIT | INFO | RESTRICTED | actor | access_audit | NO | ALLOWED/DENIED | AUSENTE | action,reason | 12m | tabla sin writers | schema vacío | poblar o migrar |
| `m06.legacy.create_notification` | INCONSISTENTE | NOTIFICATION | INFO | PRIVATE | user | notifications | NO | SELF/DENIED | AUSENTE | SYSTEM self-only | N.A. | RPC legacy | deuda dual pipeline | deprecar |
| `m06.edge.push_invoked` | IMPLEMENTADO | INTEGRATION | INFO | SECURITY_SENSITIVE | edge | Edge push | NO | processed | AUSENTE | counts | efímero | console | sanitizeError ok | HEALTH metrics |

### A.8 Conteos de clasificación (aprox.)

| Status | Cantidad aproximada |
|---|---|
| IMPLEMENTADO | ~55 |
| PARCIAL | ~20 |
| AUSENTE | ~15 |
| RIESGO | ~8 |
| INCONSISTENTE | ~4 |
| DUPLICADO | 1 path (`user_status_history`) |
| FUTURO | capa central M07 completa |

Convención event_key observada: `m0X.<dominio>.<accion>` — adoptar en M07 para `audit_events` / `security_events`.
