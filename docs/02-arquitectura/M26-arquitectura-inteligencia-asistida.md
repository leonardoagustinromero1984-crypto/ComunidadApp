# M26 — Arquitectura de inteligencia asistida

`M26AiRepository` separa la lectura de proyecciones públicas (matches, duplicados, sesiones, recomendaciones aptas) de las operaciones autenticadas. `MockM26AiRepository` opera sobre `M26AiMemoryStore`; cuando `useSupabase` está activo, `DataProvider` selecciona `SupabaseM26AiRepository`.

Los modelos internos conservan propietario, estado e identificadores para autorización. Las proyecciones `M26Public*` se construyen con `M26PrivacySanitizer`, que elimina datos de contacto y evita exponer identificadores internos. `M26RecommendationEligibilityService` filtra recomendaciones con revisión humana aprobada.

Bloque 2 incorpora `SupabaseM26RemoteDataSource`, el mapeo JSON local y la migración forward-only 072. Las tablas `m26_*` tienen RLS deny-all y los RPC `SECURITY DEFINER` entregan proyecciones sin PII ni rutas internas.

La migración 072 está creada localmente y no fue aplicada a staging.
