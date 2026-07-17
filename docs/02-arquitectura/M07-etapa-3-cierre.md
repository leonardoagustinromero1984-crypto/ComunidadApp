# M07 — Etapa 3 cierre: Persistencia segura, auditoría crítica y writers server-side

**Fecha:** 2026-07-17  
**Producto:** LeoVer  
**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Etapa:** 3 — Persistencia mínima y auditoría crítica  
**Rama:** `m07/etapa-3-persistencia-auditoria-critica-writers`  
**Commit base:** `ef8f13157b55fcea74e5ffc67804d77bf2d43257`  
**Estado:** implementación lista para revisión; **sin commit** en este cierre documental.

---

## 1. Resumen de implementación

Se agregó la foundation server-side de observabilidad:

- Migración única `029_m07_observability_audit_security_error_foundation.sql`
- Tablas append-only deny-by-default
- Catálogo SQL alineado a los **108** event keys Kotlin
- Writers SECURITY DEFINER + grants mínimos
- Repositorios Android RPC-only
- Instrumentación selectiva M01–M06 (sin tocar AuthRepository / domain/auth / UsernameValidators)
- AppLogger: remoto solo allowlisted, sin loops, sin proveedores externos

**No iniciado:** Etapa 4, M08, dashboards, alertas operativas, métricas/health productivos, analítica de producto, Crashlytics/Sentry/OTel.

---

## 2. Migración 029

| Check | Resultado |
|---|---|
| Archivo | `supabase/migrations/029_m07_observability_audit_security_error_foundation.sql` |
| 001–028 | intactas (no editadas) |
| Transaccional | `begin` … `commit` |
| Secretos | ninguno |
| search_path | `public` en funciones DEFINER |

---

## 3. Tablas creadas

| Tabla | Rol |
|---|---|
| `observability_event_catalog` | catálogo server-side (108 keys) |
| `audit_events` | auditoría append-only |
| `security_events` | seguridad (sin passwords/tokens/PII) |
| `application_errors` | errores sanitizados + fingerprint/dedup 15m |
| `observability_export_requests` | solicitudes (sin archivos / signed URLs) |

**No creadas:** `performance_metrics`, `health_checks`, `analytics_events`, `alert_rules`, `alert_incidents`.

---

## 4. Catálogo server-side

- Tabla `observability_event_catalog` sembrada desde el catálogo Kotlin Etapa 2 (108 keys).
- Validación: `m07_validate_event_key`, `m07_validate_metadata`.
- Event keys arbitrarios → `OBS_EVENT_UNKNOWN`.
- **Drift Kotlin↔SQL (futuro):** comparar sets de `event_key` en CI (test Etapa 3 ya aserta contención SQL⊇Kotlin); fallar pipeline si difieren.

---

## 5. Writers / funciones

Helpers: `m07_validate_*`, `m07_require_actor`, `m07_resolve_actor_type`, `m07_resolve_organization_scope`, `m07_sanitize_reason_code`, `m07_new_correlation_id`, `m07_validate_correlation_id`.

Writers: `m07_write_audit_event` (service_role), `m07_write_security_event` (authenticated allowlisted + service_role), `m07_write_application_error` (allowlisted + service_role), `m07_request_export`, lists, `m07_best_effort_*`, `m07_client_note_data_access`.

Best-effort: fallos de observabilidad no abortan dominio (`m07_best_effort_*`, reporter Android con flag anti-loop).

---

## 6. Grants y RLS

- RLS enable + policies `using (false) with check (false)` en tablas M07 (cliente sin DML directo).
- `revoke all … from public, anon, authenticated` en helpers internos.
- `m07_write_audit_event` → **solo** `service_role`.
- Security/error/export/list → grants explícitos authenticated donde corresponde + allowlists en función.

---

## 7. Correlation IDs

- Obligatorio en writes; regeneración server-side si blank; rechazo de PII (`user-`, email, `.`).
- Android: `ObservabilityInstrumentation.correlationOrNew()`; logout limpia contexto.
- Edge: reutiliza correlation de delete-account; push genera id opaco.
- Sin migración masiva de call sites.

---

## 8. Instrumentación M01–M06

| Evento | Capa |
|---|---|
| `m01.auth.login_failure` | LoginViewModel / SessionViewModel (no AuthRepository) |
| `m01.auth.logout` | SessionViewModel + clear correlation |
| `m01.consent.gate_unavailable` | AppLogger observa mensaje AuthRepository sin modificarlo |
| `m01.account.deletion_failed` | Edge `delete-account` |
| `m02.permission.denied` | AuthorizationService |
| `m02.admin.audit_read` / M04 sensitive | RPC `m07_client_note_data_access` (allowlisted) |
| `m05.signed_url.issued` | SupabaseFileDownloadRepository (sin URL/path completo) |
| `m05.storage.error` | allowlist error + RPC note |
| `m06.deep_link.permission_denied` | NotificationDeepLinkRouter |
| `m06.dead_letter.recorded` | trigger SQL en `notification_dead_letters` |
| `m06.edge.push_invoked` | Edge `push` best-effort audit |
| `m06.access_audit.decision` | RPC note allowlisted |

Silos existentes **no migrados ni borrados**.

---

## 9. Repositorios Android

- `SupabaseAuditEventRepository` — list RPC; append cliente denegado
- `SupabaseSecurityEventRepository` — solo allowlist
- `SupabaseApplicationErrorRepository` — solo allowlist sanitizado
- `SupabaseObservabilityExportRepository` — RPC export
- `DataProvider`: useSupabase → repos reales permitidos; mocks en local
- Sin service role; sin acceso a tablas

---

## 10. AppLogger

- SanitizedThrowable + correlation opcional
- Remoto solo códigos allowlisted; DEBUG no remoto
- Detección consent gate sin tocar AuthRepository
- Anti-loop (`ObservabilityClientReporter.reporting`)
- Sin Crashlytics/Sentry/OTel/Analytics

---

## 11. Compatibilidad con silos

Conservados: `user_status_history`, `role_assignment_history`, `organization_*`, `administrative_audit_log`, `file_access_audit`, `notification_access_audit`, `notification_dead_letters`.

Estrategia futura: vistas/proyección unificada sobre M07 + silos; sin copiar históricos en Etapa 3.

---

## 12. Calidad

| Check | Resultado |
|---|---|
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **515 tests, 0 failures, 0 errors, 0 skipped** |
| `lintDebug` | **SUCCESS** |

---

## 13. Staging 014–029

**PENDIENTE DE VALIDACIÓN REMOTA** (014–028 heredado + 029 nuevo).  
Sin apply staging/producción en esta etapa. Release sigue bloqueado hasta evidencia remota.

---

## 14. Auth / username

Intactos: `AuthRepository`, `domain/auth`, `UsernameValidators`. Sin corrección del error de username.

---

## 15. Parada

Documentos:

```text
/docs/02-arquitectura/M07-etapa-3-cierre.md
/docs/04-calidad/M07-pruebas-persistencia-auditoria-seguridad-errores.md
```

**No iniciar M07 Etapa 4.**  
**No iniciar M08.**  
**No hacer commit hasta revisión.**  
**No merge a `main`.**  
**No aplicar producción.**
