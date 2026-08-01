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
| M16-011 | Persistencia remota | M03/M10 | Implementado | SupabaseM16ShelterRepository | 2 |
| M16-012 | Migración 053 | Supabase | Aplicada staging | SQL/RLS 50/50 PASS | 2 |
| M16-013 | Integración mascotas M08 | M08 | Implementado | Proyección operativa | 3 |
| M16-014 | Integración adopciones M09 | M09 | Implementado | Dimensión adopción separada | 3 |
| M16-015 | Integración tránsito M15 | M15 | Implementado | Dimensión tránsito separada | 3 |
| M16-019 | Resumen operativo refugio | M16 | Implementado | UI manage + servicio | 3 |
| M16-020 | Ocupación derivada | M08/M11/M15 | Implementado | Sin duplicar mascotas | 3 |
| M16-021 | Compatibilidad M11 legacy | M11/M16 | Implementado | Adaptador navegación | 3 |
| M16-016 | Métricas operativas | M16 | Implementado | Bloque 4 — committed/overCapacity | 4 |
| M16-017 | Cola verificación M04 | M04/M16 | Implementado | Integrada en cola org | 4 |
| M16-018 | Smoke remoto | Ops | PASS repositorio | PostgREST 6/6; físico diferido | 4 |
| M16-019 | Notificaciones M06 | M06 | Hooks prep. | INFRA unavailable | 4 |

## Bloques

- **Bloque 1:** fundación funcional local — **CERRADO OFICIAL**
- **Bloque 2:** persistencia y seguridad remota — **CERRADO OFICIAL** (053 aplicada staging)
- **Bloque 3:** integración operativa M08/M09/M15, ocupación derivada, compat M11 — **CERRADO OFICIAL**
- **Bloque 4:** corrección ocupación, métricas, M04, cierre global — **CERRADO OFICIAL**
- **M16 Refugios:** **CIERRE OFICIAL COMPLETADO** (2026-08-01)
