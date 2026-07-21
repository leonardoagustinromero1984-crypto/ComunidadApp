# M08 — Reporte de validación Etapa 5 (UI responsables y transferencias)

**Producto:** LeoVer
**Módulo:** M08 — Mascotas y responsables
**Fecha:** 2026-07-21
**Rama:** `m08/etapa-5-ui-responsables-transferencias`
**Estado formal:**

```text
M08 ETAPA 5 — UI RESPONSABLES Y TRANSFERENCIAS LISTA
SMOKE INTEGRAL M08 — PENDIENTE
DEFECTOS ETAPA 4D — BACKLOG
PRODUCCIÓN NO MODIFICADA
```

Este reporte registra el estado de implementación y las **ejecuciones medidas** de la Etapa 5. **No declara PASS de smoke integral.**

---

## 1. Alcance validado (implementación)

| Entregable | Estado |
|---|---|
| Rutas `pet_responsibilities` / `pet_authorizations` / `pet_transfers` / `pet_transfer_detail` con URL encoding | Implementado |
| Entrada "Responsables y permisos" en PetDetail condicionada por `PetAccessContext.canRead` | Implementado |
| `PetResponsibilitiesViewModel` + `PetResponsibilitiesScreen` | Implementado |
| `PetAuthorizationsViewModel` + `PetAuthorizationsScreen` (allowlist sin transfer/deceased/archive/manage_responsibilities) | Implementado |
| `PetTransfersViewModel` + `PetTransfersScreen` + `PetTransferDetailScreen` (VM compartido) | Implementado |
| Cableado en `ComunidappNavGraph` con factories | Implementado |
| Fix `SerialName` DTO `pet_status_history` (`reason`/`changed_by`/`changed_at`) + orden `changed_at` | Implementado |
| Motivo opcional en `PetTransferRepository.cancel` (default compatible) | Implementado |
| Códigos/mensajes nuevos en `M08PetErrorMapper` | Implementado |
| Tests unitarios ViewModels + guardas estáticas (fakes + coroutines test) | Implementado |
| `scripts/ci/m08_stage5_quality_checks.sh` | Implementado |
| Backlog defectos 4D (`M08-backlog-defectos-smoke-staging.md`) | Publicado |

Restricciones verificadas: sin cambios en migraciones 001–036, sin migración 037, sin SQL remoto, sin secretos/service_role en `app/src/main`.

---

## 2. Tests agregados (Etapa 5)

| Suite | Cobertura principal | Casos medidos |
|---|---|---|
| `PetResponsibilitiesViewModelTest` | principal PERSON/ORG, co/temporales, vacío, capacidad denegada, revocar (nunca PRINCIPAL), ARCHIVED/DECEASED, doble envío, búsqueda controlada, repos nulos, mapeo de errores | 18 |
| `PetAuthorizationsViewModelTest` | grant múltiple válido, capacidad inválida/vacía, revocar, expiradas, FORBIDDEN, vigencia pasada, doble envío, repos nulos | 13 |
| `PetTransfersViewModelTest` | pendiente/historial, iniciar persona/org, XOR destino, conflicto PENDING, aceptar (refresh contexto), rechazar, cancelar con motivo, terminales no editables, PET_NOT_ACTIVE/FORBIDDEN, doble envío, detalle por id, expiración 7 días | 21 |
| `M08Stage5StaticGuardsTest` | rutas, wiring de navegación, gating por contexto (no ownerId), sin RPC en composables, sin GlobalScope, allowlist, DTO 035, script y backlog presentes | 10 |

Total nuevos Etapa 5: **62** casos. Baseline previa observada: **627**. Suite actual medida: **689** (627 + 62). Failures: **0**.

---

## 3. Ejecuciones registradas (2026-07-21)

Comandos con `--no-configuration-cache`:

| Verificación | Comando | Resultado |
|---|---|---|
| Unit tests local | `gradlew.bat :app:testLocalDebugUnitTest --no-configuration-cache` | **PASS — 689 tests, 0 failures, 0 errors** |
| Assemble local | `gradlew.bat :app:assembleLocalDebug --no-configuration-cache` | **PASS (BUILD SUCCESSFUL)** |
| Lint local | `gradlew.bat :app:lintLocalDebug --no-configuration-cache` | **PASS (BUILD SUCCESSFUL)** |
| Unit tests staging | `gradlew.bat :app:testStagingDebugUnitTest --no-configuration-cache` | **PASS — 689 tests, 0 failures, 0 errors** |
| Assemble staging | `gradlew.bat :app:assembleStagingDebug --no-configuration-cache` | **PASS (BUILD SUCCESSFUL)** — credenciales staging presentes en `local.properties` (no versionadas) |
| Lint staging | `gradlew.bat :app:lintStagingDebug --no-configuration-cache` | **PASS (BUILD SUCCESSFUL)** |
| JaCoCo | `gradlew.bat :app:jacocoTestReport --no-configuration-cache` | **PASS (BUILD SUCCESSFUL)** — cobertura informativa ~19% instrucciones / ~10% ramas (HTML Total) |

### Quality gates (Git Bash, secuencial)

| Script | Resultado |
|---|---|
| `scripts/ci/m07_quality_checks.sh` | **PASSED** |
| `scripts/ci/m08_stage2_quality_checks.sh` | **PASSED** |
| `scripts/ci/m08_stage3_freeze_quality_checks.sh` | **PASSED** |
| `scripts/ci/m08_stage3b_quality_checks.sh` | **PASSED** |
| `scripts/ci/m08_stage3c_quality_checks.sh` | **PASSED** |
| `scripts/ci/m08_stage4b_quality_checks.sh` | **PASSED** |
| `scripts/ci/m08_stage4c_quality_checks.sh` | **PASSED** |
| `scripts/ci/m08_stage4d_quality_checks.sh` | **PASSED** |
| `scripts/ci/m08_stage5_quality_checks.sh` | **PASSED** |

### Inventario esquema / producción

| Control | Resultado |
|---|---|
| Migración máxima | **036** |
| Migración 037 | **Ausente** |
| Diff `supabase/migrations/` | **Sin cambios** |
| Producción | **PRODUCCIÓN NO MODIFICADA** |
| `local.properties` / APK / secretos | No trackeados / no incluidos en el commit |

---

## 4. Pendientes y riesgos

- **Smoke integral M08 (staging):** PENDIENTE. Debe incluir la re-verificación de `M08-SMOKE-001` (crash en detalle de mascota, ver backlog 4D); el fix de `SerialName` de historial es candidato relacionado pero **no confirmado** como causa.
- **Defectos Etapa 4D:** permanecen en BACKLOG (`docs/04-calidad/M08-backlog-defectos-smoke-staging.md`); esta etapa no los cierra.
- **Notificaciones M06 de transferencias/co-responsabilidad:** fuera de alcance de Etapa 5.
- Producción: **PRODUCCIÓN NO MODIFICADA** (sin apply remoto, sin cambios de esquema).

---

## 5. Documentos relacionados

- `docs/02-arquitectura/M08-etapa-5-ui-responsables-transferencias.md`
- `docs/04-calidad/M08-backlog-defectos-smoke-staging.md`
- `docs/04-calidad/M08-matriz-impacto-y-no-regresion.md`
- `docs/03-modulos/M08-mascotas-y-responsables.md`
