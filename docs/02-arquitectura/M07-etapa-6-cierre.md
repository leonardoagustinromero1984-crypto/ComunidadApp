# M07 Etapa 6 — Cierre: validación integral, staging y cierre final

**Estado:** foundation M07 cerrado **localmente**; release remoto **bloqueado**  
**Producto:** LeoVer  
**Rama:** `m07/etapa-6-validacion-staging-cierre-final`  
**Commit base:** `a02acb15bc78be6b9c405d563f2de2030da70abd` (M07 Etapa 5)  
**Migración correctiva:** `supabase/migrations/032_m07_stage6_final_validation_hardening.sql`  
**Alcance:** únicamente M07 Etapa 6. Sin M08. Sin merge a `main`. Sin producción. Sin funcionalidad nueva.

---

## Resumen

Auditoría integral de Etapas 1–5. Se corrigieron defectos bloqueantes residuales del proxy `audit.view` (SQL + UI + autorización local). Catálogos exactos **118 / 28 / 14**. Quality script **PASSED**. Build/lint/tests/JaCoCo locales **SUCCESS**. Staging **no aplicado** (sin acceso autorizado). Deudas honestas: exportación de archivo, integración M06, staging y username.

---

## Defectos encontrados

| ID | Severidad | Descripción |
|---|---|---|
| **D1** | Bloqueante | `m07_list_audit_events` aún OR’d `audit.view` como autoridad M07 |
| **D2** | Bloqueante | `m07_record_health_check` MANUAL exigía `audit.view` (rompía `health.check.execute`) |
| **D3** | Bloqueante | `m07_evaluate_alert_rule` / `m07_evaluate_enabled_alert_rules` exigían `audit.view` |
| **B1** | Bloqueante | Rutas `observability_audit` / `errors` / `exports` montaban `AdministrativeAuditScreen` con gate `AUDIT_VIEW` |
| **B2** | Bloqueante | `ObservabilityAuthorization` VIEW aún OR’d `AUDIT_VIEW`; ViewModels mapeaban `PermissionCode.AUDIT_VIEW` |

No bloqueantes (deuda documentada): **EXPORTACIÓN DE ARCHIVO PENDIENTE**, **INTEGRACIÓN M06 PENDIENTE**, staging remoto, umbral JaCoCo 30%/20% no activado.

---

## Correcciones

1. **Migración 032** — `CREATE OR REPLACE` mínimo de gates (D1–D3); no edita 029–031.
2. **UI** — pantallas dedicadas `ObservabilityAuditListScreen`, `ObservabilityErrorsListScreen`, `ObservabilityExportsScreen` + ViewModels con permisos dedicados; NavGraph sin proxy `AdministrativeAuditScreen` en rutas M07.
3. **Repos** — `ApplicationErrorRepository.list` + wiring Supabase RPC `m07_list_application_errors`.
4. **Autorización local** — VIEW solo con permisos M07 dedicados; sin mapear `audit.view` a autoridad M07.
5. **CI** — quality script / workflow permiten highest **031 o 032**; base prior-edit check → `a02acb1…`; staging note **014–032**.
6. **Tests** — `M07Stage6FinalValidationTest` + ajuste Stage 5 (032 permitida) + Stage 2 org-deny con `OBSERVABILITY_VIEW`.

---

## Migración 032

- Presente: `032_m07_stage6_final_validation_hardening.sql`.
- Alcance: solo corrección de gates de permiso documentados (D1–D3).
- `BEGIN`/`COMMIT`; `SECURITY DEFINER` + `SET search_path = public`.
- **001–031 intactas** vs commit base (sin editar archivos 029–031).
- Numeración única hasta 032.
- Sin funcionalidad nueva, sin tablas marketing/tracking, sin secretos, sin cron.

Gates resultantes:

- list audit → `observability.view` \| `audit.view_sensitive`
- health MANUAL → `health.check.execute`
- evaluate alerts → `observability.manage` \| `alert.manage`

---

## Catálogos (igualdad exacta)

