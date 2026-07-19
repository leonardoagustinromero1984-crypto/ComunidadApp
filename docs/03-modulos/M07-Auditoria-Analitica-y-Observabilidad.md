# LEOVER — M07 Auditoría, Analítica y Observabilidad

**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Versión:** 1.0  
**Estado:** autorizado únicamente para Etapa 1 — Auditoría y diseño  
**Dependencias:** M00–M06 cerrados a nivel código y calidad local  
**Backend oficial:** Supabase  
**Commit base:** `b2066147e85366d543684ec8f41c68c0b82fe7e9`  
**Staging heredado:** migraciones `014`–`028` pendientes de validación remota  
**Regla principal:** inventariar primero logs, auditorías, métricas, eventos, errores, salud, trazas y datos sensibles existentes. No instrumentar producción ni agregar proveedores antes de cerrar la auditoría.

---

## 1. Objetivo

M07 será la capa transversal de visibilidad operativa de LeoVer.

Debe permitir, por etapas:

- auditoría de acciones críticas;
- registro estructurado de eventos;
- métricas técnicas y de producto;
- trazabilidad de errores;
- salud de servicios;
- monitoreo de jobs, Edge Functions, RPC y entregas;
- detección de abuso y anomalías;
- tableros operativos;
- alertas;
- retención y privacidad;
- correlación entre módulos;
- soporte a investigaciones;
- evidencia para seguridad y cumplimiento;
- exportaciones controladas;
- diagnóstico sin exponer PII o secretos.

M07 no debe convertirse en una copia de logs sin gobierno ni en un sistema de vigilancia invasivo.

---

## 2. Dependencias y autoridad

| Área | Módulo propietario |
|---|---|
| Identidad y sesión | M01 |
| Usuarios, permisos y estados | M02 |
| Organizaciones | M03 |
| Moderación, soporte y verificación | M04 |
| Archivos y adjuntos | M05 |
| Notificaciones | M06 |
| Auditoría/analítica/observabilidad | M07 |

Reglas:

- actor derivado de `auth.uid()` cuando exista sesión;
- procesos server-side deben declarar actor técnico;
- M07 observa y audita, no concede permisos;
- AccountType y `active_modules` no otorgan autoridad;
- acceso a datos de observabilidad requiere permisos M02/M04 específicos;
- roles M03 quedan limitados a su organización;
- una auditoría no debe exponer información fuera del alcance del lector.

---

## 3. Áreas funcionales

### 3.1 Auditoría

Registrar acciones críticas:

- login y seguridad;
- cambios de perfil y privacidad;
- roles y permisos;
- membresías y ownership;
- moderación y apelaciones;
- soporte;
- archivos sensibles;
- notificaciones;
- cambios administrativos;
- operaciones de eliminación;
- accesos sensibles;
- exportaciones;
- fallos de autorización.

Debe existir:

- actor;
- acción;
- recurso;
- organización;
- resultado;
- motivo sanitizado;
- fecha;
- correlation ID;
- fuente;
- IP/hash cuando sea legal y necesario;
- metadata allowlisted.

### 3.2 Observabilidad técnica

- errores Android;
- fallos RPC;
- fallos RLS;
- Edge Functions;
- latencia;
- disponibilidad;
- reintentos;
- dead-letter;
- tareas bloqueadas;
- migraciones;
- health checks;
- sincronización;
- conectividad;
- consumo de recursos permitido.

### 3.3 Analítica de producto

Futuro:

- adquisición;
- activación;
- retención;
- adopciones;
- publicaciones;
- casos perdidos/encontrados;
- organizaciones activas;
- funnels;
- conversión;
- satisfacción;
- uso por módulo.

Reglas:

- mínimo dato necesario;
- agregación;
- consentimiento cuando corresponda;
- sin fingerprinting invasivo;
- marketing y analítica no se confunden;
- no usar datos sensibles para segmentación.

---

## 4. Tipos de eventos

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

Cada evento debe declarar:

- event key;
- categoría;
- severidad;
- origen;
- actor;
- recurso;
- organización;
- resultado;
- sensibilidad;
- retención;
- correlation ID;
- metadata permitida.

---

## 5. Severidad

```text
DEBUG
INFO
NOTICE
WARNING
ERROR
CRITICAL
```

Reglas:

