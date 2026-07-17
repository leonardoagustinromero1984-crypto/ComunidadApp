# M07 Etapa 4 — Cierre: Observabilidad operativa, health, métricas y UI interna

**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 4 — Observabilidad operativa interna  
**Estado:** Implementada localmente (sin commit obligatorio en este cierre)  
**Commit base:** `7fbaf357b1632e219a565d7965f23c65eb8b2d93`  
**Rama:** `m07/etapa-4-observabilidad-operativa-health-metricas-ui`  
**Fecha:** 2026-07-17  

---

## 1. Resumen

Se completó la capa operativa de LeoVer M07 Etapa 4:

- Migración única `030` (métricas, health, alertas, incidentes, preferencias/filtros).
- Writers server-side y lectura staff vía RPC.
- UI administrativa interna (overview, métricas, health, incidentes + reuso de auditoría Etapa 3).
- Instrumentación agregada en Edge `push` y `delete-account`.
- CI ampliado con checks locales + JaCoCo informativo.
- Sin Crashlytics, Firebase Analytics, Sentry, OpenTelemetry, SaaS, marketing ni tracking individual.

---

## 2. Migración 030

Archivo:

`supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql`

- Transaccional (`begin`/`commit`).
- Compatible con `029` (reutiliza `m07_require_actor`, correlation, `has_permission('audit.view')`, `m07_best_effort_audit`).
- Deny-by-default RLS (`using (false) with check (false)`).
- `SECURITY DEFINER` + `SET search_path = public`.
- Grants mínimos; `m07_record_metric` solo `service_role`.
- Sin secretos; sin tablas de analítica comercial.

Migraciones `001`–`029` no editadas.

---

## 3. Tablas creadas

| Tabla | Rol |
|-------|-----|
| `m07_metric_catalog` | Allowlist de métricas |
| `m07_health_check_catalog` | Allowlist de checks |
| `performance_metrics` | Métricas agregadas |
| `health_checks` | Evidencias de health + TTL |
| `alert_rules` | Reglas internas (sin SQL libre) |
| `alert_incidents` | Incidentes OPEN/ACK/RESOLVED/SUPPRESSED |
| `alert_incident_transitions` | Historial append-only |
| `observability_dashboard_preferences` | Preferencias staff |
| `observability_saved_filters` | Filtros privados validados |

**No creadas:** `analytics_events`, `analytics_sessions`, `product_funnels`, `marketing_segments`, `user_behavior_profiles`.

---

## 4. Métricas

Tipos: COUNTER, GAUGE, DURATION, RATE, SIZE, QUEUE_DEPTH, SUCCESS_RATIO, FAILURE_RATIO.

Catálogo Kotlin: `OperationalMetricCatalog` (28 keys) ↔ SQL `m07_metric_catalog`.

Incluye como mínimo las métricas M00–M07 pedidas (CI, auth/authz agregadas, moderación/soporte, uploads/storage, outbox/delivery/dead-letter, writers/health/incidentes).

Reglas: sin userId/email/IP/fingerprint; dimensions allowlisted; ventanas válidas; dedup por métrica/ventana/dimensions/source; Android no escribe métricas remotas arbitrarias.

---

## 5. Health checks

Estados: HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN, SKIPPED.

Checks iniciales (14) sembrados en catálogo, incluyendo `database.rpc_ping`, pipelines M05/M06, writers M07, `edge.push.readiness`, `edge.delete_account.readiness`, `ci.latest_build`.

Manual vía `m07_run_health_check_manual` (permiso `audit.view` como proxy de OBSERVABILITY_*). Sin evidencia → UNKNOWN. Sin mutación de dominio. Sin cron obligatorio.

---

## 6. Reglas e incidentes

Condiciones allowlisted (GREATER_THAN … NO_DATA). Tres reglas plataforma sembradas (`m07_unhealthy_count`, `m06_dead_letter`, `m06_outbox_depth`).

Incidentes: dedup por rule+ventana horaria; cooldown; transiciones auditadas best-effort; sin DELETE cliente; sin correo/SMS/proveedor externo.

**Integración notificación interna M06:** PENDIENTE (no simulada). Requiere reutilizar pipeline server-side validable.

