# M27 Bloque 1 — Validación

Validaciones cubiertas:

- URLs HTTPS válidas para webhooks y redirect OAuth;
- scopes en formato snake_case;
- contratos publicados vs borradores (elegibilidad);
- proyecciones sin PII ni secretos completos;
- permisos de developer para registrar recursos;
- cuotas sandbox vs producción documentadas.

Ejecutar:

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m27.*" --no-configuration-cache --max-workers=1 --console=plain
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```
