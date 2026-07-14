# M00 — Lint ANTES (línea base Etapa 3)

**Fecha:** 2026-07-14  
**Rama:** `m00/etapa-3-calidad-ci` (commit docs `179f4ab`, app alineada a `main` `98d287c`)  
**Comando:**

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue
```

## Resultados previos de medición (auditoría Etapa 1 + revalidación Etapa 2)

Medición consistente sobre el mismo código de `main` (sin WIP GPS/pagos):

| Métrica | Valor |
|---------|------:|
| Errors | **53** |
| Warnings | **35** |
| Hints | **1** |
| assembleDebug | SUCCESS |
| testDebugUnitTest | SUCCESS (7) |
| lintDebug | FAILED |

### Primer fallo reportado

`AndroidManifest.xml` — `AppLinkUrlError` (`autoVerify` + scheme custom OAuth).

### Distribución de IDs (reporte lint debug)

| Count | ID |
|------:|----|
| 51 | `InvalidFragmentVersionForActivityResult` |
| 2 | `AppLinkUrlError` |
| ~13 | `GradleDependency` / avisos de versión (warnings) |
| ~8 | `UnusedResources` (mezcla error/warning según severidad del reporte) |
| resto | `OldTargetApi`, `Unused…`, etc. |

### Causa raíz preliminar (confirmada en Etapa 3)

```text
./gradlew :app:dependencies --configuration debugCompileClasspath
→ androidx.fragment:fragment:1.0.0   (transitiva antigua)
```

`registerForActivityResult` requiere Fragment ≥ 1.3.0; la resolución traía **1.0.0**.

### App Links

Scheme `com.comunidapp.app` + `android:autoVerify="true"` no es Android App Link HTTPS → lint correcto al marcar error.
