# LEOVER — M07 Etapa 3: Persistencia Segura, Auditoría Crítica y Writers Server-Side

**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 3 — Persistencia mínima y auditoría crítica  
**Estado de entrada:** Etapa 2 aprobada y consolidada  
**Commit base:** `ef8f13157b55fcea74e5ffc67804d77bf2d43257`  
**Rama base:** `m07/etapa-2-contratos-catalogo-correlation-sanitizacion`  
**Calidad de entrada:** 506 tests, 0 failures, 0 errors, 0 skipped; `assembleDebug` y `lintDebug` en SUCCESS  
**Staging heredado:** migraciones `014`–`028` pendientes de validación remota  
**Objetivo:** persistir de forma segura auditoría, eventos de seguridad y errores sanitizados; crear writers server-side; propagar correlation IDs; e instrumentar únicamente gaps críticos de M01–M06.  
**Fuera de alcance:** dashboards, alertas operativas, métricas de performance completas, health checks productivos, analítica de producto, proveedores externos, producción, M08 y cambios de auth/username.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M07-auditoria-inicial.md`
2. `/docs/02-arquitectura/M07-etapa-2-cierre.md`
3. `/docs/03-modulos/M07-Auditoria-Analitica-y-Observabilidad.md`
4. `/docs/03-modulos/M07-Etapa-2-Contratos-Catalogo-Correlation-IDs-Sanitizacion-y-Mocks.md`
5. `/docs/02-arquitectura/M06-cierre-final.md`
6. `/docs/04-calidad/M06-reporte-validacion-staging.md`
7. ADR-0001 a ADR-0005.
8. Este documento.

---

## 2. Protección Git

1. Confirmar commit base:

```text
ef8f13157b55fcea74e5ffc67804d77bf2d43257
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m07/etapa-3-persistencia-auditoria-critica-writers
```

4. No incorporar GPS, mapas o pagos.
5. No hacer merge a `main`.
6. No iniciar M07 Etapa 4.
7. No iniciar M08.
8. No modificar username, `AuthRepository`, `domain/auth` ni `UsernameValidators`.
9. No aplicar producción.
10. No afirmar staging sin evidencia.

---

## 3. Migración

Crear una única migración nueva:

```text
supabase/migrations/029_m07_observability_audit_security_error_foundation.sql
```

No editar migraciones `001`–`028`.

La migración debe ser:

- transaccional;
- compatible con datos existentes;
- append-only donde corresponda;
- deny-by-default;
- con grants mínimos;
- con `SECURITY DEFINER` solo cuando sea necesario;
- con `SET search_path = public`;
- sin secretos;
- sin service role en Android;
- sin tablas de marketing;
- sin dashboards ni jobs.

---

## 4. Persistencia mínima

Crear:

```text
audit_events
security_events
application_errors
observability_export_requests
```

No crear todavía:

```text
performance_metrics
health_checks
analytics_events
alert_rules
alert_incidents
```

Esos elementos quedan para etapas posteriores.

---

## 5. `audit_events`

Campos mínimos:

```text
id uuid
event_key text
category text
severity text
sensitivity text
actor_user_id uuid?
actor_type text
actor_technical_id text?
organization_id uuid?
resource_type text?
resource_id text?
action text
result text
reason_code text?
correlation_id text
request_id text?
source text
metadata jsonb
occurred_at timestamptz
retention_policy_key text
retention_until timestamptz?
created_at timestamptz
```

Reglas:

- event key debe existir en catálogo M07;
- metadata allowlisted;
- no payload arbitrario;
- no UPDATE/DELETE cliente;
- append-only;
- actor derivado server-side;
- organización validada server-side;
- correlation ID obligatorio para writes server-side;
- sensibilidad y retención no elegidas por Android;
- exportaciones quedan auditadas;
- duplicación por correlation/event/resource debe estar controlada.

---

## 6. `security_events`

Campos mínimos:

```text
id uuid
event_key text
severity text
sensitivity text
actor_user_id uuid?
actor_type text
organization_id uuid?
resource_type text?
resource_id text?
result text
reason_code text?
permission_code text?
correlation_id text
source text
metadata jsonb
occurred_at timestamptz
retention_policy_key text
created_at timestamptz
```

Casos iniciales:

- login failure;
- permission denied;
- cross-user deny;
- deep-link deny;
- token revocation;
- export denied;
- repeated authorization failure;
- suspicious input rejection;
- service-only RPC denial.

Reglas:

- no almacenar email, username, password ni token;
- login failure usa actor anónimo o user ID solo si ya está autenticado;
- metadata no debe permitir credential stuffing data cruda;
- no exponer security events al usuario común;
- staff requiere permiso específico;
- organización no concede acceso global.

---

## 7. `application_errors`

Campos mínimos:

```text
id uuid
error_code text
module text
layer text
severity text
sensitivity text
correlation_id text
actor_user_id uuid?
organization_id uuid?
sanitized_message text
fingerprint text
is_retryable boolean
app_version text?
platform text?
environment text
metadata jsonb
occurred_at timestamptz
resolved_at timestamptz?
created_at timestamptz
```

Reglas:

- no guardar stack trace crudo;
- no guardar `Throwable`;
- mensaje sanitizado y truncado;
- fingerprint estable sin PII;
- deduplicación por fingerprint + ventana;
- Android no puede escribir arbitrary remote errors;
- permitir un canal cliente muy limitado solo si usa RPC validada y catálogo allowlisted;
- errores server-side se escriben desde funciones internas;
- resolved_at solo por permiso administrativo futuro;
- no usar esta tabla como log general.

---

## 8. Export requests

Crear `observability_export_requests` para modelar solicitudes, no archivos finales.

Campos:

```text
id
requested_by
scope
organization_id?
sensitivity
filters
status
reason
correlation_id
requested_at
completed_at?
failure_code?
created_at
```

Reglas:

- no generar archivos en esta etapa;
- solicitud requiere permiso;
- scope y filtros allowlisted;
- export sensible requiere permiso elevado;
- export denegado genera security event;
- solicitud creada genera audit event;
- usuario común no accede;
- organización solo puede pedir scope propio;
- no incluir PII completa en filtros;
- sin signed URL.

---

## 9. Catálogo server-side

Crear una representación server-side equivalente al catálogo local.

Opciones permitidas:

- tabla `observability_event_catalog` administrada por migración; o
- función allowlist explícita.

Debe cubrir exactamente los event keys instrumentados en Etapa 3 y permitir validación de:

- categoría;
- severidad;
- sensibilidad;
- actor types;
- metadata permitida;
- metadata requerida;
- retención;
- persistencia remota;
- analytics permitido.

No aceptar keys arbitrarias.

La fuente Kotlin sigue siendo contrato de aplicación; la fuente SQL es enforcement server-side.

Documentar cómo se detectará drift entre Kotlin y SQL en una etapa posterior.

---

## 10. Writers server-side

Crear funciones equivalentes:

```text
m07_validate_event_key(...)
m07_validate_metadata(...)
m07_require_actor(...)
m07_resolve_actor_type(...)
m07_resolve_organization_scope(...)
m07_sanitize_reason_code(...)
m07_write_audit_event(...)
m07_write_security_event(...)
m07_write_application_error(...)
m07_request_export(...)
m07_list_audit_events(...)
m07_list_security_events(...)
m07_list_application_errors(...)
```

Reglas:

- `SECURITY DEFINER` solo donde haga falta;
- `SET search_path = public`;
- `auth.uid()` como actor cliente;
- `service_role` solo para writers internos;
- revocar `PUBLIC`, `anon` y `authenticated` en funciones internas;
- grants explícitos;
- metadata sanitizada;
- correlation ID obligatorio;
- permisos M02/M04 para lecturas;
- roles M03 solo scope propio;
- AccountType y `active_modules` nunca autorizan;
- error de writer no debe filtrar SQL o stack.

---

## 11. RLS

### `audit_events`

- sin INSERT/UPDATE/DELETE cliente;
- lectura global solo con permiso `AUDIT_VIEW`;
- sensibilidad RESTRICTED/SECURITY_SENSITIVE requiere `AUDIT_VIEW_SENSITIVE`;
- organización puede ver solo eventos propios permitidos y no sensibles;
- usuario común no lista auditoría.

### `security_events`

- sin escritura cliente;
- lectura solo con `SECURITY_EVENTS_VIEW`;
- organización no ve eventos de seguridad global;
- usuario común no ve denegaciones internas.

### `application_errors`

- sin escritura arbitraria;
- lectura solo con `OBSERVABILITY_VIEW`;
- contenido sensible requiere permiso elevado;
- usuario puede recibir un código de referencia, no leer la fila.

### `observability_export_requests`

- creación solo mediante RPC;
- lectura propia del solicitante staff autorizado;
- gestión futura con permiso;
- filtros/scope validados.

---

## 12. Correlation IDs end-to-end

Implementar propagación selectiva:

### Android

- generar correlation ID en operaciones críticas elegidas;
- adjuntarlo a RPCs nuevas M07;
- incluirlo en errores seguros;
- limpiar contexto al logout/cambio de cuenta;
- no migrar todos los call sites.

### SQL

- aceptar correlation ID validado;
- generar uno server-side si la operación interna no lo recibe;
- no usar user ID ni timestamp legible;
- persistirlo en eventos y errores.

### Edge Functions

- reutilizar `Idempotency-Key`/correlation existente donde aplique;
- propagar a writers M07;
- no loguear headers completos.

No implementar un sistema distribuido completo en esta etapa.

---

## 13. Instrumentación selectiva obligatoria

Instrumentar solo gaps críticos y de bajo acoplamiento.

### M01

- `m01.auth.login_failure`;
- `m01.auth.logout`;
- `m01.consent.gate_unavailable`;
- `m01.account.deletion_failed`.

No modificar lógica de autenticación. Instrumentar en capas permitidas sin tocar `domain/auth` ni validadores.

### M02

- `m02.permission.denied`;
- `m02.admin.audit_read`;
- consolidar writer futuro sin migrar históricos.

### M04

- acceso sensible permitido/denegado;
- lectura de auditoría;
- INTERNAL projection denied;
- no registrar body INTERNAL.

### M05

- `m05.signed_url.issued`;
- `m05.download.performed`;
- `m05.storage.error`;
- nunca registrar URL firmada ni path sensible completo.

### M06

- `m06.deep_link.permission_denied`;
- `m06.access_audit.decision`;
- `m06.dead_letter.recorded`;
- `m06.edge.push_invoked`;
- no duplicar hechos que ya existen en deliveries/outbox.

Reglas:

- no reescribir procesos completos;
- usar writers server-side;
- best-effort sin abortar mutación de dominio;
- fallos del writer deben quedar como código local sanitizado;
- no crear loops de observabilidad;
- no instrumentar eventos de alto volumen no aprobados.

---

## 14. Android repositories

Crear implementaciones:

```text
SupabaseAuditEventRepository
SupabaseSecurityEventRepository
SupabaseApplicationErrorRepository
SupabaseObservabilityExportRepository
```

Restricciones:

- Android no obtiene capacidad de insertar eventos arbitrarios;
- repositorios solo llaman RPCs allowlisted;
- `SecurityEventRepository` cliente puede reportar solo eventos explícitamente permitidos;
- `ApplicationErrorRepository` cliente reporta solo errores sanitizados y allowlisted;
- listados requieren permisos;
- `useSupabase=true` usa repos reales para operaciones permitidas;
- writers internos continúan server-side;
- no exponer service role;
- no exponer tablas directas.

Mantener mocks para `useSupabase=false`.

---

## 15. `AppLogger`

Integración mínima:

- usar `SanitizedThrowable`;
- agregar correlation ID cuando exista;
- reportar remotamente solo errores allowlisted y no DEBUG;
- no convertir cada log en evento remoto;
- no enviar mensajes de usuario;
- no enviar stack;
- no enviar en mocks salvo configuración explícita;
- evitar recursión si falla el repositorio M07;
- fallback local sanitizado.

No agregar Crashlytics, Sentry, Analytics u OpenTelemetry.

---

## 16. Compatibilidad con silos existentes

No migrar ni borrar:

- `user_status_history`;
- `role_assignment_history`;
- `organization_status_history`;
- `organization_audit_log`;
- `administrative_audit_log`;
- `file_access_audit`;
- `notification_access_audit`;
- `notification_dead_letters`.

Estrategia Etapa 3:

- nuevos eventos críticos se escriben en M07;
- mantener writers existentes;
- evitar doble escritura salvo evento aprobado;
- documentar silos y futura proyección/vista;
- no copiar históricos.

---

## 17. Errores seguros

Usar códigos:

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
OBS_WRITE_DENIED
OBS_WRITE_FAILED
OBS_READ_DENIED
OBS_UNKNOWN
```

