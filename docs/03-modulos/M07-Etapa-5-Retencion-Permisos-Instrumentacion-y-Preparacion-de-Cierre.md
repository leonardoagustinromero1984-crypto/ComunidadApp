# LEOVER — M07 Etapa 5: Retención, Permisos, Instrumentación y Preparación de Cierre

**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 5 — Gobierno operativo y preparación de cierre  
**Estado de entrada:** Etapa 4 aprobada y consolidada  
**Commit base:** `65b0a3d914cf13db6d525b5362f6c35869fea32a`  
**Rama base:** `m07/etapa-4-observabilidad-operativa-health-metricas-ui`  
**Calidad de entrada:** 525 tests, 0 failures, 0 errors, 0 skipped; `assembleDebug`, `lintDebug` y `jacocoTestReport` en SUCCESS  
**Staging heredado:** migraciones `014`–`030` pendientes de validación remota  
**Objetivo:** reemplazar permisos proxy, aplicar gobierno de retención, auditar lecturas/exportaciones, completar instrumentación selectiva, endurecer CI y dejar M07 preparado para validación integral y cierre final en Etapa 6.  
**Fuera de alcance:** cierre final, apply remoto, producción, analítica comercial, proveedores externos, M08 y cambios de auth/username.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M07-auditoria-inicial.md`
2. `/docs/02-arquitectura/M07-etapa-2-cierre.md`
3. `/docs/02-arquitectura/M07-etapa-3-cierre.md`
4. `/docs/02-arquitectura/M07-etapa-4-cierre.md`
5. `/docs/04-calidad/M07-pruebas-persistencia-auditoria-seguridad-errores.md`
6. `/docs/04-calidad/M07-pruebas-observabilidad-operativa-health-metricas-ui.md`
7. `/docs/03-modulos/M07-Auditoria-Analitica-y-Observabilidad.md`
8. `/docs/03-modulos/M07-Etapa-5-Retencion-Permisos-Instrumentacion-y-Preparacion-de-Cierre.md`
9. `/docs/02-arquitectura/M06-cierre-final.md`
10. ADR-0001 a ADR-0005.

---

## 2. Protección Git

1. Confirmar commit base:

```text
65b0a3d914cf13db6d525b5362f6c35869fea32a
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m07/etapa-5-retencion-permisos-instrumentacion-cierre
```

4. No mezclar GPS, mapas ni pagos.
5. No hacer merge a `main`.
6. No iniciar M07 Etapa 6.
7. No iniciar M08.
8. No modificar `AuthRepository`, `domain/auth` ni `UsernameValidators`.
9. No corregir el error de username.
10. No aplicar staging ni producción.
11. No afirmar validaciones remotas sin evidencia.

---

## 3. Migración

Crear una única migración nueva:

```text
supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql
```

No editar migraciones `001`–`030`.

La migración debe ser:

- transaccional;
- compatible con `029` y `030`;
- deny-by-default;
- con grants mínimos;
- con `SECURITY DEFINER` solo cuando sea necesario;
- con `SET search_path = public`;
- sin secretos;
- sin SQL dinámico;
- sin cron obligatorio;
- sin marketing;
- sin tracking individual;
- sin servicio externo.

---

## 4. Permisos M07 dedicados

Agregar al catálogo/autorización M02 los permisos equivalentes a:

```text
OBSERVABILITY_VIEW
OBSERVABILITY_MANAGE
AUDIT_VIEW_SENSITIVE
SECURITY_EVENTS_VIEW
EXPORT_AUDIT_DATA
ALERT_MANAGE
RETENTION_MANAGE
HEALTH_CHECK_EXECUTE
```

Reglas:

- no reemplazar permisos existentes usados por otros módulos;
- migrar M07 desde `audit.view` proxy a permisos dedicados;
- `AccountType` y `active_modules` no conceden autoridad;
- roles M03 solo operan dentro de su organización;
- permisos globales solo plataforma;
- cambios de permisos no modifican auth ni username;
- acceso sensible requiere permiso específico;
- UI y RPC deben revalidar permisos reales;
- deep links internos nunca conceden acceso.

Mapa mínimo:

| Acción | Permiso |
|---|---|
| Ver overview/métricas/health | `OBSERVABILITY_VIEW` |
| Administrar reglas/incidentes | `OBSERVABILITY_MANAGE` o `ALERT_MANAGE` |
| Ver auditoría sensible | `AUDIT_VIEW_SENSITIVE` |
| Ver eventos de seguridad | `SECURITY_EVENTS_VIEW` |
| Solicitar exportaciones | `EXPORT_AUDIT_DATA` |
| Ejecutar retención | `RETENTION_MANAGE` |
| Ejecutar checks manuales | `HEALTH_CHECK_EXECUTE` |

---

## 5. Retención

Crear:

```text
observability_retention_policies
observability_retention_runs
observability_retention_run_items
```

No crear almacenamiento externo.

### Políticas iniciales

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

Campos de política:

```text
policy_key
target_table
retention_days?
delete_mode
enabled
legal_hold_supported
description_code
created_at
updated_at
```

Modos:

```text
HARD_DELETE
ANONYMIZE
KEEP_UNTIL_RESOLVED
NO_DELETE
LEGAL_REVIEW
```

Reglas:

- plazos marcados como configuración técnica, no asesoramiento legal;
- `LEGAL_REVIEW_REQUIRED` nunca se purga automáticamente;
- security/audit sensibles no se eliminan sin política explícita;
- legal hold bloquea purga;
- runs son auditados;
- dry-run obligatorio antes de execute;
- ejecución solo server-side y con permiso;
- sin cron obligatorio;
- sin borrar silos históricos de M02–M06;
- no borrar filas requeridas por incidentes abiertos;
- no borrar exports en progreso;
- toda acción devuelve conteos, no datos eliminados.

---

## 6. Funciones de retención

Crear equivalentes:

```text
m07_list_retention_policies(...)
m07_preview_retention_run(...)
m07_execute_retention_run(...)
m07_list_retention_runs(...)
m07_set_legal_hold(...)
m07_release_legal_hold(...)
```

Reglas:

- preview no muta;
- execute requiere preview válido y reciente;
- correlation ID obligatorio;
- idempotencia;
- lotes limitados;
- timeout controlado;
- no SQL libre;
- target allowlisted;
- actor y permiso derivados server-side;
- resultado auditado;
- errores sanitizados;
- sin retornar contenido sensible;
- no `PUBLIC EXECUTE`;
- `service_role` para operación interna;
- authenticated solo mediante RPC autorizada;
- no loops de auditoría.

---

## 7. Auditoría de lecturas sensibles

Completar eventos para:

```text
m02.admin.audit_read
m04.sensitive.access_projection
m05.signed_url.issued
m05.download.performed
m06.access_audit.decision
m07.audit.read
m07.security.read
m07.error.read
m07.export.requested
m07.export.denied
m07.retention.previewed
m07.retention.executed
m07.retention.legal_hold_changed
m07.health.manual_check
m07.incident.acknowledged
m07.incident.resolved
```

Reglas:

- no registrar el contenido leído;
- no registrar signed URL;
- no registrar filtros con PII;
- registrar actor, scope, recurso opaco, resultado y correlation ID;
- lecturas paginadas no generan un evento por fila;
- evitar duplicación y alto volumen;
- denegaciones importantes van a `security_events`;
- lectura sensible permitida va a `audit_events`;
- errores técnicos van a `application_errors`.

---

## 8. Exportaciones controladas

Completar flujo de `observability_export_requests`.

Estados:

```text
REQUESTED
AUTHORIZED
PROCESSING
COMPLETED
FAILED
DENIED
EXPIRED
CANCELLED
```

Etapa 5 puede generar un artefacto local/server-side únicamente si:

- no requiere nuevo proveedor;
- usa formato seguro CSV o JSONL;
- columnas allowlisted;
- sin PII completa;
- sin tokens, SQL, stack, signed URLs o INTERNAL;
- tamaño y filas limitados;
- checksum;
- expiración;
- acceso autenticado y autorizado;
- descarga auditada;
- no expone bucket/path;
- no crea signed URL directa desde Android.

Si no puede implementarse de forma segura:

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
```