| Catálogo | Kotlin | SQL | Estado |
|---|---:|---:|---|
| Event keys | **118** | **118** | sin drift (quality script) |
| Metric keys | **28** | **28** | OK |
| Health checks | **14** | **14** | OK |

- Sin keys duplicadas; convención `m07.*` / allowlists.
- Metadata deny-by-default; event key desconocido denegado.
- `m07.incident.staff_notification` catalogado; **sin simulación de envío**.

---

## Permisos M07

| Permiso | Uso |
|---|---|
| `observability.view` | overview, métricas, health, listados operativos |
| `observability.manage` | administración operativa / evaluate |
| `audit.view_sensitive` | auditoría sensible |
| `security.events.view` | security events |
| `export.audit_data` | exportaciones |
| `alert.manage` | acknowledge/resolve / evaluate |
| `retention.manage` | retención / legal hold |
| `health.check.execute` | health manual |

Confirmado: usuario común denegado; `AccountType` / `active_modules` sin autoridad; actores plataforma para globales; roles M03 limitados a org; deep links sin autoridad; UI↔RPC alineadas; **sin proxy residual `audit.view` como autoridad M07** (SQL 032 + UI dedicada + authorize local). `PermissionCode.AUDIT_VIEW` permanece para **M02** `ADMINISTRATIVE_AUDIT` únicamente.

---

## RLS y grants

- Tablas M07 deny-by-default + RLS.
- Grants mínimos; writers internos sin `PUBLIC EXECUTE`.
- `m07_record_metric` solo `service_role`.
- Android sin service role / sin acceso directo a tablas (RPC-only).
- Sin tablas marketing / analytics comercial / tracking individual.

---

## Auditoría / seguridad / errores

- Append-only; actor server-side; org validada; correlation ID.
- Metadata allowlisted; sin stack / Throwable raw / JWT / Bearer / tokens / service role / signed URLs / SQL / body INTERNAL / chat / documentos / PII completa.
- Fingerprints estables; deduplicación; una auditoría por request paginado (no por fila).
- Denegaciones relevantes → `security_events`; técnicos → `application_errors`; sin loops de observabilidad.

---

## Métricas / health

- 28 métricas agregadas; 14 health tipados.
- Sin user ID / email / IP / coordenadas / fingerprint individual.
- Dimensions/units/ventanas allowlisted; deduplicación.
- Android sin escritura remota arbitraria; UNKNOWN sin evidencia; TTL; manual con permiso; sin mutación de dominio; sin cron obligatorio; details sanitizados.
- Edge push / delete-account sin secretos en cliente.

---

## Incidentes

- Condiciones allowlisted; sin SQL libre; thresholds / cooldown / dedup.
- Idempotencia; `OPEN → ACKNOWLEDGED → RESOLVED`; inválidas denegadas; `SUPPRESSED` según política; auditoría de transiciones; sin DELETE cliente; sin PII; sin loops.

---

## Retención / legal hold

- Preview no muta; execute exige preview válido/reciente; preview consumido no reutilizable; execute idempotente; lotes limitados.
- Legal hold bloquea; `LEGAL_REVIEW_REQUIRED` no se purga; incidentes abiertos / exportaciones en proceso protegidos.
- Silos M02–M06 no eliminados; respuestas sin contenido eliminado; correlation + run auditado; sin cron obligatorio; UI preview/confirm/execute separados; deep link no ejecuta.

---

## Exportación

**EXPORTACIÓN DE ARCHIVO PENDIENTE**

- Request/authorize + `AUTHORIZED` + `filePending`.
- `READY_SIMULATED` no alcanzable en flujos nuevos / no mostrado como archivo real.
- Sin CSV/JSONL simulado, signed URL, bucket/path en Android.
- Export denied/requested auditados; filtros sin PII.

---

## Integración M06

**INTEGRACIÓN M06 PENDIENTE**

No implementada solo para cerrar deuda. Clave `m07.incident.staff_notification` catalogada; envío no simulado.

---

## UI / repositorios

Rutas: `observability_overview`, `_metrics`, `_health`, `_incidents`, `_audit`, `_errors`, `_exports`, `_retention`, `_permissions_info`.