---

## 7. Writers / RPCs

| Función | Acceso |
|---------|--------|
| `m07_record_metric` | service_role |
| `m07_record_health_check` | service_role + authenticated MANUAL gated |
| `m07_evaluate_alert_rule` / `m07_evaluate_enabled_alert_rules` | authenticated + service_role |
| `m07_acknowledge_incident` / `m07_resolve_incident` | authenticated + service_role |
| `m07_list_*` / `m07_get_operational_summary` | authenticated + service_role |
| `m07_save_dashboard_preferences` / `m07_save_filter` | authenticated + service_role |
| `m07_run_health_check_manual` | authenticated + service_role |

Sin PUBLIC EXECUTE en writers internos.

---

## 8. RLS y grants

- Tablas: revoke a `public`/`anon`/`authenticated`; policies deny-by-default.
- Lectura/gestión vía RPC con `has_permission('audit.view')` (proxy hasta permisos OBSERVABILITY_* dedicados en M02).
- Preferencias/filtros solo del actor autenticado vía RPC.

---

## 9. UI interna

Rutas: `observability_overview|metrics|health|incidents|audit|errors|exports`.

Gate: `AdministrativeAccessGate` + `PermissionCode.AUDIT_VIEW`. Deep link no concede autoridad. Entrada desde Perfil solo si `canViewAudit`.

Componentes: Overview/Metrics/Health/Incidents screens + ViewModels + `ObservabilityFilterState` + `ObservabilityUiErrorMapper`.

Auditoría/errores/exportaciones reutilizan pantallas/repos Etapa 3 (export request sin archivo/signed URL).

---

## 10. Repositorios

- `OperationalObservabilityRepository`
- `MockOperationalObservabilityRepository`
- `SupabaseOperationalObservabilityRepository` (RPC-only)

DataProvider: mock si `useSupabase=false`; RPC si `true`. Repos Etapa 3 intactos.

---

## 11. CI

- Workflow: quality script `scripts/ci/m07_quality_checks.sh` (numeración migraciones, catálogo Kotlin↔SQL, secret patterns, SQL básico).
- JaCoCo: task `:app:jacocoTestReport` informativa, no bloqueante; artefactos en CI.
- Sin credenciales staging/producción.

---

## 12. Edge

- `push`: duración, procesados, delivered/retryable/permanent/invalid, métricas agregadas, health `edge.push.readiness`.
- `delete-account`: duración, fallos por etapa sanitizada, métrica `m01.account.deletion_failure_count`, health `edge.delete_account.readiness`.
- Sin JWT/headers/payload/FCM secrets en observabilidad.

---

## 13. Privacidad

Métricas agregadas; sin identidad individual; sin contenido/chat/docs/coords/IP/fingerprints; sin signed URLs; filtros sin PII; export sin archivo; staging sin afirmar éxito.

---

## 14. Calidad local (evidencia)

| Check | Resultado |
|-------|-----------|
| `:app:assembleDebug` | SUCCESS (corrida previa en Etapa 4) |
| `:app:testDebugUnitTest` | **525** tests, 0 failures, 0 errors, 0 skipped |
| `:app:lintDebug` | SUCCESS |
| `:app:jacocoTestReport` | SUCCESS (baseline informativo) |

Pruebas previas conservadas (515) + nuevas Etapa 4.

---

## 15. Staging 014–030

**PENDIENTE DE VALIDACIÓN REMOTA.** No se aplicó staging ni producción en esta etapa. No hay evidencia remota; release sigue bloqueado por validación de migraciones.

---

## 16. Auth / username intactos

Confirmado sin cambios en:

- `AuthRepository`
- `domain/auth`
- `UsernameValidators`

No se corrigió el error de username.

---

## 17. Fuera de alcance / pendientes

- M07 Etapa 5 no iniciada.
- M08 no iniciado.
- Merge a `main` no realizado.
- Permisos M02 `OBSERVABILITY_VIEW/MANAGE` dedicados: proxy `audit.view`.
- Notificación interna M06 ante incidentes: PENDIENTE.
- Analítica comercial / marketing: fuera de alcance.
- Validación remota staging 014–030: PENDIENTE.
