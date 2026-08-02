# M26 Bloque 1 — Validación

Validaciones cubiertas:

- etiquetas de matching válidas, distintas y sin markup inseguro;
- scores en rango 0–1;
- prompts de asistencia acotados;
- recomendaciones con título y rationale válidos;
- elegibilidad: solo `humanReviewed` + `APPROVED` en catálogo público;
- proyecciones sin PII ni identificadores internos.

Ejecutar:

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m26.*" --no-configuration-cache --max-workers=1 --console=plain
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```