- Ocultas sin permiso; revalidación; org correcta; deep links sin autoridad.
- Loading/empty/error/retry; paginación; filtros sanitizados; logout/cambio de cuenta limpia estado.
- Info de permisos no edita roles; staging UI **PENDIENTE** sin evidencia; sin contenido sensible.

Repos: interfaces completas; mocks deterministas; Supabase RPC-only; `useSupabase=false` funcional; errores seguros; sin fake success; repos Etapas 3–5 conservados.

---

## Edge

Sin cambios de proveedor. Push / delete-account: sin secretos al cliente; logs sanitizados (validación remota pendiente).

---

## CI

- `scripts/ci/m07_quality_checks.sh` → **QUALITY CHECKS PASSED** (catálogos, permisos, migraciones, DEFINER/search_path, writers, RLS, secretos, tablas prohibidas, resumen Markdown).
- Sin credenciales staging; sin proveedor externo en denylist.

---

## JaCoCo (baseline final real)

Fuente: `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` tras `:app:jacocoTestReport`.

| Métrica | Covered | Missed | % |
|---|---:|---:|---:|
| Line | 9729 | 24633 | **28.31%** |
| Branch | 2608 | 24196 | **9.73%** |
| Instruction | 63565 | 292593 | **17.85%** |
| Class | 571 | 1491 | **27.69%** |

**Variación vs entrada Etapa 5** (line 28.59% / branch 9.86% / instruction 18.03% / class 28.14%): ligera baja relativa por incremento de código/UI/VMs M07 y superficie no instrumentada (Compose/UI screens), no por exclusión arbitraria. Umbral **line 30% / instruction 20% no activado**.

---

## Pruebas y build

| Check | Resultado |
|---|---|
| `:app:assembleDebug` | SUCCESS |
| `:app:testDebugUnitTest` | SUCCESS — **544** tests, 0 failures, 0 errors, 0 skipped |
| `:app:lintDebug` | SUCCESS |
| `:app:jacocoTestReport` | SUCCESS |
| `scripts/ci/m07_quality_checks.sh` | PASSED |

Conservadas las 535 previas + suite Etapa 6 (+ ajuste Stage 2/5).

---

## Staging

**PENDIENTE DE VALIDACIÓN REMOTA**

- Sin `supabase/config.toml` linkeado; sin project ref autorizado; sin backup; sin apply remoto.
- Secuencia pendiente: `014`→…→`031` **+ `032`** (creada por defecto bloqueante).
- Producción no utilizada.

**RELEASE BLOQUEADO** hasta staging PASS.

---

## Username

**USERNAME NO REVALIDADO — STAGING PENDIENTE**

`AuthRepository`, `domain/auth` y `UsernameValidators` **intactos**. No corregido en M07. Requiere rama de autenticación separada si persiste tras staging.

---

## Riesgos residuales

1. Staging no validado → release bloqueado.
2. Exportación sin archivo real.
3. Notificación staff M07↔M06 no integrada.
4. Cobertura JaCoCo informativa bajo umbral propuesto (no gate).
5. Username no revalidado remotamente.

---

## Checklist Etapa 6

- [x] Base y rama correctas  
- [x] 032 mínima justificada; 001–031 intactas  
- [x] 118 / 28 / 14 exactos  
- [x] Permisos M07 + sin proxy `audit.view`  
- [x] RLS / grants / DEFINER / search_path  
- [x] Export / M06 deudas honestas  
- [x] UI/repos/CI locales  
- [x] Quality PASSED; tests/build/lint/JaCoCo  
- [x] Auth/username intactos; username pendiente  
- [x] Staging pendiente; **RELEASE BLOQUEADO**  
- [x] Cuatro documentos  
- [x] Sin M08 / sin merge / sin producción / sin commit automático  

---

## No iniciado

- M08  
- Merge a `main`  
- Producción  
- Analítica comercial / marketing / tracking individual / Crashlytics / Firebase Analytics / Sentry / OpenTelemetry  
- Commit (pendiente de pedido explícito)
