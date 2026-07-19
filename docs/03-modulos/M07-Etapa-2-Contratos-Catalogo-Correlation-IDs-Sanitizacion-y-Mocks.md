# LEOVER — M07 Etapa 2: Contratos, Catálogo, Correlation IDs, Sanitización y Mocks

**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 2 — Contratos Kotlin puros y base local  
**Estado de entrada:** Etapa 1 aprobada y consolidada  
**Commit base:** `a4f2f681c241d4f0dd0960685dab591d039227f3`  
**Rama base:** `m07/auditoria-analitica-observabilidad-auditoria`  
**Calidad de entrada:** 489 tests, 0 failures, 0 errors, 0 skipped; `assembleDebug` y `lintDebug` en SUCCESS  
**Staging heredado:** migraciones `014`–`028` pendientes de validación remota  
**Objetivo:** construir contratos, catálogo central, correlation IDs, sanitización segura, interfaces y mocks deterministas sin persistencia SQL ni proveedores externos.  
**Fuera de alcance:** migraciones, tablas, dashboards, alertas reales, Crashlytics, Firebase Analytics, Sentry, OpenTelemetry, producción, M08 y cambios de auth/username.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M07-auditoria-inicial.md`
2. `/docs/03-modulos/M07-Auditoria-Analitica-y-Observabilidad.md`
3. `/docs/02-arquitectura/M06-cierre-final.md`
4. `/docs/04-calidad/M06-reporte-validacion-staging.md`
5. ADR-0001 a ADR-0005.
6. Este documento.

---

## 2. Protección Git

1. Confirmar commit base:

```text
a4f2f681c241d4f0dd0960685dab591d039227f3
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m07/etapa-2-contratos-catalogo-correlation-sanitizacion
```

4. No incorporar GPS, mapas o pagos.
5. No hacer merge a `main`.
6. No iniciar M07 Etapa 3.
7. No iniciar M08.
8. No modificar username, `AuthRepository`, `domain/auth` ni `UsernameValidators`.
9. No aplicar staging ni producción.

---

## 3. Alcance técnico

Crear contratos Kotlin puros para:

```text
AuditEvent
SecurityEvent
ApplicationError
PerformanceMetric
HealthCheck
AnalyticsEvent
AlertRule
AlertIncident
ObservabilityExport
RetentionPolicy
CorrelationContext
```

La Etapa 2 no persiste remotamente. Todo funciona mediante interfaces y mocks locales deterministas.

---

## 4. Paquetes sugeridos

Crear una estructura equivalente:

```text
app/src/main/java/com/comunidapp/app/domain/observability/
app/src/main/java/com/comunidapp/app/domain/observability/catalog/
app/src/main/java/com/comunidapp/app/domain/observability/correlation/
app/src/main/java/com/comunidapp/app/domain/observability/sanitization/
app/src/main/java/com/comunidapp/app/domain/observability/retention/
app/src/main/java/com/comunidapp/app/domain/observability/authorization/
app/src/main/java/com/comunidapp/app/data/observability/
app/src/main/java/com/comunidapp/app/data/repository/
```

Respetar la arquitectura actual. No duplicar `AppLogger`, `AppError`, `DataProvider` ni repositorios existentes.

---

## 5. Categorías

Definir exactamente:

```text
AUDIT
SECURITY
AUTHORIZATION
ERROR
PERFORMANCE
HEALTH
BUSINESS
PRODUCT_ANALYTICS
DATA_ACCESS
EXPORT
INTEGRATION
JOB
NOTIFICATION
FILE
MODERATION
SUPPORT
SYSTEM
OTHER
```

Reglas:

- categoría desconocida → `OTHER`;
- categoría no concede acceso;
- PRODUCT_ANALYTICS no equivale a marketing;
- AUDIT y SECURITY deben diferenciarse;
- ERROR no debe usarse para estados normales.

---

## 6. Severidad

Definir:

```text
DEBUG
INFO
NOTICE
WARNING
ERROR
CRITICAL
```

Reglas:

- DEBUG no persiste remotamente por defecto;
- CRITICAL requiere política de alerta futura;
- severidad no saltea permisos ni sanitización;
- eventos normales no deben inflarse a ERROR;
- denegaciones esperadas usan WARNING/NOTICE según política.

---

## 7. Sensibilidad

Definir:

```text
PUBLIC_AGGREGATE
INTERNAL
CONFIDENTIAL
RESTRICTED
SECURITY_SENSITIVE
```

Reglas:

- passwords, tokens, secretos y signed URLs siempre prohibidos;
- contenido de chat siempre prohibido;
- body INTERNAL siempre prohibido;
- documentos y PII completa prohibidos;
- coordenadas precisas prohibidas salvo contrato futuro explícito;
- SECURITY_SENSITIVE exige acceso M02 específico;
- exportación de RESTRICTED/SECURITY_SENSITIVE requiere permiso y auditoría futura.

---

## 8. Resultado y actor

### Resultado

Definir algo equivalente:

```text
SUCCESS
FAILURE
DENIED
PARTIAL
SKIPPED
RETRYING
DEAD_LETTER
UNKNOWN
```

### Actor

Tipos:

```text
ANONYMOUS
AUTHENTICATED_USER
PLATFORM_STAFF
ORGANIZATION_MEMBER
SYSTEM
EDGE_FUNCTION
DATABASE_TRIGGER
CI
EXTERNAL_INTEGRATION
UNKNOWN
```

Reglas:

- Android no puede autodeclararse staff;
- AccountType y `active_modules` no otorgan autoridad;
- el actor real se resolverá server-side en Etapa 3;
- en mocks se usa contexto explícito;
- UNKNOWN se maneja deny-by-default.

---

## 9. Catálogo central de eventos

Crear un catálogo local tipado.

Cada definición debe incluir:

```text
eventKey
module
category
defaultSeverity
sensitivity
actorTypes
resourceTypes
organizationScoped
allowedMetadataKeys
requiredMetadataKeys
retentionPolicyKey
remotePersistenceAllowed
analyticsAllowed
samplingPolicy
```

Convención obligatoria:

```text
m00.<dominio>.<accion>
m01.<dominio>.<accion>
...
m07.<dominio>.<accion>
```

Reglas:

- event keys únicos;
- no aceptar keys arbitrarias en producción;
- metadata deny-by-default;
- required keys validadas;
- unknown event → rechazo o fallback explícito no persistente;
- catálogo M00–M06 basado en la auditoría Etapa 1;
- catálogo M07 incluye eventos propios.

---

## 10. Eventos mínimos del catálogo

Incluir contratos para al menos:

### M00

```text
m00.config.loaded
m00.config.missing
m00.feature_flag.evaluated
m00.build.debug_assemble
m00.ci.unit_tests
m00.ci.lint
m00.error.app_result_failure
```

### M01

```text
m01.auth.login_success
m01.auth.login_failure
m01.auth.logout
m01.auth.verify_email
m01.auth.password_recovery
m01.auth.password_changed
m01.consent.recorded
m01.consent.gate_unavailable
m01.account.deletion_requested
m01.account.deletion_completed
m01.account.deletion_failed
```

### M02

```text
m02.profile.onboarding_completed
m02.profile.updated
m02.privacy.settings_changed
m02.role.assigned
m02.role.revoked
m02.role.expired
m02.status.changed
m02.permission.denied
m02.admin.audit_read
```

### M03

```text
m03.org.created
m03.invitation.created
m03.invitation.accepted
m03.invitation.declined
m03.invitation.expired
m03.member.role_changed
m03.member.removed
m03.ownership.transferred
m03.branch.changed
```

### M04

Incluir familias:

```text
m04.report.*
m04.case.*
m04.action.*
m04.appeal.*
m04.verification.*
m04.support.*
m04.sensitive.access_projection
m04.audit.helper_write
```

### M05

```text
m05.upload.session_started
m05.upload.completed
m05.upload.failed
m05.upload.cancelled
m05.verification_document.ready
m05.signed_url.issued
m05.download.performed
m05.file.deleted
m05.retention.expiry
m05.storage.error
```

### M06

Incluir familias:

```text
m06.event.*
m06.recipient.*
m06.inbox.*
m06.delivery.*
m06.outbox.*
m06.dead_letter.*
m06.installation.*
m06.preference.*
m06.deep_link.*
m06.access_audit.*
m06.edge.*
```

### M07

```text
m07.event.accepted
m07.event.rejected
m07.event.sanitized
m07.correlation.created
m07.correlation.propagated
m07.error.captured
m07.health.checked
m07.export.requested
m07.export.denied
m07.alert.rule_evaluated
```

No instrumentar todavía M01–M06. Solo definir contratos y catálogo.

---

## 11. Metadata allowlist

Crear un validador central.

Keys permitidas iniciales:

```text
event_key
module
result
reason_code
permission_code
resource_type
resource_id
organization_id
channel
attempt_count
error_code
app_version
platform
environment
build_type
job_name
duration_ms
status_code
feature_flag
correlation_id
request_id
installation_fingerprint
file_type
file_size_bucket
```

Reglas:

- ids opacos permitidos;
- nombres, emails, teléfonos y direcciones no;
- raw token no;
- JWT/Bearer no;
- signed URL no;
- service role no;
- SQL no;
- stack trace no;
- provider message ID no al cliente;
- contenido de chat no;
- body INTERNAL no;
- documentos/base64 no;
- PII completa no;
- coordenadas precisas no.

Metadata extra no declarada → rechazo.

---

## 12. Correlation IDs

Crear:

```text
CorrelationId
CorrelationContext
CorrelationIdGenerator
CorrelationContextProvider
CorrelationPropagationPolicy
```

Requisitos:

- UUID aleatorio o formato equivalente seguro;
- no contiene user ID, email, timestamp legible ni PII;
- único por operación raíz;
- puede derivar child context conservando root;
- soporta `requestId`, `sessionId`, `jobId`, `eventId`;
- límites de longitud;
- caracteres allowlisted;
- invalid/blank → regeneración o rechazo según contexto;
- logout/cambio de cuenta limpia contexto de sesión;
- tests deterministas con generador inyectable.

No enviar headers ni modificar RPC todavía.

---

## 13. Sanitización

Crear o ampliar contratos equivalentes:

```text
SensitiveDataSanitizer
ThrowableSanitizer
MetadataSanitizer
StructuredLogSanitizer
ObservabilityErrorSanitizer
```

### Debe redactar

- JWT;
- Bearer;
- API keys;
- access/refresh tokens;
- FCM tokens;
- service role;
- passwords;
- secrets;
- signed URLs;
- query strings sensibles;
- emails;
- teléfonos;
- coordenadas;
- SQL;
- stack traces;
- rutas locales sensibles;
- headers;
- base64 grande;
- contenido de chat;
- body INTERNAL.

### Throwable

No loguear el objeto crudo en modo remoto.

Crear un modelo seguro:

```text
SanitizedThrowable
errorClass
safeMessage
fingerprint
causeDepth
isRetryable
```

Reglas:

- fingerprint estable sin PII;
- máximo de profundidad;
- mensaje truncado;
- causas cíclicas seguras;
- stack no persistido;
- en debug local se puede conservar comportamiento actual solo si pasa sanitizer;
- no romper `AppLogger`.

---

## 14. Retención

Crear contratos, sin SQL:

```text
RetentionPolicy
RetentionPolicyKey
RetentionDecision
```

Políticas iniciales sugeridas:

```text
NO_REMOTE
DEBUG_7_DAYS
TECHNICAL_30_DAYS
TECHNICAL_90_DAYS
AUDIT_12_MONTHS
SECURITY_24_MONTHS
AGGREGATE_24_MONTHS
UNTIL_RESOLUTION
LEGAL_REVIEW_REQUIRED
```

Reglas:

- DEBUG → NO_REMOTE por defecto;
- SECURITY_SENSITIVE → política explícita;
- UNKNOWN → NO_REMOTE;
- plazos legales quedan marcados como sujetos a revisión;
- política no se calcula desde Android arbitrariamente.

---

## 15. Sampling

Crear contratos:

```text
SamplingPolicy
ALWAYS
NEVER
RATE
FIRST_PER_WINDOW
ERROR_ONLY
```

Reglas:

- AUDIT crítico y SECURITY crítico → ALWAYS;
- DEBUG → NEVER remoto;
- denegaciones de permiso pueden usar sampling futuro, nunca silenciamiento total;
- pruebas deterministas con random/clock inyectables;
- sampling no se implementa remoto.

---

## 16. Autorización local

Crear decisiones deny-by-default:

```text
ALLOWED
DENIED_NOT_AUTHENTICATED
DENIED_PERMISSION
DENIED_ORGANIZATION
DENIED_SENSITIVITY
DENIED_EXPORT
DENIED_METADATA
DENIED_EVENT_KEY
DENIED_UNKNOWN
```

Contexto:

```text
ObservabilityAuthorizationContext
actorId
permissions
organizationIds
isPlatformActor
requestedSensitivity
requestedAction
```

Permisos conceptuales:

```text
AUDIT_VIEW
AUDIT_VIEW_SENSITIVE
SECURITY_EVENTS_VIEW
OBSERVABILITY_VIEW
OBSERVABILITY_MANAGE
ANALYTICS_VIEW
EXPORT_AUDIT_DATA
ALERT_MANAGE
```

No modificar M02 en esta etapa. Usar códigos conceptuales/mapeo futuro.

---

## 17. Repositorios e interfaces

Crear interfaces:

```text
AuditEventRepository
SecurityEventRepository
ApplicationErrorRepository
PerformanceMetricRepository
HealthCheckRepository
AnalyticsEventRepository
AlertRepository
ObservabilityExportRepository
EventCatalogRepository
CorrelationContextRepository
```

Reglas:

- interfaces sin Supabase;
- no exponer secretos;
- no permitir metadata no validada;
- exports con autorización;
- alertas solo contrato;
- health checks solo contrato;
- sin IO remoto real.

---

## 18. Mocks deterministas

Crear mocks in-memory:

- store compartido;
- reloj inyectable;
- IDs predecibles;
- correlation IDs predecibles;
- filtros;
- paginación;
- sensibilidad;
- autorización;
- sampling;
- retención;
- deduplicación por event ID;
- fingerprints de errores;
- health states;
- alert incidents simulados;
- exportaciones simuladas sin archivos reales.

DataProvider:

- `useSupabase=false`: mocks M07 no nulos;
- `useSupabase=true`: mantener M07 mocks o implementación client-denied explícita hasta Etapa 3;
- no crear repositorios Supabase;
- no romper M00–M06.

---

## 19. AppLogger

Integrar con cuidado:

- no reemplazar el logger;
- agregar sanitización de `Throwable`;
- permitir correlation ID opcional;
- evitar cambios masivos de call sites;
- no enviar logs remotos;
- no agregar breadcrumbs;
- no agregar crash handler;
- no cambiar comportamiento de release salvo para evitar fuga;
- tests de regresión.

---

## 20. Errores

Definir códigos seguros:

```text
OBS_EVENT_UNKNOWN
OBS_METADATA_DENIED
OBS_SENSITIVE_DATA_REDACTED
OBS_CORRELATION_INVALID
OBS_PERMISSION_DENIED
OBS_EXPORT_DENIED
OBS_RETENTION_UNDEFINED
OBS_SAMPLING_REJECTED
OBS_REPOSITORY_UNAVAILABLE
OBS_UNKNOWN
```

No exponer:

- SQL;
- stack;
- token;
- secreto;
- signed URL;
- raw exception;
- PII;
- contenido.

---

## 21. Pruebas obligatorias

Conservar las 489 pruebas existentes.

Agregar pruebas para:

### Catálogo

- event keys únicos;
- convención `m0X.dominio.accion`;
- evento desconocido;
- categoría/severidad/sensibilidad;
- metadata requerida;
- metadata extra rechazada;
- catálogo M00–M07 disponible.

### Sanitización

- JWT;
- Bearer;
- token;
- service role;
- signed URL;
- email;
- phone;
- coordinates;
- SQL;
- stack;
- local paths;
- base64;
- chat;
- INTERNAL;
- truncado;
- Throwable con causa profunda;
- causa cíclica;
- fingerprint estable;
- no raw stack.

### Correlation

- generación;
- child context;
- invalid/blank;
- longitud;
- caracteres;
- logout/cambio de cuenta;
- generador determinista;
- no PII.

### Retención/sampling

- DEBUG no remoto;
- SECURITY crítica;
- UNKNOWN;
- policy legal pendiente;
- sampling deterministic;
- denegaciones no desaparecen completamente.

### Autorización

- no autenticado;
- permiso desconocido;
- sensibilidad;
- organización incorrecta;
- AccountType/modules sin autoridad;
- export sin permiso;
- deny-by-default.

### Repos/mocks

- append-only conceptual;
- filtros;
- paginación;
- dedup;
- health;
- alerts;
- export simulado;
- DataProvider no nulo;
- `useSupabase=true` sin backend M07 inseguro.

### Regresión

- AppLogger existente;
- AppError;
- M01–M06;
- FCM;
- archivos;
- auth/username;
- WIP aislado.

---

## 22. Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Documentar total final.

---

## 23. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M07-etapa-2-cierre.md
```

