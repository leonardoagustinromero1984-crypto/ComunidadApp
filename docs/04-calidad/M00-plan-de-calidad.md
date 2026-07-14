# M00 — Plan de calidad

**Fecha:** 2026-07-14  
**Etapa:** 2 (documentación); ejecución de fixes de lint = etapas posteriores  
**Base de medición:** auditoría M00 + corrida Etapa 1 / revalidación Etapa 2

## 1. Estado actual

| Validación | Estado (Etapa 1 / pre-Etapa 2) | Notas |
|------------|--------------------------------|-------|
| `:app:assembleDebug` | OK | |
| `:app:testDebugUnitTest` | OK | 7 tests (1 ejemplo + 6 mappers) |
| `:app:lintDebug` | FAIL | ~53 errors, ~35 warnings |
| Instrumentados | No ejecutados en M00 aún | Requieren emulador/dispositivo |

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

### Pasos (Etapa posterior de M00, no Etapa 2)

1. Confirmar versión resuelta:

   ```bash
   ./gradlew.bat :app:dependencies --configuration debugCompileClasspath | findstr fragment
   ```

2. Añadir explícitamente en `gradle/libs.versions.toml` + `app/build.gradle.kts`:

   - `androidx.fragment:fragment-ktx` ≥ **1.8.x** (o al menos 1.3.0).

3. Re-ejecutar `:app:lintDebug` y verificar caída masiva del conteo (~51 → ~0 de ese ID).

4. Si persiste en un call-site concreto, inspeccionar ese archivo (no silenciar con baseline global).

### Criterio de éxito

- Cero (o residual justificado) de `InvalidFragmentVersionForActivityResult`.

---

## 3. Plan — `AppLinkUrlError`

### Problema

`AndroidManifest.xml` declara `intent-filter` con `android:autoVerify="true"` y scheme custom `com.comunidapp.app` / host `login-callback`. Lint exige scheme `http`/`https` y host de dominio web para **Android App Links**.

Este deep link es de **OAuth Supabase** (custom scheme), no App Link web.

### Corrección propuesta (etapa de fixes)

1. Quitar `android:autoVerify="true"` del intent-filter de login-callback **o**  
2. Separar: deep link custom sin `autoVerify`; App Links https solo cuando exista dominio M11.

3. Verificar que el redirect URI en Supabase Auth coincida.

### Criterio de éxito

- Lint sin `AppLinkUrlError`.  
- Login con deep link sigue funcionando en dispositivo.

---

## 4. Criterios para aceptar un lint baseline

Solo **después** de:

1. Resolver Fragment / Activity Result.  
2. Resolver App Links / deep link.  
3. Revisar issues de seguridad/correctness restantes.  

Entonces se podrá generar baseline **solo** para deuda no crítica (UnusedResources, sugerencias de versiones, estilo), documentando cada ID retenido en este archivo o en un ADR de calidad.

Baseline **no** se crea en Etapa 2.

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
| Fixes calidad | Lint Fragment + AppLinks verdes o justificados |
| CI (futuro) | Mismos comandos en GitHub Actions; bloqueo de `.env` reales |

Comandos:

```bash
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
```

La etapa no debe **empeorar** el número de fallos de compile/tests respecto al checkpoint.
