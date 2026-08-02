# M20 — Matriz funcional (Bloques 1–3)

## Alcance Bloque 1

Lista de conversaciones, hilo, envío de texto, bloqueo stub, context hints (pet/org/event). Navegación: Comunidad → Mensajería.

## Alcance Bloque 2

Persistencia Supabase: tablas `m20_conversations`, `m20_messages`, `m20_user_blocks`; RPCs autenticados; JSON público sin PII; mock conservado como fallback.

## Alcance Bloque 3

Operaciones mock completas, UI bandeja/hilo ampliada, paginación, idempotencia, edición/borrado lógico, archivo per-participante, contexto M17/M19, moderación M04 adapter.

## Tipos de conversación

| Tipo | Uso |
|------|-----|
| `DIRECT` | Entre dos usuarios |
| `ORGANIZATION` | Contexto refugio/org |
| `SUPPORT` | Soporte LeoVer |
| `CONTEXTUAL` | Vinculada a entidad (pet, evento, campaña, post) |

## Estados de conversación (vista actor)

| Estado | Envío permitido | Visible en bandeja |
|--------|-----------------|-------------------|
| `ACTIVE` | Sí | Sí |
| `ARCHIVED` (per-participante) | No (actor) | Sí |
| `BLOCKED` | No | Sí |

## Estados de mensaje

| Código | Descripción |
|--------|-------------|
| `PENDING_LOCAL` | Cola local |
| `SENT` | Enviado |
| `DELIVERED` | Entregado |
| `READ` | Leído |
| `FAILED` | Fallido |
| `EDITED` | Editado |
| `DELETED` | Eliminado lógico |

## Context hints

| Tipo | Origen | routeHint |
|------|--------|-----------|
| `PET` | M08 | `m08/pets/{id}` |
| `ORGANIZATION` | M03 | `m03/orgs/{id}` |
| `EVENT` | M18 | `m18/events/{id}` |
| `CAMPAIGN` | M17 | `m17/campaigns/{id}` |
| `SOCIAL_POST` | M19 | `m19/posts/{id}` |

## Permisos (contrato Kotlin)

| Código | Rol típico |
|--------|------------|
| `messaging.view` | Usuario autenticado |
| `messaging.send` | Participante ACTIVE |
| `messaging.block` | Participante |

## Integraciones

| Módulo | Uso M20 |
|--------|---------|
| M01 | Actor / sesión |
| M03 | Contexto organización |
| M04 | Reportes vía adapter |
| M05 | `attachmentRef` (referencia only) |
| M06 | Notificaciones futuras |
| M08 | Contexto mascota |
| M17 | Contexto campaña |
| M18 | Contexto evento |
| M19 | Contexto publicación social |

## Excluido Bloque 3

- Aplicación SQL 062 en staging
- Upload adjuntos binarios
- Retención avanzada
- **M20 cerrado oficialmente** (Bloque 4 — 062+063 staging, 125/125 + smoke 25/25)
