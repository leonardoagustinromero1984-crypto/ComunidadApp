# M22 Bloque 2 — Validación

Validar antes de aplicar en un entorno autorizado:

1. `m22_list_catalog` y `m22_get_provider_detail` no exponen propietario, organización ni identificadores internos.
2. `anon` no puede leer ni escribir directamente las tres tablas `m22_*`.
3. Un propietario puede crear, editar, agregar sede/oferta y archivar; otro usuario recibe `M22_PERMISSION_DENIED`.
4. La gestión organizacional exige `provider.profile.manage` o `provider.catalog.manage` según operación.
5. `FIXED` y `FROM` requieren `price_amount_cents > 0`; `QUOTE` requiere precio nulo.
6. El catálogo sólo devuelve prestadores `ACTIVE`, sedes activas y ofertas activas.

Pruebas locales:

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m22.*" --no-configuration-cache --max-workers=1 --console=plain
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```
