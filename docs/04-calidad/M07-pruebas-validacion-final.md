# M07 — Pruebas: validación final (Etapa 6)

**Módulo:** M07 Etapa 6  
**Producto:** LeoVer  
**Base:** `a02acb15bc78be6b9c405d563f2de2030da70abd`  
**Rama:** `m07/etapa-6-validacion-staging-cierre-final`  
**Suite nueva:** `M07Stage6FinalValidationTest`  
**Total unit tests (local):** **544** (0 failures, 0 errors, 0 skipped)

---

## 1. Auditoría estática

| Caso | Evidencia |
|---|---|
| Contratos / catálogos / correlation / sanitización / AppLogger | revisión Etapas 1–5 + quality script |
| Writers DEFINER + search_path | `m07_quality_checks.sh` |
| Sin Crashlytics / Firebase Analytics / Sentry / OpenTelemetry | denylist CI + rg |
| Sin tablas marketing / tracking | quality script |
| Auth/username intactos | git diff vacío vs HEAD en AuthRepository / domain/auth / UsernameValidators |

---

## 2. Migraciones

| Caso | Evidencia |
|---|---|
| 001–031 intactas vs base | quality script prior-edit check + git |
| 032 mínima D1–D3 | `migrations_001to032_uniqueAnd032IsHardeningOnly` |
| BEGIN/COMMIT / search_path | test + script |
| Sin editar 029–031 | git diff vacío en esos archivos |

---

## 3. Catálogos

| Caso | Evidencia |
|---|---|
| 118 eventos Kotlin | `catalogs_exactSizes` + `ObservabilityEventCatalog.size()` |
| 118 SQL | quality script |
| 28 métricas / 14 health | mismo test + script |
| Drift detectado por script | PASSED |
| `m07.incident.staff_notification` catalogado sin envío | `debtMarkers_honest` |

---

## 4. Permisos

| Caso | Evidencia |
|---|---|
| 8 permisos dedicados | `dedicatedPermissions_presentEverywhere` |
| Matriz ADMIN | mismo test |
| AccountType / deep link | `accountTypeAndDeepLinkNeverAuthorize` |
| `audit.view` no es autoridad M07 local | `auditView_isNotM07ViewAuthority` |
| SQL sin OR audit.view residual en list | assert 032 |
| UI sin AdministrativeAuditScreen en rutas M07 | `m07Routes_doNotUseAdministrativeAuditProxy` |

---

## 5. Seguridad / auditoría / errores

| Caso | Evidencia |
|---|---|
| Suites Etapas 2–5 conservadas | 535 previas + Stage 6 |
| List application errors | repo + VM Etapa 6 |
| Append-only / metadata / sanitización | tests Stage 2–3 + script |

---

## 6. Métricas / health / alertas / incidentes

| Caso | Evidencia |
|---|---|
| Stage 4 suite | `M07Stage4OperationalObservabilityTest` |
| Health MANUAL + `health.check.execute` (032 D2) | migración + test 032 |
| Evaluate alerts sin audit.view (032 D3) | migración + test 032 |

---

## 7. Retención / export / M06

| Caso | Evidencia |
|---|---|
| Stage 5 retención/legal hold | `M07Stage5RetentionPermissionsInstrumentationTest` |
| Export file pending | Stage 5 + `debtMarkers_honest` |
| M06 pendiente | mismo |

---

## 8. UI / repos / DataProvider

| Caso | Evidencia |
|---|---|
| Pantallas audit/errors/exports dedicadas | NavGraph + `ObservabilityScreens` / List VMs |
| Wiring DataProvider | `M07DataProviderWiringTest` |
| RPC-only Supabase | repos Stage 3–5 |

---

## 9. Edge

| Caso | Evidencia |
|---|---|
| Sin secretos en cliente | revisión estática / quality secret scan |
| Validación remota Edge | **NO EJECUTADO** (staging pendiente) |

---

## 10. CI

| Caso | Evidencia |
|---|---|
| `bash scripts/ci/m07_quality_checks.sh` | **QUALITY CHECKS PASSED** |
| Resumen Markdown | `app/build/reports/m07-quality-summary.md` |
| Highest migration 032 | summary Generated 2026-07-17 |

---

## 11. Regresión

Conservadas pruebas M00–M06 + M07 Etapas 1–5. Ajuste menor Stage 2: org-deny usa `OBSERVABILITY_VIEW` en lugar de `AUDIT_VIEW`. Stage 5 permite presencia de 032.

---

## 12. Comandos ejecutados

```text
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:jacocoTestReport
bash scripts/ci/m07_quality_checks.sh
```

---

## 13. Resultados

| Check | Resultado |
|---|---|
| assembleDebug | SUCCESS |
| testDebugUnitTest | **544** / 0 failures / 0 errors / 0 skipped |
| lintDebug | SUCCESS |
| jacocoTestReport | SUCCESS |
| m07_quality_checks | PASSED |

### JaCoCo baseline final (real)

| Counter | Covered | Missed | % |
|---|---:|---:|---:|
| Line | 9729 | 24633 | **28.31%** |
| Branch | 2608 | 24196 | **9.73%** |
| Instruction | 63565 | 292593 | **17.85%** |
| Class | 571 | 1491 | **27.69%** |

Entrada Etapa 5: line 28.59% / branch 9.86% / instruction 18.03% / class 28.14%. Variación por código UI/VMs M07 añadido (denominador crece). Umbral 30%/20% **no activado**.

---

## 14. Limitaciones

- Staging remoto **no ejecutado**.  
- Username **no revalidado**.  
- Exportación de archivo e integración M06 siguen pendientes.  
- No se inventaron porcentajes ni resultados remotos.

---

## 15. Staging

Ver `docs/04-calidad/M07-reporte-validacion-staging.md`.

```text
PENDIENTE DE VALIDACIÓN REMOTA
RELEASE BLOQUEADO
USERNAME NO REVALIDADO — STAGING PENDIENTE
```