- DEBUG no se persiste remotamente en producción salvo diagnóstico autorizado;
- CRITICAL requiere política de alertas;
- severidad no concede acceso;
- no usar ERROR para eventos normales;
- fallos de autorización se registran sin revelar detalles sensibles.

---

## 6. Sensibilidad

```text
PUBLIC_AGGREGATE
INTERNAL
CONFIDENTIAL
RESTRICTED
SECURITY_SENSITIVE
```

Reglas:

- tokens, secretos, passwords y signed URLs nunca se almacenan;
- payloads de soporte, moderación y archivos sensibles deben minimizarse;
- PII completa no va a logs;
- contenido de chat no se registra;
- cuerpos de notificaciones sensibles no se registran;
- los eventos RESTRICTED requieren permiso explícito;
- exportaciones deben auditarse.

---

## 7. Modelo conceptual

No implementar antes de la auditoría.

Entidades candidatas:

```text
audit_events
security_events
application_errors
performance_metrics
health_checks
analytics_events
analytics_sessions
alert_rules
alert_incidents
observability_exports
data_retention_policies
correlation_contexts
```

### `audit_events`

Campos conceptuales:

```text
id
event_key
category
severity
sensitivity
actor_user_id?
actor_type
actor_technical_id?
organization_id?
resource_type?
resource_id?
action
result
reason_code?
correlation_id
source
metadata
occurred_at
retention_until?
created_at
```

### `application_errors`

```text
id
error_code
module
layer
severity
correlation_id
user_id?
organization_id?
sanitized_message
fingerprint
app_version?
device_context?
occurred_at
resolved_at?
```

### `performance_metrics`

```text
metric_key
module
value
unit
dimensions
window_start
window_end
recorded_at
```

No persistir stack traces crudos, tokens, SQL, URLs firmadas ni payload sensible.

---

## 8. Correlation ID

Toda operación crítica debe poder correlacionarse.

Formato conceptual:

```text
correlationId
requestId?
sessionId?
jobId?
eventId?
```

Reglas:

- generado al inicio de la operación;
- propagado Android → RPC → Edge Function → outbox/job cuando sea seguro;
- no contiene PII;
- visible en errores de soporte como referencia corta;
- no reutilizar entre sesiones independientes;
- logs sin correlation ID en flujos críticos deben detectarse.

---

## 9. Fuentes a auditar

### Android

- `AppLogger`;
- logs directos;
- `println`;
- Timber/Logcat;
- manejo de excepciones;
- errores UI;
- sanitización;
- crash handling;
- performance;
- navegación;
- sesión;
- logout;
- red;
- Supabase client;
- FCM;
- background;
- mocks;
- tests.

### Supabase

- migraciones `001`–`028`;
- tablas de auditoría;
- access logs;
- RLS;
- RPC;
- triggers;
- Edge Functions;
- outbox;
- dead-letter;
- cron/jobs;
- Realtime;
- auth logs;
- database logs;
- storage access;
- funciones SECURITY DEFINER;
- grants.

### Infraestructura y repositorio

- CI;
- GitHub Actions;
- lint;
- tests;
- build artifacts;
- secrets;
- environment configuration;
- versioning;
- crash/analytics SDK;
- dashboards;
- alerting;
- backups;
- migration evidence.

No agregar proveedor nuevo en Etapa 1.

---

## 10. Eventos existentes M00–M06

Auditar y clasificar:

```text
IMPLEMENTADO
PARCIAL
DUPLICADO
INCONSISTENTE
RIESGO
AUSENTE
FUTURO
```

Mínimos:

### M00

- errores;
- configuración;
- feature flags;
- builds;
- CI;
- sanitización.

### M01

- login;
- logout;
- recuperación;
- verificación;
- cambios de seguridad;
- intentos denegados;
- eliminación de cuenta.

### M02

- perfiles;
- privacidad;
- roles;
- permisos;
- estados;
- administración.

### M03

- organizaciones;
- invitaciones;
- membresías;
- roles internos;
- ownership;
- sucursales.

### M04

- reportes;
- moderación;
- medidas;
- apelaciones;
- verificación;
- soporte;
- accesos sensibles.

### M05

- upload;
- download;
- signed URLs;
- retención;
- eliminación;
- acceso sensible;
- storage failures.

### M06

