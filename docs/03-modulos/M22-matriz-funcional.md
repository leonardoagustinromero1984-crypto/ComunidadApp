# M22 — Matriz funcional

| Capacidad | Estado |
|---|---|
| Perfil de prestador | Local, borrador/activo/suspendido/archivado |
| Sedes y cobertura | Local, ciudad/barrio/radio |
| Servicios y precios | Local, fijo/desde/a cotizar |
| Catálogo público | Solo perfiles activos y proyección sanitizada |
| Gestión propia | Crear, actualizar, sede, oferta, publicar, suspender, reactivar y archivar |
| Persistencia remota | Bloque 2 local: migración 066, RPC Supabase y adaptador remoto; no aplicada a staging |
| Seguridad de datos | RLS, RPC `SECURITY DEFINER`, permisos M03 y proyecciones públicas sanitizadas |
| Operaciones y ciclo de vida | Bloque 3 local: publicación condicionada, suspensión/reactivación, filtros y stub M06 |
| Próximo alcance | Bloque 4: endurecimiento autoritativo y aplicación autorizada de 066 |
