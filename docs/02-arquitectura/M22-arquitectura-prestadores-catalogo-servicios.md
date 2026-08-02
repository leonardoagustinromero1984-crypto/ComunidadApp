# M22 — Arquitectura de prestadores y catálogo

`M22ProviderRepository` separa la lectura pública del catálogo y la gestión autenticada. En Bloque 1, `MockM22ProviderRepository` opera sobre `M22ProviderMemoryStore`; `DataProvider` expone solamente esa implementación.

Los modelos internos conservan propietario, estado e identificadores para autorización. Las proyecciones `M22Public*` se construyen con `M22PrivacySanitizer`, que elimina datos de contacto y evita exponer identificadores internos cuando basta una etiqueta visible.

Bloque 2 incorporará el adaptador remoto y sus contratos SQL sin modificar los DTO públicos.
