# M19 — Matriz funcional (Bloques 1–2)

## Alcance Bloque 1

Fundación local/mock de **red social y contenido** vinculado a organizaciones M03. Publicaciones, comentarios, reacciones y feed público sanitizado.

## Alcance Bloque 2

Persistencia Supabase (migración 060, **no aplicada**): tablas, RLS, RPCs, repositorio remoto. Mock conservado.

## Estados de publicación

| Estado | Público en feed | Terminal |
|--------|-----------------|----------|
| `DRAFT` | No | No |
| `PUBLISHED` | Sí | No |
| `HIDDEN` | No | No |
| `REMOVED` | No | Sí |

**Idempotencia:** publicar, ocultar y eliminar repetidos son no-op con registro interno.

## Tipos de reacción

| Código | Descripción |
|--------|-------------|
| `LIKE` | Me gusta |
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
