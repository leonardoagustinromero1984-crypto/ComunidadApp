# LEOVER — M07 Etapa 6: Validación Integral, Staging y Cierre Final

**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 6 — Validación integral y cierre final  
**Estado de entrada:** Etapa 5 aprobada y consolidada  
**Commit base:** `a02acb15bc78be6b9c405d563f2de2030da70abd`  
**Rama base:** `m07/etapa-5-retencion-permisos-instrumentacion-cierre`  
**Calidad de entrada:** 535 tests, 0 failures, 0 errors, 0 skipped; `assembleDebug`, `lintDebug`, `jacocoTestReport` y quality script aprobados  
**Catálogos:** 118 event keys, 28 métricas, 14 health checks  
**Staging heredado:** migraciones `014`–`031` pendientes de validación remota  
**Objetivo:** auditar las Etapas 1–5, corregir únicamente defectos bloqueantes, validar staging si existe autorización, documentar deuda y cerrar formalmente M07 sin iniciar M08.  
**Fuera de alcance:** analítica comercial, marketing, tracking individual, proveedores externos, cambios de auth/username, producción y merge a `main`.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M07-auditoria-inicial.md`
2. `/docs/02-arquitectura/M07-etapa-2-cierre.md`
3. `/docs/02-arquitectura/M07-etapa-3-cierre.md`
4. `/docs/02-arquitectura/M07-etapa-4-cierre.md`
5. `/docs/02-arquitectura/M07-etapa-5-cierre.md`
6. `/docs/04-calidad/M07-pruebas-persistencia-auditoria-seguridad-errores.md`
7. `/docs/04-calidad/M07-pruebas-observabilidad-operativa-health-metricas-ui.md`
8. `/docs/04-calidad/M07-pruebas-retencion-permisos-instrumentacion-cierre.md`
9. `/docs/03-modulos/M07-Auditoria-Analitica-y-Observabilidad.md`
10. `/docs/03-modulos/M07-Etapa-6-Validacion-Integral-Staging-y-Cierre-Final.md`
11. `/docs/02-arquitectura/M06-cierre-final.md`
12. `/docs/04-calidad/M06-reporte-validacion-staging.md`
13. ADR-0001 a ADR-0005.

---

## 2. Protección Git

1. Confirmar commit base:

```text
a02acb15bc78be6b9c405d563f2de2030da70abd
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m07/etapa-6-validacion-staging-cierre-final
```

4. No mezclar GPS, mapas ni pagos.
5. No hacer merge a `main`.
6. No iniciar M08.
7. No modificar `AuthRepository`, `domain/auth` ni `UsernameValidators`.
8. No corregir el error de username en esta rama.
9. No usar producción.
10. No afirmar staging, exportación o integración M06 sin evidencia.

---

## 3. Alcance de auditoría final

Revisar integralmente:

- contratos Kotlin M07;
- catálogo de 118 event keys;
- catálogo SQL;
- 28 métricas;
- 14 health checks;
- permisos M07 dedicados;
- migraciones `029`, `030` y `031`;
- que `001`–`031` no tengan ediciones indebidas;
- tablas M07;
- writers `SECURITY DEFINER`;
- `search_path`;
- grants;
- RLS;
- metadata allowlist;
- correlation IDs;
- sanitización;
- `Throwable`;
- AppLogger;
- auditoría;
- seguridad;
- errores;
- métricas;
- health;
- reglas;
- incidentes;
- retención;
- legal hold;
- exportaciones;
- UI administrativa;
- navegación;
- repositorios;
- DataProvider;
- Edge Functions;
- CI;
- JaCoCo;
- privacidad;
- compatibilidad con silos M02–M06;
- deuda documentada.

Corregir únicamente defectos reales y bloqueantes de M07.

No agregar funcionalidades nuevas.

---

## 4. Migraciones

Auditar:

```text
029_m07_observability_audit_security_error_foundation.sql
030_m07_operational_observability_health_metrics_incidents.sql
031_m07_retention_permissions_instrumentation_closure_readiness.sql
```

Confirmar:

- `001`–`028` intactas;
- `029`–`031` sin ediciones posteriores no documentadas;
- numeración única;
- `BEGIN` / `COMMIT`;
- RLS activa;
- grants mínimos;
- writers internos sin `PUBLIC EXECUTE`;
- `SECURITY DEFINER` con `SET search_path = public`;
- sin SQL dinámico;
- sin secretos;
- sin service role en Android;
- sin tablas de marketing o tracking individual;
- compatibilidad de catálogo, permisos y seeds.

### Migración correctiva

Crear `032` únicamente si existe un defecto real y bloqueante:

```text
supabase/migrations/032_m07_stage6_final_validation_hardening.sql
```

Reglas:

- no editar `029`–`031`;
- alcance mínimo;
- defecto documentado;
- pruebas de regresión;
- sin funcionalidad nueva;
- no usar `032` solo para formato o refactor.

---

## 5. Catálogos y drift

Validar igualdad exacta:

```text
Kotlin event keys = SQL event keys = 118
Kotlin metric keys = SQL metric keys = 28
Kotlin health checks = SQL health checks = 14
Kotlin permissions M07 = SQL permissions M07
```

Confirmar:

- sin duplicados;
- convención correcta;
- metadata allowlisted;
- keys desconocidas denegadas;
- `m07.incident.staff_notification` catalogada pero no enviada;
- cambios de catálogo detectados por CI;
- quality script falla ante drift real.

---

## 6. Seguridad y permisos

Validar:

- usuario común sin acceso M07;
- `AccountType` y `active_modules` sin autoridad;
- permisos globales solo plataforma;
- roles M03 limitados a organización;
- deep link interno no concede acceso;
- `observability.view` para overview/métricas/health;
- `observability.manage` / `alert.manage` para gestión;
- `audit.view_sensitive` para auditoría sensible;
- `security.events.view` para seguridad;
- `export.audit_data` para export;
- `retention.manage` para retención;
- `health.check.execute` para check manual;
- RPC y UI alineadas;
- denial genera evento seguro cuando corresponde;
- no existe proxy residual `audit.view` en M07 salvo compatibilidad documentada no autoritativa.

---

## 7. Auditoría, seguridad y errores

Validar:

- append-only;
- actor derivado server-side;
- organización derivada/validada;
- correlation ID obligatorio;
- metadata deny-by-default;
- no stack trace;
- no Throwable raw;
- no JWT/Bearer/token/service role;
- no signed URL;
- no SQL;
- no body INTERNAL;
- no chat;
- no documento;
- no PII completa;
- deduplicación de errores;
- fingerprints estables sin PII;
- lecturas sensibles auditadas una vez por request;
- denegaciones relevantes en `security_events`;
- errores técnicos en `application_errors`;
- sin loops de observabilidad.

---

## 8. Métricas y health

Validar:

- 28 métricas exactas;
- 14 health checks exactos;
- métricas agregadas;
- sin user ID, email, IP, coordenadas o fingerprint individual;
- dimensions allowlisted;
- unit allowlisted;
- ventanas válidas;
- deduplicación;
- Android no escribe métricas remotas;
- `m07_record_metric` solo `service_role`;
- status health tipado;
- TTL;
- `UNKNOWN` sin evidencia;
- checks manuales con permiso;
- sin mutación de dominio;
- sin cron obligatorio;
- detalles sanitizados;
- Edge `push` y `delete-account` agregados y sin secretos.

---

## 9. Reglas e incidentes

Validar:

- condiciones allowlisted;
- sin SQL libre;
- thresholds válidos;
- cooldown;
- deduplicación;
- incidentes idempotentes;
- ciclo:

```text
OPEN → ACKNOWLEDGED → RESOLVED
```

- transiciones inválidas denegadas;
- `SUPPRESSED` según política;
- auditoría de transiciones;
- sin DELETE cliente;
- sin PII;
- sin loops.

### Integración M06

Mantener como:

```text
INTEGRACIÓN M06 PENDIENTE
```

salvo que exista evidencia segura end-to-end.

No implementar en Etapa 6 solo para eliminar la deuda.

No simular envío.

---

## 10. Retención y legal hold

Validar:

- políticas exactas;
- targets allowlisted;
- preview sin mutación;
- execute exige preview válido y reciente;
- preview consumido/idempotencia;
- lotes limitados;
- legal hold bloquea purga;
- `LEGAL_REVIEW_REQUIRED` no se purga automáticamente;
- incidentes abiertos protegidos;
- exportaciones en proceso protegidas;
- silos M02–M06 no eliminados;
- respuesta con conteos, no contenido;
- correlation ID;
- auditoría del run;
- sin cron obligatorio;
- UI exige confirmación explícita;
- deep link no ejecuta retención.

---

## 11. Exportaciones

Mantener como:

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
```

