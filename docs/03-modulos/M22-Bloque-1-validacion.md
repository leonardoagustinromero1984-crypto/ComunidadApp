# M22 Bloque 1 — Validación

Validaciones cubiertas:

- nombres y descripciones obligatorios, acotados y sin markup inseguro;
- precios coherentes con `FIXED`, `FROM` y `QUOTE`;
- cobertura válida por ciudad, barrio o radio;
- acceso de gestión limitado al propietario;
- archivo idempotente;
- catálogo público limitado a perfiles activos y sin PII.

Ejecutar:

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m22.*" --no-configuration-cache --max-workers=1 --console=plain
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```
