# M00 — Plan de calidad

**Fecha:** 2026-07-14  
**Etapa:** 4 (cierre M00); lint verde desde Etapa 3  
**Base de medición:** auditoría M00 + Etapas 3–4

## 1. Estado actual (actualizado Etapa 4)

| Validación | Estado | Notas |
|------------|--------|-------|
| `:app:assembleDebug` | OK | |
| `:app:testDebugUnitTest` | OK | **20** tests (7 previos + 13 fundación: AppConfig, flags, sanitize, AppError) |
| `:app:lintDebug` | **OK** | 0 errors; 38 warnings (deuda no crítica) |
| CI | Workflow `.github/workflows/android-ci.yml` | PR/push a `main`; modo mock sin secretos |
| Instrumentados | Pendientes | Requieren emulador/dispositivo |

Evidencia: [M00-lint-antes.md](M00-lint-antes.md) → [M00-lint-despues.md](M00-lint-despues.md).

### Lint — Issues dominantes (Etapa 1)

| Count approx. | ID | Criticidad |
|--------------:|----|------------|
| 51 | `InvalidFragmentVersionForActivityResult` | Alta para CI; probable causa raíz de versión |
| 2 | `AppLinkUrlError` | Alta / funcionamiento deep links |
| 13 | `GradleDependency` / similares | Baja (no actualizar en masa) |
| 8 | `UnusedResources` | Media-baja |
| resto | Varios | Media-baja |

**Prohibido en Etapa 2:** crear `lint-baseline.xml` que oculte los 53 errores sin análisis.

---

## 2. Plan — `InvalidFragmentVersionForActivityResult`

### Hipótesis de causa raíz

Lint de `androidx.activity` exige **Fragment ≥ 1.3.0** cuando se usa `registerForActivityResult`. El catálogo actual no declara `androidx.fragment:fragment` de forma explícita; la versión transitiva puede ser antigua o inconsistente con Activity 1.8.0.

### Hecho en Etapa 3

1. Confirmado transitivo `androidx.fragment:fragment:1.0.0`.
2. Añadido `androidx.fragment:fragment-ktx:1.8.6` explícito.
3. Lint: **0** `InvalidFragmentVersionForActivityResult`.

---

## 3. Plan — `AppLinkUrlError`

### Problema

`AndroidManifest.xml` declara `intent-filter` con `android:autoVerify="true"` y scheme custom `com.comunidapp.app` / host `login-callback`. Lint exige scheme `http`/`https` y host de dominio web para **Android App Links**.

Este deep link es de **OAuth Supabase** (custom scheme), no App Link web.

### Hecho en Etapa 3

1. Quitado `android:autoVerify="true"` del intent-filter OAuth `com.comunidapp.app` / `login-callback`.
2. App Links HTTPS quedan para M11 cuando exista dominio real.
3. Lint sin `AppLinkUrlError`. Validar login en dispositivo en smoke futuro.

---

## 4. Criterios para aceptar un lint baseline

Causas raíz críticas resueltas en Etapa 3. Lint queda en verde **sin baseline**.

Un baseline solo se consideraría más adelante para warnings históricos no críticos, documentando cada ID.

---

## 5. Pruebas instrumentadas pendientes

- Ejecutar `:app:connectedDebugAndroidTest` en CI o máquina con emulador cuando exista workflow.  
- Priorizar smoke: splash → login mock → home.  
- No bloquean cierre documental de Etapa 2.

---

## 6. Dependencias y actualizaciones

- Las warnings `GradleDependency` / `NewerVersionAvailable` **no** autorizan updates masivos.  
- Cambios de versión: PR acotado, un eje (p. ej. solo Fragment), con compile + tests + lint.  
- Firebase BOM / Supabase BOM: coordinar con push y Auth.

---

## 7. Validaciones por etapa M00

| Etapa | Mínimo obligatorio |
|-------|--------------------|
| 2 (docs) | `assembleDebug` + `testDebugUnitTest`; lint registrado (puede fallar) |
| 3 (calidad/CI) | Lint Fragment + AppLinks verdes; workflow CI |
| 4 (config/observabilidad) | AppConfig/flags/logger/errores/UI states + network security; tests fundación; lint 0 errors |
| CI | Mismos comandos en GitHub Actions; sin secretos reales |

Comandos:

```bash
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
```

La etapa no debe **empeorar** el número de fallos de compile/tests respecto al checkpoint.