- eventos;
- recipients;
- deliveries;
- outbox;
- dead-letter;
- instalaciones;
- preferencias;
- push;
- deep links;
- permission deny.

---

## 11. Privacidad

Confirmar o diseñar:

- minimización;
- allowlist de metadata;
- redacción;
- hashing cuando corresponda;
- retención;
- eliminación;
- acceso;
- exportación;
- anonimización;
- consentimiento;
- residencia de datos;
- no registrar contenido de chat;
- no registrar passwords;
- no registrar tokens;
- no registrar documentos;
- no registrar signed URLs;
- no registrar bodies INTERNAL;
- no registrar coordenadas precisas salvo evento autorizado y agregado.

---

## 12. Retención

Políticas candidatas:

| Tipo | Retención inicial a validar |
|---|---|
| Seguridad crítica | 12–24 meses |
| Auditoría administrativa | 12 meses |
| Accesos sensibles | 12 meses |
| Errores técnicos | 30–90 días |
| Métricas agregadas | 12–24 meses |
| Debug | no remoto o máximo 7 días |
| Exportaciones | según necesidad legal |
| Dead-letter | hasta resolución + ventana |

No fijar plazos legales definitivos sin revisión correspondiente.

---

## 13. Acceso

Roles conceptuales:

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

Reglas:

- permisos M02, no AccountType;
- org roles solo ven datos agregados y propios;
- datos globales solo plataforma;
- exportaciones requieren permiso y auditoría;
- soporte no ve seguridad sensible por defecto;
- moderación no ve analytics global por defecto;
- deny-by-default.

---

## 14. Alertas

Futuro:

- error rate;
- auth abuse;
- permission denials anómalos;
- RLS failures;
- Edge Function failures;
- dead-letter creciente;
- push delivery failures;
- storage errors;
- upload failures;
- latencia;
- migración inconsistente;
- backup ausente;
- health check fallido.

Etapa 1 solo diseña criterios. No crear alertas reales.

---

## 15. Dashboards

Futuro:

- salud general;
- seguridad;
- M04 operaciones;
- M05 storage;
- M06 notificaciones;
- producto;
- organizaciones;
- adopciones;
- soporte;
- releases.

Etapa 1 no crea dashboards.

---

## 16. Android

Auditar:

- logger central;
- redacción;
- correlation IDs;
- captura de errores;
- breadcrumbs;
- crashes;
- ANR;
- performance;
- app version;
- device info;
- offline;
- retry;
- logout;
- cambio de cuenta;
- FCM;
- deep links;
- WorkManager;
- logs de desarrollo;
- tests.

No agregar Firebase Crashlytics/Analytics, Sentry ni proveedor en Etapa 1.

---

## 17. Supabase

Auditar:

- tablas de auditoría existentes;
- duplicaciones entre M02/M03/M04/M05/M06;
- access audit;
- error logs;
- SECURITY DEFINER;
- grants;
- RLS;
- triggers;
- correlation ID;
- Edge Function logs;
- dead letters;
- health checks;
- pg_cron;
- backups;
- Realtime;
- Storage access logs.

No crear migraciones en Etapa 1.

---

## 18. CI/CD

Auditar:

- GitHub Actions;
- secretos;
- branches;
- checks;
- build;
- lint;
- tests;
- artifacts;
- dependency scanning;
- secret scanning;
- code coverage;
- migration lint;
- SQL checks;
- release evidence;
- rollback;
- changelog;
- SBOM, si existe.

---

## 19. Fuera de alcance

- dashboards reales;
- alertas productivas;
- proveedor de analytics;
- Crashlytics/Sentry;
- OpenTelemetry;
- data warehouse;
- BI externo;
- producción;
- migraciones;
- pantallas;
- M08;
- cambios de username/auth;
- GPS, mapas o pagos;
- email;
- marketing.

---

## 20. Etapas de M07

### Etapa 1 — Auditoría y diseño

Inventario completo y arquitectura objetivo.

### Etapa 2 — Contratos, catálogo y sanitización

- eventos;
- severidad;
- sensibilidad;
- correlation ID;
- metadata allowlist;
- retención;
- permisos;
- interfaces;
- mocks;
- pruebas;
- sin SQL.

### Etapa 3 — Persistencia y auditoría crítica

- tablas;
- RLS;
- RPC;
- integración M01–M06;
- errores;
- accesos;
- exportaciones controladas.

