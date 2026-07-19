# M07 — Pruebas: Observabilidad operativa, health, métricas y UI

**Módulo:** M07 Etapa 4  
**Alcance:** migración 030, métricas, health, alertas/incidentes, repos/UI, CI, regresión  
**Evidencia local:** `:app:testDebugUnitTest` → **525** tests, 0 failures, 0 errors, 0 skipped (2026-07-17)

---

## 1. Conservación

Se conservaron las **515** pruebas previas (Etapas 1–3 y módulos anteriores).  
Se agregaron pruebas Etapa 4 (suite `M07Stage4OperationalObservabilityTest` + wiring DataProvider ampliado).

Total medido: **525**.

---

## 2. Migración 030

Cubierto en `M07Stage4OperationalObservabilityTest`:

- `030` es la única migración nueva de esta etapa; `001`–`029` presentes.
- Numeración sin duplicados.
- Tablas esperadas presentes.
- Tablas de analítica comercial ausentes.
- RLS deny-by-default.
- Grants: `m07_record_metric` → service_role; revoke de public/anon/authenticated en writer.
- Sin PUBLIC EXECUTE hacia writer de métricas.
- `search_path = public`, SECURITY DEFINER, sin `service_role_key`.

---

## 3. Métricas

- Keys allowlisted (28) Kotlin ↔ SQL.
- Dimensions forbidden (`user_id`, email, IP, etc.) denegadas.
- Ventana inválida / unit / métrica desconocida rechazadas.
- Deduplicación mock por métrica/ventana/dimensions/source.
- `SupabaseOperationalObservabilityRepository.recordMetricLocalOnly` → `OBS_WRITE_DENIED`.

---

## 4. Health

- Estados HEALTHY (rpc_ping manual) / UNKNOWN sin evidencia.
- Detalle sanitizado (`NO_EVIDENCE`).
- Ejecución denegada sin permisos.
- Sin mutación de dominio (solo probes locales).

---

## 5. Reglas e incidentes

- OPEN → ACKNOWLEDGED → RESOLVED.
- Transición inválida denegada.
- Permisos manage requeridos para ack/resolve.
- Summary tipado sin PII (`THRESHOLD_BREACHED`).
- Evaluación SQL determinista (sin SQL libre) validada estáticamente en migración.

---

## 6. UI / repositorios

- Overview mock con `stagingStatus = PENDIENTE`.
- Filtros por módulo.
- Denegación sin autenticación.
- Contratos Mock/Supabase asignables a `OperationalObservabilityRepository`.
- DataProvider wiring Etapa 3 + Etapa 4.

Navegación: gate `AUDIT_VIEW`; rutas no expuestas a usuarios comunes en Profile sin permiso.

---

## 7. CI

Script `scripts/ci/m07_quality_checks.sh`:

- Catálogo eventos Kotlin↔SQL exacto (108).
- Catálogo métricas Kotlin↔SQL (28).
- Numeración de migraciones.
- Patrones locales de secretos.
- SQL básico 030 (begin/commit, sin analytics).

JaCoCo: `:app:jacocoTestReport` genera HTML/XML bajo `app/build/reports/jacoco/` (informativo, no bloqueante).

---

## 8. Regresión

- Auth/username: `UsernameValidators` + `AuthState` intactos (test de regresión).
- Catálogo eventos 108 intacto vs migración 029.
- WIP aislado: sin GPS/mapas/pagos; sin Etapa 5/M08.

---

## 9. Comandos ejecutados

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:jacocoTestReport
```

Resultados (no inventados):

| Comando | Resultado |
|---------|-----------|
| assembleDebug | SUCCESS |
| testDebugUnitTest | 525 / 0 fail / 0 err / 0 skip |
| lintDebug | SUCCESS |
| jacocoTestReport | SUCCESS (baseline informativo) |

---

## 10. Staging

Migraciones **014–030**: **PENDIENTE DE VALIDACIÓN REMOTA**.  
CI no usa credenciales staging ni producción.
