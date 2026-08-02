# M22 — Aplicación de migración 066 en Supabase

## Estado

`066_m22_service_providers_and_catalog.sql` fue creada localmente para M22 Bloque 2. No fue aplicada a staging ni producción.

## Precondiciones

- Revisar que el proyecto contenga las migraciones 001–065.
- Confirmar la existencia de `organizations`, `users`, `organization_branches` y los permisos M03.
- Autorizar explícitamente el entorno objetivo.

## Aplicación

Ejecutar la migración por el flujo habitual de Supabase. No editar ni reordenar migraciones ya aplicadas.

## Verificación posterior

- Comprobar que las tres tablas `m22_*` tengan RLS activo.
- Confirmar que `anon` no tenga privilegios sobre las tablas.
- Ejecutar los RPC públicos y autenticados descritos en la validación M22 Bloque 2.
- Verificar que los importes retornados y persistidos estén en centavos.

## Rollback

La migración es forward-only. Si se requiere corregirla, crear una migración posterior; no eliminar ni modificar 066 aplicada.
