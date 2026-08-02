# M19 — Matriz funcional (Bloques 1–3)

## Alcance Bloque 3

Feed paginado, filtros, referencias M08/M16/M17/M18, media M05, visibilidad, ARCHIVED, moderación ampliada, LOVE, comentarios editar/archivar. Navegación: Comunidad → Red social.

## Estados de publicación

| Estado | Público en feed | Terminal |
|--------|-----------------|----------|
| `DRAFT` | No | No |
| `PUBLISHED` | Sí (según visibilidad) | No |
| `HIDDEN` | No | No |
| `ARCHIVED` | No | Sí (autor) |
| `REMOVED` | No | Sí |
| `REMOVED_BY_MODERATION` | No | Sí |

## Tipos de reacción

| Código | Descripción |
|--------|-------------|
| `LIKE` | Me gusta |
| `LOVE` | Me encanta |
| `SUPPORT` | Apoyo |
| `CELEBRATE` | Celebrar |

Un usuario = una reacción por publicación (cambio de tipo reemplaza la anterior).

## Permisos M03

| Código | Rol típico |
|--------|------------|
| `social.view` | MEMBER+ |
| `social.manage` | MANAGER+ |

## Integraciones

| Módulo | Uso M19 |
|--------|---------|
| M03 | Organización, permisos |
| M04 | Reportes vía adapter (sin cola duplicada) |
| M05 | `coverImageRef` como referencia |

## Excluido

- M20 mensajería
- SQL aplicado en Bloque 1
- Cola moderación paralela
