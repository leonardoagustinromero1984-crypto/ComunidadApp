# M22 — Arquitectura de prestadores y catálogo

`M22ProviderRepository` separa la lectura pública del catálogo y la gestión autenticada. `MockM22ProviderRepository` opera sobre `M22ProviderMemoryStore`; cuando `useSupabase` está activo, `DataProvider` selecciona `SupabaseM22ProviderRepository`.

Los modelos internos conservan propietario, estado e identificadores para autorización. Las proyecciones `M22Public*` se construyen con `M22PrivacySanitizer`, que elimina datos de contacto y evita exponer identificadores internos cuando basta una etiqueta visible.

Bloque 2 incorpora `SupabaseM22RemoteDataSource`, el mapeo JSON local y la migración forward-only 066. Las tablas internas tienen RLS y los RPC `SECURITY DEFINER` entregan proyecciones públicas sin propietario, organización ni rutas internas. Los precios se representan como enteros en centavos.

La migración 066 está creada localmente y no fue aplicada a staging. Bloque 3 no comenzó.