salvo que exista implementación segura real y probada.

Validar:

- requests y autorización;
- estados;
- `AUTHORIZED + filePending`;
- `READY_SIMULATED` no alcanzable en flujos nuevos;
- `READY_SIMULATED` no se muestra como archivo real;
- sin CSV/JSONL simulado;
- sin signed URLs;
- Android no expone bucket/path;
- export denied auditado;
- export requested auditado;
- sin PII en filtros.

La ausencia de archivo no bloquea el cierre del foundation M07 si queda documentada como deuda.

---

## 12. UI y navegación

Validar rutas:

```text
observability_overview
observability_metrics
observability_health
observability_incidents
observability_audit
observability_errors
observability_exports
observability_retention
observability_permissions_info
```

Confirmar:

- ocultas sin permiso;
- gate server-derived;
- organización correcta;
- deep link no concede acceso;
- loading/empty/error/retry;
- paginación;
- filtros sanitizados;
- logout/cambio de cuenta limpia estado;
- retención preview/confirm/execute separados;
- info de permisos no edita roles;
- staging muestra `PENDIENTE` sin evidencia;
- no contenido sensible.

---

## 13. Repositorios y DataProvider

Validar:

- interfaces completas;
- mocks deterministas;
- Supabase RPC-only;
- sin acceso directo a tablas;
- sin service role en Android;
- `useSupabase=false` funcional;
- `useSupabase=true` seguro;
- repos Etapa 3 conservados;
- repos Etapa 4/5 conservados;
- errores seguros;
- no loops;
- no fake success.