Mantener request y autorización funcionando, sin simular archivo.

---

## 9. Integración de incidentes con M06

Intentar completar la deuda de Etapa 4 solo mediante el pipeline server-derived M06 existente.

Evento sugerido:

```text
m07.incident.staff_notification
```

Requisitos:

- recipient derivado server-side;
- solo staff con permisos M07 adecuados;
- organización respetada;
- copy genérico;
- sin métricas, thresholds o datos sensibles completos;
- idempotency key;
- deduplicación;
- preferencia/canal según M06;
- no nuevo canal;
- no Android cross-user;
- fallo de notificación no aborta incidente;
- no loop incidente→notificación→métrica→incidente.

Si no puede validarse localmente:

```text
INTEGRACIÓN M06 PENDIENTE
```

No simular éxito.

---

## 10. Instrumentación selectiva restante

Completar únicamente:

### M00 / CI

- resultado de quality checks;
- catálogo drift;
- migración inválida;
- patrón de secreto detectado;
- cobertura informativa.

### M01 / M02

- logout y permission deny sin duplicados;
- lectura de auditoría administrativa;
- no tocar repositorio de auth.

### M04

- lectura sensible permitida/denegada;
- INTERNAL projection deny;
- sin body INTERNAL.

### M05

- signed URL issued y download realizados sin URL/path completo;
- retención overdue agregada;
- storage errors sanitizados.

