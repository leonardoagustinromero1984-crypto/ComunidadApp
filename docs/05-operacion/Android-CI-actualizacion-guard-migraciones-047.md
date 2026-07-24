# Android CI — actualización del guard de migraciones a 047

## Causa raíz

El workflow `android-ci.yml` ejecuta `scripts/ci/m07_quality_checks.sh` antes de assemble/test/lint.
Ese script conservaba un tope histórico de migraciones (**032–036**), incompatible con el repo actual (máxima **047** tras M09–M12).

## Error observado

```text
Highest migration: 047
Expected highest migration 032–036, got 047
QUALITY CHECKS FAILED
```

Paso: **M07 local quality checks (catalog, migrations, secrets, SQL)**.

## Control desactualizado

En `scripts/ci/m07_quality_checks.sh`:

- Título: `Migration numbering 001–034`
- Condición: highest ∈ {032, 033, 034, 035, 036}

## Corrección

- Título: `Migration numbering 001–047`
- Condición estricta: highest **debe ser exactamente `047`**
- Si aparece `048` (u otra) sin actualizar el guard deliberadamente → el CI vuelve a fallar
- No se usó rango permisivo ni `continue-on-error`

## Validaciones ejecutadas

```text
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Resultado esperado: quality checks PASSED y `Highest migration: 047`.

## Confirmaciones

- Migraciones **001–047** intactas (sin edición de SQL).
- **Sin migración 048**.
- Workflow sin debilitar: tests/lint siguen activos; JaCoCo permanece informativo.
- Advertencia **Node.js 20 deprecado** de Actions: informativa / no bloqueante (no es la causa del exit 1).
- Smoke funcional M12: **PENDIENTE EXTERNO** (no se declara M12 CERRADO).
- M13: no iniciado.

## Segundo fallo detectado: tarea lintDebug ambigua

### Contexto

Tras el hotfix del guard de migraciones (**M07 quality checks: PASS**, highest **047**), el job falló en el paso **Assemble, test, lint (mock / no secrets)**.

### Error observado

```text
Cannot locate tasks that match ':app:lintDebug' as task 'lintDebug' is ambiguous in project ':app'.
Candidates: lintAnalyzeLocalDebug, lintFixLocalDebug, lintLocalDebug, lintReportLocalDebug, …
```

### Causa raíz

El proyecto define **product flavors** (`local`, etc.). Las tareas genéricas `assembleDebug` / `testDebugUnitTest` / `lintDebug` son **ambiguas**; CI debe apuntar a la variante **`localDebug`**.

### Tarea anterior (workflow)

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue
```

### Corrección (tareas explícitas)

```text
./gradlew :app:assembleLocalDebug :app:testLocalDebugUnitTest :app:lintLocalDebug --continue --no-configuration-cache
```

Rutas de artefactos de test/lint alineadas a `localDebug`. **JaCoCo** no se tocó como tarea de cobertura: ya ejecuta `:app:jacocoTestReport` (depende de `testLocalDebugUnitTest` en Gradle) y sigue **informativo / no bloqueante** (también con `--no-configuration-cache`).

Al desbloquear unit tests, tres guardas Kotlin con techo histórico **032–036 / 037 ausente** también fallaban (misma clase de desfase que el script M07). Se alinearon a **highest = 047** y **048 ausente**, sin tocar SQL ni código funcional.

`--no-configuration-cache` evita fallos de store/reuse del Configuration Cache (p.ej. `copyLocalDebugApk` / `finalizedBy` y capturas de script en `assembleLocalDebug`) que tumbarían el job tras completar las tareas.

### Validaciones ejecutadas

```text
.\gradlew.bat :app:tasks --all
.\gradlew.bat :app:assembleLocalDebug :app:testLocalDebugUnitTest :app:lintLocalDebug
git diff --check
git status -sb
```

### Confirmaciones adicionales

- Advertencia **Node.js 20** de Actions: sigue siendo informativa / no bloqueante.
- Smoke funcional M12: **PENDIENTE EXTERNO**.
- M12 no cerrado oficialmente; M13 no iniciado.

## Tercer fallo detectado: regresión M09 en ejecución limpia

### Contexto

Tras el hotfix `localDebug`, CI superó M07, `assembleLocalDebug` y `lintLocalDebug`. En ejecución limpia:

```text
1246 tests completed, 1 failed
M09AdoptionApplicationTest > acceptApplication_rejectsOthers_andPausesPublication FAILED
java.lang.AssertionError at M09AdoptionApplicationTest.kt:250
```

La validación local previa había dejado `testLocalDebugUnitTest` **UP-TO-DATE**, por lo que **no re-ejecutó** la suite y ocultó el fallo.

### Aserción (línea ~264–267)

- **Esperado:** `AdoptionApplicationStatus.REJECTED` para la postulación competidora (`a2`).
- **Obtenido:** `AdoptionApplicationStatus.ACCEPTED` cuando `a1.id == a2.id`.

### Causa raíz

**DEFECTO_PRODUCTIVO_M09** (mock): `MockAdoptionApplicationRepository.submitApplication` generaba `id = "app_$millis"`. Dos envíos en el mismo milisegundo (frecuente en CI con `UnconfinedTestDispatcher`) comparten ID; al aceptar, `acceptApplication` marca **ambas** filas como `ACCEPTED` (rama `app.id == id`) y no rechaza la competidora.

### Corrección

- IDs únicos: `app_${seq}_${millis}` con `AtomicLong`.
- La prueba ahora también exige `a1.id != a2.id` (sin debilitar el resto de aserciones).

### Validaciones

```text
.\gradlew.bat :app:testLocalDebugUnitTest --tests "...acceptApplication_rejectsOthers_andPausesPublication" --rerun-tasks --no-configuration-cache
.\gradlew.bat :app:testLocalDebugUnitTest --tests "com.comunidapp.app.viewmodel.M09AdoptionApplicationTest" --rerun-tasks --no-configuration-cache
.\gradlew.bat :app:assembleLocalDebug :app:testLocalDebugUnitTest :app:lintLocalDebug --continue --rerun-tasks --no-configuration-cache
```

Smoke funcional M12: **PENDIENTE EXTERNO**. M12 no cerrado; M13 no iniciado.
