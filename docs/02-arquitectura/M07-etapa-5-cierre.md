# M07 Etapa 5 — Cierre: retención, permisos dedicados, instrumentación y preparación de cierre

**Estado:** implementada localmente (sin commit obligatorio en este paso)  
**Rama:** `m07/etapa-5-retencion-permisos-instrumentacion-cierre`  
**Commit base:** `65b0a3d914cf13db6d525b5362f6c35869fea32a` (M07 Etapa 4)  
**Migración:** `supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql`  
**Alcance:** únicamente M07 Etapa 5. No Etapa 6. No M08. Sin merge a `main`. Sin staging/producción.

---

## Resumen

LeoVer completa en Etapa 5 la gobernanza de retención, permisos M07 dedicados (reemplazo del proxy `audit.view` en rutas/RPC M07), auditoría de lecturas/acciones operativas, flujo de exportación con archivo pendiente honesto, instrumentación selectiva y pantallas internas de retención/permisos. La integración de notificaciones de incidentes con M06 permanece documentada como pendiente (sin simular éxito).

---

## Migración 031

- Transaccional (`begin` / `commit`).
- Compatible con 029/030; **001–030 intactas** (verificado vs base Git).
- Deny-by-default + RLS en tablas de retención.
- Grants mínimos; `SECURITY DEFINER` con `SET search_path = public`.
- Sin SQL dinámico, sin secretos, sin cron obligatorio, sin marketing/tracking/servicios externos.
- Seed de permisos dedicados + políticas de retención + RPCs preview/execute/legal hold.
- Catálogo de eventos: +10 claves (total Kotlin↔SQL **118**).

---

## Permisos dedicados

| Permiso | Uso mínimo |
|---|---|
| `observability.view` | overview, métricas, health |
| `observability.manage` | reglas / administración operativa |
| `audit.view_sensitive` | auditoría sensible |
| `security.events.view` | security events |
| `export.audit_data` | exportaciones |
| `alert.manage` | acknowledge/resolve |
| `retention.manage` | retención |
| `health.check.execute` | health manual |

Reglas aplicadas: no se eliminaron permisos de otros módulos; `AccountType`/`active_modules` no otorgan autoridad; deep links no conceden acceso; UI/RPC revalidan permisos reales; administración de roles sigue en M02.

---

## Retención y legal hold

Tablas:

- `observability_retention_policies`
- `observability_retention_runs`
- `observability_retention_run_items`

Políticas/modos sembrados según spec (incluye `LEGAL_REVIEW_REQUIRED`, `HARD_DELETE`, `ANONYMIZE`, etc.).

RPCs:

- `m07_list_retention_policies`
- `m07_preview_retention_run`
- `m07_execute_retention_run`
- `m07_list_retention_runs`
- `m07_set_legal_hold`
- `m07_release_legal_hold`

Reglas: preview no muta datos de negocio; execute exige preview válido/reciente; legal hold bloquea; lotes limitados; sin devolver contenido eliminado; correlación obligatoria.

---

## Auditoría de lecturas / acciones

Claves añadidas / cableadas en 031 + catálogo Kotlin:

- `m07.audit.read`, `m07.security.read`, `m07.error.read`
- `m07.export.requested` / denegaciones vía security writer
- `m07.retention.previewed|executed|legal_hold_changed`
- `m07.health.manual_check`
- `m07.incident.acknowledged|resolved`
- `m07.incident.staff_notification` (catálogo preparado; envío M06 pendiente)

Sin contenido leído, sin signed URLs, sin paths completos, sin filtros con PII.

---

## Exportaciones

Estados ampliados (`REQUESTED`…`CANCELLED` + legado `READY_SIMULATED`).

**EXPORTACIÓN DE ARCHIVO PENDIENTE**

- Request/authorize auditado.
- `file_pending=true`.
- Android no crea signed URLs ni descarga de archivo.
- No se simula CSV/JSONL ni artefacto descargable.

