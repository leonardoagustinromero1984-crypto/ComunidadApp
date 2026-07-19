# M07 — Pruebas: retención, permisos, instrumentación y preparación de cierre

**Módulo:** M07 Etapa 5  
**Base:** `65b0a3d914cf13db6d525b5362f6c35869fea32a`  
**Suite nueva:** `M07Stage5RetentionPermissionsInstrumentationTest`  
**Total unit tests (local):** **535** (525 previas + nuevas Etapa 5; 0 failures)

---

## Migración 031

| Caso | Evidencia |
|---|---|
| 031 es la única nueva | `migration031_isOnlyNewAndPriorIntact` |
| 001–030 intactas vs base | quality script + git diff base |
| Permisos M07 presentes | test + SQL seed |
| Tablas retención + RLS + grants | test migración + script |
| Sin PUBLIC EXECUTE retención | script grep grants |
| `search_path` en DEFINER | script definer=search_path counts |
| Sin tablas marketing | script + test |

---

## Permisos

| Caso | Evidencia |
|---|---|
| Permisos dedicados Kotlin + matriz ADMIN | `dedicatedPermissions_inKotlinMatrixAndResolver` |
| AccountType/modules sin autoridad | resolver con claims dummy → deny |
| Deep link no concede | `deepLinkGrantsAccess() == false` |
| Usuario común denegado | `commonUserDenied_platformActorOrgSensitivity` |
| UI gates sin proxy `audit.view` | ViewModels overview/metrics/health/incidents/retention |

---

## Retención

| Caso | Evidencia |
|---|---|
| Listado / preview / execute | `retention_listPreviewExecuteLegalHoldBatchesUnknownTarget` |
| Execute sin preview / preview expirado | mismo test |
| Idempotencia preview consumido | mismo test |
| Legal hold / release / política legal | mismo test |
| Target desconocido | mismo test |
| Sin datos sensibles en respuesta | assert sin `password` |
| RPC-only contracts | `retentionRepository_rpcOnlyContract` |

---

## Lecturas / exportaciones

| Caso | Evidencia |
|---|---|
| Export denied / authorized + file pending | `export_filePendingHonest_noSignedUrl` |
| Sin signed URL / path | `simulatedArtifactLabel == null` |
| Catálogo 118 + keys Etapa 5 | `catalog_size118_andStage5KeysPresent` |
| Stage 3/4 actualizados a 118 union | `M07Stage3*`, `M07Stage4*` |

---

## M06

| Caso | Evidencia |
|---|---|
| Pendiente honesto | `m06Integration_documentedPending_noSimulatedSuccess` |
| Clave catálogo `m07.incident.staff_notification` | presente, sin envío simulado |

---

## UI / repositorios / DataProvider

| Caso | Evidencia |
|---|---|
| Wiring Retention Mock/Supabase | `M07DataProviderWiringTest` |
| Pantallas retention + permissions info | código UI + NavRoutes |
| Preview ≠ execute + confirmación | `ObservabilityRetentionViewModel` |

---

## CI

| Caso | Evidencia |
|---|---|
| Catálogos / permisos / migraciones / secretos | `scripts/ci/m07_quality_checks.sh` PASSED |
| Artefacto Markdown | `app/build/reports/m07-quality-summary.md` |
| JaCoCo informativo | report XML con baseline real (ver cierre Etapa 5) |

---

## Regresión

| Caso | Resultado |
|---|---|
| 525 previas + nuevas = 535 | PASS |
| AuthRepository / domain/auth / UsernameValidators | sin diff |
| Sin Crashlytics/Firebase Analytics/Sentry/OTel | quality + revisión código |
| Staging 014–031 | PENDIENTE (no remoto) |

---

## Comandos ejecutados (local)

```text
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:jacocoTestReport
bash scripts/ci/m07_quality_checks.sh
```

No inventar resultados remotos. No aplicar staging/producción en esta etapa.