---

## 14. CI y JaCoCo

Ejecutar:

```bash
bash scripts/ci/m07_quality_checks.sh
```

Confirmar:

- catálogos exactos;
- permisos Kotlin↔SQL;
- migraciones numeradas;
- detección de migraciones anteriores modificadas cuando existe base;
- DEFINER/search_path;
- grants;
- RLS;
- secretos;
- tablas prohibidas;
- resumen Markdown;
- sin credenciales staging;
- sin proveedor externo.

### JaCoCo

Ejecutar reporte real.

Baseline de entrada:

```text
Line:        28.59%
Branch:       9.86%
Instruction: 18.03%
Class:       28.14%
```

Reglas:

- no inventar;
- no crear tests artificiales solo para subir porcentaje;
- no excluir paquetes arbitrariamente;
- no activar umbral `line ≥ 30% / instruction ≥ 20%` sin revisión;
- documentar baseline final real;
- si baja, explicar causa;
- si sube, documentar pruebas reales añadidas.

---

## 15. Calidad local

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:jacocoTestReport
```

Requisitos:

- conservar las 535 pruebas existentes;
- agregar pruebas solo por defectos reales o validación final;
- 0 failures;
- 0 errors;
- lint sin errores;
- build SUCCESS;
- quality script PASSED.

---

## 16. Validación staging

Determinar con evidencia si existe acceso autorizado a staging.

### Si existe acceso autorizado

Antes:

- identificar project ref real;
- revisar historial remoto;
- generar backup o punto de recuperación;
- registrar actor técnico, fecha y entorno;
- no usar producción;
- no reejecutar migraciones aplicadas;
- no editar migraciones aplicadas.

Aplicar únicamente las pendientes, en orden real:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022 → 023 → 024 → 025 → 026 → 027 → 028 → 029 → 030 → 031
```

Incluir `032` solo si fue creada por defecto bloqueante.

### Si no existe acceso autorizado

Documentar:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

No simular resultados.

El código puede cerrarse localmente, pero:

```text
RELEASE BLOQUEADO
```

hasta staging PASS.

---

## 17. Matriz remota mínima

### Seguridad

- cliente sin DML directo en tablas M07;
- writers internos no ejecutables por cliente;
- permisos dedicados;
- org scope;
- sensibilidad;
- deep links;
- metadata prohibida;
- export denied;
- retention denied;
- health manual denied.

### Datos

- catálogo exacto;
- auditoría append-only;
- error dedup;
- métricas agregadas;
- health TTL;
- incidentes idempotentes;
- legal hold;
- preview/execute;
- export file pending;
- `READY_SIMULATED` no alcanzable.

### Edge

- `push` métricas/health;
- `delete-account` métricas/health;
- logs sanitizados;
- sin secretos;
- correlation IDs.

### UI/dispositivo

- navegación staff;
- usuario común denegado;
- org incorrecta;
- logout;
- cambio de cuenta;
- filtros;
- incidentes;
- retención;
- export pendiente;
- staging pending/real.

### CI

- quality script;
- JaCoCo;
- artifacts;
- no secretos.

---

## 18. Revalidación de username

Después de aplicar las migraciones autorizadas en staging, volver a probar el problema de verificación de username.

Reglas:

- no corregirlo dentro de M07;
- registrar únicamente resultado;
- si continúa, abrir rama separada de autenticación;
- si desaparece, documentar qué dependencia remota lo resolvió;
- `AuthRepository`, `domain/auth` y `UsernameValidators` permanecen intactos en esta rama.

Si staging no se aplica, documentar:

```text
USERNAME NO REVALIDADO — STAGING PENDIENTE
```

---

## 19. Correcciones permitidas

Se permiten únicamente:

- correcciones Kotlin M07;
- correcciones SQL mediante `032` mínima;
- correcciones Edge M07;
- correcciones CI M07;
- pruebas;
- documentación.

No se permite:

- nueva funcionalidad;
- analítica comercial;
- proveedor externo;
- tracking individual;
- marketing;
- cambios auth/username;
- M08;
- merge a `main`;
- producción.

---

## 20. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M07-etapa-6-cierre.md
/docs/02-arquitectura/M07-cierre-final.md
/docs/04-calidad/M07-reporte-validacion-staging.md
/docs/04-calidad/M07-pruebas-validacion-final.md
```

### `M07-etapa-6-cierre.md`

Debe incluir:

- rama/base;
- defectos encontrados;
- correcciones;
- migración `032` o ausencia;
- catálogos;
- permisos;
- RLS/grants;
- auditoría/seguridad/errores;
- métricas/health;
- incidentes;
- retención;
- export;
- M06;
- UI/repos;
- Edge;
- CI;
- JaCoCo;
- pruebas;
- staging;
- username;
- riesgos;
- checklist.

### `M07-cierre-final.md`

Debe resumir:

- arquitectura completa;
- contratos;
- persistencia;
- catálogo;
- correlation;
- sanitización;
- seguridad;
- auditoría;
- errores;
- métricas;
- health;
- incidentes;
- retención;
- permisos;
- exportaciones;
- UI;
- CI;
- privacidad;
- compatibilidad M00–M06;
- deuda aceptada;
- condiciones de release;
- staging;
- auth/username;
- M08 no iniciado.

### `M07-reporte-validacion-staging.md`

Registrar para cada caso:

```text
caso
entorno
fecha
actor técnico
resultado
evidencia
observaciones
```

Sin acceso remoto: `NO EJECUTADO`.

### `M07-pruebas-validacion-final.md`

Registrar:

- auditoría estática;
- migraciones;
- catálogos;
- permisos;
- seguridad;
- métricas;
- health;
- alertas/incidentes;
- retención;
- export;
- UI;
- Edge;
- CI;
- regresión;
- comandos;
- resultados;
- limitaciones;
- staging.

---

## 21. Criterios de aceptación

- [ ] Base y rama correctas.
- [ ] `001`–`031` intactas o `032` mínima justificada.
- [ ] 118 event keys exactos.
- [ ] 28 métricas exactas.
- [ ] 14 health checks exactos.
- [ ] Permisos M07 exactos.
- [ ] RLS deny-by-default.
- [ ] Grants mínimos.
- [ ] Sin PUBLIC EXECUTE interno.
- [ ] Metadata segura.
- [ ] Correlation IDs.
- [ ] Sin PII/secrets/stack/SQL/signed URLs.
- [ ] Métricas agregadas.
- [ ] Health tipado.
- [ ] Incidentes idempotentes.
- [ ] Retención segura.
- [ ] Legal hold.
- [ ] Exportación pendiente honesta o real segura.
- [ ] Integración M06 pendiente honesta o real segura.
- [ ] UI protegida.
- [ ] Repos RPC-only.
- [ ] Edge sanitizado.
- [ ] Quality script PASSED.
- [ ] JaCoCo real documentado.
- [ ] Tests/build/lint verdes.
- [ ] Auth/username intactos.
- [ ] Username revalidado o pendiente honestamente.
- [ ] Staging PASS o pendiente honesta.
- [ ] Release bloqueado sin staging PASS.
- [ ] Cuatro documentos creados.
- [ ] Sin M08.
- [ ] Sin merge a main.
- [ ] Sin producción.

---

## 22. Parada

No iniciar M08.

No agregar analítica comercial, marketing, tracking individual o proveedores externos.

No corregir username/auth en esta rama.

No hacer merge a `main`.

No usar producción.

Detenerse al crear:

```text
/docs/02-arquitectura/M07-etapa-6-cierre.md
/docs/02-arquitectura/M07-cierre-final.md
/docs/04-calidad/M07-reporte-validacion-staging.md
/docs/04-calidad/M07-pruebas-validacion-final.md
```

No hacer commit hasta revisión.
