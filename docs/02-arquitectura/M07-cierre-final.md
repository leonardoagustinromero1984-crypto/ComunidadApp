# M07 — Cierre final del módulo Auditoría, Analítica y Observabilidad

**Fecha:** 2026-07-17  
**Producto:** LeoVer  
**Etapas cerradas (local):** 1–6  
**Commit base Etapa 6:** `a02acb15bc78be6b9c405d563f2de2030da70abd`  
**Rama Etapa 6:** `m07/etapa-6-validacion-staging-cierre-final`  
**Estado de release:** **RELEASE BLOQUEADO** (staging pendiente)

---

## 1. Arquitectura M07

M07 es el foundation de observabilidad operativa de LeoVer: catálogo deny-by-default, writers `SECURITY DEFINER`, correlación, sanitización, permisos dedicados, métricas agregadas, health tipado, alertas/incidentes, retención/legal hold y exportaciones con archivo pendiente honesto.

Android consume vía RPC/mocks; no escribe tablas remotas arbitrarias; no usa service role; no autodeclara staff.

Migraciones M07: **029** (persistencia/auditoría), **030** (métricas/health/alertas), **031** (retención/permisos), **032** (hardening gates residuales Etapa 6).

---

## 2. Contratos Kotlin

Paquete `domain/observability`: enums, contratos tipados, catálogos, autorización, correlation, sanitización, retention policy, metadata allowlist. Repositorios en data + wiring `DataProvider`.

---

## 3. Persistencia

Tablas operativas M07 (auditoría, security events, application errors, metrics, health, alert rules, incidents, exports, retention policies/runs/items, catálogos). RLS deny-by-default; grants mínimos; writers internos sin `PUBLIC EXECUTE`; `SET search_path = public` en DEFINER.

001–028 (otros módulos) + 029–031 intactas; 032 solo gates.

---

## 4. Catálogo

- **118** event keys Kotlin ↔ SQL  
- **28** metric keys  
- **14** health checks  

Event key desconocido denegado; metadata allowlisted; sin drift (quality script).

---

## 5. Correlation

Correlation IDs validados en writers/RPC; propagación en lecturas/acciones auditadas; retención con correlation obligatoria.

---

## 6. Sanitización

`ThrowableSanitizer` / AppLogger / metadata allowlist: sin stack raw, JWT, Bearer, tokens, service role, signed URLs, SQL, body INTERNAL, chat, documentos, PII completa.

---

## 7. Seguridad

Permisos dedicados (no `AccountType` / `active_modules` / deep links). Actores plataforma para globales; org scope para roles M03. Denegaciones relevantes en `security_events`.

---

## 8. Auditoría

Append-only; actor server-side; org validada; una auditoría por request paginado; lecturas Etapa 5+ auditadas sin contenido leído.

---

## 9. Errores

`application_errors` con fingerprints estables y deduplicación; sin loops de observabilidad.

---

## 10. Métricas

Agregadas; dimensions/units/ventanas allowlisted; `m07_record_metric` solo service_role; Android `recordMetricLocalOnly` / denegación remota arbitraria.

---

## 11. Health

14 checks; UNKNOWN sin evidencia; TTL; manual con `health.check.execute`; details sanitizados; sin mutación de dominio; sin cron obligatorio.

---

## 12. Incidentes

Reglas allowlisted; cooldown/dedup; estados `OPEN → ACKNOWLEDGED → RESOLVED`; `SUPPRESSED` según política; sin DELETE cliente.

---

## 13. Retención

Preview ≠ execute; preview consumido; legal hold; `LEGAL_REVIEW_REQUIRED`; protección de incidentes abiertos y exports en proceso; silos M02–M06 preservados.

---

## 14. Permisos

`observability.view|manage`, `audit.view_sensitive`, `security.events.view`, `export.audit_data`, `alert.manage`, `retention.manage`, `health.check.execute`. Matriz administrativa alineada. Proxy `audit.view` eliminado como autoridad M07 (032 + UI + authorize).

---

## 15. Exportaciones

**EXPORTACIÓN DE ARCHIVO PENDIENTE** — request/authorize + `filePending`; sin artefacto descargable simulado.

---

## 16. UI

Nueve rutas `observability_*` con gates dedicados, estados loading/empty/error/retry, paginación, limpieza en logout/cambio de cuenta, permissions info solo lectura.

---

## 17. CI

`scripts/ci/m07_quality_checks.sh` + workflow Android: catálogos, migraciones, DEFINER/search_path, RLS, secretos, tablas prohibidas, resumen Markdown. **PASSED** localmente.

---

## 18. Privacidad

Deny-by-default end-to-end; sin identidad en métricas; sin proveedores externos de telemetría (Crashlytics / Firebase Analytics / Sentry / OpenTelemetry).

---

## 19. Compatibilidad M00–M06

Instrumentación selectiva; silos de dominio no purgados por retención M07; Edge push/delete-account sin secretos al cliente.

**INTEGRACIÓN M06 PENDIENTE** (notificación staff de incidentes).

---

## 20. Deuda aceptada

| Deuda | Estado |
|---|---|
| Exportación de archivo | PENDIENTE |
| Integración notificaciones M06 | PENDIENTE |
| Staging remoto 014–032 | PENDIENTE DE VALIDACIÓN REMOTA |
| Umbral JaCoCo 30%/20% | no activado |
| Username revalidación | USERNAME NO REVALIDADO — STAGING PENDIENTE |

---

## 21. Condiciones de release

Antes de release:

1. Autorizar staging no productivo; project ref; actor/fecha/entorno.  
2. Backup / punto de recuperación.  
3. Verificar historial remoto; no reejecutar/editar aplicadas.  
4. Aplicar pendientes en orden `014`→…→`031`→`032`.  
5. Matriz seguridad/UI/retención/export/health/incidentes.  
6. Revalidar username (sin tocar auth en M07; rama auth si falla).  
7. Mantener deudas honestas o cerrarlas con evidencia.  
8. **Bloquear release** ante cualquier fallo.

---

## 22. Staging

**PENDIENTE DE VALIDACIÓN REMOTA** — sin acceso autorizado demostrable (`config.toml` ausente). Producción no usada.

---

## 23. Auth / username

`AuthRepository`, `domain/auth`, `UsernameValidators` intactos. Username no corregido ni revalidado remotamente en esta etapa.

---

## 24. Validación local de cierre

- Tests: **544**, 0 failures  
- assemble / lint / JaCoCo: SUCCESS  
- Quality: PASSED  
- JaCoCo final: line **28.31%**, branch **9.73%**, instruction **17.85%**, class **27.69%**

---

## 25. Fuera de alcance / no iniciado

**M08 no iniciado.** Sin merge a `main`. Sin producción. Sin GPS/mapas/pagos. Sin analítica comercial, marketing, tracking individual ni telemetría SaaS externa.