### M06

- access audit writers;
- dead-letter growth;
- outbox health;
- incident notification si se valida.

### M07

- writer failures;
- reads;
- exports;
- retention;
- manual health;
- incident transitions.

Evitar instrumentar eventos de alto volumen sin agregación.

---

## 11. UI interna

Agregar o completar:

```text
observability_retention
observability_permissions_info
```

### Retención

Mostrar:

- políticas;
- tabla objetivo;
- modo;
- días;
- legal hold;
- preview;
- conteos estimados;
- ejecutar;
- historial de runs;
- estados;
- correlation ID;
- permisos;
- loading/empty/error/retry.

Reglas:

- preview y execute separados;
- confirmación explícita antes de execute;
- sin mostrar datos a borrar;
- no ejecutar desde deep link;
- no usar AccountType/modules;
- no mostrar la ruta sin `RETENTION_MANAGE`.

### Información de permisos

Mostrar solo para staff:

- permiso requerido por sección;
- estado permitido/denegado;
- sin editor de roles en M07;
- administración real continúa en M02;
- no duplicar pantalla de roles.

Actualizar gates de Etapa 4 para dejar de usar `AUDIT_VIEW` como proxy donde correspondan permisos dedicados.

---

## 12. Repositorios

Crear o ampliar:

```text
RetentionRepository
SupabaseRetentionRepository
MockRetentionRepository
ObservabilityPermissionsResolver
ObservabilityExportRepository
```

Reglas:

- RPC-only;
- sin acceso directo a tablas;
- sin service role;
- mocks deterministas;
- preview y execute diferenciados;
- export status;
- permisos dedicados;
- DataProvider completo;
- `useSupabase=true` seguro;
- `useSupabase=false` funcional.

---

## 13. CI avanzado local

Ampliar `scripts/ci/m07_quality_checks.sh` y workflow.

Requisitos:

- catálogo Kotlin↔SQL exacto;
- catálogo métricas Kotlin↔SQL exacto;
- catálogo health Kotlin↔SQL exacto;
- permisos M07 presentes en Kotlin/SQL;
- migraciones `001`–`031` numeradas sin duplicados;
- detección de edición de migraciones anteriores cuando exista base Git;
- `SECURITY DEFINER` con `search_path`;
- writers internos sin `PUBLIC EXECUTE`;
- RLS en tablas M07;
- patrones de secretos;
- tablas prohibidas de marketing/analytics;
- cobertura JaCoCo informativa;
- resumen Markdown como artefacto;
- no credenciales staging;
- no proveedor externo.

No imponer threshold bloqueante de cobertura sin baseline documentada.

---

## 14. Cobertura y calidad

Registrar baseline JaCoCo:

```text
line coverage
branch coverage
instruction coverage
classes covered
packages con menor cobertura
```

Reglas:

- no inventar porcentajes;
- no excluir paquetes arbitrariamente;
- generated code puede documentarse;
- baseline informativa;
- proponer umbral para Etapa 6, no activarlo sin revisión;
- tests nuevos deben cubrir lógica de permisos, retención y seguridad.

---

## 15. Privacidad y seguridad

Confirmar:

- sin identidad en métricas;
- sin PII en filtros;
- sin contenido;
- sin chat;
- sin INTERNAL;
- sin documentos;
- sin signed URLs;
- sin tokens;
- sin headers;
- sin SQL;
- sin stack;
- sin rutas locales o bucket paths;
- legal hold;
- retención por sensibilidad;
- auditoría de lectura/export/retención;
- deny-by-default;
- permisos M07 reales;
- organización correcta;
- Android sin service role.

---

## 16. Errores seguros

Usar códigos equivalentes:

```text
OBS_RETENTION_POLICY_UNKNOWN
OBS_RETENTION_PREVIEW_REQUIRED
OBS_RETENTION_PREVIEW_EXPIRED
OBS_RETENTION_LEGAL_HOLD
OBS_RETENTION_EXECUTION_DENIED
OBS_RETENTION_RUN_FAILED
OBS_EXPORT_NOT_READY
OBS_EXPORT_EXPIRED
OBS_EXPORT_SCOPE_DENIED
OBS_PERMISSION_MAPPING_INVALID
OBS_M06_NOTIFICATION_PENDING
OBS_CI_QUALITY_CHECK_FAILED
OBS_UNKNOWN
```

No exponer SQL, stack, token, signed URL, PII ni datos eliminados.

