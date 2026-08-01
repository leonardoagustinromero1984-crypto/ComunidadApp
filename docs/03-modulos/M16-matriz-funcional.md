# M16 — Matriz funcional

| ID | Descripción | Autoritativo | Estado B1 | Validación | Bloque |
|----|-------------|--------------|-----------|------------|--------|
| M16-001 | Perfil refugio vinculado a org M03 | M03 + M16 | Implementado | Mock + validadores | 1 |
| M16-002 | Estados operativo/publicación/verificación | M16 | Implementado | Enums + transiciones | 1 |
| M16-003 | Capacidad declarada agregada | M16 | Implementado | Validadores capacidad | 1 |
| M16-004 | Horarios semanales estructurados | M16 | Implementado | Validador horarios | 1 |
| M16-005 | Contacto público institucional | M16 | Implementado | Sanitización | 1 |
| M16-006 | Directorio y búsqueda pública | M16 | Implementado | UI + mock | 1 |
| M16-007 | Detalle público sin PII | M16 | Implementado | `M16PublicShelter` | 1 |
| M16-008 | Administración local mock | M16 | Implementado | Pantalla manage | 1 |
| M16-009 | Permisos org (manager) | M03/M02 | Mock parcial | Policy mock | 1 |
| M16-010 | Idempotencia / terminales | M16 | Implementado | Repo mock | 1 |
| M16-011 | Persistencia remota | M03/M10 | Pendiente | REMOTE_VALIDATION_PENDING | 2 |
| M16-012 | Migración 053 | Supabase | Propuesta | No creada | 2 |
| M16-013 | Integración mascotas M08 | M08 | Pendiente | — | 3 |
| M16-014 | Integración adopciones M09 | M09 | Pendiente | — | 3 |
| M16-015 | Integración tránsito M15 | M15 | Pendiente | — | 3 |
| M16-016 | Métricas operativas | M16 | Pendiente | — | 4 |
| M16-017 | Smoke remoto | Ops | Pendiente | — | 4 |
| M16-018 | Notificaciones M06 | M06 | Hooks prep. | INFRA unavailable | 4 |

## Bloques

- **Bloque 1:** fundación funcional local — **CERRADO LOCALMENTE**
- **Bloque 2:** persistencia y seguridad remota — pendiente
- **Bloque 3:** integración mascotas/adopciones/tránsito — pendiente
- **Bloque 4:** métricas, privacidad, smoke, cierre — pendiente