No exponer:

- SQL;
- stack;
- raw exception;
- token;
- signed URL;
- service role;
- metadata sensible;
- IDs internos no necesarios.

---

## 18. Pruebas obligatorias

Conservar las 506 pruebas existentes.

Agregar pruebas SQL/estáticas/Kotlin para:

### Migración y grants

- `029` única nueva;
- `001`–`028` intactas;
- sin PUBLIC EXECUTE en writers internos;
- grants mínimos;
- RLS activas;
- cliente sin INSERT/UPDATE/DELETE;
- service_role writer interno.

### Catálogo

- event keys allowlisted;
- metadata permitida/requerida;
- evento desconocido denegado;
- drift documentado.

### Audit/security/error

- append-only;
- actor derivado;
- org scope;
- sensibilidad;
- correlation obligatorio;
- dedup de errores;
- no stack;
- no token;
- no signed URL;
- export denied genera security event.

### Instrumentación

- login failure sin email/password;
- permission denied;
- sensitive access;
- signed URL issued sin URL;
- download sin path completo;
- deep-link deny;
- access audit;
- dead-letter sin duplicar payload;
- Edge push invoked sanitizado.

### Android

- repositorios no permiten event key arbitraria;
- useSupabase=true sin service role;
- AppLogger no crea loop;
- DEBUG no remoto;
- error allowlisted sí;
- error desconocido local only;
- correlation propagada;
- logout limpia contexto.

