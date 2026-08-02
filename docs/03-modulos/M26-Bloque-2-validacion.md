# M26 Bloque 2 — Validación

Validar antes de aplicar en un entorno autorizado:

1. `m26_list_visual_matches` y `m26_list_eligible_recommendations` no exponen IDs de usuario ni PII.
2. `anon` no puede leer ni escribir directamente las tablas `m26_*`.
3. Un usuario solo ve sus matches, duplicados y sesiones; otro usuario recibe listas vacías o `M26_PERMISSION_DENIED` en mutaciones.
4. Recomendaciones pendientes no aparecen en `m26_list_eligible_recommendations`.
5. Scores entre 0 y 1; etiquetas distintas en matching.
6. Cierre de sesión idempotente rechaza sesiones ya cerradas.

Pruebas locales:

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m26.*" --no-configuration-cache --max-workers=1 --console=plain
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```
