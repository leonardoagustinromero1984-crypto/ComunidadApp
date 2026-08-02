# M22 Bloque 2 — Auditoría

## Alcance implementado

- Persistencia forward-only en `066_m22_service_providers_and_catalog.sql`.
- Tablas internas para prestadores, sedes y ofertas; los importes usan centavos.
- RLS activo y sin privilegios directos para `anon` ni `authenticated`.
- RPC `SECURITY DEFINER` con `search_path = public`, autorización por `auth.uid()` y permisos M03.
- Proyecciones públicas sanitizadas: no devuelven `organization_id`, `owner_user_id` ni rutas internas.
- Adaptador remoto Kotlin y selección Supabase/mock mediante `DataProvider.useSupabase`.

## Fuera de alcance

- La migración 066 no fue aplicada a staging.
- No se modificaron `service_profiles` ni tablas veterinarias M12.
- No se implementó M23 (agenda o reservas).
