# M22 Bloque 3 — Auditoría

## Alcance implementado
- Ciclo de vida local explícito: `DRAFT → ACTIVE`, `ACTIVE ↔ SUSPENDED` y archivado terminal idempotente.
- Publicación condicionada a una sede activa y una oferta activa; los perfiles suspendidos o archivados no son públicos.
- Filtros públicos por categoría y ciudad, sin exponer propietario, organización ni identificadores internos.
- Operaciones de publicar, suspender y reactivar en repositorios mock y Supabase; el adaptador remoto usa `m22_update_provider` con `p_status`.
- Stub seguro de hooks M06, sin envío de notificaciones ni dependencia de infraestructura externa.
- Acciones de publicar, suspender y reactivar disponibles en la gestión propia.

## Fuera de alcance
- La migración 066 no fue modificada ni aplicada a staging.
- La validación autoritativa de transiciones en backend queda pendiente de Bloque 4.
- No se implementaron agenda, reservas ni entregas M06.