### Etapa 4 — Observabilidad operativa y UI

- salud;
- métricas;
- incidentes;
- dashboards internos;
- alertas;
- filtros;
- exportaciones;
- integración CI/Edge.

### Etapa 5 — Calidad y cierre

- seguridad;
- privacidad;
- staging;
- regresión;
- cierre final.

---

## 21. Ejecución autorizada: solo Etapa 1

### Rama

```text
m07/auditoria-analitica-observabilidad-auditoria
```

### Crear únicamente

```text
/docs/02-arquitectura/M07-auditoria-inicial.md
```

### Auditar

#### Git y documentación

- commit base M06;
- working tree;
- ramas;
- cierres M00–M06;
- ADR;
- documentación de calidad y staging.

#### Android

- logger;
- sanitización;
- correlation IDs;
- crash/error handling;
- logs directos;
- FCM;
- deep links;
- session/logout;
- network;
- performance;
- mocks;
- tests;
- dependencias de analytics/crash.

#### Supabase

- migraciones `001`–`028`;
- audit tables;
- access logs;
- error tables;
- dead letters;
- RPC;
- triggers;
- RLS;
- grants;
- Edge Functions;
- Realtime;
- Storage;
- backups/config;
- cron/jobs;
- health.

#### CI/CD

- workflows;
- secrets;
- checks;
- artifacts;
- rollback;
- dependency/security scanning;
- coverage;
- SQL validation;
- migration evidence.

#### Eventos

Inventariar eventos M00–M06 y clasificarlos:

```text
IMPLEMENTADO
PARCIAL
DUPLICADO
INCONSISTENTE
RIESGO
AUSENTE
FUTURO
```

#### Seguridad y privacidad

- PII;
- secrets;
- tokens;
- signed URLs;
- SQL;
- stack traces;
- chat;
- INTERNAL;
- coordenadas;
- retención;
- exportaciones;
- permisos;
- multi-tenant;
- staff access.

### No hacer

- no modificar funcionalidad;
- no crear migraciones;
- no agregar SDK/proveedor;
- no crear dashboards;
- no crear alertas;
- no crear repositorios;
- no crear pantallas;
- no tocar auth/username;
- no iniciar M08;
- no aplicar staging;
- no usar producción;
- no hacer merge a `main`.

### Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

### Contenido obligatorio del informe

- estado Git;
- inventario Android;
- inventario Supabase;
- inventario CI/CD;
- tablas y duplicaciones;
- catálogo de eventos M00–M06;
- logger y sanitización;
- correlation IDs;
- errores;
- auditoría;
- métricas;
- salud;
- dead-letter;
- Edge Functions;
- Realtime;
- backups;
- privacidad;
- retención;
- permisos;
- exportaciones;
- alertas propuestas;
- dashboards propuestos;
- riesgos;
- gaps;
- decisiones;
- arquitectura objetivo;
- archivos futuros;
- plan por etapas;
- calidad;
- staging;
- parada.

---

## 22. Criterios de aceptación de Etapa 1

- [ ] Commit base confirmado.
- [ ] Working tree limpio.
- [ ] Rama correcta.
- [ ] Sin cambios funcionales.
- [ ] Sin migraciones, SDKs, dashboards o alertas.
- [ ] Inventario Android completo.
- [ ] Inventario Supabase completo.
- [ ] Inventario CI/CD completo.
- [ ] Eventos M00–M06 clasificados.
- [ ] Logs y sanitización auditados.
- [ ] Correlation IDs auditados.
- [ ] PII/secrets/tokens auditados.
- [ ] Tablas duplicadas identificadas.
- [ ] Auditoría y observabilidad diferenciadas.
- [ ] Retención diseñada.
- [ ] Permisos diseñados.
- [ ] Exportaciones diseñadas.
- [ ] Alertas propuestas.
- [ ] Dashboards propuestos.
- [ ] Auth/username intactos.
- [ ] Build aprobado.
- [ ] Tests aprobados.
- [ ] Lint aprobado.
- [ ] Staging declarado honestamente.
- [ ] Auditoría creada.
- [ ] Sin M08.
- [ ] Sin merge a main.

---

## 23. Parada

No iniciar Etapa 2.

No iniciar M08.

No crear migraciones, dashboards, alertas ni proveedores.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M07-auditoria-inicial.md
```