---

## 17. Pruebas obligatorias

Conservar las 525 pruebas existentes.

Agregar pruebas para:

### Migración

- `031` única nueva;
- `001`–`030` intactas;
- permisos M07;
- tablas de retención;
- RLS;
- grants;
- sin PUBLIC EXECUTE;
- search_path;
- sin tablas de marketing.

### Permisos

- permisos dedicados;
- sin proxy `AUDIT_VIEW` en rutas nuevas;
- usuario común denegado;
- plataforma;
- organización;
- sensibilidad;
- AccountType/modules sin autoridad;
- deep link no concede acceso.

### Retención

- listado;
- preview;
- execute sin preview;
- preview expirado;
- idempotencia;
- legal hold;
- batches;
- target no allowlisted;
- policy legal;
- incidentes abiertos;
- export en progreso;
- auditoría;
- sin datos sensibles en respuesta.

### Lecturas/export

- lectura auditada una vez por request;
- filtros sanitizados;
- export denied;
- export requested;
- archivo pendiente o seguro;
- descarga auditada;
- sin signed URL/path.

### M06

- recipient server-side;
- idempotencia;
- sin loop;
- pendiente honesto si no se implementa.

### UI/repos

- gates;
- retention screen;
- preview/confirm/execute;
- permisos info;
- logout limpia estado;
- RPC-only;
- mocks;
- estados UI.

### CI

- permisos Kotlin↔SQL;
- catálogos exactos;
- migraciones;
- DEFINER/search_path;
- RLS;
- secret patterns;
- JaCoCo artifact.

### Regresión

- 525 pruebas previas;
- M01–M07 Etapa 4;
- auth/username;
- M06;
- WIP aislado.

---

## 18. Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:jacocoTestReport
```

Ejecutar también el quality script en entorno compatible:

```bash
bash scripts/ci/m07_quality_checks.sh
```

No inventar resultados.

---

## 19. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M07-etapa-5-cierre.md
/docs/04-calidad/M07-pruebas-retencion-permisos-instrumentacion-cierre.md
```

### Cierre

Debe incluir:

- rama/base;
- migración `031`;
- permisos;
- retención;
- legal hold;
- lecturas sensibles;
- exportaciones;
- integración M06;
- instrumentación;
- UI;
- repositorios;
- CI;
- JaCoCo;
- privacidad;
- archivos;
- pruebas;
- riesgos;
- deuda;
- staging;
- preparación Etapa 6;
- checklist;
- parada.

### Pruebas

Debe incluir:

- migración;
- permisos;
- retención;
- auditoría de lectura;
- export;
- M06;
- UI/repos;
- CI;
- privacidad;
- regresión;
- comandos;
- resultados;
- limitaciones;
- staging.

---

## 20. Criterios de aceptación

- [ ] Base y rama correctas.
- [ ] Solo migración `031` nueva.
- [ ] `001`–`030` sin ediciones.
- [ ] Permisos M07 dedicados.
- [ ] Proxy `audit.view` eliminado en M07 donde corresponde.
- [ ] Retención con preview, execute y legal hold.
- [ ] Sin cron obligatorio.
- [ ] Lecturas sensibles auditadas.
- [ ] Exportaciones controladas o pendientes honestamente.
- [ ] Integración M06 implementada con seguridad o pendiente honestamente.
- [ ] RLS deny-by-default.
- [ ] Writers server-side.
- [ ] Sin PUBLIC EXECUTE.
- [ ] UI solo staff autorizado.
- [ ] Repositorios RPC-only.
- [ ] CI avanzado local.
- [ ] Baseline JaCoCo registrada.
- [ ] Sin analítica comercial.
- [ ] Sin proveedores externos.
- [ ] Tests verdes.
- [ ] Build/lint/JaCoCo verdes.
- [ ] Auth/username intactos.
- [ ] Staging `014`–`031` pendiente o validado con evidencia.
- [ ] Sin Etapa 6.
- [ ] Sin M08.
- [ ] Sin merge a main.

---

## 21. Preparación para Etapa 6

La Etapa 6 será:

```text
Validación integral, staging y cierre final de M07
```

No iniciarla en este turno.

Debe recibir como entrada:

- migraciones `029`–`031`;
- catálogos exactos;
- permisos dedicados;
- retención;
- UI;
- CI;
- calidad local;
- matriz remota pendiente;
- deudas explícitas.

---

## 22. Parada

No iniciar M07 Etapa 6.

No iniciar M08.

No agregar analítica comercial, tracking individual o proveedores externos.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M07-etapa-5-cierre.md
/docs/04-calidad/M07-pruebas-retencion-permisos-instrumentacion-cierre.md
```

No hacer commit hasta revisión.
