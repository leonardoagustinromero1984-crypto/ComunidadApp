# LEOVER — M07 Etapa 4: Observabilidad Operativa, Health Checks, Métricas y UI Interna

**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 4 — Observabilidad operativa interna  
**Estado de entrada:** Etapa 3 aprobada y consolidada  
**Commit base:** `7fbaf357b1632e219a565d7965f23c65eb8b2d93`  
**Rama base:** `m07/etapa-3-persistencia-auditoria-critica-writers`  
**Calidad de entrada:** 515 tests, 0 failures, 0 errors, 0 skipped; `assembleDebug` y `lintDebug` en SUCCESS  
**Staging heredado:** migraciones `014`–`029` pendientes de validación remota  
**Objetivo:** agregar métricas operativas agregadas, health checks, reglas e incidentes internos, exportaciones controladas y una UI administrativa segura, sin proveedores externos ni analítica comercial.  
**Fuera de alcance:** marketing, tracking invasivo, contenido de chat, analítica de producto completa, Crashlytics, Firebase Analytics, Sentry, OpenTelemetry, producción, M08 y cambios de auth/username.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M07-auditoria-inicial.md`
2. `/docs/02-arquitectura/M07-etapa-2-cierre.md`
3. `/docs/02-arquitectura/M07-etapa-3-cierre.md`
4. `/docs/04-calidad/M07-pruebas-persistencia-auditoria-seguridad-errores.md`
5. `/docs/03-modulos/M07-Auditoria-Analitica-y-Observabilidad.md`
6. `/docs/03-modulos/M07-Etapa-4-Observabilidad-Operativa-Health-Checks-Metricas-y-UI-Interna.md`
7. `/docs/02-arquitectura/M06-cierre-final.md`
8. ADR-0001 a ADR-0005.
9. Este documento.

---

## 2. Protección Git

1. Confirmar commit base:

```text
7fbaf357b1632e219a565d7965f23c65eb8b2d93
```

2. Confirmar working tree limpio.
3. Crear la rama:

```text
m07/etapa-4-observabilidad-operativa-health-metricas-ui
```

4. No incorporar GPS, mapas o pagos.
5. No hacer merge a `main`.
6. No iniciar M07 Etapa 5.
7. No iniciar M08.
8. No modificar username, `AuthRepository`, `domain/auth` ni `UsernameValidators`.
9. No aplicar producción.
10. No afirmar staging sin evidencia.

---

## 3. Migración

Crear una única migración nueva:

```text
supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql
```

No editar migraciones `001`–`029`.

La migración debe ser:

- transaccional;
- compatible con `029`;
- deny-by-default;
- con grants mínimos;
- con `SECURITY DEFINER` solo cuando sea necesario;
- con `SET search_path = public`;
- sin secretos;
- sin service role en Android;
- sin datos de marketing;
- sin contenido de usuario;
- sin jobs externos obligatorios.

---

## 4. Persistencia operativa

Crear:

```text
performance_metrics
health_checks
alert_rules
alert_incidents
observability_dashboard_preferences
observability_saved_filters
```

No crear todavía:

```text
analytics_events
analytics_sessions
product_funnels
marketing_segments
user_behavior_profiles
```

---

## 5. `performance_metrics`

Campos mínimos:

```text
id uuid
metric_key text
module text
metric_type text
value_numeric numeric
unit text
dimensions jsonb
window_start timestamptz
window_end timestamptz
sample_count bigint
correlation_id text?
source text
recorded_at timestamptz
retention_policy_key text
created_at timestamptz
```

Tipos:

```text
COUNTER
GAUGE
DURATION
RATE
SIZE
QUEUE_DEPTH
SUCCESS_RATIO
FAILURE_RATIO
```

Reglas:

- métricas agregadas;
- no user ID;
- no email;
- no IP;
- no device fingerprint individual;
- no resource ID personal salvo agregación aprobada;
- dimensions allowlisted;
- mínimo k-anonymity conceptual para datos de producto futuros;
- Android no escribe métricas arbitrarias;
- writers server-side;
- no usar como event log;
- ventanas válidas;
- unit allowlisted;
- deduplicación por metric/window/dimensions/source.

---

## 6. Métricas operativas iniciales

Implementar al menos:

### M00 / CI

```text
m00.ci.build_duration_ms
m00.ci.test_duration_ms
m00.ci.lint_duration_ms
m00.ci.test_count
m00.ci.failure_count
```

### M01 / M02

```text
m01.auth.login_failure_rate
m01.account.deletion_failure_count
m02.authorization.denied_count
```

Sin guardar identidad.

### M04

```text
m04.moderation.open_cases
m04.moderation.unassigned_cases
m04.support.open_tickets
m04.support.first_response_age_minutes
m04.verification.pending_reviews
```

### M05

```text
m05.upload.pending_count
m05.upload.failure_rate
m05.storage.error_count
m05.retention.overdue_count
```

### M06

```text
m06.outbox.queue_depth
m06.outbox.oldest_pending_age_seconds
m06.delivery.success_rate
m06.delivery.retryable_failure_count
m06.dead_letter.count
m06.installation.revoked_count
```

### M07

```text
m07.audit.writer_failure_count
m07.security.denial_rate
m07.error.unique_fingerprint_count
m07.health.unhealthy_count
m07.incident.open_count
```

No incluir métricas de marketing o comportamiento personal.

---

## 7. `health_checks`

Campos mínimos:

```text
id uuid
check_key text
module text
component text
status text
severity text
latency_ms bigint?
details jsonb
correlation_id text?
checked_at timestamptz
expires_at timestamptz?
source text
created_at timestamptz
```

Estados:

```text
HEALTHY
DEGRADED
UNHEALTHY
UNKNOWN
SKIPPED
```

Checks iniciales:

```text
database.rpc_ping
database.catalog_consistency
database.migration_visibility
m05.storage.readiness
m05.upload_pipeline
m06.outbox_backlog
m06.push_delivery_pipeline
m06.dead_letter_growth
m07.audit_writer
m07.security_writer
m07.error_writer
edge.push.readiness
edge.delete_account.readiness
ci.latest_build
```

Reglas:

- checks no deben mutar dominio;
- no usar secretos en details;
- no exponer URLs privadas;
- status UNKNOWN ante falta de evidencia;
- check manual/RPC permitido;
- no crear cron obligatorio;
- TTL/expiración;
- último estado no sustituye historial;
- cliente común no ejecuta checks privilegiados.

---

## 8. `alert_rules`

Campos:

```text
id
rule_key
name
metric_key?
health_check_key?
condition_type
threshold
window_seconds
severity
enabled
cooldown_seconds
organization_id?
created_by
updated_by
created_at
updated_at
```

Condiciones:

```text
GREATER_THAN
GREATER_OR_EQUAL
LESS_THAN
LESS_OR_EQUAL
EQUALS
NOT_EQUALS
STATUS_IS
RATE_ABOVE
NO_DATA
```

Reglas:

- reglas internas;
- sin envío externo;
- no permiten SQL libre;
- no permiten expresiones arbitrarias;
- rule keys únicos;
- thresholds validados;
- organización solo para métricas propias permitidas;
- platform rules solo permisos globales;
- cambios auditados en M07;
- no editar reglas críticas sin `OBSERVABILITY_MANAGE`.

---

## 9. `alert_incidents`

Campos:

```text
id
rule_id
incident_key
status
severity
title_code
summary
first_detected_at
last_detected_at
occurrence_count
acknowledged_by?
acknowledged_at?
resolved_by?
resolved_at?
resolution_code?
correlation_id
organization_id?
created_at
updated_at
```

Estados:

```text
OPEN
ACKNOWLEDGED
RESOLVED
SUPPRESSED
```

Reglas:

- incidentes no contienen PII;
- summary sanitizado y tipado;
- dedup por rule/window/scope;
- acknowledge/resolve requiere permisos;
- toda transición genera audit event;
- no borrar incidentes desde cliente;
- no enviar notificación externa;
- puede emitir una notificación interna M06 para staff autorizado solo si reutiliza el pipeline server-side existente, sin nuevo canal;
- si esa integración no puede validarse, dejarla PENDIENTE y no simularla.

---

## 10. Evaluación de reglas

Crear funciones equivalentes:

```text
m07_record_metric(...)
m07_record_health_check(...)
m07_evaluate_alert_rule(...)
m07_evaluate_enabled_alert_rules(...)
m07_acknowledge_incident(...)
m07_resolve_incident(...)
m07_list_metrics(...)
m07_list_health_checks(...)
m07_list_alert_rules(...)
m07_list_incidents(...)
m07_get_operational_summary(...)
m07_save_dashboard_preferences(...)
m07_save_filter(...)
```

Reglas:

- writers internos server-only;
- evaluación determinista;
- no SQL dinámico;
- no expresiones libres;
- correlation ID;
- metadata/dimensions allowlisted;
- incidentes idempotentes;
- cooldown;
- no crear loop alert→event→alert;
- errores sanitizados;
- evaluación manual o invocada por backend/CI;
- sin cron obligatorio en esta etapa.

---

## 11. RLS y grants

### Métricas y health

- sin INSERT/UPDATE/DELETE cliente;
- lectura global con `OBSERVABILITY_VIEW`;
- organización solo datos agregados propios permitidos;
- detalles sensibles requieren permiso elevado.

### Reglas

- lectura con `OBSERVABILITY_VIEW`;
- creación/edición con `OBSERVABILITY_MANAGE`;
- reglas globales solo plataforma;
- organización solo su scope;
- sin SQL libre.

### Incidentes

- lectura con `OBSERVABILITY_VIEW`;
- acknowledge/resolve con `OBSERVABILITY_MANAGE`;
- seguridad sensible requiere permiso adicional;
- append histórico de transiciones;
- sin DELETE cliente.

### Preferencias/filtros

- usuario staff solo sus preferencias;
- filtros validados;
- no guardar PII ni queries SQL;
- no compartir filtros privados por defecto.

---

## 12. UI administrativa interna

Crear una sección interna profesional accesible solo con permisos M02/M07.

Rutas sugeridas:

```text
observability_overview
observability_metrics
observability_health
observability_incidents
observability_audit
observability_errors
observability_exports
```

No exponer estas rutas a usuarios comunes.

### Overview

Mostrar:

- estado general;
- checks HEALTHY/DEGRADED/UNHEALTHY/UNKNOWN;
- incidentes abiertos;
- dead letters;
- backlog M06;
- errores por fingerprint;
- fallos de autorización agregados;
- uploads fallidos;
- tickets/casos pendientes;
- última actualización;
- estado staging como PENDIENTE si no hay evidencia.

### Métricas

- filtros por módulo;
- rango temporal;
- métrica;
- unidad;
- scope;
- tabla y tarjetas;
- gráficos simples permitidos;
- sin tracking de usuario;
- sin PII;
- estados loading/empty/error/retry.

### Health

- componente;
- status;
- latencia;
- checked_at;
- expiración;
- detalle sanitizado;
- ejecutar check manual solo con permiso;
- mostrar UNKNOWN cuando no hay evidencia.

### Incidentes

- OPEN/ACKNOWLEDGED/RESOLVED;
- severity;
- occurrence count;
- acknowledge;
- resolve;
- historial;
- filtros;
- sin texto libre sensible.

### Auditoría/errores/exportaciones

Reutilizar repositorios Etapa 3:

- listados paginados;
- filtros seguros;
- sensibilidad;
- correlation ID;
- código de referencia;
- request de exportación;
- sin descarga real todavía si no existe mecanismo seguro;
- sin signed URL.

---

## 13. Android

Crear o adaptar componentes equivalentes:

```text
OperationalObservabilityRepository
SupabaseOperationalObservabilityRepository
MockOperationalObservabilityRepository
ObservabilityOverviewViewModel
ObservabilityMetricsViewModel
ObservabilityHealthViewModel
ObservabilityIncidentsViewModel
ObservabilityOverviewScreen
ObservabilityMetricsScreen
ObservabilityHealthScreen
ObservabilityIncidentsScreen
ObservabilityFilterState
ObservabilityUiErrorMapper
```

DataProvider:

- `useSupabase=false`: mock completo;
- `useSupabase=true`: repositorio RPC-only;
- sin acceso directo a tablas;
- sin service role;
- no romper repositorios de Etapa 3.

Navegación:

- permiso revalidado;
- deep link interno no concede acceso;
- AccountType/modules sin autoridad;
- organización correcta;
- logout/cambio de cuenta limpia filtros y estado.

---

## 14. CI/CD

Ampliar el workflow existente sin proveedores externos.

Agregar, si es compatible:

- cobertura JVM con JaCoCo o herramienta Gradle ya disponible;
- umbral inicial informativo, no bloquear hasta documentar baseline;
- validación del catálogo Kotlin↔SQL bidireccional;
- validación de migraciones duplicadas/numeración;
- búsqueda de secretos con herramienta local/open-source en CI si no requiere servicio externo;
- lint SQL básico;
- artefactos de cobertura;
- resumen de calidad.

Reglas:

- no subir secrets;
- no usar credenciales staging;
- no ejecutar producción;
- no romper CI actual;
- si una herramienta requiere proveedor/servicio externo, no agregarla;
- documentar qué se implementó y qué quedó pendiente.

---

## 15. Integración Edge Functions

Instrumentar de forma agregada:

### `push`

- duración;
- cantidad procesada;
- entregadas;
- retryables;
- permanentes;
- inválidos;
- sin provider IDs en cliente;
- health readiness.

### `delete-account`

- duración;
- éxito/fallo;
- etapa fallida sanitizada;
- health readiness;
- correlation existente.

No guardar JWT, headers, payload de usuario ni secretos.

---

## 16. Privacidad

Confirmar:

- métricas agregadas;
- sin identidad individual;
- sin contenido;
- sin coordenadas;
- sin IP;
- sin device fingerprint individual;
- sin chat;
- sin INTERNAL;
- sin documentos;
- sin signed URLs;
- sin payloads crudos;
- filtros sin PII;
- export requests sin archivo todavía;
- retención M07 aplicada.

---

## 17. Errores seguros

Usar códigos equivalentes:

```text
OBS_METRIC_UNKNOWN
OBS_METRIC_DIMENSION_DENIED
OBS_HEALTH_CHECK_UNKNOWN
OBS_HEALTH_EXECUTION_DENIED
OBS_ALERT_RULE_INVALID
OBS_ALERT_EVALUATION_FAILED
OBS_INCIDENT_NOT_FOUND
OBS_INCIDENT_TRANSITION_DENIED
OBS_FILTER_INVALID
OBS_DASHBOARD_PERMISSION_DENIED
OBS_DATA_UNAVAILABLE
OBS_UNKNOWN
```

No exponer SQL, stack, token, signed URL, secretos, PII ni nombres internos innecesarios.

---

## 18. Pruebas obligatorias

Conservar las 515 pruebas existentes.

Agregar pruebas para:

### Migración

- `030` única nueva;
- `001`–`029` intactas;
- tablas esperadas;
- tablas de analítica ausentes;
- RLS;
- grants;
- writers server-only;
- no PUBLIC EXECUTE.

### Métricas

- key allowlisted;
- dimensions allowlisted;
- sin user/email/IP;
- ventana válida;
- dedup;
- units;
- agregación;
- org scope;
- Android sin write arbitrario.

### Health

- estados;
- TTL;
- UNKNOWN sin evidencia;
- detalles sanitizados;
- check privilegiado;
- no mutación de dominio.

### Alertas/incidentes

- threshold;
- no SQL libre;
- evaluación;
- cooldown;
- dedup;
- OPEN→ACKNOWLEDGED→RESOLVED;
- transición inválida;
- permisos;
- audit de transición;
- sin PII;
- sin loop.

### UI/repos

- overview;
- filtros;
- paginación;
- loading/empty/error;
- permiso denegado;
- organización incorrecta;
- deep link interno;
- logout limpia estado;
- mocks;
- Supabase RPC-only.

### CI

- catálogo Kotlin↔SQL exacto;
- migraciones numeradas;
- secret patterns;
- artefactos de cobertura cuando aplique.

### Regresión

- 515 pruebas previas;
- M01–M07 Etapa 3;
- M06;
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

Si se agrega cobertura:

```powershell
.\gradlew.bat :app:jacocoTestReport
```

No inventar resultados.

---

## 20. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M07-etapa-4-cierre.md
/docs/04-calidad/M07-pruebas-observabilidad-operativa-health-metricas-ui.md
```

