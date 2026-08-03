# M27 Bloque 3 — Validación

Operaciones implementadas localmente: apps, claves (hash + reveal-once), webhooks, suscripciones, eventos, entregas simuladas, firma stub, idempotencia, rate limit, OAuth stub, auditoría, SSRF.

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m27.*" --no-configuration-cache --max-workers=1 --console=plain
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Resultado esperado: **54 tests PASS** (14 foundation + 40 operations), compilación PASS.

Migración 075–077: **aplicadas en staging**. Validación remota Bloque 4: **130/130 PASS**.