---

## Integración M06

**INTEGRACIÓN M06 PENDIENTE**

Motivo: allowlist `origin_module` de notificaciones M06 excluye `M07`; no hay canal/permiso staff M07 validado de punta a punta. Fallo de notificación no se simula; el acknowledge/resolve de incidentes no depende de M06.

---

## Instrumentación selectiva

- M00/CI: outcome de quality checks (`ObservabilityInstrumentation.reportCiQualityCheck`).
- M01/M02: logout / permission denied (existente; sin tocar AuthRepository).
- M04–M06: señales agregadas / writers existentes; sin alto volumen.
- M07: retención/export/health/incidentes vía RPC + logs locales selectivos.

Sin Crashlytics / Firebase Analytics / Sentry / OpenTelemetry / proveedores externos.

---

## UI

Rutas nuevas:

- `observability_retention`
- `observability_permissions_info`

Pantallas: políticas, preview/confirm/execute separados, historial, correlation ID, estados loading/empty/error/retry; info de permisos solo lectura (no edita roles). Gates con permisos dedicados (ya no `audit.view` proxy en overview/métricas/health/incidentes/retención).

---

## Repositorios

- `RetentionRepository` / `MockRetentionRepository` / `SupabaseRetentionRepository`
- `ObservabilityPermissionsResolver`
- `ObservabilityExportRepository` ampliado (`AUTHORIZED` + `filePending`)
- `DataProvider.retentionRepository` (RPC-only / mock)

Android sin service role; mocks deterministas.

---

## CI

- `scripts/ci/m07_quality_checks.sh` ampliado (001–031, catálogos, permisos, DEFINER/search_path, RLS, secretos, artefacto Markdown).
- Workflow Android CI: staging **014–031 PENDIENTE**; summary + JaCoCo informativo.

Resultado local: **QUALITY CHECKS PASSED**.

---

## Baseline JaCoCo (real, informativo)

Fuente: `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` tras `:app:jacocoTestReport` (classDirs AGP `built_in_kotlinc` corregidos en `app/build.gradle.kts`).

| Métrica | Covered | Missed | % |
|---|---:|---:|---:|
| Line | 9727 | 24300 | **28.59%** |
| Branch | 2606 | 23833 | **9.86%** |
| Instruction | 63550 | 288816 | **18.03%** |
| Class | 571 | 1458 | **28.14%** (571 clases con cobertura) |

Paquetes con menor cobertura de líneas (muestra): UI screens (`search`, `home`, `lostfound`, `chat`, `moderation`, `adoptions`, `admin`, `components`) en ~0% — esperado sin UI instrumentada.

**Propuesta Etapa 6 (no activada):** umbral informativo inicial de **line ≥ 30%** / **instruction ≥ 20%** solo tras revisión humana; no gate bloqueante en esta etapa.

Generated / Compose UI puede permanecer documentado como baja cobertura.

---

## Privacidad

Deny-by-default; sin identidad en métricas; sin PII en filtros; sin chat/INTERNAL/documentos/signed URLs/tokens/headers/SQL/stack/bucket paths; legal hold; permisos dedicados; organización; Android sin service role.

---

## Validaciones locales ejecutadas

| Check | Resultado |
|---|---|
| `:app:assembleDebug` | SUCCESS |
| `:app:testDebugUnitTest` | SUCCESS — **535** tests, 0 failures |
| `:app:lintDebug` | SUCCESS |
| `:app:jacocoTestReport` | SUCCESS (baseline real arriba) |
| `scripts/ci/m07_quality_checks.sh` | PASSED |
| Staging 014–031 | **PENDIENTE** (no aplicado) |
| AuthRepository / domain/auth / UsernameValidators | **intactos** |

---

## No iniciado

- M07 Etapa 6
- M08
- Merge a `main`
- Staging / producción
- Commit (pendiente de pedido explícito)
