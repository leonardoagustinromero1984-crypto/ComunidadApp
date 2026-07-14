# M00 — Cierre Etapa 3: Calidad, saneamiento Git y CI

**Fecha:** 2026-07-14  
**Rama:** `m00/etapa-3-calidad-ci`  
**Módulo:** M00 — Fundación Técnica  

---

## 1. Estrategia de ramas y commits (WIP no perdido)

| Ref | SHA / nota | Contenido |
|-----|------------|-----------|
| `main` | `98d287c` | Producto sin WIP GPS/pagos |
| `wip/gps-mapas-pagos` | `68ceb82` | Checkpoint WIP funcional (GPS, mapa, pagos stub) |
| `checkpoint/pre-m00-etapa-2` | `68ceb82` | Misma punta WIP |
| `backup/pre-m00-etapa-3` | `830140d` | WIP + docs Etapa 2 completas (respaldo total) |
| `m00/etapa-2` | `830140d` | Historia mezclada (no mergear a main tal cual) |
| `m00/etapa-3-calidad-ci` | desde `main` + docs import + fixes calidad | **Rama limpia** para PR |

Procedimiento:

1. Commit docs Etapa 2 en `m00/etapa-2` → `830140d`.  
2. `backup/pre-m00-etapa-3` y `wip/gps-mapas-pagos` apuntan a estados seguros.  
3. Rama limpia `m00/etapa-3-calidad-ci` desde `main`.  
4. `git checkout m00/etapa-2 -- <rutas aprobadas docs/gobierno>` → commit `179f4ab` (solo docs).  
5. Fixes Fragment + Manifest + CI en commits posteriores de esta rama.

**Confirmación:** ningún cambio del usuario fue descartado (`git branch wip/gps-mapas-pagos`, `backup/pre-m00-etapa-3`).  
**No** se fusionó el checkpoint mezclado a `main`.

---

## 2. Archivos creados

| Archivo |
|---------|
| `.github/workflows/android-ci.yml` |
| `docs/04-calidad/M00-lint-antes.md` |
| `docs/04-calidad/M00-lint-despues.md` |
| `docs/02-arquitectura/M00-etapa-3-cierre.md` (este) |

## 3. Archivos modificados

| Archivo | Motivo |
|---------|--------|
| `gradle/libs.versions.toml` | `fragment-ktx` 1.8.6 |
| `app/build.gradle.kts` | `implementation(libs.androidx.fragment.ktx)` |
| `app/src/main/AndroidManifest.xml` | quitar `autoVerify` OAuth |
| `docs/04-calidad/M00-plan-de-calidad.md` | estado post-Etapa 3 |

## 4. Archivos eliminados

Ninguno.

## 5. Causas raíz lint

| Familia | Causa | Fix |
|---------|-------|-----|
| `InvalidFragmentVersionForActivityResult` | Transitiva `fragment:1.0.0` | Forzar `fragment-ktx:1.8.6` |
| `AppLinkUrlError` | `autoVerify` en scheme custom | Quitar `autoVerify`; conservar deep link |

Sin baseline. Sin `@SuppressLint` global. Sin update masivo de dependencias.

---

## 6. Resultados build / tests / lint

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue
```

| Comando | Antes | Después |
|---------|-------|---------|
| assembleDebug | SUCCESS | SUCCESS |
| testDebugUnitTest | 7 OK | **7 OK** (conservados) |
| lintDebug | FAIL 53 errors | **SUCCESS 0 errors** / ~35 warnings |

Detalle: [M00-lint-antes.md](../04-calidad/M00-lint-antes.md), [M00-lint-despues.md](../04-calidad/M00-lint-despues.md).

Instrumentados (futuro):

```bash
./gradlew.bat :app:connectedDebugAndroidTest
```

---

## 7. CI

Workflow: `.github/workflows/android-ci.yml`

- Triggers: `push` y `pull_request` a `main`.  
- JDK 17 Temurin + `gradle/actions/setup-gradle@v4`.  
- `permissions: contents: read`.  
- Concurrencia cancela runs previos de la misma rama.  
- Comando: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue`.  
- Sin secretos Supabase/Firebase/MP (modo mock).  
- Artefactos: reportes de tests y lint (`if: always()`).

Validación local de los mismos comandos: **PASSED**.

---

## 8. Riesgos y deuda pendiente

- ~35 warnings de dependencias/recursos (no admitidos como update masivo).  
- Smoke manual del deep link OAuth post-cambio de `autoVerify`.  
- WIP GPS/pagos aún fuera de `main` — mergear en PR aparte de features.  
- `google-services.json` versionado (repo privado).  
- AppConfig / FeatureFlags / AppLogger: **siguientes**, no Etapa 3.

---

## 9. Checklist de aceptación

- [x] Rama limpia `m00/etapa-3-calidad-ci` desde `main`  
- [x] Docs Etapa 2 separadas del WIP  
- [x] Backup / WIP refs; nada perdido  
- [x] assembleDebug OK  
- [x] testDebugUnitTest OK (≥ 7 tests)  
- [x] lintDebug OK  
- [x] Fragment Activity Result corregido en causa raíz  
- [x] AppLinkUrlError corregido sin dominio inventado  
- [x] Sin update masivo de dependencias  
- [x] Sin baseline ni suppress global de críticos  
- [x] `android-ci.yml` presente  
- [x] CI sin secretos reales  
- [x] Informes lint antes/después  
- [x] Sin cambios funcionales de negocio en esta rama  
- [x] Sin segundo backend  
- [x] Este cierre  

---

## 10. Recomendación siguiente etapa

1. Abrir PR `m00/etapa-3-calidad-ci` → `main` (**solo** docs M00 + calidad + CI).  
2. Mantener `wip/gps-mapas-pagos` para un PR feature futuro.  
3. Tras merge: Etapa M00 de **AppConfig / FeatureFlags / AppLogger** (si se aprueba), **sin** NestJS.  
4. Smoke device: login Supabase deep link.

**No avanzar** a FeatureFlags/AppLogger/M01 hasta revisión explícita de este cierre.
