# M20 — Matriz funcional (Bloques 1–2)

## Alcance Bloque 1

Lista de conversaciones, hilo, envío de texto, bloqueo stub, context hints (pet/org/event). Navegación: Comunidad → Mensajería.

## Alcance Bloque 2

Persistencia Supabase: tablas `m20_conversations`, `m20_messages`, `m20_user_blocks`; RPCs autenticados; JSON público sin PII; mock conservado como fallback.

## Estados de conversación

| Estado | Envío permitido | Visible en bandeja |
|--------|-----------------|-------------------|
| `ACTIVE` | Sí | Sí |
| `ARCHIVED` | No | Sí (archivada) |
| `BLOCKED` | No | Sí (bloqueada) |

## Estados de mensaje (mock)

| Código | Descripción |
|--------|-------------|
| `SENT` | Enviado |
| `DELIVERED` | Entregado |
| `READ` | Leído |

## Context hints

| Tipo | Origen | routeHint |
|------|--------|-----------|
| `PET` | M08 | `m08/pets/{id}` |
| `ORGANIZATION` | M03 | `m03/orgs/{id}` |
| `EVENT` | M18 | `m18/events/{id}` |

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
| M05 | `attachmentRef` (referencia only B1) |
| M06 | Notificaciones futuras (no chat) |
| M08 | Contexto mascota |
| M18 | Contexto evento |

## Excluido Bloque 1–2

- Aplicación SQL en staging (062 pendiente de aplicar)
- Upload adjuntos
- Retención avanzada
- Reemplazo chat legacy en producción
- Cierre oficial M20 (Bloque 3 pendiente)