### Cierre

Debe incluir:

- rama/base;
- migración 030;
- tablas;
- métricas;
- health checks;
- reglas;
- incidentes;
- writers;
- RLS/grants;
- UI;
- repositorios;
- CI;
- Edge;
- privacidad;
- archivos;
- pruebas;
- build/lint/cobertura;
- riesgos;
- deuda;
- staging;
- checklist;
- parada.

### Pruebas

Debe incluir:

- migración;
- métricas;
- health;
- alertas/incidentes;
- permisos;
- UI;
- repositorios;
- CI;
- privacidad;
- regresión;
- comandos;
- resultados;
- limitaciones;
- staging.

---

## 21. Criterios de aceptación

- [ ] Base y rama correctas.
- [ ] Solo migración `030` nueva.
- [ ] `001`–`029` sin ediciones.
- [ ] Métricas agregadas, sin PII.
- [ ] Health checks tipados.
- [ ] Reglas sin SQL libre.
- [ ] Incidentes idempotentes.
- [ ] Sin alertas externas.
- [ ] RLS deny-by-default.
- [ ] Writers server-side.
- [ ] UI solo staff autorizado.
- [ ] Deep links no conceden acceso.
- [ ] Repositorios RPC-only.
- [ ] Mocks completos.
- [ ] CI mejorado sin proveedores externos.
- [ ] Edge instrumentado sin secretos.
- [ ] Sin analítica comercial.
- [ ] Sin Crashlytics/Analytics/Sentry/OTel.
- [ ] Tests verdes.
- [ ] Build/lint verdes.
- [ ] Auth/username intactos.
- [ ] Staging `014`–`030` pendiente o validado con evidencia.
- [ ] Sin Etapa 5.
- [ ] Sin M08.
- [ ] Sin merge a main.

---

## 22. Parada

No iniciar M07 Etapa 5.

No iniciar M08.

No agregar analítica de producto, marketing, tracking individual ni proveedores externos.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M07-etapa-4-cierre.md
/docs/04-calidad/M07-pruebas-observabilidad-operativa-health-metricas-ui.md
```

No hacer commit hasta revisión.
