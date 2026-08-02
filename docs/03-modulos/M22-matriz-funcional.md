# M22 — Matriz funcional

| Capacidad | Estado |
|---|---|
| Perfil de prestador | DRAFT/ACTIVE/SUSPENDED/ARCHIVED |
| Sedes y cobertura | Ciudad/barrio/radio; CHECK RADIUS corregido en 067 |
| Servicios y precios | FIXED/FROM/QUOTE en centavos |
| Catálogo público | Solo ACTIVE; filtros categoría/ciudad |
| Gestión propia | CRUD, publicar, suspender, reactivar, archivar |
| Persistencia remota | 066+067 aplicadas staging; RPC Supabase + repositorio |
| Operaciones ciclo de vida | Bloque 3: publicación condicionada, idempotencia, permisos |
| Validación remota | 75/75 estructural + 25/25 smoke |
| Próximo alcance | **M23 Agenda y reservas** (no iniciado) |