### Regresión

- 506 pruebas previas;
- M01–M06;
- FCM;
- archivos;
- auth/username;
- WIP aislado.

---

## 19. Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Documentar total final.

---

## 20. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M07-etapa-3-cierre.md
/docs/04-calidad/M07-pruebas-persistencia-auditoria-seguridad-errores.md
```

### Cierre

Debe incluir:

- rama y base;
- migración 029;
- tablas;
- catálogo SQL;
- writers;
- grants;
- RLS;
- correlation;
- instrumentación M01–M06;
- repositorios;
- AppLogger;
- compatibilidad;
- archivos;
- pruebas;
- build/lint;
- riesgos;
- deuda;
- staging;
- checklist;
- parada.

### Pruebas

Debe incluir:

- matriz de grants/RLS;
- catálogo;
- auditoría;
- seguridad;
- errores;
- instrumentación;
- Android;
- regresión;
- comandos;
- resultados;
- limitaciones;
- staging.

---

## 21. Criterios de aceptación

- [ ] Base y rama correctas.
- [ ] Solo migración `029` nueva.
- [ ] `001`–`028` sin ediciones.
- [ ] Tablas mínimas creadas.
- [ ] Sin métricas/health/analytics persistentes todavía.
- [ ] Writers server-side seguros.
- [ ] Sin PUBLIC EXECUTE.
- [ ] RLS deny-by-default.
- [ ] Correlation IDs persistidos.
- [ ] Metadata allowlisted.
- [ ] Sin stack/tokens/signed URLs.
- [ ] Instrumentación crítica M01–M06.
- [ ] Sin loops de observabilidad.
- [ ] Repositorios Supabase limitados.
- [ ] Mocks conservados.
- [ ] AppLogger compatible.
- [ ] Silos existentes conservados.
- [ ] Tests verdes.
- [ ] Build/lint verdes.
- [ ] Auth/username intactos.
- [ ] Staging `014`–`029` pendiente o validado con evidencia.
- [ ] Sin Etapa 4.
- [ ] Sin M08.
- [ ] Sin merge a main.

---

## 22. Parada

No iniciar M07 Etapa 4.

No iniciar M08.

No crear dashboards, alertas reales, métricas/health productivos ni proveedores.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M07-etapa-3-cierre.md
/docs/04-calidad/M07-pruebas-persistencia-auditoria-seguridad-errores.md
```

No hacer commit hasta revisión.
