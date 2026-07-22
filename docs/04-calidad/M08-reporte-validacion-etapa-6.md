# M08 — Reporte de validación Etapa 6

**Producto:** LeoVer  
**Módulo:** M08 — Mascotas y responsables  
**Fecha:** 2026-07-21  
**Rama:** `m08/etapa-6-fallecimiento-duplicados-fotos`  
**Estado formal:**

```text
M08 ETAPA 6 — FALLECIMIENTO, DUPLICADOS Y FOTOS LISTOS
SMOKE INTEGRAL M08 — PENDIENTE
DEFECTOS DIFERIDOS — BACKLOG
PRODUCCIÓN NO MODIFICADA
```

---

## Resumen de alcance

| Ítem | Estado |
|---|---|
| Auditoría de contratos | Implementado |
| `M08PetErrorMapper` códigos Etapa 6 | Implementado |
| Wrappers `PetRepository` (deceased/restore/history/duplicates) | Implementado |
| UI Detail + dialogs + badges | Implementado |
| `PetStatusHistoryScreen` + ruta | Implementado |
| Form: duplicados + microchip + avatar gate | Implementado |
| Galería pet completa | **BACKLOG** (documentado; sin pantalla) |
| Migraciones / producción | Sin cambios (máx. 036) |

---

## Resultados de validación (rellenar al cierre)

| Check | Resultado | Notas |
|---|---|---|
| `:app:testLocalDebugUnitTest` | _pendiente_ | `--max-workers=1 --no-daemon --no-configuration-cache` |
| `:app:assembleLocalDebug` | _pendiente_ | |
| `:app:lintLocalDebug` | _pendiente_ | |
| `:app:testStagingDebugUnitTest` | _pendiente_ | |
| `:app:assembleStagingDebug` | _pendiente_ | |
| `:app:lintStagingDebug` | _pendiente_ | |
| `:app:jacocoTestReport` | _pendiente_ | |
| `m07` quality (si aplica) | _pendiente_ | Git Bash, uno a uno |
| `m08_stage6_quality_checks.sh` | _pendiente_ | |
| Conteo tests | _pendiente_ | Requisito: >689, 0 failures |
| Migración máx. | **036** | Sin 037 |

---

## Criterios de no-regresión

- Sin smoke PASS inventado; `M08-SMOKE-001` permanece OPEN/BACKLOG.
- Sin `service_role` en fuentes Android.
- Sin escritura de `photo_url`.
- Sin autorización por `ownerId`.
- Sin Stage 7 / merge a `main` / APK trackeado.