Debe incluir:

- rama y base;
- archivos;
- contratos;
- catálogo;
- categorías;
- severidad;
- sensibilidad;
- actors/results;
- metadata;
- correlation;
- sanitización;
- Throwable;
- retención;
- sampling;
- autorización;
- repositorios;
- mocks;
- DataProvider;
- AppLogger;
- errores;
- pruebas;
- build/lint;
- deuda;
- staging;
- checklist;
- parada.

---

## 24. Criterios de aceptación

- [ ] Base y rama correctas.
- [ ] Sin SQL/migraciones.
- [ ] Sin proveedores externos.
- [ ] Sin dashboards/alertas reales.
- [ ] Sin pantallas.
- [ ] Contratos completos.
- [ ] Catálogo M00–M07.
- [ ] Event keys únicos.
- [ ] Metadata deny-by-default.
- [ ] Correlation IDs seguros.
- [ ] Throwable sanitizado.
- [ ] Retención/sampling.
- [ ] Autorización deny-by-default.
- [ ] Interfaces sin Supabase.
- [ ] Mocks deterministas.
- [ ] DataProvider no nulo.
- [ ] AppLogger compatible.
- [ ] Tests verdes.
- [ ] Build/lint verdes.
- [ ] Auth/username intactos.
- [ ] Staging 014–028 pendiente o validado con evidencia.
- [ ] Sin Etapa 3.
- [ ] Sin M08.
- [ ] Sin merge a main.

---

## 25. Parada

No iniciar M07 Etapa 3.

No iniciar M08.

No crear migraciones, tablas, dashboards, alertas reales ni proveedores.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M07-etapa-2-cierre.md
```

No hacer commit hasta revisión.
