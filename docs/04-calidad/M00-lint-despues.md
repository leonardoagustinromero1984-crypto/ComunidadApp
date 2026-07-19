# M00 — Lint DESPUÉS (Etapa 3)

**Fecha:** 2026-07-14  
**Rama:** `m00/etapa-3-calidad-ci`  
**Comando:**

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue
```

## Comparación

| Métrica | Antes | Después |
|---------|------:|--------:|
| Errors | 53 | **0** |
| Warnings | 35 | **35** |
| Hints | 1 | (sin cambio relevante) |
| assembleDebug | SUCCESS | SUCCESS |
| testDebugUnitTest | SUCCESS (7) | SUCCESS (7) |
| lintDebug | FAILED | **SUCCESS** |
| Baseline | no | **no creado** (preferido) |

## Correcciones realizadas

### 1. `InvalidFragmentVersionForActivityResult` (51 → 0)

- **Causa raíz:** resolución transitiva de `androidx.fragment:fragment:1.0.0`.
- **Cambio mínimo:** dependencia explícita `androidx.fragment:fragment-ktx:1.8.6` en `gradle/libs.versions.toml` + `app/build.gradle.kts`.
- **No** se actualizó Compose, Kotlin, AGP ni el resto del catálogo.

### 2. `AppLinkUrlError` (2 → 0)

- **Causa raíz:** `android:autoVerify="true"` sobre deep link OAuth con scheme custom `com.comunidapp.app`.
- **Cambio:** se quitó `autoVerify` del `intent-filter` de `login-callback`. Se conserva scheme/host para Supabase Auth.
- **No** se inventó dominio HTTPS ni Digital Asset Links.

### 3. `UnusedResources`

- No se eliminaron recursos en esta etapa: al pasar lint a verde, muchos quedan como **warnings**.
- Eliminación queda como deuda opcional (requiere verificación reflection/manifest/previews).

## Warnings deliberadamente pendientes

Avisos `GradleDependency` / versiones nuevas:

| Área | Decisión | Justificación |
|------|----------|---------------|
| AGP / libs con “newer version” | **Mantener** | Updates masivos prohibidos; no corrigen errores |
| Iconos AutoMirrored / APIs deprecadas (compile warnings Kotlin) | **Mantener** | Fuera de lint fail; cambio cosmético |

## Baseline

**No se creó** `app/lint-baseline.xml`. CI falla ante cualquier error de lint nuevo.
